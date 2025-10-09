package com.xprobe.scanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * 配置持久化管理器
 * 
 * 功能：
 * - 将配置序列化为JSON保存到磁盘
 * - 从磁盘加载JSON配置
 * - 提供默认配置
 */
public class ConfigPersistence {
    private static final String CONFIG_FILE = 
        System.getProperty("user.home") + "/.xprobe/config.json";
    private static final String BACKUP_FILE = 
        System.getProperty("user.home") + "/.xprobe/config.json.backup";
    private static final String TEMP_FILE = 
        System.getProperty("user.home") + "/.xprobe/config.json.tmp";
    
    private final ObjectMapper mapper;
    
    // ✅ 配置缓存（避免频繁磁盘IO）
    private volatile XProbeConfig cachedConfig;
    private volatile long lastLoadTime = 0;
    private static final long CACHE_TTL_MS = 5000; // 缓存5秒
    
    public ConfigPersistence() {
        this.mapper = new ObjectMapper();
        // 美化输出，便于阅读和编辑
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // 忽略未知属性（向前兼容）
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
            false
        );
    }
    
    /**
     * ✅ P0修复: 保存配置到磁盘（带备份和原子性写入）
     * 
     * 保存策略：
     * 1. 如果已有配置文件，先备份到 config.json.backup
     * 2. 写入到临时文件 config.json.tmp
     * 3. 原子性重命名 config.json.tmp → config.json
     * 4. 失败时不影响原有配置
     * 
     * @param config 配置对象
     * @throws IOException 如果保存失败
     */
    public void save(XProbeConfig config) throws IOException {
        File file = new File(CONFIG_FILE);
        File tempFile = new File(TEMP_FILE);
        File backupFile = new File(BACKUP_FILE);
        
        // 创建目录（如果不存在）
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("无法创建配置目录: " + parentDir.getAbsolutePath());
            }
        }
        
        // ✅ 步骤1: 备份现有配置
        if (file.exists()) {
            try {
                // 使用Files.copy可能更安全，但为了兼容性使用简单方式
                if (backupFile.exists() && !backupFile.delete()) {
                    throw new IOException("无法删除旧备份文件");
                }
                if (!file.renameTo(backupFile)) {
                    // 重命名失败，尝试复制
                    java.nio.file.Files.copy(
                        file.toPath(), 
                        backupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } catch (Exception e) {
                // 备份失败不影响保存，只记录警告
                System.err.println("⚠️ 配置备份失败: " + e.getMessage());
            }
        }
        
        // ✅ 步骤2: 写入临时文件
        try {
            mapper.writeValue(tempFile, config);
        } catch (IOException e) {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new IOException("写入临时配置文件失败: " + e.getMessage(), e);
        }
        
        // ✅ 步骤3: 原子性重命名（关键步骤）
        try {
            // 如果目标文件存在，先删除（Windows需要）
            if (file.exists() && !file.delete()) {
                throw new IOException("无法删除旧配置文件");
            }
            
            // 重命名临时文件为正式文件
            if (!tempFile.renameTo(file)) {
                // 重命名失败，尝试复制
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            }
        } catch (Exception e) {
            // 保存失败，尝试从备份恢复
            if (backupFile.exists()) {
                try {
                    backupFile.renameTo(file);
                    System.err.println("⚠️ 保存失败，已从备份恢复配置");
                } catch (Exception restoreError) {
                    System.err.println("❌ 保存失败且备份恢复失败: " + restoreError.getMessage());
                }
            }
            throw new IOException("保存配置文件失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件（如果还存在）
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        // ✅ 更新缓存
        cachedConfig = config;
        lastLoadTime = System.currentTimeMillis();
    }
    
    /**
     * ✅ P0修复: 从磁盘加载配置（带缓存和备份恢复）
     * 
     * 加载策略：
     * 1. 尝试从主配置文件加载
     * 2. 如果主配置损坏，尝试从备份加载
     * 3. 如果备份也损坏，返回默认配置
     * 
     * @return 配置对象，如果文件不存在则返回默认配置
     * @throws IOException 如果加载失败
     */
    public XProbeConfig load() throws IOException {
        // ✅ 检查缓存是否有效（5秒内）
        long now = System.currentTimeMillis();
        if (cachedConfig != null && (now - lastLoadTime) < CACHE_TTL_MS) {
            return cachedConfig;
        }
        
        File file = new File(CONFIG_FILE);
        File backupFile = new File(BACKUP_FILE);
        
        // ✅ 步骤1: 尝试从主配置文件加载
        if (file.exists()) {
            try {
                cachedConfig = mapper.readValue(file, XProbeConfig.class);
                lastLoadTime = now;
                return cachedConfig;
            } catch (IOException e) {
                System.err.println("⚠️ 主配置文件损坏: " + e.getMessage());
                
                // ✅ 步骤2: 尝试从备份恢复
                if (backupFile.exists()) {
                    try {
                        System.err.println("正在尝试从备份恢复配置...");
                        cachedConfig = mapper.readValue(backupFile, XProbeConfig.class);
                        lastLoadTime = now;
                        
                        // 从备份恢复成功，保存回主配置文件
                        try {
                            save(cachedConfig);
                            System.err.println("✅ 已从备份恢复配置");
                        } catch (IOException saveError) {
                            System.err.println("⚠️ 备份恢复成功但保存失败: " + saveError.getMessage());
                        }
                        
                        return cachedConfig;
                    } catch (IOException backupError) {
                        System.err.println("❌ 备份文件也已损坏: " + backupError.getMessage());
                        // 继续到步骤3
                    }
                }
                
                // ✅ 步骤3: 所有恢复尝试失败，返回默认配置
                System.err.println("⚠️ 配置文件和备份都无法加载，使用默认配置");
                throw new IOException("配置文件损坏且备份恢复失败: " + e.getMessage(), e);
            }
        }
        
        // 文件不存在，返回默认配置
        cachedConfig = createDefaultConfig();
        lastLoadTime = now;
        return cachedConfig;
    }
    
    /**
     * ✅ 强制重新加载配置（跳过缓存）
     * 用于UI保存后立即生效
     */
    public XProbeConfig forceReload() throws IOException {
        cachedConfig = null;
        return load();
    }
    
    /**
     * ✅ 使缓存失效
     */
    public void invalidateCache() {
        cachedConfig = null;
        lastLoadTime = 0;
    }
    
    /**
     * 创建默认配置
     * 
     * @return 默认配置对象
     */
    private XProbeConfig createDefaultConfig() {
        XProbeConfig config = new XProbeConfig();
        
        // 黑白名单默认禁用
        config.setWhitelistEnabled(false);
        config.setBlacklistEnabled(false);
        
        // 参数收集默认为仅参数模式
        config.setCollectionMode("PARAMETERS_ONLY");
        
        // 初始化空集合
        config.setGlobalParameters(new HashSet<>());
        
        return config;
    }
    
    /**
     * 获取配置文件路径
     * 
     * @return 配置文件路径
     */
    public String getConfigFilePath() {
        return CONFIG_FILE;
    }
    
    /**
     * 检查配置文件是否存在
     * 
     * @return true 如果存在
     */
    public boolean configFileExists() {
        return new File(CONFIG_FILE).exists();
    }
    
    /**
     * 删除配置文件
     * 
     * @return true 如果删除成功
     */
    public boolean deleteConfigFile() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }
    
    /**
     * ✅ P0新增: 检查备份文件是否存在
     * 
     * @return true 如果备份文件存在
     */
    public boolean backupFileExists() {
        return new File(BACKUP_FILE).exists();
    }
    
    /**
     * ✅ P0新增: 获取备份文件路径
     * 
     * @return 备份文件路径
     */
    public String getBackupFilePath() {
        return BACKUP_FILE;
    }
    
    /**
     * ✅ P0新增: 手动创建备份
     * 
     * 用于用户手动备份配置的场景
     * 
     * @throws IOException 如果备份失败
     */
    public void createManualBackup() throws IOException {
        File file = new File(CONFIG_FILE);
        File backupFile = new File(BACKUP_FILE);
        
        if (!file.exists()) {
            throw new IOException("配置文件不存在，无法备份");
        }
        
        java.nio.file.Files.copy(
            file.toPath(),
            backupFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }
    
    /**
     * ✅ P0新增: 从备份恢复（不保存）
     * 
     * 仅加载备份内容，不自动保存回主配置
     * 
     * @return 从备份加载的配置对象
     * @throws IOException 如果备份不存在或加载失败
     */
    public XProbeConfig loadFromBackup() throws IOException {
        File backupFile = new File(BACKUP_FILE);
        
        if (!backupFile.exists()) {
            throw new IOException("备份文件不存在");
        }
        
        return mapper.readValue(backupFile, XProbeConfig.class);
    }
    
    /**
     * ✅ P0新增: 删除备份文件
     * 
     * @return true 如果删除成功
     */
    public boolean deleteBackupFile() {
        File backupFile = new File(BACKUP_FILE);
        if (backupFile.exists()) {
            return backupFile.delete();
        }
        return true;
    }
}


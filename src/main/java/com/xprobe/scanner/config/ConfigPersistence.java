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
     * 保存配置到磁盘
     * 
     * @param config 配置对象
     * @throws IOException 如果保存失败
     */
    public void save(XProbeConfig config) throws IOException {
        File file = new File(CONFIG_FILE);
        
        // 创建目录（如果不存在）
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // 序列化并保存
        mapper.writeValue(file, config);
        
        // ✅ 更新缓存
        cachedConfig = config;
        lastLoadTime = System.currentTimeMillis();
    }
    
    /**
     * 从磁盘加载配置（带缓存）
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
        
        if (file.exists()) {
            try {
                cachedConfig = mapper.readValue(file, XProbeConfig.class);
                lastLoadTime = now;
                return cachedConfig;
            } catch (IOException e) {
                // 配置文件损坏，返回默认配置
                throw new IOException("配置文件损坏: " + e.getMessage(), e);
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
        
        // Arjun默认配置
        config.setArjunPath("arjun");
        config.setBurpProxyAddress("http://127.0.0.1:8080");
        config.setThreadCount(10);
        config.setTimeout(30);
        config.setSendToBurp(true);
        config.setEnableJsonOutput(true);
        config.setEnableVerboseOutput(false);
        
        // 参数收集默认为仅参数模式
        config.setCollectionMode("PARAMETERS_ONLY");
        
        // 初始化空集合
        config.setGlobalParameters(new HashSet<>());
        config.setCustomDictionary(new HashSet<>());
        
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
}


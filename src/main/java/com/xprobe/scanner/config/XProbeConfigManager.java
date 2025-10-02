package com.xprobe.scanner.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * ✅ XProbe配置管理器（单例模式 + 观察者模式）
 * 
 * 优势：
 * 1. 配置在内存中只有一份，避免重复加载
 * 2. 配置变更时主动通知订阅者
 * 3. 线程安全
 * 4. 支持配置热更新
 * 
 * 使用方式：
 * 1. 初始化时加载一次：manager.initialize()
 * 2. 组件获取配置：manager.getConfig()
 * 3. 订阅配置变更：manager.subscribe(config -> { ... })
 * 4. 保存配置：manager.saveConfig(config)
 */
public class XProbeConfigManager {
    
    private final ConfigPersistence persistence;
    
    // ✅ 单例配置对象（内存中唯一副本）
    private volatile XProbeConfig currentConfig;
    
    // ✅ 配置变更监听器列表（观察者模式）
    private final List<Consumer<XProbeConfig>> listeners = new CopyOnWriteArrayList<>();
    
    private volatile boolean initialized = false;
    
    public XProbeConfigManager(ConfigPersistence persistence) {
        this.persistence = persistence;
    }
    
    /**
     * ✅ 初始化配置管理器（启动时调用一次）
     * 
     * 如果加载失败，会使用默认配置并标记为已初始化，确保系统能正常工作
     */
    public synchronized void initialize() throws IOException {
        if (!initialized) {
            try {
                currentConfig = persistence.load();
                initialized = true;
            } catch (IOException e) {
                // ✅ 加载失败时使用默认配置，确保系统能正常工作
                currentConfig = new XProbeConfig();
                initialized = true;
                // 重新抛出异常，让调用者知道加载失败了
                throw e;
            }
        }
    }
    
    /**
     * ✅ 获取当前配置（快速，无IO）
     * 
     * ⚠️ 警告：返回的是内部配置对象的引用，请勿直接修改！
     * 如果需要修改配置，请使用 getConfigCopy() 或 updateConfig()
     * 
     * @return 当前配置对象（只读）
     */
    public XProbeConfig getConfig() {
        if (!initialized) {
            throw new IllegalStateException("ConfigManager未初始化，请先调用initialize()");
        }
        return currentConfig;
    }
    
    /**
     * ✅ 获取配置的副本（防御性复制）
     * 
     * 用于需要修改配置的场景，修改副本后调用 saveConfig() 保存
     * 
     * @return 配置对象的深拷贝
     */
    public XProbeConfig getConfigCopy() {
        return getConfig().copy();
    }
    
    /**
     * ✅ 事务式更新配置
     * 
     * 推荐的配置更新方式，保证原子性和线程安全
     * 
     * @param updater 配置更新函数
     * @throws IOException 如果保存失败
     */
    public synchronized void updateConfig(java.util.function.Consumer<XProbeConfig> updater) throws IOException {
        // 1. 创建副本
        XProbeConfig copy = getConfigCopy();
        
        // 2. 应用修改
        updater.accept(copy);
        
        // 3. 保存并更新（原子操作）
        saveConfig(copy);
    }
    
    /**
     * ✅ 保存配置并通知所有订阅者
     * 
     * @param config 新的配置对象
     */
    public synchronized void saveConfig(XProbeConfig config) throws IOException {
        // 1. 保存到磁盘
        persistence.save(config);
        
        // 2. 更新内存中的配置
        currentConfig = config;
        
        // 3. 通知所有订阅者
        notifyListeners(config);
    }
    
    /**
     * ✅ 重新加载配置（从磁盘）
     * 用于外部修改了配置文件的场景
     */
    public synchronized void reload() throws IOException {
        XProbeConfig newConfig = persistence.forceReload();
        currentConfig = newConfig;
        notifyListeners(newConfig);
    }
    
    /**
     * ✅ 订阅配置变更事件
     * 
     * @param listener 监听器，当配置变更时被调用
     */
    public void subscribe(Consumer<XProbeConfig> listener) {
        listeners.add(listener);
    }
    
    /**
     * ✅ 取消订阅
     */
    public void unsubscribe(Consumer<XProbeConfig> listener) {
        listeners.remove(listener);
    }
    
    /**
     * 通知所有订阅者
     */
    private void notifyListeners(XProbeConfig config) {
        for (Consumer<XProbeConfig> listener : listeners) {
            try {
                listener.accept(config);
            } catch (Exception e) {
                // 防止单个监听器异常影响其他监听器
                System.err.println("配置变更监听器执行失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * ✅ 便捷方法：检查被动扫描是否启用（线程安全）
     * 
     * @return 如果被动扫描启用返回true，未初始化时返回false（默认）
     */
    public boolean isPassiveScanEnabled() {
        try {
            return getConfig().isEnablePassiveScan();
        } catch (IllegalStateException e) {
            // 未初始化，返回默认值（禁用）
            return false;
        }
    }
    
    /**
     * ✅ 便捷方法：获取全局注入模式（线程安全）
     * 
     * @return 全局注入模式，未初始化时返回BATCH（默认）
     */
    public Configuration.InjectionMode getGlobalInjectionMode() {
        try {
            return getConfig().getGlobalInjectionMode();
        } catch (IllegalStateException e) {
            // 未初始化，返回默认值（批量模式）
            return Configuration.InjectionMode.BATCH;
        }
    }
    
    /**
     * ✅ 便捷方法：获取扫描结果记录模式（线程安全）
     * 
     * @return 扫描结果记录模式，未初始化时返回MATCHED_ONLY（默认）
     */
    public XProbeConfig.ScanResultLogMode getScanResultLogMode() {
        try {
            return getConfig().getScanResultLogMode();
        } catch (IllegalStateException e) {
            // 未初始化，返回默认值（仅记录命中）
            return XProbeConfig.ScanResultLogMode.MATCHED_ONLY;
        }
    }
    
    /**
     * 获取配置文件路径
     */
    public String getConfigFilePath() {
        return persistence.getConfigFilePath();
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}


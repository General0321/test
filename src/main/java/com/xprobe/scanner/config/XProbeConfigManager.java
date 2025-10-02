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
     * 初始化配置管理器（启动时调用一次）
     */
    public synchronized void initialize() throws IOException {
        if (!initialized) {
            currentConfig = persistence.load();
            initialized = true;
        }
    }
    
    /**
     * ✅ 获取当前配置（快速，无IO）
     * 
     * @return 当前配置对象（只读，不要修改！）
     */
    public XProbeConfig getConfig() {
        if (!initialized) {
            throw new IllegalStateException("ConfigManager未初始化，请先调用initialize()");
        }
        return currentConfig;
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
     * ✅ 便捷方法：检查被动扫描是否启用
     */
    public boolean isPassiveScanEnabled() {
        return getConfig().isEnablePassiveScan();
    }
    
    /**
     * ✅ 便捷方法：获取全局注入模式
     */
    public Configuration.InjectionMode getGlobalInjectionMode() {
        return getConfig().getGlobalInjectionMode();
    }
    
    /**
     * ✅ 便捷方法：获取扫描结果记录模式
     */
    public XProbeConfig.ScanResultLogMode getScanResultLogMode() {
        return getConfig().getScanResultLogMode();
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


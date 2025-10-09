package com.xprobe.scanner.config;

import java.io.File;
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
    private final RulePersistence rulePersistence;
    
    // ✅ 单例配置对象（内存中唯一副本）
    private volatile XProbeConfig currentConfig;
    
    // ✅ 配置变更监听器列表（观察者模式）
    private final List<Consumer<XProbeConfig>> listeners = new CopyOnWriteArrayList<>();
    
    private volatile boolean initialized = false;
    
    public XProbeConfigManager(ConfigPersistence persistence) {
        this.persistence = persistence;
        this.rulePersistence = new RulePersistence();
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
     * ✅ P0修复: 获取当前配置（防御性复制，线程安全）
     * 
     * 返回配置对象的深拷贝，避免并发修改问题
     * 
     * @return 当前配置对象的副本
     */
    public XProbeConfig getConfig() {
        if (!initialized) {
            throw new IllegalStateException("ConfigManager未初始化，请先调用initialize()");
        }
        // ✅ P0修复: 返回深拷贝而非引用，完全避免并发修改问题
        return currentConfig.copy();
    }
    
    /**
     * ✅ 获取配置的副本（与getConfig()相同，保留以兼容旧代码）
     * 
     * @deprecated 使用 getConfig() 即可，已经返回副本
     */
    @Deprecated
    public XProbeConfig getConfigCopy() {
        return getConfig();
    }
    
    /**
     * ✅ 获取配置引用（仅供内部只读使用，性能优化）
     * 
     * 警告：仅供内部便捷方法使用，外部代码不应调用此方法
     * 
     * @return 当前配置对象的引用（只读）
     */
    private XProbeConfig getConfigReference() {
        if (!initialized) {
            throw new IllegalStateException("ConfigManager未初始化，请先调用initialize()");
        }
        return currentConfig;
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
     * ✅ 便捷方法：检查被动扫描是否启用（线程安全，高性能）
     * 
     * @return 如果被动扫描启用返回true，未初始化时返回false（默认）
     */
    public boolean isPassiveScanEnabled() {
        try {
            // ✅ 性能优化: 只读操作使用引用而非副本
            return getConfigReference().isEnablePassiveScan();
        } catch (IllegalStateException e) {
            // 未初始化，返回默认值（禁用）
            return false;
        }
    }
    
    /**
     * ✅ 便捷方法：获取全局注入模式（线程安全，高性能）
     * 
     * @return 全局注入模式，未初始化时返回BATCH（默认）
     */
    public Configuration.InjectionMode getGlobalInjectionMode() {
        try {
            // ✅ 性能优化: 只读操作使用引用而非副本
            return getConfigReference().getGlobalInjectionMode();
        } catch (IllegalStateException e) {
            // 未初始化，返回默认值（批量模式）
            return Configuration.InjectionMode.BATCH;
        }
    }
    
    /**
     * ✅ 便捷方法：获取扫描结果记录模式（线程安全，高性能）
     * 
     * @return 扫描结果记录模式，未初始化时返回MATCHED_ONLY（默认）
     */
    public XProbeConfig.ScanResultLogMode getScanResultLogMode() {
        try {
            // ✅ 性能优化: 只读操作使用引用而非副本
            return getConfigReference().getScanResultLogMode();
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
    
    // ========== 规则导入导出功能（新增）==========
    
    /**
     * ✅ 导出规则到JSON文件
     * 
     * @param file 目标文件
     * @throws IOException 如果导出失败
     */
    public void exportRules(File file) throws IOException {
        // ✅ 使用getConfig()获取深拷贝，避免并发问题
        XProbeConfig config = getConfig();
        List<Configuration> rules = config.getScanConfigurations();
        
        // ✅ 再次创建副本，确保完全独立
        List<Configuration> rulesCopy = new java.util.ArrayList<>(rules);
        rulePersistence.exportRules(rulesCopy, file);
    }
    
    /**
     * ✅ 从JSON文件导入规则（追加或替换模式）
     * 
     * @param file 源文件
     * @param append true=追加到现有规则，false=替换现有规则
     * @throws IOException 如果导入失败
     */
    public void importRules(File file, boolean append) throws IOException {
        List<Configuration> importedRules = rulePersistence.importRules(file);
        
        // ✅ 验证导入的规则
        validateImportedRules(importedRules);
        
        updateConfig(config -> {
            if (append) {
                // ✅ 追加模式：创建新列表并处理ID冲突
                List<Configuration> existingRules = config.getScanConfigurations();
                List<Configuration> mergedRules = new java.util.ArrayList<>(existingRules);
                
                // ✅ 处理ID冲突：检查并重新生成重复的规则ID
                for (Configuration rule : importedRules) {
                    if (hasConflictingId(mergedRules, rule.getRuleId())) {
                        rule.generateNewRuleId();  // 生成新的唯一ID
                    }
                    mergedRules.add(rule);
                }
                
                config.setScanConfigurations(mergedRules);
            } else {
                // ✅ 替换模式：使用导入的规则
                config.setScanConfigurations(new java.util.ArrayList<>(importedRules));
            }
        });
    }
    
    /**
     * ✅ 验证导入的规则
     * 
     * @param rules 待验证的规则列表
     * @throws IOException 如果验证失败
     */
    private void validateImportedRules(List<Configuration> rules) throws IOException {
        if (rules == null) {
            throw new IOException("导入的规则列表为null");
        }
        
        // ✅ 限制规则数量（防止DoS）
        if (rules.size() > 1000) {
            throw new IOException("导入的规则数量过多（最多1000条），实际: " + rules.size());
        }
        
        // ✅ 验证每个规则的基本字段
        for (int i = 0; i < rules.size(); i++) {
            Configuration rule = rules.get(i);
            if (rule == null) {
                throw new IOException("规则 #" + (i + 1) + " 为null");
            }
            
            // 确保有规则ID
            if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                // 自动生成ID而不是抛出异常
                rule.generateNewRuleId();
            }
            
            // 验证规则名称
            if (rule.getCustomLabel() == null || rule.getCustomLabel().trim().isEmpty()) {
                rule.setCustomLabel("导入的规则 #" + (i + 1));
            }
        }
    }
    
    /**
     * ✅ 检查规则ID是否冲突
     * 
     * @param existingRules 现有规则列表
     * @param ruleId 要检查的规则ID
     * @return true 如果ID已存在
     */
    private boolean hasConflictingId(List<Configuration> existingRules, String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            return false;
        }
        
        for (Configuration rule : existingRules) {
            if (ruleId.equals(rule.getRuleId())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * ✅ 保存规则到外部文件（如果启用了外部规则文件）
     * 
     * 根据配置决定是否将规则保存到单独的文件中
     * 
     * @throws IOException 如果保存失败
     */
    public void saveRulesToExternalFile() throws IOException {
        XProbeConfig config = getConfigReference();
        
        if (config.isUseExternalRuleFile()) {
            String ruleFilePath = config.getEffectiveRuleFilePath();
            List<Configuration> rules = config.getScanConfigurations();
            rulePersistence.saveRules(rules, ruleFilePath);
        }
    }
    
    /**
     * ✅ 从外部文件加载规则（如果启用了外部规则文件）
     * 
     * @throws IOException 如果加载失败
     */
    public void loadRulesFromExternalFile() throws IOException {
        XProbeConfig config = getConfigReference();
        
        if (config.isUseExternalRuleFile()) {
            String ruleFilePath = config.getEffectiveRuleFilePath();
            List<Configuration> rules = rulePersistence.loadRules(ruleFilePath);
            
            updateConfig(cfg -> {
                cfg.setScanConfigurations(rules);
            });
        }
    }
    
    /**
     * ✅ 同步规则（根据配置决定保存位置）
     * 
     * 如果启用了外部规则文件，则保存到外部文件，否则保存到主配置文件
     * 
     * @throws IOException 如果保存失败
     */
    public void syncRules() throws IOException {
        XProbeConfig config = getConfigReference();
        
        if (config.isUseExternalRuleFile()) {
            // 保存到外部规则文件
            saveRulesToExternalFile();
            
            // 主配置文件中清空规则（避免冗余）
            XProbeConfig configCopy = config.copy();
            configCopy.setScanConfigurations(new java.util.ArrayList<>());
            persistence.save(configCopy);
        } else {
            // 保存到主配置文件
            persistence.save(config);
        }
    }
    
    /**
     * ✅ 获取规则文件路径
     * 
     * @return 规则文件的完整路径
     */
    public String getRuleFilePath() {
        return getConfigReference().getEffectiveRuleFilePath();
    }
    
    /**
     * ✅ 验证规则文件格式
     * 
     * @param filePath 文件路径
     * @return true 如果格式正确
     */
    public boolean validateRuleFile(String filePath) {
        return rulePersistence.validateRuleFile(filePath);
    }
}



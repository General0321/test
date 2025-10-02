package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.*;

/**
 * XProbe 统一配置类
 * 
 * 用于持久化所有插件配置，包括：
 * - 黑白名单
 * - Arjun工具配置
 * - 参数收集模式
 * - 全局参数字典
 * - 被动扫描规则
 */
public class XProbeConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 黑白名单配置
    private List<String> whitelist = new ArrayList<>();
    private List<String> blacklist = new ArrayList<>();
    private boolean whitelistEnabled = false;
    private boolean blacklistEnabled = false;
    
    // Arjun工具配置
    private String arjunPath = "arjun";
    private String burpProxyAddress = "http://127.0.0.1:8080";
    private int threadCount = 10;
    private int timeout = 30;
    private Set<String> customDictionary = new HashSet<>();
    private boolean enableJsonOutput = true;
    private boolean enableVerboseOutput = false;
    private boolean sendToBurp = true;
    
    // 参数收集模式
    private String collectionMode = "PARAMETERS_ONLY"; // "PARAMETERS_ONLY" or "PARAMETERS_AND_KEYWORDS"
    
    // 全局参数字典
    private Set<String> globalParameters = new HashSet<>();
    
    // 被动扫描规则
    private List<Configuration> scanConfigurations = new ArrayList<>();
    
    // 被动扫描配置
    private boolean enablePassiveScan = true;  // 被动扫描总开关（默认启用）
    private Configuration.InjectionMode globalInjectionMode = Configuration.InjectionMode.BATCH;  // 全局注入模式（默认批量）
    private ScanResultLogMode scanResultLogMode = ScanResultLogMode.MATCHED_ONLY;  // 扫描结果记录模式（默认仅命中）
    
    // 扫描结果记录模式枚举
    public enum ScanResultLogMode {
        ALL_REQUESTS("记录所有流量", "记录被动扫描发出的所有请求（包括未命中的）"),
        MATCHED_ONLY("仅记录命中", "只记录命中规则的请求（节省内存和性能）");
        
        private final String displayName;
        private final String description;
        
        ScanResultLogMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // 主动探测配置
    private boolean enableActiveScan = false;
    private int bruteforceInterval = 300;
    private int minParameterCount = 5;
    private int maxConcurrentHosts = 3;
    private boolean autoStart = false;
    private boolean verboseLogging = false;
    
    // 代理池配置
    private boolean enableProxyPool = false;
    private int proxyTimeout = 10;
    private int maxRetries = 3;
    private List<String> proxyList = new ArrayList<>();
    
    // 默认构造函数
    public XProbeConfig() {
    }
    
    // ========== Getters and Setters ==========
    
    public List<String> getWhitelist() {
        return whitelist;
    }
    
    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist != null ? whitelist : new ArrayList<>();
    }
    
    public List<String> getBlacklist() {
        return blacklist;
    }
    
    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist != null ? blacklist : new ArrayList<>();
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public void setWhitelistEnabled(boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }
    
    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }
    
    public void setBlacklistEnabled(boolean blacklistEnabled) {
        this.blacklistEnabled = blacklistEnabled;
    }
    
    public String getArjunPath() {
        return arjunPath;
    }
    
    public void setArjunPath(String arjunPath) {
        this.arjunPath = arjunPath;
    }
    
    public String getBurpProxyAddress() {
        return burpProxyAddress;
    }
    
    public void setBurpProxyAddress(String burpProxyAddress) {
        this.burpProxyAddress = burpProxyAddress;
    }
    
    public int getThreadCount() {
        return threadCount;
    }
    
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public Set<String> getCustomDictionary() {
        return customDictionary;
    }
    
    public void setCustomDictionary(Set<String> customDictionary) {
        this.customDictionary = customDictionary != null ? customDictionary : new HashSet<>();
    }
    
    public boolean isEnableJsonOutput() {
        return enableJsonOutput;
    }
    
    public void setEnableJsonOutput(boolean enableJsonOutput) {
        this.enableJsonOutput = enableJsonOutput;
    }
    
    public boolean isEnableVerboseOutput() {
        return enableVerboseOutput;
    }
    
    public void setEnableVerboseOutput(boolean enableVerboseOutput) {
        this.enableVerboseOutput = enableVerboseOutput;
    }
    
    public boolean isSendToBurp() {
        return sendToBurp;
    }
    
    public void setSendToBurp(boolean sendToBurp) {
        this.sendToBurp = sendToBurp;
    }
    
    public String getCollectionMode() {
        return collectionMode;
    }
    
    public void setCollectionMode(String collectionMode) {
        this.collectionMode = collectionMode;
    }
    
    public Set<String> getGlobalParameters() {
        return globalParameters;
    }
    
    public void setGlobalParameters(Set<String> globalParameters) {
        this.globalParameters = globalParameters != null ? globalParameters : new HashSet<>();
    }
    
    public List<Configuration> getScanConfigurations() {
        return scanConfigurations;
    }
    
    public void setScanConfigurations(List<Configuration> scanConfigurations) {
        this.scanConfigurations = scanConfigurations != null ? scanConfigurations : new ArrayList<>();
    }
    
    public boolean isEnablePassiveScan() {
        return enablePassiveScan;
    }
    
    public void setEnablePassiveScan(boolean enablePassiveScan) {
        this.enablePassiveScan = enablePassiveScan;
    }
    
    public Configuration.InjectionMode getGlobalInjectionMode() {
        return globalInjectionMode != null ? globalInjectionMode : Configuration.InjectionMode.BATCH;
    }
    
    public void setGlobalInjectionMode(Configuration.InjectionMode globalInjectionMode) {
        this.globalInjectionMode = globalInjectionMode;
    }
    
    public ScanResultLogMode getScanResultLogMode() {
        return scanResultLogMode != null ? scanResultLogMode : ScanResultLogMode.MATCHED_ONLY;
    }
    
    public void setScanResultLogMode(ScanResultLogMode scanResultLogMode) {
        this.scanResultLogMode = scanResultLogMode;
    }
    
    public boolean isEnableActiveScan() {
        return enableActiveScan;
    }
    
    public void setEnableActiveScan(boolean enableActiveScan) {
        this.enableActiveScan = enableActiveScan;
    }
    
    public int getBruteforceInterval() {
        return bruteforceInterval;
    }
    
    public void setBruteforceInterval(int bruteforceInterval) {
        this.bruteforceInterval = bruteforceInterval;
    }
    
    public int getMinParameterCount() {
        return minParameterCount;
    }
    
    public void setMinParameterCount(int minParameterCount) {
        this.minParameterCount = minParameterCount;
    }
    
    public int getMaxConcurrentHosts() {
        return maxConcurrentHosts;
    }
    
    public void setMaxConcurrentHosts(int maxConcurrentHosts) {
        this.maxConcurrentHosts = maxConcurrentHosts;
    }
    
    public boolean isAutoStart() {
        return autoStart;
    }
    
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
    
    public boolean isVerboseLogging() {
        return verboseLogging;
    }
    
    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }
    
    public boolean isEnableProxyPool() {
        return enableProxyPool;
    }
    
    public void setEnableProxyPool(boolean enableProxyPool) {
        this.enableProxyPool = enableProxyPool;
    }
    
    public int getProxyTimeout() {
        return proxyTimeout;
    }
    
    public void setProxyTimeout(int proxyTimeout) {
        this.proxyTimeout = proxyTimeout;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public List<String> getProxyList() {
        return proxyList;
    }
    
    public void setProxyList(List<String> proxyList) {
        this.proxyList = proxyList != null ? proxyList : new ArrayList<>();
    }
    
    /**
     * ✅ 创建配置对象的深拷贝
     * 
     * 用于防御性复制，避免多线程并发修改同一个配置对象
     * 
     * @return 配置对象的完整副本
     */
    public XProbeConfig copy() {
        XProbeConfig copy = new XProbeConfig();
        
        // 黑白名单配置（深拷贝）
        copy.setWhitelist(new ArrayList<>(this.whitelist));
        copy.setBlacklist(new ArrayList<>(this.blacklist));
        copy.setWhitelistEnabled(this.whitelistEnabled);
        copy.setBlacklistEnabled(this.blacklistEnabled);
        
        // Arjun工具配置
        copy.setArjunPath(this.arjunPath);
        copy.setBurpProxyAddress(this.burpProxyAddress);
        copy.setThreadCount(this.threadCount);
        copy.setTimeout(this.timeout);
        copy.setCustomDictionary(new HashSet<>(this.customDictionary));
        copy.setEnableJsonOutput(this.enableJsonOutput);
        copy.setEnableVerboseOutput(this.enableVerboseOutput);
        copy.setSendToBurp(this.sendToBurp);
        
        // 参数收集模式
        copy.setCollectionMode(this.collectionMode);
        
        // 全局参数字典（深拷贝）
        copy.setGlobalParameters(new HashSet<>(this.globalParameters));
        
        // 被动扫描规则（深拷贝，但Configuration对象本身不需要深拷贝，因为它们是不可变的）
        copy.setScanConfigurations(new ArrayList<>(this.scanConfigurations));
        
        // 被动扫描配置
        copy.setEnablePassiveScan(this.enablePassiveScan);
        copy.setGlobalInjectionMode(this.globalInjectionMode);
        copy.setScanResultLogMode(this.scanResultLogMode);
        
        // 主动探测配置
        copy.setEnableActiveScan(this.enableActiveScan);
        copy.setBruteforceInterval(this.bruteforceInterval);
        copy.setMinParameterCount(this.minParameterCount);
        copy.setMaxConcurrentHosts(this.maxConcurrentHosts);
        copy.setAutoStart(this.autoStart);
        copy.setVerboseLogging(this.verboseLogging);
        
        // 代理池配置
        copy.setEnableProxyPool(this.enableProxyPool);
        copy.setProxyTimeout(this.proxyTimeout);
        copy.setMaxRetries(this.maxRetries);
        copy.setProxyList(new ArrayList<>(this.proxyList));
        
        return copy;
    }
}

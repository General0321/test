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
    
    // Java原生Arjun配置
    private boolean arjunEnabled = true;
    private int arjunChunkSize = 250;
    private int arjunTimeout = 15;
    private Set<String> arjunCustomDictionary = new HashSet<>();  // 用户自定义字典
    
    // ✅ Arjun高级配置（性能优化）
    private boolean arjunStableMode = false;      // 稳定模式（随机延迟3-10秒）
    private int arjunThreads = 5;                 // 并发线程数（1-20）
    private int arjunMaxRetries = 5;              // 最大重试次数（1-10）
    private int arjunRateLimit = 9999;            // 速率限制（req/s）
    private Map<String, String> arjunCustomHeaders = new HashMap<>();  // 自定义HTTP头（覆盖/添加）
    
    // Arjun实时模式配置
    private int arjunRealtimeInterval = 300;  // 定时检查间隔（秒），默认5分钟
    private int arjunRealtimeThreshold = 15;  // 参数阈值，默认15个
    
    // 参数收集模式
    private String collectionMode = "PARAMETERS_ONLY"; // "PARAMETERS_ONLY" or "PARAMETERS_AND_KEYWORDS"
    
    // 全局参数字典
    private Set<String> globalParameters = new HashSet<>();
    
    // 被动扫描规则
    private List<Configuration> scanConfigurations = new ArrayList<>();
    
    // ✅ 规则文件配置（新增）
    private boolean useExternalRuleFile = false;  // 是否使用外部规则文件
    private String ruleFilePath = "";             // 外部规则文件路径（空则使用默认路径）
    
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
    private int minParameterCount = 15;  // ✅ 默认15个参数触发Arjun
    private int maxConcurrentHosts = 3;
    private boolean autoStart = false;
    private boolean verboseLogging = false;
    
    // ✅ 线程池配置（性能优化）
    private int scannerCoreThreads = -1;      // 核心线程数（-1=自动，CPU×2）
    private int scannerMaxThreads = -1;       // 最大线程数（-1=自动，CPU×4）
    private int scannerQueueSize = 2000;      // 任务队列大小（默认2000）
    private int scannerKeepAliveSeconds = 120; // 空闲线程存活时间（默认120秒）
    
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
        return whitelist != null ? whitelist : new ArrayList<>();
    }
    
    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist != null ? whitelist : new ArrayList<>();
    }
    
    public List<String> getBlacklist() {
        return blacklist != null ? blacklist : new ArrayList<>();
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
    
    public String getCollectionMode() {
        return collectionMode;
    }
    
    public void setCollectionMode(String collectionMode) {
        this.collectionMode = collectionMode;
    }
    
    public Set<String> getGlobalParameters() {
        return globalParameters != null ? globalParameters : new HashSet<>();
    }
    
    public void setGlobalParameters(Set<String> globalParameters) {
        this.globalParameters = globalParameters != null ? globalParameters : new HashSet<>();
    }
    
    public List<Configuration> getScanConfigurations() {
        return scanConfigurations != null ? scanConfigurations : new ArrayList<>();
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
    
    // ✅ 线程池配置 Getters/Setters
    public int getScannerCoreThreads() {
        return scannerCoreThreads;
    }
    
    public void setScannerCoreThreads(int scannerCoreThreads) {
        this.scannerCoreThreads = scannerCoreThreads;
    }
    
    public int getScannerMaxThreads() {
        return scannerMaxThreads;
    }
    
    public void setScannerMaxThreads(int scannerMaxThreads) {
        this.scannerMaxThreads = scannerMaxThreads;
    }
    
    public int getScannerQueueSize() {
        return scannerQueueSize;
    }
    
    public void setScannerQueueSize(int scannerQueueSize) {
        this.scannerQueueSize = Math.max(100, scannerQueueSize);  // 至少100
    }
    
    public int getScannerKeepAliveSeconds() {
        return scannerKeepAliveSeconds;
    }
    
    public void setScannerKeepAliveSeconds(int scannerKeepAliveSeconds) {
        this.scannerKeepAliveSeconds = Math.max(10, scannerKeepAliveSeconds);  // 至少10秒
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
        return proxyList != null ? proxyList : new ArrayList<>();
    }
    
    public void setProxyList(List<String> proxyList) {
        this.proxyList = proxyList != null ? proxyList : new ArrayList<>();
    }
    
    // Java原生Arjun配置的Getters和Setters
    public boolean isArjunEnabled() {
        return arjunEnabled;
    }
    
    public void setArjunEnabled(boolean arjunEnabled) {
        this.arjunEnabled = arjunEnabled;
    }
    
    public int getArjunChunkSize() {
        return arjunChunkSize;
    }
    
    public void setArjunChunkSize(int arjunChunkSize) {
        this.arjunChunkSize = Math.max(10, Math.min(arjunChunkSize, 1000));
    }
    
    public int getArjunTimeout() {
        return arjunTimeout;
    }
    
    public void setArjunTimeout(int arjunTimeout) {
        this.arjunTimeout = Math.max(5, Math.min(arjunTimeout, 60));
    }
    
    public Set<String> getArjunCustomDictionary() {
        return arjunCustomDictionary != null ? arjunCustomDictionary : new HashSet<>();
    }
    
    public void setArjunCustomDictionary(Set<String> arjunCustomDictionary) {
        this.arjunCustomDictionary = arjunCustomDictionary != null ? arjunCustomDictionary : new HashSet<>();
    }
    
    // Arjun实时模式配置的Getters和Setters
    public int getArjunRealtimeInterval() {
        return arjunRealtimeInterval;
    }

    public void setArjunRealtimeInterval(int arjunRealtimeInterval) {
        this.arjunRealtimeInterval = Math.max(60, Math.min(arjunRealtimeInterval, 3600));  // 1-60分钟
    }

    public int getArjunRealtimeThreshold() {
        return arjunRealtimeThreshold;
    }

    public void setArjunRealtimeThreshold(int arjunRealtimeThreshold) {
        this.arjunRealtimeThreshold = Math.max(1, Math.min(arjunRealtimeThreshold, 100));  // 1-100个参数
    }
    
    // ✅ Arjun高级配置的Getters和Setters
    public boolean isArjunStableMode() {
        return arjunStableMode;
    }
    
    public void setArjunStableMode(boolean arjunStableMode) {
        this.arjunStableMode = arjunStableMode;
    }
    
    public int getArjunThreads() {
        return arjunThreads;
    }
    
    public void setArjunThreads(int arjunThreads) {
        this.arjunThreads = Math.max(1, Math.min(arjunThreads, 20));  // 1-20线程
    }
    
    public int getArjunMaxRetries() {
        return arjunMaxRetries;
    }
    
    public void setArjunMaxRetries(int arjunMaxRetries) {
        this.arjunMaxRetries = Math.max(1, Math.min(arjunMaxRetries, 10));  // 1-10次重试
    }
    
    public int getArjunRateLimit() {
        return arjunRateLimit;
    }
    
    public void setArjunRateLimit(int arjunRateLimit) {
        this.arjunRateLimit = Math.max(1, Math.min(arjunRateLimit, 10000));  // 1-10000 req/s
    }
    
    public Map<String, String> getArjunCustomHeaders() {
        return arjunCustomHeaders != null ? arjunCustomHeaders : new HashMap<>();
    }
    
    public void setArjunCustomHeaders(Map<String, String> arjunCustomHeaders) {
        this.arjunCustomHeaders = arjunCustomHeaders != null ? arjunCustomHeaders : new HashMap<>();
    }
    
    // ✅ 规则文件配置的Getters和Setters
    public boolean isUseExternalRuleFile() {
        return useExternalRuleFile;
    }
    
    public void setUseExternalRuleFile(boolean useExternalRuleFile) {
        this.useExternalRuleFile = useExternalRuleFile;
    }
    
    public String getRuleFilePath() {
        return ruleFilePath;
    }
    
    public void setRuleFilePath(String ruleFilePath) {
        this.ruleFilePath = ruleFilePath;
    }
    
    /**
     * ✅ 获取规则文件的完整路径
     * 
     * @return 规则文件路径，如果未设置则返回默认路径
     */
    public String getEffectiveRuleFilePath() {
        if (ruleFilePath != null && !ruleFilePath.trim().isEmpty()) {
            return ruleFilePath;
        }
        // 默认规则文件路径
        return System.getProperty("user.home") + "/.xprobe/rules.json";
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
        
        // ✅ 线程池配置
        copy.setScannerCoreThreads(this.scannerCoreThreads);
        copy.setScannerMaxThreads(this.scannerMaxThreads);
        copy.setScannerQueueSize(this.scannerQueueSize);
        copy.setScannerKeepAliveSeconds(this.scannerKeepAliveSeconds);
        
        // 代理池配置
        copy.setEnableProxyPool(this.enableProxyPool);
        copy.setProxyTimeout(this.proxyTimeout);
        copy.setMaxRetries(this.maxRetries);
        copy.setProxyList(new ArrayList<>(this.proxyList));
        
        // Java原生Arjun配置
        copy.setArjunEnabled(this.arjunEnabled);
        copy.setArjunChunkSize(this.arjunChunkSize);
        copy.setArjunTimeout(this.arjunTimeout);
        copy.setArjunCustomDictionary(new HashSet<>(this.arjunCustomDictionary));
        
        // ✅ 修复：Arjun高级配置（性能优化）
        copy.setArjunStableMode(this.arjunStableMode);
        copy.setArjunThreads(this.arjunThreads);
        copy.setArjunMaxRetries(this.arjunMaxRetries);
        copy.setArjunRateLimit(this.arjunRateLimit);
        copy.setArjunCustomHeaders(this.arjunCustomHeaders != null ? new HashMap<>(this.arjunCustomHeaders) : new HashMap<>());
        
        // Arjun实时模式配置
        copy.setArjunRealtimeInterval(this.arjunRealtimeInterval);
        copy.setArjunRealtimeThreshold(this.arjunRealtimeThreshold);
        
        // ✅ 规则文件配置
        copy.setUseExternalRuleFile(this.useExternalRuleFile);
        copy.setRuleFilePath(this.ruleFilePath);
        
        return copy;
    }
}

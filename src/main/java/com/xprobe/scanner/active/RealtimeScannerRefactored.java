package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.core.TaskScheduler;
import com.xprobe.scanner.active.arjun.ArjunService;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.models.RequestContext;
import com.xprobe.scanner.active.headers.ActiveProbeHeaderManager;
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.utils.BoundedCache;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * 实时扫描器（重构版） - 使用 ParameterCollector 和 ParameterManager
 * 
 * 核心职责：
 * 1. 被动收集参数（委托给 ParameterCollector）
 * 2. 管理全局参数（委托给 ParameterManager）
 * 3. 触发 Arjun 扫描（用户主动触发）
 * 4. 增量传递参数给 Arjun
 */
public class RealtimeScannerRefactored {
    private final MontoyaApi api;
    private final GlobalFilter globalFilter;
    private final ConfigurationManager configManager;  // ✅ 配置管理器
    private final ArjunService arjunService;  // ✅ 使用Java原生Arjun替代外部Python版本
    // ✅ 主动探测请求头管理（按主机维度 + 配置中心覆盖）
    private final ActiveProbeHeaderManager headerManager;
    private final com.xprobe.scanner.config.XProbeConfig xprobeConfig;
    
    // 使用新的参数收集器和管理器
    private final ParameterCollector parameterCollector;
    private final ParameterManager parameterManager;
    
    // ✅ P0修复: 被动扫描去重机制 - 使用有界缓存（FIFO）防止内存泄漏
    // 最多保存10万条记录，超过则自动淘汰最早插入的记录
    private final BoundedCache<String, Boolean> passiveScanProcessedKeys = new BoundedCache<>(100_000);
    
    // ✅ TaskScheduler引用（用于Arjun发现参数后触发漏洞扫描）
    private TaskScheduler taskScheduler;
    
    // ✅ OriginalResponseCache引用（用于缓存主动探测的原始响应，供被动扫描规则使用）
    private com.xprobe.scanner.core.OriginalResponseCache responseCache;
    
    // ✅ ActiveProbeTab引用（用于接口探测结果回调）
    private com.xprobe.scanner.ui.ActiveProbeTab activeProbeTab;
    
    // ✅ 智能触发机制（按主域名）
    private final Map<String, Long> lastArjunTriggerTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastParameterCount = new ConcurrentHashMap<>();
    private volatile int minParameterThreshold = 15;  // ✅ P1修复：volatile确保多线程可见性
    
    // ✅ 随机路径基准响应缓存（按具体Host+方法+ContentType缓存，避免不同子域共享同一基准）
    // ✅ 优化：使用双随机路径建立"不存在"的基准响应，更准确地区分泛解析、反射和真实接口
    private static class RandomPathBaseline {
        final String randomPath1;
        final String randomPath2;
        final int statusCode1;
        final int statusCode2;
        final String responseBody1;
        final String responseBody2;
        final long timestamp;
        final boolean isWildcard;  // 是否为泛解析（两个随机路径响应相同）
        final String baselineStatusCode;  // 基准状态码（如果两个随机路径相同）
        final String baselineResponseBody;  // 基准响应体（如果两个随机路径相同，去除反射后）
        
        RandomPathBaseline(String randomPath1, String randomPath2,
                          int statusCode1, int statusCode2,
                          String responseBody1, String responseBody2,
                          long timestamp, boolean isWildcard,
                          String baselineStatusCode, String baselineResponseBody) {
            this.randomPath1 = randomPath1;
            this.randomPath2 = randomPath2;
            this.statusCode1 = statusCode1;
            this.statusCode2 = statusCode2;
            this.responseBody1 = responseBody1;
            this.responseBody2 = responseBody2;
            this.timestamp = timestamp;
            this.isWildcard = isWildcard;
            this.baselineStatusCode = baselineStatusCode;
            this.baselineResponseBody = baselineResponseBody;
        }
    }
    private final Map<String, RandomPathBaseline> randomPathBaselineCache = new ConcurrentHashMap<>();
    private static final long RANDOM_PATH_CACHE_TTL = 300_000;  // 5分钟缓存有效期
    // ✅ 修复：用于同步建立基准的锁映射（每个cacheKey一个锁，避免全局阻塞）
    private final Map<String, Object> baselineLocks = new ConcurrentHashMap<>();
    
    // ✅ Arjun结果监听器（用于通知UI显示结果）
    private final List<ArjunResultListener> arjunResultListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int cooldownSeconds = 300;  // ✅ P1修复：volatile确保多线程可见性
    
    // ✅ 修复：主动探测总开关（控制Arjun触发）
    private volatile boolean arjunEnabled = false;
    
    // ✅ 修复：触发模式（手动 vs 实时）
    private volatile boolean isRealtimeMode = false;  // 默认手动模式
    
    // ✅ 新增：参数扫描挂起开关（用于"先接口后参数"期间阻断后台Arjun）
    private volatile boolean paramScanSuspended = false;
    public void suspendRealtimeParamScan(boolean suspend) {
        this.paramScanSuspended = suspend;
        api.logging().raiseDebugEvent("参数扫描挂起状态: " + suspend);
    }
    
    // ✅ 新增：接口探测策略标志（true=接口探测后做Arjun，false=直接Arjun）
    private volatile boolean requireInterfaceFirst = false;
    public void setRequireInterfaceFirst(boolean require) {
        this.requireInterfaceFirst = require;
        api.logging().raiseDebugEvent("接口探测策略: " + (require ? "接口探测后做Arjun" : "直接Arjun"));
    }
    
    // ✅ 新增：主动探测启用被动扫描规则标志（用于将主动探测结果传递给被动扫描规则）
    private volatile boolean enablePassiveScanRulesForActiveProbe = false;
    public void setEnablePassiveScanRulesForActiveProbe(boolean enable) {
        this.enablePassiveScanRulesForActiveProbe = enable;
        api.logging().raiseInfoEvent("主动探测被动扫描规则: " + (enable ? "已启用" : "已禁用"));
    }
    
    public RealtimeScannerRefactored(MontoyaApi api, ConfigurationManager configManager, 
                                    GlobalFilter globalFilter, LogModel logModel, 
                                    com.xprobe.scanner.config.XProbeConfig xprobeConfig) {
        this.api = api;
        this.globalFilter = globalFilter;
        this.configManager = configManager;  // ✅ 保存配置管理器
        
        // ✅ 从配置中初始化ArjunService
        com.xprobe.scanner.active.arjun.config.ArjunConfig arjunConfig = 
            new com.xprobe.scanner.active.arjun.config.ArjunConfig(
                xprobeConfig.getArjunChunkSize(), 
                false  // heuristic已禁用
            );
        arjunConfig.setTimeout(xprobeConfig.getArjunTimeout());
        arjunConfig.setEnabled(xprobeConfig.isArjunEnabled());
        
        // ✅ P0修复：传递xprobeConfig以启用高级配置（稳定模式、线程数、重试次数、速率限制）
        this.arjunService = new ArjunService(api, logModel, arjunConfig, xprobeConfig);
        
        // 应用用户自定义字典
        if (xprobeConfig.getArjunCustomDictionary() != null) {
            this.arjunService.setUserCustomDictionary(xprobeConfig.getArjunCustomDictionary());
        }
        
        // 初始化新的组件
        this.parameterCollector = new ParameterCollector(api);
        this.parameterManager = new ParameterManager(api);
        
        // ✅ 初始化主动探测请求头管理器（按主机维度 + 配置中心覆盖）
        this.headerManager = new ActiveProbeHeaderManager(api, xprobeConfig);
        this.xprobeConfig = xprobeConfig;
        
        api.logging().raiseInfoEvent("✅ 实时扫描器已初始化（Java原生Arjun + 参数收集器 + 请求头管理器）");
    }
    
    /**
     * ✅ 设置TaskScheduler引用（用于Arjun发现参数后触发扫描）
     */
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }
    
    /**
     * ✅ 设置OriginalResponseCache引用（用于缓存主动探测的原始响应）
     */
    public void setResponseCache(com.xprobe.scanner.core.OriginalResponseCache responseCache) {
        this.responseCache = responseCache;
    }
    
    /**
     * ✅ 设置ActiveProbeTab引用（用于接口探测结果回调）
     */
    public void setActiveProbeTab(com.xprobe.scanner.ui.ActiveProbeTab activeProbeTab) {
        this.activeProbeTab = activeProbeTab;
    }
    
    // ========== 被动收集参数 ==========
    
    /**
     * 处理新的 HTTP 请求（被动收集参数）
     * 
     * @param request HTTP 请求
     */
    /**
     * 处理HTTP响应，从中收集参数和关键词
     * 
     * @param request 原始请求
     * @param responseReceived HTTP响应事件
     */
    public void processResponse(HttpRequest request, burp.api.montoya.http.handler.HttpResponseReceived responseReceived) {
        try {
            String url = request.url();
            
            // 跳过 Arjun 触发的流量
            for (var header : request.headers()) {
                if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
                    return;
                }
            }
            
            // ✅ 修复：参数收集应该一直工作，只检查静态资源过滤，不检查黑白名单
            // 参数收集是基础功能，不应该被主动探测的过滤器限制
            if (globalFilter != null && globalFilter.isStaticResourceFilterEnabled()) {
                // 只检查静态资源，不检查黑白名单
                if (com.xprobe.scanner.utils.StaticResourceFilter.isStaticResource(url)) {
                    return;
                }
            }
            
            // ✅ 只收集响应参数（请求参数已在 processNewRequest 中收集）
            parameterCollector.collectFromResponse(request, responseReceived);
            
            // 可选：根据请求参数触发智能扫描
            // ✅ 修复：手动解析 URL，避免 URI 类无法处理未编码的特殊字符
            String host = null;
            String endpoint = null;
            try {
                URI uri = new URI(url);
                host = uri.getHost();
                endpoint = uri.getPath();
            } catch (Exception e) {
                // ✅ 如果 URI 解析失败（可能包含未编码的特殊字符），使用手动解析
                int schemeEnd = url.indexOf("://");
                if (schemeEnd != -1) {
                    int pathStart = url.indexOf('/', schemeEnd + 3);
                    String hostPort;
                    if (pathStart == -1) {
                        hostPort = url.substring(schemeEnd + 3);
                        endpoint = "/";
                    } else {
                        hostPort = url.substring(schemeEnd + 3, pathStart);
                        endpoint = url.substring(pathStart);
                        int queryIndex = endpoint.indexOf('?');
                        if (queryIndex > 0) {
                            endpoint = endpoint.substring(0, queryIndex);
                        }
                    }
                    int portStart = hostPort.indexOf(':');
                    host = portStart == -1 ? hostPort : hostPort.substring(0, portStart);
                }
            }
            // ✅ 修复：如果 host 为 null，跳过处理
            if (host == null || host.isEmpty() || "null".equals(host)) {
                return;
            }
            
            // ✅ 新增：更新最新请求头缓存
            if (endpoint != null && !endpoint.isEmpty()) {
                headerManager.updateFromRequest(host, request);
            }
            
            String mainDomain = extractMainDomain(host);
            // ✅ 修复：如果 mainDomain 为 null，跳过处理
            if (mainDomain == null || mainDomain.isEmpty()) {
                return;
            }
            api.logging().raiseDebugEvent("处理主域名: " + mainDomain + " 的响应");
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("处理响应时出错: " + e.getMessage());
        }
    }
    
    public void processNewRequest(HttpRequest request) {
        try {
            String url = request.url();
            
            // 跳过 Arjun 触发的流量
            for (var header : request.headers()) {
                if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
                    return;
                }
            }
            
            // ✅ 修复：参数收集应该一直工作，只检查静态资源过滤，不检查黑白名单
            // 参数收集是基础功能，不应该被主动探测的过滤器限制
            // 只检查静态资源，让参数收集更宽松
            if (globalFilter != null && globalFilter.isStaticResourceFilterEnabled()) {
                // 只检查静态资源，不检查黑白名单
                if (com.xprobe.scanner.utils.StaticResourceFilter.isStaticResource(url)) {
                    return;
                }
            }
            
            // 委托给参数收集器（参数收集器内部也会检查静态资源，这里是双重保险）
            boolean hasNewParameters = parameterCollector.collectFromRequest(request);
            
            // ✅ 新增：更新最新请求头缓存（用于后续的接口探测和参数探测）
            try {
                String host = null;
                String endpoint = null;
                try {
                    URI uri = new URI(url);
                    host = uri.getHost();
                    endpoint = uri.getPath();
                } catch (Exception e) {
                    // 手动解析
                    int schemeEnd = url.indexOf("://");
                    if (schemeEnd != -1) {
                        int pathStart = url.indexOf('/', schemeEnd + 3);
                        if (pathStart != -1) {
                            String hostPort = url.substring(schemeEnd + 3, pathStart);
                            int portStart = hostPort.indexOf(':');
                            host = portStart == -1 ? hostPort : hostPort.substring(0, portStart);
                            endpoint = url.substring(pathStart);
                            int queryIndex = endpoint.indexOf('?');
                            if (queryIndex > 0) {
                                endpoint = endpoint.substring(0, queryIndex);
                            }
                        }
                    }
                }
                
                if (host != null && !host.isEmpty() && endpoint != null && !endpoint.isEmpty()) {
                    headerManager.updateFromRequest(host, request);
                }
            } catch (Exception e) {
                api.logging().raiseDebugEvent("更新请求头缓存失败: " + e.getMessage());
            }
            
            if (hasNewParameters) {
                // 记录统计信息
                ParameterCollector.CollectorStatistics stats = parameterCollector.getStatistics();
                api.logging().raiseDebugEvent("收集器统计: " + stats);
                
                // ✅ 智能触发Arjun（基于阈值和冷却时间）
                checkAndAutoTriggerArjun(request);
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("处理新请求时出错: " + e.getMessage());
        }
    }
    
    // ========== 请求头管理机制 ==========
    
    /**
     * ✅ 新增：构建请求头缓存key
     * @param host 主机名
     * @param endpoint 端点路径
     * @return 缓存key
     */
    /**
     * ✅ 新增：更新最新请求头缓存
     * 在被动收集时调用，记录最新的请求头状态
     * @param host 主机名
     * @param endpoint 端点路径
     * @param request HTTP请求
     */
        // ===== 值规范化与占位控制 =====
    private String getPlaceholder() {
        if (xprobeConfig != null) {
            return xprobeConfig.getParamValuePlaceholder();
        }
        // 使用配置默认值，避免硬编码
        return com.xprobe.scanner.config.XProbeConfig.DEFAULT_PARAM_VALUE_PLACEHOLDER;
    }

    private int getMaxParamValueLength() {
        if (xprobeConfig != null) {
            return xprobeConfig.getParamValueMaxLength();
        }
        // 使用配置默认值，避免硬编码
        return com.xprobe.scanner.config.XProbeConfig.DEFAULT_PARAM_VALUE_MAX_LENGTH;
    }

    private String normalizeValue(String v) {
        if (v == null) return getPlaceholder();
        return v.length() > getMaxParamValueLength() ? getPlaceholder() : v;
    }

    // ========== 智能触发机制 ==========
    
    /**
     * 检查并自动触发Arjun（基于阈值和冷却时间）
     * ✅ 只在实时模式下执行
     */
    private void checkAndAutoTriggerArjun(HttpRequest request) {
        try {
            // ✅ 修复：检查主开关和模式
            if (!arjunEnabled || !isRealtimeMode) {
                return;  // 主开关关闭或非实时模式，不自动触发
            }
            
            String mainDomain = extractMainDomain(request);
            long currentTime = System.currentTimeMillis();
            
            // 1. 检查冷却时间（5分钟）
            Long lastTriggerTime = lastArjunTriggerTime.get(mainDomain);
            if (lastTriggerTime != null) {
                long elapsedSeconds = (currentTime - lastTriggerTime) / 1000;
                if (elapsedSeconds < cooldownSeconds) {
                    api.logging().raiseDebugEvent(String.format(
                        "主域名 %s 在冷却期内，剩余 %d 秒",
                        mainDomain, cooldownSeconds - elapsedSeconds
                    ));
                    return;
                }
            }
            
            // 2. 计算未扫描参数数量
            Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
            Set<ParameterCollector.EndpointKey> endpoints = 
                parameterCollector.getEndpointKeysForMainDomain(mainDomain);
            
            int totalUnscannedParams = 0;
            for (ParameterCollector.EndpointKey epKey : endpoints) {
                Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                    epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
                );
                totalUnscannedParams += incrementalParams.size();
            }
            
            // 3. 检查是否达到阈值（15个参数）
            if (totalUnscannedParams >= minParameterThreshold) {
                api.logging().raiseInfoEvent(String.format(
                    "✅ [智能触发] 主域名 %s 达到参数阈值 (未扫描: %d / 阈值: %d)",
                    mainDomain, totalUnscannedParams, minParameterThreshold
                ));
                
                // 异步触发Arjun（避免阻塞）
                CompletableFuture.runAsync(() -> {
                    triggerArjunForMainDomain(mainDomain);
                });
                
                // 更新触发时间和参数计数
                lastArjunTriggerTime.put(mainDomain, currentTime);
                lastParameterCount.put(mainDomain, collectedParams.size());
            } else {
                api.logging().raiseDebugEvent(String.format(
                    "主域名 %s 参数未达阈值 (未扫描: %d / 阈值: %d)",
                    mainDomain, totalUnscannedParams, minParameterThreshold
                ));
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("智能触发Arjun时出错: " + e.getMessage());
        }
    }
    
    /**
     * 定时检查所有主域名（兜底机制，只在有新参数时触发）
     * ✅ 只在实时模式下执行
     */
    public void periodicArjunCheck() {
        try {
            // ✅ 修复：检查主开关和模式
            if (!arjunEnabled || !isRealtimeMode) {
                return;  // 主开关关闭或非实时模式，不定时触发
            }
            
            Set<String> allMainDomains = parameterCollector.getAllMainDomains();
            long currentTime = System.currentTimeMillis();
            
            api.logging().raiseInfoEvent(String.format(
                "🔍 定时检查Arjun触发条件 (%d 个主域名)",
                allMainDomains.size()
            ));
            
            for (String mainDomain : allMainDomains) {
                // 获取当前参数数量
                Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
                int currentParamCount = collectedParams.size();
                
                // 获取上次记录的参数数量
                Integer lastCount = lastParameterCount.get(mainDomain);
                
                // ✅ 只在有新参数时触发
                if (lastCount == null || currentParamCount > lastCount) {
                    // 检查是否有未扫描的参数
                    Set<ParameterCollector.EndpointKey> endpoints = 
                        parameterCollector.getEndpointKeysForMainDomain(mainDomain);
                    
                    int totalUnscannedParams = 0;
                    for (ParameterCollector.EndpointKey epKey : endpoints) {
                        Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
                        );
                        totalUnscannedParams += incrementalParams.size();
                    }
                    
                    if (totalUnscannedParams > 0) {
                        api.logging().raiseInfoEvent(String.format(
                            "✅ [定时触发] 主域名 %s 有新参数 (当前: %d, 上次: %d, 未扫描: %d)",
                            mainDomain, currentParamCount, lastCount == null ? 0 : lastCount, totalUnscannedParams
                        ));
                        
                        CompletableFuture.runAsync(() -> {
                            triggerArjunForMainDomain(mainDomain);
                        });
                        
                        // 更新触发时间和参数计数
                        lastArjunTriggerTime.put(mainDomain, currentTime);
                        lastParameterCount.put(mainDomain, currentParamCount);
                    }
                } else {
                    api.logging().raiseDebugEvent(String.format(
                        "主域名 %s 无新参数，跳过触发 (参数数: %d)",
                        mainDomain, currentParamCount
                    ));
                }
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("定时检查Arjun时出错: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 为指定子域名触发Arjun扫描（使用主域名下所有接口和参数）
     * 
     * 核心逻辑：
     * 1. 获取主域名下所有收集的接口和参数
     * 2. 将请求的目标host改为选中的子域名
     * 3. 对所有接口进行参数探测
     * 
     * @param mainDomain 主域名
     * @param targetHost 目标子域名（探测的目标host）
     */
    public void triggerArjunForHost(String mainDomain, String targetHost) {
        // ✅ 默认使用主域下所有子域的接口和参数（保持向后兼容）
        triggerArjunForHost(mainDomain, targetHost, false, false);
    }
    
    /**
     * ✅ 为指定子域名触发Arjun扫描（支持参数范围选择）
     * 
     * @param mainDomain 主域名
     * @param targetHost 目标子域名（探测的目标host）
     * @param useOnlyHostParameters 如果为true，只使用该子域的参数；如果为false，使用主域下所有子域的参数
     */
    public void triggerArjunForHost(String mainDomain, String targetHost, boolean useOnlyHostParameters) {
        // ✅ 保持向后兼容：接口范围默认跟随参数范围
        triggerArjunForHost(mainDomain, targetHost, useOnlyHostParameters, useOnlyHostParameters);
    }
    
    /**
     * ✅ 为指定子域名触发Arjun扫描（支持接口范围和参数范围独立选择）
     * 
     * @param mainDomain 主域名
     * @param targetHost 目标子域名（探测的目标host）
     * @param useOnlyHostEndpoints 如果为true，只使用该子域的接口；如果为false，使用主域下所有子域的接口
     * @param useOnlyHostParameters 如果为true，只使用该子域的参数；如果为false，使用主域下所有子域的参数
     */
    public void triggerArjunForHost(String mainDomain, String targetHost, 
                                    boolean useOnlyHostEndpoints, boolean useOnlyHostParameters) {
        // ✅ 向后兼容：获取数据后调用性能优化版本
        Set<ParameterCollector.EndpointKey> allEndpointKeys = null;
        Set<String> allParameters = null;
        Set<String> keywords = null;
        
        if (!useOnlyHostEndpoints) {
            allEndpointKeys = parameterCollector.getEndpointKeysForMainDomain(mainDomain);
        }
        
        if (!useOnlyHostParameters) {
            allParameters = parameterCollector.getParametersForMainDomain(mainDomain);
            if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
            }
        }
        
        triggerArjunForHost(mainDomain, targetHost, useOnlyHostEndpoints, useOnlyHostParameters,
            allEndpointKeys, allParameters, keywords);
    }
    
    /**
     * ✅ 为指定子域名触发Arjun扫描（性能优化版本：接收已缓存的数据，避免重复获取）
     * 
     * @param mainDomain 主域名
     * @param targetHost 目标子域名（探测的目标host）
     * @param useOnlyHostEndpoints 如果为true，只使用该子域的接口；如果为false，使用主域下所有子域的接口
     * @param useOnlyHostParameters 如果为true，只使用该子域的参数；如果为false，使用主域下所有子域的参数
     * @param allEndpointKeys 主域下所有接口（已缓存，如果为null则内部获取）
     * @param allParameters 主域下所有参数（已缓存，如果为null则内部获取）
     * @param keywords 主域下所有关键词（已缓存，如果为null则内部获取）
     */
    public void triggerArjunForHost(String mainDomain, String targetHost, 
                                    boolean useOnlyHostEndpoints, boolean useOnlyHostParameters,
                                    Set<ParameterCollector.EndpointKey> allEndpointKeys,
                                    Set<String> allParameters, Set<String> keywords) {
        try {
            // ✅ 修复：检查主动探测总开关
            if (!arjunEnabled) {
                api.logging().raiseDebugEvent("主动探测已禁用，跳过Arjun扫描: " + targetHost);
                return;
            }
            
            // ✅ 根据参数范围选择获取参数
            Set<String> collectedParams;
            if (useOnlyHostParameters) {
                // ✅ 仅使用该子域的参数
                collectedParams = parameterCollector.getParametersForHost(targetHost);
                api.logging().raiseInfoEvent(String.format(
                    "使用仅选中子域参数模式: 子域名 %s, 参数数=%d",
                    targetHost, collectedParams.size()
                ));
            } else {
                // ✅ 使用主域下所有子域的参数合集（包含主域本身）
                if (allParameters != null) {
                    collectedParams = new HashSet<>(allParameters);
                    api.logging().raiseInfoEvent(String.format(
                        "使用主域所有子域参数合集（缓存）: 主域名 %s, 子域名 %s, 参数数=%d",
                        mainDomain, targetHost, collectedParams.size()
                    ));
                } else {
                    collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
                    api.logging().raiseInfoEvent(String.format(
                        "使用主域所有子域参数合集（实时获取）: 主域名 %s, 子域名 %s, 参数数=%d",
                        mainDomain, targetHost, collectedParams.size()
                    ));
                }
            }
            
            // 如果启用了关键词收集，将关键词也加入参数列表
            if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                if (keywords != null) {
                    collectedParams.addAll(keywords);
                } else {
                    Set<String> keywordsFromCollector = parameterCollector.getKeywordsForMainDomain(mainDomain);
                    collectedParams.addAll(keywordsFromCollector);
                }
            }
            
            // ✅ 获取主域名下所有收集的接口（使用缓存或获取）
            Set<ParameterCollector.EndpointKey> allEndpointKeysToUse;
            if (allEndpointKeys != null) {
                allEndpointKeysToUse = allEndpointKeys;
            } else {
                allEndpointKeysToUse = parameterCollector.getEndpointKeysForMainDomain(mainDomain);
            }
            
            // ✅ 根据接口范围选择过滤接口
            Set<ParameterCollector.EndpointKey> endpointKeysToScan;
            if (useOnlyHostEndpoints) {
                // 只使用该子域的接口
                endpointKeysToScan = new HashSet<>();
                for (ParameterCollector.EndpointKey epKey : allEndpointKeysToUse) {
                    if (epKey.host.equals(targetHost)) {
                        endpointKeysToScan.add(epKey);
                    }
                }
                api.logging().raiseInfoEvent(String.format(
                    "🔍 对子域名 %s 进行Arjun扫描（接口范围: 仅选中子域, 参数范围: %s）: 参数数=%d, 接口数=%d",
                    targetHost, useOnlyHostParameters ? "仅选中子域" : "主域所有子域", 
                    collectedParams.size(), endpointKeysToScan.size()
                ));
            } else {
                // 使用主域下所有子域的接口
                endpointKeysToScan = allEndpointKeysToUse;
                api.logging().raiseInfoEvent(String.format(
                    "🔍 对子域名 %s 进行Arjun扫描（接口范围: 主域所有子域, 参数范围: %s）: 参数数=%d, 接口数=%d",
                    targetHost, useOnlyHostParameters ? "仅选中子域" : "主域所有子域",
                    collectedParams.size(), endpointKeysToScan.size()
                ));
            }
            
            // ✅ 检查接口和参数是否为空
            if (endpointKeysToScan.isEmpty()) {
                api.logging().raiseInfoEvent(String.format(
                    "⚠️ 子域名 %s 没有可用的接口，跳过 Arjun 扫描",
                    targetHost
                ));
                return;
            }
            
            if (collectedParams.isEmpty()) {
                // ✅ 详细诊断：为什么参数为空
                api.logging().raiseInfoEvent(String.format(
                    "⚠️ 子域名 %s 没有可用的参数，跳过 Arjun 扫描\n" +
                    "   诊断信息：\n" +
                    "   - 参数范围: %s\n" +
                    "   - 主域名: %s\n" +
                    "   - 是否使用缓存: %s\n" +
                    "   - 建议：检查参数收集数据或尝试清空Arjun缓存后重新收集",
                    targetHost,
                    useOnlyHostParameters ? "仅选中子域" : "主域所有子域",
                    mainDomain,
                    allParameters != null ? "是" : "否"
                ));
                return;
            }
            
            int scanned = 0;
            int skippedEmptyParams = 0;
            int skippedNoTemplate = 0;
            int skippedModifyFailed = 0;
            
            api.logging().raiseInfoEvent(String.format(
                "🔍 开始扫描接口: 接口数=%d, 参数数=%d",
                endpointKeysToScan.size(), collectedParams.size()
            ));
            
            for (ParameterCollector.EndpointKey epKey : endpointKeysToScan) {
                // ✅ 检查是否已停止（在循环开始时检查，避免处理已停止的任务）
                if (!arjunEnabled) {
                    api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断扫描: " + targetHost);
                    break;
                }
                
                // ✅ 修复：如果执行策略是接口探测成功后做 Arjun 探测，只对接口探测成功的接口做 Arjun 探测
                // 只有在 requireInterfaceFirst=true 时才检查接口存在性
                if (requireInterfaceFirst) {
                    String endpointKey = buildEndpointKey(epKey.method, targetHost, epKey.contentType, epKey.endpoint);
                    Boolean endpointExists = endpointExistenceCache.get(endpointKey);
                    if (endpointExists != null && !endpointExists) {
                        api.logging().raiseDebugEvent(String.format(
                            "⚠️ 跳过接口 %s %s (%s) %s：接口探测结果为不存在",
                            epKey.method, targetHost, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                        ));
                        continue;
                    }
                    // 如果 endpointExists == null，说明还没有探测过，跳过（接口探测策略要求先探测接口）
                    if (endpointExists == null) {
                        api.logging().raiseDebugEvent(String.format(
                            "⚠️ 跳过接口 %s %s (%s) %s：接口探测策略要求先探测接口，但该接口尚未探测",
                            epKey.method, targetHost, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                        ));
                        continue;
                    }
                }
                // 如果 requireInterfaceFirst=false，直接进行Arjun扫描，不检查接口存在性
                
                api.logging().raiseInfoEvent(String.format(
                    "处理接口: %s %s (%s) %s",
                    epKey.method, targetHost, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                ));
                
                // ✅ 获取原始请求模板
                HttpRequest originalTemplate = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                if (originalTemplate == null) {
                    skippedNoTemplate++;
                    api.logging().raiseInfoEvent(String.format(
                        "⚠️ 跳过接口 %s %s (%s) %s：未找到请求模板",
                        epKey.method, targetHost, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                    ));
                    continue;
                }
                
                // ✅ 定义三形态组合（所有模板都尝试三形态）
                String[][] combinations = new String[][]{
                    {"GET", null},
                    {"POST", "application/x-www-form-urlencoded"},
                    {"POST", "application/json"}
                };
                api.logging().raiseInfoEvent(String.format(
                    "✅ 模板将尝试三形态: %s %s (%s) %s",
                    epKey.method, targetHost, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                ));
                
                // ✅ 对每个形态进行扫描
                for (String[] combo : combinations) {
                    // ✅ 再次检查是否已停止（在内层循环中也检查）
                    if (!arjunEnabled) {
                        api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断形态扫描");
                        break;
                    }
                    String method = combo[0];
                    String contentType = combo[1];
                    String contentTypeForKey = ("GET".equalsIgnoreCase(method)) ? null : contentType;
                    
                    // ✅ 计算增量参数（未扫描过的）
                    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                        method, targetHost, contentTypeForKey, epKey.endpoint, collectedParams
                    );
                    
                    // ✅ 即使无增量参数，也回退到当前参数来源范围的集合
                    if (incrementalParams.isEmpty()) {
                        api.logging().raiseDebugEvent(String.format(
                            "无增量参数，回退到当前参数来源集合: %s %s (%s) %s (参数数=%d)",
                            method, targetHost, (contentTypeForKey != null ? contentTypeForKey : "N/A"), epKey.endpoint, collectedParams.size()
                        ));
                        incrementalParams = new HashSet<>(collectedParams);
                    }
                    
                    if (incrementalParams.isEmpty()) {
                        skippedEmptyParams++;
                        api.logging().raiseInfoEvent(String.format(
                            "⚠️ 跳过接口 %s %s (%s) %s：无可用参数",
                            method, targetHost, contentTypeForKey != null ? contentTypeForKey : "N/A", epKey.endpoint
                        ));
                        continue;
                    }
                    
                    // ✅ 构建完整URL（保留原始URL参数，用于GET请求）
                    String fullUrl = buildUrlFromTemplate(originalTemplate, targetHost, stripQuery(epKey.endpoint));
                    if (fullUrl == null) {
                        skippedModifyFailed++;
                        api.logging().raiseInfoEvent(String.format(
                            "⚠️ 跳过接口 %s %s (%s) %s：构建URL失败",
                            method, targetHost, contentTypeForKey != null ? contentTypeForKey : "N/A", epKey.endpoint
                        ));
                        continue;
                    }
                    
                    // ✅ 构建带参数的请求（形态转换：原始参数+新参数都按目标形态放置）
                    java.util.Set<String> sanitizedParams = sanitizeParamNames(collectedParams);
                    HttpRequest baseRequest = buildRequestWithParamsAndTransform(fullUrl, method, contentType, originalTemplate, sanitizedParams);
                    if (baseRequest == null) {
                        skippedModifyFailed++;
                        continue;
                    }
                    
                    // ✅ 修改host为目标子域名
                    HttpRequest modifiedRequest = modifyRequestHost(baseRequest, targetHost);
                    if (modifiedRequest == null) {
                        skippedModifyFailed++;
                        continue;
                    }
                    
                    // ✅ 复制关键会话头（Cookie/Authorization等）
                    modifiedRequest = copyCriticalSessionHeaders(originalTemplate, modifiedRequest);
                    
                    // ✅ 新增：应用最新请求头（确保使用最新的会话信息，覆盖旧的请求头）
                    modifiedRequest = headerManager.applyTo(modifiedRequest, targetHost);
                    
                    final HttpRequest finalRequest = modifiedRequest;
                    final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                    final String finalMethod = method;
                    final String finalContentType = contentTypeForKey;
                    final String finalDisplayContentType = contentTypeForKey != null ? contentTypeForKey : "N/A";
                    
                    // ✅ 检查 arjunService 是否可用
                    if (arjunService == null) {
                        api.logging().raiseErrorEvent("❌ arjunService 为 null，无法执行 Arjun 扫描");
                        continue;
                    }
                    
                    api.logging().raiseInfoEvent(String.format(
                        "🚀 调用 Arjun 扫描: %s %s (%s) %s, 参数数=%d",
                        finalMethod, targetHost, finalDisplayContentType, epKey.endpoint,
                        finalIncrementalParams.size()
                    ));
                    
                    // 异步调用 Arjun（先在表格插入“进行中”占位）
                    {
                        String paramTypeProgress = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                        notifyArjunProgress(mainDomain, targetHost, epKey.endpoint, paramTypeProgress);
                    }
                    arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                        // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                        long timestamp = System.currentTimeMillis();
                        
                        if (result.isSuccess()) {
                            String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                            
                            if (!result.getFoundParameters().isEmpty()) {
                                api.logging().raiseInfoEvent(String.format(
                                    "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                    result.getFoundParameters().size(),
                                    finalMethod, targetHost, finalContentType, epKey.endpoint, 
                                    result.getFoundParameters()
                                ));
                                
                                // ✅ 通知UI显示结果（传入立即生成的时间戳）
                                notifyArjunResult(mainDomain, targetHost, epKey.endpoint, result.getFoundParameters(), paramType, timestamp);
                                
                                // ✅ 将发现的参数传递给漏洞扫描器
                                triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                            } else {
                                api.logging().raiseDebugEvent(String.format(
                                    "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                    finalMethod, targetHost, finalContentType, epKey.endpoint
                                ));
                                notifyArjunResult(mainDomain, targetHost, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                            }
                            
                            parameterManager.markParametersAsScanned(
                                finalMethod, targetHost, finalContentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                        } else {
                            // ✅ 修复：失败时也通知UI，显示扫描失败的结果
                            String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                            notifyArjunResult(mainDomain, targetHost, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                            
                            parameterManager.markParametersAsScanned(
                                finalMethod, targetHost, finalContentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                        }
                    }).exceptionally(ex -> {
                        // ✅ 修复：异常时也立即生成时间戳
                        long timestamp = System.currentTimeMillis();
                        api.logging().raiseErrorEvent("Arjun扫描异常: " + ex.getMessage());
                        
                        // ✅ 修复：异常时也通知UI，显示扫描异常的结果
                        String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                        notifyArjunResult(mainDomain, targetHost, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                        
                        parameterManager.markParametersAsScanned(
                            finalMethod, targetHost, finalContentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                        return null;
                    });
                    
                    scanned++;
                }
            }
            
            // ✅ 详细的统计日志
            if (scanned > 0) {
                api.logging().raiseInfoEvent(String.format(
                    "✅ 子域名 %s Arjun扫描完成: 扫描了 %d 个接口, 跳过 %d 个（参数已扫描: %d, 无模板: %d, 修改失败: %d）",
                    targetHost, scanned, skippedEmptyParams + skippedNoTemplate + skippedModifyFailed,
                    skippedEmptyParams, skippedNoTemplate, skippedModifyFailed
                ));
            } else {
                api.logging().raiseInfoEvent(String.format(
                    "⚠️ 子域名 %s Arjun扫描: 没有发送任何请求（跳过 %d 个接口：参数已扫描: %d, 无模板: %d, 修改失败: %d）",
                    targetHost, skippedEmptyParams + skippedNoTemplate + skippedModifyFailed,
                    skippedEmptyParams, skippedNoTemplate, skippedModifyFailed
                ));
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发子域名Arjun扫描时出错: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 修改请求的host为目标子域名（克隆方式）
     * 仅替换 HttpService 与 Host 头，保留原始 headers/body/Cookie/Authorization，避免会话丢失
     */
    private HttpRequest modifyRequestHost(HttpRequest originalRequest, String targetHost) {
        try {
            burp.api.montoya.http.HttpService originalService = originalRequest.httpService();
            boolean isSecure = originalService.secure();
            int originalPort = originalService.port();

            // 规范化 host：HttpService 使用裸 host；Host 头的 IPv6 使用 [addr]
            String rawHost = targetHost;
            if (rawHost.startsWith("[") && rawHost.endsWith("]")) {
                rawHost = rawHost.substring(1, rawHost.length() - 1);
            }

            // 计算有效端口
            int effectivePort = originalPort;
            if (effectivePort <= 0) {
                effectivePort = isSecure ? 443 : 80;
            }

            // 替换服务
            burp.api.montoya.http.HttpService newService = burp.api.montoya.http.HttpService.httpService(
                rawHost,
                effectivePort,
                isSecure
            );
            HttpRequest newReq = originalRequest.withService(newService);

            // 设置 Host 头
            String hostHeaderHost = rawHost.contains(":") ? "[" + rawHost + "]" : rawHost;
            boolean defaultPort = (isSecure && effectivePort == 443) || (!isSecure && effectivePort == 80);
            String hostHeaderValue = defaultPort ? hostHeaderHost : hostHeaderHost + ":" + effectivePort;

            boolean hasHost = false;
            for (var h : newReq.headers()) {
                if ("Host".equalsIgnoreCase(h.name())) { hasHost = true; break; }
            }
            if (hasHost) {
                newReq = newReq.withUpdatedHeader("Host", hostHeaderValue);
            } else {
                newReq = newReq.withAddedHeader("Host", hostHeaderValue);
            }

            return newReq;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("修改请求host失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * ✅ 为指定主域名触发Arjun扫描（包含所有子域名）
     * @param mainDomain 主域名
     */
    public void triggerArjunForMainDomain(String mainDomain) {
        try {
            // ✅ 修复：检查主动探测总开关
            if (!arjunEnabled) {
                api.logging().raiseDebugEvent("主动探测已禁用，跳过Arjun扫描: " + mainDomain);
                return;
            }
            
            Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
            Set<ParameterCollector.EndpointKey> endpointKeys = 
                parameterCollector.getEndpointKeysForMainDomain(mainDomain);
            
            api.logging().raiseInfoEvent(String.format(
                "🔍 触发Arjun扫描: 主域名=%s, 参数数=%d, 接口数=%d",
                mainDomain, collectedParams.size(), endpointKeys.size()
            ));
            
            int scanned = 0;
            for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                // ✅ 计算增量参数（新参数会触发已探测过的端点重新扫描）
                Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                    epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
                );
                
                if (incrementalParams.isEmpty()) {
                    continue;
                }
                
                HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                if (templateRequest == null) {
                    continue;
                }
                
                final HttpRequest finalRequest = templateRequest;
                final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                // 秒显：先插入“参数探测中”占位
                {
                    String paramTypeProgress = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                    notifyArjunProgress(mainDomain, epKey.host, epKey.endpoint, paramTypeProgress);
                }
                
                // 异步调用 Arjun
                arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                    // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                    long timestamp = System.currentTimeMillis();
                    
                    if (result.isSuccess()) {
                        // ✅ 优化日志：区分找到参数和未找到参数的情况
                        String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                        
                        if (!result.getFoundParameters().isEmpty()) {
                            api.logging().raiseInfoEvent(String.format(
                                "✅ Arjun 发现 %d 个参数: %s - %s",
                                result.getFoundParameters().size(),
                                epKey, result.getFoundParameters()
                            ));
                            
                            // ✅ 通知UI显示结果（传入立即生成的时间戳）
                            notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType, timestamp);
                            
                            triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                        } else {
                            // ✅ 修复：即使没有发现参数，也添加到表格中
                            api.logging().raiseDebugEvent(String.format(
                                "Arjun 扫描完成，未发现隐藏参数: %s",
                                epKey
                            ));
                            notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                        }
                        parameterManager.markParametersAsScanned(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    } else {
                        // ✅ 修复：失败时也立即生成时间戳并通知UI
                        String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                        notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                        
                        parameterManager.markParametersAsScanned(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    }
                }).exceptionally(ex -> {
                    // ✅ 修复：异常时也立即生成时间戳
                    long timestamp = System.currentTimeMillis();
                    // ✅ P0修复：添加异常处理，避免无限重试
                    api.logging().raiseErrorEvent("Arjun扫描异常: " + ex.getMessage());
                    
                    String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                    
                    parameterManager.markParametersAsScanned(
                        epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                        finalIncrementalParams
                    );
                    return null;
                });
                
                scanned++;
            }
            
            api.logging().raiseInfoEvent(String.format(
                "✅ 主域名 %s Arjun扫描完成: 扫描了 %d 个接口",
                mainDomain, scanned
            ));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发主域名Arjun扫描时出错: " + e.getMessage());
        }
    }
    
    /**
     * 提取主域名（从HttpRequest）
     * - 如果是IP地址，返回完整IP
     * - 如果是域名，返回主域名（倒数第二级+顶级域名）
     */
    private String extractMainDomain(HttpRequest request) {
        try {
            URI uri = new URI(request.url());
            String host = uri.getHost();
            
            // ✅ 检测是否为IP地址（IPv4）
            if (host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                return host;  // IP地址直接返回完整IP
            }
            
            // ✅ IPv6地址也直接返回
            if (host.contains(":")) {
                return host;
            }
            
            // ✅ 域名：提取主域名
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) {
            return request.url();
        }
    }
    
    /**
     * 设置参数阈值
     */
    public void setMinParameterThreshold(int threshold) {
        this.minParameterThreshold = threshold;
        api.logging().raiseInfoEvent("设置Arjun参数阈值: " + threshold);
    }
    
    /**
     * 设置冷却时间（秒）
     */
    public void setCooldownSeconds(int seconds) {
        this.cooldownSeconds = seconds;
        api.logging().raiseInfoEvent("设置Arjun冷却时间: " + seconds + "秒");
    }
    
    /**
     * 获取冷却时间（秒）
     */
    public int getCooldownSeconds() {
        return this.cooldownSeconds;
    }
    
    // ========== 主动 Arjun 扫描 ==========
    
    /**
     * 手动触发 Arjun 参数探测（从 SiteMap 历史流量）
     * 
     * 核心逻辑：
     * 1. 从 SiteMap 获取所有历史请求
     * 2. 应用全局黑白名单过滤
     * 3. 按主域名分组
     * 4. 对每个接口，计算增量参数（未扫描过的）
     * 5. 只有增量参数时才调用 Arjun
     */
    public void triggerManualArjunScan() {
        try {
            // ✅ 修复：检查主动探测总开关
            if (!arjunEnabled) {
                api.logging().raiseInfoEvent("主动探测已禁用，跳过Arjun扫描");
                return;
            }
            
            api.logging().raiseInfoEvent("从 SiteMap 历史流量触发 Arjun 扫描...");
            
            CompletableFuture.runAsync(() -> {
                try {
                    performIncrementalArjunScan();
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("Arjun 扫描失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发 Arjun 扫描时出错: " + e.getMessage());
        }
    }
    
    /**
     * 从 Proxy 实时流量触发 Arjun 扫描
     * 
     * 核心逻辑：
     * 1. 使用 ParameterCollector 中已收集的参数
     * 2. 不从 SiteMap 读取，而是直接使用实时收集的数据
     * 3. 适合实时监听模式
     */
    public void triggerArjunScanFromProxy() {
        try {
            // ✅ 修复：检查主动探测总开关
            if (!arjunEnabled) {
                api.logging().raiseInfoEvent("主动探测已禁用，跳过Arjun扫描");
                return;
            }
            
            api.logging().raiseInfoEvent("从 Proxy 实时流量触发 Arjun 扫描...");
            
            CompletableFuture.runAsync(() -> {
                try {
                    // 使用已收集的参数进行扫描（不读取SiteMap）
                    performIncrementalArjunScanFromCollectedData();
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("Proxy Arjun 扫描失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发 Proxy Arjun 扫描时出错: " + e.getMessage());
        }
    }
    
    public void triggerManualEndpointScan(String url) {
        triggerManualEndpointScan(url, true);
    }
    
    /**
     * 对手动添加的端点进行 Arjun 扫描
     * 注意：手动添加的端点会尝试所有 method 和 contentType 组合
     * 
     * @param url 手动添加的 URL
     * @param runArjun 是否执行Arjun参数探测
     * @param interfaceDiscoveryFirst 是否先探测接口再参数探测（仅当runArjun=true时有效）
     */
    public void triggerManualEndpointScan(String url, boolean runArjun, boolean interfaceDiscoveryFirst) {
        try {
            // 兼容旧接口：忽略返回的Future
            triggerManualEndpointScanAsync(url, runArjun, interfaceDiscoveryFirst);
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发手动端点 Arjun 扫描时出错: " + e.getMessage());
        }
    }

    /**
     * 新增：异步触发手动端点扫描，并返回可等待的Future
     */
    public CompletableFuture<Void> triggerManualEndpointScanAsync(String url, boolean runArjun, boolean interfaceDiscoveryFirst) {
        return triggerManualEndpointScanAsync(url, runArjun, interfaceDiscoveryFirst, false);
    }

    /**
     * 带参数范围控制的手动端点扫描（useOnlyHostParameters=true 表示仅使用目标子域的参数）
     */
    public CompletableFuture<Void> triggerManualEndpointScanAsync(String url, boolean runArjun, boolean interfaceDiscoveryFirst, boolean useOnlyHostParameters) {
        return triggerManualEndpointScanAsync(url, runArjun, interfaceDiscoveryFirst, useOnlyHostParameters, null);
    }
    
    /**
     * 带参数范围控制和原始请求模板的手动端点扫描
     * @param originalTemplate 原始请求模板（可选），如果提供则保留请求头
     */
    public CompletableFuture<Void> triggerManualEndpointScanAsync(String url, boolean runArjun, boolean interfaceDiscoveryFirst, boolean useOnlyHostParameters, HttpRequest originalTemplate) {
        try {
            api.logging().raiseInfoEvent("开始对手动添加的端点进行 Arjun 探测: " + url);

            // 直接进入增量扫描流程（让 ParameterManager 统一控制增量/缓存）
            // 提示：重复触发也不会重复扫描，增量逻辑会返回空集并跳过
            final HttpRequest finalTemplate = originalTemplate;
            return CompletableFuture.runAsync(() -> {
                try {
                    performIncrementalArjunScan(true, url, runArjun, interfaceDiscoveryFirst, useOnlyHostParameters, finalTemplate);
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("手动端点 Arjun 扫描失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发手动端点 Arjun 扫描时出错: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 对手动添加的端点进行 Arjun 扫描（兼容旧接口，默认不先探测接口）
     * 
     * @param url 手动添加的 URL
     * @param runArjun 是否执行Arjun参数探测
     */
    public void triggerManualEndpointScan(String url, boolean runArjun) {
        triggerManualEndpointScan(url, runArjun, false);
    }

    /**
     * 仅执行接口探测（不触发Arjun）
     * 从SiteMap读取历史流量，同步参数和接口统计
     */
    private void performIncrementalArjunScan() {
        performIncrementalArjunScan(false, null, true, false);
    }
    
    public java.util.concurrent.CompletableFuture<ParameterCollector.CollectorStatistics> triggerInterfaceDiscovery() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ParameterCollector.CollectorStatistics before = parameterCollector.getStatistics();
                SiteMap siteMap = api.siteMap();
                List<HttpRequestResponse> entries = siteMap.requestResponses();
                int processed = 0;

                for (HttpRequestResponse entry : entries) {
                    if (entry == null || entry.request() == null) {
                        continue;
                    }

                    if (parameterCollector.collectFromRequest(entry.request())) {
                        processed++;
                    }

                    if (entry.response() != null) {
                        parameterCollector.collectFromResponse(entry.request(), entry.response());
                        // 解析响应体中的相对路径接口
                        try {
                            String body = entry.response().bodyToString();
                            if (body != null && !body.isEmpty()) {
                                java.util.Set<String> paths = com.xprobe.scanner.active.discovery.InterfaceAutoDiscoveryEngine.extractRelativePaths(body);
                                if (!paths.isEmpty()) {
                                    java.net.URI u = new java.net.URI(entry.request().url());
                                    String scheme = (u.getScheme() != null) ? u.getScheme() : "https";
                                    String host = u.getHost();
                                    if (host == null || host.isEmpty()) continue;
                                    int port = u.getPort();
                                    if (port < 0) port = ("https".equalsIgnoreCase(scheme) ? 443 : 80);
                                    for (String p : paths) {
                                        String full = scheme + "://" + host + ( (port==443 && "https".equalsIgnoreCase(scheme)) || (port==80 && "http".equalsIgnoreCase(scheme)) ? "" : (":"+port) ) + p;
                                        burp.api.montoya.http.message.requests.HttpRequest synth = buildRequest(full, "GET", null);
                                        if (synth != null) {
                                            parameterCollector.collectFromRequest(synth);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                }

                ParameterCollector.CollectorStatistics after = parameterCollector.getStatistics();
                int endpointDelta = after.getEndpointCount() - before.getEndpointCount();
                int parameterDelta = after.getParameterCount() - before.getParameterCount();

                api.logging().raiseInfoEvent(String.format(
                    "接口探测完成: 处理 %d 条站点数据, 记录请求 %d 条, 新接口 %d 个, 新参数 %d 个",
                    entries.size(),
                    processed,
                    Math.max(endpointDelta, 0),
                    Math.max(parameterDelta, 0)
                ));

                return after;
            } catch (Exception e) {
                api.logging().raiseErrorEvent("接口探测失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 针对选中目标与接口范围执行“自动采集”（从 SiteMap 获取），满足 AUTO 模式按范围采集的设计
     * @param targets 选中目标 Map<mainDomain, Set<host>>
     * @param useOnlyHostEndpoints true=仅选中子域接口；false=主域所有子域接口
     */
    public java.util.concurrent.CompletableFuture<ParameterCollector.CollectorStatistics> triggerInterfaceDiscoveryForTargets(
            Map<String, java.util.Set<String>> targets, boolean useOnlyHostEndpoints) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ParameterCollector.CollectorStatistics before = parameterCollector.getStatistics();
                if (targets == null || targets.isEmpty()) {
                    api.logging().raiseInfoEvent("AUTO接口采集：未选择任何目标，跳过");
                    return before;
                }

                // 选中主域集合
                java.util.Set<String> selectedMainDomains = new java.util.HashSet<>(targets.keySet());
                // 允许的host集合
                java.util.Set<String> allowedHosts = new java.util.HashSet<>();
                if (useOnlyHostEndpoints) {
                    // 仅选中子域
                    for (var e : targets.entrySet()) {
                        if (e.getValue() != null) allowedHosts.addAll(e.getValue());
                    }
                }

                SiteMap siteMap = api.siteMap();
                java.util.List<HttpRequestResponse> entries = siteMap.requestResponses();
                int processed = 0;

                for (HttpRequestResponse entry : entries) {
                    if (entry == null || entry.request() == null) continue;
                    String url = entry.request().url();
                    String host;
                    try {
                        java.net.URI u = new java.net.URI(url);
                        host = u.getHost();
                    } catch (Exception ex) {
                        continue;
                    }
                    if (host == null || host.isEmpty()) continue;

                    // 判断是否允许
                    boolean allow;
                    if (useOnlyHostEndpoints) {
                        allow = allowedHosts.contains(host);
                    } else {
                        // 主域匹配
                        String md = extractMainDomain(host);
                        allow = selectedMainDomains.contains(md);
                    }
                    if (!allow) continue;

                    // 收集
                    if (parameterCollector.collectFromRequest(entry.request())) {
                        processed++;
                    }
                    if (entry.response() != null) {
                        parameterCollector.collectFromResponse(entry.request(), entry.response());
                    }
                }

                ParameterCollector.CollectorStatistics after = parameterCollector.getStatistics();
                api.logging().raiseInfoEvent(String.format(
                    "AUTO接口采集完成: 处理 %d 条站点数据, 记录请求 %d 条, 新接口 %d 个, 新参数 %d 个 (范围=%s)",
                    entries.size(), processed,
                    Math.max(after.getEndpointCount() - before.getEndpointCount(), 0),
                    Math.max(after.getParameterCount() - before.getParameterCount(), 0),
                    useOnlyHostEndpoints ? "仅选中子域" : "主域所有子域"
                ));
                return after;
            } catch (Exception e) {
                api.logging().raiseErrorEvent("AUTO接口采集失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 执行增量 Arjun 扫描（从 SiteMap/Proxy 收集的流量）
     */
    private void performIncrementalArjunScan(boolean isManualEndpoint, String manualUrl, boolean runArjun, boolean interfaceDiscoveryFirst) {
        performIncrementalArjunScan(isManualEndpoint, manualUrl, runArjun, interfaceDiscoveryFirst, null);
    }

    /**
     * 执行增量 Arjun 扫描（从 SiteMap/Proxy 收集的流量），可选手动参数范围
     * @param useOnlyHostParameters 当非空且为true时，在手动端点模式下仅使用目标子域参数
     */
    private void performIncrementalArjunScan(boolean isManualEndpoint, String manualUrl, boolean runArjun, boolean interfaceDiscoveryFirst, Boolean useOnlyHostParameters) {
        performIncrementalArjunScan(isManualEndpoint, manualUrl, runArjun, interfaceDiscoveryFirst, useOnlyHostParameters, null);
    }
    
    private void performIncrementalArjunScan(boolean isManualEndpoint, String manualUrl, boolean runArjun, boolean interfaceDiscoveryFirst, Boolean useOnlyHostParameters, HttpRequest originalTemplate) {
        try {
            int totalScanned = 0;
            int totalSkipped = 0;
            int totalIncrementalParams = 0;
            
            if (isManualEndpoint && manualUrl != null) {
                // 手动添加的端点：尝试所有 method 和 contentType 组合
                boolean onlyHostParams = useOnlyHostParameters != null ? useOnlyHostParameters : false;
                totalScanned = scanManualEndpoint(manualUrl, runArjun, interfaceDiscoveryFirst, onlyHostParams, originalTemplate);
                api.logging().raiseInfoEvent(String.format(
                    "手动端点扫描完成: 扫描 %d 个组合",
                    totalScanned
                ));
            } else {
                // 从 SiteMap 收集的流量：使用原流量的 method 和 contentType
                SiteMap siteMap = api.siteMap();
                List<HttpRequestResponse> requestResponses = siteMap.requestResponses();
                
                // 按主域名分组请求
                Map<String, List<HttpRequest>> domainToRequests = groupRequestsByMainDomain(requestResponses);
                
                api.logging().raiseInfoEvent(String.format(
                    "从 SiteMap 获取了 %d 个请求，分组为 %d 个主域名",
                    requestResponses.size(), domainToRequests.size()
                ));
                
                // 对每个主域名进行扫描
                for (Map.Entry<String, List<HttpRequest>> entry : domainToRequests.entrySet()) {
                    String mainDomain = entry.getKey();
                    
                    // 获取该主域名收集的所有参数
                    Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
                    
                    // 如果启用了关键词收集，将关键词也加入参数列表
                    if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                        Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                        collectedParams.addAll(keywords);
                        api.logging().raiseDebugEvent(String.format(
                            "主域名 %s: 合并了 %d 个关键词", mainDomain, keywords.size()
                        ));
                    }
                    
                    // 获取该主域名的所有接口（包含 method 和 contentType）
                    Set<ParameterCollector.EndpointKey> endpointKeys = 
                        parameterCollector.getEndpointKeysForMainDomain(mainDomain);
                    
                    api.logging().raiseInfoEvent(String.format(
                        "主域名 %s: 收集了 %d 个参数, %d 个接口组合",
                        mainDomain, collectedParams.size(), endpointKeys.size()
                    ));
                    
                    // 对每个接口组合进行增量扫描
                    for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                        // ✅ 检查是否已停止（在循环开始时检查）
                        if (!arjunEnabled) {
                            api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断扫描: " + mainDomain);
                            break;
                        }
                        
                        // ✅ 修复：如果执行策略是接口探测成功后做 Arjun 探测，只对接口探测成功的接口做 Arjun 探测
                        // 只有在 requireInterfaceFirst=true 时才检查接口存在性
                        if (requireInterfaceFirst) {
                            String endpointKey = buildEndpointKey(epKey.method, epKey.host, epKey.contentType, epKey.endpoint);
                            Boolean endpointExists = endpointExistenceCache.get(endpointKey);
                            if (endpointExists != null && !endpointExists) {
                                api.logging().raiseDebugEvent(String.format(
                                    "⚠️ 跳过接口 %s %s (%s) %s：接口探测结果为不存在",
                                    epKey.method, epKey.host, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                                ));
                                continue;
                            }
                            // 如果 endpointExists == null，说明还没有探测过，跳过（接口探测策略要求先探测接口）
                            if (endpointExists == null) {
                                api.logging().raiseDebugEvent(String.format(
                                    "⚠️ 跳过接口 %s %s (%s) %s：接口探测策略要求先探测接口，但该接口尚未探测",
                                    epKey.method, epKey.host, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                                ));
                                continue;
                            }
                        }
                        // 如果 requireInterfaceFirst=false，直接进行Arjun扫描，不检查接口存在性
                        
                        // 获取该接口的请求模板
                        HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                        if (templateRequest == null) {
                            api.logging().raiseDebugEvent("未找到请求模板: " + epKey);
                            continue;
                        }
                        
                        // ✅ 定义三形态组合（所有模板都尝试三形态）
                        String[][] combinations = new String[][]{
                            {"GET", null},
                            {"POST", "application/x-www-form-urlencoded"},
                            {"POST", "application/json"}
                        };
                        
                        // ✅ 对每个形态进行扫描
                        for (String[] combo : combinations) {
                            // ✅ 再次检查是否已停止（在内层循环中也检查）
                            if (!arjunEnabled) {
                                api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断形态扫描");
                                break;
                            }
                            String method = combo[0];
                            String contentType = combo[1];
                            String contentTypeForKey = ("GET".equalsIgnoreCase(method)) ? null : contentType;
                            
                            // 计算增量参数（未扫描过的）
                            Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                                method, epKey.host, contentTypeForKey, epKey.endpoint, collectedParams
                            );
                            
                            // ✅ 即使无增量参数，也回退到当前参数来源范围的集合
                            if (incrementalParams.isEmpty()) {
                                api.logging().raiseDebugEvent(String.format(
                                    "无增量参数，回退到当前参数来源集合: %s %s (%s) %s (参数数=%d)",
                                    method, epKey.host, (contentTypeForKey != null ? contentTypeForKey : "N/A"), epKey.endpoint, collectedParams.size()
                                ));
                                incrementalParams = new HashSet<>(collectedParams);
                            }
                            
                            if (incrementalParams.isEmpty()) {
                                totalSkipped++;
                                api.logging().raiseDebugEvent(String.format(
                                    "跳过 %s %s (%s) %s (无可用参数)", 
                                    method, epKey.host, contentTypeForKey != null ? contentTypeForKey : "N/A", epKey.endpoint
                                ));
                                continue;
                            }
                            
                            // ✅ 构建完整URL（保留原始URL参数，用于GET请求）
                            String fullUrl = buildUrlFromTemplate(templateRequest, epKey.host, stripQuery(epKey.endpoint));
                            if (fullUrl == null) {
                                api.logging().raiseDebugEvent("构建URL失败: " + epKey);
                                continue;
                            }
                            
                            // ✅ 构建带参数的请求（形态转换：原始参数+新参数都按目标形态放置）
                            java.util.Set<String> sanitizedParams = sanitizeParamNames(collectedParams);
                            HttpRequest baseRequest = buildRequestWithParamsAndTransform(fullUrl, method, contentType, templateRequest, sanitizedParams);
                            if (baseRequest == null) {
                                continue;
                            }
                            
                            // ✅ 复制关键会话头（Cookie/Authorization等）
                            HttpRequest requestToScan = copyCriticalSessionHeaders(templateRequest, baseRequest);
                            final HttpRequest finalRequest = requestToScan;
                            final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                            final String finalMethod = method;
                            final String finalContentType = contentTypeForKey;
                            totalIncrementalParams += incrementalParams.size();
                            
                            api.logging().raiseInfoEvent(String.format(
                                "扫描 %s %s (%s) %s, 增量参数: %d",
                                finalMethod, epKey.host, finalContentType != null ? finalContentType : "N/A", epKey.endpoint, incrementalParams.size()
                            ));
                            
                            // 秒显：先插入“参数探测中”占位
                            {
                                String paramTypeProgress = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunProgress(mainDomain, epKey.host, epKey.endpoint, paramTypeProgress);
                            }
                            // 异步调用 Arjun
                            arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                                // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                                long timestamp = System.currentTimeMillis();
                                
                                if (result.isSuccess()) {
                                    // ✅ 优化日志：区分找到参数和未找到参数的情况
                                    String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                    
                                    if (!result.getFoundParameters().isEmpty()) {
                                        api.logging().raiseInfoEvent(String.format(
                                            "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                            result.getFoundParameters().size(),
                                            finalMethod, epKey.host, finalContentType, epKey.endpoint, result.getFoundParameters()
                                        ));
                                        
                                        // ✅ 通知UI显示结果（传入立即生成的时间戳）
                                        notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType, timestamp);
                                        
                                        // ✅ 将发现的参数传递给漏洞扫描器
                                        triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                    } else {
                                        // ✅ 修复：即使没有发现参数，也添加到表格中
                                        api.logging().raiseDebugEvent(String.format(
                                            "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                            finalMethod, epKey.host, finalContentType, epKey.endpoint
                                        ));
                                        notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                                    }
                                    
                                    // 标记参数为已扫描
                                    parameterManager.markParametersAsScanned(
                                        finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                        finalIncrementalParams
                                    );
                                } else {
                                    api.logging().raiseErrorEvent(
                                        "Arjun 扫描失败: " + result.getErrorMessage()
                                    );
                                    
                                    // ✅ 修复：失败时也通知UI，显示扫描失败的结果
                                    String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                                    
                                    // 🔴 优化：即使失败也标记（避免无限重试）
                                    parameterManager.markParametersAsScanned(
                                        finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                        finalIncrementalParams
                                    );
                                    api.logging().raiseDebugEvent(String.format(
                                        "已标记失败的扫描参数，避免重复尝试: %s %s (%s) %s", 
                                        finalMethod, epKey.host, finalContentType, epKey.endpoint
                                    ));
                                }
                            }).exceptionally(ex -> {
                                // ✅ 修复：异常时也立即生成时间戳
                                long timestamp = System.currentTimeMillis();
                                api.logging().raiseErrorEvent("Arjun 异步执行失败: " + ex.getMessage());
                                
                                // ✅ 修复：异常时也通知UI，显示扫描异常的结果
                                String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                                
                                // 🔴 优化：异常时也标记
                                parameterManager.markParametersAsScanned(
                                    finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                    finalIncrementalParams
                                );
                                return null;
                            });
                            
                            totalScanned++;
                        }
                    }
                }
                
                api.logging().raiseInfoEvent(String.format(
                    "Arjun 扫描完成: 扫描 %d 个接口, 跳过 %d 个, 总增量参数 %d 个",
                    totalScanned, totalSkipped, totalIncrementalParams
                ));
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("执行增量 Arjun 扫描时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从已收集的数据进行增量 Arjun 扫描（Proxy实时流量模式）
     * 
     * 🔴 优化1：区分数据源
     * - performIncrementalArjunScan(): 从 SiteMap 读取历史流量（手动触发）
     * - performIncrementalArjunScanFromCollectedData(): 使用 ParameterCollector 实时数据（实时监听）
     */
    private void performIncrementalArjunScanFromCollectedData() {
        try {
            int totalScanned = 0;
            int totalSkipped = 0;
            int totalIncrementalParams = 0;
            
            // 直接从 ParameterCollector 获取所有主域名（实时收集的数据）
            Set<String> allMainDomains = parameterCollector.getAllMainDomains();
            
            api.logging().raiseInfoEvent(String.format(
                "从 Proxy 实时收集的数据中扫描 %d 个主域名",
                allMainDomains.size()
            ));
            
            // 对每个主域名进行扫描
            for (String mainDomain : allMainDomains) {
                // 获取该主域名收集的所有参数
                Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
                
                // 如果启用了关键词收集，将关键词也加入参数列表
                if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                    Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                    collectedParams.addAll(keywords);
                }
                
                // 获取该主域名的所有接口
                Set<ParameterCollector.EndpointKey> endpointKeys = 
                    parameterCollector.getEndpointKeysForMainDomain(mainDomain);
                
                // 对每个接口组合进行增量扫描
                for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                    // ✅ 检查是否已停止（在循环开始时检查）
                    if (!arjunEnabled) {
                        api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断扫描: " + mainDomain);
                        break;
                    }
                    
                    // ✅ 修复：如果执行策略是接口探测成功后做 Arjun 探测，只对接口探测成功的接口做 Arjun 探测
                    // 只有在 requireInterfaceFirst=true 时才检查接口存在性
                    if (requireInterfaceFirst) {
                        String endpointKey = buildEndpointKey(epKey.method, epKey.host, epKey.contentType, epKey.endpoint);
                        Boolean endpointExists = endpointExistenceCache.get(endpointKey);
                        if (endpointExists != null && !endpointExists) {
                            api.logging().raiseDebugEvent(String.format(
                                "⚠️ 跳过接口 %s %s (%s) %s：接口探测结果为不存在",
                                epKey.method, epKey.host, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                            ));
                            continue;
                        }
                        // 如果 endpointExists == null，说明还没有探测过，跳过（接口探测策略要求先探测接口）
                        if (endpointExists == null) {
                            api.logging().raiseDebugEvent(String.format(
                                "⚠️ 跳过接口 %s %s (%s) %s：接口探测策略要求先探测接口，但该接口尚未探测",
                                epKey.method, epKey.host, epKey.contentType != null ? epKey.contentType : "N/A", epKey.endpoint
                            ));
                            continue;
                        }
                    }
                    // 如果 requireInterfaceFirst=false，直接进行Arjun扫描，不检查接口存在性
                    
                    HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                    if (templateRequest == null) {
                        continue;
                    }
                    
                    // ✅ 检查模板是否同时有URL和Body参数（特殊例外：保持原状）
                    // ✅ 定义三形态组合（所有模板都尝试三形态）
                    String[][] combinations = new String[][]{
                        {"GET", null},
                        {"POST", "application/x-www-form-urlencoded"},
                        {"POST", "application/json"}
                    };
                    
                    // ✅ 对每个形态进行扫描
                    for (String[] combo : combinations) {
                        // ✅ 再次检查是否已停止（在内层循环中也检查）
                        if (!arjunEnabled) {
                            api.logging().raiseInfoEvent("⏹️ 主动探测已停止，中断形态扫描");
                            break;
                        }
                        String method = combo[0];
                        String contentType = combo[1];
                        String contentTypeForKey = ("GET".equalsIgnoreCase(method)) ? null : contentType;
                        
                        Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                            method, epKey.host, contentTypeForKey, epKey.endpoint, collectedParams
                        );
                        
                        // ✅ 即使无增量参数，也回退到当前参数来源范围的集合
                        if (incrementalParams.isEmpty()) {
                            incrementalParams = new HashSet<>(collectedParams);
                        }
                        
                        if (incrementalParams.isEmpty()) {
                            totalSkipped++;
                            continue;
                        }
                        
                        // ✅ 构建完整URL（保留原始URL参数，用于GET请求）
                        String fullUrl = buildUrlFromTemplate(templateRequest, epKey.host, stripQuery(epKey.endpoint));
                        if (fullUrl == null) {
                            continue;
                        }
                        
                        // ✅ 构建带参数的请求（形态转换：原始参数+新参数都按目标形态放置）
                        java.util.Set<String> sanitizedParams = sanitizeParamNames(collectedParams);
                            HttpRequest baseRequest = buildRequestWithParamsAndTransform(fullUrl, method, contentType, templateRequest, sanitizedParams);
                        if (baseRequest == null) {
                            continue;
                        }
                        
                        // ✅ 复制关键会话头（Cookie/Authorization等）
                        HttpRequest requestToScan = copyCriticalSessionHeaders(templateRequest, baseRequest);
                        
                        // ✅ 新增：应用最新请求头（确保使用最新的会话信息，覆盖旧的请求头）
                        requestToScan = headerManager.applyTo(requestToScan, epKey.host);
                        
                        final HttpRequest finalRequest = requestToScan;
                        final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                        final String finalMethod = method;
                        final String finalContentType = contentTypeForKey;
                        totalIncrementalParams += incrementalParams.size();
                        
                        // 异步调用 Arjun（与SiteMap模式相同的逻辑）
                        arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                            // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                            long timestamp = System.currentTimeMillis();
                            
                            if (result.isSuccess()) {
                                // ✅ 优化日志：区分找到参数和未找到参数的情况
                                String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                
                                if (!result.getFoundParameters().isEmpty()) {
                                    api.logging().raiseInfoEvent(String.format(
                                        "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                        result.getFoundParameters().size(),
                                        finalMethod, epKey.host, finalContentType, epKey.endpoint, result.getFoundParameters()
                                    ));
                                    
                                    // ✅ 通知UI显示结果（传入立即生成的时间戳）
                                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType, timestamp);
                                    
                                    // ✅ 将发现的参数传递给漏洞扫描器
                                    triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                } else {
                                    // ✅ 修复：即使没有发现参数，也添加到表格中
                                    api.logging().raiseDebugEvent(String.format(
                                        "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                        finalMethod, epKey.host, finalContentType, epKey.endpoint
                                    ));
                                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                                }
                                
                                parameterManager.markParametersAsScanned(
                                    finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                    finalIncrementalParams
                                );
                            } else {
                                // ✅ 修复：失败时也通知UI，显示扫描失败的结果
                                String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                                
                                // 🔴 优化2：失败也标记（避免无限重试）
                                parameterManager.markParametersAsScanned(
                                    finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                    finalIncrementalParams
                                );
                            }
                        }).exceptionally(ex -> {
                            // ✅ 修复：异常时也立即生成时间戳
                            long timestamp = System.currentTimeMillis();
                            // ✅ 修复：异常时也通知UI，显示扫描异常的结果
                            String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                            notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType, timestamp);
                            
                            // 🔴 优化2：异常也标记
                            parameterManager.markParametersAsScanned(
                                finalMethod, epKey.host, finalContentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                            return null;
                        });
                        
                        totalScanned++;
                    }
                }
            }
            
            api.logging().raiseInfoEvent(String.format(
                "Proxy实时流量 Arjun 扫描完成: 扫描 %d 个接口, 跳过 %d 个, 总增量参数 %d 个",
                totalScanned, totalSkipped, totalIncrementalParams
            ));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Proxy实时流量扫描时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 扫描手动添加的端点（尝试所有 method 和 contentType 组合）
     * 
     * @param url 手动添加的 URL
     * @param runArjun 是否执行Arjun扫描
     * @param interfaceDiscoveryFirst 是否先探测接口
     * @param useOnlyHostParameters 是否仅使用目标子域参数
     * @param originalTemplate 原始请求模板（可选），如果提供则保留请求头
     * @return 扫描的组合数量
     */
    private int scanManualEndpoint(String url, boolean runArjun, boolean interfaceDiscoveryFirst, boolean useOnlyHostParameters, HttpRequest originalTemplate) {
        try {
            // ✅ 修复：手动解析 URL，避免 URI 类无法处理未编码的特殊字符（如 JSON 中的 {, }, " 等）
            String host = null;
            String endpointPath = null;
            
            try {
                // 先尝试使用 URI 解析（对于标准 URL）
                URI uri = new URI(url);
                host = uri.getHost();
                endpointPath = uri.getPath();
            } catch (Exception e) {
                // ✅ 如果 URI 解析失败（可能包含未编码的特殊字符），使用手动解析
                // 解析 scheme
                int schemeEnd = url.indexOf("://");
                if (schemeEnd == -1) {
                    api.logging().raiseErrorEvent("手动端点扫描失败: 无法解析URL格式（缺少scheme） - url=" + url);
                    return 0;
                }
                
                // 解析 host 和 port
                int pathStart = url.indexOf('/', schemeEnd + 3);
                String hostPort;
                if (pathStart == -1) {
                    hostPort = url.substring(schemeEnd + 3);
                    endpointPath = "/";
                } else {
                    hostPort = url.substring(schemeEnd + 3, pathStart);
                    // 提取 path（去掉 query 参数）
                    int queryStart = url.indexOf('?', pathStart);
                    if (queryStart > 0) {
                        endpointPath = url.substring(pathStart, queryStart);
                    } else {
                        endpointPath = url.substring(pathStart);
                    }
                }
                
                // 解析 port
                int portStart = hostPort.indexOf(':');
                if (portStart == -1) {
                    host = hostPort;
                } else {
                    host = hostPort.substring(0, portStart);
                }
            }
            
            // ✅ 修复：如果 host 为 null 或 "null"，直接返回
            if (host == null || host.isEmpty() || "null".equals(host)) {
                api.logging().raiseErrorEvent("手动端点扫描失败: 无法确定 host - url=" + url);
                return 0;
            }
            
            if (endpointPath == null || endpointPath.isEmpty()) {
                endpointPath = "/";
            }
            
            // ✅ 修复：声明为 final，供 lambda 使用
            final String finalHost = host;
            final String endpoint = endpointPath;
            String mainDomain = extractMainDomain(host);
            // ✅ 修复：如果 mainDomain 为 null，直接返回
            if (mainDomain == null || mainDomain.isEmpty()) {
                api.logging().raiseErrorEvent("手动端点扫描失败: 无法确定 mainDomain - host=" + host);
                return 0;
            }
            
            // 获取参数集合：根据参数范围决定使用主域参数合集或目标子域参数
            Set<String> collectedParams = useOnlyHostParameters
                ? parameterCollector.getParametersForHost(finalHost)
                : parameterCollector.getParametersForMainDomain(mainDomain);
            
            // 如果启用了关键词收集，将关键词也加入参数列表（仅在使用主域参数合集时合并关键词）
            if (!useOnlyHostParameters && parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                collectedParams.addAll(keywords);
            }
            
            // ✅ 定义三形态组合（所有手动端点都尝试三形态）
            String[][] combinations = new String[][]{
                {"GET", null},
                {"POST", "application/x-www-form-urlencoded"},
                {"POST", "application/json"}
            };
            
            int scannedCount = 0;
            
            api.logging().raiseInfoEvent(String.format(
                "手动端点 %s: 将尝试 %d 个形态组合",
                url, combinations.length
            ));
            
            // ✅ 从URL中直接提取参数（如果URL包含查询参数）
            // ✅ 修复：优先使用传入的原始请求模板，如果没有则从URL构建
            final HttpRequest tempTemplate = (originalTemplate != null) ? originalTemplate : getTemplateRequestForUrl(url);
            
            // ✅ 修复：如果原始模板为空，尝试从parameterCollector获取
            HttpRequest finalTemplate = tempTemplate;
            if (finalTemplate == null) {
                // 尝试从parameterCollector获取该endpoint的模板请求
                try {
                    // ✅ 修复：使用已解析的 endpointPath，而不是 uri.getPath()
                    String path = endpointPath;
                    if (path == null || path.isEmpty()) path = "/";
                    // 尝试获取GET方法的模板（最常见）
                    HttpRequest templateFromCollector = parameterCollector.getEndpointTemplate(mainDomain, path);
                    if (templateFromCollector != null) {
                        finalTemplate = templateFromCollector;
                    }
                } catch (Exception e) {
                    // 忽略错误
                }
            }
            
            // 如果还是没有模板，使用从URL构建的
            if (finalTemplate == null) {
                finalTemplate = getTemplateRequestForUrl(url);
            }
            
            // ✅ 修复：声明为final，供lambda使用
            final HttpRequest finalTemplateForLambda = finalTemplate;
            
            // 尝试所有组合
            for (String[] combo : combinations) {
                String method = combo[0];
                String contentType = combo[1];
                
                // ✅ 修复：对于GET请求，使用null作为contentType（因为GET不需要Content-Type）
                String contentTypeForKey = ("GET".equalsIgnoreCase(method)) ? null : contentType;
                
                // 计算增量参数
                Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                    method, finalHost, contentTypeForKey, endpoint, collectedParams
                );
                    
                    // 即使无增量参数，在参数探测阶段也不跳过，回退到当前参数来源范围的集合
                    if (incrementalParams.isEmpty() && runArjun) {
                        api.logging().raiseDebugEvent(String.format(
                            "无增量参数，回退到当前参数来源集合: %s %s (%s) %s (参数数=%d)",
                            method, finalHost, (contentTypeForKey != null ? contentTypeForKey : "N/A"), endpoint, collectedParams.size()
                        ));
                        incrementalParams = new HashSet<>(collectedParams);
                    }
                    
                    // ✅ 构建请求（形态转换：URL中的参数+新参数都按目标形态放置）
                    java.util.Set<String> sanitizedParams2 = sanitizeParamNames(collectedParams);
                    HttpRequest request = buildRequestWithParamsAndTransform(url, method, contentType, finalTemplateForLambda, sanitizedParams2);
                    if (request == null) {
                        continue;
                    }
                    
                    // ✅ 修复：复制关键会话头（确保Arjun扫描时也保留请求头）
                    HttpRequest requestWithHeaders = copyCriticalSessionHeaders(finalTemplateForLambda, request);
                    // 应用最新主机级请求头 + 配置中心自定义请求头（最高优先级）
                    requestWithHeaders = headerManager.applyTo(requestWithHeaders, finalHost);
                    
                    final HttpRequest finalRequest = requestWithHeaders;
                    final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                    final String finalMethod = method;
                    final String finalContentType = contentTypeForKey;  // ✅ 使用修正后的contentType
                    final String finalDisplayContentType = contentTypeForKey != null ? contentTypeForKey : "N/A";
                    
                    api.logging().raiseInfoEvent(String.format(
                        "%s手动端点: %s %s (%s) %s, 增量参数: %d",
                        runArjun ? "扫描" : "验证",
                        method, finalHost, finalDisplayContentType, endpoint, incrementalParams.size()
                    ));
                    
                    // ✅ 如果runArjun=true，根据interfaceDiscoveryFirst决定是否先探测接口
                    if (runArjun) {
                        // ✅ 如果需要先探测接口，先进行接口探测（异步执行，不阻塞循环）
                        if (interfaceDiscoveryFirst) {
                            // ✅ 异步接口探测：避免阻塞循环，让多个接口探测并发执行
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    // 先秒显接口探测占位
                                    notifyInterfaceProgress(mainDomain, finalHost, endpoint, finalMethod, finalContentType);
                                    
                                    // ✅ 检查HTTP服务是否可用
                                    if (api.http() == null) {
                                        api.logging().raiseErrorEvent("HTTP服务不可用，无法发送接口探测请求");
                                        return false;
                                    }
                                    
                                    // 1. 发送原始路径请求（不携带探测参数，去掉URL查询串）
                                    // ✅ 修复：接口探测时不传入原始请求模板，确保不包含任何参数
                                    String cleanUrl = stripQuery(url);
                                    api.logging().raiseDebugEvent(String.format(
                                        "🔍 接口探测（Arjun前）：构建请求 - URL=%s, method=%s, contentType=%s",
                                        cleanUrl, finalMethod, finalContentType != null ? finalContentType : "N/A"
                                    ));
                                    HttpRequest validationRequest = buildRequestForInterfaceDiscovery(cleanUrl, finalMethod, finalContentType, tempTemplate);
                                    if (validationRequest == null) {
                                        api.logging().raiseErrorEvent("接口探测失败：无法构建请求 - " + cleanUrl);
                                        return false;
                                    }
                                    
                                    // ✅ 新增：应用最新请求头（确保使用最新的会话信息）
                                    validationRequest = headerManager.applyTo(validationRequest, finalHost);
                                    
                                    api.logging().raiseDebugEvent(String.format(
                                        "🔍 接口探测（Arjun前）：发送请求 - %s %s (%s) %s",
                                        finalMethod, finalHost, finalContentType != null ? finalContentType : "N/A", endpoint
                                    ));
                                    HttpRequestResponse originalResponse = api.http().sendRequest(validationRequest);
                                    int originalStatusCode = -1;
                                    String originalResponseBody = null;
                                    if (originalResponse != null && originalResponse.response() != null) {
                                        originalStatusCode = originalResponse.response().statusCode();
                                        originalResponseBody = originalResponse.response().bodyToString();
                                    }
                                    
                                    // ✅ 如果原始路径返回404，直接断定不存在，跳过Arjun扫描
                                    if (originalStatusCode == 404) {
                                        api.logging().raiseDebugEvent(String.format(
                                            "接口不存在: %s %s (%s) %s - 状态码 404，跳过Arjun扫描",
                                            finalMethod, finalHost, finalDisplayContentType, endpoint
                                        ));
                                        return false;  // 接口不存在
                                    }
                                    
                                    // 2. ✅ 如果原始路径返回非404，需要和随机路径的响应码和响应体对比
                                    boolean endpointExists = false;
                                    if (originalStatusCode != 404 && originalStatusCode != -1) {
                                        // ✅ 统一处理：2xx、3xx、其他4xx都需要验证
                                        // ✅ 优化：使用缓存的随机路径响应，避免重复发送请求
                                        // ✅ 修复：接口探测阶段应使用不带参数的 validationRequest，避免收集到探测参数
                                        endpointExists = validateEndpointWithRandomPath(
                                            url, validationRequest, originalResponse, 
                                            originalStatusCode, originalResponseBody,
                                            finalMethod, finalHost, finalContentType, endpoint, mainDomain
                                        );
                                    } else if (originalStatusCode >= 500) {
                                        // 5xx服务器错误，可能是临时问题，保守处理为接口存在
                                        api.logging().raiseInfoEvent(String.format(
                                            "接口探测: %s %s (%s) %s - 状态码 %d (服务器错误)，继续Arjun扫描",
                                            finalMethod, finalHost, finalDisplayContentType, endpoint, originalStatusCode
                                        ));
                                        endpointExists = true;
                                    } else if (originalStatusCode == -1) {
                                        // 原始请求无响应，跳过
                                        api.logging().raiseDebugEvent(String.format(
                                            "接口探测: %s %s (%s) %s - 无响应，跳过Arjun扫描",
                                            finalMethod, finalHost, finalDisplayContentType, endpoint
                                        ));
                                        return false;
                                    }
                                    
                                    // ✅ 如果接口不存在，跳过Arjun扫描
                                    if (!endpointExists) {
                                        return false;  // 接口不存在
                                    }
                                    
                                    // ✅ 接口存在，继续进行Arjun参数探测
                                    api.logging().raiseInfoEvent(String.format(
                                        "接口验证通过，开始Arjun参数探测: %s %s (%s) %s",
                                        finalMethod, finalHost, finalDisplayContentType, endpoint
                                    ));
                                    
                                    return true;  // 接口存在
                                    
                                } catch (Exception sendError) {
                                    // 接口探测失败，保守处理，继续Arjun扫描
                                    api.logging().raiseDebugEvent(String.format(
                                        "接口探测失败: %s，继续Arjun扫描: %s",
                                        sendError.getMessage(), endpoint
                                    ));
                                    return true; // 保守处理：继续扫描
                                }
                            }).thenAccept(endpointExists -> {
                                // ✅ 先同步写入接口探测结果到表格
                                notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, endpointExists);
                                
                                // ✅ 如果接口不存在，跳过Arjun扫描
                                if (!endpointExists) {
                                    return; // 接口不存在，跳过
                                }
                                
                                // ✅ 接口存在：先同步插入"参数探测中"占位，再发起扫描
                                String paramTypeProgress = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunProgress(mainDomain, finalHost, endpoint, paramTypeProgress);
                                
                                // ✅ 进行Arjun参数探测（接口已验证存在）
                                arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                                    // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                                    long timestamp = System.currentTimeMillis();
                                    
                                    if (result.isSuccess()) {
                                        String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                        
                                        if (!result.getFoundParameters().isEmpty()) {
                                            api.logging().raiseInfoEvent(String.format(
                                                "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                                result.getFoundParameters().size(),
                                                finalMethod, finalHost, finalContentType, endpoint, 
                                                result.getFoundParameters()
                                            ));
                                            
                                            notifyArjunResult(mainDomain, finalHost, endpoint, result.getFoundParameters(), paramType, timestamp);
                                            triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                        } else {
                                            api.logging().raiseDebugEvent(String.format(
                                                "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                                finalMethod, finalHost, finalContentType, endpoint
                                            ));
                                            notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                                        }
                                        
                                        parameterManager.markParametersAsScanned(
                                            finalMethod, finalHost, finalContentType, endpoint, 
                                            finalIncrementalParams
                                        );
                                    } else {
                                        // ✅ 修复：失败时也通知UI，显示扫描失败的结果
                                        String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                        notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                                        
                                        parameterManager.markParametersAsScanned(
                                            finalMethod, finalHost, finalContentType, endpoint, 
                                            finalIncrementalParams
                                        );
                                    }
                                }).exceptionally(ex -> {
                                    // ✅ 修复：异常时也立即生成时间戳
                                    long timestamp = System.currentTimeMillis();
                                    api.logging().raiseErrorEvent("Arjun扫描异常: " + ex.getMessage());
                                    
                                    // ✅ 修复：异常时也通知UI，显示扫描异常的结果
                                    String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                    notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                                    
                                    parameterManager.markParametersAsScanned(
                                        finalMethod, finalHost, finalContentType, endpoint, 
                                        finalIncrementalParams
                                    );
                                    return null;
                                });
                            });  // 闭合外层的thenAccept
                        } else {
                            // ✅ 直接进行Arjun参数探测（不先探测接口）
                            // 秒显：先插入“参数探测中”占位
                            {
                                String paramTypeProgress = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunProgress(mainDomain, finalHost, endpoint, paramTypeProgress);
                            }
                            arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                                // ✅ 修复：在收到响应时立即生成时间戳，避免延迟
                                long timestamp = System.currentTimeMillis();
                                
                                if (result.isSuccess()) {
                                    String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                    
                                    if (!result.getFoundParameters().isEmpty()) {
                                        api.logging().raiseInfoEvent(String.format(
                                            "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                            result.getFoundParameters().size(),
                                            finalMethod, finalHost, finalContentType, endpoint, 
                                            result.getFoundParameters()
                                        ));
                                        
                                        notifyArjunResult(mainDomain, finalHost, endpoint, result.getFoundParameters(), paramType, timestamp);
                                        triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                    } else {
                                        // ✅ 修复：即使没有发现参数，也添加到表格中
                                        api.logging().raiseDebugEvent(String.format(
                                            "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                            finalMethod, finalHost, finalContentType, endpoint
                                        ));
                                        notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                                    }
                                    
                                    parameterManager.markParametersAsScanned(
                                        finalMethod, finalHost, finalContentType, endpoint, 
                                        finalIncrementalParams
                                    );
                                } else {
                                    api.logging().raiseErrorEvent(
                                        "Arjun 扫描失败: " + result.getErrorMessage()
                                    );
                                    
                                    // ✅ 修复：失败时也通知UI，显示扫描失败的结果
                                    String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                    notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                                    
                                    parameterManager.markParametersAsScanned(
                                        finalMethod, finalHost, finalContentType, endpoint, 
                                        finalIncrementalParams
                                    );
                                }
                            }).exceptionally(ex -> {
                                // ✅ 修复：异常时也立即生成时间戳
                                long timestamp = System.currentTimeMillis();
                                api.logging().raiseErrorEvent("Arjun 异步执行失败: " + ex.getMessage());
                                
                                // ✅ 修复：异常时也通知UI，显示扫描异常的结果
                                String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                notifyArjunResult(mainDomain, finalHost, endpoint, new HashSet<>(), paramType, timestamp);
                            
                            parameterManager.markParametersAsScanned(
                                finalMethod, finalHost, finalContentType, endpoint, 
                                finalIncrementalParams
                            );
                            return null;
                        });
                        }
                    } else {
                        // ✅ 接口探测：使用随机路径对比验证接口是否存在（异步并发执行）
                        // 目的：避免泛解析、反射等导致的误判
                        // ✅ 优化：异步并发执行，不阻塞循环，确保速度，结果实时显示
                        CompletableFuture.runAsync(() -> {
                            try {
                                // ✅ 检查HTTP服务是否可用
                                if (api.http() == null) {
                                    api.logging().raiseErrorEvent("HTTP服务不可用，无法发送接口探测请求");
                                    return;
                                }
                                
                                // ✅ 修复：接口探测应该不带参数，使用干净的请求
                                // 1. 先发送原始路径请求（不携带探测参数，去掉URL查询串）
                                // ✅ 修复：接口探测时不传入原始请求模板，确保不包含任何参数（URL参数和Body参数）
                                // 只保留请求头（如 Cookie、Authorization），但不包含参数
                                String cleanUrl = stripQuery(url);
                                api.logging().raiseDebugEvent(String.format(
                                    "🔍 接口探测：构建请求 - URL=%s, method=%s, contentType=%s",
                                    cleanUrl, finalMethod, finalContentType != null ? finalContentType : "N/A"
                                ));
                                HttpRequest validationRequest = buildRequestForInterfaceDiscovery(cleanUrl, finalMethod, finalContentType, finalTemplateForLambda);
                                if (validationRequest == null) {
                                    api.logging().raiseErrorEvent("接口探测失败：无法构建请求 - " + cleanUrl);
                                    if (!runArjun && activeProbeTab != null) {
                                        notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, false);
                                    }
                                    return;
                                }
                                
                                // ✅ 新增：应用最新请求头（确保使用最新的会话信息）
                                validationRequest = headerManager.applyTo(validationRequest, finalHost);
                                
                                api.logging().raiseDebugEvent(String.format(
                                    "🔍 接口探测：发送请求 - %s %s (%s) %s",
                                    finalMethod, finalHost, finalContentType != null ? finalContentType : "N/A", endpoint
                                ));
                                HttpRequestResponse originalResponse = api.http().sendRequest(validationRequest);
                                int originalStatusCode = -1;
                                String originalResponseBody = null;
                                if (originalResponse != null && originalResponse.response() != null) {
                                    originalStatusCode = originalResponse.response().statusCode();
                                    originalResponseBody = originalResponse.response().bodyToString();
                                }
                                
                                // ✅ 如果原始路径返回404，直接断定不存在
                                if (originalStatusCode == 404) {
                                    api.logging().raiseDebugEvent(String.format(
                                        "接口不存在: %s %s (%s) %s - 状态码 404",
                                        finalMethod, finalHost, finalDisplayContentType, endpoint
                                    ));
                                    
                                    // ✅ 通知UI接口探测结果（仅当runArjun=false时，即纯接口探测模式）
                                    if (!runArjun && activeProbeTab != null) {
                                        notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, false);
                                    }
                                    
                                    return;  // 接口不存在
                                }
                                
                                // 2. ✅ 如果原始路径返回非404（包括其他4xx、2xx、3xx），需要和随机路径的响应码和响应体对比
                                if (originalStatusCode != 404 && originalStatusCode != -1) {
                                    // ✅ 统一处理：2xx、3xx、其他4xx都需要验证
                                    // ✅ 优化：使用缓存的随机路径响应，避免重复发送请求
                                    boolean endpointExists = validateEndpointWithRandomPath(
                                        url, validationRequest, originalResponse, 
                                        originalStatusCode, originalResponseBody,
                                        finalMethod, finalHost, finalContentType, endpoint, mainDomain
                                    );
                                    
                                    // ✅ 通知UI接口探测结果（仅当runArjun=false时，即纯接口探测模式）
                                    if (!runArjun && activeProbeTab != null) {
                                        notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, endpointExists);
                                    }
                                } else if (originalStatusCode >= 500) {
                                    // 5xx服务器错误，可能是临时问题
                                    api.logging().raiseInfoEvent(String.format(
                                        "接口探测: %s %s (%s) %s - 状态码 %d (服务器错误)",
                                        finalMethod, finalHost, finalDisplayContentType, endpoint, originalStatusCode
                                    ));
                                    // 继续处理，但标记为不确定
                                    if (originalResponse != null) {
                                        // ✅ 修复：使用不带参数的 validationRequest 进行参数收集
                                        parameterCollector.collectFromRequest(validationRequest);
                                        if (originalResponse.response() != null) {
                                            parameterCollector.collectFromResponse(validationRequest, originalResponse.response());
                                        }
                                    }
                                    // ✅ 通知UI接口探测结果（5xx视为存在）
                                    if (!runArjun && activeProbeTab != null) {
                                        notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, true);
                                    }
                                } else {
                                    // 原始请求无响应
                                    api.logging().raiseDebugEvent(String.format(
                                        "接口探测: %s %s (%s) %s - 无响应",
                                        finalMethod, finalHost, finalDisplayContentType, endpoint
                                    ));
                                    // ✅ 通知UI接口探测结果（无响应视为不存在）
                                    if (!runArjun && activeProbeTab != null) {
                                        notifyInterfaceResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, false);
                                    }
                                }
                            } catch (Exception sendError) {
                                api.logging().raiseErrorEvent("接口探测失败: " + sendError.getMessage());
                                sendError.printStackTrace();  // ✅ 添加堆栈跟踪，便于调试
                                // ✅ 通知UI接口探测结果（异常视为失败）
                                if (!runArjun && activeProbeTab != null) {
                                    try {
                                        SwingUtilities.invokeLater(() -> {
                                            activeProbeTab.addInterfaceDiscoveryResult(mainDomain, finalHost, endpoint, finalMethod, finalContentType, false, System.currentTimeMillis());
                                        });
                                    } catch (Exception e) {
                                        // 忽略错误
                                    }
                                }
                            }
                        });  // 异步并发执行，不阻塞循环
                    }
                    
                scannedCount++;
            }
            
            return scannedCount;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("扫描手动端点时出错: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * 构建请求（用于手动添加的端点）
     * ✅ 修复：正确处理GET、POST、POST+JSON三种请求类型
     */
    /**
     * 构建HTTP请求
     * @param url 请求URL
     * @param method HTTP方法
     * @param contentType Content-Type（可选）
     * @param originalRequest 原始请求（可选），如果提供则保留其请求头
     */
    private HttpRequest buildRequest(String url, String method, String contentType, HttpRequest originalRequest) {
        try {
            // ✅ 手动解析 URL，避免 URI 类无法处理未编码字符的问题
            String scheme = null;
            String host = null;
            int port = -1;
            String fullPath = null;
            
            // 解析 scheme
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) {
                api.logging().raiseErrorEvent("无法解析URL格式（缺少scheme）: " + url);
                return null;
            }
            scheme = url.substring(0, schemeEnd);
            
            // 解析 host 和 port
            int pathStart = url.indexOf('/', schemeEnd + 3);
            String hostPort;
            if (pathStart == -1) {
                hostPort = url.substring(schemeEnd + 3);
                fullPath = "/";
            } else {
                hostPort = url.substring(schemeEnd + 3, pathStart);
                // ✅ 修复：提取 path，去掉查询参数（避免包含未编码的特殊字符，如 JSON）
                int queryStart = url.indexOf('?', pathStart);
                if (queryStart > 0) {
                    fullPath = url.substring(pathStart, queryStart);
                } else {
                    fullPath = url.substring(pathStart);
                }
            }
            
            // 解析 port
            int portStart = hostPort.indexOf(':');
            if (portStart == -1) {
                host = hostPort;
                port = -1; // 使用默认端口
            } else {
                host = hostPort.substring(0, portStart);
                try {
                    port = Integer.parseInt(hostPort.substring(portStart + 1));
                } catch (NumberFormatException e) {
                    api.logging().raiseErrorEvent("无法解析端口号: " + hostPort.substring(portStart + 1));
                    return null;
                }
            }
            
            // 确保路径不为空
            if (fullPath.isEmpty()) {
                fullPath = "/";
            }
            
            // ✅ 修复：构建HttpService（包含host、port、scheme信息）
            burp.api.montoya.http.HttpService httpService;
            if (port == -1) {
                // 默认端口
                int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
                httpService = burp.api.montoya.http.HttpService.httpService(host, defaultPort, scheme.equalsIgnoreCase("https"));
            } else {
                httpService = burp.api.montoya.http.HttpService.httpService(host, port, scheme.equalsIgnoreCase("https"));
            }
            
            // 构建请求行
            String requestLine = method + " " + fullPath + " HTTP/1.1";
            
            // 构建请求头
            StringBuilder headers = new StringBuilder();
            
            // ✅ 修复：如果提供了原始请求，保留其请求头（除了需要调整的）
            if (originalRequest != null) {
                // 保留原始请求的所有请求头（除了 Host、Content-Length、Content-Type）
                for (var header : originalRequest.headers()) {
                    String headerName = header.name();
                    // 跳过需要根据新请求调整的请求头
                    if ("Host".equalsIgnoreCase(headerName) || 
                        "Content-Length".equalsIgnoreCase(headerName) ||
                        "Content-Type".equalsIgnoreCase(headerName)) {
                        continue;
                    }
                    // 保留其他请求头（如 Authorization、Cookie、X-Requested-With 等）
                    headers.append(headerName).append(": ").append(header.value()).append("\r\n");
                }
            }
            
            // 添加 Host 头（必须根据新URL设置）
            headers.append("Host: ").append(host);
            if (port != -1 && !(("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80))) {
                headers.append(":").append(port);
            }
            headers.append("\r\n");
            
            // 如果没有原始请求或原始请求没有 User-Agent，添加默认的 User-Agent
            if (originalRequest == null || originalRequest.headerValue("User-Agent") == null) {
                headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n");
            }
            
            // 如果没有原始请求或原始请求没有 Accept，添加默认的 Accept
            if (originalRequest == null || originalRequest.headerValue("Accept") == null) {
                headers.append("Accept: */*\r\n");
            }
            
            // 构建请求体
            String body = null;
            
            if ("GET".equalsIgnoreCase(method)) {
                // GET请求：不需要Content-Type和Body
                // 参数在URL的query string中（如果有）
            } else if ("POST".equalsIgnoreCase(method)) {
                // POST请求：需要Content-Type和Body
                if (contentType != null) {
                    headers.append("Content-Type: ").append(contentType).append("\r\n");
                    
                    if ("application/x-www-form-urlencoded".equals(contentType)) {
                        // 表单提交：空的body（或者可以添加一个测试参数）
                        body = "";
                        headers.append("Content-Length: 0\r\n");
                    } else if (contentType.contains("application/json")) {
                        // ✅ 修复：JSON类型（包括 application/json 和 application/json; charset=utf-8 等）使用空JSON对象
                        body = "{}";
                        headers.append("Content-Length: ").append(body.length()).append("\r\n");
                    } else {
                        // 其他Content-Type：空body
                        body = "";
                        headers.append("Content-Length: 0\r\n");
                    }
                } else {
                    // 如果没有指定Content-Type，默认使用form-urlencoded
                    headers.append("Content-Type: application/x-www-form-urlencoded\r\n");
                    body = "";
                    headers.append("Content-Length: 0\r\n");
                }
            }
            
            // 构建完整请求
            StringBuilder requestStr = new StringBuilder();
            requestStr.append(requestLine).append("\r\n");
            requestStr.append(headers);
            requestStr.append("\r\n");
            if (body != null) {
                requestStr.append(body);
            }
            
            // ✅ 修复：使用HttpService构建请求，确保包含服务信息
            HttpRequest request = HttpRequest.httpRequest(httpService, requestStr.toString());
            
            return request;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建请求失败: " + e.getMessage());
            e.printStackTrace();  // ✅ 添加堆栈跟踪，便于调试
            return null;
        }
    }
    
    /**
     * 构建HTTP请求（重载方法，兼容旧代码）
     */
    private HttpRequest buildRequest(String url, String method, String contentType) {
        return buildRequest(url, method, contentType, null);
    }
    
    /**
     * ✅ 构建用于接口探测的请求（不带任何参数）
     * 确保接口探测时不包含任何 URL 参数或 Body 参数
     * 
     * @param url 请求URL（应该已经通过 stripQuery 去掉查询参数）
     * @param method HTTP方法
     * @param contentType Content-Type（仅用于设置请求头，不包含参数）
     * @param originalRequest 原始请求模板（仅用于保留请求头，不包含参数）
     * @return 不带任何参数的HTTP请求
     */
    private HttpRequest buildRequestForInterfaceDiscovery(String url, String method, String contentType, HttpRequest originalRequest) {
        try {
            // ✅ 手动解析 URL，确保不包含查询参数
            String scheme = null;
            String host = null;
            int port = -1;
            String fullPath = null;
            
            // 解析 scheme
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) {
                api.logging().raiseErrorEvent("无法解析URL格式（缺少scheme）: " + url);
                return null;
            }
            scheme = url.substring(0, schemeEnd);
            
            // 解析 host 和 port
            int pathStart = url.indexOf('/', schemeEnd + 3);
            String hostPort;
            if (pathStart == -1) {
                hostPort = url.substring(schemeEnd + 3);
                fullPath = "/";
            } else {
                hostPort = url.substring(schemeEnd + 3, pathStart);
                fullPath = url.substring(pathStart);
            }
            
            // ✅ 确保路径不包含查询参数
            int queryIndex = fullPath.indexOf('?');
            if (queryIndex > 0) {
                fullPath = fullPath.substring(0, queryIndex);
            }
            
            // 解析 port
            int portStart = hostPort.indexOf(':');
            if (portStart == -1) {
                host = hostPort;
                port = -1; // 使用默认端口
            } else {
                host = hostPort.substring(0, portStart);
                try {
                    port = Integer.parseInt(hostPort.substring(portStart + 1));
                } catch (NumberFormatException e) {
                    api.logging().raiseErrorEvent("无法解析端口号: " + hostPort.substring(portStart + 1));
                    return null;
                }
            }
            
            // 确保路径不为空
            if (fullPath.isEmpty()) {
                fullPath = "/";
            }
            
            // ✅ 构建HttpService
            burp.api.montoya.http.HttpService httpService;
            if (port == -1) {
                int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
                httpService = burp.api.montoya.http.HttpService.httpService(host, defaultPort, scheme.equalsIgnoreCase("https"));
            } else {
                httpService = burp.api.montoya.http.HttpService.httpService(host, port, scheme.equalsIgnoreCase("https"));
            }
            
            // ✅ 构建请求行（不包含查询参数）
            String requestLine = method + " " + fullPath + " HTTP/1.1";
            
            // ✅ 构建请求头（只保留必要的请求头，不包含参数相关的头）
            StringBuilder headers = new StringBuilder();
            
            // ✅ 如果提供了原始请求，只保留会话相关的请求头（如 Cookie、Authorization），不包含参数相关的头
            if (originalRequest != null) {
                for (var header : originalRequest.headers()) {
                    String headerName = header.name();
                    // 跳过需要根据新请求调整的请求头
                    if ("Host".equalsIgnoreCase(headerName) || 
                        "Content-Length".equalsIgnoreCase(headerName) ||
                        "Content-Type".equalsIgnoreCase(headerName)) {
                        continue;
                    }
                    // ✅ 只保留会话相关的请求头（Cookie、Authorization 等），不包含参数相关的头
                    // 注意：这里不会复制任何参数，因为参数是在请求体或URL查询字符串中的
                    headers.append(headerName).append(": ").append(header.value()).append("\r\n");
                }
            }
            
            // 添加 Host 头
            headers.append("Host: ").append(host);
            if (port != -1 && !(("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80))) {
                headers.append(":").append(port);
            }
            headers.append("\r\n");
            
            // 如果没有原始请求或原始请求没有 User-Agent，添加默认的 User-Agent
            if (originalRequest == null || originalRequest.headerValue("User-Agent") == null) {
                headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n");
            }
            
            // 如果没有原始请求或原始请求没有 Accept，添加默认的 Accept
            if (originalRequest == null || originalRequest.headerValue("Accept") == null) {
                headers.append("Accept: */*\r\n");
            }
            
            // ✅ 构建请求体（接口探测时不包含任何参数）
            String body = null;
            
            if ("GET".equalsIgnoreCase(method)) {
                // GET请求：不需要Content-Type和Body，也不包含任何参数
            } else if ("POST".equalsIgnoreCase(method)) {
                // POST请求：需要Content-Type，但Body不包含任何参数
                if (contentType != null) {
                    headers.append("Content-Type: ").append(contentType).append("\r\n");
                } else {
                    // 如果没有指定Content-Type，默认使用form-urlencoded
                    headers.append("Content-Type: application/x-www-form-urlencoded\r\n");
                }
                // ✅ 修复：接口探测时，根据Content-Type设置Body
                // - JSON类型：使用空JSON对象 {}
                // - 其他类型：Body为空字符串
                if (contentType != null && contentType.contains("application/json")) {
                    body = "{}";  // ✅ POST + JSON 时使用空JSON对象
                    headers.append("Content-Length: ").append(body.length()).append("\r\n");
                } else {
                    body = "";  // ✅ 其他POST请求Body为空
                    headers.append("Content-Length: 0\r\n");
                }
            }
            
            // 构建完整请求
            StringBuilder requestStr = new StringBuilder();
            requestStr.append(requestLine).append("\r\n");
            requestStr.append(headers);
            requestStr.append("\r\n");
            if (body != null) {
                requestStr.append(body);
            }
            
            // ✅ 构建请求，确保不包含任何参数
            HttpRequest request = HttpRequest.httpRequest(httpService, requestStr.toString());
            
            // ✅ 验证：确保请求不包含任何参数
            if (request.parameters().size() > 0) {
                api.logging().raiseDebugEvent("警告：接口探测请求包含参数，这不应该发生");
            }
            
            return request;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建接口探测请求失败: " + e.getMessage());
            e.printStackTrace();  // ✅ 添加堆栈跟踪，便于调试
            return null;
        }
    }
    
    /**
     * ✅ 辅助方法：获取URL的模板请求（用于保留请求头）
     * @param url 请求URL
     * @return 模板请求，如果构建失败返回null
     */
    private HttpRequest getTemplateRequestForUrl(String url) {
        try {
            // ✅ 修复：手动解析 URL，避免 URI 类无法处理未编码的特殊字符
            String host = null;
            String scheme = null;
            int port = -1;
            String path = null;
            String query = null;
            
            try {
                // 先尝试使用 URI 解析（对于标准 URL）
                java.net.URI urlUri = new java.net.URI(url);
                host = urlUri.getHost();
                scheme = urlUri.getScheme();
                port = urlUri.getPort();
                path = urlUri.getPath();
                query = urlUri.getQuery();
            } catch (Exception e) {
                // ✅ 如果 URI 解析失败（可能包含未编码的特殊字符），使用手动解析
                int schemeEnd = url.indexOf("://");
                if (schemeEnd == -1) {
                    // 如果解析失败，尝试使用buildRequest
                    return buildRequest(url, "GET", null);
                }
                scheme = url.substring(0, schemeEnd);
                
                int pathStart = url.indexOf('/', schemeEnd + 3);
                String hostPort;
                if (pathStart == -1) {
                    hostPort = url.substring(schemeEnd + 3);
                    path = "/";
                } else {
                    hostPort = url.substring(schemeEnd + 3, pathStart);
                    int queryStart = url.indexOf('?', pathStart);
                    if (queryStart > 0) {
                        path = url.substring(pathStart, queryStart);
                        query = url.substring(queryStart + 1);
                    } else {
                        path = url.substring(pathStart);
                    }
                }
                
                int portStart = hostPort.indexOf(':');
                if (portStart == -1) {
                    host = hostPort;
                    port = -1;
                } else {
                    host = hostPort.substring(0, portStart);
                    try {
                        port = Integer.parseInt(hostPort.substring(portStart + 1));
                    } catch (NumberFormatException ex) {
                        port = -1;
                    }
                }
            }
            
            if (host == null || host.isEmpty() || "null".equals(host)) {
                // 如果解析失败，尝试使用buildRequest
                return buildRequest(url, "GET", null);
            }
            
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            
            if (query != null && !query.isEmpty()) {
                // URL中包含查询参数，构建一个临时请求来提取这些参数
                burp.api.montoya.http.HttpService httpService = burp.api.montoya.http.HttpService.httpService(
                    host, 
                    port > 0 ? port : ("https".equalsIgnoreCase(scheme) ? 443 : 80),
                    "https".equalsIgnoreCase(scheme)
                );
                return burp.api.montoya.http.message.requests.HttpRequest.httpRequest(
                    httpService, 
                    "GET " + path + "?" + query + " HTTP/1.1\r\nHost: " + host + "\r\n\r\n"
                );
            } else {
                // URL中没有查询参数，构建一个基础请求
                return buildRequest(url, "GET", null);
            }
        } catch (Exception e) {
            // 如果解析失败，尝试使用buildRequest
            try {
                return buildRequest(url, "GET", null);
            } catch (Exception e2) {
                // 忽略错误，返回null
                return null;
            }
        }
    }
    
    /**
     * ✅ 构建带随机路径的请求（用于验证接口是否存在）
     * 使用随机路径对比原始路径，判断接口是否真实存在
     * @param originalUrl 原始URL
     * @param method HTTP方法
     * @param contentType Content-Type
     * @param randomPath 随机路径
     * @param originalRequest 原始请求（可选），如果提供则保留其请求头
     */
    private HttpRequest buildRequestWithRandomPath(String originalUrl, String method, String contentType, String randomPath, HttpRequest originalRequest) {
        try {
            String host = null;
            String scheme = null;
            int port = -1;
            
            try {
                // 先尝试使用 URI 解析（对于标准 URL）
                URI originalUri = new URI(originalUrl);
                host = originalUri.getHost();
                scheme = originalUri.getScheme();
                port = originalUri.getPort();
            } catch (Exception e) {
                // ✅ 如果 URI 解析失败（可能包含未编码的特殊字符），使用手动解析
                int schemeEnd = originalUrl.indexOf("://");
                if (schemeEnd == -1) {
                    api.logging().raiseErrorEvent("构建随机路径请求失败: 无法解析URL格式（缺少scheme） - " + originalUrl);
                    return null;
                }
                scheme = originalUrl.substring(0, schemeEnd);
                
                int pathStart = originalUrl.indexOf('/', schemeEnd + 3);
                String hostPort;
                if (pathStart == -1) {
                    hostPort = originalUrl.substring(schemeEnd + 3);
                } else {
                    hostPort = originalUrl.substring(schemeEnd + 3, pathStart);
                }
                
                int portStart = hostPort.indexOf(':');
                if (portStart == -1) {
                    host = hostPort;
                    port = -1;
                } else {
                    host = hostPort.substring(0, portStart);
                    try {
                        port = Integer.parseInt(hostPort.substring(portStart + 1));
                    } catch (NumberFormatException ex) {
                        port = -1;
                    }
                }
            }
            
            if (host == null || host.isEmpty() || "null".equals(host)) {
                api.logging().raiseErrorEvent("构建随机路径请求失败: 无法确定 host - " + originalUrl);
                return null;
            }
            
            // 构建随机路径URL
            String randomUrl;
            if (port != -1) {
                randomUrl = scheme + "://" + host + ":" + port + randomPath;
            } else {
                randomUrl = scheme + "://" + host + randomPath;
            }
            
            // ✅ 修复：使用原始请求保留请求头
            return buildRequest(randomUrl, method, contentType, originalRequest);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建随机路径请求失败: " + e.getMessage());
            e.printStackTrace();  // ✅ 添加堆栈跟踪，便于调试
            return null;
        }
    }
    
    /**
     * 构建带随机路径的请求（重载方法，兼容旧代码）
     */
    private HttpRequest buildRequestWithRandomPath(String originalUrl, String method, String contentType, String randomPath) {
        return buildRequestWithRandomPath(originalUrl, method, contentType, randomPath, null);
    }
    
    /**
     * 获取请求的Content-Type
     */
    private String getContentType(HttpRequest request) {
        for (var header : request.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
    
    /**
     * ✅ 异步验证接口是否存在（通过双随机路径对比建立基准）
     * ✅ 优化：异步执行，避免阻塞
     */
    private CompletableFuture<Boolean> validateEndpointWithRandomPathAsync(
            String url, HttpRequest originalRequest, 
            HttpRequestResponse originalResponse, int originalStatusCode, 
            String originalResponseBody, String method, String host, 
            String contentType, String endpoint, String mainDomain) {
        return CompletableFuture.supplyAsync(() -> {
            return validateEndpointWithRandomPath(url, originalRequest, originalResponse, 
                originalStatusCode, originalResponseBody, method, host, contentType, endpoint, mainDomain);
        });
    }
    
    /**
     * ✅ 验证接口是否存在（通过双随机路径对比建立基准）
     * ✅ 优化：使用双随机路径建立"不存在"的基准响应，更准确地区分泛解析、反射和真实接口
     * 
     * 逻辑：
     * 1. 首次建立基准：发送2个随机路径，对比响应确定"不存在"的响应特征
     * 2. 后续接口判断：与基准响应对比，而非与单个随机路径对比
     * 3. 基准类型：
     *    - 两个随机路径都返回404 → 基准：404（真实接口应返回非404）
     *    - 两个随机路径响应相同（去除反射后）→ 基准：泛解析响应
     *    - 两个随机路径响应不同 → 可能是反射或其他，需要进一步判断
     * 
     * @param url 原始URL
     * @param originalRequest 原始请求
     * @param originalResponse 原始响应
     * @param originalStatusCode 原始状态码
     * @param originalResponseBody 原始响应体
     * @param method HTTP方法（原始请求的方法）
     * @param host 主机名
     * @param contentType Content-Type（原始请求的Content-Type）
     * @param endpoint 端点路径
     * @param mainDomain 主域名（用于缓存key）
     * @return true表示接口存在，false表示接口不存在
     */
    private boolean validateEndpointWithRandomPath(
            String url, HttpRequest originalRequest, 
            HttpRequestResponse originalResponse, int originalStatusCode, 
            String originalResponseBody, String method, String host, 
            String contentType, String endpoint, String mainDomain) {
        // ✅ 优先使用接口结果缓存，减少重复验证
        String cacheKeyEndpoint = buildEndpointKey(method, host, contentType, endpoint);
        Boolean cachedExist = endpointExistenceCache.get(cacheKeyEndpoint);
        if (cachedExist != null) {
            notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, cachedExist);
            return cachedExist;
        }
        
        // ✅ 提取原始路径（用于去除反射）
        String originalPath = extractPathFromUrl(url);
        
        // ✅ 生成缓存key：使用具体Host（含子域）+方法+ContentType，避免不同子域复用基线
        String domainKey = (host != null && !host.isEmpty()) ? host : mainDomain;
        String cacheKey = domainKey + "|" + method + "|" + (contentType != null ? contentType : "null");
        
        long currentTime = System.currentTimeMillis();
        
        // ✅ 检查缓存，如果存在且未过期，使用基准判断
        RandomPathBaseline baseline = randomPathBaselineCache.get(cacheKey);
        if (baseline != null && (currentTime - baseline.timestamp) < RANDOM_PATH_CACHE_TTL) {
            return validateWithBaseline(baseline, originalPath, originalStatusCode, originalResponseBody,
                                      originalRequest, originalResponse, method, host, contentType, 
                                      endpoint, mainDomain);
        }
        
        // ✅ 修复：使用双重检查锁定模式，确保同一cacheKey只有一个线程在建立基准
        // 获取或创建该cacheKey对应的锁对象
        Object lock = baselineLocks.computeIfAbsent(cacheKey, k -> new Object());
        
        synchronized (lock) {
            try {
                // ✅ 再次检查缓存（双重检查），可能其他线程已经建立好了
                baseline = randomPathBaselineCache.get(cacheKey);
                if (baseline != null && (currentTime - baseline.timestamp) < RANDOM_PATH_CACHE_TTL) {
                    // 其他线程已经建立好了基准，直接使用
                    return validateWithBaseline(baseline, originalPath, originalStatusCode, originalResponseBody,
                                              originalRequest, originalResponse, method, host, contentType, 
                                              endpoint, mainDomain);
                }
                
                // ✅ 缓存不存在或已过期，当前线程负责发送2个随机路径建立基准
                return establishBaselineAndValidate(url, originalRequest, originalResponse, originalStatusCode,
                                                   originalResponseBody, originalPath, method, host, contentType,
                                                   endpoint, mainDomain, cacheKey, currentTime);
            } finally {
                // ✅ 建立完成后，清理锁（使用try-finally确保总是清理，避免内存泄漏）
                baselineLocks.remove(cacheKey);
            }
        }
    }
    
    /**
     * ✅ 使用已建立的基准判断接口是否存在
     */
    private boolean validateWithBaseline(RandomPathBaseline baseline, String originalPath,
                                       int originalStatusCode, String originalResponseBody,
                                       HttpRequest originalRequest, HttpRequestResponse originalResponse,
                                       String method, String host, String contentType, String endpoint,
                                       String mainDomain) {
        // ✅ 场景1：基准是两个随机路径都返回404
        if (baseline.statusCode1 == 404 && baseline.statusCode2 == 404) {
            // 如果原始路径也返回404，说明接口不存在
            if (originalStatusCode == 404) {
                api.logging().raiseDebugEvent(String.format(
                    "接口不存在: %s %s (%s) %s - 状态码 404 (基准：两个随机路径都返回404，使用缓存)",
                    method, host, contentType != null ? contentType : "N/A", endpoint
                ));
                notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, false);
                return false;
            }
            // 如果原始路径返回非404，说明接口存在
            api.logging().raiseInfoEvent(String.format(
                "✅ 接口存在: %s %s (%s) %s - 状态码 %d (基准：两个随机路径都返回404，使用缓存)",
                method, host, contentType != null ? contentType : "N/A", endpoint, originalStatusCode
            ));
            collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
            return true;
        }
        
        // ✅ 场景2：基准是泛解析（两个随机路径响应相同）
        if (baseline.isWildcard && baseline.baselineStatusCode != null && baseline.baselineResponseBody != null) {
            // 去除反射后对比
            String cleanedOriginalBody = removeReflectedPath(originalResponseBody, originalPath, baseline.randomPath1);
            String cleanedBaselineBody = baseline.baselineResponseBody;
            
            // 对比状态码和响应体
            if (originalStatusCode == Integer.parseInt(baseline.baselineStatusCode) &&
                cleanedOriginalBody != null && cleanedBaselineBody != null &&
                cleanedOriginalBody.equals(cleanedBaselineBody)) {
                // 与基准响应相同，说明接口不存在（泛解析）
                api.logging().raiseDebugEvent(String.format(
                    "接口不存在: %s %s (%s) %s - 状态码 %d，与基准响应相同 (泛解析，使用缓存)",
                    method, host, contentType != null ? contentType : "N/A", endpoint, originalStatusCode
                ));
                notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, false);
                return false;
            } else {
                // 与基准响应不同，说明接口存在
                api.logging().raiseInfoEvent(String.format(
                    "✅ 接口存在: %s %s (%s) %s - 状态码 %d (与基准响应不同，验证通过，使用缓存)",
                    method, host, contentType != null ? contentType : "N/A", endpoint, originalStatusCode
                ));
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
        }
        
        // ✅ 场景3：两个随机路径响应不同（可能是反射或其他复杂情况）
        // 这种情况下，需要与两个随机路径都对比，如果与任一不同，认为接口存在
        String cleanedOriginalBody = removeReflectedPath(originalResponseBody, originalPath, baseline.randomPath1);
        String cleanedBaseline1 = removeReflectedPath(baseline.responseBody1, baseline.randomPath1, baseline.randomPath2);
        String cleanedBaseline2 = removeReflectedPath(baseline.responseBody2, baseline.randomPath2, baseline.randomPath1);
        
        // 如果原始响应与两个随机路径响应都不同，认为接口存在
        boolean differentFromBaseline1 = !cleanedOriginalBody.equals(cleanedBaseline1) || 
                                        originalStatusCode != baseline.statusCode1;
        boolean differentFromBaseline2 = !cleanedOriginalBody.equals(cleanedBaseline2) || 
                                        originalStatusCode != baseline.statusCode2;
        
        if (differentFromBaseline1 && differentFromBaseline2) {
            api.logging().raiseInfoEvent(String.format(
                "✅ 接口存在: %s %s (%s) %s - 状态码 %d (与两个随机路径响应都不同，验证通过，使用缓存)",
                method, host, contentType != null ? contentType : "N/A", endpoint, originalStatusCode
            ));
            collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
            return true;
        } else {
            // 与至少一个随机路径响应相同，保守处理为接口不存在
            api.logging().raiseDebugEvent(String.format(
                "接口不存在: %s %s (%s) %s - 状态码 %d (与随机路径响应相同，使用缓存)",
                method, host, contentType != null ? contentType : "N/A", endpoint, originalStatusCode
            ));
            notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, false);
            return false;
        }
    }
    
    /**
     * ✅ 建立基准并验证接口是否存在
     */
    private boolean establishBaselineAndValidate(String url, HttpRequest originalRequest,
                                                HttpRequestResponse originalResponse, int originalStatusCode,
                                                String originalResponseBody, String originalPath,
                                                String method, String host, String contentType,
                                                String endpoint, String mainDomain, String cacheKey,
                                                long currentTime) {
        // ✅ 生成2个随机路径
        String randomPath1 = "/xprobe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String randomPath2 = "/xprobe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        // ✅ 检查HTTP服务是否可用
        if (api.http() == null) {
            api.logging().raiseErrorEvent("HTTP服务不可用，无法发送随机路径请求");
            // 保守处理，认为接口存在
            collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
            return true;
        }
        
        try {
            // ✅ 并发发送两个随机路径请求
            // ✅ 修复：传入原始请求以保留请求头
            HttpRequest randomPathRequest1 = buildRequestWithRandomPath(url, method, contentType, randomPath1, originalRequest);
            if (randomPathRequest1 == null) {
                api.logging().raiseDebugEvent("无法构建第一个随机路径请求，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            HttpRequest randomPathRequest2 = buildRequestWithRandomPath(url, method, contentType, randomPath2, originalRequest);
            if (randomPathRequest2 == null) {
                api.logging().raiseDebugEvent("无法构建第二个随机路径请求，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
            java.util.concurrent.CompletableFuture<HttpRequestResponse> f1 = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return api.http().sendRequest(randomPathRequest1);
                } catch (Exception e) {
                    return null;
                }
            });
            java.util.concurrent.CompletableFuture<HttpRequestResponse> f2 = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return api.http().sendRequest(randomPathRequest2);
                } catch (Exception e) {
                    return null;
                }
            });
            
            HttpRequestResponse randomResponse1 = f1.join();
            if (randomResponse1 == null || randomResponse1.response() == null) {
                api.logging().raiseDebugEvent("第一个随机路径请求无响应，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            HttpRequestResponse randomResponse2 = f2.join();
            if (randomResponse2 == null || randomResponse2.response() == null) {
                api.logging().raiseDebugEvent("第二个随机路径请求无响应，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
            int randomStatusCode1 = randomResponse1.response().statusCode();
            String randomResponseBody1 = randomResponse1.response().bodyToString();
            int randomStatusCode2 = randomResponse2.response().statusCode();
            String randomResponseBody2 = randomResponse2.response().bodyToString();
            
            // ✅ 建立基准
            boolean isWildcard = false;
            String baselineStatusCode = null;
            String baselineResponseBody = null;
            
            // ✅ 场景1：两个随机路径都返回404
            if (randomStatusCode1 == 404 && randomStatusCode2 == 404) {
                // 基准：404（真实接口应返回非404）
                baselineStatusCode = "404";
                baselineResponseBody = null;
                isWildcard = false;
            }
            // ✅ 场景2：两个随机路径响应相同（去除反射后）→ 泛解析
            else if (randomStatusCode1 == randomStatusCode2 && randomStatusCode1 >= 200 && randomStatusCode1 < 500) {
                String cleanedBody1 = removeReflectedPath(randomResponseBody1, randomPath1, randomPath2);
                String cleanedBody2 = removeReflectedPath(randomResponseBody2, randomPath2, randomPath1);
                
                if (cleanedBody1 != null && cleanedBody2 != null && cleanedBody1.equals(cleanedBody2)) {
                    // 两个随机路径响应相同，建立泛解析基准
                    isWildcard = true;
                    baselineStatusCode = String.valueOf(randomStatusCode1);
                    baselineResponseBody = cleanedBody1;
                }
            }
            // ✅ 场景3：两个随机路径响应不同（可能是反射或其他）
            // 这种情况下，isWildcard=false，baselineStatusCode和baselineResponseBody为null
            
            // ✅ 缓存基准
            RandomPathBaseline baseline = new RandomPathBaseline(
                randomPath1, randomPath2,
                randomStatusCode1, randomStatusCode2,
                randomResponseBody1, randomResponseBody2,
                currentTime, isWildcard,
                baselineStatusCode, baselineResponseBody
            );
            randomPathBaselineCache.put(cacheKey, baseline);
            
            api.logging().raiseInfoEvent(String.format(
                "✅ 建立随机路径基准: %s %s (%s) - 类型: %s",
                method, host != null ? host : mainDomain, contentType != null ? contentType : "N/A",
                isWildcard ? "泛解析" : (randomStatusCode1 == 404 && randomStatusCode2 == 404 ? "404基准" : "复杂场景")
            ));
            
            // ✅ 使用新建立的基准判断当前接口
            return validateWithBaseline(baseline, originalPath, originalStatusCode, originalResponseBody,
                                      originalRequest, originalResponse, method, host, contentType,
                                      endpoint, mainDomain);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("建立随机路径基准失败: " + e.getMessage());
            // 保守处理，认为接口存在
            collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
            return true;
        }
    }
    
    /**
     * ✅ 收集参数并通知UI接口探测结果
     */
    private void collectAndNotify(HttpRequest originalRequest, HttpRequestResponse originalResponse,
                                  String mainDomain, String host, String endpoint, String method, String contentType,
                                  boolean exists) {
        if (originalResponse != null) {
            parameterCollector.collectFromRequest(originalRequest);
            if (originalResponse.response() != null) {
                parameterCollector.collectFromResponse(originalRequest, originalResponse.response());
            }
        }
        notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, exists);
        
        // ✅ 新增：如果启用被动扫描规则，将主动探测的结果传递给被动扫描规则
        if (enablePassiveScanRulesForActiveProbe && originalRequest != null && originalResponse != null) {
            triggerPassiveScanForActiveProbeResult(originalRequest, originalResponse);
        }
    }
    
    /**
     * ✅ 将主动探测的结果传递给被动扫描规则
     * 功能：类似于 RequestHandler.handleHttpResponseReceived() 的逻辑
     */
    private void triggerPassiveScanForActiveProbeResult(HttpRequest request, HttpRequestResponse responseReceived) {
        try {
            // 检查被动扫描是否启用（通过检查是否有启用的配置）
            if (configManager == null || configManager.getEnabledConfigurations().isEmpty()) {
                return;
            }
            
            // 检查 TaskScheduler 是否已设置
            if (taskScheduler == null) {
                api.logging().raiseDebugEvent("⚠️ TaskScheduler未设置，无法触发被动扫描规则");
                return;
            }
            
            // 检查请求是否应该扫描（使用 GlobalFilter）
            if (globalFilter != null && !globalFilter.shouldProcessPassive(request.url())) {
                return;
            }
            
            // 创建请求上下文
            RequestContext context = new RequestContext(
                "ACTIVE_PROBE",  // 工具来源标记为主动探测
                request.method(),
                request.url(),
                request.toString().hashCode()
            );
            
            // 收集扫描任务
            List<ScanTask> scanTasks = new ArrayList<>();
            for (Configuration config : configManager.getEnabledConfigurations()) {
                if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                    ScanTask task = new ScanTask(null, config, request, context);
                    scanTasks.add(task);
                }
            }
            
            // 调度扫描任务
            if (!scanTasks.isEmpty()) {
                // ✅ 缓存原始响应（如果 responseCache 可用）
                // 这样被动扫描器才能找到原始响应进行对比
                if (responseCache != null && responseReceived != null && responseReceived.response() != null) {
                    try {
                        responseCache.put(
                            request.method(),
                            request.url(),
                            responseReceived.response()  // ✅ 提取 HttpResponse 对象
                        );
                        api.logging().raiseDebugEvent(String.format(
                            "✅ 已缓存主动探测的原始响应: %s %s",
                            request.method(), request.url()
                        ));
                    } catch (Exception e) {
                        api.logging().raiseDebugEvent("缓存原始响应失败: " + e.getMessage());
                    }
                }
                
                taskScheduler.scheduleScan(scanTasks);
                api.logging().raiseDebugEvent(String.format(
                    "✅ 主动探测结果已传递给被动扫描规则: %s %s (任务数: %d)",
                    request.method(), request.url(), scanTasks.size()
                ));
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发被动扫描规则失败: " + e.getMessage());
        }
    }
    
    // ===== 接口探测时间戳缓存（首包发送时刻） =====
    private final java.util.concurrent.ConcurrentHashMap<String, Long> interfaceSendTs = new java.util.concurrent.ConcurrentHashMap<>();
    private String buildEndpointKey(String method, String host, String contentType, String endpoint) {
        return (method == null ? "" : method.toUpperCase()) + "|" +
               (host == null ? "" : host) + "|" +
               normalizeContentType(contentType) + "|" +
               (endpoint == null || endpoint.isEmpty() ? "/" : endpoint);
    }
    
    // 接口结果去重与缓存
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> endpointExistenceCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> displayedInterfaceKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> pendingInterfaceKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "application/x-www-form-urlencoded";
        String lower = contentType.toLowerCase().trim();
        int p = lower.indexOf(';');
        if (p > 0) lower = lower.substring(0, p).trim();
        if (lower.contains("json")) return "application/json";
        if (lower.contains("xml")) return "application/xml";
        if (lower.contains("multipart")) return "multipart/form-data";
        if (lower.contains("form")) return "application/x-www-form-urlencoded";
        return lower;
    }

    // 去掉URL中的查询串（?后面的部分）
    private String stripQuery(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            int port = uri.getPort();
            // ✅ 修复：如果 host 为 null 或 "null"，使用手动解析
            if (host == null || host.isEmpty() || "null".equals(host)) {
                // 手动解析 URL，去掉查询参数
                int idx = url.indexOf('?');
                return idx > 0 ? url.substring(0, idx) : url;
            }
            boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443) ||
                                  ("http".equalsIgnoreCase(scheme) && port == 80);
            String portStr = (port > 0 && !defaultPort) ? (":" + port) : "";
            return scheme + "://" + host + portStr + (path != null && !path.isEmpty() ? path : "/");
        } catch (Exception e) {
            int idx = url.indexOf('?');
            return idx > 0 ? url.substring(0, idx) : url;
        }
    }

    // 过滤无效参数名，确保只保留 [a-zA-Z0-9_-]，并去重
    private java.util.Set<String> sanitizeParamNames(java.util.Set<String> names) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (names == null) return out;
        for (String n : names) {
            if (n == null) continue;
            String t = n.trim();
            if (t.isEmpty()) continue;
            // 只保留合法参数名，避免注入到 URL/Body 时污染请求
            if (t.matches("[a-zA-Z0-9_-]+")) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * ✅ 通知UI接口探测结果
     */
    private void notifyInterfaceResult(String mainDomain, String host, String endpoint, String method,
                                      String contentType, boolean exists) {
        String key = buildEndpointKey(method, host, contentType, endpoint);
        endpointExistenceCache.put(key, exists);
        pendingInterfaceKeys.remove(key);
        
        // 去重：同一结果只展示一次
        if (!displayedInterfaceKeys.add(key)) {
            return;
        }
        if (activeProbeTab != null) {
            try {
                Long tsObj = interfaceSendTs.remove(key);
                long ts = tsObj != null ? tsObj : System.currentTimeMillis();
                if (SwingUtilities.isEventDispatchThread()) {
                    activeProbeTab.addOrUpdateInterfaceDiscoveryResult(mainDomain, host, endpoint, method, contentType, exists, ts);
                } else {
                    SwingUtilities.invokeAndWait(() -> activeProbeTab.addOrUpdateInterfaceDiscoveryResult(mainDomain, host, endpoint, method, contentType, exists, ts));
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }
    
    // ✅ 在发起 Arjun 扫描前，向表格插入"⏳ 探测中"的占位行（秒显）
    private void notifyArjunProgress(String mainDomain, String host, String endpoint, String parameterType) {
        if (activeProbeTab != null) {
            try {
                long ts = System.currentTimeMillis();
                if (SwingUtilities.isEventDispatchThread()) {
                    activeProbeTab.addArjunProgress(mainDomain, host, endpoint, parameterType, ts);
                } else {
                    SwingUtilities.invokeAndWait(() -> activeProbeTab.addArjunProgress(mainDomain, host, endpoint, parameterType, ts));
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }

    // ✅ 接口探测占位：秒显
    private void notifyInterfaceProgress(String mainDomain, String host, String endpoint, String method, String contentType) {
        String key = buildEndpointKey(method, host, contentType, endpoint);
        // 若已有缓存结果，直接用缓存更新UI，避免重复占位与发包
        Boolean cached = endpointExistenceCache.get(key);
        if (cached != null) {
            notifyInterfaceResult(mainDomain, host, endpoint, method, contentType, cached);
            return;
        }
        // 若已在进行中，直接返回
        if (!pendingInterfaceKeys.add(key)) {
            return;
        }
        if (activeProbeTab != null) {
            try {
                long ts = System.currentTimeMillis();
                // 记录首包发送时刻，后续结果沿用该时间，避免时间跳变
                interfaceSendTs.put(key, ts);
                if (SwingUtilities.isEventDispatchThread()) {
                    activeProbeTab.addInterfaceProgress(mainDomain, host, endpoint, method, contentType, ts);
                } else {
                    SwingUtilities.invokeAndWait(() -> activeProbeTab.addInterfaceProgress(mainDomain, host, endpoint, method, contentType, ts));
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }
    
    /**
     * ✅ 从URL中提取路径部分（用于接口探测时的反射去除）
     * 例如：https://example.com/api/user/info?id=123 -> /api/user/info
     */
    private String extractPathFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            return path;
        } catch (Exception e) {
            // 如果解析失败，尝试简单提取
            try {
                int pathStart = url.indexOf("://");
                if (pathStart != -1) {
                    pathStart = url.indexOf("/", pathStart + 3);
                    if (pathStart != -1) {
                        int queryStart = url.indexOf("?", pathStart);
                        if (queryStart != -1) {
                            return url.substring(pathStart, queryStart);
                        } else {
                            return url.substring(pathStart);
                        }
                    }
                }
            } catch (Exception e2) {
                // 忽略错误
            }
            return "/";
        }
    }
    
    /**
     * ✅ 从响应体中移除反射的路径字符
     * 避免路径反射导致的误判（如果路径出现在响应中，需要移除后再对比）
     * 
     * @param responseBody 响应体
     * @param originalPath 原始路径（如 /api/user/info）
     * @param randomPath 随机路径（如 /xprobe_abc123def456）
     * @return 去除路径反射后的响应体
     */
    private String removeReflectedPath(String responseBody, String originalPath, String randomPath) {
        if (responseBody == null) {
            return "";
        }
        
        String cleaned = responseBody;
        
        // 去除原始路径的反射
        if (originalPath != null && originalPath.length() > 1) {  // 路径长度大于1（至少是"/"）
            // 转义特殊字符，使用正则表达式替换
            String escaped = Pattern.quote(originalPath);
            cleaned = cleaned.replaceAll(escaped, "");
        }
        
        // 去除随机路径的反射
        if (randomPath != null && randomPath.length() > 1) {
            String escaped = Pattern.quote(randomPath);
            cleaned = cleaned.replaceAll(escaped, "");
        }
        
        return cleaned;
    }
    
    /**
     * 按主域名分组请求
     */
    private Map<String, List<HttpRequest>> groupRequestsByMainDomain(
            List<HttpRequestResponse> requestResponses) {
        
        Map<String, List<HttpRequest>> domainToRequests = new HashMap<>();
        int filteredCount = 0;
        
        for (HttpRequestResponse rr : requestResponses) {
            HttpRequest req = rr.request();
            String url = req.url();
            
            // 1. 跳过 Arjun 自己产生的流量
            boolean isArjunTraffic = false;
            for (var header : req.headers()) {
                if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
                    isArjunTraffic = true;
                    break;
                }
            }
            if (isArjunTraffic) continue;
            
            // 2. 应用全局黑白名单过滤
            if (!globalFilter.shouldProcessActive(url)) {
                filteredCount++;
                continue;
            }
            
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                // ✅ 修复：如果 host 为 null 或 "null"，跳过
                if (host == null || host.isEmpty() || "null".equals(host)) {
                    api.logging().raiseDebugEvent("跳过请求：无法确定 host - url=" + url);
                    continue;
                }
                String mainDomain = extractMainDomain(host);
                // ✅ 修复：如果 mainDomain 为 null，跳过
                if (mainDomain == null || mainDomain.isEmpty()) {
                    api.logging().raiseDebugEvent("跳过请求：无法确定 mainDomain - host=" + host);
                    continue;
                }
                domainToRequests.computeIfAbsent(mainDomain, k -> new ArrayList<>()).add(req);
            } catch (Exception e) {
                api.logging().raiseDebugEvent("解析 URL 失败: " + url);
            }
        }
        
        api.logging().raiseDebugEvent(String.format(
            "过滤了 %d 个请求（Arjun流量或黑名单）", filteredCount
        ));
        
        return domainToRequests;
    }
    
    // ========== 参数收集模式管理 ==========
    
    /**
     * 设置参数收集模式
     */
    public void setCollectionMode(ParameterCollector.CollectionMode mode) {
        parameterCollector.setCollectionMode(mode);
    }
    
    /**
     * 获取参数收集模式
     */
    public ParameterCollector.CollectionMode getCollectionMode() {
        return parameterCollector.getCollectionMode();
    }
    
    /**
     * 获取参数收集器实例
     */
    public ParameterCollector getParameterCollector() {
        return parameterCollector;
    }
    
    /**
     * ✅ 获取Arjun服务引用
     */
    public ArjunService getArjunService() {
        return arjunService;
    }
    
    // ========== 全局参数管理 ==========
    
    /**
     * 添加全局自定义参数
     */
    public void addGlobalCustomParameter(String parameter) {
        parameterManager.addGlobalParameter(parameter);
    }
    
    /**
     * 批量添加全局自定义参数
     */
    public void addGlobalCustomParameters(Set<String> parameters) {
        parameterManager.addGlobalParameters(parameters);
    }
    
    /**
     * 获取全局自定义字典
     */
    public Set<String> getGlobalCustomDictionary() {
        return parameterManager.getGlobalParameters();
    }
    
    /**
     * 清空全局自定义字典
     */
    public void clearGlobalCustomDictionary() {
        parameterManager.clearGlobalCustomParameters();
    }
    
    /**
     * ✅ 清空Arjun扫描缓存
     * 功能：清空所有主动探测相关的缓存，包括：
     * 1. 已扫描的参数记录（ParameterManager）
     * 2. 随机路径基准缓存（randomPathBaselineCache）
     * 3. 基准锁（baselineLocks）
     * 允许重新扫描之前扫描过的端点
     */
    public void clearArjunCache() {
        // 1. 清空已扫描的参数记录
        parameterManager.clearScannedParameters();
        
        // 2. 清空随机路径基准缓存
        int baselineCacheSize = randomPathBaselineCache.size();
        randomPathBaselineCache.clear();
        
        // 3. 清空基准锁
        baselineLocks.clear();
        
        // 4. ✅ 清空接口探测相关缓存（确保UI与缓存状态一致）
        int endpointCacheSize = endpointExistenceCache.size();
        int pendingSize = pendingInterfaceKeys.size();
        int displayedSize = displayedInterfaceKeys.size();
        int timestampSize = interfaceSendTs.size();
        endpointExistenceCache.clear();
        pendingInterfaceKeys.clear();
        displayedInterfaceKeys.clear();
        interfaceSendTs.clear();
        
        api.logging().raiseInfoEvent(String.format(
            "✅ 清空所有主动探测缓存: 参数记录已清空, 随机路径基准缓存 %d 条已清空, 接口探测缓存 %d 条已清空 (pending: %d, displayed: %d, timestamp: %d)",
            baselineCacheSize, endpointCacheSize, pendingSize, displayedSize, timestampSize
        ));
    }
    
    /**
     * ✅ 清空被动扫描缓存
     * 功能：清空参数扫描去重缓存，使被动扫描可以重新扫描之前扫描过的参数
     */
    public void clearPassiveScanCache() {
        int beforeSize = passiveScanProcessedKeys.size();
        passiveScanProcessedKeys.clear();
        int afterSize = passiveScanProcessedKeys.size();
        api.logging().raiseInfoEvent(String.format("✅ 清空被动扫描去重缓存: %d → %d 条", beforeSize, afterSize));
    }
    
    // ========== 参数导入导出 ==========
    
    /**
     * 从文件导入全局参数
     */
    public void importGlobalParametersFromFile(String filePath) {
        try {
            int count = parameterManager.importGlobalParametersFromFile(filePath);
            api.logging().raiseInfoEvent("从文件导入了 " + count + " 个全局参数");
        } catch (Exception e) {
            api.logging().raiseErrorEvent("导入参数文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出全局参数到文件
     */
    public void exportGlobalParametersToFile(String filePath) {
        try {
            parameterManager.exportGlobalParametersToFile(filePath);
        } catch (Exception e) {
            api.logging().raiseErrorEvent("导出参数文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出收集的参数（按主域名）
     */
    public void exportCollectedParametersToFile(String mainDomain, String filePath) {
        try {
            Set<String> params = parameterCollector.getParametersForMainDomain(mainDomain);
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filePath))) {
                writer.println("# 主域名: " + mainDomain);
                writer.println("# 参数数量: " + params.size());
                writer.println();
                
                List<String> sorted = new ArrayList<>(params);
                Collections.sort(sorted);
                sorted.forEach(writer::println);
            }
            api.logging().raiseInfoEvent("导出了 " + params.size() + " 个参数到: " + filePath);
        } catch (Exception e) {
            api.logging().raiseErrorEvent("导出参数文件失败: " + e.getMessage());
        }
    }
    
    // ========== 被动扫描去重 ==========
    
    /**
     * 原子性检查并标记被动扫描状态（Check-And-Set）
     * 
     * 这个方法保证在并发场景下的线程安全：
     * - 如果参数尚未被标记，则标记并返回false（表示应该扫描）
     * - 如果参数已经被标记，则返回true（表示应该跳过）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param path 路径
     * @param contentType Content-Type
     * @param parameterName 参数名
     * @param scanType 扫描类型
     * @return true 如果已被处理过（应跳过扫描），false 如果首次处理（应继续扫描）
     */
    /**
     * 检查并标记被动扫描已处理（新版：支持颗粒度控制）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param path 请求路径
     * @param contentType Content-Type
     * @param targetIdentifier 目标标识符（参数名、Header名等）
     * @param config 扫描配置（包含颗粒度设置）
     * @return true=已处理（跳过），false=未处理（继续扫描）
     */
    public boolean checkAndMarkPassiveScanProcessed(String method, String host, String path, 
                                                   String contentType, String targetIdentifier, 
                                                   com.xprobe.scanner.config.Configuration config) {
        // ✅ 使用 DeduplicationKeyGenerator 生成去重key，支持颗粒度控制
        String key = com.xprobe.scanner.core.DeduplicationKeyGenerator.generateKey(
            method, host, path, contentType, config, targetIdentifier
        );
        
        // ✅ P0修复: 使用LRUCache.put()原子操作
        // put()返回true表示首次添加(未被处理过)，返回false表示已存在(已被处理过)
        boolean wasNew = passiveScanProcessedKeys.put(key, Boolean.TRUE);
        
        // 返回相反值：
        // wasNew=true 表示首次添加（未被处理过），返回false（应该扫描）
        // wasNew=false 表示已存在（已被处理过），返回true（应该跳过）
        return !wasNew;
    }
    
    /**
     * ✅ 只检查是否已处理（不标记）
     * 用于过滤阶段，决定"打不打"
     * 
     * @param key 去重key
     * @return true=已处理（跳过），false=未处理（继续）
     */
    public boolean isAlreadyProcessed(String key) {
        return passiveScanProcessedKeys.containsKey(key);
    }
    
    /**
     * ✅ 标记为已处理
     * 用于注入阶段，每打完一个目标后调用
     * 
     * @param key 去重key
     */
    public void markAsProcessed(String key) {
        passiveScanProcessedKeys.put(key, Boolean.TRUE);
    }
    
    // ========== 统计信息 ==========
    
    /**
     * 获取收集器统计信息
     */
    public ParameterCollector.CollectorStatistics getCollectorStatistics() {
        return parameterCollector.getStatistics();
    }
    
    /**
     * 获取管理器统计信息
     */
    public ParameterManager.ManagerStatistics getManagerStatistics() {
        return parameterManager.getStatistics();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 提取主域名（从host字符串）
     * - 如果是IP地址，返回完整IP
     * - 如果是域名，返回主域名（倒数第二级+顶级域名）
     */
    private String extractMainDomain(String host) {
        if (host == null || host.isEmpty()) {
            return host;
        }
        
        // ✅ 检测是否为IP地址（IPv4）
        if (host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return host;  // IP地址直接返回完整IP
        }
        
        // ✅ IPv6地址也直接返回
        if (host.contains(":")) {
            return host;
        }
        
        // ✅ 域名：提取主域名
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }
    
    /**
     * 获取 GlobalFilter
     */
    public GlobalFilter getGlobalFilter() {
        return globalFilter;
    }
    
    /**
     * ✅ 触发漏洞扫描（Arjun发现参数后调用）
     * 
     * @param originalRequest 原始HTTP请求
     * @param foundParams Arjun发现的参数名列表
     */
    private void triggerVulnerabilityScan(HttpRequest originalRequest, Set<String> foundParams) {
        if (taskScheduler == null) {
            api.logging().raiseDebugEvent("⚠️ TaskScheduler未设置，无法触发漏洞扫描");
            return;
        }
        
        if (foundParams == null || foundParams.isEmpty()) {
            return;
        }
        
        try {
            // ✅ 提取原始请求的参数值
            java.util.Map<String, String> originalUrlParams = new java.util.HashMap<>();
            java.util.Map<String, String> originalBodyParams = new java.util.HashMap<>();
            for (var p : originalRequest.parameters()) {
                if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                    originalUrlParams.put(p.name(), p.value());
                } else if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                    originalBodyParams.put(p.name(), p.value());
                }
            }
            
            // 获取Content-Type
            String contentType = getContentType(originalRequest);
            
            // ✅ 1. 基于原始请求，添加Arjun发现的参数，✅ 使用原参数值
            HttpRequest requestWithParams = originalRequest;
            
            // 根据请求类型添加参数
            if ("GET".equalsIgnoreCase(originalRequest.method())) {
                // GET: 添加URL参数，✅ 使用原参数值
                for (String paramName : foundParams) {
                    String value = originalUrlParams.containsKey(paramName) ? originalUrlParams.get(paramName) : 
                                  (originalBodyParams.containsKey(paramName) ? originalBodyParams.get(paramName) : getPlaceholder());
                    value = normalizeValue(value);
                    requestWithParams = requestWithParams.withAddedParameters(
                        HttpParameter.urlParameter(paramName, value)
                    );
                }
            } else if (contentType != null && contentType.contains("application/json")) {
                // JSON: 需要合并到JSON body（使用Arjun的方式），✅ 使用原参数值
                requestWithParams = buildJsonRequestWithParams(originalRequest, foundParams, originalUrlParams, originalBodyParams);
            } else {
                // POST表单: 添加body参数，✅ 使用原参数值
                for (String paramName : foundParams) {
                    String value = originalUrlParams.containsKey(paramName) ? originalUrlParams.get(paramName) : 
                                  (originalBodyParams.containsKey(paramName) ? originalBodyParams.get(paramName) : getPlaceholder());
                    value = normalizeValue(value);
                    requestWithParams = requestWithParams.withAddedParameters(
                        HttpParameter.bodyParameter(paramName, value)
                    );
                }
            }
            
            // ✅ 2. 创建RequestContext
            RequestContext context = new RequestContext(
                "ARJUN",  // 来源标记为Arjun
                requestWithParams.method(),
                requestWithParams.url(),
                requestWithParams.toString().hashCode()
            );
            
            // ✅ 3. 为每个发现的参数创建ScanTask
            // 注意：使用@SuppressWarnings抑制类型转换警告
            // HttpRequest在Scanner中使用时会被转换为HttpRequestToBeSent
            List<ScanTask> scanTasks = new ArrayList<>();
            List<ParsedHttpParameter> parameters = requestWithParams.parameters();
            
            for (ParsedHttpParameter param : parameters) {
                // 只扫描Arjun发现的参数
                if (foundParams.contains(param.name())) {
                    // 为每个启用的配置创建任务
                    for (Configuration config : configManager.getEnabledConfigurations()) {
                        // ✅ 修复：直接使用HttpRequest，不需要强制转换
                        scanTasks.add(new ScanTask(param, config, requestWithParams, context));
                    }
                }
            }
            
            // ✅ 5. 提交扫描任务
            if (!scanTasks.isEmpty()) {
                api.logging().raiseInfoEvent(String.format(
                    "🔍 触发漏洞扫描: %s 个参数 × %d 个规则 = %d 个任务",
                    foundParams.size(),
                    configManager.getEnabledConfigurations().size(),
                    scanTasks.size()
                ));
                
                taskScheduler.scheduleScan(scanTasks);
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发漏洞扫描失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ✅ 构建包含参数的JSON请求（重载：无原参数值）
     */
    @SuppressWarnings("unchecked")
    private HttpRequest buildJsonRequestWithParams(HttpRequest originalRequest, Set<String> paramNames) {
        return buildJsonRequestWithParams(originalRequest, paramNames, null, null);
    }
    
    /**
     * ✅ 构建包含参数的JSON请求，✅ 使用原参数值
     */
    @SuppressWarnings("unchecked")
    private HttpRequest buildJsonRequestWithParams(HttpRequest originalRequest, Set<String> paramNames,
                                                   java.util.Map<String, String> originalUrlParams,
                                                   java.util.Map<String, String> originalBodyParams) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            
            String originalBody = originalRequest.bodyToString();
            
            // 解析原始JSON
            Map<String, Object> jsonMap;
            if (originalBody == null || originalBody.trim().isEmpty()) {
                jsonMap = new HashMap<>();
            } else {
                jsonMap = jsonMapper.readValue(originalBody, Map.class);
            }
            
            // ✅ 添加发现的参数，使用原参数值
            for (String paramName : paramNames) {
                String value = getPlaceholder();
                if (originalUrlParams != null && originalUrlParams.containsKey(paramName)) {
                    value = originalUrlParams.get(paramName);
                } else if (originalBodyParams != null && originalBodyParams.containsKey(paramName)) {
                    value = originalBodyParams.get(paramName);
                } else if (jsonMap.containsKey(paramName)) {
                    // 如果原JSON中已有该参数，使用原值
                    Object existingValue = jsonMap.get(paramName);
                    if (existingValue != null) {
                        value = existingValue.toString();
                    }
                }
                value = normalizeValue(value);
                jsonMap.put(paramName, value);
            }
            
            // 序列化回JSON
            String newBody = jsonMapper.writeValueAsString(jsonMap);
            
            // 更新Content-Type（如果没有）
            HttpRequest result = originalRequest.withBody(newBody);
            if (getContentType(result) == null) {
                result = result.withAddedHeader("Content-Type", "application/json");
            }
            
            return result;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建JSON请求失败: " + e.getMessage());
            // 降级：返回原始请求
            return originalRequest;
        }
    }
    
    // ========== 扫描控制方法 ==========
    
    /**
     * 启动实时扫描（被动参数收集）
     * 注意：实时扫描自动运行，此方法主要用于UI控制
     */
    public void startRealtimeScanning() {
        arjunEnabled = true;  // ✅ 修复：启用Arjun触发
        // 注意：默认是手动模式，需要UI明确设置为实时模式
        api.logging().raiseInfoEvent("✅ 主动探测已启用（默认手动模式）");
    }
    
    /**
     * 停止实时扫描
     * 注意：实际上只是停止Arjun主动探测，被动收集继续进行
     */
    public void stopRealtimeScanning() {
        arjunEnabled = false;  // ✅ 修复：禁用Arjun触发
        isRealtimeMode = false;  // 同时关闭实时模式
        api.logging().raiseInfoEvent("⚫ 主动探测已禁用（被动参数收集继续进行）");
    }
    
    /**
     * 设置为实时模式
     */
    public void setRealtimeMode(boolean enabled) {
        if (!arjunEnabled && enabled) {
            api.logging().raiseInfoEvent("⚠️ 主开关未启用，无法切换到实时模式");
            return;
        }
        this.isRealtimeMode = enabled;
        if (enabled) {
            api.logging().raiseInfoEvent("✅ 切换到实时模式（自动触发Arjun）");
        } else {
            api.logging().raiseInfoEvent("✅ 切换到手动模式（需点击按钮触发）");
        }
    }
    
    /**
     * 获取当前模式
     */
    public boolean isRealtimeMode() {
        return isRealtimeMode;
    }
    
    /**
     * 获取主域名统计信息
     */
    public Map<String, DomainStatistics> getDomainStatistics() {
        Map<String, DomainStatistics> stats = new HashMap<>();
        
        for (String mainDomain : parameterCollector.getAllMainDomains()) {
            Set<String> hosts = parameterCollector.getHostsForMainDomain(mainDomain);
            Set<String> endpoints = parameterCollector.getEndpointsForMainDomain(mainDomain);
            Set<String> parameters = parameterCollector.getParametersForMainDomain(mainDomain);
            Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
            
            // ✅ 使用实际的最后更新时间，而不是当前时间
            long lastUpdateTime = parameterCollector.getLastUpdateTimeForDomain(mainDomain);
            
            stats.put(mainDomain, new DomainStatistics(
                mainDomain,
                hosts.size(),
                endpoints.size(),
                parameters.size(),
                keywords.size(),
                lastUpdateTime  // ✅ 使用实际数据变化时间
            ));
        }
        
        return stats;
    }
    
    /**
     * 域统计信息
     */
    public static class DomainStatistics {
        private final String mainDomain;
        private final int hostCount;
        private final int endpointCount;
        private final int parameterCount;
        private final int keywordCount;
        private final long lastUpdateTime;
        
        public DomainStatistics(String mainDomain, int hostCount, int endpointCount, 
                               int parameterCount, int keywordCount, long lastUpdateTime) {
            this.mainDomain = mainDomain;
            this.hostCount = hostCount;
            this.endpointCount = endpointCount;
            this.parameterCount = parameterCount;
            this.keywordCount = keywordCount;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        public String getMainDomain() { return mainDomain; }
        public int getHostCount() { return hostCount; }
        public int getEndpointCount() { return endpointCount; }
        public int getParameterCount() { return parameterCount; }
        public int getKeywordCount() { return keywordCount; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        /**
         * 获取格式化的最后更新时间
         */
        public String getLastUpdateTimeFormatted() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            return sdf.format(new java.util.Date(lastUpdateTime));
        }
    }
    
    // ========== Arjun结果监听器机制 ==========
    
    /**
     * Arjun结果监听器接口
     */
    public interface ArjunResultListener {
        /**
         * 当Arjun发现参数时被调用
         * 
         * @param mainDomain 主域名
         * @param host 触发扫描的目标子域
         * @param endpoint 接口路径
         * @param foundParameters 发现的参数集合
         * @param parameterType 参数类型（GET/POST/JSON等）
         * @param timestamp 探测时间戳
         */
        void onArjunResultFound(String mainDomain, String host, String endpoint, Set<String> foundParameters, 
                               String parameterType, long timestamp);
    }
    
    /**
     * 注册Arjun结果监听器
     */
    public void addArjunResultListener(ArjunResultListener listener) {
        if (listener != null) {
            arjunResultListeners.add(listener);
        }
    }
    
    /**
     * 移除Arjun结果监听器
     */
    public void removeArjunResultListener(ArjunResultListener listener) {
        arjunResultListeners.remove(listener);
    }
    
    /**
     * 通知所有监听器Arjun发现了参数
     * ✅ 修复：时间戳由调用方在收到响应时立即生成，避免延迟
     */
    private void notifyArjunResult(String mainDomain, String host, String endpoint, Set<String> foundParameters, 
                                   String parameterType, long timestamp) {
        for (ArjunResultListener listener : arjunResultListeners) {
            try {
                listener.onArjunResultFound(mainDomain, host, endpoint, foundParameters, parameterType, timestamp);
            } catch (Exception e) {
                api.logging().raiseErrorEvent("Arjun结果监听器执行失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * ✅ P0修复：关闭RealtimeScanner资源
     */
    /**
     * 停止所有主动任务并清空调度队列（不影响被动收集）
     */
    // ===== 辅助方法：形态识别与参数注入 =====
    private boolean hasBothUrlAndBodyParameters(burp.api.montoya.http.message.requests.HttpRequest request) {
        boolean hasUrl = false, hasBody = false;
        if (request == null) return false;
        for (var p : request.parameters()) {
            if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) hasUrl = true;
            if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) hasBody = true;
            if (hasUrl && hasBody) return true;
        }
        return false;
    }

    /**
     * 构建请求并转换参数形态（原始参数+新参数都按目标形态放置）
     * @param url 目标URL（可能包含原始URL参数）
     * @param method 目标HTTP方法
     * @param contentType 目标Content-Type
     * @param originalTemplate 原始模板请求（用于提取原始参数）
     * @param newParamNames 新参数名称集合
     */
    private burp.api.montoya.http.message.requests.HttpRequest buildRequestWithParamsAndTransform(
            String url, String method, String contentType, HttpRequest originalTemplate, java.util.Set<String> newParamNames) {
        try {
            // ✅ 修复：提取原始请求的所有参数（URL和Body），使用 List 保留所有参数值（包括同名参数）
            // 注意：虽然 Burp API 的 parameters() 可能不会返回同名参数，但为了完整性，我们使用 List
            java.util.Map<String, String> originalUrlParams = new java.util.HashMap<>();
            java.util.Map<String, String> originalBodyParams = new java.util.HashMap<>();
            
            if (originalTemplate != null) {
                // ✅ 遍历所有参数，如果同名参数有多个值，保留最后一个（这是 Burp API 的行为）
                // 但更重要的是确保所有参数名都被提取
                for (var p : originalTemplate.parameters()) {
                    if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                        originalUrlParams.put(p.name(), p.value());
                    } else if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                        originalBodyParams.put(p.name(), p.value());
                    }
                }
            }
            
            // ✅ 修复：如果参数在当前接口模板中不存在，尝试从同一主域的其他接口模板中查找参数值
            // 这样可以确保跨接口的参数也能使用原始值，而不是 xprobe_test
            // 注意：必须在合并 allParamNames 之前查找，这样查找到的值才能被使用
            if (newParamNames != null && parameterCollector != null && !newParamNames.isEmpty()) {
                // 从 URL 中提取 host，然后查找对应的主域
                String mainDomainToSearch = null;
                try {
                    java.net.URI uri = new java.net.URI(url);
                    String host = uri.getHost();
                    if (host != null && !host.isEmpty()) {
                        // 遍历所有主域，找到包含该 host 的主域
                        java.util.Set<String> allMainDomains = parameterCollector.getAllMainDomains();
                        for (String md : allMainDomains) {
                            java.util.Set<String> hosts = parameterCollector.getHostsForMainDomain(md);
                            if (hosts != null && hosts.contains(host)) {
                                mainDomainToSearch = md;
                                break;
                            }
                        }
                        // 如果没找到，host 本身可能就是主域（IP地址的情况）
                        if (mainDomainToSearch == null) {
                            mainDomainToSearch = host;
                        }
                    }
                } catch (Exception e) {
                    api.logging().raiseDebugEvent("提取主域失败: " + e.getMessage());
                }
                
                if (mainDomainToSearch != null) {
                    // ✅ 对 newParamNames 中的每个参数，如果不在当前模板中，从其他接口模板中查找
                    for (String paramName : newParamNames) {
                        if (!originalUrlParams.containsKey(paramName) && !originalBodyParams.containsKey(paramName)) {
                            // 尝试从同一主域的所有接口模板中查找该参数
                            java.util.Set<ParameterCollector.EndpointKey> allEndpoints = parameterCollector.getEndpointKeysForMainDomain(mainDomainToSearch);
                            if (allEndpoints != null && !allEndpoints.isEmpty()) {
                                boolean found = false;
                                for (ParameterCollector.EndpointKey otherEpKey : allEndpoints) {
                                    HttpRequest otherTemplate = parameterCollector.getEndpointTemplate(mainDomainToSearch, otherEpKey);
                                    if (otherTemplate != null) {
                                        for (var p : otherTemplate.parameters()) {
                                            if (p.name().equals(paramName)) {
                                                if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                                                    originalUrlParams.put(paramName, p.value());
                                                    found = true;
                                                    api.logging().raiseDebugEvent(String.format(
                                                        "✅ 从其他接口模板中找到参数值: %s=%s (来自 %s %s)",
                                                        paramName, p.value(), otherEpKey.method, otherEpKey.endpoint
                                                    ));
                                                } else if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                                                    originalBodyParams.put(paramName, p.value());
                                                    found = true;
                                                    api.logging().raiseDebugEvent(String.format(
                                                        "✅ 从其他接口模板中找到参数值: %s=%s (来自 %s %s)",
                                                        paramName, p.value(), otherEpKey.method, otherEpKey.endpoint
                                                    ));
                                                }
                                                break; // 找到第一个值就使用
                                            }
                                        }
                                        // 如果已经找到该参数的值，就不需要继续查找其他接口
                                        if (found) {
                                            break;
                                        }
                                    }
                                }
                                if (!found) {
                                    api.logging().raiseDebugEvent(String.format(
                                        "⚠️ 未找到参数值: %s (主域: %s, 接口数: %d)",
                                        paramName, mainDomainToSearch, allEndpoints.size()
                                    ));
                                }
                            } else {
                                api.logging().raiseDebugEvent(String.format(
                                    "⚠️ 主域 %s 没有接口模板", mainDomainToSearch
                                ));
                            }
                        }
                    }
                } else {
                    api.logging().raiseDebugEvent("⚠️ 无法确定主域，跳过跨接口参数查找");
                }
            }
            
            // ✅ 合并所有参数（原始参数+新参数）
            java.util.Set<String> allParamNames = new java.util.HashSet<>();
            if (newParamNames != null) {
                allParamNames.addAll(newParamNames);
            }
            allParamNames.addAll(originalUrlParams.keySet());
            allParamNames.addAll(originalBodyParams.keySet());
            
            // ✅ 移除URL中的查询参数（避免重复），统一通过参数对象添加
            String cleanUrl = url;
            try {
                java.net.URI uri = new java.net.URI(url);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                String path = uri.getPath();
                int port = uri.getPort();
                // ✅ 修复：如果 host 为 null 或 "null"，使用手动解析
                if (host == null || host.isEmpty() || "null".equals(host)) {
                    // 手动解析 URL
                    int queryIndex = url.indexOf("?");
                    if (queryIndex > 0) {
                        cleanUrl = url.substring(0, queryIndex);
                    }
                } else {
                    boolean defaultPort = (scheme != null && scheme.equalsIgnoreCase("https") && port == 443) || 
                                          (scheme != null && scheme.equalsIgnoreCase("http") && port == 80);
                    String portStr = (port > 0 && !defaultPort) ? (":" + port) : "";
                    cleanUrl = scheme + "://" + host + portStr + (path != null && !path.isEmpty() ? path : "/");
                }
            } catch (Exception e) {
                int queryIndex = url.indexOf("?");
                if (queryIndex > 0) {
                    cleanUrl = url.substring(0, queryIndex);
                }
            }
            
            // ✅ 修复：传入原始请求模板以保留请求头
            burp.api.montoya.http.message.requests.HttpRequest req = buildRequest(cleanUrl, method, contentType, originalTemplate);
            if (req == null) return null;
            
            if (allParamNames.isEmpty()) {
                // ✅ 修复：即使没有参数，也要确保请求头被保留
                return req;
            }
            
            try {
                if ("GET".equalsIgnoreCase(method)) {
                    // GET请求：所有参数（原始URL+原始Body+新参数）都添加到URL
                    for (String name : allParamNames) {
                        String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                      (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                        value = normalizeValue(value);
                        req = req.withAddedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.urlParameter(name, value)
                        );
                    }
                } else if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
                    // POST JSON：所有参数（原始URL+原始Body+新参数）都添加到JSON Body
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    boolean first = true;
                    for (String name : allParamNames) {
                        if (!first) sb.append(",");
                        String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                      (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                        value = normalizeValue(value);
                        sb.append("\"").append(name).append("\":\"").append(escapeJson(value)).append("\"");
                        first = false;
                    }
                    sb.append("}");
                    req = req.withBody(sb.toString());
                    // 确保Content-Type
                    boolean hasCT = false;
                    for (var h : req.headers()) { if ("Content-Type".equalsIgnoreCase(h.name())) { hasCT = true; break; } }
                    if (!hasCT) req = req.withAddedHeader("Content-Type", "application/json");
                } else {
                    // POST 表单：所有参数（原始URL+原始Body+新参数）都添加到Body
                    for (String name : allParamNames) {
                        String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                      (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                        value = normalizeValue(value);
                        req = req.withAddedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.bodyParameter(name, value)
                        );
                    }
                    // 确保Content-Type
                    boolean hasCT = false;
                    for (var h : req.headers()) { if ("Content-Type".equalsIgnoreCase(h.name())) { hasCT = true; break; } }
                    if (!hasCT) req = req.withAddedHeader("Content-Type", "application/x-www-form-urlencoded");
                }
            } catch (Exception e) {
                api.logging().raiseErrorEvent("构建请求参数失败: " + e.getMessage());
            }
            return req;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建请求失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private burp.api.montoya.http.message.requests.HttpRequest buildRequestWithParams(
            String url, String method, String contentType, java.util.Set<String> paramNames) {
        return buildRequestWithParams(url, method, contentType, paramNames, null);
    }
    
    /**
     * ✅ 修复：支持传入原始模板请求，使用原参数值
     */
    private burp.api.montoya.http.message.requests.HttpRequest buildRequestWithParams(
            String url, String method, String contentType, java.util.Set<String> paramNames, HttpRequest originalTemplate) {
        // ✅ 提取原始请求的参数值
        java.util.Map<String, String> originalUrlParams = new java.util.HashMap<>();
        java.util.Map<String, String> originalBodyParams = new java.util.HashMap<>();
        if (originalTemplate != null) {
            for (var p : originalTemplate.parameters()) {
                if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                    originalUrlParams.put(p.name(), p.value());
                } else if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                    originalBodyParams.put(p.name(), p.value());
                }
            }
        }
        
        // ✅ 修复：对于所有请求，都需要移除URL中的查询参数（避免参数重复）
        // 参数会通过 withAddedParameters 统一添加，确保使用原始参数值
        String cleanUrl = url;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            int port = uri.getPort();
            // ✅ 修复：如果 host 为 null 或 "null"，使用手动解析
            if (host == null || host.isEmpty() || "null".equals(host)) {
                // 手动解析 URL
                int queryIndex = url.indexOf("?");
                if (queryIndex > 0) {
                    cleanUrl = url.substring(0, queryIndex);
                }
            } else {
                boolean defaultPort = (scheme != null && scheme.equalsIgnoreCase("https") && port == 443) || 
                                      (scheme != null && scheme.equalsIgnoreCase("http") && port == 80);
                String portStr = (port > 0 && !defaultPort) ? (":" + port) : "";
                cleanUrl = scheme + "://" + host + portStr + (path != null && !path.isEmpty() ? path : "/");
            }
        } catch (Exception e) {
            // 如果解析失败，使用原始URL
            int queryIndex = url.indexOf("?");
            if (queryIndex > 0) {
                cleanUrl = url.substring(0, queryIndex);
            }
        }
        
        burp.api.montoya.http.message.requests.HttpRequest req = buildRequest(cleanUrl, method, contentType);
        if (req == null || paramNames == null || paramNames.isEmpty()) return req;
        try {
            if ("GET".equalsIgnoreCase(method)) {
                // GET请求：参数添加到URL，✅ 使用原参数值
                for (String name : paramNames) {
                    String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                  (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                    req = req.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.urlParameter(name, value)
                    );
                }
            } else if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
                // POST JSON：参数添加到Body，确保URL中没有参数，✅ 使用原参数值
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                boolean first = true;
                for (String name : paramNames) {
                    if (!first) sb.append(",");
                    String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                  (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                    sb.append("\"").append(name).append("\":\"").append(escapeJson(value)).append("\"");
                    first = false;
                }
                sb.append("}");
                req = req.withBody(sb.toString());
                // 确保Content-Type
                boolean hasCT = false;
                for (var h : req.headers()) { if ("Content-Type".equalsIgnoreCase(h.name())) { hasCT = true; break; } }
                if (!hasCT) req = req.withAddedHeader("Content-Type", "application/json");
            } else {
                // POST 表单：参数添加到Body，确保URL中没有参数，✅ 使用原参数值
                for (String name : paramNames) {
                    String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                  (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                    req = req.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.bodyParameter(name, value)
                    );
                }
                // 确保Content-Type
                boolean hasCT = false;
                for (var h : req.headers()) { if ("Content-Type".equalsIgnoreCase(h.name())) { hasCT = true; break; } }
                if (!hasCT) req = req.withAddedHeader("Content-Type", "application/x-www-form-urlencoded");
            }
        } catch (Exception ignore) {}
        return req;
    }

    /**
     * 将增量参数添加到原始请求中，保持原有参数位置（URL参数添加到URL，Body参数添加到Body）
     * ✅ 修复：使用原参数值（从 originalTemplate 中提取，而不是从已修改的 request 中提取）
     */
    private burp.api.montoya.http.message.requests.HttpRequest addIncrementalParamsToRequest(
            burp.api.montoya.http.message.requests.HttpRequest request, 
            java.util.Set<String> incrementalParams,
            HttpRequest originalTemplate) {
        if (request == null || incrementalParams == null || incrementalParams.isEmpty()) {
            return request;
        }
        try {
            // ✅ 修复：从 originalTemplate 中提取原始参数值，而不是从已修改的 request 中提取
            java.util.Map<String, String> originalUrlParams = new java.util.HashMap<>();
            java.util.Map<String, String> originalBodyParams = new java.util.HashMap<>();
            if (originalTemplate != null) {
                for (var p : originalTemplate.parameters()) {
                    if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                        originalUrlParams.put(p.name(), p.value());
                    } else if (p.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                        originalBodyParams.put(p.name(), p.value());
                    }
                }
            }
            
            // 检查原始请求的参数类型分布
            boolean hasUrlParams = !originalUrlParams.isEmpty();
            boolean hasBodyParams = !originalBodyParams.isEmpty();
            String contentType = null;
            for (var h : request.headers()) {
                if ("Content-Type".equalsIgnoreCase(h.name())) {
                    contentType = h.value();
                    break;
                }
            }
            
            // 根据原始请求的参数分布，决定增量参数的添加位置
            if (hasUrlParams && hasBodyParams) {
                // ✅ 同时有URL和Body参数：增量参数只添加到URL（避免破坏Body结构）
                // 这样可以保持原始请求的Body结构不变，只修改URL参数
                for (String name : incrementalParams) {
                    String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : 
                                  (originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder());
                    request = request.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.urlParameter(name, value)
                    );
                }
            } else if (hasUrlParams) {
                // 只有URL参数：增量参数添加到URL，✅ 使用原参数值
                for (String name : incrementalParams) {
                    String value = originalUrlParams.containsKey(name) ? originalUrlParams.get(name) : getPlaceholder();
                    value = normalizeValue(value);
                    request = request.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.urlParameter(name, value)
                    );
                }
            } else if (hasBodyParams) {
                // 只有Body参数：根据Content-Type决定
                if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
                    // JSON Body：合并到现有JSON，✅ 使用原参数值
                    String existingBody = request.bodyToString();
                    if (existingBody != null && existingBody.trim().startsWith("{")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(existingBody.trim());
                        if (!existingBody.trim().endsWith("}")) {
                            sb.setLength(sb.length() - 1);
                        }
                        if (sb.length() > 1 && !sb.substring(1, sb.length() - 1).trim().isEmpty()) {
                            sb.insert(sb.length() - 1, ",");
                        }
                        for (String name : incrementalParams) {
                            String value = originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder();
                            value = normalizeValue(value);
                            sb.insert(sb.length() - 1, String.format("\"%s\":\"%s\",", name, escapeJson(value)));
                        }
                        if (sb.charAt(sb.length() - 2) == ',') {
                            sb.deleteCharAt(sb.length() - 2);
                        }
                        sb.append("}");
                        request = request.withBody(sb.toString());
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("{");
                        boolean first = true;
                        for (String name : incrementalParams) {
                            if (!first) sb.append(",");
                            String value = originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder();
                            value = normalizeValue(value);
                            sb.append("\"").append(name).append("\":\"").append(escapeJson(value)).append("\"");
                            first = false;
                        }
                        sb.append("}");
                        request = request.withBody(sb.toString());
                    }
                } else {
                    // 表单Body：添加到Body参数，✅ 使用原参数值
                    for (String name : incrementalParams) {
                        String value = originalBodyParams.containsKey(name) ? originalBodyParams.get(name) : getPlaceholder();
                            value = normalizeValue(value);
                        request = request.withAddedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.bodyParameter(name, value)
                        );
                    }
                }
            } else {
                // 没有参数：根据方法决定（GET→URL，POST→Body），✅ 使用原参数值（虽然原请求没有参数，但保持一致性）
                String method = request.method();
                if ("GET".equalsIgnoreCase(method)) {
                    for (String name : incrementalParams) {
                        request = request.withAddedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.urlParameter(name, getPlaceholder())
                        );
                    }
                } else {
                    if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("{");
                        boolean first = true;
                        for (String name : incrementalParams) {
                            if (!first) sb.append(",");
                            sb.append("\"").append(name).append("\":\"").append(escapeJson(getPlaceholder())).append("\"");
                            first = false;
                        }
                        sb.append("}");
                        request = request.withBody(sb.toString());
                    } else {
                        for (String name : incrementalParams) {
                            request = request.withAddedParameters(
                                burp.api.montoya.http.message.params.HttpParameter.bodyParameter(name, getPlaceholder())
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("添加增量参数失败: " + e.getMessage());
        }
        return request;
    }

    /**
     * 从原始请求复制关键会话头到新请求（跳过Host和Content-Length）
     */
    private burp.api.montoya.http.message.requests.HttpRequest copyCriticalSessionHeaders(
            burp.api.montoya.http.message.requests.HttpRequest original, 
            burp.api.montoya.http.message.requests.HttpRequest target) {
        if (original == null || target == null) return target;
        try {
            java.util.Set<String> criticalHeaders = java.util.Set.of(
                "Cookie", "Authorization", "Referer", "X-CSRF-Token", 
                "X-Requested-With", "X-Auth-Token", "X-API-Key"
            );
            for (var h : original.headers()) {
                String name = h.name();
                if (criticalHeaders.contains(name) || 
                    name.toLowerCase(java.util.Locale.ROOT).startsWith("x-")) {
                    // 跳过Host和Content-Length（由API自动处理）
                    if ("Host".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                        continue;
                    }
                    // 如果目标请求已有该头，更新；否则添加
                    boolean exists = false;
                    for (var th : target.headers()) {
                        if (name.equalsIgnoreCase(th.name())) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        target = target.withUpdatedHeader(name, h.value());
                    } else {
                        target = target.withAddedHeader(name, h.value());
                    }
                }
            }
        } catch (Exception ignore) {}
        return target;
    }

    /**
     * 从模板请求构建完整URL（替换host和path）
     */
    private String buildUrlFromTemplate(HttpRequest template, String targetHost, String endpoint) {
        try {
            // ✅ 修复：优先使用 template.httpService() 获取 scheme 和 port，避免 URL 解析问题
            String scheme = "https";
            int port = 443;
            boolean isSecure = true;
            
            // 尝试从 template 的 httpService 获取
            try {
                burp.api.montoya.http.HttpService httpService = template.httpService();
                if (httpService != null) {
                    scheme = httpService.secure() ? "https" : "http";
                    port = httpService.port();
                    isSecure = httpService.secure();
                }
            } catch (Exception ignored) {}
            
            // 如果无法从 httpService 获取，尝试从 URL 解析
            try {
                String originalUrl = template.url();
                java.net.URI uri = new java.net.URI(originalUrl);
                String uriScheme = uri.getScheme();
                if (uriScheme != null && !uriScheme.isEmpty()) {
                    scheme = uriScheme;
                    isSecure = "https".equalsIgnoreCase(scheme);
                }
                int uriPort = uri.getPort();
                if (uriPort > 0) {
                    port = uriPort;
                } else {
                    port = isSecure ? 443 : 80;
                }
            } catch (Exception ignored) {
                // 使用默认值
            }
            
            boolean defaultPort = (isSecure && port == 443) || (!isSecure && port == 80);
            String portStr = defaultPort ? "" : (":" + port);
            
            // ✅ 确保 targetHost 不为 null
            if (targetHost == null || targetHost.isEmpty() || "null".equals(targetHost)) {
                api.logging().raiseErrorEvent("构建URL失败: targetHost 为空或为 null");
                return null;
            }
            
            // ✅ 确保 endpoint 不为 null
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = "/";
            }
            
            // ✅ 保留原始URL的查询参数（用于GET请求）
            String query = null;
            String fragment = null;
            try {
                String originalUrl = template.url();
                java.net.URI uri = new java.net.URI(originalUrl);
                query = uri.getQuery();
                fragment = uri.getFragment();
            } catch (Exception ignored) {}
            
            String url = scheme + "://" + targetHost + portStr + endpoint;
            if (query != null && !query.isEmpty()) {
                url += "?" + query;
            }
            if (fragment != null && !fragment.isEmpty()) {
                url += "#" + fragment;
            }
            return url;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建URL失败: " + e.getMessage());
            return null;
        }
    }

    public void stopAllTasksAndClear() {
        try {
            // 关闭实时触发
            stopRealtimeScanning();
            // 清空任务调度器队列
            if (taskScheduler != null) {
                taskScheduler.pauseAllTasksAndClear();
            }
            api.logging().raiseInfoEvent("⏹️ 已请求停止所有主动任务并清空队列");
        } catch (Exception e) {
            api.logging().raiseErrorEvent("停止所有主动任务失败: " + e.getMessage());
        }
    }

    public void shutdown() {
        api.logging().raiseInfoEvent("关闭RealtimeScanner资源...");
        
        if (arjunService != null) {
            arjunService.shutdown();
        }
    }
}


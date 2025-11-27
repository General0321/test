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
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.utils.BoundedCache;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

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
    
    // 使用新的参数收集器和管理器
    private final ParameterCollector parameterCollector;
    private final ParameterManager parameterManager;
    
    // ✅ P0修复: 被动扫描去重机制 - 使用有界缓存（FIFO）防止内存泄漏
    // 最多保存10万条记录，超过则自动淘汰最早插入的记录
    private final BoundedCache<String, Boolean> passiveScanProcessedKeys = new BoundedCache<>(100_000);
    
    // ✅ TaskScheduler引用（用于Arjun发现参数后触发漏洞扫描）
    private TaskScheduler taskScheduler;
    
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
        
        api.logging().raiseInfoEvent("✅ 实时扫描器已初始化（Java原生Arjun + 参数收集器）");
    }
    
    /**
     * ✅ 设置TaskScheduler引用（用于Arjun发现参数后触发扫描）
     */
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
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
            
            // 检查全局过滤器
            if (!globalFilter.shouldProcessActive(url)) {
                return;
            }
            
            // ✅ 只收集响应参数（请求参数已在 processNewRequest 中收集）
            parameterCollector.collectFromResponse(request, responseReceived);
            
            // 可选：根据请求参数触发智能扫描
            URI uri = new URI(url);
            String mainDomain = extractMainDomain(uri.getHost());
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
            
            // 检查全局过滤器
            if (!globalFilter.shouldProcessActive(url)) {
                api.logging().raiseDebugEvent("URL 被过滤器阻止: " + url);
                return;
            }
            
            // 委托给参数收集器
            boolean hasNewParameters = parameterCollector.collectFromRequest(request);
            
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
        try {
            // ✅ 修复：检查主动探测总开关
            if (!arjunEnabled) {
                api.logging().raiseDebugEvent("主动探测已禁用，跳过Arjun扫描: " + targetHost);
                return;
            }
            
            // ✅ 获取主域名下所有收集的参数
            Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
            
            // 如果启用了关键词收集，将关键词也加入参数列表
            if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                collectedParams.addAll(keywords);
            }
            
            // ✅ 获取主域名下所有收集的接口
            Set<ParameterCollector.EndpointKey> allEndpointKeys = 
                parameterCollector.getEndpointKeysForMainDomain(mainDomain);
            
            api.logging().raiseInfoEvent(String.format(
                "🔍 对子域名 %s 进行Arjun扫描（使用主域名 %s 的所有接口和参数）: 参数数=%d, 接口数=%d",
                targetHost, mainDomain, collectedParams.size(), allEndpointKeys.size()
            ));
            
            int scanned = 0;
            for (ParameterCollector.EndpointKey epKey : allEndpointKeys) {
                // ✅ 计算增量参数（未扫描过的）
                Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                    epKey.method, targetHost, epKey.contentType, epKey.endpoint, collectedParams
                );
                
                if (incrementalParams.isEmpty()) {
                    continue;
                }
                
                // ✅ 获取原始请求模板，但修改host为目标子域名
                HttpRequest originalTemplate = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                if (originalTemplate == null) {
                    continue;
                }
                
                // ✅ 修改请求的host为目标子域名
                HttpRequest modifiedRequest = modifyRequestHost(originalTemplate, targetHost);
                if (modifiedRequest == null) {
                    continue;
                }
                
                final HttpRequest finalRequest = modifiedRequest;
                final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                
                // 异步调用 Arjun
                arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                    if (result.isSuccess()) {
                        String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                        
                        if (!result.getFoundParameters().isEmpty()) {
                            api.logging().raiseInfoEvent(String.format(
                                "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                result.getFoundParameters().size(),
                                epKey.method, targetHost, epKey.contentType, epKey.endpoint, 
                                result.getFoundParameters()
                            ));
                            
                            // ✅ 通知UI显示结果
                            notifyArjunResult(mainDomain, targetHost, epKey.endpoint, result.getFoundParameters(), paramType);
                            
                            // ✅ 将发现的参数传递给漏洞扫描器
                            triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                        } else {
                            api.logging().raiseDebugEvent(String.format(
                                "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                epKey.method, targetHost, epKey.contentType, epKey.endpoint
                            ));
                            notifyArjunResult(mainDomain, targetHost, epKey.endpoint, new HashSet<>(), paramType);
                        }
                        
                        parameterManager.markParametersAsScanned(
                            epKey.method, targetHost, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    } else {
                        parameterManager.markParametersAsScanned(
                            epKey.method, targetHost, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    }
                }).exceptionally(ex -> {
                    api.logging().raiseErrorEvent("Arjun扫描异常: " + ex.getMessage());
                    parameterManager.markParametersAsScanned(
                        epKey.method, targetHost, epKey.contentType, epKey.endpoint, 
                        finalIncrementalParams
                    );
                    return null;
                });
                
                scanned++;
            }
            
            api.logging().raiseInfoEvent(String.format(
                "✅ 子域名 %s Arjun扫描完成: 扫描了 %d 个接口",
                targetHost, scanned
            ));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发子域名Arjun扫描时出错: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 修改请求的host为目标子域名
     * 使用buildRequest方法重新构建请求，但修改host
     */
    private HttpRequest modifyRequestHost(HttpRequest originalRequest, String targetHost) {
        try {
            // ✅ 获取原始请求的服务信息
            burp.api.montoya.http.HttpService originalService = originalRequest.httpService();
            int port = originalService.port();
            boolean isSecure = originalService.secure();
            
            // ✅ 获取原始URL的路径和查询字符串
            String originalUrl = originalRequest.url();
            URI originalUri = new URI(originalUrl);
            String scheme = originalUri.getScheme();
            String path = originalUri.getPath();
            if (path.isEmpty()) {
                path = "/";
            }
            String query = originalUri.getQuery();
            String fullPath = query != null ? path + "?" + query : path;
            
            // ✅ 获取原始请求的方法和Content-Type
            String method = originalRequest.method();
            String contentType = null;
            for (var header : originalRequest.headers()) {
                if ("Content-Type".equalsIgnoreCase(header.name())) {
                    contentType = header.value();
                    break;
                }
            }
            
            // ✅ 构建新URL（替换host）
            String newUrl;
            if (port != -1 && !(("https".equalsIgnoreCase(scheme) && port == 443) || 
                               ("http".equalsIgnoreCase(scheme) && port == 80))) {
                newUrl = scheme + "://" + targetHost + ":" + port + fullPath;
            } else {
                newUrl = scheme + "://" + targetHost + fullPath;
            }
            
            // ✅ 使用buildRequest方法构建新请求（会自动设置正确的Host头）
            return buildRequest(newUrl, method, contentType);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("修改请求host失败: " + e.getMessage());
            e.printStackTrace();
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
                
                // 异步调用 Arjun
                arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                    if (result.isSuccess()) {
                        // ✅ 优化日志：区分找到参数和未找到参数的情况
                        String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                        
                        if (!result.getFoundParameters().isEmpty()) {
                            api.logging().raiseInfoEvent(String.format(
                                "✅ Arjun 发现 %d 个参数: %s - %s",
                                result.getFoundParameters().size(),
                                epKey, result.getFoundParameters()
                            ));
                            
                            // ✅ 通知UI显示结果
                            notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType);
                            
                            triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                        } else {
                            // ✅ 修复：即使没有发现参数，也添加到表格中
                            api.logging().raiseDebugEvent(String.format(
                                "Arjun 扫描完成，未发现隐藏参数: %s",
                                epKey
                            ));
                            notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType);
                        }
                        parameterManager.markParametersAsScanned(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    } else {
                        parameterManager.markParametersAsScanned(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                    }
                }).exceptionally(ex -> {
                    // ✅ P0修复：添加异常处理，避免无限重试
                    api.logging().raiseErrorEvent("Arjun扫描异常: " + ex.getMessage());
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
            api.logging().raiseInfoEvent("开始对手动添加的端点进行 Arjun 探测: " + url);
            
            // 检查是否已被扫描过（任意method和contentType组合）
            URI uri = new URI(url);
            String host = uri.getHost();
            String endpoint = uri.getPath().isEmpty() ? "/" : uri.getPath();
            
            if (parameterManager.hasBeenScanned(host, endpoint)) {
                api.logging().raiseInfoEvent(String.format(
                    "端点 %s %s 已经被扫描过，跳过",
                    host, endpoint
                ));
                return;
            }
            
            CompletableFuture.runAsync(() -> {
                try {
                    performIncrementalArjunScan(true, url, runArjun, interfaceDiscoveryFirst);
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("手动端点 Arjun 扫描失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("触发手动端点 Arjun 扫描时出错: " + e.getMessage());
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
     * 执行增量 Arjun 扫描（从 SiteMap/Proxy 收集的流量）
     */
    private void performIncrementalArjunScan(boolean isManualEndpoint, String manualUrl, boolean runArjun, boolean interfaceDiscoveryFirst) {
        try {
            int totalScanned = 0;
            int totalSkipped = 0;
            int totalIncrementalParams = 0;
            
            if (isManualEndpoint && manualUrl != null) {
                // 手动添加的端点：尝试所有 method 和 contentType 组合
                totalScanned = scanManualEndpoint(manualUrl, runArjun, interfaceDiscoveryFirst);
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
                        // 计算增量参数（未扫描过的）
                        Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
                        );
                        
                        if (incrementalParams.isEmpty()) {
                            totalSkipped++;
                            api.logging().raiseDebugEvent(String.format(
                                "跳过 %s (无新参数)", epKey
                            ));
                            continue;
                        }
                        
                        // 获取该接口的请求模板
                        HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                        if (templateRequest == null) {
                            api.logging().raiseDebugEvent("未找到请求模板: " + epKey);
                            continue;
                        }
                        
                        final HttpRequest finalRequest = templateRequest;
                        final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                        totalIncrementalParams += incrementalParams.size();
                        
                        api.logging().raiseInfoEvent(String.format(
                            "扫描 %s, 增量参数: %d",
                            epKey, incrementalParams.size()
                        ));
                        
                        // 异步调用 Arjun
                        arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                            if (result.isSuccess()) {
                                // ✅ 优化日志：区分找到参数和未找到参数的情况
                                String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                                
                                if (!result.getFoundParameters().isEmpty()) {
                                    api.logging().raiseInfoEvent(String.format(
                                        "✅ Arjun 发现 %d 个参数: %s - %s",
                                        result.getFoundParameters().size(),
                                        epKey, result.getFoundParameters()
                                    ));
                                    
                                    // ✅ 通知UI显示结果
                                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType);
                                    
                                    // ✅ 将发现的参数传递给漏洞扫描器
                                    triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                } else {
                                    // ✅ 修复：即使没有发现参数，也添加到表格中
                                    api.logging().raiseDebugEvent(String.format(
                                        "Arjun 扫描完成，未发现隐藏参数: %s",
                                        epKey
                                    ));
                                    notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType);
                                }
                                
                                // 标记参数为已扫描
                                parameterManager.markParametersAsScanned(
                                    epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                                    finalIncrementalParams
                                );
                            } else {
                                api.logging().raiseErrorEvent(
                                    "Arjun 扫描失败: " + result.getErrorMessage()
                                );
                                
                                // 🔴 优化：即使失败也标记（避免无限重试）
                                parameterManager.markParametersAsScanned(
                                    epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                                    finalIncrementalParams
                                );
                                api.logging().raiseDebugEvent(String.format(
                                    "已标记失败的扫描参数，避免重复尝试: %s", epKey
                                ));
                            }
                        }).exceptionally(ex -> {
                            api.logging().raiseErrorEvent("Arjun 异步执行失败: " + ex.getMessage());
                            
                            // 🔴 优化：异常时也标记
                            parameterManager.markParametersAsScanned(
                                epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                            return null;
                        });
                        
                        totalScanned++;
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
                    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                        epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
                    );
                    
                    if (incrementalParams.isEmpty()) {
                        totalSkipped++;
                        continue;
                    }
                    
                    HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
                    if (templateRequest == null) {
                        continue;
                    }
                    
                    final HttpRequest finalRequest = templateRequest;
                    final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                    totalIncrementalParams += incrementalParams.size();
                    
                    // 异步调用 Arjun（与SiteMap模式相同的逻辑）
                    arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                        if (result.isSuccess()) {
                            // ✅ 优化日志：区分找到参数和未找到参数的情况
                            String paramType = epKey.contentType != null && epKey.contentType.contains("json") ? "JSON" : epKey.method;
                            
                            if (!result.getFoundParameters().isEmpty()) {
                                api.logging().raiseInfoEvent(String.format(
                                    "✅ Arjun 发现 %d 个参数: %s - %s",
                                    result.getFoundParameters().size(),
                                    epKey, result.getFoundParameters()
                                ));
                                
                                // ✅ 通知UI显示结果
                                notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, result.getFoundParameters(), paramType);
                                
                                // ✅ 将发现的参数传递给漏洞扫描器
                                triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                            } else {
                                // ✅ 修复：即使没有发现参数，也添加到表格中
                                api.logging().raiseDebugEvent(String.format(
                                    "Arjun 扫描完成，未发现隐藏参数: %s",
                                    epKey
                                ));
                                notifyArjunResult(mainDomain, epKey.host, epKey.endpoint, new HashSet<>(), paramType);
                            }
                            
                            parameterManager.markParametersAsScanned(
                                epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                        } else {
                            // 🔴 优化2：失败也标记（避免无限重试）
                            parameterManager.markParametersAsScanned(
                                epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                                finalIncrementalParams
                            );
                        }
                    }).exceptionally(ex -> {
                        // 🔴 优化2：异常也标记
                        parameterManager.markParametersAsScanned(
                            epKey.method, epKey.host, epKey.contentType, epKey.endpoint, 
                            finalIncrementalParams
                        );
                        return null;
                    });
                    
                    totalScanned++;
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
     * @return 扫描的组合数量
     */
    private int scanManualEndpoint(String url, boolean runArjun, boolean interfaceDiscoveryFirst) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String endpoint = uri.getPath().isEmpty() ? "/" : uri.getPath();
            String mainDomain = extractMainDomain(host);
            
            // 获取该主域名收集的所有参数
            Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
            
            // 如果启用了关键词收集，将关键词也加入参数列表
            if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                collectedParams.addAll(keywords);
            }
            
            // 定义最常见的 HTTP 方法和 Content-Type 组合
            // 只测试最常见的3种组合，覆盖大多数场景
            String[][] combinations = {
                {"GET", "application/x-www-form-urlencoded"},                                    // GET请求（无Content-Type）
                {"POST", "application/x-www-form-urlencoded"},     // POST表单提交
                {"POST", "application/json"}                      // POST JSON API
            };
            
            int scannedCount = 0;
            
            api.logging().raiseInfoEvent(String.format(
                "手动端点 %s: 将尝试 %d 个最常见的组合",
                url, combinations.length
            ));
            
            // 尝试所有组合
            for (String[] combo : combinations) {
                String method = combo[0];
                String contentType = combo[1];
                
                // ✅ 修复：对于GET请求，使用null作为contentType（因为GET不需要Content-Type）
                String contentTypeForKey = ("GET".equalsIgnoreCase(method)) ? null : contentType;
                
                // 计算增量参数
                Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                    method, host, contentTypeForKey, endpoint, collectedParams
                );
                    
                    if (incrementalParams.isEmpty() && runArjun) {
                        String displayContentType = contentTypeForKey != null ? contentTypeForKey : "N/A";
                        api.logging().raiseDebugEvent(String.format(
                            "跳过 %s %s (%s) %s (无新参数)",
                            method, host, displayContentType, endpoint
                        ));
                        continue;
                    }
                    
                    // 构建请求
                    HttpRequest request = buildRequest(url, method, contentType);
                    if (request == null) {
                        continue;
                    }
                    
                    final HttpRequest finalRequest = request;
                    final Set<String> finalIncrementalParams = new HashSet<>(incrementalParams);
                    final String finalMethod = method;
                    final String finalContentType = contentTypeForKey;  // ✅ 使用修正后的contentType
                    final String finalDisplayContentType = contentTypeForKey != null ? contentTypeForKey : "N/A";
                    
                    api.logging().raiseInfoEvent(String.format(
                        "%s手动端点: %s %s (%s) %s, 增量参数: %d",
                        runArjun ? "扫描" : "验证",
                        method, host, finalDisplayContentType, endpoint, incrementalParams.size()
                    ));
                    
                    // ✅ 如果runArjun=true，根据interfaceDiscoveryFirst决定是否先探测接口
                    if (runArjun) {
                        // ✅ 如果需要先探测接口，先进行接口探测
                        if (interfaceDiscoveryFirst) {
                            try {
                                // 1. 先发送原始路径请求
                                // ✅ 修复：检查HTTP服务是否可用
                                if (api.http() == null) {
                                    api.logging().raiseErrorEvent("HTTP服务不可用，无法发送接口探测请求");
                                    continue;
                                }
                                
                                HttpRequestResponse originalResponse = api.http().sendRequest(finalRequest);
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
                                        finalMethod, host, finalDisplayContentType, endpoint
                                    ));
                                    continue;  // 跳过此接口
                                }
                                
                                // 2. ✅ 如果原始路径返回非404，需要和随机路径的响应码和响应体对比
                                boolean endpointExists = false;
                                if (originalStatusCode != 404 && originalStatusCode != -1) {
                                    // ✅ 统一处理：2xx、3xx、其他4xx都需要验证
                                    // ✅ 优化：使用缓存的随机路径响应，避免重复发送请求
                                    endpointExists = validateEndpointWithRandomPath(
                                        url, finalRequest, originalResponse, 
                                        originalStatusCode, originalResponseBody,
                                        finalMethod, host, finalContentType, endpoint, mainDomain
                                    );
                                } else if (originalStatusCode >= 500) {
                                    // 5xx服务器错误，可能是临时问题，保守处理为接口存在
                                    api.logging().raiseInfoEvent(String.format(
                                        "接口探测: %s %s (%s) %s - 状态码 %d (服务器错误)，继续Arjun扫描",
                                        finalMethod, host, finalDisplayContentType, endpoint, originalStatusCode
                                    ));
                                    endpointExists = true;
                                } else if (originalStatusCode == -1) {
                                    // 原始请求无响应，跳过
                                    api.logging().raiseDebugEvent(String.format(
                                        "接口探测: %s %s (%s) %s - 无响应，跳过Arjun扫描",
                                        finalMethod, host, finalDisplayContentType, endpoint
                                    ));
                                    continue;
                                }
                                
                                // ✅ 如果接口不存在，跳过Arjun扫描
                                if (!endpointExists) {
                                    continue;  // 接口不存在，跳过
                                }
                                
                                // ✅ 接口存在，继续进行Arjun参数探测
                                api.logging().raiseInfoEvent(String.format(
                                    "接口验证通过，开始Arjun参数探测: %s %s (%s) %s",
                                    finalMethod, host, finalDisplayContentType, endpoint
                                ));
                                
                            } catch (Exception sendError) {
                                // 接口探测失败，保守处理，继续Arjun扫描
                                api.logging().raiseDebugEvent(String.format(
                                    "接口探测失败: %s，继续Arjun扫描: %s",
                                    sendError.getMessage(), endpoint
                                ));
                            }
                        }
                        
                        // ✅ 进行Arjun参数探测
                        // 如果interfaceDiscoveryFirst=true，此时接口已验证存在
                        // 如果interfaceDiscoveryFirst=false，直接进行参数探测（不先探测接口）
                        arjunService.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                            if (result.isSuccess()) {
                                String paramType = finalContentType != null && finalContentType.contains("json") ? "JSON" : finalMethod;
                                
                                if (!result.getFoundParameters().isEmpty()) {
                                    api.logging().raiseInfoEvent(String.format(
                                        "✅ Arjun 发现 %d 个参数: %s %s (%s) %s - %s",
                                        result.getFoundParameters().size(),
                                        finalMethod, host, finalContentType, endpoint, 
                                        result.getFoundParameters()
                                    ));
                                    
                                    notifyArjunResult(mainDomain, host, endpoint, result.getFoundParameters(), paramType);
                                    triggerVulnerabilityScan(finalRequest, result.getFoundParameters());
                                } else {
                                    // ✅ 修复：即使没有发现参数，也添加到表格中
                                    api.logging().raiseDebugEvent(String.format(
                                        "Arjun 扫描完成，未发现隐藏参数: %s %s (%s) %s",
                                        finalMethod, host, finalContentType, endpoint
                                    ));
                                    notifyArjunResult(mainDomain, host, endpoint, new HashSet<>(), paramType);
                                }
                                
                                parameterManager.markParametersAsScanned(
                                    finalMethod, host, finalContentType, endpoint, 
                                    finalIncrementalParams
                                );
                            } else {
                                api.logging().raiseErrorEvent(
                                    "Arjun 扫描失败: " + result.getErrorMessage()
                                );
                            }
                        }).exceptionally(ex -> {
                            api.logging().raiseErrorEvent("Arjun 异步执行失败: " + ex.getMessage());
                            return null;
                        });
                    } else {
                        // ✅ 接口探测：使用随机路径对比验证接口是否存在
                        // 目的：避免泛解析、反射等导致的误判
                        // ✅ 优化：随机路径由validateEndpointWithRandomPath内部缓存管理，每种类型只发送一次
                        try {
                            // 1. 先发送原始路径请求
                            // ✅ 修复：检查HTTP服务是否可用
                            if (api.http() == null) {
                                api.logging().raiseErrorEvent("HTTP服务不可用，无法发送接口探测请求");
                                continue;
                            }
                            
                            HttpRequestResponse originalResponse = api.http().sendRequest(finalRequest);
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
                                    finalMethod, host, finalDisplayContentType, endpoint
                                ));
                                
                                // ✅ 通知UI接口探测结果（仅当runArjun=false时，即纯接口探测模式）
                                if (!runArjun && activeProbeTab != null) {
                                    try {
                                        // ✅ 修复：使用已定义的mainDomain变量，不重复声明
                                        activeProbeTab.addInterfaceDiscoveryResult(mainDomain, host, endpoint, finalMethod, finalContentType, false, System.currentTimeMillis());
                                    } catch (Exception e) {
                                        // 忽略错误
                                    }
                                }
                                
                                continue;  // 跳过此接口
                            }
                            
                            // 2. ✅ 如果原始路径返回非404（包括其他4xx、2xx、3xx），需要和随机路径的响应码和响应体对比
                            if (originalStatusCode != 404 && originalStatusCode != -1) {
                                // ✅ 统一处理：2xx、3xx、其他4xx都需要验证
                                // ✅ 优化：使用缓存的随机路径响应，避免重复发送请求
                                boolean endpointExists = validateEndpointWithRandomPath(
                                    url, finalRequest, originalResponse, 
                                    originalStatusCode, originalResponseBody,
                                    finalMethod, host, finalContentType, endpoint, mainDomain
                                );
                                
                                if (!endpointExists) {
                                    // ✅ 通知UI接口探测结果（仅当runArjun=false时，即纯接口探测模式）
                                    if (!runArjun && activeProbeTab != null) {
                                        try {
                                            // ✅ 修复：使用已定义的mainDomain变量，不重复声明
                                            activeProbeTab.addInterfaceDiscoveryResult(mainDomain, host, endpoint, finalMethod, finalContentType, false, System.currentTimeMillis());
                                        } catch (Exception e) {
                                            // 忽略错误
                                        }
                                    }
                                    continue;  // 接口不存在，跳过
                                }
                                
                                // ✅ 通知UI接口探测结果（仅当runArjun=false时，即纯接口探测模式）
                                if (!runArjun && activeProbeTab != null) {
                                    try {
                                        // ✅ 修复：使用已定义的mainDomain变量，不重复声明
                                        activeProbeTab.addInterfaceDiscoveryResult(mainDomain, host, endpoint, finalMethod, finalContentType, true, System.currentTimeMillis());
                                    } catch (Exception e) {
                                        // 忽略错误
                                    }
                                }
                            } else if (originalStatusCode >= 500) {
                                // 5xx服务器错误，可能是临时问题
                                api.logging().raiseInfoEvent(String.format(
                                    "接口探测: %s %s (%s) %s - 状态码 %d (服务器错误)",
                                    finalMethod, host, finalDisplayContentType, endpoint, originalStatusCode
                                ));
                                // 继续处理，但标记为不确定
                                if (originalResponse != null) {
                                    parameterCollector.collectFromRequest(finalRequest);
                                    if (originalResponse.response() != null) {
                                        parameterCollector.collectFromResponse(finalRequest, originalResponse.response());
                                    }
                                }
                            } else if (originalStatusCode == -1) {
                                // 原始请求无响应
                                api.logging().raiseDebugEvent(String.format(
                                    "接口探测: %s %s (%s) %s - 无响应",
                                    finalMethod, host, finalDisplayContentType, endpoint
                                ));
                            }
                            
                        } catch (Exception sendError) {
                            api.logging().raiseErrorEvent("接口探测失败: " + sendError.getMessage());
                        }
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
    private HttpRequest buildRequest(String url, String method, String contentType) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            int port = uri.getPort();
            String path = uri.getPath();
            if (path.isEmpty()) {
                path = "/";
            }
            String query = uri.getQuery();
            String fullPath = query != null ? path + "?" + query : path;
            
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
            headers.append("Host: ").append(host);
            if (port != -1 && !(("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80))) {
                headers.append(":").append(port);
            }
            headers.append("\r\n");
            headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n");
            headers.append("Accept: */*\r\n");
            
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
                    } else if ("application/json".equals(contentType)) {
                        // JSON API：空的JSON对象
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
            return null;
        }
    }
    
    /**
     * ✅ 构建带随机路径的请求（用于验证接口是否存在）
     * 使用随机路径对比原始路径，判断接口是否真实存在
     */
    private HttpRequest buildRequestWithRandomPath(String originalUrl, String method, String contentType, String randomPath) {
        try {
            URI originalUri = new URI(originalUrl);
            String host = originalUri.getHost();
            String scheme = originalUri.getScheme();
            int port = originalUri.getPort();
            
            // 构建随机路径URL
            String randomUrl;
            if (port != -1) {
                randomUrl = scheme + "://" + host + ":" + port + randomPath;
            } else {
                randomUrl = scheme + "://" + host + randomPath;
            }
            
            // 使用相同的method和contentType构建请求
            return buildRequest(randomUrl, method, contentType);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建随机路径请求失败: " + e.getMessage());
            return null;
        }
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
            // ✅ 发送第一个随机路径请求
            HttpRequest randomPathRequest1 = buildRequestWithRandomPath(url, method, contentType, randomPath1);
            if (randomPathRequest1 == null) {
                api.logging().raiseDebugEvent("无法构建第一个随机路径请求，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
            HttpRequestResponse randomResponse1 = api.http().sendRequest(randomPathRequest1);
            if (randomResponse1 == null || randomResponse1.response() == null) {
                api.logging().raiseDebugEvent("第一个随机路径请求无响应，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
            int randomStatusCode1 = randomResponse1.response().statusCode();
            String randomResponseBody1 = randomResponse1.response().bodyToString();
            
            // ✅ 发送第二个随机路径请求
            HttpRequest randomPathRequest2 = buildRequestWithRandomPath(url, method, contentType, randomPath2);
            if (randomPathRequest2 == null) {
                api.logging().raiseDebugEvent("无法构建第二个随机路径请求，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
            HttpRequestResponse randomResponse2 = api.http().sendRequest(randomPathRequest2);
            if (randomResponse2 == null || randomResponse2.response() == null) {
                api.logging().raiseDebugEvent("第二个随机路径请求无响应，保守处理为接口存在");
                collectAndNotify(originalRequest, originalResponse, mainDomain, host, endpoint, method, contentType, true);
                return true;
            }
            
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
    }
    
    /**
     * ✅ 通知UI接口探测结果
     */
    private void notifyInterfaceResult(String mainDomain, String host, String endpoint, String method,
                                      String contentType, boolean exists) {
        if (activeProbeTab != null) {
            try {
                activeProbeTab.addInterfaceDiscoveryResult(mainDomain, host, endpoint, method, contentType, exists, System.currentTimeMillis());
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
                String host = new URI(url).getHost();
                String mainDomain = extractMainDomain(host);
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
     * 功能：清空已扫描的参数记录，允许重新扫描之前扫描过的端点
     */
    public void clearArjunCache() {
        parameterManager.clearScannedParameters();
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
            // 获取Content-Type
            String contentType = getContentType(originalRequest);
            
            // ✅ 1. 基于原始请求，添加Arjun发现的参数
            HttpRequest requestWithParams = originalRequest;
            
            // 根据请求类型添加参数
            if ("GET".equalsIgnoreCase(originalRequest.method())) {
                // GET: 添加URL参数
                for (String paramName : foundParams) {
                    requestWithParams = requestWithParams.withAddedParameters(
                        HttpParameter.urlParameter(paramName, "xprobe_test")
                    );
                }
            } else if (contentType != null && contentType.contains("application/json")) {
                // JSON: 需要合并到JSON body（使用Arjun的方式）
                requestWithParams = buildJsonRequestWithParams(originalRequest, foundParams);
            } else {
                // POST表单: 添加body参数
                for (String paramName : foundParams) {
                    requestWithParams = requestWithParams.withAddedParameters(
                        HttpParameter.bodyParameter(paramName, "xprobe_test")
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
     * ✅ 构建包含参数的JSON请求
     */
    @SuppressWarnings("unchecked")
    private HttpRequest buildJsonRequestWithParams(HttpRequest originalRequest, Set<String> paramNames) {
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
            
            // 添加发现的参数（使用测试值）
            for (String paramName : paramNames) {
                jsonMap.put(paramName, "xprobe_test");
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
     */
    private void notifyArjunResult(String mainDomain, String host, String endpoint, Set<String> foundParameters, 
                                   String parameterType) {
        long timestamp = System.currentTimeMillis();
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
    public void shutdown() {
        api.logging().raiseInfoEvent("关闭RealtimeScanner资源...");
        
        if (arjunService != null) {
            arjunService.shutdown();
        }
    }
}


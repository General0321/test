package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.GlobalFilter;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

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
    private final ArjunIntegration arjunIntegration;
    private final ExternalToolConfig toolConfig;
    
    // 使用新的参数收集器和管理器
    private final ParameterCollector parameterCollector;
    private final ParameterManager parameterManager;
    
    // 被动扫描去重机制
    private final Set<String> passiveScanProcessedKeys = ConcurrentHashMap.newKeySet();
    
    public RealtimeScannerRefactored(MontoyaApi api, ConfigurationManager configManager, 
                                    GlobalFilter globalFilter) {
        this.api = api;
        this.globalFilter = globalFilter;
        this.toolConfig = new ExternalToolConfig();
        this.arjunIntegration = new ArjunIntegration(api, toolConfig);
        
        // 初始化新的组件
        this.parameterCollector = new ParameterCollector(api);
        this.parameterManager = new ParameterManager(api);
        
        api.logging().raiseInfoEvent("实时扫描器已初始化（使用参数收集器和管理器）");
    }
    
    // ========== 被动收集参数 ==========
    
    /**
     * 处理新的 HTTP 请求（被动收集参数）
     * 
     * @param request HTTP 请求
     */
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
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("处理新请求时出错: " + e.getMessage());
        }
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
    
    /**
     * 对手动添加的端点进行 Arjun 扫描
     * 注意：手动添加的端点会尝试所有 method 和 contentType 组合
     * 
     * @param url 手动添加的 URL
     */
    public void triggerManualEndpointScan(String url) {
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
                    performIncrementalArjunScan(true, url);
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
     * 执行增量 Arjun 扫描（从 SiteMap/Proxy 收集的流量）
     */
    private void performIncrementalArjunScan() {
        performIncrementalArjunScan(false, null);
    }
    
    /**
     * 执行增量 Arjun 扫描
     * 
     * @param isManualEndpoint 是否为手动添加的端点
     * @param manualUrl 手动添加的URL（如果isManualEndpoint=true）
     */
    private void performIncrementalArjunScan(boolean isManualEndpoint, String manualUrl) {
        try {
            int totalScanned = 0;
            int totalSkipped = 0;
            int totalIncrementalParams = 0;
            
            if (isManualEndpoint && manualUrl != null) {
                // 手动添加的端点：尝试所有 method 和 contentType 组合
                totalScanned = scanManualEndpoint(manualUrl);
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
                        arjunIntegration.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                            if (result.isSuccess()) {
                                api.logging().raiseInfoEvent(String.format(
                                    "Arjun 发现参数: %s - %s",
                                    epKey, result.getFoundParameters()
                                ));
                                
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
                    arjunIntegration.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                        if (result.isSuccess()) {
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
    private int scanManualEndpoint(String url) {
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
                {"GET", "application/x-www-form-urlencoded"},   // 普通GET请求
                {"POST", "application/x-www-form-urlencoded"},  // 表单提交
                {"POST", "application/json"}                    // JSON API
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
                    // 计算增量参数
                    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
                        method, host, contentType, endpoint, collectedParams
                    );
                    
                    if (incrementalParams.isEmpty()) {
                        api.logging().raiseDebugEvent(String.format(
                            "跳过 %s %s (%s) %s (无新参数)",
                            method, host, contentType, endpoint
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
                    final String finalContentType = contentType;
                    
                    api.logging().raiseInfoEvent(String.format(
                        "扫描手动端点: %s %s (%s) %s, 增量参数: %d",
                        method, host, contentType, endpoint, incrementalParams.size()
                    ));
                    
                    // 异步调用 Arjun
                    arjunIntegration.scan(finalRequest, finalIncrementalParams).thenAccept(result -> {
                        if (result.isSuccess()) {
                            api.logging().raiseInfoEvent(String.format(
                                "Arjun 发现参数: %s %s (%s) %s - %s",
                                finalMethod, host, finalContentType, endpoint, 
                                result.getFoundParameters()
                            ));
                            
                            // 标记参数为已扫描
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
     */
    private HttpRequest buildRequest(String url, String method, String contentType) {
        try {
            // 构建基础请求
            String requestLine = method + " " + url + " HTTP/1.1";
            String headers = "Host: " + new URI(url).getHost() + "\r\n" +
                           "Content-Type: " + contentType + "\r\n" +
                           "User-Agent: Mozilla/5.0\r\n";
            
            String requestStr = requestLine + "\r\n" + headers + "\r\n";
            HttpRequest request = HttpRequest.httpRequest(requestStr);
            
            return request;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("构建请求失败: " + e.getMessage());
            return null;
        }
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
     * 检查被动扫描是否已处理
     */
    public boolean isPassiveScanProcessed(String method, String host, String path, 
                                         String contentType, String parameterName, 
                                         String scanType) {
        String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
        return passiveScanProcessedKeys.contains(key);
    }
    
    /**
     * 标记被动扫描为已处理
     */
    public void markPassiveScanProcessed(String method, String host, String path, 
                                        String contentType, String parameterName, 
                                        String scanType) {
        String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
        passiveScanProcessedKeys.add(key);
    }
    
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
        
        // Set.add()对于ConcurrentHashMap.newKeySet()来说是原子操作
        // 如果key已存在，返回false；如果key不存在，添加并返回true
        boolean wasAdded = passiveScanProcessedKeys.add(key);
        
        // 返回相反值：
        // wasAdded=true 表示首次添加（未被处理过），返回false（应该扫描）
        // wasAdded=false 表示已存在（已被处理过），返回true（应该跳过）
        return !wasAdded;
    }
    
    /**
     * 检查并标记被动扫描已处理（旧版：向后兼容）
     * 
     * @deprecated 使用 checkAndMarkPassiveScanProcessed(method, host, path, contentType, targetIdentifier, config)
     */
    @Deprecated
    public boolean checkAndMarkPassiveScanProcessed(String method, String host, String path, 
                                                   String contentType, String parameterName, 
                                                   String scanType) {
        String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
        boolean wasAdded = passiveScanProcessedKeys.add(key);
        return !wasAdded;
    }
    
    /**
     * 生成被动扫描去重标识符（旧版：向后兼容）
     * 
     * @deprecated 使用 DeduplicationKeyGenerator.generateKey()
     */
    @Deprecated
    private String generatePassiveScanKey(String method, String host, String path, 
                                         String contentType, String parameterName, 
                                         String scanType) {
        String cleanPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
        String normalizedContentType = normalizeContentType(contentType);
        return method + "|" + host + "|" + cleanPath + "|" + normalizedContentType + 
               "|" + parameterName + "|" + scanType;
    }
    
    /**
     * 标准化 Content-Type
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "application/x-www-form-urlencoded";
        }
        
        String lower = contentType.toLowerCase();
        if (lower.contains("json")) {
            return "application/json";
        } else if (lower.contains("xml")) {
            return "application/xml";
        } else if (lower.contains("form")) {
            return "application/x-www-form-urlencoded";
        } else if (lower.contains("multipart")) {
            return "multipart/form-data";
        }
        return contentType;
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
     * 提取主域名
     */
    private String extractMainDomain(String host) {
        if (host == null || host.isEmpty()) {
            return host;
        }
        
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
     * 获取外部工具配置
     */
    public ExternalToolConfig getToolConfig() {
        return toolConfig;
    }
    
    // ========== 扫描控制方法 ==========
    
    /**
     * 启动实时扫描（被动参数收集）
     * 注意：实时扫描自动运行，此方法主要用于UI控制
     */
    public void startRealtimeScanning() {
        api.logging().raiseInfoEvent("实时参数收集已启动");
        // 实际上参数收集是自动进行的，通过 processNewRequest() 被动收集
        // 这里可以添加额外的启动逻辑，比如清理缓存等
    }
    
    /**
     * 停止实时扫描
     * 注意：实际上只是停止Arjun主动探测，被动收集继续进行
     */
    public void stopRealtimeScanning() {
        api.logging().raiseInfoEvent("实时参数收集已停止");
        // 可以添加停止逻辑，如清空待扫描队列等
    }
    
    /**
     * 获取主机统计信息（用于UI显示）
     * 注意：由于重构，现在按主域名管理，返回空Map
     * @deprecated 建议使用 getDomainStatistics()
     */
    @Deprecated
    public Map<String, Object> getHostStatistics() {
        // 为了向后兼容，返回空Map
        return new HashMap<>();
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
            
            stats.put(mainDomain, new DomainStatistics(
                mainDomain,
                hosts.size(),
                endpoints.size(),
                parameters.size(),
                keywords.size(),
                System.currentTimeMillis()
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
}


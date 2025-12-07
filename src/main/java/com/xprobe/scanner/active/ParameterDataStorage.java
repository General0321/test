package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 参数收集数据持久化存储
 * 负责保存和加载收集的接口、参数数据
 * 
 * 存储格式：JSON
 * 文件路径：~/.xprobe/parameter_data.json
 * 
 * 数据结构：
 * {
 *   "mainDomains": {
 *     "example.com": {
 *       "mainDomain": "example.com",
 *       "hosts": ["www.example.com", "api.example.com"],
 *       "endpoints": [
 *         {
 *           "host": "www.example.com",
 *           "endpoint": "/api/user",
 *           "method": "GET",
 *           "contentType": "application/json",
 *           "parameters": ["id", "name"]
 *         }
 *       ],
 *       "keywords": ["keyword1", "keyword2"],
 *       "lastUpdateTime": 1234567890
 *     }
 *   }
 * }
 */
public class ParameterDataStorage {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.xprobe";
    private static final String DATA_FILE = CONFIG_DIR + "/parameter_data.json";
    
    private final MontoyaApi api;
    private final ObjectMapper objectMapper;
    
    public ParameterDataStorage(MontoyaApi api) {
        this.api = api;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
    
    /**
     * 保存参数收集数据
     */
    public void save(ParameterCollector collector) throws IOException {
        try {
            // 创建配置目录
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                api.logging().raiseDebugEvent("创建配置目录: " + CONFIG_DIR);
            }
            
            // 构建数据模型
            ParameterDataModel dataModel = buildDataModel(collector);
            
            // 写入JSON文件
            objectMapper.writeValue(new File(DATA_FILE), dataModel);
            
            api.logging().raiseInfoEvent("参数收集数据已保存到: " + DATA_FILE);
            
        } catch (IOException e) {
            api.logging().raiseErrorEvent("保存参数收集数据失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 加载参数收集数据
     */
    public ParameterDataModel load() {
        try {
            Path dataFile = Paths.get(DATA_FILE);
            
            if (Files.exists(dataFile)) {
                ParameterDataModel model = objectMapper.readValue(new File(DATA_FILE), ParameterDataModel.class);
                api.logging().raiseInfoEvent("参数收集数据已从磁盘加载: " + DATA_FILE);
                return model;
            } else {
                api.logging().raiseDebugEvent("参数收集数据文件不存在: " + DATA_FILE);
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("加载参数收集数据失败: " + e.getMessage());
        }
        
        // 返回空模型
        return new ParameterDataModel();
    }
    
    /**
     * ✅ 从存储的数据恢复到 ParameterCollector
     * 恢复所有收集的接口、参数和关键词数据
     */
    public void restoreToCollector(ParameterCollector collector, MontoyaApi api) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains == null || model.mainDomains.isEmpty()) {
                api.logging().raiseDebugEvent("没有可恢复的数据");
                return;
            }
            
            api.logging().raiseInfoEvent("开始恢复参数收集数据...");
            int restoredEndpoints = 0;
            int restoredParameters = 0;
            int restoredKeywords = 0;
            
            for (MainDomainData mainDomainData : model.mainDomains.values()) {
                // ✅ 优先使用新的按子域名组织的数据结构
                if (mainDomainData.hostDataMap != null && !mainDomainData.hostDataMap.isEmpty()) {
                    // 新格式：按子域名组织
                    for (HostData hostData : mainDomainData.hostDataMap.values()) {
                        if (hostData.endpoints != null) {
                            for (EndpointData epData : hostData.endpoints) {
                                try {
                                    HttpRequest request = buildRequestFromEndpointData(epData, api);
                                    if (request != null) {
                                        // ✅ 直接调用 collectFromRequest，它现在会即使没有参数也添加接口
                                        collector.collectFromRequest(request);
                                        restoredEndpoints++;
                                    }
                                } catch (Exception e) {
                                    api.logging().raiseDebugEvent("恢复接口失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
                                }
                            }
                        }
                        // ✅ 直接恢复该子域名的参数（不仅仅是统计）
                        if (hostData.parameters != null && !hostData.parameters.isEmpty()) {
                            String host = hostData.host;
                            String mainDomain = mainDomainData.mainDomain;
                            api.logging().raiseInfoEvent(String.format(
                                "恢复子域 %s 的参数: %d 个参数 (主域: %s)",
                                host, hostData.parameters.size(), mainDomain
                            ));
                            
                            // ✅ 诊断：恢复前检查主域参数数
                            Set<String> beforeParams = collector.getParametersForMainDomain(mainDomain);
                            api.logging().raiseInfoEvent(String.format(
                                "恢复前: 主域 %s 的参数数=%d",
                                mainDomain, beforeParams.size()
                            ));
                            
                            for (String parameter : hostData.parameters) {
                                // 为每个接口添加参数（如果接口已恢复）
                                // 如果接口未恢复，至少添加到主域参数集合中
                                collector.addParameterDirectly(host, parameter, mainDomain);
                                restoredParameters++;
                            }
                            
                            // ✅ 诊断：恢复后检查主域参数数
                            Set<String> afterParams = collector.getParametersForMainDomain(mainDomain);
                            api.logging().raiseInfoEvent(String.format(
                                "恢复后: 主域 %s 的参数数=%d (新增: %d)",
                                mainDomain, afterParams.size(), afterParams.size() - beforeParams.size()
                            ));
                        }
                    }
                } else if (mainDomainData.endpoints != null) {
                    // 向后兼容：旧格式（扁平结构）
                    for (EndpointData epData : mainDomainData.endpoints) {
                        try {
                            HttpRequest request = buildRequestFromEndpointData(epData, api);
                            if (request != null) {
                                // ✅ 直接调用 collectFromRequest，它现在会即使没有参数也添加接口
                                collector.collectFromRequest(request);
                                restoredEndpoints++;
                            }
                        } catch (Exception e) {
                            api.logging().raiseDebugEvent("恢复接口失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
                        }
                    }
                }
                
                // ✅ 恢复关键词（按主域名）
                if (mainDomainData.keywords != null && !mainDomainData.keywords.isEmpty()) {
                    // 关键词需要通过响应来恢复，这里先记录
                    restoredKeywords += mainDomainData.keywords.size();
                }
            }
            
            api.logging().raiseInfoEvent(String.format(
                "✅ 参数收集数据恢复完成: 恢复了 %d 个接口, %d 个参数, %d 个关键词", 
                restoredEndpoints, restoredParameters, restoredKeywords
            ));
            
            // ✅ 修复：恢复数据时不应该修改原文件，移除自动保存逻辑
            // 如果需要规范化数据，应该由用户手动触发保存操作
            
            // ✅ 诊断：检查恢复后的参数是否正确填充到主域参数集合中
            for (MainDomainData mainDomainData : model.mainDomains.values()) {
                String mainDomain = mainDomainData.mainDomain;
                Set<String> restoredParams = collector.getParametersForMainDomain(mainDomain);
                
                // ✅ 计算期望参数数（去重后的参数数，因为 allParameters 是 Set）
                Set<String> expectedParams = new HashSet<>();
                int totalCount = 0;
                if (mainDomainData.hostDataMap != null) {
                    for (HostData hostData : mainDomainData.hostDataMap.values()) {
                        if (hostData.parameters != null) {
                            totalCount += hostData.parameters.size();
                            expectedParams.addAll(hostData.parameters);
                        }
                    }
                }
                
                api.logging().raiseInfoEvent(String.format(
                    "诊断: 主域 %s 恢复后的参数数=%d (期望去重后: %d, 原始总数: %d)",
                    mainDomain, restoredParams.size(), expectedParams.size(), totalCount
                ));
                
                // ✅ 如果参数数不匹配，列出缺失的参数
                if (restoredParams.size() < expectedParams.size()) {
                    Set<String> missingParams = new HashSet<>(expectedParams);
                    missingParams.removeAll(restoredParams);
                    api.logging().raiseInfoEvent(String.format(
                        "缺失的参数数: %d, 前10个缺失参数: %s",
                        missingParams.size(), 
                        missingParams.stream().limit(10).collect(java.util.stream.Collectors.joining(", "))
                    ));
                }
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("恢复参数收集数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ✅ 一键清空所有数据
     */
    public boolean clearAll() {
        try {
            ParameterDataModel model = new ParameterDataModel();
            model.mainDomains = new HashMap<>();
            objectMapper.writeValue(new File(DATA_FILE), model);
            api.logging().raiseInfoEvent("已清空所有参数收集数据");
            return true;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("清空所有数据失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除指定主域名的数据
     */
    public boolean deleteMainDomain(String mainDomain) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains != null && model.mainDomains.containsKey(mainDomain)) {
                model.mainDomains.remove(mainDomain);
                objectMapper.writeValue(new File(DATA_FILE), model);
                api.logging().raiseInfoEvent("已删除主域名数据: " + mainDomain);
                return true;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("删除主域名数据失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * ✅ 删除指定子域名的数据（适配新的数据结构）
     */
    public boolean deleteHost(String mainDomain, String host) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains != null && model.mainDomains.containsKey(mainDomain)) {
                MainDomainData mainDomainData = model.mainDomains.get(mainDomain);
                
                // ✅ 优先使用新的按子域名组织的数据结构
                if (mainDomainData.hostDataMap != null) {
                    mainDomainData.hostDataMap.remove(host);
                }
                
                // ✅ 向后兼容：处理旧格式
                if (mainDomainData.endpoints != null) {
                    mainDomainData.endpoints.removeIf(ep -> ep.host.equals(host));
                }
                
                // 从hosts列表中移除
                if (mainDomainData.hosts != null) {
                    mainDomainData.hosts.remove(host);
                }
                
                // 如果hosts为空，删除整个主域名
                if (mainDomainData.hosts == null || mainDomainData.hosts.isEmpty()) {
                    model.mainDomains.remove(mainDomain);
                }
                
                objectMapper.writeValue(new File(DATA_FILE), model);
                api.logging().raiseInfoEvent(String.format("已删除子域名数据: %s (主域名: %s)", host, mainDomain));
                return true;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("删除子域名数据失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 获取文件路径
     */
    public String getDataFilePath() {
        return DATA_FILE;
    }
    
    /**
     * 检查数据文件是否存在
     */
    public boolean exists() {
        return Files.exists(Paths.get(DATA_FILE));
    }
    
    // ========== 内部方法 ==========
    
    /**
     * ✅ 从 ParameterCollector 构建数据模型（优化版：按主域名->子域名结构）
     * 每个子域名保存有哪些接口和参数
     */
    private ParameterDataModel buildDataModel(ParameterCollector collector) {
        ParameterDataModel model = new ParameterDataModel();
        model.mainDomains = new HashMap<>();
        
        for (String mainDomain : collector.getAllMainDomains()) {
            MainDomainData mainDomainData = new MainDomainData();
            mainDomainData.mainDomain = mainDomain;
            mainDomainData.hosts = new ArrayList<>(collector.getHostsForMainDomain(mainDomain));
            mainDomainData.hostDataMap = new HashMap<>(); // ✅ 按子域名组织数据
            mainDomainData.keywords = new ArrayList<>(collector.getKeywordsForMainDomain(mainDomain));
            mainDomainData.lastUpdateTime = collector.getLastUpdateTimeForDomain(mainDomain);
            
            // ✅ 获取所有接口，按子域名分组
            Set<ParameterCollector.EndpointKey> endpointKeys = 
                collector.getEndpointKeysForMainDomain(mainDomain);
            
            // ✅ 按子域名组织数据
            Map<String, HostData> hostDataMap = new HashMap<>();
            
            // ✅ 修复：使用 Map 去重，确保同一个 endpoint（相同 method+host+contentType+path）只保存一次
            Map<String, EndpointData> endpointMap = new HashMap<>();
            
            for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                HttpRequest template = collector.getEndpointTemplate(mainDomain, epKey);
                if (template != null) {
                    String host = epKey.host;
                    
                    // ✅ 生成去重 key（method|host|contentType|endpoint，endpoint 已不包含 query）
                    String endpointKey = epKey.method + "|" + host + "|" + 
                                       (epKey.contentType != null ? epKey.contentType : "N/A") + "|" + epKey.endpoint;
                    
                    // ✅ 如果已存在相同的 endpoint，跳过（不重复保存）
                    if (endpointMap.containsKey(endpointKey)) {
                        continue;
                    }
                    
                    // 获取或创建该子域名的数据
                    HostData hostData = hostDataMap.computeIfAbsent(host, k -> {
                        HostData hd = new HostData();
                        hd.host = host;
                        hd.endpoints = new ArrayList<>();
                        // ✅ 获取该子域名的所有参数（从ParameterCollector）
                        hd.parameters = new ArrayList<>(collector.getParametersForHost(host));
                        return hd;
                    });
                    
                    // 添加接口信息
                    EndpointData epData = new EndpointData();
                    epData.host = epKey.host;
                    epData.endpoint = epKey.endpoint;  // ✅ endpoint 已不包含 query（在 ParameterCollector 中已处理）
                    epData.method = epKey.method;
                    epData.contentType = epKey.contentType;
                    
                    // ✅ 保存"干净"的URL（去掉query，避免二次拼接）
                    // 优先使用 epKey 的信息构建 URL，而不是依赖 template.url()，避免 URL 格式问题
                    String cleanUrl;
                    try {
                        String rawUrl = template.url();
                        cleanUrl = stripQuery(rawUrl);
                        // ✅ 验证：如果 stripQuery 返回的 URL 格式有问题（host 为 null），使用 epKey 信息手动构建
                        if (cleanUrl != null && !cleanUrl.isEmpty()) {
                            java.net.URI testUri = new java.net.URI(cleanUrl);
                            if (testUri.getHost() == null || testUri.getHost().isEmpty() || "null".equals(testUri.getHost())) {
                                // URL 格式有问题，手动构建
                                String scheme = rawUrl.startsWith("https://") ? "https" : "http";
                                int port = template.httpService().port();
                                boolean isSecure = template.httpService().secure();
                                String portStr = (port > 0 && !(isSecure && port == 443) && !(!isSecure && port == 80)) ? (":" + port) : "";
                                cleanUrl = scheme + "://" + epKey.host + portStr + epKey.endpoint;
                            }
                        } else {
                            // cleanUrl 为空，手动构建
                            String scheme = template.httpService().secure() ? "https" : "http";
                            int port = template.httpService().port();
                            boolean isSecure = template.httpService().secure();
                            String portStr = (port > 0 && !(isSecure && port == 443) && !(!isSecure && port == 80)) ? (":" + port) : "";
                            cleanUrl = scheme + "://" + epKey.host + portStr + epKey.endpoint;
                        }
                    } catch (Exception e) {
                        // 如果解析失败，手动构建
                        String scheme = template.httpService().secure() ? "https" : "http";
                        int port = template.httpService().port();
                        boolean isSecure = template.httpService().secure();
                        String portStr = (port > 0 && !(isSecure && port == 443) && !(!isSecure && port == 80)) ? (":" + port) : "";
                        cleanUrl = scheme + "://" + epKey.host + portStr + epKey.endpoint;
                    }
                    epData.url = cleanUrl;
                    
                    // ✅ 修复：保存完整的原始请求，包括所有 URL 参数，以便恢复时能正确解析所有参数
                    StringBuilder requestStr = new StringBuilder();
                    String requestPath = template.path();
                    
                    // ✅ 修复：去掉 requestPath 中可能包含的 query 参数，避免重复
                    int queryIndex = requestPath.indexOf('?');
                    if (queryIndex > 0) {
                        requestPath = requestPath.substring(0, queryIndex);
                    }
                    
                    // ✅ 收集所有 URL 参数，构建完整的请求行
                    // 注意：直接使用原始参数值，不进行编码，因为恢复时会正确处理
                    // 注意：如果同一个参数名有多个值，template.parameters() 会返回多个 HttpParameter 对象，这里会全部保存
                    java.util.List<String> urlParams = new java.util.ArrayList<>();
                    for (var param : template.parameters()) {
                        if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                            // ✅ 直接使用原始值，不编码（恢复时会通过 parseQueryToList 正确处理，保留所有参数值）
                            urlParams.add(param.name() + "=" + param.value());
                        }
                    }
                    
                    // 构建请求行：如果有 URL 参数，添加到 path 后面
                    if (!urlParams.isEmpty()) {
                        requestStr.append(template.method()).append(" ").append(requestPath).append("?").append(String.join("&", urlParams));
                    } else {
                        requestStr.append(template.method()).append(" ").append(requestPath);
                    }
                    requestStr.append(" HTTP/1.1\r\n");
                    
                    // 添加所有headers
                    for (var header : template.headers()) {
                        requestStr.append(header.name()).append(": ").append(header.value()).append("\r\n");
                    }
                    requestStr.append("\r\n");
                    
                    // 添加body（如果有）
                    if (template.body() != null && template.body().length() > 0) {
                        requestStr.append(template.bodyToString());
                    }
                    
                    epData.requestTemplate = requestStr.toString();
                    
                    // ✅ 添加到去重 Map 和 hostData
                    endpointMap.put(endpointKey, epData);
                    hostData.endpoints.add(epData);
                }
            }
            
            // ✅ 将按子域名组织的数据添加到主域名数据中
            mainDomainData.hostDataMap = hostDataMap;
            
            model.mainDomains.put(mainDomain, mainDomainData);
        }
        
        return model;
    }
    
    /**
     * ✅ 从端点数据构建 HttpRequest
     * 使用保存的请求字符串和HttpService信息重新构建请求
     */
    private HttpRequest buildRequestFromEndpointData(EndpointData epData, MontoyaApi api) {
        try {
            // ✅ 简化逻辑：直接使用 epData.host 和 epData.endpoint，不依赖 URL 解析
            // 1. 确定 host（优先使用 epData.host）
            String host = epData.host;
            if (host == null || host.isEmpty() || "null".equals(host)) {
                // 如果 epData.host 无效，尝试从 epData.url 解析
                if (epData.url != null && !epData.url.isEmpty()) {
                    try {
                        String cleanUrl = stripQuery(epData.url);
                        java.net.URI uri = new java.net.URI(cleanUrl);
                        host = uri.getHost();
                        if (host == null || host.isEmpty() || "null".equals(host)) {
                            api.logging().raiseErrorEvent("恢复接口失败: 无法确定 host - url=" + epData.url + ", host=" + epData.host);
                            return null;
                        }
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("恢复接口失败: 解析 URL 失败 - url=" + epData.url + ", error=" + e.getMessage());
                        return null;
                    }
                } else {
                    api.logging().raiseErrorEvent("恢复接口失败: host 为空且无 url - endpoint=" + epData.endpoint);
                    return null;
                }
            }
            
            // 2. 确定 path（优先使用 epData.endpoint）
            String cleanPath = "/";
            if (epData.endpoint != null && !epData.endpoint.isEmpty()) {
                int qIdx = epData.endpoint.indexOf('?');
                cleanPath = qIdx > 0 ? epData.endpoint.substring(0, qIdx) : epData.endpoint;
                if (cleanPath.isEmpty()) cleanPath = "/";
            } else if (epData.url != null && !epData.url.isEmpty()) {
                // 如果 epData.endpoint 为空，从 URL 解析 path
                try {
                    String cleanUrl = stripQuery(epData.url);
                    java.net.URI uri = new java.net.URI(cleanUrl);
                    String uriPath = uri.getPath();
                    if (uriPath != null && !uriPath.isEmpty() && !uriPath.contains("/null/")) {
                        cleanPath = uriPath;
                    }
                } catch (Exception ignored) {}
            }
            
            // 3. 确定 scheme 和 port（从 epData.url 解析，如果 URL 无效则使用默认值）
            String scheme = "https";
            int port = 443;
            boolean isSecure = true;
            if (epData.url != null && !epData.url.isEmpty()) {
                try {
                    String cleanUrl = stripQuery(epData.url);
                    java.net.URI uri = new java.net.URI(cleanUrl);
                    scheme = uri.getScheme() == null ? "https" : uri.getScheme();
                    isSecure = "https".equalsIgnoreCase(scheme);
                    port = uri.getPort();
                    if (port == -1) port = isSecure ? 443 : 80;
                } catch (Exception ignored) {
                    // 使用默认值
                }
            }
            
            // 4. ✅ 修复：从 requestTemplate 中解析所有参数（URL 和 Body），而不仅仅是从 query string 中提取
            // 首先尝试从 requestTemplate 解析完整的请求
            HttpRequest restoredRequest = null;
            if (epData.requestTemplate != null && !epData.requestTemplate.isEmpty()) {
                try {
                    // 解析 requestTemplate 字符串
                    String[] lines = epData.requestTemplate.split("\r\n");
                    if (lines.length > 0) {
                        // 解析请求行：METHOD PATH HTTP/1.1
                        String requestLine = lines[0];
                        String[] requestParts = requestLine.split("\\s+");
                        if (requestParts.length >= 2) {
                            String templateMethod = requestParts[0];
                            String templatePath = requestParts[1];
                            
                            // 从 templatePath 提取 query 参数
                            String templateQuery = null;
                            int queryIdx = templatePath.indexOf('?');
                            if (queryIdx > 0) {
                                templateQuery = templatePath.substring(queryIdx + 1);
                                templatePath = templatePath.substring(0, queryIdx);
                            }
                            
                            // 构建基础请求
                            StringBuilder requestBuilder = new StringBuilder();
                            requestBuilder.append(templateMethod).append(" ").append(templatePath).append(" HTTP/1.1\r\n");
                            
                            // 添加 headers（跳过第一行请求行）
                            for (int i = 1; i < lines.length; i++) {
                                String line = lines[i];
                                if (line.isEmpty()) {
                                    // 空行表示 headers 结束
                                    break;
                                }
                                requestBuilder.append(line).append("\r\n");
                            }
                            requestBuilder.append("\r\n");
                            
                            // 添加 body（如果有）
                            boolean foundEmptyLine = false;
                            StringBuilder bodyBuilder = new StringBuilder();
                            for (int i = 1; i < lines.length; i++) {
                                if (lines[i].isEmpty()) {
                                    foundEmptyLine = true;
                                    continue;
                                }
                                if (foundEmptyLine) {
                                    bodyBuilder.append(lines[i]);
                                    if (i < lines.length - 1) {
                                        bodyBuilder.append("\r\n");
                                    }
                                }
                            }
                            
                            burp.api.montoya.http.HttpService httpService = burp.api.montoya.http.HttpService.httpService(host, port, isSecure);
                            String requestString = requestBuilder.toString();
                            if (bodyBuilder.length() > 0) {
                                restoredRequest = HttpRequest.httpRequest(httpService, requestString + bodyBuilder.toString());
                            } else {
                                restoredRequest = HttpRequest.httpRequest(httpService, requestString);
                            }
                            
                            // ✅ 修复：添加 URL 参数（从 templatePath 的 query 部分），保留所有参数值（包括同名参数）
                            if (templateQuery != null && !templateQuery.isEmpty()) {
                                java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> urlParams = parseQueryToList(templateQuery);
                                for (java.util.AbstractMap.SimpleEntry<String, String> e : urlParams) {
                                    restoredRequest = restoredRequest.withAddedParameters(
                                        burp.api.montoya.http.message.params.HttpParameter.urlParameter(e.getKey(), e.getValue())
                                    );
                                }
                            }
                            
                            // ✅ 修复：添加 Body 参数（从 body 中解析，如果是表单格式），保留所有参数值（包括同名参数）
                            if (bodyBuilder.length() > 0) {
                                String bodyStr = bodyBuilder.toString();
                                // 检查 Content-Type
                                String contentType = null;
                                for (var header : restoredRequest.headers()) {
                                    if ("Content-Type".equalsIgnoreCase(header.name())) {
                                        contentType = header.value();
                                        break;
                                    }
                                }
                                
                                // 如果是表单格式，解析 body 参数
                                if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                                    java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> bodyParams = parseQueryToList(bodyStr);
                                    for (java.util.AbstractMap.SimpleEntry<String, String> e : bodyParams) {
                                        restoredRequest = restoredRequest.withAddedParameters(
                                            burp.api.montoya.http.message.params.HttpParameter.bodyParameter(e.getKey(), e.getValue())
                                        );
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    api.logging().raiseDebugEvent("从 requestTemplate 解析请求失败，使用简化方式: " + e.getMessage());
                }
            }
            
            // 如果从 requestTemplate 解析失败，使用简化方式
            if (restoredRequest == null) {
                // 构建 HttpService
                burp.api.montoya.http.HttpService httpService = burp.api.montoya.http.HttpService.httpService(host, port, isSecure);
                
                // 构建一个"纯净"的请求行与基础头（不拼接 ?query）
                StringBuilder sb = new StringBuilder();
                String method = epData.method != null ? epData.method : "GET";
                sb.append(method).append(" ").append(cleanPath).append(" HTTP/1.1\r\n");
                sb.append("Host: ").append(host);
                if (!((isSecure && port == 443) || (!isSecure && port == 80))) {
                    sb.append(":").append(port);
                }
                sb.append("\r\n");
                sb.append("User-Agent: XProbe-Restore\r\n");
                sb.append("Accept: */*\r\n\r\n");
                restoredRequest = HttpRequest.httpRequest(httpService, sb.toString());
                
                // 从 epData.url 或 epData.endpoint 提取 query 参数（作为后备）
                String rawQuery = null;
                if (epData.url != null && !epData.url.isEmpty()) {
                    int queryStart = epData.url.indexOf('?');
                    if (queryStart > 0) {
                        rawQuery = epData.url.substring(queryStart + 1);
                    }
                } else if (epData.endpoint != null && !epData.endpoint.isEmpty()) {
                    int queryStart = epData.endpoint.indexOf('?');
                    if (queryStart > 0) {
                        rawQuery = epData.endpoint.substring(queryStart + 1);
                    }
                }
                
                // ✅ 修复：从 epData.url 或 epData.endpoint 提取 query 参数，保留所有参数值（包括同名参数）
                if (rawQuery != null && !rawQuery.isEmpty()) {
                    java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> q = parseQueryToList(rawQuery);
                    for (java.util.AbstractMap.SimpleEntry<String, String> e : q) {
                        restoredRequest = restoredRequest.withAddedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.urlParameter(e.getKey(), e.getValue())
                        );
                    }
                }
            }
            
            return restoredRequest;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("从端点数据构建请求失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Map<String,String> parseQueryToMap(String query) {
        Map<String,String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        // 将后续出现的 '?' 也作为分隔符处理，避免把 '?k=v' 落入 value 造成再次注入
        String normalized = query.replace('?', '&');
        for (String part : normalized.split("&")) {
            if (part == null || part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            String k = urlDecode(kv[0]);
            String v = kv.length > 1 ? urlDecode(kv[1]) : "";
            if (k != null && !k.isEmpty()) map.put(k, v);
        }
        return map;
    }
    
    /**
     * ✅ 修复：解析 query string，保留所有参数值（包括同名参数）
     * 返回参数对列表，每个参数都会保留，即使参数名相同
     */
    private java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> parseQueryToList(String query) {
        java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> list = new java.util.ArrayList<>();
        if (query == null || query.isEmpty()) return list;
        // 将后续出现的 '?' 也作为分隔符处理，避免把 '?k=v' 落入 value 造成再次注入
        String normalized = query.replace('?', '&');
        for (String part : normalized.split("&")) {
            if (part == null || part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            String k = urlDecode(kv[0]);
            String v = kv.length > 1 ? urlDecode(kv[1]) : "";
            if (k != null && !k.isEmpty()) {
                list.add(new java.util.AbstractMap.SimpleEntry<>(k, v));
            }
        }
        return list;
    }
    private String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return s; }
    }

    // 去掉URL中的查询串，保存/恢复时统一用干净的path
    private String stripQuery(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            int port = uri.getPort();
            boolean https = "https".equalsIgnoreCase(scheme);
            if (path == null || path.isEmpty()) path = "/";
            if (host == null) return url; // 兜底
            String portStr = (port > 0 && !(https && port == 443) && !(!https && port == 80)) ? (":" + port) : "";
            return scheme + "://" + host + portStr + path;
        } catch (Exception e) {
            int idx = url.indexOf('?');
            return idx > 0 ? url.substring(0, idx) : url;
        }
    }
    
    // ========== 数据模型类 ==========
    
    /**
     * 参数数据模型（根对象）
     */
    public static class ParameterDataModel {
        public Map<String, MainDomainData> mainDomains = new HashMap<>();
    }
    
    /**
     * ✅ 主域名数据（优化版：按子域名组织）
     */
    public static class MainDomainData {
        public String mainDomain;
        public List<String> hosts = new ArrayList<>();
        public Map<String, HostData> hostDataMap = new HashMap<>(); // ✅ 按子域名组织的数据
        public List<EndpointData> endpoints = new ArrayList<>(); // ✅ 保留用于向后兼容
        public List<String> keywords = new ArrayList<>();
        public long lastUpdateTime;
    }
    
    /**
     * ✅ 子域名数据（包含该子域名的接口和参数）
     */
    public static class HostData {
        public String host;
        public List<EndpointData> endpoints = new ArrayList<>(); // ✅ 该子域名的所有接口
        public List<String> parameters = new ArrayList<>(); // ✅ 该子域名的所有参数
    }
    
    /**
     * ✅ 接口数据（优化版）
     * 包含恢复所需的所有信息
     */
    public static class EndpointData {
        public String host;
        public String endpoint;
        public String method;
        public String contentType;
        public String url; // ✅ 完整的URL（用于恢复时构建HttpService）
        public List<String> parameters = new ArrayList<>(); // ✅ 该接口的参数列表
        public String requestTemplate; // ✅ 完整的HTTP请求字符串（包含header和body）
    }
}


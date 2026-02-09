package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import com.xprobe.scanner.utils.BoundedCache;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 参数收集器 - 负责从 HTTP 流量中收集参数
 * 
 * 功能：
 * 1. 从被动流量中收集参数
 * 2. 按主域名分组管理参数
 * 3. 支持增量收集和去重
 * 4. 管理接口和参数的关联关系
 * 5. 支持两种收集模式：仅参数名 / 参数名+关键词
 */
public class ParameterCollector {
    private final MontoyaApi api;
    
    // 收集模式
    public enum CollectionMode {
        PARAMETERS_ONLY,        // 仅收集参数名
        PARAMETERS_AND_KEYWORDS // 收集参数名 + 参数值作为关键词
    }
    
    private volatile CollectionMode collectionMode = CollectionMode.PARAMETERS_ONLY;
    
    // 按主域名分组的数据存储
    private final Map<String, DomainData> domainDataMap = new ConcurrentHashMap<>();
    
    // 请求去重（避免重复处理同一请求）
    // Key: method|url|contentType
    // ✅ 修复：使用BoundedCache防止内存泄漏（限制10万条记录）
    private final BoundedCache<String, Boolean> processedRequests = new BoundedCache<>(100_000);
    
    // 关键词存储（按主域名）
    private final Map<String, Set<String>> domainKeywords = new ConcurrentHashMap<>();
    
    public ParameterCollector(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 设置收集模式
     */
    public void setCollectionMode(CollectionMode mode) {
        this.collectionMode = mode;
        api.logging().raiseInfoEvent("参数收集模式: " + 
            (mode == CollectionMode.PARAMETERS_ONLY ? "仅参数名" : "参数名+关键词"));
    }
    
    /**
     * 获取当前收集模式
     */
    public CollectionMode getCollectionMode() {
        return collectionMode;
    }
    
    /**
     * 从 HTTP 请求中收集参数
     * 
     * @param request HTTP 请求
     * @return 是否收集到新参数
     */
    public boolean collectFromRequest(HttpRequest request) {
        try {
            String url = request.url();
            
            // ✅ 过滤静态资源（排除css、png等，保留js）
            if (!com.xprobe.scanner.utils.StaticResourceFilter.shouldCollectParameters(url)) {
                api.logging().raiseDebugEvent("跳过静态资源: " + url);
                return false;
            }
            
            String method = request.method();
            String contentType = getContentType(request);
            
            // 先不做去重，提取参数名用于构建更精细的去重键
            // 提取参数（仅名称，用于签名）将在下方完成后再计算去重键
            
            URI uri = new URI(url);
            String host = uri.getHost();
            // ✅ 修复：如果 host 为 null，尝试从 request 的 httpService 获取
            if (host == null || host.isEmpty() || "null".equals(host)) {
                host = request.httpService().host();
            }
            // ✅ 修复：如果 host 仍然为 null，直接返回，避免后续使用 null 作为 key
            if (host == null || host.isEmpty() || "null".equals(host)) {
                api.logging().raiseDebugEvent("跳过请求：无法确定 host - url=" + url);
                return false;
            }
            String mainDomain = extractMainDomain(host);
            // ✅ 修复：如果 mainDomain 为 null（不应该发生，但保险起见），直接返回
            if (mainDomain == null || mainDomain.isEmpty()) {
                api.logging().raiseDebugEvent("跳过请求：无法确定 mainDomain - host=" + host);
                return false;
            }
            // ✅ 修复：直接使用 request.path() 而不是从 URL 解析，避免 URL 格式问题导致路径错误
            String endpoint = request.path();
            if (endpoint == null || endpoint.isEmpty()) {
                // 如果 request.path() 返回空，再从 URL 解析
                endpoint = uri.getPath();
            }
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = "/";
            }
            
            // ✅ 修复：去掉 endpoint 中的 query 参数，确保同一个接口不会因为参数不同而被当作不同接口
            int queryIndex = endpoint.indexOf('?');
            if (queryIndex > 0) {
                endpoint = endpoint.substring(0, queryIndex);
            }
            if (endpoint.isEmpty()) {
                endpoint = "/";
            }
            
            // 获取或创建域数据
            DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
            
            // ✅ 修复：规范化endpoint，将路径参数化的接口识别为同一个接口
            // 例如：/api/business/folder/tree/1001090991 和 /api/business/folder/tree/587091401 识别为同一个接口
            // 注意：需要在获取domainData之后调用，因为normalizeEndpoint需要访问domainDataMap
            String normalizedEndpoint = normalizeEndpoint(endpoint, mainDomain);
            
            // ✅ 如果规范化后的endpoint与原始不同，说明找到了相似的接口
            // 此时需要更新endpoint，以便后续使用规范化后的endpoint
            if (!normalizedEndpoint.equals(endpoint)) {
                endpoint = normalizedEndpoint;
            }
            
            // 先提取参数（用于构建更精细的去重键）
            Set<String> parameters = extractParameters(request);
            
            // 构建去重键（包含参数名指纹，避免不同参数集被误判为重复）
            String paramSig = buildParamSignature(parameters);
            String dedupeKey = "REQUEST|" + method + "|" + host + "|" + normalizeContentType(contentType) + "|" + endpoint + "|" + paramSig;
            if (processedRequests.containsKey(dedupeKey)) {
                return false;
            }
            
            // ✅ 添加接口信息（即使没有参数也要添加接口）
            domainData.addEndpoint(host, endpoint, method, contentType, request);
            
            
            // 检查是否有新参数
            boolean hasNewParameters = false;
            for (String param : parameters) {
                if (!domainData.hasParameter(param)) {
                    hasNewParameters = true;
                    break;
                }
            }
            
            // 添加参数（如果有参数的话）
            for (String param : parameters) {
                domainData.addParameter(host, endpoint, param);
            }
            
            // 标记请求已处理（使用包含参数名指纹的去重键）
            processedRequests.put(dedupeKey, Boolean.TRUE);
            
            // 如果启用了关键词收集模式，提取参数值作为关键词
            if (collectionMode == CollectionMode.PARAMETERS_AND_KEYWORDS) {
                Set<String> keywords = extractKeywords(request);
                if (!keywords.isEmpty()) {
                    domainKeywords.computeIfAbsent(mainDomain, k -> ConcurrentHashMap.newKeySet())
                                 .addAll(keywords);
                    
                    api.logging().raiseDebugEvent(String.format(
                        "收集关键词: 主域名=%s, 关键词数=%d",
                        mainDomain, keywords.size()
                    ));
                }
            }
            
            // 标记请求已处理
            processedRequests.put(dedupeKey, Boolean.TRUE);
            
            if (hasNewParameters) {
                api.logging().raiseDebugEvent(String.format(
                    "收集到新参数: 主域名=%s, host=%s, endpoint=%s, 参数数=%d",
                    mainDomain, host, endpoint, parameters.size()
                ));
            }
            
            return hasNewParameters;
            
        } catch (URISyntaxException e) {
            api.logging().raiseErrorEvent("解析 URL 失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从 HTTP 响应中收集参数
     * ✅ 修复：恢复响应包参数收集功能
     * 
     * @param request 原始请求
     * @param response HTTP 响应
     * @return 是否收集到新参数
     */
    public boolean collectFromResponse(HttpRequest request, burp.api.montoya.http.message.responses.HttpResponse response) {
        try {
            String url = request.url();
            
            // ✅ 过滤静态资源（排除css、png等，保留js）
            if (!com.xprobe.scanner.utils.StaticResourceFilter.shouldCollectParameters(url)) {
                return false;
            }
            
            String method = request.method();
            String contentType = getContentType(request);
            
            URI uri = new URI(url);
            String host = uri.getHost();
            // ✅ 修复：如果 host 为 null，尝试从 request 的 httpService 获取
            if (host == null || host.isEmpty() || "null".equals(host)) {
                host = request.httpService().host();
            }
            // ✅ 修复：如果 host 仍然为 null，直接返回，避免后续使用 null 作为 key
            if (host == null || host.isEmpty() || "null".equals(host)) {
                api.logging().raiseDebugEvent("跳过响应：无法确定 host - url=" + url);
                return false;
            }
            String mainDomain = extractMainDomain(host);
            // ✅ 修复：如果 mainDomain 为 null，直接返回
            if (mainDomain == null || mainDomain.isEmpty()) {
                api.logging().raiseDebugEvent("跳过响应：无法确定 mainDomain - host=" + host);
                return false;
            }
            
            // 从响应中提取参数（从JSON、HTML等）
            Set<String> parameters = extractParametersFromResponse(response);
            
            if (parameters.isEmpty()) {
                return false;
            }
            
            // ✅ 修复：直接使用 request.path() 而不是从 URL 解析，避免 URL 格式问题导致路径错误
            String endpoint = request.path();
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = uri.getPath();
            }
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = "/";
            }
            
            // ✅ 修复：去掉 endpoint 中的 query 参数，确保同一个接口不会因为参数不同而被当作不同接口
            int queryIndex = endpoint.indexOf('?');
            if (queryIndex > 0) {
                endpoint = endpoint.substring(0, queryIndex);
            }
            if (endpoint.isEmpty()) {
                endpoint = "/";
            }
            
            // 获取或创建域数据（需要在normalizeEndpoint之前获取）
            DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
            
            // ✅ 修复：规范化endpoint，将路径参数化的接口识别为同一个接口
            // 例如：/api/business/folder/tree/1001090991 和 /api/business/folder/tree/587091401 识别为同一个接口
            String normalizedEndpoint = normalizeEndpoint(endpoint, mainDomain);
            if (!normalizedEndpoint.equals(endpoint)) {
                endpoint = normalizedEndpoint;
            }
            
            // 构建响应去重键（包含参数名指纹）
            String respParamSig = buildParamSignature(parameters);
            String dedupeKey = "RESPONSE|" + method + "|" + host + "|" + normalizeContentType(contentType) + "|" + endpoint + "|" + respParamSig;
            if (processedRequests.containsKey(dedupeKey)) {
                return false;
            }
            
            // 检查是否有新参数
            boolean hasNewParameters = false;
            for (String param : parameters) {
                if (!domainData.hasParameter(param)) {
                    hasNewParameters = true;
                }
                domainData.addParameter(host, endpoint, param);
            }
            
            // 标记响应已处理
            processedRequests.put(dedupeKey, Boolean.TRUE);
            
            if (hasNewParameters) {
                api.logging().raiseDebugEvent(String.format(
                    "从响应收集到新参数: 主域名=%s, 参数数=%d",
                    mainDomain, parameters.size()
                ));
            }
            
            return hasNewParameters;
            
        } catch (URISyntaxException e) {
            api.logging().raiseErrorEvent("解析 URL 失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取主域名的所有参数（合并所有子域名）
     */
    public Set<String> getParametersForMainDomain(String mainDomain) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getAllParameters() : new HashSet<>();
    }
    
    /**
     * 获取特定 host 的参数
     */
    public Set<String> getParametersForHost(String host) {
        String mainDomain = extractMainDomain(host);
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getHostParameters(host) : new HashSet<>();
    }
    
    /**
     * ✅ 直接添加接口（用于数据恢复，跳过去重检查）
     * 
     * @param host 子域名
     * @param endpoint 接口路径
     * @param method HTTP方法
     * @param contentType Content-Type
     * @param request HTTP请求
     * @param mainDomain 主域名
     */
    public void addEndpointDirectly(String host, String endpoint, String method, 
                                   String contentType, HttpRequest request, String mainDomain) {
        DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
        Set<ParameterCollector.EndpointKey> beforeKeys = domainData.getAllEndpointKeys();
        int beforeCount = beforeKeys != null ? beforeKeys.size() : 0;
        
        domainData.addEndpoint(host, endpoint, method, contentType, request);
        
        Set<ParameterCollector.EndpointKey> afterKeys = domainData.getAllEndpointKeys();
        int afterCount = afterKeys != null ? afterKeys.size() : 0;
        
        if (afterCount > beforeCount) {
            api.logging().raiseDebugEvent(String.format(
                "✅ 恢复接口成功: %s %s %s %s (主域 %s, 接口数: %d -> %d)",
                method, host, contentType, endpoint, mainDomain, beforeCount, afterCount
            ));
        } else {
            api.logging().raiseDebugEvent(String.format(
                "恢复接口（已存在）: %s %s %s %s (主域 %s, 接口数: %d)",
                method, host, contentType, endpoint, mainDomain, beforeCount
            ));
        }
    }
    
    /**
     * ✅ 直接添加参数（用于数据恢复，不需要endpoint）
     * 
     * @param host 子域名
     * @param parameter 参数名
     * @param mainDomain 主域名
     */
    public void addParameterDirectly(String host, String parameter, String mainDomain) {
        DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
        int beforeSize = domainData.getAllParameters().size();
        domainData.addParameterDirectly(host, parameter);
        int afterSize = domainData.getAllParameters().size();
        // ✅ 记录所有参数添加情况（包括已存在的参数）
        if (afterSize > beforeSize) {
            api.logging().raiseDebugEvent(String.format(
                "恢复参数: 主域 %s, 子域 %s, 参数 %s (主域参数数: %d -> %d) [新增]",
                mainDomain, host, parameter, beforeSize, afterSize
            ));
        } else {
            api.logging().raiseDebugEvent(String.format(
                "恢复参数: 主域 %s, 子域 %s, 参数 %s (主域参数数: %d) [已存在]",
                mainDomain, host, parameter, beforeSize
            ));
        }
    }
    
    /**
     * 获取主域名的所有 host
     */
    public Set<String> getHostsForMainDomain(String mainDomain) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getAllHosts() : new HashSet<>();
    }
    
    /**
     * 获取主域名的所有接口
     */
    public Set<String> getEndpointsForMainDomain(String mainDomain) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getAllEndpoints() : new HashSet<>();
    }
    
    /**
     * 获取主域名的所有接口（包含method和contentType信息）
     */
    public Set<EndpointKey> getEndpointKeysForMainDomain(String mainDomain) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getAllEndpointKeys() : new HashSet<>();
    }
    
    /**
     * 获取接口的请求模板
     */
    public HttpRequest getEndpointTemplate(String mainDomain, String endpoint) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getEndpointTemplate(endpoint) : null;
    }
    
    /**
     * 获取接口的请求模板（通过 EndpointKey）
     */
    public HttpRequest getEndpointTemplate(String mainDomain, EndpointKey epKey) {
        DomainData domainData = domainDataMap.get(mainDomain);
        return domainData != null ? domainData.getEndpointTemplate(epKey) : null;
    }
    
    /**
     * 获取主域名的关键词
     */
    public Set<String> getKeywordsForMainDomain(String mainDomain) {
        Set<String> keywords = domainKeywords.get(mainDomain);
        return keywords != null ? new HashSet<>(keywords) : new HashSet<>();
    }
    
    /**
     * 获取所有主域名
     */
    public Set<String> getAllMainDomains() {
        return new HashSet<>(domainDataMap.keySet());
    }
    
    /**
     * 获取统计信息
     */
    public CollectorStatistics getStatistics() {
        int totalDomains = domainDataMap.size();
        int totalHosts = 0;
        int totalEndpoints = 0;
        int totalParameters = 0;
        int totalKeywords = 0;
        
        for (DomainData domainData : domainDataMap.values()) {
            totalHosts += domainData.getAllHosts().size();
            totalEndpoints += domainData.getAllEndpoints().size();
            totalParameters += domainData.getAllParameters().size();
        }
        
        for (Set<String> keywords : domainKeywords.values()) {
            totalKeywords += keywords.size();
        }
        
        return new CollectorStatistics(totalDomains, totalHosts, totalEndpoints, 
                                      totalParameters, totalKeywords, collectionMode);
    }
    
    /**
     * ✅ 获取域名的最后更新时间
     */
    public long getLastUpdateTimeForDomain(String mainDomain) {
        DomainData domainData = domainDataMap.get(mainDomain);
        if (domainData == null) {
            return 0;
        }
        return domainData.getLastUpdateTime();
    }
    
    /**
     * 清空所有数据
     */
    public void clear() {
        domainDataMap.clear();
        processedRequests.clear();
        domainKeywords.clear();
        api.logging().raiseInfoEvent("参数收集器已清空");
    }
    
    /**
     * ✅ 清空指定主域名的数据
     */
    public boolean clearMainDomain(String mainDomain) {
        if (domainDataMap.remove(mainDomain) != null) {
            domainKeywords.remove(mainDomain);
            api.logging().raiseInfoEvent("已清空主域名数据: " + mainDomain);
            return true;
        }
        return false;
    }
    
    /**
     * ✅ 清空指定子域名的数据
     */
    public boolean clearHost(String mainDomain, String host) {
        DomainData domainData = domainDataMap.get(mainDomain);
        if (domainData == null) {
            return false;
        }
        
        // 从hosts列表中移除
        boolean hostRemoved = domainData.hosts.remove(host);
        
        // 从hostParameters中移除
        domainData.hostParameters.remove(host);
        
        // 从endpointMap中移除该host的所有接口
        domainData.endpointMap.entrySet().removeIf(entry -> {
            EndpointInfo epInfo = entry.getValue();
            if (epInfo.host.equals(host)) {
                // 从allParameters中移除该接口的参数
                epInfo.parameters.forEach(domainData.allParameters::remove);
                return true;
            }
            return false;
        });
        
        // 如果主域名下没有host了，删除整个主域名
        if (domainData.hosts.isEmpty()) {
            domainDataMap.remove(mainDomain);
            domainKeywords.remove(mainDomain);
        }
        
        if (hostRemoved) {
            api.logging().raiseInfoEvent(String.format("已清空子域名数据: %s (主域名: %s)", host, mainDomain));
        }
        
        return hostRemoved;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 构建参数名集合的稳定签名（用于请求/响应去重键）
     * 规则：按字典序排序参数名，使用'\n'连接；为空返回"NOPARAMS"；
     * 再计算 SHA-256 的十六进制，避免过长键。
     */
    private String buildParamSignature(Set<String> parameters) {
        try {
            if (parameters == null || parameters.isEmpty()) {
                return "NOPARAMS";
            }
            java.util.List<String> list = new java.util.ArrayList<>(parameters);
            java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
            String joined = String.join("\n", list);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 降级：回退到长度和哈希码组合
            return "FALLBACK-" + (parameters != null ? parameters.size() : 0) + "-" + (parameters != null ? parameters.hashCode() : 0);
        }
    }
    
    // ========== 参数和关键词提取逻辑（参考 GAP.py）==========
    
    // 参数名正则：只允许 A-Z a-z 0-9 - _ . ~ [ ]
    // 参考 GAP.py: REGEX_PARAM = re.compile(r"^[A-Za-z0-9_.~\-\[\]]+$")
    private static final java.util.regex.Pattern PATTERN_VALID_PARAM = 
        java.util.regex.Pattern.compile("^[A-Za-z0-9_.~\\-\\[\\]]+$");
    
    // 关键词正则：至少3个字符的单词，不在路径分隔符后
    // 参考 GAP.py: REGEX_WORDS = re.compile(r"(?<![\/])\b\w{3,}\b(?![\/])")
    private static final java.util.regex.Pattern PATTERN_WORDS = 
        java.util.regex.Pattern.compile("(?<![/])\\b\\w{3,}\\b(?![/])");
    
    // ✅ 性能优化：响应参数提取的正则（避免每次重新编译）
    // JSON键名提取：匹配 "key": 格式
    private static final Pattern PATTERN_JSON_KEY = 
        Pattern.compile("\"([a-zA-Z_][a-zA-Z0-9_]*)\"\\s*:");
    
    // HTML name属性提取：匹配 name="xxx" 或 name='xxx'
    private static final Pattern PATTERN_HTML_NAME = 
        Pattern.compile("name\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    
    // 停用词列表（参考 GAP.py 的常见无意义词）
    private static final Set<String> STOP_WORDS = Set.of(
        "true", "false", "null", "undefined", "nan", 
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", 
        "was", "one", "our", "out", "day", "get", "has", "him", "his", "how",
        "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did",
        "its", "let", "put", "say", "she", "too", "use", "yes", "no"
    );
    
    /**
     * 提取参数名（参考 GAP.py 的 addParameter 逻辑）
     * ✅ P0修复：支持从JSON body中自动提取嵌套参数（如 user.name, items[0].id）
     */
    private Set<String> extractParameters(HttpRequest request) {
        Set<String> parameters = new HashSet<>();
        
        // 1. 提取Burp API自动识别的参数（URL参数、表单参数、顶层JSON键）
        for (ParsedHttpParameter param : request.parameters()) {
            String paramName = cleanParameterName(param.name());
            
            // 验证参数名格式（参考 GAP.py）
            if (paramName != null && !paramName.isEmpty() && 
                PATTERN_VALID_PARAM.matcher(paramName).matches()) {
                parameters.add(paramName);
            }
        }
        
        // 2. ✅ P0修复：对于JSON body，额外提取所有嵌套参数
        String contentType = getContentType(request);
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try {
                String bodyStr = request.bodyToString();
                if (bodyStr != null && !bodyStr.trim().isEmpty()) {
                    // 提取JSON中的所有嵌套参数路径
                    Set<String> jsonParams = extractNestedJsonParameters(bodyStr);
                    parameters.addAll(jsonParams);
                }
            } catch (Exception e) {
                api.logging().raiseDebugEvent("提取JSON嵌套参数失败: " + e.getMessage());
            }
        }
        
        return parameters;
    }
    
    /**
     * ✅ P0修复：从JSON body中提取所有嵌套参数路径
     * 例如：{"user": {"name": "test"}} → ["user", "user.name"]
     *      {"items": [{"id": 1}]} → ["items", "items[0]", "items[0].id"]
     */
    private Set<String> extractNestedJsonParameters(String jsonBody) {
        Set<String> parameters = new HashSet<>();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = jsonMapper.readTree(jsonBody);
            
            if (rootNode != null) {
                // 递归提取所有参数路径
                extractJsonPaths(rootNode, "", parameters);
            }
        } catch (Exception e) {
            // JSON解析失败，使用正则降级处理
            api.logging().raiseDebugEvent("JSON解析失败，使用正则降级提取: " + e.getMessage());
            // 降级：使用正则提取顶层键
            java.util.regex.Matcher matcher = PATTERN_JSON_KEY.matcher(jsonBody);
            while (matcher.find()) {
                String paramName = matcher.group(1);
                if (PATTERN_VALID_PARAM.matcher(paramName).matches()) {
                    parameters.add(paramName);
                }
            }
        }
        
        return parameters;
    }
    
    /**
     * ✅ 递归提取JSON中的所有路径（支持嵌套对象和数组）
     * @param node 当前JSON节点
     * @param currentPath 当前路径（如 "user" 或 "user.name"）
     * @param parameters 参数集合（输出）
     */
    private void extractJsonPaths(com.fasterxml.jackson.databind.JsonNode node, 
                                  String currentPath, 
                                  Set<String> parameters) {
        if (node == null) {
            return;
        }
        
        if (node.isObject()) {
            // 对象：遍历所有键
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                com.fasterxml.jackson.databind.JsonNode value = entry.getValue();
                
                // 构建新路径
                String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                
                // 验证参数名格式
                if (PATTERN_VALID_PARAM.matcher(key).matches()) {
                    // 添加当前路径
                    parameters.add(newPath);
                    
                    // 递归处理嵌套对象和数组
                    if (value.isObject() || value.isArray()) {
                        extractJsonPaths(value, newPath, parameters);
                    }
                }
            });
        } else if (node.isArray()) {
            // 数组：遍历所有元素
            for (int i = 0; i < node.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode element = node.get(i);
                String arrayPath = currentPath + "[" + i + "]";
                
                // 添加数组索引路径（如 items[0]）
                parameters.add(arrayPath);
                
                // 递归处理数组元素
                if (element.isObject() || element.isArray()) {
                    extractJsonPaths(element, arrayPath, parameters);
                }
            }
        }
    }
    
    /**
     * 从响应中提取参数（从JSON、HTML等）
     * ✅ 修复：恢复响应包参数提取功能
     */
    private Set<String> extractParametersFromResponse(burp.api.montoya.http.message.responses.HttpResponse response) {
        Set<String> parameters = new HashSet<>();
        
        try {
            // ✅ 修复：使用UTF-8编码获取响应体，避免中文乱码
            String body;
            try {
                byte[] bodyBytes = response.body().getBytes();
                body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 降级处理：使用默认方法
                body = response.bodyToString();
            }
            
            if (body == null || body.isEmpty()) {
                return parameters;
            }
            
            // 限制响应大小，避免处理超大响应（1MB）
            if (body.length() > 1024 * 1024) {
                api.logging().raiseDebugEvent("响应过大，跳过参数提取: " + body.length() + " bytes");
                return parameters;
            }
            
            // 从JSON响应中提取参数名
            if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                try {
                    // ✅ 性能优化：使用静态编译的正则
                    java.util.regex.Matcher matcher = PATTERN_JSON_KEY.matcher(body);
                    while (matcher.find()) {
                        String paramName = matcher.group(1);
                        if (PATTERN_VALID_PARAM.matcher(paramName).matches()) {
                            parameters.add(paramName);
                        }
                    }
                } catch (Exception e) {
                    api.logging().raiseDebugEvent("解析JSON响应失败: " + e.getMessage());
                }
            }
            
            // 从HTML响应中提取input、select等表单元素的name属性
            if (body.contains("<") && body.contains(">")) {
                try {
                    // ✅ 性能优化：使用静态编译的正则
                    java.util.regex.Matcher matcher = PATTERN_HTML_NAME.matcher(body);
                    while (matcher.find()) {
                        String paramName = cleanParameterName(matcher.group(1));
                        if (paramName != null && PATTERN_VALID_PARAM.matcher(paramName).matches()) {
                            parameters.add(paramName);
                        }
                    }
                } catch (Exception e) {
                    api.logging().raiseDebugEvent("解析HTML响应失败: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("从响应提取参数失败: " + e.getMessage());
        }
        
        return parameters;
    }
    
    /**
     * 清理参数名（参考 GAP.py 的参数清理逻辑）
     */
    private String cleanParameterName(String param) {
        if (param == null || param.isEmpty()) {
            return null;
        }
        
        // 移除URL编码的方括号
        param = param.replace("%5b", "").replace("%5B", "")
                    .replace("%5d", "").replace("%5D", "");
        
        // 移除反斜杠、斜杠、引号等（参考 GAP.py）
        param = param.replace("\\", "").replace("/", "")
                    .replace("quot;", "").replace("apos;", "")
                    .replace("amp;", "").replace("\"", "")
                    .replace("'", "");
        
        // 如果参数包含 ? 则取 ? 后面的部分，除非 ? 在末尾
        if (param.contains("?")) {
            String[] parts = param.split("\\?");
            if (parts.length > 1) {
                param = parts[1];
            } else {
                param = parts[0];
            }
        }
        
        return param.trim();
    }
    
    /**
     * 提取参数值作为关键词（参考 GAP.py 的 addWord 和 getResponseWords 逻辑）
     */
    private Set<String> extractKeywords(HttpRequest request) {
        Set<String> keywords = new HashSet<>();
        
        // 从参数值中提取单词
        for (ParsedHttpParameter param : request.parameters()) {
            String value = param.value();
            if (value != null && !value.isEmpty()) {
                // 使用正则提取单词（参考 GAP.py）
                java.util.regex.Matcher matcher = PATTERN_WORDS.matcher(value);
                while (matcher.find()) {
                    String word = matcher.group();
                    word = sanitizeWord(word);
                    
                    // 验证并添加关键词
                    if (isValidKeyword(word)) {
                        keywords.add(word);
                    }
                }
            }
        }
        
        // 也可以从请求体中提取（如果是文本内容）
        if (request.body() != null) {
            String body = request.bodyToString();
            if (body != null && body.length() < 10000) { // 限制大小避免性能问题
                java.util.regex.Matcher matcher = PATTERN_WORDS.matcher(body);
                while (matcher.find()) {
                    String word = matcher.group();
                    word = sanitizeWord(word);
                    
                    if (isValidKeyword(word)) {
                        keywords.add(word);
                    }
                }
            }
        }
        
        return keywords;
    }
    
    /**
     * 清理单词（参考 GAP.py 的 sanitizeWord）
     */
    private String sanitizeWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        
        // 移除引号、尖括号、圆括号、空格等（参考 GAP.py 的 REGEX_WORDSUB）
        word = word.replaceAll("[\"%22<>%3c%3e()%28%29\\s%20'&#;]", "");
        
        // 移除撇号
        word = word.replace("'", "");
        
        return word.trim();
    }
    
    /**
     * 验证关键词是否有效（参考 GAP.py 的 addWord 过滤逻辑）
     */
    private boolean isValidKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        
        int wordLen = keyword.length();
        
        // 最小长度限制（参考 GAP.py 默认为 3）
        if (wordLen < 3) {
            return false;
        }
        
        // 最大长度限制（参考 GAP.py，避免过长的值如base64）
        if (wordLen > 50) {
            return false;
        }
        
        // 跳过纯数字（参考 GAP.py 的 cbWordDigits 选项）
        if (keyword.matches("^\\d+$")) {
            return false;
        }
        
        // 跳过停用词（参考 GAP.py 的 lstStopWords）
        if (STOP_WORDS.contains(keyword.toLowerCase())) {
            return false;
        }
        
        // 跳过全大写且长度小于等于3的（通常是缩写如 API, URL）
        if (wordLen <= 3 && keyword.equals(keyword.toUpperCase())) {
            return false;
        }
        
        // 跳过包含特殊字符的
        if (!keyword.matches("^[A-Za-z0-9_-]+$")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取 Content-Type
     */
    private String getContentType(HttpRequest request) {
        for (var header : request.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return "application/x-www-form-urlencoded";
    }
    
    /**
     * 提取主域名
     * - 如果是IP地址，返回完整IP
     * - 如果是域名，返回主域名（倒数第二级+顶级域名）
     */
    private String extractMainDomain(String host) {
        if (host == null || host.isEmpty()) {
            return host;
        }
        
        // ✅ 检测是否为IP地址（IPv4）
        if (host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            // IP地址直接返回完整IP作为主域名
            return host;
        }
        
        // ✅ IPv6地址也直接返回
        if (host.contains(":")) {
            return host;
        }
        
        // ✅ 域名：提取主域名（倒数第二级+顶级域名）
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }
    
    /**
     * ✅ 规范化endpoint，将路径参数化的接口识别为同一个接口
     * 
     * 规则：
     * 1. 将路径按 `/` 拆分成单词
     * 2. 如果只有一处不同，且位置相同，则判断为同一个接口
     * 3. 将不同的部分替换为占位符 `{id}` 或 `{param}`
     * 
     * 示例：
     * - /api/business/folder/tree/1001090991 → /api/business/folder/tree/{id}
     * - /api/business/folder/tree/587091401 → /api/business/folder/tree/{id}
     * 
     * @param endpoint 原始endpoint
     * @param mainDomain 主域名（用于查找同一主域名下的其他endpoint进行比较）
     * @return 规范化后的endpoint
     */
    private String normalizeEndpoint(String endpoint, String mainDomain) {
        if (endpoint == null || endpoint.isEmpty() || "/".equals(endpoint)) {
            return endpoint;
        }
        
        // ✅ 先尝试从已收集的endpoint中找到相似的
        String[] currentParts = endpoint.split("/");
        if (currentParts.length < 2) {
            return endpoint;  // 路径太短，不需要规范化
        }
        
        // ✅ 优先查找同一主域名下的endpoint（更准确）
        DomainData domainData = domainDataMap.get(mainDomain);
        if (domainData != null) {
            Set<String> existingEndpoints = domainData.getAllEndpoints();
            
            String normalized = findSimilarEndpoint(endpoint, currentParts, existingEndpoints);
            if (normalized != null) {
                return normalized;
            }
        }
        
        // ✅ 如果同一主域名下没找到，再遍历所有主域名下的endpoint（跨主域比较，但优先级较低）
        for (Map.Entry<String, DomainData> entry : domainDataMap.entrySet()) {
            if (entry.getKey().equals(mainDomain)) {
                continue;  // 已经检查过了
            }
            
            DomainData otherDomainData = entry.getValue();
            
            // 获取该主域名下的所有endpoint
            Set<String> existingEndpoints = otherDomainData.getAllEndpoints();
            
            String normalized = findSimilarEndpoint(endpoint, currentParts, existingEndpoints);
            if (normalized != null) {
                return normalized;
            }
        }
        
        // ✅ 如果没有找到相似的，返回原始endpoint
        return endpoint;
    }
    
    /**
     * ✅ 查找相似的endpoint（辅助方法）
     */
    private String findSimilarEndpoint(String endpoint, String[] currentParts, Set<String> existingEndpoints) {
        for (String existingEndpoint : existingEndpoints) {
            if (existingEndpoint == null || existingEndpoint.isEmpty() || "/".equals(existingEndpoint)) {
                continue;
            }
            
            // ✅ 如果existingEndpoint已经是规范化后的（包含{id}），跳过
            if (existingEndpoint.contains("{id}") || existingEndpoint.contains("{param}")) {
                continue;
            }
            
            String[] existingParts = existingEndpoint.split("/");
            
            // ✅ 检查长度是否相同
            if (existingParts.length != currentParts.length) {
                continue;
            }
            
            // ✅ 检查是否只有一处不同
            int diffCount = 0;
            int diffIndex = -1;
            for (int i = 0; i < currentParts.length; i++) {
                if (!currentParts[i].equals(existingParts[i])) {
                    diffCount++;
                    diffIndex = i;
                }
            }
            
            // ✅ 如果只有一处不同，且不同的部分都是数字或看起来像ID，则认为是同一个接口
            if (diffCount == 1 && diffIndex >= 0) {
                String currentDiff = currentParts[diffIndex];
                String existingDiff = existingParts[diffIndex];
                
                // ✅ 检查不同的部分是否都是数字或UUID格式（看起来像ID）
                boolean currentIsId = isIdLike(currentDiff);
                boolean existingIsId = isIdLike(existingDiff);
                
                if (currentIsId && existingIsId) {
                    // ✅ 找到相似的endpoint，使用占位符替换不同的部分
                    String[] normalizedParts = currentParts.clone();
                    normalizedParts[diffIndex] = "{id}";
                    String normalized = String.join("/", normalizedParts);
                    
                    api.logging().raiseDebugEvent(String.format(
                        "✅ 规范化endpoint: %s → %s (与 %s 相似)",
                        endpoint, normalized, existingEndpoint
                    ));
                    
                    return normalized;
                }
            }
        }
        
        return null;
    }
    
    /**
     * ✅ 判断字符串是否像ID（数字、UUID等）
     */
    private boolean isIdLike(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        // ✅ 纯数字（至少3位，避免误判）
        if (str.matches("^\\d{3,}$")) {
            return true;
        }
        
        // ✅ UUID格式（8-4-4-4-12）
        if (str.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            return true;
        }
        
        // ✅ 32位或64位十六进制字符串（MD5/SHA256等）
        if (str.matches("^[0-9a-fA-F]{32}$") || str.matches("^[0-9a-fA-F]{64}$")) {
            return true;
        }
        
        // ✅ 其他常见的ID格式（如：base64编码的ID，至少8个字符）
        if (str.length() >= 8 && str.matches("^[A-Za-z0-9_-]+$")) {
            // 如果包含数字，更可能是ID
            if (str.matches(".*\\d.*")) {
                return true;
            }
        }
        
        return false;
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
    
    // ========== 内部类 ==========
    
    /**
     * 域数据 - 存储一个主域名下的所有数据
     */
    private static class DomainData {
        private final String mainDomain;
        
        // 主域名下所有参数（合并所有子域名）
        private final Set<String> allParameters = ConcurrentHashMap.newKeySet();
        
        // 按 host 分组的参数
        private final Map<String, Set<String>> hostParameters = new ConcurrentHashMap<>();
        
        // 接口信息
        private final Map<String, EndpointInfo> endpointMap = new ConcurrentHashMap<>();
        
        // host 列表
        private final Set<String> hosts = ConcurrentHashMap.newKeySet();
        
        // ✅ 最后更新时间（仅在数据实际变化时更新）
        private volatile long lastUpdateTime = System.currentTimeMillis();
        
        public DomainData(String mainDomain) {
            this.mainDomain = mainDomain;
        }
        
        /**
         * ✅ 获取最后更新时间
         */
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        /**
         * ✅ 更新最后更新时间（仅在数据变化时调用）
         */
        private void updateLastUpdateTime() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        /**
         * 添加参数
         * ✅ 修复：遍历查找匹配的EndpointInfo（因为完整key包含method和contentType）
         */
        public void addParameter(String host, String endpoint, String parameter) {
            boolean isNew = allParameters.add(parameter);  // ✅ 检查是否是新参数
            hosts.add(host);
            
            // 添加到 host 参数集合
            hostParameters.computeIfAbsent(host, k -> ConcurrentHashMap.newKeySet())
                         .add(parameter);
            
            // ✅ 修复：遍历endpointMap，找到匹配host和endpoint的EndpointInfo
            for (Map.Entry<String, EndpointInfo> entry : endpointMap.entrySet()) {
                EndpointInfo epInfo = entry.getValue();
                if (epInfo.host.equals(host) && epInfo.endpoint.equals(endpoint)) {
                    epInfo.addParameter(parameter);
                    break;  // 找到第一个匹配的即可（同一host+endpoint可能有多个method/contentType组合）
                }
            }
            
            // ✅ 如果是新参数，更新时间
            if (isNew) {
                updateLastUpdateTime();
            }
        }
        
        /**
         * ✅ 直接添加参数（用于数据恢复，不需要endpoint）
         * 
         * @param host 子域名
         * @param parameter 参数名
         */
        public void addParameterDirectly(String host, String parameter) {
            boolean isNew = allParameters.add(parameter);
            hosts.add(host);
            hostParameters.computeIfAbsent(host, k -> ConcurrentHashMap.newKeySet())
                         .add(parameter);
            if (isNew) {
                updateLastUpdateTime();
            }
        }
        
        /**
         * 添加接口信息
         */
        public void addEndpoint(String host, String endpoint, String method, 
                               String contentType, HttpRequest request) {
            String normalizedContentType = normalizeContentType(contentType);
            // ✅ 修复：统一使用完整key（method|host|contentType|endpoint），与getEndpointTemplate保持一致
            String endpointKey = method + "|" + host + "|" + normalizedContentType + "|" + endpoint;
            hosts.add(host);
            
            // ✅ 修复：先检查是否存在，正确判断是否为新接口
            boolean isNewEndpoint = !endpointMap.containsKey(endpointKey);
            EndpointInfo epInfo = endpointMap.computeIfAbsent(endpointKey, 
                k -> new EndpointInfo(host, endpoint, method, normalizedContentType, request));
            
            // ✅ 修复：如果接口已存在，更新模板请求以合并所有参数（确保包含所有见过的参数值）
            if (!isNewEndpoint && epInfo != null) {
                epInfo.updateTemplateRequest(request);
            }
            
            // ✅ 修复：只在新接口时更新时间
            if (isNewEndpoint) {
                updateLastUpdateTime();
            }
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
        
        /**
         * 检查是否有该参数
         */
        public boolean hasParameter(String parameter) {
            return allParameters.contains(parameter);
        }
        
        /**
         * 获取所有参数
         */
        public Set<String> getAllParameters() {
            return new HashSet<>(allParameters);
        }
        
        /**
         * 获取特定 host 的参数
         */
        public Set<String> getHostParameters(String host) {
            Set<String> params = hostParameters.get(host);
            return params != null ? new HashSet<>(params) : new HashSet<>();
        }
        
        /**
         * 获取所有 host
         */
        public Set<String> getAllHosts() {
            return new HashSet<>(hosts);
        }
        
        /**
         * 获取所有接口（仅路径）
         */
        public Set<String> getAllEndpoints() {
            return endpointMap.values().stream()
                .map(EndpointInfo::getEndpoint)
                .collect(java.util.stream.Collectors.toSet());
        }
        
        /**
         * 获取所有接口Key（包含method、host、contentType、endpoint）
         */
        public Set<EndpointKey> getAllEndpointKeys() {
            return endpointMap.values().stream()
                .map(info -> new EndpointKey(info.method, info.host, info.contentType, info.endpoint))
                .collect(java.util.stream.Collectors.toSet());
        }
        
        /**
         * 获取接口的请求模板（仅通过endpoint查找）
         */
        public HttpRequest getEndpointTemplate(String endpoint) {
            for (EndpointInfo epInfo : endpointMap.values()) {
                if (epInfo.getEndpoint().equals(endpoint) && epInfo.getTemplateRequest() != null) {
                    return epInfo.getTemplateRequest();
                }
            }
            return null;
        }
        
        /**
         * 获取接口的请求模板（通过 EndpointKey 精确查找）
         */
        public HttpRequest getEndpointTemplate(EndpointKey epKey) {
            String key = epKey.method + "|" + epKey.host + "|" + epKey.contentType + "|" + epKey.endpoint;
            EndpointInfo epInfo = endpointMap.get(key);
            return epInfo != null ? epInfo.getTemplateRequest() : null;
        }
    }
    
    /**
     * 接口信息
     */
    private static class EndpointInfo {
        private final String host;
        private final String endpoint;
        private final String method;
        private final String contentType;
        private final Set<String> parameters = ConcurrentHashMap.newKeySet();
        private HttpRequest templateRequest;
        
        public EndpointInfo(String host, String endpoint, String method, 
                          String contentType, HttpRequest request) {
            this.host = host;
            this.endpoint = endpoint;
            this.method = method;
            this.contentType = contentType;
            
            // 保存第一个请求作为模板
            if (this.templateRequest == null) {
                this.templateRequest = request;
            }
        }
        
        public void addParameter(String parameter) {
            parameters.add(parameter);
        }
        
        /**
         * ✅ 修复：更新模板请求，合并所有参数（包括新请求中的参数）
         * ✅ 修复：动态更新请求头（Cookie、Authorization等），新值替换旧值
         * 这样确保模板请求包含所有见过的参数值和最新的请求头
         */
        public void updateTemplateRequest(HttpRequest newRequest) {
            if (newRequest == null) return;
            
            // 如果当前模板为空，直接使用新请求
            if (this.templateRequest == null) {
                this.templateRequest = newRequest;
                return;
            }
            
            // 收集旧模板中的所有参数（使用 List 保留所有参数值，包括同名参数）
            java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> allUrlParams = new java.util.ArrayList<>();
            java.util.List<java.util.AbstractMap.SimpleEntry<String, String>> allBodyParams = new java.util.ArrayList<>();
            
            // 先添加旧模板中的所有参数
            for (var param : this.templateRequest.parameters()) {
                if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                    allUrlParams.add(new java.util.AbstractMap.SimpleEntry<>(param.name(), param.value()));
                } else if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                    allBodyParams.add(new java.util.AbstractMap.SimpleEntry<>(param.name(), param.value()));
                }
            }
            
            // 再添加新请求中的所有参数（如果参数名已存在，也添加，保留所有值）
            for (var param : newRequest.parameters()) {
                if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                    allUrlParams.add(new java.util.AbstractMap.SimpleEntry<>(param.name(), param.value()));
                } else if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                    allBodyParams.add(new java.util.AbstractMap.SimpleEntry<>(param.name(), param.value()));
                }
            }
            
            // ✅ 修复：构建一个干净的请求（去掉 URL 中的 query 参数和 body 中的参数），然后添加所有合并后的参数
            // 1. 获取干净的 path（去掉 query 参数）
            String cleanPath = newRequest.path();
            int queryIndex = cleanPath != null ? cleanPath.indexOf('?') : -1;
            if (queryIndex > 0) {
                cleanPath = cleanPath.substring(0, queryIndex);
            }
            if (cleanPath == null || cleanPath.isEmpty()) {
                cleanPath = "/";
            }
            
            // 2. ✅ 修复：合并请求头，新请求的请求头优先（动态更新Cookie、Authorization等）
            // 先收集旧模板的所有请求头（作为基础）
            java.util.Map<String, String> mergedHeaders = new java.util.LinkedHashMap<>();
            for (var header : this.templateRequest.headers()) {
                String headerName = header.name();
                // ✅ 跳过Host和Content-Length（由API自动处理）
                if ("Host".equalsIgnoreCase(headerName) || "Content-Length".equalsIgnoreCase(headerName)) {
                    continue;
                }
                mergedHeaders.put(headerName.toLowerCase(java.util.Locale.ROOT), header.value());
            }
            
            // ✅ 再添加新请求的请求头（新值会替换旧值，实现动态更新）
            for (var header : newRequest.headers()) {
                String headerName = header.name();
                // ✅ 跳过Host和Content-Length（由API自动处理）
                if ("Host".equalsIgnoreCase(headerName) || "Content-Length".equalsIgnoreCase(headerName)) {
                    continue;
                }
                // ✅ 新请求的请求头优先，替换旧值（实现Cookie、Authorization等的动态更新）
                mergedHeaders.put(headerName.toLowerCase(java.util.Locale.ROOT), header.value());
            }
            
            // 3. 构建基础请求字符串（使用新请求的 method、合并后的 headers、body，但使用干净的 path）
            try {
                StringBuilder requestBuilder = new StringBuilder();
                requestBuilder.append(newRequest.method()).append(" ").append(cleanPath).append(" HTTP/1.1\r\n");
                
                // ✅ 添加合并后的 headers（保持原始大小写，但值已更新）
                // 先添加新请求的 headers（保持顺序）
                java.util.Set<String> addedHeaders = new java.util.HashSet<>();
                for (var header : newRequest.headers()) {
                    String headerName = header.name();
                    // ✅ 跳过Host和Content-Length（由API自动处理）
                    if ("Host".equalsIgnoreCase(headerName) || "Content-Length".equalsIgnoreCase(headerName)) {
                        continue;
                    }
                    String headerKey = headerName.toLowerCase(java.util.Locale.ROOT);
                    String headerValue = mergedHeaders.containsKey(headerKey) ? mergedHeaders.get(headerKey) : header.value();
                    requestBuilder.append(headerName).append(": ").append(headerValue).append("\r\n");
                    addedHeaders.add(headerKey);
                }
                
                // ✅ 添加旧模板中独有的请求头（新请求中没有的）
                for (var header : this.templateRequest.headers()) {
                    String headerName = header.name();
                    if ("Host".equalsIgnoreCase(headerName) || "Content-Length".equalsIgnoreCase(headerName)) {
                        continue;
                    }
                    String headerKey = headerName.toLowerCase(java.util.Locale.ROOT);
                    // ✅ 如果新请求中没有这个请求头，添加旧模板的请求头
                    if (!addedHeaders.contains(headerKey) && mergedHeaders.containsKey(headerKey)) {
                        requestBuilder.append(headerName).append(": ").append(mergedHeaders.get(headerKey)).append("\r\n");
                    }
                }
                
                requestBuilder.append("\r\n");
                
                // 添加 body（如果有，但要去掉 body 中的参数）
                // 注意：如果 body 是表单格式，参数在 body 中，我们需要去掉这些参数
                String bodyStr = newRequest.bodyToString();
                if (bodyStr != null && !bodyStr.isEmpty()) {
                    // 检查 Content-Type 是否是表单格式
                    boolean isFormData = false;
                    for (var header : newRequest.headers()) {
                        if ("Content-Type".equalsIgnoreCase(header.name()) && 
                            header.value().contains("application/x-www-form-urlencoded")) {
                            isFormData = true;
                            break;
                        }
                    }
                    // 如果不是表单格式，直接添加 body（如 JSON、XML 等）
                    if (!isFormData) {
                        requestBuilder.append(bodyStr);
                    }
                    // 如果是表单格式，body 中的参数会被下面的 withAddedParameters 添加，所以这里不添加
                }
                
                // 4. 构建干净的请求（不包含任何参数）
                burp.api.montoya.http.message.requests.HttpRequest updatedRequest = 
                    burp.api.montoya.http.message.requests.HttpRequest.httpRequest(
                        newRequest.httpService(), 
                        requestBuilder.toString()
                    );
                
                // 5. 添加所有 URL 参数（包括旧模板和新请求中的所有参数值）
                for (java.util.AbstractMap.SimpleEntry<String, String> e : allUrlParams) {
                    updatedRequest = updatedRequest.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.urlParameter(e.getKey(), e.getValue())
                    );
                }
                
                // 6. 添加所有 Body 参数（包括旧模板和新请求中的所有参数值）
                for (java.util.AbstractMap.SimpleEntry<String, String> e : allBodyParams) {
                    updatedRequest = updatedRequest.withAddedParameters(
                        burp.api.montoya.http.message.params.HttpParameter.bodyParameter(e.getKey(), e.getValue())
                    );
                }
                
                this.templateRequest = updatedRequest;
            } catch (Exception e) {
                // 如果构建失败，使用新请求（至少保留新请求的参数）
                this.templateRequest = newRequest;
            }
        }
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public HttpRequest getTemplateRequest() {
            return templateRequest;
        }
    }
    
    /**
     * 接口Key - 唯一标识一个接口（method + host + contentType + endpoint）
     */
    public static class EndpointKey {
        public final String method;
        public final String host;
        public final String contentType;
        public final String endpoint;
        
        public EndpointKey(String method, String host, String contentType, String endpoint) {
            this.method = method;
            this.host = host;
            this.contentType = contentType;
            this.endpoint = endpoint;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndpointKey that = (EndpointKey) o;
            return method.equals(that.method) && 
                   host.equals(that.host) && 
                   contentType.equals(that.contentType) && 
                   endpoint.equals(that.endpoint);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(method, host, contentType, endpoint);
        }
        
        @Override
        public String toString() {
            return method + " " + host + " (" + contentType + ") " + endpoint;
        }
    }
    
    /**
     * 收集器统计信息
     */
    public static class CollectorStatistics {
        private final int domainCount;
        private final int hostCount;
        private final int endpointCount;
        private final int parameterCount;
        private final int keywordCount;
        private final CollectionMode mode;
        
        public CollectorStatistics(int domainCount, int hostCount, 
                                  int endpointCount, int parameterCount,
                                  int keywordCount, CollectionMode mode) {
            this.domainCount = domainCount;
            this.hostCount = hostCount;
            this.endpointCount = endpointCount;
            this.parameterCount = parameterCount;
            this.keywordCount = keywordCount;
            this.mode = mode;
        }
        
        public int getDomainCount() { return domainCount; }
        public int getHostCount() { return hostCount; }
        public int getEndpointCount() { return endpointCount; }
        public int getParameterCount() { return parameterCount; }
        public int getKeywordCount() { return keywordCount; }
        public CollectionMode getMode() { return mode; }
        
        @Override
        public String toString() {
            if (mode == CollectionMode.PARAMETERS_AND_KEYWORDS) {
                return String.format("主域名: %d, Host: %d, 接口: %d, 参数: %d, 关键词: %d [模式: 参数+关键词]",
                    domainCount, hostCount, endpointCount, parameterCount, keywordCount);
            } else {
                return String.format("主域名: %d, Host: %d, 接口: %d, 参数: %d [模式: 仅参数]",
                    domainCount, hostCount, endpointCount, parameterCount);
            }
        }
    }
}


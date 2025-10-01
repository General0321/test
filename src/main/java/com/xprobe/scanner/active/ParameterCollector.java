package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.ParsedHttpParameter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<String> processedRequests = Collections.newSetFromMap(
        new ConcurrentHashMap<String, Boolean>()
    );
    
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
            String method = request.method();
            String contentType = getContentType(request);
            
            // 去重检查（method + url + contentType）
            String dedupeKey = method + "|" + url + "|" + normalizeContentType(contentType);
            if (processedRequests.contains(dedupeKey)) {
                return false;
            }
            
            URI uri = new URI(url);
            String host = uri.getHost();
            String mainDomain = extractMainDomain(host);
            String endpoint = uri.getPath().isEmpty() ? "/" : uri.getPath();
            
            // 提取参数
            Set<String> parameters = extractParameters(request);
            
            if (parameters.isEmpty()) {
                processedRequests.add(dedupeKey);
                return false;
            }
            
            // 获取或创建域数据
            DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
            
            // 检查是否有新参数
            boolean hasNewParameters = false;
            for (String param : parameters) {
                if (!domainData.hasParameter(param)) {
                    hasNewParameters = true;
                    break;
                }
            }
            
            // 添加参数和接口信息
            domainData.addEndpoint(host, endpoint, method, contentType, request);
            for (String param : parameters) {
                domainData.addParameter(host, endpoint, param);
            }
            
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
            processedRequests.add(dedupeKey);
            
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
     * 清空所有数据
     */
    public void clear() {
        domainDataMap.clear();
        processedRequests.clear();
        domainKeywords.clear();
        api.logging().raiseInfoEvent("参数收集器已清空");
    }
    
    // ========== 辅助方法 ==========
    
    // ========== 参数和关键词提取逻辑（参考 GAP.py）==========
    
    // 参数名正则：只允许 A-Z a-z 0-9 - _ . ~ [ ]
    // 参考 GAP.py: REGEX_PARAM = re.compile(r"^[A-Za-z0-9_.~\-\[\]]+$")
    private static final java.util.regex.Pattern PATTERN_VALID_PARAM = 
        java.util.regex.Pattern.compile("^[A-Za-z0-9_.~\\-\\[\\]]+$");
    
    // 关键词正则：至少3个字符的单词，不在路径分隔符后
    // 参考 GAP.py: REGEX_WORDS = re.compile(r"(?<![\/])\b\w{3,}\b(?![\/])")
    private static final java.util.regex.Pattern PATTERN_WORDS = 
        java.util.regex.Pattern.compile("(?<![/])\\b\\w{3,}\\b(?![/])");
    
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
     */
    private Set<String> extractParameters(HttpRequest request) {
        Set<String> parameters = new HashSet<>();
        
        for (ParsedHttpParameter param : request.parameters()) {
            String paramName = cleanParameterName(param.name());
            
            // 验证参数名格式（参考 GAP.py）
            if (paramName != null && !paramName.isEmpty() && 
                PATTERN_VALID_PARAM.matcher(paramName).matches()) {
                parameters.add(paramName);
            }
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
        
        public DomainData(String mainDomain) {
            this.mainDomain = mainDomain;
        }
        
        /**
         * 添加参数
         */
        public void addParameter(String host, String endpoint, String parameter) {
            allParameters.add(parameter);
            hosts.add(host);
            
            // 添加到 host 参数集合
            hostParameters.computeIfAbsent(host, k -> ConcurrentHashMap.newKeySet())
                         .add(parameter);
            
            // 添加到接口参数集合
            String endpointKey = host + ":" + endpoint;
            EndpointInfo epInfo = endpointMap.get(endpointKey);
            if (epInfo != null) {
                epInfo.addParameter(parameter);
            }
        }
        
        /**
         * 添加接口信息
         */
        public void addEndpoint(String host, String endpoint, String method, 
                               String contentType, HttpRequest request) {
            String normalizedContentType = normalizeContentType(contentType);
            String endpointKey = method + "|" + host + "|" + normalizedContentType + "|" + endpoint;
            hosts.add(host);
            
            endpointMap.computeIfAbsent(endpointKey, 
                k -> new EndpointInfo(host, endpoint, method, normalizedContentType, request));
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


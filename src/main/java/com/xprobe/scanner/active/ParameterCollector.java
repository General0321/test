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
            
            // 去重检查（method + url + contentType）
            String dedupeKey = method + "|" + url + "|" + normalizeContentType(contentType);
            if (processedRequests.containsKey(dedupeKey)) {
                return false;
            }
            
            URI uri = new URI(url);
            String host = uri.getHost();
            String mainDomain = extractMainDomain(host);
            String endpoint = uri.getPath().isEmpty() ? "/" : uri.getPath();
            
            // 提取参数
            Set<String> parameters = extractParameters(request);
            
            if (parameters.isEmpty()) {
                processedRequests.put(dedupeKey, Boolean.TRUE);
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
            
            // 去重检查
            String dedupeKey = "RESPONSE|" + method + "|" + url + "|" + normalizeContentType(contentType);
            if (processedRequests.containsKey(dedupeKey)) {
                return false;
            }
            
            URI uri = new URI(url);
            String host = uri.getHost();
            String mainDomain = extractMainDomain(host);
            
            // 从响应中提取参数（从JSON、HTML等）
            Set<String> parameters = extractParametersFromResponse(response);
            
            if (parameters.isEmpty()) {
                processedRequests.put(dedupeKey, Boolean.TRUE);
                return false;
            }
            
            // 获取或创建域数据
            DomainData domainData = domainDataMap.computeIfAbsent(mainDomain, DomainData::new);
            
            // 检查是否有新参数
            boolean hasNewParameters = false;
            String endpoint = uri.getPath().isEmpty() ? "/" : uri.getPath();
            
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
            endpointMap.computeIfAbsent(endpointKey, 
                k -> new EndpointInfo(host, endpoint, method, normalizedContentType, request));
            
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


package com.xprobe.scanner.active.arjun.http;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xprobe.scanner.active.arjun.error.RateLimiter;

import java.util.*;

/**
 * HTTP请求执行器 - 使用Burp API发送请求
 * ✅ 集成速率限制和错误处理（对应Python的requester.py）
 */
public class BurpHttpRequester {
    
    private final MontoyaApi api;
    private final ObjectMapper jsonMapper;
    private final RateLimiter rateLimiter;    // ✅ 新增：速率限制器
    private final int timeout;                 // ✅ 新增：超时时间（秒）
    private final Map<String, String> customHeaders;  // ✅ 自定义HTTP头（覆盖/添加）
    
    public BurpHttpRequester(MontoyaApi api) {
        this(api, 9999, false, 15, new HashMap<>());  // 默认：9999 req/s, 非稳定模式, 15秒超时, 无自定义头
    }
    
    public BurpHttpRequester(MontoyaApi api, int maxRequestsPerSecond, boolean stableMode, int timeout) {
        this(api, maxRequestsPerSecond, stableMode, timeout, new HashMap<>());
    }
    
    public BurpHttpRequester(MontoyaApi api, int maxRequestsPerSecond, boolean stableMode, int timeout, 
                            Map<String, String> customHeaders) {
        this.api = api;
        this.jsonMapper = new ObjectMapper();
        this.rateLimiter = new RateLimiter(api, maxRequestsPerSecond, stableMode);
        this.timeout = timeout;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : new HashMap<>();
    }
    
    /**
     * 发送HTTP请求（对应Python的requester()）
     * ✅ 集成速率限制（@limits装饰器）
     * ✅ 捕获异常并包装为RequestResult
     */
    public RequestResult sendRequest(HttpRequest request) {
        try {
            // ✅ 速率限制（Python: @limits + time.sleep(delay)）
            rateLimiter.acquire();
            
            // ✅ 应用自定义HTTP头（覆盖/添加）
            HttpRequest modifiedRequest = request;
            if (customHeaders != null && !customHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = entry.getValue();
                    
                    // ✅ 空值检查：跳过无效的头
                    if (headerName == null || headerName.trim().isEmpty() || 
                        headerValue == null || headerValue.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 检查请求中是否已有该头
                    boolean headerExists = modifiedRequest.headers().stream()
                        .anyMatch(header -> header.name().equalsIgnoreCase(headerName));
                    
                    if (headerExists) {
                        // 覆盖现有头
                        modifiedRequest = modifiedRequest.withUpdatedHeader(headerName, headerValue);
                    } else {
                        // 添加新头
                        modifiedRequest = modifiedRequest.withAddedHeader(headerName, headerValue);
                    }
                }
            }
            
            // 发送请求
            HttpRequestResponse result = api.http().sendRequest(modifiedRequest);
            
            if (result.response() == null) {
                return RequestResult.error(new RuntimeException("响应为空"));
            }
            
            return RequestResult.success(result.response());
            
        } catch (Exception e) {
            // ✅ Python: 捕获所有异常返回字符串（requester.py line 79-80）
            api.logging().raiseDebugEvent("请求异常: " + e.getMessage());
            return RequestResult.error(e);
        }
    }
    
    /**
     * 请求结果包装器（用于错误处理）
     */
    public static class RequestResult {
        private final HttpResponse response;
        private final Exception exception;
        
        private RequestResult(HttpResponse response, Exception exception) {
            this.response = response;
            this.exception = exception;
        }
        
        public static RequestResult success(HttpResponse response) {
            return new RequestResult(response, null);
        }
        
        public static RequestResult error(Exception exception) {
            return new RequestResult(null, exception);
        }
        
        public boolean isSuccess() {
            return exception == null && response != null;
        }
        
        public HttpResponse getResponse() {
            return response;
        }
        
        public Exception getException() {
            return exception;
        }
    }
    
    /**
     * 构建测试请求（添加测试参数）
     * ✅ 优化：只添加原始请求中不存在的参数，避免重复参数
     */
    public HttpRequest buildTestRequest(HttpRequest originalRequest, 
                                         Map<String, String> testParams) {
        
        if (testParams == null || testParams.isEmpty()) {
            return originalRequest;
        }
        
        // ✅ P1修复：根据请求类型只过滤对应位置的参数，避免误过滤
        Set<String> existingParamNames = new HashSet<>();
        
        // 获取Content-Type
        String requestContentType = getContentType(originalRequest);
        
        if ("GET".equalsIgnoreCase(originalRequest.method())) {
            // GET请求：只过滤URL参数
            for (var param : originalRequest.parameters()) {
                if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.URL) {
                    existingParamNames.add(param.name());
                }
            }
        } else if (requestContentType != null && requestContentType.contains("application/json")) {
            // JSON请求：从JSON body中提取参数名（不能用parameters()）
            // JSON参数不会出现在parameters()中，所以无需过滤
            // 将在buildJsonRequest中合并时自动处理
        } else {
            // POST表单：只过滤Body参数
            for (var param : originalRequest.parameters()) {
                if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.BODY) {
                    existingParamNames.add(param.name());
                }
            }
        }
        
        // ✅ 过滤掉已存在的参数（避免重复参数如 ?id=123&id=test）
        Map<String, String> filteredParams = new HashMap<>();
        for (Map.Entry<String, String> entry : testParams.entrySet()) {
            if (!existingParamNames.contains(entry.getKey())) {
                filteredParams.put(entry.getKey(), entry.getValue());
            }
        }
        
        // 如果过滤后没有参数需要添加，直接返回原始请求
        if (filteredParams.isEmpty()) {
            api.logging().raiseDebugEvent("  所有测试参数已存在于原始请求中，跳过添加");
            return originalRequest;
        }
        
        HttpRequest modifiedRequest = originalRequest;
        
        // 获取Content-Type
        String contentType = getContentType(originalRequest);
        
        // 根据请求类型添加参数（只添加不存在的）
        if ("GET".equalsIgnoreCase(originalRequest.method())) {
            // GET: 添加URL参数
            for (Map.Entry<String, String> entry : filteredParams.entrySet()) {
                modifiedRequest = modifiedRequest.withAddedParameters(
                    HttpParameter.urlParameter(entry.getKey(), entry.getValue())
                );
            }
        } else if (contentType != null && contentType.contains("application/json")) {
            // JSON: 合并到JSON body（只合并不存在的参数）
            modifiedRequest = buildJsonRequest(originalRequest, filteredParams);
        } else {
            // POST表单: 添加body参数
            for (Map.Entry<String, String> entry : filteredParams.entrySet()) {
                modifiedRequest = modifiedRequest.withAddedParameters(
                    HttpParameter.bodyParameter(entry.getKey(), entry.getValue())
                );
            }
        }
        
        // 添加标记header（避免被插件重复扫描）
        modifiedRequest = modifiedRequest.withAddedHeader(
            "X-XProbe-ParamDiscovery", "1"
        );
        
        return modifiedRequest;
    }
    
    /**
     * 构建JSON请求（合并测试参数到JSON body）
     */
    @SuppressWarnings("unchecked")
    private HttpRequest buildJsonRequest(HttpRequest originalRequest, 
                                          Map<String, String> testParams) {
        try {
            String originalBody = originalRequest.bodyToString();
            
            // 解析原始JSON
            Map<String, Object> jsonMap;
            if (originalBody == null || originalBody.trim().isEmpty()) {
                jsonMap = new HashMap<>();
            } else {
                jsonMap = jsonMapper.readValue(originalBody, Map.class);
            }
            
            // 添加测试参数
            jsonMap.putAll(testParams);
            
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
            // 降级：使用原始请求
            return originalRequest;
        }
    }
    
    /**
     * 获取Content-Type
     */
    private String getContentType(HttpRequest request) {
        for (var header : request.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
}


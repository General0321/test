package com.xprobe.scanner.active.arjun.http;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * HTTP请求执行器 - 使用Burp API发送请求
 */
public class BurpHttpRequester {
    
    private final MontoyaApi api;
    private final ObjectMapper jsonMapper;
    
    public BurpHttpRequester(MontoyaApi api) {
        this.api = api;
        this.jsonMapper = new ObjectMapper();
    }
    
    /**
     * 发送HTTP请求
     */
    public HttpResponse sendRequest(HttpRequest request) {
        try {
            HttpRequestResponse result = api.http().sendRequest(request);
            
            if (result.response() == null) {
                throw new RuntimeException("响应为空");
            }
            
            return result.response();
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("发送请求失败: " + e.getMessage());
            throw new RuntimeException("请求失败", e);
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


package com.xprobe.scanner.core;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Pair响应特征
 * 保存每个Pair的响应特征，供后续Pair引用
 * 
 * 支持的特征：
 * - responseTime: 响应时间（毫秒）
 * - responseLength: 响应体长度（字节）
 * - statusCode: 状态码
 * - headerCount: 响应头数量
 * - customVariables: 自定义提取的变量
 * 
 * ✨ 内存优化：
 * - 默认只保存哈希和长度（HASH_ONLY）
 * - 需要内容对比时保存清理后的内容（HASH_AND_CLEANED）
 * - 详细分析时保存完整内容（FULL_CONTENT）
 */
public class PairResponseFeatures {
    
    /**
     * 响应体保存模式
     */
    public enum BodySaveMode {
        /** 只保存MD5哈希（32字节）+ 长度 - 最省内存，默认模式 */
        HASH_ONLY,
        
        /** 保存哈希 + 清理后的内容 - 适合内容对比（忽略动态部分） */
        HASH_AND_CLEANED,
        
        /** 保存完整内容 + 清理后的内容 - 用于详细分析（最耗内存） */
        FULL_CONTENT
    }
    
    private int pairId;                           // Pair ID
    private long responseTime;                    // 响应时间（毫秒）
    private int responseLength;                   // 响应体长度（字节）
    private int statusCode;                       // 状态码
    private int headerCount;                      // 响应头数量
    private HttpResponse response;                // 原始响应对象（可选）
    
    // ✨ 新增：响应体内容相关
    private String responseBodyHash;              // 响应体MD5哈希（用于快速对比）
    private String responseBodyContent;           // 响应体内容（可选，用于详细对比）
    private String responseBodyClean;             // 清理动态内容后的响应体（用于内容对比）
    
    private Map<String, String> customVariables = new HashMap<>();  // 自定义变量
    
    /**
     * 从HttpResponse构建特征
     * 
     * @param saveBodyContent 是否保存完整响应体内容（用于详细对比）
     */
    public static PairResponseFeatures fromResponse(int pairId, 
                                                    HttpResponse response, 
                                                    long responseTime,
                                                    BodySaveMode saveMode) {
        PairResponseFeatures features = new PairResponseFeatures();
        features.pairId = pairId;
        features.responseTime = responseTime;
        
        if (response != null) {
            features.statusCode = response.statusCode();
            features.responseLength = response.body().length();
            features.headerCount = response.headers().size();
            features.response = response;
            
            // ✨ 智能保存响应体（根据模式）
            String bodyContent = response.bodyToString();
            if (bodyContent != null) {
                // 总是计算MD5哈希（32字节，很小）
                features.responseBodyHash = computeMD5(bodyContent);
                
                switch (saveMode) {
                    case HASH_ONLY:
                        // 只保存哈希和长度（最省内存，默认）
                        break;
                        
                    case HASH_AND_CLEANED:
                        // 保存哈希 + 清理后的内容（适合内容对比，忽略动态部分）
                        features.responseBodyClean = cleanDynamicContent(bodyContent);
                        break;
                        
                    case FULL_CONTENT:
                        // 保存完整内容（用于详细分析，最耗内存）
                        features.responseBodyContent = bodyContent;
                        features.responseBodyClean = cleanDynamicContent(bodyContent);
                        break;
                }
            }
        }
        
        return features;
    }
    
    /**
     * 从HttpResponse构建特征（默认：只保存哈希，最省内存）
     */
    public static PairResponseFeatures fromResponse(int pairId, 
                                                    HttpResponse response, 
                                                    long responseTime) {
        return fromResponse(pairId, response, responseTime, BodySaveMode.HASH_ONLY);
    }
    
    /**
     * 从HttpResponse构建特征（根据是否需要内容对比智能选择）
     * 
     * @param needBodyComparison 是否需要响应体内容对比
     */
    public static PairResponseFeatures fromResponse(int pairId, 
                                                    HttpResponse response, 
                                                    long responseTime,
                                                    boolean needBodyComparison) {
        // 如果需要内容对比，保存清理后的内容；否则只保存哈希
        BodySaveMode mode = needBodyComparison ? BodySaveMode.HASH_AND_CLEANED : BodySaveMode.HASH_ONLY;
        return fromResponse(pairId, response, responseTime, mode);
    }
    
    /**
     * 添加自定义变量
     */
    public void addVariable(String name, String value) {
        customVariables.put(name, value);
    }
    
    /**
     * 获取指定特征的值
     * 
     * @param featureName 特征名称（responseTime, responseLength, statusCode等）
     * @return 特征值（字符串形式）
     */
    public String getFeatureValue(String featureName) {
        switch (featureName.toLowerCase()) {
            case "responsetime":
            case "response_time":
                return String.valueOf(responseTime);
                
            case "responselength":
            case "response_length":
                return String.valueOf(responseLength);
                
            case "statuscode":
            case "status_code":
                return String.valueOf(statusCode);
                
            case "headercount":
            case "header_count":
                return String.valueOf(headerCount);
                
            default:
                // 尝试从自定义变量中获取
                return customVariables.getOrDefault(featureName, null);
        }
    }
    
    /**
     * 获取特征的数值形式（用于数值比较）
     */
    public long getFeatureNumericValue(String featureName) {
        String value = getFeatureValue(featureName);
        if (value == null) {
            return 0;
        }
        
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    // ========== Getters and Setters ==========
    
    public int getPairId() {
        return pairId;
    }
    
    public void setPairId(int pairId) {
        this.pairId = pairId;
    }
    
    public long getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }
    
    public int getResponseLength() {
        return responseLength;
    }
    
    public void setResponseLength(int responseLength) {
        this.responseLength = responseLength;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public int getHeaderCount() {
        return headerCount;
    }
    
    public void setHeaderCount(int headerCount) {
        this.headerCount = headerCount;
    }
    
    public HttpResponse getResponse() {
        return response;
    }
    
    public void setResponse(HttpResponse response) {
        this.response = response;
    }
    
    public Map<String, String> getCustomVariables() {
        return customVariables;
    }
    
    public void setCustomVariables(Map<String, String> customVariables) {
        this.customVariables = customVariables;
    }
    
    /**
     * 获取特征摘要（用于日志）
     */
    public String getSummary() {
        return String.format("Pair[%d] - Status:%d, Time:%dms, Length:%d bytes, Headers:%d",
            pairId, statusCode, responseTime, responseLength, headerCount);
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 计算字符串的MD5哈希
     */
    private static String computeMD5(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 清理响应体中的动态内容（用于内容对比）
     * 移除时间戳、token、nonce等动态元素
     */
    private static String cleanDynamicContent(String content) {
        if (content == null) {
            return "";
        }
        
        String cleaned = content;
        
        // 清理常见的动态内容模式
        cleaned = cleaned.replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?", "[TIMESTAMP]");  // ISO时间戳
        cleaned = cleaned.replaceAll("\"timestamp\"\\s*:\\s*\\d+", "\"timestamp\":[TIMESTAMP]");              // JSON时间戳
        cleaned = cleaned.replaceAll("\"csrf_token\"\\s*:\\s*\"[^\"]+\"", "\"csrf_token\":\"[TOKEN]\"");      // CSRF token
        cleaned = cleaned.replaceAll("nonce=[a-zA-Z0-9]+", "nonce=[NONCE]");                                  // Nonce
        cleaned = cleaned.replaceAll("sessionid=[a-zA-Z0-9]+", "sessionid=[SESSION]");                        // Session ID
        cleaned = cleaned.replaceAll("\\b[0-9a-f]{32}\\b", "[MD5HASH]");                                      // MD5哈希
        cleaned = cleaned.replaceAll("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "[UUID]"); // UUID
        
        return cleaned;
    }
    
    // ========== Getters for 响应体内容 ==========
    
    public String getResponseBodyHash() {
        return responseBodyHash;
    }
    
    public void setResponseBodyHash(String responseBodyHash) {
        this.responseBodyHash = responseBodyHash;
    }
    
    public String getResponseBodyContent() {
        return responseBodyContent;
    }
    
    public void setResponseBodyContent(String responseBodyContent) {
        this.responseBodyContent = responseBodyContent;
    }
    
    public String getResponseBodyClean() {
        return responseBodyClean;
    }
    
    public void setResponseBodyClean(String responseBodyClean) {
        this.responseBodyClean = responseBodyClean;
    }
}


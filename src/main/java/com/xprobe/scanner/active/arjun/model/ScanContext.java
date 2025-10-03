package com.xprobe.scanner.active.arjun.model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.*;

/**
 * 扫描上下文 - 保存扫描过程中的状态
 */
public class ScanContext {
    
    private final HttpRequest originalRequest;
    private final BaselineFactors factors;
    private Set<String> dictionary;
    private final HttpResponse baselineResponse;
    private final boolean isHealthy;
    
    public ScanContext(HttpRequest originalRequest, 
                       BaselineFactors factors,
                       Set<String> dictionary, 
                       HttpResponse baselineResponse,
                       boolean isHealthy) {
        this.originalRequest = originalRequest;
        this.factors = factors;
        this.dictionary = new LinkedHashSet<>(dictionary);
        this.baselineResponse = baselineResponse;
        this.isHealthy = isHealthy;
    }
    
    /**
     * 添加字典参数
     */
    public void addDictionary(Set<String> newParams) {
        this.dictionary.addAll(newParams);
    }
    
    /**
     * 创建新的上下文（使用新字典）
     */
    public ScanContext withDictionary(Set<String> newDict) {
        return new ScanContext(
            originalRequest, 
            factors, 
            newDict, 
            baselineResponse, 
            isHealthy
        );
    }
    
    // Getters
    
    public HttpRequest getOriginalRequest() {
        return originalRequest;
    }
    
    public BaselineFactors getFactors() {
        return factors;
    }
    
    public Set<String> getDictionary() {
        return new LinkedHashSet<>(dictionary);
    }
    
    public HttpResponse getBaselineResponse() {
        return baselineResponse;
    }
    
    public boolean isHealthy() {
        return isHealthy;
    }
}


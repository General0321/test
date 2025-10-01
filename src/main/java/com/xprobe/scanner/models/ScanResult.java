package com.xprobe.scanner.models;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * 扫描结果
 */
public class ScanResult {
    private final boolean isVulnerable;
    private final String scanType;
    private final String parameterName;
    private final String payload;
    private final HttpRequest originalRequest;
    private final HttpRequest modifiedRequest;
    private final HttpResponse response;
    private final long responseTime;
    private final String evidence;
    
    private ScanResult(Builder builder) {
        this.isVulnerable = builder.isVulnerable;
        this.scanType = builder.scanType;
        this.parameterName = builder.parameterName;
        this.payload = builder.payload;
        this.originalRequest = builder.originalRequest;
        this.modifiedRequest = builder.modifiedRequest;
        this.response = builder.response;
        this.responseTime = builder.responseTime;
        this.evidence = builder.evidence;
    }
    
    // Getters
    public boolean isVulnerable() { return isVulnerable; }
    public String getScanType() { return scanType; }
    public String getParameterName() { return parameterName; }
    public String getPayload() { return payload; }
    public HttpRequest getOriginalRequest() { return originalRequest; }
    public HttpRequest getModifiedRequest() { return modifiedRequest; }
    public HttpResponse getResponse() { return response; }
    public long getResponseTime() { return responseTime; }
    public String getEvidence() { return evidence; }
    
    // Builder pattern
    public static class Builder {
        private boolean isVulnerable;
        private String scanType;
        private String parameterName;
        private String payload;
        private HttpRequest originalRequest;
        private HttpRequest modifiedRequest;
        private HttpResponse response;
        private long responseTime;
        private String evidence;
        
        public Builder vulnerable(boolean isVulnerable) {
            this.isVulnerable = isVulnerable;
            return this;
        }
        
        public Builder scanType(String scanType) {
            this.scanType = scanType;
            return this;
        }
        
        public Builder parameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }
        
        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder originalRequest(HttpRequest originalRequest) {
            this.originalRequest = originalRequest;
            return this;
        }
        
        public Builder modifiedRequest(HttpRequest modifiedRequest) {
            this.modifiedRequest = modifiedRequest;
            return this;
        }
        
        public Builder response(HttpResponse response) {
            this.response = response;
            return this;
        }
        
        public Builder responseTime(long responseTime) {
            this.responseTime = responseTime;
            return this;
        }
        
        public Builder evidence(String evidence) {
            this.evidence = evidence;
            return this;
        }
        
        public ScanResult build() {
            return new ScanResult(this);
        }
    }
}

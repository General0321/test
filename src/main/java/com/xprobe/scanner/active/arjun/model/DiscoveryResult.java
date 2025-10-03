package com.xprobe.scanner.active.arjun.model;

import java.util.*;

/**
 * 参数发现结果
 */
public class DiscoveryResult {
    
    private final boolean success;
    private final String url;
    private final Set<String> foundParams;
    private final String errorMessage;
    private final long scanTimeMs;
    
    private DiscoveryResult(boolean success, String url, 
                            Set<String> foundParams, String errorMessage,
                            long scanTimeMs) {
        this.success = success;
        this.url = url;
        this.foundParams = foundParams != null ? new LinkedHashSet<>(foundParams) : new LinkedHashSet<>();
        this.errorMessage = errorMessage;
        this.scanTimeMs = scanTimeMs;
    }
    
    /**
     * 创建成功结果
     */
    public static DiscoveryResult success(String url, Set<String> params, long scanTimeMs) {
        return new DiscoveryResult(true, url, params, null, scanTimeMs);
    }
    
    /**
     * 创建失败结果
     */
    public static DiscoveryResult error(String errorMessage) {
        return new DiscoveryResult(false, null, null, errorMessage, 0);
    }
    
    // Getters
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getUrl() {
        return url;
    }
    
    public Set<String> getFoundParams() {
        return new LinkedHashSet<>(foundParams);
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getScanTimeMs() {
        return scanTimeMs;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "DiscoveryResult{url='" + url + "', found=" + foundParams.size() + 
                   " params, time=" + scanTimeMs + "ms}";
        } else {
            return "DiscoveryResult{error='" + errorMessage + "'}";
        }
    }
}


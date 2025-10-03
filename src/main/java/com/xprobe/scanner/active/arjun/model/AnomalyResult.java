package com.xprobe.scanner.active.arjun.model;

import java.util.*;

/**
 * 异常检测结果
 */
public class AnomalyResult {
    
    private final boolean hasAnomaly;
    private final String anomalyType;
    private final Set<String> params;
    private final String reason;
    
    private AnomalyResult(boolean hasAnomaly, String anomalyType, 
                          Set<String> params, String reason) {
        this.hasAnomaly = hasAnomaly;
        this.anomalyType = anomalyType;
        this.params = params != null ? new LinkedHashSet<>(params) : new LinkedHashSet<>();
        this.reason = reason;
    }
    
    /**
     * 创建异常检测结果
     */
    public static AnomalyResult detected(String type, Set<String> params, String reason) {
        return new AnomalyResult(true, type, params, reason);
    }
    
    /**
     * 创建正常结果（无异常）
     */
    public static AnomalyResult normal() {
        return new AnomalyResult(false, null, null, null);
    }
    
    // Getters
    
    public boolean hasAnomaly() {
        return hasAnomaly;
    }
    
    public String getAnomalyType() {
        return anomalyType;
    }
    
    public Set<String> getParams() {
        return new LinkedHashSet<>(params);
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public String toString() {
        if (hasAnomaly) {
            return "AnomalyResult{type='" + anomalyType + "', params=" + params + 
                   ", reason='" + reason + "'}";
        } else {
            return "AnomalyResult{normal}";
        }
    }
}


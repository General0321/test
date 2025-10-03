package com.xprobe.scanner.active.arjun.model;

/**
 * 参数候选 - 可能的有效参数
 */
public class ParamCandidate {
    
    private final String name;
    private final String anomalyType;
    private final int confidence;
    
    public ParamCandidate(String name) {
        this(name, null, 100);
    }
    
    public ParamCandidate(String name, String anomalyType, int confidence) {
        this.name = name;
        this.anomalyType = anomalyType;
        this.confidence = confidence;
    }
    
    // Getters
    
    public String getName() {
        return name;
    }
    
    public String getAnomalyType() {
        return anomalyType;
    }
    
    public int getConfidence() {
        return confidence;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParamCandidate that = (ParamCandidate) o;
        return name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public String toString() {
        return "ParamCandidate{" +
               "name='" + name + '\'' +
               (anomalyType != null ? ", type='" + anomalyType + '\'' : "") +
               ", confidence=" + confidence +
               '}';
    }
}


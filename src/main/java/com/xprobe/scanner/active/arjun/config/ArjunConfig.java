package com.xprobe.scanner.active.arjun.config;

/**
 * Arjun配置类
 */
public class ArjunConfig {
    
    private boolean enabled = true;
    private int chunkSize = 200;  // ✅ 默认值从250改为200
    private boolean enableHeuristic = true;
    private int maxThreads = 5;
    private int timeout = 15;
    
    public ArjunConfig() {
    }
    
    public ArjunConfig(int chunkSize, boolean enableHeuristic) {
        this.chunkSize = chunkSize;
        this.enableHeuristic = enableHeuristic;
    }
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public void setChunkSize(int chunkSize) {
        this.chunkSize = Math.max(10, Math.min(chunkSize, 1000));
    }
    
    public boolean isEnableHeuristic() {
        return enableHeuristic;
    }
    
    public void setEnableHeuristic(boolean enableHeuristic) {
        this.enableHeuristic = enableHeuristic;
    }
    
    public int getMaxThreads() {
        return maxThreads;
    }
    
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = Math.max(1, Math.min(maxThreads, 20));
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = Math.max(5, Math.min(timeout, 60));
    }
}


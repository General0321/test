package com.xprobe.scanner.config;

import java.io.Serializable;

/**
 * 请求-响应配对
 * 一个配对包含：请求匹配条件 + 响应匹配条件
 */
public class RuleMatchPair implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int id;                                    // 配对ID（用于逻辑表达式引用）
    private String label;                              // 配对标签（用户可见）
    private boolean enabled = true;                    // 是否启用此配对
    private UnifiedHttpConfig requestConfig;           // 请求配置
    private UnifiedResponseConfig responseConfig;      // 响应配置
    
    public RuleMatchPair() {
        this.requestConfig = new UnifiedHttpConfig();
        this.responseConfig = new UnifiedResponseConfig();
    }
    
    public RuleMatchPair(int id) {
        this.id = id;
        this.requestConfig = new UnifiedHttpConfig();
        this.responseConfig = new UnifiedResponseConfig();
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    // ✅ 添加getName()作为getLabel()的别名（向后兼容）
    public String getName() {
        return label;
    }
    
    public void setName(String name) {
        this.label = name;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public UnifiedHttpConfig getRequestConfig() {
        return requestConfig;
    }
    
    public void setRequestConfig(UnifiedHttpConfig requestConfig) {
        this.requestConfig = requestConfig;
    }
    
    public UnifiedResponseConfig getResponseConfig() {
        return responseConfig;
    }
    
    public void setResponseConfig(UnifiedResponseConfig responseConfig) {
        this.responseConfig = responseConfig;
    }
    
    /**
     * 获取显示标签
     */
    public String getDisplayLabel() {
        if (label != null && !label.isEmpty()) {
            return "[" + id + "] " + label;
        }
        return "[" + id + "] 配对" + id;
    }
}


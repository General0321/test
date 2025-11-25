package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求-响应配对
 * 一个配对包含：请求匹配条件 + 响应匹配条件
 * 
 * ✨ 支持高级检测功能：
 * - 响应差异对比（通过ResponseComparisonConfig）
 * - 时间验证（通过ResponseComparisonConfig）
 * - 链式变量提取（通过extractVariables，依赖关系自动从变量使用推断）
 */
public class RuleMatchPair implements Serializable {
    private static final long serialVersionUID = 2L;  // ✅ 版本号升级
    
    private int id;                                    // 配对ID（用于逻辑表达式引用）
    private String label;                              // 配对标签（用户可见）
    private UnifiedHttpConfig requestConfig;           // 请求配置
    private UnifiedResponseConfig responseConfig;      // 响应配置
    
    // ========== 高级模式配置（新增）==========
    
    /** 响应对比配置（用于响应差异对比和时间验证） */
    private ResponseComparisonConfig comparisonConfig;
    
    /** 【MULTI_STAGE_CHAINED】从此Pair的响应中提取数据的规则
     *  格式: {"变量名": "正则表达式"}
     *  示例: {"user_id": "\"id\":\"(\\d+)\"", "token": "token=([a-zA-Z0-9]+)"}
     *  提取的变量可在后续Pair中使用：{{VAR:user_id}}
     */
    private Map<String, String> extractVariables = new HashMap<>();
    
    /** Pair权重(1-100) - 用于加权评分模式（未来扩展） */
    private int weight = 50;
    
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
    
    public String getName() {
        return label;
    }
    
    public void setName(String name) {
        this.label = name;
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
    
    // ========== 高级模式相关Getter/Setter ==========
    
    public ResponseComparisonConfig getComparisonConfig() {
        return comparisonConfig;
    }
    
    public void setComparisonConfig(ResponseComparisonConfig comparisonConfig) {
        this.comparisonConfig = comparisonConfig;
    }
    
    public Map<String, String> getExtractVariables() {
        return extractVariables;
    }
    
    public void setExtractVariables(Map<String, String> extractVariables) {
        this.extractVariables = extractVariables;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
}


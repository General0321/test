package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的HTTP响应配置
 * 类似UnifiedHttpConfig，但针对HTTP响应
 */
public class UnifiedResponseConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<ResponseElementConfig> elements = new ArrayList<>();
    private String conditionExpression; // 复杂条件表达式，如 "(1 AND 2) OR 3"
    
    public UnifiedResponseConfig() {
    }
    
    // Getters and Setters
    public List<ResponseElementConfig> getElements() {
        return elements;
    }
    
    public void setElements(List<ResponseElementConfig> elements) {
        this.elements = elements;
    }
    
    public String getConditionExpression() {
        return conditionExpression;
    }
    
    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }
    
    public void addElement(ResponseElementConfig element) {
        this.elements.add(element);
    }
    
    /**
     * HTTP响应元素配置
     */
    public static class ResponseElementConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int id;              // 元素ID，用于表达式引用
        private ElementType type;    // 元素类型
        private MatchConfig matchConfig; // 匹配配置
        
        public ResponseElementConfig() {
            this.matchConfig = new MatchConfig();
        }
        
        public ResponseElementConfig(ElementType type) {
            this.type = type;
            this.matchConfig = new MatchConfig();
        }
        
        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public ElementType getType() { return type; }
        public void setType(ElementType type) { this.type = type; }
        
        public MatchConfig getMatchConfig() { return matchConfig; }
        public void setMatchConfig(MatchConfig matchConfig) { this.matchConfig = matchConfig; }
        
        /**
         * 获取显示标签
         */
        public String getDisplayLabel() {
            switch (type) {
                case STATUS_CODE:
                    return "Status Code";
                case RESPONSE_HEADERS:
                    return "Response Headers";
                case RESPONSE_BODY:
                    return "Response Body";
                case RESPONSE_TIME:
                    return "Response Time";
                case RESPONSE_LENGTH:
                    return "Response Length";
                case COLLABORATOR:
                    return "Collaborator Interaction";
                default:
                    return "Unknown";
            }
        }
    }
    
    /**
     * 匹配配置
     */
    public static class MatchConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private MatchType matchType = MatchType.CONTAINS;  // 匹配类型
        private List<String> values = new ArrayList<>();    // 匹配值（多个，OR关系）
        private boolean caseSensitive = false;              // 是否区分大小写
        
        // 数值比较相关（用于时间、长度等）
        private ComparisonOperator comparisonOperator = ComparisonOperator.GREATER_THAN;
        private long numericValue = 0;
        
        // 范围比较（用于时间范围、长度范围等）
        private long numericValueMin = 0;  // 最小值（用于范围比较）
        private long numericValueMax = 0;  // 最大值（用于范围比较）
        
        // Collaborator相关
        private List<CollaboratorType> collaboratorTypes = new ArrayList<>();
        
        public MatchConfig() {
        }
        
        // Getters and Setters
        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
        
        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }
        
        public ComparisonOperator getComparisonOperator() { return comparisonOperator; }
        public void setComparisonOperator(ComparisonOperator operator) { this.comparisonOperator = operator; }
        
        public long getNumericValue() { return numericValue; }
        public void setNumericValue(long value) { this.numericValue = value; }
        
        public long getNumericValueMin() { return numericValueMin; }
        public void setNumericValueMin(long min) { this.numericValueMin = min; }
        
        public long getNumericValueMax() { return numericValueMax; }
        public void setNumericValueMax(long max) { this.numericValueMax = max; }
        
        public List<CollaboratorType> getCollaboratorTypes() { return collaboratorTypes; }
        public void setCollaboratorTypes(List<CollaboratorType> types) { this.collaboratorTypes = types; }
        
        public void addValue(String value) {
            if (value != null && !value.isEmpty()) {
                this.values.add(value);
            }
        }
    }
    
    /**
     * 响应元素类型
     */
    public enum ElementType {
        STATUS_CODE("状态码"),
        RESPONSE_HEADERS("响应头"),
        RESPONSE_BODY("响应体"),
        RESPONSE_TIME("响应时间"),
        RESPONSE_LENGTH("响应长度"),
        COLLABORATOR("Collaborator外带");
        
        private final String displayName;
        
        ElementType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * 匹配类型
     */
    public enum MatchType {
        EQUALS("完全匹配"),
        CONTAINS("部分匹配"),
        REGEX("正则匹配"),
        STARTS_WITH("开头匹配"),
        ENDS_WITH("结尾匹配"),
        NOT_EQUALS("不等于"),
        NOT_CONTAINS("不包含"),
        NUMERIC_COMPARISON("数值比较");  // 用于时间、长度
        
        private final String displayName;
        
        MatchType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * 数值比较操作符
     */
    public enum ComparisonOperator {
        GREATER_THAN(">", "大于"),
        GREATER_THAN_OR_EQUAL(">=", "大于等于"),
        LESS_THAN("<", "小于"),
        LESS_THAN_OR_EQUAL("<=", "小于等于"),
        EQUAL("=", "等于"),
        NOT_EQUAL("!=", "不等于"),
        BETWEEN("BETWEEN", "在范围内"),
        NOT_BETWEEN("NOT BETWEEN", "不在范围内");
        
        private final String symbol;
        private final String displayName;
        
        ComparisonOperator(String symbol, String displayName) {
            this.symbol = symbol;
            this.displayName = displayName;
        }
        
        public String getSymbol() {
            return symbol;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName + " (" + symbol + ")";
        }
    }
    
    /**
     * Collaborator交互类型
     */
    public enum CollaboratorType {
        DNS("DNS查询"),
        HTTP("HTTP请求"),
        HTTPS("HTTPS请求"),
        SMTP("SMTP连接");
        
        private final String displayName;
        
        CollaboratorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * ✅ 获取显示摘要（用于UI）
     */
    public String getDisplaySummary() {
        if (elements == null || elements.isEmpty()) {
            return "无配置";
        }
        
        StringBuilder summary = new StringBuilder();
        for (ResponseElementConfig element : elements) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(element.getType().toString());
        }
        
        return summary.toString();
    }
}


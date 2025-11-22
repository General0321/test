package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的HTTP配置
 * 将请求条件和注入点统一在HTTP元素上配置
 */
public class UnifiedHttpConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<HttpElementConfig> elements = new ArrayList<>();
    private String conditionExpression; // 复杂条件表达式，如 "(1 AND 2) OR 3"
    
    public UnifiedHttpConfig() {
    }
    
    // Getters and Setters
    public List<HttpElementConfig> getElements() {
        return elements;
    }
    
    public void setElements(List<HttpElementConfig> elements) {
        this.elements = elements;
    }
    
    public String getConditionExpression() {
        return conditionExpression;
    }
    
    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }
    
    public void addElement(HttpElementConfig element) {
        this.elements.add(element);
    }
    
    /**
     * HTTP元素配置
     * 统一配置匹配和注入
     */
    public static class HttpElementConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int id;              // 元素ID，用于表达式引用
        private ElementType type;    // 元素类型
        private String name;         // 元素名称（Parameter名、Header名等）
        private String exampleValue; // 示例值（用于UI显示）
        
        // 匹配配置
        private boolean useForMatch = false;
        private MatchConfig nameMatchConfig;   // 名称匹配配置（对于Parameter、Header等）
        private MatchConfig valueMatchConfig;  // 值匹配配置
        
        // 注入配置
        private boolean useForInjection = false;
        private InjectionTarget injectionTarget; // 注入目标（值、名称、整体）
        private List<String> payloads = new ArrayList<>();
        
        public HttpElementConfig() {
        }
        
        public HttpElementConfig(ElementType type) {
            this.type = type;
            this.nameMatchConfig = new MatchConfig();
            this.valueMatchConfig = new MatchConfig();
        }
        
        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public ElementType getType() { return type; }
        public void setType(ElementType type) { this.type = type; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getExampleValue() { return exampleValue; }
        public void setExampleValue(String exampleValue) { this.exampleValue = exampleValue; }
        
        public boolean isUseForMatch() { return useForMatch; }
        public void setUseForMatch(boolean useForMatch) { this.useForMatch = useForMatch; }
        
        public MatchConfig getNameMatchConfig() { return nameMatchConfig; }
        public void setNameMatchConfig(MatchConfig nameMatchConfig) { this.nameMatchConfig = nameMatchConfig; }
        
        public MatchConfig getValueMatchConfig() { return valueMatchConfig; }
        public void setValueMatchConfig(MatchConfig valueMatchConfig) { this.valueMatchConfig = valueMatchConfig; }
        
        public boolean isUseForInjection() { return useForInjection; }
        public void setUseForInjection(boolean useForInjection) { this.useForInjection = useForInjection; }
        
        public InjectionTarget getInjectionTarget() { return injectionTarget; }
        public void setInjectionTarget(InjectionTarget injectionTarget) { this.injectionTarget = injectionTarget; }
        
        public List<String> getPayloads() { return payloads; }
        public void setPayloads(List<String> payloads) { this.payloads = payloads; }
        
        /**
         * 获取显示标签
         */
        public String getDisplayLabel() {
            switch (type) {
                case METHOD:
                    return "Method";
                case PATH:
                    return "Path";
                case PARAMETER:
                    return "Parameter: " + (name != null ? name : "?");
                case HEADER:
                    return "Header: " + (name != null ? name : "?");
                case COOKIE:
                    return "Cookie: " + (name != null ? name : "?");
                case BODY:
                    return "Body";
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
        
        private MatchType matchType = MatchType.ANY;  // 匹配类型
        private List<String> values = new ArrayList<>(); // 匹配值（多个，OR关系）
        private boolean caseSensitive = false;        // 是否区分大小写
        
        public MatchConfig() {
        }
        
        // Getters and Setters
        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }
        
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
        
        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }
        
        /**
         * 添加匹配值
         */
        public void addValue(String value) {
            if (value != null && !value.isEmpty()) {
                this.values.add(value);
            }
        }
    }
    
    /**
     * HTTP元素类型
     */
    public enum ElementType {
        METHOD("HTTP方法"),
        HOST("主机"),  // ✅ 新增：主机匹配
        PATH("URL路径"),
        PARAMETER("URL参数"),
        HEADER("请求头"),
        COOKIE("Cookie"),
        BODY("请求体");
        
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
        ANY("任意值"),           // 任意值都匹配
        EQUALS("完全匹配"),       // 完全相等
        CONTAINS("部分匹配"),     // 包含
        REGEX("正则匹配"),        // 正则表达式
        STARTS_WITH("开头匹配"),  // 以...开头
        ENDS_WITH("结尾匹配"),    // 以...结尾
        NOT_EQUALS("不等于"),     // 不等于
        NOT_CONTAINS("不包含");   // 不包含
        
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
     * 注入目标
     */
    public enum InjectionTarget {
        VALUE("值"),        // 注入到值
        NAME("名称"),       // 注入到名称
        ENTIRE("整体");     // 注入整体（如整个Body）
        
        private final String displayName;
        
        InjectionTarget(String displayName) {
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
        
        int matchCount = (int) elements.stream().filter(HttpElementConfig::isUseForMatch).count();
        int injectCount = (int) elements.stream().filter(HttpElementConfig::isUseForInjection).count();
        
        StringBuilder summary = new StringBuilder();
        if (matchCount > 0) {
            summary.append("匹配条件×").append(matchCount);
        }
        if (injectCount > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append("注入点×").append(injectCount);
        }
        
        return summary.length() > 0 ? summary.toString() : "无配置";
    }
}


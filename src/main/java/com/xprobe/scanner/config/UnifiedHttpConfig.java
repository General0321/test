package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
     * 转换为传统的Configuration格式（向后兼容）
     */
    public Configuration toConfiguration() {
        Configuration config = new Configuration();
        
        // 转换匹配条件
        List<Configuration.RequestCondition> conditions = new ArrayList<>();
        for (HttpElementConfig element : elements) {
            if (element.isUseForMatch()) {
                conditions.addAll(elementToRequestConditions(element));
            }
        }
        config.setRequestConditions(conditions);
        
        // 如果有复杂表达式，设置ConditionExpression
        if (conditionExpression != null && !conditionExpression.isEmpty()) {
            // TODO: 转换为ConditionExpression对象
        }
        
        // 转换注入点
        List<Configuration.InjectionPoint> injectionPoints = new ArrayList<>();
        List<String> allPayloads = new ArrayList<>();
        
        for (HttpElementConfig element : elements) {
            if (element.isUseForInjection()) {
                Configuration.InjectionPoint point = elementToInjectionPoint(element);
                if (point != null) {
                    injectionPoints.add(point);
                    allPayloads.addAll(element.getPayloads());
                }
            }
        }
        
        config.setInjectionPoints(injectionPoints);
        config.setPayloads(allPayloads.stream().distinct().collect(Collectors.toList()));
        
        return config;
    }
    
    /**
     * 将HTTP元素转换为RequestCondition列表
     */
    private List<Configuration.RequestCondition> elementToRequestConditions(HttpElementConfig element) {
        List<Configuration.RequestCondition> conditions = new ArrayList<>();
        
        switch (element.getType()) {
            case METHOD:
                conditions.add(createCondition("HTTP Method", element.getValueMatchConfig()));
                break;
                
            case HOST:
                conditions.add(createCondition("Host", element.getValueMatchConfig()));
                break;
                
            case PATH:
                conditions.add(createCondition("URL Path", element.getValueMatchConfig()));
                break;
                
            case PARAMETER:
                // 参数名条件
                if (element.getNameMatchConfig().getMatchType() != MatchType.ANY) {
                    conditions.add(createCondition("Parameter Name", element.getNameMatchConfig()));
                }
                // 参数值条件（如果需要）
                if (element.getValueMatchConfig().getMatchType() != MatchType.ANY) {
                    // TODO: 需要扩展Configuration.RequestCondition支持参数值匹配
                }
                break;
                
            case HEADER:
                // Header名条件
                Configuration.RequestCondition headerCondition = createCondition("Request Header", element.getValueMatchConfig());
                headerCondition.setValue(element.getName() + ":" + String.join("|", element.getValueMatchConfig().getValues()));
                conditions.add(headerCondition);
                break;
                
            case COOKIE:
                // Cookie条件
                Configuration.RequestCondition cookieCondition = new Configuration.RequestCondition();
                cookieCondition.setConditionType("Parameter Exists");
                cookieCondition.setValue(element.getName());
                conditions.add(cookieCondition);
                break;
                
            case BODY:
                conditions.add(createCondition("Body Contains", element.getValueMatchConfig()));
                break;
        }
        
        return conditions;
    }
    
    /**
     * 创建RequestCondition
     */
    private Configuration.RequestCondition createCondition(String type, MatchConfig matchConfig) {
        Configuration.RequestCondition condition = new Configuration.RequestCondition();
        condition.setConditionType(type);
        condition.setMatchType(matchTypeToString(matchConfig.getMatchType()));
        
        // 设置多值
        if (matchConfig.getValues().size() > 1) {
            condition.setMultiLine(true);
            condition.setValues(matchConfig.getValues());
        } else if (!matchConfig.getValues().isEmpty()) {
            condition.setValue(matchConfig.getValues().get(0));
        }
        
        return condition;
    }
    
    /**
     * 匹配类型转字符串
     */
    private String matchTypeToString(MatchType type) {
        switch (type) {
            case EQUALS: return "Equals";
            case CONTAINS: return "Contains";
            case REGEX: return "Regex";
            case STARTS_WITH: return "Starts With";
            case ENDS_WITH: return "Ends With";
            default: return "Contains";
        }
    }
    
    /**
     * 将HTTP元素转换为InjectionPoint
     */
    private Configuration.InjectionPoint elementToInjectionPoint(HttpElementConfig element) {
        Configuration.InjectionPoint point = new Configuration.InjectionPoint();
        
        switch (element.getType()) {
            case PARAMETER:
                point.setPointType("Parameter Value");
                point.setTargetName(element.getName());
                break;
                
            case HEADER:
                point.setPointType("Request Header Value");
                point.setTargetName(element.getName());
                break;
                
            case COOKIE:
                point.setPointType("Cookie Value");
                point.setTargetName(element.getName());
                break;
                
            case PATH:
                point.setPointType("URL Path");
                break;
                
            case BODY:
                point.setPointType("Request Body");
                break;
                
            case METHOD:
                // Method通常不注入
                return null;
                
            default:
                return null;
        }
        
        return point;
    }
    
    /**
     * 从传统Configuration转换（向后兼容）
     */
    public static UnifiedHttpConfig fromConfiguration(Configuration config) {
        UnifiedHttpConfig unifiedConfig = new UnifiedHttpConfig();
        int elementId = 1;
        
        // 转换RequestConditions
        if (config.getRequestConditions() != null) {
            for (Configuration.RequestCondition condition : config.getRequestConditions()) {
                HttpElementConfig element = conditionToElement(condition, elementId++);
                if (element != null) {
                    unifiedConfig.addElement(element);
                }
            }
        }
        
        // 转换InjectionPoints
        if (config.getInjectionPoints() != null) {
            for (Configuration.InjectionPoint point : config.getInjectionPoints()) {
                HttpElementConfig element = injectionPointToElement(point, config.getPayloads(), elementId++);
                if (element != null) {
                    unifiedConfig.addElement(element);
                }
            }
        }
        
        return unifiedConfig;
    }
    
    /**
     * RequestCondition转Element
     */
    private static HttpElementConfig conditionToElement(Configuration.RequestCondition condition, int id) {
        HttpElementConfig element = new HttpElementConfig();
        element.setId(id);
        element.setUseForMatch(true);
        
        String conditionType = condition.getConditionType();
        if (conditionType == null) return null;
        
        switch (conditionType) {
            case "HTTP Method":
                element.setType(ElementType.METHOD);
                element.getValueMatchConfig().setValues(condition.getAllValues());
                break;
                
            case "URL Path":
                element.setType(ElementType.PATH);
                element.getValueMatchConfig().setValues(condition.getAllValues());
                break;
                
            case "Parameter Name":
                element.setType(ElementType.PARAMETER);
                element.getNameMatchConfig().setValues(condition.getAllValues());
                break;
                
            case "Request Header":
                element.setType(ElementType.HEADER);
                // 解析header名和值
                String headerValue = condition.getValue();
                if (headerValue != null && headerValue.contains(":")) {
                    String[] parts = headerValue.split(":", 2);
                    element.setName(parts[0].trim());
                    element.getValueMatchConfig().addValue(parts[1].trim());
                }
                break;
                
            case "Body Contains":
                element.setType(ElementType.BODY);
                element.getValueMatchConfig().setValues(condition.getAllValues());
                break;
                
            default:
                return null;
        }
        
        // 设置匹配类型
        String matchType = condition.getMatchType();
        if (matchType != null) {
            element.getValueMatchConfig().setMatchType(stringToMatchType(matchType));
        }
        
        return element;
    }
    
    /**
     * InjectionPoint转Element
     */
    private static HttpElementConfig injectionPointToElement(Configuration.InjectionPoint point, 
                                                            List<String> payloads, int id) {
        HttpElementConfig element = new HttpElementConfig();
        element.setId(id);
        element.setUseForInjection(true);
        element.setPayloads(payloads != null ? new ArrayList<>(payloads) : new ArrayList<>());
        
        String pointType = point.getPointType();
        if (pointType == null) return null;
        
        switch (pointType) {
            case "Parameter Value":
                element.setType(ElementType.PARAMETER);
                element.setName(point.getTargetName());
                element.setInjectionTarget(InjectionTarget.VALUE);
                break;
                
            case "Request Header Value":
                element.setType(ElementType.HEADER);
                element.setName(point.getTargetName());
                element.setInjectionTarget(InjectionTarget.VALUE);
                break;
                
            case "Cookie Value":
                element.setType(ElementType.COOKIE);
                element.setName(point.getTargetName());
                element.setInjectionTarget(InjectionTarget.VALUE);
                break;
                
            case "URL Path":
                element.setType(ElementType.PATH);
                element.setInjectionTarget(InjectionTarget.ENTIRE);
                break;
                
            case "Request Body":
                element.setType(ElementType.BODY);
                element.setInjectionTarget(InjectionTarget.ENTIRE);
                break;
                
            default:
                return null;
        }
        
        return element;
    }
    
    /**
     * 字符串转MatchType
     */
    private static MatchType stringToMatchType(String type) {
        if (type == null) return MatchType.CONTAINS;
        
        switch (type) {
            case "Equals": return MatchType.EQUALS;
            case "Contains": return MatchType.CONTAINS;
            case "Regex": return MatchType.REGEX;
            case "Starts With": return MatchType.STARTS_WITH;
            case "Ends With": return MatchType.ENDS_WITH;
            default: return MatchType.CONTAINS;
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


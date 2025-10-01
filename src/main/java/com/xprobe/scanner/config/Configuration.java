package com.xprobe.scanner.config;  // 包名

import java.io.Serializable;  // 导入 Serializable 接口
import java.util.ArrayList;
import java.util.List;        // 导入 List 接口
import java.util.UUID;

public class Configuration implements Serializable {  // 定义 Configuration 类，实现 Serializable 接口
    private static final long serialVersionUID = 2L;  // 序列化版本ID (已更新)

    // ========== 基础信息 ==========
    private String ruleId;                // 规则唯一标识（UUID）
    private String customLabel;           // 自定义标签（用户可见的规则名称）
    private String description;           // 规则描述
    private boolean enabled;              // 是否启用
    
    // ========== 旧字段（向后兼容）==========
    private List<String> parameterNames;  // 参数名列表（兼容模式）
    private String parameterNameType;     // 参数名类型（兼容模式）
    private List<String> parameterValues; // 参数值列表（即payload列表）
    private List<MatchRule> matchRules;   // 匹配规则列表
    
    // ========== 新字段（灵活注入系统）==========
    private List<RequestCondition> requestConditions;  // 请求匹配条件
    private List<InjectionPoint> injectionPoints;     // 注入点列表
    private DeduplicationGranularity deduplicationGranularity;  // 去重颗粒度
    
    // ========== 配对架构（最新）==========
    private List<RuleMatchPair> pairs;                // 请求-响应配对列表
    private String pairExpression;                    // 配对间逻辑表达式


    // 默认构造函数
    public Configuration() {
        this.ruleId = UUID.randomUUID().toString();
        this.requestConditions = new ArrayList<>();
        this.injectionPoints = new ArrayList<>();
        this.parameterValues = new ArrayList<>();
        this.matchRules = new ArrayList<>();
        this.pairs = new ArrayList<>();
        this.enabled = true;
    }
    
    // 构造函数，初始化所有属性（兼容旧代码）
    public Configuration(List<String> parameterNames, String parameterNameType,
                         List<String> parameterValues, List<MatchRule> matchRules,
                         String customLabel, boolean enabled) {
        this.ruleId = UUID.randomUUID().toString();    // 自动生成规则ID
        this.parameterNames = parameterNames;          // 初始化参数名列表
        this.parameterNameType = parameterNameType;    // 初始化参数名类型
        this.parameterValues = parameterValues;        // 初始化参数值列表
        this.matchRules = matchRules;                  // 初始化匹配规则列表
        this.customLabel = customLabel;                // 初始化自定义标签
        this.enabled = enabled;
        
        // 初始化新字段
        this.requestConditions = new ArrayList<>();
        this.injectionPoints = new ArrayList<>();
        
        // 自动从旧配置迁移到新配置
        migrateToNewFormat();
    }
    
    /**
     * 将旧格式配置迁移到新格式
     */
    private void migrateToNewFormat() {
        // 如果已有注入点配置，不进行迁移
        if (injectionPoints != null && !injectionPoints.isEmpty()) {
            return;
        }
        
        // 从旧的parameterNames迁移到注入点
        if (parameterNames != null && !parameterNames.isEmpty()) {
            InjectionPoint point = new InjectionPoint();
            point.setPointType("Parameter Value");
            point.setTargetName(String.join(",", parameterNames));
            point.setInjectionStrategy("Replace");
            
            if (injectionPoints == null) {
                injectionPoints = new ArrayList<>();
            }
            injectionPoints.add(point);
        }
    }

    // ========== 规则ID相关方法 ==========
    
    /**
     * 获取规则ID
     */
    public String getRuleId() {
        if (ruleId == null || ruleId.isEmpty()) {
            ruleId = UUID.randomUUID().toString();
        }
        return ruleId;
    }
    
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }
    
    /**
     * 生成新的规则ID（用于规则复制）
     */
    public void generateNewRuleId() {
        this.ruleId = UUID.randomUUID().toString();
    }
    
    // ========== 请求条件相关方法 ==========
    
    public List<RequestCondition> getRequestConditions() {
        return requestConditions;
    }
    
    public void setRequestConditions(List<RequestCondition> requestConditions) {
        this.requestConditions = requestConditions;
    }
    
    // ========== 注入点相关方法 ==========
    
    public List<InjectionPoint> getInjectionPoints() {
        return injectionPoints;
    }
    
    public void setInjectionPoints(List<InjectionPoint> injectionPoints) {
        this.injectionPoints = injectionPoints;
    }
    
    // ========== Payload相关方法 ==========
    
    /**
     * 获取payload列表（新名称，语义更清晰）
     */
    public List<String> getPayloads() {
        return parameterValues;
    }
    
    public void setPayloads(List<String> payloads) {
        this.parameterValues = payloads;
    }
    
    // ========== 去重颗粒度相关方法 ==========
    
    public DeduplicationGranularity getDeduplicationGranularity() {
        if (deduplicationGranularity == null) {
            return DeduplicationGranularity.AUTO;  // 默认自动检测
        }
        return deduplicationGranularity;
    }
    
    public void setDeduplicationGranularity(DeduplicationGranularity granularity) {
        this.deduplicationGranularity = granularity;
    }
    
    // ========== 旧方法（向后兼容）==========
    
    public List<String> getParameterNames() {  // 获取参数名列表
        return parameterNames;
    }

    public void setParameterNames(List<String> parameterNames) {  // 设置参数名列表
        this.parameterNames = parameterNames;
    }

    public String getParameterNameType() {  // 获取参数名类型
        return parameterNameType;
    }

    public void setParameterNameType(String parameterNameType) {  // 设置参数名类型
        this.parameterNameType = parameterNameType;
    }

    public List<String> getParameterValues() {  // 获取参数值列表
        return parameterValues;
    }

    public void setParameterValues(List<String> parameterValues) {  // 设置参数值列表
        this.parameterValues = parameterValues;
    }

    public List<MatchRule> getMatchRules() {  // 获取匹配规则列表
        return matchRules;
    }

    // 获取规则字符串列表
    public List<String> getAllMatchRuleStrings() {
        List<String> ruleStrings = new ArrayList<>();
        if (matchRules != null) {
            for (MatchRule matchRule : matchRules) {
                if (matchRule.getRule() != null) {
                    ruleStrings.add(matchRule.getRule());
                }
            }
        }
        return ruleStrings;
    }


    public void setMatchRules(List<MatchRule> matchRules) {  // 设置匹配规则列表
        this.matchRules = matchRules;
    }

    public String getCustomLabel() {  // 获取自定义标签
        return customLabel;
    }

    public void setCustomLabel(String customLabel) {  // 设置自定义标签
        this.customLabel = customLabel;
    }

    // 获取第一个匹配规则
    public String getMatchRule() {
        if (matchRules != null && !matchRules.isEmpty()) {
            return matchRules.get(0).getRule();  // 假设获取第一个匹配规则
        }
        return null;
    }

    // 获取第一个匹配类型
    public String getMatchType() {
        if (matchRules != null && !matchRules.isEmpty()) {
            return matchRules.get(0).getMatchType();  // 假设获取第一个匹配类型
        }
        return null;
    }

    public boolean isEnabled() {  // 获取启用状态
        return enabled;
    }

    public void setEnabled(boolean enabled) {  // 设置启用状态
        this.enabled = enabled;
    }

    public String getDescription() {  // 获取描述
        return description;
    }

    public void setDescription(String description) {  // 设置描述
        this.description = description;
    }

    // ========== 枚举定义 ==========
    
    /**
     * 注入模式枚举
     * 决定如何对匹配的多个参数进行注入
     */
    public enum InjectionMode {
        /**
         * 批量模式 - 所有匹配的参数同时注入同一个payload
         * 
         * 示例：参数名匹配 [id, uid]，payload为 [p1, p2]
         * 请求1: ?id=p1&uid=p1
         * 请求2: ?id=p2&uid=p2
         * 
         * 优点：速度快，请求数少（payload数量）
         * 缺点：无法单独定位哪个参数有漏洞
         */
        BATCH("批量模式（快速）", "所有匹配参数同时注入相同payload"),
        
        /**
         * 逐个模式 - 每次只对一个匹配的参数注入payload
         * 
         * 示例：参数名匹配 [id, uid]，payload为 [p1, p2]
         * 请求1: ?id=p1&uid=原值
         * 请求2: ?id=p2&uid=原值
         * 请求3: ?id=原值&uid=p1
         * 请求4: ?id=原值&uid=p2
         * 
         * 优点：精确定位漏洞参数
         * 缺点：速度慢，请求数多（参数数 × payload数量）
         */
        INDIVIDUAL("逐个模式（精确）", "每次只注入一个参数，其他保持原值");
        
        private final String displayName;
        private final String description;
        
        InjectionMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * 去重颗粒度枚举
     * 决定以什么维度进行去重检查
     */
    public enum DeduplicationGranularity {
        /**
         * 自动检测 - 根据注入点类型自动选择颗粒度（推荐）
         */
        AUTO("自动检测", "根据规则类型智能选择"),
        
        // ========== 细粒度选项 ==========
        
        /**
         * 基于全局 - 所有请求只测试一次
         * Key: ruleId
         * 示例: 某个规则全局只测试一次
         */
        GLOBAL("全局", "整个规则只测试一次"),
        
        /**
         * 基于主机 - 每个主机只测试一次
         * Key: ruleId + host
         * 示例: example.com只测试一次，api.example.com再测试一次
         */
        HOST("主机级", "每个主机只测试一次"),
        
        /**
         * 基于路径 - 每个路径只测试一次
         * Key: ruleId + host + path
         * 示例: /api/user只测试一次，/api/post再测试一次
         */
        PATH("路径级", "每个路径只测试一次"),
        
        /**
         * 基于请求 - 每个完整请求只测试一次（包含路径+Content-Type）
         * Key: ruleId + method + host + path + contentType
         * 示例: GET /api/user (JSON)只测试一次
         */
        REQUEST("请求级", "每个完整请求只测试一次"),
        
        /**
         * 基于参数名 - 每个参数名只测试一次（全局）
         * Key: ruleId + parameterName
         * 示例: 参数id在任何请求中都只测试一次
         */
        PARAMETER_NAME_GLOBAL("参数名(全局)", "相同参数名只测试一次"),
        
        /**
         * 基于参数名（路径级）- 每个路径下的参数名只测试一次
         * Key: ruleId + host + path + parameterName
         * 示例: /api/user下的id只测试一次，/api/post下的id再测试一次
         */
        PARAMETER_NAME_PER_PATH("参数名(路径)", "每个路径下的参数名分别测试"),
        
        /**
         * 基于参数（请求级）- 每个请求中的参数分别测试
         * Key: ruleId + method + host + path + contentType + parameterName
         * 示例: GET /api/user?id=1 测试id，GET /api/user?name=x 测试name
         */
        PARAMETER("参数级", "每个请求中的参数分别测试"),
        
        /**
         * 基于注入点 - 每个注入点分别测试
         * Key: ruleId + method + host + path + contentType + injectionPointHash
         * 示例: 多个注入点的规则，每个注入点都测试
         */
        INJECTION_POINT("注入点级", "每个注入点分别测试"),
        
        /**
         * 无去重 - 每次都测试
         * Key: ruleId + timestamp + random
         * 示例: Fuzzing模式，每次都测试
         */
        NONE("无去重", "每次都测试（Fuzzing模式）");
        
        private final String displayName;
        private final String description;
        
        DeduplicationGranularity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return displayName + " - " + description;
        }
    }

    // ========== 内部类定义 ==========

    /**
     * 请求匹配条件
     * 决定哪些请求会被此规则测试
     */
    public static class RequestCondition implements Serializable {
        private static final long serialVersionUID = 2L;  // 更新版本号
        
        private String conditionType;     // 条件类型
        private String matchType;         // 匹配类型
        private String value;             // 匹配值（单行，向后兼容）
        private List<String> values;      // 匹配值（多行，新增）
        private String operator;          // 逻辑操作符（AND/OR）
        private boolean multiLine = false; // 是否启用多行模式
        
        /**
         * 条件类型:
         * - Content-Type      (Content-Type匹配)
         * - URL Path          (URL路径匹配)
         * - HTTP Method       (HTTP方法匹配)
         * - Request Header    (请求头匹配)
         * - Parameter Name    (参数名匹配)
         * - Parameter Exists  (参数存在性检查)
         * - Body Contains     (请求体包含)
         */
        
        /**
         * 匹配类型:
         * - Equals            (等于)
         * - Contains          (包含)
         * - Starts With       (开始于)
         * - Ends With         (结束于)
         * - Regex Match       (正则匹配)
         * - Not Equals        (不等于)
         * - Not Contains      (不包含)
         */
        
        public RequestCondition() {
        }
        
        public RequestCondition(String conditionType, String matchType, String value, String operator) {
            this.conditionType = conditionType;
            this.matchType = matchType;
            this.value = value;
            this.operator = operator;
        }
        
        // Getters and Setters
        public String getConditionType() { return conditionType; }
        public void setConditionType(String conditionType) { this.conditionType = conditionType; }
        
        public String getMatchType() { return matchType; }
        public void setMatchType(String matchType) { this.matchType = matchType; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
        
        public boolean isMultiLine() { return multiLine; }
        public void setMultiLine(boolean multiLine) { this.multiLine = multiLine; }
        
        /**
         * 获取所有匹配值（兼容单行和多行）
         */
        public List<String> getAllValues() {
            if (multiLine && values != null && !values.isEmpty()) {
                return values;
            }
            if (value != null && !value.isEmpty()) {
                return java.util.Arrays.asList(value);
            }
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 注入点（简化版）
     * 定义在HTTP请求的哪个位置插入payload
     * 
     * 注意：注入策略已移除，现在直接在Payload中使用变量表达：
     * - {{ORIGINAL}} - 保留原值并追加/前置
     * - 不使用{{ORIGINAL}} - 完全替换
     */
    public static class InjectionPoint implements Serializable {
        private static final long serialVersionUID = 2L;  // 更新版本号
        
        private String pointType;        // 注入点类型
        private String targetName;       // 目标名称（参数名/Header名等）
        private String description;      // 描述（可选）
        
        // ===== 废弃字段（向后兼容）=====
        @Deprecated
        private String injectionStrategy;// 注入策略（已废弃，使用payload变量替代）
        @Deprecated
        private String marker;           // 标记符（已废弃）
        
        /**
         * 注入点类型:
         * - Parameter Value        (参数值)
         * - Parameter Name         (参数名)
         * - URL Path               (URL路径)
         * - URL Path Segment       (URL路径段)
         * - Request Header Value   (请求头值)
         * - Request Header Name    (请求头名)
         * - Request Body           (整个请求体)
         * - Request Body Part      (请求体部分)
         * - Cookie Value           (Cookie值)
         * - Query String           (查询字符串)
         */
        
        /**
         * 新的Payload变量系统（替代注入策略）:
         * - {{ORIGINAL}}              - 原始值
         * - {{ORIGINAL_URL_ENCODED}}  - URL编码的原始值
         * - {{ORIGINAL_BASE64}}       - Base64编码的原始值
         * 
         * 示例：
         * 1. 追加: {{ORIGINAL}}' OR '1'='1--
         * 2. 前置: admin{{ORIGINAL}}
         * 3. 替换: ' OR '1'='1--
         * 4. 组合: {{ORIGINAL}}_{{RANDOM_STRING}}
         */
        
        public InjectionPoint() {
        }
        
        public InjectionPoint(String pointType, String targetName) {
            this.pointType = pointType;
            this.targetName = targetName;
        }
        
        @Deprecated
        public InjectionPoint(String pointType, String targetName, String injectionStrategy) {
            this.pointType = pointType;
            this.targetName = targetName;
            this.injectionStrategy = injectionStrategy;
        }
        
        // Getters and Setters
        public String getPointType() { return pointType; }
        public void setPointType(String pointType) { this.pointType = pointType; }
        
        public String getTargetName() { return targetName; }
        public void setTargetName(String targetName) { this.targetName = targetName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        @Deprecated
        public String getInjectionStrategy() { return injectionStrategy; }
        @Deprecated
        public void setInjectionStrategy(String injectionStrategy) { this.injectionStrategy = injectionStrategy; }
        
        @Deprecated
        public String getMarker() { return marker; }
        @Deprecated
        public void setMarker(String marker) { this.marker = marker; }
        
        /**
         * 检查是否需要从旧格式迁移
         */
        public boolean needsMigration() {
            return injectionStrategy != null && !injectionStrategy.isEmpty();
        }
    }

    // 内部类 MatchRule，用于存储匹配规则信息
    public static class MatchRule implements Serializable {  // 实现 Serializable 接口
        private static final long serialVersionUID = 1L;  // 序列化版本ID
        private boolean enabled;

        private String matchType;  // 匹配类型
        private String location;   // 匹配位置
        private String rule;       // 匹配规则
        private String operator;   // 逻辑操作符

        // 无参构造函数
        public MatchRule() {
        }

        // 构造函数，初始化所有属性
        public MatchRule(String location, String rule, String operator, String matchType) {
            this.location = location;  // 初始化匹配位置
            this.rule = rule;          // 初始化匹配规则
            this.operator = operator;  // 初始化逻辑操作符
            this.matchType = matchType;// 初始化匹配类型
        }

        // Getter 和 Setter 方法
        public String getLocation() {  // 获取匹配位置
            return location;
        }

        public void setLocation(String location) {  // 设置匹配位置
            this.location = location;
        }

        public String getRule() {  // 获取匹配规则
            return rule;
        }

        public void setRule(String rule) {  // 设置匹配规则
            this.rule = rule;
        }

        public String getOperator() {  // 获取逻辑操作符
            return operator;
        }

        public void setOperator(String operator) {  // 设置逻辑操作符
            this.operator = operator;
        }

        public String getMatchType() {  // 获取匹配类型
            return matchType;
        }

        public void setMatchType(String matchType) {  // 设置匹配类型
            this.matchType = matchType;
        }

        public boolean isEnabled() {  // 获取启用状态
            return enabled;
        }

        public void setEnabled(boolean enabled) {  // 设置启用状态
            this.enabled = enabled;
        }

    }
    
    // ========== 配对架构的Getter和Setter ==========
    
    public List<RuleMatchPair> getPairs() {
        return pairs;
    }
    
    public void setPairs(List<RuleMatchPair> pairs) {
        this.pairs = pairs;
    }
    
    public String getPairExpression() {
        return pairExpression;
    }
    
    public void setPairExpression(String pairExpression) {
        this.pairExpression = pairExpression;
    }
}

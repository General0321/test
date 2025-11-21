package com.xprobe.scanner.config;

/**
 * Pair检测模式枚举
 * 定义多对匹配时的高级检测模式
 * 
 * 基于HackerOne真实漏洞案例提取的6大核心检测模式
 */
public enum PairMode {
    /**
     * 标准模式（默认）- 单个请求响应验证
     * 适用场景: 基础检测、单一条件验证
     * 逻辑: 请求匹配 + 响应匹配 = 成功
     */
    STANDARD("标准模式", "单个请求响应验证"),
    
    /**
     * 时间延迟验证模式
     * 场景: SQL时间盲注、命令注入延迟
     * 逻辑: 检查多个请求的响应时间呈线性增长关系
     * 案例: #1024984 (SQL sleep), #1034625
     * 要求: 需要多个Pair，每个设置不同的timeBaseline
     */
    TIME_BASED_VERIFICATION("时间验证", "多请求时间对比验证"),
    
    /**
     * 布尔逻辑对比模式
     * 场景: SQL布尔盲注、逻辑漏洞
     * 逻辑: TRUE/FALSE payload响应存在显著差异
     * 案例: #1102591, #1107536
     * 要求: 需要至少2个Pair (TRUE和FALSE)
     */
    BOOLEAN_COMPARISON("布尔对比", "TRUE/FALSE响应差异对比"),
    
    /**
     * Payload反射确认模式
     * 场景: XSS、模板注入、SSTI
     * 逻辑: 注入的唯一标记在响应中可见
     * 案例: #1003433, #1040533
     * 要求: payload中包含唯一标记（使用变量）
     */
    REFLECTION_CONFIRMATION("反射确认", "Payload反射验证"),
    
    /**
     * 多阶段链式验证模式
     * 场景: IDOR链、会话固定、状态依赖漏洞
     * 逻辑: 按顺序执行多个步骤，提取中间结果传递给下一步
     * 案例: IDOR账户接管、订单篡改
     * 要求: 使用extractVariables和dependsOn配置依赖关系
     */
    MULTI_STAGE_CHAINED("链式验证", "多阶段数据提取传递");
    
    private final String displayName;
    private final String description;
    
    PairMode(String displayName, String description) {
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


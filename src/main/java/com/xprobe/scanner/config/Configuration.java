package com.xprobe.scanner.config;  // 包名

import java.io.Serializable;  // 导入 Serializable 接口
import java.util.ArrayList;
import java.util.List;        // 导入 List 接口

public class Configuration implements Serializable {  // 定义 Configuration 类，实现 Serializable 接口
    private static final long serialVersionUID = 1L;  // 序列化版本ID

    private List<String> parameterNames;  // 参数名列表
    private String parameterNameType;     // 参数名类型
    private List<String> parameterValues; // 参数值列表
    private List<MatchRule> matchRules;   // 匹配规则列表
    private String customLabel;           // 自定义标签
    private boolean enabled;


    // 构造函数，初始化所有属性
    public Configuration(List<String> parameterNames, String parameterNameType,
                         List<String> parameterValues, List<MatchRule> matchRules,
                         String customLabel, boolean enabled) {
        this.parameterNames = parameterNames;          // 初始化参数名列表
        this.parameterNameType = parameterNameType;    // 初始化参数名类型
        this.parameterValues = parameterValues;        // 初始化参数值列表
        this.matchRules = matchRules;                  // 初始化匹配规则列表
        this.customLabel = customLabel;                // 初始化自定义标签
        this.enabled = enabled;
    }

    // Getter 和 Setter 方法
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


    // 内部类 MatchRule，用于存储匹配规则信息
    public static class MatchRule implements Serializable {  // 实现 Serializable 接口
        private static final long serialVersionUID = 1L;  // 序列化版本ID
        private boolean enabled;

        private String matchType;  // 匹配类型
        private String location;   // 匹配位置
        private String rule;       // 匹配规则
        private String operator;   // 逻辑操作符


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

    }
}

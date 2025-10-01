package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 请求条件评估器
 * 判断请求是否满足配置的条件
 * 支持简单链式逻辑和复杂表达式
 */
public class RequestConditionEvaluator {
    
    /**
     * 评估请求是否匹配条件表达式
     * 优先使用表达式，如果没有表达式则使用简单链式逻辑（向后兼容）
     */
    public static boolean evaluate(HttpRequest request, 
                                   List<Configuration.RequestCondition> conditions,
                                   ConditionExpression expression) {
        // 优先使用表达式
        if (expression != null) {
            return expression.evaluate(request);
        }
        
        // 向后兼容：使用简单链式逻辑
        return evaluateSimple(request, conditions);
    }
    
    /**
     * 评估请求是否匹配所有条件（简单链式逻辑，向后兼容）
     */
    public static boolean evaluate(HttpRequest request, 
                                   List<Configuration.RequestCondition> conditions) {
        return evaluateSimple(request, conditions);
    }
    
    /**
     * 简单链式逻辑评估
     */
    private static boolean evaluateSimple(HttpRequest request,
                                          List<Configuration.RequestCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;  // 没有条件，默认匹配所有请求
        }
        
        boolean result = evaluateCondition(request, conditions.get(0));
        
        for (int i = 1; i < conditions.size(); i++) {
            Configuration.RequestCondition condition = conditions.get(i);
            boolean conditionResult = evaluateCondition(request, condition);
            
            String operator = condition.getOperator();
            if ("AND".equalsIgnoreCase(operator)) {
                result = result && conditionResult;
            } else if ("OR".equalsIgnoreCase(operator)) {
                result = result || conditionResult;
            }
        }
        
        return result;
    }
    
    /**
     * 评估单个条件（公开方法，供ConditionExpression调用）
     * 支持多行匹配（任意一行匹配即可）
     */
    public static boolean evaluateCondition(HttpRequest request,
                                            Configuration.RequestCondition condition) {
        if (condition == null) {
            return true;
        }
        
        String conditionType = condition.getConditionType();
        String matchType = condition.getMatchType();
        
        // 获取所有匹配值（支持多行）
        java.util.List<String> values = condition.getAllValues();
        if (values.isEmpty()) {
            return true;
        }
        
        if (conditionType == null || matchType == null) {
            return true;
        }
        
        // 多行模式：任意一行匹配即可（OR逻辑）
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            
            boolean result = evaluateSingleValue(request, conditionType, matchType, value.trim());
            if (result) {
                return true;  // 任意一个值匹配就返回true
            }
        }
        
        return false;  // 所有值都不匹配
    }
    
    /**
     * 评估单个值（内部方法）
     */
    private static boolean evaluateSingleValue(HttpRequest request,
                                               String conditionType,
                                               String matchType,
                                               String value) {
        switch (conditionType) {
            case "Content-Type":
                return evaluateContentType(request, matchType, value);
                
            case "URL Path":
                return evaluateUrlPath(request, matchType, value);
                
            case "HTTP Method":
                return evaluateHttpMethod(request, matchType, value);
                
            case "Request Header":
                return evaluateRequestHeader(request, matchType, value);
                
            case "Parameter Name":
                return evaluateParameterName(request, matchType, value);
                
            case "Parameter Exists":
                return evaluateParameterExists(request, value);
                
            case "Body Contains":
                return evaluateBodyContains(request, matchType, value);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估Content-Type
     */
    private static boolean evaluateContentType(HttpRequest request, 
                                               String matchType, 
                                               String value) {
        String contentType = request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .findFirst()
            .map(h -> h.value())
            .orElse("");
        if (contentType == null) {
            contentType = "";
        }
        
        return matchString(contentType, matchType, value);
    }
    
    /**
     * 评估URL路径
     */
    private static boolean evaluateUrlPath(HttpRequest request, 
                                          String matchType, 
                                          String value) {
        String path = request.path();
        if (path == null) {
            path = "";
        }
        return matchString(path, matchType, value);
    }
    
    /**
     * 评估HTTP方法
     */
    private static boolean evaluateHttpMethod(HttpRequest request, 
                                             String matchType, 
                                             String value) {
        String method = request.method();
        if (method == null) {
            method = "";
        }
        return matchString(method, matchType, value);
    }
    
    /**
     * 评估请求头
     */
    private static boolean evaluateRequestHeader(HttpRequest request, 
                                                String matchType, 
                                                String value) {
        // value格式: "Header-Name: Header-Value"
        if (!value.contains(":")) {
            return false;
        }
        
        String[] parts = value.split(":", 2);
        String headerName = parts[0].trim();
        String expectedValue = parts[1].trim();
        String actualValue = request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase(headerName))
            .findFirst()
            .map(h -> h.value())
            .orElse(null);
        
        if (actualValue == null) {
            return false;
        }
        
        return matchString(actualValue, matchType, expectedValue);
    }
    
    /**
     * 评估参数名
     */
    private static boolean evaluateParameterName(HttpRequest request, 
                                                String matchType, 
                                                String value) {
        for (var param : request.parameters()) {
            if (matchString(param.name(), matchType, value)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 评估参数是否存在
     */
    private static boolean evaluateParameterExists(HttpRequest request, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return false;
        }
        // 遍历所有参数检查是否存在
        for (var param : request.parameters()) {
            if (param.name().equals(paramName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 评估请求体包含
     */
    private static boolean evaluateBodyContains(HttpRequest request, 
                                               String matchType, 
                                               String value) {
        String body = request.bodyToString();
        if (body == null) {
            body = "";
        }
        return matchString(body, matchType, value);
    }
    
    /**
     * 字符串匹配工具
     */
    private static boolean matchString(String actual, String matchType, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        
        switch (matchType) {
            case "Equals":
                return actual.equals(expected);
                
            case "Contains":
                return actual.contains(expected);
                
            case "Starts With":
                return actual.startsWith(expected);
                
            case "Ends With":
                return actual.endsWith(expected);
                
            case "Regex Match":
                try {
                    Pattern pattern = Pattern.compile(expected);
                    return pattern.matcher(actual).find();
                } catch (Exception e) {
                    return false;
                }
                
            case "Not Equals":
                return !actual.equals(expected);
                
            case "Not Contains":
                return !actual.contains(expected);
                
            default:
                return false;
        }
    }
}


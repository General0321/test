package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedHttpConfig.*;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 统一HTTP评估器
 * 评估HTTP请求是否匹配配置的条件
 */
public class UnifiedHttpEvaluator {
    
    /**
     * 评估HTTP请求是否匹配配置
     * 
     * @param request HTTP请求
     * @param config 统一配置
     * @return 是否匹配
     */
    public static boolean evaluate(HttpRequest request, UnifiedHttpConfig config) {
        if (config == null || config.getElements() == null) {
            return true;
        }
        
        // 如果有复杂表达式，使用表达式评估
        String expression = config.getConditionExpression();
        if (expression != null && !expression.trim().isEmpty()) {
            return evaluateExpression(request, config, expression);
        }
        
        // 否则，所有启用匹配的元素都需要满足（AND关系）
        for (HttpElementConfig element : config.getElements()) {
            if (element.isUseForMatch()) {
                if (!evaluateElement(request, element)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 评估复杂表达式
     * 支持格式如: "(1 AND 2) OR (3 AND 4)"
     */
    private static boolean evaluateExpression(HttpRequest request, 
                                             UnifiedHttpConfig config, 
                                             String expression) {
        // 先评估所有元素，建立ID到结果的映射
        java.util.Map<Integer, Boolean> elementResults = new java.util.HashMap<>();
        for (HttpElementConfig element : config.getElements()) {
            if (element.isUseForMatch()) {
                elementResults.put(element.getId(), evaluateElement(request, element));
            }
        }
        
        // 解析并评估表达式
        return evaluateExpressionRecursive(expression, elementResults);
    }
    
    /**
     * 递归评估表达式
     */
    private static boolean evaluateExpressionRecursive(String expr, 
                                                       java.util.Map<Integer, Boolean> results) {
        expr = expr.trim();
        
        // 处理括号
        while (expr.contains("(")) {
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            if (end == -1) {
                throw new IllegalArgumentException("不匹配的括号");
            }
            
            String subExpr = expr.substring(start + 1, end);
            boolean subResult = evaluateExpressionRecursive(subExpr, results);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        
        // 处理OR（优先级低）
        if (expr.contains(" OR ")) {
            String[] parts = expr.split(" OR ");
            for (String part : parts) {
                if (evaluateExpressionRecursive(part.trim(), results)) {
                    return true;
                }
            }
            return false;
        }
        
        // 处理AND（优先级高）
        if (expr.contains(" AND ")) {
            String[] parts = expr.split(" AND ");
            for (String part : parts) {
                if (!evaluateExpressionRecursive(part.trim(), results)) {
                    return false;
                }
            }
            return true;
        }
        
        // 处理NOT
        if (expr.startsWith("NOT ")) {
            return !evaluateExpressionRecursive(expr.substring(4).trim(), results);
        }
        
        // 处理单个元素ID或布尔值
        if (expr.equals("true")) return true;
        if (expr.equals("false")) return false;
        
        try {
            int elementId = Integer.parseInt(expr.trim());
            return results.getOrDefault(elementId, false);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的表达式: " + expr);
        }
    }
    
    /**
     * 评估单个HTTP元素
     */
    public static boolean evaluateElement(HttpRequest request, HttpElementConfig element) {
        if (element == null || !element.isUseForMatch()) {
            return true;
        }
        
        switch (element.getType()) {
            case METHOD:
                return evaluateMethod(request, element);
                
            case HOST:
                return evaluateHost(request, element);
                
            case PATH:
                return evaluatePath(request, element);
                
            case PARAMETER:
                return evaluateParameter(request, element);
                
            case HEADER:
                return evaluateHeader(request, element);
                
            case COOKIE:
                return evaluateCookie(request, element);
                
            case BODY:
                return evaluateBody(request, element);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估HTTP方法
     */
    private static boolean evaluateMethod(HttpRequest request, HttpElementConfig element) {
        String method = request.method();
        return matchValue(method, element.getValueMatchConfig());
    }
    
    /**
     * ✅ 评估主机（Host）
     */
    private static boolean evaluateHost(HttpRequest request, HttpElementConfig element) {
        try {
            java.net.URI uri = new java.net.URI(request.url());
            String host = uri.getHost();
            if (host == null) {
                host = "";
            }
            return matchValue(host, element.getValueMatchConfig());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 评估URL路径
     */
    private static boolean evaluatePath(HttpRequest request, HttpElementConfig element) {
        String path = request.path();
        return matchValue(path, element.getValueMatchConfig());
    }
    
    /**
     * 评估参数
     * ✅ 修复：PARAMETER类型只处理URL参数和POST参数，不包括Cookie参数
     */
    private static boolean evaluateParameter(HttpRequest request, HttpElementConfig element) {
        List<ParsedHttpParameter> parameters = request.parameters();
        
        // 遍历所有参数（排除Cookie类型）
        for (ParsedHttpParameter param : parameters) {
            // ✅ 修复：排除Cookie类型的参数
            if (param.type() == HttpParameterType.COOKIE) {
                continue;
            }
            
            // 检查参数名
            boolean nameMatches = matchValue(param.name(), element.getNameMatchConfig());
            if (!nameMatches) {
                continue;
            }
            
            // 检查参数值
            boolean valueMatches = matchValue(param.value(), element.getValueMatchConfig());
            if (valueMatches) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 评估Header
     * ✅ 修复：支持区分大小写配置
     */
    private static boolean evaluateHeader(HttpRequest request, HttpElementConfig element) {
        // 遍历所有header
        for (var header : request.headers()) {
            // 1. 检查header名称是否匹配
            boolean nameMatches = false;
            
            // 优先使用element.name（精确匹配，支持区分大小写）
            String elementName = element.getName();
            if (elementName != null && !elementName.isEmpty()) {
                // ✅ 使用nameMatchConfig的caseSensitive设置
                boolean caseSensitive = element.getNameMatchConfig() != null 
                    ? element.getNameMatchConfig().isCaseSensitive() 
                    : false;  // 默认不区分大小写（符合HTTP规范）
                    
                if (caseSensitive) {
                    nameMatches = header.name().equals(elementName);
                } else {
                    nameMatches = header.name().equalsIgnoreCase(elementName);
                }
            } 
            // 其次使用nameMatchConfig（支持多种匹配模式和大小写）
            else if (element.getNameMatchConfig() != null) {
                nameMatches = matchValue(header.name(), element.getNameMatchConfig());
            } 
            // 如果两者都没有，跳过（必须指定header名称）
            else {
                continue;
            }
            
            if (!nameMatches) {
                continue;
            }
            
            // 2. 检查header值是否匹配（支持区分大小写）
            boolean valueMatches = matchValue(header.value(), element.getValueMatchConfig());
            if (valueMatches) {
                return true;  // 找到匹配的header
            }
        }
        
        return false;  // 没有找到匹配的header
    }
    
    /**
     * 评估Cookie
     * ✅ 修复：支持区分大小写配置
     */
    private static boolean evaluateCookie(HttpRequest request, HttpElementConfig element) {
        String cookieName = element.getName();
        if (cookieName == null || cookieName.isEmpty()) {
            return false;
        }
        
        // ✅ 获取区分大小写设置
        boolean caseSensitive = element.getNameMatchConfig() != null 
            ? element.getNameMatchConfig().isCaseSensitive() 
            : true;  // Cookie名称默认区分大小写
        
        // 查找Cookie
        for (ParsedHttpParameter param : request.parameters()) {
            if (param.type() == HttpParameterType.COOKIE) {
                boolean nameMatches;
                if (caseSensitive) {
                    nameMatches = param.name().equals(cookieName);
                } else {
                    nameMatches = param.name().equalsIgnoreCase(cookieName);
                }
                
                if (nameMatches) {
                    return matchValue(param.value(), element.getValueMatchConfig());
                }
            }
        }
        
        return false;
    }
    
    /**
     * 评估Body
     */
    private static boolean evaluateBody(HttpRequest request, HttpElementConfig element) {
        String body = request.bodyToString();
        if (body == null) {
            body = "";
        }
        
        return matchValue(body, element.getValueMatchConfig());
    }
    
    /**
     * 匹配值
     * ✅ 修复：NOT_CONTAINS和NOT_EQUALS使用AND逻辑
     * 
     * @param actualValue 实际值
     * @param matchConfig 匹配配置
     * @return 是否匹配
     */
    private static boolean matchValue(String actualValue, MatchConfig matchConfig) {
        if (matchConfig == null) {
            return true;
        }
        
        // ✅ 修复：null安全检查
        if (actualValue == null) {
            actualValue = "";
        }
        
        MatchType matchType = matchConfig.getMatchType();
        List<String> expectedValues = matchConfig.getValues();
        boolean caseSensitive = matchConfig.isCaseSensitive();
        
        // ANY类型直接返回true
        if (matchType == MatchType.ANY) {
            return true;
        }
        
        // 没有配置值，ANY类型默认匹配
        if (expectedValues == null || expectedValues.isEmpty()) {
            return matchType == MatchType.ANY;
        }
        
        // 准备实际值
        String compareActual = caseSensitive ? actualValue : actualValue.toLowerCase();
        
        // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
        boolean isNegativeMatch = (matchType == MatchType.NOT_EQUALS || matchType == MatchType.NOT_CONTAINS);
        
        if (isNegativeMatch) {
            // ✅ 反向匹配：所有值都不匹配才返回true（AND逻辑）
            for (String expectedValue : expectedValues) {
                if (expectedValue == null || expectedValue.isEmpty()) {
                    continue;
                }
                
                String compareExpected = caseSensitive ? expectedValue : expectedValue.toLowerCase();
                
                boolean matched = false;
                switch (matchType) {
                    case NOT_EQUALS:
                        matched = compareActual.equals(compareExpected);
                        break;
                        
                    case NOT_CONTAINS:
                        matched = compareActual.contains(compareExpected);
                        break;
                        
                    default:
                        matched = false;
                }
                
                // 如果找到一个匹配的，说明不满足"都不匹配"的条件
                if (matched) {
                    return false;
                }
            }
            // 所有值都不匹配，返回true
            return true;
            
        } else {
            // ✅ 正向匹配：任意一个匹配就返回true（OR逻辑）
            for (String expectedValue : expectedValues) {
                if (expectedValue == null || expectedValue.isEmpty()) {
                    continue;
                }
                
                String compareExpected = caseSensitive ? expectedValue : expectedValue.toLowerCase();
                
                boolean matches = false;
                switch (matchType) {
                    case EQUALS:
                        matches = compareActual.equals(compareExpected);
                        break;
                        
                    case CONTAINS:
                        matches = compareActual.contains(compareExpected);
                        break;
                        
                    case REGEX:
                        try {
                            Pattern pattern = caseSensitive 
                                ? Pattern.compile(expectedValue)
                                : Pattern.compile(expectedValue, Pattern.CASE_INSENSITIVE);
                            matches = pattern.matcher(actualValue).find();
                        } catch (Exception e) {
                            matches = false;
                        }
                        break;
                        
                    case STARTS_WITH:
                        matches = compareActual.startsWith(compareExpected);
                        break;
                        
                    case ENDS_WITH:
                        matches = compareActual.endsWith(compareExpected);
                        break;
                        
                    default:
                        matches = false;
                }
                
                if (matches) {
                    return true;
                }
            }
            
            return false;
        }
    }
    
    /**
     * 获取匹配的参数名列表（用于注入）
     * ✅ 修复：PARAMETER类型只处理URL参数和POST参数，不包括Cookie参数
     */
    public static List<String> getMatchedParameterNames(HttpRequest request, HttpElementConfig element) {
        List<String> matchedNames = new java.util.ArrayList<>();
        
        if (element.getType() != ElementType.PARAMETER) {
            return matchedNames;
        }
        
        List<ParsedHttpParameter> parameters = request.parameters();
        for (ParsedHttpParameter param : parameters) {
            // ✅ 修复：排除Cookie类型的参数
            if (param.type() == HttpParameterType.COOKIE) {
                continue;
            }
            
            // 检查参数名是否匹配
            if (matchValue(param.name(), element.getNameMatchConfig())) {
                matchedNames.add(param.name());
            }
        }
        
        return matchedNames;
    }
    
    /**
     * 获取注入点的原始值
     * 用于{{ORIGINAL}}变量解析
     */
    public static String getOriginalValue(HttpRequest request, HttpElementConfig element) {
        if (request == null || element == null) {
            return "";
        }
        
        ElementType type = element.getType();
        String name = element.getName();
        
        switch (type) {
            case METHOD:
                return request.method();
                
            case HOST:
                try {
                    java.net.URI uri = new java.net.URI(request.url());
                    String host = uri.getHost();
                    return host != null ? host : "";
                } catch (Exception e) {
                    return "";
                }
                
            case PATH:
                return request.path();
                
            case PARAMETER:
                // ✅ 修复：PARAMETER类型只处理URL参数和POST参数，不包括Cookie参数
                if (name != null && !name.isEmpty()) {
                    for (var param : request.parameters()) {
                        // ✅ 修复：排除Cookie类型的参数
                        if (param.type() == HttpParameterType.COOKIE) {
                            continue;
                        }
                        if (param.name().equals(name)) {
                            return param.value();
                        }
                    }
                }
                return "";
                
            case HEADER:
                if (name != null && !name.isEmpty()) {
                    return request.headers().stream()
                        .filter(h -> h.name().equalsIgnoreCase(name))
                        .findFirst()
                        .map(h -> h.value())
                        .orElse("");
                }
                return "";
                
            case COOKIE:
                if (name != null && !name.isEmpty()) {
                    for (var param : request.parameters()) {
                        if (param.type() == HttpParameterType.COOKIE && 
                            param.name().equals(name)) {
                            return param.value();
                        }
                    }
                }
                return "";
                
            case BODY:
                return request.bodyToString();
                
            default:
                return "";
        }
    }
}


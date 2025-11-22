package com.xprobe.scanner.core;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.core.PayloadVariableResolver.PayloadContext;
import com.xprobe.scanner.config.UnifiedResponseConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 统一响应评估器
 * 评估HTTP响应是否满足配置的匹配条件
 */
public class UnifiedResponseEvaluator {
    
    /**
     * 评估响应是否匹配配置
     * 
     * @param response HTTP响应
     * @param config 响应配置
     * @param payloadContext Payload上下文（包含Collaborator信息）
     * @param responseTime 响应时间（毫秒）
     * @return 是否匹配
     */
    public static boolean evaluate(HttpResponse response, 
                                   UnifiedResponseConfig config,
                                   PayloadContext payloadContext,
                                   long responseTime,
                                   Map<String, String> sharedVariables) {
        if (config == null || config.getElements() == null || config.getElements().isEmpty()) {
            return false;
        }
        
        // 评估每个元素
        Map<Integer, Boolean> elementResults = new HashMap<>();
        for (ResponseElementConfig element : config.getElements()) {
            boolean result = evaluateElement(response, element, payloadContext, responseTime, sharedVariables);
            elementResults.put(element.getId(), result);
        }
        
        // 根据表达式评估最终结果
        String expression = config.getConditionExpression();
        if (expression == null || expression.trim().isEmpty()) {
            // 默认：所有元素都需满足（AND关系）
            return elementResults.values().stream().allMatch(b -> b);
        }
        
        // 使用表达式评估
        return evaluateExpression(expression, elementResults);
    }
    
    /**
     * 评估单个响应元素
     */
    private static boolean evaluateElement(HttpResponse response,
                                          ResponseElementConfig element,
                                          PayloadContext payloadContext,
                                          long responseTime,
                                          Map<String, String> sharedVariables) {
        if (element == null || element.getMatchConfig() == null) {
            return false;
        }
        
        ElementType type = element.getType();
        MatchConfig matchConfig = element.getMatchConfig();
        
        switch (type) {
            case STATUS_CODE:
                return evaluateStatusCode(response, matchConfig, payloadContext, sharedVariables);
                
            case RESPONSE_HEADERS:
                return evaluateHeaders(response, matchConfig, payloadContext, sharedVariables);
                
            case RESPONSE_BODY:
                return evaluateBody(response, matchConfig, payloadContext, sharedVariables);
                
            case RESPONSE_TIME:
                return evaluateTime(responseTime, matchConfig);
                
            case RESPONSE_LENGTH:
                return evaluateLength(response, matchConfig);
                
            case COLLABORATOR:
                return evaluateCollaborator(payloadContext, matchConfig);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估状态码
     */
    private static boolean evaluateStatusCode(HttpResponse response, MatchConfig config,
                                              PayloadContext payloadContext,
                                              Map<String, String> sharedVariables) {
        if (response == null) return false;
        
        String statusCode = String.valueOf(response.statusCode());
        return matchTextValues(statusCode, config, payloadContext, sharedVariables);
    }
    
    /**
     * 评估响应头
     */
    private static boolean evaluateHeaders(HttpResponse response, MatchConfig config,
                                           PayloadContext payloadContext,
                                           Map<String, String> sharedVariables) {
        if (response == null) return false;
        
        // 将所有响应头拼接成一个字符串
        StringBuilder headersText = new StringBuilder();
        response.headers().forEach(header -> {
            headersText.append(header.name()).append(": ").append(header.value()).append("\n");
        });
        
        return matchTextValues(headersText.toString(), config, payloadContext, sharedVariables);
    }
    
    /**
     * 评估响应体
     */
    private static boolean evaluateBody(HttpResponse response, MatchConfig config,
                                        PayloadContext payloadContext,
                                        Map<String, String> sharedVariables) {
        if (response == null) {
            return false;
        }
        
        // ✅ 修复：使用UTF-8编码获取响应体，避免中文乱码
        // ✅ 修复：添加 null 检查，防止 NPE
        String body;
        try {
            if (response.body() != null) {
                byte[] bodyBytes = response.body().getBytes();
                body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                body = "";
            }
        } catch (Exception e) {
            // 降级处理：使用默认方法
            try {
                body = response.bodyToString();
            } catch (Exception ex) {
                body = "";
            }
        }
        
        if (body == null) {
            body = "";
        }
        
        return matchTextValues(body, config, payloadContext, sharedVariables);
    }
    
    /**
     * 评估响应时间
     */
    private static boolean evaluateTime(long responseTime, MatchConfig config) {
        return compareNumeric(responseTime, config);
    }
    
    /**
     * 评估响应长度
     */
    private static boolean evaluateLength(HttpResponse response, MatchConfig config) {
        if (response == null) return false;
        
        // ✅ 安全检查：确保body不为null
        if (response.body() == null) {
            return compareNumeric(0, config);
        }
        
        long length = response.body().length();
        return compareNumeric(length, config);
    }
    
    /**
     * 评估Collaborator交互
     */
    private static boolean evaluateCollaborator(PayloadContext payloadContext, MatchConfig config) {
        if (payloadContext == null || payloadContext.getCollaboratorClient() == null) {
            return false;
        }
        
        CollaboratorClient client = payloadContext.getCollaboratorClient();
        
        try {
            // 获取所有交互
            var interactions = client.getAllInteractions();
            
            if (interactions == null || interactions.isEmpty()) {
                return false;
            }
            
            // 检查交互类型
            List<CollaboratorType> requiredTypes = config.getCollaboratorTypes();
            if (requiredTypes == null || requiredTypes.isEmpty()) {
                // 如果没有指定类型，只要有交互就算匹配
                return true;
            }
            
            // 检查是否有匹配的交互类型
            for (var interaction : interactions) {
                String interactionType = interaction.type().toString().toUpperCase();
                
                for (CollaboratorType type : requiredTypes) {
                    if (interactionType.contains(type.name())) {
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            // Collaborator检查失败
            return false;
        }
    }
    
    /**
     * 文本匹配
     * ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
     */
    private static boolean matchTextValues(String actual, MatchConfig config,
                                           PayloadContext payloadContext,
                                           Map<String, String> sharedVariables) {
        if (actual == null) {
            actual = "";
        }
        
        List<String> values = config.getValues();
        if (values == null || values.isEmpty()) {
            return false;
        }
        
        MatchType matchType = config.getMatchType();
        boolean caseSensitive = config.isCaseSensitive();
        
        // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
        boolean isNegativeMatch = (matchType == MatchType.NOT_EQUALS || matchType == MatchType.NOT_CONTAINS);
        
        if (isNegativeMatch) {
            // ✅ 反向匹配：所有值都不匹配才返回true（AND逻辑）
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                String resolvedValue = resolveExpectedValue(value, payloadContext, sharedVariables);
                // ✅ 修复：如果变量解析失败（返回null），跳过此值
                if (resolvedValue == null || resolvedValue.isEmpty()) {
                    continue;
                }
                
                boolean matched = matchSingleValue(actual, resolvedValue, 
                    matchType == MatchType.NOT_EQUALS ? MatchType.EQUALS : MatchType.CONTAINS,
                    caseSensitive);
                
                // 如果找到一个匹配的，说明不满足"都不匹配"的条件
                if (matched) {
                    return false;
                }
            }
            // 所有值都不匹配，返回true
            return true;
            
        } else {
            // ✅ 正向匹配：任意一个匹配就返回true（OR逻辑）
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                String resolvedValue = resolveExpectedValue(value, payloadContext, sharedVariables);
                // ✅ 修复：如果变量解析失败（返回null），跳过此值
                if (resolvedValue == null || resolvedValue.isEmpty()) {
                    continue;
                }
                
                boolean result = matchSingleValue(actual, resolvedValue, matchType, caseSensitive);
                if (result) {
                    return true;  // 任意一个值匹配即返回true
                }
            }
            
            return false;
        }
    }
    
    /**
     * 单个值匹配
     * ✅ 修复：添加 null 检查，防止 NPE
     */
    private static boolean matchSingleValue(String actual, String expected, 
                                           MatchType matchType, boolean caseSensitive) {
        // ✅ 修复：null 安全检查
        if (actual == null) {
            actual = "";
        }
        if (expected == null) {
            return false;  // 如果期望值为 null，无法匹配
        }
        
        if (!caseSensitive) {
            actual = actual.toLowerCase();
            expected = expected.toLowerCase();
        }
        
        switch (matchType) {
            case EQUALS:
                return actual.equals(expected);
                
            case CONTAINS:
                return actual.contains(expected);
                
            case REGEX:
                try {
                    Pattern pattern = caseSensitive 
                        ? Pattern.compile(expected)
                        : Pattern.compile(expected, Pattern.CASE_INSENSITIVE);
                    return pattern.matcher(actual).find();
                } catch (Exception e) {
                    return false;
                }
                
            case STARTS_WITH:
                return actual.startsWith(expected);
                
            case ENDS_WITH:
                return actual.endsWith(expected);
                
            case NOT_EQUALS:
                return !actual.equals(expected);
                
            case NOT_CONTAINS:
                return !actual.contains(expected);
                
            default:
                return false;
        }
    }
    
    /**
     * 数值比较
     */
    private static boolean compareNumeric(long actual, MatchConfig config) {
        long expected = config.getNumericValue();
        ComparisonOperator operator = config.getComparisonOperator();
        
        if (operator == null) {
            operator = ComparisonOperator.GREATER_THAN;
        }
        
        switch (operator) {
            case GREATER_THAN:
                return actual > expected;
                
            case GREATER_THAN_OR_EQUAL:
                return actual >= expected;
                
            case LESS_THAN:
                return actual < expected;
                
            case LESS_THAN_OR_EQUAL:
                return actual <= expected;
                
            case EQUAL:
                return actual == expected;
                
            case NOT_EQUAL:
                return actual != expected;
                
            case BETWEEN:
                // 在范围内：min <= actual <= max
                long min = config.getNumericValueMin();
                long max = config.getNumericValueMax();
                return actual >= min && actual <= max;
                
            case NOT_BETWEEN:
                // 不在范围内：actual < min OR actual > max
                long min2 = config.getNumericValueMin();
                long max2 = config.getNumericValueMax();
                return actual < min2 || actual > max2;
                
            default:
                return false;
        }
    }
    
    /**
     * 评估逻辑表达式
     */
    private static boolean evaluateExpression(String expression, Map<Integer, Boolean> elementResults) {
        if (expression == null || expression.trim().isEmpty()) {
            return elementResults.values().stream().allMatch(b -> b);
        }
        
        try {
            // 简化版表达式评估
            // 替换元素ID为其结果值
            String expr = expression;
            for (Map.Entry<Integer, Boolean> entry : elementResults.entrySet()) {
                String id = String.valueOf(entry.getKey());
                String value = entry.getValue() ? "true" : "false";
                // 使用单词边界确保完整匹配ID
                expr = expr.replaceAll("\\b" + id + "\\b", value);
            }
            
            // 评估布尔表达式
            return evaluateBooleanExpression(expr);
            
        } catch (Exception e) {
            // 表达式评估失败，默认返回AND所有结果
            return elementResults.values().stream().allMatch(b -> b);
        }
    }
    
    /**
     * 评估布尔表达式
     * ✅ 修复：添加 null 检查
     */
    private static boolean evaluateBooleanExpression(String expr) {
        if (expr == null) {
            return false;
        }
        expr = expr.trim();
        if (expr.isEmpty()) {
            return false;
        }
        
        // 处理括号
        while (expr.contains("(")) {
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            if (end == -1) {
                throw new IllegalArgumentException("括号不匹配");
            }
            
            String subExpr = expr.substring(start + 1, end);
            boolean subResult = evaluateBooleanExpression(subExpr);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        
        // 处理NOT
        while (expr.toUpperCase().contains("NOT")) {
            int notPos = expr.toUpperCase().indexOf("NOT");
            String remaining = expr.substring(notPos + 3).trim();
            String[] tokens = remaining.split("\\s+");
            if (tokens.length > 0) {
                boolean value = Boolean.parseBoolean(tokens[0]);
                expr = expr.substring(0, notPos) + (!value) + 
                       (tokens.length > 1 ? " " + remaining.substring(tokens[0].length()).trim() : "");
            }
        }
        
        // 处理AND
        if (expr.toUpperCase().contains("AND")) {
            String[] parts = expr.split("(?i)\\s+AND\\s+");
            for (String part : parts) {
                if (!Boolean.parseBoolean(part.trim())) {
                    return false;
                }
            }
            return true;
        }
        
        // 处理OR
        if (expr.toUpperCase().contains("OR")) {
            String[] parts = expr.split("(?i)\\s+OR\\s+");
            for (String part : parts) {
                if (Boolean.parseBoolean(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        
        // 单个布尔值
        return Boolean.parseBoolean(expr.trim());
    }

    private static final java.util.regex.Pattern DOUBLE_BRACE_PATTERN = java.util.regex.Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final java.util.regex.Pattern SINGLE_BRACE_PATTERN = java.util.regex.Pattern.compile("\\{([A-Za-z0-9_:\\-\\.]+)\\}");

    private static String resolveExpectedValue(String value, PayloadContext payloadContext,
                                               Map<String, String> sharedVariables) {
        if (value == null) {
            return null;
        }
        String result = replacePlaceholders(value, payloadContext, sharedVariables, DOUBLE_BRACE_PATTERN);
        // ✅ 修复：确保 result 不为 null 才继续处理单括号变量
        if (result != null) {
            result = replacePlaceholders(result, payloadContext, sharedVariables, SINGLE_BRACE_PATTERN);
        }
        return result;
    }

    private static String replacePlaceholders(String input,
                                              PayloadContext payloadContext,
                                              Map<String, String> sharedVariables,
                                              java.util.regex.Pattern pattern) {
        if (input == null) {
            return null;
        }
        java.util.regex.Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String token = matcher.group(1);
            String replacement = resolvePlaceholderValue(token, payloadContext, sharedVariables);
            if (replacement == null) {
                replacement = matcher.group(0);  // 保持原始占位符
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return input;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolvePlaceholderValue(String token,
                                                  PayloadContext payloadContext,
                                                  Map<String, String> sharedVariables) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("PAIR:")) {
            String[] parts = trimmed.split(":", 3);
            if (parts.length < 3) {
                return null;
            }
            try {
                int pairId = Integer.parseInt(parts[1].trim());
                String varName = parts[2].trim();
                return lookupSharedVariable(varName, pairId, sharedVariables);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (upper.startsWith("VAR:")) {
            String varName = trimmed.substring(4);
            return lookupSharedVariable(varName, null, sharedVariables);
        }

        if (upper.startsWith("CTX:")) {
            String varName = trimmed.substring(4);
            return lookupPayloadVariable(varName, payloadContext);
        }

        // 首先在payload上下文中查找
        String ctxValue = lookupPayloadVariable(trimmed, payloadContext);
        if (ctxValue != null) {
            return ctxValue;
        }

        // 然后在共享变量中查找
        return lookupSharedVariable(trimmed, null, sharedVariables);
    }

    private static String lookupPayloadVariable(String name, PayloadContext payloadContext) {
        if (payloadContext == null || payloadContext.getVariables() == null || name == null) {
            return null;
        }
        Map<String, String> vars = payloadContext.getVariables();
        String exact = vars.get(name);
        if (exact != null) {
            return exact;
        }
        String lower = vars.get(name.toLowerCase());
        if (lower != null) {
            return lower;
        }
        return vars.get(name.toUpperCase());
    }

    private static String lookupSharedVariable(String name, Integer pairId, Map<String, String> sharedVariables) {
        if (sharedVariables == null || name == null) {
            return null;
        }
        String exactKey = name;
        if (pairId != null) {
            String key = "PAIR:" + pairId + ":" + name;
            if (sharedVariables.containsKey(key)) {
                return sharedVariables.get(key);
            }
            String keyUpper = "PAIR:" + pairId + ":" + name.toUpperCase();
            if (sharedVariables.containsKey(keyUpper)) {
                return sharedVariables.get(keyUpper);
            }
            String keyLower = "PAIR:" + pairId + ":" + name.toLowerCase();
            if (sharedVariables.containsKey(keyLower)) {
                return sharedVariables.get(keyLower);
            }
        }
        if (sharedVariables.containsKey(exactKey)) {
            return sharedVariables.get(exactKey);
        }
        String upper = name.toUpperCase();
        if (sharedVariables.containsKey(upper)) {
            return sharedVariables.get(upper);
        }
        String lower = name.toLowerCase();
        if (sharedVariables.containsKey(lower)) {
            return sharedVariables.get(lower);
        }
        return null;
    }
}


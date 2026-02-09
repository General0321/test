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
     * ✅ P0修复：支持匹配嵌套JSON参数（如 user.name, items[0].id）
     */
    private static boolean evaluateParameter(HttpRequest request, HttpElementConfig element) {
        List<ParsedHttpParameter> parameters = request.parameters();
        
        // 1. 遍历Burp API自动识别的参数（URL参数、表单参数、顶层JSON键）
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
        
        // 2. ✅ P0修复：对于JSON body，额外检查嵌套参数
        String contentType = request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse("");
        
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try {
                String bodyStr = request.bodyToString();
                if (bodyStr != null && !bodyStr.trim().isEmpty()) {
                    // 检查嵌套JSON参数
                    if (evaluateNestedJsonParameters(bodyStr, element)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // 忽略JSON解析错误
            }
        }
        
        return false;
    }
    
    /**
     * ✅ P0修复：评估嵌套JSON参数（按键名匹配，如 "name" 或 "id"）
     * 例如：{"user": {"name": "test"}} → 匹配键名 "name"
     *      {"items": [{"id": 1}]} → 匹配键名 "id"
     */
    private static boolean evaluateNestedJsonParameters(String jsonBody, HttpElementConfig element) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = jsonMapper.readTree(jsonBody);
            
            if (rootNode == null) {
                return false;
            }
            
            // 递归检查所有嵌套参数
            return checkJsonPaths(rootNode, "", element);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ 递归检查JSON键名是否匹配配置（与参数收集逻辑一致）
     * 例如：{"user": {"name": "test"}} → 匹配键名 "user" 或 "name"
     *      {"items": [{"id": 1}]} → 匹配键名 "items" 或 "id"
     */
    private static boolean checkJsonPaths(com.fasterxml.jackson.databind.JsonNode node, 
                                          String currentPath, 
                                          HttpElementConfig element) {
        if (node == null) {
            return false;
        }
        
        if (node.isObject()) {
            // 对象：遍历所有键
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = 
                node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry = fields.next();
                String key = entry.getKey();
                com.fasterxml.jackson.databind.JsonNode value = entry.getValue();
                
                // ✅ 修改：检查键名是否匹配（而不是路径）
                boolean nameMatches = matchValue(key, element.getNameMatchConfig());
                if (nameMatches) {
                    // 检查参数值是否匹配（支持多种类型）
                    String valueStr = "";
                    if (value.isNull()) {
                        valueStr = "null";  // ✅ 修复：显式处理null值
                    } else if (value.isTextual()) {
                        valueStr = value.asText();
                    } else if (value.isNumber()) {
                        valueStr = value.asText();
                    } else if (value.isBoolean()) {
                        valueStr = String.valueOf(value.asBoolean());
                    } else {
                        valueStr = value.toString();
                    }
                    boolean valueMatches = matchValue(valueStr, element.getValueMatchConfig());
                    if (valueMatches) {
                        return true;
                    }
                }
                
                // 递归检查嵌套对象和数组
                if (value.isObject() || value.isArray()) {
                    String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                    if (checkJsonPaths(value, newPath, element)) {
                        return true;
                    }
                }
            }
        } else if (node.isArray()) {
            // 数组：遍历所有元素，提取元素中的键名
            for (int i = 0; i < node.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode arrayElement = node.get(i);
                
                // ✅ 修改：数组元素不检查数组索引路径，只递归检查元素内容
                if (arrayElement.isObject() || arrayElement.isArray()) {
                    if (checkJsonPaths(arrayElement, currentPath, element)) {
                        return true;
                    }
                }
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
     * ✅ 修复：统一与Header的匹配逻辑，支持nameMatchConfig
     */
    private static boolean evaluateCookie(HttpRequest request, HttpElementConfig element) {
        // 遍历所有Cookie参数
        for (ParsedHttpParameter param : request.parameters()) {
            if (param.type() != HttpParameterType.COOKIE) {
                continue;
            }
            
            // 1. 检查Cookie名称是否匹配
            boolean nameMatches = false;
            
            // 优先使用element.name（精确匹配，支持区分大小写）
            String elementName = element.getName();
            if (elementName != null && !elementName.isEmpty()) {
                // ✅ 使用nameMatchConfig的caseSensitive设置
                boolean caseSensitive = element.getNameMatchConfig() != null 
                    ? element.getNameMatchConfig().isCaseSensitive() 
                    : true;  // Cookie名称默认区分大小写
                    
                if (caseSensitive) {
                    nameMatches = param.name().equals(elementName);
                } else {
                    nameMatches = param.name().equalsIgnoreCase(elementName);
                }
            } 
            // 其次使用nameMatchConfig（支持多种匹配模式和大小写）
            else if (element.getNameMatchConfig() != null) {
                nameMatches = matchValue(param.name(), element.getNameMatchConfig());
            } 
            // 如果两者都没有，跳过（必须指定Cookie名称）
            else {
                continue;
            }
            
            if (!nameMatches) {
                continue;
            }
            
            // 2. 检查Cookie值是否匹配（支持区分大小写）
            boolean valueMatches = matchValue(param.value(), element.getValueMatchConfig());
            if (valueMatches) {
                return true;  // 找到匹配的Cookie
            }
        }
        
        return false;  // 没有找到匹配的Cookie
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
     * ✅ P0修复：支持嵌套JSON参数路径
     */
    public static List<String> getMatchedParameterNames(HttpRequest request, HttpElementConfig element) {
        List<String> matchedNames = new java.util.ArrayList<>();
        
        if (element.getType() != ElementType.PARAMETER) {
            return matchedNames;
        }
        
        // 1. 检查顶层参数（URL参数、表单参数、顶层JSON键）
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
        
        // 2. ✅ P0修复：对于JSON body，额外收集嵌套参数路径
        String contentType = request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse("");
        
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try {
                String bodyStr = request.bodyToString();
                if (bodyStr != null && !bodyStr.trim().isEmpty()) {
                    collectMatchedJsonPaths(bodyStr, "", element, matchedNames);
                }
            } catch (Exception e) {
                // 忽略JSON解析错误
            }
        }
        
        return matchedNames;
    }
    
    /**
     * ✅ 从JSON中提取指定路径的值
     * 支持路径格式：user.name, items[0].id
     */
    private static String getJsonPathValue(String jsonBody, String path) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = jsonMapper.readTree(jsonBody);
            
            if (rootNode == null) {
                return "";
            }
            
            // 解析路径
            com.fasterxml.jackson.databind.JsonNode currentNode = rootNode;
            String[] parts = path.split("\\.");
            
            for (String part : parts) {
                if (part.contains("[")) {
                    // 处理数组索引，如 items[0]
                    int bracketIndex = part.indexOf('[');
                    String key = part.substring(0, bracketIndex);
                    String indexStr = part.substring(bracketIndex + 1, part.indexOf(']'));
                    
                    try {
                        int arrayIndex = Integer.parseInt(indexStr);
                        if (currentNode.isObject() && currentNode.has(key)) {
                            currentNode = currentNode.get(key);
                            if (currentNode.isArray() && arrayIndex >= 0 && arrayIndex < currentNode.size()) {
                                currentNode = currentNode.get(arrayIndex);
                            } else {
                                return "";
                            }
                        } else {
                            return "";
                        }
                    } catch (NumberFormatException e) {
                        return "";
                    }
                } else {
                    // 普通对象键
                    if (currentNode.isObject() && currentNode.has(part)) {
                        currentNode = currentNode.get(part);
                    } else {
                        return "";
                    }
                }
            }
            
            // 提取值
            if (currentNode.isNull()) {
                return "null";
            } else if (currentNode.isTextual()) {
                return currentNode.asText();
            } else if (currentNode.isNumber()) {
                return currentNode.asText();
            } else if (currentNode.isBoolean()) {
                return String.valueOf(currentNode.asBoolean());
            } else {
                return currentNode.toString();
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * ✅ 收集匹配的JSON路径（用于getMatchedParameterNames）
     */
    private static void collectMatchedJsonPaths(String jsonBody, 
                                                 String currentPath,
                                                 HttpElementConfig element,
                                                 List<String> matchedPaths) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = jsonMapper.readTree(jsonBody);
            
            if (rootNode != null) {
                collectJsonPathsForNames(rootNode, currentPath, element, matchedPaths);
            }
        } catch (Exception e) {
            // 忽略JSON解析错误
        }
    }
    
    /**
     * ✅ 递归收集匹配的JSON路径
     */
    private static void collectJsonPathsForNames(com.fasterxml.jackson.databind.JsonNode node,
                                                 String currentPath,
                                                 HttpElementConfig element,
                                                 List<String> matchedPaths) {
        if (node == null) {
            return;
        }
        
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                com.fasterxml.jackson.databind.JsonNode value = entry.getValue();
                
                // 构建新路径
                String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;
                
                // 检查键名是否匹配
                if (matchValue(key, element.getNameMatchConfig())) {
                    matchedPaths.add(newPath);
                }
                
                // 递归处理嵌套对象和数组
                if (value.isObject() || value.isArray()) {
                    collectJsonPathsForNames(value, newPath, element, matchedPaths);
                }
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode arrayElement = node.get(i);
                String arrayPath = currentPath + "[" + i + "]";
                
                if (arrayElement.isObject() || arrayElement.isArray()) {
                    collectJsonPathsForNames(arrayElement, arrayPath, element, matchedPaths);
                }
            }
        }
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
                    // 1. 先检查顶层参数（URL参数、表单参数、顶层JSON键）
                    for (var param : request.parameters()) {
                        // ✅ 修复：排除Cookie类型的参数
                        if (param.type() == HttpParameterType.COOKIE) {
                            continue;
                        }
                        if (param.name().equals(name)) {
                            return param.value();
                        }
                    }
                    
                    // 2. ✅ P0修复：如果是嵌套JSON路径（包含.或[），从JSON body中提取
                    if (name.contains(".") || name.contains("[")) {
                        String contentType = request.headers().stream()
                            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
                            .map(h -> h.value())
                            .findFirst()
                            .orElse("");
                        
                        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                            try {
                                String bodyStr = request.bodyToString();
                                if (bodyStr != null && !bodyStr.trim().isEmpty()) {
                                    return getJsonPathValue(bodyStr, name);
                                }
                            } catch (Exception e) {
                                // 忽略JSON解析错误
                            }
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


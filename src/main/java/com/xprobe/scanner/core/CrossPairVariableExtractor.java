package com.xprobe.scanner.core;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 跨Pair变量提取器
 * 用于MULTI_STAGE_CHAINED模式
 * 
 * 从响应中提取变量，供后续Pair使用
 */
public class CrossPairVariableExtractor {
    
    /**
     * 从响应中提取变量
     * 
     * @param response HTTP响应
     * @param extractRules 提取规则 Map<变量名, 正则表达式>
     * @return 提取的变量 Map<变量名, 值>
     */
    public static Map<String, String> extractVariables(HttpResponse response, 
                                                        Map<String, String> extractRules) {
        Map<String, String> extractedVars = new HashMap<>();
        
        if (response == null || extractRules == null || extractRules.isEmpty()) {
            return extractedVars;
        }
        
        String responseBody = response.bodyToString();
        String responseHeaders = extractHeadersAsString(response);
        
        // 对每个提取规则执行
        for (Map.Entry<String, String> rule : extractRules.entrySet()) {
            String varName = rule.getKey();
            String regex = rule.getValue();
            
            try {
                // 先在响应体中查找
                String value = extractFromText(responseBody, regex);
                
                // 如果响应体中没找到，在响应头中查找
                if (value == null) {
                    value = extractFromText(responseHeaders, regex);
                }
                
                if (value != null) {
                    extractedVars.put(varName, value);
                }
            } catch (Exception e) {
                // 正则表达式错误，跳过
                System.err.println("❌ 变量提取失败 [" + varName + "]: " + e.getMessage());
            }
        }
        
        return extractedVars;
    }
    
    /**
     * 从文本中提取第一个匹配组的值
     * 
     * @param text 文本
     * @param regex 正则表达式（必须包含至少一个捕获组）
     * @return 提取的值，如果没有匹配则返回null
     */
    private static String extractFromText(String text, String regex) {
        if (text == null || regex == null) {
            return null;
        }
        
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            // 返回第一个捕获组的值
            if (matcher.groupCount() > 0) {
                return matcher.group(1);
            } else {
                // 如果没有捕获组，返回整个匹配
                return matcher.group(0);
            }
        }
        
        return null;
    }
    
    /**
     * 将响应头转换为字符串（用于提取）
     * 
     * @param response HTTP响应
     * @return 响应头字符串
     */
    private static String extractHeadersAsString(HttpResponse response) {
        if (response == null) {
            return "";
        }
        
        StringBuilder headers = new StringBuilder();
        response.headers().forEach(header -> {
            headers.append(header.name()).append(": ").append(header.value()).append("\n");
        });
        
        return headers.toString();
    }
    
    /**
     * 替换文本中的变量占位符
     * 
     * 支持的格式：
     * - {{PAIR:id:name}} - 从指定Pair获取变量（优先匹配）
     * - {{VAR:name}} - 从变量映射中获取变量
     * 
     * @param text 原始文本
     * @param variables 变量映射
     * @return 替换后的文本
     */
    public static String replaceVariables(String text, Map<String, String> variables) {
        if (text == null || variables == null || variables.isEmpty()) {
            return text;
        }
        
        String result = text;
        
        // 1. 优先替换 {{PAIR:id:name}} 格式的占位符
        Pattern pairPattern = Pattern.compile("\\{\\{PAIR:(\\d+):([^}]+)\\}\\}");
        Matcher pairMatcher = pairPattern.matcher(result);
        StringBuffer pairSb = new StringBuffer();
        while (pairMatcher.find()) {
            String pairId = pairMatcher.group(1);
            String varName = pairMatcher.group(2);
            
            // 尝试多种key格式
            String[] keysToTry = {
                "PAIR:" + pairId + ":" + varName,
                "PAIR:" + pairId + ":" + varName.toUpperCase(),
                "PAIR:" + pairId + ":" + varName.toLowerCase()
            };
            
            String replacement = null;
            for (String key : keysToTry) {
                if (variables.containsKey(key)) {
                    replacement = variables.get(key);
                    break;
                }
            }
            
            if (replacement != null) {
                pairMatcher.appendReplacement(pairSb, Matcher.quoteReplacement(replacement));
            } else {
                // 如果找不到，保持原始占位符
                pairMatcher.appendReplacement(pairSb, Matcher.quoteReplacement(pairMatcher.group(0)));
            }
        }
        pairMatcher.appendTail(pairSb);
        result = pairSb.toString();
        
        // 2. 替换 {{VAR:变量名}} 格式的占位符
        Pattern varPattern = Pattern.compile("\\{\\{VAR:([^}]+)\\}\\}");
        Matcher varMatcher = varPattern.matcher(result);
        StringBuffer varSb = new StringBuffer();
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            
            // 尝试多种key格式
            String[] keysToTry = {
                varName,
                varName.toUpperCase(),
                varName.toLowerCase(),
                "VAR:" + varName,
                "VAR:" + varName.toUpperCase(),
                "VAR:" + varName.toLowerCase()
            };
            
            String replacement = null;
            for (String key : keysToTry) {
                if (variables.containsKey(key)) {
                    replacement = variables.get(key);
                    break;
                }
            }
            
            if (replacement != null) {
                varMatcher.appendReplacement(varSb, Matcher.quoteReplacement(replacement));
            } else {
                // 如果找不到，保持原始占位符
                varMatcher.appendReplacement(varSb, Matcher.quoteReplacement(varMatcher.group(0)));
            }
        }
        varMatcher.appendTail(varSb);
        result = varSb.toString();
        
        return result;
    }
    
    /**
     * 检查文本是否包含变量占位符
     * 
     * @param text 文本
     * @return true表示包含，false表示不包含
     */
    public static boolean containsVariablePlaceholder(String text) {
        if (text == null) {
            return false;
        }
        
        // 检查是否包含 {{VAR:...}} 或 {{PAIR:...}} 格式
        return (text.contains("{{VAR:") || text.contains("{{PAIR:")) && text.contains("}}");
    }
    
    /**
     * 提取文本中使用的所有变量名
     * 
     * @param text 文本
     * @return 变量名列表
     */
    public static java.util.List<String> extractVariableNames(String text) {
        java.util.List<String> varNames = new java.util.ArrayList<>();
        
        if (text == null) {
            return varNames;
        }
        
        // 提取 {{PAIR:id:name}} 格式
        Pattern pairPattern = Pattern.compile("\\{\\{PAIR:(\\d+):([^}]+)\\}\\}");
        Matcher pairMatcher = pairPattern.matcher(text);
        while (pairMatcher.find()) {
            varNames.add("PAIR:" + pairMatcher.group(1) + ":" + pairMatcher.group(2));
        }
        
        // 提取 {{VAR:name}} 格式
        Pattern varPattern = Pattern.compile("\\{\\{VAR:([^}]+)\\}\\}");
        Matcher varMatcher = varPattern.matcher(text);
        while (varMatcher.find()) {
            varNames.add(varMatcher.group(1));
        }
        
        return varNames;
    }
}


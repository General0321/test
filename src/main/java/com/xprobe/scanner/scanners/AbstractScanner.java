package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描器抽象基类，提供通用功能
 */
public abstract class AbstractScanner implements Scanner {
    protected final MontoyaApi api;
    protected final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    
    protected AbstractScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        // ✅ 旧Scanner跳过配对架构的任务（由UniversalScanner处理）
        if (task.getConfiguration().getPairs() != null && 
            !task.getConfiguration().getPairs().isEmpty()) {
            return false;
        }
        
        // ✅ 检查参数是否为null（旧架构必须有parameter）
        if (task.getParameter() == null) {
            return false;
        }
        
        // 默认检查：参数类型和扫描类型匹配
        if (task.getParameter().type() == HttpParameterType.COOKIE) {
            return false;
        }
        return task.getScanType().equals(getType());
    }
    
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScanResult> results = new ArrayList<>();
            HttpRequest originalRequest = task.getRequest().copyToTempFile();
            
            // 从配置中获取payload
            List<String> payloads = task.getConfiguration().getParameterValues();
            for (String payload : payloads) {
                try {
                    HttpRequest modifiedRequest = buildRequest(originalRequest, task.getParameter(), payload);
                    ScanResult result = performScan(task, originalRequest, modifiedRequest, payload);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("Error in " + getName() + " scan: " + e.getMessage());
                }
            }
            
            // 注意：参数已在 RequestHandler 中标记为扫描中，无需重复标记
            
            return results;
        });
    }
    
    // 已删除 markParameterAsScanned 方法
    // 标记操作已在 RequestHandler.checkAndMarkParameterAsScanning() 中完成
    // 这样可以避免并发场景下的重复扫描问题
    
    /**
     * 构建修改后的请求
     */
    protected HttpRequest buildRequest(HttpRequest originalRequest, ParsedHttpParameter parameter, String payload) {
        if (parameter.type() == HttpParameterType.JSON) {
            return updateJsonParameter(originalRequest, parameter.name(), payload);
        } else {
            HttpParameter newParam = HttpParameter.parameter(parameter.name(), payload, parameter.type());
            return originalRequest.withUpdatedParameters(newParam);
        }
    }
    
    /**
     * 执行具体的扫描逻辑
     */
    protected abstract ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                            HttpRequest modifiedRequest, String payload);
    
    /**
     * 发送HTTP请求并获取响应
     */
    protected HttpRequestResponse sendRequest(HttpRequest request) {
        return api.http().sendRequest(request);
    }
    
    /**
     * 检查响应是否匹配规则
     */
    protected boolean isResponseMatch(String responseBody, int responseCode, long responseTime, 
                                    List<Configuration.MatchRule> matchRules) {
        return isResponseMatch(responseBody, responseCode, responseTime, null, matchRules);
    }
    
    /**
     * 检查响应是否匹配规则（包含响应头）
     */
    protected boolean isResponseMatch(String responseBody, int responseCode, long responseTime, 
                                    String responseHeaders, List<Configuration.MatchRule> matchRules) {
        if (matchRules.isEmpty()) {
            return false;
        }
        
        List<String> operators = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        
        for (Configuration.MatchRule rule : matchRules) {
            boolean currentResult = matchSingleRule(responseBody, responseCode, responseTime, responseHeaders, rule);
            results.add(currentResult);
            
            if (rule.getOperator() != null) {
                operators.add(rule.getOperator().toUpperCase());
            }
        }
        
        return evaluateResults(results, operators);
    }
    
    private boolean matchSingleRule(String responseBody, int responseCode, long responseTime, 
                                   String responseHeaders, Configuration.MatchRule rule) {
        switch (rule.getLocation()) {
            case "Body":
                return matchResponseBody(responseBody, rule);
            case "HTTP-Status_Code":
                return matchStatusCode(responseCode, rule);
            case "HTTP-Response_Time":
                return matchResponseTime(responseTime, rule);
            case "HTTP-Response_Headers":
                return matchResponseHeaders(responseHeaders, rule);
            default:
                api.logging().raiseDebugEvent("Unknown location type: " + rule.getLocation());
                return false;
        }
    }
    
    private boolean matchResponseBody(String responseBody, Configuration.MatchRule rule) {
        String matchType = rule.getMatchType();
        String matchRule = rule.getRule();
        
        if ("String Match".equals(matchType)) {
            return responseBody.contains(matchRule);
        } else if ("Regex Match".equals(matchType)) {
            try {
                Pattern pattern = Pattern.compile(matchRule);
                Matcher matcher = pattern.matcher(responseBody);
                return matcher.find();
            } catch (Exception e) {
                api.logging().raiseErrorEvent("Invalid regex pattern: " + matchRule);
            }
        }
        return false;
    }
    
    private boolean matchStatusCode(int responseCode, Configuration.MatchRule rule) {
        try {
            int expectedCode = Integer.parseInt(rule.getRule());
            return expectedCode == responseCode;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean matchResponseTime(long responseTime, Configuration.MatchRule rule) {
        try {
            double ruleValue = Double.parseDouble(rule.getRule());
            long minTime = (long) (ruleValue * 1000);
            long maxTime = minTime + 1500;
            return responseTime >= minTime && responseTime < maxTime;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean matchResponseHeaders(String responseHeaders, Configuration.MatchRule rule) {
        if (responseHeaders == null || responseHeaders.isEmpty()) {
            return false;
        }
        
        String matchType = rule.getMatchType();
        String matchRule = rule.getRule();
        
        if ("String Match".equals(matchType)) {
            return responseHeaders.contains(matchRule);
        } else if ("Regex Match".equals(matchType)) {
            try {
                Pattern pattern = Pattern.compile(matchRule);
                Matcher matcher = pattern.matcher(responseHeaders);
                return matcher.find();
            } catch (Exception e) {
                api.logging().raiseErrorEvent("Invalid regex pattern: " + matchRule);
            }
        }
        return false;
    }
    
    private boolean evaluateResults(List<Boolean> results, List<String> operators) {
        if (results.isEmpty()) {
            return false;
        }
        
        if (results.size() == 1) {
            return results.get(0);
        }
        
        // 处理NOT运算符（先处理所有NOT）
        List<Boolean> processedResults = new ArrayList<>(results);
        List<String> processedOperators = new ArrayList<>(operators);
        
        for (int i = 0; i < processedOperators.size(); i++) {
            if ("NOT".equals(processedOperators.get(i))) {
                if (i + 1 < processedResults.size()) {
                    processedResults.set(i + 1, !processedResults.get(i + 1));
                }
                processedOperators.remove(i);
                i--; // 调整索引
            }
        }
        
        // 处理AND和OR运算符
        boolean finalResult = processedResults.get(0);
        
        for (int i = 0; i < processedOperators.size() && i + 1 < processedResults.size(); i++) {
            String operator = processedOperators.get(i);
            boolean nextResult = processedResults.get(i + 1);
            
            switch (operator) {
                case "AND":
                    finalResult = finalResult && nextResult;
                    break;
                case "OR":
                    finalResult = finalResult || nextResult;
                    break;
                default:
                    api.logging().raiseErrorEvent("Unsupported operator: " + operator);
                    break;
            }
        }
        return finalResult;
    }
    
    /**
     * 更新JSON参数
     */
    protected HttpRequest updateJsonParameter(HttpRequest originalRequest, String paramName, String newValue) {
        try {
            String requestBody = originalRequest.bodyToString();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(requestBody);
            
            boolean updated = updateJsonField(rootNode, paramName, newValue);
            
            if (updated) {
                String updatedBody = objectMapper.writeValueAsString(rootNode);
                return originalRequest.withBody(updatedBody);
            }
            
            return originalRequest;
        } catch (IOException | IllegalArgumentException e) {
            return originalRequest;
        }
    }
    
    private boolean updateJsonField(JsonNode rootNode, String paramName, String newValue) {
        boolean updated = false;
        
        if (rootNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) rootNode;
            
            if (objectNode.has(paramName)) {
                JsonNode valueNode = objectNode.get(paramName);
                if (valueNode.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) valueNode;
                    if (arrayNode.size() > 0) {
                        arrayNode.set(0, newValue);
                        updated = true;
                    } else {
                        arrayNode.add(newValue);
                        updated = true;
                    }
                } else {
                    objectNode.put(paramName, newValue);
                    updated = true;
                }
            }
            
            for (Iterator<String> it = objectNode.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                updated |= updateJsonField(objectNode.get(fieldName), paramName, newValue);
            }
        } else if (rootNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) rootNode;
            for (JsonNode childNode : arrayNode) {
                updated |= updateJsonField(childNode, paramName, newValue);
            }
        }
        
        return updated;
    }
}

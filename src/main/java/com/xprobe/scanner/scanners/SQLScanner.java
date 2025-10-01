package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

/**
 * SQL注入扫描器
 */
public class SQLScanner extends AbstractScanner {
    
    public SQLScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        super(api, realtimeScanner);
    }
    
    @Override
    public String getType() {
        return "sql";
    }
    
    @Override
    public String getName() {
        return "SQL Injection Scanner";
    }
    
    @Override
    public String getDescription() {
        return "扫描SQL注入漏洞";
    }
    
    @Override
    protected HttpRequest buildRequest(HttpRequest originalRequest, ParsedHttpParameter parameter, String payload) {
        try {
            // SQL扫描需要特殊处理：替换{value}占位符
            String paramValue = parameter.value();
            String finalPayload = payload.replace("{value}", paramValue);
            
            if (parameter.type() == HttpParameterType.JSON) {
                return updateJsonParameter(originalRequest, parameter.name(), finalPayload);
            } else {
                // URL编码
                String encodedPayload = URLEncoder.encode(finalPayload, "UTF-8");
                HttpParameter newParam = HttpParameter.parameter(parameter.name(), encodedPayload, parameter.type());
                return originalRequest.withUpdatedParameters(newParam);
            }
        } catch (UnsupportedEncodingException e) {
            api.logging().raiseErrorEvent("Encoding error: " + e.getMessage());
            return originalRequest;
        }
    }
    
    @Override
    protected ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                   HttpRequest modifiedRequest, String payload) {
        long startTime = System.currentTimeMillis();
        
        // 发送请求
        HttpRequestResponse requestResponse = sendRequest(modifiedRequest);
        HttpResponse response = requestResponse.response();
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        
        // 检查响应是否匹配规则
        boolean isVulnerable = isResponseMatch(
            response.bodyToString(),
            response.statusCode(),
            responseTime,
            task.getConfiguration().getMatchRules()
        );
        
        if (isVulnerable) {
            return new ScanResult.Builder()
                .vulnerable(true)
                .scanType(getType())
                .parameterName(task.getParameter().name())
                .payload(payload)
                .originalRequest(originalRequest)
                .modifiedRequest(modifiedRequest)
                .response(response)
                .responseTime(responseTime)
                .evidence("SQL injection vulnerability detected with payload: " + payload)
                .build();
        }
        
        return null;
    }
    
    @Override
    public List<String> getPayloads() {
        // Payload现在从Configuration中动态获取
        // 这个方法仅用于接口兼容，实际不会被调用
        return Arrays.asList();
    }
}

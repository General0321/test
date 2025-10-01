package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.Arrays;
import java.util.List;

/**
 * LFI (Local File Inclusion) 扫描器
 */
public class LFIScanner extends AbstractScanner {
    
    public LFIScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        super(api, realtimeScanner);
    }
    
    @Override
    public String getType() {
        return "lfi";
    }
    
    @Override
    public String getName() {
        return "LFI Scanner";
    }
    
    @Override
    public String getDescription() {
        return "扫描本地文件包含漏洞";
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
                .evidence("LFI vulnerability detected with payload: " + payload)
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

package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSRF (Server-Side Request Forgery) 扫描器
 */
public class SSRFScanner extends AbstractScanner {
    
    public SSRFScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        super(api, realtimeScanner);
    }
    
    @Override
    public String getType() {
        return "ssrf";
    }
    
    @Override
    public String getName() {
        return "SSRF Scanner";
    }
    
    @Override
    public String getDescription() {
        return "扫描服务端请求伪造漏洞";
    }
    
    @Override
    protected ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                   HttpRequest modifiedRequest, String payload) {
        // SSRF扫描需要特殊处理，因为可能包含DNS日志检测
        if (payload.contains("{dnslog}")) {
            return performDnsLogScan(task, originalRequest, modifiedRequest, payload);
        } else {
            return performNormalScan(task, originalRequest, modifiedRequest, payload);
        }
    }
    
    private ScanResult performNormalScan(ScanTask task, HttpRequest originalRequest, 
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
                .evidence("SSRF vulnerability detected with payload: " + payload)
                .build();
        }
        
        return null;
    }
    
    private ScanResult performDnsLogScan(ScanTask task, HttpRequest originalRequest, 
                                       HttpRequest modifiedRequest, String payload) {
        try {
            // 创建Collaborator客户端 (声明为final以便在lambda中使用)
            final CollaboratorClient collaboratorClient = api.collaborator().createClient();
            String dnsPayload = collaboratorClient.generatePayload().toString();
            
            // 替换{dnslog}占位符
            String finalPayload = payload.replace("{dnslog}", dnsPayload);
            HttpRequest finalRequest = buildRequest(originalRequest, task.getParameter(), finalPayload);
            
            long startTime = System.currentTimeMillis();
            
            // 发送请求
            HttpRequestResponse requestResponse = sendRequest(finalRequest);
            HttpResponse response = requestResponse.response();
            
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            // 使用CompletableFuture.supplyAsync来正确返回结果
            CompletableFuture<ScanResult> futureResult = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // 等待3秒后检查DNS交互
                        Thread.sleep(3000);
                        
                        List<Interaction> interactions = collaboratorClient.getAllInteractions();
                        if (interactions != null && !interactions.isEmpty()) {
                            // 发现DNS交互，说明存在SSRF
                            ScanResult result = new ScanResult.Builder()
                                .vulnerable(true)
                                .scanType(getType())
                                .parameterName(task.getParameter().name())
                                .payload(finalPayload)
                                .originalRequest(originalRequest)
                                .modifiedRequest(finalRequest)
                                .response(response)
                                .responseTime(responseTime)
                                .evidence("SSRF vulnerability confirmed via DNS interaction: " + 
                                        interactions.get(0).toString())
                                .build();
                            
                            // 记录交互详情
                            api.logging().raiseInfoEvent("SSRF DNS interaction detected: " + 
                                "Client IP: " + interactions.get(0).clientIp() + 
                                ", Timestamp: " + interactions.get(0).timeStamp());
                            
                            return result;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        api.logging().raiseErrorEvent("DNS log scan interrupted: " + e.getMessage());
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("Error checking DNS interactions: " + e.getMessage());
                    }
                    return null;
                });
            
            // 等待结果（最多等待5秒）
            try {
                return futureResult.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 超时或其他错误，记录日志并返回null
                api.logging().raiseErrorEvent("DNS log scan timeout or error: " + e.getMessage());
                return null;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Error in DNS log scan: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    protected HttpRequest buildRequest(HttpRequest originalRequest, ParsedHttpParameter parameter, String payload) {
        // DNS日志占位符替换已在performDnsLogScan方法中处理
        // 这里直接使用父类的默认实现
        return super.buildRequest(originalRequest, parameter, payload);
    }
    
    @Override
    public List<String> getPayloads() {
        // Payload现在从Configuration中动态获取
        // 这个方法仅用于接口兼容，实际不会被调用
        return Arrays.asList();
    }
}

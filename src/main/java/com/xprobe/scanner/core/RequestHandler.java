package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.models.RequestContext;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP请求处理器，负责处理Burp拦截的HTTP请求
 */
public class RequestHandler implements HttpHandler {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final RequestFilter requestFilter;
    private final TaskScheduler taskScheduler;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    private final OriginalResponseCache responseCache;  // ✅ 原始响应缓存
    
    public RequestHandler(MontoyaApi api, ConfigurationManager configManager, 
                         RequestFilter requestFilter, TaskScheduler taskScheduler, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner,
                         XProbeConfigManager xprobeConfigManager, OriginalResponseCache responseCache) {
        this.api = api;
        this.configManager = configManager;
        this.requestFilter = requestFilter;
        this.taskScheduler = taskScheduler;
        this.realtimeScanner = realtimeScanner;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        this.responseCache = responseCache;  // ✅ 保存响应缓存引用
    }
    
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // ✅ 只处理参数收集和实时扫描，被动扫描延迟到响应收到后
        
        // 将请求发送给实时扫描器处理（只处理PROXY流量）
        if (requestToBeSent.toolSource().isFromTool(burp.api.montoya.core.ToolType.PROXY)) {
            realtimeScanner.processNewRequest(requestToBeSent);
        }
        
        // 立即返回，不阻塞请求
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }
    
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        // ✅ 只处理PROXY流量
        if (responseReceived.toolSource().isFromTool(burp.api.montoya.core.ToolType.PROXY)) {
            try {
                HttpRequest initiatingRequest = responseReceived.initiatingRequest();
                
                // ✅ 1. 先缓存原始响应
                responseCache.put(
                    initiatingRequest.method(), 
                    initiatingRequest.url(), 
                    responseReceived
                );
                
                // ✅ 2. 收集响应中的参数和关键词
                realtimeScanner.processResponse(
                    initiatingRequest, 
                    responseReceived
                );
                
                // ✅ 3. 触发被动扫描（此时原始响应已缓存）
                if (xprobeConfigManager.isPassiveScanEnabled()) {
                    if (shouldScanRequest(initiatingRequest)) {
                        // 创建请求上下文
                        RequestContext context = new RequestContext(
                            responseReceived.toolSource().toolType().toString(),
                            initiatingRequest.method(),
                            initiatingRequest.url(),
                            initiatingRequest.toString().hashCode()
                        );
                        
                        // 收集扫描任务
                        List<ScanTask> scanTasks = collectScanTasks(initiatingRequest, context);
                        
                        // 调度扫描任务（原始响应已在缓存中）
                        if (!scanTasks.isEmpty()) {
                            taskScheduler.scheduleScan(scanTasks);
                        }
                    }
                }
                
            } catch (Exception e) {
                api.logging().raiseErrorEvent("处理响应时出错: " + e.getMessage());
            }
        }
        
        return ResponseReceivedAction.continueWith(responseReceived);
    }
    
    /**
     * 收集所有需要扫描的任务
     */
    private List<ScanTask> collectScanTasks(HttpRequest request, RequestContext context) {
        List<ScanTask> tasks = new ArrayList<>();
        
        // 遍历所有启用的配置
        for (Configuration config : configManager.getEnabledConfigurations()) {
            // 配对架构：创建一个基于整个请求的扫描任务
            if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                ScanTask task = new ScanTask(null, config, request, context);
                tasks.add(task);
            }
        }
        
        return tasks;
    }
    
    /**
     * 检查请求是否应该扫描（重载：HttpRequest）
     */
    private boolean shouldScanRequest(HttpRequest request) {
        try {
            String url = request.url();
            
            // 基本URL检查（模拟RequestFilter的逻辑）
            if (url == null || url.isEmpty()) {
                return false;
            }
            
            // 检查静态资源（简化版）
            String[] staticExtensions = {".js", ".css", ".jpg", ".png", ".gif", ".svg", ".ico", ".woff", ".ttf"};
            for (String ext : staticExtensions) {
                if (url.toLowerCase().endsWith(ext)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
}

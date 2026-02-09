package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
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
    private final GlobalFilter globalFilter;  // ✅ 添加GlobalFilter引用，用于检查黑白名单
    private final ScanTaskCollector scanTaskCollector; // ✅ 新增：扫描任务收集器
    
    public RequestHandler(MontoyaApi api, ConfigurationManager configManager, 
                         RequestFilter requestFilter, TaskScheduler taskScheduler, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner,
                         XProbeConfigManager xprobeConfigManager, OriginalResponseCache responseCache,
                         GlobalFilter globalFilter) {
        this.api = api;
        this.configManager = configManager;
        this.requestFilter = requestFilter;
        this.taskScheduler = taskScheduler;
        this.realtimeScanner = realtimeScanner;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        this.responseCache = responseCache;  // ✅ 保存响应缓存引用
        this.globalFilter = globalFilter;  // ✅ 保存GlobalFilter引用
        this.scanTaskCollector = new ScanTaskCollector(api, configManager); // ✅ 初始化收集器
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
                // 注意：HttpResponseReceived 可以直接作为 HttpResponse 使用（实现了 HttpResponse 接口）
                responseCache.put(
                    initiatingRequest,
                    (HttpResponse) responseReceived
                );
                
                // ✅ 2. 收集响应中的参数和关键词
                realtimeScanner.processResponse(
                    initiatingRequest, 
                    responseReceived
                );
                
                // ✅ 3. 触发被动扫描（此时原始响应已缓存）
                // 注意：主动探测的请求（Arjun 扫描和接口探测）使用 api.http().sendRequest() 发送，
                // 不会经过 Proxy，所以不会被 RequestHandler 捕获。
                // 主动探测的被动扫描规则控制已在 RealtimeScannerRefactored 中处理。
                if (xprobeConfigManager.isPassiveScanEnabled()) {
                    if (shouldScanRequest(initiatingRequest)) {
                        // 创建请求上下文
                        RequestContext context = new RequestContext(
                            responseReceived.toolSource().toolType().toString(),
                            initiatingRequest.method(),
                            initiatingRequest.url(),
                            initiatingRequest.toString().hashCode()
                        );
                        
                        // 收集扫描任务（传递响应用于过滤器检查）
                        List<ScanTask> scanTasks = scanTaskCollector.collectScanTasks(initiatingRequest, (HttpResponse) responseReceived, context);
                        
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
     * 检查请求是否应该扫描（重载：HttpRequest）
     * ✅ 检查顺序：基本URL检查 → GlobalFilter（包含静态资源过滤和黑白名单）
     */
    private boolean shouldScanRequest(HttpRequest request) {
        try {
            String url = request.url();
            
            // 1. 基本URL检查
            if (url == null || url.isEmpty()) {
                return false;
            }
            
            // ✅ 2. 使用GlobalFilter统一检查（包含静态资源过滤和黑白名单，已在GlobalFilter内部按正确顺序处理）
            if (globalFilter != null && !globalFilter.shouldProcessPassive(url)) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
}

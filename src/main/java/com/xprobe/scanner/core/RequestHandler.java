package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.models.RequestContext;
import com.xprobe.scanner.models.ScanTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 收集所有需要扫描的任务（重载：接受HttpRequest）
     * ✅ 支持从响应处理器触发被动扫描
     */
    private List<ScanTask> collectScanTasks(HttpRequest request, RequestContext context) {
        List<ScanTask> tasks = new ArrayList<>();
        
        // 获取所有参数
        List<ParsedHttpParameter> parameters = request.parameters();
        
        // 获取Content-Type
        String contentType = getContentType(request);
        
        // 遍历所有启用的配置
        for (Configuration config : configManager.getEnabledConfigurations()) {
            // 检查是否是配对架构
            if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                // 新架构：配对架构
                // 创建一个基于整个请求的扫描任务
                ScanTask task = new ScanTask(null, config, request, context);
                tasks.add(task);
            } else {
                // 旧架构：按参数匹配
                for (ParsedHttpParameter param : parameters) {
                    if (isParameterMatch(param.name(), config)) {
                        // 原子性检查并标记为扫描中（避免并发重复扫描）
                        if (checkAndMarkParameterAsScanning(request, param, config, contentType)) {
                            continue; // 跳过已扫描或正在扫描的参数
                        }
                        
                        ScanTask task = new ScanTask(param, config, request, context);
                        tasks.add(task);
                    }
                }
            }
        }
        
        return tasks;
    }
    
    /**
     * 收集所有需要扫描的任务（原版：接受HttpRequestToBeSent）
     * ✅ 支持新旧两种架构
     */
    private List<ScanTask> collectScanTasks(HttpRequestToBeSent request, RequestContext context) {
        List<ScanTask> tasks = new ArrayList<>();
        
        // 获取所有参数
        List<ParsedHttpParameter> parameters = request.parameters();
        
        // 获取Content-Type
        String contentType = getContentType(request);
        
        // 遍历所有启用的配置
        for (Configuration config : configManager.getEnabledConfigurations()) {
            // 检查是否是配对架构
            if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                // 新架构：配对架构
                // 创建一个基于整个请求的扫描任务
                ScanTask task = new ScanTask(null, config, request, context);
                tasks.add(task);
            } else {
                // 旧架构：按参数匹配
                for (ParsedHttpParameter param : parameters) {
                    if (isParameterMatch(param.name(), config)) {
                        // 原子性检查并标记为扫描中（避免并发重复扫描）
                        if (checkAndMarkParameterAsScanning(request, param, config, contentType)) {
                            continue; // 跳过已扫描或正在扫描的参数
                        }
                        
                        ScanTask task = new ScanTask(param, config, request, context);
                        tasks.add(task);
                    }
                }
            }
        }
        
        return tasks;
    }
    
    /**
     * 获取请求的Content-Type（重载：HttpRequest）
     */
    private String getContentType(HttpRequest request) {
        try {
            for (var header : request.headers()) {
                if ("Content-Type".equalsIgnoreCase(header.name())) {
                    return header.value();
                }
            }
            return "application/x-www-form-urlencoded";
        } catch (Exception e) {
            return "application/x-www-form-urlencoded";
        }
    }
    
    /**
     * 获取请求的Content-Type（原版：HttpRequestToBeSent）
     */
    private String getContentType(HttpRequestToBeSent request) {
        try {
            for (var header : request.headers()) {
                if ("Content-Type".equalsIgnoreCase(header.name())) {
                    return header.value();
                }
            }
            return "application/x-www-form-urlencoded";
        } catch (Exception e) {
            return "application/x-www-form-urlencoded";
        }
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
    
    /**
     * 检查并标记参数为扫描中状态（重载：HttpRequest）
     */
    private boolean checkAndMarkParameterAsScanning(HttpRequest request, ParsedHttpParameter param, 
                                            Configuration config, String contentType) {
        try {
            String url = request.url();
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            
            boolean alreadyProcessed = realtimeScanner.checkAndMarkPassiveScanProcessed(
                request.method(), 
                host, 
                path, 
                contentType, 
                param.name(),
                config);
            
            return alreadyProcessed;
        } catch (URISyntaxException e) {
            api.logging().raiseErrorEvent("Error parsing URL: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查并标记参数为扫描中状态（原版：HttpRequestToBeSent）
     * 如果已经扫描过或正在扫描中，返回 true；否则标记为扫描中并返回 false
     */
    private boolean checkAndMarkParameterAsScanning(HttpRequestToBeSent request, ParsedHttpParameter param, 
                                            Configuration config, String contentType) {
        try {
            // 从URL中提取host和path
            String url = request.url();
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            
            // ✅ 使用新的去重逻辑：支持颗粒度控制
            // 使用 DeduplicationKeyGenerator 生成去重key
            boolean alreadyProcessed = realtimeScanner.checkAndMarkPassiveScanProcessed(
                request.method(), 
                host, 
                path, 
                contentType, 
                param.name(),  // targetIdentifier
                config);       // 传递完整配置，支持颗粒度控制
            
            return alreadyProcessed;
        } catch (URISyntaxException | IllegalArgumentException e) {
            // 如果检查失败，默认不跳过（继续扫描）
            api.logging().raiseErrorEvent("检查参数扫描状态时出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查参数名是否匹配配置规则
     */
    private boolean isParameterMatch(String paramName, Configuration config) {
        String parameterNameType = config.getParameterNameType();
        List<String> parameterNames = config.getParameterNames();
        
        if ("String Match".equals(parameterNameType)) {
            // 字符串精确匹配
            return parameterNames.contains(paramName);
        } else if ("Regex Match".equals(parameterNameType)) {
            // 正则表达式匹配
            for (String regex : parameterNames) {
                try {
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(paramName);
                    if (matcher.matches()) {
                        return true;
                    }
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("Invalid regex pattern: " + regex);
                }
            }
        }
        
        return false;
    }
}

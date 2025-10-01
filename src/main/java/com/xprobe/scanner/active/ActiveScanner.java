package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.xprobe.scanner.config.ConfigurationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 主动扫描器 - 基于被动流量进行接口和参数探测
 */
public class ActiveScanner {
    private final MontoyaApi api;
    private final ExternalToolConfig toolConfig;
    private final RealtimeScannerRefactored realtimeScanner;

    /**
     * 构造函数 - 使用已有的 RealtimeScannerRefactored 实例
     * 
     * @param api Montoya API
     * @param configManager 配置管理器
     * @param realtimeScanner 已有的 RealtimeScannerRefactored 实例（避免创建多个实例导致数据不同步）
     */
    public ActiveScanner(MontoyaApi api, ConfigurationManager configManager, RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.toolConfig = new ExternalToolConfig();
        this.realtimeScanner = realtimeScanner;  // 使用传入的实例，不创建新的
    }

    /**
     * 基于被动流量扫描目标
     */
    public List<ScanResult> scanTarget(ScanTarget target) {
        List<ScanResult> results = new ArrayList<>();
        
        try {
            // 1. 从Burp站点地图收集该目标相关的被动流量
            List<HttpRequest> passiveRequests = collectPassiveRequestsForTarget(target);
            
            // 2. 参数探测由 RealtimeScanner 的 Arjun 集成负责
            //    不再在 ActiveScanner 中直接调用外部工具
            
            // 3. 添加收集统计信息到结果中
            results.add(new ScanResult(target, "统计信息", "收集请求数", "COLLECTION_STATS", 
                String.valueOf(passiveRequests.size()), ""));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("扫描目标 " + target.getUrl() + " 时出错: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 从Burp站点地图收集特定目标的被动流量
     */
    private List<HttpRequest> collectPassiveRequestsForTarget(ScanTarget target) {
        List<HttpRequest> requests = new ArrayList<>();
        
        try {
            String targetHost = new URI(target.getUrl()).getHost();
            SiteMap siteMap = api.siteMap();
            
            // 从站点地图中获取所有请求
            List<HttpRequestResponse> requestResponses = siteMap.requestResponses();
            
            int collectedCount = 0;
            for (HttpRequestResponse requestResponse : requestResponses) {
                String url = requestResponse.request().url();
                if (url.contains(targetHost)) {
                    requests.add(requestResponse.request());
                    collectedCount++;
                    api.logging().raiseInfoEvent("为目标 " + targetHost + " 发现相关URL: " + url);
                }
            }
            
            api.logging().raiseInfoEvent("为目标 " + targetHost + " 收集到 " + collectedCount + " 个相关请求");
            
        } catch (URISyntaxException | IllegalArgumentException e) {
            api.logging().raiseErrorEvent("收集目标 " + target.getUrl() + " 的被动流量时出错: " + e.getMessage());
        }
        
        return requests;
    }
    
    // 已删除所有未使用的探测方法
    // 参数探测由 RealtimeScanner 的 Arjun 集成负责
    // 接口和目录探测功能已废弃

    
    // 已删除所有未使用的辅助方法
    // 参数探测功能由 ArjunIntegration 类负责
    // 已删除 extractEvidence 方法，功能未使用

    /**
     * 异步扫描目标
     */
    public CompletableFuture<List<ScanResult>> scanTargetAsync(ScanTarget target) {
        return CompletableFuture.supplyAsync(() -> scanTarget(target));
    }

    /**
     * 检查URL是否有效
     */
    public boolean isValidUrl(String url) {
        try {
            new URI(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 已删除 extractApiEndpoints 方法，功能未使用
    
    /**
     * 获取外部工具配置
     */
    public ExternalToolConfig getToolConfig() {
        return toolConfig;
    }
    
    /**
     * 更新外部工具配置
     */
    public void updateToolConfig(ExternalToolConfig newConfig) {
        if (newConfig != null) {
            this.toolConfig.setArjunPath(newConfig.getArjunPath());
            this.toolConfig.setBurpProxyAddress(newConfig.getBurpProxyAddress());
            this.toolConfig.setThreadCount(newConfig.getThreadCount());
            this.toolConfig.setTimeout(newConfig.getTimeout());
            this.toolConfig.setCustomDictionary(newConfig.getCustomDictionary());
            this.toolConfig.setEnableJsonOutput(newConfig.isEnableJsonOutput());
            this.toolConfig.setEnableVerboseOutput(newConfig.isEnableVerboseOutput());
            this.toolConfig.setSendToBurp(newConfig.isSendToBurp());
        }
    }
    
    /**
     * 启动实时扫描
     */
    public void startRealtimeScanning() {
        realtimeScanner.startRealtimeScanning();
    }
    
    /**
     * 停止实时扫描
     */
    public void stopRealtimeScanning() {
        realtimeScanner.stopRealtimeScanning();
    }
    
    /**
     * 处理新的HTTP请求（从被动扫描器调用）
     */
    public void processNewRequest(HttpRequest request) {
        realtimeScanner.processNewRequest(request);
    }
    
    /**
     * 获取实时扫描器
     */
    public RealtimeScannerRefactored getRealtimeScanner() {
        return realtimeScanner;
    }
}

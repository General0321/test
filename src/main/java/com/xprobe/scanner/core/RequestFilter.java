package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;

import java.util.List;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 请求过滤器，负责过滤不需要扫描的请求
 */
public class RequestFilter {
    private final MontoyaApi api;
    private final GlobalFilter globalFilter;
    
    // 使用LRU缓存记录已处理的请求，防止重复扫描
    private final Set<Integer> processedRequests = Collections.synchronizedSet(
        Collections.newSetFromMap(
            new LinkedHashMap<Integer, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
                    return size() > 10000; // 限制最大10000个条目
                }
            }
        )
    );
    
    public RequestFilter(MontoyaApi api, GlobalFilter globalFilter) {
        this.api = api;
        this.globalFilter = globalFilter;
    }
    
    /**
     * 判断请求是否应该被扫描
     */
    public boolean shouldScan(HttpRequestToBeSent request) {
        // 1. 检查工具来源
        if (!isFromValidTool(request)) {
            return false;
        }
        
        // 2. 检查是否已处理
        int requestHash = request.toString().hashCode();
        if (processedRequests.contains(requestHash)) {
            return false;
        }
        
        // 3. 检查黑白名单
        if (!passBlackWhiteList(request)) {
            return false;
        }
        
        // 4. 记录已处理
        processedRequests.add(requestHash);
        
        return true;
    }
    
    /**
     * 检查请求是否来自有效的工具
     */
    private boolean isFromValidTool(HttpRequestToBeSent request) {
        return request.toolSource().isFromTool(ToolType.PROXY) || 
               request.toolSource().isFromTool(ToolType.REPEATER);
    }
    
    /**
     * 检查黑白名单
     */
    private boolean passBlackWhiteList(HttpRequestToBeSent request) {
        String url = request.url();
        return globalFilter.shouldProcessPassive(url);
    }
    
    /**
     * 更新白名单配置
     */
    public void updateWhitelist(List<String> whitelist, boolean enabled) {
        globalFilter.updateWhitelist(whitelist, enabled);
        api.logging().raiseInfoEvent("Whitelist updated: " + 
            (enabled ? "Enabled with " + whitelist.size() + " items" : "Disabled"));
    }
    
    /**
     * 更新黑名单配置
     */
    public void updateBlacklist(List<String> blacklist, boolean enabled) {
        globalFilter.updateBlacklist(blacklist, enabled);
        api.logging().raiseInfoEvent("Blacklist updated: " + 
            (enabled ? "Enabled with " + blacklist.size() + " items" : "Disabled"));
    }
    
    /**
     * 清空已处理的请求缓存
     */
    public void clearProcessedRequests() {
        processedRequests.clear();
        api.logging().raiseInfoEvent("Cleared processed requests cache");
    }
}

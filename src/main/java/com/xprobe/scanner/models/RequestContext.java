package com.xprobe.scanner.models;

import burp.api.montoya.core.ToolType;

/**
 * 请求上下文信息
 */
public class RequestContext {
    private final String toolSource;
    private final String method;
    private final String url;
    private final int requestHash;
    
    public RequestContext(String toolSource, String method, String url, int requestHash) {
        this.toolSource = toolSource;
        this.method = method;
        this.url = url;
        this.requestHash = requestHash;
    }
    
    public String getToolSource() {
        return toolSource;
    }
    
    public String getMethod() {
        return method;
    }
    
    public String getUrl() {
        return url;
    }
    
    public int getRequestHash() {
        return requestHash;
    }
}

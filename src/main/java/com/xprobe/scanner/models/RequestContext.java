package com.xprobe.scanner.models;

/**
 * 请求上下文信息
 */
public class RequestContext {
    private final String toolSource;
    private final String method;
    private final String url;
    private final int requestHash;
    private final boolean skipDeduplication;
    
    public RequestContext(String toolSource, String method, String url, int requestHash) {
        this(toolSource, method, url, requestHash, false);
    }
    
    public RequestContext(String toolSource, String method, String url, int requestHash, boolean skipDeduplication) {
        this.toolSource = toolSource;
        this.method = method;
        this.url = url;
        this.requestHash = requestHash;
        this.skipDeduplication = skipDeduplication;
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

    public boolean isSkipDeduplication() {
        return skipDeduplication;
    }
}

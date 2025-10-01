package com.xprobe.scanner.models;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import com.xprobe.scanner.config.Configuration;

/**
 * 扫描任务
 */
public class ScanTask {
    private final ParsedHttpParameter parameter;
    private final Configuration configuration;
    private final HttpRequestToBeSent request;
    private final RequestContext context;
    
    public ScanTask(ParsedHttpParameter parameter, Configuration configuration, 
                    HttpRequestToBeSent request, RequestContext context) {
        this.parameter = parameter;
        this.configuration = configuration;
        this.request = request;
        this.context = context;
    }
    
    public ParsedHttpParameter getParameter() {
        return parameter;
    }
    
    public Configuration getConfiguration() {
        return configuration;
    }
    
    public HttpRequestToBeSent getRequest() {
        return request;
    }
    
    public RequestContext getContext() {
        return context;
    }
    
    public String getScanType() {
        // ✅ 智能判断：配对架构使用UniversalScanner
        if (configuration.getPairs() != null && !configuration.getPairs().isEmpty()) {
            return com.xprobe.scanner.scanners.UniversalScanner.SCANNER_TYPE;
        }
        
        // 旧架构使用customLabel
        return configuration.getCustomLabel();
    }
}

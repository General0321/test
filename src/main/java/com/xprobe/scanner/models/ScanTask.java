package com.xprobe.scanner.models;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import com.xprobe.scanner.config.Configuration;

/**
 * 扫描任务
 * ✅ 修复：使用HttpRequest代替HttpRequestToBeSent，避免类型转换错误
 */
public class ScanTask {
    private final ParsedHttpParameter parameter;
    private final Configuration configuration;
    private final HttpRequest request;  // ✅ 改为HttpRequest
    private final RequestContext context;
    
    public ScanTask(ParsedHttpParameter parameter, Configuration configuration, 
                    HttpRequest request, RequestContext context) {
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
    
    public HttpRequest getRequest() {  // ✅ 改为HttpRequest
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

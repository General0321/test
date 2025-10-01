package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import com.xprobe.scanner.config.Configuration;
import java.util.List;

/**
 * 注入点执行器（简化版）
 * 负责在指定位置插入已解析的payload
 * 
 * 注意：payload已经通过PayloadVariableResolver解析，
 *       其中{{ORIGINAL}}等变量已被替换为实际值
 */
public class InjectionPointExecutor {
    
    /**
     * 获取注入点的原始值
     * 
     * @param request 原始请求
     * @param point 注入点
     * @return 原始值
     */
    public static String getOriginalValue(HttpRequest request,
                                         Configuration.InjectionPoint point) {
        if (point == null) {
            return "";
        }
        
        String pointType = point.getPointType();
        String targetName = point.getTargetName();
        
        if (pointType == null) {
            return "";
        }
        
        switch (pointType) {
            case "Parameter Value":
                return getParameterValue(request, targetName);
                
            case "URL Path":
                return request.path();
                
            case "URL Path Segment":
                return getPathSegmentValue(request, targetName);
                
            case "Request Header Value":
                return getHeaderValue(request, targetName);
                
            case "Request Body":
                return request.bodyToString();
                
            case "Cookie Value":
                return getCookieValue(request, targetName);
                
            case "Query String":
                return getQueryString(request);
                
            default:
                return "";
        }
    }
    
    /**
     * 应用单个注入（payload已解析）
     * 
     * @param request 原始请求
     * @param point 注入点
     * @param resolvedPayload 已解析的payload
     * @return 修改后的请求
     */
    public static HttpRequest applySingleInjection(HttpRequest request,
                                                  Configuration.InjectionPoint point,
                                                  String resolvedPayload) {
        if (point == null || resolvedPayload == null) {
            return request;
        }
        
        String pointType = point.getPointType();
        String targetName = point.getTargetName();
        
        if (pointType == null) {
            return request;
        }
        
        switch (pointType) {
            case "Parameter Value":
                return injectParameterValue(request, targetName, resolvedPayload);
                
            case "URL Path":
                return request.withPath(resolvedPayload);
                
            case "URL Path Segment":
                return injectUrlPathSegment(request, targetName, resolvedPayload);
                
            case "Request Header Value":
                return injectHeaderValue(request, targetName, resolvedPayload);
                
            case "Request Body":
                return request.withBody(resolvedPayload);
                
            case "Cookie Value":
                return injectCookieValue(request, targetName, resolvedPayload);
                
            case "Query String":
                return injectQueryString(request, resolvedPayload);
                
            default:
                return request;
        }
    }
    
    // ========== 获取原始值的辅助方法 ==========
    
    private static String getParameterValue(HttpRequest request, String paramNames) {
        if (paramNames == null || paramNames.isEmpty()) {
            return "";
        }
        
        String[] names = paramNames.split(",");
        for (String paramName : names) {
            paramName = paramName.trim();
            for (var param : request.parameters()) {
                if (param.name().equals(paramName)) {
                    return param.value();
                }
            }
        }
        return "";
    }
    
    private static String getPathSegmentValue(HttpRequest request, String segmentIndex) {
        try {
            String[] segments = request.path().split("/");
            int index = Integer.parseInt(segmentIndex);
            if (index >= 0 && index < segments.length) {
                return segments[index];
            }
        } catch (NumberFormatException e) {
            // Invalid index
        }
        return "";
    }
    
    private static String getHeaderValue(HttpRequest request, String headerName) {
        if (headerName == null || headerName.isEmpty()) {
            return "";
        }
        return request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase(headerName))
            .findFirst()
            .map(h -> h.value())
            .orElse("");
    }
    
    private static String getCookieValue(HttpRequest request, String cookieName) {
        if (cookieName == null || cookieName.isEmpty()) {
            return "";
        }
        
        for (var param : request.parameters()) {
            if (param.name().equals(cookieName) && 
                param.type() == HttpParameterType.COOKIE) {
                return param.value();
            }
        }
        return "";
    }
    
    private static String getQueryString(HttpRequest request) {
        String path = request.path();
        if (path.contains("?")) {
            String[] parts = path.split("\\?", 2);
            return parts.length > 1 ? parts[1] : "";
        }
        return "";
    }
    
    // ========== 注入方法（简化版）==========
    
    /**
     * 注入参数值（payload已解析）
     */
    private static HttpRequest injectParameterValue(HttpRequest request,
                                                   String paramNames,
                                                   String resolvedPayload) {
        if (paramNames == null || paramNames.isEmpty()) {
            return request;
        }
        
        String[] names = paramNames.split(",");
        HttpRequest modifiedRequest = request;
        
        for (String paramName : names) {
            paramName = paramName.trim();
            
            // 查找该参数
            for (var param : request.parameters()) {
                if (param.name().equals(paramName)) {
                    // 直接使用已解析的payload
                    HttpParameter newParam = HttpParameter.parameter(
                        paramName, resolvedPayload, param.type()
                    );
                    modifiedRequest = modifiedRequest.withUpdatedParameters(newParam);
                    break;  // 只处理第一个匹配的参数
                }
            }
        }
        
        return modifiedRequest;
    }
    
    /**
     * 注入URL路径段（payload已解析）
     */
    private static HttpRequest injectUrlPathSegment(HttpRequest request,
                                                   String segmentIndex,
                                                   String resolvedPayload) {
        String originalPath = request.path();
        String[] segments = originalPath.split("/");
        
        try {
            int index = Integer.parseInt(segmentIndex);
            if (index >= 0 && index < segments.length) {
                segments[index] = resolvedPayload;
                String newPath = String.join("/", segments);
                return request.withPath(newPath);
            }
        } catch (NumberFormatException e) {
            // Invalid index, return original request
        }
        
        return request;
    }
    
    /**
     * 注入请求头值（payload已解析）
     */
    private static HttpRequest injectHeaderValue(HttpRequest request,
                                                String headerName,
                                                String resolvedPayload) {
        if (headerName == null || headerName.isEmpty()) {
            return request;
        }
        
        return request.withUpdatedHeader(headerName, resolvedPayload);
    }
    
    /**
     * 注入Cookie值（payload已解析）
     */
    private static HttpRequest injectCookieValue(HttpRequest request,
                                                String cookieName,
                                                String resolvedPayload) {
        if (cookieName == null || cookieName.isEmpty()) {
            return request;
        }
        
        // 找到Cookie参数并修改
        for (var param : request.parameters()) {
            if (param.name().equals(cookieName) && 
                param.type() == HttpParameterType.COOKIE) {
                HttpParameter newParam = HttpParameter.parameter(
                    cookieName, resolvedPayload, HttpParameterType.COOKIE
                );
                return request.withUpdatedParameters(newParam);
            }
        }
        
        return request;
    }
    
    /**
     * 注入查询字符串（payload已解析）
     */
    private static HttpRequest injectQueryString(HttpRequest request,
                                                String resolvedPayload) {
        String newPath = request.path();
        
        if (newPath.contains("?")) {
            newPath = newPath.split("\\?")[0] + "?" + resolvedPayload;
        } else {
            newPath = newPath + "?" + resolvedPayload;
        }
        
        return request.withPath(newPath);
    }
}


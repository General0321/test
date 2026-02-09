package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;

import java.net.URI;
import java.util.List;

/**
 * 规则过滤器工具类
 * 用于检查请求和响应是否应该被规则过滤器过滤
 */
public class RuleFilterHelper {
    
    /**
     * 检查请求和响应是否应该被过滤
     * 
     * @param request HTTP请求
     * @param response HTTP响应（可能为null）
     * @param filter 规则过滤器
     * @return true 如果应该被过滤（排除），false 如果应该被检测
     */
    public static boolean shouldFilter(HttpRequest request, HttpResponse response, Configuration.RuleFilter filter) {
        if (filter == null || !filter.isEnabled()) {
            return false;  // 过滤器未启用，不过滤
        }
        
        // 根据模式判断
        if (filter.getMode() == Configuration.RuleFilter.FilterMode.BLACKLIST) {
            // 黑名单模式：任一条件匹配则排除
            return checkAnyFilter(request, response, filter);
        } else {
            // 白名单模式：所有条件都匹配才通过（否则排除）
            return !checkAllFilters(request, response, filter);
        }
    }
    
    /**
     * 黑名单模式：检查是否有任一条件匹配（匹配则排除）
     */
    private static boolean checkAnyFilter(HttpRequest request, HttpResponse response, Configuration.RuleFilter filter) {
        // 如果任一启用的过滤条件匹配，则排除
        if (filter.isFilterRequestContentType() && checkRequestContentType(request, filter)) {
            return true;
        }
        if (filter.isFilterRequestMethod() && checkRequestMethod(request, filter)) {
            return true;
        }
        if (filter.isFilterResponseContentType() && response != null && checkResponseContentType(response, filter)) {
            return true;
        }
        if (filter.isFilterResponseStatusCode() && response != null && checkResponseStatusCode(response, filter)) {
            return true;
        }
        if (filter.isFilterFileExtension() && checkFileExtension(request, filter)) {
            return true;
        }
        return false;
    }
    
    /**
     * 白名单模式：检查是否所有条件都匹配（都匹配才通过）
     * ✅ 修复：如果启用了某个条件但列表为空，该条件视为"不参与过滤"（不影响结果）
     */
    private static boolean checkAllFilters(HttpRequest request, HttpResponse response, Configuration.RuleFilter filter) {
        // 如果所有启用的过滤条件都匹配，才通过
        boolean allMatch = true;
        boolean hasAnyFilter = false;  // 是否有任何启用的过滤条件（且列表不为空）
        
        if (filter.isFilterRequestContentType()) {
            List<String> filterTypes = filter.getRequestContentTypes();
            if (filterTypes != null && !filterTypes.isEmpty()) {
                hasAnyFilter = true;
                allMatch = allMatch && checkRequestContentType(request, filter);
            }
            // ✅ 修复：如果列表为空，该条件不参与过滤（不影响allMatch）
        }
        if (filter.isFilterRequestMethod()) {
            List<String> filterMethods = filter.getRequestMethods();
            if (filterMethods != null && !filterMethods.isEmpty()) {
                hasAnyFilter = true;
                allMatch = allMatch && checkRequestMethod(request, filter);
            }
            // ✅ 修复：如果列表为空，该条件不参与过滤（不影响allMatch）
        }
        if (filter.isFilterResponseContentType()) {
            List<String> filterTypes = filter.getResponseContentTypes();
            if (filterTypes != null && !filterTypes.isEmpty()) {
                hasAnyFilter = true;
                if (response != null) {
                    allMatch = allMatch && checkResponseContentType(response, filter);
                } else {
                    // 如果启用了响应Content-Type过滤但没有响应，则不匹配
                    allMatch = false;
                }
            }
            // ✅ 修复：如果列表为空，该条件不参与过滤（不影响allMatch）
        }
        if (filter.isFilterResponseStatusCode()) {
            List<Integer> statusCodes = filter.getResponseStatusCodes();
            Configuration.RuleFilter.StatusCodeRange range = filter.getStatusCodeRange();
            boolean hasStatusCodeFilter = (statusCodes != null && !statusCodes.isEmpty()) ||
                                         (range != null && range.isEnabled());
            if (hasStatusCodeFilter) {
                hasAnyFilter = true;
                if (response != null) {
                    allMatch = allMatch && checkResponseStatusCode(response, filter);
                } else {
                    // 如果启用了响应状态码过滤但没有响应，则不匹配
                    allMatch = false;
                }
            }
            // ✅ 修复：如果列表为空且范围未启用，该条件不参与过滤（不影响allMatch）
        }
        if (filter.isFilterFileExtension()) {
            List<String> filterExtensions = filter.getFileExtensions();
            if (filterExtensions != null && !filterExtensions.isEmpty()) {
                hasAnyFilter = true;
                allMatch = allMatch && checkFileExtension(request, filter);
            }
            // ✅ 修复：如果列表为空，该条件不参与过滤（不影响allMatch）
        }
        
        // ✅ 如果没有任何启用的过滤条件（或所有条件的列表都为空），白名单模式应该拒绝所有请求（安全默认行为）
        if (!hasAnyFilter) {
            return false;
        }
        
        return allMatch;
    }
    
    /**
     * 检查请求Content-Type
     */
    private static boolean checkRequestContentType(HttpRequest request, Configuration.RuleFilter filter) {
        String contentType = getRequestContentType(request);
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        
        List<String> filterTypes = filter.getRequestContentTypes();
        if (filterTypes == null || filterTypes.isEmpty()) {
            return false;
        }
        
        String lowerContentType = contentType.toLowerCase();
        for (String filterType : filterTypes) {
            if (filterType != null && !filterType.isEmpty()) {
                // 支持部分匹配（如 application/json 匹配 application/json; charset=utf-8）
                if (lowerContentType.contains(filterType.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查请求方法
     */
    private static boolean checkRequestMethod(HttpRequest request, Configuration.RuleFilter filter) {
        String method = request.method();
        if (method == null || method.isEmpty()) {
            return false;
        }
        
        List<String> filterMethods = filter.getRequestMethods();
        if (filterMethods == null || filterMethods.isEmpty()) {
            return false;
        }
        
        String upperMethod = method.toUpperCase();
        for (String filterMethod : filterMethods) {
            if (filterMethod != null && !filterMethod.isEmpty()) {
                if (upperMethod.equals(filterMethod.toUpperCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查响应Content-Type
     */
    private static boolean checkResponseContentType(HttpResponse response, Configuration.RuleFilter filter) {
        String contentType = getResponseContentType(response);
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        
        List<String> filterTypes = filter.getResponseContentTypes();
        if (filterTypes == null || filterTypes.isEmpty()) {
            return false;
        }
        
        String lowerContentType = contentType.toLowerCase();
        for (String filterType : filterTypes) {
            if (filterType != null && !filterType.isEmpty()) {
                // 支持部分匹配（如 text/html 匹配 text/html; charset=utf-8）
                if (lowerContentType.contains(filterType.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查响应状态码
     */
    private static boolean checkResponseStatusCode(HttpResponse response, Configuration.RuleFilter filter) {
        int statusCode = response.statusCode();
        
        // 1. 检查状态码列表
        List<Integer> statusCodes = filter.getResponseStatusCodes();
        if (statusCodes != null && !statusCodes.isEmpty()) {
            for (Integer code : statusCodes) {
                if (code != null && code == statusCode) {
                    return true;
                }
            }
        }
        
        // 2. 检查状态码范围
        Configuration.RuleFilter.StatusCodeRange range = filter.getStatusCodeRange();
        if (range != null && range.isEnabled() && range.getMin() != null && range.getMax() != null) {
            if (statusCode >= range.getMin() && statusCode <= range.getMax()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查文件后缀名
     */
    private static boolean checkFileExtension(HttpRequest request, Configuration.RuleFilter filter) {
        String url = request.url();
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String extension = extractFileExtension(url);
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        
        List<String> filterExtensions = filter.getFileExtensions();
        if (filterExtensions == null || filterExtensions.isEmpty()) {
            return false;
        }
        
        String lowerExtension = extension.toLowerCase();
        for (String filterExt : filterExtensions) {
            if (filterExt != null && !filterExt.isEmpty()) {
                // 去除点号（.js -> js）
                String cleanExt = filterExt.startsWith(".") ? filterExt.substring(1) : filterExt;
                if (lowerExtension.equals(cleanExt.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取请求Content-Type
     */
    private static String getRequestContentType(HttpRequest request) {
        if (request == null) {
            return null;
        }
        return request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取响应Content-Type
     */
    private static String getResponseContentType(HttpResponse response) {
        if (response == null) {
            return null;
        }
        return response.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 从URL中提取文件后缀名
     * ✅ 修复：改进回退逻辑，正确处理URL的path部分
     */
    private static String extractFileExtension(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            
            if (path == null || path.isEmpty() || path.equals("/")) {
                return null;
            }
            
            // 获取文件扩展名
            int lastDot = path.lastIndexOf('.');
            int lastSlash = path.lastIndexOf('/');
            
            // 确保.在/之后（是扩展名，不是域名中的.）
            if (lastDot > lastSlash && lastDot < path.length() - 1) {
                return path.substring(lastDot + 1).toLowerCase();
            }
        } catch (Exception e) {
            // ✅ 修复：如果URI解析失败，使用改进的字符串匹配作为回退
            // 需要正确提取path部分，避免匹配到域名中的点
            String lowerUrl = url.toLowerCase();
            
            // 移除查询参数和fragment
            int queryIndex = lowerUrl.indexOf('?');
            int fragmentIndex = lowerUrl.indexOf('#');
            if (queryIndex != -1) {
                lowerUrl = lowerUrl.substring(0, queryIndex);
            } else if (fragmentIndex != -1) {
                lowerUrl = lowerUrl.substring(0, fragmentIndex);
            }
            
            // 查找path开始位置（第一个/在协议之后，或第一个/）
            int protocolIndex = lowerUrl.indexOf("://");
            int pathStart = -1;
            if (protocolIndex != -1) {
                pathStart = lowerUrl.indexOf('/', protocolIndex + 3);
            } else {
                pathStart = lowerUrl.indexOf('/');
            }
            
            if (pathStart != -1) {
                String path = lowerUrl.substring(pathStart);
                int lastDot = path.lastIndexOf('.');
                int lastSlash = path.lastIndexOf('/');
                
                // 确保.在/之后（是扩展名，不是域名中的.）
                if (lastDot > lastSlash && lastDot < path.length() - 1) {
                    return path.substring(lastDot + 1);
                }
            } else {
                // 没有path部分，尝试在整个URL中查找（但需要确保不是域名中的点）
                // 这种情况很少见，但为了完整性还是处理
                int lastDot = lowerUrl.lastIndexOf('.');
                int protocolIndex2 = lowerUrl.indexOf("://");
                int domainEnd = -1;
                if (protocolIndex2 != -1) {
                    domainEnd = lowerUrl.indexOf('/', protocolIndex2 + 3);
                    if (domainEnd == -1) {
                        domainEnd = lowerUrl.length();
                    }
                } else {
                    domainEnd = lowerUrl.indexOf('/');
                    if (domainEnd == -1) {
                        domainEnd = lowerUrl.length();
                    }
                }
                
                // 确保点在域名之后
                if (lastDot >= domainEnd && lastDot < lowerUrl.length() - 1) {
                    return lowerUrl.substring(lastDot + 1);
                }
            }
        }
        
        return null;
    }
}


package com.xprobe.scanner.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 静态资源过滤器
 * ✅ 用于过滤CSS、图片、字体等静态资源
 * ✅ JS文件不被过滤（因为可能包含敏感信息）
 */
public class StaticResourceFilter {
    
    // 静态资源扩展名（不包括.js）
    private static final Set<String> STATIC_EXTENSIONS = new HashSet<>(Arrays.asList(
        // 样式
        "css", "scss", "sass", "less",
        // 图片
        "jpg", "jpeg", "png", "gif", "bmp", "svg", "ico", "webp",
        // 字体
        "woff", "woff2", "ttf", "eot", "otf",
        // 视频/音频
        "mp4", "avi", "mov", "wmv", "flv", "mp3", "wav", "ogg",
        // 文档（通常是静态下载）
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        // 其他
        "zip", "rar", "tar", "gz", "7z",
        "swf", "map"
    ));
    
    /**
     * 检查URL是否是静态资源
     * @param url 要检查的URL
     * @return true 如果是静态资源（应该被过滤）
     */
    public static boolean isStaticResource(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        try {
            // ✅ 修复：使用URI解析，提取path部分（不包括协议、域名、查询参数）
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            
            // 如果path为null或为空，尝试从URL中提取path部分
            if (path == null || path.isEmpty() || path.equals("/")) {
                // 回退方案：从URL中提取path部分
                // 移除查询参数和fragment
                String urlWithoutQuery = url;
                int queryIndex = url.indexOf('?');
                int fragmentIndex = url.indexOf('#');
                
                if (queryIndex != -1) {
                    urlWithoutQuery = url.substring(0, queryIndex);
                } else if (fragmentIndex != -1) {
                    urlWithoutQuery = url.substring(0, fragmentIndex);
                }
                
                // 查找path开始位置（第一个/在协议之后）
                int protocolIndex = urlWithoutQuery.indexOf("://");
                if (protocolIndex != -1) {
                    int pathStart = urlWithoutQuery.indexOf('/', protocolIndex + 3);
                    if (pathStart != -1) {
                        path = urlWithoutQuery.substring(pathStart);
                    } else {
                        path = "/";
                    }
                } else {
                    // 没有协议，直接查找第一个/
                    int pathStart = urlWithoutQuery.indexOf('/');
                    if (pathStart != -1) {
                        path = urlWithoutQuery.substring(pathStart);
                    } else {
                        path = "/";
                    }
                }
            }
            
            // 获取文件扩展名
            int lastDot = path.lastIndexOf('.');
            int lastSlash = path.lastIndexOf('/');
            
            // 确保.在/之后（是扩展名，不是域名中的.）
            if (lastDot > lastSlash && lastDot < path.length() - 1) {
                String extension = path.substring(lastDot + 1).toLowerCase();
                return STATIC_EXTENSIONS.contains(extension);
            }
        } catch (Exception e) {
            // 如果URI解析失败，使用简单的字符串匹配作为回退
            // 检查path中是否包含静态资源后缀
            String lowerUrl = url.toLowerCase();
            
            // 移除查询参数和fragment
            int queryIndex = lowerUrl.indexOf('?');
            int fragmentIndex = lowerUrl.indexOf('#');
            if (queryIndex != -1) {
                lowerUrl = lowerUrl.substring(0, queryIndex);
            } else if (fragmentIndex != -1) {
                lowerUrl = lowerUrl.substring(0, fragmentIndex);
            }
            
            // 查找path部分（第一个/在协议之后，或第一个/）
            int protocolIndex = lowerUrl.indexOf("://");
            int pathStart = -1;
            if (protocolIndex != -1) {
                pathStart = lowerUrl.indexOf('/', protocolIndex + 3);
            } else {
                pathStart = lowerUrl.indexOf('/');
            }
            
            if (pathStart != -1) {
                String path = lowerUrl.substring(pathStart);
                // 检查path中是否以静态资源扩展名结尾
                for (String ext : STATIC_EXTENSIONS) {
                    if (path.endsWith("." + ext) || path.contains("." + ext + "?")) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查URL是否应该被Arjun扫描
     * @param url 要检查的URL
     * @return true 如果应该被扫描
     */
    public static boolean shouldScanWithArjun(String url) {
        // Arjun应该排除所有静态资源（包括JS）
        if (isStaticResource(url)) {
            return false;
        }
        
        // 额外排除JS文件
        if (url == null || url.isEmpty()) {
            return true;
        }
        
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            
            if (path == null || path.isEmpty() || path.equals("/")) {
                return true;
            }
            
            int lastDot = path.lastIndexOf('.');
            int lastSlash = path.lastIndexOf('/');
            
            if (lastDot > lastSlash && lastDot < path.length() - 1) {
                String extension = path.substring(lastDot + 1).toLowerCase();
                return !extension.equals("js");
            }
        } catch (Exception e) {
            // 解析失败，使用简单检查
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.endsWith(".js") || lowerUrl.contains(".js?")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查URL是否应该收集参数
     * @param url 要检查的URL
     * @return true 如果应该收集参数
     */
    public static boolean shouldCollectParameters(String url) {
        // 参数收集：排除静态资源，但保留JS
        return !isStaticResource(url);
    }
}


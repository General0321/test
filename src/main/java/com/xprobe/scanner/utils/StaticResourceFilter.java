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
        
        // 移除查询参数
        String path = url.split("\\?")[0];
        
        // 获取文件扩展名
        int lastDot = path.lastIndexOf('.');
        int lastSlash = path.lastIndexOf('/');
        
        // 确保.在/之后（是扩展名，不是域名中的.）
        if (lastDot > lastSlash && lastDot < path.length() - 1) {
            String extension = path.substring(lastDot + 1).toLowerCase();
            return STATIC_EXTENSIONS.contains(extension);
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
        
        String path = url.split("\\?")[0];
        int lastDot = path.lastIndexOf('.');
        int lastSlash = path.lastIndexOf('/');
        
        if (lastDot > lastSlash && lastDot < path.length() - 1) {
            String extension = path.substring(lastDot + 1).toLowerCase();
            return !extension.equals("js");
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


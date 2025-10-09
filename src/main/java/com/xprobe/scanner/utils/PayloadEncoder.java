package com.xprobe.scanner.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * ✅ P0修复: Payload编码工具类
 * 
 * 用于根据不同的Content-Type正确编码payload,防止特殊字符破坏请求格式
 */
public class PayloadEncoder {
    
    /**
     * URL编码
     * 
     * 用于GET参数和POST表单参数
     * 
     * @param value 原始值
     * @return URL编码后的值
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 always supported
            return value;
        }
    }
    
    /**
     * HTML实体编码
     * 
     * 用于HTML属性值等场景
     * 
     * @param value 原始值
     * @return HTML编码后的值
     */
    public static String htmlEncode(String value) {
        if (value == null) {
            return "";
        }
        
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * JSON字符串转义
     * 
     * 注意: Jackson会自动处理,通常无需手动调用此方法
     * 
     * @param value 原始值
     * @return JSON转义后的值
     */
    public static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * 根据Content-Type自动选择编码方式
     * 
     * @param value 原始值
     * @param contentType Content-Type头
     * @param paramType 参数类型 (url/body/header)
     * @return 编码后的值
     */
    public static String autoEncode(String value, String contentType, String paramType) {
        if (value == null) {
            return "";
        }
        
        // JSON类型: 由Jackson自动处理,不需要手动编码
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            return value;  // Jackson会自动转义
        }
        
        // URL参数: 始终使用URL编码
        if ("url".equalsIgnoreCase(paramType)) {
            return urlEncode(value);
        }
        
        // POST表单: 使用URL编码
        if ("body".equalsIgnoreCase(paramType) && 
            (contentType == null || contentType.toLowerCase().contains("application/x-www-form-urlencoded"))) {
            return urlEncode(value);
        }
        
        // Header: 通常不需要编码,但移除换行符防止Header注入
        if ("header".equalsIgnoreCase(paramType)) {
            return value.replace("\r", "").replace("\n", "");
        }
        
        // 默认: 不编码
        return value;
    }
    
    /**
     * 检查payload是否包含需要编码的特殊字符
     * 
     * @param value 待检查的值
     * @return true 如果包含特殊字符
     */
    public static boolean needsEncoding(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // 检查常见需要编码的字符
        return value.contains("&") || 
               value.contains("=") ||
               value.contains("%") ||
               value.contains("+") ||
               value.contains(" ") ||
               value.contains("?") ||
               value.contains("#");
    }
    
    /**
     * URL解码
     * 
     * @param value URL编码的值
     * @return 解码后的值
     */
    public static String urlDecode(String value) {
        if (value == null) {
            return "";
        }
        
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 always supported
            return value;
        } catch (IllegalArgumentException e) {
            // 解码失败,返回原值
            return value;
        }
    }
}

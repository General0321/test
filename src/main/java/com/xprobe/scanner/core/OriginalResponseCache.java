package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原始响应缓存（LRU策略）
 * 用于快速查找请求对应的原始响应，避免遍历Proxy History
 */
public class OriginalResponseCache {
    private final Map<String, HttpResponse> cache;
    private final int maxSize;

    /**
     * 创建缓存
     * @param maxSize 最大缓存条目数（推荐1000-5000）
     */
    public OriginalResponseCache(int maxSize) {
        this.maxSize = maxSize;
        // LinkedHashMap with access-order (LRU)
        this.cache = new LinkedHashMap<String, HttpResponse>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, HttpResponse> eldest) {
                return size() > OriginalResponseCache.this.maxSize;
            }
        };
    }

    /**
     * 生成缓存key（旧版兼容：method|url）
     * @param method HTTP方法
     * @param url 完整URL
     * @return 缓存key
     */
    private String generateKey(String method, String url) {
        return (method == null ? "" : method) + "|" + (url == null ? "" : url);
    }

    /**
     * 生成缓存key（增强版）：method|url|contentType|bodyHash
     */
    private String generateKey(HttpRequest request) {
        if (request == null) {
            return "|"; // 最小占位
        }
        String method = safe(request.method());
        String url = safe(request.url());
        String contentType = normalizeContentType(getContentType(request));
        String bodyHash = computeBodyHash(request);
        return method + "|" + url + "|" + contentType + "|" + bodyHash;
    }

    /** 获取请求的 Content-Type */
    private String getContentType(HttpRequest request) {
        try {
            for (var header : request.headers()) {
                if ("Content-Type".equalsIgnoreCase(header.name())) {
                    return header.value();
                }
            }
        } catch (Exception ignored) {
        }
        return "application/x-www-form-urlencoded"; // 默认
    }

    /** 规范化 Content-Type（去掉分号后参数，并归一化常见类型） */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "application/x-www-form-urlencoded";
        String lower = contentType.toLowerCase().trim();
        int p = lower.indexOf(';');
        if (p > 0) lower = lower.substring(0, p).trim();
        if (lower.contains("json")) return "application/json";
        if (lower.contains("xml")) return "application/xml";
        if (lower.contains("multipart")) return "multipart/form-data";
        if (lower.contains("form")) return "application/x-www-form-urlencoded";
        return lower;
    }

    /**
     * 计算请求体的SHA-256哈希（无body时返回固定占位符）
     */
    private String computeBodyHash(HttpRequest request) {
        try {
            String body = request.bodyToString();
            if (body == null || body.isEmpty()) {
                return "NOBODY";
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            // 降级：使用请求字符串的hashCode
            try {
                return "ERR-" + Integer.toHexString(request.toString().hashCode());
            } catch (Exception ex) {
                return "ERR";
            }
        }
    }

    private String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * 缓存响应（旧版兼容）
     * @param method HTTP方法
     * @param url 完整URL
     * @param response 原始响应
     */
    public synchronized void put(String method, String url, HttpResponse response) {
        if (method == null || url == null || response == null) {
            return;
        }
        String key = generateKey(method, url);
        cache.put(key, response);
    }

    /**
     * 缓存响应（增强版，推荐）
     */
    public synchronized void put(HttpRequest request, HttpResponse response) {
        if (request == null || response == null) {
            return;
        }
        String key = generateKey(request);
        cache.put(key, response);
    }

    /**
     * 查找响应（旧版兼容）
     * @param method HTTP方法
     * @param url 完整URL
     * @return 找到的响应，如果不存在则返回null
     */
    public synchronized HttpResponse get(String method, String url) {
        if (method == null || url == null) {
            return null;
        }
        String key = generateKey(method, url);
        return cache.get(key);
    }

    /**
     * 查找响应（增强版，推荐）
     */
    public synchronized HttpResponse get(HttpRequest request) {
        if (request == null) {
            return null;
        }
        String key = generateKey(request);
        HttpResponse resp = cache.get(key);
        if (resp != null) {
            return resp;
        }
        // 向后兼容回退：尝试旧键（method|url），尽量提高命中率
        return cache.get(generateKey(request.method(), request.url()));
    }

    /** 获取缓存大小 */
    public synchronized int size() {
        return cache.size();
    }

    /** 清空缓存 */
    public synchronized void clear() {
        cache.clear();
    }
}

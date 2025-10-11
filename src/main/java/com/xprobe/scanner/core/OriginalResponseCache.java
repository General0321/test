package com.xprobe.scanner.core;

import burp.api.montoya.http.message.responses.HttpResponse;

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
     * 生成缓存key
     * @param method HTTP方法
     * @param url 完整URL
     * @return 缓存key
     */
    private String generateKey(String method, String url) {
        return method + "|" + url;
    }
    
    /**
     * 缓存响应
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
     * 查找响应
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
     * 获取缓存大小
     */
    public synchronized int size() {
        return cache.size();
    }
    
    /**
     * 清空缓存
     */
    public synchronized void clear() {
        cache.clear();
    }
}


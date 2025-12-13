package com.xprobe.scanner.active.headers;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpHeader;
import com.xprobe.scanner.config.XProbeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动探测请求头管理器
 * - 按主机(host)维度缓存最新请求头
 * - 应用配置中心中设置的自定义请求头（优先级最高，自动覆盖）
 * - 提供更新与应用两个核心方法
 */
public class ActiveProbeHeaderManager {
    private final MontoyaApi api;
    private final XProbeConfig xprobeConfig; // 用于获取配置中心的自定义请求头

    // 按主机缓存最新请求头：host -> List<HttpHeader>
    private final Map<String, List<HttpHeader>> hostHeadersCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10_000; // 最多缓存10000个主机

    public ActiveProbeHeaderManager(MontoyaApi api, XProbeConfig xprobeConfig) {
        this.api = api;
        this.xprobeConfig = xprobeConfig;
    }

    /**
     * 从请求中更新该主机的最新请求头缓存
     */
    public void updateFromRequest(String host, HttpRequest request) {
        if (host == null || host.isEmpty() || request == null) return;
        try {
            if (hostHeadersCache.size() >= MAX_CACHE_SIZE) {
                // 简单的半量清理策略，避免内存膨胀
                int toRemove = MAX_CACHE_SIZE / 2;
                int removed = 0;
                for (String k : hostHeadersCache.keySet()) {
                    hostHeadersCache.remove(k);
                    if (++removed >= toRemove) break;
                }
                api.logging().raiseDebugEvent("请求头主机缓存达到上限，已清理一半");
            }
            hostHeadersCache.put(host, new ArrayList<>(request.headers()));
        } catch (Exception e) {
            api.logging().raiseDebugEvent("更新主机请求头缓存失败: " + e.getMessage());
        }
    }

    /**
     * 将最新请求头与配置中心的自定义请求头应用到目标请求
     * 优先级：配置中心自定义请求头 > 主机缓存请求头 > 原始请求头
     * 跳过：Host、Content-Length（由底层自动处理）
     * 注意：允许配置中心覆盖 Content-Type（用户明确设置即视为有意覆盖）
     */
    public HttpRequest applyTo(HttpRequest targetRequest, String host) {
        if (targetRequest == null) return null;
        HttpRequest result = targetRequest;
        try {
            // 1) 先应用主机缓存的最新请求头（作为基础，会覆盖原始请求中的同名头）
            List<HttpHeader> latest = hostHeadersCache.get(host);
            if (latest != null && !latest.isEmpty()) {
                for (HttpHeader h : latest) {
                    String name = h.name();
                    if ("Host".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) continue;
                    boolean exists = false;
                    for (HttpHeader th : result.headers()) {
                        if (name.equalsIgnoreCase(th.name())) { exists = true; break; }
                    }
                    if (exists) result = result.withUpdatedHeader(name, h.value());
                    else result = result.withAddedHeader(name, h.value());
                }
            }

            // 2) 再应用配置中心的自定义请求头（最高优先级，覆盖上一步的同名头）
            Map<String, String> custom = (xprobeConfig != null) ? xprobeConfig.getArjunCustomHeaders() : null;
            if (custom != null && !custom.isEmpty()) {
                for (Map.Entry<String, String> e : custom.entrySet()) {
                    String name = e.getKey();
                    if (name == null || name.isBlank()) continue;
                    if ("Host".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) continue;
                    String value = e.getValue() != null ? e.getValue() : "";
                    boolean exists = false;
                    for (HttpHeader th : result.headers()) {
                        if (name.equalsIgnoreCase(th.name())) { exists = true; break; }
                    }
                    if (exists) result = result.withUpdatedHeader(name, value);
                    else result = result.withAddedHeader(name, value);
                }
            }
            return result;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("应用请求头失败: " + e.getMessage());
            return targetRequest;
        }
    }
}




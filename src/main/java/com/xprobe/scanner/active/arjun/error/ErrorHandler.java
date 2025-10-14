package com.xprobe.scanner.active.arjun.error;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.model.BaselineFactors;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 错误处理器 - 对应Python的error_handler.py
 * 
 * 核心功能：
 * 1. 检测HTTP错误状态码（400/413/418/429/503）
 * 2. 处理超时（自动增加超时时间）
 * 3. 处理连接拒绝（rate limit检测）
 * 4. 决定是否重试、继续或终止
 */
public class ErrorHandler {
    
    private final MontoyaApi api;
    
    // ⚠️ 不健康的状态码（来自Python）
    private static final Set<Integer> UNHEALTHY_CODES = Set.of(400, 413, 418, 429, 503);
    
    // 配置
    private volatile int currentTimeout;       // 当前超时时间（秒）
    private final int maxTimeout;              // 最大超时时间（秒）
    private final boolean stableMode;          // 稳定模式
    
    // 状态
    private final AtomicInteger badRequestCount;   // 400错误计数
    private final AtomicBoolean killSwitch;        // 全局终止开关
    
    /**
     * 处理结论枚举
     */
    public enum Conclusion {
        OK,      // 继续正常处理
        RETRY,   // 重试当前请求
        KILL     // 终止扫描
    }
    
    public ErrorHandler(MontoyaApi api, int initialTimeout, int maxTimeout, boolean stableMode) {
        this.api = api;
        this.currentTimeout = initialTimeout;
        this.maxTimeout = maxTimeout;
        this.stableMode = stableMode;
        this.badRequestCount = new AtomicInteger(0);
        this.killSwitch = new AtomicBoolean(false);
    }
    
    /**
     * ✅ 重置错误处理器状态（用于新的扫描）
     * 功能：清除 killSwitch 和错误计数，允许开始新的扫描
     */
    public void reset() {
        killSwitch.set(false);
        badRequestCount.set(0);
        currentTimeout = 15;  // 重置为初始超时时间
        api.logging().raiseDebugEvent("ErrorHandler 状态已重置");
    }
    
    /**
     * 处理响应错误（核心方法，对应Python的error_handler()）
     * 
     * @param response 响应对象（可能为null）
     * @param exception 异常（可能为null）
     * @param factors 基线因子
     * @param isHealthy 目标是否健康
     * @return 处理结论
     */
    public Conclusion handleResponse(HttpResponse response, 
                                      Exception exception,
                                      BaselineFactors factors,
                                      boolean isHealthy) {
        
        // 1. 检查kill开关
        if (killSwitch.get()) {
            return Conclusion.KILL;
        }
        
        // 2. 处理异常
        if (exception != null) {
            return handleException(exception);
        }
        
        // 3. 处理响应为null
        if (response == null) {
            api.logging().raiseErrorEvent("⚠️ 响应为空");
            return Conclusion.RETRY;
        }
        
        // 4. 处理不健康状态码
        int statusCode = response.statusCode();
        if (UNHEALTHY_CODES.contains(statusCode)) {
            return handleUnhealthyStatusCode(statusCode, factors, isHealthy);
        }
        
        return Conclusion.OK;
    }
    
    /**
     * 处理异常（对应Python的超时和连接拒绝处理）
     */
    private Conclusion handleException(Exception e) {
        String errorMessage = e.getMessage();
        
        // 1. 超时处理（Python: line 50-59）
        if (isTimeout(e)) {
            if (currentTimeout >= maxTimeout) {
                api.logging().raiseErrorEvent("❌ 连接超时，无法进一步增加超时时间");
                api.logging().raiseErrorEvent("   目标可能有速率限制，建议启用稳定模式");
                killSwitch.set(true);
                return Conclusion.KILL;
            } else {
                currentTimeout += 5;  // ✅ 同Python：每次增加5秒
                api.logging().raiseInfoEvent(String.format(
                    "⏱️ 连接超时，超时时间增加到 %d 秒",
                    currentTimeout
                ));
                return Conclusion.RETRY;
            }
        }
        
        // 2. 连接拒绝处理（Python: line 60-61 + connection_refused()）
        if (isConnectionRefused(e)) {
            return handleConnectionRefused();
        }
        
        // 3. 其他异常
        api.logging().raiseErrorEvent("❌ 遇到错误: " + errorMessage);
        killSwitch.set(true);
        return Conclusion.KILL;
    }
    
    /**
     * 处理不健康状态码（Python: line 30-48）
     */
    private Conclusion handleUnhealthyStatusCode(int statusCode, 
                                                  BaselineFactors factors,
                                                  boolean isHealthy) {
        
        // 如果目标本身就不健康（初始探测时就返回这些状态码），则继续
        if (!isHealthy) {
            return Conclusion.OK;
        }
        
        // 503: 服务器过载
        if (statusCode == 503) {
            api.logging().raiseErrorEvent("❌ 目标无法处理请求（503），建议启用稳定模式");
            killSwitch.set(true);
            return Conclusion.KILL;
        }
        
        // 429/418: 速率限制
        if (statusCode == 429 || statusCode == 418) {
            api.logging().raiseErrorEvent(String.format(
                "❌ 目标有速率限制（%d），建议启用稳定模式",
                statusCode
            ));
            killSwitch.set(true);
            return Conclusion.KILL;
        }
        
        // 400/413: 错误请求
        if (statusCode == 400 || statusCode == 413) {
            // ✅ 修复：添加factors的null检查
            // 只有当基线状态码不是400时才计数（Python: line 41）
            if (factors != null && factors.getSameCode() != null && factors.getSameCode() != statusCode) {
                int count = badRequestCount.incrementAndGet();
                
                if (count > 20) {  // ✅ 同Python：超过20次终止
                    api.logging().raiseErrorEvent(
                        "❌ 服务器收到错误请求（400）次数过多，尝试减小chunk size"
                    );
                    killSwitch.set(true);
                    return Conclusion.KILL;
                }
                
                api.logging().raiseDebugEvent(String.format(
                    "⚠️ 错误请求（%d）计数: %d/20",
                    statusCode, count
                ));
            }
            
            return Conclusion.OK;
        }
        
        return Conclusion.OK;
    }
    
    /**
     * 处理连接拒绝（Python: connection_refused()）
     */
    private Conclusion handleConnectionRefused() {
        if (stableMode) {
            // 稳定模式：等待30秒后重试
            api.logging().raiseInfoEvent("⏸️ 触发速率限制，稳定连接（等待30秒）...");
            try {
                Thread.sleep(30000);  // ✅ 同Python：等待30秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            killSwitch.set(false);  // 重置kill开关
            return Conclusion.RETRY;
        } else {
            api.logging().raiseErrorEvent("❌ 目标有速率限制，建议启用稳定模式");
            killSwitch.set(true);
            return Conclusion.KILL;
        }
    }
    
    /**
     * 检测是否为超时异常
     */
    private boolean isTimeout(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("timeout") || 
               msg.contains("Timeout") ||
               msg.contains("timed out");
    }
    
    /**
     * 检测是否为连接拒绝
     */
    private boolean isConnectionRefused(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("ConnectionRefused") ||
               msg.contains("Connection refused") ||
               msg.contains("ECONNREFUSED");
    }
    
    // ========== Getters ==========
    
    public int getCurrentTimeout() {
        return currentTimeout;
    }
    
    public boolean isKilled() {
        return killSwitch.get();
    }
    
    public void setKilled(boolean killed) {
        killSwitch.set(killed);
    }
    
    public void resetBadRequestCount() {
        badRequestCount.set(0);
    }
    
    public int getBadRequestCount() {
        return badRequestCount.get();
    }
}

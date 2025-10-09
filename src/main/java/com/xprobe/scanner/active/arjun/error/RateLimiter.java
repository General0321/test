package com.xprobe.scanner.active.arjun.error;

import burp.api.montoya.MontoyaApi;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限制器 - 对应Python requester.py的@limits装饰器
 * 
 * Python实现：
 * @sleep_and_retry
 * @limits(calls=mem.var['rate_limit'], period=1)
 * 
 * 功能：限制每秒最多发送N个请求
 */
public class RateLimiter {
    
    private final MontoyaApi api;
    private final int maxRequestsPerSecond;     // 每秒最大请求数
    private final boolean stableMode;            // 稳定模式
    
    // 状态
    private final AtomicLong lastRequestTime;    // 上次请求时间（毫秒）
    private final AtomicLong requestCount;       // 当前秒内的请求计数
    private final AtomicLong currentSecond;      // 当前秒（用于重置计数）
    
    public RateLimiter(MontoyaApi api, int maxRequestsPerSecond, boolean stableMode) {
        this.api = api;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.stableMode = stableMode;
        this.lastRequestTime = new AtomicLong(0);
        this.requestCount = new AtomicLong(0);
        this.currentSecond = new AtomicLong(System.currentTimeMillis() / 1000);
    }
    
    /**
     * 等待直到允许发送请求（对应Python的@sleep_and_retry @limits）
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long nowSecond = now / 1000;
        
        // ✅ 检查是否进入新的秒
        if (nowSecond > currentSecond.get()) {
            // 新的一秒，重置计数
            currentSecond.set(nowSecond);
            requestCount.set(0);
        }
        
        // ✅ 检查是否超过速率限制
        if (requestCount.get() >= maxRequestsPerSecond) {
            // 超过限制，需要等待到下一秒
            long waitTime = 1000 - (now % 1000);  // 等到下一秒
            
            api.logging().raiseDebugEvent(String.format(
                "⏸️ 速率限制 (%d req/s)，等待 %dms...",
                maxRequestsPerSecond, waitTime
            ));
            
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 重新计算
            now = System.currentTimeMillis();
            nowSecond = now / 1000;
            currentSecond.set(nowSecond);
            requestCount.set(0);
        }
        
        // ✅ Python的stable模式：随机延迟3-10秒（requester.py line 24-25）
        if (stableMode) {
            int randomDelay = 3000 + (int) (Math.random() * 7000);  // 3-10秒
            
            api.logging().raiseDebugEvent(String.format(
                "🐢 稳定模式：随机延迟 %dms",
                randomDelay
            ));
            
            try {
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 增加计数
        requestCount.incrementAndGet();
        lastRequestTime.set(now);
    }
    
    /**
     * 获取当前秒内已发送的请求数
     */
    public long getCurrentRequestCount() {
        return requestCount.get();
    }
    
    /**
     * 获取上次请求的时间间隔（毫秒）
     */
    public long getTimeSinceLastRequest() {
        return System.currentTimeMillis() - lastRequestTime.get();
    }
}

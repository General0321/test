package com.xprobe.scanner.active.arjun.error;

import burp.api.montoya.MontoyaApi;

import java.util.Random;
import java.util.function.Supplier;

/**
 * 重试策略 - 对应Python bruter.py的递归重试
 * 
 * 核心功能：
 * 1. 递归重试（对应Python的 return bruter(...)）
 * 2. 指数退避（优化：避免频繁重试）
 * 3. 最大重试次数限制（安全：避免无限循环）
 */
public class RetryStrategy {
    
    private final MontoyaApi api;
    private final int maxRetries;           // 最大重试次数
    private final Random random;
    
    public RetryStrategy(MontoyaApi api, int maxRetries) {
        this.api = api;
        this.maxRetries = maxRetries;
        this.random = new Random();
    }
    
    /**
     * 执行带重试的操作（对应Python的bruter递归调用）
     * 
     * @param operation 要执行的操作
     * @param errorHandler 错误处理器
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     * @throws RuntimeException 如果达到最大重试次数或被终止
     */
    public <T> T executeWithRetry(
            Supplier<RetryableResult<T>> operation,
            ErrorHandler errorHandler,
            String operationName) {
        
        int retryCount = 0;
        
        while (retryCount <= maxRetries) {
            // 检查kill开关
            if (errorHandler.isKilled()) {
                throw new RuntimeException("扫描已终止（kill开关）");
            }
            
            try {
                // 执行操作
                RetryableResult<T> result = operation.get();
                
                // 处理响应错误
                ErrorHandler.Conclusion conclusion = errorHandler.handleResponse(
                    result.getResponse(),
                    result.getException(),
                    result.getFactors(),
                    result.isHealthy()
                );
                
                // 根据结论决定下一步
                switch (conclusion) {
                    case OK:
                        // 成功，返回结果
                        return result.getValue();
                    
                    case RETRY:
                        // 需要重试
                        retryCount++;
                        
                        if (retryCount > maxRetries) {
                            api.logging().raiseErrorEvent(String.format(
                                "❌ %s 达到最大重试次数 (%d)",
                                operationName, maxRetries
                            ));
                            throw new RuntimeException("达到最大重试次数");
                        }
                        
                        // ✅ 指数退避 + 随机抖动（避免雷鸣羊群效应）
                        int baseDelay = (int) Math.pow(2, retryCount - 1) * 1000;  // 1s, 2s, 4s, 8s, 16s
                        int jitter = random.nextInt(500);  // 0-500ms随机抖动
                        int delay = baseDelay + jitter;
                        
                        api.logging().raiseDebugEvent(String.format(
                            "🔄 %s 重试 %d/%d (等待 %dms)...",
                            operationName, retryCount, maxRetries, delay
                        ));
                        
                        Thread.sleep(delay);
                        break;
                    
                    case KILL:
                        // 终止扫描
                        api.logging().raiseErrorEvent(
                            "❌ " + operationName + " 被终止"
                        );
                        throw new RuntimeException("扫描被终止");
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试被中断", e);
            }
        }
        
        throw new RuntimeException("未预期的重试循环退出");
    }
    
    /**
     * 可重试的结果包装器
     */
    public static class RetryableResult<T> {
        private final T value;
        private final burp.api.montoya.http.message.responses.HttpResponse response;
        private final Exception exception;
        private final com.xprobe.scanner.active.arjun.model.BaselineFactors factors;
        private final boolean healthy;
        
        public RetryableResult(
                T value,
                burp.api.montoya.http.message.responses.HttpResponse response,
                Exception exception,
                com.xprobe.scanner.active.arjun.model.BaselineFactors factors,
                boolean healthy) {
            this.value = value;
            this.response = response;
            this.exception = exception;
            this.factors = factors;
            this.healthy = healthy;
        }
        
        // 成功的结果（无需重试）
        public static <T> RetryableResult<T> success(
                T value,
                burp.api.montoya.http.message.responses.HttpResponse response,
                com.xprobe.scanner.active.arjun.model.BaselineFactors factors,
                boolean healthy) {
            return new RetryableResult<>(value, response, null, factors, healthy);
        }
        
        // 错误的结果（可能需要重试）
        public static <T> RetryableResult<T> error(
                Exception exception,
                com.xprobe.scanner.active.arjun.model.BaselineFactors factors,
                boolean healthy) {
            return new RetryableResult<>(null, null, exception, factors, healthy);
        }
        
        public T getValue() {
            return value;
        }
        
        public burp.api.montoya.http.message.responses.HttpResponse getResponse() {
            return response;
        }
        
        public Exception getException() {
            return exception;
        }
        
        public com.xprobe.scanner.active.arjun.model.BaselineFactors getFactors() {
            return factors;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
    }
}

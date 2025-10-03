package com.xprobe.scanner.active.arjun;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.active.arjun.config.ArjunConfig;
import com.xprobe.scanner.active.arjun.model.DiscoveryResult;
import com.xprobe.scanner.Logs.LogModel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Arjun服务 - Java原生实现（替代外部Python Arjun）
 * 
 * 核心优势：
 * ✅ 无需外部依赖（纯Java实现）
 * ✅ 跨平台（不受macOS SIP等安全限制）
 * ✅ 更强大的异常检测算法
 * ✅ 支持GET/POST/POST-JSON
 * ✅ 内置152个特殊参数
 * ✅ 动态稳定性因子调整
 */
public class ArjunService {
    
    private final MontoyaApi api;
    private final ParamDiscoveryEngine engine;
    private final LogModel logModel;
    private final ArjunConfig config;
    
    // 统计数据
    private final AtomicInteger totalScans = new AtomicInteger(0);
    private final AtomicInteger successfulScans = new AtomicInteger(0);
    private final AtomicInteger failedScans = new AtomicInteger(0);
    private final AtomicInteger totalParamsFound = new AtomicInteger(0);
    
    // 用户自定义字典（从配置加载）
    private Set<String> userCustomDictionary = new HashSet<>();
    
    public ArjunService(MontoyaApi api, LogModel logModel) {
        this(api, logModel, new ArjunConfig());
    }
    
    public ArjunService(MontoyaApi api, LogModel logModel, ArjunConfig config) {
        this.api = api;
        this.logModel = logModel;
        this.config = config;
        this.engine = new ParamDiscoveryEngine(api, config.getChunkSize(), false);
        
        api.logging().raiseInfoEvent("✅ Arjun服务初始化完成（Java原生实现）");
    }
    
    /**
     * 扫描URL查找隐藏参数（兼容ArjunIntegration接口）
     * 
     * @param request HTTP请求对象
     * @param customDictionary 自定义参数字典（来自ParameterCollector）
     * @return CompletableFuture<ArjunResult> 扫描结果
     */
    public CompletableFuture<ArjunResult> scan(HttpRequest request, Set<String> customDictionary) {
        totalScans.incrementAndGet();
        
        String url = request.url();
        String method = request.method();
        
        // 合并用户自定义字典
        Set<String> mergedDictionary = new HashSet<>(customDictionary);
        mergedDictionary.addAll(userCustomDictionary);
        
        // 记录开始日志到Dashboard
        logArjunStart(url, method, mergedDictionary.size());
        
        return engine.scan(request, mergedDictionary).thenApply(discoveryResult -> {
            // 转换DiscoveryResult为ArjunResult
            ArjunResult arjunResult = convertToArjunResult(discoveryResult);
            
            // 更新统计
            if (arjunResult.isSuccess()) {
                successfulScans.incrementAndGet();
                totalParamsFound.addAndGet(arjunResult.getFoundParameters().size());
                
                // 记录成功日志到Dashboard
                logArjunSuccess(url, method, arjunResult.getFoundParameters(), 
                               discoveryResult.getScanTimeMs());
            } else {
                failedScans.incrementAndGet();
                
                // 记录失败日志到Dashboard
                logArjunFailure(url, method, arjunResult.getErrorMessage());
            }
            
            return arjunResult;
        }).exceptionally(ex -> {
            failedScans.incrementAndGet();
            
            String errorMsg = "Arjun扫描异常: " + ex.getMessage();
            api.logging().raiseErrorEvent(errorMsg);
            
            // 记录异常日志到Dashboard
            logArjunFailure(url, method, errorMsg);
            
            return ArjunResult.error(errorMsg);
        });
    }
    
    /**
     * 记录Arjun扫描开始日志到Dashboard
     */
    private void logArjunStart(String url, String method, int dictSize) {
        api.logging().raiseInfoEvent(String.format(
            "🔍 Arjun扫描开始: %s %s (字典: %d 个参数)",
            method, url, dictSize
        ));
        
        // ✅ Arjun的探测流量不记录到扫描结果表
        // 只在Burp日志窗口显示，避免与漏洞扫描结果混淆
    }
    
    /**
     * 记录Arjun扫描成功日志到Dashboard
     */
    private void logArjunSuccess(String url, String method, Set<String> foundParams, long scanTimeMs) {
        String resultMsg;
        if (foundParams.isEmpty()) {
            resultMsg = String.format("✅ Arjun扫描完成: %s %s - 未发现新参数 (耗时: %dms)",
                method, url, scanTimeMs);
        } else {
            resultMsg = String.format("✅ Arjun发现参数: %s %s - %s (耗时: %dms)",
                method, url, foundParams, scanTimeMs);
        }
        
        api.logging().raiseInfoEvent(resultMsg);
        
        // ✅ Arjun的探测流量不记录到扫描结果表
        // 发现的参数会发送给UniversalScanner，漏洞扫描结果才会显示在表中
    }
    
    /**
     * 记录Arjun扫描失败日志到Dashboard
     */
    private void logArjunFailure(String url, String method, String errorMessage) {
        String resultMsg = String.format("❌ Arjun扫描失败: %s %s - %s",
            method, url, errorMessage);
        
        api.logging().raiseErrorEvent(resultMsg);
        
        // ✅ Arjun的探测流量不记录到扫描结果表
        // 错误信息已在Burp日志窗口显示
    }
    
    /**
     * 转换DiscoveryResult为ArjunResult（兼容性）
     */
    private ArjunResult convertToArjunResult(DiscoveryResult discoveryResult) {
        if (discoveryResult.isSuccess()) {
            return ArjunResult.success(
                discoveryResult.getUrl(),
                discoveryResult.getFoundParams(),
                Collections.emptyList()  // Java版本不需要原始输出
            );
        } else {
            return ArjunResult.error(discoveryResult.getErrorMessage());
        }
    }
    
    /**
     * 获取统计信息
     */
    public ArjunStatistics getStatistics() {
        return new ArjunStatistics(
            totalScans.get(),
            successfulScans.get(),
            failedScans.get(),
            totalParamsFound.get()
        );
    }
    
    /**
     * 获取配置
     */
    public ArjunConfig getConfig() {
        return config;
    }
    
    /**
     * 设置用户自定义字典
     */
    public void setUserCustomDictionary(Set<String> dictionary) {
        this.userCustomDictionary = dictionary != null ? new HashSet<>(dictionary) : new HashSet<>();
        api.logging().raiseInfoEvent(String.format(
            "✅ Arjun用户自定义字典已更新: %d 个参数", 
            this.userCustomDictionary.size()
        ));
    }
    
    /**
     * 获取用户自定义字典
     */
    public Set<String> getUserCustomDictionary() {
        return new HashSet<>(userCustomDictionary);
    }
    
    /**
     * Arjun扫描结果（兼容ArjunIntegration.ArjunResult）
     */
    public static class ArjunResult {
        private final boolean success;
        private final String url;
        private final Set<String> foundParameters;
        private final List<String> output;
        private final String errorMessage;
        
        private ArjunResult(boolean success, String url, Set<String> foundParameters, 
                           List<String> output, String errorMessage) {
            this.success = success;
            this.url = url;
            this.foundParameters = foundParameters != null ? foundParameters : new LinkedHashSet<>();
            this.output = output != null ? output : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
        
        public static ArjunResult success(String url, Set<String> foundParameters, List<String> output) {
            return new ArjunResult(true, url, foundParameters, output, null);
        }
        
        public static ArjunResult error(String errorMessage) {
            return new ArjunResult(false, null, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getUrl() { return url; }
        public Set<String> getFoundParameters() { return new LinkedHashSet<>(foundParameters); }
        public List<String> getOutput() { return new ArrayList<>(output); }
        public String getErrorMessage() { return errorMessage; }
        
        @Override
        public String toString() {
            if (success) {
                return "ArjunResult{url='" + url + "', foundParameters=" + foundParameters.size() + "}";
            } else {
                return "ArjunResult{error='" + errorMessage + "'}";
            }
        }
    }
    
    /**
     * Arjun统计信息
     */
    public static class ArjunStatistics {
        private final int totalScans;
        private final int successfulScans;
        private final int failedScans;
        private final int totalParamsFound;
        
        public ArjunStatistics(int totalScans, int successfulScans, 
                              int failedScans, int totalParamsFound) {
            this.totalScans = totalScans;
            this.successfulScans = successfulScans;
            this.failedScans = failedScans;
            this.totalParamsFound = totalParamsFound;
        }
        
        public int getTotalScans() { return totalScans; }
        public int getSuccessfulScans() { return successfulScans; }
        public int getFailedScans() { return failedScans; }
        public int getTotalParamsFound() { return totalParamsFound; }
        
        @Override
        public String toString() {
            return String.format(
                "ArjunStats{总扫描: %d, 成功: %d, 失败: %d, 发现参数: %d}",
                totalScans, successfulScans, failedScans, totalParamsFound
            );
        }
    }
}


package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.scanners.Scanner;
import com.xprobe.scanner.scanners.ScannerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务调度器，负责调度和执行扫描任务
 */
public class TaskScheduler {
    private final MontoyaApi api;
    private final ScannerFactory scannerFactory;
    private final LogModel logModel;
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    private final ExecutorService executorService;
    private static final AtomicInteger logId = new AtomicInteger(0);
    
    public TaskScheduler(MontoyaApi api, ScannerFactory scannerFactory, LogModel logModel, XProbeConfigManager xprobeConfigManager) {
        this.api = api;
        this.scannerFactory = scannerFactory;
        this.logModel = logModel;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        // 创建固定大小的线程池
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
    }
    
    /**
     * 调度扫描任务
     */
    public void scheduleScan(List<ScanTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        
        // ✅ 修复：使用 CompletableFuture.allOf 替代 parallelStream()
        // 这样可以精确控制并发数量，避免使用全局 ForkJoinPool
        List<CompletableFuture<Void>> futures = tasks.stream()
            .map(task -> CompletableFuture.runAsync(() -> executeScanTask(task), executorService))
            .collect(java.util.stream.Collectors.toList());
        
        // ✅ 修复：使用 whenComplete 处理异常，并记录完成信息
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    api.logging().raiseErrorEvent("批量扫描时发生错误: " + throwable.getMessage());
                    throwable.printStackTrace();
                } else {
                    api.logging().raiseDebugEvent("批量扫描完成: " + tasks.size() + " 个任务");
                }
            });
    }
    
    /**
     * 执行单个扫描任务
     */
    private void executeScanTask(ScanTask task) {
        try {
            // 获取对应的扫描器
            Scanner scanner = scannerFactory.getScanner(task.getScanType());
            if (scanner == null) {
                api.logging().raiseErrorEvent("No scanner found for type: " + task.getScanType());
                return;
            }
            
            // 检查扫描器是否可以处理该任务
            if (!scanner.canScan(task)) {
                return;
            }
            
            // 执行扫描
            CompletableFuture<List<ScanResult>> futureScanResults = scanner.scan(task);
            
            // ✅ 处理扫描结果（记录所有结果，不仅仅是命中的）
            futureScanResults.thenAccept(results -> {
                if (results != null && !results.isEmpty()) {
                    results.forEach(result -> {
                        // ✅ 记录所有结果（包括未命中的）
                        logResult(task, result);
                        
                        // 如果命中规则，额外记录到Burp日志
                        if (result.isVulnerable()) {
                            api.logging().raiseInfoEvent(String.format(
                                "✓ Vulnerability found: %s in parameter '%s' with payload: %s",
                                result.getScanType(),
                                result.getParameterName(),
                                result.getPayload()
                            ));
                        }
                    });
                }
            }).exceptionally(ex -> {
                api.logging().raiseErrorEvent("Error during scan: " + ex.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Error executing scan task: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 记录扫描结果到日志
     */
    private void logResult(ScanTask task, ScanResult result) {
        try {
            // ✅ 安全检查：确保响应对象不为null
            HttpResponse response = result.getResponse();
            if (response == null) {
                api.logging().raiseErrorEvent("⚠️ 扫描结果缺少响应对象，跳过记录: " + result.getScanType());
                return;
            }
            
            // ✅ 根据配置决定是否记录（零开销）
            boolean shouldLog = false;
            
            switch (xprobeConfigManager.getScanResultLogMode()) {
                case ALL_REQUESTS:
                    // 记录所有请求（无论是否命中）
                    shouldLog = true;
                    break;
                    
                case MATCHED_ONLY:
                    // 仅记录命中的请求
                    shouldLog = result.isVulnerable();
                    break;
            }
            
            if (!shouldLog) {
                return;  // 跳过记录
            }
            
            int id = logId.incrementAndGet();
            
            // ✅ 安全获取响应字段
            int responseLength = 0;
            int statusCode = 0;
            try {
                responseLength = response.body() != null ? response.body().length() : 0;
                statusCode = response.statusCode();
            } catch (Exception e) {
                api.logging().raiseErrorEvent("⚠️ 读取响应字段失败: " + e.getMessage());
            }
            
            // ✅ 同步添加到日志模型（包含规则名称）
            synchronized (logModel) {
                // ✅ 如果命中规则，传递规则名称；否则传递null
                String ruleName = result.isVulnerable() ? result.getScanType() : null;
                
                // ✅ 关键修复：从修改后的请求中提取method和url
                HttpRequest modifiedRequest = result.getModifiedRequest();
                String displayMethod = modifiedRequest != null ? modifiedRequest.method() : task.getContext().getMethod();
                String displayUrl = modifiedRequest != null ? modifiedRequest.url() : task.getContext().getUrl();
                
                logModel.add(
                    id,
                    task.getContext().getToolSource(),
                    displayMethod,      // ✅ 修改后的method
                    displayUrl,         // ✅ 修改后的url
                    result.getOriginalRequest(),
                    response,
                    responseLength,
                    statusCode,
                    result.getResponseTime(),
                    result.getModifiedRequest(),
                    response,
                    ruleName  // ✅ 传递规则名称
                );
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("❌ Error logging result: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 关闭任务调度器
     * ✅ 修复：正确等待任务完成，避免资源泄漏
     */
    public void shutdown() {
        api.logging().raiseInfoEvent("开始关闭任务调度器...");
        executorService.shutdown();
        
        try {
            // 等待最多30秒让任务完成
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                api.logging().raiseInfoEvent("任务未在30秒内完成，强制终止...");
                
                // 强制停止所有任务
                List<Runnable> pendingTasks = executorService.shutdownNow();
                api.logging().raiseInfoEvent("强制终止了 " + pendingTasks.size() + " 个待执行任务");
                
                // 再等待5秒确保线程停止
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    api.logging().raiseErrorEvent("⚠️ 部分线程可能未正确关闭！");
                }
            }
            
            api.logging().raiseInfoEvent("✅ 任务调度器已安全关闭");
            
        } catch (InterruptedException e) {
            api.logging().raiseErrorEvent("关闭任务调度器时被中断");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigPersistence;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.scanners.Scanner;
import com.xprobe.scanner.scanners.ScannerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务调度器，负责调度和执行扫描任务
 */
public class TaskScheduler {
    private final MontoyaApi api;
    private final ScannerFactory scannerFactory;
    private final LogModel logModel;
    private final ConfigPersistence configPersistence;  // ✅ 添加配置持久化
    private final ExecutorService executorService;
    private static final AtomicInteger logId = new AtomicInteger(0);
    
    public TaskScheduler(MontoyaApi api, ScannerFactory scannerFactory, LogModel logModel, ConfigPersistence configPersistence) {
        this.api = api;
        this.scannerFactory = scannerFactory;
        this.logModel = logModel;
        this.configPersistence = configPersistence;  // ✅ 保存引用
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
        
        // 使用CompletableFuture异步处理所有任务
        CompletableFuture.runAsync(() -> {
            // 并行处理所有扫描任务
            tasks.parallelStream().forEach(this::executeScanTask);
        }, executorService);
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
            
            // ✅ 根据配置决定是否记录
            XProbeConfig.ScanResultLogMode logMode = getScanResultLogMode();
            boolean shouldLog = false;
            
            switch (logMode) {
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
                
                logModel.add(
                    id,
                    task.getContext().getToolSource(),
                    task.getContext().getMethod(),
                    task.getContext().getUrl(),
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
     * ✅ 获取扫描结果记录模式
     */
    private XProbeConfig.ScanResultLogMode getScanResultLogMode() {
        try {
            XProbeConfig config = configPersistence.load();
            return config.getScanResultLogMode();
        } catch (Exception e) {
            api.logging().raiseDebugEvent("读取记录模式失败，使用默认: " + e.getMessage());
            return XProbeConfig.ScanResultLogMode.MATCHED_ONLY;  // 默认仅命中
        }
    }
    
    /**
     * 关闭任务调度器
     */
    public void shutdown() {
        executorService.shutdown();
        api.logging().raiseInfoEvent("Task scheduler shutdown");
    }
}

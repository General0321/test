package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.Configuration;
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
    private final ExecutorService executorService;
    private static final AtomicInteger logId = new AtomicInteger(0);
    
    public TaskScheduler(MontoyaApi api, ScannerFactory scannerFactory, LogModel logModel) {
        this.api = api;
        this.scannerFactory = scannerFactory;
        this.logModel = logModel;
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
            
            // 处理扫描结果
            futureScanResults.thenAccept(results -> {
                if (results != null && !results.isEmpty()) {
                    results.forEach(result -> {
                        if (result.isVulnerable()) {
                            logResult(task, result);
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
            int id = logId.incrementAndGet();
            
            // 同步添加到日志模型
            synchronized (logModel) {
                logModel.add(
                    id,
                    task.getContext().getToolSource(),
                    task.getContext().getMethod(),
                    task.getContext().getUrl(),
                    result.getOriginalRequest(),
                    result.getResponse(),
                    result.getResponse().body().length(),
                    result.getResponse().statusCode(),
                    result.getResponseTime(),
                    result.getModifiedRequest(),
                    result.getResponse()
                );
            }
            
            // 记录到Burp日志
            api.logging().raiseInfoEvent(String.format(
                "Vulnerability found: %s in parameter '%s' with payload: %s",
                result.getScanType(),
                result.getParameterName(),
                result.getPayload()
            ));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Error logging result: " + e.getMessage());
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

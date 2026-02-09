package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.scanners.Scanner;
import com.xprobe.scanner.scanners.ScannerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务调度器，负责调度和执行扫描任务
 */
public class TaskScheduler {
    private final MontoyaApi api;
    private final ScannerFactory scannerFactory;
    private final LogModel logModel;
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    private final OriginalResponseCache responseCache;  // ✅ 原始响应缓存
    private final ExecutorService executorService;
    private static final AtomicInteger logId = new AtomicInteger(0);
    
    // ✅ 扫描任务监听器
    private ScanTaskListener scanTaskListener;
    
    // ✅ 任务进度跟踪
    private final Map<ScanTask, TaskProgress> taskProgressMap = new ConcurrentHashMap<>();
    
    // ✅ 任务Future跟踪（用于取消任务）
    private final Map<ScanTask, CompletableFuture<Void>> taskFutures = new ConcurrentHashMap<>();
    
    // ✅ 累计历史统计数据（包括已完成的任务）
    private volatile int cumulativeTotalSent = 0;
    private volatile int cumulativeTotalExpected = 0;
    
    public TaskScheduler(MontoyaApi api, ScannerFactory scannerFactory, LogModel logModel, 
                        XProbeConfigManager xprobeConfigManager, OriginalResponseCache responseCache) {
        this.api = api;
        this.scannerFactory = scannerFactory;
        this.logModel = logModel;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        this.responseCache = responseCache;  // ✅ 保存响应缓存引用
        
        // ✅ 修复：创建可伸缩线程池（从配置读取参数）
        XProbeConfig config = xprobeConfigManager.getConfig();
        int cpuCount = Runtime.getRuntime().availableProcessors();
        
        // ✅ 修复：确保CPU数量至少为1（防止异常情况，异常时使用默认值4）
        if (cpuCount < 1) {
            cpuCount = 4;
            api.logging().raiseErrorEvent("⚠️ 检测到CPU数量异常，使用默认值4");
        }
        
        // 核心线程数：-1表示自动（CPU×2），否则使用配置值
        int corePoolSize = config.getScannerCoreThreads() == -1 
            ? cpuCount * 2 
            : config.getScannerCoreThreads();
        
        // ✅ 修复：验证核心线程数（必须 >= 1）
        if (corePoolSize < 1) {
            api.logging().raiseErrorEvent("⚠️ 核心线程数无效: " + corePoolSize + "，使用默认值: " + (cpuCount * 2));
            corePoolSize = cpuCount * 2;
        }
        
        // 最大线程数：-1表示自动（核心×2），否则使用配置值
        int maximumPoolSize = config.getScannerMaxThreads() == -1 
            ? corePoolSize * 2 
            : config.getScannerMaxThreads();
        
        // ✅ 修复：验证最大线程数（必须 >= 核心线程数）
        if (maximumPoolSize < corePoolSize) {
            api.logging().raiseErrorEvent("⚠️ 最大线程数(" + maximumPoolSize + ")小于核心线程数(" + corePoolSize + ")，调整为: " + (corePoolSize * 2));
            maximumPoolSize = corePoolSize * 2;
        }
        if (maximumPoolSize < 1) {
            api.logging().raiseErrorEvent("⚠️ 最大线程数无效: " + maximumPoolSize + "，使用默认值: " + (corePoolSize * 2));
            maximumPoolSize = corePoolSize * 2;
        }
        
        // 队列大小和空闲时间从配置读取
        int queueSize = config.getScannerQueueSize();
        long keepAliveTime = config.getScannerKeepAliveSeconds();
        
        // ✅ 修复：验证队列大小（必须 > 0）
        if (queueSize < 1) {
            api.logging().raiseErrorEvent("⚠️ 队列大小无效: " + queueSize + "，使用默认值: 2000");
            queueSize = 2000;
        }
        
        // ✅ 修复：验证空闲时间（必须 >= 0）
        if (keepAliveTime < 0) {
            api.logging().raiseErrorEvent("⚠️ 空闲时间无效: " + keepAliveTime + "，使用默认值: 120");
            keepAliveTime = 120;
        }
        
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueSize);
        
        this.executorService = new ThreadPoolExecutor(
            corePoolSize,           // 核心线程数
            maximumPoolSize,        // 最大线程数
            keepAliveTime,          // 空闲线程存活时间
            TimeUnit.SECONDS,
            workQueue,              // 有界队列
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "XProbe-Scanner-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);  // 守护线程，随主线程退出
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：由调用者线程执行
        );
        
        api.logging().raiseInfoEvent(String.format(
            "✅ 线程池初始化完成: CPU=%d核, 核心线程=%d, 最大线程=%d, 队列=%d, 空闲回收=%d秒%s",
            cpuCount, corePoolSize, maximumPoolSize, queueSize, keepAliveTime,
            config.getScannerCoreThreads() == -1 ? " (自动)" : " (用户配置)"
        ));
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
            .map(task -> {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> executeScanTask(task), executorService);
                // ✅ 保存Future引用，用于取消任务
                taskFutures.put(task, future);
                // ✅ 任务完成后自动清理Future引用
                future.whenComplete((result, throwable) -> {
                    taskFutures.remove(task);
                });
                return future;
            })
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
            
            // ✅ 计算预计请求数量
            int expectedRequests = 0;
            if (scanner instanceof com.xprobe.scanner.scanners.UniversalScanner) {
                expectedRequests = ((com.xprobe.scanner.scanners.UniversalScanner) scanner)
                    .calculateExpectedRequestCount(task);
            }
            
            // ✅ 创建任务进度跟踪
            TaskProgress progress = new TaskProgress(task, expectedRequests);
            taskProgressMap.put(task, progress);
            progress.setRunning(true);
            
            // ✅ 通知任务开始
            if (scanTaskListener != null) {
                String ruleName = task.getConfiguration() != null 
                    ? task.getConfiguration().getCustomLabel() 
                    : task.getScanType();
                scanTaskListener.onScanTaskStart(task, expectedRequests, ruleName);
            }
            
            // 执行扫描
            CompletableFuture<List<ScanResult>> futureScanResults = scanner.scan(task);
            
            // ✅ 处理扫描结果（记录所有结果，不仅仅是命中的）
            futureScanResults.thenAccept(results -> {
                int vulnerabilityCount = 0;
                if (results != null && !results.isEmpty()) {
                    for (ScanResult result : results) {
                        // ✅ 记录所有结果（包括未命中的）
                        logResult(task, result, progress);
                        
                        // 如果命中规则，额外记录到Burp日志
                        if (result.isVulnerable()) {
                            vulnerabilityCount++;
                            // ✅ 修复：立即更新漏洞数量（用于动态调整预计数量）
                            // 注意：这里使用 vulnerabilityCount 而不是累加，因为每次发现漏洞都会立即更新
                            progress.setVulnerabilityCount(vulnerabilityCount);
                            api.logging().raiseInfoEvent(String.format(
                                "✓ Vulnerability found: %s in parameter '%s' with payload: %s",
                                result.getScanType(),
                                result.getParameterName(),
                                result.getPayload()
                            ));
                        }
                    }
                }
                
                // ✅ 标记任务完成
                progress.setCompleted(true);
                progress.setVulnerabilityCount(vulnerabilityCount);  // ✅ 存储漏洞数量（最终确认）
                
                // ✅ 累计历史统计数据
                // ✅ 改进：如果发现漏洞，使用实际发送数量作为预计数量（因为发现漏洞后扫描会停止）
                // ✅ 性能优化：使用原子操作减少锁竞争（但这里需要同步，因为涉及多个变量的原子更新）
                synchronized (taskProgressMap) {
                    cumulativeTotalSent += progress.getSentRequests();
                    // 如果发现漏洞，预计数量 = 实际发送数量（因为发现漏洞后扫描会停止）
                    if (vulnerabilityCount > 0) {
                        cumulativeTotalExpected += progress.getSentRequests();
                    } else {
                        cumulativeTotalExpected += progress.getExpectedRequests();
                    }
                }
                
                // ✅ 通知任务完成
                if (scanTaskListener != null) {
                    String ruleName = task.getConfiguration() != null 
                        ? task.getConfiguration().getCustomLabel() 
                        : task.getScanType();
                    scanTaskListener.onScanTaskComplete(
                        task, 
                        progress.getSentRequests(), 
                        vulnerabilityCount, 
                        ruleName
                    );
                }
                
                // ✅ 清理进度跟踪（延迟清理，避免频繁创建删除）
                // 延迟5秒后清理，确保监听器有时间获取统计数据
                new Timer(true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        taskProgressMap.remove(task);
                    }
                }, 5000);
                
            }).exceptionally(ex -> {
                api.logging().raiseErrorEvent("Error during scan: " + ex.getMessage());
                
                // ✅ 标记任务完成（即使失败）
                if (progress != null) {
                    progress.setCompleted(true);
                    
                    // ✅ 累计历史统计数据（失败时也要累计，确保数据一致性）
                    // ✅ 改进：如果发现漏洞，使用实际发送数量作为预计数量（因为发现漏洞后扫描会停止）
                    synchronized (taskProgressMap) {
                        cumulativeTotalSent += progress.getSentRequests();
                        // 失败时没有漏洞，使用原始预计数量
                        cumulativeTotalExpected += progress.getExpectedRequests();
                    }
                    
                    // ✅ 通知任务完成（失败的情况）
                    if (scanTaskListener != null) {
                        String ruleName = task.getConfiguration() != null 
                            ? task.getConfiguration().getCustomLabel() 
                            : task.getScanType();
                        scanTaskListener.onScanTaskComplete(task, progress.getSentRequests(), 0, ruleName);
                    }
                }
                
                return null;
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Error executing scan task: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从缓存中查找原始响应（O(1)查找）
     * @param originalRequest 原始请求
     * @return 找到的原始响应，如果未找到则返回null
     */
    private HttpResponse findOriginalResponse(HttpRequest originalRequest) {
        try {
            // 从缓存中查找（O(1)操作）
            HttpResponse cachedResponse = responseCache.get(originalRequest);
            
            if (cachedResponse != null) {
                api.logging().raiseDebugEvent(
                    "✅ 从缓存找到原始响应: " + originalRequest.method() + " " + originalRequest.url()
                );
                return cachedResponse;
            } else {
                api.logging().raiseDebugEvent(
                    "⚠️ 缓存中未找到原始响应: " + originalRequest.method() + " " + originalRequest.url()
                );
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("❌ 查找原始响应失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 单个任务进度跟踪
     */
    public static class TaskProgress {
        private final ScanTask task;
        private final int expectedRequests;
        private final AtomicInteger sentRequests = new AtomicInteger(0);  // ✅ 使用AtomicInteger确保线程安全
        private volatile boolean running = false;
        private volatile boolean completed = false;
        private volatile int vulnerabilityCount = 0;  // ✅ 漏洞数量
        private volatile String ruleName;  // ✅ 规则名称
        
        public TaskProgress(ScanTask task, int expectedRequests) {
            this.task = task;
            this.expectedRequests = expectedRequests;
            // ✅ 初始化规则名称
            if (task.getConfiguration() != null) {
                this.ruleName = task.getConfiguration().getCustomLabel();
            } else {
                this.ruleName = task.getScanType();
            }
        }
        
        public void incrementSentRequests() {
            sentRequests.incrementAndGet();  // ✅ 使用原子操作确保线程安全
        }
        
        public void setRunning(boolean running) {
            this.running = running;
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
            this.running = false;
        }
        
        public void setVulnerabilityCount(int count) {
            this.vulnerabilityCount = count;
        }
        
        public boolean isRunning() { return running && !completed; }
        public boolean isCompleted() { return completed; }
        public int getSentRequests() { return sentRequests.get(); }  // ✅ 使用AtomicInteger的get方法
        public int getExpectedRequests() { return expectedRequests; }
        public ScanTask getTask() { return task; }
        public int getVulnerabilityCount() { return vulnerabilityCount; }
        public String getRuleName() { return ruleName; }
    }
    
    /**
     * 任务进度统计
     */
    public static class TaskProgressStatistics {
        private final int scanningCount;
        private final int waitingCount;
        private final int totalSent;
        private final int totalExpected;
        
        public TaskProgressStatistics(int scanningCount, int waitingCount, int totalSent, int totalExpected) {
            this.scanningCount = scanningCount;
            this.waitingCount = waitingCount;
            this.totalSent = totalSent;
            this.totalExpected = totalExpected;
        }
        
        public int getScanningCount() { return scanningCount; }
        public int getWaitingCount() { return waitingCount; }
        public int getTotalSent() { return totalSent; }
        public int getTotalExpected() { return totalExpected; }
    }
    
    /**
     * 设置扫描任务监听器
     */
    public void setScanTaskListener(ScanTaskListener listener) {
        this.scanTaskListener = listener;
    }
    
    /**
     * 获取任务进度统计（包含历史累计数据）
     * ✅ 性能优化：使用 ConcurrentHashMap 的 values() 视图，避免长时间持有锁
     */
    public TaskProgressStatistics getProgressStatistics() {
        int scanningCount = 0;
        int waitingCount = 0;
        int currentTotalSent = 0;
        int currentTotalExpected = 0;
        
        // ✅ 性能优化：使用快照避免长时间持有锁，减少锁竞争
        // ConcurrentHashMap.values() 返回的是弱一致性视图，适合高并发场景
        List<TaskProgress> progressSnapshot;
        synchronized (taskProgressMap) {
            progressSnapshot = new ArrayList<>(taskProgressMap.values());
        }
        
        // ✅ 在锁外进行遍历，减少锁持有时间
        for (TaskProgress progress : progressSnapshot) {
            if (progress.isRunning()) {
                scanningCount++;
                currentTotalSent += progress.getSentRequests();
                // ✅ 改进：如果发现漏洞，使用实际发送数量作为预计数量（因为发现漏洞后扫描会停止）
                if (progress.getVulnerabilityCount() > 0) {
                    currentTotalExpected += progress.getSentRequests();
                } else {
                    currentTotalExpected += progress.getExpectedRequests();
                }
            } else if (!progress.isCompleted()) {
                // 未完成且未运行的任务算作等待中
                waitingCount++;
                // ✅ 等待中的任务也要计入预计数量（使用原始预计数量）
                currentTotalExpected += progress.getExpectedRequests();
            }
            // ✅ 已完成的任务已累计到 cumulativeTotalSent/cumulativeTotalExpected
        }
        
        // ✅ 累计历史数据 + 当前进行中的任务数据
        int totalSent = cumulativeTotalSent + currentTotalSent;
        int totalExpected = cumulativeTotalExpected + currentTotalExpected;
        
        return new TaskProgressStatistics(scanningCount, waitingCount, totalSent, totalExpected);
    }
    
    /**
     * ✅ 获取所有任务进度列表（用于规则列表显示）
     */
    public List<TaskScheduler.TaskProgress> getAllTaskProgress() {
        synchronized (taskProgressMap) {
            return new ArrayList<>(taskProgressMap.values());
        }
    }
    
    /**
     * 记录扫描结果到日志
     */
    private void logResult(ScanTask task, ScanResult result, TaskProgress progress) {
        // ✅ 更新进度（每记录一个结果，表示发送了一个请求）
        if (progress != null) {
            progress.incrementSentRequests();
            
            // ✅ 通知进度更新
            if (scanTaskListener != null && progress.isRunning()) {
                scanTaskListener.onScanTaskProgress(
                    task, 
                    progress.getSentRequests(), 
                    progress.getExpectedRequests()
                );
            }
        }
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
            
            // ✅ 从缓存中查找原始响应（O(1)操作）
            HttpRequest originalRequest = result.getOriginalRequest();
            HttpResponse originalResponse = findOriginalResponse(originalRequest);
            
            // 如果找不到原始响应，使用修改后的响应作为fallback
            if (originalResponse == null) {
                api.logging().raiseDebugEvent("⚠️ 未找到原始响应，使用修改后响应作为原始响应");
                originalResponse = response;
            }
            
            // ✅ 安全获取原始响应字段
            int originalResponseLength = 0;
            int originalStatusCode = 0;
            try {
                originalResponseLength = originalResponse.body() != null ? originalResponse.body().length() : 0;
                originalStatusCode = originalResponse.statusCode();
            } catch (Exception e) {
                api.logging().raiseErrorEvent("⚠️ 读取原始响应字段失败: " + e.getMessage());
            }
            
            // ✅ 同步添加到日志模型（包含规则名称）
            synchronized (logModel) {
                // ✅ P0修复：只有真正命中的结果才传递规则名称，避免所有数据包都变红
                // 关键：只有 result.isVulnerable() = true 时才传递规则名称，否则传递 null
                // 这样只有命中的数据包会在UI中显示为红色，其他测试请求保持正常颜色
                String ruleName = result.isVulnerable() ? result.getScanType() : null;
                
                // ✅ 调试日志：确认只有命中的结果才有规则名称
                if (result.isVulnerable()) {
                    api.logging().raiseDebugEvent(
                        "✅ [结果记录] 命中规则: " + ruleName + 
                        ", isVulnerable=" + result.isVulnerable() + 
                        ", scanType=" + result.getScanType()
                    );
                } else {
                    api.logging().raiseDebugEvent(
                        "⚠️ [结果记录] 未命中: ruleName=null" + 
                        ", isVulnerable=" + result.isVulnerable() + 
                        ", scanType=" + result.getScanType()
                    );
                }
                
                // ✅ 关键修复：从修改后的请求中提取method和url
                HttpRequest modifiedRequest = result.getModifiedRequest();
                String displayMethod = modifiedRequest != null ? modifiedRequest.method() : task.getContext().getMethod();
                String displayUrl = modifiedRequest != null ? modifiedRequest.url() : task.getContext().getUrl();
                
                logModel.add(
                    id,
                    task.getContext().getToolSource(),
                    displayMethod,      // ✅ 修改后的method
                    displayUrl,         // ✅ 修改后的url
                    originalRequest,    // ✅ 原始请求
                    originalResponse,   // ✅ 从Proxy History获取的原始响应
                    originalResponseLength,  // originalResponseLen
                    originalStatusCode,      // originalResponseCode
                    result.getResponseTime(),
                    modifiedRequest,    // ✅ 修改后的请求
                    response,           // ✅ 修改后的响应（扫描器收到的响应）
                    ruleName  // ✅ 传递规则名称（只有命中的结果才有，其他为null）
                );
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("❌ Error logging result: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ✅ 一键暂停所有任务并清空任务列表
     */
    public void pauseAllTasksAndClear() {
        api.logging().raiseInfoEvent("⏸️ 开始暂停所有任务并清空任务列表...");
        
        int cancelledCount = 0;
        int clearedCount = 0;
        
        // 1. 取消所有正在执行的任务
        synchronized (taskFutures) {
            for (Map.Entry<ScanTask, CompletableFuture<Void>> entry : taskFutures.entrySet()) {
                ScanTask task = entry.getKey();
                CompletableFuture<Void> future = entry.getValue();
                
                if (future != null && !future.isDone()) {
                    boolean cancelled = future.cancel(true);  // true表示中断正在执行的任务
                    if (cancelled) {
                        cancelledCount++;
                        api.logging().raiseDebugEvent("✅ 已取消任务: " + 
                            (task.getConfiguration() != null ? task.getConfiguration().getCustomLabel() : task.getScanType()));
                    }
                }
            }
            taskFutures.clear();
        }
        
        // 2. 标记所有任务为已完成（停止状态）
        synchronized (taskProgressMap) {
            for (Map.Entry<ScanTask, TaskProgress> entry : taskProgressMap.entrySet()) {
                TaskProgress progress = entry.getValue();
                if (progress.isRunning()) {
                    progress.setRunning(false);
                    progress.setCompleted(true);
                    clearedCount++;
                }
            }
        }
        
        // 3. 清空任务进度映射
        clearedCount += taskProgressMap.size();
        taskProgressMap.clear();
        
        // 4. 通知监听器（通过触发任务完成事件，让监听器自己处理UI更新）
        // 注意：这里不直接调用SwingUtilities，因为TaskScheduler不应该依赖Swing
        // 监听器会通过事件机制自动更新UI
        // 由于所有任务都被取消，我们不需要单独通知，UI会通过定时刷新自动更新
        
        api.logging().raiseInfoEvent(String.format(
            "✅ 已暂停并清空所有任务: 取消 %d 个正在执行的任务, 清空 %d 个任务",
            cancelledCount, clearedCount
        ));
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

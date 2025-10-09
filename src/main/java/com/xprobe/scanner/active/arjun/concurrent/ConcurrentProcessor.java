package com.xprobe.scanner.active.arjun.concurrent;

import burp.api.montoya.MontoyaApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 并发处理器 - 对应Python的ThreadPoolExecutor
 * 
 * Python实现（__main__.py narrower()）:
 * threadpool = ThreadPoolExecutor(max_workers=mem.var['threads'])
 * futures = (threadpool.submit(bruter, request, factors, params) for params in param_groups)
 * for i, result in enumerate(as_completed(futures)):
 *     ...
 * 
 * 功能：并发处理多个chunk的测试
 */
public class ConcurrentProcessor {
    
    private final MontoyaApi api;
    private final int maxWorkers;              // 最大线程数
    private final ExecutorService executor;     // 线程池
    
    public ConcurrentProcessor(MontoyaApi api, int maxWorkers) {
        this.api = api;
        this.maxWorkers = maxWorkers;
        
        // 创建固定大小的线程池
        this.executor = Executors.newFixedThreadPool(
            maxWorkers,
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("Arjun-Worker-" + (counter++));
                    thread.setDaemon(true);
                    return thread;
                }
            }
        );
    }
    
    /**
     * 并发处理多个任务（对应Python的as_completed）
     * 
     * @param items 要处理的项目列表
     * @param processor 处理函数
     * @param progressCallback 进度回调
     * @param killSwitch kill开关检查
     * @return 处理结果列表
     */
    public <T, R> List<R> processConcurrently(
            List<T> items,
            Function<T, R> processor,
            ProgressCallback progressCallback,
            Supplier<Boolean> killSwitch) {
        
        List<R> results = new ArrayList<>();
        
        // 提交所有任务
        List<Future<R>> futures = new ArrayList<>();
        for (T item : items) {
            Future<R> future = executor.submit(() -> processor.apply(item));
            futures.add(future);
        }
        
        // 等待完成并收集结果（as_completed）
        int completed = 0;
        for (Future<R> future : futures) {
            try {
                // 检查kill开关
                if (killSwitch != null && killSwitch.get()) {
                    api.logging().raiseInfoEvent("⚠️ 并发处理被终止（kill开关）");
                    cancelRemaining(futures);
                    break;
                }
                
                // 获取结果
                R result = future.get();
                if (result != null) {
                    results.add(result);
                }
                
                // 报告进度
                completed++;
                if (progressCallback != null) {
                    progressCallback.onProgress(completed, items.size());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                api.logging().raiseErrorEvent("并发处理被中断");
                cancelRemaining(futures);
                break;
            } catch (ExecutionException e) {
                api.logging().raiseErrorEvent("任务执行失败: " + e.getCause().getMessage());
                // 继续处理其他任务
            }
        }
        
        return results;
    }
    
    /**
     * 取消剩余的任务
     */
    private <R> void cancelRemaining(List<Future<R>> futures) {
        for (Future<R> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
    
    /**
     * ✅ P0修复: 关闭线程池 - 增加等待时间避免强制终止
     */
    public void shutdown() {
        executor.shutdown();
        try {
            // ✅ 等待30秒让任务完成(与TaskScheduler保持一致)
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                api.logging().raiseInfoEvent("⚠️ Arjun线程池未在30秒内完成,强制终止");
                List<Runnable> pendingTasks = executor.shutdownNow();
                api.logging().raiseInfoEvent("强制终止了 " + pendingTasks.size() + " 个待执行任务");
                
                // 再等待5秒确保线程停止
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    api.logging().raiseErrorEvent("⚠️ 部分Arjun线程可能未正确关闭");
                }
            }
        } catch (InterruptedException e) {
            api.logging().raiseInfoEvent("Arjun线程池关闭被中断");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }
    
    /**
     * Kill开关Supplier
     */
    @FunctionalInterface
    public interface Supplier<T> {
        T get();
    }
}

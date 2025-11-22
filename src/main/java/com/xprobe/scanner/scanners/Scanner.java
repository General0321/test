package com.xprobe.scanner.scanners;

import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.models.ScanResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 扫描器接口，所有具体的扫描器都需要实现这个接口
 */
public interface Scanner {
    /**
     * 获取扫描器类型标识
     */
    String getType();
    
    /**
     * 获取扫描器名称
     */
    String getName();
    
    /**
     * 获取扫描器描述
     */
    String getDescription();
    
    /**
     * 判断是否可以扫描该任务
     */
    boolean canScan(ScanTask task);
    
    /**
     * 执行扫描任务
     * @param task 扫描任务
     * @return 异步返回扫描结果列表
     */
    CompletableFuture<List<ScanResult>> scan(ScanTask task);
    
    /**
     * 获取该扫描器的payload列表
     */
    List<String> getPayloads();
}

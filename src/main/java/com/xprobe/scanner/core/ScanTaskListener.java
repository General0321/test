package com.xprobe.scanner.core;

import com.xprobe.scanner.models.ScanTask;

/**
 * 扫描任务监听器接口
 * 用于通知扫描任务的开始、进度和完成事件
 */
public interface ScanTaskListener {
    
    /**
     * 扫描任务开始时调用
     * @param task 扫描任务
     * @param expectedRequestCount 预计的请求数量
     * @param ruleName 规则名称
     */
    void onScanTaskStart(ScanTask task, int expectedRequestCount, String ruleName);
    
    /**
     * 扫描任务进度更新时调用
     * @param task 扫描任务
     * @param sentRequests 已发送的请求数
     * @param expectedRequestCount 预计的请求总数
     */
    void onScanTaskProgress(ScanTask task, int sentRequests, int expectedRequestCount);
    
    /**
     * 扫描任务完成时调用
     * @param task 扫描任务
     * @param totalRequests 实际发送的请求总数
     * @param vulnerabilityCount 发现的漏洞数量
     * @param ruleName 规则名称
     */
    void onScanTaskComplete(ScanTask task, int totalRequests, int vulnerabilityCount, String ruleName);
}


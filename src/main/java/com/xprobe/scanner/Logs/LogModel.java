package com.xprobe.scanner.Logs;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LogModel extends AbstractTableModel {

    //private final List<HttpResponseEntry> log;
    private List<LogEntry> log;
    
    // ✅ 滚动窗口机制：保留最新的N条记录
    private static final int MAX_ENTRIES = 7000;  // 最多保留7000条（滚动窗口）
    
    // ✅ P0修复：漏洞计数器（线程安全）
    private final AtomicInteger vulnerabilityCount = new AtomicInteger(0);

    public LogModel() {
        this.log = new ArrayList<>();
    }
    
    /**
     * ✅ 设置最大记录数（线程安全）
     * 使用 AtomicInteger 确保多线程环境下的可见性
     */
    private final AtomicInteger maxEntries = new AtomicInteger(MAX_ENTRIES);
    
    public void setMaxEntries(int max) {
        int validMax = Math.max(100, Math.min(max, 10000));  // 限制在100-10000之间
        maxEntries.set(validMax);
    }
    
    public int getMaxEntries() {
        return maxEntries.get();
    }

    @Override
    public synchronized int getRowCount() {
        return log.size();
    }

    @Override
    public int getColumnCount() {
        return 8;  // ✅ 增加到8列（新增"命中规则"列）
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "#";
            case 1:
                return "来源";
            case 2:
                return "Method";
            case 3:
                return "URL";
            case 4:
                return "响应码";
            case 5:
                return "响应长度";
            case 6:
                return "响应时间";
            case 7:
                return "命中规则";  // ✅ 新增列
            default:
                return "";
        }
    }
    
    /**
     * ✅ 重写此方法，让表格按正确类型排序
     * - 第0列（序号）：Integer 类型 → 数字排序
     * - 第4列（响应码）：Integer 类型 → 数字排序
     * - 第5列（响应长度）：Integer 类型 → 数字排序
     * - 其他列：String 类型 → 字符串排序
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:  // 序号
                return Integer.class;
            case 4:  // 响应码
                return Integer.class;
            case 5:  // 响应长度
                return Integer.class;
            default:
                return String.class;
        }
    }

    @Override
    public synchronized Object getValueAt(int rowIndex, int columnIndex) {
        LogEntry LogEntry = log.get(rowIndex);

        switch (columnIndex) {
            case 0:
                return LogEntry.id;
            case 1:
                return LogEntry.from;
            case 2:
                return LogEntry.method;
            case 3:
                return LogEntry.url;
            case 4:
                return LogEntry.originalResponseCode;
            case 5:
                return LogEntry.originalResponseLen;
            case 6:
                return String.format("%.3f s", LogEntry.originalResponseTime / 1000.0);
            case 7:  // ✅ 显示规则名称
                return LogEntry.ruleName != null ? LogEntry.ruleName : "";
//            // case 3 -> entry.timeBetweenRequestAndResponse != null ? entry.timeBetweenRequestAndResponse.toMillis() + " ms" : "N/A";
//            case 3 -> entry.timeBetweenRequestAndResponse != null ? String.format("%.3f s", entry.timeBetweenRequestAndResponse.toMillis() / 1000.0) : "N/A";
            default:
                return "";
        }
    }

    /**
     * ✅ 优化：减少锁持有时间，在锁外触发UI更新
     */
    public void add(int id, String from, String method, String url, HttpRequest originalRequest, HttpResponse originalResponse, int originalResponseLen, int originalResponseCode, long originalResponseTime, HttpRequest modifiedRequest, HttpResponse modifiedResponse, String ruleName) {
        final int indexToInsert;
        final boolean shouldDelete;
        final boolean isVulnerable = (ruleName != null && !ruleName.isEmpty());
        
        synchronized (this) {
            // ✅ 滚动窗口机制：如果达到最大值，删除最旧的一条
            if (log.size() >= maxEntries.get()) {
                LogEntry removed = log.remove(0);
                // ✅ 如果删除的是漏洞记录，减少计数
                if (removed.isVulnerable()) {
                    vulnerabilityCount.decrementAndGet();
                }
                shouldDelete = true;
            } else {
                shouldDelete = false;
            }
            
            // 添加新条目到末尾
            indexToInsert = log.size();
            log.add(new LogEntry(id, from, method, url, originalRequest, originalResponse, originalResponseLen, originalResponseCode, originalResponseTime, modifiedRequest, modifiedResponse, ruleName));
            
            // ✅ P0修复：如果是漏洞记录，增加计数
            if (isVulnerable) {
                vulnerabilityCount.incrementAndGet();
            }
        }
        
        // ✅ 在锁外触发UI更新，避免阻塞
        SwingUtilities.invokeLater(() -> {
            if (shouldDelete) {
                fireTableRowsDeleted(0, 0);
            }
            fireTableRowsInserted(indexToInsert, indexToInsert);
        });
    }
    
    /**
     * ✅ 清空所有条目
     */
    public void clear() {
        synchronized (this) {
            log.clear();
            // ✅ P0修复：清空时重置计数器
            vulnerabilityCount.set(0);
        }
        
        // ✅ 在锁外触发UI更新
        SwingUtilities.invokeLater(() -> {
            fireTableDataChanged();
        });
    }
    
    /**
     * ✅ 获取当前条目数量
     */
    public synchronized int size() {
        return log.size();
    }
    
    /**
     * ✅ 检查是否已满
     */
    public synchronized boolean isFull() {
        return log.size() >= maxEntries.get();  // ✅ 使用 .get() 获取当前值
    }
    
    /**
     * ✅ 批量添加条目（优化性能）
     */
    public void addAll(List<LogEntry> entries) {
        synchronized (this) {
            for (LogEntry entry : entries) {
                // 如果达到最大值，删除最旧的
                if (log.size() >= maxEntries.get()) {
                    LogEntry removed = log.remove(0);
                    // ✅ P0修复：如果删除的是漏洞记录，减少计数
                    if (removed.isVulnerable()) {
                        vulnerabilityCount.decrementAndGet();
                    }
                }
                log.add(entry);
                // ✅ P0修复：如果是漏洞记录，增加计数
                if (entry.isVulnerable()) {
                    vulnerabilityCount.incrementAndGet();
                }
            }
        }
        
        // ✅ 在锁外触发UI更新
        SwingUtilities.invokeLater(() -> {
            fireTableDataChanged();
        });
    }
    
    /**
     * ✅ P0修复：获取漏洞数量（O(1)时间复杂度）
     * 不需要遍历整个列表，性能提升5000倍
     */
    public int getVulnerabilityCount() {
        return vulnerabilityCount.get();
    }


    // ✅ 已删除 addArjunLog() 方法
    // Arjun的探测流量不记录到扫描结果表，只在Burp日志窗口显示

    public synchronized LogEntry get(int rowIndex) {
        return log.get(rowIndex);
    }




    public static class LogEntry {
        public final int id;
        public final String from;
        public final String method;
        public final String url;
        public final HttpRequest originalRequest;
        public final HttpResponse originalResponse;
        public final int originalResponseLen;
        public final int originalResponseCode;
        public final Long originalResponseTime;
        public final HttpRequest modifiedRequest;
        public final HttpResponse modifiedResponse;
        public final String ruleName;  // ✅ 修改：存储规则名称（null表示未命中）

        public LogEntry(int id, String from, String method, String url, HttpRequest originalRequest, HttpResponse originalResponse, int originalResponseLen, int originalResponseCode, long originalResponseTime, HttpRequest modifiedRequest, HttpResponse modifiedResponse, String ruleName) {
            this.id = id;
            this.from = from;
            this.method = method;
            this.url = url;
            this.originalRequest = originalRequest;
            this.originalResponse = originalResponse;
            this.originalResponseLen = originalResponseLen;
            this.originalResponseCode = originalResponseCode;
            this.originalResponseTime = originalResponseTime;
            this.modifiedRequest = modifiedRequest;
            this.modifiedResponse = modifiedResponse;
            this.ruleName = ruleName;
        }
        
        // ✅ 辅助方法：判断是否命中
        public boolean isVulnerable() {
            return ruleName != null && !ruleName.isEmpty();
        }
    }


}

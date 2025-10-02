package com.xprobe.scanner.Logs;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class LogModel extends AbstractTableModel {

    //private final List<HttpResponseEntry> log;
    private List<LogEntry> log;
    
    // ✅ 最大容量限制（防止内存泄漏）
    private static final int MAX_ENTRIES = 10000;
    private static final int CLEANUP_THRESHOLD = 9000;  // 90%时触发清理

    public LogModel() {
        this.log = new ArrayList<>();
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

    public synchronized void add(int id, String from, String method, String url, HttpRequest originalRequest, HttpResponse originalResponse, int originalResponseLen, int originalResponseCode, long originalResponseTime, HttpRequest modifiedRequest, HttpResponse modifiedResponse, String ruleName) {
        // ✅ 检查是否需要清理旧数据
        if (log.size() >= CLEANUP_THRESHOLD) {
            cleanupOldEntries();
        }
        
        int index = log.size();
        log.add(new LogEntry(id, from, method, url, originalRequest, originalResponse, originalResponseLen, originalResponseCode, originalResponseTime, modifiedRequest, modifiedResponse, ruleName));
        fireTableRowsInserted(index, index);
    }
    
    /**
     * ✅ 清理旧条目（保留最新的50%）
     */
    private synchronized void cleanupOldEntries() {
        int removeCount = log.size() - (MAX_ENTRIES / 2);
        if (removeCount > 0) {
            // 删除最旧的条目
            for (int i = 0; i < removeCount; i++) {
                log.remove(0);
            }
            fireTableDataChanged();
        }
    }
    
    /**
     * ✅ 清空所有条目
     */
    public synchronized void clear() {
        log.clear();
        fireTableDataChanged();
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
        return log.size() >= MAX_ENTRIES;
    }

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

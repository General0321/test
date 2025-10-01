package com.xprobe.scanner.Logs;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class LogModel extends AbstractTableModel {

    //private final List<HttpResponseEntry> log;
    private List<LogEntry> log;

    public LogModel() {
        this.log = new ArrayList<>();
    }

    @Override
    public synchronized int getRowCount() {
        return log.size();
    }

    @Override
    public int getColumnCount() {
        return 7;  // 四列
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
//            // case 3 -> entry.timeBetweenRequestAndResponse != null ? entry.timeBetweenRequestAndResponse.toMillis() + " ms" : "N/A";
//            case 3 -> entry.timeBetweenRequestAndResponse != null ? String.format("%.3f s", entry.timeBetweenRequestAndResponse.toMillis() / 1000.0) : "N/A";
            default:
                return "";
        }
    }

    public synchronized void add(int id,String from, String method, String url, HttpRequest originalRequest, HttpResponse originalResponse, int originalResponseLen ,int originalResponseCode, long originalResponseTime, HttpRequest modifiedRequest, HttpResponse modifiedResponse) {
        int index = log.size();
        log.add(new LogEntry(id, from, method, url, originalRequest, originalResponse, originalResponseLen , originalResponseCode, originalResponseTime, modifiedRequest, modifiedResponse));
        fireTableRowsInserted(index, index);
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

        public LogEntry(int id, String from, String method, String url, HttpRequest originalRequest, HttpResponse originalResponse, int originalResponseLen, int originalResponseCode, long originalResponseTime, HttpRequest modifiedRequest, HttpResponse modifiedResponse) {
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
        }
    }


}

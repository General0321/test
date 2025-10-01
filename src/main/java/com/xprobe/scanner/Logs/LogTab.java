package com.xprobe.scanner.Logs;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import java.awt.*;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

public class LogTab {
    private JSplitPane mainSplitPane; // 主分割窗格

    public LogTab(MontoyaApi api, LogModel logModel) {

        // 主分割窗格
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // 带有请求/响应编辑器的选项卡
        JTabbedPane tabs = new JTabbedPane();

        // 创建只读的 HTTP 请求和响应编辑器
        HttpRequestEditor originalRequest = api.userInterface().createHttpRequestEditor(READ_ONLY);
        HttpResponseEditor originalResponse = api.userInterface().createHttpResponseEditor(READ_ONLY);
        JSplitPane originalRequestResponse = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, originalRequest.uiComponent(), originalResponse.uiComponent());
        originalRequestResponse.setResizeWeight(0.5); // 初始时分配等同的空间给请求和响应编辑器


        // 修改请求板
        HttpRequestEditor modifiedRequest = api.userInterface().createHttpRequestEditor(READ_ONLY);
        HttpResponseEditor modifiedResponse = api.userInterface().createHttpResponseEditor(READ_ONLY);
        JSplitPane modifiedRequestResponse = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, modifiedRequest.uiComponent(), modifiedResponse.uiComponent());
        modifiedRequestResponse.setResizeWeight(0.5); // 初始时分配等同的空间给请求和响应编辑器


        tabs.addTab("original", originalRequestResponse);
        tabs.addTab("modified", modifiedRequestResponse);

        splitPane.setBottomComponent(tabs);


        // 创建一个表格用于显示日志条目，获取model的数据，用于表格视图上展示
        JTable table = new JTable(logModel) {
            @Override
            public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
                if (rowIndex >= 0) {
                    LogModel.LogEntry LogEntry = logModel.get(rowIndex);
                    originalRequest.setRequest(LogEntry.originalRequest);
                    originalResponse.setResponse(LogEntry.originalResponse);

                    modifiedRequest.setRequest(LogEntry.modifiedRequest);
                    modifiedResponse.setResponse(LogEntry.modifiedResponse);
                }
                super.changeSelection(rowIndex, columnIndex, toggle, extend);
            }
        };

        // 创建滚动面板以封装表格，支持滚动
        JScrollPane scrollPane = new JScrollPane(table);

        // 使用类的属性初始化主分割窗格
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, splitPane);
        mainSplitPane.setResizeWeight(0.5); // 设置分割比例
    }

    public Component getComponent() {
        return mainSplitPane;
    }

    // 使用JSplitPane，通常是用来在一个顶级容器（如 JFrame）中进行分割布局的组件，然后套娃的方式，这2个布局如果使用另一个JSplitPane来封装，那么就变成次级布局了，这样就形成了一个新的布局，这样一直套
    // VERTICAL_SPLIT 垂直分割   HORIZONTAL_SPLIT 水平分割

}

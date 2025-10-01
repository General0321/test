package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.xprobe.scanner.Logs.LogModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

/**
 * 扫描结果选项卡 - 优化版
 */
public class ScanResultTab {
    private JSplitPane mainSplitPane;
    private final MontoyaApi api;
    private final LogModel logModel;
    
    // UI组件
    private JTable resultTable;
    private JTextField searchField;
    private JComboBox<String> filterComboBox;
    private TableRowSorter<LogModel> sorter;
    
    // 统计标签
    private JLabel totalCountLabel;
    private JLabel filteredCountLabel;
    private JLabel methodStatsLabel;
    
    // 编辑器
    private HttpRequestEditor originalRequest;
    private HttpResponseEditor originalResponse;
    private HttpRequestEditor modifiedRequest;
    private HttpResponseEditor modifiedResponse;

    public ScanResultTab(MontoyaApi api, LogModel logModel) {
        this.api = api;
        this.logModel = logModel;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
    }
    
    private void initializeComponents() {
        // 创建只读的 HTTP 请求和响应编辑器
        originalRequest = api.userInterface().createHttpRequestEditor(READ_ONLY);
        originalResponse = api.userInterface().createHttpResponseEditor(READ_ONLY);
        modifiedRequest = api.userInterface().createHttpRequestEditor(READ_ONLY);
        modifiedResponse = api.userInterface().createHttpResponseEditor(READ_ONLY);
        
        // 创建结果表格
        resultTable = new JTable(logModel) {
            @Override
            public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
                if (rowIndex >= 0) {
                    int modelRow = convertRowIndexToModel(rowIndex);
                    LogModel.LogEntry logEntry = logModel.get(modelRow);
                    originalRequest.setRequest(logEntry.originalRequest);
                    originalResponse.setResponse(logEntry.originalResponse);
                    modifiedRequest.setRequest(logEntry.modifiedRequest);
                    modifiedResponse.setResponse(logEntry.modifiedResponse);
                }
                super.changeSelection(rowIndex, columnIndex, toggle, extend);
            }
        };
        
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setRowHeight(22);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
        // 设置表格排序器
        sorter = new TableRowSorter<>(logModel);
        resultTable.setRowSorter(sorter);
        
        // 搜索框
        searchField = new JTextField(30);
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        // 过滤下拉框
        filterComboBox = new JComboBox<>(new String[]{
            "全部", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        });
        
        // 统计标签
        totalCountLabel = new JLabel("总计: 0");
        filteredCountLabel = new JLabel("显示: 0");
        methodStatsLabel = new JLabel("");
        
        Font statsFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        totalCountLabel.setFont(statsFont);
        filteredCountLabel.setFont(statsFont);
        methodStatsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    }
    
    private void setupLayout() {
        // 顶部控制面板
        JPanel topPanel = createTopPanel();
        
        // 中间编辑器面板
        JPanel editorPanel = createEditorPanel();
        
        // 表格滚动面板
        JScrollPane tableScrollPane = new JScrollPane(resultTable);
        tableScrollPane.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2),
            "📊 扫描结果列表",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 13)
        ));
        
        // 包含顶部控制面板和表格的面板
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.add(topPanel, BorderLayout.NORTH);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 主分割窗格 (垂直)
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, editorPanel);
        mainSplitPane.setResizeWeight(0.4);
        mainSplitPane.setDividerLocation(300);
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        
        // 左侧：搜索和过滤
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        leftPanel.add(new JLabel("🔍 搜索:"));
        leftPanel.add(searchField);
        
        leftPanel.add(Box.createHorizontalStrut(10));
        leftPanel.add(new JLabel("📋 方法:"));
        leftPanel.add(filterComboBox);
        
        JButton clearFilterButton = new JButton("清除过滤");
        clearFilterButton.addActionListener(e -> clearFilters());
        leftPanel.add(clearFilterButton);
        
        // 右侧：操作按钮
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JButton refreshButton = new JButton("🔄 刷新");
        JButton exportButton = new JButton("📤 导出");
        JButton clearButton = new JButton("🗑️ 清空");
        
        refreshButton.addActionListener(e -> refreshTable());
        exportButton.addActionListener(e -> exportResults());
        clearButton.addActionListener(e -> clearResults());
        
        rightPanel.add(refreshButton);
        rightPanel.add(exportButton);
        rightPanel.add(clearButton);
        
        // 中间：统计信息
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        statsPanel.add(totalCountLabel);
        statsPanel.add(new JLabel("|"));
        statsPanel.add(filteredCountLabel);
        statsPanel.add(new JLabel("|"));
        statsPanel.add(methodStatsLabel);
        
        // 组合面板
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(leftPanel, BorderLayout.WEST);
        controlPanel.add(statsPanel, BorderLayout.CENTER);
        controlPanel.add(rightPanel, BorderLayout.EAST);
        
        topPanel.add(controlPanel, BorderLayout.CENTER);
        
        return topPanel;
    }
    
    private JPanel createEditorPanel() {
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
            "📝 请求/响应详情",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 13)
        ));
        
        // 选项卡
        JTabbedPane tabs = new JTabbedPane();
        
        // 原始请求/响应
        JSplitPane originalRequestResponse = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            originalRequest.uiComponent(),
            originalResponse.uiComponent()
        );
        originalRequestResponse.setResizeWeight(0.5);
        
        // 修改后的请求/响应
        JSplitPane modifiedRequestResponse = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            modifiedRequest.uiComponent(),
            modifiedResponse.uiComponent()
        );
        modifiedRequestResponse.setResizeWeight(0.5);
        
        tabs.addTab("📨 原始请求/响应", originalRequestResponse);
        tabs.addTab("🔧 修改后请求/响应", modifiedRequestResponse);
        
        editorPanel.add(tabs, BorderLayout.CENTER);
        
        return editorPanel;
    }
    
    private void setupEventListeners() {
        // 搜索框实时过滤
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        
        // 方法过滤下拉框
        filterComboBox.addActionListener(e -> applyFilters());
        
        // 监听表格数据变化以更新统计
        logModel.addTableModelListener(e -> updateStatistics());
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().trim();
        String methodFilter = (String) filterComboBox.getSelectedItem();
        
        RowFilter<LogModel, Object> rf = null;
        
        try {
            java.util.List<RowFilter<LogModel, Object>> filters = new java.util.ArrayList<>();
            
            // 搜索过滤
            if (!searchText.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + searchText)); // 不区分大小写
            }
            
            // 方法过滤
            if (!"全部".equals(methodFilter)) {
                filters.add(RowFilter.regexFilter(methodFilter, 2)); // 第2列是Method
            }
            
            // 组合过滤器
            if (!filters.isEmpty()) {
                rf = RowFilter.andFilter(filters);
            }
            
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        
        sorter.setRowFilter(rf);
        updateStatistics();
    }
    
    private void clearFilters() {
        searchField.setText("");
        filterComboBox.setSelectedIndex(0);
        sorter.setRowFilter(null);
        updateStatistics();
    }
    
    private void refreshTable() {
        logModel.fireTableDataChanged();
        updateStatistics();
        api.logging().raiseInfoEvent("扫描结果已刷新");
    }
    
    private void exportResults() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出扫描结果");
        fileChooser.setSelectedFile(new File("scan_results.csv"));
        
        if (fileChooser.showSaveDialog(mainSplitPane) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // 写入CSV头
                writer.println("ID,来源,Method,URL,响应码,响应长度,响应时间");
                
                // 写入数据
                for (int i = 0; i < logModel.getRowCount(); i++) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        logModel.getValueAt(i, 0),
                        logModel.getValueAt(i, 1),
                        logModel.getValueAt(i, 2),
                        logModel.getValueAt(i, 3),
                        logModel.getValueAt(i, 4),
                        logModel.getValueAt(i, 5),
                        logModel.getValueAt(i, 6)
                    );
                }
                
                JOptionPane.showMessageDialog(mainSplitPane, 
                    "扫描结果已成功导出到: " + file.getAbsolutePath(), 
                    "导出成功", 
                    JOptionPane.INFORMATION_MESSAGE);
                api.logging().raiseInfoEvent("扫描结果已导出到: " + file.getAbsolutePath());
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainSplitPane, 
                    "导出失败: " + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
                api.logging().raiseErrorEvent("导出扫描结果失败: " + e.getMessage());
            }
        }
    }
    
    private void clearResults() {
        int result = JOptionPane.showConfirmDialog(
            mainSplitPane,
            "确定要清空所有扫描结果吗？\n此操作不可恢复！",
            "确认清空",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // 清空LogModel (需要在LogModel中实现clear方法)
            // 这里我们暂时通过刷新来模拟
            JOptionPane.showMessageDialog(mainSplitPane, 
                "清空功能需要在LogModel中实现clear()方法", 
                "提示", 
                JOptionPane.INFORMATION_MESSAGE);
            // TODO: logModel.clear();
            updateStatistics();
        }
    }
    
    private void updateStatistics() {
        int total = logModel.getRowCount();
        int filtered = resultTable.getRowCount();
        
        totalCountLabel.setText(String.format("总计: %d", total));
        filteredCountLabel.setText(String.format("显示: %d", filtered));
        
        // 统计各种方法的数量
        java.util.Map<String, Integer> methodCounts = new java.util.HashMap<>();
        for (int i = 0; i < total; i++) {
            String method = (String) logModel.getValueAt(i, 2); // Method列
            methodCounts.put(method, methodCounts.getOrDefault(method, 0) + 1);
        }
        
        // 格式化方法统计
        StringBuilder stats = new StringBuilder();
        methodCounts.forEach((method, count) -> {
            if (stats.length() > 0) stats.append(", ");
            stats.append(String.format("%s: %d", method, count));
        });
        
        methodStatsLabel.setText(stats.length() > 0 ? stats.toString() : "无数据");
        
        // 更新颜色
        totalCountLabel.setForeground(new Color(52, 152, 219));
        filteredCountLabel.setForeground(
            filtered < total ? new Color(241, 196, 15) : new Color(46, 204, 113)
        );
    }

    public Component getComponent() {
        return mainSplitPane;
    }
}


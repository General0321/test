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
    private JComboBox<String> vulnerableFilterComboBox;  // ✅ 新增：命中规则过滤
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
        
        // 创建结果表格（带高亮）
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
            
            // ✅ 高亮显示命中规则的行
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                
                if (!isRowSelected(row)) {
                    int modelRow = convertRowIndexToModel(row);
                    LogModel.LogEntry entry = logModel.get(modelRow);
                    
                    if (entry.isVulnerable()) {
                        // 命中规则：高亮显示（浅红色背景）
                        c.setBackground(new Color(255, 230, 230));
                        c.setForeground(new Color(200, 0, 0));
                    } else {
                        // 未命中：正常显示
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }
                } else {
                    // 选中行：使用默认选中颜色
                    c.setBackground(getSelectionBackground());
                    c.setForeground(getSelectionForeground());
                }
                
                return c;
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
        
        // ✅ 新增：命中规则过滤下拉框（初始只有"全部流量"和"所有命中"）
        vulnerableFilterComboBox = new JComboBox<>(new String[]{
            "全部流量", "所有命中"
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
        
        // ✅ 新增：命中规则过滤
        leftPanel.add(Box.createHorizontalStrut(10));
        leftPanel.add(new JLabel("🎯 筛选:"));
        leftPanel.add(vulnerableFilterComboBox);
        
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
    
    // ✅ 用于节流更新规则筛选选项
    private javax.swing.Timer updateRuleFilterTimer;
    private volatile boolean needsRuleFilterUpdate = false;
    
    private void setupEventListeners() {
        // 搜索框实时过滤
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        
        // 方法过滤下拉框
        filterComboBox.addActionListener(e -> applyFilters());
        
        // ✅ 命中规则过滤下拉框
        vulnerableFilterComboBox.addActionListener(e -> applyFilters());
        
        // ✅ 初始化节流定时器（每2秒最多更新一次规则筛选选项）
        updateRuleFilterTimer = new javax.swing.Timer(2000, e -> {
            if (needsRuleFilterUpdate) {
                updateRuleFilterOptions();
                needsRuleFilterUpdate = false;
            }
        });
        updateRuleFilterTimer.setRepeats(true);
        updateRuleFilterTimer.start();
        
        // ✅ 监听表格数据变化（节流更新 + 智能滚动）
        logModel.addTableModelListener(e -> {
            updateStatistics();
            // ✅ 标记需要更新，由定时器批量执行
            needsRuleFilterUpdate = true;
            
            // ✅ 智能滚动：只有当用户在底部时，才自动滚动到最新记录
            if (e.getType() == javax.swing.event.TableModelEvent.INSERT) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        int lastRow = resultTable.getRowCount() - 1;
                        if (lastRow < 0) return;
                        
                        // 获取滚动面板
                        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, resultTable);
                        if (scrollPane == null) return;
                        
                        JViewport viewport = scrollPane.getViewport();
                        Rectangle viewRect = viewport.getViewRect();
                        Rectangle tableRect = resultTable.getBounds();
                        
                        // ✅ 判断用户是否在底部附近（距离底部小于100像素）
                        boolean isNearBottom = (viewRect.y + viewRect.height) >= (tableRect.height - 100);
                        
                        // ✅ 只有在底部时，才自动滚动
                        if (isNearBottom) {
                            resultTable.scrollRectToVisible(resultTable.getCellRect(lastRow, 0, true));
                        }
                    } catch (Exception ex) {
                        // 忽略滚动错误
                    }
                });
            }
        });
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().trim();
        String methodFilter = (String) filterComboBox.getSelectedItem();
        String vulnerableFilter = (String) vulnerableFilterComboBox.getSelectedItem();
        
        // ✅ 安全检查：避免在更新下拉框时触发过滤
        if (vulnerableFilter == null) {
            return;
        }
        
        RowFilter<LogModel, Object> rf = null;
        
        try {
            java.util.List<RowFilter<LogModel, Object>> filters = new java.util.ArrayList<>();
            
            // 搜索过滤
            if (!searchText.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + searchText)); // 不区分大小写
            }
            
            // 方法过滤
            if (methodFilter != null && !"全部".equals(methodFilter)) {
                filters.add(RowFilter.regexFilter(methodFilter, 2)); // 第2列是Method
            }
            
            // ✅ 命中规则过滤
            if (!"全部流量".equals(vulnerableFilter)) {
                filters.add(new RowFilter<LogModel, Object>() {
                    @Override
                    public boolean include(Entry<? extends LogModel, ?> entry) {
                        int modelRow = (Integer) entry.getIdentifier();
                        LogModel.LogEntry logEntry = logModel.get(modelRow);
                        
                        if ("所有命中".equals(vulnerableFilter)) {
                            // 显示所有命中了任何规则的
                            return logEntry.isVulnerable();
                        } else {
                            // 显示命中特定规则的
                            return vulnerableFilter.equals(logEntry.ruleName);
                        }
                    }
                });
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
        vulnerableFilterComboBox.setSelectedIndex(0);  // ✅ 重置命中规则过滤
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
            // ✅ 清空LogModel
            logModel.clear();
            updateStatistics();
            api.logging().raiseInfoEvent("扫描结果已清空");
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
    
    /**
     * 更新规则筛选选项（动态添加已出现的规则名称）
     */
    private void updateRuleFilterOptions() {
        // ✅ 临时移除ActionListener，避免在更新过程中触发过滤
        java.awt.event.ActionListener[] listeners = vulnerableFilterComboBox.getActionListeners();
        for (java.awt.event.ActionListener listener : listeners) {
            vulnerableFilterComboBox.removeActionListener(listener);
        }
        
        try {
            // 收集所有出现过的规则名称
            java.util.Set<String> ruleNames = new java.util.HashSet<>();
            for (int i = 0; i < logModel.getRowCount(); i++) {
                LogModel.LogEntry entry = logModel.get(i);
                if (entry.isVulnerable() && entry.ruleName != null) {
                    ruleNames.add(entry.ruleName);
                }
            }
            
            // 保存当前选择
            String currentSelection = (String) vulnerableFilterComboBox.getSelectedItem();
            
            // 重建下拉框选项
            vulnerableFilterComboBox.removeAllItems();
            vulnerableFilterComboBox.addItem("全部流量");
            vulnerableFilterComboBox.addItem("所有命中");
            
            // 添加具体的规则名称（按字母顺序）
            java.util.List<String> sortedRules = new java.util.ArrayList<>(ruleNames);
            java.util.Collections.sort(sortedRules);
            for (String ruleName : sortedRules) {
                vulnerableFilterComboBox.addItem(ruleName);
            }
            
            // 恢复之前的选择（如果还存在）
            if (currentSelection != null) {
                for (int i = 0; i < vulnerableFilterComboBox.getItemCount(); i++) {
                    if (currentSelection.equals(vulnerableFilterComboBox.getItemAt(i))) {
                        vulnerableFilterComboBox.setSelectedIndex(i);
                        return;
                    }
                }
            }
            
            // 如果之前的选择不存在了，默认选择"全部流量"
            vulnerableFilterComboBox.setSelectedIndex(0);
            
        } finally {
            // ✅ 重新添加ActionListener
            for (java.awt.event.ActionListener listener : listeners) {
                vulnerableFilterComboBox.addActionListener(listener);
            }
        }
    }

    /**
     * ✅ 清理资源（停止Timer等）
     */
    public void cleanup() {
        if (updateRuleFilterTimer != null) {
            updateRuleFilterTimer.stop();
            updateRuleFilterTimer = null;
        }
    }
    
    public Component getComponent() {
        // ✅ 添加组件移除监听器，自动清理
        if (mainSplitPane.getComponentListeners().length == 0) {
            mainSplitPane.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (!mainSplitPane.isDisplayable()) {
                        cleanup();
                    }
                }
            });
        }
        return mainSplitPane;
    }
}


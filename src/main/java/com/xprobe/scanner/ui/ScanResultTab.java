package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.xprobe.scanner.Logs.LogModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

/**
 * 扫描结果选项卡 - 优化版
 */
public class ScanResultTab {
    private JSplitPane mainSplitPane;
    private final MontoyaApi api;
    private final LogModel logModel;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;  // ✅ 实时扫描器（用于清空扫描缓存）
    private final com.xprobe.scanner.core.OriginalResponseCache responseCache;  // ✅ 原始响应缓存（用于清空缓存）
    
    // UI组件
    private JTable resultTable;
    private JTextField searchField;  // 搜索框
    private JComboBox<String> filterComboBox;
    private JComboBox<String> vulnerableFilterComboBox;  // 命中规则过滤
    private TableRowSorter<LogModel> sorter;
    
    // 统计标签（使用组合面板：文字 + 带颜色的数字）
    private JPanel totalCountPanel;
    private JLabel totalCountNumberLabel;  // 数字部分（浅蓝色）
    private JPanel filteredCountPanel;
    private JLabel filteredCountNumberLabel;  // 数字部分（浅绿色）
    private JLabel methodStatsLabel;
    
    // 编辑器
    private HttpRequestEditor originalRequest;
    private HttpResponseEditor originalResponse;
    private HttpRequestEditor modifiedRequest;
    private HttpResponseEditor modifiedResponse;

    public ScanResultTab(MontoyaApi api, LogModel logModel, 
                        com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner,
                        com.xprobe.scanner.core.OriginalResponseCache responseCache) {
        this.api = api;
        this.logModel = logModel;
        this.realtimeScanner = realtimeScanner;  // ✅ 保存实时扫描器引用（用于清空扫描缓存）
        this.responseCache = responseCache;  // ✅ 保存响应缓存引用（用于清空缓存）
        
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
            
            // ✅ 使用默认样式（去掉颜色高亮）
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                
                if (!isRowSelected(row)) {
                    // 未选中行：使用默认白色背景
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
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
        // ✅ 使用普通字体，不使用粗体（Burp Suite风格）
        resultTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        // 设置表格排序器
        sorter = new TableRowSorter<>(logModel);
        resultTable.setRowSorter(sorter);
        
        // ✅ 设置列宽（URL列要相对长）
        javax.swing.table.TableColumnModel columnModel = resultTable.getColumnModel();
        // #: 50, 来源: 80, Method: 80, URL: 400（更宽）, 响应码: 80, 响应长度: 100, 响应时间: 100, 命中规则: 150
        if (columnModel.getColumnCount() >= 8) {
            columnModel.getColumn(0).setPreferredWidth(50);   // #
            columnModel.getColumn(1).setPreferredWidth(80);   // 来源
            columnModel.getColumn(2).setPreferredWidth(80);   // Method
            columnModel.getColumn(3).setPreferredWidth(400);  // URL (更宽)
            columnModel.getColumn(4).setPreferredWidth(80);   // 响应码
            columnModel.getColumn(5).setPreferredWidth(100);  // 响应长度
            columnModel.getColumn(6).setPreferredWidth(100);  // 响应时间
            columnModel.getColumn(7).setPreferredWidth(150);  // 命中规则
        }
        
        // 搜索框
        searchField = new JTextField(20);
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        // 过滤下拉框（匹配图片中的"全部"）
        filterComboBox = new JComboBox<>(new String[]{
            "全部", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        });
        
        // 命中规则过滤下拉框（匹配图片中的"全部流量"）
        vulnerableFilterComboBox = new JComboBox<>(new String[]{
            "全部流量", "所有命中"
        });
        
        // ✅ 统计标签（使用组合面板：文字 + 带颜色的数字）
        Font statsFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        
        // 总计：文字 + 数字（浅蓝色）
        totalCountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel totalTextLabel = new JLabel("总计: ");
        totalTextLabel.setFont(statsFont);
        totalCountNumberLabel = new JLabel("0");
        totalCountNumberLabel.setFont(statsFont);
        totalCountNumberLabel.setForeground(new Color(0x5DADE2));  // 浅蓝色
        totalCountPanel.add(totalTextLabel);
        totalCountPanel.add(totalCountNumberLabel);
        
        // 显示：文字 + 数字（浅绿色）
        filteredCountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel filteredTextLabel = new JLabel("显示: ");
        filteredTextLabel.setFont(statsFont);
        filteredCountNumberLabel = new JLabel("0");
        filteredCountNumberLabel.setFont(statsFont);
        filteredCountNumberLabel.setForeground(new Color(0x58D68D));  // 浅绿色
        filteredCountPanel.add(filteredTextLabel);
        filteredCountPanel.add(filteredCountNumberLabel);
        
        methodStatsLabel = new JLabel("");
        methodStatsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    }
    
    private void setupLayout() {
        // 顶部控制面板
        JPanel topPanel = createTopPanel();
        
        // 中间编辑器面板
        JPanel editorPanel = createEditorPanel();
        
        // 表格滚动面板
        JScrollPane tableScrollPane = new JScrollPane(resultTable);
        // ✅ BLIT滚动优化（像Burp一样丝滑）
        tableScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tableScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        
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
        // 顶部控制面板：左侧、中间、右侧三部分
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        // 左侧：搜索图标、搜索框、过滤下拉框和清除按钮
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        // 🔍 搜索图标（最左上角）
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        leftPanel.add(searchIcon);
        leftPanel.add(searchField);
        
        leftPanel.add(Box.createHorizontalStrut(5));
        leftPanel.add(filterComboBox);
        leftPanel.add(vulnerableFilterComboBox);
        JButton clearFilterButton = new JButton("清除");
        clearFilterButton.addActionListener(e -> clearFilters());
        leftPanel.add(clearFilterButton);
        
        // 右侧：操作按钮
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton refreshButton = new JButton("刷新");
        JButton exportButton = new JButton("导出");
        JButton clearButton = new JButton("清空结果");
        JButton clearCacheButton = new JButton("清空扫描缓存");
        refreshButton.addActionListener(e -> refreshTable());
        exportButton.addActionListener(e -> exportResults());
        clearButton.addActionListener(e -> clearResults());
        clearCacheButton.addActionListener(e -> clearPassiveScanCache());
        rightPanel.add(refreshButton);
        rightPanel.add(exportButton);
        rightPanel.add(clearButton);
        rightPanel.add(clearCacheButton);
        
        // 中间：统计信息
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        statsPanel.add(totalCountPanel);
        statsPanel.add(new JLabel("|"));
        statsPanel.add(filteredCountPanel);
        statsPanel.add(new JLabel("|"));
        statsPanel.add(methodStatsLabel);
        
        // 组合：左侧 | 中间（统计） | 右侧
        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(statsPanel, BorderLayout.CENTER);
        topPanel.add(rightPanel, BorderLayout.EAST);
        
        return topPanel;
    }
    
    private JPanel createEditorPanel() {
        JPanel editorPanel = new JPanel(new BorderLayout());
        
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
        
        tabs.addTab("原始请求/响应", originalRequestResponse);
        tabs.addTab("修改后请求/响应", modifiedRequestResponse);
        
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
        
        // 命中规则过滤下拉框
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
        
        // 安全检查：避免在更新下拉框时触发过滤
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
            
            // 命中规则过滤
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
        vulnerableFilterComboBox.setSelectedIndex(0);
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
            try (java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), 
                    java.nio.charset.StandardCharsets.UTF_8);
                 java.io.BufferedWriter bw = new java.io.BufferedWriter(osw);
                 PrintWriter writer = new PrintWriter(bw)) {
                
                // ✅ 写入UTF-8 BOM（可选，有助于Excel等软件识别UTF-8编码）
                writer.write('\ufeff');
                
                // 写入CSV头（包含所有列）
                writer.println("ID,来源,Method,URL,响应码,响应长度,响应时间,命中规则");
                
                // ✅ 导出当前表格中显示的数据（过滤后的结果）
                int visibleRowCount = resultTable.getRowCount();
                for (int i = 0; i < visibleRowCount; i++) {
                    // 将视图行索引转换为模型行索引
                    int modelRow = resultTable.convertRowIndexToModel(i);
                    
                    // 获取数据（包括所有8列）
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                        escapeCsvValue(logModel.getValueAt(modelRow, 0)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 1)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 2)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 3)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 4)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 5)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 6)),
                        escapeCsvValue(logModel.getValueAt(modelRow, 7))  // 命中规则
                    );
                }
                
                JOptionPane.showMessageDialog(mainSplitPane, 
                    String.format("扫描结果已成功导出到: %s\n\n导出 %d 条记录（当前表格显示的数据）", 
                        file.getAbsolutePath(), visibleRowCount), 
                    "导出成功", 
                    JOptionPane.INFORMATION_MESSAGE);
                api.logging().raiseInfoEvent(String.format(
                    "扫描结果已导出到: %s (共 %d 条记录)", 
                    file.getAbsolutePath(), visibleRowCount
                ));
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainSplitPane, 
                    "导出失败: " + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
                api.logging().raiseErrorEvent("导出扫描结果失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * ✅ CSV值转义（处理包含逗号、引号、换行符的值）
     */
    private String escapeCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        // 如果包含逗号、引号或换行符，需要用引号包裹并转义引号
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
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
    
    /**
     * ✅ 清空被动扫描缓存
     * 功能：清空扫描去重缓存和原始响应缓存，使被动扫描可以重新扫描
     * 
     * 说明：
     * - 被动扫描的去重逻辑由规则的"去重颗粒度"配置决定
     * - 去重key格式：根据规则配置动态生成（如：ruleId|method|host|path|param）
     * - 编辑规则后，规则ID不变，内容变了，必须清空缓存才能用新规则重新扫描
     * - 同时清空原始响应缓存，确保重新扫描时使用最新的响应
     * 
     * 注意：
     * - 添加新规则不需要清空缓存（新规则ID会自动扫描）
     * - 修改规则后需要清空缓存（规则ID不变，需要强制重新扫描）
     */
    private void clearPassiveScanCache() {
        int result = JOptionPane.showConfirmDialog(
            mainSplitPane,
            "确定要清空被动扫描缓存吗？\n\n" +
            "这将清空以下缓存：\n" +
            "• 扫描去重缓存（使被动扫描可以重新扫描之前的流量）\n" +
            "• 原始响应缓存（确保重新扫描时使用最新的响应）\n\n" +
            "适用场景：\n" +
            "• 修改了规则，想用新规则重新扫描\n" +
            "• 测试规则配置是否正确\n" +
            "• 怀疑之前的扫描有漏报",
            "确认清空缓存",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // ✅ 清空扫描去重缓存
            // 说明：去重逻辑完全由规则的"去重颗粒度"配置决定
            // - 每个规则有独立的去重key（ruleId + 其他信息）
            // - 编辑规则时，ruleId不变，缓存中的旧key还在，必须清空才能重新扫描
            if (realtimeScanner != null) {
                realtimeScanner.clearPassiveScanCache();
            }
            
            // ✅ 清空原始响应缓存
            int responseCacheSize = 0;
            if (responseCache != null) {
                responseCacheSize = responseCache.size();
                responseCache.clear();
                api.logging().raiseInfoEvent("✅ 原始响应缓存已清空（清空前: " + responseCacheSize + " 条）");
            }
            
            JOptionPane.showMessageDialog(
                mainSplitPane,
                "✅ 被动扫描缓存已清空！\n\n" +
                "已清空：\n" +
                "• 扫描去重缓存（使被动扫描可以重新扫描）\n" +
                "• 原始响应缓存（" + responseCacheSize + " 条）\n\n" +
                "说明：\n" +
                "• 每个规则都有独立的去重缓存\n" +
                "• 去重颗粒度由规则配置决定\n" +
                "• 之前扫描过的流量现在可以重新扫描了",
                "清空成功",
                JOptionPane.INFORMATION_MESSAGE
            );
            
            api.logging().raiseInfoEvent("✅ 用户手动清空了被动扫描缓存（去重缓存 + 响应缓存）");
        }
    }
    
    private void updateStatistics() {
        int total = logModel.getRowCount();
        int filtered = resultTable.getRowCount();
        
        // ✅ 更新数字部分（颜色已在初始化时设置）
        totalCountNumberLabel.setText(String.valueOf(total));
        filteredCountNumberLabel.setText(String.valueOf(filtered));
        
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
        
        // ✅ 去掉颜色设置，使用默认颜色
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


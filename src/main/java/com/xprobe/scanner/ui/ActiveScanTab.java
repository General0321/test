package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.active.ActiveScanner;
import com.xprobe.scanner.active.ScanTarget;
import com.xprobe.scanner.active.ScanResult;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.integration.ScanResultIntegrator;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 主动扫描选项卡
 */
public class ActiveScanTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final ActiveScanner activeScanner;
    private ScanResultIntegrator integrator;
    
    // UI组件
    private JTable targetTable;
    private DefaultTableModel targetTableModel;
    private JTable resultTable;
    private DefaultTableModel resultTableModel;
    private JTextArea targetInputArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton startScanButton;
    private JButton stopScanButton;
    private JButton configButton;
    private JButton realtimeButton;
    private JButton clearButton;
    private JButton arjunScanButton;
    
    // 扫描控制
    private volatile boolean isScanning = false;
    private volatile boolean isRealtimeScanning = false;
    private CompletableFuture<Void> currentScanTask;

    public ActiveScanTab(MontoyaApi api, ConfigurationManager configManager, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.configManager = configManager;
        this.activeScanner = new ActiveScanner(api, configManager, realtimeScanner);
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
    }
    
    public void setIntegrator(ScanResultIntegrator integrator) {
        this.integrator = integrator;
    }

    private void initializeComponents() {
            // 目标表格
            targetTableModel = new DefaultTableModel(new Object[]{"目标URL", "状态", "收集请求数", "发现接口数", "发现参数数", "Arjun探测结果数", "提交被动扫描数"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        targetTable = new JTable(targetTableModel);
        targetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 结果表格
        resultTableModel = new DefaultTableModel(new Object[]{"目标", "接口", "参数", "类型", "状态", "证据", "时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultTable = new JTable(resultTableModel);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 目标输入区域
        targetInputArea = new JTextArea(6, 50);
        targetInputArea.setBorder(BorderFactory.createTitledBorder("扫描目标 (每行一个URL) - 将基于Burp被动流量进行探测"));
        targetInputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        targetInputArea.setText("示例:\nhttps://example.com\nhttps://api.example.com\nhttps://admin.example.com");

        // 进度条和状态标签
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("就绪");

        // 按钮
        startScanButton = new JButton("开始扫描");
        stopScanButton = new JButton("停止扫描");
        configButton = new JButton("工具配置");
        realtimeButton = new JButton("启动实时扫描");
        clearButton = new JButton("清空结果");
        arjunScanButton = new JButton("Arjun参数探测");
        
        stopScanButton.setEnabled(false);
    }

    private void setupLayout() {
        panel = new JPanel(new BorderLayout());

        // 顶部：目标输入和控制面板
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JScrollPane(targetInputArea), BorderLayout.CENTER);
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(startScanButton);
        controlPanel.add(stopScanButton);
        controlPanel.add(arjunScanButton);
        controlPanel.add(configButton);
        controlPanel.add(realtimeButton);
        controlPanel.add(clearButton);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(progressBar);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(statusLabel);
        
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(controlPanel, BorderLayout.SOUTH);

        // 中间：分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(200);

        // 目标表格
        JScrollPane targetScrollPane = new JScrollPane(targetTable);
        targetScrollPane.setBorder(BorderFactory.createTitledBorder("扫描目标列表"));
        
        // 结果表格
        JScrollPane resultScrollPane = new JScrollPane(resultTable);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("扫描结果"));

        splitPane.setTopComponent(targetScrollPane);
        splitPane.setBottomComponent(resultScrollPane);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
    }

    private void setupEventListeners() {
        startScanButton.addActionListener(e -> startScan());
        stopScanButton.addActionListener(e -> stopScan());
        arjunScanButton.addActionListener(e -> startArjunScan());
        configButton.addActionListener(e -> showToolConfigDialog());
        realtimeButton.addActionListener(e -> toggleRealtimeScanning());
        clearButton.addActionListener(e -> clearResults());
        
        // 目标表格选择监听器
        targetTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = targetTable.getSelectedRow();
                if (selectedRow != -1) {
                    // 可以在这里添加显示目标详情的逻辑
                }
            }
        });
        
        // 结果表格选择监听器
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = resultTable.getSelectedRow();
                if (selectedRow != -1) {
                    // 可以在这里添加显示结果详情的逻辑
                }
            }
        });
    }

    private void startScan() {
        String targetsText = targetInputArea.getText().trim();
        if (targetsText.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请输入要扫描的目标URL", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] targetUrls = targetsText.split("\\r?\\n");
        List<ScanTarget> targets = new java.util.ArrayList<>();
        
        for (String url : targetUrls) {
            url = url.trim();
            if (!url.isEmpty()) {
                targets.add(new ScanTarget(url));
            }
        }

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有有效的目标URL", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 更新UI状态
        isScanning = true;
        startScanButton.setEnabled(false);
        stopScanButton.setEnabled(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("正在扫描...");

        // 清空之前的结果
        targetTableModel.setRowCount(0);
        resultTableModel.setRowCount(0);

        // 添加目标到表格
        for (ScanTarget target : targets) {
            targetTableModel.addRow(new Object[]{
                target.getUrl(),
                "等待中",
                0, // 收集请求数
                0, // 发现接口数
                0, // 发现参数数
                0  // 扫描结果数
            });
        }

        // 开始异步扫描
        currentScanTask = CompletableFuture.runAsync(() -> {
            try {
                performScan(targets);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("扫描出错: " + e.getMessage());
                    JOptionPane.showMessageDialog(panel, "扫描过程中出现错误: " + e.getMessage(), 
                        "错误", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isScanning = false;
                    startScanButton.setEnabled(true);
                    stopScanButton.setEnabled(false);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    statusLabel.setText("扫描完成");
                });
            }
        });
    }

    private void performScan(List<ScanTarget> targets) {
        int totalTargets = targets.size();
        int completedTargets = 0;

        for (ScanTarget target : targets) {
            if (!isScanning) {
                break; // 如果用户停止了扫描
            }

            try {
                // 更新目标状态
                SwingUtilities.invokeLater(() -> {
                    int rowIndex = targets.indexOf(target);
                    if (rowIndex >= 0) {
                        targetTableModel.setValueAt("扫描中", rowIndex, 1);
                    }
                });

                // 执行主动扫描
                List<ScanResult> results = activeScanner.scanTarget(target);

                // 更新结果
                SwingUtilities.invokeLater(() -> {
                    int rowIndex = targets.indexOf(target);
                    if (rowIndex >= 0) {
                        int interfaceCount = (int) results.stream()
                            .filter(r -> "VALID_ENDPOINT".equals(r.getType()) || "VALID_COMBINATION".equals(r.getType()))
                            .map(ScanResult::getEndpoint)
                            .distinct()
                            .count();
                        int parameterCount = (int) results.stream()
                            .filter(r -> "VALID_PARAMETER".equals(r.getType()) || "VALID_COMBINATION".equals(r.getType()))
                            .map(ScanResult::getParameter)
                            .distinct()
                            .count();
                        int arjunResultCount = (int) results.stream()
                            .filter(r -> r.getType().startsWith("VALID") && !"COLLECTION_STATS".equals(r.getType()))
                            .count();
                        int submittedCount = (int) results.stream()
                            .filter(r -> r.getType().startsWith("VALID") && !"COLLECTION_STATS".equals(r.getType()))
                            .count();
                        int totalResultCount = results.size();
                        
                        // 从扫描结果中获取收集的请求数
                        int collectedRequests = getCollectedRequestsCountFromResults(results);
                        
                        targetTableModel.setValueAt("完成", rowIndex, 1);
                        targetTableModel.setValueAt(collectedRequests, rowIndex, 2);
                        targetTableModel.setValueAt(interfaceCount, rowIndex, 3);
                        targetTableModel.setValueAt(parameterCount, rowIndex, 4);
                        targetTableModel.setValueAt(arjunResultCount, rowIndex, 5);
                        targetTableModel.setValueAt(submittedCount, rowIndex, 6);
                    }

                    // 添加结果到结果表格（过滤掉统计信息）
                    for (ScanResult result : results) {
                        if (!"COLLECTION_STATS".equals(result.getType())) {
                            String typeDisplay = getTypeDisplayName(result.getType());
                            String statusDisplay = getStatusDisplayName(result.getStatus(), result.getType());
                            
                            resultTableModel.addRow(new Object[]{
                                result.getTarget().getUrl(),
                                result.getEndpoint(),
                                result.getParameter(),
                                typeDisplay,
                                statusDisplay,
                                result.getEvidence(),
                                result.getTimestamp()
                            });
                        }
                    }
                    
                    // 集成主动扫描结果到被动扫描器
                    if (integrator != null) {
                        integrator.integrateActiveScanResultsAsync(results);
                    }
                });

                completedTargets++;
                
                // 更新进度
                final int currentCompleted = completedTargets;
                SwingUtilities.invokeLater(() -> {
                    int progress = (currentCompleted * 100) / totalTargets;
                    progressBar.setValue(progress);
                    statusLabel.setText("扫描进度: " + currentCompleted + "/" + totalTargets);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    int rowIndex = targets.indexOf(target);
                    if (rowIndex >= 0) {
                        targetTableModel.setValueAt("错误", rowIndex, 1);
                    }
                });
                api.logging().raiseErrorEvent("扫描目标 " + target.getUrl() + " 时出错: " + e.getMessage());
            }
        }
    }

    private void stopScan() {
        isScanning = false;
        if (currentScanTask != null && !currentScanTask.isDone()) {
            currentScanTask.cancel(true);
        }
        
        startScanButton.setEnabled(true);
        stopScanButton.setEnabled(false);
        progressBar.setIndeterminate(false);
        statusLabel.setText("扫描已停止");
    }

    private void clearResults() {
        int result = JOptionPane.showConfirmDialog(
            panel,
            "确定要清空所有扫描结果吗？",
            "确认清空",
            JOptionPane.YES_NO_OPTION
        );
        
        if (result == JOptionPane.YES_OPTION) {
            targetTableModel.setRowCount(0);
            resultTableModel.setRowCount(0);
            targetInputArea.setText("");
            progressBar.setValue(0);
            statusLabel.setText("就绪");
        }
    }
    
    /**
     * 开始Arjun参数探测
     */
    private void startArjunScan() {
        try {
            // 检查是否有RealtimeScanner
            if (activeScanner.getRealtimeScanner() == null) {
                JOptionPane.showMessageDialog(panel, 
                    "请先启动实时扫描以收集参数数据", 
                    "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // 确认对话框
            int result = JOptionPane.showConfirmDialog(panel, 
                "确定要开始Arjun参数探测吗？\n这将对所有收集到的接口和参数进行探测。", 
                "确认Arjun探测", JOptionPane.YES_NO_OPTION);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            
            // 更新UI状态
            arjunScanButton.setEnabled(false);
            statusLabel.setText("正在执行Arjun参数探测...");
            
            // 异步执行Arjun参数探测
            CompletableFuture.runAsync(() -> {
                try {
                    activeScanner.getRealtimeScanner().triggerManualArjunScan();
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(panel, 
                            "Arjun参数探测执行失败: " + e.getMessage(), 
                            "错误", JOptionPane.ERROR_MESSAGE);
                    });
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        arjunScanButton.setEnabled(true);
                        statusLabel.setText("Arjun参数探测完成");
                    });
                }
            });
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(panel, 
                "启动Arjun参数探测时出错: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
            arjunScanButton.setEnabled(true);
            statusLabel.setText("Arjun参数探测失败");
        }
    }

    private int getCollectedRequestsCountFromResults(List<ScanResult> results) {
        // 从扫描结果中查找收集统计信息
        for (ScanResult result : results) {
            if ("COLLECTION_STATS".equals(result.getType())) {
                try {
                    return Integer.parseInt(result.getStatus());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    private String getTypeDisplayName(String type) {
        switch (type) {
            case "VALID_PARAMETER":
                return "有效参数";
            case "INVALID_PARAMETER":
                return "无效参数";
            case "VALID_ENDPOINT":
                return "有效接口";
            case "INVALID_ENDPOINT":
                return "无效接口";
            case "VALID_COMBINATION":
                return "有效组合";
            case "INVALID_COMBINATION":
                return "无效组合";
            case "ARJUN_RESULT":
                return "Arjun探测结果";
            case "EXTERNAL_TOOL":
                return "外部工具";
            default:
                return type;
        }
    }
    
    private String getStatusDisplayName(String status, String type) {
        if (type.startsWith("VALID")) {
            return "✓ " + status;
        } else if (type.startsWith("INVALID")) {
            return "✗ " + status;
        }
        return status;
    }
    
    private void showToolConfigDialog() {
        ExternalToolConfigDialog dialog = new ExternalToolConfigDialog(
            SwingUtilities.getWindowAncestor(panel), 
            activeScanner.getToolConfig()
        );
        
        dialog.setVisible(true);
        
        if (dialog.isConfigUpdated()) {
            activeScanner.updateToolConfig(dialog.getUpdatedConfig());
            api.logging().raiseInfoEvent("外部工具配置已更新");
        }
    }
    
    private void toggleRealtimeScanning() {
        if (isRealtimeScanning) {
            // 停止实时扫描
            activeScanner.stopRealtimeScanning();
            isRealtimeScanning = false;
            realtimeButton.setText("启动实时扫描");
            realtimeButton.setBackground(new Color(46, 204, 113));
            statusLabel.setText("实时扫描已停止");
            api.logging().raiseInfoEvent("实时扫描已停止");
        } else {
            // 启动实时扫描
            activeScanner.startRealtimeScanning();
            isRealtimeScanning = true;
            realtimeButton.setText("停止实时扫描");
            realtimeButton.setBackground(new Color(231, 76, 60));
            statusLabel.setText("实时扫描已启动");
            api.logging().raiseInfoEvent("实时扫描已启动");
        }
    }

    public Component getComponent() {
        return panel;
    }
    
    public ActiveScanner getActiveScanner() {
        return activeScanner;
    }
}

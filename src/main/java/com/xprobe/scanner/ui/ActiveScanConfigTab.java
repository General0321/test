package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.active.RealtimeScannerRefactored;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 主动扫描配置选项卡 - 专注于主动探测参数配置
 */
public class ActiveScanConfigTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final RealtimeScannerRefactored realtimeScanner;
    
    // 主动扫描配置
    private JSpinner bruteforceIntervalSpinner;
    private JSpinner minParameterCountSpinner;
    private JSpinner maxConcurrentHostsSpinner;
    private JCheckBox autoStartCheckBox;
    private JCheckBox verboseLoggingCheckBox;
    private JCheckBox enableActiveScanCheckBox;
    
    // 全局自定义字典
    private JTextArea globalCustomDictArea;
    private JButton addCustomParamButton;
    private JButton removeCustomParamButton;
    private JButton importDictButton;
    private JButton exportDictButton;
    private JButton clearDictButton;
    
    // 统计信息
    private JTable hostStatsTable;
    private DefaultTableModel hostStatsModel;
    private JButton refreshStatsButton;
    
    public ActiveScanConfigTab(MontoyaApi api, RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadCurrentConfig();
    }
    
    private void initializeComponents() {
        // 主动扫描配置
        bruteforceIntervalSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 300, 10));
        minParameterCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        maxConcurrentHostsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        autoStartCheckBox = new JCheckBox("自动启动实时扫描");
        verboseLoggingCheckBox = new JCheckBox("详细日志记录");
        enableActiveScanCheckBox = new JCheckBox("启用主动探测");
        
        // 全局自定义字典
        globalCustomDictArea = new JTextArea(10, 40);
        globalCustomDictArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        addCustomParamButton = new JButton("添加参数");
        removeCustomParamButton = new JButton("移除选中");
        importDictButton = new JButton("导入字典");
        exportDictButton = new JButton("导出字典");
        clearDictButton = new JButton("清空字典");
        
        // 统计信息表格
        hostStatsModel = new DefaultTableModel(new Object[]{"Host", "接口数", "参数数", "爆破次数", "最后更新"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        hostStatsTable = new JTable(hostStatsModel);
        hostStatsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        refreshStatsButton = new JButton("刷新统计");
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 创建选项卡
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 扫描配置选项卡
        tabbedPane.addTab("扫描配置", createScanConfigPanel());
        
        // 自定义字典选项卡
        tabbedPane.addTab("自定义字典", createCustomDictPanel());
        
        // 统计信息选项卡
        tabbedPane.addTab("统计信息", createStatsPanel());
        
        panel.add(tabbedPane, BorderLayout.CENTER);
    }
    
    
    private JPanel createScanConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 爆破间隔
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("爆破间隔(秒):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(bruteforceIntervalSpinner, gbc);
        
        // 最小参数数量
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("最小参数数量:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(minParameterCountSpinner, gbc);
        
        // 最大并发host数
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("最大并发host数:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(maxConcurrentHostsSpinner, gbc);
        
        // 启用主动探测
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(enableActiveScanCheckBox, gbc);
        
        // 自动启动
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(autoStartCheckBox, gbc);
        
        // 详细日志
        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(verboseLoggingCheckBox, gbc);
        
        // 说明文本
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        JTextArea helpText = new JTextArea(6, 40);
        helpText.setText("主动探测配置说明:\n" +
            "• 爆破间隔: Arjun探测的最小时间间隔\n" +
            "• 最小参数数量: 触发探测所需的最少参数数\n" +
            "• 最大并发host数: 同时处理的host数量限制\n" +
            "• 启用主动探测: 控制是否进行主动探测\n" +
            "• 自动启动: 插件加载时自动启动实时扫描\n" +
            "• 详细日志: 记录详细的扫描过程日志");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        panel.add(helpText, gbc);
        
        return panel;
    }
    
    private JPanel createCustomDictPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 字典区域
        JPanel dictPanel = new JPanel(new BorderLayout(5, 5));
        dictPanel.setBorder(BorderFactory.createTitledBorder("全局自定义参数字典"));
        
        JScrollPane scrollPane = new JScrollPane(globalCustomDictArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dictPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addCustomParamButton);
        buttonPanel.add(removeCustomParamButton);
        buttonPanel.add(importDictButton);
        buttonPanel.add(exportDictButton);
        buttonPanel.add(clearDictButton);
        
        dictPanel.add(buttonPanel, BorderLayout.SOUTH);
        panel.add(dictPanel, BorderLayout.CENTER);
        
        // 说明文本
        JTextArea helpText = new JTextArea(3, 40);
        helpText.setText("全局自定义字典说明:\n" +
            "• 每行一个参数名\n" +
            "• 这些参数将用于所有host的爆破\n" +
            "• 优先级: host特定参数 > 全局自定义 > 常见参数");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        panel.add(helpText, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 统计表格
        JScrollPane scrollPane = new JScrollPane(hostStatsTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshStatsButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void setupEventListeners() {
        // 主动探测开关
        enableActiveScanCheckBox.addActionListener(e -> toggleActiveScan());
        
        // 自定义字典按钮
        addCustomParamButton.addActionListener(e -> addCustomParameter());
        removeCustomParamButton.addActionListener(e -> removeCustomParameter());
        importDictButton.addActionListener(e -> importDictionary());
        exportDictButton.addActionListener(e -> exportDictionary());
        clearDictButton.addActionListener(e -> clearDictionary());
        
        // 统计按钮
        refreshStatsButton.addActionListener(e -> refreshStatistics());
    }
    
    private void toggleActiveScan() {
        boolean enabled = enableActiveScanCheckBox.isSelected();
        if (enabled) {
            realtimeScanner.startRealtimeScanning();
            api.logging().raiseInfoEvent("主动探测已启用");
        } else {
            realtimeScanner.stopRealtimeScanning();
            api.logging().raiseInfoEvent("主动探测已禁用");
        }
    }
    
    private void addCustomParameter() {
        String input = JOptionPane.showInputDialog(panel, "请输入参数名:", "添加自定义参数");
        if (input != null && !input.trim().isEmpty()) {
            globalCustomDictArea.append(input.trim() + "\n");
            realtimeScanner.addGlobalCustomParameter(input.trim());
        }
    }
    
    private void removeCustomParameter() {
        String selectedText = globalCustomDictArea.getSelectedText();
        if (selectedText != null) {
            globalCustomDictArea.replaceSelection("");
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要移除的参数", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void importDictionary() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件", "txt"));
        
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                java.nio.file.Files.lines(file.toPath())
                    .forEach(line -> globalCustomDictArea.append(line + "\n"));
                JOptionPane.showMessageDialog(panel, "字典导入成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(panel, "导入失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportDictionary() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件", "txt"));
        
        if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".txt")) {
                    file = new java.io.File(file.getAbsolutePath() + ".txt");
                }
                java.nio.file.Files.write(file.toPath(), globalCustomDictArea.getText().getBytes());
                JOptionPane.showMessageDialog(panel, "字典导出成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(panel, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void clearDictionary() {
        int result = JOptionPane.showConfirmDialog(panel, "确定要清空自定义字典吗？", "确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            globalCustomDictArea.setText("");
            realtimeScanner.clearGlobalCustomDictionary();
        }
    }
    
    private void refreshStatistics() {
        hostStatsModel.setRowCount(0);
        
        var stats = realtimeScanner.getDomainStatistics();
        for (var entry : stats.entrySet()) {
            var stat = entry.getValue();
            hostStatsModel.addRow(new Object[]{
                stat.getMainDomain() + " (" + stat.getHostCount() + " hosts)",
                stat.getEndpointCount(),
                stat.getParameterCount(),
                0,  // bruteforceCount 不再使用，设为0
                new java.util.Date(stat.getLastUpdateTime()).toString()
            });
        }
    }
    
    private void loadCurrentConfig() {
        // 加载当前配置
        refreshStatistics();
    }
    
    private void saveConfig() {
        // 保存配置
        JOptionPane.showMessageDialog(panel, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void resetConfig() {
        int result = JOptionPane.showConfirmDialog(panel, "确定要重置所有配置吗？", "确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            // 重置配置
            bruteforceIntervalSpinner.setValue(30);
            minParameterCountSpinner.setValue(3);
            maxConcurrentHostsSpinner.setValue(5);
            enableActiveScanCheckBox.setSelected(false);
            autoStartCheckBox.setSelected(false);
            verboseLoggingCheckBox.setSelected(false);
            globalCustomDictArea.setText("");
        }
    }
    
    public Component getComponent() {
        return panel;
    }
}

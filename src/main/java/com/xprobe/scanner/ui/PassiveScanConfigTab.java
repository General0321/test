package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.config.XProbeConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * 被动扫描配置选项卡 - 管理被动扫描规则
 */
public class PassiveScanConfigTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    
    // 配置表格
    private JTable configurationTable;
    private DefaultTableModel tableModel;
    private JTextArea detailTextArea;
    
    // 按钮
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton saveButton;
    private JButton refreshButton;
    private JButton exportButton;  // ✅ 导出规则按钮
    private JButton importButton;  // ✅ 导入规则按钮
    
    // 总开关和全局设置
    private JToggleButton passiveScanToggleButton;  // ✅ 改为按钮形式
    private JComboBox<Configuration.InjectionMode> globalInjectionModeCombo;  // ✅ 全局注入模式
    private JComboBox<XProbeConfig.ScanResultLogMode> logModeCombo;  // ✅ 扫描结果记录模式
    
    public PassiveScanConfigTab(MontoyaApi api, ConfigurationManager configManager, XProbeConfigManager xprobeConfigManager) {
        this.api = api;
        this.configManager = configManager;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadConfigurations();
        loadSavedSettings();  // ✅ 加载保存的设置（包括开关状态和全局注入模式）
    }
    
    private void initializeComponents() {
        // 配置表格 - 更新列名以反映新架构
        tableModel = new DefaultTableModel(new Object[]{"规则名称", "启用状态", "注入点数", "Payload数", "匹配规则数"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        configurationTable = new JTable(tableModel);
        configurationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configurationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showConfigurationDetails();
            }
        });
        
        // 详情文本区域
        detailTextArea = new JTextArea(15, 50);
        detailTextArea.setEditable(false);
        detailTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        // 按钮
        addButton = new JButton("添加规则");
        editButton = new JButton("编辑规则");
        deleteButton = new JButton("删除规则");
        saveButton = new JButton("保存配置");
        refreshButton = new JButton("刷新");
        exportButton = new JButton("📤 导出规则");  // ✅ 导出规则按钮
        importButton = new JButton("📥 导入规则");  // ✅ 导入规则按钮
        
        // ✅ 被动扫描总开关（按钮形式）
        passiveScanToggleButton = new JToggleButton("🟢 被动扫描已启用", true);
        passiveScanToggleButton.setFont(passiveScanToggleButton.getFont().deriveFont(Font.BOLD, 14f));
        passiveScanToggleButton.setForeground(new Color(0, 120, 0));
        passiveScanToggleButton.setBackground(new Color(230, 255, 230));
        passiveScanToggleButton.setFocusPainted(false);
        passiveScanToggleButton.setBorderPainted(true);
        
        // ✅ 全局注入模式选择
        globalInjectionModeCombo = new JComboBox<>(Configuration.InjectionMode.values());
        globalInjectionModeCombo.setSelectedItem(Configuration.InjectionMode.BATCH);
        
        // ✅ 扫描结果记录模式选择
        logModeCombo = new JComboBox<>(XProbeConfig.ScanResultLogMode.values());
        logModeCombo.setSelectedItem(XProbeConfig.ScanResultLogMode.MATCHED_ONLY);
        logModeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                         boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof XProbeConfig.ScanResultLogMode) {
                    setText(((XProbeConfig.ScanResultLogMode) value).getDisplayName());
                }
                return this;
            }
        });
        globalInjectionModeCombo.setFont(globalInjectionModeCombo.getFont().deriveFont(13f));
        globalInjectionModeCombo.setToolTipText("<html>" +
            "<b>全局注入模式（可在单个规则中覆盖）</b><br>" +
            "• 批量模式：所有匹配参数同时注入，速度快<br>" +
            "• 逐个模式：每次只注入一个参数，精确定位" +
            "</html>");
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // ✅ 优化后的顶部控制面板
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        topPanel.setBackground(new Color(248, 252, 255));  // 更柔和的浅蓝色背景
        
        // 1. 被动扫描总开关（左侧，更突出）
        topPanel.add(passiveScanToggleButton);
        topPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        
        // 分隔线
        JSeparator separator1 = new JSeparator(SwingConstants.VERTICAL);
        separator1.setMaximumSize(new Dimension(1, 35));
        separator1.setForeground(new Color(200, 220, 240));
        topPanel.add(separator1);
        topPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        
        // 2. 全局注入模式组
        JPanel injectionModePanel = new JPanel();
        injectionModePanel.setLayout(new BoxLayout(injectionModePanel, BoxLayout.Y_AXIS));
        injectionModePanel.setOpaque(false);
        
        JLabel modeLabel = new JLabel("全局注入模式");
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 12f));
        modeLabel.setForeground(new Color(50, 50, 50));
        
        JPanel modeValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        modeValuePanel.setOpaque(false);
        modeValuePanel.add(globalInjectionModeCombo);
        JLabel modeHint = new JLabel("各规则可单独设置");
        modeHint.setForeground(new Color(120, 120, 120));
        modeHint.setFont(modeHint.getFont().deriveFont(Font.PLAIN, 10f));
        modeValuePanel.add(modeHint);
        
        injectionModePanel.add(modeLabel);
        injectionModePanel.add(Box.createRigidArea(new Dimension(0, 3)));
        injectionModePanel.add(modeValuePanel);
        
        topPanel.add(injectionModePanel);
        topPanel.add(Box.createRigidArea(new Dimension(25, 0)));
        
        // 分隔线
        JSeparator separator2 = new JSeparator(SwingConstants.VERTICAL);
        separator2.setMaximumSize(new Dimension(1, 35));
        separator2.setForeground(new Color(200, 220, 240));
        topPanel.add(separator2);
        topPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        
        // 3. 结果记录模式组
        JPanel logModePanel = new JPanel();
        logModePanel.setLayout(new BoxLayout(logModePanel, BoxLayout.Y_AXIS));
        logModePanel.setOpaque(false);
        
        JLabel logModeLabel = new JLabel("扫描结果记录");
        logModeLabel.setFont(logModeLabel.getFont().deriveFont(Font.BOLD, 12f));
        logModeLabel.setForeground(new Color(50, 50, 50));
        
        JPanel logValuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        logValuePanel.setOpaque(false);
        
        logModeCombo.setFont(logModeCombo.getFont().deriveFont(12f));
        logModeCombo.setToolTipText("<html>" +
            "<b>扫描结果记录模式</b><br>" +
            "• <b>记录所有流量</b>：记录所有被动扫描发送的请求（便于调试，内存占用高）<br>" +
            "• <b>仅记录命中</b>：只记录命中规则的请求（推荐，节省内存和性能）" +
            "</html>");
        logValuePanel.add(logModeCombo);
        
        JLabel logHint = new JLabel("仅命中可节省资源");
        logHint.setForeground(new Color(180, 100, 0));
        logHint.setFont(logHint.getFont().deriveFont(Font.PLAIN, 10f));
        logValuePanel.add(logHint);
        
        logModePanel.add(logModeLabel);
        logModePanel.add(Box.createRigidArea(new Dimension(0, 3)));
        logModePanel.add(logValuePanel);
        
        topPanel.add(logModePanel);
        
        // 右侧填充
        topPanel.add(Box.createHorizontalGlue());
        
        panel.add(topPanel, BorderLayout.NORTH);
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 配置表格面板
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("被动扫描规则列表"));
        
        JScrollPane tableScrollPane = new JScrollPane(configurationTable);
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // ✅ BLIT滚动优化（像Burp一样丝滑）
        tableScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tableScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 表格按钮面板
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableButtonPanel.add(addButton);
        tableButtonPanel.add(editButton);
        tableButtonPanel.add(deleteButton);
        tableButtonPanel.add(refreshButton);
        
        // ✅ 添加分隔符
        tableButtonPanel.add(Box.createRigidArea(new Dimension(20, 0)));
        
        // ✅ 导入导出按钮
        tableButtonPanel.add(importButton);
        tableButtonPanel.add(exportButton);
        
        tablePanel.add(tableButtonPanel, BorderLayout.SOUTH);
        
        // 详情面板
        JPanel detailPanel = new JPanel(new BorderLayout(5, 5));
        detailPanel.setBorder(BorderFactory.createTitledBorder("规则详情"));
        
        JScrollPane detailScrollPane = new JScrollPane(detailTextArea);
        detailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // ✅ BLIT滚动优化（像Burp一样丝滑）
        detailScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        detailScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        detailPanel.add(detailScrollPane, BorderLayout.CENTER);
        
        // 详情按钮面板
        JPanel detailButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        detailButtonPanel.add(saveButton);
        detailPanel.add(detailButtonPanel, BorderLayout.SOUTH);
        
        // 分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(tablePanel);
        splitPane.setBottomComponent(detailPanel);
        splitPane.setDividerLocation(300);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // 说明文本
        JTextArea helpText = new JTextArea(4, 80);
        helpText.setText("【灵活规则系统】被动扫描配置说明:\n" +
            "• 基于全新的灵活规则系统，支持自定义请求条件、注入点、Payload和响应匹配\n" +
            "• 支持多种注入点类型：参数值、URL路径、请求头、请求体、Cookie等\n" +
            "• 支持动态Payload变量：{{COLLABORATOR}}、{{RANDOM_STRING}}、{{BASE64:xxx}}等\n" +
            "• 支持Burp Collaborator集成进行外带检测，支持灵活的去重策略");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mainPanel.add(helpText, BorderLayout.SOUTH);
        
        panel.add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupEventListeners() {
        addButton.addActionListener(e -> addConfiguration());
        editButton.addActionListener(e -> editConfiguration());
        deleteButton.addActionListener(e -> deleteConfiguration());
        saveButton.addActionListener(e -> saveConfiguration());
        refreshButton.addActionListener(e -> loadConfigurations());
        exportButton.addActionListener(e -> exportRules());  // ✅ 导出规则
        importButton.addActionListener(e -> importRules());  // ✅ 导入规则
        
        // ✅ 被动扫描开关状态变化时自动保存并更新UI
        passiveScanToggleButton.addActionListener(e -> {
            updateToggleButtonAppearance();
            savePassiveScanEnabled();
        });
        
        // ✅ 全局注入模式变化时自动保存
        globalInjectionModeCombo.addActionListener(e -> saveGlobalInjectionMode());
        
        // ✅ 记录模式变化时自动保存
        logModeCombo.addActionListener(e -> saveLogMode());
    }
    
    private void loadConfigurations() {
        tableModel.setRowCount(0);
        
        List<Configuration> configurations = configManager.getAllConfigurations();
        for (int i = 0; i < configurations.size(); i++) {
            Configuration config = configurations.get(i);
            String enabledStatus = config.isEnabled() ? "✓ 启用" : "✗ 禁用";
            
            // 从配对架构中统计注入点、payload和响应匹配规则数量
            int injectionPointCount = 0;
            int payloadCount = 0;
            int matchRuleCount = 0;
            
            if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                for (var pair : config.getPairs()) {
                    if (pair.getRequestConfig() != null && pair.getRequestConfig().getElements() != null) {
                        for (var element : pair.getRequestConfig().getElements()) {
                            if (element.isUseForInjection()) {
                                injectionPointCount++;
                                if (element.getPayloads() != null) {
                                    payloadCount += element.getPayloads().size();
                                }
                            }
                        }
                    }
                    if (pair.getResponseConfig() != null && pair.getResponseConfig().getElements() != null) {
                        matchRuleCount += pair.getResponseConfig().getElements().size();
                    }
                }
            }
            
            // 使用customLabel作为规则名称，如果为空则使用默认名称
            String ruleName = config.getCustomLabel();
            if (ruleName == null || ruleName.trim().isEmpty()) {
                ruleName = "规则 " + (i + 1);
            }
            
            tableModel.addRow(new Object[]{
                ruleName,
                enabledStatus,
                injectionPointCount,
                payloadCount,
                matchRuleCount
            });
        }
    }
    
    private void showConfigurationDetails() {
        int selectedRow = configurationTable.getSelectedRow();
        if (selectedRow >= 0) {
            List<Configuration> configurations = configManager.getAllConfigurations();
            if (selectedRow < configurations.size()) {
                Configuration config = configurations.get(selectedRow);
                
                StringBuilder details = new StringBuilder();
                String ruleName = config.getCustomLabel();
                if (ruleName == null || ruleName.trim().isEmpty()) {
                    ruleName = "规则 " + (selectedRow + 1);
                }
                
                details.append("═══════════════════════════════════════\n");
                details.append("规则名称: ").append(ruleName).append("\n");
                details.append("规则ID: ").append(config.getRuleId() != null && config.getRuleId().length() >= 8 
                    ? config.getRuleId().substring(0, 8) + "..." 
                    : (config.getRuleId() != null ? config.getRuleId() : "N/A")).append("\n");
                details.append("启用状态: ").append(config.isEnabled() ? "✓ 启用" : "✗ 禁用").append("\n");
                if (config.getDescription() != null && !config.getDescription().isEmpty()) {
                    details.append("描述: ").append(config.getDescription()).append("\n");
                }
                details.append("═══════════════════════════════════════\n\n");
                
                // ✅ 显示配对架构信息
                if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                    details.append("【请求-响应配对】(共 ").append(config.getPairs().size()).append(" 个)\n\n");
                    
                    int pairIndex = 1;
                    for (var pair : config.getPairs()) {
                        details.append("配对 ").append(pairIndex++).append(": ")
                               .append(pair.getLabel() != null ? pair.getLabel() : "未命名")
                               .append("\n");
                        
                        // 请求配置摘要
                        if (pair.getRequestConfig() != null) {
                            details.append("  ├─ 请求: ").append(pair.getRequestConfig().getDisplaySummary()).append("\n");
                        }
                        
                        // 响应配置摘要
                        if (pair.getResponseConfig() != null) {
                            details.append("  └─ 响应: ").append(pair.getResponseConfig().getDisplaySummary()).append("\n");
                        }
                        
                        details.append("\n");
                    }
                    
                    // 配对表达式
                    if (config.getPairExpression() != null && !config.getPairExpression().isEmpty()) {
                        details.append("配对逻辑: ").append(config.getPairExpression()).append("\n\n");
                    }
                }
                
                // 去重颗粒度
                if (config.getDeduplicationGranularity() != null) {
                    details.append("【去重颗粒度】").append(config.getDeduplicationGranularity()).append("\n");
                }
                
                detailTextArea.setText(details.toString());
            }
        } else {
            detailTextArea.setText("");
        }
    }
    
    private void addConfiguration() {
        // 使用基于配对的规则配置对话框
        Window owner = SwingUtilities.getWindowAncestor(panel);
        PairBasedRuleConfigDialog dialog = new PairBasedRuleConfigDialog(owner, api, configManager, null);
        
        if (dialog.showDialog()) {
            Configuration newConfig = dialog.getConfiguration();
            configManager.addConfiguration(newConfig);
            
            // ✅ 修复：同步保存到XProbeConfig，确保导出时有数据
            try {
                xprobeConfigManager.updateConfig(config -> {
                    config.setScanConfigurations(configManager.getConfigurations());
                });
                api.logging().raiseInfoEvent("新规则已添加并保存: " + newConfig.getCustomLabel());
            } catch (Exception e) {
                api.logging().raiseErrorEvent("保存规则失败: " + e.getMessage());
            }
            
            loadConfigurations();
        }
    }
    
    private void editConfiguration() {
        int selectedRow = configurationTable.getSelectedRow();
        if (selectedRow >= 0) {
            List<Configuration> configurations = configManager.getAllConfigurations();
            if (selectedRow < configurations.size()) {
                Configuration config = configurations.get(selectedRow);
                
                // 使用基于配对的规则配置对话框
                Window owner = SwingUtilities.getWindowAncestor(panel);
                PairBasedRuleConfigDialog dialog = new PairBasedRuleConfigDialog(owner, api, configManager, config);
                
                if (dialog.showDialog()) {
                    // ✅ 修复：同步保存到XProbeConfig
                    try {
                        xprobeConfigManager.updateConfig(cfg -> {
                            cfg.setScanConfigurations(configManager.getConfigurations());
                        });
                        api.logging().raiseInfoEvent("规则已更新并保存: " + config.getCustomLabel());
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("保存规则失败: " + e.getMessage());
                    }
                    
                    loadConfigurations();
                }
            }
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要编辑的规则", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void deleteConfiguration() {
        int selectedRow = configurationTable.getSelectedRow();
        if (selectedRow >= 0) {
            int result = JOptionPane.showConfirmDialog(panel, 
                "确定要删除规则 " + (selectedRow + 1) + " 吗？", 
                "确认删除", 
                JOptionPane.YES_NO_OPTION);
            
            if (result == JOptionPane.YES_OPTION) {
                configManager.removeConfiguration(selectedRow);
                
                // ✅ 修复：同步保存到XProbeConfig
                try {
                    xprobeConfigManager.updateConfig(config -> {
                        config.setScanConfigurations(configManager.getConfigurations());
                    });
                    api.logging().raiseInfoEvent("规则已删除并保存: " + (selectedRow + 1));
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("保存规则失败: " + e.getMessage());
                }
                
                loadConfigurations();
                detailTextArea.setText("");
            }
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要删除的规则", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void saveConfiguration() {
        JOptionPane.showMessageDialog(panel, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * ✅ 更新开关按钮外观
     */
    private void updateToggleButtonAppearance() {
        if (passiveScanToggleButton.isSelected()) {
            // 启用状态：绿色
            passiveScanToggleButton.setText("🟢 被动扫描已启用");
            passiveScanToggleButton.setForeground(new Color(0, 120, 0));
            passiveScanToggleButton.setBackground(new Color(230, 255, 230));
        } else {
            // 禁用状态：红色
            passiveScanToggleButton.setText("🔴 被动扫描已禁用");
            passiveScanToggleButton.setForeground(new Color(180, 0, 0));
            passiveScanToggleButton.setBackground(new Color(255, 230, 230));
        }
    }
    
    /**
     * ✅ 保存被动扫描开关状态（防御性复制）
     */
    private void savePassiveScanEnabled() {
        try {
            // ✅ 获取配置副本（防止并发修改）
            XProbeConfig config = xprobeConfigManager.getConfig();
            config.setEnablePassiveScan(passiveScanToggleButton.isSelected());
            xprobeConfigManager.saveConfig(config);
            
            if (passiveScanToggleButton.isSelected()) {
                api.logging().raiseInfoEvent("✅ 被动扫描已启用");
            } else {
                api.logging().raiseInfoEvent("❌ 被动扫描已禁用");
            }
        } catch (Exception e) {
            // ✅ 保存失败：回滚UI状态
            SwingUtilities.invokeLater(() -> {
                passiveScanToggleButton.setSelected(!passiveScanToggleButton.isSelected());
                updateToggleButtonAppearance();
            });
            
            // ✅ 显示错误对话框
            JOptionPane.showMessageDialog(
                panel,
                "配置保存失败：" + e.getMessage() + "\n请检查磁盘空间和文件权限。",
                "保存失败",
                JOptionPane.ERROR_MESSAGE
            );
            
            api.logging().raiseErrorEvent("❌ 保存被动扫描开关失败: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 保存全局注入模式（防御性复制）
     */
    private void saveGlobalInjectionMode() {
        Configuration.InjectionMode selectedMode = (Configuration.InjectionMode) globalInjectionModeCombo.getSelectedItem();
        try {
            // ✅ 获取配置副本（防止并发修改）
            XProbeConfig config = xprobeConfigManager.getConfig();
            config.setGlobalInjectionMode(selectedMode);
            xprobeConfigManager.saveConfig(config);
            
            api.logging().raiseInfoEvent("✅ 全局注入模式已设置为: " + selectedMode.getDisplayName());
        } catch (Exception e) {
            // ✅ 显示错误对话框
            JOptionPane.showMessageDialog(
                panel,
                "配置保存失败：" + e.getMessage() + "\n请检查磁盘空间和文件权限。",
                "保存失败",
                JOptionPane.ERROR_MESSAGE
            );
            
            api.logging().raiseErrorEvent("❌ 保存全局注入模式失败: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 保存扫描结果记录模式（防御性复制）
     */
    private void saveLogMode() {
        XProbeConfig.ScanResultLogMode selectedMode = (XProbeConfig.ScanResultLogMode) logModeCombo.getSelectedItem();
        try {
            // ✅ 获取配置副本（防止并发修改）
            XProbeConfig config = xprobeConfigManager.getConfig();
            config.setScanResultLogMode(selectedMode);
            xprobeConfigManager.saveConfig(config);
            
            String performanceNote = selectedMode == XProbeConfig.ScanResultLogMode.ALL_REQUESTS 
                ? " ⚠️ 注意：记录所有流量会增加内存和性能开销" 
                : "";
            api.logging().raiseInfoEvent("✅ 扫描结果记录模式已更新: " + selectedMode.getDisplayName() + performanceNote);
        } catch (Exception e) {
            // ✅ 显示错误对话框
            JOptionPane.showMessageDialog(
                panel,
                "配置保存失败：" + e.getMessage() + "\n请检查磁盘空间和文件权限。",
                "保存失败",
                JOptionPane.ERROR_MESSAGE
            );
            
            api.logging().raiseErrorEvent("❌ 保存记录模式失败: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 加载保存的设置（使用配置管理器，零开销）
     */
    public void loadSavedSettings() {
        try {
            XProbeConfig config = xprobeConfigManager.getConfig();
            
            // 加载被动扫描开关状态
            passiveScanToggleButton.setSelected(config.isEnablePassiveScan());
            updateToggleButtonAppearance();
            
            // 加载全局注入模式
            if (config.getGlobalInjectionMode() != null) {
                globalInjectionModeCombo.setSelectedItem(config.getGlobalInjectionMode());
            }
            
            // ✅ 加载扫描结果记录模式
            if (config.getScanResultLogMode() != null) {
                logModeCombo.setSelectedItem(config.getScanResultLogMode());
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("加载设置失败: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 导出规则到JSON文件
     */
    private void exportRules() {
        try {
            // 创建文件选择器
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("导出扫描规则");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));
            
            // 设置默认文件名
            String defaultFileName = "xprobe_rules_" + 
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".json";
            fileChooser.setSelectedFile(new File(defaultFileName));
            
            int result = fileChooser.showSaveDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                
                // 确保文件扩展名为.json
                if (!file.getName().toLowerCase().endsWith(".json")) {
                    file = new File(file.getAbsolutePath() + ".json");
                }
                
                // 导出规则
                xprobeConfigManager.exportRules(file);
                
                // 显示成功消息
                JOptionPane.showMessageDialog(
                    panel,
                    "规则导出成功！\n文件路径: " + file.getAbsolutePath(),
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                api.logging().raiseInfoEvent("✅ 规则已导出到: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            // 显示错误消息
            JOptionPane.showMessageDialog(
                panel,
                "规则导出失败：" + e.getMessage(),
                "导出失败",
                JOptionPane.ERROR_MESSAGE
            );
            
            api.logging().raiseErrorEvent("❌ 规则导出失败: " + e.getMessage());
        }
    }
    
    /**
     * ✅ 从JSON文件导入规则
     */
    private void importRules() {
        try {
            // 创建文件选择器
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("导入扫描规则");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));
            
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                
                // 询问导入模式
                String[] options = {"追加到现有规则", "替换现有规则", "取消"};
                int choice = JOptionPane.showOptionDialog(
                    panel,
                    "请选择导入模式：\n\n" +
                        "• 追加模式：保留现有规则，新增导入的规则\n" +
                        "• 替换模式：清空现有规则，仅保留导入的规则",
                    "选择导入模式",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
                );
                
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    return;
                }
                
                boolean append = (choice == 0); // 0=追加, 1=替换
                
                // 导入规则
                xprobeConfigManager.importRules(file, append);
                
                // ✅ 修复：同步规则到ConfigurationManager
                XProbeConfig config = xprobeConfigManager.getConfig();
                List<Configuration> importedConfigs = config.getScanConfigurations();
                
                // 清空ConfigurationManager并重新加载
                while (configManager.getAllConfigurations().size() > 0) {
                    configManager.removeConfiguration(0);
                }
                for (Configuration cfg : importedConfigs) {
                    configManager.addConfiguration(cfg);
                }
                
                // 刷新规则列表
                loadConfigurations();
                
                // 显示成功消息
                String mode = append ? "追加" : "替换";
                JOptionPane.showMessageDialog(
                    panel,
                    "规则导入成功！（" + mode + "模式）\n文件: " + file.getName(),
                    "导入成功",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                api.logging().raiseInfoEvent("✅ 规则已从文件导入: " + file.getAbsolutePath() + " (模式: " + mode + ")");
            }
        } catch (Exception e) {
            // 显示错误消息
            JOptionPane.showMessageDialog(
                panel,
                "规则导入失败：" + e.getMessage() + "\n\n可能原因：\n" +
                    "• 文件格式不正确\n" +
                    "• 文件已损坏\n" +
                    "• 规则版本不兼容",
                "导入失败",
                JOptionPane.ERROR_MESSAGE
            );
            
            api.logging().raiseErrorEvent("❌ 规则导入失败: " + e.getMessage());
        }
    }
    
    public Component getComponent() {
        return panel;
    }
}

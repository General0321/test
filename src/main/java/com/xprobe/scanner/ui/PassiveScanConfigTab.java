package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * 被动扫描配置选项卡 - 管理被动扫描规则
 */
public class PassiveScanConfigTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    
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
    
    public PassiveScanConfigTab(MontoyaApi api, ConfigurationManager configManager) {
        this.api = api;
        this.configManager = configManager;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadConfigurations();
    }
    
    private void initializeComponents() {
        // 配置表格
        tableModel = new DefaultTableModel(new Object[]{"规则名称", "规则类型", "启用状态", "参数数量"}, 0) {
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
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 配置表格面板
        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.setBorder(BorderFactory.createTitledBorder("被动扫描规则列表"));
        
        JScrollPane tableScrollPane = new JScrollPane(configurationTable);
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 表格按钮面板
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableButtonPanel.add(addButton);
        tableButtonPanel.add(editButton);
        tableButtonPanel.add(deleteButton);
        tableButtonPanel.add(refreshButton);
        tablePanel.add(tableButtonPanel, BorderLayout.SOUTH);
        
        // 详情面板
        JPanel detailPanel = new JPanel(new BorderLayout(5, 5));
        detailPanel.setBorder(BorderFactory.createTitledBorder("规则详情"));
        
        JScrollPane detailScrollPane = new JScrollPane(detailTextArea);
        detailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
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
        JTextArea helpText = new JTextArea(3, 80);
        helpText.setText("被动扫描配置说明:\n" +
            "• 这里配置的是被动扫描的检测规则，决定对哪些请求进行安全检测\n" +
            "• 规则类型包括: LFI、SQL注入、SSRF等安全漏洞检测\n" +
            "• 只有启用的规则才会对匹配的请求进行检测");
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
    }
    
    private void loadConfigurations() {
        tableModel.setRowCount(0);
        
        List<Configuration> configurations = configManager.getAllConfigurations();
        for (int i = 0; i < configurations.size(); i++) {
            Configuration config = configurations.get(i);
            String ruleType = getRuleTypeDisplayName(config.getParameterNameType());
            String enabledStatus = config.isEnabled() ? "启用" : "禁用";
            int parameterCount = config.getParameterValues().size();
            
            // 使用customLabel作为规则名称，如果为空则使用默认名称
            String ruleName = config.getCustomLabel();
            if (ruleName == null || ruleName.trim().isEmpty()) {
                ruleName = "规则 " + (i + 1);
            }
            
            tableModel.addRow(new Object[]{
                ruleName,
                ruleType,
                enabledStatus,
                parameterCount
            });
        }
    }
    
    private String getRuleTypeDisplayName(String ruleType) {
        switch (ruleType.toLowerCase()) {
            case "lfi": return "LFI (本地文件包含)";
            case "sql": return "SQL注入";
            case "ssrf": return "SSRF (服务器端请求伪造)";
            default: return ruleType;
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
                details.append("规则名称: ").append(ruleName).append("\n");
                details.append("规则类型: ").append(config.getParameterNameType()).append("\n");
                details.append("启用状态: ").append(config.isEnabled() ? "启用" : "禁用").append("\n");
                details.append("参数数量: ").append(config.getParameterValues().size()).append("\n\n");
                
                details.append("检测参数:\n");
                for (String param : config.getParameterValues()) {
                    details.append("  • ").append(param).append("\n");
                }
                
                detailTextArea.setText(details.toString());
            }
        } else {
            detailTextArea.setText("");
        }
    }
    
    private void addConfiguration() {
        // 创建新配置对话框
        ConfigurationDialog dialog = new ConfigurationDialog(panel, "添加规则", null, configManager);
        dialog.setVisible(true);
        
        if (dialog.isConfigurationSaved()) {
            loadConfigurations();
            api.logging().raiseInfoEvent("新规则已添加");
        }
    }
    
    private void editConfiguration() {
        int selectedRow = configurationTable.getSelectedRow();
        if (selectedRow >= 0) {
            List<Configuration> configurations = configManager.getAllConfigurations();
            if (selectedRow < configurations.size()) {
                Configuration config = configurations.get(selectedRow);
                
                ConfigurationDialog dialog = new ConfigurationDialog(panel, "编辑规则", config, configManager);
                dialog.setVisible(true);
                
                if (dialog.isConfigurationSaved()) {
                    loadConfigurations();
                    api.logging().raiseInfoEvent("规则已更新");
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
                loadConfigurations();
                detailTextArea.setText("");
                api.logging().raiseInfoEvent("规则已删除: " + (selectedRow + 1));
            }
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要删除的规则", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void saveConfiguration() {
        JOptionPane.showMessageDialog(panel, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public Component getComponent() {
        return panel;
    }
}

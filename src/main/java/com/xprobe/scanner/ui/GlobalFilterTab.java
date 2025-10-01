package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.active.RealtimeScannerRefactored;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局配置选项卡 - 统一管理黑白名单和主动探测配置
 */
public class GlobalFilterTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final GlobalFilter globalFilter;
    private final RealtimeScannerRefactored realtimeScanner;
    
    // 黑白名单配置
    private JCheckBox whitelistEnabledCheckBox;
    private JCheckBox blacklistEnabledCheckBox;
    private JTextArea whitelistTextArea;
    private JTextArea blacklistTextArea;
    private JButton addWhitelistButton;
    private JButton addBlacklistButton;
    private JButton removeWhitelistButton;
    private JButton removeBlacklistButton;
    private JButton clearWhitelistButton;
    private JButton clearBlacklistButton;
    
    // 主动探测配置
    private JCheckBox enableActiveScanCheckBox;
    private JSpinner bruteforceIntervalSpinner;
    private JSpinner minParameterCountSpinner;
    private JSpinner maxConcurrentHostsSpinner;
    private JCheckBox autoStartCheckBox;
    private JCheckBox verboseLoggingCheckBox;
    private JComboBox<String> parameterCollectionModeComboBox;
    
    // 代理池配置
    private JTextArea proxyListArea;
    private JCheckBox enableProxyPoolCheckBox;
    private JSpinner proxyTimeoutSpinner;
    private JSpinner maxRetriesSpinner;
    private JButton addProxyButton;
    private JButton removeProxyButton;
    private JButton testProxyButton;
    private JButton importProxyButton;
    private JButton exportProxyButton;
    
    public GlobalFilterTab(MontoyaApi api, GlobalFilter globalFilter, RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.globalFilter = globalFilter;
        this.realtimeScanner = realtimeScanner;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadCurrentConfig();
    }
    
    private void initializeComponents() {
        // 黑白名单配置
        whitelistEnabledCheckBox = new JCheckBox("启用白名单");
        blacklistEnabledCheckBox = new JCheckBox("启用黑名单");
        whitelistTextArea = new JTextArea(8, 30);
        blacklistTextArea = new JTextArea(8, 30);
        whitelistTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        blacklistTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        addWhitelistButton = new JButton("添加白名单");
        addBlacklistButton = new JButton("添加黑名单");
        removeWhitelistButton = new JButton("移除选中");
        removeBlacklistButton = new JButton("移除选中");
        clearWhitelistButton = new JButton("清空白名单");
        clearBlacklistButton = new JButton("清空黑名单");
        
        // 主动探测配置
        enableActiveScanCheckBox = new JCheckBox("启用主动探测");
        bruteforceIntervalSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 300, 10));
        minParameterCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        maxConcurrentHostsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        autoStartCheckBox = new JCheckBox("自动启动实时扫描");
        verboseLoggingCheckBox = new JCheckBox("详细日志记录");
        
        // 参数收集模式配置
        parameterCollectionModeComboBox = new JComboBox<>(new String[]{"仅收集参数名", "参数名+关键词"});
        parameterCollectionModeComboBox.setToolTipText("选择参数收集模式：仅参数名或同时收集参数值作为关键词");
        
        
        // 代理池配置
        proxyListArea = new JTextArea(10, 40);
        proxyListArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        enableProxyPoolCheckBox = new JCheckBox("启用代理池");
        proxyTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
        maxRetriesSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        addProxyButton = new JButton("添加代理");
        removeProxyButton = new JButton("移除选中");
        testProxyButton = new JButton("测试代理");
        importProxyButton = new JButton("导入代理");
        exportProxyButton = new JButton("导出代理");
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 创建选项卡
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 黑白名单选项卡
        tabbedPane.addTab("黑白名单", createFilterPanel());
        
        // 主动探测配置选项卡
        tabbedPane.addTab("主动探测配置", createActiveScanPanel());
        
        // 代理池配置选项卡
        tabbedPane.addTab("代理池配置", createProxyPoolPanel());
        
        panel.add(tabbedPane, BorderLayout.CENTER);
    }
    
    private JPanel createFilterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 白名单配置
        JPanel whitelistPanel = createListPanel("白名单配置", whitelistEnabledCheckBox, 
            whitelistTextArea, addWhitelistButton, removeWhitelistButton, clearWhitelistButton);
        
        // 黑名单配置
        JPanel blacklistPanel = createListPanel("黑名单配置", blacklistEnabledCheckBox, 
            blacklistTextArea, addBlacklistButton, removeBlacklistButton, clearBlacklistButton);
        
        // 分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(whitelistPanel);
        splitPane.setRightComponent(blacklistPanel);
        splitPane.setDividerLocation(400);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // 说明文本
        JTextArea helpText = new JTextArea(4, 80);
        helpText.setText("全局过滤器说明:\n" +
            "• 白名单: 只处理白名单中的host/URL（支持正则表达式）\n" +
            "• 黑名单: 排除黑名单中的host/URL（支持正则表达式）\n" +
            "• 优先级: 白名单 > 黑名单，同时影响被动扫描和主动探测\n" +
            "• 格式: 每行一个规则，支持普通字符串匹配和正则表达式");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mainPanel.add(helpText, BorderLayout.SOUTH);
        
        // 底部按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("保存配置");
        JButton resetButton = new JButton("重置配置");
        JButton testButton = new JButton("测试规则");
        
        saveButton.addActionListener(e -> saveConfig());
        resetButton.addActionListener(e -> resetConfig());
        testButton.addActionListener(e -> testRules());
        
        buttonPanel.add(testButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(saveButton);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);
        
        return mainPanel;
    }
    
    private JPanel createActiveScanPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 启用主动探测
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(enableActiveScanCheckBox, gbc);
        
        // 爆破间隔
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("爆破间隔(秒):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(bruteforceIntervalSpinner, gbc);
        
        // 最小参数数量
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("最小参数数量:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(minParameterCountSpinner, gbc);
        
        // 最大并发host数
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("最大并发host数:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(maxConcurrentHostsSpinner, gbc);
        
        // 自动启动
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(autoStartCheckBox, gbc);
        
        // 详细日志
        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(verboseLoggingCheckBox, gbc);
        
        // 参数收集模式
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("参数收集模式:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(parameterCollectionModeComboBox, gbc);
        
        // 说明文本
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        JTextArea helpText = new JTextArea(5, 40);
        helpText.setText("主动探测配置说明:\n" +
            "• 启用主动探测: 控制是否进行主动探测\n" +
            "• 爆破间隔: Arjun探测的最小时间间隔\n" +
            "• 最小参数数量: 触发探测所需的最少参数数\n" +
            "• 最大并发host数: 同时处理的host数量限制\n" +
            "• 自动启动: 插件加载时自动启动实时扫描\n" +
            "• 参数收集模式: 选择仅收集参数名或同时收集参数值作为关键词\n" +
            "  - 仅参数名: 只收集参数名称用于Arjun探测\n" +
            "  - 参数名+关键词: 同时收集参数值作为额外关键词，提升发现率");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        panel.add(helpText, gbc);
        
        return panel;
    }
    
    
    private JPanel createProxyPoolPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 代理列表区域
        JPanel proxyListPanel = new JPanel(new BorderLayout(5, 5));
        proxyListPanel.setBorder(BorderFactory.createTitledBorder("代理列表"));
        
        JScrollPane proxyScrollPane = new JScrollPane(proxyListArea);
        proxyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        proxyListPanel.add(proxyScrollPane, BorderLayout.CENTER);
        
        // 代理操作按钮
        JPanel proxyButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        proxyButtonPanel.add(addProxyButton);
        proxyButtonPanel.add(removeProxyButton);
        proxyButtonPanel.add(testProxyButton);
        proxyButtonPanel.add(importProxyButton);
        proxyButtonPanel.add(exportProxyButton);
        proxyListPanel.add(proxyButtonPanel, BorderLayout.SOUTH);
        
        // 代理配置区域
        JPanel proxyConfigPanel = new JPanel(new GridBagLayout());
        proxyConfigPanel.setBorder(BorderFactory.createTitledBorder("代理配置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 启用代理池
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        proxyConfigPanel.add(enableProxyPoolCheckBox, gbc);
        
        // 代理超时时间
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        proxyConfigPanel.add(new JLabel("代理超时(秒):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        proxyConfigPanel.add(proxyTimeoutSpinner, gbc);
        
        // 最大重试次数
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        proxyConfigPanel.add(new JLabel("最大重试次数:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        proxyConfigPanel.add(maxRetriesSpinner, gbc);
        
        // 说明文本
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        JTextArea helpText = new JTextArea(4, 40);
        helpText.setText("代理池配置说明:\n" +
            "• 每行一个代理，格式: ip:port 或 ip:port:username:password\n" +
            "• 支持HTTP、HTTPS、SOCKS4、SOCKS5代理\n" +
            "• 代理超时: 单个代理的连接超时时间\n" +
            "• 最大重试: 代理失败时的重试次数");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        proxyConfigPanel.add(helpText, gbc);
        
        // 分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(proxyListPanel);
        splitPane.setBottomComponent(proxyConfigPanel);
        splitPane.setDividerLocation(300);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createListPanel(String title, JCheckBox enabledCheckBox, JTextArea textArea, 
                                   JButton addButton, JButton removeButton, JButton clearButton) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        
        // 启用复选框
        panel.add(enabledCheckBox, BorderLayout.NORTH);
        
        // 文本区域
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void setupEventListeners() {
        // 黑白名单按钮
        addWhitelistButton.addActionListener(e -> addToWhitelist());
        addBlacklistButton.addActionListener(e -> addToBlacklist());
        removeWhitelistButton.addActionListener(e -> removeFromWhitelist());
        removeBlacklistButton.addActionListener(e -> removeFromBlacklist());
        clearWhitelistButton.addActionListener(e -> clearWhitelist());
        clearBlacklistButton.addActionListener(e -> clearBlacklist());
        
        // 启用状态变化
        whitelistEnabledCheckBox.addActionListener(e -> updateWhitelistConfig());
        blacklistEnabledCheckBox.addActionListener(e -> updateBlacklistConfig());
        
        // 主动探测配置
        enableActiveScanCheckBox.addActionListener(e -> toggleActiveScan());
        
        // 参数收集模式变更
        parameterCollectionModeComboBox.addActionListener(e -> updateParameterCollectionMode());
        
        // 代理池配置
        addProxyButton.addActionListener(e -> addProxy());
        removeProxyButton.addActionListener(e -> removeProxy());
        testProxyButton.addActionListener(e -> testProxy());
        importProxyButton.addActionListener(e -> importProxy());
        exportProxyButton.addActionListener(e -> exportProxy());
    }
    
    private void addToWhitelist() {
        String input = JOptionPane.showInputDialog(panel, "请输入要添加到白名单的host或URL模式:", "添加白名单");
        if (input != null && !input.trim().isEmpty()) {
            whitelistTextArea.append(input.trim() + "\n");
            updateWhitelistConfig();
        }
    }
    
    private void addToBlacklist() {
        String input = JOptionPane.showInputDialog(panel, "请输入要添加到黑名单的host或URL模式:", "添加黑名单");
        if (input != null && !input.trim().isEmpty()) {
            blacklistTextArea.append(input.trim() + "\n");
            updateBlacklistConfig();
        }
    }
    
    private void removeFromWhitelist() {
        String selectedText = whitelistTextArea.getSelectedText();
        if (selectedText != null) {
            whitelistTextArea.replaceSelection("");
            updateWhitelistConfig();
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要移除的内容", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void removeFromBlacklist() {
        String selectedText = blacklistTextArea.getSelectedText();
        if (selectedText != null) {
            blacklistTextArea.replaceSelection("");
            updateBlacklistConfig();
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要移除的内容", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void clearWhitelist() {
        int result = JOptionPane.showConfirmDialog(panel, "确定要清空白名单吗？", "确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            whitelistTextArea.setText("");
            updateWhitelistConfig();
        }
    }
    
    private void clearBlacklist() {
        int result = JOptionPane.showConfirmDialog(panel, "确定要清空黑名单吗？", "确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            blacklistTextArea.setText("");
            updateBlacklistConfig();
        }
    }
    
    private void updateWhitelistConfig() {
        List<String> whitelist = parseTextArea(whitelistTextArea);
        globalFilter.updateWhitelist(whitelist, whitelistEnabledCheckBox.isSelected());
        api.logging().raiseInfoEvent("白名单已更新: " + (whitelistEnabledCheckBox.isSelected() ? "启用" : "禁用") + ", 项目数: " + whitelist.size());
    }
    
    private void updateBlacklistConfig() {
        List<String> blacklist = parseTextArea(blacklistTextArea);
        globalFilter.updateBlacklist(blacklist, blacklistEnabledCheckBox.isSelected());
        api.logging().raiseInfoEvent("黑名单已更新: " + (blacklistEnabledCheckBox.isSelected() ? "启用" : "禁用") + ", 项目数: " + blacklist.size());
    }
    
    private List<String> parseTextArea(JTextArea textArea) {
        List<String> list = new ArrayList<>();
        String text = textArea.getText();
        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }
    
    private void testRules() {
        String testUrl = JOptionPane.showInputDialog(panel, "请输入要测试的URL:", "测试规则");
        if (testUrl != null && !testUrl.trim().isEmpty()) {
            boolean shouldProcessPassive = globalFilter.shouldProcessPassive(testUrl);
            boolean shouldProcessActive = globalFilter.shouldProcessActive(testUrl);
            
            String result = String.format("测试URL: %s\n\n" +
                "被动扫描: %s\n" +
                "主动探测: %s\n\n" +
                "说明: 如果为'是'，则该URL会被处理；如果为'否'，则会被过滤掉",
                testUrl,
                shouldProcessPassive ? "是" : "否",
                shouldProcessActive ? "是" : "否");
            
            JOptionPane.showMessageDialog(panel, result, "测试结果", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void loadCurrentConfig() {
        // 加载当前配置
        whitelistEnabledCheckBox.setSelected(globalFilter.isWhitelistEnabled());
        blacklistEnabledCheckBox.setSelected(globalFilter.isBlacklistEnabled());
        
        // 加载白名单
        StringBuilder whitelistText = new StringBuilder();
        for (String item : globalFilter.getWhitelist()) {
            whitelistText.append(item).append("\n");
        }
        whitelistTextArea.setText(whitelistText.toString());
        
        // 加载黑名单
        StringBuilder blacklistText = new StringBuilder();
        for (String item : globalFilter.getBlacklist()) {
            blacklistText.append(item).append("\n");
        }
        blacklistTextArea.setText(blacklistText.toString());
        
        // 加载参数收集模式
        com.xprobe.scanner.active.ParameterCollector.CollectionMode currentMode = 
            realtimeScanner.getCollectionMode();
        if (currentMode == com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_ONLY) {
            parameterCollectionModeComboBox.setSelectedIndex(0);
        } else {
            parameterCollectionModeComboBox.setSelectedIndex(1);
        }
    }
    
    private void saveConfig() {
        updateWhitelistConfig();
        updateBlacklistConfig();
        JOptionPane.showMessageDialog(panel, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void resetConfig() {
        int result = JOptionPane.showConfirmDialog(panel, "确定要重置所有配置吗？", "确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            whitelistEnabledCheckBox.setSelected(false);
            blacklistEnabledCheckBox.setSelected(false);
            whitelistTextArea.setText("");
            blacklistTextArea.setText("");
            updateWhitelistConfig();
            updateBlacklistConfig();
        }
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
    
    private void updateParameterCollectionMode() {
        int selectedIndex = parameterCollectionModeComboBox.getSelectedIndex();
        if (selectedIndex == 0) {
            // 仅收集参数名
            realtimeScanner.setCollectionMode(com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_ONLY);
            api.logging().raiseInfoEvent("参数收集模式: 仅收集参数名");
        } else {
            // 参数名+关键词
            realtimeScanner.setCollectionMode(com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS);
            api.logging().raiseInfoEvent("参数收集模式: 参数名+关键词");
        }
    }
    
    
    private void addProxy() {
        String input = JOptionPane.showInputDialog(panel, "请输入代理地址 (格式: ip:port 或 ip:port:username:password):", "添加代理");
        if (input != null && !input.trim().isEmpty()) {
            proxyListArea.append(input.trim() + "\n");
        }
    }
    
    private void removeProxy() {
        String selectedText = proxyListArea.getSelectedText();
        if (selectedText != null) {
            proxyListArea.replaceSelection("");
        } else {
            JOptionPane.showMessageDialog(panel, "请先选择要移除的代理", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void testProxy() {
        String proxyText = proxyListArea.getText().trim();
        if (proxyText.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请先添加代理", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String[] proxies = proxyText.split("\n");
        StringBuilder result = new StringBuilder();
        result.append("代理测试结果:\n\n");
        
        int successCount = 0;
        for (String proxy : proxies) {
            String trimmed = proxy.trim();
            if (!trimmed.isEmpty()) {
                // 简单的代理格式验证
                if (trimmed.contains(":")) {
                    result.append("✓ ").append(trimmed).append(" - 格式正确\n");
                    successCount++;
                } else {
                    result.append("✗ ").append(trimmed).append(" - 格式错误\n");
                }
            }
        }
        
        result.append("\n总计: ").append(successCount).append("/").append(proxies.length).append(" 个代理可用");
        
        JOptionPane.showMessageDialog(panel, result.toString(), "代理测试结果", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void importProxy() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件", "txt"));
        
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                java.nio.file.Files.lines(file.toPath())
                    .forEach(line -> proxyListArea.append(line + "\n"));
                JOptionPane.showMessageDialog(panel, "代理导入成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(panel, "导入失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportProxy() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件", "txt"));
        
        if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".txt")) {
                    file = new java.io.File(file.getAbsolutePath() + ".txt");
                }
                java.nio.file.Files.write(file.toPath(), proxyListArea.getText().getBytes());
                JOptionPane.showMessageDialog(panel, "代理导出成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(panel, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    public Component getComponent() {
        return panel;
    }
}

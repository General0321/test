package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.active.RealtimeScannerRefactored;
import com.xprobe.scanner.active.ExternalToolConfig;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.config.ConfigStorage;
import com.xprobe.scanner.config.ConfigValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 统一配置面板 - 集成所有配置项
 */
public class UnifiedConfigTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final GlobalFilter globalFilter;
    private final RealtimeScannerRefactored realtimeScanner;
    private final ConfigStorage configStorage;
    
    // 状态标签
    private JLabel statusLabel;
    private Timer statusTimer;
    
    // 黑白名单配置组件
    private JCheckBox whitelistEnabledCheckBox;
    private JCheckBox blacklistEnabledCheckBox;
    private JTextArea whitelistTextArea;
    private JTextArea blacklistTextArea;
    
    // 外部工具配置组件
    private JTextField arjunPathField;
    private JTextField burpProxyField;
    private JSpinner threadCountSpinner;
    private JSpinner timeoutSpinner;
    private JTextArea customDictArea;
    private JCheckBox sendToBurpCheckBox;
    private JCheckBox jsonOutputCheckBox;
    private JCheckBox verboseOutputCheckBox;
    
    // 主动探测配置组件
    private JSpinner bruteforceIntervalSpinner;
    private JSpinner minParameterCountSpinner;
    private JSpinner maxConcurrentHostsSpinner;
    private JCheckBox autoStartCheckBox;
    private JCheckBox verboseLoggingCheckBox;
    private JComboBox<String> parameterCollectionModeComboBox;
    
    // 代理池配置组件
    private JTextArea proxyListArea;
    private JCheckBox enableProxyPoolCheckBox;
    private JSpinner proxyTimeoutSpinner;
    private JSpinner maxRetriesSpinner;
    
    // 外部工具配置实例
    private ExternalToolConfig toolConfig;
    
    // 配置持久化管理器
    private com.xprobe.scanner.config.ConfigPersistence configPersistence;
    
    public UnifiedConfigTab(MontoyaApi api, ConfigurationManager configManager, 
                           GlobalFilter globalFilter, RealtimeScannerRefactored realtimeScanner,
                           com.xprobe.scanner.config.ConfigPersistence configPersistence) {
        this.api = api;
        this.configManager = configManager;
        this.globalFilter = globalFilter;
        this.realtimeScanner = realtimeScanner;
        this.configPersistence = configPersistence;
        this.toolConfig = new ExternalToolConfig();
        this.configStorage = new ConfigStorage(api);
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadAllConfigurations();
    }
    
    private void initializeComponents() {
        // 状态标签
        statusLabel = new JLabel("  ");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statusLabel.setForeground(new Color(0, 150, 0));
        
        // 黑白名单组件
        whitelistEnabledCheckBox = new JCheckBox("启用白名单");
        blacklistEnabledCheckBox = new JCheckBox("启用黑名单");
        whitelistTextArea = new JTextArea(10, 40);
        blacklistTextArea = new JTextArea(10, 40);
        whitelistTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        blacklistTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        // 外部工具组件
        arjunPathField = new JTextField(30);
        burpProxyField = new JTextField("127.0.0.1:8080", 20);
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 300, 5));
        customDictArea = new JTextArea(10, 40);
        customDictArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sendToBurpCheckBox = new JCheckBox("发送结果到Burp (使用 -oB)");
        sendToBurpCheckBox.setSelected(true);
        jsonOutputCheckBox = new JCheckBox("启用JSON输出");
        verboseOutputCheckBox = new JCheckBox("启用详细输出");
        
        // 主动探测组件
        bruteforceIntervalSpinner = new JSpinner(new SpinnerNumberModel(300, 60, 3600, 60));
        minParameterCountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        maxConcurrentHostsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        autoStartCheckBox = new JCheckBox("🚀 自动启动");
        verboseLoggingCheckBox = new JCheckBox("📝 详细日志");
        parameterCollectionModeComboBox = new JComboBox<>(new String[]{"仅参数名", "参数名+关键词"});
        
        // 代理池组件
        proxyListArea = new JTextArea(8, 40);
        proxyListArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        enableProxyPoolCheckBox = new JCheckBox("启用代理池");
        proxyTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
        maxRetriesSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // 创建选项卡
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("🔐 黑白名单", createFilterPanel());
        tabbedPane.addTab("⚡ 主动探测", createActiveScanPanel());
        tabbedPane.addTab("🌐 代理池", createProxyPoolPanel());
        
        panel.add(tabbedPane, BorderLayout.CENTER);
        
        // 底部保存按钮和状态栏
        panel.add(createBottomPanel(), BorderLayout.SOUTH);
    }
    
    private JPanel createFilterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // 白名单面板
        JPanel whitelistPanel = new JPanel(new BorderLayout(5, 5));
        whitelistPanel.setBorder(new TitledBorder("白名单配置"));
        whitelistPanel.add(whitelistEnabledCheckBox, BorderLayout.NORTH);
        whitelistPanel.add(new JScrollPane(whitelistTextArea), BorderLayout.CENTER);
        
        JPanel whitelistHelpPanel = new JPanel(new BorderLayout());
        JTextArea whitelistHelp = new JTextArea(2, 40);
        whitelistHelp.setText("每行一个规则，支持正则表达式\n例如: example\\.com 或 https://api\\..*");
        whitelistHelp.setEditable(false);
        whitelistHelp.setBackground(panel.getBackground());
        whitelistHelp.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        whitelistHelpPanel.add(whitelistHelp, BorderLayout.CENTER);
        whitelistPanel.add(whitelistHelpPanel, BorderLayout.SOUTH);
        
        // 黑名单面板
        JPanel blacklistPanel = new JPanel(new BorderLayout(5, 5));
        blacklistPanel.setBorder(new TitledBorder("黑名单配置"));
        blacklistPanel.add(blacklistEnabledCheckBox, BorderLayout.NORTH);
        blacklistPanel.add(new JScrollPane(blacklistTextArea), BorderLayout.CENTER);
        
        JPanel blacklistHelpPanel = new JPanel(new BorderLayout());
        JTextArea blacklistHelp = new JTextArea(2, 40);
        blacklistHelp.setText("每行一个规则，支持正则表达式\n例如: static\\..*\\.com 或 /logout");
        blacklistHelp.setEditable(false);
        blacklistHelp.setBackground(panel.getBackground());
        blacklistHelp.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        blacklistHelpPanel.add(blacklistHelp, BorderLayout.CENTER);
        blacklistPanel.add(blacklistHelpPanel, BorderLayout.SOUTH);
        
        splitPane.setLeftComponent(whitelistPanel);
        splitPane.setRightComponent(blacklistPanel);
        splitPane.setDividerLocation(400);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // 添加总说明
        JTextArea helpText = new JTextArea(3, 80);
        helpText.setText("全局过滤器说明:\n" +
            "• 白名单: 只处理白名单中的host/URL，为空时处理所有请求\n" +
            "• 黑名单: 排除黑名单中的host/URL\n" +
            "• 优先级: 白名单 > 黑名单，同时影响被动扫描和主动探测");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mainPanel.add(helpText, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    private JPanel createActiveScanPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 创建配置容器
        JPanel configContainer = new JPanel();
        configContainer.setLayout(new BoxLayout(configContainer, BoxLayout.Y_AXIS));
        
        // 1. 基础配置组
        JPanel basicConfigPanel = createGroupPanel("⚡ 基础配置", new Color(52, 152, 219));
        JPanel basicContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 说明标签
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel enableHint = new JLabel("💡 提示: 主动探测的启用/禁用请前往「主动探测」标签页控制");
        enableHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        enableHint.setForeground(new Color(100, 100, 100));
        basicContent.add(enableHint, gbc);
        gbc.gridwidth = 1;
        
        // 参数收集模式（移到最前面）
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel modeLabel = new JLabel("🔑 参数收集模式:");
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        modeLabel.setForeground(new Color(41, 128, 185));
        basicContent.add(modeLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        parameterCollectionModeComboBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        basicContent.add(parameterCollectionModeComboBox, gbc);
        
        // 模式说明
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JPanel modeDescPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        modeDescPanel.setBorder(new EmptyBorder(5, 20, 5, 10));
        modeDescPanel.setBackground(new Color(236, 240, 241));
        
        JLabel mode1Desc = new JLabel("• 仅参数名: 只收集参数名称，更精准、更快速");
        mode1Desc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        mode1Desc.setForeground(new Color(100, 100, 100));
        
        JLabel mode2Desc = new JLabel("• 参数名+关键词: 同时收集参数值作为关键词，更全面、发现率更高");
        mode2Desc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        mode2Desc.setForeground(new Color(100, 100, 100));
        
        modeDescPanel.add(mode1Desc);
        modeDescPanel.add(mode2Desc);
        basicContent.add(modeDescPanel, gbc);
        gbc.gridwidth = 1;
        
        // 分隔线
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JSeparator separator = new JSeparator();
        separator.setBorder(new EmptyBorder(10, 0, 10, 0));
        basicContent.add(separator, gbc);
        gbc.gridwidth = 1;
        
        // 自动启动
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        autoStartCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        basicContent.add(autoStartCheckBox, gbc);
        gbc.gridwidth = 1;
        
        // 详细日志
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        verboseLoggingCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        basicContent.add(verboseLoggingCheckBox, gbc);
        gbc.gridwidth = 1;
        
        basicConfigPanel.add(basicContent, BorderLayout.CENTER);
        configContainer.add(basicConfigPanel);
        configContainer.add(Box.createVerticalStrut(10));
        
        // 2. Arjun探测配置组（实时监听模式性能控制）
        JPanel arjunConfigPanel = createGroupPanel("✨ 实时监听模式配置（性能控制预留）", new Color(155, 89, 182));
        JPanel arjunContent = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 说明提示
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JPanel hintPanel = new JPanel(new BorderLayout(5, 0));
        hintPanel.setBackground(new Color(255, 243, 224));
        hintPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(230, 126, 34), 1),
            new EmptyBorder(8, 10, 8, 10)
        ));
        
        JLabel hintIcon = new JLabel("💡");
        hintIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        
        JTextArea hintText = new JTextArea(
            "以下参数预留用于实时监听模式的性能优化（暂未启用）\n" +
            "如果未来实时模式对性能影响过大，可通过这些参数进行调优"
        );
        hintText.setEditable(false);
        hintText.setBackground(new Color(255, 243, 224));
        hintText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        hintText.setForeground(new Color(127, 63, 17));
        hintText.setLineWrap(true);
        hintText.setWrapStyleWord(true);
        
        hintPanel.add(hintIcon, BorderLayout.WEST);
        hintPanel.add(hintText, BorderLayout.CENTER);
        arjunContent.add(hintPanel, gbc);
        gbc.gridwidth = 1;
        
        // Arjun探测间隔
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel intervalLabel = new JLabel("⏱️ 探测间隔:");
        intervalLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(intervalLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3;
        bruteforceIntervalSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(bruteforceIntervalSpinner, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0.7;
        JLabel intervalUnit = new JLabel("秒 (同一主域名两次探测的最小间隔)");
        intervalUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        intervalUnit.setForeground(Color.GRAY);
        arjunContent.add(intervalUnit, gbc);
        
        // 最小参数数量
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel minParamLabel = new JLabel("🔢 最小参数数:");
        minParamLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(minParamLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3;
        minParameterCountSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(minParameterCountSpinner, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0.7;
        JLabel minParamUnit = new JLabel("个 (触发Arjun探测所需的最少参数数)");
        minParamUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        minParamUnit.setForeground(Color.GRAY);
        arjunContent.add(minParamUnit, gbc);
        
        // 最大并发host数
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel maxHostLabel = new JLabel("🔀 最大并发数:");
        maxHostLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(maxHostLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3;
        maxConcurrentHostsSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        arjunContent.add(maxConcurrentHostsSpinner, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0.7;
        JLabel maxHostUnit = new JLabel("个 (同时探测的主机数量)");
        maxHostUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        maxHostUnit.setForeground(Color.GRAY);
        arjunContent.add(maxHostUnit, gbc);
        
        arjunConfigPanel.add(arjunContent, BorderLayout.CENTER);
        configContainer.add(arjunConfigPanel);
        configContainer.add(Box.createVerticalStrut(10));
        
        // 3. Arjun工具配置组
        JPanel toolConfigPanel = createGroupPanel("🔧 Arjun工具配置", new Color(230, 126, 34));
        JPanel toolContent = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Arjun路径
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel arjunPathLabel = new JLabel("🔨 Arjun路径:");
        arjunPathLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(arjunPathLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.7;
        arjunPathField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(arjunPathField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton("📁");
        browseButton.setToolTipText("浏览选择Arjun路径");
        browseButton.addActionListener(e -> browseArjunPath());
        toolContent.add(browseButton, gbc);
        
        // Burp代理地址
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel proxyLabel = new JLabel("🌐 Burp代理:");
        proxyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(proxyLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.7; gbc.gridwidth = 2;
        burpProxyField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(burpProxyField, gbc);
        gbc.gridwidth = 1;
        
        // 线程数
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel threadsLabel = new JLabel("🔀 线程数:");
        threadsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(threadsLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3;
        threadCountSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(threadCountSpinner, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0.7;
        JLabel threadsUnit = new JLabel("个");
        threadsUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        threadsUnit.setForeground(Color.GRAY);
        toolContent.add(threadsUnit, gbc);
        
        // 超时时间
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel timeoutLabel = new JLabel("⏱️ 超时:");
        timeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(timeoutLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3;
        timeoutSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(timeoutSpinner, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0.7;
        JLabel timeoutUnit = new JLabel("秒");
        timeoutUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        timeoutUnit.setForeground(Color.GRAY);
        toolContent.add(timeoutUnit, gbc);
        
        // 发送到Burp
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        sendToBurpCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(sendToBurpCheckBox, gbc);
        
        // JSON输出
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3;
        jsonOutputCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(jsonOutputCheckBox, gbc);
        
        // 详细输出
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3;
        verboseOutputCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolContent.add(verboseOutputCheckBox, gbc);
        gbc.gridwidth = 1;
        
        toolConfigPanel.add(toolContent, BorderLayout.CENTER);
        configContainer.add(toolConfigPanel);
        configContainer.add(Box.createVerticalStrut(10));
        
        // 4. 自定义字典组
        JPanel dictPanel = createGroupPanel("📝 自定义参数字典", new Color(52, 152, 219));
        JPanel dictContent = new JPanel(new BorderLayout(5, 5));
        
        JLabel dictLabel = new JLabel("每行一个参数名（这些参数会合并到自动收集的参数中）:");
        dictLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        dictLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        dictContent.add(dictLabel, BorderLayout.NORTH);
        
        customDictArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        customDictArea.setRows(8);
        JScrollPane dictScroll = new JScrollPane(customDictArea);
        dictScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        dictContent.add(dictScroll, BorderLayout.CENTER);
        
        JPanel dictButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton testConnectionButton = new JButton("🔌 测试Arjun连接");
        testConnectionButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        testConnectionButton.addActionListener(e -> testArjunConnection());
        dictButtonPanel.add(testConnectionButton);
        dictContent.add(dictButtonPanel, BorderLayout.SOUTH);
        
        dictPanel.add(dictContent, BorderLayout.CENTER);
        configContainer.add(dictPanel);
        
        // 🔴 重要：添加滚动条，确保内容可以完整显示
        JScrollPane scrollPane = new JScrollPane(configContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);  // 提高滚动速度
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);  // 禁用横向滚动
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 底部说明
        JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(10, 0, 0, 0),
            BorderFactory.createLineBorder(new Color(52, 152, 219), 1)
        ));
        infoPanel.setBackground(new Color(236, 240, 241));
        
        JLabel infoIcon = new JLabel("💡");
        infoIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        infoIcon.setBorder(new EmptyBorder(10, 10, 10, 5));
        
        JTextArea infoText = new JTextArea(
            "主动探测工作流程:\n" +
            "1. 被动收集阶段: 监听Burp流量，自动收集接口和参数\n" +
            "2. 参数积累阶段: 当收集的参数数量达到\"最小参数数\"时触发探测\n" +
            "3. Arjun探测阶段: 使用收集的参数作为字典，对目标进行参数发现\n" +
            "4. 间隔控制: 同一主域名在\"探测间隔\"时间内不会重复探测\n" +
            "5. 并发控制: \"最大并发数\"限制同时探测的主机数量，避免资源耗尽"
        );
        infoText.setEditable(false);
        infoText.setBackground(new Color(236, 240, 241));
        infoText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        infoText.setBorder(new EmptyBorder(10, 5, 10, 10));
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        
        infoPanel.add(infoIcon, BorderLayout.WEST);
        infoPanel.add(infoText, BorderLayout.CENTER);
        
        mainPanel.add(infoPanel, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    /**
     * 创建分组面板
     */
    private JPanel createGroupPanel(String title, Color accentColor) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 2),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleLabel.setForeground(accentColor.darker());
        titleLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        return panel;
    }
    
    
    private JPanel createProxyPoolPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 配置面板
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 启用代理池
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        configPanel.add(enableProxyPoolCheckBox, gbc);
        gbc.gridwidth = 1;
        
        // 代理超时
        gbc.gridx = 0; gbc.gridy = row;
        configPanel.add(new JLabel("代理超时 (秒):"), gbc);
        gbc.gridx = 1;
        configPanel.add(proxyTimeoutSpinner, gbc);
        row++;
        
        // 最大重试次数
        gbc.gridx = 0; gbc.gridy = row;
        configPanel.add(new JLabel("最大重试次数:"), gbc);
        gbc.gridx = 1;
        configPanel.add(maxRetriesSpinner, gbc);
        row++;
        
        mainPanel.add(configPanel, BorderLayout.NORTH);
        
        // 代理列表
        JPanel proxyListPanel = new JPanel(new BorderLayout(5, 5));
        proxyListPanel.setBorder(new TitledBorder("代理列表 (格式: host:port 或 host:port:username:password)"));
        proxyListPanel.add(new JScrollPane(proxyListArea), BorderLayout.CENTER);
        
        JPanel proxyButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton importButton = new JButton("导入代理");
        JButton exportButton = new JButton("导出代理");
        JButton testProxyButton = new JButton("测试选中代理");
        importButton.addActionListener(e -> importProxies());
        exportButton.addActionListener(e -> exportProxies());
        testProxyButton.addActionListener(e -> testProxy());
        proxyButtonPanel.add(importButton);
        proxyButtonPanel.add(exportButton);
        proxyButtonPanel.add(testProxyButton);
        proxyListPanel.add(proxyButtonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(proxyListPanel, BorderLayout.CENTER);
        
        // 说明
        JTextArea helpText = new JTextArea(3, 50);
        helpText.setText("代理池说明:\n" +
            "• 每行一个代理，格式: host:port 或 host:port:username:password\n" +
            "• 启用后将随机使用代理池中的代理进行请求");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mainPanel.add(helpText, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        // 状态标签在左侧
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        bottomPanel.add(statusPanel, BorderLayout.WEST);
        
        // 按钮在右侧
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JButton resetButton = new JButton("重置所有配置");
        JButton saveButton = new JButton("💾 保存所有配置");
        
        // 美化保存按钮
        saveButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        saveButton.setPreferredSize(new Dimension(180, 35));
        saveButton.setBackground(new Color(0, 120, 215));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        
        resetButton.addActionListener(e -> resetAllConfigurations());
        saveButton.addActionListener(e -> saveAllConfigurations());
        
        buttonPanel.add(resetButton);
        buttonPanel.add(saveButton);
        
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        return bottomPanel;
    }
    
    private void setupEventListeners() {
        // 预留：未来可添加其他事件监听器
    }
    
    private void loadAllConfigurations() {
        // 从磁盘加载配置
        XProbeConfig config = configStorage.load();
        
        // 应用到UI和各个组件
        applyConfigToUI(config);
        applyConfigToComponents(config);
    }
    
    /**
     * 将配置应用到UI组件
     */
    private void applyConfigToUI(XProbeConfig config) {
        // 黑白名单
        whitelistEnabledCheckBox.setSelected(config.isWhitelistEnabled());
        blacklistEnabledCheckBox.setSelected(config.isBlacklistEnabled());
        whitelistTextArea.setText(String.join("\n", config.getWhitelist()));
        blacklistTextArea.setText(String.join("\n", config.getBlacklist()));
        
        // 主动探测
        bruteforceIntervalSpinner.setValue(config.getBruteforceInterval());
        minParameterCountSpinner.setValue(config.getMinParameterCount());
        maxConcurrentHostsSpinner.setValue(config.getMaxConcurrentHosts());
        autoStartCheckBox.setSelected(config.isAutoStart());
        verboseLoggingCheckBox.setSelected(config.isVerboseLogging());
        parameterCollectionModeComboBox.setSelectedItem(
            config.getCollectionMode().equals("PARAMETERS_AND_KEYWORDS") ? 
            "参数名+关键词" : "仅参数名"
        );
        
        // 外部工具
        arjunPathField.setText(config.getArjunPath());
        burpProxyField.setText(config.getBurpProxyAddress());
        threadCountSpinner.setValue(config.getThreadCount());
        timeoutSpinner.setValue(config.getTimeout());
        customDictArea.setText(String.join("\n", config.getCustomDictionary()));
        sendToBurpCheckBox.setSelected(config.isSendToBurp());
        jsonOutputCheckBox.setSelected(config.isEnableJsonOutput());
        verboseOutputCheckBox.setSelected(config.isEnableVerboseOutput());
        
        // 代理池
        enableProxyPoolCheckBox.setSelected(config.isEnableProxyPool());
        proxyTimeoutSpinner.setValue(config.getProxyTimeout());
        maxRetriesSpinner.setValue(config.getMaxRetries());
        proxyListArea.setText(String.join("\n", config.getProxyList()));
    }
    
    /**
     * 将配置应用到后端组件
     */
    private void applyConfigToComponents(XProbeConfig config) {
        // 应用黑白名单到GlobalFilter
        globalFilter.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
        globalFilter.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());
        
        // 应用参数收集模式
        String collectionMode = config.getCollectionMode();
        if (realtimeScanner != null && realtimeScanner.getParameterCollector() != null) {
            realtimeScanner.getParameterCollector().setCollectionMode(
                com.xprobe.scanner.active.ParameterCollector.CollectionMode.valueOf(collectionMode)
            );
        }
        
        // 应用外部工具配置
        toolConfig.setArjunPath(config.getArjunPath());
        toolConfig.setBurpProxyAddress(config.getBurpProxyAddress());
        toolConfig.setThreadCount(config.getThreadCount());
        toolConfig.setTimeout(config.getTimeout());
        toolConfig.setCustomDictionary(new ArrayList<>(config.getCustomDictionary())); // Set -> List
        toolConfig.setSendToBurp(config.isSendToBurp());
        toolConfig.setEnableJsonOutput(config.isEnableJsonOutput());
        toolConfig.setEnableVerboseOutput(config.isEnableVerboseOutput());
    }
    
    private void saveAllConfigurations() {
        try {
            // 从UI收集所有配置
            com.xprobe.scanner.config.XProbeConfig config = collectConfigFromUI();
            
            // 验证配置
            ConfigValidator.ValidationResult validationResult = ConfigValidator.validate(config);
            if (!validationResult.isValid()) {
                // 显示验证错误
                String errorMsg = "配置验证失败:\n" + validationResult.getErrorMessage();
                JOptionPane.showMessageDialog(
                    panel,
                    errorMsg,
                    "配置错误",
                    JOptionPane.ERROR_MESSAGE
                );
                showStatus("✗ 配置验证失败", false);
                api.logging().raiseErrorEvent("配置验证失败: " + validationResult.getErrorMessage());
                return;
            }
            
            // 应用到后端组件
            applyConfigToComponents(config);
            
            // ✅ 持久化到磁盘 (使用新的ConfigPersistence)
            configPersistence.save(config);
            
            // 显示成功提示
            showStatus("✓ 所有配置已成功保存到磁盘！", true);
            api.logging().raiseInfoEvent("所有配置已保存到: " + configPersistence.getConfigFilePath());
            
        } catch (Exception e) {
            showStatus("✗ 保存配置失败: " + e.getMessage(), false);
            api.logging().raiseErrorEvent("保存配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从UI收集所有配置
     */
    private XProbeConfig collectConfigFromUI() {
        XProbeConfig config = new XProbeConfig();
        
        // 黑白名单
        config.setWhitelistEnabled(whitelistEnabledCheckBox.isSelected());
        config.setBlacklistEnabled(blacklistEnabledCheckBox.isSelected());
        config.setWhitelist(parseTextAreaToList(whitelistTextArea));
        config.setBlacklist(parseTextAreaToList(blacklistTextArea));
        
        // 主动探测（启用状态由 ActiveProbeTab 的总开关控制）
        config.setBruteforceInterval((Integer) bruteforceIntervalSpinner.getValue());
        config.setMinParameterCount((Integer) minParameterCountSpinner.getValue());
        config.setMaxConcurrentHosts((Integer) maxConcurrentHostsSpinner.getValue());
        config.setAutoStart(autoStartCheckBox.isSelected());
        config.setVerboseLogging(verboseLoggingCheckBox.isSelected());
        
        // 参数收集模式
        String selectedMode = (String) parameterCollectionModeComboBox.getSelectedItem();
        config.setCollectionMode(
            "参数名+关键词".equals(selectedMode) ? 
            "PARAMETERS_AND_KEYWORDS" : "PARAMETERS_ONLY"
        );
        
        // 外部工具
        config.setArjunPath(arjunPathField.getText().trim());
        config.setBurpProxyAddress(burpProxyField.getText().trim());
        config.setThreadCount((Integer) threadCountSpinner.getValue());
        config.setTimeout((Integer) timeoutSpinner.getValue());
        config.setCustomDictionary(new HashSet<>(parseTextAreaToList(customDictArea))); // List -> Set
        config.setSendToBurp(sendToBurpCheckBox.isSelected());
        config.setEnableJsonOutput(jsonOutputCheckBox.isSelected());
        config.setEnableVerboseOutput(verboseOutputCheckBox.isSelected());
        
        // 代理池
        config.setEnableProxyPool(enableProxyPoolCheckBox.isSelected());
        config.setProxyTimeout((Integer) proxyTimeoutSpinner.getValue());
        config.setMaxRetries((Integer) maxRetriesSpinner.getValue());
        config.setProxyList(parseTextAreaToList(proxyListArea));
        
        return config;
    }
    
    /**
     * 解析文本区域为列表
     */
    private List<String> parseTextAreaToList(JTextArea textArea) {
        List<String> list = new ArrayList<>();
        String text = textArea.getText();
        if (text != null && !text.trim().isEmpty()) {
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }
    
    private void resetAllConfigurations() {
        int result = JOptionPane.showConfirmDialog(
            panel,
            "确定要重置所有配置吗？此操作将删除已保存的配置文件并恢复默认设置。",
            "确认重置",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // 删除配置文件
            configStorage.delete();
            
            // 加载默认配置
            XProbeConfig defaultConfig = new XProbeConfig();
            applyConfigToUI(defaultConfig);
            applyConfigToComponents(defaultConfig);
            
            showStatus("✓ 配置已重置为默认值", true);
            api.logging().raiseInfoEvent("配置已重置为默认值");
        }
    }
    
    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.setForeground(success ? new Color(0, 150, 0) : new Color(200, 0, 0));
        
        // 3秒后清除状态
        if (statusTimer != null) {
            statusTimer.stop();
        }
        statusTimer = new Timer(3000, e -> statusLabel.setText("  "));
        statusTimer.setRepeats(false);
        statusTimer.start();
    }
    
    // 辅助方法
    private void browseArjunPath() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            arjunPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void testArjunConnection() {
        String arjunPath = arjunPathField.getText().trim();
        if (arjunPath.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请先输入Arjun工具路径", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // ✅ 先保存配置，以便测试时使用最新配置
        saveAllConfigurations();
        
        api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        api.logging().raiseInfoEvent("🔧 用户点击测试Arjun连接按钮");
        api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // ✅ 创建临时的 ArjunIntegration 实例进行测试
        try {
            com.xprobe.scanner.config.XProbeConfig xprobeConfig = configPersistence.load();
            
            // ✅ 从XProbeConfig提取ExternalToolConfig
            com.xprobe.scanner.active.ExternalToolConfig toolConfig = new com.xprobe.scanner.active.ExternalToolConfig();
            toolConfig.setArjunPath(xprobeConfig.getArjunPath());
            toolConfig.setBurpProxyAddress(xprobeConfig.getBurpProxyAddress());
            toolConfig.setThreadCount(xprobeConfig.getThreadCount());
            toolConfig.setTimeout(xprobeConfig.getTimeout());
            // ✅ Set -> List 转换
            if (xprobeConfig.getCustomDictionary() != null) {
                toolConfig.setCustomDictionary(new java.util.ArrayList<>(xprobeConfig.getCustomDictionary()));
            }
            toolConfig.setEnableJsonOutput(xprobeConfig.isEnableJsonOutput());
            toolConfig.setEnableVerboseOutput(xprobeConfig.isEnableVerboseOutput());
            
            com.xprobe.scanner.active.ArjunIntegration arjunIntegration = 
                new com.xprobe.scanner.active.ArjunIntegration(api, toolConfig);
            
            boolean success = arjunIntegration.testConnection();
            
            if (success) {
                api.logging().raiseInfoEvent("✅ Arjun测试成功！");
                JOptionPane.showMessageDialog(panel, 
                    "Arjun工具连接成功！\n请查看Burp日志了解详细信息。", 
                    "测试结果", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                api.logging().raiseErrorEvent("❌ Arjun测试失败！");
                JOptionPane.showMessageDialog(panel, 
                    "Arjun工具连接失败！\n请查看Burp日志了解详细错误信息。", 
                    "测试结果", 
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("❌ 测试连接时出现异常: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(panel, 
                "测试连接时出错: " + e.getMessage() + "\n请查看Burp日志了解详细错误信息。", 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void importProxies() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            // TODO: 实现代理导入
            showStatus("代理导入功能开发中...", true);
        }
    }
    
    private void exportProxies() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
            // TODO: 实现代理导出
            showStatus("代理导出功能开发中...", true);
        }
    }
    
    private void testProxy() {
        // TODO: 实现代理测试
        showStatus("代理测试功能开发中...", true);
    }
    
    public Component getComponent() {
        return panel;
    }
    
    public ExternalToolConfig getToolConfig() {
        return toolConfig;
    }
}


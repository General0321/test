package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.active.RealtimeScannerRefactored;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.config.XProbeConfigManager;
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
    private com.xprobe.scanner.active.arjun.ArjunService arjunService;  // Arjun服务（可选）
    
    // 状态标签
    private JLabel statusLabel;
    private Timer statusTimer;
    
    // 黑白名单配置组件
    private JCheckBox whitelistEnabledCheckBox;
    private JCheckBox blacklistEnabledCheckBox;
    private JTextArea whitelistTextArea;
    private JTextArea blacklistTextArea;
    
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
    
    // Java原生Arjun配置组件
    private JCheckBox arjunEnabledCheckBox;
    private JSpinner arjunChunkSizeSpinner;
    private JSpinner arjunTimeoutSpinner;
    private JTextArea arjunCustomDictArea;
    private JLabel arjunDictCountLabel;
    
    // Arjun实时模式配置组件
    private JSpinner arjunRealtimeIntervalSpinner;
    private JSpinner arjunRealtimeThresholdSpinner;
    
    // ✅ 配置管理器
    private XProbeConfigManager xprobeConfigManager;
    
    public UnifiedConfigTab(MontoyaApi api, ConfigurationManager configManager, 
                           GlobalFilter globalFilter, RealtimeScannerRefactored realtimeScanner,
                           XProbeConfigManager xprobeConfigManager) {
        this.api = api;
        this.configManager = configManager;
        this.globalFilter = globalFilter;
        this.realtimeScanner = realtimeScanner;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
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
        
        // 主动探测组件
        bruteforceIntervalSpinner = new JSpinner(new SpinnerNumberModel(300, 60, 3600, 60));
        minParameterCountSpinner = new JSpinner(new SpinnerNumberModel(15, 1, 100, 1));  // ✅ 默认15
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
        
        // Java原生Arjun配置组件
        arjunEnabledCheckBox = new JCheckBox("✅ 启用Arjun参数发现");
        arjunEnabledCheckBox.setSelected(true);
        arjunChunkSizeSpinner = new JSpinner(new SpinnerNumberModel(250, 10, 1000, 10));
        arjunTimeoutSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 60, 5));
        arjunCustomDictArea = new JTextArea(8, 40);
        arjunCustomDictArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        arjunCustomDictArea.setLineWrap(false);
        arjunDictCountLabel = new JLabel("字典: 0 个参数");
        arjunDictCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        arjunDictCountLabel.setForeground(Color.GRAY);
        
        // Arjun实时模式配置组件
        arjunRealtimeIntervalSpinner = new JSpinner(new SpinnerNumberModel(300, 60, 3600, 30));  // 60秒-60分钟，步长30秒
        arjunRealtimeThresholdSpinner = new JSpinner(new SpinnerNumberModel(15, 1, 100, 5));  // 1-100个，步长5
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
        JLabel minParamUnit = new JLabel("个 (智能触发阈值：达到此数量自动触发Arjun)");
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
        
        // 2.5. Java原生Arjun配置组
        JPanel javaArjunConfigPanel = createGroupPanel("🔍 Java原生Arjun配置", new Color(52, 152, 219));
        JPanel javaArjunContent = new JPanel(new GridBagLayout());
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(5, 10, 5, 10);
        gbc2.anchor = GridBagConstraints.WEST;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        
        // 说明提示
        gbc2.gridx = 0; gbc2.gridy = 0; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JPanel javaArjunHintPanel = new JPanel(new BorderLayout(5, 0));
        javaArjunHintPanel.setBackground(new Color(232, 246, 253));
        javaArjunHintPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 1),
            new EmptyBorder(8, 10, 8, 10)
        ));
        
        JLabel javaArjunHintIcon = new JLabel("💡");
        javaArjunHintIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        
        JTextArea javaArjunHintText = new JTextArea(
            "Java原生Arjun参数发现引擎（无需外部工具，跨平台）\n" +
            "• 支持GET/POST/POST-JSON • 内置152个特殊参数 • 动态稳定性检测 • 自动去重"
        );
        javaArjunHintText.setEditable(false);
        javaArjunHintText.setBackground(new Color(232, 246, 253));
        javaArjunHintText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        javaArjunHintText.setForeground(new Color(21, 67, 96));
        javaArjunHintText.setLineWrap(true);
        javaArjunHintText.setWrapStyleWord(true);
        
        javaArjunHintPanel.add(javaArjunHintIcon, BorderLayout.WEST);
        javaArjunHintPanel.add(javaArjunHintText, BorderLayout.CENTER);
        javaArjunContent.add(javaArjunHintPanel, gbc2);
        gbc2.gridwidth = 1;
        
        // 启用Arjun
        gbc2.gridx = 0; gbc2.gridy = 1; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        arjunEnabledCheckBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        arjunEnabledCheckBox.setForeground(new Color(52, 152, 219));
        javaArjunContent.add(arjunEnabledCheckBox, gbc2);
        gbc2.gridwidth = 1;
        
        // 分块大小
        gbc2.gridx = 0; gbc2.gridy = 2; gbc2.weightx = 0;
        JLabel chunkLabel = new JLabel("📦 分块大小:");
        chunkLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(chunkLabel, gbc2);
        
        gbc2.gridx = 1; gbc2.weightx = 0.3;
        arjunChunkSizeSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(arjunChunkSizeSpinner, gbc2);
        
        gbc2.gridx = 2; gbc2.weightx = 0.7;
        JLabel chunkUnit = new JLabel("个参数/批次 (10-1000，默认250)");
        chunkUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        chunkUnit.setForeground(Color.GRAY);
        javaArjunContent.add(chunkUnit, gbc2);
        
        // 超时时间
        gbc2.gridx = 0; gbc2.gridy = 3; gbc2.weightx = 0;
        JLabel arjunTimeoutLabel = new JLabel("⏱️ 超时时间:");
        arjunTimeoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(arjunTimeoutLabel, gbc2);
        
        gbc2.gridx = 1; gbc2.weightx = 0.3;
        arjunTimeoutSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(arjunTimeoutSpinner, gbc2);
        
        gbc2.gridx = 2; gbc2.weightx = 0.7;
        JLabel arjunTimeoutUnit = new JLabel("秒 (单次请求超时，5-60秒)");
        arjunTimeoutUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        arjunTimeoutUnit.setForeground(Color.GRAY);
        javaArjunContent.add(arjunTimeoutUnit, gbc2);
        
        // 分隔线
        gbc2.gridx = 0; gbc2.gridy = 4; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JSeparator sep2 = new JSeparator();
        sep2.setBorder(new EmptyBorder(10, 0, 10, 0));
        javaArjunContent.add(sep2, gbc2);
        gbc2.gridwidth = 1;
        
        // 自定义字典标题
        gbc2.gridx = 0; gbc2.gridy = 5; gbc2.gridwidth = 2; gbc2.weightx = 1.0;
        JLabel arjunDictLabel = new JLabel("📚 自定义参数字典:");
        arjunDictLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        arjunDictLabel.setForeground(new Color(52, 152, 219));
        javaArjunContent.add(arjunDictLabel, gbc2);
        
        gbc2.gridx = 2; gbc2.weightx = 0;
        javaArjunContent.add(arjunDictCountLabel, gbc2);
        gbc2.gridwidth = 1;
        
        // 字典文本区域
        gbc2.gridx = 0; gbc2.gridy = 6; gbc2.gridwidth = 3; gbc2.weightx = 1.0; gbc2.weighty = 1.0;
        gbc2.fill = GridBagConstraints.BOTH;
        JScrollPane arjunDictScrollPane = new JScrollPane(arjunCustomDictArea);
        arjunDictScrollPane.setPreferredSize(new Dimension(400, 150));
        javaArjunContent.add(arjunDictScrollPane, gbc2);
        gbc2.weighty = 0;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.gridwidth = 1;
        
        // 按钮面板
        gbc2.gridx = 0; gbc2.gridy = 7; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JPanel arjunDictButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        arjunDictButtonPanel.setBackground(new Color(236, 240, 241));
        
        JButton uploadDictButton = new JButton("📁 上传字典");
        uploadDictButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        uploadDictButton.addActionListener(e -> uploadArjunDictionary());
        
        JButton clearDictButton = new JButton("🗑️ 清空");
        clearDictButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        clearDictButton.addActionListener(e -> clearArjunDictionary());
        
        JButton exportDictButton = new JButton("💾 导出");
        exportDictButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        exportDictButton.addActionListener(e -> exportArjunDictionary());
        
        arjunDictButtonPanel.add(uploadDictButton);
        arjunDictButtonPanel.add(clearDictButton);
        arjunDictButtonPanel.add(exportDictButton);
        javaArjunContent.add(arjunDictButtonPanel, gbc2);
        
        // 帮助提示
        gbc2.gridx = 0; gbc2.gridy = 8; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JLabel arjunDictHint = new JLabel("💡 每行一个参数名，上传TXT文件自动合并（去重）");
        arjunDictHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
        arjunDictHint.setForeground(Color.GRAY);
        javaArjunContent.add(arjunDictHint, gbc2);
        
        // 分隔线
        gbc2.gridx = 0; gbc2.gridy = 9; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JSeparator sep3 = new JSeparator();
        sep3.setBorder(new EmptyBorder(15, 0, 10, 0));
        javaArjunContent.add(sep3, gbc2);
        gbc2.gridwidth = 1;
        
        // 实时模式配置标题
        gbc2.gridx = 0; gbc2.gridy = 10; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JLabel realtimeLabel = new JLabel("⚡ 实时模式配置:");
        realtimeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        realtimeLabel.setForeground(new Color(52, 152, 219));
        javaArjunContent.add(realtimeLabel, gbc2);
        gbc2.gridwidth = 1;
        
        // 参数阈值
        gbc2.gridx = 0; gbc2.gridy = 11; gbc2.weightx = 0;
        JLabel thresholdLabel = new JLabel("🔢 参数阈值:");
        thresholdLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(thresholdLabel, gbc2);
        
        gbc2.gridx = 1; gbc2.weightx = 0.3;
        arjunRealtimeThresholdSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(arjunRealtimeThresholdSpinner, gbc2);
        
        gbc2.gridx = 2; gbc2.weightx = 0.7;
        JLabel thresholdUnit = new JLabel("个 (达到此数量自动触发Arjun)");
        thresholdUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        thresholdUnit.setForeground(Color.GRAY);
        javaArjunContent.add(thresholdUnit, gbc2);
        
        // 定时间隔
        gbc2.gridx = 0; gbc2.gridy = 12; gbc2.weightx = 0;
        JLabel rtIntervalLabel = new JLabel("⏱️ 定时间隔:");
        rtIntervalLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(rtIntervalLabel, gbc2);
        
        gbc2.gridx = 1; gbc2.weightx = 0.3;
        arjunRealtimeIntervalSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        javaArjunContent.add(arjunRealtimeIntervalSpinner, gbc2);
        
        gbc2.gridx = 2; gbc2.weightx = 0.7;
        JLabel rtIntervalUnit = new JLabel("秒 (定时兜底检查，只在有新参数时触发)");
        rtIntervalUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        rtIntervalUnit.setForeground(Color.GRAY);
        javaArjunContent.add(rtIntervalUnit, gbc2);
        
        // 实时模式提示
        gbc2.gridx = 0; gbc2.gridy = 13; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
        JLabel realtimeHint = new JLabel("💡 智能触发：达到阈值立即触发 + 定时兜底（有新参数时）");
        realtimeHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
        realtimeHint.setForeground(Color.GRAY);
        javaArjunContent.add(realtimeHint, gbc2);
        
        javaArjunConfigPanel.add(javaArjunContent, BorderLayout.CENTER);
        configContainer.add(javaArjunConfigPanel);
        configContainer.add(Box.createVerticalStrut(10));
        
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
        
        // Java原生Arjun配置
        arjunEnabledCheckBox.setSelected(config.isArjunEnabled());
        arjunChunkSizeSpinner.setValue(config.getArjunChunkSize());
        arjunTimeoutSpinner.setValue(config.getArjunTimeout());
        arjunCustomDictArea.setText(String.join("\n", config.getArjunCustomDictionary()));
        updateArjunDictCount();
        arjunRealtimeIntervalSpinner.setValue(config.getArjunRealtimeInterval());
        arjunRealtimeThresholdSpinner.setValue(config.getArjunRealtimeThreshold());
        
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
        
        // 应用Java原生Arjun配置
        if (arjunService != null) {
            com.xprobe.scanner.active.arjun.config.ArjunConfig arjunConfig = arjunService.getConfig();
            arjunConfig.setEnabled(config.isArjunEnabled());
            arjunConfig.setChunkSize(config.getArjunChunkSize());
            arjunConfig.setTimeout(config.getArjunTimeout());
            
            // 应用用户自定义字典
            arjunService.setUserCustomDictionary(config.getArjunCustomDictionary());
            
            api.logging().raiseInfoEvent(String.format(
                "✅ Arjun配置已更新: 启用=%b, 块大小=%d, 超时=%d秒, 自定义字典=%d个",
                config.isArjunEnabled(), config.getArjunChunkSize(), config.getArjunTimeout(),
                config.getArjunCustomDictionary().size()
            ));
        }
        
        // ✅ 应用实时模式配置到实时扫描器
        if (realtimeScanner != null) {
            realtimeScanner.setMinParameterThreshold(config.getArjunRealtimeThreshold());
            realtimeScanner.setCooldownSeconds(config.getArjunRealtimeInterval());
            api.logging().raiseInfoEvent(String.format(
                "✅ 实时模式配置已更新: 参数阈值=%d, 定时间隔=%d秒",
                config.getArjunRealtimeThreshold(), config.getArjunRealtimeInterval()
            ));
        }
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
            
            // ✅ 持久化到磁盘 (使用配置管理器)
            xprobeConfigManager.saveConfig(config);
            
            // 显示成功提示
            showStatus("✓ 所有配置已成功保存到磁盘！", true);
            api.logging().raiseInfoEvent("所有配置已保存到: " + xprobeConfigManager.getConfigFilePath());
            
        } catch (Exception e) {
            showStatus("✗ 保存配置失败: " + e.getMessage(), false);
            api.logging().raiseErrorEvent("保存配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从UI收集所有配置
     */
    /**
     * ✅ 从UI收集配置（防御性复制）
     * 
     * 获取配置副本并只更新UnifiedConfigTab管理的字段，
     * 保留其他组件（如PassiveScanConfigTab）设置的配置
     */
    private XProbeConfig collectConfigFromUI() {
        // ✅ 关键修复：从configManager获取副本，而不是创建新对象
        XProbeConfig config = xprobeConfigManager.getConfigCopy();
        
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
        
        // Java原生Arjun配置
        config.setArjunEnabled(arjunEnabledCheckBox.isSelected());
        config.setArjunChunkSize((Integer) arjunChunkSizeSpinner.getValue());
        config.setArjunTimeout((Integer) arjunTimeoutSpinner.getValue());
        config.setArjunCustomDictionary(new HashSet<>(parseTextAreaToList(arjunCustomDictArea)));
        config.setArjunRealtimeInterval((Integer) arjunRealtimeIntervalSpinner.getValue());
        config.setArjunRealtimeThreshold((Integer) arjunRealtimeThresholdSpinner.getValue());
        
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
    
    // ========== Arjun字典管理 ==========
    
    /**
     * 上传Arjun自定义字典
     */
    private void uploadArjunDictionary() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择Arjun字典文件");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        
        if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                java.util.Set<String> newParams = new java.util.HashSet<>();
                
                // 读取文件
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            newParams.add(line);
                        }
                    }
                }
                
                // 合并到现有字典
                String existingText = arjunCustomDictArea.getText();
                if (!existingText.isEmpty()) {
                    String[] existing = existingText.split("\n");
                    for (String param : existing) {
                        param = param.trim();
                        if (!param.isEmpty()) {
                            newParams.add(param);
                        }
                    }
                }
                
                // 更新UI
                arjunCustomDictArea.setText(String.join("\n", newParams));
                updateArjunDictCount();
                
                showStatus(String.format("✓ 上传成功！合并了 %d 个参数", newParams.size()), true);
                api.logging().raiseInfoEvent("Arjun字典上传成功: " + file.getName());
                
            } catch (Exception e) {
                showStatus("✗ 上传失败: " + e.getMessage(), false);
                api.logging().raiseErrorEvent("Arjun字典上传失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 清空Arjun自定义字典
     */
    private void clearArjunDictionary() {
        int confirm = JOptionPane.showConfirmDialog(
            panel,
            "确定要清空Arjun自定义字典吗？",
            "确认清空",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            arjunCustomDictArea.setText("");
            updateArjunDictCount();
            showStatus("✓ Arjun字典已清空", true);
            api.logging().raiseInfoEvent("Arjun自定义字典已清空");
        }
    }
    
    /**
     * 导出Arjun自定义字典
     */
    private void exportArjunDictionary() {
        String text = arjunCustomDictArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "字典为空，无法导出", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出Arjun字典");
        fileChooser.setSelectedFile(new java.io.File("arjun-custom-dict.txt"));
        
        if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                    writer.print(text);
                }
                
                showStatus("✓ 导出成功: " + file.getName(), true);
                api.logging().raiseInfoEvent("Arjun字典导出成功: " + file.getAbsolutePath());
                
            } catch (Exception e) {
                showStatus("✗ 导出失败: " + e.getMessage(), false);
                api.logging().raiseErrorEvent("Arjun字典导出失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 更新Arjun字典计数
     */
    private void updateArjunDictCount() {
        String text = arjunCustomDictArea.getText().trim();
        if (text.isEmpty()) {
            arjunDictCountLabel.setText("字典: 0 个参数");
        } else {
            String[] lines = text.split("\n");
            java.util.Set<String> uniqueParams = new java.util.HashSet<>();
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    uniqueParams.add(line);
                }
            }
            arjunDictCountLabel.setText(String.format("字典: %d 个参数", uniqueParams.size()));
        }
    }
    
    public Component getComponent() {
        return panel;
    }
    
    /**
     * 设置ArjunService实例（用于配置更新）
     */
    public void setArjunService(com.xprobe.scanner.active.arjun.ArjunService arjunService) {
        this.arjunService = arjunService;
    }
}


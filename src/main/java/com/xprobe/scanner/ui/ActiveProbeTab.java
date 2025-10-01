package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.active.ActiveScanner;
import com.xprobe.scanner.active.ParameterCollector;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.ConfigStorage;
import com.xprobe.scanner.config.XProbeConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 主动探测选项卡 - 支持实时监听和手动触发两种模式
 */
public class ActiveProbeTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ActiveScanner activeScanner;
    private final ConfigStorage configStorage;
    
    // 核心UI组件
    private JTable collectedDataTable;
    private DefaultTableModel collectedDataTableModel;
    private JTable arjunResultTable;
    private DefaultTableModel arjunResultTableModel;
    
    // 控制组件
    private JToggleButton masterEnableToggle;  // 主动探测总开关
    private JLabel statusLabel;
    private JButton arjunScanButton;
    private JButton refreshDataButton;
    private JButton addTargetButton;
    private JButton clearResultsButton;
    private JProgressBar progressBar;
    
    // 模式控制
    private JRadioButton realtimeModeRadio;
    private JRadioButton manualModeRadio;
    private ButtonGroup modeGroup;
    private JLabel modeStatusLabel;
    
    // 配置显示
    private JLabel configInfoLabel;
    
    // 统计面板
    private JLabel totalDomainsLabel;
    private JLabel totalEndpointsLabel;
    private JLabel totalParametersLabel;
    private JLabel totalKeywordsLabel;
    private JLabel arjunScansLabel;
    private JLabel arjunResultsLabel;
    
    // 定时刷新
    private javax.swing.Timer refreshTimer;
    
    // 实时监听模式定时器
    private javax.swing.Timer realtimeArjunTimer;

    public ActiveProbeTab(MontoyaApi api, ConfigurationManager configManager, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.activeScanner = new ActiveScanner(api, configManager, realtimeScanner);
        this.configStorage = new ConfigStorage(api);
        
        initializeComponents();
        loadMasterSwitchState();  // 从配置加载总开关状态
        setupLayout();
        setupEventListeners();
        startAutoRefresh();
    }

    private void initializeComponents() {
        // 主动探测总开关
        masterEnableToggle = new JToggleButton("✅ 主动探测已启用", true);
        masterEnableToggle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        masterEnableToggle.setFocusPainted(false);
        updateToggleButtonAppearance(true);
        
        // 已收集数据表格
        collectedDataTableModel = new DefaultTableModel(
            new Object[]{"🌐 主域名", "🔗 接口数", "🔑 参数数", "📝 关键词数", "🕐 最后更新", "📊 状态"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        collectedDataTable = new JTable(collectedDataTableModel);
        collectedDataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        collectedDataTable.setRowHeight(28);
        collectedDataTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        collectedDataTable.getColumnModel().getColumn(0).setPreferredWidth(200);

        // Arjun探测结果表格
        arjunResultTableModel = new DefaultTableModel(
            new Object[]{"🎯 目标域名", "🔗 接口", "✨ 发现参数", "📋 参数类型", "✅ 验证状态", "🕐 探测时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        arjunResultTable = new JTable(arjunResultTableModel);
        arjunResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        arjunResultTable.setRowHeight(25);
        arjunResultTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        // 状态标签
        statusLabel = new JLabel("🟢 就绪 - 正在监听Burp流量...");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        statusLabel.setForeground(new Color(46, 204, 113));

        // 进度条
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 25));
        
        // 按钮
        arjunScanButton = createStyledButton("✨ 开始Arjun探测", new Color(155, 89, 182));
        refreshDataButton = createStyledButton("🔄 刷新数据", new Color(52, 152, 219));
        addTargetButton = createStyledButton("➕ 手动添加", new Color(241, 196, 15));
        clearResultsButton = createStyledButton("🗑️ 清空结果", new Color(231, 76, 60));
        
        // 模式控制
        realtimeModeRadio = new JRadioButton("🔄 实时监听模式");
        manualModeRadio = new JRadioButton("👆 手动触发模式");
        modeGroup = new ButtonGroup();
        modeGroup.add(realtimeModeRadio);
        modeGroup.add(manualModeRadio);
        manualModeRadio.setSelected(true); // 默认手动模式
        
        realtimeModeRadio.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        manualModeRadio.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
        modeStatusLabel = new JLabel("当前: 手动触发模式");
        modeStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        modeStatusLabel.setForeground(Color.GRAY);
        
        // 配置信息
        configInfoLabel = new JLabel("配置: 间隔60秒 | 最小参数3个 | 最大并发5个 | 收集模式: 仅参数名");
        configInfoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        configInfoLabel.setForeground(new Color(100, 100, 100));
        
        // 统计标签
        totalDomainsLabel = new JLabel("域名: 0");
        totalEndpointsLabel = new JLabel("接口: 0");
        totalParametersLabel = new JLabel("参数: 0");
        totalKeywordsLabel = new JLabel("关键词: 0");
        arjunScansLabel = new JLabel("探测次数: 0");
        arjunResultsLabel = new JLabel("发现参数: 0");
        
        Font statsFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        totalDomainsLabel.setFont(statsFont);
        totalEndpointsLabel.setFont(statsFont);
        totalParametersLabel.setFont(statsFont);
        totalKeywordsLabel.setFont(statsFont);
        arjunScansLabel.setFont(statsFont);
        arjunResultsLabel.setFont(statsFont);
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        return button;
    }

    private void setupLayout() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 顶部：模式选择 + 配置信息 + 控制面板
        JPanel topPanel = createTopPanel();
        
        // 中间：分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.4);

        // 上半部分：已收集数据
        JPanel collectedPanel = new JPanel(new BorderLayout(5, 5));
        collectedPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2),
            "📊 已收集的流量数据（来自 Burp Proxy + SiteMap）",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            new Color(52, 152, 219)
        ));
        
        JScrollPane collectedScrollPane = new JScrollPane(collectedDataTable);
        collectedPanel.add(collectedScrollPane, BorderLayout.CENTER);
        
        // 添加右侧统计面板
        JPanel statsPanel = createStatsPanel();
        collectedPanel.add(statsPanel, BorderLayout.EAST);
        
        // 下半部分：Arjun探测结果
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(155, 89, 182), 2),
            "✨ Arjun 参数探测结果",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            new Color(155, 89, 182)
        ));
        
        JScrollPane resultScrollPane = new JScrollPane(arjunResultTable);
        resultPanel.add(resultScrollPane, BorderLayout.CENTER);

        splitPane.setTopComponent(collectedPanel);
        splitPane.setBottomComponent(resultPanel);

        // 底部：状态栏
        JPanel bottomPanel = createBottomPanel();

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        
        // 总开关面板（放在最上方）
        JPanel masterSwitchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        masterSwitchPanel.setBackground(new Color(245, 245, 245));
        masterSwitchPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2),
            new EmptyBorder(5, 10, 5, 10)
        ));
        masterSwitchPanel.add(masterEnableToggle);
        
        JLabel masterHint = new JLabel("总开关控制整个主动探测功能");
        masterHint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        masterHint.setForeground(Color.GRAY);
        masterSwitchPanel.add(masterHint);
        
        // 模式和配置容器
        JPanel modeConfigContainer = new JPanel(new BorderLayout(10, 10));
        
        // 模式选择面板
        JPanel modePanel = new JPanel(new BorderLayout(10, 5));
        modePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
            "⚙️ 探测模式",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12)
        ));
        
        // 模式选择按钮
        JPanel modeRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        modeRadioPanel.add(manualModeRadio);
        modeRadioPanel.add(realtimeModeRadio);
        modeRadioPanel.add(modeStatusLabel);
        
        // 模式说明
        JPanel modeDescPanel = new JPanel(new GridLayout(3, 1, 5, 2));
        modeDescPanel.setBorder(new EmptyBorder(0, 20, 5, 10));
        modeDescPanel.setBackground(new Color(236, 240, 241));
        
        JLabel manualDesc = new JLabel("• 手动触发: 从SiteMap获取历史流量进行Arjun探测，点击按钮时执行");
        manualDesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        manualDesc.setForeground(new Color(80, 80, 80));
        
        JLabel realtimeDesc = new JLabel("• 实时监听: 监听Burp Proxy实时流量，当参数达到阈值时自动触发Arjun探测");
        realtimeDesc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        realtimeDesc.setForeground(new Color(80, 80, 80));
        
        JLabel dedupeDesc = new JLabel("  ⚡ 去重机制: method+host+content-type+uri+已探测参数，确保不重复探测");
        dedupeDesc.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
        dedupeDesc.setForeground(new Color(120, 120, 120));
        
        modeDescPanel.add(manualDesc);
        modeDescPanel.add(realtimeDesc);
        modeDescPanel.add(dedupeDesc);
        
        JPanel modeContentPanel = new JPanel(new BorderLayout());
        modeContentPanel.add(modeRadioPanel, BorderLayout.NORTH);
        modeContentPanel.add(modeDescPanel, BorderLayout.CENTER);
        
        modePanel.add(modeContentPanel, BorderLayout.CENTER);
        
        // 配置信息面板
        JPanel configPanel = new JPanel(new BorderLayout(5, 5));
        configPanel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(5, 0, 5, 0),
            BorderFactory.createLineBorder(new Color(52, 152, 219), 1)
        ));
        configPanel.setBackground(new Color(240, 248, 255));
        
        JLabel configIcon = new JLabel("⚡");
        configIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        configIcon.setBorder(new EmptyBorder(3, 8, 3, 3));
        
        JPanel configTextPanel = new JPanel(new GridLayout(2, 1, 0, 0));
        configTextPanel.setBackground(new Color(240, 248, 255));
        
        JLabel configTitle = new JLabel("当前配置:");
        configTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        configTitle.setForeground(new Color(52, 152, 219));
        
        configTextPanel.add(configTitle);
        configTextPanel.add(configInfoLabel);
        
        JButton gotoConfigButton = new JButton("📝 修改配置");
        gotoConfigButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        gotoConfigButton.setForeground(new Color(52, 152, 219));
        gotoConfigButton.setContentAreaFilled(false);
        gotoConfigButton.setBorderPainted(false);
        gotoConfigButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gotoConfigButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(panel,
                "请前往「配置中心」→「主动探测」标签页修改配置",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
        });
        
        configPanel.add(configIcon, BorderLayout.WEST);
        configPanel.add(configTextPanel, BorderLayout.CENTER);
        configPanel.add(gotoConfigButton, BorderLayout.EAST);
        
        // 控制按钮面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controlPanel.add(arjunScanButton);
        controlPanel.add(refreshDataButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(addTargetButton);
        controlPanel.add(clearResultsButton);
        
        JPanel modeConfigPanel = new JPanel(new BorderLayout(5, 5));
        modeConfigPanel.add(modePanel, BorderLayout.NORTH);
        modeConfigPanel.add(configPanel, BorderLayout.CENTER);
        modeConfigPanel.add(controlPanel, BorderLayout.SOUTH);
        
        modeConfigContainer.add(modeConfigPanel, BorderLayout.CENTER);
        
        topPanel.add(masterSwitchPanel, BorderLayout.NORTH);
        topPanel.add(modeConfigContainer, BorderLayout.CENTER);
        
        return topPanel;
    }
    
    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2),
            "📈 实时统计",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 12)
        ));
        statsPanel.setPreferredSize(new Dimension(180, 0));
        
        statsPanel.add(Box.createVerticalStrut(10));
        statsPanel.add(createStatCard("🌐", totalDomainsLabel, new Color(52, 152, 219)));
        statsPanel.add(Box.createVerticalStrut(8));
        statsPanel.add(createStatCard("🔗", totalEndpointsLabel, new Color(46, 204, 113)));
        statsPanel.add(Box.createVerticalStrut(8));
        statsPanel.add(createStatCard("🔑", totalParametersLabel, new Color(241, 196, 15)));
        statsPanel.add(Box.createVerticalStrut(8));
        statsPanel.add(createStatCard("📝", totalKeywordsLabel, new Color(230, 126, 34)));
        statsPanel.add(Box.createVerticalStrut(15));
        statsPanel.add(createStatCard("✨", arjunScansLabel, new Color(155, 89, 182)));
        statsPanel.add(Box.createVerticalStrut(8));
        statsPanel.add(createStatCard("🎯", arjunResultsLabel, new Color(231, 76, 60)));
        statsPanel.add(Box.createVerticalGlue());
        
        return statsPanel;
    }
    
    private JPanel createStatCard(String icon, JLabel label, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 0));
        card.setMaximumSize(new Dimension(160, 35));
        card.setBackground(color.brighter().brighter());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 1),
            new EmptyBorder(5, 8, 5, 8)
        ));
        
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        
        label.setForeground(color.darker().darker());
        
        card.add(iconLabel, BorderLayout.WEST);
        card.add(label, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 5));
        bottomPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        
        // 左侧：进度条
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        progressPanel.add(new JLabel("进度:"));
        progressPanel.add(progressBar);
        
        // 右侧：状态
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusPanel.add(statusLabel);
        
        bottomPanel.add(progressPanel, BorderLayout.WEST);
        bottomPanel.add(statusPanel, BorderLayout.EAST);
        
        return bottomPanel;
    }

    private void setupEventListeners() {
        // 总开关监听
        masterEnableToggle.addActionListener(e -> toggleMasterSwitch());
        
        arjunScanButton.addActionListener(e -> startArjunScan());
        refreshDataButton.addActionListener(e -> refreshCollectedData());
        addTargetButton.addActionListener(e -> showAddTargetDialog());
        clearResultsButton.addActionListener(e -> clearResults());
        
        // 模式切换监听
        realtimeModeRadio.addActionListener(e -> switchToRealtimeMode());
        manualModeRadio.addActionListener(e -> switchToManualMode());
    }
    
    /**
     * 切换到实时监听模式
     * 
     * 实时监听模式：
     * - 数据源：Burp Proxy 实时流量
     * - 触发方式：当参数达到阈值时自动触发Arjun
     * - 去重机制：method + host + content-type + uri + 已探测参数
     */
    private void switchToRealtimeMode() {
        modeStatusLabel.setText("当前: 实时监听模式 (Proxy实时流量)");
        modeStatusLabel.setForeground(new Color(46, 204, 113));
        statusLabel.setText("🔄 实时监听Proxy流量 - 自动触发Arjun探测...");
        statusLabel.setForeground(new Color(46, 204, 113));
        
        // 启动实时监听定时器（例如每5分钟检查一次）
        if (realtimeArjunTimer == null) {
            realtimeArjunTimer = new javax.swing.Timer(300000, e -> {
                // 在实时模式下，检查Proxy流量是否有新数据需要触发Arjun
                // 去重颗粒度：method + host + content-type + uri + 已探测参数
                api.logging().raiseInfoEvent("实时监听模式: 检查Proxy流量，触发Arjun探测");
                checkAndTriggerArjunFromProxy();
            });
        }
        realtimeArjunTimer.start();
        
        api.logging().raiseInfoEvent("已切换到实时监听模式（Proxy流量）");
    }
    
    /**
     * 切换到手动触发模式
     * 
     * 手动触发模式：
     * - 数据源：SiteMap 历史流量
     * - 触发方式：用户点击按钮时执行
     * - 去重机制：method + host + content-type + uri + 已探测参数
     */
    private void switchToManualMode() {
        modeStatusLabel.setText("当前: 手动触发模式 (SiteMap历史流量)");
        modeStatusLabel.setForeground(Color.GRAY);
        statusLabel.setText("🟢 就绪 - 点击按钮从SiteMap触发探测...");
        statusLabel.setForeground(new Color(46, 204, 113));
        
        // 停止实时监听定时器
        if (realtimeArjunTimer != null) {
            realtimeArjunTimer.stop();
        }
        
        api.logging().raiseInfoEvent("已切换到手动触发模式（SiteMap流量）");
    }
    
    /**
     * 从Proxy实时流量检查并触发Arjun探测
     * 
     * 🔴 优化1：实时监听模式使用 ParameterCollector 中的实时数据
     */
    private void checkAndTriggerArjunFromProxy() {
        try {
            statusLabel.setText("🔄 从Proxy实时流量触发Arjun探测...");
            statusLabel.setForeground(new Color(52, 152, 219));
            
            // 🔴 调用新方法：使用ParameterCollector中的实时数据
            activeScanner.getRealtimeScanner().triggerArjunScanFromProxy();
            
            statusLabel.setText("✅ Proxy实时流量Arjun探测已触发");
            statusLabel.setForeground(new Color(46, 204, 113));
            
        } catch (Exception e) {
            statusLabel.setText("❌ Proxy探测触发失败: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
            api.logging().raiseErrorEvent("从Proxy触发Arjun失败: " + e.getMessage());
        }
    }
    
    /**
     * 开始自动刷新（每3秒更新一次收集数据）
     */
    private void startAutoRefresh() {
        refreshTimer = new javax.swing.Timer(3000, e -> refreshCollectedData());
        refreshTimer.start();
        
        // 初始加载
        refreshCollectedData();
    }

    /**
     * 刷新已收集的数据
     */
    private void refreshCollectedData() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (activeScanner.getRealtimeScanner() == null) {
                    return;
                }
                
                var realtimeScanner = activeScanner.getRealtimeScanner();
                ParameterCollector.CollectorStatistics stats = 
                    realtimeScanner.getParameterCollector().getStatistics();
                
                // 更新统计标签
                totalDomainsLabel.setText("域名: " + stats.getDomainCount());
                totalEndpointsLabel.setText("接口: " + stats.getEndpointCount());
                totalParametersLabel.setText("参数: " + stats.getParameterCount());
                totalKeywordsLabel.setText("关键词: " + stats.getKeywordCount());
                
                // 更新配置信息（从stats中获取收集模式）
                String modeText = stats.getMode() == ParameterCollector.CollectionMode.PARAMETERS_ONLY 
                    ? "仅参数名" : "参数名+关键词";
                configInfoLabel.setText("配置: 间隔60秒 | 最小参数3个 | 最大并发5个 | 收集模式: " + modeText);
                
                // 获取域名级别的详细统计
                Map<String, ?> domainStats = realtimeScanner.getDomainStatistics();
                
                // 清空并重新填充表格
                collectedDataTableModel.setRowCount(0);
                
                for (Map.Entry<String, ?> entry : domainStats.entrySet()) {
                    String mainDomain = entry.getKey();
                    Object statsObj = entry.getValue();
                    
                    try {
                        int endpointCount = (int) statsObj.getClass().getMethod("getEndpointCount").invoke(statsObj);
                        int parameterCount = (int) statsObj.getClass().getMethod("getParameterCount").invoke(statsObj);
                        int keywordCount = (int) statsObj.getClass().getMethod("getKeywordCount").invoke(statsObj);
                        String lastUpdate = (String) statsObj.getClass().getMethod("getLastUpdateTimeFormatted").invoke(statsObj);
                        
                        String status = endpointCount > 0 ? "✅ 已收集" : "⏳ 收集中";
                        
                        collectedDataTableModel.addRow(new Object[]{
                            mainDomain,
                            endpointCount,
                            parameterCount,
                            keywordCount,
                            lastUpdate,
                            status
                        });
                    } catch (Exception ex) {
                        api.logging().raiseDebugEvent("获取域名统计失败: " + ex.getMessage());
                    }
                }
                
                // 更新Arjun统计
                arjunResultsLabel.setText("发现参数: " + arjunResultTableModel.getRowCount());
                
            } catch (Exception ex) {
                api.logging().raiseDebugEvent("刷新数据失败: " + ex.getMessage());
            }
        });
    }

    /**
     * 启动Arjun扫描（根据当前模式选择数据源）
     */
    private void startArjunScan() {
        try {
            if (activeScanner.getRealtimeScanner() == null) {
                JOptionPane.showMessageDialog(panel, 
                    "实时扫描器未启动", 
                    "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 判断当前模式
            boolean isRealtimeMode = realtimeModeRadio.isSelected();
            String dataSource = isRealtimeMode ? "Proxy实时流量" : "SiteMap历史流量";
            
            int[] selectedRows = collectedDataTable.getSelectedRows();
            
            String message;
            if (selectedRows.length > 0) {
                message = String.format(
                    "确定要对选中的 %d 个域名进行 Arjun 参数探测吗？\n\n" +
                    "数据源: %s\n" +
                    "去重机制: method+host+content-type+uri+已探测参数\n" +
                    "Arjun 将基于已收集的接口和参数进行增量探测。",
                    selectedRows.length,
                    dataSource
                );
            } else {
                message = String.format(
                    "确定要对所有已收集的域名进行 Arjun 参数探测吗？\n\n" +
                    "数据源: %s\n" +
                    "去重机制: method+host+content-type+uri+已探测参数\n" +
                    "Arjun 将基于已收集的接口和参数进行增量探测。",
                    dataSource
                );
            }
            
            int result = JOptionPane.showConfirmDialog(panel, 
                message,
                "确认 Arjun 探测",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
            
            // 更新UI状态
            arjunScanButton.setEnabled(false);
            statusLabel.setText("✨ 正在执行 Arjun 参数探测...");
            statusLabel.setForeground(new Color(155, 89, 182));
            progressBar.setIndeterminate(true);
            
            // 异步执行Arjun扫描
            CompletableFuture.runAsync(() -> {
                try {
                    // 🔴 优化1：根据模式选择不同的数据源
                    if (isRealtimeMode) {
                        // 实时监听模式：从Proxy实时流量触发（使用ParameterCollector中的数据）
                        api.logging().raiseInfoEvent("从Proxy实时流量触发Arjun探测");
                        activeScanner.getRealtimeScanner().triggerArjunScanFromProxy();
                    } else {
                        // 手动触发模式：从SiteMap历史流量触发
                        api.logging().raiseInfoEvent("从SiteMap历史流量触发Arjun探测");
                        activeScanner.getRealtimeScanner().triggerManualArjunScan();
                    }
                    
                    int currentScans = Integer.parseInt(
                        arjunScansLabel.getText().replaceAll("[^0-9]", ""));
                    SwingUtilities.invokeLater(() -> {
                        arjunScansLabel.setText("探测次数: " + (currentScans + 1));
                    });
                    
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("✅ Arjun 参数探测完成");
                        statusLabel.setForeground(new Color(46, 204, 113));
                        JOptionPane.showMessageDialog(panel,
                            "Arjun 参数探测已完成！\n请查看探测结果表格。",
                            "探测完成",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("❌ Arjun 探测失败");
                        statusLabel.setForeground(new Color(231, 76, 60));
                        JOptionPane.showMessageDialog(panel, 
                            "Arjun 参数探测执行失败:\n" + e.getMessage(), 
                            "错误", JOptionPane.ERROR_MESSAGE);
                    });
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        arjunScanButton.setEnabled(true);
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(100);
                    });
                }
            });
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(panel, 
                "启动 Arjun 参数探测时出错:\n" + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
            arjunScanButton.setEnabled(true);
            statusLabel.setText("❌ Arjun 探测失败");
        }
    }
    
    private void showAddTargetDialog() {
        JTextArea inputArea = new JTextArea(8, 40);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inputArea.setText("# 在此输入要手动添加的目标URL，每行一个\n" +
                         "# 系统会自动检查这些URL是否已经被探测过\n" +
                         "https://example.com/api/v1");
        
        int result = JOptionPane.showConfirmDialog(
            panel,
            new JScrollPane(inputArea),
            "手动添加探测目标",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            String text = inputArea.getText().trim();
            if (!text.isEmpty()) {
                addManualTargets(text);
            }
        }
    }
    
    /**
     * 添加手动目标并触发 Arjun 探测
     * 
     * 手动添加的端点特性：
     * 1. 会先检查是否已经被探测过（任意method和contentType组合）
     * 2. 如果未探测过，会尝试所有 method (GET/POST/PUT/DELETE/PATCH) x contentType (form/json/xml/multipart) 组合
     * 3. 使用增量去重机制：method + host + contentType + endpoint + 已探测参数
     */
    private void addManualTargets(String targetsText) {
        String[] lines = targetsText.split("\\r?\\n");
        List<String> validUrls = new java.util.ArrayList<>();
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#") && line.startsWith("http")) {
                validUrls.add(line);
            }
        }
        
        if (validUrls.isEmpty()) {
            JOptionPane.showMessageDialog(panel,
                "没有找到有效的URL",
                "提示",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 确认对话框
        String message = String.format(
            "找到 %d 个有效URL\n\n" +
            "手动添加的端点将尝试最常见的 3 种组合：\n" +
            "• GET + form（普通GET请求）\n" +
            "• POST + form（表单提交）\n" +
            "• POST + json（JSON API）\n\n" +
            "已探测过的组合会自动跳过。是否开始探测？",
            validUrls.size()
        );
        
        int result = JOptionPane.showConfirmDialog(
            panel,
            message,
            "确认手动探测",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        // 更新UI状态
        statusLabel.setText("✨ 正在对手动添加的端点执行 Arjun 探测...");
        statusLabel.setForeground(new Color(155, 89, 182));
        progressBar.setIndeterminate(true);
        
        // 异步执行扫描
        CompletableFuture.runAsync(() -> {
            int scannedCount = 0;
            int skippedCount = 0;
            
            for (String url : validUrls) {
                try {
                    // 调用新的手动端点扫描API
                    // 会自动检查是否已探测过，并尝试所有 method/contentType 组合
                    activeScanner.getRealtimeScanner().triggerManualEndpointScan(url);
                    scannedCount++;
                    
                    api.logging().raiseInfoEvent("已提交手动端点扫描: " + url);
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("手动端点扫描失败: " + url + " - " + e.getMessage());
                    skippedCount++;
                }
            }
            
            final int finalScanned = scannedCount;
            final int finalSkipped = skippedCount;
            
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("✅ 手动端点探测已提交");
                statusLabel.setForeground(new Color(46, 204, 113));
                progressBar.setIndeterminate(false);
                
                JOptionPane.showMessageDialog(panel,
                    String.format("手动端点Arjun探测已提交！\n\n" +
                                "成功: %d 个\n" +
                                "失败: %d 个\n\n" +
                                "提示：每个端点将尝试 3 种最常见的组合\n" +
                                "（GET+form, POST+form, POST+json）\n" +
                                "已探测过的组合会被自动跳过",
                                finalScanned, finalSkipped),
                    "探测已提交",
                    JOptionPane.INFORMATION_MESSAGE);
                
                // 刷新数据
                refreshCollectedData();
            });
        });
    }

    private void clearResults() {
        int result = JOptionPane.showConfirmDialog(
            panel,
            "确定要清空所有探测结果吗？\n（不会清空已收集的流量数据）",
            "确认清空",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            arjunResultTableModel.setRowCount(0);
            progressBar.setValue(0);
            statusLabel.setText("🟢 就绪 - 正在监听Burp流量...");
            statusLabel.setForeground(new Color(46, 204, 113));
            arjunResultsLabel.setText("发现参数: 0");
        }
    }

    /**
     * 从配置加载总开关状态
     */
    private void loadMasterSwitchState() {
        try {
            XProbeConfig config = configStorage.load();
            boolean enabled = config.isEnableActiveScan();
            
            // 设置开关状态
            masterEnableToggle.setSelected(enabled);
            updateToggleButtonAppearance(enabled);
            
            // 应用状态到UI组件
            applyMasterSwitchState(enabled);
            
            api.logging().logToOutput("主动探测总开关状态已加载: " + (enabled ? "启用" : "禁用"));
        } catch (Exception e) {
            api.logging().logToError("加载主动探测状态失败: " + e.getMessage());
            // 默认启用
            masterEnableToggle.setSelected(true);
            updateToggleButtonAppearance(true);
            applyMasterSwitchState(true);
        }
    }
    
    /**
     * 切换主动探测总开关
     */
    private void toggleMasterSwitch() {
        boolean enabled = masterEnableToggle.isSelected();
        updateToggleButtonAppearance(enabled);
        applyMasterSwitchState(enabled);
        
        // 保存状态到配置
        saveMasterSwitchState(enabled);
    }
    
    /**
     * 应用总开关状态到UI和后端
     */
    private void applyMasterSwitchState(boolean enabled) {
        if (enabled) {
            // 启用主动探测
            activeScanner.getRealtimeScanner().startRealtimeScanning();
            statusLabel.setText("🟢 主动探测已启用 - 正在监听流量...");
            statusLabel.setForeground(new Color(46, 204, 113));
            api.logging().logToOutput("主动探测已启用");
            
            // 启用所有控制组件
            arjunScanButton.setEnabled(true);
            addTargetButton.setEnabled(true);
            realtimeModeRadio.setEnabled(true);
            manualModeRadio.setEnabled(true);
        } else {
            // 禁用主动探测
            activeScanner.getRealtimeScanner().stopRealtimeScanning();
            
            // 停止实时监听定时器
            if (realtimeArjunTimer != null && realtimeArjunTimer.isRunning()) {
                realtimeArjunTimer.stop();
            }
            
            statusLabel.setText("⚫ 主动探测已禁用");
            statusLabel.setForeground(Color.GRAY);
            api.logging().logToOutput("主动探测已禁用");
            
            // 禁用所有控制组件
            arjunScanButton.setEnabled(false);
            addTargetButton.setEnabled(false);
            realtimeModeRadio.setEnabled(false);
            manualModeRadio.setEnabled(false);
        }
    }
    
    /**
     * 保存总开关状态到配置
     */
    private void saveMasterSwitchState(boolean enabled) {
        try {
            XProbeConfig config = configStorage.load();
            config.setEnableActiveScan(enabled);
            configStorage.save(config);
            api.logging().logToOutput("主动探测状态已保存: " + (enabled ? "启用" : "禁用"));
        } catch (Exception e) {
            api.logging().logToError("保存主动探测状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新总开关按钮的外观
     */
    private void updateToggleButtonAppearance(boolean enabled) {
        if (enabled) {
            masterEnableToggle.setText("✅ 主动探测已启用");
            masterEnableToggle.setBackground(new Color(46, 204, 113));
            masterEnableToggle.setForeground(Color.WHITE);
        } else {
            masterEnableToggle.setText("❌ 主动探测已禁用");
            masterEnableToggle.setBackground(new Color(231, 76, 60));
            masterEnableToggle.setForeground(Color.WHITE);
        }
        masterEnableToggle.setOpaque(true);
    }

    public Component getComponent() {
        return panel;
    }
    
    public ActiveScanner getActiveScanner() {
        return activeScanner;
    }
}

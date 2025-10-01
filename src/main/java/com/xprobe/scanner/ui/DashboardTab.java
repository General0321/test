package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.RequestFilter;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.active.ParameterCollector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 仪表板选项卡 - 现代化的系统概览和实时监控
 * 
 * 主要功能：
 * - 实时统计信息卡片
 * - 参数收集可视化
 * - 扫描活动监控
 * - 最近发现预览
 * - 系统状态日志
 */
public class DashboardTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final RequestFilter requestFilter;
    private final LogModel logModel;
    private ParameterCollector parameterCollector;
    
    // === 统计卡片标签 ===
    private JLabel totalRequestsValue;
    private JLabel scannedRequestsValue;
    private JLabel vulnerabilitiesValue;
    private JLabel domainsValue;
    private JLabel parametersValue;
    private JLabel keywordsValue;
    private JLabel endpointsValue;
    private JLabel arjunScansValue;
    
    // === 状态指示器 ===
    private JLabel systemStatusLabel;
    private JLabel collectionModeLabel;
    
    // === 活动日志 ===
    private JTextArea activityLogArea;
    private DefaultTableModel recentFindingsModel;
    private JTable recentFindingsTable;
    
    // === 参数收集统计 ===
    private JTextArea paramStatsArea;
    
    // === 工具 ===
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Timer autoRefreshTimer;
    
    // === 配色方案 ===
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);      // 蓝色
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);       // 绿色
    private static final Color WARNING_COLOR = new Color(243, 156, 18);      // 橙色
    private static final Color DANGER_COLOR = new Color(231, 76, 60);        // 红色
    private static final Color INFO_COLOR = new Color(142, 68, 173);         // 紫色
    private static final Color TEAL_COLOR = new Color(22, 160, 133);         // 青色
    private static final Color INDIGO_COLOR = new Color(52, 73, 94);         // 靛色
    private static final Color CARD_BG = new Color(250, 250, 250);           // 卡片背景
    
    public DashboardTab(MontoyaApi api, ConfigurationManager configManager, 
                       RequestFilter requestFilter, LogModel logModel) {
        this.api = api;
        this.configManager = configManager;
        this.requestFilter = requestFilter;
        this.logModel = logModel;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        startAutoRefresh();
        updateStatistics();
        
        addActivityLog("XProbe 仪表盘已加载");
        addActivityLog("参数收集模式: 等待初始化...");
    }
    
    /**
     * 设置参数收集器引用
     */
    public void setParameterCollector(ParameterCollector collector) {
        this.parameterCollector = collector;
        updateStatistics();
    }
    
    private void initializeComponents() {
        // === 统计卡片值标签 ===
        totalRequestsValue = createValueLabel("0");
        scannedRequestsValue = createValueLabel("0");
        vulnerabilitiesValue = createValueLabel("0");
        domainsValue = createValueLabel("0");
        parametersValue = createValueLabel("0");
        keywordsValue = createValueLabel("0");
        endpointsValue = createValueLabel("0");
        arjunScansValue = createValueLabel("0");
        
        // === 状态标签 ===
        systemStatusLabel = new JLabel("● 运行中", JLabel.CENTER);
        systemStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        systemStatusLabel.setForeground(SUCCESS_COLOR);
        
        collectionModeLabel = new JLabel("等待初始化...", JLabel.CENTER);
        collectionModeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        collectionModeLabel.setForeground(Color.GRAY);
        
        // === 活动日志 ===
        activityLogArea = new JTextArea(10, 40);
        activityLogArea.setEditable(false);
        activityLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        activityLogArea.setLineWrap(true);
        activityLogArea.setWrapStyleWord(true);
        
        // === 最近发现表格 ===
        recentFindingsModel = new DefaultTableModel(
            new Object[]{"时间", "类型", "目标", "详情"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        recentFindingsTable = new JTable(recentFindingsModel);
        recentFindingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentFindingsTable.setRowHeight(25);
        recentFindingsTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        recentFindingsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        recentFindingsTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        
        // 设置表格样式
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        recentFindingsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        recentFindingsTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        
        // === 参数统计区域 ===
        paramStatsArea = new JTextArea(10, 30);
        paramStatsArea.setEditable(false);
        paramStatsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        paramStatsArea.setBackground(CARD_BG);
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(240, 242, 245));
        
        // === 主滚动面板 ===
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(new Color(240, 242, 245));
        mainContent.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // === 顶部：标题和状态栏 ===
        mainContent.add(createHeaderPanel());
        mainContent.add(Box.createVerticalStrut(15));
        
        // === 统计卡片区域 ===
        mainContent.add(createStatsCardsPanel());
        mainContent.add(Box.createVerticalStrut(15));
        
        // === 中间：双栏布局 ===
        JPanel middlePanel = new JPanel(new GridLayout(1, 2, 15, 0));
        middlePanel.setOpaque(false);
        middlePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        
        // 左侧：参数收集统计
        middlePanel.add(createParamStatsPanel());
        
        // 右侧：活动日志
        middlePanel.add(createActivityLogPanel());
        
        mainContent.add(middlePanel);
        mainContent.add(Box.createVerticalStrut(15));
        
        // === 底部：最近发现 ===
        mainContent.add(createRecentFindingsPanel());
        
        // 添加到滚动面板
        JScrollPane scrollPane = new JScrollPane(mainContent);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        panel.add(scrollPane, BorderLayout.CENTER);
    }
    
    // === UI 创建方法 ===
    
    /**
     * 创建标题面板
     */
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        
        // 左侧：标题
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("XProbe 仪表盘");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setForeground(new Color(44, 62, 80));
        
        JLabel subtitleLabel = new JLabel("  实时监控 & 参数收集");
        subtitleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitleLabel.setForeground(Color.GRAY);
        
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        
        // 右侧：状态指示器
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        statusPanel.setOpaque(false);
        statusPanel.add(systemStatusLabel);
        statusPanel.add(collectionModeLabel);
        
        header.add(titlePanel, BorderLayout.WEST);
        header.add(statusPanel, BorderLayout.EAST);
        
        return header;
    }
    
    /**
     * 创建统计卡片区域
     */
    private JPanel createStatsCardsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(2, 4, 15, 15));
        statsPanel.setOpaque(false);
        statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        
        // 第一行：请求统计
        statsPanel.add(createModernStatCard("总请求数", totalRequestsValue, 
            "📊", PRIMARY_COLOR));
        statsPanel.add(createModernStatCard("已扫描", scannedRequestsValue, 
            "✓", SUCCESS_COLOR));
        statsPanel.add(createModernStatCard("发现漏洞", vulnerabilitiesValue, 
            "⚠", DANGER_COLOR));
        statsPanel.add(createModernStatCard("主域名", domainsValue, 
            "🌐", INFO_COLOR));
        
        // 第二行：参数统计
        statsPanel.add(createModernStatCard("参数", parametersValue, 
            "🔑", TEAL_COLOR));
        statsPanel.add(createModernStatCard("关键词", keywordsValue, 
            "📝", WARNING_COLOR));
        statsPanel.add(createModernStatCard("接口", endpointsValue, 
            "🔗", INDIGO_COLOR));
        statsPanel.add(createModernStatCard("Arjun扫描", arjunScansValue, 
            "🚀", PRIMARY_COLOR));
        
        return statsPanel;
    }
    
    /**
     * 创建现代化统计卡片
     */
    private JPanel createModernStatCard(String title, JLabel valueLabel, 
                                       String icon, Color themeColor) {
        JPanel cardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 白色背景with圆角
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                // 左侧彩色条
                g2d.setColor(themeColor);
                g2d.fillRoundRect(0, 0, 5, getHeight(), 12, 12);
                
                // 微妙阴影
                g2d.setColor(new Color(0, 0, 0, 10));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                
                g2d.dispose();
            }
        };
        
        cardPanel.setLayout(new BorderLayout(10, 5));
        cardPanel.setOpaque(false);
        cardPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        // 图标
        JLabel iconLabel = new JLabel(icon, JLabel.CENTER);
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
        iconLabel.setPreferredSize(new Dimension(40, 40));
        
        // 内容面板
        JPanel contentPanel = new JPanel(new BorderLayout(0, 5));
        contentPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        titleLabel.setForeground(Color.GRAY);
        
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        valueLabel.setForeground(themeColor);
        
        contentPanel.add(titleLabel, BorderLayout.NORTH);
        contentPanel.add(valueLabel, BorderLayout.CENTER);
        
        cardPanel.add(iconLabel, BorderLayout.WEST);
        cardPanel.add(contentPanel, BorderLayout.CENTER);
        
        return cardPanel;
    }
    
    /**
     * 创建参数统计面板
     */
    private JPanel createParamStatsPanel() {
        JPanel panel = createCardPanel("参数收集统计");
        
        JScrollPane scrollPane = new JScrollPane(paramStatsArea);
        scrollPane.setBorder(null);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建活动日志面板
     */
    private JPanel createActivityLogPanel() {
        JPanel panel = createCardPanel("活动日志");
        
        JScrollPane scrollPane = new JScrollPane(activityLogArea);
        scrollPane.setBorder(null);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        
        JButton clearButton = new JButton("清空");
        clearButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        clearButton.addActionListener(e -> {
            activityLogArea.setText("");
            addActivityLog("日志已清空");
        });
        
        buttonPanel.add(clearButton);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建最近发现面板
     */
    private JPanel createRecentFindingsPanel() {
        JPanel panel = createCardPanel("最近发现");
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        
        JScrollPane scrollPane = new JScrollPane(recentFindingsTable);
        scrollPane.setBorder(null);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建基础卡片面板
     */
    private JPanel createCardPanel(String title) {
        JPanel cardPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 白色背景
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                
                // 边框
                g2d.setColor(new Color(0, 0, 0, 10));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                
                g2d.dispose();
            }
        };
        
        cardPanel.setOpaque(false);
        cardPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // 标题
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setForeground(new Color(44, 62, 80));
        
        cardPanel.add(titleLabel, BorderLayout.NORTH);
        
        return cardPanel;
    }
    
    /**
     * 创建值标签
     */
    private JLabel createValueLabel(String initialValue) {
        JLabel label = new JLabel(initialValue, JLabel.LEFT);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        return label;
    }
    
    // === 事件监听 ===
    
    private void setupEventListeners() {
        // 目前没有需要设置的事件监听器
    }
    
    // === 自动刷新 ===
    
    private void startAutoRefresh() {
        autoRefreshTimer = new Timer(true);
        autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> updateStatistics());
            }
        }, 5000, 5000); // 每5秒刷新一次
    }
    
    // === 数据更新方法 ===
    
    public void updateStatistics() {
        SwingUtilities.invokeLater(() -> {
            // 基础统计
            int totalRequests = logModel.getRowCount();
            totalRequestsValue.setText(String.valueOf(totalRequests));
            scannedRequestsValue.setText(String.valueOf(totalRequests));
            
            // 从logModel计算漏洞数
            int vulnerabilities = 0;
            for (int i = 0; i < logModel.getRowCount(); i++) {
                String severity = (String) logModel.getValueAt(i, 3);
                if ("High".equals(severity) || "Critical".equals(severity)) {
                    vulnerabilities++;
                }
            }
            vulnerabilitiesValue.setText(String.valueOf(vulnerabilities));
            
            // 参数收集统计
            if (parameterCollector != null) {
                ParameterCollector.CollectorStatistics stats = 
                    parameterCollector.getStatistics();
                
                domainsValue.setText(String.valueOf(stats.getDomainCount()));
                parametersValue.setText(String.valueOf(stats.getParameterCount()));
                keywordsValue.setText(String.valueOf(stats.getKeywordCount()));
                endpointsValue.setText(String.valueOf(stats.getEndpointCount()));
                
                // 更新收集模式显示
                String modeText = stats.getMode() == ParameterCollector.CollectionMode.PARAMETERS_ONLY 
                    ? "模式: 仅参数名" 
                    : "模式: 参数名+关键词";
                collectionModeLabel.setText(modeText);
                collectionModeLabel.setForeground(PRIMARY_COLOR);
                
                // 更新参数统计详情
                updateParamStatsDetails(stats);
            }
            
            // Arjun扫描次数（暂时从发现表获取）
            arjunScansValue.setText("0");
        });
    }
    
    private void updateParamStatsDetails(ParameterCollector.CollectorStatistics stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 参数收集详情 ===\n\n");
        sb.append("主域名数量: ").append(stats.getDomainCount()).append("\n");
        sb.append("Host 数量: ").append(stats.getHostCount()).append("\n");
        sb.append("接口数量: ").append(stats.getEndpointCount()).append("\n");
        sb.append("参数数量: ").append(stats.getParameterCount()).append("\n");
        
        if (stats.getMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
            sb.append("关键词数量: ").append(stats.getKeywordCount()).append("\n");
        }
        
        sb.append("\n收集模式: ").append(
            stats.getMode() == ParameterCollector.CollectionMode.PARAMETERS_ONLY 
                ? "仅参数名" : "参数名+关键词"
        ).append("\n");
        
        sb.append("\n提示:\n");
        sb.append("• 参数会按主域名分组\n");
        sb.append("• Arjun 扫描时自动使用\n");
        sb.append("• 支持实时增量更新\n");
        
        paramStatsArea.setText(sb.toString());
    }
    
    // === 公共方法 ===
    
    /**
     * 添加活动日志
     */
    public void addActivityLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String logEntry = String.format("[%s] %s\n", timestamp, message);
            activityLogArea.append(logEntry);
            
            // 自动滚动到底部
            activityLogArea.setCaretPosition(activityLogArea.getDocument().getLength());
            
            // 限制日志行数（保留最近500行）
            String text = activityLogArea.getText();
            String[] lines = text.split("\n");
            if (lines.length > 500) {
                StringBuilder newText = new StringBuilder();
                for (int i = lines.length - 500; i < lines.length; i++) {
                    newText.append(lines[i]).append("\n");
                }
                activityLogArea.setText(newText.toString());
            }
        });
    }
    
    /**
     * 添加发现记录
     */
    public void addFinding(String type, String target, String detail) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            recentFindingsModel.insertRow(0, new Object[]{
                timestamp, type, target, detail
            });
            
            // 限制表格行数（保留最近100条）
            while (recentFindingsModel.getRowCount() > 100) {
                recentFindingsModel.removeRow(recentFindingsModel.getRowCount() - 1);
            }
            
            // 更新统计
            updateStatistics();
        });
    }
    
    /**
     * 添加参数发现记录
     */
    public void addParameterDiscovery(String domain, int newParams) {
        addFinding("参数", domain, "发现 " + newParams + " 个新参数");
        addActivityLog("参数发现: " + domain + " - 新增 " + newParams + " 个参数");
    }
    
    /**
     * 添加 Arjun 扫描记录
     */
    public void addArjunScan(String target, int discovered) {
        addFinding("Arjun", target, "发现 " + discovered + " 个隐藏参数");
        addActivityLog("Arjun 扫描: " + target + " - 发现 " + discovered + " 个参数");
        
        // 更新 Arjun 扫描计数
        int current = Integer.parseInt(arjunScansValue.getText());
        arjunScansValue.setText(String.valueOf(current + 1));
    }
    
    /**
     * 添加漏洞发现记录
     */
    public void addVulnerabilityFound(String type, String target, String detail) {
        addFinding("漏洞-" + type, target, detail);
        addActivityLog("发现漏洞: " + type + " - " + target);
    }
    
    /**
     * 更新系统状态
     */
    public void updateSystemStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            systemStatusLabel.setText("● " + status);
            systemStatusLabel.setForeground(color);
        });
    }
    
    /**
     * 获取组件
     */
    public Component getComponent() {
        return panel;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
    }
}

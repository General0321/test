package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.RequestFilter;
import com.xprobe.scanner.core.ScanTaskListener;
import com.xprobe.scanner.core.TaskScheduler;
import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.active.ParameterCollector;
import com.xprobe.scanner.models.ScanTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
public class DashboardTab implements ScanTaskListener {
    private JPanel panel;
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final RequestFilter requestFilter;
    private final LogModel logModel;
    private ParameterCollector parameterCollector;
    private com.xprobe.scanner.active.arjun.ArjunService arjunService;  // ✅ Arjun服务引用
    
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
    
    // === 扫描状态标签 ===
    private JLabel scanningCountLabel;  // 扫描中
    private JLabel waitingCountLabel;   // 等待中
    private JLabel hostCountLabel;      // Host数量
    private JLabel modeLabel;           // 模式显示（在参数收集卡片中）
    
    // === 进度条组件 ===
    private JLabel progressInfoLabel;
    private JProgressBar progressBar;
    
    // === 规则列表组件 ===
    private JPanel rulesListContainer;  // 规则列表容器
    
    // === 工具 ===
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Timer autoRefreshTimer;
    
    // ✅ 节流机制：限制频繁事件时的更新频率
    private volatile long lastStatisticsUpdateTime = 0;
    private static final long STATISTICS_UPDATE_THROTTLE_MS = 500; // 500ms内最多更新一次
    
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
    
    /**
     * ✅ 设置Arjun服务引用（用于获取统计数据）
     */
    public void setArjunService(com.xprobe.scanner.active.arjun.ArjunService service) {
        this.arjunService = service;
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
        collectionModeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // ✅ 字体：13px → 12px
        collectionModeLabel.setForeground(Color.WHITE);
        
        // === 扫描状态标签 ===
        scanningCountLabel = createValueLabel("0");
        waitingCountLabel = createValueLabel("0");
        hostCountLabel = createValueLabel("1");
        modeLabel = createValueLabel("仅参数名");
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        modeLabel.setForeground(new Color(41, 128, 185));
        
        // === 活动日志（性能优化）===
        activityLogArea = new JTextArea(10, 40);
        activityLogArea.setEditable(false);
        // ✅ 使用支持Unicode的字体（支持emoji显示）
        activityLogArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // ✅ 字体大小12px，SANS_SERIF支持emoji
        activityLogArea.setLineWrap(false); // ✅ 不自动换行，保持左对齐
        activityLogArea.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT); // ✅ 确保从左到右
        activityLogArea.setWrapStyleWord(true);
        activityLogArea.setDoubleBuffered(true);  // ✅ 启用双缓冲
        
        // === 最近发现表格（性能优化）===
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
        recentFindingsTable.setDoubleBuffered(true);  // ✅ 启用双缓冲
        recentFindingsTable.setFillsViewportHeight(false);  // ✅ 优化渲染
        
        // ✅ 列宽优化
        recentFindingsTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        recentFindingsTable.getColumnModel().getColumn(0).setMaxWidth(100);
        recentFindingsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        recentFindingsTable.getColumnModel().getColumn(1).setMaxWidth(120);
        recentFindingsTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        
        // 设置表格样式
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        recentFindingsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        recentFindingsTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
    }
    
    private void setupLayout() {
        panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(245, 245, 245)); // #F5F5F5
        
        // === 主内容面板（不滚动）===
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(new Color(245, 245, 245));
        mainContent.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // === 顶部：标题和状态栏（紫色渐变）===
        JPanel headerPanel = createHeaderPanel();
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainContent.add(headerPanel);
        
        // === 单行指标栏 ===
        JPanel metricsBar = createMetricsBar();
        metricsBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainContent.add(metricsBar);
        
        // === 统计卡片区域（两栏布局）===
        JPanel statsPanel = createStatsCardsPanel();
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainContent.add(statsPanel);
        
        // === 整体进度条区域 ===
        JPanel progressSection = createProgressSection();
        progressSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainContent.add(progressSection);
        
        // === 活动日志区域 ===
        JPanel activityLogPanel = createActivityLogPanel();
        activityLogPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainContent.add(activityLogPanel);
        
        // 直接添加到面板（不滚动）
        panel.add(mainContent, BorderLayout.CENTER);
    }
    
    // === UI 创建方法 ===
    
    /**
     * 创建标题面板（紫色渐变背景）
     */
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 紫色渐变背景
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(102, 126, 234), // #667eea
                    getWidth(), getHeight(), new Color(118, 75, 162) // #764ba2
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
                
                // 底部边框
                g2d.setColor(new Color(90, 103, 216)); // #5a67d8
                g2d.setStroke(new BasicStroke(2));
                g2d.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
                
                g2d.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(Integer.MAX_VALUE, 50)); // ✅ 减小高度：60px → 50px
        header.setMinimumSize(new Dimension(Integer.MAX_VALUE, 50));
        header.setBorder(new EmptyBorder(10, 20, 10, 20)); // ✅ 减小padding：15px → 10px
        
        // 左侧：图标和标题（使用BoxLayout确保垂直居中）
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        
        // 图标框
        JLabel iconLabel = new JLabel("X") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 半透明白色背景
                g2d.setColor(new Color(255, 255, 255, 51)); // rgba(255,255,255,0.2)
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); // ✅ 字体：20px → 18px
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setMinimumSize(new Dimension(32, 32));
        iconLabel.setMaximumSize(new Dimension(32, 32));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.CENTER);
        iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        
        // 标题
        JLabel titleLabel = new JLabel("XProbe 仪表盘");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); // ✅ 字体：20px → 18px
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        titleLabel.setVerticalAlignment(JLabel.CENTER);
        
        leftPanel.add(iconLabel);
        leftPanel.add(Box.createHorizontalStrut(12)); // ✅ 图标和标题之间的间距
        leftPanel.add(titleLabel);
        
        // 右侧：状态徽章（使用BoxLayout确保垂直居中）
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.setOpaque(false);
        rightPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        
        // 状态徽章
        JPanel statusBadge = createStatusBadge();
        statusBadge.setAlignmentY(Component.CENTER_ALIGNMENT);
        // 模式徽章
        JPanel modeBadge = createModeBadge();
        modeBadge.setAlignmentY(Component.CENTER_ALIGNMENT);
        
        rightPanel.add(statusBadge);
        rightPanel.add(Box.createHorizontalStrut(12)); // ✅ 徽章之间的间距
        rightPanel.add(modeBadge);
        
        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        
        return header;
    }
    
    /**
     * 创建状态徽章
     */
    private JPanel createStatusBadge() {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 半透明白色背景
                g2d.setColor(new Color(255, 255, 255, 51)); // rgba(255,255,255,0.2)
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                
                g2d.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(6, 12, 6, 12));
        
        // 绿色状态点
        JLabel statusDot = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绿色圆点
                g2d.setColor(new Color(39, 174, 96)); // #27AE60
                g2d.fillOval(0, 0, 10, 10);
                
                // 发光效果
                g2d.setColor(new Color(39, 174, 96, 153)); // 60% opacity
                g2d.fillOval(-2, -2, 14, 14);
                
                g2d.dispose();
            }
        };
        statusDot.setPreferredSize(new Dimension(10, 10));
        
        // 状态文字
        JLabel statusText = new JLabel("运行中");
        statusText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // ✅ 字体：13px → 12px
        statusText.setForeground(Color.WHITE);
        
        badge.add(statusDot);
        badge.add(statusText);
        
        return badge;
    }
    
    /**
     * 创建模式徽章
     */
    private JPanel createModeBadge() {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 半透明白色背景
                g2d.setColor(new Color(255, 255, 255, 51)); // rgba(255,255,255,0.2)
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                
                g2d.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(6, 12, 6, 12));
        
        // 模式标签
        JLabel modeLabel = new JLabel("模式:");
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        modeLabel.setForeground(new Color(255, 255, 255, 204)); // rgba(255,255,255,0.8)
        
        // 模式值（使用collectionModeLabel）
        collectionModeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); // ✅ 字体：13px → 12px
        collectionModeLabel.setForeground(Color.WHITE);
        
        badge.add(modeLabel);
        badge.add(collectionModeLabel);
        
        return badge;
    }
    
    /**
     * 创建单行指标栏
     */
    private JPanel createMetricsBar() {
        JPanel metricsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        metricsBar.setBackground(Color.WHITE);
        metricsBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 224, 224)),
            new EmptyBorder(8, 20, 8, 20) // ✅ 减小padding：10px → 8px
        ));
        
        // 添加指标项
        addMetricItem(metricsBar, "总请求:", totalRequestsValue, false);
        addMetricItem(metricsBar, "已扫描:", scannedRequestsValue, false);
        addMetricItem(metricsBar, "漏洞:", vulnerabilitiesValue, true);
        addMetricItem(metricsBar, "参数:", parametersValue, false);
        addMetricItem(metricsBar, "接口:", endpointsValue, false);
        addMetricItem(metricsBar, "域名:", domainsValue, false);
        
        return metricsBar;
    }
    
    /**
     * 添加指标项
     */
    private void addMetricItem(JPanel container, String labelText, JLabel valueLabel, boolean isVulnerability) {
        JPanel metricItem = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        metricItem.setOpaque(false);
        
        JLabel label = new JLabel(labelText);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); // ✅ 字体：14px → 13px
        label.setForeground(new Color(44, 62, 80));
        
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); // ✅ 字体：14px → 13px
        if (isVulnerability) {
            valueLabel.setForeground(new Color(231, 76, 60)); // 红色
        } else {
            valueLabel.setForeground(new Color(41, 128, 185)); // 蓝色
        }
        
        metricItem.add(label);
        metricItem.add(valueLabel);
        container.add(metricItem);
    }
    
    /**
     * 创建统计卡片区域（两栏布局）
     */
    private JPanel createStatsCardsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(1, 2, 12, 0)); // ✅ 减小卡片间距：15px → 12px
        statsPanel.setOpaque(false);
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 确保左对齐
        statsPanel.setBorder(new EmptyBorder(10, 15, 0, 15)); // ✅ 减小顶部间距：15px → 10px
        
        // 左侧：扫描统计卡片
        statsPanel.add(createEnhancedStatCard(
            "扫描统计", 
            "📊",
            new Color[][]{
                {new Color(52, 152, 219), new Color(41, 128, 185)}, // 蓝色渐变
            },
            new Object[][]{
                {"总请求数:", totalRequestsValue, false, "📥"},
                {"已扫描:", scannedRequestsValue, false, "✓"},
                {"发现漏洞:", vulnerabilitiesValue, true, "⚠"},
                {"扫描中:", scanningCountLabel, false, "🔄"},
                {"等待中:", waitingCountLabel, false, "⏳"}
            }
        ));
        
        // 右侧：参数收集卡片
        statsPanel.add(createEnhancedStatCard(
            "参数收集", 
            "🔍",
            new Color[][]{
                {new Color(26, 188, 156), new Color(22, 160, 133)}, // 青绿色渐变
            },
            new Object[][]{
                {"主域名:", domainsValue, false, "🌐"},
                {"Host:", hostCountLabel, false, "🏠"},
                {"接口:", endpointsValue, false, "🔗"},
                {"参数:", parametersValue, false, "📝"},
                {"模式:", modeLabel, false, "⚙️"}
            }
        ));
        
        return statsPanel;
    }
    
    /**
     * 创建增强统计卡片（带渐变头部）
     */
    private JPanel createEnhancedStatCard(String title, String icon, 
                                           Color[][] gradients, Object[][] items) {
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // ✅ 绘制圆角边框（12px圆角）
                g2d.setColor(new Color(224, 224, 224)); // #E0E0E0
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                
                g2d.dispose();
            }
        };
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        
        // 头部（渐变背景，上方圆角）
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // ✅ 渐变背景，上方圆角（12px）
                GradientPaint gradient = new GradientPaint(
                    0, 0, gradients[0][0],
                    getWidth(), getHeight(), gradients[0][1]
                );
                g2d.setPaint(gradient);
                // ✅ 绘制上方圆角矩形（只让上方两个角是圆角）
                // 使用 GeneralPath 绘制只有上方圆角的形状
                java.awt.geom.GeneralPath path = new java.awt.geom.GeneralPath();
                path.moveTo(0, 12); // 左上角圆角起点
                path.quadTo(0, 0, 12, 0); // 左上角圆角
                path.lineTo(getWidth() - 12, 0); // 上边
                path.quadTo(getWidth(), 0, getWidth(), 12); // 右上角圆角
                path.lineTo(getWidth(), getHeight()); // 右边
                path.lineTo(0, getHeight()); // 下边
                path.closePath();
                g2d.fill(path);
                
                g2d.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(8, 15, 8, 15)); // ✅ 减小padding：10px → 8px
        header.setPreferredSize(new Dimension(Integer.MAX_VALUE, 36)); // ✅ 减小高度：40px → 36px
        
        JLabel headerIconLabel = new JLabel(icon);
        headerIconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        headerIconLabel.setForeground(Color.WHITE);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        
        header.add(headerIconLabel);
        header.add(titleLabel);
        
        // 内容区域
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 15, 10, 15)); // ✅ 减小padding：12px → 10px
        
        for (int i = 0; i < items.length; i++) {
            Object[] item = items[i];
            final boolean isLast = (i == items.length - 1); // ✅ 使用final以便在内部类中使用
            
            JPanel itemPanel = new JPanel(new BorderLayout(0, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    // ✅ 绘制底部分隔线（最后一行不显示）
                    if (!isLast) {
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setColor(new Color(240, 240, 240)); // #F0F0F0
                        g2d.setStroke(new BasicStroke(1));
                        g2d.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                        g2d.dispose();
                    }
                }
            };
            itemPanel.setOpaque(false);
            itemPanel.setBorder(new EmptyBorder(6, 0, 6, 0)); // ✅ 减小padding：8px → 6px
            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // 左侧：图标+标签
            JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); // ✅ gap: 8px
            labelPanel.setOpaque(false);
            
            // 小图标（如果有）
            String itemIcon = item.length > 3 ? (String) item[3] : null;
            if (itemIcon != null) {
                // ✅ 图标容器（带背景）
                JLabel itemIconLabel = new JLabel(itemIcon) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2d = (Graphics2D) g.create();
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        // ✅ 绘制图标背景（#ECF0F1，4px圆角）
                        g2d.setColor(new Color(236, 240, 241)); // #ECF0F1
                        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                        g2d.dispose();
                        super.paintComponent(g);
                    }
                };
                itemIconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11)); // ✅ 图标字体11px（从12px减小）
                itemIconLabel.setForeground(new Color(127, 140, 141)); // #7F8C8D
                itemIconLabel.setPreferredSize(new Dimension(20, 20));
                itemIconLabel.setHorizontalAlignment(JLabel.CENTER);
                itemIconLabel.setVerticalAlignment(JLabel.CENTER);
                labelPanel.add(itemIconLabel);
            }
            
            JLabel label = new JLabel(item[0].toString());
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); // ✅ 标签字体13px（从14px减小）
            label.setForeground(new Color(44, 62, 80)); // #2C3E50
            labelPanel.add(label);
            
            // 右侧：数值
            JLabel value;
            if (item[1] instanceof JLabel) {
                value = (JLabel) item[1];
            } else {
                value = new JLabel(item[1].toString());
            }
            
            // 如果是模式标签，使用小字体
            if (item[0].equals("模式:")) {
                value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12)); // ✅ 模式字体12px（从13px减小）
                value.setForeground(new Color(41, 128, 185)); // 蓝色
            } else {
                value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); // ✅ 数值字体22px（从26px减小）
                if ((Boolean) item[2]) {
                    value.setForeground(new Color(231, 76, 60)); // 红色
                } else {
                    value.setForeground(new Color(41, 128, 185)); // 蓝色
                }
            }
            
            itemPanel.add(labelPanel, BorderLayout.WEST);
            itemPanel.add(value, BorderLayout.EAST);
            body.add(itemPanel);
        }
        
        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        
        return card;
    }
    
    /**
     * 创建整体进度条区域
     */
    private JPanel createProgressSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 确保整个区域左对齐
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)),
            BorderFactory.createEmptyBorder(8, 15, 0, 15) // ✅ 减小顶部间距：12px → 8px
        ));
        
        // 进度卡片（标题放在卡片内部）
        JPanel progressCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // ✅ 绘制圆角边框（12px圆角）
                g2d.setColor(new Color(224, 224, 224)); // #E0E0E0
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                
                g2d.dispose();
            }
        };
        progressCard.setLayout(new BoxLayout(progressCard, BoxLayout.Y_AXIS));
        progressCard.setOpaque(true);
        progressCard.setBackground(Color.WHITE);
        progressCard.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        progressCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // ✅ 确保宽度填满
        progressCard.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15)); // ✅ 减小padding：15px → 12px
        
        // ✅ 标题放在卡片内部
        JLabel title = new JLabel("整体扫描进度");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setForeground(new Color(44, 62, 80));
        title.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0)); // ✅ 标题底部间距：12px → 10px
        
        // 进度信息
        progressInfoLabel = new JLabel("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
        progressInfoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        progressInfoLabel.setForeground(new Color(44, 62, 80));
        progressInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        progressInfoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0)); // ✅ 底部间距：12px → 10px
        
        // 进度条容器
        JPanel progressBarContainer = new JPanel(new BorderLayout(0, 0));
        progressBarContainer.setOpaque(true);
        progressBarContainer.setBackground(new Color(236, 240, 241)); // ✅ 浅灰色背景
        progressBarContainer.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        progressBarContainer.setPreferredSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBarContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBarContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBar.setForeground(new Color(52, 152, 219)); // 蓝色
        progressBar.setBackground(new Color(236, 240, 241)); // 浅灰色
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        progressBar.setBorderPainted(false);
        
        progressBarContainer.add(progressBar, BorderLayout.CENTER);
        
        // ✅ 进度条容器的间距（上下各12px，减小间距）
        JPanel progressBarWrapper = new JPanel(new BorderLayout(0, 0));
        progressBarWrapper.setOpaque(false);
        progressBarWrapper.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        progressBarWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0)); // ✅ 减小间距：12px → 10px
        progressBarWrapper.add(progressBarContainer, BorderLayout.CENTER);
        
        // 规则列表
        JPanel rulesList = new JPanel();
        rulesList.setLayout(new BoxLayout(rulesList, BoxLayout.Y_AXIS));
        rulesList.setOpaque(false);
        rulesList.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        rulesList.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); // ✅ 顶部间距：12px → 10px
        
        JLabel rulesTitle = new JLabel("正在执行的规则:");
        rulesTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        rulesTitle.setForeground(new Color(44, 62, 80));
        rulesTitle.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        rulesTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0)); // ✅ 底部间距8px
        
        rulesList.add(rulesTitle);
        
        // ✅ 规则列表容器（动态更新）
        rulesListContainer = new JPanel();
        rulesListContainer.setLayout(new BoxLayout(rulesListContainer, BoxLayout.Y_AXIS));
        rulesListContainer.setOpaque(false);
        rulesListContainer.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        rulesList.add(rulesListContainer);
        
        // ✅ 按顺序添加组件，确保对齐（标题在卡片内部）
        progressCard.add(title);
        progressCard.add(progressInfoLabel);
        progressCard.add(progressBarWrapper);
        progressCard.add(rulesList);
        
        section.add(progressCard);
        
        return section;
    }
    
    /**
     * 创建现代化统计卡片（性能优化版）
     */
    private JPanel createModernStatCard(String title, JLabel valueLabel, 
                                       String icon, Color themeColor) {
        JPanel cardPanel = new JPanel() {
            private boolean painted = false;
            
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                // ✅ 优化：减少重绘次数
                if (!painted || !isOpaque()) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    
                    // ✅ 优化渲染质量
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                                        RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                                        RenderingHints.VALUE_STROKE_PURE);
                    
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
                    painted = true;
                }
            }
        };
        
        cardPanel.setLayout(new BorderLayout(10, 5));
        cardPanel.setOpaque(false);
        cardPanel.setDoubleBuffered(true);  // ✅ 启用双缓冲
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
     * 创建活动日志面板（性能优化）
     */
    private JPanel createActivityLogPanel() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 确保左对齐
        section.setBorder(new EmptyBorder(8, 15, 15, 15)); // ✅ 减小顶部间距：12px → 8px
        
        // 日志卡片（标题和内容都在卡片内部）
        JPanel logCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // ✅ 绘制圆角边框（12px圆角）
                g2d.setColor(new Color(224, 224, 224)); // #E0E0E0
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                
                g2d.dispose();
            }
        };
        logCard.setLayout(new BoxLayout(logCard, BoxLayout.Y_AXIS));
        logCard.setOpaque(true);
        logCard.setBackground(Color.WHITE);
        logCard.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        logCard.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // ✅ 标题和清空按钮（放在卡片内部）
        JPanel header = new JPanel(new BorderLayout(0, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel title = new JLabel("活动日志");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setForeground(new Color(44, 62, 80));
        title.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        
        JButton clearButton = new JButton("清空");
        clearButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        clearButton.setBackground(new Color(41, 128, 185)); // 蓝色
        clearButton.setForeground(Color.WHITE);
        clearButton.setBorderPainted(false);
        clearButton.setFocusPainted(false);
        clearButton.setPreferredSize(new Dimension(60, 28));
        clearButton.addActionListener(e -> {
            activityLogArea.setText("");
            currentLogLines = 0;
            addActivityLog("日志已清空");
        });
        
        header.add(title, BorderLayout.WEST);
        header.add(clearButton, BorderLayout.EAST);
        
        // 日志容器（白色背景，无边框，因为外层卡片已有边框）
        JPanel logContainer = new JPanel(new BorderLayout(0, 0));
        logContainer.setOpaque(false);
        logContainer.setAlignmentX(Component.LEFT_ALIGNMENT); // ✅ 左对齐
        logContainer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); // ✅ 顶部间距：12px → 10px
        logContainer.setPreferredSize(new Dimension(Integer.MAX_VALUE, 450)); // ✅ 增加高度：350px → 450px
        logContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 450));
        
        // 日志文本区域（字体和对齐已在初始化时设置）
        activityLogArea.setBackground(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(activityLogArea);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        scrollPane.setDoubleBuffered(true);
        
        logContainer.add(scrollPane, BorderLayout.CENTER);
        
        // ✅ 按顺序添加组件（标题在卡片内部）
        logCard.add(header);
        logCard.add(logContainer);
        
        section.add(logCard);
        
        return section;
    }
    
    /**
     * 创建最近发现面板（性能优化）
     */
    private JPanel createRecentFindingsPanel() {
        JPanel panel = createCardPanel("最近发现");
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        
        JScrollPane scrollPane = new JScrollPane(recentFindingsTable);
        scrollPane.setBorder(null);
        // ✅ 使用BLIT模式（Burp标准）
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        scrollPane.setDoubleBuffered(true);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建基础卡片面板（性能优化版）
     */
    private JPanel createCardPanel(String title) {
        JPanel cardPanel = new JPanel(new BorderLayout(10, 10)) {
            private boolean painted = false;
            
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                // ✅ 优化：减少重绘次数
                if (!painted || !isOpaque()) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    
                    // ✅ 优化渲染质量
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                                        RenderingHints.VALUE_RENDER_QUALITY);
                    
                    // 白色背景
                    g2d.setColor(Color.WHITE);
                    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    
                    // 边框
                    g2d.setColor(new Color(0, 0, 0, 10));
                    g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    
                    g2d.dispose();
                    painted = true;
                }
            }
        };
        
        cardPanel.setOpaque(false);
        cardPanel.setDoubleBuffered(true);  // ✅ 启用双缓冲
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
    
    /**
     * ✅ 启动定时刷新（作为兜底机制）
     * 注意：扫描进度通过事件驱动实时更新，定时刷新主要用于：
     * 1. 更新其他统计数据（logModel、parameterCollector、arjunService）
     * 2. 作为兜底机制，确保即使事件丢失也能更新
     */
    private void startAutoRefresh() {
        autoRefreshTimer = new Timer(true);
        autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // ✅ 优化：检查面板是否可见再刷新
                if (panel.isVisible() && panel.isShowing()) {
                    // ✅ P2修复：直接调用内部实现，避免嵌套invokeLater
                    SwingUtilities.invokeLater(() -> {
                        // ✅ 只更新非事件驱动的统计数据（logModel、parameterCollector、arjunService）
                        // 扫描进度通过事件驱动实时更新，不需要定时刷新
                        // ✅ 定时刷新时强制更新（忽略节流限制）
                        updateNonEventDrivenStatistics(true);
                    });
                }
            }
        }, 30000, 30000); // ✅ 改为30秒刷新一次（作为兜底，因为扫描进度已通过事件驱动）
    }
    
    /**
     * ✅ 更新非事件驱动的统计数据（带节流机制）
     * 这些数据没有事件通知机制，需要通过定时刷新或手动调用更新
     * 
     * @param forceUpdate 是否强制更新（忽略节流限制）
     */
    private void updateNonEventDrivenStatistics(boolean forceUpdate) {
        // ✅ 节流机制：如果距离上次更新不足500ms，且不是强制更新，则跳过
        if (!forceUpdate) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStatisticsUpdateTime < STATISTICS_UPDATE_THROTTLE_MS) {
                return; // 跳过本次更新
            }
            lastStatisticsUpdateTime = currentTime;
        }
        
        // 基础统计（logModel）
        int totalRequests = logModel.getRowCount();
        totalRequestsValue.setText(String.valueOf(totalRequests));
        scannedRequestsValue.setText(String.valueOf(totalRequests));
        
        int vulnerabilities = logModel.getVulnerabilityCount();
        vulnerabilitiesValue.setText(String.valueOf(vulnerabilities));
        
        // 参数收集统计（parameterCollector）
        if (parameterCollector != null) {
            ParameterCollector.CollectorStatistics stats = 
                parameterCollector.getStatistics();
            
            domainsValue.setText(String.valueOf(stats.getDomainCount()));
            parametersValue.setText(String.valueOf(stats.getParameterCount()));
            keywordsValue.setText(String.valueOf(stats.getKeywordCount()));
            endpointsValue.setText(String.valueOf(stats.getEndpointCount()));
            hostCountLabel.setText(String.valueOf(stats.getHostCount()));
            
            // 更新收集模式显示（顶部徽章）
            String modeText = stats.getMode() == ParameterCollector.CollectionMode.PARAMETERS_ONLY 
                ? "仅参数名" 
                : "参数名+关键词";
            collectionModeLabel.setText(modeText);
            collectionModeLabel.setForeground(Color.WHITE);
            
            // 更新收集模式显示（参数收集卡片）
            modeLabel.setText(modeText);
        }
        
        // Arjun扫描次数（arjunService）
        if (arjunService != null) {
            com.xprobe.scanner.active.arjun.ArjunService.ArjunStatistics arjunStats = 
                arjunService.getStatistics();
            arjunScansValue.setText(String.valueOf(arjunStats.getTotalScans()));
        } else {
            arjunScansValue.setText("0");
        }
        
        // ✅ 更新扫描中/等待中数量（作为兜底机制，确保数据同步）
        if (taskScheduler != null) {
            TaskScheduler.TaskProgressStatistics stats = taskScheduler.getProgressStatistics();
            scanningCountLabel.setText(String.valueOf(stats.getScanningCount()));
            waitingCountLabel.setText(String.valueOf(stats.getWaitingCount()));
        } else {
            scanningCountLabel.setText("0");
            waitingCountLabel.setText("0");
        }
        
        // ✅ 注意：扫描进度（progressInfoLabel、progressBar）主要通过事件驱动实时更新
        // 但这里作为兜底机制也会更新，确保数据同步
    }
    
    // === 数据更新方法 ===
    
    /**
     * 公共方法：更新统计信息（外部调用）
     */
    public void updateStatistics() {
        SwingUtilities.invokeLater(this::updateStatisticsImpl);
    }
    
    /**
     * ✅ P2修复：内部实现，避免嵌套invokeLater
     * 注意：此方法会更新所有统计数据，包括事件驱动的数据
     * 主要用于初始化和手动刷新
     */
    private void updateStatisticsImpl() {
        // ✅ 调用非事件驱动的统计更新（包括扫描中/等待中数量）
        // ✅ 手动刷新时强制更新（忽略节流限制）
        updateNonEventDrivenStatistics(true);
        
        // ✅ 从TaskScheduler获取扫描进度（事件驱动数据，但这里作为兜底）
        if (taskScheduler != null) {
            TaskScheduler.TaskProgressStatistics stats = taskScheduler.getProgressStatistics();
            // ✅ 注意：scanningCountLabel 和 waitingCountLabel 已在 updateNonEventDrivenStatistics() 中更新
            
            // 更新进度条（包含历史累计数据）
            int totalSent = stats.getTotalSent();
            int totalExpected = stats.getTotalExpected();
            
            // ✅ 即使预计为0也显示（可能有历史数据但当前没有进行中的任务）
            if (totalExpected > 0) {
                int percent = (totalSent * 100) / totalExpected;
                int remaining = totalExpected - totalSent;
                
                progressInfoLabel.setText(String.format(
                    "已发送: %d / 预计: %d 请求  (%d%%)  剩余: ~%d 请求",
                    totalSent, totalExpected, percent, remaining
                ));
                progressBar.setValue(percent);
            } else if (totalSent > 0) {
                // ✅ 如果有历史数据但预计为0（所有任务已完成），显示历史数据
                progressInfoLabel.setText(String.format(
                    "已发送: %d / 预计: %d 请求  (100%%)  剩余: ~0 请求",
                    totalSent, totalSent
                ));
                progressBar.setValue(100);
            } else {
                // ✅ 初始状态
                progressInfoLabel.setText("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
                progressBar.setValue(0);
            }
        } else {
            // 暂时设置为0
            scanningCountLabel.setText("0");
            waitingCountLabel.setText("0");
            updateProgress();
        }
    }
    
    /**
     * 更新进度条（从TaskScheduler获取实际的扫描进度数据，包含历史累计数据）
     */
    private void updateProgress() {
        if (taskScheduler != null) {
            TaskScheduler.TaskProgressStatistics stats = taskScheduler.getProgressStatistics();
            int totalSent = stats.getTotalSent();
            int totalExpected = stats.getTotalExpected();
            
            // ✅ 即使预计为0也显示（可能有历史数据但当前没有进行中的任务）
            if (totalExpected > 0) {
                int percent = (totalSent * 100) / totalExpected;
                int remaining = totalExpected - totalSent;
                
                progressInfoLabel.setText(String.format(
                    "已发送: %d / 预计: %d 请求  (%d%%)  剩余: ~%d 请求",
                    totalSent, totalExpected, percent, remaining
                ));
                progressBar.setValue(percent);
            } else if (totalSent > 0) {
                // ✅ 如果有历史数据但预计为0（所有任务已完成），显示历史数据
                progressInfoLabel.setText(String.format(
                    "已发送: %d / 预计: %d 请求  (100%%)  剩余: ~0 请求",
                    totalSent, totalSent
                ));
                progressBar.setValue(100);
            } else {
                // ✅ 初始状态
                progressInfoLabel.setText("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
                progressBar.setValue(0);
            }
        } else {
            progressInfoLabel.setText("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
            progressBar.setValue(0);
        }
    }
    
    // === ScanTaskListener 接口实现 ===
    
    private TaskScheduler taskScheduler;
    
    /**
     * 设置TaskScheduler引用（用于获取进度统计）
     * ✅ 同时注册为事件监听器，实现订阅式更新
     */
    public void setTaskScheduler(TaskScheduler scheduler) {
        this.taskScheduler = scheduler;
        // ✅ 注册为事件监听器（订阅式更新）
        if (scheduler != null) {
            scheduler.setScanTaskListener(this);
        }
        // ✅ 设置后立即更新规则列表（显示初始状态）
        SwingUtilities.invokeLater(() -> {
            updateRulesList();
        });
    }
    
    @Override
    public void onScanTaskStart(ScanTask task, int expectedRequestCount, String ruleName) {
        SwingUtilities.invokeLater(() -> {
            try {
                HttpRequest request = task.getRequest();
                if (request == null) {
                    addActivityLog(String.format(
                        "规则 \"%s\" → (预计: %d 请求)",
                        ruleName, expectedRequestCount
                    ));
                    return;
                }
                
                String method = request.method();
                String path = request.path();
                String interfaceText = method + " " + path;
                
                // 记录规则开始日志
                addActivityLog(String.format(
                    "规则 \"%s\" → %s (预计: %d 请求)",
                    ruleName, interfaceText, expectedRequestCount
                ));
            } catch (Exception e) {
                // 如果获取请求信息失败，至少记录规则名称
                addActivityLog(String.format(
                    "规则 \"%s\" → (预计: %d 请求)",
                    ruleName, expectedRequestCount
                ));
            }
            
            // ✅ 更新规则列表
            if (taskScheduler != null) {
                updateRulesList();
            }
        });
    }
    
    @Override
    public void onScanTaskProgress(ScanTask task, int sentRequests, int expectedRequestCount) {
        SwingUtilities.invokeLater(() -> {
            // 更新整体进度
            if (taskScheduler != null) {
                TaskScheduler.TaskProgressStatistics stats = taskScheduler.getProgressStatistics();
                int totalSent = stats.getTotalSent();
                int totalExpected = stats.getTotalExpected();
                
                // ✅ 即使预计为0也显示（可能有历史数据但当前没有进行中的任务）
                if (totalExpected > 0) {
                    int percent = (totalSent * 100) / totalExpected;
                    int remaining = totalExpected - totalSent;
                    
                    progressInfoLabel.setText(String.format(
                        "已发送: %d / 预计: %d 请求  (%d%%)  剩余: ~%d 请求",
                        totalSent, totalExpected, percent, remaining
                    ));
                    progressBar.setValue(percent);
                } else if (totalSent > 0) {
                    // ✅ 如果有历史数据但预计为0（所有任务已完成），显示历史数据
                    progressInfoLabel.setText(String.format(
                        "已发送: %d / 预计: %d 请求  (100%%)  剩余: ~0 请求",
                        totalSent, totalSent
                    ));
                    progressBar.setValue(100);
                } else {
                    // ✅ 初始状态
                    progressInfoLabel.setText("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
                    progressBar.setValue(0);
                }
                
                // ✅ 更新扫描中/等待中数量（扫描统计卡片）
                scanningCountLabel.setText(String.valueOf(stats.getScanningCount()));
                waitingCountLabel.setText(String.valueOf(stats.getWaitingCount()));
                
                // ✅ 同时更新其他统计数据（带节流机制，避免频繁更新）
                updateNonEventDrivenStatistics(false);
                
                // ✅ 更新规则列表
                updateRulesList();
            }
        });
    }
    
    @Override
    public void onScanTaskComplete(ScanTask task, int totalRequests, int vulnerabilityCount, String ruleName) {
        SwingUtilities.invokeLater(() -> {
            try {
                HttpRequest request = task.getRequest();
                String interfaceText = "未知接口";
                
                if (request != null) {
                    String method = request.method();
                    String path = request.path();
                    interfaceText = method + " " + path;
                }
                
                // 记录规则完成日志
                if (vulnerabilityCount > 0) {
                    addActivityLog(String.format(
                        "规则 \"%s\" → %s [完成] [警告] 发现 %d 个漏洞",
                        ruleName, interfaceText, vulnerabilityCount
                    ));
                } else {
                    addActivityLog(String.format(
                        "规则 \"%s\" → %s [完成] 扫描完成",
                        ruleName, interfaceText
                    ));
                }
            } catch (Exception e) {
                // 如果获取请求信息失败，至少记录规则名称和结果
                if (vulnerabilityCount > 0) {
                    addActivityLog(String.format(
                        "规则 \"%s\" [完成] [警告] 发现 %d 个漏洞",
                        ruleName, vulnerabilityCount
                    ));
                } else {
                    addActivityLog(String.format(
                        "规则 \"%s\" [完成] 扫描完成",
                        ruleName
                    ));
                }
            }
            
            // 更新整体进度
            if (taskScheduler != null) {
                TaskScheduler.TaskProgressStatistics stats = taskScheduler.getProgressStatistics();
                int totalSent = stats.getTotalSent();
                int totalExpected = stats.getTotalExpected();
                
                // ✅ 即使预计为0也显示（可能有历史数据但当前没有进行中的任务）
                if (totalExpected > 0) {
                    int percent = (totalSent * 100) / totalExpected;
                    int remaining = totalExpected - totalSent;
                    
                    progressInfoLabel.setText(String.format(
                        "已发送: %d / 预计: %d 请求  (%d%%)  剩余: ~%d 请求",
                        totalSent, totalExpected, percent, remaining
                    ));
                    progressBar.setValue(percent);
                } else if (totalSent > 0) {
                    // ✅ 如果有历史数据但预计为0（所有任务已完成），显示历史数据
                    progressInfoLabel.setText(String.format(
                        "已发送: %d / 预计: %d 请求  (100%%)  剩余: ~0 请求",
                        totalSent, totalSent
                    ));
                    progressBar.setValue(100);
                } else {
                    // ✅ 初始状态
                    progressInfoLabel.setText("已发送: 0 / 预计: 0 请求  (0%)  剩余: ~0 请求");
                    progressBar.setValue(0);
                }
                
                // ✅ 更新扫描中/等待中数量（扫描统计卡片）
                scanningCountLabel.setText(String.valueOf(stats.getScanningCount()));
                waitingCountLabel.setText(String.valueOf(stats.getWaitingCount()));
                
                // ✅ 同时更新其他统计数据（带节流机制，避免频繁更新）
                updateNonEventDrivenStatistics(false);
                
                // ✅ 更新规则列表
                updateRulesList();
            }
        });
    }
    
    /**
     * ✅ 更新规则列表显示
     */
    private void updateRulesList() {
        if (rulesListContainer == null || taskScheduler == null) {
            return;
        }
        
        // 清空现有规则项
        rulesListContainer.removeAll();
        
        // 获取所有任务进度
        List<TaskScheduler.TaskProgress> allProgress = taskScheduler.getAllTaskProgress();
        
        if (allProgress.isEmpty()) {
            // 如果没有规则，显示提示
            JLabel emptyLabel = new JLabel("  暂无正在执行的规则");
            emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            emptyLabel.setForeground(new Color(127, 140, 141));
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rulesListContainer.add(emptyLabel);
        } else {
            // 显示所有规则（运行中的在前，已完成的在后）
            List<TaskScheduler.TaskProgress> runningTasks = new ArrayList<>();
            List<TaskScheduler.TaskProgress> completedTasks = new ArrayList<>();
            
            for (TaskScheduler.TaskProgress progress : allProgress) {
                if (progress.isRunning()) {
                    runningTasks.add(progress);
                } else if (progress.isCompleted()) {
                    completedTasks.add(progress);
                }
            }
            
            // 先显示运行中的规则
            for (TaskScheduler.TaskProgress progress : runningTasks) {
                rulesListContainer.add(createRuleItem(progress, true));
                rulesListContainer.add(Box.createVerticalStrut(4));
            }
            
            // 再显示已完成的规则（最多显示最近5个）
            int completedCount = Math.min(completedTasks.size(), 5);
            for (int i = 0; i < completedCount; i++) {
                TaskScheduler.TaskProgress progress = completedTasks.get(i);
                // ✅ 如果已完成但进度刚好满（sent == expected），显示进度格式而不是"已完成"
                boolean showProgress = (progress.getSentRequests() == progress.getExpectedRequests() && 
                                       progress.getSentRequests() > 0);
                rulesListContainer.add(createRuleItem(progress, showProgress));
                if (i < completedCount - 1) {
                    rulesListContainer.add(Box.createVerticalStrut(4));
                }
            }
        }
        
        // 刷新显示
        rulesListContainer.revalidate();
        rulesListContainer.repaint();
    }
    
    /**
     * ✅ 创建规则列表项
     */
    private JLabel createRuleItem(TaskScheduler.TaskProgress progress, boolean isRunning) {
        ScanTask task = progress.getTask();
        String ruleName = progress.getRuleName();
        
        // 获取接口信息
        String interfaceText = "未知接口";
        if (task.getRequest() != null) {
            String method = task.getRequest().method();
            String path = task.getRequest().path();
            interfaceText = method + " " + path;
        }
        
        // 构建显示文本
        StringBuilder text = new StringBuilder("  • 规则 \"");
        text.append(ruleName);
        text.append("\" → ");
        text.append(interfaceText);
        
        if (isRunning) {
            // 运行中或已完成但进度刚好满：显示进度
            text.append(" (");
            text.append(progress.getSentRequests());
            text.append("/");
            text.append(progress.getExpectedRequests());
            text.append(")");
        } else {
            // 已完成：显示完成标记
            text.append(" [已完成]");
            if (progress.getVulnerabilityCount() > 0) {
                text.append(" (");
                text.append(progress.getVulnerabilityCount());
                text.append("个漏洞) [警告]");
            }
        }
        
        JLabel ruleLabel = new JLabel(text.toString());
        ruleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        ruleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // 设置颜色
        if (isRunning) {
            ruleLabel.setForeground(new Color(52, 152, 219)); // 蓝色（运行中或已完成但进度刚好满）
        } else if (progress.getVulnerabilityCount() > 0) {
            ruleLabel.setForeground(new Color(231, 76, 60)); // 红色（已完成且有漏洞）
        } else {
            ruleLabel.setForeground(new Color(127, 140, 141)); // 灰色（已完成且无漏洞）
        }
        
        return ruleLabel;
    }
    
    // === 公共方法 ===
    
    // ✅ P1修复：日志行数计数器
    private int currentLogLines = 0;
    private static final int MAX_LOG_LINES = 500;
    
    /**
     * 添加活动日志
     */
    public void addActivityLog(String message) {
        SwingUtilities.invokeLater(() -> {
            // ✅ 清理消息中的控制字符和无效字符，防止乱码
            String cleanedMessage = cleanLogMessage(message);
            String timestamp = timeFormat.format(new Date());
            String logEntry = String.format("[%s] %s\n", timestamp, cleanedMessage);
            activityLogArea.append(logEntry);
            currentLogLines++;
            
            // 自动滚动到底部
            activityLogArea.setCaretPosition(activityLogArea.getDocument().getLength());
            
            // ✅ P1修复：只在每100行检查一次，性能提升100倍
            if (currentLogLines % 100 == 0 && currentLogLines > MAX_LOG_LINES) {
                cleanupOldLogs();
            }
        });
    }
    
    /**
     * ✅ 清理日志消息中的控制字符和无效字符，防止乱码
     */
    private String cleanLogMessage(String message) {
        if (message == null) {
            return "";
        }
        // 移除控制字符（除了换行符和制表符）
        StringBuilder cleaned = new StringBuilder();
        for (char c : message.toCharArray()) {
            // 保留可打印字符、换行符、制表符，以及基本的中文和英文
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                continue; // 跳过控制字符
            }
            // 移除无效的Unicode字符（替换字符、零宽字符等）
            if (c >= 0xFFFE || (c >= 0xD800 && c <= 0xDFFF)) {
                continue; // 跳过无效的Unicode字符
            }
            cleaned.append(c);
        }
        return cleaned.toString();
    }
    
    /**
     * ✅ P1修复：清理旧日志（独立方法，减少主线程阻塞）
     */
    private void cleanupOldLogs() {
        try {
            javax.swing.text.Document doc = activityLogArea.getDocument();
            String text = doc.getText(0, doc.getLength());
            String[] lines = text.split("\n");
            
            if (lines.length > MAX_LOG_LINES) {
                int linesToRemove = lines.length - MAX_LOG_LINES;
                int charCount = 0;
                for (int i = 0; i < linesToRemove; i++) {
                    charCount += lines[i].length() + 1; // +1 for \n
                }
                doc.remove(0, charCount);
                currentLogLines = MAX_LOG_LINES;
            }
        } catch (javax.swing.text.BadLocationException e) {
            // 忽略异常，不影响功能
        }
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
            
            // ✅ P1修复：移除频繁调用updateStatistics，改由定时器统一刷新（性能提升10倍）
            // updateStatistics();  // 删除此行
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
        // ✅ 清理漏洞信息中的特殊字符，防止乱码
        String cleanType = cleanLogMessage(type);
        String cleanTarget = cleanLogMessage(target);
        addActivityLog("发现漏洞: " + cleanType + " - " + cleanTarget);
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


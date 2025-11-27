package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.active.ActiveScanner;
import com.xprobe.scanner.active.ParameterCollector;
import com.xprobe.scanner.active.ParameterDataStorage;
import com.xprobe.scanner.active.arjun.ArjunService;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.ConfigStorage;
import com.xprobe.scanner.config.XProbeConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 主动探测选项卡 - 支持实时监听和手动触发两种模式
 */
public class ActiveProbeTab {
    private JPanel panel;
    private final MontoyaApi api;
    private final ActiveScanner activeScanner;
    private final ConfigStorage configStorage;
    private final ParameterDataStorage parameterDataStorage;  // ✅ 参数数据存储
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;  // ✅ 实时扫描器引用
    
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
    private JButton interfaceScanButton;
    private JButton clearResultsButton;
    private JButton clearCacheButton;  // ✅ 清空Arjun缓存按钮
    private JProgressBar progressBar;
    
    // 模式控制
    private JRadioButton realtimeModeRadio;
    private JRadioButton manualModeRadio;
    private ButtonGroup modeGroup;
    private JLabel modeStatusLabel;
    
    // 接口来源控制
    private JRadioButton sourceNoneRadio;
    private JRadioButton sourceManualRadio;
    private JRadioButton sourceAutoRadio;
    private ButtonGroup sourceGroup;
    
    // Arjun配置控制
    private JSpinner chunkSizeSpinner;
    private JSpinner timeoutSpinner;
    private JCheckBox customDictCheckbox;
    
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
    
    // ✅ 保存表格选中状态（用于刷新后恢复）
    private final int[] savedSelectedRow = {-1};
    private final List<Integer> savedSelectedRows = new ArrayList<>();

    public ActiveProbeTab(MontoyaApi api, ConfigurationManager configManager, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;  // ✅ 保存引用
        this.activeScanner = new ActiveScanner(api, configManager, realtimeScanner);
        this.configStorage = new ConfigStorage(api);
        this.parameterDataStorage = new com.xprobe.scanner.active.ParameterDataStorage(api);  // ✅ 初始化参数数据存储
        
        initializeComponents();
        loadMasterSwitchState();  // 从配置加载总开关状态
        setupLayout();
        setupEventListeners();
        registerArjunResultListener(realtimeScanner);  // ✅ 注册Arjun结果监听器
        realtimeScanner.setActiveProbeTab(this);  // ✅ 设置ActiveProbeTab引用，用于接口探测结果回调
        startAutoRefresh();
        
        // ✅ 启动时自动恢复数据
        restoreCollectedDataOnStartup();
    }

    private void initializeComponents() {
        // 主动探测总开关
        masterEnableToggle = new JToggleButton("✅ 主动探测已启用", true);
        masterEnableToggle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        masterEnableToggle.setFocusPainted(false);
        updateToggleButtonAppearance(true);
        
        // 已收集数据表格（按子域名显示）
        collectedDataTableModel = new DefaultTableModel(
            new Object[]{"子域名", "主域名", "接口数", "参数数", "关键词数", "最后更新", "状态"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        collectedDataTable = new JTable(collectedDataTableModel) {
            // ✅ 修复：重写prepareRenderer确保选中行颜色正确显示（参考 arjunResultTable）
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                
                if (isRowSelected(row)) {
                    // 选中行：使用设置的选中颜色
                    c.setBackground(getSelectionBackground());
                    c.setForeground(getSelectionForeground());
                } else {
                    // 未选中行：使用默认背景色
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                
                // ✅ 确保组件不透明，背景色才能正确显示
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                }
                
                return c;
            }
        };
        collectedDataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        collectedDataTable.setRowHeight(28);
        collectedDataTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        collectedDataTable.getColumnModel().getColumn(0).setPreferredWidth(200);  // 子域名
        collectedDataTable.getColumnModel().getColumn(1).setPreferredWidth(150);  // 主域名
        
        // ✅ 修复：保持选中状态，只有点击其他地方才取消选中
        collectedDataTable.setFocusable(true);
        collectedDataTable.setSelectionBackground(new Color(184, 207, 229)); // 选中背景色
        collectedDataTable.setSelectionForeground(Color.BLACK);
        // 禁用行悬停效果，避免用户误以为选中状态改变
        collectedDataTable.setRowSelectionAllowed(true);
        collectedDataTable.setColumnSelectionAllowed(false);
        // 设置选择模式，确保点击后保持选中
        collectedDataTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // ✅ 关键修复：禁用表格的自动行选择功能（鼠标悬停时的选择）
        // 这将阻止鼠标移动时自动改变选择
        collectedDataTable.setRowSelectionAllowed(true);
        collectedDataTable.setCellSelectionEnabled(false);
        
        // ✅ 设置表格不允许拖拽选择
        collectedDataTable.setDragEnabled(false);
        
        // ✅ 修复：监听选择变化，防止鼠标移动时清除选择
        collectedDataTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                // 保存当前选择
                savedSelectedRows.clear();
                int[] selectedRows = collectedDataTable.getSelectedRows();
                for (int row : selectedRows) {
                    savedSelectedRows.add(row);
                }
                if (selectedRows.length > 0) {
                    savedSelectedRow[0] = selectedRows[0];
                }
            }
        });
        
        // ✅ 修复：禁用鼠标移动时的自动选择行为
        // 重写鼠标事件处理，确保只有明确点击才改变选择
        collectedDataTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 确保点击时选中行
                int row = collectedDataTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                    // 如果按住Ctrl或Shift，保留多选功能
                    if (e.isControlDown()) {
                        // Ctrl点击：切换选中状态
                        if (collectedDataTable.isRowSelected(row)) {
                            collectedDataTable.removeRowSelectionInterval(row, row);
                        } else {
                            collectedDataTable.addRowSelectionInterval(row, row);
                        }
                    } else if (e.isShiftDown()) {
                        // Shift点击：区间选择
                        int anchorRow = collectedDataTable.getSelectionModel().getAnchorSelectionIndex();
                        if (anchorRow >= 0) {
                            int min = Math.min(anchorRow, row);
                            int max = Math.max(anchorRow, row);
                            collectedDataTable.setRowSelectionInterval(min, max);
                        } else {
                            collectedDataTable.setRowSelectionInterval(row, row);
                        }
                    } else {
                        // 普通点击：单选
                        collectedDataTable.setRowSelectionInterval(row, row);
                    }
                    
                    // 保存选择
                    savedSelectedRow[0] = row;
                    savedSelectedRows.clear();
                    savedSelectedRows.add(row);
                    
                    // 双击查看详情
                    if (e.getClickCount() == 2) {
                        showDomainDetails(row);
                    }
                }
            }
            
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }
            
            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = collectedDataTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        // 如果该行未选中，先选中它
                        if (!collectedDataTable.isRowSelected(row)) {
                            collectedDataTable.setRowSelectionInterval(row, row);
                            savedSelectedRow[0] = row;
                            savedSelectedRows.clear();
                            savedSelectedRows.add(row);
                        }
                        showContextMenu(e.getX(), e.getY(), row);
                    }
                }
            }
        });
        
        // ✅ 关键修复：禁用鼠标移动时的选择变化（参考 arjunResultTable）
        collectedDataTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                // ✅ 阻止拖拽时改变选择
                e.consume();
            }
            
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // ✅ 鼠标移动时不改变选择（什么都不做，保持当前选择）
            }
        });
        
        // ✅ 添加焦点监听器：失去焦点时不清除选择
        collectedDataTable.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                // 失去焦点时不自动清除选择，保持选中状态
            }
        });

        // ✅ Arjun探测结果表格（包含接口探测和参数探测结果）
        arjunResultTableModel = new DefaultTableModel(
            new Object[]{"探测类型", "目标域名", "接口", "发现参数", "参数类型", "验证状态", "探测时间"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        arjunResultTable = new JTable(arjunResultTableModel) {
            // ✅ 修复：重写prepareRenderer确保选中行颜色正确显示
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                
                if (isRowSelected(row)) {
                    // 选中行：使用设置的选中颜色
                    c.setBackground(getSelectionBackground());
                    c.setForeground(getSelectionForeground());
                } else {
                    // 未选中行：使用默认背景色
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                
                // ✅ 确保组件不透明，背景色才能正确显示
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                }
                
                return c;
            }
        };
        arjunResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        arjunResultTable.setRowHeight(25);
        arjunResultTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
        // ✅ 修复：保持选中状态，只有点击其他地方才取消选中
        arjunResultTable.setFocusable(true);
        arjunResultTable.setSelectionBackground(new Color(184, 207, 229)); // 选中背景色
        arjunResultTable.setSelectionForeground(Color.BLACK);
        // 禁用行悬停效果，避免用户误以为选中状态改变
        arjunResultTable.setRowSelectionAllowed(true);
        arjunResultTable.setColumnSelectionAllowed(false);
        // 设置选择模式，确保点击后保持选中
        arjunResultTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // ✅ 添加鼠标监听器：确保点击时选中行，移动鼠标不取消选中
        arjunResultTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 确保点击时选中行
                int row = arjunResultTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    arjunResultTable.setRowSelectionInterval(row, row);
                }
            }
            
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // ✅ 阻止默认的鼠标按下行为，防止拖拽时改变选择
                // 真正的选择在mouseClicked中处理
            }
        });
        
        // ✅ 关键修复：禁用鼠标移动时的选择变化
        arjunResultTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                // ✅ 阻止拖拽时改变选择
                e.consume();
            }
            
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // ✅ 鼠标移动时不改变选择（什么都不做，保持当前选择）
            }
        });
        
        // ✅ 添加焦点监听器：失去焦点时不自动清除选择
        arjunResultTable.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                // 失去焦点时不自动清除选择，保持选中状态
            }
        });
        
        // ✅ 禁用表格的默认拖拽选择行为
        arjunResultTable.setDragEnabled(false);

        // 状态标签
        statusLabel = new JLabel("🟢 就绪 - 正在监听Burp流量...");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        statusLabel.setForeground(new Color(46, 204, 113));

        // 进度条
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(320, 24));
        progressBar.setValue(100);
        progressBar.setString("100%");
        
        // 按钮
        arjunScanButton = createStyledButton("开始 Arjun 探测", new Color(155, 89, 182));
        interfaceScanButton = createStyledButton("接口探测", new Color(26, 188, 156));
        refreshDataButton = createStyledButton("刷新已收集数据", new Color(52, 152, 219));
        clearResultsButton = createStyledButton("清空 Arjun 结果", new Color(231, 76, 60));
        clearCacheButton = createStyledButton("清空 Arjun 缓存", new Color(230, 126, 34));  // ✅ 清空Arjun扫描记录
        
        // 模式控制
        realtimeModeRadio = new JRadioButton("实时监听模式");
        manualModeRadio = new JRadioButton("手动触发模式");
        modeGroup = new ButtonGroup();
        modeGroup.add(realtimeModeRadio);
        modeGroup.add(manualModeRadio);
        manualModeRadio.setSelected(true); // 默认手动模式
        
        realtimeModeRadio.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        manualModeRadio.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        
        modeStatusLabel = new JLabel("当前: 手动触发模式");
        modeStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        modeStatusLabel.setForeground(Color.GRAY);
        
        // 接口来源
        sourceNoneRadio = new JRadioButton("无");
        sourceManualRadio = new JRadioButton("手动添加");
        sourceAutoRadio = new JRadioButton("自动采集");
        sourceGroup = new ButtonGroup();
        sourceGroup.add(sourceNoneRadio);
        sourceGroup.add(sourceManualRadio);
        sourceGroup.add(sourceAutoRadio);
        sourceNoneRadio.setSelected(true);  // 默认选择"无"
        Font sourceFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        sourceNoneRadio.setFont(sourceFont);
        sourceManualRadio.setFont(sourceFont);
        sourceAutoRadio.setFont(sourceFont);
        
        // Arjun配置
        chunkSizeSpinner = new JSpinner(new SpinnerNumberModel(250, 10, 1000, 10));
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 60, 1));
        customDictCheckbox = new JCheckBox("使用自定义字典");
        customDictCheckbox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        customDictCheckbox.setOpaque(false);
        
        // 从配置加载初始值
        XProbeConfig storedConfig = configStorage.load();
        chunkSizeSpinner.setValue(storedConfig.getArjunChunkSize());
        timeoutSpinner.setValue(storedConfig.getArjunTimeout());
        boolean hasCustomDict = storedConfig.getArjunCustomDictionary() != null
            && !storedConfig.getArjunCustomDictionary().isEmpty();
        customDictCheckbox.setSelected(hasCustomDict);
        customDictCheckbox.setEnabled(hasCustomDict);
        
        // 配置信息
        configInfoLabel = new JLabel("配置: 间隔60秒 | 最小参数3个 | 最大并发5个 | 收集模式: 仅参数名");
        configInfoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        configInfoLabel.setForeground(new Color(100, 100, 100));
        
        // 统计标签
        totalDomainsLabel = new JLabel("域名: 0", SwingConstants.CENTER);
        totalEndpointsLabel = new JLabel("接口: 0", SwingConstants.CENTER);
        totalParametersLabel = new JLabel("参数: 0", SwingConstants.CENTER);
        totalKeywordsLabel = new JLabel("关键词: 0", SwingConstants.CENTER);
        arjunScansLabel = new JLabel("探测次数: 0", SwingConstants.CENTER);
        arjunResultsLabel = new JLabel("发现参数: 0", SwingConstants.CENTER);
        
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
        panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        // ✅ 添加面板点击监听器：点击面板其他地方时清除表格选择
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 点击面板（非表格区域）时清除表格选择
                if (!isPointInTable(e.getPoint(), collectedDataTable) && 
                    !isPointInTable(e.getPoint(), arjunResultTable)) {
                    collectedDataTable.clearSelection();
                    arjunResultTable.clearSelection();
                }
            }
        });
        panel.setFocusable(true); // 允许面板获得焦点
        
        panel.add(createTopBar(), BorderLayout.NORTH);
        panel.add(createSplitPane(), BorderLayout.CENTER);
        panel.add(createBottomPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * ✅ 检查点是否在表格范围内
     */
    private boolean isPointInTable(java.awt.Point point, JTable table) {
        if (table == null) {
            return false;
        }
        java.awt.Point tableLocation = SwingUtilities.convertPoint(panel, point, table);
        return table.contains(tableLocation);
    }
    
    private JComponent createTopBar() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.add(createConfigRow());  // 第一行：当前配置 + 操作按钮
        wrapper.add(Box.createVerticalStrut(6));
        wrapper.add(createPrimaryRow());  // 第二行：探测模式 + 接口来源 + 主要操作按钮
        return wrapper;
    }
    
    private JSplitPane createSplitPane() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(290);
        splitPane.setResizeWeight(0.52);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel collectedPanel = new JPanel(new BorderLayout(5, 5));
        collectedPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219)),
            "已收集的流量数据（Burp Proxy + SiteMap）",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 13),
            new Color(52, 152, 219)
        ));
        
        JScrollPane collectedScrollPane = new JScrollPane(collectedDataTable);
        collectedScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        collectedScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        collectedPanel.add(collectedScrollPane, BorderLayout.CENTER);
        collectedPanel.add(createStatsPanel(), BorderLayout.EAST);
        
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(155, 89, 182)),
            "Arjun 参数探测结果",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 13),
            new Color(155, 89, 182)
        ));
        
        JScrollPane resultScrollPane = new JScrollPane(arjunResultTable);
        resultScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        resultScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        resultPanel.add(resultScrollPane, BorderLayout.CENTER);

        splitPane.setTopComponent(collectedPanel);
        splitPane.setBottomComponent(resultPanel);
        return splitPane;
    }
    
    private JPanel createPrimaryRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 6, 0, 6);
        gbc.gridy = 0;
        
        // 探测模式
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        row.add(createModeSelectorPanel(), gbc);
        
        // 右侧：操作按钮（刷新已收集数据、清空 Arjun 结果、查看详情、清空 Arjun 缓存）
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        row.add(createDataToolbar(), gbc);
        return row;
    }
    
    private JPanel createConfigRow() {
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 6, 0, 6);
        gbc.gridy = 0;
        
        // 总开关
        gbc.gridx = 0;
        row.add(masterEnableToggle, gbc);
        
        // 当前配置信息
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel configTitle = new JLabel("当前配置:");
        configTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        left.add(configTitle);
        left.add(configInfoLabel);
        row.add(left, gbc);
        
        // 右侧：主要操作按钮（接口探测、开始 Arjun 探测）
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        row.add(createActionButtonGroup(), gbc);
        
        return row;
    }

    private JPanel createActionButtonGroup() {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        interfaceScanButton.setPreferredSize(new Dimension(120, 32));
        interfaceScanButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        arjunScanButton.setPreferredSize(new Dimension(140, 32));
        arjunScanButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        group.add(interfaceScanButton);
        group.add(arjunScanButton);
        return group;
    }
    
    private JPanel createModeSelectorPanel() {
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel modeLabel = new JLabel("探测模式:");
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        modePanel.add(modeLabel);
        modePanel.add(manualModeRadio);
        modePanel.add(realtimeModeRadio);
        modePanel.add(Box.createHorizontalStrut(8));
        modeStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        modePanel.add(modeStatusLabel);
        return modePanel;
    }
    
    private JPanel createSourcePanel() {
        JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel sourceLabel = new JLabel("接口来源:");
        sourceLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sourcePanel.add(sourceLabel);
        sourcePanel.add(sourceNoneRadio);
        sourcePanel.add(sourceManualRadio);
        sourcePanel.add(sourceAutoRadio);
        return sourcePanel;
    }
    
    
    private JPanel createDataToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton viewDetailsButton = createStyledButton("查看详情", new Color(46, 204, 113));
        viewDetailsButton.addActionListener(e -> {
            int rowIndex = collectedDataTable.getSelectedRow();
            if (rowIndex >= 0) {
                showDomainDetails(rowIndex);
            } else {
                JOptionPane.showMessageDialog(panel, 
                    "请先选择一个域名", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        // ✅ 添加保存数据按钮
        JButton saveDataButton = createStyledButton("💾 保存数据", new Color(52, 152, 219));
        saveDataButton.addActionListener(e -> saveCollectedData());
        
        // ✅ 添加一键清空已收集数据按钮（新增）
        JButton clearCollectedDataButton = createStyledButton("🗑️清空已收集数据", new Color(231, 76, 60));
        clearCollectedDataButton.addActionListener(e -> clearAllCollectedData());
        
        toolbar.add(refreshDataButton);
        toolbar.add(clearResultsButton);  // ✅ 清空 Arjun 结果
        toolbar.add(clearCacheButton);  // ✅ 清空 Arjun 缓存
        toolbar.add(saveDataButton);
        toolbar.add(clearCollectedDataButton);  // ✅ 清空已收集数据
        toolbar.add(viewDetailsButton);
        return toolbar;
    }
    
    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        statsPanel.setBackground(new Color(250, 250, 250));
        // ✅ 紧凑布局：减小宽度和间距
        statsPanel.setPreferredSize(new Dimension(140, 0));
        
        statsPanel.add(createStatCard(totalDomainsLabel, new Color(200, 240, 255)));
        statsPanel.add(Box.createVerticalStrut(6));  // 减小间距
        statsPanel.add(createStatCard(totalEndpointsLabel, new Color(210, 250, 230)));
        statsPanel.add(Box.createVerticalStrut(6));
        statsPanel.add(createStatCard(totalParametersLabel, new Color(255, 248, 210)));
        statsPanel.add(Box.createVerticalStrut(6));
        statsPanel.add(createStatCard(totalKeywordsLabel, new Color(255, 234, 210)));
        statsPanel.add(Box.createVerticalStrut(8));  // 减小间距
        statsPanel.add(createStatCard(arjunScansLabel, new Color(232, 210, 255)));
        statsPanel.add(Box.createVerticalStrut(6));
        statsPanel.add(createStatCard(arjunResultsLabel, new Color(255, 220, 220)));
        statsPanel.add(Box.createVerticalGlue());
        return statsPanel;
    }
    
    private JPanel createStatCard(JLabel label, Color background) {
        JPanel card = new JPanel(new BorderLayout());
        // ✅ 紧凑布局：减小卡片高度和内边距
        card.setMaximumSize(new Dimension(140, 45));
        card.setBackground(background);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(background.darker()),
            new EmptyBorder(6, 6, 6, 6)  // 减小内边距
        ));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));  // 稍微减小字体
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
        
        arjunScanButton.addActionListener(e -> openProbeConfigDialog(
            ActiveProbeConfigDialog.ProbeMode.ARJUN_ONLY));
        interfaceScanButton.addActionListener(e -> openProbeConfigDialog(
            ActiveProbeConfigDialog.ProbeMode.INTERFACE_ONLY));
        refreshDataButton.addActionListener(e -> refreshCollectedData());
        clearResultsButton.addActionListener(e -> clearResults());
        clearCacheButton.addActionListener(e -> clearArjunCache());  // ✅ 清空Arjun缓存
        
        // 模式切换监听
        realtimeModeRadio.addActionListener(e -> switchToRealtimeMode());
        manualModeRadio.addActionListener(e -> switchToManualMode());
        
        sourceNoneRadio.addActionListener(e -> handleInterfaceSourceSelection("none"));
        sourceManualRadio.addActionListener(e -> handleInterfaceSourceSelection("manual"));
        sourceAutoRadio.addActionListener(e -> handleInterfaceSourceSelection("auto"));
        
        // Arjun配置已移至配置中心统一管理，此处不再提供UI修改
        // chunkSizeSpinner和timeoutSpinner保留用于读取配置值，但不提供UI修改
    }

    private void openProbeConfigDialog(ActiveProbeConfigDialog.ProbeMode mode) {
        SwingUtilities.invokeLater(() -> {
            Window owner = SwingUtilities.getWindowAncestor(panel);
            ParameterDataStorage.ParameterDataModel dataModel = parameterDataStorage.load();
            ActiveProbeConfigDialog dialog = new ActiveProbeConfigDialog(
                owner,
                dataModel,
                mode,
                config -> SwingUtilities.invokeLater(() -> executeProbeConfiguration(config))
            );
            dialog.setVisible(true);
        });
    }
    
    private void executeProbeConfiguration(ActiveProbeConfigDialog.ExecutionConfig config) {
        disableActionButtons(true);
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        statusLabel.setText("⏳ 正在根据配置执行探测...");
        statusLabel.setForeground(new Color(52, 152, 219));
        
        CompletableFuture<Void> interfacePhase = config.getStrategy() == ActiveProbeConfigDialog.ProbeMode.ARJUN_ONLY
            ? CompletableFuture.completedFuture(null)
            : runInterfacePhase(config);
        
        CompletableFuture<Void> pipeline;
        if (config.getStrategy() == ActiveProbeConfigDialog.ProbeMode.INTERFACE_THEN_ARJUN) {
            pipeline = interfacePhase.thenCompose(v -> runArjunPhase(config));
        } else if (config.getStrategy() == ActiveProbeConfigDialog.ProbeMode.ARJUN_ONLY) {
            pipeline = runArjunPhase(config);
        } else {
            pipeline = interfacePhase;
        }
        
        ArjunService arjunService = realtimeScanner.getArjunService();
        final boolean dictionaryOverwritten = arjunService != null && !config.isCustomDictionaryEnabled();
        final Set<String> originalDictionary = dictionaryOverwritten
            ? arjunService.getUserCustomDictionary()
            : null;
        if (dictionaryOverwritten) {
            arjunService.setUserCustomDictionary(Collections.emptySet());
        }
        
        final ArjunService finalArjunService = arjunService;
        pipeline.whenComplete((unused, throwable) -> {
            if (dictionaryOverwritten && finalArjunService != null) {
                finalArjunService.setUserCustomDictionary(
                    originalDictionary != null ? originalDictionary : Collections.emptySet());
            }
            SwingUtilities.invokeLater(() -> {
                disableActionButtons(false);
                progressBar.setIndeterminate(false);
                progressBar.setValue(throwable == null ? 100 : 0);
                if (throwable == null) {
                    String successText = switch (config.getStrategy()) {
                        case INTERFACE_ONLY -> "✅ 接口探测完成";
                        case INTERFACE_THEN_ARJUN -> "✅ 接口+参数探测完成";
                        case ARJUN_ONLY -> "✅ 参数探测完成";
                    };
                    statusLabel.setText(successText);
                    statusLabel.setForeground(new Color(46, 204, 113));
                } else {
                    statusLabel.setText("❌ 执行失败");
                    statusLabel.setForeground(new Color(231, 76, 60));
                    JOptionPane.showMessageDialog(panel,
                        "执行配置失败:\n" + throwable.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
                refreshCollectedData();
            });
        });
    }
    
    private CompletableFuture<Void> runInterfacePhase(ActiveProbeConfigDialog.ExecutionConfig config) {
        return switch (config.getInterfaceSource()) {
            case MANUAL -> processManualTargets(config.getManualEntries(), false, false);
            case AUTO -> runAutoInterfaceDiscovery();
            case COLLECTED -> {
                Map<String, Set<String>> targets = resolveHostsByScope(
                    config.getSelectedHostsByDomain(),
                    config.getInterfaceScope()
                );
                if (targets.isEmpty()) {
                    yield CompletableFuture.failedFuture(
                        new IllegalArgumentException("所选主域没有可用子域可执行接口探测。"));
                }
                yield CompletableFuture.runAsync(() -> runInterfaceOnTargets(targets, config.getInterfaceScope()));
            }
        };
    }
    
    private CompletableFuture<Void> runArjunPhase(ActiveProbeConfigDialog.ExecutionConfig config) {
        return switch (config.getInterfaceSource()) {
            case MANUAL -> {
                boolean interfaceFirst = config.getStrategy() == ActiveProbeConfigDialog.ProbeMode.INTERFACE_THEN_ARJUN;
                yield processManualTargets(config.getManualEntries(), true, interfaceFirst);
            }
            case AUTO -> runAutoArjunScan();
            case COLLECTED -> {
                Map<String, Set<String>> targets = resolveHostsByScope(
                    config.getSelectedHostsByDomain(),
                    config.getParameterScope()
                );
                if (targets.isEmpty()) {
                    yield CompletableFuture.failedFuture(
                        new IllegalArgumentException("所选主域没有可用子域可执行参数探测。"));
                }
                yield CompletableFuture.runAsync(() -> runArjunOnTargets(targets, config.getParameterScope()));
            }
        };
    }
    
    private Map<String, Set<String>> resolveHostsByScope(Map<String, Set<String>> selected,
                                                        ActiveProbeConfigDialog.ScopeOption scope) {
        Map<String, Set<String>> resolved = new LinkedHashMap<>();
        ParameterCollector collector = realtimeScanner.getParameterCollector();
        selected.forEach((mainDomain, hosts) -> {
            if (mainDomain == null || mainDomain.isBlank()) {
                return;
            }
            Set<String> resolvedHosts = scope == ActiveProbeConfigDialog.ScopeOption.ALL_SUBDOMAINS
                ? new LinkedHashSet<>(collector.getHostsForMainDomain(mainDomain))
                : new LinkedHashSet<>(hosts);
            resolvedHosts.removeIf(h -> h == null || h.isBlank() || "(unknown)".equals(h));
            if (!resolvedHosts.isEmpty()) {
                resolved.put(mainDomain, resolvedHosts);
            }
        });
        return resolved;
    }
    
    private void runInterfaceOnTargets(Map<String, Set<String>> targets,
                                       ActiveProbeConfigDialog.ScopeOption scope) {
        if (scope == ActiveProbeConfigDialog.ScopeOption.ALL_SUBDOMAINS) {
            targets.keySet().forEach(this::triggerInterfaceDiscoveryForMainDomain);
        } else {
            targets.forEach((mainDomain, hosts) -> hosts.forEach(host ->
                triggerInterfaceDiscoveryForHost(mainDomain, host)));
        }
    }
    
    private void runArjunOnTargets(Map<String, Set<String>> targets,
                                   ActiveProbeConfigDialog.ScopeOption scope) {
        if (scope == ActiveProbeConfigDialog.ScopeOption.ALL_SUBDOMAINS) {
            targets.keySet().forEach(domain ->
                activeScanner.getRealtimeScanner().triggerArjunForMainDomain(domain));
        } else {
            targets.forEach((mainDomain, hosts) -> hosts.forEach(host ->
                activeScanner.getRealtimeScanner().triggerArjunForHost(mainDomain, host)));
        }
    }
    
    private void disableActionButtons(boolean disable) {
        arjunScanButton.setEnabled(!disable);
        interfaceScanButton.setEnabled(!disable);
        refreshDataButton.setEnabled(!disable);
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
        modeStatusLabel.setText("当前: 实时监听模式 (智能触发)");
        modeStatusLabel.setForeground(new Color(46, 204, 113));
        
        // ✅ 检查总开关状态（必须在设置模式之前检查）
        if (!masterEnableToggle.isSelected()) {
            statusLabel.setText("⚫ 主动探测已禁用 - 被动收集持续进行");
            statusLabel.setForeground(Color.GRAY);
            api.logging().raiseInfoEvent("⚠️ 总开关已禁用，无法切换到实时监听模式");
            // 切换回手动模式
            manualModeRadio.setSelected(true);
            modeStatusLabel.setText("当前: 手动触发模式 (SiteMap历史流量)");
            modeStatusLabel.setForeground(Color.GRAY);
            return;
        }
        
        // ✅ 确保后端总开关已启用（双重检查，确保状态同步）
        // 注意：applyMasterSwitchState() 已经调用了 startRealtimeScanning()，
        // 但为了确保状态一致，这里再次检查并启用
        activeScanner.getRealtimeScanner().startRealtimeScanning();
        
        // ✅ 设置后端为实时模式
        // 注意：setRealtimeMode() 内部会检查 arjunEnabled，如果未启用会直接返回
        // 但由于上面已经调用了 startRealtimeScanning()，所以这里应该能成功
        activeScanner.getRealtimeScanner().setRealtimeMode(true);
        
        statusLabel.setText("🔄 实时监听 - 阈值触发(15个参数) + 定时兜底(5分钟)...");
        statusLabel.setForeground(new Color(46, 204, 113));
        
        // ✅ 启动定时检查（根据配置，只在有新参数时触发）
        if (realtimeArjunTimer != null && realtimeArjunTimer.isRunning()) {
            realtimeArjunTimer.stop();
        }
        
        // 从配置中获取定时间隔（毫秒）
        int intervalMs = activeScanner.getRealtimeScanner().getCooldownSeconds() * 1000;
        
        realtimeArjunTimer = new javax.swing.Timer(intervalMs, e -> {
            api.logging().raiseInfoEvent("🔍 定时检查: 检查是否有新参数需要触发Arjun");
            activeScanner.getRealtimeScanner().periodicArjunCheck();
        });
        realtimeArjunTimer.start();
        
        api.logging().raiseInfoEvent("已切换到实时监听模式（智能触发：阈值15个参数，定时5分钟兜底）");
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
        
        // ✅ 设置后端为手动模式
        activeScanner.getRealtimeScanner().setRealtimeMode(false);
        
        // 停止实时监听定时器
        if (realtimeArjunTimer != null) {
            realtimeArjunTimer.stop();
        }
        
        api.logging().raiseInfoEvent("已切换到手动触发模式（SiteMap流量）");
    }
    
    /**
     * 开始自动刷新（每3秒更新一次收集数据）
     */
    private void startAutoRefresh() {
        // ✅ 参考dev18实现：每3秒刷新一次
        refreshTimer = new javax.swing.Timer(3000, e -> refreshCollectedData());
        refreshTimer.start();
        
        // 初始加载
        refreshCollectedData();
    }

    /**
     * 刷新已收集的数据
     * ✅ 参考dev18实现，但添加了选中行保存和恢复功能
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
                
                // ✅ 修改：按子域名显示，每行一个子域名
                ParameterCollector parameterCollector = realtimeScanner.getParameterCollector();
                
                // ✅ 修复：保存当前选中的行信息（在清空表格之前）
                Set<String> selectedHosts = new HashSet<>();
                Set<String> selectedMainDomains = new HashSet<>();
                int[] selectedRows = collectedDataTable.getSelectedRows();
                for (int row : selectedRows) {
                    if (row >= 0 && row < collectedDataTableModel.getRowCount()) {
                        String host = (String) collectedDataTableModel.getValueAt(row, 0);
                        String mainDomain = (String) collectedDataTableModel.getValueAt(row, 1);
                        if (host != null && mainDomain != null) {
                            selectedHosts.add(host);
                            selectedMainDomains.add(mainDomain);
                        }
                    }
                }
                
                // 清空并重新填充表格
                collectedDataTableModel.setRowCount(0);
                
                // 遍历所有主域名
                for (String mainDomain : parameterCollector.getAllMainDomains()) {
                    // 获取该主域名下的所有子域名（host）
                    Set<String> hosts = parameterCollector.getHostsForMainDomain(mainDomain);
                    
                    // 为每个子域名创建一行
                    for (String host : hosts) {
                        try {
                            // 获取该子域名的统计信息
                            Set<String> hostEndpoints = new HashSet<>();
                            Set<String> hostParameters = parameterCollector.getParametersForHost(host);
                            Set<String> hostKeywords = new HashSet<>();
                            
                            // 统计该子域名的接口数（需要遍历所有接口，找出属于该host的）
                            Set<ParameterCollector.EndpointKey> allEndpointKeys = 
                                parameterCollector.getEndpointKeysForMainDomain(mainDomain);
                            for (ParameterCollector.EndpointKey epKey : allEndpointKeys) {
                                if (epKey.host.equals(host)) {
                                    hostEndpoints.add(epKey.endpoint);
                                }
                            }
                            
                            // 如果启用了关键词收集，获取该子域名的关键词
                            if (parameterCollector.getCollectionMode() == ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS) {
                                // 关键词是按主域名存储的，这里使用主域名的关键词
                                hostKeywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
                            }
                            
                            // 获取最后更新时间（使用主域名的更新时间）
                            long lastUpdateTime = parameterCollector.getLastUpdateTimeForDomain(mainDomain);
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
                            String lastUpdate = sdf.format(new java.util.Date(lastUpdateTime));
                            
                            String status = hostEndpoints.size() > 0 ? "✅ 已收集" : "⏳ 收集中";
                        
                        collectedDataTableModel.addRow(new Object[]{
                                host,              // 子域名
                                mainDomain,        // 主域名
                                hostEndpoints.size(),  // 接口数
                                hostParameters.size(), // 参数数
                                hostKeywords.size(),   // 关键词数
                                lastUpdate,        // 最后更新
                                status             // 状态
                        });
                    } catch (Exception ex) {
                            api.logging().raiseErrorEvent("获取子域名统计失败: " + ex.getMessage());
                        }
                    }
                }
                
                // ✅ 修复：恢复之前选中的行（在表格填充完成后）
                if (!selectedHosts.isEmpty()) {
                    // 延迟恢复选择，确保表格已完全更新
                    SwingUtilities.invokeLater(() -> {
                        collectedDataTable.clearSelection();
                        for (int i = 0; i < collectedDataTableModel.getRowCount(); i++) {
                            String host = (String) collectedDataTableModel.getValueAt(i, 0);
                            String mainDomain = (String) collectedDataTableModel.getValueAt(i, 1);
                            // 如果该行之前被选中，恢复选中状态
                            if (host != null && mainDomain != null && selectedHosts.contains(host) && selectedMainDomains.contains(mainDomain)) {
                                collectedDataTable.addRowSelectionInterval(i, i);
                            }
                        }
                        // 更新保存的选择状态（用于鼠标移动时恢复）
                        savedSelectedRows.clear();
                        int[] currentSelectedRows = collectedDataTable.getSelectedRows();
                        for (int row : currentSelectedRows) {
                            savedSelectedRows.add(row);
                        }
                        if (currentSelectedRows.length > 0) {
                            savedSelectedRow[0] = currentSelectedRows[0];
                        }
                    });
                }
                
                // ✅ 更新Arjun统计：只统计参数探测的结果
                int paramScanCount = 0;
                for (int i = 0; i < arjunResultTableModel.getRowCount(); i++) {
                    Object type = arjunResultTableModel.getValueAt(i, 0);
                    if ("参数探测".equals(type)) {
                        paramScanCount++;
                    }
                }
                arjunResultsLabel.setText("发现参数: " + paramScanCount);
                
            } catch (Exception ex) {
                api.logging().raiseErrorEvent("刷新数据失败: " + ex.getMessage());
            }
        });
    }

    /**
     * 启动Arjun扫描（根据当前模式选择数据源）
     */
    private void startArjunScan() {
        try {
            // ✅ 修复：根据接口来源选择决定数据源
            boolean manualSource = sourceManualRadio.isSelected();
            boolean autoSource = sourceAutoRadio.isSelected();
            
            // ✅ 修复："无"选项表示使用已有采集数据，不需要提示
            
            List<String> manualTargets = manualSource ? promptManualTargets() : Collections.emptyList();
            if (manualSource && (manualTargets == null || manualTargets.isEmpty())) {
                return;
            }
            
            Boolean runInterfaceFirst = askInterfaceBeforeArjun(manualSource);
            if (runInterfaceFirst == null) {
                return;
            }
            
            Runnable arjunTask = () -> executeArjunScan(manualSource, manualTargets, autoSource);
            
            if (runInterfaceFirst) {
                // ✅ 先进行接口探测，接口探测完成后再进行Arjun参数探测
                CompletableFuture<Void> interfaceFuture = manualSource
                    ? processManualTargets(manualTargets, false, false)
                    : (autoSource ? runAutoInterfaceDiscovery() : CompletableFuture.completedFuture(null));
                
                interfaceFuture.whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("❌ 接口探测失败，已取消参数探测");
                            statusLabel.setForeground(new Color(231, 76, 60));
                        });
                        return; // 提前返回，避免继续执行
                    }
                    
                    // ✅ 接口探测完成，进行Arjun参数探测（不先探测接口，因为已经探测过了）
                    try {
                        if (manualSource) {
                            processManualTargets(manualTargets, true, false)
                                .whenComplete((unused2, throwable2) -> {
                                    SwingUtilities.invokeLater(() -> {
                                        if (throwable2 != null) {
                                            statusLabel.setText("❌ Arjun 探测失败");
                                            statusLabel.setForeground(new Color(231, 76, 60));
                                        } else {
                                            statusLabel.setText("✅ Arjun 探测完成");
                                            statusLabel.setForeground(new Color(46, 204, 113));
                                        }
                                    });
                                })
                                .exceptionally(ex -> {
                                    api.logging().raiseErrorEvent("Arjun探测异常: " + ex.getMessage());
                                    SwingUtilities.invokeLater(() -> {
                                        statusLabel.setText("❌ Arjun 探测异常");
                                        statusLabel.setForeground(new Color(231, 76, 60));
                                    });
                                    return null;
                                });
                        } else {
                            arjunTask.run();
                        }
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("启动Arjun探测时出错: " + e.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("❌ 启动Arjun探测失败");
                            statusLabel.setForeground(new Color(231, 76, 60));
                        });
                    }
                });
            } else {
                // ✅ 直接进行Arjun参数探测（不先探测接口）
                if (manualSource) {
                    processManualTargets(manualTargets, true, false);
                } else {
                    arjunTask.run();
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(panel, 
                "启动 Arjun 参数探测时出错:\n" + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startInterfaceDiscovery() {
        try {
            interfaceScanButton.setEnabled(false);
            statusLabel.setText("🔍 正在执行接口探测...");
            statusLabel.setForeground(new Color(52, 152, 219));
            progressBar.setIndeterminate(true);
            
            CompletableFuture<Void> task;
            if (sourceManualRadio.isSelected()) {
                // ✅ 手动添加模式：弹出输入框让用户输入URL
                List<String> manualTargets = promptManualTargets();
                if (manualTargets == null || manualTargets.isEmpty()) {
                    interfaceScanButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    statusLabel.setText("ℹ️ 已取消接口探测");
                    statusLabel.setForeground(Color.GRAY);
                    return;
                }
                task = processManualTargets(manualTargets, false);
            } else if (sourceAutoRadio.isSelected()) {
                // ✅ 自动采集模式：从Proxy实时流量触发
                task = runAutoInterfaceDiscovery();
            } else {
                // ✅ sourceNoneRadio被选中：使用已有采集数据，弹出选择对话框
                ScanTarget target = promptScanTarget("接口探测");
                if (target == null) {
                    // 用户取消选择
                    interfaceScanButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    statusLabel.setText("ℹ️ 已取消接口探测");
                    statusLabel.setForeground(Color.GRAY);
                    return;
                }
                // ✅ 根据选择的目标创建任务
                task = CompletableFuture.runAsync(() -> {
                    if (target.isMainDomain()) {
                        triggerInterfaceDiscoveryForMainDomain(target.getMainDomain());
                    } else {
                        triggerInterfaceDiscoveryForHost(target.getMainDomain(), target.getHost());
                    }
                });
            }
            
            task.whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
                interfaceScanButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                
                if (throwable != null) {
                    statusLabel.setText("❌ 接口探测失败");
                        statusLabel.setForeground(new Color(231, 76, 60));
                        JOptionPane.showMessageDialog(panel, 
                        "接口探测执行失败:\n" + throwable.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                } else {
                    statusLabel.setText("✅ 接口探测完成 - 接口信息已更新");
                    statusLabel.setForeground(new Color(46, 204, 113));
                    refreshCollectedData();
                }
            }));
        } catch (Exception ex) {
            interfaceScanButton.setEnabled(true);
            progressBar.setIndeterminate(false);
            statusLabel.setText("❌ 接口探测失败");
            statusLabel.setForeground(new Color(231, 76, 60));
            JOptionPane.showMessageDialog(panel, 
                "接口探测执行失败:\n" + ex.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private CompletableFuture<Void> runAutoInterfaceDiscovery() {
        return realtimeScanner.triggerInterfaceDiscovery().thenAccept(stats -> {});
    }
    
    private List<String> promptManualTargets() {
        JTextArea inputArea = new JTextArea(8, 40);
        inputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        inputArea.setText("# 在此输入要手动添加的目标URL，每行一个\n" +
                         "# 系统会自动检查这些URL是否已经被探测过\n" +
                         "https://example.com/api/v1");
        
        int result = JOptionPane.showConfirmDialog(
            panel,
            new JScrollPane(inputArea),
            "手动添加目标",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        
        String[] lines = inputArea.getText().split("\\r?\\n");
        List<String> validUrls = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.startsWith("http")) {
                validUrls.add(trimmed);
            }
        }
        
        if (validUrls.isEmpty()) {
            JOptionPane.showMessageDialog(panel,
                "没有找到有效的URL",
                "提示",
                JOptionPane.WARNING_MESSAGE);
            return null;
        }
        
        return validUrls;
    }
    
    private CompletableFuture<Void> processManualTargets(List<String> urls, boolean runArjun) {
        return processManualTargets(urls, runArjun, false);
    }
    
    private CompletableFuture<Void> processManualTargets(List<String> urls, boolean runArjun, boolean interfaceDiscoveryFirst) {
        if (urls == null || urls.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            for (String url : urls) {
                if (runArjun) {
                    activeScanner.getRealtimeScanner().triggerManualEndpointScan(url, true, interfaceDiscoveryFirst);
                } else {
                    activeScanner.getRealtimeScanner().triggerManualEndpointScan(url, false, false);
                }
            }
        });
    }
    
    private Boolean askInterfaceBeforeArjun(boolean manualSource) {
        String title = manualSource ? "手动目标 Arjun 探测" : "Arjun 参数探测";
        String message = manualSource
            ? "是否先对手动添加的接口执行一次存活检测，再进行参数探测？"
            : "是否在执行 Arjun 参数探测前，先刷新一次接口数据？";
        Object[] options = {"先接口探测再参数", "直接参数探测", "取消"};
        int option = JOptionPane.showOptionDialog(panel,
            message,
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        if (option == 0) return true;
        if (option == 1) return false;
        return null;
    }
    
    private void executeArjunScan(boolean manualSource, List<String> manualTargets, boolean autoSource) {
        arjunScanButton.setEnabled(false);
        statusLabel.setText("✨ 正在执行 Arjun 参数探测...");
        statusLabel.setForeground(new Color(155, 89, 182));
        progressBar.setIndeterminate(true);
        
        CompletableFuture<Void> arjunTask;
        if (manualSource) {
            arjunTask = processManualTargets(manualTargets, true);
        } else if (autoSource) {
            arjunTask = runAutoArjunScan();
        } else {
            // sourceNoneRadio被选中，使用已有采集数据
            arjunTask = runArjunScanFromCollectedData();
        }
        
        arjunTask.whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
            arjunScanButton.setEnabled(true);
                progressBar.setIndeterminate(false);
            progressBar.setValue(100);
                
            if (throwable != null) {
                statusLabel.setText("❌ Arjun 探测失败");
                statusLabel.setForeground(new Color(231, 76, 60));
                JOptionPane.showMessageDialog(panel,
                    "Arjun 参数探测执行失败:\n" + throwable.getMessage(), 
                    "错误", JOptionPane.ERROR_MESSAGE);
            } else {
                int currentScans = Integer.parseInt(
                    arjunScansLabel.getText().replaceAll("[^0-9]", ""));
                arjunScansLabel.setText("探测次数: " + (currentScans + 1));
                statusLabel.setText("✅ Arjun 参数探测完成");
                statusLabel.setForeground(new Color(46, 204, 113));
                refreshCollectedData();
            }
        }));
    }
    
    private CompletableFuture<Void> runAutoArjunScan() {
        boolean isRealtimeMode = realtimeModeRadio.isSelected();
        return CompletableFuture.runAsync(() -> {
            if (isRealtimeMode) {
                api.logging().raiseInfoEvent("从Proxy实时流量触发Arjun探测");
                activeScanner.getRealtimeScanner().triggerArjunScanFromProxy();
            } else {
                api.logging().raiseInfoEvent("从SiteMap历史流量触发Arjun探测");
                activeScanner.getRealtimeScanner().triggerManualArjunScan();
            }
        });
    }
    
    /**
     * ✅ 从已有采集数据触发Arjun探测（接口来源选择"无"时使用）
     * 支持选择子域名或主域名进行探测
     */
    private CompletableFuture<Void> runArjunScanFromCollectedData() {
        // ✅ 在UI线程中弹出选择对话框
        ScanTarget target = promptScanTarget("Arjun 参数探测");
        if (target == null) {
            return CompletableFuture.completedFuture(null); // 用户取消
        }
        
        return CompletableFuture.runAsync(() -> {
            if (target.isMainDomain()) {
                // ✅ 按主域名探测（包含所有子域名）
                api.logging().raiseInfoEvent("从已有采集数据触发Arjun探测: 主域名 " + target.getMainDomain());
                activeScanner.getRealtimeScanner().triggerArjunForMainDomain(target.getMainDomain());
            } else {
                // ✅ 按子域名探测
                api.logging().raiseInfoEvent("从已有采集数据触发Arjun探测: 子域名 " + target.getHost() + " (主域名: " + target.getMainDomain() + ")");
                activeScanner.getRealtimeScanner().triggerArjunForHost(target.getMainDomain(), target.getHost());
            }
        });
    }
    
    
    /**
     * ✅ 弹出选择对话框，让用户选择探测目标（子域名或主域名）
     */
    private ScanTarget promptScanTarget(String scanType) {
        try {
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            if (collector == null) {
                JOptionPane.showMessageDialog(panel,
                    "参数收集器未初始化",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
                return null;
            }
            
            Set<String> mainDomains = collector.getAllMainDomains();
            if (mainDomains.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                    "没有已收集的数据，请先收集一些流量数据",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
                return null;
            }
            
            // ✅ 创建选择对话框
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(panel), 
                "选择探测目标 - " + scanType, true);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.setSize(500, 400);
            
            // ✅ 创建选择面板
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            
            // ✅ 单选按钮组：主域名 vs 子域名
            ButtonGroup targetGroup = new ButtonGroup();
            JRadioButton mainDomainRadio = new JRadioButton("按主域名探测（包含所有子域名）", true);
            JRadioButton hostRadio = new JRadioButton("按子域名探测");
            targetGroup.add(mainDomainRadio);
            targetGroup.add(hostRadio);
            
            JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            radioPanel.add(mainDomainRadio);
            radioPanel.add(hostRadio);
            
            // ✅ 主域名选择列表
            DefaultListModel<String> mainDomainModel = new DefaultListModel<>();
            for (String md : mainDomains) {
                Set<String> hosts = collector.getHostsForMainDomain(md);
                Set<String> endpoints = collector.getEndpointsForMainDomain(md);
                Set<String> params = collector.getParametersForMainDomain(md);
                mainDomainModel.addElement(String.format("%s (%d子域名, %d接口, %d参数)", 
                    md, hosts.size(), endpoints.size(), params.size()));
            }
            JList<String> mainDomainList = new JList<>(mainDomainModel);
            mainDomainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            mainDomainList.setVisibleRowCount(8);
            JScrollPane mainDomainScroll = new JScrollPane(mainDomainList);
            mainDomainScroll.setBorder(BorderFactory.createTitledBorder("选择主域名"));
            
            // ✅ 子域名选择列表
            DefaultListModel<String> hostModel = new DefaultListModel<>();
            JList<String> hostList = new JList<>(hostModel);
            hostList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            hostList.setVisibleRowCount(8);
            JScrollPane hostScroll = new JScrollPane(hostList);
            hostScroll.setBorder(BorderFactory.createTitledBorder("选择子域名"));
            
            // ✅ 当选择主域名时，更新子域名列表
            mainDomainList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && mainDomainList.getSelectedIndex() >= 0) {
                    String selectedMainDomain = mainDomains.toArray(new String[0])[mainDomainList.getSelectedIndex()];
                    hostModel.clear();
                    Set<String> hosts = collector.getHostsForMainDomain(selectedMainDomain);
                    Set<ParameterCollector.EndpointKey> allEndpointKeys = 
                        collector.getEndpointKeysForMainDomain(selectedMainDomain);
                    
                    for (String host : hosts) {
                        // ✅ 统计该子域名的接口数量
                        int hostEndpointCount = 0;
                        for (ParameterCollector.EndpointKey epKey : allEndpointKeys) {
                            if (epKey.host.equals(host)) {
                                hostEndpointCount++;
                            }
                        }
                        Set<String> params = collector.getParametersForHost(host);
                        hostModel.addElement(String.format("%s (%d接口, %d参数)", 
                            host, hostEndpointCount, params.size()));
                    }
                }
            });
            
            // ✅ 默认选择第一个主域名
            if (mainDomainList.getModel().getSize() > 0) {
                mainDomainList.setSelectedIndex(0);
            }
            
            // ✅ 切换单选按钮时，启用/禁用相应的列表
            mainDomainRadio.addActionListener(e -> {
                mainDomainList.setEnabled(true);
                hostList.setEnabled(false);
            });
            hostRadio.addActionListener(e -> {
                mainDomainList.setEnabled(false);
                hostList.setEnabled(true);
                if (hostList.getModel().getSize() > 0 && hostList.getSelectedIndex() < 0) {
                    hostList.setSelectedIndex(0);
                }
            });
            
            // ✅ 初始状态
            mainDomainList.setEnabled(true);
            hostList.setEnabled(false);
            
            // ✅ 布局
            JPanel listPanel = new JPanel(new GridLayout(1, 2, 10, 0));
            listPanel.add(mainDomainScroll);
            listPanel.add(hostScroll);
            
            mainPanel.add(radioPanel, BorderLayout.NORTH);
            mainPanel.add(listPanel, BorderLayout.CENTER);
            
            // ✅ 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okButton = new JButton("确定");
            JButton cancelButton = new JButton("取消");
            
            okButton.addActionListener(e -> dialog.dispose());
            cancelButton.addActionListener(e -> {
                mainDomainList.clearSelection();
                hostList.clearSelection();
                dialog.dispose();
            });
            
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            dialog.add(mainPanel);
            dialog.setLocationRelativeTo(panel);
            dialog.setVisible(true);
            
            // ✅ 检查用户选择
            if (mainDomainRadio.isSelected() && mainDomainList.getSelectedIndex() >= 0) {
                String selectedMainDomain = mainDomains.toArray(new String[0])[mainDomainList.getSelectedIndex()];
                return new ScanTarget(selectedMainDomain, null, true);
            } else if (hostRadio.isSelected() && hostList.getSelectedIndex() >= 0 && mainDomainList.getSelectedIndex() >= 0) {
                String selectedMainDomain = mainDomains.toArray(new String[0])[mainDomainList.getSelectedIndex()];
                Set<String> hosts = collector.getHostsForMainDomain(selectedMainDomain);
                String selectedHost = hosts.toArray(new String[0])[hostList.getSelectedIndex()];
                return new ScanTarget(selectedMainDomain, selectedHost, false);
            }
            
            return null; // 用户取消
            
                } catch (Exception e) {
            api.logging().raiseErrorEvent("显示选择对话框失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * ✅ 扫描目标（内部类）
     */
    private static class ScanTarget {
        private final String mainDomain;
        private final String host;
        private final boolean isMainDomain;
        
        public ScanTarget(String mainDomain, String host, boolean isMainDomain) {
            this.mainDomain = mainDomain;
            this.host = host;
            this.isMainDomain = isMainDomain;
        }
        
        public String getMainDomain() { return mainDomain; }
        public String getHost() { return host; }
        public boolean isMainDomain() { return isMainDomain; }
    }
    
    /**
     * ✅ 按主域名触发接口探测（包含所有子域名）
     */
    private void triggerInterfaceDiscoveryForMainDomain(String mainDomain) {
        try {
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            Set<String> hosts = collector.getHostsForMainDomain(mainDomain);
            
            api.logging().raiseInfoEvent(String.format(
                "开始对主域名 %s 进行接口探测（包含 %d 个子域名）", mainDomain, hosts.size()
            ));
            
            // ✅ 对每个子域名进行接口探测
            for (String host : hosts) {
                triggerInterfaceDiscoveryForHost(mainDomain, host);
            }
            
            // ✅ 更新UI状态（异步完成后）
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                statusLabel.setText("✅ 主域名 " + mainDomain + " 接口探测完成");
                statusLabel.setForeground(new Color(39, 174, 96));
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("按主域名触发接口探测失败: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                statusLabel.setText("❌ 接口探测失败: " + e.getMessage());
                statusLabel.setForeground(new Color(231, 76, 60));
            });
        }
    }
    
    /**
     * ✅ 按子域名触发接口探测
     */
    private void triggerInterfaceDiscoveryForHost(String mainDomain, String host) {
        try {
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            Set<ParameterCollector.EndpointKey> endpointKeys = 
                collector.getEndpointKeysForMainDomain(mainDomain);
            
            // ✅ 过滤出该子域名的接口
            List<ParameterCollector.EndpointKey> hostEndpoints = new ArrayList<>();
            for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                if (epKey.host.equals(host)) {
                    hostEndpoints.add(epKey);
                }
            }
            
            api.logging().raiseInfoEvent(String.format(
                "开始对子域名 %s 进行接口探测（%d 个接口）", host, hostEndpoints.size()
            ));
            
            if (hostEndpoints.isEmpty()) {
                api.logging().raiseInfoEvent("⚠️ 子域名 " + host + " 没有可探测的接口");
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    statusLabel.setText("⚠️ 子域名 " + host + " 没有可探测的接口");
                    statusLabel.setForeground(new Color(241, 196, 15));
                });
                return;
            }
            
            // ✅ 修复：对每个接口进行完整的接口探测（包含随机路径验证）
            final int[] sentCount = {0}; // 使用数组来避免final限制
            for (ParameterCollector.EndpointKey epKey : hostEndpoints) {
                HttpRequest template = collector.getEndpointTemplate(mainDomain, epKey);
                if (template != null) {
                    try {
                        // ✅ 从请求模板中获取完整URL
                        String url = template.url();
                        if (url != null && !url.isEmpty()) {
                            // ✅ 调用完整的接口探测逻辑（包含随机路径验证）
                            // 参数说明：url, runArjun=false（仅接口探测）, interfaceDiscoveryFirst=false（不先探测接口）
                            realtimeScanner.triggerManualEndpointScan(url, false, false);
                            sentCount[0]++;
                            // 添加小延迟，避免请求过快
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("接口探测请求失败: " + epKey + " - " + e.getMessage());
                    }
                }
            }
            
            // ✅ 更新UI状态
            final int finalSentCount = sentCount[0];
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                statusLabel.setText("✅ 子域名 " + host + " 接口探测完成（已发送 " + finalSentCount + " 个请求，包含随机路径验证）");
                statusLabel.setForeground(new Color(39, 174, 96));
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("按子域名触发接口探测失败: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                statusLabel.setText("❌ 接口探测失败: " + e.getMessage());
                statusLabel.setForeground(new Color(231, 76, 60));
            });
        }
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
            arjunResultsLabel.setText("发现参数: 0");
            
            // ✅ 修复：根据当前开关状态设置正确的状态文本
            boolean enabled = masterEnableToggle.isSelected();
            if (enabled) {
                statusLabel.setText("🟢 主动探测已启用 - 正在监听流量...");
                statusLabel.setForeground(new Color(46, 204, 113));
            } else {
                statusLabel.setText("⚫ 主动探测已禁用");
                statusLabel.setForeground(Color.GRAY);
            }
        }
    }
    
    /**
     * ✅ 清空Arjun扫描缓存
     * 功能：清空已扫描的参数记录，使Arjun可以重新扫描之前扫描过的端点
     */
    private void clearArjunCache() {
        int result = JOptionPane.showConfirmDialog(
            panel,
            "确定要清空Arjun扫描缓存吗？\n这将允许Arjun重新扫描之前已扫描过的端点。",
            "确认清空缓存",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // 调用 ParameterManager 的清空方法
            if (realtimeScanner != null) {
                // RealtimeScanner 有 ParameterManager 实例
                realtimeScanner.clearArjunCache();
                
                JOptionPane.showMessageDialog(
                    panel,
                    "✅ Arjun扫描缓存已清空！\n\n" +
                    "说明：\n" +
                    "• Arjun已扫描端点记录已清空\n" +
                    "• 之前扫描过的端点现在可以重新扫描\n" +
                    "• 详细信息请查看Burp日志",
                    "清空成功",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                api.logging().raiseInfoEvent("✅ 用户手动清空了Arjun扫描缓存");
            }
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
     * ✅ 修复：总开关只控制主动探测，被动参数收集始终进行
     */
    private void applyMasterSwitchState(boolean enabled) {
        if (enabled) {
            // ✅ 启用主动探测（被动参数收集始终进行，不受控制）
            activeScanner.getRealtimeScanner().startRealtimeScanning();
            statusLabel.setText("🟢 主动探测已启用 - 正在监听流量...");
            statusLabel.setForeground(new Color(46, 204, 113));
            api.logging().logToOutput("✅ 主动探测已启用（被动参数收集持续进行）");
            
            // 启用控制组件
            arjunScanButton.setEnabled(true);
            interfaceScanButton.setEnabled(true);
            realtimeModeRadio.setEnabled(true);
            manualModeRadio.setEnabled(true);
            sourceNoneRadio.setEnabled(true);
            sourceManualRadio.setEnabled(true);
            sourceAutoRadio.setEnabled(true);
        } else {
            // ✅ 禁用主动探测（被动参数收集继续进行）
            activeScanner.getRealtimeScanner().stopRealtimeScanning();
            
            // 停止实时监听定时器
            if (realtimeArjunTimer != null && realtimeArjunTimer.isRunning()) {
                realtimeArjunTimer.stop();
            }
            
            statusLabel.setText("⚫ 主动探测已禁用 - 被动收集持续进行");
            statusLabel.setForeground(Color.GRAY);
            api.logging().logToOutput("⚫ 主动探测已禁用（被动参数收集持续进行）");
            
            // 禁用控制组件（但保留查看/清空按钮可用）
            arjunScanButton.setEnabled(false);
            interfaceScanButton.setEnabled(false);
            realtimeModeRadio.setEnabled(false);
            manualModeRadio.setEnabled(false);
            sourceNoneRadio.setEnabled(false);
            sourceManualRadio.setEnabled(false);
            sourceAutoRadio.setEnabled(false);
            // ✅ 修复：刷新、清空、查看详情等按钮应该始终可用（只是查看/清空数据）
            // refreshDataButton, clearResultsButton, viewDetailsButton, clearCacheButton 保持可用
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
    
    /**
     * ✅ 显示域名的详细信息
     */
    private void showDomainDetails(int row) {
        try {
            // ✅ 修改：第一列是子域名，第二列是主域名
            String selectedHost = (String) collectedDataTableModel.getValueAt(row, 0);
            String mainDomain = (String) collectedDataTableModel.getValueAt(row, 1);
            
            if (activeScanner.getRealtimeScanner() == null) {
                return;
            }
            
            var realtimeScanner = activeScanner.getRealtimeScanner();
            ParameterCollector parameterCollector = realtimeScanner.getParameterCollector();
            
            // ✅ 修改：只获取选中子域名的信息
            // 1. 获取该子域名的参数
            Set<String> hostParameters = parameterCollector.getParametersForHost(selectedHost);
            
            // 2. 获取该子域名的接口（通过过滤 EndpointKey）
            Set<ParameterCollector.EndpointKey> allEndpointKeys = 
                parameterCollector.getEndpointKeysForMainDomain(mainDomain);
            Set<String> hostEndpoints = new HashSet<>();
            for (ParameterCollector.EndpointKey epKey : allEndpointKeys) {
                if (epKey.host.equals(selectedHost)) {
                    hostEndpoints.add(epKey.endpoint);
                }
            }
            
            // 3. 关键词按主域名存储，这里显示主域名的关键词（作为参考）
            Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
            
            // 构建详情文本
            StringBuilder details = new StringBuilder();
            details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            details.append("🌐 子域名: ").append(selectedHost).append("\n");
            details.append("📌 主域名: ").append(mainDomain).append("\n");
            details.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            
            // 接口列表（只显示该子域名的接口）
            details.append("🔗 接口路径 (").append(hostEndpoints.size()).append("个):\n");
            if (hostEndpoints.isEmpty()) {
                details.append("  - 无\n");
            } else {
                hostEndpoints.stream().limit(50).forEach(endpoint -> 
                    details.append("  • ").append(endpoint).append("\n"));
                if (hostEndpoints.size() > 50) {
                    details.append("  ... 还有 ").append(hostEndpoints.size() - 50).append(" 个\n");
                }
            }
            details.append("\n");
            
            // 参数列表（只显示该子域名的参数）
            details.append("🔑 参数名称 (").append(hostParameters.size()).append("个):\n");
            if (hostParameters.isEmpty()) {
                details.append("  - 无\n");
            } else {
                hostParameters.stream().limit(100).forEach(param -> 
                    details.append("  • ").append(param).append("\n"));
                if (hostParameters.size() > 100) {
                    details.append("  ... 还有 ").append(hostParameters.size() - 100).append(" 个\n");
                }
            }
            
            // 关键词列表（按主域名，作为参考信息）
            if (!keywords.isEmpty()) {
                details.append("\n");
                details.append("📝 关键词（主域名，参考）(").append(keywords.size()).append("个):\n");
                keywords.stream().limit(50).forEach(keyword -> 
                    details.append("  • ").append(keyword).append("\n"));
                if (keywords.size() > 50) {
                    details.append("  ... 还有 ").append(keywords.size() - 50).append(" 个\n");
                }
            }
            
            // ✅ 优化：创建自定义对话框，方便复制
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(panel), 
                "域名详细信息 - " + selectedHost, true);
            dialog.setLayout(new BorderLayout(10, 10));
            
            // 创建文本区域（可选择和复制）
            JTextArea textArea = new JTextArea(details.toString());
            textArea.setEditable(false);  // 不可编辑，但可选择
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            textArea.setCaretPosition(0);
            // ✅ 文本区域默认可以选择，用户可以通过Ctrl+A全选或鼠标选择
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(750, 550));
            scrollPane.setBorder(BorderFactory.createTitledBorder("详细信息（可全选复制）"));
            
            // ✅ 创建按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            
            // ✅ 复制全部按钮
            JButton copyAllButton = new JButton("📋 复制全部");
            copyAllButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            copyAllButton.setBackground(new Color(52, 152, 219));
            copyAllButton.setForeground(Color.WHITE);
            copyAllButton.setOpaque(true);
            copyAllButton.addActionListener(e -> {
                textArea.selectAll();
                textArea.copy();
                JOptionPane.showMessageDialog(dialog, 
                    "✅ 已复制到剪贴板", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            });
            
            // ✅ 复制参数列表按钮（使用子域名的参数）
            final Set<String> finalHostParameters = new HashSet<>(hostParameters);
            JButton copyParamsButton = new JButton("📋 复制参数");
            copyParamsButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            copyParamsButton.setBackground(new Color(155, 89, 182));
            copyParamsButton.setForeground(Color.WHITE);
            copyParamsButton.setOpaque(true);
            copyParamsButton.addActionListener(e -> {
                String paramsText = String.join("\n", finalHostParameters);
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(paramsText);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                JOptionPane.showMessageDialog(dialog, 
                    "✅ 参数列表已复制到剪贴板 (" + finalHostParameters.size() + "个)", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            });
            
            // ✅ 复制接口列表按钮（使用子域名的接口）
            final Set<String> finalHostEndpoints = new HashSet<>(hostEndpoints);
            JButton copyEndpointsButton = new JButton("📋 复制接口");
            copyEndpointsButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            copyEndpointsButton.setBackground(new Color(26, 188, 156));
            copyEndpointsButton.setForeground(Color.WHITE);
            copyEndpointsButton.setOpaque(true);
            copyEndpointsButton.addActionListener(e -> {
                String endpointsText = String.join("\n", finalHostEndpoints);
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(endpointsText);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                JOptionPane.showMessageDialog(dialog, 
                    "✅ 接口列表已复制到剪贴板 (" + finalHostEndpoints.size() + "个)", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            });
            
            // ✅ 关闭按钮
            JButton closeButton = new JButton("关闭");
            closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            closeButton.addActionListener(e -> dialog.dispose());
            
            buttonPanel.add(copyParamsButton);
            buttonPanel.add(copyEndpointsButton);
            buttonPanel.add(copyAllButton);
            buttonPanel.add(closeButton);
            
            // 添加到对话框
            dialog.add(scrollPane, BorderLayout.CENTER);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            
            // 设置对话框属性
            dialog.pack();
            dialog.setLocationRelativeTo(panel);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            
            // ✅ 添加快捷键：Ctrl+A 全选，Ctrl+C 复制
            textArea.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK), 
                "selectAll");
            textArea.getActionMap().put("selectAll", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    textArea.selectAll();
                }
            });
            
            dialog.setVisible(true);
            
        } catch (Exception ex) {
            api.logging().raiseErrorEvent("显示域名详情失败: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                panel,
                "无法显示详情: " + ex.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    // ========== Arjun结果显示 ==========
    
    /**
     * 注册Arjun结果监听器
     */
    private void registerArjunResultListener(com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        realtimeScanner.addArjunResultListener(new com.xprobe.scanner.active.RealtimeScannerRefactored.ArjunResultListener() {
            @Override
            public void onArjunResultFound(String mainDomain, String endpoint, Set<String> foundParameters, 
                                          String parameterType, long timestamp) {
                // 在UI线程中更新表格
                SwingUtilities.invokeLater(() -> {
                    addArjunResultToTable(mainDomain, endpoint, foundParameters, parameterType, timestamp);
                });
            }
        });
    }
    
    /**
     * ✅ 添加接口探测结果到表格
     */
    public void addInterfaceDiscoveryResult(String mainDomain, String endpoint, String method, 
                                           String contentType, boolean exists, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            // 格式化时间
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            String timeStr = sdf.format(new java.util.Date(timestamp));
            
            // 格式化接口信息
            String displayContentType = contentType != null ? contentType : "N/A";
            String interfaceInfo = String.format("%s %s (%s)", method, endpoint, displayContentType);
            
            // 验证状态
            String verifyStatus = exists ? "✅ 接口存在" : "❌ 接口不存在";
            
            // 添加到表格：探测类型, 目标域名, 接口, 发现参数, 参数类型, 验证状态, 探测时间
            arjunResultTableModel.addRow(new Object[]{
                "接口探测",  // 探测类型
                mainDomain,
                interfaceInfo,
                "-",  // 发现参数（接口探测无参数）
                "-",  // 参数类型（接口探测无参数类型）
                verifyStatus,
                timeStr
            });
        });
    }
    
    /**
     * 将Arjun结果添加到表格
     */
    private void addArjunResultToTable(String mainDomain, String endpoint, Set<String> foundParameters, 
                                       String parameterType, long timestamp) {
        // 格式化时间
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        String timeStr = sdf.format(new java.util.Date(timestamp));
        
        // 参数列表（逗号分隔）
        String paramsStr = foundParameters.isEmpty() ? "" : String.join(", ", foundParameters);
        
        // 参数数量
        int paramCount = foundParameters.size();
        String displayParams;
        if (paramCount == 0) {
            displayParams = "-";  // 没有发现参数
        } else if (paramCount <= 5) {
            displayParams = paramsStr;
        } else {
            displayParams = paramsStr.substring(0, Math.min(50, paramsStr.length())) + "... (共" + paramCount + "个)";
        }
        
        // 验证状态
        String verifyStatus = paramCount > 0 ? "✅ 已验证" : "⚠️ 未发现参数";
        
        // ✅ 添加到表格：探测类型, 目标域名, 接口, 发现参数, 参数类型, 验证状态, 探测时间
        arjunResultTableModel.addRow(new Object[]{
            "参数探测",  // 探测类型
            mainDomain,
            endpoint,
            displayParams,
            parameterType,
            verifyStatus,
            timeStr
        });
        
        // ✅ 更新统计：只统计参数探测的结果（包括未发现参数的）
        int paramScanCount = 0;
        for (int i = 0; i < arjunResultTableModel.getRowCount(); i++) {
            Object type = arjunResultTableModel.getValueAt(i, 0);
            if ("参数探测".equals(type)) {
                paramScanCount++;
            }
        }
        arjunResultsLabel.setText("发现参数: " + paramScanCount);
        
        // 日志
        if (paramCount > 0) {
        api.logging().raiseInfoEvent(String.format(
            "✨ Arjun发现参数: %s%s - 参数: %s (类型: %s)",
            mainDomain, endpoint, paramsStr, parameterType
        ));
        } else {
            api.logging().raiseDebugEvent(String.format(
                "Arjun扫描完成: %s%s - 未发现参数 (类型: %s)",
                mainDomain, endpoint, parameterType
            ));
        }
    }
    
    /**
     * 清理资源（停止Timer）
     * ✅ 修复：防止Timer泄漏
     */
    public void cleanup() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
        
        if (realtimeArjunTimer != null) {
            realtimeArjunTimer.stop();
            realtimeArjunTimer = null;
        }
        
        api.logging().raiseDebugEvent("ActiveProbeTab资源已清理");
    }
    
    private void handleInterfaceSourceSelection(String mode) {
        switch (mode) {
            case "none":
                statusLabel.setText("⏸️ 接口来源未指定，将使用已有采集数据");
                statusLabel.setForeground(Color.GRAY);
                break;
            case "manual":
                statusLabel.setText("📝 手动接口模式 - 点击按钮会弹出目标输入框");
                statusLabel.setForeground(new Color(46, 134, 193));
                break;
            case "auto":
                statusLabel.setText("🟢 自动采集已启用，等待新的流量数据...");
                statusLabel.setForeground(new Color(46, 204, 113));
                break;
            default:
                break;
        }
    }
    
    /**
     * ✅ 显示右键菜单（支持删除和按子域名探测）
     */
    private void showContextMenu(int x, int y, int row) {
        JPopupMenu popupMenu = new JPopupMenu();
        
        // 获取选中的行数据
        String host = (String) collectedDataTableModel.getValueAt(row, 0);
        String mainDomain = (String) collectedDataTableModel.getValueAt(row, 1);
        
        // ========== 接口探测菜单项 ==========
        JMenuItem interfaceProbeHostItem = new JMenuItem("🔍 对此子域名进行接口探测");
        interfaceProbeHostItem.addActionListener(e -> {
            if (!masterEnableToggle.isSelected()) {
                JOptionPane.showMessageDialog(panel,
                    "请先启用主动探测",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("将对子域名 %s 进行接口探测\n\n" +
                            "主域名: %s\n\n" +
                            "是否继续？", host, mainDomain),
                "确认接口探测",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                CompletableFuture.runAsync(() -> {
                    triggerInterfaceDiscoveryForHost(mainDomain, host);
                });
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("🔍 正在对子域名 " + host + " 进行接口探测...");
                    statusLabel.setForeground(new Color(52, 152, 219));
                    progressBar.setIndeterminate(true);
                });
            }
        });
        popupMenu.add(interfaceProbeHostItem);
        
        JMenuItem interfaceProbeMainDomainItem = new JMenuItem("🔍 对此主域名进行接口探测（包含所有子域名）");
        interfaceProbeMainDomainItem.addActionListener(e -> {
            if (!masterEnableToggle.isSelected()) {
                JOptionPane.showMessageDialog(panel,
                    "请先启用主动探测",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("将对主域名 %s 进行接口探测\n\n" +
                            "将包含该主域名下的所有子域名。\n\n" +
                            "是否继续？", mainDomain),
                "确认接口探测",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                CompletableFuture.runAsync(() -> {
                    triggerInterfaceDiscoveryForMainDomain(mainDomain);
                });
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("🔍 正在对主域名 " + mainDomain + " 进行接口探测...");
                    statusLabel.setForeground(new Color(52, 152, 219));
                    progressBar.setIndeterminate(true);
                });
            }
        });
        popupMenu.add(interfaceProbeMainDomainItem);
        
        popupMenu.addSeparator();
        
        // ========== 参数探测（Arjun）菜单项 ==========
        JMenuItem arjunProbeHostItem = new JMenuItem("✨ 对此子域名进行参数探测（使用主域名下所有接口和参数）");
        arjunProbeHostItem.addActionListener(e -> {
            if (!masterEnableToggle.isSelected()) {
                JOptionPane.showMessageDialog(panel,
                    "请先启用主动探测",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("将对子域名 %s 进行参数探测\n\n" +
                            "将使用主域名 %s 下所有收集的接口和参数进行探测。\n\n" +
                            "是否继续？", host, mainDomain),
                "确认参数探测",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                // ✅ 对子域名进行探测（使用主域名下所有接口和参数）
                CompletableFuture.runAsync(() -> {
                    activeScanner.getRealtimeScanner().triggerArjunForHost(mainDomain, host);
                });
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✨ 正在对子域名 " + host + " 进行参数探测...");
                    statusLabel.setForeground(new Color(155, 89, 182));
                    progressBar.setIndeterminate(true);
                });
            }
        });
        popupMenu.add(arjunProbeHostItem);
        
        JMenuItem arjunProbeMainDomainItem = new JMenuItem("✨ 对此主域名进行参数探测（包含所有子域名）");
        arjunProbeMainDomainItem.addActionListener(e -> {
            if (!masterEnableToggle.isSelected()) {
                JOptionPane.showMessageDialog(panel,
                    "请先启用主动探测",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("将对主域名 %s 进行参数探测\n\n" +
                            "将对该主域名下的所有子域名进行探测。\n\n" +
                            "是否继续？", mainDomain),
                "确认参数探测",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                CompletableFuture.runAsync(() -> {
                    activeScanner.getRealtimeScanner().triggerArjunForMainDomain(mainDomain);
                });
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("✨ 正在对主域名 " + mainDomain + " 进行参数探测...");
                    statusLabel.setForeground(new Color(155, 89, 182));
                    progressBar.setIndeterminate(true);
                });
            }
        });
        popupMenu.add(arjunProbeMainDomainItem);
        
        popupMenu.addSeparator();
        
        // 菜单项：删除子域名数据
        JMenuItem deleteHostItem = new JMenuItem("🗑️ 删除此子域名数据");
        deleteHostItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("确定要删除子域名 %s 的所有收集数据吗？\n\n" +
                            "主域名: %s\n" +
                            "此操作不可恢复！", host, mainDomain),
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                // ✅ 同时删除内存和磁盘数据
                boolean deleted = activeScanner.getRealtimeScanner()
                    .getParameterCollector()
                    .clearHost(mainDomain, host);
                
                // ✅ 同步删除磁盘数据
                if (deleted) {
                    parameterDataStorage.deleteHost(mainDomain, host);
                    JOptionPane.showMessageDialog(panel,
                        "✅ 子域名数据已删除（内存和磁盘）",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
                    refreshCollectedData();
                } else {
                    JOptionPane.showMessageDialog(panel,
                        "删除失败：未找到该子域名数据",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        popupMenu.add(deleteHostItem);
        
        // 菜单项：删除主域名数据
        JMenuItem deleteMainDomainItem = new JMenuItem("🗑️ 删除主域名 " + mainDomain + " 的所有数据");
        deleteMainDomainItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel,
                String.format("确定要删除主域名 %s 的所有收集数据吗？\n\n" +
                            "包括所有子域名、接口和参数。\n" +
                            "此操作不可恢复！", mainDomain),
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                // ✅ 同时删除内存和磁盘数据
                boolean deleted = activeScanner.getRealtimeScanner()
                    .getParameterCollector()
                    .clearMainDomain(mainDomain);
                
                // ✅ 同步删除磁盘数据
                if (deleted) {
                    parameterDataStorage.deleteMainDomain(mainDomain);
                    JOptionPane.showMessageDialog(panel,
                        "✅ 主域名数据已删除（内存和磁盘）",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE);
                    refreshCollectedData();
                } else {
                    JOptionPane.showMessageDialog(panel,
                        "删除失败：未找到该主域名数据",
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        popupMenu.add(deleteMainDomainItem);
        
        popupMenu.addSeparator();
        
        // ✅ 菜单项：一键清空所有数据
        JMenuItem clearAllItem = new JMenuItem("🗑️ 一键清空所有收集数据");
        clearAllItem.addActionListener(e -> clearAllCollectedData());
        popupMenu.add(clearAllItem);
        
        popupMenu.show(collectedDataTable, x, y);
    }
    
    /**
     * ✅ 保存收集的数据到磁盘
     */
    private void saveCollectedData() {
        try {
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            if (collector == null) {
                JOptionPane.showMessageDialog(panel,
                    "参数收集器未初始化",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            parameterDataStorage.save(collector);
            JOptionPane.showMessageDialog(panel,
                "✅ 数据已保存到: " + parameterDataStorage.getDataFilePath(),
                "保存成功",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("保存数据失败: " + e.getMessage());
            JOptionPane.showMessageDialog(panel,
                "保存数据失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * ✅ 启动时自动恢复数据（如果存在保存的数据）
     * 在插件初始化完成后自动执行，无需用户操作
     */
    private void restoreCollectedDataOnStartup() {
        try {
            if (!parameterDataStorage.exists()) {
                api.logging().raiseDebugEvent("未找到保存的参数收集数据，跳过自动恢复");
                return; // 没有保存的数据，跳过恢复
            }
            
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            if (collector == null) {
                api.logging().raiseErrorEvent("参数收集器未初始化，无法恢复数据");
                return;
            }
            
            api.logging().raiseInfoEvent("🔄 检测到保存的数据，开始自动恢复参数收集数据...");
            
            // ✅ 自动恢复数据
            parameterDataStorage.restoreToCollector(collector, api);
            
            // 延迟刷新UI（确保初始化完成）
            SwingUtilities.invokeLater(() -> {
                refreshCollectedData();
                api.logging().raiseInfoEvent("✅ 参数收集数据自动恢复完成，UI已更新");
            });
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("启动时自动恢复数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ✅ 一键清空所有收集的数据
     */
    private void clearAllCollectedData() {
        int confirm = JOptionPane.showConfirmDialog(panel,
            "确定要清空所有收集的数据吗？\n\n" +
            "包括所有主域名、子域名、接口和参数。\n" +
            "此操作不可恢复！\n\n" +
            "是否继续？",
            "确认清空",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            ParameterCollector collector = realtimeScanner.getParameterCollector();
            if (collector == null) {
                return;
            }
            
            // 清空内存中的数据
            collector.clear();
            
            // 清空磁盘中的数据
            parameterDataStorage.clearAll();
            
            // 刷新UI
            refreshCollectedData();
            
            JOptionPane.showMessageDialog(panel,
                "✅ 所有数据已清空",
                "清空成功",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("清空数据失败: " + e.getMessage());
            JOptionPane.showMessageDialog(panel,
                "清空数据失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // Arjun配置（分块大小、超时、自定义字典）已移至配置中心统一管理
    // 用户可在「配置中心」→「Java原生Arjun配置」中进行配置
}


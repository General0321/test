package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;
import com.xprobe.scanner.config.ResponseComparisonConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求-响应配对编辑对话框
 */
public class PairEditorDialog extends JDialog {
    
    private RuleMatchPair pair;
    private MontoyaApi api;
    private boolean confirmed = false;
    
    // UI组件
    private JTextField labelField;
    private UnifiedHttpConfigPanel requestPanel;
    private UnifiedResponseConfigPanel responsePanel;
    private ResponseComparisonConfigPanelSimplified comparisonPanel;
    private JTabbedPane tabbedPane;
    
    // ✨ 高级配置UI组件
    private DefaultTableModel variableTableModel;
    private JTable variableTable;
    
    public PairEditorDialog(Window owner, MontoyaApi api, RuleMatchPair pair) {
        super(owner, "编辑配对", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.pair = pair != null ? pair : new RuleMatchPair();
        
        initComponents();
        loadFromPair();
        
        setSize(1000, 700);
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部 - 基本信息
        add(createHeaderPanel(), BorderLayout.NORTH);
        
        // 中间 - 标签页
        add(createTabbedPane(), BorderLayout.CENTER);
        
        // 底部 - 按钮
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建头部面板（简化版）
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setBackground(new Color(245, 248, 250));
        
        // 左侧：标题和ID
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("配置配对 #" + pair.getId());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        leftPanel.add(titleLabel);
        
        leftPanel.add(Box.createVerticalStrut(5));
        
        JLabel descLabel = new JLabel("设置检测漏洞的请求条件和响应匹配规则");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descLabel.setForeground(new Color(100, 100, 100));
        leftPanel.add(descLabel);
        
        panel.add(leftPanel, BorderLayout.WEST);
        
        // 中间：配对标签输入（最重要的字段）
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        centerPanel.setOpaque(false);
        
        JLabel labelLabel = new JLabel("配对名称:");
        labelLabel.setFont(labelLabel.getFont().deriveFont(Font.BOLD, 12f));
        centerPanel.add(labelLabel);
        
        labelField = new JTextField(25);
        labelField.setFont(labelField.getFont().deriveFont(Font.PLAIN, 12f));
        labelField.setToolTipText("例如：SQL时间盲注检测、XSS反射检测");
        centerPanel.add(labelField);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建标签页（简化清晰）
     */
    private JTabbedPane createTabbedPane() {
        tabbedPane = new JTabbedPane();
        
        // 1. 请求配置
        requestPanel = new UnifiedHttpConfigPanel(pair.getRequestConfig());
        JScrollPane requestScroll = new JScrollPane(requestPanel);
        tabbedPane.addTab("📤 请求配置", requestScroll);
        tabbedPane.setToolTipTextAt(0, "配置当前Pair的请求条件和注入点");
        
        // 2. 响应配置
        responsePanel = new UnifiedResponseConfigPanel(pair.getResponseConfig());
        JScrollPane responseScroll = new JScrollPane(responsePanel);
        tabbedPane.addTab("📥 响应配置", responseScroll);
        tabbedPane.setToolTipTextAt(1, "配置当前Pair的响应匹配规则");
        
        // 3. 跨Pair配置（所有Pair都可以配置）
        tabbedPane.addTab("🔗 跨Pair配置", createCrossPairConfigPanel());
        tabbedPane.setToolTipTextAt(2, "配置当前Pair与其他Pair的关联");
        
        return tabbedPane;
    }
    
    /**
     * 创建跨Pair配置面板（所有配置项平等展示）
     */
    private JPanel createCrossPairConfigPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        
        // 顶部说明
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        headerPanel.setBackground(new Color(250, 252, 255));
        
        JLabel titleLabel = new JLabel("🔗 跨Pair配置");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(new Color(70, 130, 180));
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        
        JLabel descLabel = new JLabel(
            "配置当前Pair与其他Pair的关联，用于检测时间盲注、布尔盲注、IDOR等漏洞"
        );
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 12f));
        descLabel.setForeground(new Color(100, 100, 100));
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.CENTER);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // 中间内容区域
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // 配置项1：对比哪个Pair
        contentPanel.add(createComparisonSection());
        contentPanel.add(Box.createVerticalStrut(20));
        
        // 配置项2：提取什么变量
        contentPanel.add(createVariableExtractionSection());
        
        contentPanel.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        return mainPanel;
    }
    
    /**
     * 创建"对比哪个Pair"配置区域
     */
    private JPanel createComparisonSection() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(150, 100, 200), 2),
                " 对比哪个Pair（comparisonConfig） ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 13),
                new Color(150, 100, 200)
            ),
            BorderFactory.createEmptyBorder(10, 15, 15, 15)
        ));
        panel.setBackground(Color.WHITE);
        
        // 说明
        JTextArea descArea = new JTextArea(
            "当前Pair的响应与哪个Pair的响应对比？\n\n" +
            "对比维度：\n" +
            "• 响应时间（RESPONSE_TIME）：用于时间盲注\n" +
            "• 响应内容（RESPONSE_BODY）：用于布尔盲注\n" +
            "• 响应长度（RESPONSE_LENGTH）：用于差异检测\n" +
            "• 状态码（STATUS_CODE）：用于权限检测\n\n" +
            "真实检测场景：\n" +
            "1. 时间盲注（HackerOne #1024984）：\n" +
            "   Pair 2: 对比 Pair 1 的响应时间，GREATER_THAN 4000ms\n" +
            "   Pair 3: 对比 Pair 2 的响应时间，GREATER_THAN 4000ms\n\n" +
            "2. 布尔盲注（HackerOne #1102591）：\n" +
            "   Pair 2: 对比 Pair 1 的响应内容，BODY_NOT_EQUALS（TRUE vs FALSE）"
        );
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBackground(new Color(252, 250, 255));
        descArea.setFont(descArea.getFont().deriveFont(Font.PLAIN, 12f));
        descArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(descArea, BorderLayout.NORTH);
        
        // 配置区域（嵌入简化版的 ResponseComparisonConfigPanel）
        comparisonPanel = new ResponseComparisonConfigPanelSimplified(pair.getComparisonConfig());
        panel.add(comparisonPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建"提取什么变量"配置区域
     */
    private JPanel createVariableExtractionSection() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 140, 100), 2),
                " 提取什么变量（extractVariables） ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 13),
                new Color(200, 140, 100)
            ),
            BorderFactory.createEmptyBorder(10, 15, 15, 15)
        ));
        panel.setBackground(Color.WHITE);
        
        // 说明
        JTextArea descArea = new JTextArea(
            "从当前Pair的响应中提取数据，供后续Pair使用：\n\n" +
            "提取方式：\n" +
            "• 使用正则表达式从响应体提取变量（使用括号捕获组）\n" +
            "• 后续Pair在请求中使用 {变量名} 占位符引用\n\n" +
            "真实检测场景：\n" +
            "1. IDOR账户接管（HackerOne案例）：\n" +
            "   Pair 1: 创建订单 → 提取 {order_id}\n" +
            "   Pair 2: 使用 {order_id} 尝试访问或修改\n\n" +
            "2. CSRF Token绕过：\n" +
            "   Pair 1: 获取表单 → 提取 {csrf_token}\n" +
            "   Pair 2: 使用 {csrf_token} 提交恶意请求\n\n" +
            "示例正则：\"order_id\":\\s*\"([^\"]+)\""
        );
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBackground(new Color(255, 250, 245));
        descArea.setFont(descArea.getFont().deriveFont(Font.PLAIN, 12f));
        descArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(descArea, BorderLayout.NORTH);
        
        // 变量表格
        panel.add(createVariableTablePanel(), BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建变量提取表格面板
     */
    private JPanel createVariableTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        
        // 表格
        variableTableModel = new DefaultTableModel(
            new Object[]{"变量名", "提取正则", "操作"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 2; // 操作列不可编辑
            }
        };
        
        variableTable = new JTable(variableTableModel);
        variableTable.setRowHeight(30);
        variableTable.getTableHeader().setFont(variableTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        
        // 操作列按钮
        variableTable.getColumn("操作").setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JButton btn = new JButton("删除");
            btn.setForeground(Color.RED);
            btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
            return btn;
        });
        
        variableTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int column = variableTable.columnAtPoint(e.getPoint());
                int row = variableTable.rowAtPoint(e.getPoint());
                if (column == 2 && row >= 0) {
                    variableTableModel.removeRow(row);
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(variableTable);
        scrollPane.setPreferredSize(new Dimension(600, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 添加按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton addButton = new JButton("+ 添加变量");
        addButton.addActionListener(e -> {
            variableTableModel.addRow(new Object[]{"变量名", "正则表达式", "删除"});
        });
        buttonPanel.add(addButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        JButton saveButton = new JButton("保存配对");
        saveButton.addActionListener(e -> {
            if (validateAndSave()) {
                confirmed = true;
                dispose();
            }
        });
        
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        JButton validateButton = new JButton("验证配置");
        validateButton.addActionListener(e -> validateConfiguration());
        
        panel.add(validateButton);
        panel.add(saveButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    /**
     * 从配对加载数据
     */
    private void loadFromPair() {
        // 基本信息
        if (pair.getLabel() != null) {
            labelField.setText(pair.getLabel());
        }
        
        // ✨ 新增：加载变量提取规则
        if (pair.getExtractVariables() != null && !pair.getExtractVariables().isEmpty()) {
            for (Map.Entry<String, String> entry : pair.getExtractVariables().entrySet()) {
                variableTableModel.addRow(new Object[]{entry.getKey(), entry.getValue(), "删除"});
            }
        }
    }
    
    /**
     * 验证配置
     */
    private void validateConfiguration() {
        StringBuilder messages = new StringBuilder();
        messages.append("配置验证结果：\n\n");
        
        // 验证标签
        String label = labelField.getText().trim();
        if (label.isEmpty()) {
            messages.append("⚠️ 建议设置配对标签\n");
        } else {
            messages.append("✅ 配对标签：").append(label).append("\n");
        }
        
        // 验证请求配置
        UnifiedHttpConfig reqConfig = requestPanel.getConfig();
        if (reqConfig.getElements().isEmpty()) {
            messages.append("❌ 请求配置为空！请至少添加一个HTTP元素\n");
        } else {
            long matchCount = reqConfig.getElements().stream()
                .filter(UnifiedHttpConfig.HttpElementConfig::isUseForMatch)
                .count();
            long injectCount = reqConfig.getElements().stream()
                .filter(UnifiedHttpConfig.HttpElementConfig::isUseForInjection)
                .count();
            
            messages.append("✅ 请求配置：").append(reqConfig.getElements().size())
                .append("个元素（").append(matchCount).append("个匹配，")
                .append(injectCount).append("个注入）\n");
            
            if (injectCount == 0) {
                messages.append("⚠️ 没有配置注入点！请至少勾选一个元素的「注入」\n");
            }
        }
        
        // 验证响应配置
        UnifiedResponseConfig respConfig = responsePanel.getConfig();
        if (respConfig.getElements().isEmpty()) {
            // 检查是否有跨Pair配置
            ResponseComparisonConfig comparison = comparisonPanel.getConfig();
            boolean hasCrossPairComparison = comparison != null && 
                (comparison.getReferencePairId() != null || 
                 comparison.getBodyComparisonReferencePairId() != null ||
                 comparison.getReferenceFeatureType() != null);
            
            if (hasCrossPairComparison) {
                messages.append("⚠️ 响应配置为空（已配置跨Pair对比，适用于盲注检测）\n");
            } else {
                messages.append("⚠️ 响应配置为空！建议添加响应元素或配置跨Pair对比\n");
            }
        } else {
            messages.append("✅ 响应配置：").append(respConfig.getElements().size())
                .append("个元素\n");
        }
        
        JOptionPane.showMessageDialog(this,
            messages.toString(),
            "配置验证",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 验证并保存
     */
    private boolean validateAndSave() {
        // 保存标签
        pair.setLabel(labelField.getText().trim());
        
        // ✅ 检测模式改为系统自动推断，不再由用户配置
        // 系统会根据响应配置、跨Pair对比等自动推断检测类型
        
        // ✨ 新增：保存变量提取规则
        Map<String, String> extractVariables = new HashMap<>();
        for (int i = 0; i < variableTableModel.getRowCount(); i++) {
            String varName = (String) variableTableModel.getValueAt(i, 0);
            String regex = (String) variableTableModel.getValueAt(i, 1);
            if (varName != null && !varName.trim().isEmpty() && 
                regex != null && !regex.trim().isEmpty()) {
                extractVariables.put(varName.trim(), regex.trim());
            }
        }
        pair.setExtractVariables(extractVariables);
        
        // ✨ 新增：保存响应对比配置
        ResponseComparisonConfig comparisonConfig = comparisonPanel.getConfig();
        
        // ✅ 验证跨Pair引用：引用的Pair ID必须小于当前Pair ID
        if (comparisonConfig != null) {
            Integer refPairId = comparisonConfig.getReferencePairId();
            if (refPairId != null && refPairId >= pair.getId()) {
                JOptionPane.showMessageDialog(this,
                    "错误：跨Pair配置中的引用Pair ID (" + refPairId + ") 必须小于当前Pair ID (" + pair.getId() + ")！\n" +
                    "只能引用之前已执行的Pair，否则会导致检测混乱。",
                    "验证失败",
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            Integer bodyRefPairId = comparisonConfig.getBodyComparisonReferencePairId();
            if (bodyRefPairId != null && bodyRefPairId >= pair.getId()) {
                JOptionPane.showMessageDialog(this,
                    "错误：响应体对比中的引用Pair ID (" + bodyRefPairId + ") 必须小于当前Pair ID (" + pair.getId() + ")！\n" +
                    "只能引用之前已执行的Pair，否则会导致检测混乱。",
                    "验证失败",
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        
        pair.setComparisonConfig(comparisonConfig);
        
        // 保存请求配置
        UnifiedHttpConfig requestConfig = requestPanel.getConfig();
        if (requestConfig.getElements().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "请求配置不能为空！\n请至少添加一个HTTP元素。",
                "验证失败",
                JOptionPane.WARNING_MESSAGE);
            tabbedPane.setSelectedIndex(0);
            return false;
        }
        
        // 检查是否有注入点
        boolean hasInjection = requestConfig.getElements().stream()
            .anyMatch(UnifiedHttpConfig.HttpElementConfig::isUseForInjection);
        if (!hasInjection) {
            int result = JOptionPane.showConfirmDialog(this,
                "警告：没有配置任何注入点！\n" +
                "这意味着只会匹配请求，不会发送测试payload（被动检测模式）。\n" +
                "这在第一个Pair中很常见，用于匹配基础请求。\n\n" +
                "是否继续保存？",
                "确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                tabbedPane.setSelectedIndex(0);
                return false;
            }
        }
        
        pair.setRequestConfig(requestConfig);
        
        // 保存响应配置（允许为空，用于盲注等场景）
        UnifiedResponseConfig responseConfig = responsePanel.getConfig();
        if (responseConfig.getElements().isEmpty()) {
            // ✨ 响应配置为空时给出友好提示（不阻止保存）
            ResponseComparisonConfig comparison = comparisonPanel.getConfig();
            boolean hasCrossPairComparison = comparison != null && 
                (comparison.getReferencePairId() != null || 
                 comparison.getBodyComparisonReferencePairId() != null ||
                 comparison.getReferenceFeatureType() != null);
            
            String message;
            if (hasCrossPairComparison) {
                message = "提示：响应配置为空\n\n" +
                          "检测到您已配置「跨Pair配置」，这在盲注检测中很常见：\n" +
                          "• 通过对比不同Pair的响应时间、长度等特征来判断漏洞\n" +
                          "• 不依赖响应内容的具体匹配\n\n" +
                          "是否继续保存？";
            } else {
                message = "提示：响应配置为空\n\n" +
                          "当前未配置响应元素和跨Pair对比。\n" +
                          "这意味着此Pair不会进行响应检查。\n\n" +
                          "适用场景：\n" +
                          "• 作为第一个Pair仅用于匹配请求\n" +
                          "• 后续Pair通过跨Pair对比判断漏洞\n\n" +
                          "是否继续保存？";
            }
            
            int result = JOptionPane.showConfirmDialog(this,
                message,
                "确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                tabbedPane.setSelectedIndex(1);
                return false;
            }
        }
        
        pair.setResponseConfig(responseConfig);
        
        return true;
    }
    
    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }
    
    public RuleMatchPair getPair() {
        return pair;
    }
}


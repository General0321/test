package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.UnifiedResponseConfig.*;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * 响应元素详细配置对话框
 */
public class ResponseElementDetailDialog extends JDialog {
    
    private ResponseElementConfig element;
    private boolean confirmed = false;
    
    // UI组件
    private JComboBox<MatchType> matchTypeCombo;
    private JTextArea valuesArea;
    private JCheckBox caseSensitiveCheck;
    
    // 数值比较组件
    private JComboBox<ComparisonOperator> operatorCombo;
    private JSpinner numericSpinner;
    private JSpinner numericSpinnerMin;  // 范围最小值
    private JSpinner numericSpinnerMax;  // 范围最大值
    private JPanel rangePanel;           // 范围输入面板
    
    // Collaborator组件
    private JCheckBox dnsCheck, httpCheck, httpsCheck, smtpCheck;
    
    public ResponseElementDetailDialog(Window owner, ResponseElementConfig element) {
        super(owner, "响应元素配置", ModalityType.APPLICATION_MODAL);
        this.element = element;
        
        initComponents();
        loadFromElement();
        
        setSize(700, 600);  // ✅ 与请求配置对话框保持一致
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部标题
        JLabel titleLabel = new JLabel("配置: " + element.getType().getDisplayName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(titleLabel, BorderLayout.NORTH);
        
        // 中间配置面板
        // ✅ 修复：对于文本配置，使用特殊布局，将变量面板放在滚动面板外
        ElementType type = element.getType();
        if (type == ElementType.STATUS_CODE || type == ElementType.RESPONSE_HEADERS || type == ElementType.RESPONSE_BODY) {
            // 文本配置：创建包含变量面板的完整面板
            JPanel mainPanel = createTextConfigPanelWithVariables();
            add(mainPanel, BorderLayout.CENTER);
        } else {
            // 其他配置：直接使用滚动面板
            JPanel configPanel = createConfigPanel();
            JScrollPane scrollPane = new JScrollPane(configPanel);
            add(scrollPane, BorderLayout.CENTER);
        }
        
        // 底部按钮
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * ✅ 创建包含变量面板的文本配置面板（变量面板在滚动面板外，始终可见）
     */
    private JPanel createTextConfigPanelWithVariables() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 创建文本配置面板（不包含变量面板）
        JPanel textConfigPanel = createTextConfigPanel();
        
        // 将文本配置面板放在滚动面板中
        JScrollPane scrollPane = new JScrollPane(textConfigPanel);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 变量面板放在滚动面板下方，始终可见
        JPanel variablePanel = createVariableInsertPanel();
        mainPanel.add(variablePanel, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        ElementType type = element.getType();
        
        switch (type) {
            case RESPONSE_TIME:
            case RESPONSE_LENGTH:
                panel.add(createNumericConfigPanel(), BorderLayout.CENTER);
                break;
                
            case COLLABORATOR:
                panel.add(createCollaboratorConfigPanel(), BorderLayout.CENTER);
                break;
                
            default:
                // ✅ 修复：将变量面板移出滚动面板，使其始终可见
                JPanel textConfigPanel = createTextConfigPanel();
                panel.add(textConfigPanel, BorderLayout.CENTER);
                // 变量面板已经在 createTextConfigPanel 中处理，但需要确保它不在滚动面板内
                break;
        }
        
        return panel;
    }
    
    /**
     * 文本匹配配置面板（用于Status Code, Headers, Body）
     * ✅ 修复：参考请求配置的布局，将变量面板放在滚动面板外，使其始终可见
     */
    private JPanel createTextConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 匹配类型
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel("匹配类型:"));
        matchTypeCombo = new JComboBox<>(new MatchType[] {
            MatchType.EQUALS,
            MatchType.CONTAINS,
            MatchType.REGEX,
            MatchType.STARTS_WITH,
            MatchType.ENDS_WITH,
            MatchType.NOT_EQUALS,
            MatchType.NOT_CONTAINS
        });
        typePanel.add(matchTypeCombo);
        
        caseSensitiveCheck = new JCheckBox("区分大小写");
        typePanel.add(caseSensitiveCheck);
        
        panel.add(typePanel, BorderLayout.NORTH);
        
        // 匹配值面板（只包含文本区域，变量面板已移到外层）
        JPanel valuesPanel = new JPanel(new BorderLayout(5, 5));
        valuesPanel.setBorder(BorderFactory.createTitledBorder("匹配值配置"));
        
        JLabel valuesLabel = new JLabel("匹配值（每行一个，OR关系，支持正则表达式和变量）:");
        valuesPanel.add(valuesLabel, BorderLayout.NORTH);
        
        valuesArea = new JTextArea(12, 40);
        valuesArea.setLineWrap(false);
        JScrollPane scrollPane = new JScrollPane(valuesArea);
        valuesPanel.add(scrollPane, BorderLayout.CENTER);
        
        panel.add(valuesPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * ✅ 创建变量插入按钮面板
     * 支持检测请求配置中的随机变量，以及跨pair变量
     * 参考请求配置中的变量面板实现
     */
    private JPanel createVariableInsertPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("可用变量（点击插入）"));
        
        // 随机值（当前pair的请求配置中生成的）
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("随机值:"));
        row1.add(createInsertButton("{{RANDOM_STRING}}", valuesArea));
        row1.add(createInsertButton("{{RANDOM_NUMBER}}", valuesArea));
        row1.add(createInsertButton("{{UUID}}", valuesArea));
        row1.add(createInsertButton("{{TIMESTAMP}}", valuesArea));
        panel.add(row1);
        
        // 外带检测变量
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("外带检测:"));
        row2.add(createInsertButton("{{COLLABORATOR}}", valuesArea));
        row2.add(createInsertButton("{{DNSLOG}}", valuesArea));
        panel.add(row2);
        
        // 指定pair的随机值
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(new JLabel("指定pair的随机值:"));
        row3.add(createInsertButton("{{PAIR:1:RANDOM_STRING}}", valuesArea));
        row3.add(createInsertButton("{{PAIR:1:UUID}}", valuesArea));
        row3.add(createInsertButton("{{PAIR:1:TIMESTAMP}}", valuesArea));
        row3.add(createInsertButton("{{PAIR:1:COLLABORATOR}}", valuesArea));
        row3.add(createInsertButton("{{PAIR:2:RANDOM_STRING}}", valuesArea));
        row3.add(createInsertButton("{{PAIR:2:UUID}}", valuesArea));
        panel.add(row3);
        
        return panel;
    }
    
    /**
     * ✅ 创建插入按钮（参考请求配置的实现）
     */
    private JButton createInsertButton(String variable, JTextArea targetArea) {
        JButton button = new JButton(variable);
        button.setFont(new Font("Monospaced", Font.PLAIN, 10));
        button.addActionListener(e -> {
            int pos = targetArea.getCaretPosition();
            try {
                targetArea.insert(variable, pos);
            } catch (Exception ex) {
                targetArea.append(variable);
            }
        });
        return button;
    }
    
    /**
     * 数值比较配置面板（用于Response Time, Response Length）
     */
    private JPanel createNumericConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 操作符
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("比较操作符:"), gbc);
        
        gbc.gridx = 1;
        operatorCombo = new JComboBox<>(ComparisonOperator.values());
        operatorCombo.addActionListener(e -> updateNumericInputVisibility());
        panel.add(operatorCombo, gbc);
        
        // 单个数值输入
        gbc.gridx = 0;
        gbc.gridy = 1;
        String valueLabel = element.getType() == ElementType.RESPONSE_TIME 
            ? "时间阈值 (ms):" 
            : "长度阈值 (bytes):";
        panel.add(new JLabel(valueLabel), gbc);
        
        gbc.gridx = 1;
        SpinnerNumberModel model = new SpinnerNumberModel(5000L, 0L, Long.MAX_VALUE, 100L);
        numericSpinner = new JSpinner(model);
        numericSpinner.setPreferredSize(new Dimension(200, 25));
        panel.add(numericSpinner, gbc);
        
        // 范围输入（用于BETWEEN/NOT_BETWEEN）
        rangePanel = new JPanel(new GridBagLayout());
        GridBagConstraints rangegbc = new GridBagConstraints();
        rangegbc.insets = new Insets(2, 2, 2, 2);
        rangegbc.anchor = GridBagConstraints.WEST;
        
        rangegbc.gridx = 0;
        rangegbc.gridy = 0;
        rangePanel.add(new JLabel("最小值:"), rangegbc);
        
        rangegbc.gridx = 1;
        SpinnerNumberModel minModel = new SpinnerNumberModel(1000L, 0L, Long.MAX_VALUE, 100L);
        numericSpinnerMin = new JSpinner(minModel);
        numericSpinnerMin.setPreferredSize(new Dimension(120, 25));
        rangePanel.add(numericSpinnerMin, rangegbc);
        
        rangegbc.gridx = 2;
        rangePanel.add(new JLabel("  最大值:"), rangegbc);
        
        rangegbc.gridx = 3;
        SpinnerNumberModel maxModel = new SpinnerNumberModel(10000L, 0L, Long.MAX_VALUE, 100L);
        numericSpinnerMax = new JSpinner(maxModel);
        numericSpinnerMax.setPreferredSize(new Dimension(120, 25));
        rangePanel.add(numericSpinnerMax, rangegbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        rangePanel.setVisible(false);  // 默认隐藏
        panel.add(rangePanel, gbc);
        
        // 说明
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        String hint = element.getType() == ElementType.RESPONSE_TIME
            ? "示例: 响应时间 > 5000ms 表示延迟大于5秒"
            : "示例: 响应长度 > 1000 表示响应体大于1000字节";
        panel.add(new JLabel(hint), gbc);
        
        return panel;
    }
    
    /**
     * 根据操作符类型更新数值输入的可见性
     */
    private void updateNumericInputVisibility() {
        ComparisonOperator operator = (ComparisonOperator) operatorCombo.getSelectedItem();
        if (operator == ComparisonOperator.BETWEEN || operator == ComparisonOperator.NOT_BETWEEN) {
            numericSpinner.setVisible(false);
            rangePanel.setVisible(true);
        } else {
            numericSpinner.setVisible(true);
            rangePanel.setVisible(false);
        }
        revalidate();
        repaint();
    }
    
    /**
     * Collaborator配置面板
     */
    private JPanel createCollaboratorConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        JLabel titleLabel = new JLabel("选择要检测的交互类型:");
        panel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel checkPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        checkPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        dnsCheck = new JCheckBox("DNS查询");
        httpCheck = new JCheckBox("HTTP请求");
        httpsCheck = new JCheckBox("HTTPS请求");
        smtpCheck = new JCheckBox("SMTP连接");
        
        checkPanel.add(dnsCheck);
        checkPanel.add(httpCheck);
        checkPanel.add(httpsCheck);
        checkPanel.add(smtpCheck);
        
        panel.add(checkPanel, BorderLayout.CENTER);
        
        JLabel hintLabel = new JLabel("提示：至少选择一种交互类型，检测到任意选中的交互类型即认为匹配");
        panel.add(hintLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton okButton = new JButton("确定");
        okButton.addActionListener(e -> {
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
        
        panel.add(okButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    /**
     * 从元素加载配置
     */
    private void loadFromElement() {
        MatchConfig config = element.getMatchConfig();
        if (config == null) return;
        
        ElementType type = element.getType();
        
        switch (type) {
            case RESPONSE_TIME:
            case RESPONSE_LENGTH:
                if (operatorCombo != null) {
                    operatorCombo.setSelectedItem(config.getComparisonOperator());
                    updateNumericInputVisibility();  // 更新UI显示
                }
                if (numericSpinner != null) {
                    numericSpinner.setValue(config.getNumericValue());
                }
                if (numericSpinnerMin != null) {
                    numericSpinnerMin.setValue(config.getNumericValueMin());
                }
                if (numericSpinnerMax != null) {
                    numericSpinnerMax.setValue(config.getNumericValueMax());
                }
                break;
                
            case COLLABORATOR:
                if (config.getCollaboratorTypes() != null) {
                    for (CollaboratorType collabType : config.getCollaboratorTypes()) {
                        switch (collabType) {
                            case DNS:
                                dnsCheck.setSelected(true);
                                break;
                            case HTTP:
                                httpCheck.setSelected(true);
                                break;
                            case HTTPS:
                                httpsCheck.setSelected(true);
                                break;
                            case SMTP:
                                smtpCheck.setSelected(true);
                                break;
                        }
                    }
                } else {
                    // 默认选择DNS
                    dnsCheck.setSelected(true);
                }
                break;
                
            default:
                if (matchTypeCombo != null) {
                    matchTypeCombo.setSelectedItem(config.getMatchType());
                }
                if (caseSensitiveCheck != null) {
                    caseSensitiveCheck.setSelected(config.isCaseSensitive());
                }
                if (valuesArea != null && config.getValues() != null) {
                    valuesArea.setText(String.join("\n", config.getValues()));
                }
                break;
        }
    }
    
    /**
     * 验证并保存
     */
    private boolean validateAndSave() {
        MatchConfig config = element.getMatchConfig();
        if (config == null) {
            config = new MatchConfig();
            element.setMatchConfig(config);
        }
        
        ElementType type = element.getType();
        
        try {
            switch (type) {
                case RESPONSE_TIME:
                case RESPONSE_LENGTH:
                    config.setMatchType(MatchType.NUMERIC_COMPARISON);
                    ComparisonOperator operator = (ComparisonOperator) operatorCombo.getSelectedItem();
                    config.setComparisonOperator(operator);
                    
                    // 根据操作符类型保存不同的值
                    if (operator == ComparisonOperator.BETWEEN || operator == ComparisonOperator.NOT_BETWEEN) {
                        // 保存范围值（安全转换，避免Double->Long转换错误）
                        config.setNumericValueMin(((Number) numericSpinnerMin.getValue()).longValue());
                        config.setNumericValueMax(((Number) numericSpinnerMax.getValue()).longValue());
                        
                        // 验证范围
                        if (config.getNumericValueMin() > config.getNumericValueMax()) {
                            JOptionPane.showMessageDialog(this,
                                "最小值不能大于最大值",
                                "验证失败",
                                JOptionPane.WARNING_MESSAGE);
                            return false;
                        }
                    } else {
                        // 保存单个值（安全转换，避免Double->Long转换错误）
                        config.setNumericValue(((Number) numericSpinner.getValue()).longValue());
                    }
                    break;
                    
                case COLLABORATOR:
                    config.getCollaboratorTypes().clear();
                    if (dnsCheck.isSelected()) {
                        config.getCollaboratorTypes().add(CollaboratorType.DNS);
                    }
                    if (httpCheck.isSelected()) {
                        config.getCollaboratorTypes().add(CollaboratorType.HTTP);
                    }
                    if (httpsCheck.isSelected()) {
                        config.getCollaboratorTypes().add(CollaboratorType.HTTPS);
                    }
                    if (smtpCheck.isSelected()) {
                        config.getCollaboratorTypes().add(CollaboratorType.SMTP);
                    }
                    
                    if (config.getCollaboratorTypes().isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                            "请至少选择一种交互类型",
                            "验证失败",
                            JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                    break;
                    
                default:
                    config.setMatchType((MatchType) matchTypeCombo.getSelectedItem());
                    config.setCaseSensitive(caseSensitiveCheck.isSelected());
                    
                    String text = valuesArea.getText().trim();
                    if (text.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                            "请输入至少一个匹配值",
                            "验证失败",
                            JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                    
                    config.getValues().clear();
                    Arrays.stream(text.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(config::addValue);
                    break;
            }
            
            return true;
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "配置验证失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }
    
    public ResponseElementConfig getElement() {
        return element;
    }
}


package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.UnifiedHttpConfig.*;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * HTTP元素详细配置对话框
 */
public class HttpElementDetailDialog extends JDialog {
    
    private HttpElementConfig element;
    private boolean confirmed = false;
    
    // 匹配配置组件
    private JCheckBox matchEnabledCheckbox;
    private JPanel nameMatchPanel;
    private JPanel valueMatchPanel;
    
    // 注入配置组件
    private JCheckBox injectEnabledCheckbox;
    private JPanel injectionPanel;
    
    public HttpElementDetailDialog(Window owner, HttpElementConfig element) {
        super(owner, "配置: " + element.getDisplayLabel(), ModalityType.APPLICATION_MODAL);
        this.element = element;
        
        initComponents();
        loadFromElement();
        
        setSize(700, 600);
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 中间面板 - Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 匹配配置标签页
        tabbedPane.addTab("匹配配置", createMatchPanel());
        
        // 注入配置标签页
        tabbedPane.addTab("注入配置", createInjectionPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 底部按钮
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建匹配配置面板
     */
    private JPanel createMatchPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部：启用开关
        matchEnabledCheckbox = new JCheckBox("启用匹配（作为请求条件）");
        matchEnabledCheckbox.addActionListener(e -> updateMatchPanelState());
        panel.add(matchEnabledCheckbox, BorderLayout.NORTH);
        
        // 中间：配置面板
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        
        // 根据元素类型决定显示哪些配置
        if (element.getType() == ElementType.PARAMETER ||
            element.getType() == ElementType.HEADER ||
            element.getType() == ElementType.COOKIE) {
            
            // 名称匹配配置
            nameMatchPanel = createMatchConfigPanel(
                "名称匹配",
                element.getNameMatchConfig(),
                "配置元素名称的匹配条件（如参数名、Header名）"
            );
            configPanel.add(nameMatchPanel);
            configPanel.add(Box.createVerticalStrut(10));
        }
        
        // 值匹配配置
        valueMatchPanel = createMatchConfigPanel(
            getValueMatchTitle(),
            element.getValueMatchConfig(),
            "配置元素值的匹配条件"
        );
        configPanel.add(valueMatchPanel);
        
        JScrollPane scrollPane = new JScrollPane(configPanel);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 获取值匹配标题
     */
    private String getValueMatchTitle() {
        switch (element.getType()) {
            case METHOD:
                return "HTTP方法匹配";
            case PATH:
                return "URL路径匹配";
            case PARAMETER:
                return "参数值匹配";
            case HEADER:
                return "Header值匹配";
            case COOKIE:
                return "Cookie值匹配";
            case BODY:
                return "请求体匹配";
            default:
                return "值匹配";
        }
    }
    
    /**
     * 创建匹配配置面板
     */
    private JPanel createMatchConfigPanel(String title, MatchConfig config, String tooltip) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setToolTipText(tooltip);
        
        // 匹配类型选择
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel("匹配类型:"));
        
        JComboBox<MatchType> matchTypeCombo = new JComboBox<>(MatchType.values());
        matchTypeCombo.setSelectedItem(config.getMatchType());
        matchTypeCombo.addActionListener(e -> {
            config.setMatchType((MatchType) matchTypeCombo.getSelectedItem());
        });
        typePanel.add(matchTypeCombo);
        
        // 大小写敏感
        JCheckBox caseSensitiveCheckbox = new JCheckBox("区分大小写");
        caseSensitiveCheckbox.setSelected(config.isCaseSensitive());
        caseSensitiveCheckbox.addActionListener(e -> {
            config.setCaseSensitive(caseSensitiveCheckbox.isSelected());
        });
        typePanel.add(caseSensitiveCheckbox);
        
        panel.add(typePanel, BorderLayout.NORTH);
        
        // 匹配值输入
        JPanel valuesPanel = new JPanel(new BorderLayout(5, 5));
        valuesPanel.add(new JLabel("匹配值（每行一个，OR关系）:"), BorderLayout.NORTH);
        
        JTextArea valuesArea = new JTextArea(8, 40);
        valuesArea.setLineWrap(false);
        valuesArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // 加载已有值
        if (config.getValues() != null && !config.getValues().isEmpty()) {
            valuesArea.setText(String.join("\n", config.getValues()));
        }
        
        // 保存值到config
        valuesArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                String text = valuesArea.getText();
                String[] lines = text.split("\n");
                config.getValues().clear();
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        config.addValue(line);
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(valuesArea);
        valuesPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 提示标签
        JLabel hintLabel = new JLabel("提示: 支持正则表达式（需选择正则匹配类型）");
        hintLabel.setForeground(Color.GRAY);
        valuesPanel.add(hintLabel, BorderLayout.SOUTH);
        
        panel.add(valuesPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建注入配置面板
     */
    private JPanel createInjectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部：启用开关
        injectEnabledCheckbox = new JCheckBox("启用注入（作为注入点）");
        injectEnabledCheckbox.addActionListener(e -> updateInjectionPanelState());
        panel.add(injectEnabledCheckbox, BorderLayout.NORTH);
        
        // 中间：配置面板
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
        
        // 注入目标选择（某些类型不需要）
        if (element.getType() == ElementType.PARAMETER ||
            element.getType() == ElementType.HEADER ||
            element.getType() == ElementType.COOKIE) {
            
            JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            targetPanel.setBorder(BorderFactory.createTitledBorder("注入目标"));
            targetPanel.add(new JLabel("注入位置:"));
            
            JComboBox<InjectionTarget> targetCombo = new JComboBox<>(InjectionTarget.values());
            
            // ✅ 修复Bug #4: 如果injectionTarget为null，设置默认值为VALUE
            if (element.getInjectionTarget() == null) {
                element.setInjectionTarget(InjectionTarget.VALUE);
                targetCombo.setSelectedItem(InjectionTarget.VALUE);
            } else {
                targetCombo.setSelectedItem(element.getInjectionTarget());
            }
            
            targetCombo.addActionListener(e -> {
                element.setInjectionTarget((InjectionTarget) targetCombo.getSelectedItem());
            });
            targetPanel.add(targetCombo);
            
            configPanel.add(targetPanel);
            configPanel.add(Box.createVerticalStrut(10));
        }
        
        // Payload配置
        JPanel payloadPanel = new JPanel(new BorderLayout(5, 5));
        payloadPanel.setBorder(BorderFactory.createTitledBorder("Payload配置"));
        
        JLabel payloadLabel = new JLabel("Payload列表（每行一个）:");
        payloadPanel.add(payloadLabel, BorderLayout.NORTH);
        
        JTextArea payloadArea = new JTextArea(12, 50);
        payloadArea.setLineWrap(false);
        payloadArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // 加载已有Payload
        if (element.getPayloads() != null && !element.getPayloads().isEmpty()) {
            payloadArea.setText(String.join("\n", element.getPayloads()));
        }
        
        // 保存Payload到element
        payloadArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            private void update() {
                String text = payloadArea.getText();
                String[] lines = text.split("\n");
                element.getPayloads().clear();
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        element.getPayloads().add(line);
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(payloadArea);
        payloadPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 变量帮助面板
        JPanel variablePanel = createVariableHelpPanel(payloadArea);
        payloadPanel.add(variablePanel, BorderLayout.SOUTH);
        
        configPanel.add(payloadPanel);
        
        injectionPanel = configPanel;
        panel.add(configPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建变量帮助面板
     */
    private JPanel createVariableHelpPanel(JTextArea targetArea) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("可用变量（点击插入）"));
        
        // 注入点相关变量
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("注入点:"));
        row1.add(createInsertButton("{{ORIGINAL}}", targetArea));
        row1.add(createInsertButton("{{ORIGINAL_URL_ENCODED}}", targetArea));
        row1.add(createInsertButton("{{ORIGINAL_BASE64}}", targetArea));
        panel.add(row1);
        
        // 外带检测变量
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("外带检测:"));
        row2.add(createInsertButton("{{COLLABORATOR}}", targetArea));
        row2.add(createInsertButton("{{DNSLOG}}", targetArea));
        panel.add(row2);
        
        // 随机值变量
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(new JLabel("随机值:"));
        row3.add(createInsertButton("{{RANDOM_STRING}}", targetArea));
        row3.add(createInsertButton("{{RANDOM_NUMBER}}", targetArea));
        row3.add(createInsertButton("{{UUID}}", targetArea));
        row3.add(createInsertButton("{{TIMESTAMP}}", targetArea));
        panel.add(row3);
        
        // 编码函数
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row4.add(new JLabel("编码:"));
        row4.add(createInsertButton("{{BASE64:xxx}}", targetArea));
        row4.add(createInsertButton("{{URL_ENCODE:xxx}}", targetArea));
        panel.add(row4);
        
        return panel;
    }
    
    /**
     * 创建插入按钮
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
     * 创建按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton okButton = new JButton("确定");
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
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
     * 从元素加载
     */
    private void loadFromElement() {
        matchEnabledCheckbox.setSelected(element.isUseForMatch());
        injectEnabledCheckbox.setSelected(element.isUseForInjection());
        
        updateMatchPanelState();
        updateInjectionPanelState();
    }
    
    /**
     * 更新匹配面板状态
     */
    private void updateMatchPanelState() {
        boolean enabled = matchEnabledCheckbox.isSelected();
        element.setUseForMatch(enabled);
        
        if (nameMatchPanel != null) {
            setComponentsEnabled(nameMatchPanel, enabled);
        }
        if (valueMatchPanel != null) {
            setComponentsEnabled(valueMatchPanel, enabled);
        }
    }
    
    /**
     * 更新注入面板状态
     */
    private void updateInjectionPanelState() {
        boolean enabled = injectEnabledCheckbox.isSelected();
        element.setUseForInjection(enabled);
        
        if (injectionPanel != null) {
            setComponentsEnabled(injectionPanel, enabled);
        }
    }
    
    /**
     * 递归启用/禁用组件
     */
    private void setComponentsEnabled(Container container, boolean enabled) {
        for (Component comp : container.getComponents()) {
            comp.setEnabled(enabled);
            if (comp instanceof Container) {
                setComponentsEnabled((Container) comp, enabled);
            }
        }
    }
    
    /**
     * 显示对话框
     */
    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }
    
    /**
     * 获取元素
     */
    public HttpElementConfig getElement() {
        return element;
    }
}


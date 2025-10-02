package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedHttpConfig.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一HTTP配置面板
 * 在HTTP请求包格式上配置匹配条件和注入点
 */
public class UnifiedHttpConfigPanel extends JPanel {
    
    private UnifiedHttpConfig config;
    private List<HttpElementRow> elementRows;
    private JPanel elementsPanel;
    private JTextArea expressionArea;
    private int nextElementId = 1;
    
    public UnifiedHttpConfigPanel() {
        this.config = new UnifiedHttpConfig();
        this.elementRows = new ArrayList<>();
        
        initComponents();
        loadDefaultElements();
    }
    
    public UnifiedHttpConfigPanel(UnifiedHttpConfig config) {
        this.config = config != null ? config : new UnifiedHttpConfig();
        this.elementRows = new ArrayList<>();
        
        initComponents();
        loadFromConfig();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部工具栏
        add(createToolbar(), BorderLayout.NORTH);
        
        // 中间HTTP元素面板
        JScrollPane scrollPane = new JScrollPane(createElementsPanel());
        scrollPane.setBorder(BorderFactory.createTitledBorder("HTTP请求配置"));
        add(scrollPane, BorderLayout.CENTER);
        
        // 底部表达式面板
        add(createExpressionPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建工具栏
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton importBtn = new JButton("从Proxy导入");
        importBtn.setToolTipText("从Burp Proxy导入HTTP请求");
        importBtn.addActionListener(e -> importFromProxy());
        
        JButton addMethodBtn = new JButton("+ Method");
        addMethodBtn.addActionListener(e -> addElement(ElementType.METHOD));
        
        JButton addPathBtn = new JButton("+ Path");
        addPathBtn.addActionListener(e -> addElement(ElementType.PATH));
        
        JButton addParamBtn = new JButton("+ Parameter");
        addParamBtn.addActionListener(e -> addElement(ElementType.PARAMETER));
        
        JButton addHeaderBtn = new JButton("+ Header");
        addHeaderBtn.addActionListener(e -> addElement(ElementType.HEADER));
        
        JButton addCookieBtn = new JButton("+ Cookie");
        addCookieBtn.addActionListener(e -> addElement(ElementType.COOKIE));
        
        JButton addBodyBtn = new JButton("+ Body");
        addBodyBtn.addActionListener(e -> addElement(ElementType.BODY));
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> clearAll());
        
        toolbar.add(importBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(addMethodBtn);
        toolbar.add(addPathBtn);
        toolbar.add(addParamBtn);
        toolbar.add(addHeaderBtn);
        toolbar.add(addCookieBtn);
        toolbar.add(addBodyBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(clearBtn);
        
        return toolbar;
    }
    
    /**
     * 创建HTTP元素面板
     */
    private JPanel createElementsPanel() {
        elementsPanel = new JPanel();
        elementsPanel.setLayout(new BoxLayout(elementsPanel, BoxLayout.Y_AXIS));
        
        JLabel hintLabel = new JLabel("<html>" +
            "<b>配置说明:</b><br>" +
            "• <b>匹配</b> = 请求条件，决定哪些请求会被此规则测试<br>" +
            "• <b>注入</b> = 注入点，决定在哪里插入Payload<br>" +
            "• 同一个元素可以同时用于匹配和注入<br>" +
            "• 点击 <b>⚙️</b> 按钮配置详细信息（支持多个值、正则等）" +
            "</html>");
        hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        elementsPanel.add(hintLabel);
        
        return elementsPanel;
    }
    
    /**
     * 创建表达式面板
     */
    private JPanel createExpressionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TitledBorder border = BorderFactory.createTitledBorder("复杂条件表达式（可选）");
        panel.setBorder(border);
        
        JLabel label = new JLabel("<html>" +
            "使用元素ID和逻辑运算符组合复杂条件，例如: <code>(1 AND 2) OR 3</code><br>" +
            "留空则表示所有启用匹配的元素都需满足（AND关系）" +
            "</html>");
        panel.add(label, BorderLayout.NORTH);
        
        expressionArea = new JTextArea(2, 40);
        expressionArea.setLineWrap(true);
        expressionArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(expressionArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton validateBtn = new JButton("验证表达式");
        validateBtn.addActionListener(e -> validateExpression());
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> expressionArea.setText(""));
        buttonPanel.add(validateBtn);
        buttonPanel.add(clearBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 加载默认元素
     * 
     * ✅ 优化：不自动添加任何元素，让用户按需添加
     */
    private void loadDefaultElements() {
        // 不自动添加元素，保持空白，让用户按需添加
        // 用户可以通过顶部的按钮添加需要的元素：+ Method, + Path, + Parameter等
    }
    
    /**
     * 从配置加载
     */
    private void loadFromConfig() {
        if (config.getElements() != null) {
            for (HttpElementConfig element : config.getElements()) {
                HttpElementRow row = new HttpElementRow(element);
                elementRows.add(row);
                elementsPanel.add(row);
                if (element.getId() >= nextElementId) {
                    nextElementId = element.getId() + 1;
                }
            }
        }
        
        if (config.getConditionExpression() != null) {
            expressionArea.setText(config.getConditionExpression());
        }
        
        elementsPanel.revalidate();
        elementsPanel.repaint();
    }
    
    /**
     * 添加元素
     */
    private void addElement(ElementType type) {
        HttpElementConfig element = new HttpElementConfig(type);
        element.setId(nextElementId++);
        
        HttpElementRow row = new HttpElementRow(element);
        elementRows.add(row);
        elementsPanel.add(row);
        
        elementsPanel.revalidate();
        elementsPanel.repaint();
    }
    
    /**
     * 移除元素
     */
    private void removeElement(HttpElementRow row) {
        elementRows.remove(row);
        elementsPanel.remove(row);
        elementsPanel.revalidate();
        elementsPanel.repaint();
    }
    
    /**
     * 清空所有
     */
    private void clearAll() {
        int result = JOptionPane.showConfirmDialog(this,
            "确定要清空所有配置吗？",
            "确认",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            elementRows.clear();
            elementsPanel.removeAll();
            createElementsPanel();
            expressionArea.setText("");
            nextElementId = 1;
            loadDefaultElements();
            elementsPanel.revalidate();
            elementsPanel.repaint();
        }
    }
    
    /**
     * 从Proxy导入
     */
    private void importFromProxy() {
        JOptionPane.showMessageDialog(this,
            "从Proxy导入功能正在开发中...\n" +
            "将支持从Burp Proxy历史记录中选择请求并自动解析",
            "提示",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 验证表达式
     */
    private void validateExpression() {
        String expr = expressionArea.getText().trim();
        if (expr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "表达式为空，将使用默认的AND逻辑",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        try {
            // 简单验证：检查括号匹配
            int openCount = 0;
            for (char c : expr.toCharArray()) {
                if (c == '(') openCount++;
                if (c == ')') openCount--;
                if (openCount < 0) {
                    throw new IllegalArgumentException("括号不匹配");
                }
            }
            if (openCount != 0) {
                throw new IllegalArgumentException("括号不匹配");
            }
            
            // 检查是否包含有效的元素ID
            boolean hasValidId = false;
            for (HttpElementRow row : elementRows) {
                if (row.element.isUseForMatch() && 
                    expr.contains(String.valueOf(row.element.getId()))) {
                    hasValidId = true;
                    break;
                }
            }
            
            if (!hasValidId) {
                JOptionPane.showMessageDialog(this,
                    "警告：表达式中没有引用任何启用匹配的元素ID",
                    "警告",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            JOptionPane.showMessageDialog(this,
                "表达式格式正确！",
                "验证成功",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "表达式格式错误: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * 获取配置
     */
    public UnifiedHttpConfig getConfig() {
        config.getElements().clear();
        
        for (HttpElementRow row : elementRows) {
            config.addElement(row.getElement());
        }
        
        config.setConditionExpression(expressionArea.getText().trim());
        
        return config;
    }
    
    /**
     * ✅ 加载配置到UI
     */
    public void loadConfig(UnifiedHttpConfig config) {
        if (config == null) {
            return;
        }
        
        this.config = config;
        
        // 清空现有元素
        elementRows.clear();
        elementsPanel.removeAll();
        nextElementId = 1;
        
        // 加载元素
        if (config.getElements() != null) {
            for (UnifiedHttpConfig.HttpElementConfig element : config.getElements()) {
                // 创建元素行
                HttpElementRow row = new HttpElementRow(element);
                elementRows.add(row);
                elementsPanel.add(row);
                
                // 更新ID计数器
                if (element.getId() >= nextElementId) {
                    nextElementId = element.getId() + 1;
                }
            }
        }
        
        // 加载逻辑表达式
        if (config.getConditionExpression() != null) {
            expressionArea.setText(config.getConditionExpression());
        }
        
        // 刷新UI
        elementsPanel.revalidate();
        elementsPanel.repaint();
    }
    
    /**
     * HTTP元素行组件
     */
    private class HttpElementRow extends JPanel {
        
        private HttpElementConfig element;
        private JLabel idLabel;
        private JLabel typeLabel;
        private JTextField nameField;
        private JTextField valueField;
        private JButton configButton;
        private JCheckBox matchCheckbox;
        private JCheckBox injectCheckbox;
        private JButton deleteButton;
        private boolean isUpdatingSummary = false;  // ✅ 标志：是否正在更新摘要
        
        public HttpElementRow(HttpElementConfig element) {
            this.element = element;
            initComponents();
            updateDisplay();
        }
        
        private void initComponents() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
            setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            
            // ID标签
            idLabel = new JLabel();
            idLabel.setPreferredSize(new Dimension(30, 25));
            idLabel.setHorizontalAlignment(SwingConstants.CENTER);
            idLabel.setOpaque(true);
            idLabel.setBackground(new Color(230, 230, 250));
            idLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            
            // 类型标签
            typeLabel = new JLabel();
            typeLabel.setPreferredSize(new Dimension(100, 25));
            typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
            
            // 名称输入框（对于Parameter、Header、Cookie）
            nameField = new JTextField(15);
            nameField.setToolTipText("元素名称（如参数名、Header名）");
            
            // ✅ 添加DocumentListener实时保存name
            nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                private void update() {
                    // ✅ 忽略来自摘要更新的变更
                    if (!isUpdatingSummary) {
                        // ✅ 仅当没有配置 nameMatchConfig 时，才保存nameField的值
                        if (element.getNameMatchConfig() == null || 
                            element.getNameMatchConfig().getValues() == null ||
                            element.getNameMatchConfig().getValues().isEmpty()) {
                            element.setName(nameField.getText().trim());
                        }
                    }
                }
            });
            
            // 等号标签
            JLabel equalsLabel = new JLabel("=");
            
            // 值输入框
            valueField = new JTextField(20);
            valueField.setToolTipText("示例值（用于显示）");
            
            // ✅ 添加DocumentListener实时保存exampleValue
            valueField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                private void update() {
                    // ✅ 忽略来自摘要更新的变更
                    if (!isUpdatingSummary) {
                        // ✅ 仅当没有配置 payloads 时，才保存valueField的值
                        if (element.getPayloads() == null || element.getPayloads().isEmpty()) {
                            element.setExampleValue(valueField.getText().trim());
                        }
                    }
                }
            });
            
            // 配置按钮
            configButton = new JButton("⚙️");
            configButton.setToolTipText("详细配置");
            configButton.setPreferredSize(new Dimension(40, 25));
            configButton.addActionListener(e -> showDetailConfig());
            
            // 匹配复选框
            matchCheckbox = new JCheckBox("匹配");
            matchCheckbox.setToolTipText("作为请求条件");
            matchCheckbox.addActionListener(e -> {
                element.setUseForMatch(matchCheckbox.isSelected());
            });
            
            // 注入复选框
            injectCheckbox = new JCheckBox("注入");
            injectCheckbox.setToolTipText("作为注入点");
            injectCheckbox.addActionListener(e -> {
                element.setUseForInjection(injectCheckbox.isSelected());
            });
            
            // 删除按钮
            deleteButton = new JButton("✕");
            deleteButton.setToolTipText("删除此元素");
            deleteButton.setPreferredSize(new Dimension(40, 25));
            deleteButton.setForeground(Color.RED);
            deleteButton.addActionListener(e -> removeElement(HttpElementRow.this));
            
            // 添加组件
            add(idLabel);
            add(typeLabel);
            
            // 根据类型决定是否显示名称字段
            if (element.getType() == ElementType.PARAMETER ||
                element.getType() == ElementType.HEADER ||
                element.getType() == ElementType.COOKIE) {
                add(nameField);
                add(equalsLabel);
            }
            
            add(valueField);
            add(configButton);
            add(matchCheckbox);
            add(injectCheckbox);
            add(deleteButton);
        }
        
        private void updateDisplay() {
            idLabel.setText(String.valueOf(element.getId()));
            typeLabel.setText(element.getType().getDisplayName());
            
            // ✅ 优先使用updateSummaryFields()来正确显示摘要
            // 如果有配置详细信息（nameMatchConfig或payloads），显示摘要
            // 否则显示简单的name和exampleValue
            boolean hasDetailedConfig = 
                (element.getNameMatchConfig() != null && 
                 element.getNameMatchConfig().getValues() != null &&
                 !element.getNameMatchConfig().getValues().isEmpty()) ||
                (element.getPayloads() != null && !element.getPayloads().isEmpty());
            
            if (hasDetailedConfig) {
                // 有详细配置，使用updateSummaryFields()显示摘要
                updateSummaryFields();
            } else {
                // 没有详细配置，直接显示name和exampleValue
                isUpdatingSummary = true;
                try {
                    if (element.getName() != null) {
                        nameField.setText(element.getName());
                    }
                    
                    if (element.getExampleValue() != null) {
                        valueField.setText(element.getExampleValue());
                    }
                } finally {
                    isUpdatingSummary = false;
                }
            }
            
            matchCheckbox.setSelected(element.isUseForMatch());
            injectCheckbox.setSelected(element.isUseForInjection());
        }
        
        private void showDetailConfig() {
            // ✅ 修复：只在没有配置nameMatchConfig时才保存nameField的值
            if (element.getType() == ElementType.PARAMETER ||
                element.getType() == ElementType.HEADER ||
                element.getType() == ElementType.COOKIE) {
                // 仅当没有配置 nameMatchConfig 时，才从 nameField 读取值
                if (element.getNameMatchConfig() == null || 
                    element.getNameMatchConfig().getValues() == null ||
                    element.getNameMatchConfig().getValues().isEmpty()) {
                    element.setName(nameField.getText().trim());
                }
            }
            
            // ✅ 修复：只在没有配置payloads时才保存valueField的值
            if (element.getPayloads() == null || element.getPayloads().isEmpty()) {
                element.setExampleValue(valueField.getText().trim());
            }
            
            // 显示详细配置对话框
            HttpElementDetailDialog dialog = new HttpElementDetailDialog(
                SwingUtilities.getWindowAncestor(this),
                element
            );
            
            if (dialog.showDialog()) {
                element = dialog.getElement();
                updateDisplay();
                updateSummaryFields();  // ✅ 保存后自动更新摘要
            }
        }
        
        /**
         * ✅ 新增：更新摘要字段（等号左右两边的文本框）
         * 
         * 注意：设置标志避免触发DocumentListener导致摘要覆盖实际配置
         */
        private void updateSummaryFields() {
            isUpdatingSummary = true;  // ✅ 开始更新摘要，暂停数据绑定
            
            try {
                // 左边：显示匹配值的摘要
                if (element.isUseForMatch() && element.getNameMatchConfig() != null) {
                    var values = element.getNameMatchConfig().getValues();
                    if (values != null && !values.isEmpty()) {
                        if (values.size() == 1) {
                            nameField.setText(values.get(0));
                        } else {
                            nameField.setText(String.join(", ", values.subList(0, Math.min(2, values.size()))) + 
                                            (values.size() > 2 ? "..." : ""));
                        }
                    } else {
                        nameField.setText("");  // 清空
                    }
                } else {
                    // 如果不是用于匹配，显示element.name（如果有）
                    if (element.getName() != null && !element.getName().isEmpty()) {
                        nameField.setText(element.getName());
                    }
                }
                
                // 右边：显示payload的摘要
                if (element.isUseForInjection() && element.getPayloads() != null) {
                    var payloads = element.getPayloads();
                    if (!payloads.isEmpty()) {
                        if (payloads.size() == 1) {
                            valueField.setText(payloads.get(0));
                        } else {
                            // 显示第一个payload和总数
                            String summary = payloads.get(0);
                            if (summary.length() > 20) {
                                summary = summary.substring(0, 20) + "...";
                            }
                            valueField.setText(summary + " (+" + (payloads.size() - 1) + "个)");
                        }
                    } else {
                        valueField.setText("");  // 清空
                    }
                } else {
                    // 如果不是用于注入，显示element.exampleValue（如果有）
                    if (element.getExampleValue() != null && !element.getExampleValue().isEmpty()) {
                        valueField.setText(element.getExampleValue());
                    }
                }
            } finally {
                isUpdatingSummary = false;  // ✅ 摘要更新完成，恢复数据绑定
            }
        }
        
        public HttpElementConfig getElement() {
            // ✅ 修复：只在没有配置nameMatchConfig时才从nameField读取值
            // 避免摘要覆盖实际配置
            if (element.getType() == ElementType.PARAMETER ||
                element.getType() == ElementType.HEADER ||
                element.getType() == ElementType.COOKIE) {
                // 仅当没有配置 nameMatchConfig 时，才从 nameField 读取值
                if (element.getNameMatchConfig() == null || 
                    element.getNameMatchConfig().getValues() == null ||
                    element.getNameMatchConfig().getValues().isEmpty()) {
                    element.setName(nameField.getText().trim());
                }
            }
            
            // ✅ 修复：只在没有配置payloads时才从valueField读取值
            if (element.getPayloads() == null || element.getPayloads().isEmpty()) {
                element.setExampleValue(valueField.getText().trim());
            }
            
            element.setUseForMatch(matchCheckbox.isSelected());
            element.setUseForInjection(injectCheckbox.isSelected());
            
            return element;
        }
    }
}


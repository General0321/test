package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.UnifiedResponseConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一响应配置面板
 * 类似UnifiedHttpConfigPanel，但针对HTTP响应
 */
public class UnifiedResponseConfigPanel extends JPanel {
    
    private UnifiedResponseConfig config;
    private List<ResponseElementRow> elementRows;
    private JPanel elementsPanel;
    private JTextArea expressionArea;
    private int nextElementId = 1;
    
    public UnifiedResponseConfigPanel() {
        this.config = new UnifiedResponseConfig();
        this.elementRows = new ArrayList<>();
        
        initComponents();
    }
    
    public UnifiedResponseConfigPanel(UnifiedResponseConfig config) {
        this.config = config != null ? config : new UnifiedResponseConfig();
        this.elementRows = new ArrayList<>();
        
        initComponents();
        loadFromConfig();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部工具栏
        add(createToolbar(), BorderLayout.NORTH);
        
        // 中间响应元素面板
        JScrollPane scrollPane = new JScrollPane(createElementsPanel());
        scrollPane.setBorder(BorderFactory.createTitledBorder("HTTP响应配置"));
        add(scrollPane, BorderLayout.CENTER);
        
        // 底部表达式面板
        add(createExpressionPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建工具栏
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addStatusBtn = new JButton("+ Status Code");
        addStatusBtn.addActionListener(e -> addElement(ElementType.STATUS_CODE));
        
        JButton addHeadersBtn = new JButton("+ Response Headers");
        addHeadersBtn.addActionListener(e -> addElement(ElementType.RESPONSE_HEADERS));
        
        JButton addBodyBtn = new JButton("+ Response Body");
        addBodyBtn.addActionListener(e -> addElement(ElementType.RESPONSE_BODY));
        
        JButton addTimeBtn = new JButton("+ Response Time");
        addTimeBtn.addActionListener(e -> addElement(ElementType.RESPONSE_TIME));
        
        JButton addLengthBtn = new JButton("+ Response Length");
        addLengthBtn.addActionListener(e -> addElement(ElementType.RESPONSE_LENGTH));
        
        JButton addCollaboratorBtn = new JButton("+ Collaborator");
        addCollaboratorBtn.addActionListener(e -> addElement(ElementType.COLLABORATOR));
        
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> clearAll());
        
        toolbar.add(addStatusBtn);
        toolbar.add(addHeadersBtn);
        toolbar.add(addBodyBtn);
        toolbar.add(addTimeBtn);
        toolbar.add(addLengthBtn);
        toolbar.add(addCollaboratorBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(clearBtn);
        
        return toolbar;
    }
    
    /**
     * 创建响应元素面板
     */
    private JPanel createElementsPanel() {
        elementsPanel = new JPanel();
        elementsPanel.setLayout(new BoxLayout(elementsPanel, BoxLayout.Y_AXIS));
        
        JLabel hintLabel = new JLabel("配置说明：在此配置响应匹配条件（什么样的响应代表检测成功）");
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
        
        JLabel label = new JLabel("使用元素ID和逻辑运算符组合复杂条件，例如: (1 AND 2) OR 3。留空则表示所有元素都需满足（AND关系）");
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
     * 从配置加载
     */
    private void loadFromConfig() {
        if (config.getElements() != null) {
            for (ResponseElementConfig element : config.getElements()) {
                ResponseElementRow row = new ResponseElementRow(element);
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
        ResponseElementConfig element = new ResponseElementConfig(type);
        element.setId(nextElementId++);
        
        ResponseElementRow row = new ResponseElementRow(element);
        elementRows.add(row);
        elementsPanel.add(row);
        
        elementsPanel.revalidate();
        elementsPanel.repaint();
    }
    
    /**
     * 移除元素
     */
    private void removeElement(ResponseElementRow row) {
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
            elementsPanel.revalidate();
            elementsPanel.repaint();
        }
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
    public UnifiedResponseConfig getConfig() {
        config.getElements().clear();
        
        for (ResponseElementRow row : elementRows) {
            config.addElement(row.getElement());
        }
        
        config.setConditionExpression(expressionArea.getText().trim());
        
        return config;
    }
    
    /**
     * ✅ 加载配置到UI
     */
    public void loadConfig(UnifiedResponseConfig config) {
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
            for (UnifiedResponseConfig.ResponseElementConfig element : config.getElements()) {
                // 创建元素行
                ResponseElementRow row = new ResponseElementRow(element);
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
     * 响应元素行组件
     */
    private class ResponseElementRow extends JPanel {
        
        private ResponseElementConfig element;
        private JLabel idLabel;
        private JLabel typeLabel;
        private JTextField summaryField;
        private JButton configButton;
        private JButton deleteButton;
        
        public ResponseElementRow(ResponseElementConfig element) {
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
            idLabel.setBackground(new Color(255, 240, 245));
            idLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            
            // 类型标签
            typeLabel = new JLabel();
            typeLabel.setPreferredSize(new Dimension(150, 25));
            typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
            
            // 摘要字段
            summaryField = new JTextField(30);
            summaryField.setEditable(false);
            summaryField.setToolTipText("匹配条件摘要");
            
            // 配置按钮
            configButton = new JButton("⚙️");
            configButton.setToolTipText("详细配置");
            configButton.setPreferredSize(new Dimension(40, 25));
            configButton.addActionListener(e -> showDetailConfig());
            
            // 删除按钮
            deleteButton = new JButton("✕");
            deleteButton.setToolTipText("删除此元素");
            deleteButton.setPreferredSize(new Dimension(40, 25));
            deleteButton.setForeground(Color.RED);
            deleteButton.addActionListener(e -> removeElement(ResponseElementRow.this));
            
            // 添加组件
            add(idLabel);
            add(typeLabel);
            add(summaryField);
            add(configButton);
            add(deleteButton);
        }
        
        private void updateDisplay() {
            idLabel.setText(String.valueOf(element.getId()));
            typeLabel.setText(element.getType().getDisplayName());
            summaryField.setText(getSummary());
        }
        
        private String getSummary() {
            MatchConfig config = element.getMatchConfig();
            if (config == null) return "";
            
            switch (element.getType()) {
                case RESPONSE_TIME:
                case RESPONSE_LENGTH:
                    return config.getComparisonOperator().getSymbol() + " " + config.getNumericValue();
                    
                case COLLABORATOR:
                    if (config.getCollaboratorTypes() != null && !config.getCollaboratorTypes().isEmpty()) {
                        return String.join(", ", 
                            config.getCollaboratorTypes().stream()
                                .map(CollaboratorType::getDisplayName)
                                .toArray(String[]::new));
                    }
                    return "任意交互";
                    
                default:
                    if (config.getValues() != null && !config.getValues().isEmpty()) {
                        String first = config.getValues().get(0);
                        int count = config.getValues().size();
                        if (count > 1) {
                            return config.getMatchType().getDisplayName() + ": " + 
                                   first + " (+" + (count-1) + "个)";
                        }
                        return config.getMatchType().getDisplayName() + ": " + first;
                    }
                    return config.getMatchType().getDisplayName();
            }
        }
        
        private void showDetailConfig() {
            ResponseElementDetailDialog dialog = new ResponseElementDetailDialog(
                SwingUtilities.getWindowAncestor(this),
                element
            );
            
            if (dialog.showDialog()) {
                element = dialog.getElement();
                updateDisplay();
            }
        }
        
        public ResponseElementConfig getElement() {
            return element;
        }
    }
}


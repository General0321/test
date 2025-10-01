package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.Configuration;

import javax.swing.*;
import java.awt.*;

/**
 * 响应匹配规则编辑对话框
 */
public class MatchRuleEditorDialog extends JDialog {
    private Configuration.MatchRule matchRule;
    private boolean confirmed = false;
    
    private JComboBox<String> locationCombo;
    private JComboBox<String> matchTypeCombo;
    private JTextField ruleValueField;
    private JComboBox<String> operatorCombo;
    
    public MatchRuleEditorDialog(Window owner, Configuration.MatchRule existing) {
        super(owner, "响应匹配规则编辑", ModalityType.APPLICATION_MODAL);
        this.matchRule = existing != null ? existing : new Configuration.MatchRule();
        
        initComponents();
        setupLayout();
        loadMatchRule();
        
        setSize(550, 350);
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        // 匹配位置
        String[] locations = {
            "Response Body",
            "Response Headers",
            "Status Code",
            "Response Time",
            "Collaborator Interaction"
        };
        locationCombo = new JComboBox<>(locations);
        locationCombo.addActionListener(e -> updateMatchTypes());
        
        // 匹配类型
        matchTypeCombo = new JComboBox<>();
        
        // 规则值
        ruleValueField = new JTextField(30);
        
        // 操作符
        operatorCombo = new JComboBox<>(new String[]{"OR", "AND"});
    }
    
    private void setupLayout() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 匹配位置
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        mainPanel.add(new JLabel("匹配位置:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mainPanel.add(locationCombo, gbc);
        
        // 匹配类型
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        mainPanel.add(new JLabel("匹配类型:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mainPanel.add(matchTypeCombo, gbc);
        
        // 规则值
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        mainPanel.add(new JLabel("规则值:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mainPanel.add(ruleValueField, gbc);
        
        // 操作符
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        mainPanel.add(new JLabel("逻辑操作符:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mainPanel.add(operatorCombo, gbc);
        
        // 说明
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JTextArea helpText = new JTextArea(6, 40);
        helpText.setText(
            "说明：\n" +
            "• Response Body/Headers: 输入要匹配的字符串或正则表达式\n" +
            "• Status Code: 输入状态码（如: 200, 404）\n" +
            "• Response Time: 输入时间（毫秒）\n" +
            "• Collaborator Interaction: 输入交互类型（DNS, HTTP, SMTP）或留空匹配所有\n" +
            "• 逻辑操作符用于与下一个规则组合（OR=或，AND=且）"
        );
        helpText.setEditable(false);
        helpText.setBackground(mainPanel.getBackground());
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        mainPanel.add(new JScrollPane(helpText), gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirmButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        
        confirmButton.addActionListener(e -> confirmAction());
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // 初始化匹配类型
        updateMatchTypes();
    }
    
    private void updateMatchTypes() {
        String location = (String) locationCombo.getSelectedItem();
        matchTypeCombo.removeAllItems();
        
        if ("Status Code".equals(location)) {
            matchTypeCombo.addItem("Equals");
            matchTypeCombo.addItem("Greater Than");
            matchTypeCombo.addItem("Less Than");
            matchTypeCombo.addItem("Not Equals");
        } else if ("Response Time".equals(location)) {
            matchTypeCombo.addItem("Greater Than");
            matchTypeCombo.addItem("Less Than");
        } else if ("Collaborator Interaction".equals(location)) {
            matchTypeCombo.addItem("Has Interaction");
            matchTypeCombo.addItem("No Interaction");
        } else {
            // Response Body, Response Headers
            matchTypeCombo.addItem("Contains");
            matchTypeCombo.addItem("Regex Match");
            matchTypeCombo.addItem("Equals");
            matchTypeCombo.addItem("Not Contains");
            matchTypeCombo.addItem("Not Equals");
            matchTypeCombo.addItem("Starts With");
            matchTypeCombo.addItem("Ends With");
        }
    }
    
    private void loadMatchRule() {
        if (matchRule.getLocation() != null) {
            locationCombo.setSelectedItem(matchRule.getLocation());
        }
        if (matchRule.getMatchType() != null) {
            matchTypeCombo.setSelectedItem(matchRule.getMatchType());
        }
        if (matchRule.getRule() != null) {
            ruleValueField.setText(matchRule.getRule());
        }
        if (matchRule.getOperator() != null) {
            operatorCombo.setSelectedItem(matchRule.getOperator());
        }
    }
    
    private void confirmAction() {
        String ruleValue = ruleValueField.getText().trim();
        String location = (String) locationCombo.getSelectedItem();
        
        // 验证
        if (!"Collaborator Interaction".equals(location) && ruleValue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入规则值", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证状态码格式
        if ("Status Code".equals(location)) {
            try {
                Integer.parseInt(ruleValue);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "状态码必须是数字", "验证错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        // 验证响应时间格式
        if ("Response Time".equals(location)) {
            try {
                Long.parseLong(ruleValue);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "响应时间必须是数字（毫秒）", "验证错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        matchRule.setLocation((String) locationCombo.getSelectedItem());
        matchRule.setMatchType((String) matchTypeCombo.getSelectedItem());
        matchRule.setRule(ruleValue);
        matchRule.setOperator((String) operatorCombo.getSelectedItem());
        
        confirmed = true;
        dispose();
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public Configuration.MatchRule getMatchRule() {
        return matchRule;
    }
    
    public boolean showDialog() {
        setVisible(true);
        return confirmed;
    }
}


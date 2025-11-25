package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.ResponseComparisonConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 响应对比配置面板（简化版）
 * 基于真实的HackerOne漏洞检测场景设计
 */
public class ResponseComparisonConfigPanelSimplified extends JPanel {
    
    private ResponseComparisonConfig config;
    
    // 统一的对比配置
    private JComboBox<String> comparisonTypeCombo;
    private JSpinner referencePairIdSpinner;
    private JTextField thresholdField;
    private JCheckBox ignoreDynamicContentCheckBox;
    
    // ✅ 时间倍数范围配置（新增）
    private JTextField timeMultiplierMinField;
    private JTextField timeMultiplierMaxField;
    private JLabel timeMultiplierRangeLabel;
    
    public ResponseComparisonConfigPanelSimplified(ResponseComparisonConfig config) {
        this.config = config != null ? config : new ResponseComparisonConfig();
        initComponents();
        loadFromConfig();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);
        
        // 主配置面板
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 标题说明
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel titleLabel = new JLabel("对比当前Pair与其他Pair的响应");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        mainPanel.add(titleLabel, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel descLabel = new JLabel("⚠️ 注意：这里是对比当前Pair与其他Pair的响应差异，而非单独检查当前Pair的响应");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descLabel.setForeground(new Color(100, 100, 100));
        mainPanel.add(descLabel, gbc);
        row++;
        
        // 分隔线
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        gbc.insets = new Insets(15, 0, 15, 0);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(8, 8, 8, 8);
        row++;
        
        gbc.gridwidth = 1;
        
        // 1. 对比类型
        gbc.gridx = 0; gbc.gridy = row;
        JLabel typeLabel = new JLabel("对比类型:");
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 12f));
        mainPanel.add(typeLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        comparisonTypeCombo = new JComboBox<>(new String[]{
            "不对比",
            "响应体内容 - 与引用Pair相等",
            "响应体内容 - 与引用Pair不相等",
            "响应时间 - 大于引用Pair",
            "响应时间 - 小于引用Pair",
            "响应时间 - 等于引用Pair",
            "响应时间 - 不等于引用Pair",
            "响应时间 - 倍数范围对比（如：1.9-2.1倍）",
            "响应长度 - 大于引用Pair（精确比较）",
            "响应长度 - 小于引用Pair（精确比较）",
            "响应长度 - 等于引用Pair（精确比较）",
            "响应长度 - 不等于引用Pair（精确比较）",
            "响应长度 - 差异大于阈值（带容差的比较）",
            "状态码 - 与引用Pair相等",
            "状态码 - 与引用Pair不相等"
        });
        comparisonTypeCombo.setFont(comparisonTypeCombo.getFont().deriveFont(Font.PLAIN, 12f));
        comparisonTypeCombo.addActionListener(e -> updateFieldVisibility());
        mainPanel.add(comparisonTypeCombo, gbc);
        row++;
        
        gbc.gridwidth = 1;
        
        // 2. 引用Pair ID
        gbc.gridx = 0; gbc.gridy = row;
        JLabel refLabel = new JLabel("引用Pair:");
        refLabel.setFont(refLabel.getFont().deriveFont(Font.BOLD, 12f));
        mainPanel.add(refLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        referencePairIdSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        referencePairIdSpinner.setPreferredSize(new Dimension(80, 30));
        referencePairIdSpinner.setFont(referencePairIdSpinner.getFont().deriveFont(Font.BOLD, 13f));
        ((JSpinner.DefaultEditor)referencePairIdSpinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.CENTER);
        mainPanel.add(referencePairIdSpinner, gbc);
        
        gbc.gridx = 2; gbc.gridy = row;
        JLabel refHintLabel = new JLabel("（与Pair #N的响应对比）");
        refHintLabel.setForeground(new Color(100, 100, 100));
        mainPanel.add(refHintLabel, gbc);
        row++;
        
        // 3. 阈值（仅时间和长度需要）
        gbc.gridx = 0; gbc.gridy = row;
        JLabel thresholdLabel = new JLabel("阈值:");
        thresholdLabel.setFont(thresholdLabel.getFont().deriveFont(Font.BOLD, 12f));
        mainPanel.add(thresholdLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        thresholdField = new JTextField(10);
        thresholdField.setPreferredSize(new Dimension(80, 30));
        thresholdField.setFont(thresholdField.getFont().deriveFont(Font.PLAIN, 12f));
        mainPanel.add(thresholdField, gbc);
        
        gbc.gridx = 2; gbc.gridy = row;
        JLabel thresholdHintLabel = new JLabel("（时间单位：毫秒）");
        thresholdHintLabel.setForeground(new Color(100, 100, 100));
        mainPanel.add(thresholdHintLabel, gbc);
        row++;
        
        // ✅ 新增：时间倍数范围配置（仅倍数范围对比时显示）
        gbc.gridx = 0; gbc.gridy = row;
        timeMultiplierRangeLabel = new JLabel("倍数范围:");
        timeMultiplierRangeLabel.setFont(timeMultiplierRangeLabel.getFont().deriveFont(Font.BOLD, 12f));
        mainPanel.add(timeMultiplierRangeLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        JPanel multiplierPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        multiplierPanel.setBackground(Color.WHITE);
        timeMultiplierMinField = new JTextField(8);
        timeMultiplierMinField.setPreferredSize(new Dimension(60, 30));
        timeMultiplierMinField.setFont(timeMultiplierMinField.getFont().deriveFont(Font.PLAIN, 12f));
        multiplierPanel.add(timeMultiplierMinField);
        
        JLabel toLabel = new JLabel(" 至 ");
        toLabel.setForeground(new Color(100, 100, 100));
        multiplierPanel.add(toLabel);
        
        timeMultiplierMaxField = new JTextField(8);
        timeMultiplierMaxField.setPreferredSize(new Dimension(60, 30));
        timeMultiplierMaxField.setFont(timeMultiplierMaxField.getFont().deriveFont(Font.PLAIN, 12f));
        multiplierPanel.add(timeMultiplierMaxField);
        mainPanel.add(multiplierPanel, gbc);
        
        gbc.gridx = 2; gbc.gridy = row;
        JLabel multiplierHintLabel = new JLabel("（如：1.9-2.1）");
        multiplierHintLabel.setForeground(new Color(100, 100, 100));
        mainPanel.add(multiplierHintLabel, gbc);
        row++;
        
        // 4. 忽略动态内容（仅响应体对比需要）
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        ignoreDynamicContentCheckBox = new JCheckBox("忽略动态内容（时间戳、token等）");
        ignoreDynamicContentCheckBox.setSelected(true);
        ignoreDynamicContentCheckBox.setFont(ignoreDynamicContentCheckBox.getFont().deriveFont(Font.PLAIN, 12f));
        ignoreDynamicContentCheckBox.setBackground(Color.WHITE);
        mainPanel.add(ignoreDynamicContentCheckBox, gbc);
        row++;
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 底部示例说明
        add(createExamplesPanel(), BorderLayout.SOUTH);
        
        // ✅ 初始化：默认隐藏倍数范围字段
        timeMultiplierRangeLabel.setVisible(false);
        timeMultiplierMinField.setVisible(false);
        timeMultiplierMaxField.setVisible(false);
        
        // 初始化字段可见性
        updateFieldVisibility();
    }
    
    /**
     * 创建示例说明面板
     */
    private JPanel createExamplesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                " 💡 真实检测场景 ",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 11),
                new Color(70, 130, 180)
            ),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        panel.setBackground(new Color(250, 252, 255));
        
        JTextArea examplesArea = new JTextArea(
            "1. 时间盲注（HackerOne #1024984）：\n" +
            "   Pair 1: SLEEP(0) → Pair 2: SLEEP(5) → Pair 3: SLEEP(10)\n" +
            "   配置：Pair 2 对比类型=\"响应时间 - 大于\"，对比Pair=1，阈值=4000\n" +
            "          Pair 3 对比类型=\"响应时间 - 大于\"，对比Pair=2，阈值=4000\n\n" +
            "2. 布尔盲注（HackerOne #1102591）：\n" +
            "   Pair 1: TRUE payload → Pair 2: FALSE payload\n" +
            "   配置：Pair 2 对比类型=\"响应体内容 - 不相等\"，对比Pair=1，勾选忽略动态内容"
        );
        examplesArea.setEditable(false);
        examplesArea.setLineWrap(true);
        examplesArea.setWrapStyleWord(true);
        examplesArea.setBackground(new Color(250, 252, 255));
        examplesArea.setFont(examplesArea.getFont().deriveFont(Font.PLAIN, 11f));
        examplesArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        panel.add(examplesArea, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 根据选择的对比类型更新字段可见性
     */
    private void updateFieldVisibility() {
        String selected = (String) comparisonTypeCombo.getSelectedItem();
        
        // 阈值字段：仅在特定情况下需要
        // - 响应时间：仅"大于"、"小于"、"不等于"需要阈值（倍数范围不需要）
        // - 响应长度：仅"差异大于阈值"需要阈值，其他对比是精确比较
        boolean needThreshold = 
            (selected.contains("响应时间") && (selected.contains("大于") || selected.contains("小于") || selected.contains("不等于"))) ||
            selected.contains("差异大于阈值");
        thresholdField.setEnabled(needThreshold);
        
        // ✅ 新增：倍数范围字段（仅倍数范围对比时显示）
        boolean needMultiplierRange = selected != null && selected.contains("倍数范围对比");
        if (timeMultiplierRangeLabel != null) {
            timeMultiplierRangeLabel.setVisible(needMultiplierRange);
        }
        if (timeMultiplierMinField != null) {
            timeMultiplierMinField.setVisible(needMultiplierRange);
            timeMultiplierMinField.setEnabled(needMultiplierRange);
        }
        if (timeMultiplierMaxField != null) {
            timeMultiplierMaxField.setVisible(needMultiplierRange);
            timeMultiplierMaxField.setEnabled(needMultiplierRange);
        }
        
        // 忽略动态内容：仅响应体对比需要
        boolean needIgnoreDynamic = selected.contains("响应体内容");
        ignoreDynamicContentCheckBox.setEnabled(needIgnoreDynamic);
        
        // 引用Pair：除了"不对比"都需要
        boolean needReference = !selected.equals("不对比");
        referencePairIdSpinner.setEnabled(needReference);
    }
    
    /**
     * 从配置加载
     */
    private void loadFromConfig() {
        if (config == null) return;
        
        // 根据config反推对比类型
        // 1. 响应体对比模式
        if (config.getBodyComparisonMode() != null && config.getBodyComparisonMode() != ResponseComparisonConfig.BodyComparisonMode.NONE) {
            if (config.getBodyComparisonMode() == ResponseComparisonConfig.BodyComparisonMode.BODY_EQUALS) {
                comparisonTypeCombo.setSelectedItem("响应体内容 - 与引用Pair相等");
            } else if (config.getBodyComparisonMode() == ResponseComparisonConfig.BodyComparisonMode.BODY_NOT_EQUALS) {
                comparisonTypeCombo.setSelectedItem("响应体内容 - 与引用Pair不相等");
            }
            if (config.getBodyComparisonReferencePairId() != null) {
                referencePairIdSpinner.setValue(config.getBodyComparisonReferencePairId());
            }
            ignoreDynamicContentCheckBox.setSelected(config.isUseCleanedBodyComparison());
        }
        // 2. 特征对比模式（响应时间、长度、状态码）
        // ✅ 优先检查时间对比模式（RELATIVE_TO_PAIR + 倍数范围）
        else if (config.getTimeComparisonMode() == ResponseComparisonConfig.TimeComparisonMode.RELATIVE_TO_PAIR
                && config.getReferencePairId() != null) {
            Double minMultiplier = config.getRelativeTimeMultiplierMin();
            Double maxMultiplier = config.getRelativeTimeMultiplierMax();
            if (minMultiplier != null && maxMultiplier != null && minMultiplier > 0 && maxMultiplier > 0) {
                // 倍数范围模式
                referencePairIdSpinner.setValue(config.getReferencePairId());
                comparisonTypeCombo.setSelectedItem("响应时间 - 倍数范围对比（如：1.9-2.1倍）");
                timeMultiplierMinField.setText(String.valueOf(minMultiplier));
                timeMultiplierMaxField.setText(String.valueOf(maxMultiplier));
                // ✅ 确保字段可见性正确
                updateFieldVisibility();
            }
        }
        else if (config.getReferencePairId() != null && config.getReferenceFeatureType() != null) {
            referencePairIdSpinner.setValue(config.getReferencePairId());
            String featureType = config.getReferenceFeatureType();
            String operator = config.getReferenceOperator() != null ? config.getReferenceOperator() : "EQUALS";
            
            if ("RESPONSE_TIME".equals(featureType)) {
                switch (operator) {
                    case "GREATER_THAN":
                        comparisonTypeCombo.setSelectedItem("响应时间 - 大于引用Pair");
                        break;
                    case "LESS_THAN":
                        comparisonTypeCombo.setSelectedItem("响应时间 - 小于引用Pair");
                        break;
                    case "EQUALS":
                        comparisonTypeCombo.setSelectedItem("响应时间 - 等于引用Pair");
                        break;
                    case "NOT_EQUALS":
                        comparisonTypeCombo.setSelectedItem("响应时间 - 不等于引用Pair");
                        break;
                }
                if (config.getReferenceValue() != null) {
                    thresholdField.setText(config.getReferenceValue());
                } else if (config.getTimeDifferenceThreshold() > 0) {
                    thresholdField.setText(String.valueOf(config.getTimeDifferenceThreshold()));
                }
            } else if ("RESPONSE_LENGTH".equals(featureType)) {
                switch (operator) {
                    case "GREATER_THAN":
                        comparisonTypeCombo.setSelectedItem("响应长度 - 大于引用Pair（精确比较）");
                        break;
                    case "LESS_THAN":
                        comparisonTypeCombo.setSelectedItem("响应长度 - 小于引用Pair（精确比较）");
                        break;
                    case "EQUALS":
                        comparisonTypeCombo.setSelectedItem("响应长度 - 等于引用Pair（精确比较）");
                        break;
                    case "NOT_EQUALS":
                        comparisonTypeCombo.setSelectedItem("响应长度 - 不等于引用Pair（精确比较）");
                        break;
                    case "DIFFERENCE_GREATER_THAN":
                        comparisonTypeCombo.setSelectedItem("响应长度 - 差异大于阈值（带容差的比较）");
                        if (config.getLengthDifferenceThreshold() > 0) {
                            thresholdField.setText(String.valueOf(config.getLengthDifferenceThreshold()));
                        }
                        break;
                }
            } else if ("STATUS_CODE".equals(featureType)) {
                if ("EQUALS".equals(operator)) {
                    comparisonTypeCombo.setSelectedItem("状态码 - 与引用Pair相等");
                } else if ("NOT_EQUALS".equals(operator)) {
                    comparisonTypeCombo.setSelectedItem("状态码 - 与引用Pair不相等");
                }
            }
        }
        
        updateFieldVisibility();
    }
    
    /**
     * 获取配置
     */
    public ResponseComparisonConfig getConfig() {
        String selected = (String) comparisonTypeCombo.getSelectedItem();
        
        if ("不对比".equals(selected)) {
            return new ResponseComparisonConfig(); // 返回空配置
        }
        
        ResponseComparisonConfig newConfig = new ResponseComparisonConfig();
        Integer refPairId = (Integer) referencePairIdSpinner.getValue();
        
        if (selected.contains("响应体内容")) {
            // 响应体对比
            if (selected.contains("不相等")) {
                newConfig.setBodyComparisonMode(ResponseComparisonConfig.BodyComparisonMode.BODY_NOT_EQUALS);
            } else {
                newConfig.setBodyComparisonMode(ResponseComparisonConfig.BodyComparisonMode.BODY_EQUALS);
            }
            newConfig.setBodyComparisonReferencePairId(refPairId);
            newConfig.setUseCleanedBodyComparison(ignoreDynamicContentCheckBox.isSelected());
        } 
 
        else {
            // 特征对比
            newConfig.setReferencePairId(refPairId);
            
            if (selected.contains("响应时间")) {
                // ✅ 新增：处理倍数范围模式
                if (selected.contains("倍数范围对比")) {
                    // 设置时间对比模式为相对模式
                    newConfig.setTimeComparisonMode(ResponseComparisonConfig.TimeComparisonMode.RELATIVE_TO_PAIR);
                    // ✅ 注意：referencePairId已在上面设置，不需要重复设置
                    // ✅ 注意：倍数范围模式不需要设置referenceFeatureType，因为通过timeComparisonMode判断
                    
                    // 读取倍数范围
                    try {
                        String minText = timeMultiplierMinField.getText().trim();
                        String maxText = timeMultiplierMaxField.getText().trim();
                        if (!minText.isEmpty() && !maxText.isEmpty()) {
                            double minMultiplier = Double.parseDouble(minText);
                            double maxMultiplier = Double.parseDouble(maxText);
                            
                            // ✅ 验证：确保min < max且都是正数
                            if (minMultiplier <= 0 || maxMultiplier <= 0) {
                                throw new NumberFormatException("倍数必须大于0");
                            }
                            if (minMultiplier >= maxMultiplier) {
                                // 自动交换
                                double temp = minMultiplier;
                                minMultiplier = maxMultiplier;
                                maxMultiplier = temp;
                            }
                            
                            newConfig.setRelativeTimeMultiplierMin(minMultiplier);
                            newConfig.setRelativeTimeMultiplierMax(maxMultiplier);
                        } else {
                            // 如果输入为空，使用默认值
                            newConfig.setRelativeTimeMultiplierMin(1.9);
                            newConfig.setRelativeTimeMultiplierMax(2.1);
                        }
                    } catch (NumberFormatException e) {
                        // 如果输入无效，使用默认值
                        newConfig.setRelativeTimeMultiplierMin(1.9);
                        newConfig.setRelativeTimeMultiplierMax(2.1);
                    }
                } else {
                    // 普通时间对比模式：使用referenceFeatureType
                    newConfig.setReferenceFeatureType("RESPONSE_TIME");
                    
                    // 根据选择的对比类型设置操作符
                    if (selected.contains("大于")) {
                        newConfig.setReferenceOperator("GREATER_THAN");
                        newConfig.setReferenceValue(thresholdField.getText());
                    } else if (selected.contains("小于")) {
                        newConfig.setReferenceOperator("LESS_THAN");
                        newConfig.setReferenceValue(thresholdField.getText());
                    } else if (selected.contains("等于") && !selected.contains("不等于")) {
                        newConfig.setReferenceOperator("EQUALS");
                    } else if (selected.contains("不等于")) {
                        newConfig.setReferenceOperator("NOT_EQUALS");
                        newConfig.setReferenceValue(thresholdField.getText());
                    }
                }
            } 
            else if (selected.contains("响应长度")) {
                newConfig.setReferenceFeatureType("RESPONSE_LENGTH");
                
                // 根据选择的对比类型设置操作符
                if (selected.contains("大于") && selected.contains("精确比较")) {
                    newConfig.setReferenceOperator("GREATER_THAN");
                } else if (selected.contains("小于") && selected.contains("精确比较")) {
                    newConfig.setReferenceOperator("LESS_THAN");
                } else if (selected.contains("等于") && selected.contains("精确比较") && !selected.contains("不等于")) {
                    newConfig.setReferenceOperator("EQUALS");
                } else if (selected.contains("不等于") && selected.contains("精确比较")) {
                    newConfig.setReferenceOperator("NOT_EQUALS");
                } else if (selected.contains("差异大于阈值")) {
                    newConfig.setReferenceOperator("DIFFERENCE_GREATER_THAN");
                    try {
                        newConfig.setLengthDifferenceThreshold(Integer.parseInt(thresholdField.getText()));
                    } catch (NumberFormatException e) {
                        newConfig.setLengthDifferenceThreshold(50);
                    }
                }
            } 
            else if (selected.contains("状态码")) {
                newConfig.setReferenceFeatureType("STATUS_CODE");
                if (selected.contains("不相等")) {
                    newConfig.setReferenceOperator("NOT_EQUALS");
                } else {
                    newConfig.setReferenceOperator("EQUALS");
                }
            }
        }
        
        return newConfig;
    }
}


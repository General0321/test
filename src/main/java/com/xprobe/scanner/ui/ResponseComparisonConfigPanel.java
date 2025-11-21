package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.ResponseComparisonConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 响应对比配置面板
 * 用于配置跨Pair特征引用和响应体对比
 */
public class ResponseComparisonConfigPanel extends JPanel {
    
    private ResponseComparisonConfig config;
    
    // 响应体对比配置
    private JComboBox<String> bodyComparisonModeCombo;
    private JSpinner bodyComparisonReferencePairIdSpinner;
    private JCheckBox useCleanedBodyComparisonCheckBox;
    private JComboBox<String> bodySaveModeCombo;
    
    // 通用特征对比配置
    private JCheckBox enableCrossPairReferenceCheckBox;
    private JSpinner referencePairIdSpinner;
    private JComboBox<String> referenceFeatureTypeCombo;
    private JComboBox<String> referenceOperatorCombo;
    private JTextField referenceValueField;
    
    // 基础对比配置
    private JCheckBox compareStatusCodeCheckBox;
    private JCheckBox compareLengthCheckBox;
    private JSpinner lengthDifferenceThresholdSpinner;
    private JCheckBox compareTimeCheckBox;
    private JSpinner timeDifferenceThresholdSpinner;
    
    // 动态内容忽略
    private JTextArea ignoreDynamicPatternsArea;
    
    public ResponseComparisonConfigPanel(ResponseComparisonConfig config) {
        this.config = config != null ? config : new ResponseComparisonConfig();
        initComponents();
        loadFromConfig();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 主面板 - 使用BoxLayout垂直排列
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        // 1. 响应体内容对比区域
        mainPanel.add(createBodyComparisonSection());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 2. 跨Pair特征引用区域
        mainPanel.add(createCrossPairReferenceSection());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 3. 基础对比配置区域
        mainPanel.add(createBasicComparisonSection());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 4. 动态内容忽略区域
        mainPanel.add(createIgnoreDynamicContentSection());
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
        
        // 底部提示信息
        add(createHintPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建响应体内容对比区域
     */
    private JPanel createBodyComparisonSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "📄 响应体内容对比",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 响应体对比模式
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(new JLabel("对比模式:"), gbc);
        
        bodyComparisonModeCombo = new JComboBox<>(new String[]{
            "NONE", "BODY_EQUALS", "BODY_NOT_EQUALS"
        });
        bodyComparisonModeCombo.setToolTipText(
            "<html>NONE: 不对比响应体<br>" +
            "BODY_EQUALS: 当前Pair响应体 = 引用Pair响应体<br>" +
            "BODY_NOT_EQUALS: 当前Pair响应体 ≠ 引用Pair响应体</html>"
        );
        bodyComparisonModeCombo.addActionListener(e -> toggleBodyComparisonFields());
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(bodyComparisonModeCombo, gbc);
        row++;
        
        // 引用的Pair ID（用于响应体对比）
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(new JLabel("引用Pair ID:"), gbc);
        
        bodyComparisonReferencePairIdSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        bodyComparisonReferencePairIdSpinner.setToolTipText("选择要对比的Pair ID（必须是之前的Pair）");
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(bodyComparisonReferencePairIdSpinner, gbc);
        row++;
        
        // 使用清理后的内容对比
        useCleanedBodyComparisonCheckBox = new JCheckBox("忽略动态内容（时间戳、token等）");
        useCleanedBodyComparisonCheckBox.setToolTipText("启用后，会先清理响应体中的动态内容再对比（推荐）");
        useCleanedBodyComparisonCheckBox.setSelected(true);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        panel.add(useCleanedBodyComparisonCheckBox, gbc);
        row++;
        
        // 响应体保存模式
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(new JLabel("保存模式:"), gbc);
        
        bodySaveModeCombo = new JComboBox<>(new String[]{
            "HASH_ONLY", "HASH_AND_CLEANED", "FULL_CONTENT"
        });
        bodySaveModeCombo.setToolTipText(
            "<html>HASH_ONLY: 只保存哈希（最省内存）<br>" +
            "HASH_AND_CLEANED: 保存哈希+清理后内容（推荐）<br>" +
            "FULL_CONTENT: 保存完整内容（最耗内存）</html>"
        );
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(bodySaveModeCombo, gbc);
        
        return panel;
    }
    
    /**
     * 创建跨Pair特征引用区域
     */
    private JPanel createCrossPairReferenceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "🔗 跨Pair特征引用",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 启用跨Pair引用
        enableCrossPairReferenceCheckBox = new JCheckBox("启用跨Pair特征引用");
        enableCrossPairReferenceCheckBox.setToolTipText("允许当前Pair引用之前Pair的响应特征进行对比");
        enableCrossPairReferenceCheckBox.addActionListener(e -> toggleCrossPairReferenceFields());
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        panel.add(enableCrossPairReferenceCheckBox, gbc);
        gbc.gridwidth = 1;
        row++;
        
        // 引用的Pair ID
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("引用Pair ID:"), gbc);
        
        referencePairIdSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        referencePairIdSpinner.setToolTipText("选择要引用的Pair ID（必须是当前Pair之前的ID）");
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(referencePairIdSpinner, gbc);
        
        // 引用特征类型
        gbc.gridx = 2; gbc.gridy = row;
        panel.add(new JLabel("特征类型:"), gbc);
        
        referenceFeatureTypeCombo = new JComboBox<>(new String[]{
            "STATUS_CODE", "RESPONSE_LENGTH", "RESPONSE_TIME", "RESPONSE_BODY"
        });
        referenceFeatureTypeCombo.setToolTipText("选择要引用的响应特征类型");
        gbc.gridx = 3; gbc.gridy = row;
        panel.add(referenceFeatureTypeCombo, gbc);
        row++;
        
        // 比较操作符
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("比较操作符:"), gbc);
        
        referenceOperatorCombo = new JComboBox<>(new String[]{
            "EQUALS", "NOT_EQUALS", "GREATER_THAN", "LESS_THAN", "CONTAINS", "NOT_CONTAINS"
        });
        referenceOperatorCombo.setToolTipText("选择比较操作符");
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(referenceOperatorCombo, gbc);
        
        // 比较值（可选）
        gbc.gridx = 2; gbc.gridy = row;
        panel.add(new JLabel("比较值(可选):"), gbc);
        
        referenceValueField = new JTextField(15);
        referenceValueField.setToolTipText("可选：直接指定比较值。留空则与引用Pair的特征值对比");
        gbc.gridx = 3; gbc.gridy = row;
        panel.add(referenceValueField, gbc);
        
        return panel;
    }
    
    /**
     * 创建基础对比配置区域
     */
    private JPanel createBasicComparisonSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "⚙️ 基础对比配置",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // 状态码对比
        compareStatusCodeCheckBox = new JCheckBox("对比状态码");
        compareStatusCodeCheckBox.setSelected(true);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        panel.add(compareStatusCodeCheckBox, gbc);
        row++;
        
        // 长度对比
        compareLengthCheckBox = new JCheckBox("对比响应长度");
        compareLengthCheckBox.setSelected(true);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(compareLengthCheckBox, gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(new JLabel("差异阈值(字节):"), gbc);
        
        lengthDifferenceThresholdSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 10000, 10));
        lengthDifferenceThresholdSpinner.setToolTipText("超过此阈值才认为长度有显著差异");
        gbc.gridx = 2; gbc.gridy = row;
        panel.add(lengthDifferenceThresholdSpinner, gbc);
        row++;
        
        // 时间对比
        compareTimeCheckBox = new JCheckBox("对比响应时间");
        compareTimeCheckBox.setSelected(false);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        panel.add(compareTimeCheckBox, gbc);
        
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(new JLabel("差异阈值(毫秒):"), gbc);
        
        timeDifferenceThresholdSpinner = new JSpinner(new SpinnerNumberModel(1000, 0, 60000, 100));
        timeDifferenceThresholdSpinner.setToolTipText("超过此阈值才认为时间有显著差异");
        gbc.gridx = 2; gbc.gridy = row;
        panel.add(timeDifferenceThresholdSpinner, gbc);
        
        return panel;
    }
    
    /**
     * 创建动态内容忽略区域
     */
    private JPanel createIgnoreDynamicContentSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "🎭 忽略动态内容模式（正则）",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));
        
        JLabel hint = new JLabel("每行一个正则表达式，用于忽略响应体中的动态内容（时间戳、token等）");
        panel.add(hint, BorderLayout.NORTH);
        
        ignoreDynamicPatternsArea = new JTextArea(5, 40);
        ignoreDynamicPatternsArea.setLineWrap(false);
        ignoreDynamicPatternsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ignoreDynamicPatternsArea.setText(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\n" +
            "\"csrf_token\":\"[^\"]+\"\n" +
            "nonce=[a-zA-Z0-9]+"
        );
        
        JScrollPane scrollPane = new JScrollPane(ignoreDynamicPatternsArea);
        scrollPane.setPreferredSize(new Dimension(400, 100));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建提示面板
     */
    private JPanel createHintPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JLabel hint = new JLabel("💡 使用提示：响应体对比用于布尔盲注，跨Pair引用用于时间盲注、IDOR等场景");
        panel.add(hint, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 切换响应体对比字段的启用状态
     */
    private void toggleBodyComparisonFields() {
        boolean enabled = !bodyComparisonModeCombo.getSelectedItem().equals("NONE");
        bodyComparisonReferencePairIdSpinner.setEnabled(enabled);
        useCleanedBodyComparisonCheckBox.setEnabled(enabled);
        bodySaveModeCombo.setEnabled(enabled);
    }
    
    /**
     * 切换跨Pair引用字段的启用状态
     */
    private void toggleCrossPairReferenceFields() {
        boolean enabled = enableCrossPairReferenceCheckBox.isSelected();
        referencePairIdSpinner.setEnabled(enabled);
        referenceFeatureTypeCombo.setEnabled(enabled);
        referenceOperatorCombo.setEnabled(enabled);
        referenceValueField.setEnabled(enabled);
    }
    
    /**
     * 从配置加载到UI组件
     */
    private void loadFromConfig() {
        // 响应体对比
        if (config.getBodyComparisonMode() != null) {
            bodyComparisonModeCombo.setSelectedItem(config.getBodyComparisonMode().name());
        }
        if (config.getBodyComparisonReferencePairId() != null) {
            bodyComparisonReferencePairIdSpinner.setValue(config.getBodyComparisonReferencePairId());
        }
        useCleanedBodyComparisonCheckBox.setSelected(config.isUseCleanedBodyComparison());
        if (config.getBodySaveMode() != null) {
            bodySaveModeCombo.setSelectedItem(config.getBodySaveMode());
        }
        
        // 跨Pair引用
        if (config.getReferencePairId() != null) {
            enableCrossPairReferenceCheckBox.setSelected(true);
            referencePairIdSpinner.setValue(config.getReferencePairId());
        }
        if (config.getReferenceFeatureType() != null) {
            referenceFeatureTypeCombo.setSelectedItem(config.getReferenceFeatureType());
        }
        if (config.getReferenceOperator() != null) {
            referenceOperatorCombo.setSelectedItem(config.getReferenceOperator());
        }
        if (config.getReferenceValue() != null) {
            referenceValueField.setText(config.getReferenceValue());
        }
        
        // 基础对比
        compareStatusCodeCheckBox.setSelected(config.isCompareStatusCode());
        compareLengthCheckBox.setSelected(config.isCompareLength());
        lengthDifferenceThresholdSpinner.setValue(config.getLengthDifferenceThreshold());
        compareTimeCheckBox.setSelected(config.isCompareTime());
        timeDifferenceThresholdSpinner.setValue((int)config.getTimeDifferenceThreshold());
        
        // 动态内容忽略
        if (config.getIgnoreDynamicPatterns() != null && !config.getIgnoreDynamicPatterns().isEmpty()) {
            ignoreDynamicPatternsArea.setText(String.join("\n", config.getIgnoreDynamicPatterns()));
        }
        
        // 初始化字段状态
        toggleBodyComparisonFields();
        toggleCrossPairReferenceFields();
    }
    
    /**
     * 从UI组件保存到配置
     */
    public ResponseComparisonConfig getConfig() {
        ResponseComparisonConfig config = new ResponseComparisonConfig();
        
        // 响应体对比
        String selectedMode = (String) bodyComparisonModeCombo.getSelectedItem();
        if (selectedMode != null) {
            try {
                config.setBodyComparisonMode(ResponseComparisonConfig.BodyComparisonMode.valueOf(selectedMode));
            } catch (Exception e) {
                config.setBodyComparisonMode(ResponseComparisonConfig.BodyComparisonMode.NONE);
            }
        }
        config.setBodyComparisonReferencePairId((Integer) bodyComparisonReferencePairIdSpinner.getValue());
        config.setUseCleanedBodyComparison(useCleanedBodyComparisonCheckBox.isSelected());
        config.setBodySaveMode((String) bodySaveModeCombo.getSelectedItem());
        
        // 跨Pair引用
        if (enableCrossPairReferenceCheckBox.isSelected()) {
            config.setReferencePairId((Integer) referencePairIdSpinner.getValue());
            config.setReferenceFeatureType((String) referenceFeatureTypeCombo.getSelectedItem());
            config.setReferenceOperator((String) referenceOperatorCombo.getSelectedItem());
            String refValue = referenceValueField.getText().trim();
            if (!refValue.isEmpty()) {
                config.setReferenceValue(refValue);
            }
        }
        
        // 基础对比
        config.setCompareStatusCode(compareStatusCodeCheckBox.isSelected());
        config.setCompareLength(compareLengthCheckBox.isSelected());
        config.setLengthDifferenceThreshold((Integer) lengthDifferenceThresholdSpinner.getValue());
        config.setCompareTime(compareTimeCheckBox.isSelected());
        config.setTimeDifferenceThreshold((long)((Integer) timeDifferenceThresholdSpinner.getValue()));
        
        // 动态内容忽略
        String patternsText = ignoreDynamicPatternsArea.getText().trim();
        if (!patternsText.isEmpty()) {
            List<String> patterns = new ArrayList<>(Arrays.asList(patternsText.split("\n")));
            patterns.removeIf(String::isEmpty);
            config.setIgnoreDynamicPatterns(patterns);
        }
        
        return config;
    }
}


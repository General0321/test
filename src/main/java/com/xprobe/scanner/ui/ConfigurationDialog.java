package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置规则对话框
 */
public class ConfigurationDialog extends JDialog {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private Configuration originalConfig;
    private boolean configurationSaved = false;
    
    // 表单组件
    private JTextField nameField;
    private JComboBox<String> typeComboBox;
    private JCheckBox enabledCheckBox;
    private JTextArea descriptionArea;
    
    // 参数匹配配置
    private JTextArea parameterNamesArea;
    private JComboBox<String> parameterNameMatchTypeComboBox;
    private JTextArea parameterValuesArea;
    
    // 响应匹配规则配置
    private JTextArea responseExpressionArea;
    private JButton addConditionButton;
    private JButton testExpressionButton;
    private JLabel expressionHelpLabel;
    private List<ResponseCondition> availableConditions;
    
    // 按钮
    private JButton saveButton;
    private JButton cancelButton;
    private JButton testButton;
    
    public ConfigurationDialog(Component parent, String title, Configuration config, ConfigurationManager configManager) {
        super((Window) SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        this.api = null; // 暂时设为null，避免依赖问题
        this.configManager = configManager;
        this.originalConfig = config;
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadConfiguration();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 700);
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        // 表单组件
        nameField = new JTextField(20);
        typeComboBox = new JComboBox<>(new String[]{"LFI", "SQL", "SSRF"});
        enabledCheckBox = new JCheckBox("启用此规则");
        descriptionArea = new JTextArea(3, 30);
        
        // 参数匹配配置
        parameterNamesArea = new JTextArea(4, 30);
        parameterNamesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        parameterNameMatchTypeComboBox = new JComboBox<>(new String[]{"字符串匹配", "正则匹配"});
        parameterValuesArea = new JTextArea(6, 30);
        parameterValuesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        // 参数值替换统一使用占位符替换模式，支持占位符解析和直接值
        
        // 响应匹配规则配置
        responseExpressionArea = new JTextArea(6, 50);
        responseExpressionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseExpressionArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        addConditionButton = new JButton("+ 添加条件");
        testExpressionButton = new JButton("测试表达式");
        
        expressionHelpLabel = new JLabel();
        updateExpressionHelp();
        
        availableConditions = new ArrayList<>();
        initializeAvailableConditions();
        
        // 按钮
        saveButton = new JButton("保存");
        cancelButton = new JButton("取消");
        testButton = new JButton("测试规则");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部基本信息面板
        JPanel basicInfoPanel = createBasicInfoPanel();
        
        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("参数配置", createParameterPanel());
        tabbedPane.addTab("响应匹配", createResponsePanel());
        tabbedPane.addTab("规则说明", createDescriptionPanel());
        
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.add(basicInfoPanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(testButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createBasicInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("基本信息"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 规则名称
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("规则名称:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(nameField, gbc);
        
        // 规则类型
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("规则类型:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(typeComboBox, gbc);
        
        // 启用状态
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(enabledCheckBox, gbc);
        
        return panel;
    }
    
    private JPanel createParameterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 参数名配置
        JPanel paramNamePanel = new JPanel(new BorderLayout(5, 5));
        paramNamePanel.setBorder(BorderFactory.createTitledBorder("参数名匹配"));
        
        JPanel paramNameTopPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paramNameTopPanel.add(new JLabel("匹配方式:"));
        paramNameTopPanel.add(parameterNameMatchTypeComboBox);
        
        JScrollPane paramNameScrollPane = new JScrollPane(parameterNamesArea);
        paramNameScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        paramNamePanel.add(paramNameTopPanel, BorderLayout.NORTH);
        paramNamePanel.add(paramNameScrollPane, BorderLayout.CENTER);
        
        // 参数值配置
        JPanel paramValuePanel = new JPanel(new BorderLayout(5, 5));
        paramValuePanel.setBorder(BorderFactory.createTitledBorder("参数值替换（支持占位符：{value}、{dnslog}等）"));
        
        JScrollPane paramValueScrollPane = new JScrollPane(parameterValuesArea);
        paramValueScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        paramValuePanel.add(paramValueScrollPane, BorderLayout.CENTER);
        
        // 分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(paramNamePanel);
        splitPane.setBottomComponent(paramValuePanel);
        splitPane.setDividerLocation(200);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 表达式编辑器
        JPanel expressionPanel = new JPanel(new BorderLayout(5, 5));
        expressionPanel.setBorder(BorderFactory.createTitledBorder("响应匹配表达式"));
        
        // 表达式输入区域
        JScrollPane expressionScrollPane = new JScrollPane(responseExpressionArea);
        expressionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        expressionScrollPane.setPreferredSize(new Dimension(600, 150));
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        buttonPanel.add(addConditionButton);
        buttonPanel.add(testExpressionButton);
        
        // 帮助文本
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.add(expressionHelpLabel, BorderLayout.CENTER);
        helpPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        expressionPanel.add(expressionScrollPane, BorderLayout.CENTER);
        expressionPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        panel.add(expressionPanel, BorderLayout.CENTER);
        panel.add(helpPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // 规则说明
        JPanel descPanel = new JPanel(new BorderLayout(5, 5));
        descPanel.setBorder(BorderFactory.createTitledBorder("规则说明"));
        JScrollPane descScrollPane = new JScrollPane(descriptionArea);
        descScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descPanel.add(descScrollPane, BorderLayout.CENTER);
        
        // 帮助文本
        JPanel helpPanel = new JPanel(new BorderLayout(5, 5));
        helpPanel.setBorder(BorderFactory.createTitledBorder("使用说明"));
        JTextArea helpText = new JTextArea(8, 40);
        helpText.setText("参数配置说明:\n" +
            "• 参数名匹配: 每行一个参数名，支持字符串或正则表达式\n" +
            "• 参数值替换: 每行一个替换值，支持占位符替换\n" +
            "• 占位符支持: {value}替换为原始值, {dnslog}替换为DNS日志\n\n" +
            "响应匹配规则说明:\n" +
            "• 点击'添加规则'按钮添加响应匹配规则\n" +
            "• 规则类型: 响应码、响应时间、响应头、响应体\n" +
            "• 匹配方式: 字符串匹配、正则匹配\n" +
            "• 逻辑关系: AND(与)、OR(或)、NOT(非)\n" +
            "• 至少需要添加一个响应匹配规则\n" +
            "• 可以添加多个规则进行组合匹配\n\n" +
            "示例:\n" +
            "• 只匹配响应码200: 添加规则 → 响应码 → 200 → AND\n" +
            "• 响应码200+响应体包含root: 添加两个规则\n" +
            "• 响应体包含root但不包含error: 添加两个响应体规则");
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        JScrollPane helpScrollPane = new JScrollPane(helpText);
        helpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        helpPanel.add(helpScrollPane, BorderLayout.CENTER);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(descPanel);
        splitPane.setBottomComponent(helpPanel);
        splitPane.setDividerLocation(150);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void setupEventListeners() {
        saveButton.addActionListener(e -> saveConfiguration());
        cancelButton.addActionListener(e -> dispose());
        testButton.addActionListener(e -> testConfiguration());
        
        // 响应匹配规则按钮事件
        addConditionButton.addActionListener(e -> {
            System.out.println("添加条件按钮被点击");
            addResponseCondition();
        });
        testExpressionButton.addActionListener(e -> {
            System.out.println("测试表达式按钮被点击");
            testResponseExpression();
        });
        
        // 参数值替换统一使用占位符替换模式，无需自动识别
    }
    
    private void loadConfiguration() {
        if (originalConfig != null) {
            // 编辑模式
            String ruleName = originalConfig.getCustomLabel();
            if (ruleName == null || ruleName.trim().isEmpty()) {
                ruleName = "规则 " + (configManager.getAllConfigurations().indexOf(originalConfig) + 1);
            }
            nameField.setText(ruleName);
            typeComboBox.setSelectedItem(originalConfig.getParameterNameType());
            enabledCheckBox.setSelected(originalConfig.isEnabled());
            
            // 加载参数名匹配配置
            StringBuilder paramNames = new StringBuilder();
            if (originalConfig.getParameterNames() != null) {
                for (String paramName : originalConfig.getParameterNames()) {
                    paramNames.append(paramName).append("\n");
                }
            }
            parameterNamesArea.setText(paramNames.toString());
            parameterNameMatchTypeComboBox.setSelectedIndex(0); // 默认字符串匹配
            
            // 加载参数值匹配配置
            StringBuilder params = new StringBuilder();
            for (String param : originalConfig.getParameterValues()) {
                params.append(param).append("\n");
            }
            parameterValuesArea.setText(params.toString());
            // 参数值替换统一使用占位符替换模式
            
            // 加载响应匹配规则
            loadResponseRules(originalConfig);
            
            // 加载说明
            descriptionArea.setText(originalConfig.getCustomLabel() != null ? originalConfig.getCustomLabel() : "");
        } else {
            // 新建模式
            nameField.setText("");
            typeComboBox.setSelectedIndex(0);
            enabledCheckBox.setSelected(true);
            parameterNamesArea.setText("");
            parameterNameMatchTypeComboBox.setSelectedIndex(0);
            parameterValuesArea.setText("");
            // 清空响应匹配表达式
            responseExpressionArea.setText("");
            descriptionArea.setText("");
        }
        
    }
    
    private void saveConfiguration() {
        // 验证输入
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入规则名称", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证参数名匹配
        String parameterNamesText = parameterNamesArea.getText().trim();
        if (parameterNamesText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入参数名匹配规则", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 解析参数名
        List<String> parameterNames = new ArrayList<>();
        String[] nameLines = parameterNamesText.split("\n");
        for (String line : nameLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                parameterNames.add(trimmed);
            }
        }
        
        if (parameterNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入有效的参数名匹配规则", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证参数值替换
        String parametersText = parameterValuesArea.getText().trim();
        if (parametersText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入参数值替换规则", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 解析参数值（支持占位符）
        List<String> parameters = new ArrayList<>();
        String[] lines = parametersText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                parameters.add(trimmed);
            }
        }
        
        if (parameters.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入有效的参数值替换规则", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证响应匹配规则（至少需要一个）
        String expression = responseExpressionArea.getText().trim();
        if (expression.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入响应匹配表达式", "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证表达式语法
        try {
            validateExpression(expression);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "响应匹配表达式语法错误：\n" + e.getMessage(), "验证错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            // 创建配置
            String ruleType = (String) typeComboBox.getSelectedItem();
            String ruleName = nameField.getText().trim();
            String description = descriptionArea.getText().trim();
            boolean enabled = enabledCheckBox.isSelected();
            
            // 参数名列表已在上面解析
            
            // 创建响应匹配规则
            List<Configuration.MatchRule> matchRules = createResponseMatchRules();
            
            // 创建新的配置对象
            Configuration newConfig = new Configuration(
                parameterNames, // parameterNames
                ruleType, // parameterNameType
                parameters, // parameterValues
                matchRules, // matchRules
                ruleName, // customLabel - 使用用户输入的规则名称
                enabled
            );
            
            if (originalConfig != null) {
                // 更新现有配置
                int index = configManager.getAllConfigurations().indexOf(originalConfig);
                if (index >= 0) {
                    configManager.updateConfiguration(index, newConfig);
                }
            } else {
                // 添加新配置
                configManager.addConfiguration(newConfig);
            }
            
            configurationSaved = true;
            JOptionPane.showMessageDialog(this, "规则保存成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void testConfiguration() {
        String parameterNamesText = parameterNamesArea.getText().trim();
        String parametersText = parameterValuesArea.getText().trim();
        
        if (parameterNamesText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先输入参数名匹配规则", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (parametersText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先输入参数值替换规则", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 解析参数名
        List<String> parameterNames = new ArrayList<>();
        String[] nameLines = parameterNamesText.split("\n");
        for (String line : nameLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                parameterNames.add(trimmed);
            }
        }
        
        // 解析参数值
        List<String> parameters = new ArrayList<>();
        String[] lines = parametersText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                parameters.add(trimmed);
            }
        }
        
        String ruleType = (String) typeComboBox.getSelectedItem();
        String paramNameMatchType = (String) parameterNameMatchTypeComboBox.getSelectedItem();
        String paramValueMatchType = "占位符替换"; // 统一使用占位符替换模式
        
        StringBuilder result = new StringBuilder();
        result.append("规则测试结果:\n\n");
        result.append("规则类型: ").append(ruleType).append("\n");
        result.append("参数名匹配方式: ").append(paramNameMatchType).append("\n");
        result.append("参数名数量: ").append(parameterNames.size()).append("\n");
        result.append("参数值替换方式: ").append(paramValueMatchType).append("\n");
        result.append("参数值数量: ").append(parameters.size()).append("\n\n");
        
        result.append("参数名列表:\n");
        for (int i = 0; i < parameterNames.size(); i++) {
            result.append("  ").append(i + 1).append(". ").append(parameterNames.get(i)).append("\n");
        }
        
        result.append("\n参数值替换列表:\n");
        for (int i = 0; i < parameters.size(); i++) {
            result.append("  ").append(i + 1).append(". ").append(parameters.get(i)).append("\n");
        }
        
        // 添加响应匹配规则测试
        String expression = responseExpressionArea.getText().trim();
        if (!expression.isEmpty()) {
            result.append("\n响应匹配表达式:\n");
            result.append("  ").append(expression).append("\n");
        } else {
            result.append("\n响应匹配规则: 未配置\n");
        }
        
        JOptionPane.showMessageDialog(this, result.toString(), "测试结果", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private List<Configuration.MatchRule> createResponseMatchRules() {
        List<Configuration.MatchRule> matchRules = new ArrayList<>();
        
        String expression = responseExpressionArea.getText().trim();
        if (expression.isEmpty()) {
            return matchRules;
        }
        
        // 解析表达式为规则
        try {
            List<ExpressionCondition> conditions = parseExpression(expression);
            for (ExpressionCondition condition : conditions) {
                Configuration.MatchRule rule = convertExpressionToRule(condition);
                if (rule != null) {
                    matchRules.add(rule);
                }
            }
        } catch (Exception e) {
            // 如果解析失败，创建一个简单的规则
            JOptionPane.showMessageDialog(this, 
                "表达式解析失败，将使用简单规则：\n" + e.getMessage(), 
                "警告", 
                JOptionPane.WARNING_MESSAGE);
        }
        
        return matchRules;
    }
    
    private static class ExpressionCondition {
        String type;
        String operator;
        String value;
        String extra;
        
        ExpressionCondition(String type, String operator, String value, String extra) {
            this.type = type;
            this.operator = operator;
            this.value = value;
            this.extra = extra;
        }
    }
    
    private List<ExpressionCondition> parseExpression(String expression) throws Exception {
        List<ExpressionCondition> conditions = new ArrayList<>();
        
        // 简单的表达式解析（这里可以扩展为更复杂的解析器）
        String[] parts = expression.split("(&&|\\|\\||!)");
        String[] operators = expression.split("(status_code|response_time|header_contains|header_regex|body_contains|body_regex)");
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // 解析条件
            if (part.contains("status_code")) {
                String value = extractValue(part, "status_code");
                conditions.add(new ExpressionCondition("status_code", "==", value, null));
            } else if (part.contains("response_time")) {
                String value = extractValue(part, "response_time");
                String op = extractOperator(part);
                conditions.add(new ExpressionCondition("response_time", op, value, null));
            } else if (part.contains("header_contains")) {
                String value = extractFunctionValue(part, "header_contains");
                conditions.add(new ExpressionCondition("header_contains", "contains", value, null));
            } else if (part.contains("header_regex")) {
                String value = extractFunctionValue(part, "header_regex");
                conditions.add(new ExpressionCondition("header_regex", "regex", value, null));
            } else if (part.contains("body_contains")) {
                String value = extractFunctionValue(part, "body_contains");
                conditions.add(new ExpressionCondition("body_contains", "contains", value, null));
            } else if (part.contains("body_regex")) {
                String value = extractFunctionValue(part, "body_regex");
                conditions.add(new ExpressionCondition("body_regex", "regex", value, null));
            }
        }
        
        return conditions;
    }
    
    private String extractValue(String part, String function) {
        // 提取 == 200 中的 200
        String pattern = function + "\\s*[=<>!]+\\s*(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(part);
        if (m.find()) {
            return m.group(1);
        }
        return "0";
    }
    
    private String extractOperator(String part) {
        if (part.contains(">=")) return ">=";
        if (part.contains("<=")) return "<=";
        if (part.contains(">")) return ">";
        if (part.contains("<")) return "<";
        if (part.contains("!=")) return "!=";
        if (part.contains("==")) return "==";
        return "==";
    }
    
    private String extractFunctionValue(String part, String function) {
        // 提取 body_contains('root') 中的 'root'
        String pattern = function + "\\('([^']+)'\\)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(part);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
    
    private Configuration.MatchRule convertExpressionToRule(ExpressionCondition condition) {
        String location;
        String matchType;
        String rule;
        
        switch (condition.type) {
            case "status_code":
                location = "HTTP-Status_Code";
                matchType = "String Match";
                rule = condition.value;
                break;
            case "response_time":
                location = "HTTP-Response_Time";
                matchType = "String Match";
                rule = condition.value;
                break;
            case "header_contains":
                location = "HTTP-Response_Headers";
                matchType = "String Match";
                rule = condition.value;
                break;
            case "header_regex":
                location = "HTTP-Response_Headers";
                matchType = "Regex Match";
                rule = condition.value;
                break;
            case "body_contains":
                location = "Body";
                matchType = "String Match";
                rule = condition.value;
                break;
            case "body_regex":
                location = "Body";
                matchType = "Regex Match";
                rule = condition.value;
                break;
            default:
                return null;
        }
        
        return new Configuration.MatchRule(location, rule, "AND", matchType);
    }
    
    private void loadResponseRules(Configuration config) {
        // 清空表达式
        responseExpressionArea.setText("");
        
        if (config.getMatchRules() != null && !config.getMatchRules().isEmpty()) {
            // 将匹配规则转换为表达式
            StringBuilder expression = new StringBuilder();
            for (int i = 0; i < config.getMatchRules().size(); i++) {
                Configuration.MatchRule rule = config.getMatchRules().get(i);
                String condition = convertRuleToExpression(rule);
                
                if (i > 0) {
                    // 根据逻辑关系添加连接符
                    String operator = rule.getOperator();
                    if ("AND".equals(operator)) {
                        expression.append(" && ");
                    } else if ("OR".equals(operator)) {
                        expression.append(" || ");
                    } else if ("NOT".equals(operator)) {
                        expression.append(" !");
                    } else {
                        expression.append(" && "); // 默认AND
                    }
                }
                expression.append(condition);
            }
            responseExpressionArea.setText(expression.toString());
        }
    }
    
    private String convertRuleToExpression(Configuration.MatchRule rule) {
        String location = rule.getLocation();
        String ruleContent = rule.getRule();
        String matchType = rule.getMatchType();
        
        switch (location) {
            case "HTTP-Status_Code":
                return "status_code == " + ruleContent;
            case "HTTP-Response_Time":
                return "response_time > " + ruleContent;
            case "HTTP-Response_Headers":
                if ("Regex Match".equals(matchType)) {
                    return "header_regex('" + ruleContent + "')";
                } else {
                    return "header_contains('" + ruleContent + "')";
                }
            case "Body":
                if ("Regex Match".equals(matchType)) {
                    return "body_regex('" + ruleContent + "')";
                } else {
                    return "body_contains('" + ruleContent + "')";
                }
            default:
                return "body_contains('" + ruleContent + "')";
        }
    }
    
    
    private String getRuleTypeDisplayName(String location) {
        switch (location) {
            case "HTTP-Status_Code": return "响应码";
            case "HTTP-Response_Time": return "响应时间";
            case "HTTP-Response_Headers": return "响应头";
            case "Body": return "响应体";
            default: return location;
        }
    }
    
    private String getRuleTypeLocation(String displayName) {
        switch (displayName) {
            case "响应码": return "HTTP-Status_Code";
            case "响应时间": return "HTTP-Response_Time";
            case "响应头": return "HTTP-Response_Headers";
            case "响应体": return "Body";
            default: return displayName;
        }
    }
    
    private void initializeAvailableConditions() {
        availableConditions.add(new ResponseCondition(
            "status_code", "数值", "HTTP状态码", "status_code == 200"
        ));
        availableConditions.add(new ResponseCondition(
            "response_time", "数值", "响应时间(毫秒)", "response_time > 1000"
        ));
        availableConditions.add(new ResponseCondition(
            "header_contains", "字符串", "响应头包含", "header_contains('Server', 'Apache')"
        ));
        availableConditions.add(new ResponseCondition(
            "body_contains", "字符串", "响应体包含", "body_contains('root')"
        ));
        availableConditions.add(new ResponseCondition(
            "body_regex", "正则", "响应体正则匹配", "body_regex('\\d{4}-\\d{2}-\\d{2}')"
        ));
        availableConditions.add(new ResponseCondition(
            "header_regex", "正则", "响应头正则匹配", "header_regex('Content-Type', 'text/html.*')"
        ));
    }
    
    private void updateExpressionHelp() {
        String helpText = "<html><body style='font-size:11px; color:#666;'>" +
            "<b>语法：</b>status_code == 200, body_contains('root')<br/>" +
            "<b>逻辑：</b>&&(AND), ||(OR), !(NOT) | " +
            "<b>括号：</b>()分组<br/>" +
            "<b>示例：</b>(status_code == 200) && body_contains('root')<br/>" +
            "<b>提示：</b>点击'+ 添加条件'查看所有条件</body></html>";
        expressionHelpLabel.setText(helpText);
    }
    
    private void addResponseCondition() {
        System.out.println("addResponseCondition 方法被调用");
        JDialog conditionDialog = new JDialog(this, "添加响应条件", true);
        conditionDialog.setSize(500, 400);
        conditionDialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 条件列表
        JList<ResponseCondition> conditionList = new JList<>(availableConditions.toArray(new ResponseCondition[0]));
        conditionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conditionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ResponseCondition) {
                    ResponseCondition condition = (ResponseCondition) value;
                    setText("<html><b>" + condition.name + "</b> (" + condition.type + ")<br/>" +
                           "<small>" + condition.description + "</small><br/>" +
                           "<code>" + condition.example + "</code></html>");
                }
                return this;
            }
        });
        
        JScrollPane listScrollPane = new JScrollPane(conditionList);
        listScrollPane.setPreferredSize(new Dimension(450, 200));
        
        // 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton insertButton = new JButton("插入到表达式");
        JButton cancelButton = new JButton("取消");
        
        buttonPanel.add(insertButton);
        buttonPanel.add(cancelButton);
        
        panel.add(new JLabel("选择要添加的条件："), BorderLayout.NORTH);
        panel.add(listScrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 事件处理
        insertButton.addActionListener(e -> {
            ResponseCondition selected = conditionList.getSelectedValue();
            if (selected != null) {
                insertConditionToExpression(selected.example);
                conditionDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(conditionDialog, "请选择一个条件", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> conditionDialog.dispose());
        
        conditionDialog.add(panel);
        conditionDialog.setVisible(true);
    }
    
    private void insertConditionToExpression(String condition) {
        String currentText = responseExpressionArea.getText();
        if (currentText.trim().isEmpty()) {
            responseExpressionArea.setText(condition);
        } else {
            responseExpressionArea.setText(currentText + " && " + condition);
        }
        responseExpressionArea.requestFocus();
    }
    
    private void testResponseExpression() {
        String expression = responseExpressionArea.getText().trim();
        if (expression.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入响应匹配表达式", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 简单的表达式验证
        try {
            validateExpression(expression);
            JOptionPane.showMessageDialog(this, 
                "表达式语法正确！\n\n" + expression, 
                "测试结果", 
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "表达式语法错误：\n" + e.getMessage() + "\n\n" + expression, 
                "测试结果", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void validateExpression(String expression) throws Exception {
        // 简单的括号匹配检查
        int parenCount = 0;
        for (char c : expression.toCharArray()) {
            if (c == '(') parenCount++;
            else if (c == ')') parenCount--;
            if (parenCount < 0) {
                throw new Exception("括号不匹配");
            }
        }
        if (parenCount != 0) {
            throw new Exception("括号不匹配");
        }
        
        // 检查是否包含有效的条件
        boolean hasValidCondition = false;
        for (ResponseCondition condition : availableConditions) {
            if (expression.contains(condition.name)) {
                hasValidCondition = true;
                break;
            }
        }
        
        if (!hasValidCondition) {
            throw new Exception("表达式中没有有效的条件，请使用'+ 添加条件'按钮添加条件");
        }
    }
    
    
    // 响应条件类
    private static class ResponseCondition {
        String name;
        String type;
        String description;
        String example;
        
        ResponseCondition(String name, String type, String description, String example) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.example = example;
        }
        
        @Override
        public String toString() {
            return name + " (" + type + ")";
        }
    }
    
    
    public boolean isConfigurationSaved() {
        return configurationSaved;
    }
}
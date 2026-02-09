package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.Configuration;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 规则过滤器配置面板
 * 用于配置规则的流量过滤器，减少无用流量，提升扫描性能
 */
public class RuleFilterPanel extends JPanel {
    private final MontoyaApi api;
    private Configuration.RuleFilter filter;
    
    // 主控制组件
    private JCheckBox enabledCheckBox;
    private ButtonGroup modeButtonGroup;
    private JRadioButton blacklistRadio;
    private JRadioButton whitelistRadio;
    
    // 请求过滤组件
    private JCheckBox filterRequestContentTypeCheckBox;
    private DefaultListModel<String> requestContentTypeListModel;
    private JList<String> requestContentTypeList;
    private JTextField requestContentTypeField;
    
    private JCheckBox filterRequestMethodCheckBox;
    private JCheckBox getCheckBox, postCheckBox, putCheckBox, deleteCheckBox;
    private JCheckBox patchCheckBox, optionsCheckBox, headCheckBox;
    
    // 响应过滤组件
    private JCheckBox filterResponseContentTypeCheckBox;
    private DefaultListModel<String> responseContentTypeListModel;
    private JList<String> responseContentTypeList;
    private JTextField responseContentTypeField;
    
    private JCheckBox filterResponseStatusCodeCheckBox;
    private ButtonGroup statusCodeModeGroup;
    private JRadioButton statusCodeListRadio;
    private JRadioButton statusCodeRangeRadio;
    private DefaultListModel<Integer> statusCodeListModel;
    private JList<Integer> statusCodeList;
    private JTextField statusCodeField;
    private JSpinner statusCodeMinSpinner;
    private JSpinner statusCodeMaxSpinner;
    
    // 文件后缀名过滤组件
    private JCheckBox filterFileExtensionCheckBox;
    private DefaultListModel<String> fileExtensionListModel;
    private JList<String> fileExtensionList;
    private JTextField fileExtensionField;
    
    // 常用Content-Type选项
    private static final String[] COMMON_CONTENT_TYPES = {
        "application/json",
        "application/xml",
        "text/html",
        "text/plain",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
        "application/javascript",
        "text/css"
    };
    
    public RuleFilterPanel(MontoyaApi api) {
        this.api = api;
        this.filter = new Configuration.RuleFilter();
        initializeComponents();
        setupLayout();
        setupEventListeners();
    }
    
    private void initializeComponents() {
        // 启用复选框
        enabledCheckBox = new JCheckBox("启用过滤器", false);
        enabledCheckBox.setFont(enabledCheckBox.getFont().deriveFont(Font.BOLD));
        
        // 过滤模式
        modeButtonGroup = new ButtonGroup();
        blacklistRadio = new JRadioButton("排除模式（黑名单）", true);
        blacklistRadio.setToolTipText("排除列表中的项，检测其他所有流量");
        whitelistRadio = new JRadioButton("只检测模式（白名单）", false);
        whitelistRadio.setToolTipText("只检测列表中的项，排除其他所有流量");
        modeButtonGroup.add(blacklistRadio);
        modeButtonGroup.add(whitelistRadio);
        
        // 请求Content-Type
        filterRequestContentTypeCheckBox = new JCheckBox("请求Content-Type", false);
        requestContentTypeListModel = new DefaultListModel<>();
        requestContentTypeList = new JList<>(requestContentTypeListModel);
        requestContentTypeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        requestContentTypeField = new JTextField(30);
        requestContentTypeField.setToolTipText("输入Content-Type，如: application/json");
        
        // 请求方法
        filterRequestMethodCheckBox = new JCheckBox("请求方法", false);
        getCheckBox = new JCheckBox("GET");
        postCheckBox = new JCheckBox("POST");
        putCheckBox = new JCheckBox("PUT");
        deleteCheckBox = new JCheckBox("DELETE");
        patchCheckBox = new JCheckBox("PATCH");
        optionsCheckBox = new JCheckBox("OPTIONS");
        headCheckBox = new JCheckBox("HEAD");
        
        // 响应Content-Type
        filterResponseContentTypeCheckBox = new JCheckBox("响应Content-Type", false);
        responseContentTypeListModel = new DefaultListModel<>();
        responseContentTypeList = new JList<>(responseContentTypeListModel);
        responseContentTypeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        responseContentTypeField = new JTextField(30);
        responseContentTypeField.setToolTipText("输入Content-Type，如: text/html");
        
        // 响应状态码
        filterResponseStatusCodeCheckBox = new JCheckBox("响应状态码", false);
        statusCodeModeGroup = new ButtonGroup();
        statusCodeListRadio = new JRadioButton("指定状态码", true);
        statusCodeRangeRadio = new JRadioButton("状态码范围", false);
        statusCodeModeGroup.add(statusCodeListRadio);
        statusCodeModeGroup.add(statusCodeRangeRadio);
        statusCodeListModel = new DefaultListModel<>();
        statusCodeList = new JList<>(statusCodeListModel);
        statusCodeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        statusCodeField = new JTextField(30);
        statusCodeField.setToolTipText("输入状态码，用逗号分隔，如: 200, 404, 500");
        statusCodeMinSpinner = new JSpinner(new SpinnerNumberModel(200, 100, 599, 1));
        statusCodeMaxSpinner = new JSpinner(new SpinnerNumberModel(299, 100, 599, 1));
        
        // 文件后缀名
        filterFileExtensionCheckBox = new JCheckBox("文件后缀名", false);
        fileExtensionListModel = new DefaultListModel<>();
        fileExtensionList = new JList<>(fileExtensionListModel);
        fileExtensionList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileExtensionField = new JTextField(30);
        fileExtensionField.setToolTipText("输入文件后缀名，用逗号分隔，如: js, css, png");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        // 1. 启用和模式选择
        JPanel enablePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enablePanel.add(enabledCheckBox);
        mainPanel.add(enablePanel);
        
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(new TitledBorder("过滤模式"));
        modePanel.add(blacklistRadio);
        modePanel.add(Box.createHorizontalStrut(20));
        modePanel.add(whitelistRadio);
        mainPanel.add(modePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 2. 请求过滤
        JPanel requestPanel = createRequestFilterPanel();
        mainPanel.add(requestPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 3. 响应过滤
        JPanel responsePanel = createResponseFilterPanel();
        mainPanel.add(responsePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 4. 文件后缀名过滤
        JPanel extensionPanel = createFileExtensionPanel();
        mainPanel.add(extensionPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 5. 提示信息
        JPanel hintPanel = createHintPanel();
        mainPanel.add(hintPanel);
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private JPanel createRequestFilterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("请求过滤"));
        
        // 请求Content-Type
        JPanel contentTypePanel = new JPanel(new BorderLayout(5, 5));
        JPanel contentTypeHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contentTypeHeader.add(filterRequestContentTypeCheckBox);
        contentTypePanel.add(contentTypeHeader, BorderLayout.NORTH);
        
        JPanel contentTypeContent = new JPanel(new BorderLayout(5, 5));
        JPanel contentTypeInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contentTypeInput.add(new JLabel("Content-Type:"));
        contentTypeInput.add(requestContentTypeField);
        JButton addContentTypeBtn = new JButton("添加");
        addContentTypeBtn.addActionListener(e -> addRequestContentType());
        contentTypeInput.add(addContentTypeBtn);
        JButton removeContentTypeBtn = new JButton("删除选中");
        removeContentTypeBtn.addActionListener(e -> removeSelectedRequestContentTypes());
        contentTypeInput.add(removeContentTypeBtn);
        contentTypeContent.add(contentTypeInput, BorderLayout.NORTH);
        
        JScrollPane contentTypeScroll = new JScrollPane(requestContentTypeList);
        contentTypeScroll.setPreferredSize(new Dimension(400, 100));
        contentTypeContent.add(contentTypeScroll, BorderLayout.CENTER);
        contentTypePanel.add(contentTypeContent, BorderLayout.CENTER);
        panel.add(contentTypePanel);
        
        // 请求方法
        JPanel methodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        methodPanel.add(filterRequestMethodCheckBox);
        methodPanel.add(Box.createHorizontalStrut(10));
        methodPanel.add(getCheckBox);
        methodPanel.add(postCheckBox);
        methodPanel.add(putCheckBox);
        methodPanel.add(deleteCheckBox);
        methodPanel.add(patchCheckBox);
        methodPanel.add(optionsCheckBox);
        methodPanel.add(headCheckBox);
        panel.add(methodPanel);
        
        return panel;
    }
    
    private JPanel createResponseFilterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("响应过滤"));
        
        // 响应Content-Type
        JPanel contentTypePanel = new JPanel(new BorderLayout(5, 5));
        JPanel contentTypeHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contentTypeHeader.add(filterResponseContentTypeCheckBox);
        contentTypePanel.add(contentTypeHeader, BorderLayout.NORTH);
        
        JPanel contentTypeContent = new JPanel(new BorderLayout(5, 5));
        JPanel contentTypeInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contentTypeInput.add(new JLabel("Content-Type:"));
        contentTypeInput.add(responseContentTypeField);
        JButton addContentTypeBtn = new JButton("添加");
        addContentTypeBtn.addActionListener(e -> addResponseContentType());
        contentTypeInput.add(addContentTypeBtn);
        JButton removeContentTypeBtn = new JButton("删除选中");
        removeContentTypeBtn.addActionListener(e -> removeSelectedResponseContentTypes());
        contentTypeInput.add(removeContentTypeBtn);
        contentTypeContent.add(contentTypeInput, BorderLayout.NORTH);
        
        JScrollPane contentTypeScroll = new JScrollPane(responseContentTypeList);
        contentTypeScroll.setPreferredSize(new Dimension(400, 100));
        contentTypeContent.add(contentTypeScroll, BorderLayout.CENTER);
        contentTypePanel.add(contentTypeContent, BorderLayout.CENTER);
        panel.add(contentTypePanel);
        
        // 响应状态码
        JPanel statusCodePanel = new JPanel();
        statusCodePanel.setLayout(new BoxLayout(statusCodePanel, BoxLayout.Y_AXIS));
        JPanel statusCodeHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusCodeHeader.add(filterResponseStatusCodeCheckBox);
        statusCodePanel.add(statusCodeHeader);
        
        JPanel statusCodeMode = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusCodeMode.add(statusCodeListRadio);
        statusCodeMode.add(statusCodeRangeRadio);
        statusCodePanel.add(statusCodeMode);
        
        // 指定状态码模式
        JPanel statusCodeListPanel = new JPanel(new BorderLayout(5, 5));
        JPanel statusCodeInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusCodeInput.add(new JLabel("状态码:"));
        statusCodeInput.add(statusCodeField);
        JButton addStatusCodeBtn = new JButton("添加");
        addStatusCodeBtn.addActionListener(e -> addStatusCode());
        statusCodeInput.add(addStatusCodeBtn);
        JButton removeStatusCodeBtn = new JButton("删除选中");
        removeStatusCodeBtn.addActionListener(e -> removeSelectedStatusCodes());
        statusCodeInput.add(removeStatusCodeBtn);
        statusCodeListPanel.add(statusCodeInput, BorderLayout.NORTH);
        
        JScrollPane statusCodeScroll = new JScrollPane(statusCodeList);
        statusCodeScroll.setPreferredSize(new Dimension(400, 100));
        statusCodeListPanel.add(statusCodeScroll, BorderLayout.CENTER);
        statusCodePanel.add(statusCodeListPanel);
        
        // 状态码范围模式
        JPanel statusCodeRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusCodeRangePanel.add(new JLabel("从"));
        statusCodeRangePanel.add(statusCodeMinSpinner);
        statusCodeRangePanel.add(new JLabel("到"));
        statusCodeRangePanel.add(statusCodeMaxSpinner);
        statusCodePanel.add(statusCodeRangePanel);
        
        panel.add(statusCodePanel);
        
        return panel;
    }
    
    private JPanel createFileExtensionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("文件后缀名过滤"));
        
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(filterFileExtensionCheckBox);
        panel.add(header);
        
        JPanel content = new JPanel(new BorderLayout(5, 5));
        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
        input.add(new JLabel("文件后缀名:"));
        input.add(fileExtensionField);
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(e -> addFileExtension());
        input.add(addBtn);
        JButton removeBtn = new JButton("删除选中");
        removeBtn.addActionListener(e -> removeSelectedFileExtensions());
        input.add(removeBtn);
        content.add(input, BorderLayout.NORTH);
        
        JScrollPane scroll = new JScrollPane(fileExtensionList);
        scroll.setPreferredSize(new Dimension(400, 100));
        content.add(scroll, BorderLayout.CENTER);
        panel.add(content);
        
        return panel;
    }
    
    private JPanel createHintPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("💡 提示"));
        
        JTextArea hintText = new JTextArea();
        hintText.setEditable(false);
        hintText.setLineWrap(true);
        hintText.setWrapStyleWord(true);
        hintText.setBackground(panel.getBackground());
        hintText.setFont(hintText.getFont().deriveFont(Font.PLAIN, 11f));
        hintText.setText(
            "• 排除模式（黑名单）：排除列表中的项，检测其他所有流量\n" +
            "  示例：排除 application/json，则只检测非JSON请求\n\n" +
            "• 只检测模式（白名单）：只检测列表中的项，排除其他所有流量\n" +
            "  示例：只检测 text/html，则只检测HTML响应\n\n" +
            "• 未启用的过滤条件不参与判断\n" +
            "• Content-Type支持部分匹配（如 application/json 匹配 application/json; charset=utf-8）\n" +
            "• 文件后缀名自动去除点号（.js → js）"
        );
        
        panel.add(hintText);
        return panel;
    }
    
    private void setupEventListeners() {
        // 启用/禁用所有组件
        enabledCheckBox.addActionListener(e -> updateComponentsEnabled());
        
        // 各个过滤条件的启用/禁用
        filterRequestContentTypeCheckBox.addActionListener(e -> updateRequestComponentsEnabled());
        filterRequestMethodCheckBox.addActionListener(e -> updateRequestComponentsEnabled());
        filterResponseContentTypeCheckBox.addActionListener(e -> updateResponseComponentsEnabled());
        filterResponseStatusCodeCheckBox.addActionListener(e -> updateResponseComponentsEnabled());
        filterFileExtensionCheckBox.addActionListener(e -> updateFileExtensionComponentsEnabled());
        
        // 状态码模式切换
        statusCodeListRadio.addActionListener(e -> updateResponseComponentsEnabled());
        statusCodeRangeRadio.addActionListener(e -> updateResponseComponentsEnabled());
        
        updateComponentsEnabled();
    }
    
    private void updateComponentsEnabled() {
        boolean enabled = enabledCheckBox.isSelected();
        blacklistRadio.setEnabled(enabled);
        whitelistRadio.setEnabled(enabled);
        filterRequestContentTypeCheckBox.setEnabled(enabled);
        filterRequestMethodCheckBox.setEnabled(enabled);
        filterResponseContentTypeCheckBox.setEnabled(enabled);
        filterResponseStatusCodeCheckBox.setEnabled(enabled);
        filterFileExtensionCheckBox.setEnabled(enabled);
        
        updateRequestComponentsEnabled();
        updateResponseComponentsEnabled();
        updateFileExtensionComponentsEnabled();
    }
    
    private void updateRequestComponentsEnabled() {
        boolean enabled = enabledCheckBox.isSelected() && filterRequestContentTypeCheckBox.isSelected();
        requestContentTypeField.setEnabled(enabled);
        requestContentTypeList.setEnabled(enabled);
        
        enabled = enabledCheckBox.isSelected() && filterRequestMethodCheckBox.isSelected();
        getCheckBox.setEnabled(enabled);
        postCheckBox.setEnabled(enabled);
        putCheckBox.setEnabled(enabled);
        deleteCheckBox.setEnabled(enabled);
        patchCheckBox.setEnabled(enabled);
        optionsCheckBox.setEnabled(enabled);
        headCheckBox.setEnabled(enabled);
    }
    
    private void updateResponseComponentsEnabled() {
        boolean enabled = enabledCheckBox.isSelected() && filterResponseContentTypeCheckBox.isSelected();
        responseContentTypeField.setEnabled(enabled);
        responseContentTypeList.setEnabled(enabled);
        
        enabled = enabledCheckBox.isSelected() && filterResponseStatusCodeCheckBox.isSelected();
        statusCodeListRadio.setEnabled(enabled);
        statusCodeRangeRadio.setEnabled(enabled);
        statusCodeField.setEnabled(enabled && statusCodeListRadio.isSelected());
        statusCodeList.setEnabled(enabled && statusCodeListRadio.isSelected());
        statusCodeMinSpinner.setEnabled(enabled && statusCodeRangeRadio.isSelected());
        statusCodeMaxSpinner.setEnabled(enabled && statusCodeRangeRadio.isSelected());
    }
    
    private void updateFileExtensionComponentsEnabled() {
        boolean enabled = enabledCheckBox.isSelected() && filterFileExtensionCheckBox.isSelected();
        fileExtensionField.setEnabled(enabled);
        fileExtensionList.setEnabled(enabled);
    }
    
    // 添加/删除方法
    private void addRequestContentType() {
        String text = requestContentTypeField.getText().trim();
        if (!text.isEmpty() && !requestContentTypeListModel.contains(text)) {
            requestContentTypeListModel.addElement(text);
            requestContentTypeField.setText("");
        }
    }
    
    private void removeSelectedRequestContentTypes() {
        int[] indices = requestContentTypeList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
            requestContentTypeListModel.remove(indices[i]);
        }
    }
    
    private void addResponseContentType() {
        String text = responseContentTypeField.getText().trim();
        if (!text.isEmpty() && !responseContentTypeListModel.contains(text)) {
            responseContentTypeListModel.addElement(text);
            responseContentTypeField.setText("");
        }
    }
    
    private void removeSelectedResponseContentTypes() {
        int[] indices = responseContentTypeList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
            responseContentTypeListModel.remove(indices[i]);
        }
    }
    
    private void addStatusCode() {
        String text = statusCodeField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        // 支持逗号分隔的多个状态码
        String[] codes = text.split(",");
        for (String codeStr : codes) {
            try {
                int code = Integer.parseInt(codeStr.trim());
                if (code >= 100 && code <= 599 && !statusCodeListModel.contains(code)) {
                    statusCodeListModel.addElement(code);
                }
            } catch (NumberFormatException e) {
                // 忽略无效的状态码
            }
        }
        statusCodeField.setText("");
    }
    
    private void removeSelectedStatusCodes() {
        int[] indices = statusCodeList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
            statusCodeListModel.remove(indices[i]);
        }
    }
    
    private void addFileExtension() {
        String text = fileExtensionField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        
        // 支持逗号分隔的多个后缀名
        String[] extensions = text.split(",");
        for (String ext : extensions) {
            ext = ext.trim();
            // 去除点号
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty() && !fileExtensionListModel.contains(ext.toLowerCase())) {
                fileExtensionListModel.addElement(ext.toLowerCase());
            }
        }
        fileExtensionField.setText("");
    }
    
    private void removeSelectedFileExtensions() {
        int[] indices = fileExtensionList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
            fileExtensionListModel.remove(indices[i]);
        }
    }
    
    /**
     * 加载过滤器配置
     */
    public void loadFilter(Configuration.RuleFilter filter) {
        if (filter == null) {
            this.filter = new Configuration.RuleFilter();
            return;
        }
        this.filter = filter;
        
        // 加载基本设置
        enabledCheckBox.setSelected(filter.isEnabled());
        if (filter.getMode() == Configuration.RuleFilter.FilterMode.BLACKLIST) {
            blacklistRadio.setSelected(true);
        } else {
            whitelistRadio.setSelected(true);
        }
        
        // 加载请求过滤
        filterRequestContentTypeCheckBox.setSelected(filter.isFilterRequestContentType());
        requestContentTypeListModel.clear();
        for (String type : filter.getRequestContentTypes()) {
            requestContentTypeListModel.addElement(type);
        }
        
        filterRequestMethodCheckBox.setSelected(filter.isFilterRequestMethod());
        List<String> methods = filter.getRequestMethods();
        getCheckBox.setSelected(methods.contains("GET"));
        postCheckBox.setSelected(methods.contains("POST"));
        putCheckBox.setSelected(methods.contains("PUT"));
        deleteCheckBox.setSelected(methods.contains("DELETE"));
        patchCheckBox.setSelected(methods.contains("PATCH"));
        optionsCheckBox.setSelected(methods.contains("OPTIONS"));
        headCheckBox.setSelected(methods.contains("HEAD"));
        
        // 加载响应过滤
        filterResponseContentTypeCheckBox.setSelected(filter.isFilterResponseContentType());
        responseContentTypeListModel.clear();
        for (String type : filter.getResponseContentTypes()) {
            responseContentTypeListModel.addElement(type);
        }
        
        filterResponseStatusCodeCheckBox.setSelected(filter.isFilterResponseStatusCode());
        statusCodeListModel.clear();
        for (Integer code : filter.getResponseStatusCodes()) {
            statusCodeListModel.addElement(code);
        }
        
        Configuration.RuleFilter.StatusCodeRange range = filter.getStatusCodeRange();
        if (range != null && range.isEnabled()) {
            statusCodeRangeRadio.setSelected(true);
            if (range.getMin() != null) {
                statusCodeMinSpinner.setValue(range.getMin());
            }
            if (range.getMax() != null) {
                statusCodeMaxSpinner.setValue(range.getMax());
            }
        } else {
            statusCodeListRadio.setSelected(true);
        }
        
        // 加载文件后缀名
        filterFileExtensionCheckBox.setSelected(filter.isFilterFileExtension());
        fileExtensionListModel.clear();
        for (String ext : filter.getFileExtensions()) {
            fileExtensionListModel.addElement(ext);
        }
        
        updateComponentsEnabled();
    }
    
    /**
     * 获取过滤器配置
     */
    public Configuration.RuleFilter getFilter() {
        if (filter == null) {
            filter = new Configuration.RuleFilter();
        }
        
        // 保存基本设置
        filter.setEnabled(enabledCheckBox.isSelected());
        filter.setMode(blacklistRadio.isSelected() ? 
            Configuration.RuleFilter.FilterMode.BLACKLIST : 
            Configuration.RuleFilter.FilterMode.WHITELIST);
        
        // 保存请求过滤
        filter.setFilterRequestContentType(filterRequestContentTypeCheckBox.isSelected());
        List<String> requestContentTypes = new ArrayList<>();
        for (int i = 0; i < requestContentTypeListModel.size(); i++) {
            requestContentTypes.add(requestContentTypeListModel.getElementAt(i));
        }
        filter.setRequestContentTypes(requestContentTypes);
        
        filter.setFilterRequestMethod(filterRequestMethodCheckBox.isSelected());
        List<String> requestMethods = new ArrayList<>();
        if (getCheckBox.isSelected()) requestMethods.add("GET");
        if (postCheckBox.isSelected()) requestMethods.add("POST");
        if (putCheckBox.isSelected()) requestMethods.add("PUT");
        if (deleteCheckBox.isSelected()) requestMethods.add("DELETE");
        if (patchCheckBox.isSelected()) requestMethods.add("PATCH");
        if (optionsCheckBox.isSelected()) requestMethods.add("OPTIONS");
        if (headCheckBox.isSelected()) requestMethods.add("HEAD");
        filter.setRequestMethods(requestMethods);
        
        // 保存响应过滤
        filter.setFilterResponseContentType(filterResponseContentTypeCheckBox.isSelected());
        List<String> responseContentTypes = new ArrayList<>();
        for (int i = 0; i < responseContentTypeListModel.size(); i++) {
            responseContentTypes.add(responseContentTypeListModel.getElementAt(i));
        }
        filter.setResponseContentTypes(responseContentTypes);
        
        filter.setFilterResponseStatusCode(filterResponseStatusCodeCheckBox.isSelected());
        List<Integer> statusCodes = new ArrayList<>();
        for (int i = 0; i < statusCodeListModel.size(); i++) {
            statusCodes.add(statusCodeListModel.getElementAt(i));
        }
        filter.setResponseStatusCodes(statusCodes);
        
        Configuration.RuleFilter.StatusCodeRange range = filter.getStatusCodeRange();
        if (range == null) {
            range = new Configuration.RuleFilter.StatusCodeRange();
            filter.setStatusCodeRange(range);
        }
        range.setEnabled(statusCodeRangeRadio.isSelected());
        if (statusCodeRangeRadio.isSelected()) {
            range.setMin((Integer) statusCodeMinSpinner.getValue());
            range.setMax((Integer) statusCodeMaxSpinner.getValue());
        }
        
        // 保存文件后缀名
        filter.setFilterFileExtension(filterFileExtensionCheckBox.isSelected());
        List<String> fileExtensions = new ArrayList<>();
        for (int i = 0; i < fileExtensionListModel.size(); i++) {
            fileExtensions.add(fileExtensionListModel.getElementAt(i));
        }
        filter.setFileExtensions(fileExtensions);
        
        return filter;
    }
}


package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;

import javax.swing.*;
import java.awt.*;

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
    private JTabbedPane tabbedPane;
    
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
     * 创建头部面板
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 标题
        JLabel titleLabel = new JLabel(
            "<html><b style='font-size:14px'>配对 [" + pair.getId() + "] 配置</b></html>"
        );
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 标签输入
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        labelPanel.add(new JLabel("配对标签:"));
        labelField = new JTextField(30);
        labelField.setToolTipText("为这个配对起一个描述性的名称，如：SQL注入检测、时间盲注等");
        labelPanel.add(labelField);
        
        JLabel hintLabel = new JLabel(
            "<html><i>例如: SQL注入错误消息检测、SSRF DNS外带、XSS反射检测等</i></html>"
        );
        labelPanel.add(hintLabel);
        
        panel.add(labelPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建标签页
     */
    private JTabbedPane createTabbedPane() {
        tabbedPane = new JTabbedPane();
        
        // 请求配置标签页
        requestPanel = new UnifiedHttpConfigPanel(pair.getRequestConfig());
        JScrollPane requestScroll = new JScrollPane(requestPanel);
        tabbedPane.addTab("📤 请求配置", requestScroll);
        tabbedPane.setToolTipTextAt(0, "配置请求匹配条件和注入点");
        
        // 响应配置标签页
        responsePanel = new UnifiedResponseConfigPanel(pair.getResponseConfig());
        JScrollPane responseScroll = new JScrollPane(responsePanel);
        tabbedPane.addTab("📥 响应配置", responseScroll);
        tabbedPane.setToolTipTextAt(1, "配置响应匹配条件，判断漏洞是否存在");
        
        // 帮助标签页
        tabbedPane.addTab("❓ 帮助", createHelpPanel());
        
        return tabbedPane;
    }
    
    /**
     * 创建帮助面板
     */
    private JPanel createHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setMargin(new Insets(10, 10, 10, 10));
        helpText.setText(
            "═════════════════════════════════════════════════════\n" +
            "              配对配置帮助文档\n" +
            "═════════════════════════════════════════════════════\n\n" +
            
            "什么是配对？\n" +
            "──────────────────────────────────────────────────\n" +
            "配对 = 请求配置 + 响应配置\n" +
            "• 请求配置：定义哪些请求会被测试，以及如何注入payload\n" +
            "• 响应配置：定义如何判断漏洞是否存在\n\n" +
            
            "配置流程：\n" +
            "──────────────────────────────────────────────────\n" +
            "1. 在「请求配置」标签页：\n" +
            "   - 添加HTTP元素（Method、Path、Parameter等）\n" +
            "   - 勾选「匹配」表示这是请求条件\n" +
            "   - 勾选「注入」表示这是注入点\n" +
            "   - 配置payload（支持动态变量）\n\n" +
            
            "2. 在「响应配置」标签页：\n" +
            "   - 添加响应元素（Status Code、Body、Time等）\n" +
            "   - 配置匹配规则\n" +
            "   - 设置复杂表达式（可选）\n\n" +
            
            "实际案例：\n" +
            "──────────────────────────────────────────────────\n\n" +
            
            "案例1: SQL注入（错误消息）\n" +
            "请求配置：\n" +
            "  [1] Method = GET, POST (匹配)\n" +
            "  [2] Parameter id, user_id (匹配+注入)\n" +
            "      Payload: {{ORIGINAL}}' OR '1'='1--\n" +
            "  表达式: 1 AND 2\n" +
            "响应配置：\n" +
            "  [1] Status Code = 500\n" +
            "  [2] Body 包含 \"sql\", \"mysql\", \"syntax\"\n" +
            "  表达式: 1 OR 2\n\n" +
            
            "案例2: SSRF（DNS外带）\n" +
            "请求配置：\n" +
            "  [1] Parameter url, redirect, callback (匹配+注入)\n" +
            "      Payload: http://{{COLLABORATOR}}/\n" +
            "响应配置：\n" +
            "  [1] Collaborator DNS交互\n\n" +
            
            "案例3: 时间盲注\n" +
            "请求配置：\n" +
            "  [1] Parameter id (匹配+注入)\n" +
            "      Payload: {{ORIGINAL}} AND SLEEP(5)--\n" +
            "响应配置：\n" +
            "  [1] Response Time > 5000ms\n\n" +
            
            "支持的动态变量：\n" +
            "──────────────────────────────────────────────────\n" +
            "• {{ORIGINAL}} - 原始值\n" +
            "• {{ORIGINAL_URL_ENCODED}} - URL编码的原始值\n" +
            "• {{ORIGINAL_BASE64}} - Base64编码的原始值\n" +
            "• {{COLLABORATOR}} - Burp Collaborator域名\n" +
            "• {{RANDOM_STRING}} - 随机字符串\n" +
            "• {{UUID}} - UUID\n" +
            "• {{TIMESTAMP}} - 时间戳\n" +
            "• {{BASE64:xxx}} - Base64编码\n" +
            "• {{URL_ENCODE:xxx}} - URL编码\n" +
            "• {{MD5:xxx}} - MD5哈希\n" +
            "• {{SHA256:xxx}} - SHA256哈希\n\n" +
            
            "复杂表达式：\n" +
            "──────────────────────────────────────────────────\n" +
            "• AND - 逻辑与（所有条件都满足）\n" +
            "• OR  - 逻辑或（任意条件满足）\n" +
            "• NOT - 逻辑非（条件不满足）\n" +
            "• ()  - 分组（控制优先级）\n\n" +
            
            "示例:\n" +
            "  (1 AND 2) OR 3        - 1和2都满足，或3满足\n" +
            "  1 AND (2 OR 3)        - 1满足，且2或3满足\n" +
            "  NOT 1 AND 2           - 1不满足，且2满足\n" +
            "  (1 OR 2) AND (3 OR 4) - 复杂组合\n\n" +
            
            "提示：\n" +
            "──────────────────────────────────────────────────\n" +
            "• 表达式留空表示所有元素都需满足（AND关系）\n" +
            "• 多个匹配值之间是OR关系（任意一个匹配即可）\n" +
            "• 可以先配置简单规则，测试成功后再添加复杂条件\n" +
            "• 使用Collaborator时，确保Burp Suite的Collaborator功能已启用\n"
        );
        
        JScrollPane scrollPane = new JScrollPane(helpText);
        panel.add(scrollPane, BorderLayout.CENTER);
        
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
        if (pair.getLabel() != null) {
            labelField.setText(pair.getLabel());
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
            messages.append("❌ 响应配置为空！请至少添加一个响应元素\n");
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
                "这意味着只会匹配请求，不会发送测试payload。\n" +
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
        
        // 保存响应配置
        UnifiedResponseConfig responseConfig = responsePanel.getConfig();
        if (responseConfig.getElements().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "响应配置不能为空！\n请至少添加一个响应元素。",
                "验证失败",
                JOptionPane.WARNING_MESSAGE);
            tabbedPane.setSelectedIndex(1);
            return false;
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


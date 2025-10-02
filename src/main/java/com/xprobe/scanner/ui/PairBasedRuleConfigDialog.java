package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于配对的规则配置对话框
 * 新架构：请求-响应配对
 */
public class PairBasedRuleConfigDialog extends JDialog {
    
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private Configuration configuration;
    private boolean saved = false;
    
    // UI组件
    private JTextField ruleNameField;
    private JCheckBox enabledCheckBox;
    private JTextArea descriptionArea;
    private PairManagementPanel pairManagementPanel;
    private JComboBox<Configuration.DeduplicationGranularity> deduplicationCombo;
    
    // 简单模式组件
    private UnifiedHttpConfigPanel simpleRequestPanel;
    private UnifiedResponseConfigPanel simpleResponsePanel;
    private boolean isSimpleMode;
    
    public PairBasedRuleConfigDialog(Window owner, MontoyaApi api,
                                    ConfigurationManager configManager,
                                    Configuration config) {
        super(owner, config == null ? "添加扫描规则" : "编辑扫描规则", 
              ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.configManager = configManager;
        this.configuration = config != null ? config : new Configuration();
        
        initComponents();
        loadConfiguration();
        
        setSize(1200, 800);
        setLocationRelativeTo(owner);
    }
    
    private JPanel pairContainerPanel;  // 配对面板容器
    private JTabbedPane tabbedPane;     // 主标签页
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 检测是否是简单模式（默认简单模式，除非已有多个配对）
        isSimpleMode = (configuration.getPairs() == null || 
                       configuration.getPairs().isEmpty() ||
                       configuration.getPairs().size() == 1);
        
        // 顶部：模式切换按钮
        add(createModeTogglePanel(), BorderLayout.NORTH);
        
        // 中间：标签页
        rebuildTabbedPane();
        
        // 底部：按钮
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建模式切换面板
     */
    private JPanel createModeTogglePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        
        // 左侧：当前模式说明
        JLabel modeLabel = new JLabel();
        updateModeLabel(modeLabel);
        panel.add(modeLabel, BorderLayout.WEST);
        
        // 右侧：切换按钮
        JButton toggleButton = new JButton();
        updateToggleButton(toggleButton);
        
        toggleButton.addActionListener(e -> {
            // 切换模式
            if (isSimpleMode) {
                // 简单 → 高级：提示用户
                int confirm = JOptionPane.showConfirmDialog(this,
                    "切换到高级模式将允许您添加多个请求-响应配对。\n" +
                    "当前的请求和响应配置将保留为第一个配对。\n\n" +
                    "是否继续？",
                    "切换到高级模式",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
                
                if (confirm == JOptionPane.YES_OPTION) {
                    isSimpleMode = false;
                }
            } else {
                // 高级 → 简单：检查是否有多个配对
                List<RuleMatchPair> pairs = pairManagementPanel != null ? 
                    pairManagementPanel.getPairs() : new ArrayList<>();
                
                if (pairs.size() > 1) {
                    int confirm = JOptionPane.showConfirmDialog(this,
                        "当前有 " + pairs.size() + " 个配对。\n" +
                        "切换到简单模式将只保留第一个配对，其余配对将被丢弃。\n\n" +
                        "是否继续？",
                        "切换到简单模式",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    
                    if (confirm != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                
                isSimpleMode = true;
            }
            
            // 重新构建界面
            rebuildTabbedPane();
            updateModeLabel(modeLabel);
            updateToggleButton(toggleButton);
        });
        
        panel.add(toggleButton, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * 更新模式标签
     */
    private void updateModeLabel(JLabel label) {
        if (isSimpleMode) {
            label.setText("<html><b>🌟 简单模式</b> - 适合大部分场景（单个请求-响应配对）</html>");
            label.setForeground(new Color(0, 128, 0));
        } else {
            label.setText("<html><b>⚙️ 高级模式</b> - 多个配对 + 复杂逻辑</html>");
            label.setForeground(new Color(255, 140, 0));
        }
    }
    
    /**
     * 更新切换按钮
     */
    private void updateToggleButton(JButton button) {
        if (isSimpleMode) {
            button.setText("切换到高级模式 ➜");
            button.setToolTipText("需要多个请求-响应配对时切换");
        } else {
            button.setText("⬅ 切换到简单模式");
            button.setToolTipText("只需要单个请求-响应配对时切换");
        }
    }
    
    /**
     * 重新构建标签页
     */
    private void rebuildTabbedPane() {
        // 移除旧的标签页
        if (tabbedPane != null) {
            remove(tabbedPane);
        }
        
        // 创建新的标签页
        tabbedPane = new JTabbedPane();
        
        // 1. 基本信息标签页
        tabbedPane.addTab("📋 基本信息", createBasicInfoPanel());
        
        if (isSimpleMode) {
            // ✅ 简单模式：分离的请求和响应标签页
            tabbedPane.addTab("📥 请求配置", createSimpleRequestPanel());
            tabbedPane.addTab("📤 响应配置", createSimpleResponsePanel());
            tabbedPane.addTab("⚙️ 高级选项", createAdvancedPanel());
            tabbedPane.addTab("❓ 帮助", createSimpleHelpPanel());
        } else {
            // ✅ 高级模式：保留原有的配对管理
            pairContainerPanel = new JPanel(new BorderLayout());
            tabbedPane.addTab("🔗 请求-响应配对", pairContainerPanel);
            tabbedPane.addTab("⚙️ 高级选项", createAdvancedPanel());
            tabbedPane.addTab("❓ 帮助", createHelpPanel());
            
            // 重新加载配对管理面板
            if (pairManagementPanel == null && configuration.getPairs() != null && !configuration.getPairs().isEmpty()) {
                pairManagementPanel = new PairManagementPanel(api, 
                    configuration.getPairs(), 
                    configuration.getPairExpression());
                pairContainerPanel.add(pairManagementPanel, BorderLayout.CENTER);
            } else if (pairManagementPanel != null) {
                pairContainerPanel.add(pairManagementPanel, BorderLayout.CENTER);
            } else {
                // 如果是从简单模式切换过来，创建配对管理面板
                List<RuleMatchPair> pairs = new ArrayList<>();
                
                // 从简单模式面板获取配置
                if (simpleRequestPanel != null || simpleResponsePanel != null) {
                    RuleMatchPair pair = new RuleMatchPair();
                    pair.setId(1);
                    pair.setLabel("配对 1");
                    pair.setEnabled(true);
                    
                    if (simpleRequestPanel != null) {
                        pair.setRequestConfig(simpleRequestPanel.getConfig());
                    }
                    if (simpleResponsePanel != null) {
                        pair.setResponseConfig(simpleResponsePanel.getConfig());
                    }
                    
                    pairs.add(pair);
                }
                
                pairManagementPanel = new PairManagementPanel(api, pairs, "1");
                pairContainerPanel.add(pairManagementPanel, BorderLayout.CENTER);
            }
        }
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 刷新界面
        revalidate();
        repaint();
    }
    
    /**
     * 创建基本信息面板
     */
    private JPanel createBasicInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 规则名称
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("规则名称:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        ruleNameField = new JTextField(40);
        panel.add(ruleNameField, gbc);
        
        // 启用状态
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        enabledCheckBox = new JCheckBox("启用此规则", true);
        enabledCheckBox.setFont(enabledCheckBox.getFont().deriveFont(Font.BOLD));
        panel.add(enabledCheckBox, gbc);
        
        // 规则描述
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("规则描述:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        descriptionArea = new JTextArea(10, 40);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(descriptionArea);
        panel.add(scrollPane, gbc);
        
        // 说明文本
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        JLabel hintLabel = new JLabel("<html><i>" +
            "提示：规则名称用于标识此规则，描述用于说明此规则的用途和检测逻辑。注入模式在被动扫描配置Tab统一设置。" +
            "</i></html>");
        panel.add(hintLabel, gbc);
        
        return panel;
    }
    
    /**
     * 创建高级选项面板
     */
    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 去重颗粒度
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("去重颗粒度:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        deduplicationCombo = new JComboBox<>(Configuration.DeduplicationGranularity.values());
        deduplicationCombo.setSelectedItem(Configuration.DeduplicationGranularity.AUTO);
        panel.add(deduplicationCombo, gbc);
        
        // 说明
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setBackground(panel.getBackground());
        helpText.setText(
            "去重颗粒度说明：\n\n" +
            "━━━━━━━━ 推荐选项 ━━━━━━━━\n\n" +
            "• AUTO（自动检测）\n" +
            "  - 智能判断去重策略，根据注入点类型自动选择\n" +
            "  - 参数注入 → PARAMETER，整体扫描 → REQUEST\n" +
            "  - ✅ 推荐大多数场景使用\n\n" +
            
            "━━━━━━━━ 细粒度选项 ━━━━━━━━\n\n" +
            "【全局级别】\n" +
            "• GLOBAL（全局）\n" +
            "  - 整个规则只测试一次，所有请求共享\n" +
            "  - 适用：概念验证、快速检测\n\n" +
            
            "• HOST（主机级）\n" +
            "  - 每个主机只测试一次\n" +
            "  - 例：example.com测试一次，api.example.com再测试一次\n\n" +
            
            "• PATH（路径级）\n" +
            "  - 每个路径只测试一次\n" +
            "  - 例：/api/user测试一次，/api/post再测试一次\n\n" +
            
            "【请求级别】\n" +
            "• REQUEST（请求级）\n" +
            "  - 每个完整请求只测试一次（method+host+path+content-type）\n" +
            "  - 适用：路径扫描、整体body替换\n\n" +
            
            "【参数级别】\n" +
            "• PARAMETER_NAME_GLOBAL（参数名-全局）\n" +
            "  - 相同参数名只测试一次（跨所有请求）\n" +
            "  - 例：id参数在任何地方只测试一次\n\n" +
            
            "• PARAMETER_NAME_PER_PATH（参数名-路径）\n" +
            "  - 每个路径下的参数名分别测试\n" +
            "  - 例：/api/user?id=1测试id，/api/post?id=1再测试id\n\n" +
            
            "• PARAMETER（参数级）\n" +
            "  - 每个请求中的参数分别测试\n" +
            "  - 适用：参数注入（SQL注入、XSS、LFI等）\n\n" +
            
            "【特殊选项】\n" +
            "• INJECTION_POINT（注入点级）\n" +
            "  - 每个注入点都分别测试\n" +
            "  - 适用：需要测试所有位置，如fuzzing\n\n" +
            
            "• NONE（无去重）\n" +
            "  - 每次都测试，不进行去重\n" +
            "  - 适用：Fuzzing模式、压力测试\n\n" +
            
            "━━━━━━━━ 选择建议 ━━━━━━━━\n\n" +
            "✅ 一般场景 → AUTO\n" +
            "✅ 参数注入 → PARAMETER 或 PARAMETER_NAME_PER_PATH\n" +
            "✅ 路径扫描 → PATH 或 REQUEST\n" +
            "✅ 快速验证 → GLOBAL 或 HOST\n" +
            "✅ 完全测试 → INJECTION_POINT 或 NONE\n"
        );
        JScrollPane scrollPane = new JScrollPane(helpText);
        panel.add(scrollPane, gbc);
        
        return panel;
    }
    
    /**
     * 创建简单请求配置面板
     */
    private JPanel createSimpleRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 说明文字
        JLabel hintLabel = new JLabel(
            "<html><b>🎯 配置说明：</b>在此配置<b>匹配条件</b>（哪些请求会被扫描）和<b>注入点</b>（在哪里注入Payload）" +
            "<br><i>提示：大部分场景只需要配置一个请求-响应配对，这样更简单直观。</i></html>"
        );
        hintLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 130, 180), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        hintLabel.setBackground(new Color(240, 248, 255));
        hintLabel.setOpaque(true);
        panel.add(hintLabel, BorderLayout.NORTH);
        
        // 请求配置面板
        simpleRequestPanel = new UnifiedHttpConfigPanel();
        panel.add(simpleRequestPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建简单响应配置面板
     */
    private JPanel createSimpleResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 说明文字
        JLabel hintLabel = new JLabel(
            "<html><b>🎯 配置说明：</b>在此配置<b>响应匹配条件</b>（什么样的响应代表检测成功）" +
            "<br><i>提示：可以配置响应码、响应体、响应时间、Collaborator交互等条件。</i></html>"
        );
        hintLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 120, 70), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        hintLabel.setBackground(new Color(255, 248, 240));
        hintLabel.setOpaque(true);
        panel.add(hintLabel, BorderLayout.NORTH);
        
        // 响应配置面板
        simpleResponsePanel = new UnifiedResponseConfigPanel();
        panel.add(simpleResponsePanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建简单模式帮助面板
     */
    private JPanel createSimpleHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setMargin(new Insets(10, 10, 10, 10));
        helpText.setText(
            "═══════════════════════════════════════════════════════════════\n" +
            "          XProbe 简化规则配置帮助（单配对模式）\n" +
            "═══════════════════════════════════════════════════════════════\n\n" +
            
            "一、什么是简化模式？\n" +
            "────────────────────────────────────────────────────────────\n" +
            "简化模式适合大部分场景（约90%），只需配置：\n" +
            "  • 请求配置：定义哪些请求会被测试，以及如何注入payload\n" +
            "  • 响应配置：定义如何判断漏洞是否存在\n\n" +
            
            "相比高级模式（多配对），简化模式：\n" +
            "  ✅ 更简单：无需理解\"配对\"概念\n" +
            "  ✅ 更直观：请求和响应分开配置\n" +
            "  ✅ 更快速：操作步骤减少40%\n\n" +
            
            "二、配置步骤\n" +
            "────────────────────────────────────────────────────────────\n" +
            "1. 填写基本信息\n" +
            "   - 规则名称：如\"SQL注入检测\"、\"XSS检测\"等\n" +
            "   - 启用状态：勾选后规则才会生效\n" +
            "   - 规则描述：说明规则用途和检测逻辑\n\n" +
            
            "2. 配置请求\n" +
            "   - 点击「📥 请求配置」标签页\n" +
            "   - 添加匹配条件（如Method、Parameter等）\n" +
            "   - 配置注入点和Payload\n\n" +
            
            "3. 配置响应\n" +
            "   - 点击「📤 响应配置」标签页\n" +
            "   - 添加匹配条件（如状态码、响应体等）\n\n" +
            
            "4. 设置高级选项\n" +
            "   - 选择合适的去重颗粒度（通常选AUTO）\n\n" +
            
            "5. 保存规则\n\n" +
            
            "三、实际案例\n" +
            "────────────────────────────────────────────────────────────\n\n" +
            
            "案例1: SQL注入检测\n" +
            "规则名称: SQL注入错误消息检测\n\n" +
            
            "请求配置:\n" +
            "  [1] Method = GET, POST  ✓用于匹配\n" +
            "  [2] Parameter\n" +
            "      匹配值: id, user_id, uid\n" +
            "      Payload: {{ORIGINAL}}' OR '1'='1--\n" +
            "      ✓用于匹配 ✓用于注入\n" +
            "  逻辑表达式: 1 AND 2\n\n" +
            
            "响应配置:\n" +
            "  [1] 状态码 = 500\n" +
            "  [2] 响应体 包含 \"sql\", \"mysql\", \"syntax\"\n" +
            "  逻辑表达式: 1 OR 2\n\n" +
            
            "说明: 响应500或包含SQL错误消息即认为存在SQL注入\n\n" +
            
            "───────────────────────────────────────────────────────────\n\n" +
            
            "案例2: XSS检测\n" +
            "规则名称: 反射XSS检测\n\n" +
            
            "请求配置:\n" +
            "  [1] Parameter\n" +
            "      匹配值: q, search, keyword\n" +
            "      Payload: <script>alert(1)</script>\n" +
            "      ✓用于匹配 ✓用于注入\n\n" +
            
            "响应配置:\n" +
            "  [1] 响应体 包含 \"<script>alert(1)</script>\"\n" +
            "  [2] 响应体 包含 \"alert(1)\"\n" +
            "  逻辑表达式: 1 OR 2\n\n" +
            
            "说明: 响应体包含payload即认为存在XSS\n\n" +
            
            "───────────────────────────────────────────────────────────\n\n" +
            
            "案例3: SSRF检测\n" +
            "规则名称: SSRF DNS外带检测\n\n" +
            
            "请求配置:\n" +
            "  [1] Parameter\n" +
            "      匹配值: url, redirect, callback, host\n" +
            "      Payload: http://{{COLLABORATOR}}/\n" +
            "      ✓用于匹配 ✓用于注入\n\n" +
            
            "响应配置:\n" +
            "  [1] Collaborator 有DNS交互\n\n" +
            
            "说明: 检测到DNS外带即认为存在SSRF\n\n" +
            
            "四、支持的动态变量\n" +
            "────────────────────────────────────────────────────────────\n" +
            "• {{ORIGINAL}} - 注入点原始值\n" +
            "• {{ORIGINAL_URL_ENCODED}} - URL编码的原始值\n" +
            "• {{ORIGINAL_BASE64}} - Base64编码的原始值\n" +
            "• {{COLLABORATOR}} / {{DNSLOG}} - Burp Collaborator域名\n" +
            "• {{RANDOM_STRING}} - 随机字符串（8位）\n" +
            "• {{UUID}} - UUID\n" +
            "• {{TIMESTAMP}} - 当前时间戳\n" +
            "• {{BASE64:xxx}} - Base64编码指定内容\n" +
            "• {{URL_ENCODE:xxx}} - URL编码指定内容\n\n" +
            
            "五、什么时候需要高级模式？\n" +
            "────────────────────────────────────────────────────────────\n" +
            "如果您需要以下功能，点击右上角「切换到高级模式」按钮：\n" +
            "  • 同一漏洞的多种检测方法（如SQL注入的错误消息+时间盲注）\n" +
            "  • 复杂的逻辑关系（如 (1 OR 2) AND 3）\n" +
            "  • 多步骤验证流程\n\n" +
            
            "点击「切换到高级模式 ➜」按钮后：\n" +
            "  1. 当前的请求和响应配置会保留为第一个配对\n" +
            "  2. 界面切换到配对管理模式\n" +
            "  3. 您可以添加更多配对并设置逻辑表达式\n\n" +
            
            "如需切换回简单模式，点击「⬅ 切换到简单模式」按钮。\n\n" +
            
            "六、最佳实践\n" +
            "────────────────────────────────────────────────────────────\n" +
            "1. 合理命名：规则名称应清晰描述检测的漏洞类型\n" +
            "2. 详细描述：说明检测逻辑、payload含义等\n" +
            "3. 从简单开始：先用简化模式，需要时再切换高级模式\n" +
            "4. 测试验证：配置完成后先在测试环境验证\n" +
            "5. 渐进配置：从基础条件开始，逐步完善\n\n" +
            
            "七、常见问题\n" +
            "────────────────────────────────────────────────────────────\n" +
            "Q: 简化模式和高级模式有什么区别？\n" +
            "A: 简化模式只支持单个请求-响应配对，界面更简单。\n" +
            "   高级模式支持多个配对和复杂逻辑表达式。\n\n" +
            
            "Q: 如何从简化模式切换到高级模式？\n" +
            "A: 点击对话框右上角的「切换到高级模式 ➜」按钮即可。\n" +
            "   当前的请求和响应配置会保留为第一个配对。\n\n" +
            
            "Q: 从高级模式切换回简单模式会丢失数据吗？\n" +
            "A: 如果有多个配对，切换时会提示您。切换后只保留第一个配对，\n" +
            "   其余配对将被丢弃。建议在只有1个配对时切换回简单模式。\n\n" +
            
            "Q: 去重颗粒度如何选择？\n" +
            "A: • AUTO（推荐）- 智能选择，适合大多数场景\n" +
            "   • PARAMETER_NAME_PER_PATH - 参数注入（每个路径分别测试）\n" +
            "   • PARAMETER - 参数注入（每个请求分别测试）\n" +
            "   • PATH - 路径扫描（每个路径一次）\n" +
            "   • HOST - 主机扫描（每个主机一次）\n" +
            "   • GLOBAL - 全局扫描（整个规则一次）\n" +
            "   • NONE - 无去重（Fuzzing模式）\n" +
            "   详见「⚙️ 高级选项」标签页的完整说明。\n\n" +
            
            "───────────────────────────────────────────────────────────\n" +
            "需要更多帮助？查看完整文档或联系支持。\n"
        );
        
        JScrollPane scrollPane = new JScrollPane(helpText);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建高级模式帮助面板
     */
    private JPanel createHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setMargin(new Insets(10, 10, 10, 10));
        helpText.setText(
            "═══════════════════════════════════════════════════════════════\n" +
            "          XProbe 基于配对的规则配置帮助\n" +
            "═══════════════════════════════════════════════════════════════\n\n" +
            
            "一、配对架构简介\n" +
            "────────────────────────────────────────────────────────────\n" +
            "配对 = 请求配置 + 响应配置\n" +
            "• 请求配置：定义哪些请求会被测试，以及如何注入payload\n" +
            "• 响应配置：定义如何判断漏洞是否存在\n" +
            "• 一个规则可以包含多个配对，用逻辑表达式组合\n\n" +
            
            "二、配置步骤\n" +
            "────────────────────────────────────────────────────────────\n" +
            "1. 填写基本信息\n" +
            "   - 规则名称：如\"SQL注入检测\"、\"XSS检测\"等\n" +
            "   - 启用状态：勾选后规则才会生效\n" +
            "   - 规则描述：说明规则用途和检测逻辑\n\n" +
            
            "2. 配置请求-响应配对\n" +
            "   - 点击「添加新配对」创建配对\n" +
            "   - 在配对中配置请求和响应\n" +
            "   - 可以添加多个配对\n" +
            "   - 使用表达式组合配对（如 1 OR 2）\n\n" +
            
            "3. 设置高级选项\n" +
            "   - 选择合适的去重颗粒度\n" +
            "   - 通常选择AUTO即可\n\n" +
            
            "三、实际案例\n" +
            "────────────────────────────────────────────────────────────\n\n" +
            
            "案例1: SQL注入检测（两种方法）\n" +
            "规则名称: SQL注入综合检测\n\n" +
            
            "配对1: 错误消息检测\n" +
            "  请求配置:\n" +
            "    [1] Method = GET, POST\n" +
            "    [2] Parameter id, user_id, uid\n" +
            "        Payload: {{ORIGINAL}}' OR '1'='1--\n" +
            "    表达式: 1 AND 2\n" +
            "  响应配置:\n" +
            "    [1] Status Code = 500\n" +
            "    [2] Body 包含 \"sql\", \"mysql\", \"syntax\"\n" +
            "    表达式: 1 OR 2\n\n" +
            
            "配对2: 时间盲注检测\n" +
            "  请求配置:\n" +
            "    [1] Parameter id, user_id, uid\n" +
            "        Payload: {{ORIGINAL}} AND SLEEP(5)--\n" +
            "  响应配置:\n" +
            "    [1] Response Time > 5000ms\n\n" +
            
            "配对逻辑: 1 OR 2\n" +
            "说明: 错误消息或时间延迟任意一个成功即认为存在SQL注入\n\n" +
            
            "───────────────────────────────────────────────────────────\n\n" +
            
            "案例2: SSRF检测（多协议）\n" +
            "规则名称: SSRF综合检测\n\n" +
            
            "配对1: HTTP外带\n" +
            "  请求配置:\n" +
            "    [1] Parameter url, redirect, callback\n" +
            "        Payload: http://{{COLLABORATOR}}/\n" +
            "  响应配置:\n" +
            "    [1] Collaborator HTTP交互\n\n" +
            
            "配对2: DNS外带\n" +
            "  请求配置:\n" +
            "    [1] Parameter host, domain\n" +
            "        Payload: {{COLLABORATOR}}\n" +
            "  响应配置:\n" +
            "    [1] Collaborator DNS交互\n\n" +
            
            "配对逻辑: 1 OR 2\n" +
            "说明: HTTP或DNS任意一个有外带即认为存在SSRF\n\n" +
            
            "───────────────────────────────────────────────────────────\n\n" +
            
            "案例3: XSS检测\n" +
            "规则名称: XSS检测\n\n" +
            
            "配对1: 反射XSS\n" +
            "  请求配置:\n" +
            "    [1] Parameter q, search, keyword\n" +
            "        Payload: <script>alert(1)</script>\n" +
            "  响应配置:\n" +
            "    [1] Body 包含 \"<script>alert(1)</script>\"\n" +
            "    [2] Body 包含 \"alert(1)\"\n" +
            "    表达式: 1 OR 2\n\n" +
            
            "配对逻辑: 1\n" +
            "说明: 响应体包含payload即认为存在XSS\n\n" +
            
            "四、支持的动态变量\n" +
            "────────────────────────────────────────────────────────────\n" +
            "• {{ORIGINAL}} - 注入点原始值\n" +
            "• {{ORIGINAL_URL_ENCODED}} - URL编码的原始值\n" +
            "• {{ORIGINAL_BASE64}} - Base64编码的原始值\n" +
            "• {{COLLABORATOR}} / {{DNSLOG}} - Burp Collaborator域名\n" +
            "• {{RANDOM_STRING}} - 随机字符串（8位）\n" +
            "• {{UUID}} - UUID\n" +
            "• {{TIMESTAMP}} - 当前时间戳\n" +
            "• {{BASE64:xxx}} - Base64编码指定内容\n" +
            "• {{URL_ENCODE:xxx}} - URL编码指定内容\n" +
            "• {{MD5:xxx}} - MD5哈希\n" +
            "• {{SHA256:xxx}} - SHA256哈希\n\n" +
            
            "五、逻辑表达式\n" +
            "────────────────────────────────────────────────────────────\n" +
            "• AND - 逻辑与（所有条件都满足）\n" +
            "• OR - 逻辑或（任意条件满足）\n" +
            "• NOT - 逻辑非（条件不满足）\n" +
            "• () - 分组（控制优先级）\n\n" +
            
            "示例:\n" +
            "  1 OR 2              - 配对1或配对2\n" +
            "  1 AND 2             - 配对1和配对2\n" +
            "  (1 OR 2) AND 3      - (1或2) 且 3\n" +
            "  1 OR 2 OR 3         - 任意一个\n\n" +
            
            "六、最佳实践\n" +
            "────────────────────────────────────────────────────────────\n" +
            "1. 合理命名：规则名称应清晰描述检测的漏洞类型\n" +
            "2. 详细描述：说明检测逻辑、payload含义等\n" +
            "3. 多配对组合：同一漏洞的不同检测方法用配对组合\n" +
            "4. 测试验证：配置完成后先在测试环境验证\n" +
            "5. 渐进配置：从简单规则开始，逐步添加复杂条件\n\n" +
            
            "七、常见问题\n" +
            "────────────────────────────────────────────────────────────\n" +
            "Q: 配对和之前的规则有什么区别？\n" +
            "A: 配对架构将请求和响应紧密关联，每个配对代表一种检测\n" +
            "   方法，多个配对可以用逻辑表达式组合，更灵活、更直观。\n\n" +
            
            "Q: 如何处理多种检测方法？\n" +
            "A: 创建多个配对，每个配对对应一种检测方法，然后用\n" +
            "   OR表达式组合，任意一个成功即可。\n\n" +
            
            "Q: 去重颗粒度如何选择？\n" +
            "A: • AUTO（推荐）- 智能选择，适合大多数场景\n" +
            "   • PARAMETER_NAME_PER_PATH - 参数注入（每个路径分别测试）\n" +
            "   • PARAMETER - 参数注入（每个请求分别测试）\n" +
            "   • PATH - 路径扫描（每个路径一次）\n" +
            "   • HOST - 主机扫描（每个主机一次）\n" +
            "   • GLOBAL - 全局扫描（整个规则一次）\n" +
            "   • NONE - 无去重（Fuzzing模式）\n" +
            "   详见「⚙️ 高级选项」标签页的完整说明。\n\n" +
            
            "Q: Collaborator如何使用？\n" +
            "A: 在payload中使用{{COLLABORATOR}}变量，在响应配置中\n" +
            "   添加Collaborator元素，选择要检测的交互类型即可。\n\n" +
            
            "───────────────────────────────────────────────────────────\n" +
            "需要更多帮助？查看完整文档或联系支持。\n"
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
        
        JButton saveButton = new JButton("保存规则");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> saveConfiguration());
        
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> {
            saved = false;
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
     * 加载配置
     */
    private void loadConfiguration() {
        // 基本信息
        if (configuration.getCustomLabel() != null) {
            ruleNameField.setText(configuration.getCustomLabel());
        }
        enabledCheckBox.setSelected(configuration.isEnabled());
        if (configuration.getDescription() != null) {
            descriptionArea.setText(configuration.getDescription());
        }
        
        if (isSimpleMode) {
            // ✅ 简单模式：加载单个配对到请求/响应面板
            if (configuration.getPairs() != null && !configuration.getPairs().isEmpty()) {
                RuleMatchPair firstPair = configuration.getPairs().get(0);
                if (firstPair.getRequestConfig() != null) {
                    simpleRequestPanel.loadConfig(firstPair.getRequestConfig());
                }
                if (firstPair.getResponseConfig() != null) {
                    simpleResponsePanel.loadConfig(firstPair.getResponseConfig());
                }
            }
        } else {
            // ✅ 高级模式：加载配对管理面板
            if (configuration.getPairs() != null && !configuration.getPairs().isEmpty()) {
                pairManagementPanel = new PairManagementPanel(api, 
                    configuration.getPairs(), 
                    configuration.getPairExpression());
            } else {
                pairManagementPanel = new PairManagementPanel(api);
            }
            
            pairContainerPanel.removeAll();
            pairContainerPanel.add(pairManagementPanel, BorderLayout.CENTER);
            pairContainerPanel.revalidate();
            pairContainerPanel.repaint();
        }
        
        // 高级选项
        if (configuration.getDeduplicationGranularity() != null) {
            deduplicationCombo.setSelectedItem(configuration.getDeduplicationGranularity());
        }
    }
    
    /**
     * 验证配置
     */
    private void validateConfiguration() {
        StringBuilder messages = new StringBuilder();
        messages.append("配置验证结果：\n\n");
        
        // 验证规则名称
        String ruleName = ruleNameField.getText().trim();
        if (ruleName.isEmpty()) {
            messages.append("❌ 规则名称不能为空\n");
        } else {
            messages.append("✅ 规则名称：").append(ruleName).append("\n");
        }
        
        if (isSimpleMode) {
            // 简单模式验证
            messages.append("✅ 配置模式：简化模式（单配对）\n");
            
            UnifiedHttpConfig requestConfig = simpleRequestPanel.getConfig();
            UnifiedResponseConfig responseConfig = simpleResponsePanel.getConfig();
            
            if (requestConfig == null || requestConfig.getElements().isEmpty()) {
                messages.append("⚠️ 请求配置为空，请在「📥 请求配置」标签页添加\n");
            } else {
                messages.append("✅ 请求配置：已配置 ").append(requestConfig.getElements().size()).append(" 个元素\n");
            }
            
            if (responseConfig == null || responseConfig.getElements().isEmpty()) {
                messages.append("⚠️ 响应配置为空，请在「📤 响应配置」标签页添加\n");
            } else {
                messages.append("✅ 响应配置：已配置 ").append(responseConfig.getElements().size()).append(" 个元素\n");
            }
        } else {
            // 高级模式验证
            messages.append("✅ 配置模式：高级模式（多配对）\n");
            
            List<RuleMatchPair> pairs = pairManagementPanel.getPairs();
            if (pairs.isEmpty()) {
                messages.append("❌ 至少需要配置一个请求-响应配对\n");
            } else {
                messages.append("✅ 已配置 ").append(pairs.size()).append(" 个配对\n");
                
                for (int i = 0; i < pairs.size(); i++) {
                    RuleMatchPair pair = pairs.get(i);
                    messages.append("  配对").append(i + 1).append(": ");
                    
                    if (pair.getRequestConfig() == null || 
                        pair.getRequestConfig().getElements().isEmpty()) {
                        messages.append("⚠️ 请求配置为空\n");
                    } else if (pair.getResponseConfig() == null || 
                               pair.getResponseConfig().getElements().isEmpty()) {
                        messages.append("⚠️ 响应配置为空\n");
                    } else {
                        messages.append("✅ 配置完整\n");
                    }
                }
            }
            
            // 验证配对表达式
            String pairExpr = pairManagementPanel.getPairExpression();
            if (pairExpr != null && !pairExpr.isEmpty()) {
                messages.append("✅ 配对表达式：").append(pairExpr).append("\n");
            } else if (pairs.size() > 1) {
                messages.append("⚠️ 有多个配对但未设置表达式，将使用AND逻辑\n");
            }
        }
        
        JOptionPane.showMessageDialog(this,
            messages.toString(),
            "配置验证",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 保存配置
     */
    private void saveConfiguration() {
        // 验证规则名称
        String ruleName = ruleNameField.getText().trim();
        if (ruleName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "规则名称不能为空！",
                "验证失败",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        List<RuleMatchPair> pairs;
        String pairExpression = null;
        
        if (isSimpleMode) {
            // ✅ 简单模式：从请求/响应面板创建单个配对
            UnifiedHttpConfig requestConfig = simpleRequestPanel.getConfig();
            UnifiedResponseConfig responseConfig = simpleResponsePanel.getConfig();
            
            // 验证配置
            if ((requestConfig == null || requestConfig.getElements().isEmpty()) &&
                (responseConfig == null || responseConfig.getElements().isEmpty())) {
                JOptionPane.showMessageDialog(this,
                    "请求配置和响应配置不能都为空！\n" +
                    "请至少配置「📥 请求配置」或「📤 响应配置」中的一个。",
                    "验证失败",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // 创建配对
            RuleMatchPair pair = new RuleMatchPair();
            pair.setId(1);
            pair.setLabel("配对 1");
            pair.setEnabled(true);
            pair.setRequestConfig(requestConfig);
            pair.setResponseConfig(responseConfig);
            
            pairs = new ArrayList<>();
            pairs.add(pair);
            pairExpression = "1";  // 单个配对
        } else {
            // ✅ 高级模式：从配对管理面板获取
            pairs = pairManagementPanel.getPairs();
            if (pairs.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "至少需要配置一个请求-响应配对！\n请点击「请求-响应配对」标签页添加配对。",
                    "验证失败",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            pairExpression = pairManagementPanel.getPairExpression();
        }
        
        // 保存配置
        configuration.setCustomLabel(ruleName);
        configuration.setEnabled(enabledCheckBox.isSelected());
        configuration.setDescription(descriptionArea.getText().trim());
        configuration.setPairs(pairs);
        configuration.setPairExpression(pairExpression);
        configuration.setDeduplicationGranularity(
            (Configuration.DeduplicationGranularity) deduplicationCombo.getSelectedItem()
        );
        
        saved = true;
        dispose();
    }
    
    public boolean showDialog() {
        setVisible(true);
        return saved;
    }
    
    public Configuration getConfiguration() {
        return configuration;
    }
}


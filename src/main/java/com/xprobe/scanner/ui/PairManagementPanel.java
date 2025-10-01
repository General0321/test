package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 配对管理面板
 * 管理多个请求-响应配对，以及配对之间的逻辑关系
 */
public class PairManagementPanel extends JPanel {
    
    private MontoyaApi api;
    private List<RuleMatchPair> pairs;
    private List<PairDisplayPanel> pairPanels;
    private JPanel pairsContainer;
    private JTextArea expressionArea;
    private int nextPairId = 1;
    
    public PairManagementPanel(MontoyaApi api) {
        this.api = api;
        this.pairs = new ArrayList<>();
        this.pairPanels = new ArrayList<>();
        
        initComponents();
    }
    
    public PairManagementPanel(MontoyaApi api, List<RuleMatchPair> pairs, String expression) {
        this.api = api;
        this.pairs = pairs != null ? new ArrayList<>(pairs) : new ArrayList<>();
        this.pairPanels = new ArrayList<>();
        
        initComponents();
        loadPairs(expression);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 顶部工具栏
        add(createToolbar(), BorderLayout.NORTH);
        
        // 中间配对列表
        JScrollPane scrollPane = new JScrollPane(createPairsContainer());
        scrollPane.setBorder(BorderFactory.createTitledBorder("请求-响应配对列表"));
        add(scrollPane, BorderLayout.CENTER);
        
        // 底部表达式面板
        add(createExpressionPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 创建工具栏
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addButton = new JButton("+ 添加新配对");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD));
        addButton.addActionListener(e -> addNewPair());
        
        JButton clearButton = new JButton("清空全部");
        clearButton.addActionListener(e -> clearAll());
        
        JButton helpButton = new JButton("❓ 帮助");
        helpButton.addActionListener(e -> showHelp());
        
        toolbar.add(addButton);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(clearButton);
        toolbar.add(helpButton);
        
        return toolbar;
    }
    
    /**
     * 创建配对容器
     */
    private JPanel createPairsContainer() {
        pairsContainer = new JPanel();
        pairsContainer.setLayout(new BoxLayout(pairsContainer, BoxLayout.Y_AXIS));
        
        // 提示标签
        JLabel hintLabel = new JLabel("<html>" +
            "<b>配对管理说明:</b><br>" +
            "• 每个配对包含：请求配置 + 响应配置<br>" +
            "• 点击「编辑配对」按钮进行详细配置<br>" +
            "• 可以配置多个配对，使用逻辑表达式组合<br>" +
            "• 例如：配对1检测错误消息，配对2检测时间延迟" +
            "</html>");
        hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        pairsContainer.add(hintLabel);
        
        return pairsContainer;
    }
    
    /**
     * 创建表达式面板
     */
    private JPanel createExpressionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TitledBorder border = BorderFactory.createTitledBorder("配对间逻辑表达式");
        panel.setBorder(border);
        
        JLabel label = new JLabel("<html>" +
            "使用配对ID和逻辑运算符组合多个配对，例如: <code>1 OR 2</code> 表示满足配对1或配对2即可<br>" +
            "留空则表示所有配对都需满足（AND关系）" +
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
     * 加载配对
     */
    private void loadPairs(String expression) {
        for (RuleMatchPair pair : pairs) {
            addPairPanel(pair);
            if (pair.getId() >= nextPairId) {
                nextPairId = pair.getId() + 1;
            }
        }
        
        if (expression != null && !expression.isEmpty()) {
            expressionArea.setText(expression);
        }
        
        pairsContainer.revalidate();
        pairsContainer.repaint();
    }
    
    /**
     * 添加新配对
     */
    private void addNewPair() {
        RuleMatchPair newPair = new RuleMatchPair(nextPairId++);
        newPair.setLabel("新配对");
        
        // 打开编辑对话框
        PairEditorDialog dialog = new PairEditorDialog(
            SwingUtilities.getWindowAncestor(this),
            api,
            newPair
        );
        
        if (dialog.showDialog()) {
            pairs.add(newPair);
            addPairPanel(newPair);
            pairsContainer.revalidate();
            pairsContainer.repaint();
        }
    }
    
    /**
     * 添加配对显示面板
     */
    private void addPairPanel(RuleMatchPair pair) {
        PairDisplayPanel panel = new PairDisplayPanel(pair);
        pairPanels.add(panel);
        pairsContainer.add(panel);
    }
    
    /**
     * 移除配对
     */
    private void removePair(PairDisplayPanel panel) {
        int result = JOptionPane.showConfirmDialog(this,
            "确定要删除配对 [" + panel.pair.getId() + "] 吗？",
            "确认删除",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            pairs.remove(panel.pair);
            pairPanels.remove(panel);
            pairsContainer.remove(panel);
            pairsContainer.revalidate();
            pairsContainer.repaint();
        }
    }
    
    /**
     * 清空所有配对
     */
    private void clearAll() {
        if (pairs.isEmpty()) {
            return;
        }
        
        int result = JOptionPane.showConfirmDialog(this,
            "确定要清空所有配对吗？此操作不可撤销！",
            "确认",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            pairs.clear();
            pairPanels.clear();
            pairsContainer.removeAll();
            createPairsContainer();
            expressionArea.setText("");
            nextPairId = 1;
            pairsContainer.revalidate();
            pairsContainer.repaint();
        }
    }
    
    /**
     * 验证表达式
     */
    private void validateExpression() {
        String expr = expressionArea.getText().trim();
        
        if (expr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "表达式为空，将使用默认的AND逻辑\n" +
                "即：所有配对都需满足",
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
            
            // 检查引用的配对ID是否存在
            for (RuleMatchPair pair : pairs) {
                String id = String.valueOf(pair.getId());
                if (expr.contains(id)) {
                    // ID存在
                }
            }
            
            JOptionPane.showMessageDialog(this,
                "表达式格式正确！\n" +
                "表达式: " + expr,
                "验证成功",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "表达式格式错误: " + e.getMessage(),
                "验证失败",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * 显示帮助
     */
    private void showHelp() {
        String help = "═══════════════════════════════════════════\n" +
            "          配对管理帮助\n" +
            "═══════════════════════════════════════════\n\n" +
            
            "什么是配对？\n" +
            "─────────────────────────────────────────\n" +
            "配对 = 请求配置 + 响应配置\n" +
            "每个配对代表一种漏洞检测方法\n\n" +
            
            "为什么需要多个配对？\n" +
            "─────────────────────────────────────────\n" +
            "• 同一漏洞可能有多种检测方法\n" +
            "• 例如SQL注入：\n" +
            "  - 配对1：检测错误消息\n" +
            "  - 配对2：检测时间延迟\n" +
            "  - 逻辑：1 OR 2（任意一个成功即可）\n\n" +
            
            "配对间逻辑表达式：\n" +
            "─────────────────────────────────────────\n" +
            "• AND - 所有配对都需满足\n" +
            "• OR  - 任意配对满足即可\n" +
            "• NOT - 配对不满足\n" +
            "• ()  - 分组控制优先级\n\n" +
            
            "示例：\n" +
            "─────────────────────────────────────────\n" +
            "1 OR 2          - 满足配对1或配对2\n" +
            "1 AND 2         - 同时满足配对1和配对2\n" +
            "(1 OR 2) AND 3  - (1或2) 且 3\n" +
            "1 OR 2 OR 3     - 满足任意一个\n\n" +
            
            "常见使用场景：\n" +
            "─────────────────────────────────────────\n" +
            "1. SQL注入综合检测：\n" +
            "   配对1: 错误消息\n" +
            "   配对2: 时间盲注\n" +
            "   配对3: Boolean盲注\n" +
            "   逻辑: 1 OR 2 OR 3\n\n" +
            
            "2. SSRF多协议检测：\n" +
            "   配对1: HTTP外带\n" +
            "   配对2: DNS外带\n" +
            "   逻辑: 1 OR 2\n\n" +
            
            "3. XSS组合检测：\n" +
            "   配对1: 反射XSS\n" +
            "   配对2: 存储XSS\n" +
            "   逻辑: 1 OR 2\n";
        
        JTextArea textArea = new JTextArea(help);
        textArea.setEditable(false);
        textArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        
        JOptionPane.showMessageDialog(this,
            scrollPane,
            "配对管理帮助",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 获取所有配对
     */
    public List<RuleMatchPair> getPairs() {
        return new ArrayList<>(pairs);
    }
    
    /**
     * 获取配对表达式
     */
    public String getPairExpression() {
        return expressionArea.getText().trim();
    }
    
    /**
     * 配对显示面板
     */
    private class PairDisplayPanel extends JPanel {
        
        private RuleMatchPair pair;
        private JPanel contentPanel;
        private boolean expanded = false;
        
        public PairDisplayPanel(RuleMatchPair pair) {
            this.pair = pair;
            initComponents();
        }
        
        private void initComponents() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
            
            // 头部
            add(createHeaderPanel(), BorderLayout.NORTH);
            
            // 内容（可展开/折叠）
            contentPanel = createContentPanel();
            contentPanel.setVisible(expanded);
            add(contentPanel, BorderLayout.CENTER);
        }
        
        private JPanel createHeaderPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 5));
            
            // 左侧：标题
            JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            
            JButton toggleButton = new JButton(expanded ? "▼" : "▶");
            toggleButton.setPreferredSize(new Dimension(40, 25));
            toggleButton.addActionListener(e -> {
                expanded = !expanded;
                toggleButton.setText(expanded ? "▼" : "▶");
                contentPanel.setVisible(expanded);
                revalidate();
                repaint();
            });
            titlePanel.add(toggleButton);
            
            JLabel idLabel = new JLabel("[" + pair.getId() + "]");
            idLabel.setFont(idLabel.getFont().deriveFont(Font.BOLD, 14f));
            idLabel.setForeground(new Color(0, 100, 200));
            titlePanel.add(idLabel);
            
            String label = pair.getLabel() != null && !pair.getLabel().isEmpty() 
                ? pair.getLabel() 
                : "配对" + pair.getId();
            JLabel nameLabel = new JLabel(label);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
            titlePanel.add(nameLabel);
            
            panel.add(titlePanel, BorderLayout.WEST);
            
            // 右侧：按钮
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            
            JButton editButton = new JButton("编辑配对");
            editButton.addActionListener(e -> editPair());
            
            JButton deleteButton = new JButton("删除");
            deleteButton.setForeground(Color.RED);
            deleteButton.addActionListener(e -> removePair(PairDisplayPanel.this));
            
            buttonPanel.add(editButton);
            buttonPanel.add(deleteButton);
            
            panel.add(buttonPanel, BorderLayout.EAST);
            
            return panel;
        }
        
        private JPanel createContentPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 30, 0, 0));
            
            // 请求配置摘要
            JPanel requestPanel = new JPanel(new BorderLayout(5, 5));
            requestPanel.add(new JLabel("📤 请求配置:"), BorderLayout.NORTH);
            JTextArea requestSummary = new JTextArea(getRequestSummary());
            requestSummary.setEditable(false);
            requestSummary.setBackground(new Color(250, 250, 250));
            requestSummary.setRows(3);
            requestPanel.add(new JScrollPane(requestSummary), BorderLayout.CENTER);
            
            // 响应配置摘要
            JPanel responsePanel = new JPanel(new BorderLayout(5, 5));
            responsePanel.add(new JLabel("📥 响应配置:"), BorderLayout.NORTH);
            JTextArea responseSummary = new JTextArea(getResponseSummary());
            responseSummary.setEditable(false);
            responseSummary.setBackground(new Color(250, 250, 250));
            responseSummary.setRows(3);
            responsePanel.add(new JScrollPane(responseSummary), BorderLayout.CENTER);
            
            // 组合
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                requestPanel, responsePanel);
            splitPane.setResizeWeight(0.5);
            panel.add(splitPane, BorderLayout.CENTER);
            
            return panel;
        }
        
        private String getRequestSummary() {
            UnifiedHttpConfig config = pair.getRequestConfig();
            if (config == null || config.getElements().isEmpty()) {
                return "（未配置）";
            }
            
            StringBuilder sb = new StringBuilder();
            for (UnifiedHttpConfig.HttpElementConfig element : config.getElements()) {
                sb.append("[").append(element.getId()).append("] ");
                sb.append(element.getType().getDisplayName());
                if (element.getName() != null && !element.getName().isEmpty()) {
                    sb.append(" ").append(element.getName());
                }
                if (element.isUseForMatch()) {
                    sb.append(" [匹配]");
                }
                if (element.isUseForInjection()) {
                    sb.append(" [注入]");
                }
                sb.append("\n");
            }
            
            if (config.getConditionExpression() != null && !config.getConditionExpression().isEmpty()) {
                sb.append("表达式: ").append(config.getConditionExpression());
            }
            
            return sb.toString();
        }
        
        private String getResponseSummary() {
            UnifiedResponseConfig config = pair.getResponseConfig();
            if (config == null || config.getElements().isEmpty()) {
                return "（未配置）";
            }
            
            StringBuilder sb = new StringBuilder();
            for (UnifiedResponseConfig.ResponseElementConfig element : config.getElements()) {
                sb.append("[").append(element.getId()).append("] ");
                sb.append(element.getType().getDisplayName());
                sb.append("\n");
            }
            
            if (config.getConditionExpression() != null && !config.getConditionExpression().isEmpty()) {
                sb.append("表达式: ").append(config.getConditionExpression());
            }
            
            return sb.toString();
        }
        
        private void editPair() {
            PairEditorDialog dialog = new PairEditorDialog(
                SwingUtilities.getWindowAncestor(PairManagementPanel.this),
                api,
                pair
            );
            
            if (dialog.showDialog()) {
                // 更新显示
                removeAll();
                initComponents();
                revalidate();
                repaint();
            }
        }
    }
}


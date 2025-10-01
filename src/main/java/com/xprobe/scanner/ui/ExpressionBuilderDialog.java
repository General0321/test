package com.xprobe.scanner.ui;

import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.core.ConditionExpression;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

/**
 * 表达式构建器对话框
 * 支持复杂的逻辑表达式（AND、OR、NOT、括号）
 */
public class ExpressionBuilderDialog extends JDialog {
    private JTree expressionTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JTextArea expressionPreview;
    private List<Configuration.RequestCondition> conditions;
    private ConditionExpression expression;
    private boolean confirmed = false;
    
    public ExpressionBuilderDialog(Window owner, List<Configuration.RequestCondition> conditions, 
                                   ConditionExpression existingExpression) {
        super(owner, "表达式构建器 - 高级模式", ModalityType.APPLICATION_MODAL);
        this.conditions = conditions;
        this.expression = existingExpression;
        
        initComponents();
        setupLayout();
        loadExpression();
        
        setSize(800, 600);
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        // 表达式树
        rootNode = new DefaultMutableTreeNode("表达式根节点");
        treeModel = new DefaultTreeModel(rootNode);
        expressionTree = new JTree(treeModel);
        expressionTree.setShowsRootHandles(true);
        expressionTree.setRootVisible(true);
        
        // 表达式预览
        expressionPreview = new JTextArea(3, 60);
        expressionPreview.setEditable(false);
        expressionPreview.setLineWrap(true);
        expressionPreview.setWrapStyleWord(true);
        expressionPreview.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        expressionPreview.setBackground(new Color(240, 240, 240));
        expressionPreview.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("表达式预览"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部：说明和预览
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JTextArea helpText = new JTextArea(2, 60);
        helpText.setText(
            "高级表达式模式：支持复杂的逻辑组合，包括括号分组。\n" +
            "使用下方按钮构建表达式树，实时预览将显示最终的逻辑表达式。"
        );
        helpText.setEditable(false);
        helpText.setBackground(topPanel.getBackground());
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        topPanel.add(helpText, BorderLayout.NORTH);
        topPanel.add(expressionPreview, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 中部：表达式树和操作按钮
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // 左侧：表达式树
        JPanel treePanel = new JPanel(new BorderLayout(5, 5));
        treePanel.setBorder(BorderFactory.createTitledBorder("表达式结构"));
        treePanel.add(new JScrollPane(expressionTree), BorderLayout.CENTER);
        
        splitPane.setLeftComponent(treePanel);
        
        // 右侧：操作面板
        JPanel operationPanel = createOperationPanel();
        splitPane.setRightComponent(operationPanel);
        
        splitPane.setDividerLocation(500);
        add(splitPane, BorderLayout.CENTER);
        
        // 底部：按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirmButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        JButton resetButton = new JButton("重置");
        JButton quickBuildButton = new JButton("快速构建");
        
        confirmButton.addActionListener(e -> confirmAction());
        cancelButton.addActionListener(e -> dispose());
        resetButton.addActionListener(e -> resetExpression());
        quickBuildButton.addActionListener(e -> quickBuild());
        
        buttonPanel.add(quickBuildButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createOperationPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("操作"));
        
        JPanel buttonsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // 添加节点按钮
        JButton addAndButton = new JButton("添加 AND 组");
        JButton addOrButton = new JButton("添加 OR 组");
        JButton addNotButton = new JButton("添加 NOT");
        JButton addConditionButton = new JButton("添加条件");
        
        addAndButton.addActionListener(e -> addLogicalNode("AND"));
        addOrButton.addActionListener(e -> addLogicalNode("OR"));
        addNotButton.addActionListener(e -> addLogicalNode("NOT"));
        addConditionButton.addActionListener(e -> addConditionNode());
        
        // 编辑/删除按钮
        JButton deleteButton = new JButton("删除选中节点");
        JButton moveUpButton = new JButton("上移");
        JButton moveDownButton = new JButton("下移");
        
        deleteButton.addActionListener(e -> deleteSelectedNode());
        moveUpButton.addActionListener(e -> moveNode(-1));
        moveDownButton.addActionListener(e -> moveNode(1));
        
        buttonsPanel.add(new JLabel("添加节点:"));
        buttonsPanel.add(addAndButton);
        buttonsPanel.add(addOrButton);
        buttonsPanel.add(addNotButton);
        buttonsPanel.add(addConditionButton);
        buttonsPanel.add(new JSeparator());
        buttonsPanel.add(new JLabel("编辑节点:"));
        buttonsPanel.add(deleteButton);
        buttonsPanel.add(moveUpButton);
        buttonsPanel.add(moveDownButton);
        
        panel.add(buttonsPanel, BorderLayout.NORTH);
        
        // 条件列表
        JPanel conditionsPanel = new JPanel(new BorderLayout(5, 5));
        conditionsPanel.setBorder(BorderFactory.createTitledBorder("可用条件"));
        
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < conditions.size(); i++) {
            Configuration.RequestCondition condition = conditions.get(i);
            listModel.addElement(String.format("条件%d: %s %s '%s'", 
                i + 1,
                condition.getConditionType(),
                condition.getMatchType(),
                condition.getValue()
            ));
        }
        
        JList<String> conditionsList = new JList<>(listModel);
        conditionsPanel.add(new JScrollPane(conditionsList), BorderLayout.CENTER);
        
        panel.add(conditionsPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void loadExpression() {
        rootNode.removeAllChildren();
        
        if (expression == null) {
            rootNode.setUserObject("表达式（空）");
        } else {
            buildTreeFromExpression(rootNode, expression);
        }
        
        treeModel.reload();
        expandAll();
        updatePreview();
    }
    
    private void buildTreeFromExpression(DefaultMutableTreeNode parent, ConditionExpression expr) {
        if (expr instanceof ConditionExpression.ConditionNode) {
            ConditionExpression.ConditionNode node = (ConditionExpression.ConditionNode) expr;
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                "条件" + (node.getConditionIndex() + 1)
            );
            treeNode.setUserObject(node);
            parent.add(treeNode);
        } else if (expr instanceof ConditionExpression.LogicalNode) {
            ConditionExpression.LogicalNode node = (ConditionExpression.LogicalNode) expr;
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                node.getOperator().getLabel() + " (" + node.getOperator().getCode() + ")"
            );
            treeNode.setUserObject(node);
            parent.add(treeNode);
            
            for (ConditionExpression child : node.getChildren()) {
                buildTreeFromExpression(treeNode, child);
            }
        }
    }
    
    private void addLogicalNode(String operator) {
        TreePath path = expressionTree.getSelectionPath();
        DefaultMutableTreeNode selectedNode = path != null ? 
            (DefaultMutableTreeNode) path.getLastPathComponent() : rootNode;
        
        ConditionExpression.LogicalNode.Operator op = 
            ConditionExpression.LogicalNode.Operator.fromCode(operator);
        ConditionExpression.LogicalNode logicalNode = new ConditionExpression.LogicalNode(op);
        
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
            op.getLabel() + " (" + op.getCode() + ")"
        );
        treeNode.setUserObject(logicalNode);
        
        selectedNode.add(treeNode);
        treeModel.reload(selectedNode);
        expandAll();
        updatePreview();
    }
    
    private void addConditionNode() {
        TreePath path = expressionTree.getSelectionPath();
        if (path == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个父节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        
        // 弹出对话框选择条件
        String[] options = new String[conditions.size()];
        for (int i = 0; i < conditions.size(); i++) {
            Configuration.RequestCondition condition = conditions.get(i);
            options[i] = String.format("条件%d: %s %s '%s'", 
                i + 1,
                condition.getConditionType(),
                condition.getMatchType(),
                condition.getValue()
            );
        }
        
        String selected = (String) JOptionPane.showInputDialog(
            this,
            "选择要添加的条件:",
            "添加条件",
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        );
        
        if (selected != null) {
            int index = java.util.Arrays.asList(options).indexOf(selected);
            ConditionExpression.ConditionNode conditionNode = 
                new ConditionExpression.ConditionNode(conditions.get(index), index);
            
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("条件" + (index + 1));
            treeNode.setUserObject(conditionNode);
            
            selectedNode.add(treeNode);
            treeModel.reload(selectedNode);
            expandAll();
            updatePreview();
        }
    }
    
    private void deleteSelectedNode() {
        TreePath path = expressionTree.getSelectionPath();
        if (path == null || path.getLastPathComponent() == rootNode) {
            JOptionPane.showMessageDialog(this, "请选择要删除的节点（不能删除根节点）", 
                "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
        
        parent.remove(selectedNode);
        treeModel.reload(parent);
        updatePreview();
    }
    
    private void moveNode(int direction) {
        TreePath path = expressionTree.getSelectionPath();
        if (path == null) {
            return;
        }
        
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
        
        if (parent == null) {
            return;
        }
        
        int index = parent.getIndex(selectedNode);
        int newIndex = index + direction;
        
        if (newIndex >= 0 && newIndex < parent.getChildCount()) {
            parent.remove(selectedNode);
            parent.insert(selectedNode, newIndex);
            treeModel.reload(parent);
            expressionTree.setSelectionPath(new TreePath(selectedNode.getPath()));
            updatePreview();
        }
    }
    
    private void resetExpression() {
        int result = JOptionPane.showConfirmDialog(this,
            "确定要重置表达式吗？这将清空所有节点。",
            "确认重置",
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            rootNode.removeAllChildren();
            rootNode.setUserObject("表达式（空）");
            treeModel.reload();
            updatePreview();
        }
    }
    
    private void quickBuild() {
        String[] options = {"全部AND", "全部OR", "(C1 OR C2) AND C3+", "取消"};
        int choice = JOptionPane.showOptionDialog(this,
            "选择快速构建模式:",
            "快速构建",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        rootNode.removeAllChildren();
        
        switch (choice) {
            case 0: // 全部AND
                buildAllAnd();
                break;
            case 1: // 全部OR
                buildAllOr();
                break;
            case 2: // (C1 OR C2) AND C3+
                buildMixedExample();
                break;
        }
        
        treeModel.reload();
        expandAll();
        updatePreview();
    }
    
    private void buildAllAnd() {
        ConditionExpression.LogicalNode andNode = new ConditionExpression.LogicalNode(
            ConditionExpression.LogicalNode.Operator.AND
        );
        
        DefaultMutableTreeNode andTreeNode = new DefaultMutableTreeNode("且 (AND)");
        andTreeNode.setUserObject(andNode);
        rootNode.add(andTreeNode);
        
        for (int i = 0; i < conditions.size(); i++) {
            ConditionExpression.ConditionNode condNode = 
                new ConditionExpression.ConditionNode(conditions.get(i), i);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("条件" + (i + 1));
            treeNode.setUserObject(condNode);
            andTreeNode.add(treeNode);
        }
    }
    
    private void buildAllOr() {
        ConditionExpression.LogicalNode orNode = new ConditionExpression.LogicalNode(
            ConditionExpression.LogicalNode.Operator.OR
        );
        
        DefaultMutableTreeNode orTreeNode = new DefaultMutableTreeNode("或 (OR)");
        orTreeNode.setUserObject(orNode);
        rootNode.add(orTreeNode);
        
        for (int i = 0; i < conditions.size(); i++) {
            ConditionExpression.ConditionNode condNode = 
                new ConditionExpression.ConditionNode(conditions.get(i), i);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("条件" + (i + 1));
            treeNode.setUserObject(condNode);
            orTreeNode.add(treeNode);
        }
    }
    
    private void buildMixedExample() {
        if (conditions.size() < 3) {
            JOptionPane.showMessageDialog(this, "需要至少3个条件", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // (C1 OR C2) AND C3 AND C4...
        ConditionExpression.LogicalNode andNode = new ConditionExpression.LogicalNode(
            ConditionExpression.LogicalNode.Operator.AND
        );
        DefaultMutableTreeNode andTreeNode = new DefaultMutableTreeNode("且 (AND)");
        andTreeNode.setUserObject(andNode);
        rootNode.add(andTreeNode);
        
        // OR组
        ConditionExpression.LogicalNode orNode = new ConditionExpression.LogicalNode(
            ConditionExpression.LogicalNode.Operator.OR
        );
        DefaultMutableTreeNode orTreeNode = new DefaultMutableTreeNode("或 (OR)");
        orTreeNode.setUserObject(orNode);
        andTreeNode.add(orTreeNode);
        
        // 添加前两个条件到OR组
        for (int i = 0; i < Math.min(2, conditions.size()); i++) {
            ConditionExpression.ConditionNode condNode = 
                new ConditionExpression.ConditionNode(conditions.get(i), i);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("条件" + (i + 1));
            treeNode.setUserObject(condNode);
            orTreeNode.add(treeNode);
        }
        
        // 剩余条件直接添加到AND组
        for (int i = 2; i < conditions.size(); i++) {
            ConditionExpression.ConditionNode condNode = 
                new ConditionExpression.ConditionNode(conditions.get(i), i);
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode("条件" + (i + 1));
            treeNode.setUserObject(condNode);
            andTreeNode.add(treeNode);
        }
    }
    
    private void updatePreview() {
        ConditionExpression expr = buildExpressionFromTree(rootNode);
        if (expr != null) {
            expressionPreview.setText(expr.toExpressionString());
        } else {
            expressionPreview.setText("（空表达式）");
        }
    }
    
    private ConditionExpression buildExpressionFromTree(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 0) {
            return null;
        }
        
        Object userObject = ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject();
        
        if (userObject instanceof ConditionExpression) {
            return (ConditionExpression) userObject;
        }
        
        return null;
    }
    
    private void expandAll() {
        for (int i = 0; i < expressionTree.getRowCount(); i++) {
            expressionTree.expandRow(i);
        }
    }
    
    private void confirmAction() {
        // TODO: 从树构建表达式对象
        confirmed = true;
        dispose();
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public ConditionExpression getExpression() {
        return expression;
    }
}


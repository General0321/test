package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 条件表达式系统 - 支持复杂的逻辑表达式和括号
 * 
 * 示例表达式：
 * - (条件1 AND 条件2) OR 条件3
 * - ((条件1 OR 条件2) AND 条件3) OR (条件4 AND 条件5)
 * - NOT (条件1 OR 条件2)
 */
public abstract class ConditionExpression implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 评估表达式
     */
    public abstract boolean evaluate(HttpRequest request);
    
    /**
     * 转换为字符串表示
     */
    public abstract String toExpressionString();
    
    /**
     * 单个条件节点
     */
    public static class ConditionNode extends ConditionExpression {
        private static final long serialVersionUID = 1L;
        private Configuration.RequestCondition condition;
        private int conditionIndex; // 在条件列表中的索引
        
        public ConditionNode(Configuration.RequestCondition condition, int index) {
            this.condition = condition;
            this.conditionIndex = index;
        }
        
        @Override
        public boolean evaluate(HttpRequest request) {
            return RequestConditionEvaluator.evaluateCondition(request, condition);
        }
        
        @Override
        public String toExpressionString() {
            return "条件" + (conditionIndex + 1);
        }
        
        public Configuration.RequestCondition getCondition() {
            return condition;
        }
        
        public int getConditionIndex() {
            return conditionIndex;
        }
    }
    
    /**
     * 逻辑操作符节点
     */
    public static class LogicalNode extends ConditionExpression {
        private static final long serialVersionUID = 1L;
        
        public enum Operator {
            AND("AND", "且"),
            OR("OR", "或"),
            NOT("NOT", "非");
            
            private final String code;
            private final String label;
            
            Operator(String code, String label) {
                this.code = code;
                this.label = label;
            }
            
            public String getCode() { return code; }
            public String getLabel() { return label; }
            
            public static Operator fromCode(String code) {
                for (Operator op : values()) {
                    if (op.code.equalsIgnoreCase(code)) {
                        return op;
                    }
                }
                return AND; // 默认
            }
        }
        
        private Operator operator;
        private List<ConditionExpression> children;
        
        public LogicalNode(Operator operator) {
            this.operator = operator;
            this.children = new ArrayList<>();
        }
        
        public void addChild(ConditionExpression child) {
            children.add(child);
        }
        
        public void addChildren(List<ConditionExpression> children) {
            this.children.addAll(children);
        }
        
        @Override
        public boolean evaluate(HttpRequest request) {
            if (children.isEmpty()) {
                return true;
            }
            
            switch (operator) {
                case AND:
                    for (ConditionExpression child : children) {
                        if (!child.evaluate(request)) {
                            return false;
                        }
                    }
                    return true;
                    
                case OR:
                    for (ConditionExpression child : children) {
                        if (child.evaluate(request)) {
                            return true;
                        }
                    }
                    return false;
                    
                case NOT:
                    if (children.isEmpty()) {
                        return true;
                    }
                    return !children.get(0).evaluate(request);
                    
                default:
                    return true;
            }
        }
        
        @Override
        public String toExpressionString() {
            if (children.isEmpty()) {
                return "";
            }
            
            if (operator == Operator.NOT) {
                return "NOT (" + children.get(0).toExpressionString() + ")";
            }
            
            StringBuilder sb = new StringBuilder();
            String opStr = " " + operator.getCode() + " ";
            
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sb.append(opStr);
                }
                
                ConditionExpression child = children.get(i);
                // 如果子节点也是逻辑节点，加括号
                if (child instanceof LogicalNode) {
                    sb.append("(").append(child.toExpressionString()).append(")");
                } else {
                    sb.append(child.toExpressionString());
                }
            }
            
            return sb.toString();
        }
        
        public Operator getOperator() {
            return operator;
        }
        
        public List<ConditionExpression> getChildren() {
            return children;
        }
    }
    
    /**
     * 表达式构建器
     */
    public static class Builder {
        /**
         * 从条件列表创建简单的AND链式表达式
         */
        public static ConditionExpression fromSimpleConditions(
                List<Configuration.RequestCondition> conditions) {
            if (conditions == null || conditions.isEmpty()) {
                return null;
            }
            
            if (conditions.size() == 1) {
                return new ConditionNode(conditions.get(0), 0);
            }
            
            // 构建链式表达式
            LogicalNode root = null;
            LogicalNode current = null;
            
            for (int i = 0; i < conditions.size(); i++) {
                Configuration.RequestCondition condition = conditions.get(i);
                ConditionNode node = new ConditionNode(condition, i);
                
                if (i == 0) {
                    // 第一个条件
                    if (conditions.size() > 1) {
                        String nextOp = conditions.get(1).getOperator();
                        LogicalNode.Operator op = LogicalNode.Operator.fromCode(
                            nextOp != null ? nextOp : "AND"
                        );
                        root = new LogicalNode(op);
                        root.addChild(node);
                        current = root;
                    } else {
                        return node;
                    }
                } else {
                    current.addChild(node);
                    
                    // 如果下一个条件的操作符不同，需要创建新的逻辑节点
                    if (i < conditions.size() - 1) {
                        String nextOp = conditions.get(i + 1).getOperator();
                        if (nextOp != null && !nextOp.equalsIgnoreCase(current.getOperator().getCode())) {
                            // 操作符改变，需要重新组织结构
                            // 这里简化处理：优先级 AND > OR
                            // 实际应用中可能需要更复杂的解析
                        }
                    }
                }
            }
            
            return root;
        }
        
        /**
         * 创建AND表达式
         */
        public static LogicalNode and(ConditionExpression... expressions) {
            LogicalNode node = new LogicalNode(LogicalNode.Operator.AND);
            for (ConditionExpression expr : expressions) {
                if (expr != null) {
                    node.addChild(expr);
                }
            }
            return node;
        }
        
        /**
         * 创建OR表达式
         */
        public static LogicalNode or(ConditionExpression... expressions) {
            LogicalNode node = new LogicalNode(LogicalNode.Operator.OR);
            for (ConditionExpression expr : expressions) {
                if (expr != null) {
                    node.addChild(expr);
                }
            }
            return node;
        }
        
        /**
         * 创建NOT表达式
         */
        public static LogicalNode not(ConditionExpression expression) {
            LogicalNode node = new LogicalNode(LogicalNode.Operator.NOT);
            if (expression != null) {
                node.addChild(expression);
            }
            return node;
        }
    }
}


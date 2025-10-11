// 完整测试evaluate流程
// 编译: javac -encoding UTF-8 TEST_FULL_EVALUATE.java
// 运行: java TEST_FULL_EVALUATE

import java.util.*;

public class TEST_FULL_EVALUATE {
    
    enum MatchType {
        NOT_CONTAINS,
        CONTAINS
    }
    
    enum ElementType {
        RESPONSE_BODY
    }
    
    static class MatchConfig {
        MatchType matchType;
        List<String> values;
        boolean caseSensitive;
        
        MatchConfig(MatchType matchType, List<String> values, boolean caseSensitive) {
            this.matchType = matchType;
            this.values = values;
            this.caseSensitive = caseSensitive;
        }
        
        public MatchType getMatchType() { return matchType; }
        public List<String> getValues() { return values; }
        public boolean isCaseSensitive() { return caseSensitive; }
    }
    
    static class ResponseElementConfig {
        int id;
        ElementType type;
        MatchConfig matchConfig;
        
        ResponseElementConfig(int id, ElementType type, MatchConfig matchConfig) {
            this.id = id;
            this.type = type;
            this.matchConfig = matchConfig;
        }
        
        public int getId() { return id; }
        public ElementType getType() { return type; }
        public MatchConfig getMatchConfig() { return matchConfig; }
    }
    
    static class UnifiedResponseConfig {
        List<ResponseElementConfig> elements = new ArrayList<>();
        String conditionExpression = "";
        
        public void addElement(ResponseElementConfig element) {
            elements.add(element);
        }
        
        public List<ResponseElementConfig> getElements() { return elements; }
        public String getConditionExpression() { return conditionExpression; }
    }
    
    // 模拟evaluate方法
    private static boolean evaluate(String responseBody, UnifiedResponseConfig config) {
        System.out.println("\n========== evaluate() 开始 ==========");
        
        if (config == null || config.getElements() == null || config.getElements().isEmpty()) {
            System.out.println("配置为空，返回false");
            return false;
        }
        
        // 评估每个元素
        Map<Integer, Boolean> elementResults = new HashMap<>();
        for (ResponseElementConfig element : config.getElements()) {
            System.out.println("\n评估元素ID: " + element.getId());
            boolean result = evaluateElement(responseBody, element);
            elementResults.put(element.getId(), result);
            System.out.println("元素 " + element.getId() + " 评估结果: " + result);
        }
        
        // 根据表达式评估最终结果
        String expression = config.getConditionExpression();
        if (expression == null || expression.trim().isEmpty()) {
            // 默认：所有元素都需满足（AND关系）
            boolean finalResult = elementResults.values().stream().allMatch(b -> b);
            System.out.println("\n没有表达式，使用AND关系");
            System.out.println("所有元素结果: " + elementResults.values());
            System.out.println("最终结果 (所有都true才true): " + finalResult);
            System.out.println("\n========== evaluate() 返回: " + finalResult + " ==========");
            return finalResult;
        }
        
        return false;
    }
    
    // 模拟evaluateElement方法
    private static boolean evaluateElement(String responseBody, ResponseElementConfig element) {
        System.out.println("  evaluateElement() 开始");
        
        if (element == null || element.getMatchConfig() == null) {
            System.out.println("  元素或配置为空，返回false");
            return false;
        }
        
        ElementType type = element.getType();
        MatchConfig matchConfig = element.getMatchConfig();
        
        if (type == ElementType.RESPONSE_BODY) {
            boolean result = evaluateBody(responseBody, matchConfig);
            System.out.println("  evaluateElement() 返回: " + result);
            return result;
        }
        
        return false;
    }
    
    // 模拟evaluateBody方法
    private static boolean evaluateBody(String body, MatchConfig config) {
        System.out.println("    evaluateBody() 开始");
        System.out.println("    响应体: " + body);
        
        if (body == null) {
            body = "";
        }
        
        boolean result = matchTextValues(body, config);
        System.out.println("    evaluateBody() 返回: " + result);
        return result;
    }
    
    // 模拟matchTextValues方法
    private static boolean matchTextValues(String actual, MatchConfig config) {
        System.out.println("      matchTextValues() 开始");
        
        if (actual == null) {
            actual = "";
        }
        
        List<String> values = config.getValues();
        if (values == null || values.isEmpty()) {
            System.out.println("      值列表为空，返回false");
            return false;
        }
        
        MatchType matchType = config.getMatchType();
        boolean caseSensitive = config.isCaseSensitive();
        
        System.out.println("      匹配类型: " + matchType);
        System.out.println("      配置值: " + values);
        
        boolean isNegativeMatch = (matchType == MatchType.NOT_CONTAINS);
        
        if (isNegativeMatch) {
            System.out.println("      使用反向匹配（AND逻辑）");
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                boolean matched = matchSingleValue(actual, value, MatchType.CONTAINS, caseSensitive);
                System.out.println("      检查 \"" + value + "\": " + (matched ? "包含" : "不包含"));
                
                if (matched) {
                    System.out.println("      → 找到匹配，返回false");
                    System.out.println("      matchTextValues() 返回: false");
                    return false;
                }
            }
            System.out.println("      → 所有值都不匹配，返回true");
            System.out.println("      matchTextValues() 返回: true");
            return true;
            
        } else {
            System.out.println("      使用正向匹配（OR逻辑）");
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                boolean result = matchSingleValue(actual, value, matchType, caseSensitive);
                if (result) {
                    System.out.println("      → 找到匹配，返回true");
                    System.out.println("      matchTextValues() 返回: true");
                    return true;
                }
            }
            System.out.println("      → 没有匹配，返回false");
            System.out.println("      matchTextValues() 返回: false");
            return false;
        }
    }
    
    private static boolean matchSingleValue(String actual, String expected, MatchType matchType, boolean caseSensitive) {
        if (!caseSensitive) {
            actual = actual.toLowerCase();
            expected = expected.toLowerCase();
        }
        
        if (matchType == MatchType.CONTAINS) {
            return actual.contains(expected);
        }
        return false;
    }
    
    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("完整evaluate流程测试");
        System.out.println("============================================");
        
        // 实际响应体
        String responseBody = "{\"code\":\"cms-adaption#500\",\"bizCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"不允许访问\",\"data\":null,\"errorType\":\"SYSTEM_EXCEPTION\"}";
        
        System.out.println("\n响应体内容:");
        System.out.println(responseBody);
        
        // 配置
        MatchConfig matchConfig = new MatchConfig(
            MatchType.NOT_CONTAINS,
            Arrays.asList("不允许访问"),
            false
        );
        
        ResponseElementConfig element = new ResponseElementConfig(
            1,
            ElementType.RESPONSE_BODY,
            matchConfig
        );
        
        UnifiedResponseConfig responseConfig = new UnifiedResponseConfig();
        responseConfig.addElement(element);
        
        System.out.println("\n配置:");
        System.out.println("响应体: NOT_CONTAINS [\"不允许访问\"]");
        System.out.println("区分大小写: false");
        
        // 执行评估
        System.out.println("\n============================================");
        System.out.println("执行评估");
        System.out.println("============================================");
        
        boolean result = evaluate(responseBody, responseConfig);
        
        System.out.println("\n============================================");
        System.out.println("最终判断");
        System.out.println("============================================");
        System.out.println("evaluate() 最终返回: " + result);
        System.out.println("\n在UniversalScanner中:");
        System.out.println("  boolean responseMatched = evaluate(...); // = " + result);
        System.out.println("  if (responseMatched) {  // = " + (result ? "true，进入if" : "false，不进入if"));
        System.out.println("      // 配对成功，规则命中");
        System.out.println("  }");
        System.out.println("\n结论: " + (result ? "❌ 规则会命中（不正确）" : "✅ 规则不会命中（正确）"));
        
        System.out.println("\n============================================");
        System.out.println("预期行为说明");
        System.out.println("============================================");
        System.out.println("配置: NOT_CONTAINS [\"不允许访问\"]");
        System.out.println("响应: 包含\"不允许访问\"");
        System.out.println("预期: 不应该告警（规则不命中）");
        System.out.println("实际: " + (result ? "会告警❌" : "不会告警✅"));
    }
}


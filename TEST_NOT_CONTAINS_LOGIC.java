// 测试NOT_CONTAINS逻辑
// 编译: javac TEST_NOT_CONTAINS_LOGIC.java
// 运行: java TEST_NOT_CONTAINS_LOGIC

import java.util.*;

public class TEST_NOT_CONTAINS_LOGIC {
    
    enum MatchType {
        NOT_CONTAINS,
        CONTAINS,
        EQUALS
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
    
    // 复制自UnifiedResponseEvaluator的逻辑
    private static boolean matchTextValues(String actual, MatchConfig config) {
        if (actual == null) {
            actual = "";
        }
        
        List<String> values = config.getValues();
        if (values == null || values.isEmpty()) {
            return false;
        }
        
        MatchType matchType = config.getMatchType();
        boolean caseSensitive = config.isCaseSensitive();
        
        // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
        boolean isNegativeMatch = (matchType == MatchType.NOT_CONTAINS);
        
        if (isNegativeMatch) {
            // ✅ 反向匹配：所有值都不匹配才返回true（AND逻辑）
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                boolean matched = matchSingleValue(actual, value, MatchType.CONTAINS, caseSensitive);
                
                // 如果找到一个匹配的，说明不满足"都不匹配"的条件
                if (matched) {
                    System.out.println("  [反向匹配] 找到匹配: actual包含'" + value + "' → 返回false");
                    return false;
                }
            }
            // 所有值都不匹配，返回true
            System.out.println("  [反向匹配] 所有值都不匹配 → 返回true");
            return true;
            
        } else {
            // ✅ 正向匹配：任意一个匹配就返回true（OR逻辑）
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                
                boolean result = matchSingleValue(actual, value, matchType, caseSensitive);
                if (result) {
                    System.out.println("  [正向匹配] 找到匹配: '" + value + "' → 返回true");
                    return true;
                }
            }
            
            System.out.println("  [正向匹配] 没有匹配 → 返回false");
            return false;
        }
    }
    
    private static boolean matchSingleValue(String actual, String expected, MatchType matchType, boolean caseSensitive) {
        if (!caseSensitive) {
            actual = actual.toLowerCase();
            expected = expected.toLowerCase();
        }
        
        switch (matchType) {
            case EQUALS:
                return actual.equals(expected);
            case CONTAINS:
                return actual.contains(expected);
            default:
                return false;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("测试NOT_CONTAINS逻辑");
        System.out.println("============================================\n");
        
        // 测试1：响应体不包含"error"（单个值）
        System.out.println("【测试1】NOT_CONTAINS [\"error\"]");
        MatchConfig config1 = new MatchConfig(MatchType.NOT_CONTAINS, Arrays.asList("error"), false);
        
        System.out.println("\n测试1.1: actual=\"success\"");
        boolean result1_1 = matchTextValues("success", config1);
        System.out.println("结果: " + result1_1 + " (预期: true)\n");
        
        System.out.println("测试1.2: actual=\"error occurred\"");
        boolean result1_2 = matchTextValues("error occurred", config1);
        System.out.println("结果: " + result1_2 + " (预期: false)\n");
        
        System.out.println("测试1.3: actual=\"Error Occurred\" (大小写不敏感)");
        boolean result1_3 = matchTextValues("Error Occurred", config1);
        System.out.println("结果: " + result1_3 + " (预期: false)\n");
        
        // 测试2：响应体不包含"error"或"warning"（多个值）
        System.out.println("\n【测试2】NOT_CONTAINS [\"error\", \"warning\"]");
        MatchConfig config2 = new MatchConfig(MatchType.NOT_CONTAINS, Arrays.asList("error", "warning"), false);
        
        System.out.println("\n测试2.1: actual=\"success\"");
        boolean result2_1 = matchTextValues("success", config2);
        System.out.println("结果: " + result2_1 + " (预期: true)\n");
        
        System.out.println("测试2.2: actual=\"error occurred\"");
        boolean result2_2 = matchTextValues("error occurred", config2);
        System.out.println("结果: " + result2_2 + " (预期: false)\n");
        
        System.out.println("测试2.3: actual=\"warning found\"");
        boolean result2_3 = matchTextValues("warning found", config2);
        System.out.println("结果: " + result2_3 + " (预期: false)\n");
        
        System.out.println("测试2.4: actual=\"error and warning\"");
        boolean result2_4 = matchTextValues("error and warning", config2);
        System.out.println("结果: " + result2_4 + " (预期: false)\n");
        
        // 测试3：大小写敏感
        System.out.println("\n【测试3】NOT_CONTAINS [\"Error\"] (大小写敏感)");
        MatchConfig config3 = new MatchConfig(MatchType.NOT_CONTAINS, Arrays.asList("Error"), true);
        
        System.out.println("\n测试3.1: actual=\"error occurred\"");
        boolean result3_1 = matchTextValues("error occurred", config3);
        System.out.println("结果: " + result3_1 + " (预期: true - 小写error不匹配大写Error)\n");
        
        System.out.println("测试3.2: actual=\"Error occurred\"");
        boolean result3_2 = matchTextValues("Error occurred", config3);
        System.out.println("结果: " + result3_2 + " (预期: false - 大写Error匹配)\n");
        
        // 总结
        System.out.println("\n============================================");
        System.out.println("测试总结");
        System.out.println("============================================");
        System.out.println("测试1.1: " + (result1_1 == true ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试1.2: " + (result1_2 == false ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试1.3: " + (result1_3 == false ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试2.1: " + (result2_1 == true ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试2.2: " + (result2_2 == false ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试2.3: " + (result2_3 == false ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试2.4: " + (result2_4 == false ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试3.1: " + (result3_1 == true ? "✅ 通过" : "❌ 失败"));
        System.out.println("测试3.2: " + (result3_2 == false ? "✅ 通过" : "❌ 失败"));
        
        boolean allPassed = result1_1 == true && result1_2 == false && result1_3 == false &&
                           result2_1 == true && result2_2 == false && result2_3 == false && result2_4 == false &&
                           result3_1 == true && result3_2 == false;
        
        System.out.println("\n" + (allPassed ? "🎉 所有测试通过！" : "❌ 有测试失败！"));
    }
}


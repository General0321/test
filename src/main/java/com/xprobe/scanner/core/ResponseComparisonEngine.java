package com.xprobe.scanner.core;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.ResponseComparisonConfig;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 响应特征对比引擎
 * 支持多个数据包的响应特征提取和对比
 * 
 * 用于高级检测模式：
 * - BOOLEAN_COMPARISON: 对比TRUE/FALSE响应差异
 * - TIME_BASED_VERIFICATION: 验证时间延迟关系
 * - REFLECTION_CONFIRMATION: 确认Payload反射
 * 
 * ✨ 新增：支持跨Pair特征引用
 * - 可在后续Pair中引用前面Pair的响应时间、长度、状态码等特征
 * - 支持相对时间比较（如：2倍于Pair1的响应时间）
 */
public class ResponseComparisonEngine {
    
    /**
     * 对比两个响应是否存在显著差异（用于布尔盲注）
     * 
     * @param response1 响应1（通常是TRUE payload）
     * @param response2 响应2（通常是FALSE payload）
     * @param config 对比配置
     * @return true表示存在显著差异，false表示相似
     */
    public static boolean hasSignificantDifference(HttpResponse response1, 
                                                    HttpResponse response2,
                                                    ResponseComparisonConfig config) {
        if (response1 == null || response2 == null || config == null) {
            return false;
        }
        
        boolean hasDifference = false;
        
        // 1. 对比状态码
        if (config.isCompareStatusCode()) {
            if (response1.statusCode() != response2.statusCode()) {
                hasDifference = true;
            }
        }
        
        // 2. 对比响应长度
        if (config.isCompareLength()) {
            int len1 = response1.body() != null ? response1.body().length() : 0;
            int len2 = response2.body() != null ? response2.body().length() : 0;
            int diff = Math.abs(len1 - len2);
            
            if (diff > config.getLengthDifferenceThreshold()) {
                hasDifference = true;
            }
        }
        
        // 3. 对比响应内容
        if (config.isCompareContent()) {
            String content1 = cleanDynamicContent(response1.bodyToString(), config);
            String content2 = cleanDynamicContent(response2.bodyToString(), config);
            
            // 检查关键内容差异模式
            if (!config.getContentDifferencePatterns().isEmpty()) {
                for (String pattern : config.getContentDifferencePatterns()) {
                    boolean matches1 = Pattern.compile(pattern).matcher(content1).find();
                    boolean matches2 = Pattern.compile(pattern).matcher(content2).find();
                    
                    // 一个匹配一个不匹配 = 差异
                    if (matches1 != matches2) {
                        hasDifference = true;
                        break;
                    }
                }
            }
            
            // 计算内容相似度
            double similarity = calculateSimilarity(content1, content2);
            if (similarity < config.getContentSimilarityThreshold()) {
                hasDifference = true;
            }
        }
        
        return hasDifference;
    }
    
    /**
     * 验证时间延迟是否符合预期（用于时间盲注）
     * 
     * @param actualTime 实际响应时间（毫秒）
     * @param expectedTime 期望响应时间（毫秒）
     * @param tolerancePercent 允许的误差百分比（0-100）
     * @return true表示符合预期，false表示不符合
     */
    public static boolean verifyTimeDelay(long actualTime, 
                                          long expectedTime,
                                          int tolerancePercent) {
        if (expectedTime <= 0) {
            return false;
        }
        
        // 计算允许的误差范围
        long tolerance = (expectedTime * tolerancePercent) / 100;
        long minAcceptable = expectedTime - tolerance;
        long maxAcceptable = expectedTime + tolerance;
        
        return actualTime >= minAcceptable && actualTime <= maxAcceptable;
    }
    
    /**
     * 验证多个时间延迟是否呈线性关系（用于高级时间盲注）
     * 
     * @param times 时间列表（毫秒）
     * @param baselines 期望基线列表（毫秒）
     * @param tolerancePercent 允许的误差百分比
     * @return true表示符合线性关系，false表示不符合
     */
    public static boolean verifyLinearTimeProgression(List<Long> times,
                                                      List<Long> baselines,
                                                      int tolerancePercent) {
        if (times == null || baselines == null || 
            times.size() != baselines.size() || times.size() < 2) {
            return false;
        }
        
        // 验证每个时间点是否在误差范围内
        for (int i = 0; i < times.size(); i++) {
            if (!verifyTimeDelay(times.get(i), baselines.get(i), tolerancePercent)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查响应中是否包含反射的Payload标记（用于XSS等）
     * 
     * @param response HTTP响应
     * @param marker 唯一标记
     * @return true表示存在反射，false表示不存在
     */
    public static boolean hasReflection(HttpResponse response, String marker) {
        if (response == null || marker == null || marker.isEmpty()) {
            return false;
        }
        
        String body = response.bodyToString();
        return body != null && body.contains(marker);
    }
    
    /**
     * 检查反射的Payload是否处于可利用的上下文（用于XSS验证）
     * 
     * @param response HTTP响应
     * @param marker 唯一标记
     * @param contextPatterns 危险上下文模式列表
     * @return true表示在可利用上下文中，false表示不在
     */
    public static boolean hasExploitableReflection(HttpResponse response,
                                                    String marker,
                                                    List<String> contextPatterns) {
        if (response == null || marker == null || 
            contextPatterns == null || contextPatterns.isEmpty()) {
            return false;
        }
        
        String body = response.bodyToString();
        if (body == null) {
            return false;
        }
        
        // 检查是否匹配任一危险上下文模式
        for (String patternTemplate : contextPatterns) {
            String pattern = patternTemplate.replace("{MARKER}", Pattern.quote(marker));
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(body).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 清理响应中的动态内容（用于内容对比）
     * 
     * @param content 原始内容
     * @param config 配置
     * @return 清理后的内容
     */
    private static String cleanDynamicContent(String content, ResponseComparisonConfig config) {
        if (content == null) {
            return "";
        }
        
        String cleaned = content;
        
        // 移除所有动态内容模式
        for (String pattern : config.getIgnoreDynamicPatterns()) {
            cleaned = cleaned.replaceAll(pattern, "[DYNAMIC]");
        }
        
        return cleaned;
    }
    
    /**
     * 计算两个字符串的相似度（简化版 Levenshtein距离）
     * 
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度（0.0-1.0）
     */
    private static double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        // 简化计算：基于长度和共同字符数
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        
        int minLen = Math.min(s1.length(), s2.length());
        int commonChars = 0;
        
        // 计算共同字符数（简化版）
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                commonChars++;
            }
        }
        
        return (double) commonChars / maxLen;
    }
    
    /**
     * 提取响应的特征摘要（用于调试）
     * 
     * @param response HTTP响应
     * @return 特征摘要字符串
     */
    public static String extractFeatureSummary(HttpResponse response) {
        if (response == null) {
            return "null";
        }
        
        return String.format("Status=%d, Length=%d, Headers=%d", 
            response.statusCode(),
            response.body() != null ? response.body().length() : 0,
            response.headers().size()
        );
    }
    
    // ========== 跨Pair特征引用相关方法（新增）==========
    
    /**
     * 验证时间延迟是否符合预期（支持引用前面Pair的时间）
     * 
     * @param actualTime 当前实际响应时间
     * @param config 对比配置
     * @param referenceFeatures 引用的Pair的响应特征
     * @return true表示符合预期
     */
    public static boolean verifyTimeDelayWithReference(long actualTime,
                                                        ResponseComparisonConfig config,
                                                        PairResponseFeatures referenceFeatures) {
        if (config == null) {
            return false;
        }
        
        // 根据时间比较模式确定期望时间
        if (config.getTimeComparisonMode() == ResponseComparisonConfig.TimeComparisonMode.RELATIVE_TO_PAIR) {
            // 相对模式：基于引用Pair的时间 × 倍数（支持范围）
            if (referenceFeatures == null) {
                return false;
            }
            long referenceTime = referenceFeatures.getResponseTime();
            
            // ✅ 支持倍数范围模式
            Double minMultiplier = config.getRelativeTimeMultiplierMin();
            Double maxMultiplier = config.getRelativeTimeMultiplierMax();
            
            if (minMultiplier != null && maxMultiplier != null) {
                // ✅ 验证：确保倍数范围有效
                if (minMultiplier <= 0 || maxMultiplier <= 0 || minMultiplier >= maxMultiplier) {
                    return false;
                }
                
                // 范围模式：检查实际时间是否在 [referenceTime * minMultiplier, referenceTime * maxMultiplier] 范围内
                long minExpectedTime = (long) (referenceTime * minMultiplier);
                long maxExpectedTime = (long) (referenceTime * maxMultiplier);
                
                // ✅ 验证：确保期望时间有效
                if (minExpectedTime <= 0 || maxExpectedTime <= 0 || minExpectedTime >= maxExpectedTime) {
                    return false;
                }
                
                // 应用容差百分比
                int tolerancePercent = config.getTimeTolerancePercent();
                // 对最小和最大期望时间分别应用容差
                long minTolerance = (minExpectedTime * tolerancePercent) / 100;
                long maxTolerance = (maxExpectedTime * tolerancePercent) / 100;
                
                // ✅ 修复：确保容差不会导致minAcceptable小于0
                long minAcceptable = Math.max(0, minExpectedTime - minTolerance);
                long maxAcceptable = maxExpectedTime + maxTolerance;
                
                boolean inRange = actualTime >= minAcceptable && actualTime <= maxAcceptable;
                
                return inRange;
            } else {
                // 单倍数模式：使用relativeTimeMultiplier（向后兼容）
                double multiplier = config.getRelativeTimeMultiplier() != null ? 
                                   config.getRelativeTimeMultiplier() : 1.0;
                long expectedTime = (long) (referenceTime * multiplier);
                
                // 使用标准时间验证逻辑
                return verifyTimeDelay(actualTime, expectedTime, config.getTimeTolerancePercent());
            }
            
        } else {
            // 绝对模式：使用timeBaseline
            long expectedTime = config.getTimeBaseline() != null ? config.getTimeBaseline() : 0;
            
            // 使用标准时间验证逻辑
            return verifyTimeDelay(actualTime, expectedTime, config.getTimeTolerancePercent());
        }
    }
    
    /**
     * 对比当前响应与引用Pair的响应是否存在显著差异
     * 
     * @param currentResponse 当前响应
     * @param referenceFeatures 引用的Pair的响应特征
     * @param config 对比配置
     * @return true表示存在显著差异
     */
    public static boolean hasSignificantDifferenceWithReference(HttpResponse currentResponse,
                                                                 PairResponseFeatures referenceFeatures,
                                                                 ResponseComparisonConfig config) {
        if (currentResponse == null || referenceFeatures == null || config == null) {
            return false;
        }
        
        HttpResponse referenceResponse = referenceFeatures.getResponse();
        if (referenceResponse == null) {
            return false;
        }
        
        // 使用标准差异对比逻辑
        return hasSignificantDifference(currentResponse, referenceResponse, config);
    }
    
    /**
     * 检查响应特征是否满足条件（支持数值比较）
     * 
     * @param currentValue 当前值
     * @param operator 比较操作符（EQUALS, GREATER_THAN, LESS_THAN等）
     * @param referenceValue 参考值
     * @return true表示满足条件
     */
    public static boolean checkFeatureCondition(long currentValue, 
                                                String operator,
                                                long referenceValue) {
        if (operator == null) {
            return false;
        }
        
        switch (operator.toUpperCase()) {
            case "EQUALS":
            case "EQ":
                return currentValue == referenceValue;
                
            case "GREATER_THAN":
            case "GT":
                return currentValue > referenceValue;
                
            case "GREATER_THAN_OR_EQUALS":
            case "GTE":
                return currentValue >= referenceValue;
                
            case "LESS_THAN":
            case "LT":
                return currentValue < referenceValue;
                
            case "LESS_THAN_OR_EQUALS":
            case "LTE":
                return currentValue <= referenceValue;
                
            case "NOT_EQUALS":
            case "NEQ":
                return currentValue != referenceValue;
                
            default:
                return false;
        }
    }
    
    /**
     * 从特征映射中解析引用表达式
     * 支持格式：{{PAIR:1:responseTime}}
     * 
     * @param expression 表达式
     * @param featuresMap Pair特征映射
     * @return 解析后的值，如果无法解析则返回null
     */
    public static String resolveFeatureReference(String expression,
                                                  java.util.Map<Integer, PairResponseFeatures> featuresMap) {
        if (expression == null || !expression.contains("{{PAIR:")) {
            return expression;
        }
        
        // 提取 {{PAIR:pairId:featureName}}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{PAIR:(\\d+):([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(expression);
        
        String result = expression;
        while (matcher.find()) {
            int pairId = Integer.parseInt(matcher.group(1));
            String featureName = matcher.group(2);
            
            PairResponseFeatures features = featuresMap.get(pairId);
            if (features != null) {
                String value = features.getFeatureValue(featureName);
                if (value != null) {
                    result = result.replace(matcher.group(0), value);
                }
            }
        }
        
        return result;
    }
    
    // ========== 响应体内容对比方法（新增）==========
    
    /**
     * 对比当前Pair的响应体与引用Pair的响应体
     * 
     * @param currentFeatures 当前Pair的响应特征
     * @param referenceFeatures 引用Pair的响应特征
     * @param config 对比配置
     * @return true表示满足对比条件
     */
    public static boolean compareResponseBody(PairResponseFeatures currentFeatures,
                                              PairResponseFeatures referenceFeatures,
                                              ResponseComparisonConfig config) {
        if (currentFeatures == null || referenceFeatures == null || config == null) {
            return false;
        }
        
        ResponseComparisonConfig.BodyComparisonMode mode = config.getBodyComparisonMode();
        if (mode == null || mode == ResponseComparisonConfig.BodyComparisonMode.NONE) {
            return true; // 不需要对比
        }
        
        boolean useClean = config.isUseCleanedBodyComparison();
        
        switch (mode) {
            case BODY_EQUALS:
                return isBodyEqual(currentFeatures, referenceFeatures, useClean);
                
            case BODY_NOT_EQUALS:
                return !isBodyEqual(currentFeatures, referenceFeatures, useClean);
                
            case BODY_SIMILAR:
                return isBodySimilar(currentFeatures, referenceFeatures, 
                                    config.getContentSimilarityThreshold(), useClean);
                
            case BODY_NOT_SIMILAR:
                return !isBodySimilar(currentFeatures, referenceFeatures, 
                                     config.getContentSimilarityThreshold(), useClean);
                
            default:
                return false;
        }
    }
    
    /**
     * 检查两个Pair的响应体是否相似
     * 
     * @param features1 Pair1的响应特征
     * @param features2 Pair2的响应特征
     * @param threshold 相似度阈值（0.0-1.0）
     * @param useClean 是否使用清理后的内容
     * @return true表示相似
     */
    private static boolean isBodySimilar(PairResponseFeatures features1,
                                        PairResponseFeatures features2,
                                        double threshold,
                                        boolean useClean) {
        if (features1 == null || features2 == null) {
            return false;
        }
        
        String content1 = useClean ? features1.getResponseBodyClean() : features1.getResponseBodyContent();
        String content2 = useClean ? features2.getResponseBodyClean() : features2.getResponseBodyContent();
        
        if (content1 == null || content2 == null) {
            // 如果没有完整内容，退回到哈希对比
            return isBodyEqual(features1, features2, false);
        }
        
        double similarity = calculateSimilarity(content1, content2);
        return similarity >= threshold;
    }
    
    /**
     * ✨ 静态方法：检查两个Pair的响应体是否相等
     * 供UniversalScanner直接调用
     * 
     * @param features1 Pair1的响应特征
     * @param features2 Pair2的响应特征
     * @param useClean 是否使用清理后的内容（忽略动态内容）
     * @return true表示相等
     */
    public static boolean isBodyEqual(PairResponseFeatures features1,
                                     PairResponseFeatures features2,
                                     boolean useClean) {
        if (features1 == null || features2 == null) {
            return false;
        }
        
        if (useClean) {
            // 使用清理后的内容对比（忽略时间戳、token等动态内容）
            String clean1 = features1.getResponseBodyClean();
            String clean2 = features2.getResponseBodyClean();
            
            // ✅ 降级策略：如果没有清理后的内容，使用哈希对比
            if (clean1 == null || clean2 == null) {
                return isBodyEqual(features1, features2, false);
            }
            
            return clean1.equals(clean2);
        } else {
            // 使用MD5哈希快速对比（精确匹配）
            String hash1 = features1.getResponseBodyHash();
            String hash2 = features2.getResponseBodyHash();
            
            if (hash1 == null && hash2 == null) return true;
            if (hash1 == null || hash2 == null) return false;
            
            return hash1.equals(hash2);
        }
    }
    
    /**
     * ✨ 静态方法：评估通用跨Pair特征引用
     * 供UniversalScanner直接调用
     * 
     * @param currentFeatures 当前Pair的响应特征
     * @param refFeatures 引用Pair的响应特征
     * @param config 响应对比配置
     * @return true表示对比通过
     */
    public static boolean evaluateCrossPairFeature(PairResponseFeatures currentFeatures,
                                                   PairResponseFeatures refFeatures,
                                                   ResponseComparisonConfig config) {
        if (currentFeatures == null || refFeatures == null || config == null) {
            return false;
        }
        
        String featureType = config.getReferenceFeatureType();
        String operator = config.getReferenceOperator();
        String explicitValue = config.getReferenceValue();
        
        if (featureType == null || operator == null) {
            return false;
        }
        
        Object currentValue = null;
        Object refValue = null;
        
        // 提取特征值
        switch (featureType.toUpperCase()) {
            case "STATUS_CODE":
                currentValue = currentFeatures.getStatusCode();
                refValue = refFeatures.getStatusCode();
                break;
            case "RESPONSE_LENGTH":
                currentValue = (long) currentFeatures.getResponseLength();
                refValue = (long) refFeatures.getResponseLength();
                break;
            case "RESPONSE_TIME":
                currentValue = currentFeatures.getResponseTime();
                refValue = refFeatures.getResponseTime();
                break;
            case "RESPONSE_BODY":
                // 使用清理后的内容或哈希
                boolean useClean = config.isUseCleanedBodyComparison();
                if (useClean) {
                    currentValue = currentFeatures.getResponseBodyClean();
                    refValue = refFeatures.getResponseBodyClean();
                } else {
                    currentValue = currentFeatures.getResponseBodyHash();
                    refValue = refFeatures.getResponseBodyHash();
                }
                break;
            default:
                return false;
        }
        
        if (currentValue == null || refValue == null) {
            return false;
        }
        
        // ✨ 特殊处理：DIFFERENCE_GREATER_THAN 操作符
        if ("DIFFERENCE_GREATER_THAN".equalsIgnoreCase(operator)) {
            // 计算两个值的差异绝对值，判断是否大于阈值
            if (currentValue instanceof Number && refValue instanceof Number) {
                long diff = Math.abs(((Number) currentValue).longValue() - ((Number) refValue).longValue());
                long threshold = config.getLengthDifferenceThreshold();
                return diff > threshold;
            }
            return false;
        }
        
        // 如果提供了explicitValue，则与explicitValue比较，否则与refValue比较
        Object compareTarget = (explicitValue != null && !explicitValue.isEmpty()) ? explicitValue : refValue;
        
        // 执行比较
        return compareValues(currentValue, compareTarget, operator);
    }
    
    /**
     * ✨ 通用值比较方法（静态版本）
     */
    private static boolean compareValues(Object val1, Object val2, String operator) {
        // ✅ 修复：添加null检查
        if (val1 == null || val2 == null) {
            // 如果任一值为null，只有NOT_EQUALS在两者都为null时返回false，其他情况返回false
            if ("NOT_EQUALS".equalsIgnoreCase(operator)) {
                return val1 != val2;  // null != null 返回false，null != non-null 返回true
            }
            return false;
        }
        
        try {
            switch (operator.toUpperCase()) {
                case "EQUALS":
                    return val1.equals(val2);
                case "NOT_EQUALS":
                    return !val1.equals(val2);
                case "GREATER_THAN":
                    if (val1 instanceof Number && val2 instanceof Number) {
                        return ((Number) val1).longValue() > ((Number) val2).longValue();
                    } else if (val2 instanceof String) {
                        // 尝试解析字符串为数字
                        try {
                            long numVal2 = Long.parseLong((String) val2);
                            return ((Number) val1).longValue() > numVal2;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                    break;
                case "LESS_THAN":
                    if (val1 instanceof Number && val2 instanceof Number) {
                        return ((Number) val1).longValue() < ((Number) val2).longValue();
                    } else if (val2 instanceof String) {
                        try {
                            long numVal2 = Long.parseLong((String) val2);
                            return ((Number) val1).longValue() < numVal2;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                    break;
                case "CONTAINS":
                    if (val1 instanceof String && val2 instanceof String) {
                        return ((String) val1).contains((String) val2);
                    }
                    break;
                case "NOT_CONTAINS":
                    if (val1 instanceof String && val2 instanceof String) {
                        return !((String) val1).contains((String) val2);
                    }
                    break;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}


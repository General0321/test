package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 响应对比配置
 * 用于BOOLEAN_COMPARISON和TIME_BASED_VERIFICATION模式
 * 
 * 支持多个数据包的响应特征提取和对比
 */
public class ResponseComparisonConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // ========== 基础对比配置 ==========
    
    /** 是否比较响应状态码 */
    private boolean compareStatusCode = true;
    
    /** 是否比较响应长度 */
    private boolean compareLength = true;
    
    /** 长度差异阈值(字节) - 超过此值认为显著不同 */
    private int lengthDifferenceThreshold = 50;
    
    /** 是否比较响应内容 */
    private boolean compareContent = true;
    
    /** 内容相似度阈值(0.0-1.0) - 低于此值认为显著不同 */
    private double contentSimilarityThreshold = 0.7;
    
    /** 是否比较响应时间 */
    private boolean compareTime = false;
    
    /** 响应时间差异阈值(毫秒) */
    private long timeDifferenceThreshold = 1000;
    
    // ========== 时间验证专用配置 ==========
    
    /** 【TIME_BASED_VERIFICATION】时间基线(毫秒) - 期望的响应时间 */
    private Long timeBaseline;
    
    /** 【TIME_BASED_VERIFICATION】允许的时间误差范围(百分比, 0-100) */
    private int timeTolerancePercent = 20;
    
    // ========== 跨Pair特征引用配置（新增）==========
    
    /** 引用的Pair ID - 用于对比或获取特征 */
    private Integer referencePairId;
    
    /** 响应时间比较模式（用于时间盲注）
     *  - ABSOLUTE: 绝对值比较（使用timeBaseline）
     *  - RELATIVE_TO_PAIR: 相对于某个Pair的倍数（如：2倍于Pair1）
     */
    private TimeComparisonMode timeComparisonMode = TimeComparisonMode.ABSOLUTE;
    
    /** 【RELATIVE_TO_PAIR模式】相对时间倍数（如：2.0表示2倍时间） */
    private Double relativeTimeMultiplier = 1.0;
    
    /** 【BOOLEAN_COMPARISON】是否与指定Pair对比响应差异 */
    private boolean compareWithReferencePair = false;
    
    // ========== 响应体内容对比配置（新增）==========
    
    /** 响应体对比模式 */
    private BodyComparisonMode bodyComparisonMode = BodyComparisonMode.NONE;
    
    /** 【BODY_EQUALS/BODY_NOT_EQUALS】引用的Pair ID（用于响应体对比） */
    private Integer bodyComparisonReferencePairId;
    
    /** 是否使用清理后的内容对比（忽略动态内容） */
    private boolean useCleanedBodyComparison = true;
    
    /** 响应体保存模式（用于内存优化） */
    private String bodySaveMode = "HASH_AND_CLEANED";  // HASH_ONLY, HASH_AND_CLEANED, FULL_CONTENT
    
    // ========== 跨Pair特征引用配置（新增）==========
    
    /** 引用特征类型 (STATUS_CODE, RESPONSE_LENGTH, RESPONSE_TIME, RESPONSE_BODY) */
    private String referenceFeatureType;
    
    /** 比较操作符 (EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, NOT_CONTAINS) */
    private String referenceOperator;
    
    /** 比较值（可选，如果不设置则直接对比两个Pair的特征值） */
    private String referenceValue;
    
    /** 是否忽略动态内容进行响应体对比 */
    private boolean ignoreDynamicContentForBodyComparison = true;
    
    // ========== 内容对比高级配置 ==========
    
    /** 关键内容差异正则列表 - 匹配任一即认为不同 */
    private List<String> contentDifferencePatterns = new ArrayList<>();
    
    /** 忽略的动态内容正则 - 对比前先清除这些内容 */
    private List<String> ignoreDynamicPatterns = Arrays.asList(
        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}",  // ISO时间戳
        "\"timestamp\":\\d+",                         // JSON时间戳
        "\"csrf_token\":\"[^\"]+\"",                  // CSRF token
        "nonce=[a-zA-Z0-9]+",                         // Nonce
        "sessionid=[a-zA-Z0-9]+"                      // Session ID
    );
    
    // ========== 反射确认专用配置 ==========
    
    /** 【REFLECTION_CONFIRMATION】期望反射的唯一标记
     *  支持占位符: {RANDOM}, {TIMESTAMP}, {UUID}
     *  示例: "XSS_{RANDOM}_MARKER"
     */
    private String reflectionMarker;
    
    /** 【REFLECTION_CONFIRMATION】是否检查上下文可利用性 */
    private boolean checkExploitableContext = false;
    
    /** 【REFLECTION_CONFIRMATION】危险上下文模式列表 */
    private List<String> exploitableContextPatterns = Arrays.asList(
        "<script[^>]*>.*" + "{MARKER}" + ".*</script>",  // JS上下文
        "on\\w+=[\"'].*" + "{MARKER}",                    // 事件处理器
        "<.*\\s" + "{MARKER}" + "\\s*=",                  // 属性名注入
        "href=[\"']javascript:.*" + "{MARKER}"            // JavaScript URL
    );
    
    // ========== 构造函数 ==========
    
    public ResponseComparisonConfig() {
    }
    
    // ========== Getters and Setters ==========
    
    public boolean isCompareStatusCode() {
        return compareStatusCode;
    }
    
    public void setCompareStatusCode(boolean compareStatusCode) {
        this.compareStatusCode = compareStatusCode;
    }
    
    public boolean isCompareLength() {
        return compareLength;
    }
    
    public void setCompareLength(boolean compareLength) {
        this.compareLength = compareLength;
    }
    
    public int getLengthDifferenceThreshold() {
        return lengthDifferenceThreshold;
    }
    
    public void setLengthDifferenceThreshold(int lengthDifferenceThreshold) {
        this.lengthDifferenceThreshold = lengthDifferenceThreshold;
    }
    
    public boolean isCompareContent() {
        return compareContent;
    }
    
    public void setCompareContent(boolean compareContent) {
        this.compareContent = compareContent;
    }
    
    public double getContentSimilarityThreshold() {
        return contentSimilarityThreshold;
    }
    
    public void setContentSimilarityThreshold(double contentSimilarityThreshold) {
        this.contentSimilarityThreshold = contentSimilarityThreshold;
    }
    
    public boolean isCompareTime() {
        return compareTime;
    }
    
    public void setCompareTime(boolean compareTime) {
        this.compareTime = compareTime;
    }
    
    public long getTimeDifferenceThreshold() {
        return timeDifferenceThreshold;
    }
    
    public void setTimeDifferenceThreshold(long timeDifferenceThreshold) {
        this.timeDifferenceThreshold = timeDifferenceThreshold;
    }
    
    public Long getTimeBaseline() {
        return timeBaseline;
    }
    
    public void setTimeBaseline(Long timeBaseline) {
        this.timeBaseline = timeBaseline;
    }
    
    public int getTimeTolerancePercent() {
        return timeTolerancePercent;
    }
    
    public void setTimeTolerancePercent(int timeTolerancePercent) {
        this.timeTolerancePercent = timeTolerancePercent;
    }
    
    public List<String> getContentDifferencePatterns() {
        return contentDifferencePatterns;
    }
    
    public void setContentDifferencePatterns(List<String> contentDifferencePatterns) {
        this.contentDifferencePatterns = contentDifferencePatterns;
    }
    
    public List<String> getIgnoreDynamicPatterns() {
        return ignoreDynamicPatterns;
    }
    
    public void setIgnoreDynamicPatterns(List<String> ignoreDynamicPatterns) {
        this.ignoreDynamicPatterns = ignoreDynamicPatterns;
    }
    
    public String getReflectionMarker() {
        return reflectionMarker;
    }
    
    public void setReflectionMarker(String reflectionMarker) {
        this.reflectionMarker = reflectionMarker;
    }
    
    public boolean isCheckExploitableContext() {
        return checkExploitableContext;
    }
    
    public void setCheckExploitableContext(boolean checkExploitableContext) {
        this.checkExploitableContext = checkExploitableContext;
    }
    
    public List<String> getExploitableContextPatterns() {
        return exploitableContextPatterns;
    }
    
    public void setExploitableContextPatterns(List<String> exploitableContextPatterns) {
        this.exploitableContextPatterns = exploitableContextPatterns;
    }
    
    public Integer getReferencePairId() {
        return referencePairId;
    }
    
    public void setReferencePairId(Integer referencePairId) {
        this.referencePairId = referencePairId;
    }
    
    public TimeComparisonMode getTimeComparisonMode() {
        return timeComparisonMode;
    }
    
    public void setTimeComparisonMode(TimeComparisonMode timeComparisonMode) {
        this.timeComparisonMode = timeComparisonMode;
    }
    
    public Double getRelativeTimeMultiplier() {
        return relativeTimeMultiplier;
    }
    
    public void setRelativeTimeMultiplier(Double relativeTimeMultiplier) {
        this.relativeTimeMultiplier = relativeTimeMultiplier;
    }
    
    public boolean isCompareWithReferencePair() {
        return compareWithReferencePair;
    }
    
    public void setCompareWithReferencePair(boolean compareWithReferencePair) {
        this.compareWithReferencePair = compareWithReferencePair;
    }
    
    /**
     * 时间比较模式枚举
     */
    public enum TimeComparisonMode {
        /** 绝对值比较 - 使用timeBaseline */
        ABSOLUTE,
        
        /** 相对于某个Pair的倍数 - 使用referencePairId和relativeTimeMultiplier */
        RELATIVE_TO_PAIR
    }
    
    /**
     * 响应体对比模式枚举（新增）
     */
    public enum BodyComparisonMode {
        /** 不对比响应体 */
        NONE,
        
        /** 响应体必须与引用Pair相等 */
        BODY_EQUALS,
        
        /** 响应体必须与引用Pair不相等 */
        BODY_NOT_EQUALS,
        
        /** 响应体必须与引用Pair相似（基于相似度阈值） */
        BODY_SIMILAR,
        
        /** 响应体必须与引用Pair不相似 */
        BODY_NOT_SIMILAR
    }
    
    public BodyComparisonMode getBodyComparisonMode() {
        return bodyComparisonMode != null ? bodyComparisonMode : BodyComparisonMode.NONE;
    }
    
    public void setBodyComparisonMode(BodyComparisonMode bodyComparisonMode) {
        this.bodyComparisonMode = bodyComparisonMode;
    }
    
    public Integer getBodyComparisonReferencePairId() {
        return bodyComparisonReferencePairId;
    }
    
    public void setBodyComparisonReferencePairId(Integer bodyComparisonReferencePairId) {
        this.bodyComparisonReferencePairId = bodyComparisonReferencePairId;
    }
    
    public boolean isUseCleanedBodyComparison() {
        return useCleanedBodyComparison;
    }
    
    public void setUseCleanedBodyComparison(boolean useCleanedBodyComparison) {
        this.useCleanedBodyComparison = useCleanedBodyComparison;
    }
    
    // ========== 新增字段的 Getters and Setters ==========
    
    public String getBodySaveMode() {
        return bodySaveMode != null ? bodySaveMode : "HASH_AND_CLEANED";
    }
    
    public void setBodySaveMode(String bodySaveMode) {
        this.bodySaveMode = bodySaveMode;
    }
    
    public String getReferenceFeatureType() {
        return referenceFeatureType;
    }
    
    public void setReferenceFeatureType(String referenceFeatureType) {
        this.referenceFeatureType = referenceFeatureType;
    }
    
    public String getReferenceOperator() {
        return referenceOperator;
    }
    
    public void setReferenceOperator(String referenceOperator) {
        this.referenceOperator = referenceOperator;
    }
    
    public String getReferenceValue() {
        return referenceValue;
    }
    
    public void setReferenceValue(String referenceValue) {
        this.referenceValue = referenceValue;
    }
    
    public boolean isIgnoreDynamicContentForBodyComparison() {
        return ignoreDynamicContentForBodyComparison;
    }
    
    public void setIgnoreDynamicContentForBodyComparison(boolean ignoreDynamicContentForBodyComparison) {
        this.ignoreDynamicContentForBodyComparison = ignoreDynamicContentForBodyComparison;
    }
}


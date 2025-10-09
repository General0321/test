package com.xprobe.scanner.active.gap;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GAP.py风格的参数和词过滤器
 * 参考GAP.py的过滤逻辑和正则表达式
 * 
 * GAP.py: https://github.com/xnl-h4ck3r/GAP-Burp-Extension
 */
public class GapStyleFilter {
    
    private final GapFilterConfig config;
    
    // ========== GAP.py的正则表达式 ==========
    
    // REGEX_PARAM: 参数验证正则（只接受 A-Z a-z 0-9 - _ . ~ [ ]）
    private static final Pattern PATTERN_VALID_PARAM = 
        Pattern.compile("^[A-Za-z0-9_.~\\-\\[\\]]+$");
    
    // REGEX_WORDS: 提取至少3字符的单词（前后不能是斜杠）
    private static final Pattern PATTERN_WORDS = 
        Pattern.compile("(?<![/])\\b\\w{3,}\\b(?![/])");
    
    // REGEX_WORDSUB: 清理词中的特殊字符
    private static final Pattern PATTERN_WORD_SUB = 
        Pattern.compile("\\\"|%22|<|%3c|>|%3e|\\(|%28|\\)|%29|\\s|%20", 
        Pattern.CASE_INSENSITIVE);
    
    // JavaScript关键字（避免误判）
    private static final Set<String> JS_KEYWORDS = Set.of(
        "var", "let", "const", "function", "return", "if", "else", "for", "while",
        "break", "continue", "switch", "case", "default", "try", "catch", "finally",
        "throw", "new", "this", "super", "class", "extends", "import", "export",
        "async", "await", "yield", "typeof", "instanceof", "delete", "void",
        "window", "document", "console", "Object", "Array", "String", "Number",
        "Boolean", "Date", "Math", "JSON", "Promise", "Symbol", "Map", "Set"
    );
    
    // HTML标签名（避免误判）
    private static final Set<String> HTML_TAGS = Set.of(
        "div", "span", "input", "button", "form", "table", "header", "footer",
        "nav", "section", "article", "aside", "main", "p", "a", "img", "h1",
        "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "td", "tr", "th"
    );
    
    /**
     * 构造函数
     */
    public GapStyleFilter(GapFilterConfig config) {
        this.config = config;
    }
    
    /**
     * 验证参数是否有效（参考GAP.py的addParameter逻辑）
     * 
     * @param param 参数名
     * @return true=有效, false=无效
     */
    public boolean isValidParameter(String param) {
        if (param == null || param.isEmpty()) {
            return false;
        }
        
        param = param.trim();
        
        // 1. 长度检查（最小3字符，参考GAP.py）
        if (param.length() < 3) {
            return false;
        }
        
        // 2. 正则验证（只接受 [A-Za-z0-9_.~-[]]）
        if (!PATTERN_VALID_PARAM.matcher(param).matches()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证词是否有效（参考GAP.py的addWord逻辑）
     * 
     * @param word 词
     * @return true=有效, false=无效
     */
    public boolean isValidWord(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        
        word = word.trim();
        
        // 转小写（如果配置要求）
        String checkWord = config.isToLowerCase() ? word.toLowerCase() : word;
        
        // 1. 长度检查
        if (word.length() < config.getMinWordLength()) {
            return false;
        }
        
        if (word.length() > config.getMaxWordLength()) {
            return false;
        }
        
        // 2. 停用词检查
        if (config.isStopWord(checkWord)) {
            return false;
        }
        
        // 3. JavaScript关键字检查
        if (JS_KEYWORDS.contains(checkWord)) {
            return false;
        }
        
        // 4. HTML标签检查
        if (HTML_TAGS.contains(checkWord)) {
            return false;
        }
        
        // 5. 数字检查
        if (!config.isIncludeWordsWithDigits()) {
            if (word.matches(".*\\d.*")) {
                return false;
            }
        }
        
        // 6. 正则验证
        if (!PATTERN_VALID_PARAM.matcher(word).matches()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 从文本中提取词（参考GAP.py的REGEX_WORDS）
     * 
     * @param text 文本
     * @return 有效的词集合
     */
    public Set<String> extractWords(String text) {
        Set<String> words = new HashSet<>();
        
        if (text == null || text.isEmpty()) {
            return words;
        }
        
        // 使用GAP.py的词提取正则
        Matcher matcher = PATTERN_WORDS.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            
            // 清理词（移除引号、括号等）
            word = PATTERN_WORD_SUB.matcher(word).replaceAll("");
            
            // 验证并添加
            if (isValidWord(word)) {
                if (config.isToLowerCase()) {
                    words.add(word.toLowerCase());
                } else {
                    words.add(word);
                }
            }
        }
        
        return words;
    }
    
    /**
     * 清理参数名（参考GAP.py的参数清理逻辑）
     * 
     * @param param 原始参数名
     * @return 清理后的参数名
     */
    public String cleanParameter(String param) {
        if (param == null || param.isEmpty()) {
            return null;
        }
        
        // 移除URL编码的方括号
        param = param.replace("%5b", "").replace("%5B", "")
                    .replace("%5d", "").replace("%5D", "");
        
        // 移除特殊字符
        param = param.replace("\\", "").replace("/", "")
                    .replace("quot;", "").replace("apos;", "")
                    .replace("amp;", "").replace("\"", "")
                    .replace("'", "");
        
        // 处理 ? 分隔
        if (param.contains("?")) {
            String[] parts = param.split("\\?");
            if (parts.length > 1 && !parts[parts.length - 1].isEmpty()) {
                param = parts[parts.length - 1];
            }
        }
        
        param = param.trim();
        
        // 如果清理后为空，返回null
        if (param.isEmpty()) {
            return null;
        }
        
        return param;
    }
    
    /**
     * 判断词是否看起来像参数名
     * （包含下划线、驼峰命名、特定前缀/后缀等特征）
     */
    public boolean looksLikeParameter(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        
        // 1. 包含下划线 → 很可能是参数 (user_id, api_key)
        if (word.contains("_")) {
            return true;
        }
        
        // 2. 驼峰命名 → 很可能是参数 (userId, apiKey)
        if (word.matches(".*[a-z][A-Z].*")) {
            return true;
        }
        
        // 3. 包含数字 → 可能是版本或ID (apiV2, user1)
        if (word.matches(".*\\d.*")) {
            return true;
        }
        
        // 4. 特定前缀/后缀 → 很可能是参数
        String lower = word.toLowerCase();
        if (lower.startsWith("is") || lower.startsWith("has") || 
            lower.startsWith("get") || lower.startsWith("set") ||
            lower.endsWith("id") || lower.endsWith("key") || 
            lower.endsWith("token") || lower.endsWith("name") ||
            lower.endsWith("code") || lower.endsWith("type") ||
            lower.endsWith("status") || lower.endsWith("flag")) {
            return true;
        }
        
        // 5. 长度适中 (6-20) → 可能是参数
        if (word.length() >= 6 && word.length() <= 20) {
            return true;
        }
        
        // 6. 太短且没有特殊特征 → 可能是噪音
        if (word.length() < 6) {
            return false;
        }
        
        // 7. 默认接受（长度>20的已被配置过滤）
        return true;
    }
    
    /**
     * 参数来源枚举（用于上下文感知过滤）
     */
    public enum ParameterSource {
        URL_PARAM,              // 最可信
        FORM_INPUT_NAME,        // 很可信
        META_TAG,               // 比较可信
        LOCALSTORAGE_KEY,       // 比较可信
        JS_VARIABLE,            // 中等可信
        JS_FUNCTION_PARAM,      // 需要过滤
        OBJECT_DESTRUCTURE,     // 需要过滤
        HTML_COMMENT,           // 需要严格过滤
        IMG_ALT,                // 需要非常严格过滤
        INLINE_EVENT            // 需要严格过滤
    }
    
    /**
     * 基于来源的上下文感知过滤
     * 
     * @param word 词
     * @param source 来源
     * @return true=接受, false=拒绝
     */
    public boolean shouldAcceptByContext(String word, ParameterSource source) {
        // 不同来源有不同的可信度
        switch (source) {
            case URL_PARAM:
            case FORM_INPUT_NAME:
            case LOCALSTORAGE_KEY:
                // 最可信的来源，直接接受（仅基础验证）
                return isValidParameter(word);
                
            case META_TAG:
            case JS_VARIABLE:
                // 比较可信，使用参数验证
                return isValidParameter(word) && !config.isStopWord(word);
                
            case JS_FUNCTION_PARAM:
            case OBJECT_DESTRUCTURE:
                // 需要过滤通用名
                return isValidParameter(word) && isValidWord(word) && looksLikeParameter(word);
                
            case HTML_COMMENT:
            case INLINE_EVENT:
                // 严格过滤，只保留明显的参数
                return isValidWord(word) && looksLikeParameter(word);
                
            case IMG_ALT:
                // 非常严格的过滤，只保留下划线或驼峰命名
                return isValidWord(word) && (word.contains("_") || word.matches(".*[a-z][A-Z].*"));
                
            default:
                return isValidParameter(word);
        }
    }
}



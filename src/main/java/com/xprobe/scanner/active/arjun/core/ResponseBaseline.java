package com.xprobe.scanner.active.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.model.BaselineFactors;

import java.util.*;

/**
 * 响应基线管理 - 建立异常检测规则
 * 
 * 对应Arjun的define()函数
 */
public class ResponseBaseline {
    
    private final MontoyaApi api;
    
    public ResponseBaseline(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 建立基线规则（对比两个响应）
     * 
     * @param response1 第一个测试响应
     * @param response2 第二个测试响应
     * @param testParam 测试参数名
     * @param testValue 测试参数值
     * @param wordlist 字典（用于检测参数名反射）
     * @return 基线因子
     */
    public BaselineFactors define(HttpResponse response1, 
                                   HttpResponse response2,
                                   String testParam,
                                   String testValue,
                                   Set<String> wordlist) {
        
        BaselineFactors factors = new BaselineFactors();
        
        String body1 = response1.bodyToString();
        String body2 = response2.bodyToString();
        
        // 1. 检查HTTP状态码
        if (response1.statusCode() == response2.statusCode()) {
            factors.setSameCode(Integer.valueOf(response1.statusCode()));
        }
        
        // 2. 检查响应头
        if (headersEqual(response1, response2)) {
            factors.setSameHeaders(getHeaderKeys(response1));
        }
        
        // 3. 检查重定向
        String redirect1 = getRedirectLocation(response1);
        String redirect2 = getRedirectLocation(response2);
        if (redirect1 != null && redirect1.equals(redirect2)) {
            factors.setSameRedirect(redirect1);
        }
        
        // 4. 检查响应体
        if (body1.equals(body2)) {
            factors.setSameBody(body1);
        } 
        // 5. 检查行数
        else if (countLines(body1) == countLines(body2)) {
            factors.setLinesNum(countLines(body1));
        }
        // 6. 检查纯文本（去HTML）
        else {
            String plaintext1 = removeTags(body1);
            String plaintext2 = removeTags(body2);
            if (plaintext1.equals(plaintext2)) {
                factors.setSamePlaintext(plaintext1);
            }
            // 7. 检查不同的行
            else {
                List<String> diffLines = findCommonLines(body1, body2);
                if (!diffLines.isEmpty()) {
                    factors.setLinesDiff(diffLines);
                }
            }
        }
        
        // 8. 检查参数名反射
        if (!body2.contains(testParam)) {
            Set<String> existingWords = new HashSet<>();
            for (String word : wordlist) {
                if (body2.contains(word)) {
                    existingWords.add(word);
                }
            }
            factors.setParamMissing(existingWords);
        }
        
        // 9. 检查参数值反射
        if (!body2.contains(testValue)) {
            factors.setValueMissing(true);
        }
        
        api.logging().raiseDebugEvent("✅ 基线规则建立: " + factors.summary());
        
        return factors;
    }
    
    /**
     * 检查响应头是否相同（只比较header名称）
     */
    private boolean headersEqual(HttpResponse r1, HttpResponse r2) {
        Set<String> keys1 = getHeaderKeys(r1);
        Set<String> keys2 = getHeaderKeys(r2);
        return keys1.equals(keys2);
    }
    
    /**
     * 获取响应头键列表（排序后的集合）
     */
    private Set<String> getHeaderKeys(HttpResponse response) {
        Set<String> keys = new TreeSet<>();  // 自动排序
        for (var header : response.headers()) {
            keys.add(header.name());
        }
        return keys;
    }
    
    /**
     * 获取重定向位置
     */
    private String getRedirectLocation(HttpResponse response) {
        for (var header : response.headers()) {
            if ("Location".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
    
    /**
     * 统计行数
     */
    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n").length;
    }
    
    /**
     * 移除HTML标签
     */
    private String removeTags(String html) {
        if (html == null) {
            return "";
        }
        // 移除所有 <tag> 和 </tag>
        return html.replaceAll("<[^>]+>", "").trim();
    }
    
    /**
     * 找出共同的行（两个文本都有的行）
     */
    private List<String> findCommonLines(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return new ArrayList<>();
        }
        
        String[] lines1 = text1.split("\n");
        String[] lines2 = text2.split("\n");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(lines1));
        Set<String> set2 = new HashSet<>(Arrays.asList(lines2));
        
        // 找交集（共同的行）
        set1.retainAll(set2);
        
        return new ArrayList<>(set1);
    }
}


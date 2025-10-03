package com.xprobe.scanner.active.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.model.AnomalyResult;
import com.xprobe.scanner.active.arjun.model.BaselineFactors;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 异常检测器 - 对比响应与基线
 * 
 * 对应Arjun的compare()函数
 */
public class AnomalyDetector {
    
    private final MontoyaApi api;
    
    public AnomalyDetector(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 检测响应异常
     * 
     * @param response 待检测的响应
     * @param factors 基线因子
     * @param testParams 测试参数
     * @return 异常检测结果
     */
    public AnomalyResult compare(HttpResponse response, 
                                 BaselineFactors factors,
                                 Map<String, String> testParams) {
        
        if (response == null) {
            return AnomalyResult.normal();
        }
        
        // 1. 检查HTTP状态码
        if (factors.getSameCode() != null && 
            response.statusCode() != factors.getSameCode()) {
            return AnomalyResult.detected(
                "http_code", 
                testParams.keySet(),
                String.format("HTTP状态码变化: %d → %d", 
                    factors.getSameCode(), response.statusCode())
            );
        }
        
        // 2. 检查响应头
        if (factors.getSameHeaders() != null && !factors.getSameHeaders().isEmpty()) {
            Set<String> currentHeaders = getHeaderKeys(response);
            if (!currentHeaders.equals(factors.getSameHeaders())) {
                return AnomalyResult.detected(
                    "http_headers",
                    testParams.keySet(),
                    "响应头变化"
                );
            }
        }
        
        // 3. 检查重定向
        if (factors.getSameRedirect() != null) {
            String currentRedirect = getRedirectLocation(response);
            if (currentRedirect != null && 
                !currentRedirect.equals(factors.getSameRedirect())) {
                return AnomalyResult.detected(
                    "redirection",
                    testParams.keySet(),
                    String.format("重定向变化: %s → %s", 
                        factors.getSameRedirect(), currentRedirect)
                );
            }
        }
        
        String body = response.bodyToString();
        
        // 4. 检查响应体
        if (factors.getSameBody() != null && 
            !body.equals(factors.getSameBody())) {
            return AnomalyResult.detected(
                "body_content",
                testParams.keySet(),
                String.format("响应体变化: %d → %d bytes", 
                    factors.getSameBody().length(), body.length())
            );
        }
        
        // 5. 检查行数
        if (factors.getLinesNum() != null && 
            countLines(body) != factors.getLinesNum()) {
            return AnomalyResult.detected(
                "line_count",
                testParams.keySet(),
                String.format("行数变化: %d → %d", 
                    factors.getLinesNum(), countLines(body))
            );
        }
        
        // 6. 检查纯文本
        if (factors.getSamePlaintext() != null) {
            String plaintext = removeTags(body);
            if (!plaintext.equals(factors.getSamePlaintext())) {
                return AnomalyResult.detected(
                    "plaintext",
                    testParams.keySet(),
                    String.format("纯文本变化: %d → %d chars", 
                        factors.getSamePlaintext().length(), plaintext.length())
                );
            }
        }
        
        // 7. 检查行差异
        if (factors.getLinesDiff() != null && !factors.getLinesDiff().isEmpty()) {
            for (String line : factors.getLinesDiff()) {
                if (!body.contains(line)) {
                    return AnomalyResult.detected(
                        "line_diff",
                        testParams.keySet(),
                        "特定行缺失"
                    );
                }
            }
        }
        
        // 8. 检查参数名反射
        if (factors.getParamMissing() != null) {
            for (String param : testParams.keySet()) {
                // 只检查长度>=5的参数（避免误报）
                if (param.length() >= 5 && 
                    !factors.getParamMissing().contains(param) &&
                    isParamReflected(body, param)) {
                    return AnomalyResult.detected(
                        "param_reflection",
                        testParams.keySet(),
                        "参数名反射: " + param
                    );
                }
            }
        }
        
        // 9. 检查参数值反射
        if (factors.isValueMissing()) {
            for (String value : testParams.values()) {
                // 只检查6位值（Arjun的随机值长度）
                if (value != null && value.length() == 6 &&
                    isValueReflected(body, value)) {
                    return AnomalyResult.detected(
                        "value_reflection",
                        testParams.keySet(),
                        "参数值反射: " + value
                    );
                }
            }
        }
        
        // 无异常
        return AnomalyResult.normal();
    }
    
    /**
     * 检查参数名是否被反射（出现在响应中且被引号包围）
     */
    private boolean isParamReflected(String body, String param) {
        // 匹配: "param" 或 'param' 或 >param< 等
        String regex = String.format("['\">\\s]%s['\">\\s]", Pattern.quote(param));
        return Pattern.compile(regex).matcher(body).find();
    }
    
    /**
     * 检查参数值是否被反射
     */
    private boolean isValueReflected(String body, String value) {
        // 匹配: "value" 或 'value' 或 >value< 等
        String regex = String.format("['\">\\s]%s['\">\\s]", Pattern.quote(value));
        return Pattern.compile(regex).matcher(body).find();
    }
    
    /**
     * 获取响应头键列表
     */
    private Set<String> getHeaderKeys(HttpResponse response) {
        Set<String> keys = new TreeSet<>();
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
        return html.replaceAll("<[^>]+>", "").trim();
    }
}


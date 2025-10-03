package com.xprobe.scanner.active.arjun.model;

import java.util.*;

/**
 * 基线因子 - 用于异常检测的基准规则
 * 
 * 对应Arjun的factors字典，包含9种检测规则
 */
public class BaselineFactors {
    
    // 1. HTTP状态码相同
    private Integer sameCode;
    
    // 2. 响应体完全相同
    private String sameBody;
    
    // 3. 去HTML后纯文本相同
    private String samePlaintext;
    
    // 4. 响应行数相同
    private Integer linesNum;
    
    // 5. 不同的行（共同的行列表）
    private List<String> linesDiff;
    
    // 6. 响应头相同
    private Set<String> sameHeaders;
    
    // 7. 重定向相同
    private String sameRedirect;
    
    // 8. 参数名未在响应中出现（已存在的词）
    private Set<String> paramMissing;
    
    // 9. 参数值未在响应中出现
    private boolean valueMissing;
    
    public BaselineFactors() {
        this.linesDiff = new ArrayList<>();
        this.sameHeaders = new TreeSet<>();
        this.paramMissing = new HashSet<>();
    }
    
    /**
     * 生成基线摘要（用于日志）
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        int ruleCount = 0;
        
        if (sameCode != null) {
            sb.append("code=").append(sameCode);
            ruleCount++;
        }
        if (sameBody != null) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("body=same");
            ruleCount++;
        }
        if (samePlaintext != null) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("plaintext=same");
            ruleCount++;
        }
        if (linesNum != null) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("lines=").append(linesNum);
            ruleCount++;
        }
        if (linesDiff != null && !linesDiff.isEmpty()) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("diff_lines=").append(linesDiff.size());
            ruleCount++;
        }
        if (sameHeaders != null && !sameHeaders.isEmpty()) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("headers=").append(sameHeaders.size());
            ruleCount++;
        }
        if (sameRedirect != null) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("redirect=").append(sameRedirect);
            ruleCount++;
        }
        if (paramMissing != null && !paramMissing.isEmpty()) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("param_missing=").append(paramMissing.size());
            ruleCount++;
        }
        if (valueMissing) {
            if (ruleCount > 0) sb.append(", ");
            sb.append("value_missing=true");
            ruleCount++;
        }
        
        return sb.toString() + " (共" + ruleCount + "条规则)";
    }
    
    // Getters and Setters
    
    public Integer getSameCode() {
        return sameCode;
    }
    
    public void setSameCode(Integer sameCode) {
        this.sameCode = sameCode;
    }
    
    public String getSameBody() {
        return sameBody;
    }
    
    public void setSameBody(String sameBody) {
        this.sameBody = sameBody;
    }
    
    public String getSamePlaintext() {
        return samePlaintext;
    }
    
    public void setSamePlaintext(String samePlaintext) {
        this.samePlaintext = samePlaintext;
    }
    
    public Integer getLinesNum() {
        return linesNum;
    }
    
    public void setLinesNum(Integer linesNum) {
        this.linesNum = linesNum;
    }
    
    public List<String> getLinesDiff() {
        return linesDiff;
    }
    
    public void setLinesDiff(List<String> linesDiff) {
        this.linesDiff = linesDiff;
    }
    
    public Set<String> getSameHeaders() {
        return sameHeaders;
    }
    
    public void setSameHeaders(Set<String> sameHeaders) {
        this.sameHeaders = sameHeaders;
    }
    
    public String getSameRedirect() {
        return sameRedirect;
    }
    
    public void setSameRedirect(String sameRedirect) {
        this.sameRedirect = sameRedirect;
    }
    
    public Set<String> getParamMissing() {
        return paramMissing;
    }
    
    public void setParamMissing(Set<String> paramMissing) {
        this.paramMissing = paramMissing;
    }
    
    public boolean isValueMissing() {
        return valueMissing;
    }
    
    public void setValueMissing(boolean valueMissing) {
        this.valueMissing = valueMissing;
    }
    
    /**
     * 移除不稳定的因子（关键功能！）
     * 用于动态调整基线规则
     */
    public void removeFactor(String factorType) {
        switch (factorType) {
            case "http_code":
                this.sameCode = null;
                break;
            case "body_content":
                this.sameBody = null;
                break;
            case "plaintext":
                this.samePlaintext = null;
                break;
            case "line_count":
                this.linesNum = null;
                break;
            case "line_diff":
                this.linesDiff = null;
                break;
            case "http_headers":
                this.sameHeaders = null;
                break;
            case "redirection":
                this.sameRedirect = null;
                break;
            case "param_reflection":
                this.paramMissing = null;
                break;
            case "value_reflection":
                this.valueMissing = false;
                break;
            default:
                // 未知类型，忽略
                break;
        }
    }
    
    /**
     * 检查是否还有有效的因子
     */
    public boolean hasAnyFactor() {
        return sameCode != null || 
               sameBody != null || 
               samePlaintext != null ||
               linesNum != null ||
               (linesDiff != null && !linesDiff.isEmpty()) ||
               (sameHeaders != null && !sameHeaders.isEmpty()) ||
               sameRedirect != null ||
               (paramMissing != null && !paramMissing.isEmpty()) ||
               valueMissing;
    }
}


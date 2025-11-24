package com.xprobe.scanner.core;

import com.xprobe.scanner.utils.StaticResourceFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 全局过滤器 - 统一管理黑白名单和静态资源过滤
 */
public class GlobalFilter {
    private boolean whitelistEnabled = false;
    private boolean blacklistEnabled = false;
    private boolean staticResourceFilterEnabled = true;  // ✅ 默认启用静态资源过滤
    private List<String> whitelist = new ArrayList<>();
    private List<String> blacklist = new ArrayList<>();
    private List<Pattern> whitelistPatterns = new ArrayList<>();
    private List<Pattern> blacklistPatterns = new ArrayList<>();
    
    /**
     * 检查URL是否应该被处理（被动扫描）
     */
    public boolean shouldProcessPassive(String url) {
        return shouldProcess(url, "被动扫描");
    }
    
    /**
     * 检查URL是否应该被主动探测
     */
    public boolean shouldProcessActive(String url) {
        return shouldProcess(url, "主动探测");
    }
    
    /**
     * 统一的处理逻辑
     * ✅ 检查顺序：URL有效性检查 → 静态资源过滤 → 黑白名单 → 通过
     */
    private boolean shouldProcess(String url, String type) {
        // ✅ 0. 最优先：检查URL有效性
        if (url == null || url.isEmpty()) {
            return false; // URL无效，不处理
        }
        
        // ✅ 1. 检查静态资源（默认启用）
        if (staticResourceFilterEnabled && StaticResourceFilter.isStaticResource(url)) {
            return false; // 静态资源，不处理
        }
        
        // ✅ 2. 检查黑白名单（白名单优先）
        // 如果白名单启用，检查是否在白名单中
        if (whitelistEnabled && !whitelist.isEmpty()) {
            boolean inWhitelist = false;
            
            // 先检查字符串匹配
            for (String pattern : whitelist) {
                if (url.contains(pattern)) {
                    inWhitelist = true;
                    break;
                }
            }
            
            // 如果字符串匹配失败，再使用编译好的正则表达式
            if (!inWhitelist && !whitelistPatterns.isEmpty()) {
                for (Pattern regex : whitelistPatterns) {
                    if (regex.matcher(url).find()) {
                        inWhitelist = true;
                        break;
                    }
                }
            }
            
            if (!inWhitelist) {
                return false; // 不在白名单中，不处理
            }
        }
        
        // 如果黑名单启用，检查是否在黑名单中
        if (blacklistEnabled && !blacklist.isEmpty()) {
            // ✅ 修复：确保URL不为null且不为空
            if (url == null || url.isEmpty()) {
                return true; // URL无效，跳过黑名单检查（已在前面检查过）
            }
            
            // 先检查字符串匹配
            for (String pattern : blacklist) {
                if (pattern != null && !pattern.trim().isEmpty() && url.contains(pattern.trim())) {
                    return false; // 在黑名单中，不处理
                }
            }
            
            // 再使用编译好的正则表达式
            if (!blacklistPatterns.isEmpty()) {
                for (Pattern regex : blacklistPatterns) {
                    if (regex != null && regex.matcher(url).find()) {
                        return false; // 在黑名单中，不处理
                    }
                }
            }
        }
        
        return true; // 通过所有检查，可以处理
    }
    
    /**
     * 更新白名单
     */
    public void updateWhitelist(List<String> whitelist, boolean enabled) {
        this.whitelist = new ArrayList<>(whitelist);
        this.whitelistEnabled = enabled;
        compileWhitelistPatterns();
    }
    
    /**
     * 更新黑名单
     */
    public void updateBlacklist(List<String> blacklist, boolean enabled) {
        this.blacklist = new ArrayList<>(blacklist);
        this.blacklistEnabled = enabled;
        compileBlacklistPatterns();
    }
    
    /**
     * 编译白名单正则表达式
     */
    private void compileWhitelistPatterns() {
        whitelistPatterns.clear();
        for (String pattern : whitelist) {
            try {
                whitelistPatterns.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                // 忽略无效的正则表达式
            }
        }
    }
    
    /**
     * 编译黑名单正则表达式
     */
    private void compileBlacklistPatterns() {
        blacklistPatterns.clear();
        for (String pattern : blacklist) {
            try {
                blacklistPatterns.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                // 忽略无效的正则表达式
            }
        }
    }
    
    /**
     * 添加白名单项
     */
    public void addWhitelistItem(String item) {
        if (item != null && !item.trim().isEmpty()) {
            whitelist.add(item.trim());
            compileWhitelistPatterns();
        }
    }
    
    /**
     * 添加黑名单项
     */
    public void addBlacklistItem(String item) {
        if (item != null && !item.trim().isEmpty()) {
            blacklist.add(item.trim());
            compileBlacklistPatterns();
        }
    }
    
    /**
     * 移除白名单项
     */
    public void removeWhitelistItem(String item) {
        whitelist.remove(item);
        compileWhitelistPatterns();
    }
    
    /**
     * 移除黑名单项
     */
    public void removeBlacklistItem(String item) {
        blacklist.remove(item);
        compileBlacklistPatterns();
    }
    
    /**
     * 清空白名单
     */
    public void clearWhitelist() {
        whitelist.clear();
        whitelistPatterns.clear();
    }
    
    /**
     * 清空黑名单
     */
    public void clearBlacklist() {
        blacklist.clear();
        blacklistPatterns.clear();
    }
    
    // Getters
    public boolean isWhitelistEnabled() { return whitelistEnabled; }
    public boolean isBlacklistEnabled() { return blacklistEnabled; }
    public boolean isStaticResourceFilterEnabled() { return staticResourceFilterEnabled; }
    public List<String> getWhitelist() { return new ArrayList<>(whitelist); }
    public List<String> getBlacklist() { return new ArrayList<>(blacklist); }
    
    // Setters
    public void setWhitelistEnabled(boolean enabled) { this.whitelistEnabled = enabled; }
    public void setBlacklistEnabled(boolean enabled) { this.blacklistEnabled = enabled; }
    public void setStaticResourceFilterEnabled(boolean enabled) { this.staticResourceFilterEnabled = enabled; }
}


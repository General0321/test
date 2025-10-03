package com.xprobe.scanner.active.arjun.config;

import java.util.*;

/**
 * 特殊参数 - 基于Arjun的special.json
 * 
 * 这些参数使用特定的值，更容易触发特定行为
 * 例如: debug=1, admin=true, waf=off
 */
public class SpecialParams {
    
    private static final Map<String, String> SPECIAL = new LinkedHashMap<>();
    
    static {
        // Debug 参数
        SPECIAL.put("debug", "1");
        SPECIAL.put("debug", "true");
        SPECIAL.put("debug", "yes");
        SPECIAL.put("debug", "on");
        SPECIAL.put("isdebug", "1");
        SPECIAL.put("isdebug", "true");
        SPECIAL.put("isdebug", "yes");
        SPECIAL.put("isdebug", "on");
        
        // Test 参数
        SPECIAL.put("test", "1");
        SPECIAL.put("test", "true");
        SPECIAL.put("test", "yes");
        SPECIAL.put("test", "on");
        SPECIAL.put("istest", "1");
        SPECIAL.put("istest", "true");
        SPECIAL.put("istest", "yes");
        SPECIAL.put("istest", "on");
        
        // Admin 参数
        SPECIAL.put("admin", "1");
        SPECIAL.put("admin", "true");
        SPECIAL.put("admin", "yes");
        SPECIAL.put("admin", "on");
        SPECIAL.put("isadmin", "1");
        SPECIAL.put("isadmin", "true");
        SPECIAL.put("isadmin", "yes");
        SPECIAL.put("isadmin", "on");
        
        // Source 参数
        SPECIAL.put("source", "1");
        SPECIAL.put("source", "true");
        SPECIAL.put("source", "yes");
        SPECIAL.put("source", "on");
        
        // Show 参数
        SPECIAL.put("show", "1");
        SPECIAL.put("show", "true");
        SPECIAL.put("show", "yes");
        SPECIAL.put("show", "on");
        
        // Bot 参数
        SPECIAL.put("bot", "1");
        SPECIAL.put("bot", "yes");
        SPECIAL.put("bot", "on");
        SPECIAL.put("isbot", "1");
        SPECIAL.put("isbot", "yes");
        SPECIAL.put("isbot", "on");
        
        // Anti-bot/Anti-robot 参数（禁用）
        SPECIAL.put("antibot", "off");
        SPECIAL.put("antibot", "0");
        SPECIAL.put("antibot", "no");
        SPECIAL.put("antibot", "none");
        SPECIAL.put("antirobot", "off");
        SPECIAL.put("antirobot", "0");
        SPECIAL.put("antirobot", "no");
        SPECIAL.put("antirobot", "none");
        
        // Environment 参数
        SPECIAL.put("env", "staging");
        SPECIAL.put("env", "test");
        SPECIAL.put("env", "testing");
        SPECIAL.put("env", "pre");
        SPECIAL.put("env", "uat");
        SPECIAL.put("env", "daily");
        SPECIAL.put("isenv", "staging");
        SPECIAL.put("isenv", "test");
        SPECIAL.put("isenv", "testing");
        SPECIAL.put("isenv", "uat");
        
        // Captcha 参数（禁用）
        SPECIAL.put("captcha", "off");
        SPECIAL.put("captcha", "0");
        SPECIAL.put("captcha", "no");
        SPECIAL.put("captcha", "none");
        SPECIAL.put("hascaptcha", "off");
        SPECIAL.put("hascaptcha", "0");
        SPECIAL.put("hascaptcha", "no");
        
        // Signing/Signature 参数（禁用）
        SPECIAL.put("signing", "off");
        SPECIAL.put("signing", "0");
        SPECIAL.put("signing", "no");
        SPECIAL.put("signature", "off");
        SPECIAL.put("signature", "0");
        SPECIAL.put("signature", "no");
        
        // Encryption 参数（禁用）
        SPECIAL.put("enc", "off");
        SPECIAL.put("enc", "0");
        SPECIAL.put("enc", "no");
        SPECIAL.put("encryption", "off");
        SPECIAL.put("encryption", "0");
        SPECIAL.put("encryption", "no");
        SPECIAL.put("isenc", "off");
        SPECIAL.put("isenc", "0");
        SPECIAL.put("isencryption", "off");
        SPECIAL.put("isencryption", "0");
        
        // WAF 参数（禁用）
        SPECIAL.put("waf", "disabled");
        SPECIAL.put("waf", "disable");
        SPECIAL.put("waf", "off");
        SPECIAL.put("waf", "0");
        SPECIAL.put("waf", "no");
        SPECIAL.put("haswaf", "disabled");
        SPECIAL.put("haswaf", "off");
        SPECIAL.put("haswaf", "0");
        
        // Security 参数（禁用）
        SPECIAL.put("security", "disabled");
        SPECIAL.put("security", "disable");
        SPECIAL.put("security", "0");
        SPECIAL.put("security", "no");
        SPECIAL.put("issecurity", "disabled");
        SPECIAL.put("issecurity", "0");
        SPECIAL.put("hassecurity", "0");
        SPECIAL.put("hassecurity", "no");
        
        // Automation 参数
        SPECIAL.put("automation", "on");
        SPECIAL.put("automation", "1");
        SPECIAL.put("automation", "yes");
        SPECIAL.put("hasautomation", "on");
        SPECIAL.put("hasautomation", "1");
        
        // SSO 参数
        SPECIAL.put("sso", "1");
        SPECIAL.put("singlesignon", "1");
        SPECIAL.put("hassso", "1");
        SPECIAL.put("dosso", "1");
        SPECIAL.put("dosinglesignon", "1");
        SPECIAL.put("hassinglesignon", "1");
        
        // Anti-crawl 参数（禁用）
        SPECIAL.put("anticrawl", "off");
        SPECIAL.put("anticrawl", "0");
        SPECIAL.put("anticrawl", "no");
        
        // Disable 参数
        SPECIAL.put("disable", "waf");
        SPECIAL.put("disable", "security");
        SPECIAL.put("disabled", "waf");
        SPECIAL.put("disabled", "security");
    }
    
    /**
     * 获取特殊参数映射
     */
    public static Map<String, String> getSpecialParams() {
        return new LinkedHashMap<>(SPECIAL);
    }
    
    /**
     * 获取特殊参数名集合（用于字典）
     */
    public static Set<String> getSpecialParamNames() {
        return new LinkedHashSet<>(SPECIAL.keySet());
    }
    
    /**
     * 获取特殊参数数量
     */
    public static int getCount() {
        return SPECIAL.size();
    }
}


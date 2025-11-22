package com.xprobe.scanner.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 配置验证器
 * 对用户输入的配置进行合法性校验
 */
public class ConfigValidator {
    
    // 正则模式
    private static final Pattern IP_PORT_PATTERN = 
        Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}$");
    private static final Pattern DOMAIN_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public String getErrorMessage() {
            return String.join("\n", errors);
        }
    }
    
    /**
     * 验证完整配置
     */
    public static ValidationResult validate(XProbeConfig config) {
        List<String> errors = new ArrayList<>();
        
        // 验证主动探测配置
        validateActiveScanConfig(config, errors);
        
        // 验证代理池配置
        validateProxyPoolConfig(config, errors);
        
        // 验证黑白名单
        validateFilterConfig(config, errors);
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 验证主动探测配置
     */
    private static void validateActiveScanConfig(XProbeConfig config, List<String> errors) {
        // 扫描间隔
        if (config.getBruteforceInterval() < 10) {
            errors.add("扫描间隔不能小于10秒");
        }
        if (config.getBruteforceInterval() > 3600) {
            errors.add("扫描间隔不能大于3600秒（1小时）");
        }
        
        // 最小参数数量
        if (config.getMinParameterCount() < 1) {
            errors.add("最小参数数量不能小于1");
        }
        if (config.getMinParameterCount() > 100) {
            errors.add("最小参数数量不能大于100");
        }
        
        // 最大并发主机数
        if (config.getMaxConcurrentHosts() < 1) {
            errors.add("最大并发主机数不能小于1");
        }
        if (config.getMaxConcurrentHosts() > 50) {
            errors.add("最大并发主机数不能大于50");
        }
    }
    
    /**
     * 验证代理池配置
     */
    private static void validateProxyPoolConfig(XProbeConfig config, List<String> errors) {
        if (config.isEnableProxyPool()) {
            // 代理超时
            if (config.getProxyTimeout() < 5) {
                errors.add("代理超时不能小于5秒");
            }
            if (config.getProxyTimeout() > 60) {
                errors.add("代理超时不能大于60秒");
            }
            
            // 最大重试次数
            if (config.getMaxRetries() < 0) {
                errors.add("最大重试次数不能小于0");
            }
            if (config.getMaxRetries() > 10) {
                errors.add("最大重试次数不能大于10");
            }
            
            // 代理列表
            if (config.getProxyList().isEmpty()) {
                errors.add("启用代理池时，代理列表不能为空");
            } else {
                // 验证每个代理格式
                for (String proxy : config.getProxyList()) {
                    if (!IP_PORT_PATTERN.matcher(proxy.trim()).matches()) {
                        errors.add("代理地址格式错误: " + proxy + " (应为 IP:端口 格式)");
                    }
                }
            }
        }
    }
    
    /**
     * 验证黑白名单配置
     */
    private static void validateFilterConfig(XProbeConfig config, List<String> errors) {
        // 验证白名单规则
        for (String rule : config.getWhitelist()) {
            if (!isValidFilterRule(rule)) {
                errors.add("白名单规则格式错误: " + rule);
            }
        }
        
        // 验证黑名单规则
        for (String rule : config.getBlacklist()) {
            if (!isValidFilterRule(rule)) {
                errors.add("黑名单规则格式错误: " + rule);
            }
        }
    }
    
    /**
     * 验证过滤规则是否有效
     */
    private static boolean isValidFilterRule(String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return false;
        }
        
        rule = rule.trim();
        
        // 支持通配符规则
        if (rule.contains("*")) {
            return true;
        }
        
        // 支持正则表达式
        if (rule.startsWith("^") || rule.contains(".*")) {
            try {
                Pattern.compile(rule);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        // 支持域名规则
        return DOMAIN_PATTERN.matcher(rule).matches() || rule.contains("/");
    }
}


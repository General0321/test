package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload变量解析器（支持Burp Collaborator和注入点原值）
 * 
 * 支持的变量：
 * === 注入点相关 ===
 * - {{ORIGINAL}}              - 原始值（参数原值、Header原值等）
 * - {{ORIGINAL_URL_ENCODED}}  - URL编码的原始值
 * - {{ORIGINAL_BASE64}}       - Base64编码的原始值
 * 
 * === 外带检测 ===
 * - {{COLLABORATOR}} 或 {{DNSLOG}} - Burp Collaborator域名
 * 
 * === 随机值 ===
 * - {{RANDOM_STRING}} - 随机字符串
 * - {{RANDOM_NUMBER}} - 随机数字
 * - {{TIMESTAMP}}     - 时间戳
 * - {{UUID}}          - UUID
 * 
 * === 编码函数 ===
 * - {{BASE64:xxx}}    - Base64编码
 * - {{URL_ENCODE:xxx}} - URL编码
 */
public class PayloadVariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final MontoyaApi api;
    private final Collaborator collaborator;
    
    public PayloadVariableResolver(MontoyaApi api) {
        this.api = api;
        this.collaborator = api.collaborator();
    }
    
    /**
     * 解析payload中的变量（不带外部context）
     * 
     * @param payload 原始payload
     * @return 解析后的payload和上下文信息
     */
    public PayloadContext resolvePayload(String payload) {
        return resolvePayload(payload, new HashMap<>());
    }
    
    /**
     * 解析payload中的变量（带外部context，用于{{ORIGINAL}}等变量）
     * 
     * @param payload 原始payload
     * @param initialContext 初始上下文（例如包含original值）
     * @return 解析后的payload和上下文信息
     */
    public PayloadContext resolvePayload(String payload, Map<String, String> initialContext) {
        if (payload == null || !payload.contains("{{")) {
            return new PayloadContext(payload, initialContext, null, null);
        }
        
        String resolved = payload;
        Map<String, String> context = new HashMap<>(initialContext);  // 复制初始context
        CollaboratorClient collaboratorClient = null;
        CollaboratorPayload collaboratorPayload = null;
        
        Matcher matcher = VARIABLE_PATTERN.matcher(payload);
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            
            // ===== 注入点原值变量 =====
            if ("ORIGINAL".equals(variable)) {
                String original = context.getOrDefault("original", "");
                resolved = resolved.replace("{{" + variable + "}}", original);
            } 
            else if ("ORIGINAL_URL_ENCODED".equals(variable)) {
                String original = context.getOrDefault("original", "");
                try {
                    String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8.toString());
                    resolved = resolved.replace("{{" + variable + "}}", encoded);
                } catch (Exception e) {
                    resolved = resolved.replace("{{" + variable + "}}", original);
                }
            }
            else if ("ORIGINAL_BASE64".equals(variable)) {
                String original = context.getOrDefault("original", "");
                String encoded = Base64.getEncoder().encodeToString(
                    original.getBytes(StandardCharsets.UTF_8)
                );
                resolved = resolved.replace("{{" + variable + "}}", encoded);
            }
            // ===== Collaborator变量 =====
            else if ("COLLABORATOR".equals(variable) || "DNSLOG".equals(variable)) {
                if (collaboratorClient == null) {
                    // 创建Collaborator client（只创建一次）
                    try {
                        collaboratorClient = collaborator.createClient();
                        collaboratorPayload = collaboratorClient.generatePayload();
                        context.put("collaborator_domain", collaboratorPayload.toString());
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("生成Collaborator payload失败: " + e.getMessage());
                        // 如果失败，使用占位符
                        context.put("collaborator_domain", "collaborator-error");
                    }
                }
                String domain = context.getOrDefault("collaborator_domain", "collaborator-error");
                resolved = resolved.replace("{{" + variable + "}}", domain);
            } 
            // ===== 其他变量 =====
            else {
                String value = resolveVariable(variable, context);
                resolved = resolved.replace("{{" + variable + "}}", value);
            }
        }
        
        return new PayloadContext(resolved, context, collaboratorClient, collaboratorPayload);
    }
    
    /**
     * 解析单个变量（不包括Collaborator）
     */
    private String resolveVariable(String variable, Map<String, String> context) {
        // 检查是否是函数式变量（如 BASE64:xxx）
        if (variable.contains(":")) {
            String[] parts = variable.split(":", 2);
            String function = parts[0];
            String argument = parts[1];
            
            switch (function) {
                case "BASE64":
                    return Base64.getEncoder().encodeToString(
                        argument.getBytes(StandardCharsets.UTF_8)
                    );
                    
                case "URL_ENCODE":
                    try {
                        return URLEncoder.encode(argument, StandardCharsets.UTF_8.toString());
                    } catch (Exception e) {
                        return argument;
                    }
                    
                default:
                    return "{{" + variable + "}}";
            }
        }
        
        // 简单变量
        switch (variable) {
            case "RANDOM_STRING":
                String randomStr = generateRandomString(8);
                context.put("random_string", randomStr);
                return randomStr;
                
            case "RANDOM_NUMBER":
                String randomNum = String.valueOf(System.currentTimeMillis() % 100000);
                context.put("random_number", randomNum);
                return randomNum;
                
            case "TIMESTAMP":
                String timestamp = String.valueOf(System.currentTimeMillis());
                context.put("timestamp", timestamp);
                return timestamp;
                
            case "UUID":
                String uuid = UUID.randomUUID().toString();
                context.put("uuid", uuid);
                return uuid;
                
            default:
                return "{{" + variable + "}}";
        }
    }
    
    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * Payload上下文（包含解析后的payload和Collaborator相关对象）
     */
    public static class PayloadContext {
        private final String resolvedPayload;
        private final Map<String, String> variables;
        private final CollaboratorClient collaboratorClient;
        private final CollaboratorPayload collaboratorPayload;
        
        public PayloadContext(String resolvedPayload, 
                            Map<String, String> variables,
                            CollaboratorClient collaboratorClient,
                            CollaboratorPayload collaboratorPayload) {
            this.resolvedPayload = resolvedPayload;
            this.variables = variables;
            this.collaboratorClient = collaboratorClient;
            this.collaboratorPayload = collaboratorPayload;
        }
        
        public String getResolvedPayload() {
            return resolvedPayload;
        }
        
        public Map<String, String> getVariables() {
            return variables;
        }
        
        public CollaboratorClient getCollaboratorClient() {
            return collaboratorClient;
        }
        
        public CollaboratorPayload getCollaboratorPayload() {
            return collaboratorPayload;
        }
        
        /**
         * 获取Collaborator域名
         */
        public String getCollaboratorDomain() {
            return variables.get("collaborator_domain");
        }
        
        /**
         * 是否使用了Collaborator
         */
        public boolean usesCollaborator() {
            return collaboratorClient != null && collaboratorPayload != null;
        }
        
        /**
         * 检查是否有Collaborator交互记录
         */
        public boolean hasCollaboratorInteractions() {
            if (collaboratorClient == null) {
                return false;
            }
            
            try {
                List<Interaction> interactions = collaboratorClient.getAllInteractions();
                return interactions != null && !interactions.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        
        /**
         * 获取Collaborator交互记录
         */
        public List<Interaction> getCollaboratorInteractions() {
            if (collaboratorClient == null) {
                return java.util.Collections.emptyList();
            }
            try {
                return collaboratorClient.getAllInteractions();
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        }
        
        /**
         * 获取交互类型描述
         */
        public String getInteractionDescription() {
            if (!hasCollaboratorInteractions()) {
                return "无交互记录";
            }
            
            StringBuilder desc = new StringBuilder();
            List<Interaction> interactions = getCollaboratorInteractions();
            
            for (Interaction interaction : interactions) {
                desc.append("- ").append(interaction.type()).append(": ");
                
                // 使用toString()避免枚举类型问题
                String typeStr = interaction.type().toString();
                desc.append(typeStr);
                
                if (typeStr.contains("DNS")) {
                    desc.append(" (DNS查询)");
                } else if (typeStr.contains("HTTP")) {
                    desc.append(" (HTTP请求)");
                } else if (typeStr.contains("SMTP")) {
                    desc.append(" (SMTP连接)");
                }
                
                desc.append("\n");
            }
            
            return desc.toString();
        }
    }
}


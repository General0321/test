package com.xprobe.scanner.core;

import com.xprobe.scanner.config.Configuration;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * 去重Key生成器
 * 
 * 负责为每次扫描生成唯一的标识符，用于防止重复扫描
 * 
 * 去重Key的组成：
 * 1. 请求特征：method, host, path, contentType
 * 2. 规则标识：ruleId (唯一标识规则)
 * 3. 注入点特征：注入点类型和目标的哈希值
 */
public class DeduplicationKeyGenerator {
    
    /**
     * 生成去重Key（支持颗粒度控制）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param path 请求路径
     * @param contentType Content-Type
     * @param config 扫描配置
     * @param targetIdentifier 目标标识符（参数名、Header名等）
     * @return 去重Key
     */
    /**
     * 生成去重Key（支持颗粒度控制）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param path 请求路径
     * @param contentType Content-Type
     * @param config 扫描配置
     * @param targetIdentifier 目标标识符（参数名、Header名等）
     * @return 去重Key
     */
    public static String generateKey(String method, 
                                     String host, 
                                     String path, 
                                     String contentType, 
                                     Configuration config,
                                     String targetIdentifier) {
        return generateKey(method, host, path, contentType, config, targetIdentifier, null, null);
    }

    /**
     * 生成去重Key（支持颗粒度控制/配对ID/Payload区分）
     *
     * @param payloadResolved 已解析后的payload（可选；用于区分相同注入目标但不同payload的请求）
     */
    public static String generateKey(String method,
                                     String host,
                                     String path,
                                     String contentType,
                                     Configuration config,
                                     String targetIdentifier,
                                     Integer pairId,
                                     String payloadResolved) {
        // 基础部分
        String normalizedContentType = normalizeContentType(contentType);
        String ruleId = config.getRuleId();

        // 如果提供了pairId，将其附加到ruleId后，确保不同配对生成不同的Key
        if (pairId != null) {
            ruleId = ruleId + "_p" + pairId;
        }

        // payload哈希（可选）
        String payloadHash = null;
        if (payloadResolved != null && !payloadResolved.isEmpty()) {
            payloadHash = computeHash(payloadResolved);
        }

        // 获取去重颗粒度
        Configuration.DeduplicationGranularity granularity = config.getDeduplicationGranularity();

        // 如果是AUTO，自动检测
        if (granularity == Configuration.DeduplicationGranularity.AUTO) {
            granularity = detectGranularity(config);
        }

        // 根据颗粒度生成Key
        switch (granularity) {
            case GLOBAL:
                return ruleId;
            case HOST:
                return String.format("%s|%s", ruleId, host);
            case PATH:
                String normalizedPath = normalizePathForDeduplication(path);
                return String.format("%s|%s|%s", ruleId, host, normalizedPath);
            case REQUEST:
                String normalizedPathForRequest = normalizePathForDeduplication(path);
                return String.format("%s|%s|%s|%s|%s",
                    ruleId, method, host, normalizedPathForRequest, normalizedContentType
                );
            case PARAMETER_NAME_GLOBAL:
                return String.format("%s|%s",
                    ruleId, targetIdentifier != null ? targetIdentifier : "default"
                );
            case PARAMETER_NAME_PER_PATH:
                String normalizedPathForParam = normalizePathForDeduplication(path);
                return String.format("%s|%s|%s|%s",
                    ruleId, host, normalizedPathForParam,
                    targetIdentifier != null ? targetIdentifier : "default"
                );
            case PARAMETER:
                String normalizedPathForParameter = normalizePathForDeduplication(path);
                // ✅ 关键：参数级别去重时加入payload哈希，避免不同payload互相去重
                return String.format("%s|%s|%s|%s|%s|%s|%s",
                    ruleId, method, host, normalizedPathForParameter, normalizedContentType,
                    targetIdentifier != null ? targetIdentifier : "default",
                    payloadHash != null ? payloadHash : "nopayload"
                );
            case INJECTION_POINT:
                String normalizedPathForInjection = normalizePathForDeduplication(path);
                String injectionPointHash = generateInjectionPointHash(config);
                // ✅ 关键：注入点级别去重时加入payload哈希，避免不同payload互相去重
                return String.format("%s|%s|%s|%s|%s|%s|%s",
                    ruleId, method, host, normalizedPathForInjection, normalizedContentType, injectionPointHash,
                    payloadHash != null ? payloadHash : "nopayload"
                );
            case NONE:
                return String.format("%s|%s|%d",
                    ruleId, System.currentTimeMillis(),
                    (int)(Math.random() * 1000000)
                );
            default:
                String normalizedPathForDefault = normalizePathForDeduplication(path);
                return String.format("%s|%s|%s|%s|%s",
                    ruleId, method, host, normalizedPathForDefault, normalizedContentType
                );
        }
    }

    /**
     * 兼容旧签名：生成去重Key（支持颗粒度控制和配对ID）
     */
    public static String generateKey(String method, 
                                     String host, 
                                     String path, 
                                     String contentType, 
                                     Configuration config,
                                     String targetIdentifier,
                                     Integer pairId) {
        return generateKey(method, host, path, contentType, config, targetIdentifier, pairId, null);
    }

    /**
     * 自动检测去重颗粒度
     */
    private static Configuration.DeduplicationGranularity detectGranularity(Configuration config) {
        // 检查是否有配对
        if (config.getPairs() != null && !config.getPairs().isEmpty()) {
            // 检查第一个配对的请求配置
            var firstPair = config.getPairs().get(0);
            if (firstPair.getRequestConfig() != null) {
                var elements = firstPair.getRequestConfig().getElements();
                
                if (elements == null || elements.isEmpty()) {
                    return Configuration.DeduplicationGranularity.REQUEST;
                }
                
                // 检查是否有参数级别的注入
                boolean hasParameterInjection = elements.stream()
                    .anyMatch(e -> e.isUseForInjection() && 
                                  (e.getType() == com.xprobe.scanner.config.UnifiedHttpConfig.ElementType.PARAMETER ||
                                   e.getType() == com.xprobe.scanner.config.UnifiedHttpConfig.ElementType.HEADER ||
                                   e.getType() == com.xprobe.scanner.config.UnifiedHttpConfig.ElementType.COOKIE));
                
                if (hasParameterInjection) {
                    return Configuration.DeduplicationGranularity.PARAMETER;
                }
                
                // 否则使用请求级别
                return Configuration.DeduplicationGranularity.REQUEST;
            }
        }
        
        // 默认使用请求级别
        return Configuration.DeduplicationGranularity.REQUEST;
    }
    
    /**
     * 生成注入点特征哈希
     * 
     * 为什么需要这个：
     * 同一个规则可能有多个注入点，需要分别去重
     * 例如：同时在参数和Header注入，应该算两次不同的扫描
     */
    private static String generateInjectionPointHash(Configuration config) {
        StringBuilder signature = new StringBuilder();
        
        // 基于配对的注入点
        if (config.getPairs() != null && !config.getPairs().isEmpty()) {
            for (var pair : config.getPairs()) {
                if (pair.getRequestConfig() != null) {
                    var elements = pair.getRequestConfig().getElements();
                    if (elements != null) {
                        for (var element : elements) {
                            if (element.isUseForInjection()) {
                                signature.append(String.format("%s:%s:%s:%s;",
                                    element.getType() != null ? element.getType() : "",
                                    element.getName() != null ? element.getName() : "",
                                    element.getInjectionTarget() != null ? element.getInjectionTarget() : "",
                                    element.getPayloads() != null ? element.getPayloads().size() : 0
                                ));
                            }
                        }
                    }
                }
            }
        }
        
        if (signature.length() == 0) {
            return "default";
        }
        
        // 计算SHA-256哈希（避免Key过长）
        return computeHash(signature.toString());
    }
    
    /**
     * 计算字符串的SHA-256哈希值（前8位）
     */
    private static String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // 只取前8位（16个十六进制字符）
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // 如果哈希失败，返回输入的简单哈希
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * 清理路径（去除查询字符串和fragment）
     * ✅ 修复：确保去除查询字符串（?之后）和fragment（#之后）
     */
    private static String cleanPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // 先去除fragment（#之后）
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex != -1) {
            path = path.substring(0, fragmentIndex);
        }
        
        // 再去除查询字符串（?之后）
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }
        
        // 如果路径为空，返回默认路径
        if (path.isEmpty()) {
            return "/";
        }
        
        return path;
    }
    
    /**
     * 标准化路径（用于PATH级别去重）
     * 提取路径的基础结构，忽略参数值
     * ✅ 修复：确保正确处理查询字符串，只保留路径部分
     * 例如：
     * - /ztbox?action=zpblog&appname=pcsearch → /ztbox
     * - /it/u=123&fm=456 → /it/u=*&fm=*
     */
    private static String normalizePathForDeduplication(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // ✅ 修复：先去除查询字符串和fragment（确保只保留路径部分）
        String cleanPath = cleanPath(path);
        
        // ✅ 修复：如果路径中包含 `=`，说明路径中嵌入了参数（如 /it/u=123&fm=456 或 /it/u=123）
        // 标准化：将参数值替换为 *
        // 注意：需要确保 `=` 在路径部分，而不是域名中的点（如 `example.com`）
        if (cleanPath.contains("=")) {
            // 检查 `=` 是否在路径部分（在最后一个 `/` 之后）
            int equalsIndex = cleanPath.indexOf('=');
            int lastSlashIndex = cleanPath.lastIndexOf('/');
            
            if (equalsIndex > lastSlashIndex) {
                // `=` 在路径部分，使用正则表达式将参数值替换为 *
                // 匹配模式：参数名=参数值，将参数值替换为 *
                // 注意：`[^&]+` 会匹配一个或多个非 `&` 字符，包括到字符串结尾
                // 例如：
                // - `/it/u=1085880584,4000660480&fm=225` → `/it/u=*&fm=*`
                // - `/it/u=123` → `/it/u=*`
                String normalized = cleanPath.replaceAll("=([^&]+)", "=*");
                return normalized;
            }
        }
        
        return cleanPath;
    }
    
    /**
     * 标准化Content-Type
     */
    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "application/x-www-form-urlencoded";
        }
        
        // 去除charset等参数，只保留主类型
        String normalized = contentType.split(";")[0].trim().toLowerCase();
        
        // 标准化常见类型
        if (normalized.contains("json")) {
            return "application/json";
        } else if (normalized.contains("xml")) {
            return "application/xml";
        } else if (normalized.contains("form")) {
            return "application/x-www-form-urlencoded";
        } else if (normalized.contains("multipart")) {
            return "multipart/form-data";
        }
        
        return normalized;
    }
}


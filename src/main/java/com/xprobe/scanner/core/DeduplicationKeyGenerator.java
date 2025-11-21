package com.xprobe.scanner.core;

import com.xprobe.scanner.config.Configuration;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
        return generateKey(method, host, path, contentType, config, targetIdentifier, null);
    }

    /**
     * 生成去重Key（支持颗粒度控制和配对ID）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param path 请求路径
     * @param contentType Content-Type
     * @param config 扫描配置
     * @param targetIdentifier 目标标识符（参数名、Header名等）
     * @param pairId 配对ID（可选，用于区分同一规则下的不同配对）
     * @return 去重Key
     */
    public static String generateKey(String method, 
                                     String host, 
                                     String path, 
                                     String contentType, 
                                     Configuration config,
                                     String targetIdentifier,
                                     Integer pairId) {
        // 基础部分
        String cleanPath = cleanPath(path);
        String normalizedContentType = normalizeContentType(contentType);
        String ruleId = config.getRuleId();
        
        // 如果提供了pairId，将其附加到ruleId后，确保不同配对生成不同的Key
        if (pairId != null) {
            ruleId = ruleId + "_p" + pairId;
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
                // 全局：整个规则只测试一次
                return ruleId;
                
            case HOST:
                // 主机级：每个主机只测试一次
                return String.format("%s|%s", ruleId, host);
                
            case PATH:
                // 路径级：每个路径只测试一次
                return String.format("%s|%s|%s", ruleId, host, cleanPath);
                
            case REQUEST:
                // 请求级：每个完整请求只测试一次
                return String.format("%s|%s|%s|%s|%s",
                    ruleId, method, host, cleanPath, normalizedContentType
                );
                
            case PARAMETER_NAME_GLOBAL:
                // 参数名(全局)：相同参数名只测试一次
                return String.format("%s|%s",
                    ruleId, targetIdentifier != null ? targetIdentifier : "default"
                );
                
            case PARAMETER_NAME_PER_PATH:
                // 参数名(路径级)：每个路径下的参数名分别测试
                return String.format("%s|%s|%s|%s",
                    ruleId, host, cleanPath, 
                    targetIdentifier != null ? targetIdentifier : "default"
                );
                
            case PARAMETER:
                // 参数级：每个请求中的参数分别扫描
                return String.format("%s|%s|%s|%s|%s|%s",
                    ruleId, method, host, cleanPath, normalizedContentType,
                    targetIdentifier != null ? targetIdentifier : "default"
                );
                
            case INJECTION_POINT:
                // 注入点级：每个注入点分别扫描
                String injectionPointHash = generateInjectionPointHash(config);
                return String.format("%s|%s|%s|%s|%s|%s",
                    ruleId, method, host, cleanPath, normalizedContentType, injectionPointHash
                );
                
            case NONE:
                // 无去重：每次都测试（Fuzzing模式）
                return String.format("%s|%s|%d",
                    ruleId, System.currentTimeMillis(), 
                    (int)(Math.random() * 1000000)
                );
                
            default:
                // 默认使用REQUEST级别
                return String.format("%s|%s|%s|%s|%s",
                    ruleId, method, host, cleanPath, normalizedContentType
                );
        }
    }
    
    /**
     * 向后兼容的方法（不指定targetIdentifier）
     */
    public static String generatePassiveScanKey(String method, 
                                               String host, 
                                               String path, 
                                               String contentType, 
                                               Configuration config) {
        return generateKey(method, host, path, contentType, config, null, null);
    }
    
    /**
     * 自动检测去重颗粒度
     */
    private static Configuration.DeduplicationGranularity detectGranularity(Configuration config) {
        // ✅ 新架构：检查是否有配对
        if (config.getPairs() != null && !config.getPairs().isEmpty()) {
            // 新架构：检查第一个配对的请求配置
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
        
        // ✅ 旧架构：检查注入点
        java.util.List<Configuration.InjectionPoint> points = config.getInjectionPoints();
        
        if (points == null || points.isEmpty()) {
            return Configuration.DeduplicationGranularity.REQUEST;
        }
        
        // 只有一个注入点
        if (points.size() == 1) {
            Configuration.InjectionPoint point = points.get(0);
            String type = point.getPointType();
            
            if (type == null) {
                return Configuration.DeduplicationGranularity.REQUEST;
            }
            
            switch (type) {
                case "Parameter Value":
                case "Request Header Value":
                case "Cookie Value":
                    // 这些类型通常需要对每个目标分别扫描
                    return Configuration.DeduplicationGranularity.PARAMETER;
                    
                case "Request Body":
                case "URL Path":
                case "Query String":
                    // 这些类型通常整体替换
                    return Configuration.DeduplicationGranularity.REQUEST;
                    
                default:
                    return Configuration.DeduplicationGranularity.REQUEST;
            }
        }
        
        // 多个注入点 → 注入点级别
        return Configuration.DeduplicationGranularity.INJECTION_POINT;
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
        
        // ✅ 新架构：基于配对的注入点
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
        
        // ✅ 旧架构：基于注入点列表
        if (signature.length() == 0) {
            java.util.List<Configuration.InjectionPoint> injectionPoints = config.getInjectionPoints();
            if (injectionPoints != null && !injectionPoints.isEmpty()) {
                signature.append(injectionPoints.stream()
                    .map(point -> String.format("%s:%s:%s",
                        point.getPointType() != null ? point.getPointType() : "",
                        point.getTargetName() != null ? point.getTargetName() : "",
                        point.getInjectionStrategy() != null ? point.getInjectionStrategy() : ""
                    ))
                    .collect(Collectors.joining(";"))
                );
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
     * 清理路径（去除查询字符串）
     */
    private static String cleanPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
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


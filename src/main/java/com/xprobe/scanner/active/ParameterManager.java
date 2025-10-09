package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参数管理器 - 统一管理全局参数和增量传递
 * 
 * 功能：
 * 1. 管理全局自定义参数字典
 * 2. 追踪已传递给 Arjun 的参数（增量控制）
 * 3. 参数导入导出
 * 4. 参数合并和去重
 */
public class ParameterManager {
    private final MontoyaApi api;
    
    // 全局自定义参数字典（用户上传的参数，应用于所有域名）
    private final Set<String> globalCustomParameters = ConcurrentHashMap.newKeySet();
    
    // 已传递给 Arjun 的参数记录（按 method+host+contentType+endpoint 维度）
    // Key: method|host|contentType|endpoint, Value: 已扫描的参数集合
    private final Map<String, Set<String>> arjunScannedParameters = new ConcurrentHashMap<>();
    
    public ParameterManager(MontoyaApi api) {
        this.api = api;
        // ✅ 不再初始化默认参数，完全依靠从流量收集和用户自定义
        // 特殊参数（用于WAF绕过）由ArjunDictionary.getSpecialParams()提供
        api.logging().raiseDebugEvent("参数管理器已初始化（无默认参数，依靠流量收集）");
    }
    
    // ========== 全局参数管理 ==========
    
    /**
     * 添加全局自定义参数
     */
    public void addGlobalParameter(String parameter) {
        if (isValidParameterName(parameter)) {
            globalCustomParameters.add(parameter.trim());
            api.logging().raiseInfoEvent("添加全局参数: " + parameter);
        } else {
            api.logging().raiseErrorEvent("无效的参数名: " + parameter);
        }
    }
    
    /**
     * 批量添加全局参数
     */
    public void addGlobalParameters(Collection<String> parameters) {
        int count = 0;
        for (String param : parameters) {
            if (isValidParameterName(param)) {
                globalCustomParameters.add(param.trim());
                count++;
            }
        }
        api.logging().raiseInfoEvent("批量添加了 " + count + " 个全局参数");
    }
    
    /**
     * 获取全局参数
     */
    public Set<String> getGlobalParameters() {
        return new HashSet<>(globalCustomParameters);
    }
    
    /**
     * 清空全局自定义参数
     */
    public void clearGlobalCustomParameters() {
        globalCustomParameters.clear();
        api.logging().raiseInfoEvent("清空全局自定义参数");
    }
    
    // ========== 增量参数管理 ==========
    
    /**
     * 获取增量参数（未被 Arjun 扫描过的参数）
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param contentType Content-Type
     * @param endpoint 接口路径
     * @param collectedParams 从被动流量收集的参数
     * @return 需要传递给 Arjun 的增量参数
     */
    public Set<String> getIncrementalParameters(String method, String host, String contentType,
                                               String endpoint, Set<String> collectedParams) {
        // 合并所有参数：收集的参数 + 全局参数
        Set<String> allParams = new HashSet<>(collectedParams);
        allParams.addAll(globalCustomParameters);
        
        // 获取已扫描的参数
        String key = generateKey(method, host, contentType, endpoint);
        Set<String> scanned = arjunScannedParameters.get(key);
        
        if (scanned == null || scanned.isEmpty()) {
            // 首次扫描，返回所有参数
            return allParams;
        }
        
        // 计算增量：移除已扫描的参数
        Set<String> incremental = new HashSet<>(allParams);
        incremental.removeAll(scanned);
        
        return incremental;
    }
    
    /**
     * 标记参数为已扫描
     * 
     * @param method HTTP方法
     * @param host 主机名
     * @param contentType Content-Type
     * @param endpoint 接口路径
     * @param parameters 已扫描的参数
     */
    public void markParametersAsScanned(String method, String host, String contentType,
                                       String endpoint, Set<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        
        String key = generateKey(method, host, contentType, endpoint);
        arjunScannedParameters.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                             .addAll(parameters);
        
        api.logging().raiseDebugEvent(String.format(
            "标记已扫描: %s %s (%s) %s, 参数数=%d",
            method, host, contentType, endpoint, parameters.size()
        ));
    }
    
    /**
     * 获取已扫描的参数
     */
    public Set<String> getScannedParameters(String method, String host, String contentType, 
                                           String endpoint) {
        String key = generateKey(method, host, contentType, endpoint);
        Set<String> scanned = arjunScannedParameters.get(key);
        return scanned != null ? new HashSet<>(scanned) : new HashSet<>();
    }
    
    /**
     * 检查指定endpoint是否已被扫描过（任意method和contentType组合）
     */
    public boolean hasBeenScanned(String host, String endpoint) {
        String prefix = host + "|";
        String suffix = "|" + endpoint;
        
        for (String key : arjunScannedParameters.keySet()) {
            if (key.contains(prefix) && key.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 清空已扫描记录
     */
    public void clearScannedParameters() {
        arjunScannedParameters.clear();
        api.logging().raiseInfoEvent("清空 Arjun 扫描记录");
    }
    
    // ========== 参数导入导出 ==========
    
    /**
     * 从文件导入全局参数
     * 
     * @param filePath 参数文件路径，每行一个参数
     * @return 导入的参数数量
     */
    public int importGlobalParametersFromFile(String filePath) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 验证并添加参数
                if (isValidParameterName(line)) {
                    globalCustomParameters.add(line);
                    count++;
                } else {
                    api.logging().raiseErrorEvent(
                        String.format("第 %d 行参数名无效: %s", lineNum, line));
                }
            }
        }
        
        api.logging().raiseInfoEvent(
            String.format("从文件导入了 %d 个全局参数: %s", count, filePath));
        return count;
    }
    
    /**
     * 导出全局参数到文件
     * 
     * @param filePath 导出文件路径
     */
    public void exportGlobalParametersToFile(String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // 写入文件头
            writer.println("# XProbe 全局参数字典");
            writer.println("# 生成时间: " + new Date());
            writer.println("# 参数数量: " + globalCustomParameters.size());
            writer.println();
            
            // 排序后写入（便于阅读和版本控制）
            List<String> sorted = new ArrayList<>(globalCustomParameters);
            Collections.sort(sorted);
            
            for (String param : sorted) {
                writer.println(param);
            }
        }
        
        api.logging().raiseInfoEvent(
            String.format("导出了 %d 个全局参数到: %s", 
                globalCustomParameters.size(), filePath));
    }
    
    /**
     * 导出已扫描参数记录
     */
    public void exportScannedParametersToFile(String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("# XProbe Arjun 扫描记录");
            writer.println("# 生成时间: " + new Date());
            writer.println();
            
            // 按主域名:接口排序
            List<String> keys = new ArrayList<>(arjunScannedParameters.keySet());
            Collections.sort(keys);
            
            for (String key : keys) {
                Set<String> params = arjunScannedParameters.get(key);
                writer.println("## " + key);
                
                List<String> sorted = new ArrayList<>(params);
                Collections.sort(sorted);
                for (String param : sorted) {
                    writer.println("  - " + param);
                }
                writer.println();
            }
        }
        
        api.logging().raiseInfoEvent("导出扫描记录到: " + filePath);
    }
    
    // ========== 统计信息 ==========
    
    /**
     * 获取统计信息
     */
    public ManagerStatistics getStatistics() {
        int scannedEndpointCount = arjunScannedParameters.size();
        int totalScannedParams = arjunScannedParameters.values().stream()
            .mapToInt(Set::size)
            .sum();
        
        return new ManagerStatistics(
            globalCustomParameters.size(),
            scannedEndpointCount,
            totalScannedParams
        );
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 生成 method+host+contentType+endpoint 的唯一键
     */
    private String generateKey(String method, String host, String contentType, String endpoint) {
        String normalizedContentType = normalizeContentType(contentType);
        return method + "|" + host + "|" + normalizedContentType + "|" + endpoint;
    }
    
    /**
     * 标准化 Content-Type（确保去重Key一致）
     * 
     * 作用：
     * 1. 移除字符集等额外参数（如 "; charset=UTF-8"）
     * 2. 统一常见类型的表示
     * 3. 确保去重Key的一致性
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "application/x-www-form-urlencoded";
        }
        
        // 转小写并移除空格
        String lower = contentType.toLowerCase().trim();
        
        // 移除字符集等额外参数（如 "; charset=UTF-8"）
        int semicolonIndex = lower.indexOf(';');
        if (semicolonIndex > 0) {
            lower = lower.substring(0, semicolonIndex).trim();
        }
        
        // 标准化常见类型
        if (lower.contains("json")) {
            return "application/json";
        } else if (lower.contains("xml")) {
            return "application/xml";
        } else if (lower.contains("form")) {
            return "application/x-www-form-urlencoded";
        } else if (lower.contains("multipart")) {
            return "multipart/form-data";
        }
        
        // 默认返回原值（但已移除额外参数）
        return lower.isEmpty() ? "application/x-www-form-urlencoded" : lower;
    }
    
    /**
     * 验证参数名是否有效
     */
    private boolean isValidParameterName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        // 参数名只能包含字母、数字、下划线、中划线
        return name.matches("[a-zA-Z0-9_-]+");
    }
    
    // ========== 内部类 ==========
    
    /**
     * 管理器统计信息
     */
    public static class ManagerStatistics {
        private final int globalParameterCount;
        private final int scannedEndpointCount;
        private final int totalScannedParameterCount;
        
        public ManagerStatistics(int globalParameterCount, int scannedEndpointCount, 
                                int totalScannedParameterCount) {
            this.globalParameterCount = globalParameterCount;
            this.scannedEndpointCount = scannedEndpointCount;
            this.totalScannedParameterCount = totalScannedParameterCount;
        }
        
        public int getGlobalParameterCount() { return globalParameterCount; }
        public int getScannedEndpointCount() { return scannedEndpointCount; }
        public int getTotalScannedParameterCount() { return totalScannedParameterCount; }
        
        @Override
        public String toString() {
            return String.format("全局参数: %d, 已扫描接口: %d, 已扫描参数总数: %d",
                globalParameterCount, scannedEndpointCount, totalScannedParameterCount);
        }
    }
}


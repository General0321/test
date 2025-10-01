# 参数管理优化修复方案

## 修复 1: 添加主域名分组功能

### 方案 A: 添加配置选项（推荐）

在 `RealtimeScanner` 中添加配置：

```java
public class RealtimeScanner {
    // 添加配置选项
    private volatile boolean useMainDomainGrouping = false;  // 默认禁用，保持向后兼容
    
    /**
     * 设置是否使用主域名分组
     * @param enabled true=按主域名分组, false=按完整host分组
     */
    public void setUseMainDomainGrouping(boolean enabled) {
        this.useMainDomainGrouping = enabled;
        api.logging().raiseInfoEvent("主域名分组: " + (enabled ? "已启用" : "已禁用"));
    }
    
    public boolean isUseMainDomainGrouping() {
        return useMainDomainGrouping;
    }
    
    // 修改 processNewRequest
    public void processNewRequest(HttpRequest request) {
        try {
            String url = request.url();
            String fullHost = new URL(url).getHost();
            
            // 根据配置决定使用主域名还是完整host
            String hostKey = useMainDomainGrouping ? extractMainDomain(fullHost) : fullHost;
            
            // ... 其余逻辑使用 hostKey
        }
    }
}
```

### 方案 B: 在扫描时合并主域名参数

不改变存储结构，在扫描时合并：

```java
/**
 * 获取主域名下所有参数（合并所有子域名）
 */
private Set<String> getParametersForMainDomain(String mainDomain) {
    Set<String> params = new HashSet<>();
    
    // 遍历所有 host，找到同主域名的
    for (Map.Entry<String, HostData> entry : hostDataMap.entrySet()) {
        String host = entry.getKey();
        if (extractMainDomain(host).equalsIgnoreCase(mainDomain)) {
            params.addAll(entry.getValue().getParameters());
            
            // 也可以包括已被 Arjun 扫描过的参数
            for (String endpoint : entry.getValue().getEndpoints()) {
                params.addAll(entry.getValue().getArjunScannedParams(endpoint));
            }
        }
    }
    
    return params;
}

// 在 performManualArjunScan 中使用
private void performManualArjunScan() {
    // ...
    for (Map.Entry<String, List<HttpRequest>> entry : hostToRequests.entrySet()) {
        String host = entry.getKey();
        String mainDomain = extractMainDomain(host);
        HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
        
        for (HttpRequest req : entry.getValue()) {
            String endpoint = extractEndpoint(req.url());
            
            // 使用主域名级别的参数合并
            Set<String> hostParams = getParametersForMainDomain(mainDomain);  // ← 改这里
            hostParams.addAll(globalCustomDictionary);
            // ...
        }
    }
}
```

---

## 修复 2: 添加参数导入/导出功能

在 `RealtimeScanner` 中添加：

```java
/**
 * 从文件导入参数到全局字典
 * 
 * @param filePath 参数文件路径，每行一个参数名
 * 支持 # 开头的注释
 */
public void importGlobalParametersFromFile(String filePath) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
        String line;
        int count = 0;
        int lineNum = 0;
        
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            
            // 跳过空行和注释
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // 验证参数名（可选）
            if (isValidParameterName(line)) {
                globalCustomDictionary.add(line);
                count++;
            } else {
                api.logging().raiseErrorEvent("第 " + lineNum + " 行参数名无效: " + line);
            }
        }
        
        api.logging().raiseInfoEvent("从文件导入了 " + count + " 个全局参数");
        
    } catch (IOException e) {
        api.logging().raiseErrorEvent("导入参数文件失败: " + e.getMessage());
    }
}

/**
 * 导出全局参数到文件
 */
public void exportGlobalParametersToFile(String filePath) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
        // 写入文件头
        writer.println("# XProbe 全局参数字典");
        writer.println("# 生成时间: " + new java.util.Date());
        writer.println("# 参数数量: " + globalCustomDictionary.size());
        writer.println();
        
        // 排序后写入（便于阅读）
        List<String> sorted = new ArrayList<>(globalCustomDictionary);
        Collections.sort(sorted);
        
        for (String param : sorted) {
            writer.println(param);
        }
        
        api.logging().raiseInfoEvent("导出了 " + globalCustomDictionary.size() + " 个全局参数到: " + filePath);
        
    } catch (IOException e) {
        api.logging().raiseErrorEvent("导出参数文件失败: " + e.getMessage());
    }
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

/**
 * 导出某个 host 收集的参数
 */
public void exportHostParametersToFile(String host, String filePath) {
    HostData hostData = hostDataMap.get(host);
    if (hostData == null) {
        api.logging().raiseErrorEvent("Host 不存在: " + host);
        return;
    }
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
        writer.println("# Host: " + host);
        writer.println("# 主域名: " + hostData.getMainDomain());
        writer.println("# 参数数量: " + hostData.getParameters().size());
        writer.println();
        
        List<String> sorted = new ArrayList<>(hostData.getParameters());
        Collections.sort(sorted);
        
        for (String param : sorted) {
            writer.println(param);
        }
        
        api.logging().raiseInfoEvent("导出了 " + hostData.getParameters().size() + " 个参数到: " + filePath);
        
    } catch (IOException e) {
        api.logging().raiseErrorEvent("导出参数文件失败: " + e.getMessage());
    }
}

/**
 * 导出主域名下所有参数（合并子域名）
 */
public void exportMainDomainParametersToFile(String mainDomain, String filePath) {
    Set<String> params = getParametersForMainDomain(mainDomain);
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
        writer.println("# 主域名: " + mainDomain);
        writer.println("# 参数数量: " + params.size());
        writer.println("# 包含所有子域名的参数");
        writer.println();
        
        List<String> sorted = new ArrayList<>(params);
        Collections.sort(sorted);
        
        for (String param : sorted) {
            writer.println(param);
        }
        
        api.logging().raiseInfoEvent("导出了 " + params.size() + " 个主域名参数到: " + filePath);
        
    } catch (IOException e) {
        api.logging().raiseErrorEvent("导出参数文件失败: " + e.getMessage());
    }
}
```

---

## 修复 3: 提取参数管理器（重构）

### 创建独立的参数管理器类

```java
package com.xprobe.scanner.active;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参数管理器 - 统一管理所有参数
 */
public class ParameterManager {
    // 全局参数字典（应用于所有域名）
    private final Set<String> globalParameters = ConcurrentHashMap.newKeySet();
    
    // Host 参数字典（按 host 或主域名分组）
    private final Map<String, Set<String>> hostParameters = new ConcurrentHashMap<>();
    
    // 配置选项
    private volatile boolean useMainDomainGrouping = false;
    
    /**
     * 添加全局参数
     */
    public void addGlobalParameter(String param) {
        if (isValidParameterName(param)) {
            globalParameters.add(param.trim());
        }
    }
    
    /**
     * 批量添加全局参数
     */
    public void addGlobalParameters(Collection<String> params) {
        for (String param : params) {
            addGlobalParameter(param);
        }
    }
    
    /**
     * 添加 Host 参数
     */
    public void addHostParameter(String host, String param) {
        String key = useMainDomainGrouping ? extractMainDomain(host) : host;
        hostParameters.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(param);
    }
    
    /**
     * 获取全局参数
     */
    public Set<String> getGlobalParameters() {
        return new HashSet<>(globalParameters);
    }
    
    /**
     * 获取 Host 参数
     */
    public Set<String> getHostParameters(String host) {
        String key = useMainDomainGrouping ? extractMainDomain(host) : host;
        Set<String> params = hostParameters.get(key);
        return params != null ? new HashSet<>(params) : new HashSet<>();
    }
    
    /**
     * 获取合并后的参数（全局 + Host）
     */
    public Set<String> getMergedParameters(String host) {
        Set<String> merged = new HashSet<>(globalParameters);
        merged.addAll(getHostParameters(host));
        return merged;
    }
    
    /**
     * 获取主域名下所有参数（合并所有子域名）
     */
    public Set<String> getMainDomainParameters(String mainDomain) {
        Set<String> params = new HashSet<>(globalParameters);
        
        for (Map.Entry<String, Set<String>> entry : hostParameters.entrySet()) {
            if (extractMainDomain(entry.getKey()).equalsIgnoreCase(mainDomain)) {
                params.addAll(entry.getValue());
            }
        }
        
        return params;
    }
    
    /**
     * 从文件导入全局参数
     */
    public int importGlobalParametersFromFile(String filePath) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (isValidParameterName(line)) {
                        globalParameters.add(line);
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * 导出全局参数到文件
     */
    public void exportGlobalParametersToFile(String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("# XProbe 全局参数字典");
            writer.println("# 参数数量: " + globalParameters.size());
            writer.println();
            
            List<String> sorted = new ArrayList<>(globalParameters);
            Collections.sort(sorted);
            sorted.forEach(writer::println);
        }
    }
    
    /**
     * 清空全局参数
     */
    public void clearGlobalParameters() {
        globalParameters.clear();
    }
    
    /**
     * 获取统计信息
     */
    public ParameterStatistics getStatistics() {
        return new ParameterStatistics(
            globalParameters.size(),
            hostParameters.size(),
            hostParameters.values().stream().mapToInt(Set::size).sum()
        );
    }
    
    /**
     * 设置主域名分组
     */
    public void setUseMainDomainGrouping(boolean enabled) {
        this.useMainDomainGrouping = enabled;
    }
    
    private boolean isValidParameterName(String name) {
        return name != null && !name.isEmpty() && name.matches("[a-zA-Z0-9_-]+");
    }
    
    private String extractMainDomain(String host) {
        if (host == null || host.isEmpty()) return host;
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }
    
    /**
     * 参数统计信息
     */
    public static class ParameterStatistics {
        private final int globalCount;
        private final int hostCount;
        private final int totalHostParameters;
        
        public ParameterStatistics(int globalCount, int hostCount, int totalHostParameters) {
            this.globalCount = globalCount;
            this.hostCount = hostCount;
            this.totalHostParameters = totalHostParameters;
        }
        
        public int getGlobalCount() { return globalCount; }
        public int getHostCount() { return hostCount; }
        public int getTotalHostParameters() { return totalHostParameters; }
        
        @Override
        public String toString() {
            return String.format("全局参数: %d, Host数: %d, 总参数: %d", 
                globalCount, hostCount, totalHostParameters);
        }
    }
}
```

### 在 RealtimeScanner 中使用

```java
public class RealtimeScanner {
    // 使用参数管理器替代原有字段
    private final ParameterManager parameterManager;
    
    public RealtimeScanner(MontoyaApi api, ConfigurationManager configManager, GlobalFilter globalFilter) {
        this.api = api;
        this.configManager = configManager;
        this.toolConfig = new ExternalToolConfig();
        this.globalFilter = globalFilter;
        this.arjunIntegration = new ArjunIntegration(api, toolConfig);
        this.parameterManager = new ParameterManager();  // ← 使用参数管理器
        
        startBackgroundProcessing();
        loadArjunStateSafely();
    }
    
    // 公开参数管理器的方法
    public void addGlobalCustomParameter(String parameter) {
        parameterManager.addGlobalParameter(parameter);
        api.logging().raiseInfoEvent("添加全局自定义参数: " + parameter);
    }
    
    public Set<String> getGlobalCustomDictionary() {
        return parameterManager.getGlobalParameters();
    }
    
    public void importGlobalParametersFromFile(String filePath) {
        try {
            int count = parameterManager.importGlobalParametersFromFile(filePath);
            api.logging().raiseInfoEvent("从文件导入了 " + count + " 个全局参数");
        } catch (IOException e) {
            api.logging().raiseErrorEvent("导入参数文件失败: " + e.getMessage());
        }
    }
    
    // 在扫描时使用参数管理器
    private void performManualArjunScan() {
        // ...
        Set<String> hostParams = parameterManager.getMergedParameters(host);
        // ...
    }
}
```

---

## 修复优先级

### 高优先级（建议立即实现）

1. **✅ 修复 1 - 方案 B**：在扫描时合并主域名参数
   - 不改变现有结构
   - 影响小，风险低
   - 立即提升扫描效果

2. **✅ 修复 2**：添加参数导入/导出功能
   - 用户强需求
   - 实现简单
   - 立即可用

### 中优先级（下个版本）

3. **✅ 修复 1 - 方案 A**：添加主域名分组配置
   - 提供用户选择
   - 需要UI配置界面

### 低优先级（长期优化）

4. **✅ 修复 3**：提取参数管理器
   - 代码重构
   - 需要充分测试
   - 改善可维护性

---

## 实施建议

### 第一步：快速修复（本周）

实现修复 1-方案B 和修复 2：

```java
// 在 RealtimeScanner 中添加
private Set<String> getParametersForMainDomain(String mainDomain) { }
public void importGlobalParametersFromFile(String filePath) { }
public void exportGlobalParametersToFile(String filePath) { }
```

### 第二步：UI 支持（下周）

在 UI 中添加：
- 参数导入/导出按钮
- 主域名分组开关
- 参数统计显示

### 第三步：长期重构（下个版本）

提取 ParameterManager，重构代码结构。

---

**文档生成时间**: 2025-10-01


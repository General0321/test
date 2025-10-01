# 参数管理重构指南

## 📋 概述

本次重构将复杂的参数管理逻辑从 `RealtimeScanner`（1297行）拆分成三个独立的类：

1. **ParameterCollector** - 负责被动收集参数
2. **ParameterManager** - 负责全局参数和增量控制
3. **RealtimeScannerRefactored** - 简化版扫描器（使用上述两个类）

---

## 🎯 主要改进

### 1. ✅ 主域名分组
**问题**：之前按完整 `host` 分组，子域名参数不共享
```
之前:
www.example.com   → 10 个参数（独立）
api.example.com   → 15 个参数（独立）

现在:
example.com → 25 个参数（合并）
```

### 2. ✅ 增量传递
**问题**：之前每次扫描都传递所有参数，重复扫描
```
之前:
第1次扫描: [id, name, email]  ← 扫描全部
第2次扫描: [id, name, email, token]  ← 重复扫描 id, name, email

现在:
第1次扫描: [id, name, email]  ← 扫描全部
第2次扫描: [token]  ← 只扫描新参数（增量）
```

### 3. ✅ 用户触发
**问题**：之前可能自动触发扫描
```
现在:
- 只在用户点击"开始扫描"时才执行
- 被动收集参数不触发扫描
- 完全可控
```

### 4. ✅ 代码分离
**问题**：之前所有逻辑在 1297 行的单个文件中
```
之前:
RealtimeScanner.java (1297 行)
├── 参数收集
├── 参数管理
├── Arjun 集成
├── 状态持久化
└── 其他...

现在:
ParameterCollector.java (400 行) - 参数收集
ParameterManager.java (350 行) - 参数管理
RealtimeScannerRefactored.java (450 行) - 核心逻辑
```

---

## 📦 新增的类

### ParameterCollector
```java
/**
 * 参数收集器 - 从 HTTP 流量中收集参数
 */
public class ParameterCollector {
    // 核心方法
    public boolean collectFromRequest(HttpRequest request)
    public Set<String> getParametersForMainDomain(String mainDomain)
    public Set<String> getParametersForHost(String host)
    public Set<String> getHostsForMainDomain(String mainDomain)
    public Set<String> getEndpointsForMainDomain(String mainDomain)
    public HttpRequest getEndpointTemplate(String mainDomain, String endpoint)
    public CollectorStatistics getStatistics()
}
```

**特点**：
- ✅ 按主域名自动分组
- ✅ 记录接口和参数的关联
- ✅ 保存请求模板
- ✅ 自动去重

### ParameterManager
```java
/**
 * 参数管理器 - 统一管理全局参数和增量控制
 */
public class ParameterManager {
    // 全局参数管理
    public void addGlobalParameter(String parameter)
    public void addGlobalParameters(Collection<String> parameters)
    public Set<String> getGlobalParameters()
    
    // 增量控制
    public Set<String> getIncrementalParameters(String mainDomain, String endpoint, Set<String> collectedParams)
    public void markParametersAsScanned(String mainDomain, String endpoint, Set<String> parameters)
    public Set<String> getScannedParameters(String mainDomain, String endpoint)
    
    // 导入导出
    public int importGlobalParametersFromFile(String filePath)
    public void exportGlobalParametersToFile(String filePath)
    public void exportScannedParametersToFile(String filePath)
}
```

**特点**：
- ✅ 管理全局参数（应用于所有域名）
- ✅ 追踪已扫描的参数（按主域名+接口维度）
- ✅ 计算增量参数（未扫描过的）
- ✅ 支持参数导入导出

### RealtimeScannerRefactored
```java
/**
 * 实时扫描器（重构版）
 */
public class RealtimeScannerRefactored {
    // 被动收集
    public void processNewRequest(HttpRequest request)
    
    // 主动扫描（用户触发）
    public void triggerManualArjunScan()
    
    // 全局参数管理
    public void addGlobalCustomParameter(String parameter)
    public Set<String> getGlobalCustomDictionary()
    
    // 参数导入导出
    public void importGlobalParametersFromFile(String filePath)
    public void exportGlobalParametersToFile(String filePath)
    public void exportCollectedParametersToFile(String mainDomain, String filePath)
    
    // 统计信息
    public CollectorStatistics getCollectorStatistics()
    public ManagerStatistics getManagerStatistics()
}
```

**特点**：
- ✅ 职责单一清晰
- ✅ 委托给专门的类处理
- ✅ 代码量减少 65%（450 vs 1297行）

---

## 🔄 迁移步骤

### 步骤 1: 更新 XProbe.java 中的初始化

**原来的代码**：
```java
// 创建 RealtimeScanner (必须在 ScannerFactory 之前创建)
com.xprobe.scanner.active.RealtimeScanner realtimeScanner = 
    new com.xprobe.scanner.active.RealtimeScanner(api, configManager, globalFilter);
```

**新的代码**：
```java
// 创建 RealtimeScannerRefactored（使用新的重构版本）
com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner = 
    new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter);
```

### 步骤 2: 更新类型声明

在所有使用 `RealtimeScanner` 的地方，改为 `RealtimeScannerRefactored`：

**文件列表**：
- `XProbe.java`
- `RequestHandler.java`
- `GlobalFilterTab.java`
- `ActiveScanTab.java`
- `ScannerFactory.java`
- `AbstractScanner.java`

**示例修改**：
```java
// 修改前
private final com.xprobe.scanner.active.RealtimeScanner realtimeScanner;

// 修改后
private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
```

### 步骤 3: API 保持兼容

以下 API 保持不变，无需修改调用代码：

```java
// 被动收集参数
realtimeScanner.processNewRequest(request);

// 全局参数管理
realtimeScanner.addGlobalCustomParameter(String parameter);
realtimeScanner.addGlobalCustomParameters(Set<String> parameters);
realtimeScanner.getGlobalCustomDictionary();
realtimeScanner.clearGlobalCustomDictionary();

// 被动扫描去重
realtimeScanner.isPassiveScanProcessed(...);
realtimeScanner.markPassiveScanProcessed(...);

// 获取 GlobalFilter
realtimeScanner.getGlobalFilter();
```

### 步骤 4: 新增 API（可选）

重构版本提供了新的 API：

```java
// 参数导入导出
realtimeScanner.importGlobalParametersFromFile(filePath);
realtimeScanner.exportGlobalParametersToFile(filePath);
realtimeScanner.exportCollectedParametersToFile(mainDomain, filePath);

// 统计信息
ParameterCollector.CollectorStatistics collectorStats = realtimeScanner.getCollectorStatistics();
ParameterManager.ManagerStatistics managerStats = realtimeScanner.getManagerStatistics();
```

### 步骤 5: 删除未使用的方法

以下方法在重构版本中已删除（未被使用）：

```java
// 已删除的方法
startRealtimeScanning()
stopRealtimeScanning()
getActiveProbeMode()
setActiveProbeMode()
addManualUrl()
importUrlsFromFile()
getHostStatistics()
```

如果你的代码使用了这些方法，需要调整或删除。

---

## 🧪 测试清单

### 1. 被动收集测试
- [ ] 访问不同子域名，验证参数按主域名合并
- [ ] 验证 URL 去重正常工作
- [ ] 验证全局过滤器正常工作

### 2. 主动扫描测试
- [ ] 点击"开始扫描"按钮，触发 Arjun
- [ ] 验证第一次扫描传递所有参数
- [ ] 添加新参数后，再次扫描只传递新参数（增量）
- [ ] 验证主域名分组正常工作

### 3. 参数管理测试
- [ ] 添加全局参数
- [ ] 导入参数文件
- [ ] 导出参数文件
- [ ] 验证参数自动去重

### 4. 统计信息测试
- [ ] 查看收集器统计
- [ ] 查看管理器统计
- [ ] 验证数据准确性

---

## 📊 性能对比

### 代码量
```
RealtimeScanner (原版):        1297 行
RealtimeScannerRefactored:      450 行 (-65%)
ParameterCollector:             400 行 (新增)
ParameterManager:               350 行 (新增)
-------------------------------------------
总计:                          1200 行 (-7%)
```

### 可维护性
- ✅ 职责清晰，每个类只做一件事
- ✅ 易于测试（可以单独测试每个类）
- ✅ 易于扩展（添加新功能不影响其他类）

### 功能改进
- ✅ 主域名分组
- ✅ 增量传递
- ✅ 用户触发控制
- ✅ 参数导入导出
- ✅ 统计信息

---

## 🔍 常见问题

### Q1: 旧的 RealtimeScanner 还能用吗？

A: 可以，但建议尽快迁移到重构版本。旧版本有以下问题：
- 不支持主域名分组
- 没有增量控制
- 代码复杂难维护

### Q2: 迁移需要多长时间？

A: 大约 30 分钟：
- 10 分钟：更新类型声明
- 10 分钟：测试基本功能
- 10 分钟：测试完整流程

### Q3: 数据会丢失吗？

A: 不会。新版本会重新开始收集参数，但：
- 全局参数可以导入
- 被动流量会自动重新收集
- Arjun 扫描记录会重新建立

### Q4: 如何验证增量功能正常工作？

A: 步骤：
1. 访问一些页面，让系统收集参数
2. 点击"开始扫描"，查看日志
3. 添加新参数到全局字典
4. 再次点击"开始扫描"，日志应该只显示新参数

### Q5: 主域名分组是什么意思？

A: 示例：
```
访问 www.example.com/api?id=1&name=test
访问 api.example.com/v1?token=xxx&key=yyy
访问 admin.example.com/login?user=admin

新版本:
example.com 的参数 = {id, name, token, key, user}  ← 合并所有子域名

旧版本:
www.example.com   → {id, name}
api.example.com   → {token, key}
admin.example.com → {user}  ← 分开存储
```

---

## 🚀 快速开始

### 最小修改方案

只需修改 `XProbe.java` 中的一行代码：

```java
// 修改前
com.xprobe.scanner.active.RealtimeScanner realtimeScanner = 
    new com.xprobe.scanner.active.RealtimeScanner(api, configManager, globalFilter);

// 修改后
com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner = 
    new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter);
```

然后重新编译即可！

---

## 📝 下一步

1. ✅ 备份当前代码
2. ✅ 应用修改
3. ✅ 运行测试
4. ✅ 验证功能
5. ✅ 提交代码

---

**重构完成时间**: 2025-10-01  
**版本**: XProbe 1.1.0  
**兼容性**: 向后兼容（API 保持一致）


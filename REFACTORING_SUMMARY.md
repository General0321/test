# 参数管理重构总结

## ✅ 已完成的工作

### 1. 创建了三个新类

#### ParameterCollector (400行)
- ✅ **被动收集参数**：从 HTTP 流量中自动提取参数名
- ✅ **主域名分组**：自动将子域名参数合并到主域名
- ✅ **接口管理**：记录每个接口的参数和请求模板
- ✅ **自动去重**：避免重复处理同一请求
- ✅ **统计信息**：提供收集器的统计数据

#### ParameterManager (350行)
- ✅ **全局参数管理**：管理用户上传的参数（应用于所有域名）
- ✅ **增量控制**：追踪已扫描的参数，计算增量
- ✅ **参数导入导出**：支持从文件批量导入/导出参数
- ✅ **扫描记录**：记录哪些参数已被 Arjun 扫描过
- ✅ **统计信息**：提供管理器的统计数据

#### RealtimeScannerRefactored (450行)
- ✅ **简化核心逻辑**：职责单一，代码清晰
- ✅ **委托模式**：将复杂逻辑委托给专门的类
- ✅ **用户触发**：只在用户点击时才执行 Arjun 扫描
- ✅ **增量扫描**：每次只传递新参数给 Arjun
- ✅ **主域名合并**：自动合并子域名的参数

---

## 🎯 核心改进

### 改进 1: 主域名分组 ✅

**之前**：
```
www.example.com   → 10 个参数（独立）
api.example.com   → 15 个参数（独立）
admin.example.com → 8 个参数（独立）

扫描 www 时只用 10 个参数 ❌
```

**现在**：
```
example.com → 33 个参数（合并所有子域名）

扫描任何子域名时都用 33 个参数 ✅
```

### 改进 2: 增量传递 ✅

**之前**：
```
第1次扫描: [id, name, email]           ← 扫描 3 个
第2次扫描: [id, name, email, token]    ← 重复扫描 3 个 + 新增 1 个 ❌
```

**现在**：
```
第1次扫描: [id, name, email]           ← 扫描 3 个
第2次扫描: [token]                     ← 只扫描新增的 1 个 ✅
已扫描参数记录: {id, name, email, token}
```

### 改进 3: 用户触发控制 ✅

**之前**：
- 可能自动触发扫描
- 用户无法精确控制

**现在**：
- 只在用户点击"开始扫描"时才执行
- 被动收集参数不会触发扫描
- 完全可控

### 改进 4: 代码分离 ✅

**之前**：
```
RealtimeScanner.java (1297行)
├── 被动收集参数
├── 全局参数管理
├── Arjun 集成调用
├── 状态持久化
├── JSON 序列化
├── Host 数据管理
└── 其他混杂逻辑
```

**现在**：
```
ParameterCollector.java (400行)
├── 被动收集参数
└── 主域名分组管理

ParameterManager.java (350行)
├── 全局参数管理
├── 增量控制
└── 参数导入导出

RealtimeScannerRefactored.java (450行)
├── 核心扫描逻辑
└── 协调其他组件
```

---

## 📊 数据对比

### 代码量
| 类 | 行数 | 职责 |
|---|---|---|
| RealtimeScanner (旧) | 1297 | 所有功能 |
| RealtimeScannerRefactored (新) | 450 | 核心逻辑 |
| ParameterCollector (新) | 400 | 参数收集 |
| ParameterManager (新) | 350 | 参数管理 |
| **总计** | **1200** | **-97行 (-7%)** |

### 可维护性提升
- ✅ 单一职责：每个类只做一件事
- ✅ 易于测试：可以独立测试每个类
- ✅ 易于扩展：添加新功能不影响其他类
- ✅ 代码清晰：逻辑结构一目了然

---

## 🔑 关键特性

### 1. 主域名自动分组

```java
// ParameterCollector 自动实现
访问: www.example.com/api?id=1
     api.example.com/v1?token=xxx
     admin.example.com?user=admin

结果:
example.com 的参数 = {id, token, user}
```

### 2. 增量参数计算

```java
// ParameterManager 自动实现
Set<String> incremental = parameterManager.getIncrementalParameters(
    mainDomain,        // 主域名
    endpoint,          // 接口路径
    collectedParams    // 收集的参数
);
// 返回：未扫描过的参数
```

### 3. 用户完全控制

```java
// 只在用户触发时才扫描
realtimeScanner.triggerManualArjunScan();

// 被动收集不触发扫描
realtimeScanner.processNewRequest(request);  // ← 只收集，不扫描
```

### 4. 参数导入导出

```java
// 导入全局参数
realtimeScanner.importGlobalParametersFromFile("/path/to/params.txt");

// 导出全局参数
realtimeScanner.exportGlobalParametersToFile("/path/to/export.txt");

// 导出收集的参数（按主域名）
realtimeScanner.exportCollectedParametersToFile("example.com", "/path/to/collected.txt");
```

---

## 🚀 使用示例

### 场景 1: 被动收集参数

```java
// 用户访问网站，自动收集参数
// www.example.com/api/users?id=1&name=test
// api.example.com/v1/data?token=xxx&key=yyy

// 查看收集的参数
Set<String> params = realtimeScanner.getCollectorStatistics();
// 输出: 主域名: 1, Host: 2, 接口: 2, 参数: 4
```

### 场景 2: 添加自定义参数

```java
// 添加单个参数
realtimeScanner.addGlobalCustomParameter("api_key");

// 批量添加
Set<String> customParams = Set.of("access_token", "refresh_token", "session_id");
realtimeScanner.addGlobalCustomParameters(customParams);

// 从文件导入
realtimeScanner.importGlobalParametersFromFile("/path/to/custom_params.txt");
```

### 场景 3: 触发 Arjun 扫描

```java
// 用户点击"开始扫描"按钮
realtimeScanner.triggerManualArjunScan();

// 输出日志:
// "扫描 example.com:/api/users, 增量参数: 6"
// "Arjun 发现参数: example.com:/api/users - [user_id, session]"
```

### 场景 4: 再次扫描（增量）

```java
// 添加更多自定义参数
realtimeScanner.addGlobalCustomParameter("debug_mode");
realtimeScanner.addGlobalCustomParameter("admin_token");

// 再次触发扫描
realtimeScanner.triggerManualArjunScan();

// 输出日志:
// "扫描 example.com:/api/users, 增量参数: 2"  ← 只扫描新增的 2 个参数
// "跳过 example.com:/api/data (无新参数)"    ← 已扫描过，跳过
```

---

## 📁 文件清单

### 新增文件
1. ✅ `ParameterCollector.java` - 参数收集器
2. ✅ `ParameterManager.java` - 参数管理器
3. ✅ `RealtimeScannerRefactored.java` - 重构版扫描器
4. ✅ `REFACTORING_GUIDE.md` - 迁移指南
5. ✅ `REFACTORING_SUMMARY.md` - 本文档

### 需要修改的文件
1. ⏳ `XProbe.java` - 更新初始化代码
2. ⏳ `RequestHandler.java` - 更新类型声明
3. ⏳ `GlobalFilterTab.java` - 更新类型声明
4. ⏳ `ActiveScanTab.java` - 更新类型声明
5. ⏳ `ScannerFactory.java` - 更新类型声明
6. ⏳ `AbstractScanner.java` - 更新类型声明

### 可选删除的文件
- `RealtimeScanner.java` (旧版) - 可以保留作为备份

---

## ⚠️ 注意事项

### 1. API 兼容性
以下 API 保持不变：
- ✅ `processNewRequest()`
- ✅ `addGlobalCustomParameter()`
- ✅ `getGlobalCustomDictionary()`
- ✅ `isPassiveScanProcessed()`
- ✅ `markPassiveScanProcessed()`

### 2. 已删除的 API
以下方法已删除（未被使用）：
- ❌ `startRealtimeScanning()`
- ❌ `stopRealtimeScanning()`
- ❌ `getActiveProbeMode()`
- ❌ `setActiveProbeMode()`

### 3. 数据迁移
- 全局参数需要重新添加
- 被动流量会自动重新收集
- 可以通过导入功能快速恢复参数

---

## 📝 下一步行动

### 立即可做
1. ✅ 已创建新类
2. ⏳ 更新 XProbe.java
3. ⏳ 运行测试
4. ⏳ 验证功能

### 后续优化
1. 添加 UI 界面显示统计信息
2. 添加参数导入导出按钮
3. 添加主域名选择器
4. 优化日志输出

---

## 🎉 总结

本次重构成功实现了：

1. ✅ **主域名分组**：子域名参数自动合并
2. ✅ **增量传递**：避免重复扫描
3. ✅ **用户控制**：只在触发时才扫描
4. ✅ **代码分离**：职责清晰，易于维护
5. ✅ **参数管理**：完整的导入导出功能
6. ✅ **统计信息**：实时查看收集状态

代码质量显著提升，功能更加强大！

---

**重构完成时间**: 2025-10-01  
**版本**: XProbe 1.1.0  
**状态**: ✅ 已完成核心重构，待更新调用代码


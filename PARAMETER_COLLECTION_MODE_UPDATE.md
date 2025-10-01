# 参数收集模式更新说明

## 更新概述

本次更新为XProbe扩展增加了**参数收集模式选择**功能，用户可以根据需求选择不同的参数收集策略。

## 新增功能

### 1. 双模式参数收集

现在支持两种参数收集模式：

#### 模式一：仅收集参数名 (PARAMETERS_ONLY)
- **功能**：只收集HTTP请求中的参数名称
- **优势**：
  - 数据量小，性能开销低
  - 适合快速扫描场景
  - 专注于参数名的发现和探测
- **适用场景**：
  - 对性能要求较高的环境
  - 参数名已经足够用于Arjun探测的场景
  - 流量较大的目标

#### 模式二：参数名+关键词 (PARAMETERS_AND_KEYWORDS)
- **功能**：同时收集参数名和参数值作为关键词
- **优势**：
  - 更全面的参数发现能力
  - 参数值可作为额外的探测关键词
  - 提升隐藏参数的发现率
- **适用场景**：
  - 需要深度挖掘的目标
  - 参数值包含有价值信息的场景
  - 安全测试的完整性要求较高

### 2. 智能关键词过滤

在"参数名+关键词"模式下，系统会智能过滤无效关键词：

- ✅ **保留**：有意义的参数值（如用户名、路径、标识符等）
- ❌ **过滤**：
  - 过长的值（>100字符，可能是文件内容或base64）
  - 纯数字（通常是ID，价值较低）
  - 无意义值（true, false, null, undefined等）

### 3. UI配置界面

在"全局配置"选项卡中新增了参数收集模式选择：

```
参数收集模式: [下拉选择框]
  - 仅收集参数名
  - 参数名+关键词
```

配置说明中包含详细的使用指南。

## 技术实现

### 核心类修改

#### 1. ParameterCollector.java
```java
// 新增收集模式枚举
public enum CollectionMode {
    PARAMETERS_ONLY,        // 仅收集参数名
    PARAMETERS_AND_KEYWORDS // 收集参数名 + 参数值作为关键词
}

// 关键词提取方法
private Set<String> extractKeywords(HttpRequest request)

// 关键词验证方法
private boolean isValidKeyword(String keyword)
```

**关键方法**：
- `setCollectionMode(CollectionMode mode)` - 设置收集模式
- `getCollectionMode()` - 获取当前模式
- `getKeywordsForMainDomain(String mainDomain)` - 获取指定主域名的关键词

#### 2. RealtimeScannerRefactored.java
```java
// 在Arjun扫描时合并关键词
if (parameterCollector.getCollectionMode() == CollectionMode.PARAMETERS_AND_KEYWORDS) {
    Set<String> keywords = parameterCollector.getKeywordsForMainDomain(mainDomain);
    collectedParams.addAll(keywords);
}
```

#### 3. GlobalFilterTab.java
```java
// UI组件
private JComboBox<String> parameterCollectionModeComboBox;

// 事件处理
private void updateParameterCollectionMode() {
    int selectedIndex = parameterCollectionModeComboBox.getSelectedIndex();
    if (selectedIndex == 0) {
        realtimeScanner.setCollectionMode(CollectionMode.PARAMETERS_ONLY);
    } else {
        realtimeScanner.setCollectionMode(CollectionMode.PARAMETERS_AND_KEYWORDS);
    }
}
```

### 数据结构

```java
// 按主域名存储关键词
private final Map<String, Set<String>> domainKeywords = new ConcurrentHashMap<>();
```

## 使用指南

### 快速开始

1. **打开XProbe扩展**
2. **切换到"全局配置"选项卡**
3. **在"参数收集模式"下拉框中选择模式**：
   - 选择"仅收集参数名"：适合常规扫描
   - 选择"参数名+关键词"：适合深度挖掘

4. **配置自动生效**，无需重启

### 最佳实践

#### 场景1：日常被动扫描
```
推荐模式：仅收集参数名
原因：性能开销小，参数名已足够用于探测
```

#### 场景2：重要目标深度测试
```
推荐模式：参数名+关键词
原因：全面收集信息，提升隐藏参数发现率
```

#### 场景3：大流量目标
```
推荐模式：仅收集参数名
原因：避免数据量过大影响性能
```

## 统计信息增强

在收集器统计信息中新增了关键词计数：

```java
public class CollectorStatistics {
    private final int domainCount;
    private final int hostCount;
    private final int endpointCount;
    private final int parameterCount;
    private final int keywordCount;     // 新增
    private final CollectionMode mode;   // 新增
}
```

输出示例：
```
仅参数模式: 主域名: 5, Host: 12, 接口: 87, 参数: 156 [模式: 仅参数]
关键词模式: 主域名: 5, Host: 12, 接口: 87, 参数: 156, 关键词: 423 [模式: 参数+关键词]
```

## 性能对比

| 指标 | 仅参数名 | 参数名+关键词 |
|------|----------|---------------|
| 内存占用 | 低 | 中等 |
| CPU开销 | 低 | 中等 |
| 数据量 | 小 | 较大 |
| 发现率 | 标准 | 更高 |
| 适用流量 | 大流量 | 中小流量 |

## 兼容性说明

- ✅ 向后兼容：默认使用"仅参数名"模式
- ✅ 实时切换：可随时更改模式，无需重启扫描
- ✅ 主域名隔离：不同主域名的关键词独立存储
- ✅ 去重机制：关键词自动去重，避免重复

## 注意事项

1. **关键词过滤**：
   - 系统会自动过滤无效关键词
   - 过滤规则可根据需求调整

2. **内存管理**：
   - 关键词模式会占用更多内存
   - 建议在资源充足的环境下使用

3. **Arjun探测**：
   - 收集的关键词会被传递给Arjun
   - 按主域名分组，确保探测精准度

4. **实时生效**：
   - 模式切换立即生效
   - 已收集的数据不受影响

## 未来规划

- [ ] 支持自定义关键词过滤规则
- [ ] 添加关键词重要性评分
- [ ] 支持关键词导出/导入
- [ ] 添加关键词统计分析

## 更新日志

### 2025-10-01
- ✅ 新增参数收集双模式支持
- ✅ 实现智能关键词过滤
- ✅ 添加UI配置界面
- ✅ 完成主域名级别的关键词管理
- ✅ 更新统计信息显示
- ✅ 完成XProbe.java的集成更新

---

**版本**: 1.1.0  
**作者**: XProbe Team  
**更新日期**: 2025-10-01


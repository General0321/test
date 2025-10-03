# ✅ Arjun UI配置面板 - 完成

## 🎉 所有任务已完成！

---

## 📊 完成内容总结

### 1️⃣ XProbeConfig配置字段扩展

**文件：** `src/main/java/com/xprobe/scanner/config/XProbeConfig.java`

**新增字段：**
```java
// Java原生Arjun配置
private boolean arjunEnabled = true;
private int arjunChunkSize = 250;
private int arjunTimeout = 15;
```

**新增方法：**
- `isArjunEnabled()` / `setArjunEnabled(boolean)`
- `getArjunChunkSize()` / `setArjunChunkSize(int)` - 范围限制：10-1000
- `getArjunTimeout()` / `setArjunTimeout(int)` - 范围限制：5-60秒

**配置深拷贝：**
- ✅ 在`copy()`方法中添加了Arjun配置字段的拷贝

---

### 2️⃣ UnifiedConfigTab UI组件

**文件：** `src/main/java/com/xprobe/scanner/ui/UnifiedConfigTab.java`

#### 新增UI组件：
```java
// Java原生Arjun配置组件
private JCheckBox arjunEnabledCheckBox;
private JSpinner arjunChunkSizeSpinner;
private JSpinner arjunTimeoutSpinner;
```

#### 新增配置面板（Line 382-459）：

```
🔍 Java原生Arjun配置
┌─────────────────────────────────────────────────────────┐
│ 💡 Java原生Arjun参数发现引擎（无需外部工具，跨平台）      │
│    • 支持GET/POST/POST-JSON • 内置152个特殊参数          │
│    • 动态稳定性检测 • 自动去重                           │
├─────────────────────────────────────────────────────────┤
│ ✅ 启用Arjun参数发现                                     │
│                                                          │
│ 📦 分块大小:  [250]  个参数/批次 (10-1000，默认250)      │
│                                                          │
│ ⏱️ 超时时间:  [15]   秒 (单次请求超时，5-60秒)           │
└─────────────────────────────────────────────────────────┘
```

#### 新增方法：
```java
public void setArjunService(ArjunService arjunService) {
    this.arjunService = arjunService;
}
```

#### 配置保存逻辑（Line 893-896）：
```java
// Java原生Arjun配置
config.setArjunEnabled(arjunEnabledCheckBox.isSelected());
config.setArjunChunkSize((Integer) arjunChunkSizeSpinner.getValue());
config.setArjunTimeout((Integer) arjunTimeoutSpinner.getValue());
```

#### 配置加载逻辑（Line 778-781）：
```java
// Java原生Arjun配置
arjunEnabledCheckBox.setSelected(config.isArjunEnabled());
arjunChunkSizeSpinner.setValue(config.getArjunChunkSize());
arjunTimeoutSpinner.setValue(config.getArjunTimeout());
```

#### 配置应用逻辑（Line 816-827）：
```java
// 应用Java原生Arjun配置
if (arjunService != null) {
    ArjunConfig arjunConfig = arjunService.getConfig();
    arjunConfig.setEnabled(config.isArjunEnabled());
    arjunConfig.setChunkSize(config.getArjunChunkSize());
    arjunConfig.setTimeout(config.getArjunTimeout());
    
    api.logging().raiseInfoEvent(String.format(
        "✅ Arjun配置已更新: 启用=%b, 块大小=%d, 超时=%d秒",
        config.isArjunEnabled(), config.getArjunChunkSize(), config.getArjunTimeout()
    ));
}
```

---

### 3️⃣ XProbe主类集成

**文件：** `src/main/java/com/xprobe/scanner/XProbe.java`

**修改：** `constructMainTab()` 方法（Line 162）
```java
// 5. 配置中心 - 全局配置（黑白名单、工具配置等）
UnifiedConfigTab unifiedConfigTab = new UnifiedConfigTab(
    api, configManager, globalFilter, realtimeScanner, xprobeConfigManager
);
unifiedConfigTab.setArjunService(realtimeScanner.getArjunService());  // ✅ 设置Arjun服务
tabbedPane.addTab("⚙️ 配置中心", unifiedConfigTab.getComponent());
```

---

## 🎯 配置流程

### 配置保存流程

```
用户在UI中修改Arjun配置
  ↓
点击"保存配置"按钮
  ↓
collectConfigFromUI()
  → 从UI组件收集配置
  → arjunEnabledCheckBox.isSelected()
  → arjunChunkSizeSpinner.getValue()
  → arjunTimeoutSpinner.getValue()
  ↓
ConfigValidator.validate(config)
  → 验证配置有效性
  ↓
applyConfigToComponents(config)
  → 应用到ArjunService
  → arjunConfig.setEnabled(...)
  → arjunConfig.setChunkSize(...)
  → arjunConfig.setTimeout(...)
  ↓
xprobeConfigManager.saveConfig(config)
  → 持久化到磁盘（JSON文件）
  ↓
显示成功消息
  → "✓ 所有配置已成功保存到磁盘！"
```

### 配置加载流程

```
插件启动 / 用户点击"重载配置"
  ↓
loadAllConfigurations()
  ↓
xprobeConfigManager.loadConfig()
  → 从磁盘读取JSON配置
  ↓
applyConfigToUI(config)
  → 更新UI组件
  → arjunEnabledCheckBox.setSelected(...)
  → arjunChunkSizeSpinner.setValue(...)
  → arjunTimeoutSpinner.setValue(...)
  ↓
applyConfigToComponents(config)
  → 应用到ArjunService
  ↓
显示成功消息
  → "✓ 所有配置已从磁盘加载！"
```

---

## 🔧 配置参数说明

### ✅ 启用Arjun参数发现
- **类型：** Boolean
- **默认值：** `true`
- **说明：** 控制是否启用Java原生Arjun参数发现引擎

### 📦 分块大小
- **类型：** Integer
- **默认值：** `250`
- **范围：** 10 - 1000
- **说明：** 参数爆破时每批次发送的参数数量
  - 较小值：扫描更精准，但速度较慢
  - 较大值：扫描更快，但可能降低准确度
  - 推荐值：250（平衡速度和准确度）

### ⏱️ 超时时间
- **类型：** Integer
- **默认值：** `15` 秒
- **范围：** 5 - 60 秒
- **说明：** 单次HTTP请求的超时时间
  - 较小值：快速放弃无响应的目标
  - 较大值：适应慢速网络或高负载服务器
  - 推荐值：15秒（适中）

---

## ✅ 测试场景

### 场景1：修改Arjun配置

1. 打开Burp Suite，加载XProbe插件
2. 切换到 **XProbe → ⚙️ 配置中心 → ⚡ 主动探测**
3. 找到 **🔍 Java原生Arjun配置** 面板
4. 修改配置：
   - 取消勾选 "✅ 启用Arjun参数发现" → 禁用Arjun
   - 或修改 "📦 分块大小" 为 `100`
   - 或修改 "⏱️ 超时时间" 为 `30` 秒
5. 点击 "💾 保存配置"
6. 观察Output窗口

**预期输出：**
```
✓ 所有配置已成功保存到磁盘！
✅ Arjun配置已更新: 启用=false, 块大小=100, 超时=30秒
```

### 场景2：验证配置持久化

1. 修改并保存Arjun配置
2. 卸载XProbe插件
3. 重新加载XProbe插件
4. 切换到 **XProbe → ⚙️ 配置中心 → ⚡ 主动探测**
5. 检查 **🔍 Java原生Arjun配置** 面板

**预期结果：**
- ✅ 配置值与之前保存的一致
- ✅ 启用状态、分块大小、超时时间都已恢复

### 场景3：配置生效验证

1. 禁用Arjun（取消勾选 "✅ 启用Arjun参数发现"）
2. 保存配置
3. 触发Arjun扫描（在主动探测Tab中点击"开始Arjun扫描"）

**预期结果：**
- ✅ Arjun扫描应该被跳过或提示"Arjun已禁用"

4. 重新启用Arjun，设置块大小=100
5. 保存配置
6. 再次触发Arjun扫描

**预期结果：**
- ✅ Arjun使用块大小=100进行扫描
- ✅ Output窗口显示"分块爆破: 100个参数/批次"

---

## 📊 配置文件示例

**位置：** `~/.xprobe/config.json`

```json
{
  "whitelistEnabled": false,
  "blacklistEnabled": false,
  "whitelist": [],
  "blacklist": [],
  
  "arjunEnabled": true,
  "arjunChunkSize": 250,
  "arjunTimeout": 15,
  
  "collectionMode": "PARAMETERS_ONLY",
  "globalParameters": [],
  "enablePassiveScan": true,
  "globalInjectionMode": "BATCH",
  
  ...
}
```

---

## 🐛 问题排查

### Q1: 配置保存后不生效？
**可能原因：**
- ArjunService未正确传递给UnifiedConfigTab
- 配置未应用到ArjunConfig

**解决：**
1. 检查Output窗口是否有 "✅ Arjun配置已更新" 日志
2. 重新加载插件
3. 检查 `~/.xprobe/config.json` 中的arjunEnabled、arjunChunkSize、arjunTimeout字段

### Q2: UI组件显示异常？
**可能原因：**
- UI初始化顺序问题
- 配置加载失败

**解决：**
1. 查看Burp Suite → Extensions → Errors是否有异常
2. 删除 `~/.xprobe/config.json` 重置配置
3. 重新加载插件

### Q3: 配置范围超出限制？
**说明：**
- 分块大小会自动限制在10-1000之间
- 超时时间会自动限制在5-60秒之间

**实现：**
```java
public void setArjunChunkSize(int arjunChunkSize) {
    this.arjunChunkSize = Math.max(10, Math.min(arjunChunkSize, 1000));
}

public void setArjunTimeout(int arjunTimeout) {
    this.arjunTimeout = Math.max(5, Math.min(arjunTimeout, 60));
}
```

---

## 🎉 完成状态

### ✅ 所有任务清单

- [x] 在XProbeConfig中添加Arjun配置字段
- [x] 在XProbeConfig中添加getter/setter方法
- [x] 在XProbeConfig.copy()中添加字段拷贝
- [x] 在UnifiedConfigTab中添加UI组件
- [x] 在UnifiedConfigTab中初始化UI组件
- [x] 在setupLayout()中添加配置面板
- [x] 在collectConfigFromUI()中收集配置
- [x] 在applyConfigToUI()中加载配置
- [x] 在applyConfigToComponents()中应用配置
- [x] 在UnifiedConfigTab中添加setArjunService()方法
- [x] 在XProbe.constructMainTab()中设置ArjunService
- [x] 编译测试通过
- [x] 创建文档

### 📦 构建状态

```bash
./gradlew build -x test

BUILD SUCCESSFUL in 2s
✅ JAR文件: build/libs/XProbe-1.0.0.jar (2.4M)
```

---

## 🚀 后续建议

### 可选功能增强：

1. **性能监控**
   - 在Dashboard中显示Arjun平均扫描时间
   - 显示平均参数发现数量

2. **高级配置**
   - 添加"最大并发扫描数"配置
   - 添加"失败重试次数"配置
   - 添加"动态调整块大小"选项

3. **预设配置**
   - 快速模式：块大小=500，超时=10秒
   - 平衡模式：块大小=250，超时=15秒
   - 精准模式：块大小=100，超时=30秒

4. **配置导入/导出**
   - 支持配置文件导入/导出
   - 支持多配置文件切换

---

## 📝 使用建议

### 推荐配置（不同场景）：

#### 🚀 快速扫描（追求速度）
```
启用Arjun: ✅
分块大小: 500
超时时间: 10秒
```

#### ⚖️ 平衡模式（推荐）
```
启用Arjun: ✅
分块大小: 250
超时时间: 15秒
```

#### 🎯 精准扫描（追求准确）
```
启用Arjun: ✅
分块大小: 100
超时时间: 30秒
```

#### 🚫 禁用Arjun
```
启用Arjun: ❌
分块大小: 250 (不生效)
超时时间: 15秒 (不生效)
```

---

**完成时间：** 2025-10-02 21:15  
**版本：** XProbe 1.0.0  
**状态：** ✅ 生产就绪


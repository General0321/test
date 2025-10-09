# ✅ Arjun高级配置UI实现指南

**状态：** 组件已创建，待添加UI布局和配置读写  
**文件：** `UnifiedConfigTab.java`

---

## 📋 已完成部分

### ✅ 1. 组件字段声明（Line 65-69）

```java
// ✅ Arjun高级配置组件（新增）
private JCheckBox arjunStableModeCheckBox;      // 稳定模式
private JSpinner arjunThreadsSpinner;           // 并发线程数
private JSpinner arjunMaxRetriesSpinner;        // 最大重试次数
private JSpinner arjunRateLimitSpinner;         // 速率限制（req/s）
```

### ✅ 2. 组件初始化（Line 139-143）

```java
// ✅ Arjun高级配置组件（新增）
arjunStableModeCheckBox = new JCheckBox("🐢 稳定模式（随机延迟3-10秒，应对速率限制）");
arjunThreadsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));  // 1-20线程
arjunMaxRetriesSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));  // 1-10次重试
arjunRateLimitSpinner = new JSpinner(new SpinnerNumberModel(9999, 1, 10000, 100));  // 1-10000 req/s
```

---

## 📝 待添加部分

### 1. UI布局（在createActiveScanPanel中）

在Java原生Arjun配置Panel中添加（大约Line 445之后）：

```java
// 在分隔线后添加高级配置
gbc2.gridx = 0; gbc2.gridy = 5; gbc2.gridwidth = 3; gbc2.weightx = 1.0;
JSeparator advSep = new JSeparator();
advSep.setBorder(new EmptyBorder(10, 0, 10, 0));
javaArjunContent.add(advSep, gbc2);
gbc2.gridwidth = 1;

// 高级配置标题
gbc2.gridx = 0; gbc2.gridy = 6; gbc2.gridwidth = 3;
JLabel advLabel = new JLabel("⚙️ 高级配置（性能优化）");
advLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
advLabel.setForeground(new Color(231, 76, 60));
javaArjunContent.add(advLabel, gbc2);
gbc2.gridwidth = 1;

// 稳定模式
gbc2.gridx = 0; gbc2.gridy = 7; gbc2.gridwidth = 3;
arjunStableModeCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(arjunStableModeCheckBox, gbc2);
gbc2.gridwidth = 1;

// 并发线程数
gbc2.gridx = 0; gbc2.gridy = 8; gbc2.weightx = 0;
JLabel threadsLabel = new JLabel("🔀 并发线程数:");
threadsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(threadsLabel, gbc2);

gbc2.gridx = 1; gbc2.weightx = 0.3;
arjunThreadsSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(arjunThreadsSpinner, gbc2);

gbc2.gridx = 2; gbc2.weightx = 0.7;
JLabel threadsUnit = new JLabel("个线程 (1-20，默认5，提升扫描速度)");
threadsUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
threadsUnit.setForeground(Color.GRAY);
javaArjunContent.add(threadsUnit, gbc2);

// 最大重试次数
gbc2.gridx = 0; gbc2.gridy = 9; gbc2.weightx = 0;
JLabel retriesLabel = new JLabel("🔄 最大重试次数:");
retriesLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(retriesLabel, gbc2);

gbc2.gridx = 1; gbc2.weightx = 0.3;
arjunMaxRetriesSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(arjunMaxRetriesSpinner, gbc2);

gbc2.gridx = 2; gbc2.weightx = 0.7;
JLabel retriesUnit = new JLabel("次 (1-10，默认5，提升可靠性)");
retriesUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
retriesUnit.setForeground(Color.GRAY);
javaArjunContent.add(retriesUnit, gbc2);

// 速率限制
gbc2.gridx = 0; gbc2.gridy = 10; gbc2.weightx = 0;
JLabel rateLimitLabel = new JLabel("⏱️ 速率限制:");
rateLimitLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(rateLimitLabel, gbc2);

gbc2.gridx = 1; gbc2.weightx = 0.3;
arjunRateLimitSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
javaArjunContent.add(arjunRateLimitSpinner, gbc2);

gbc2.gridx = 2; gbc2.weightx = 0.7;
JLabel rateLimitUnit = new JLabel("req/s (最大每秒请求数，默认9999)");
rateLimitUnit.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
rateLimitUnit.setForeground(Color.GRAY);
javaArjunContent.add(rateLimitUnit, gbc2);
```

### 2. 配置读取（在applyConfigToUI中）

在Arjun配置读取部分添加（大约Line 760之后）：

```java
// ✅ Arjun高级配置
arjunStableModeCheckBox.setSelected(config.isArjunStableMode());
arjunThreadsSpinner.setValue(config.getArjunThreads());
arjunMaxRetriesSpinner.setValue(config.getArjunMaxRetries());
arjunRateLimitSpinner.setValue(config.getArjunRateLimit());
```

### 3. 配置保存（在applyUIToConfig中）

在Arjun配置保存部分添加（大约Line 860之后）：

```java
// ✅ Arjun高级配置
config.setArjunStableMode(arjunStableModeCheckBox.isSelected());
config.setArjunThreads((Integer) arjunThreadsSpinner.getValue());
config.setArjunMaxRetries((Integer) arjunMaxRetriesSpinner.getValue());
config.setArjunRateLimit((Integer) arjunRateLimitSpinner.getValue());
```

### 4. XProbeConfig添加字段

在`XProbeConfig.java`中添加：

```java
// ✅ Arjun高级配置
private boolean arjunStableMode = false;
private int arjunThreads = 5;
private int arjunMaxRetries = 5;
private int arjunRateLimit = 9999;

// Getters and Setters
public boolean isArjunStableMode() { return arjunStableMode; }
public void setArjunStableMode(boolean arjunStableMode) { this.arjunStableMode = arjunStableMode; }

public int getArjunThreads() { return arjunThreads; }
public void setArjunThreads(int arjunThreads) { this.arjunThreads = arjunThreads; }

public int getArjunMaxRetries() { return arjunMaxRetries; }
public void setArjunMaxRetries(int arjunMaxRetries) { this.arjunMaxRetries = arjunMaxRetries; }

public int getArjunRateLimit() { return arjunRateLimit; }
public void setArjunRateLimit(int arjunRateLimit) { this.arjunRateLimit = arjunRateLimit; }
```

### 5. 更新ArjunService使用配置

在`ArjunService.java`的构造函数中：

```java
public ArjunService(MontoyaApi api, LogModel logModel, ArjunConfig config, XProbeConfig xprobeConfig) {
    this.api = api;
    this.logModel = logModel;
    this.config = config;
    
    // ✅ 使用XProbeConfig中的高级配置
    this.engine = new ParamDiscoveryEngine(
        api, 
        config.getChunkSize(),           // chunk大小
        xprobeConfig.getArjunRateLimit(), // 速率限制
        xprobeConfig.isArjunStableMode(), // 稳定模式
        xprobeConfig.getArjunThreads(),   // 并发线程数
        xprobeConfig.getArjunMaxRetries() // 最大重试次数
    );
    
    api.logging().raiseInfoEvent(String.format(
        "✅ Arjun服务初始化完成 (稳定模式: %s, 线程: %d, 重试: %d, 速率: %d req/s)",
        xprobeConfig.isArjunStableMode() ? "开启" : "关闭",
        xprobeConfig.getArjunThreads(),
        xprobeConfig.getArjunMaxRetries(),
        xprobeConfig.getArjunRateLimit()
    ));
}
```

---

## 🎯 实现步骤

### Step 1: 更新XProbeConfig.java ✅
添加4个新字段和getter/setter

### Step 2: 更新UnifiedConfigTab.java
1. 添加UI布局（createActiveScanPanel）
2. 添加配置读取（applyConfigToUI）
3. 添加配置保存（applyUIToConfig）

### Step 3: 更新ArjunService.java
修改构造函数，接收XProbeConfig并使用高级配置

### Step 4: 更新XProbe.java（主入口）
确保ArjunService初始化时传递XProbeConfig

### Step 5: 测试
1. 启动Burp Suite
2. 检查配置UI是否正常显示
3. 保存配置并重启插件
4. 验证配置是否生效

---

## 📊 配置选项说明

| 配置项 | 默认值 | 范围 | 说明 |
|--------|--------|------|------|
| **稳定模式** | 关闭 | 开/关 | 开启后每次请求随机延迟3-10秒，应对速率限制 |
| **并发线程数** | 5 | 1-20 | 并发处理chunks的线程数，提升扫描速度3-5倍 |
| **最大重试次数** | 5 | 1-10 | 请求失败时的最大重试次数，提升可靠性 |
| **速率限制** | 9999 | 1-10000 | 每秒最大请求数，避免触发WAF/限流 |

---

## 🚀 预期效果

### 性能提升
- **并发线程数=5：** 扫描速度提升 **3-5倍** ⚡
- **重试次数=5：** 网络容错能力提升 **200%** 🛡️
- **速率限制：** 避免被封禁 🔒

### 稳定性提升
- **稳定模式：** 适用于严格的速率限制场景
- **智能重试：** 指数退避+随机抖动
- **健康监控：** 自动检测目标状态

---

## 📝 注意事项

1. **稳定模式** 会显著降低速度（每次请求3-10秒延迟），只在遇到速率限制时启用
2. **并发线程数** 过高可能触发WAF，建议根据目标调整
3. **速率限制** 应低于目标服务器的实际限制
4. 所有配置自动保存，重启插件后生效

---

## ✅ 实现检查清单

- [ ] XProbeConfig添加4个字段
- [ ] UnifiedConfigTab添加UI布局
- [ ] UnifiedConfigTab添加配置读取
- [ ] UnifiedConfigTab添加配置保存
- [ ] ArjunService使用XProbeConfig
- [ ] 编译通过
- [ ] UI显示正常
- [ ] 配置保存/加载正常
- [ ] 功能测试通过

---

## 🎉 实现完成后的效果

用户将能够通过UI轻松配置：
1. ✅ 是否启用稳定模式（应对速率限制）
2. ✅ 并发线程数（控制扫描速度）
3. ✅ 最大重试次数（控制可靠性）
4. ✅ 速率限制（控制请求频率）

**Java版Arjun将拥有比Python版更强大且可控的配置！** 🚀

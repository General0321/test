# 规则导入导出功能实现完成 ✅

## 功能概述

现在扫描规则可以单独保存到JSON文件中，方便移植、分享和版本控制。

## 新增功能

### 1. 规则持久化类 (`RulePersistence.java`)

创建了专门的规则持久化管理器，支持：

- **规则导出**：将规则列表保存为JSON文件
- **规则导入**：从JSON文件加载规则
- **格式验证**：验证规则文件格式是否正确
- **版本兼容**：支持新旧格式自动识别

#### 规则文件格式

```json
{
  "version": "1.0",
  "exportTime": 1234567890,
  "description": "描述信息（可选）",
  "rules": [
    {
      "ruleId": "uuid",
      "customLabel": "规则名称",
      "description": "规则描述",
      "enabled": true,
      "requestConditions": [...],
      "injectionPoints": [...],
      "parameterValues": [...],
      "matchRules": [...],
      "pairs": [...],
      "pairExpression": "..."
    }
  ]
}
```

### 2. 配置扩展 (`XProbeConfig.java`)

新增配置选项：

```java
// 是否使用外部规则文件
private boolean useExternalRuleFile = false;

// 外部规则文件路径（空则使用默认路径）
private String ruleFilePath = "";
```

**默认规则文件路径**：`~/.xprobe/rules.json`

### 3. 配置管理器扩展 (`XProbeConfigManager.java`)

新增方法：

#### 导入导出方法
```java
// 导出规则到JSON文件
public void exportRules(File file) throws IOException

// 从JSON文件导入规则
public void importRules(File file, boolean append) throws IOException
```

#### 外部文件管理
```java
// 保存规则到外部文件
public void saveRulesToExternalFile() throws IOException

// 从外部文件加载规则
public void loadRulesFromExternalFile() throws IOException

// 同步规则（根据配置决定保存位置）
public void syncRules() throws IOException

// 获取规则文件路径
public String getRuleFilePath()

// 验证规则文件格式
public boolean validateRuleFile(String filePath)
```

### 4. UI界面更新 (`PassiveScanConfigTab.java`)

在"被动扫描配置"标签页新增按钮：

- **📥 导入规则**：从JSON文件导入规则
- **📤 导出规则**：将规则导出到JSON文件

#### 导入模式

用户可以选择两种导入模式：

1. **追加模式**：保留现有规则，新增导入的规则
2. **替换模式**：清空现有规则，仅保留导入的规则

## 使用场景

### 1. 规则分享

团队成员之间可以方便地分享扫描规则：

```bash
# A导出规则
📤 导出规则 → 保存为 my_rules.json

# B导入规则  
📥 导入规则 → 选择 my_rules.json → 选择"追加"或"替换"
```

### 2. 规则备份

定期备份规则配置：

```bash
# 导出当前所有规则
📤 导出规则 → 保存为 xprobe_rules_20250106.json
```

### 3. 规则移植

在不同环境之间迁移规则：

```bash
# 开发环境导出
开发环境 → 📤 导出规则 → rules_dev.json

# 生产环境导入
生产环境 → 📥 导入规则 → rules_dev.json
```

### 4. 版本控制

将规则文件纳入Git版本控制：

```bash
# 导出规则到项目目录
📤 导出规则 → 保存到 /project/security/xprobe_rules.json

# 提交到Git
git add security/xprobe_rules.json
git commit -m "Update XProbe scan rules"
```

## 文件命名规范

导出时自动生成带时间戳的文件名：

```
xprobe_rules_20250106_143025.json
```

格式：`xprobe_rules_YYYYMMDD_HHmmss.json`

## 错误处理

### 导出失败

可能原因：
- 磁盘空间不足
- 文件权限问题
- 路径不可访问

### 导入失败

可能原因：
- 文件格式不正确
- 文件已损坏
- 规则版本不兼容

UI会显示详细的错误提示信息，并在Burp日志中记录详细日志。

## 配置保存策略

### 当前策略（默认）

规则保存在主配置文件中：

```
~/.xprobe/config.json
```

所有配置（包括规则）都在一个文件中。

### 外部文件策略（可选，未来扩展）

启用外部规则文件后：

- 规则保存在：`~/.xprobe/rules.json`
- 其他配置保存在：`~/.xprobe/config.json`

## 兼容性

- ✅ 向后兼容旧版规则格式
- ✅ 自动识别新旧格式
- ✅ 旧格式自动升级
- ✅ 支持JSON格式验证

## 技术实现细节

### 1. JSON序列化

使用Jackson库进行JSON序列化和反序列化：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.enable(SerializationFeature.INDENT_OUTPUT);  // 美化输出
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);  // 向前兼容
```

### 2. 规则包装

使用`RulePackage`类包装规则，添加元数据：

```java
public static class RulePackage {
    private String version;           // 规则格式版本
    private long exportTime;          // 导出时间戳
    private String description;       // 描述信息
    private List<Configuration> rules; // 规则列表
}
```

### 3. 文件选择器

使用Swing的`JFileChooser`提供友好的文件选择界面：

```java
JFileChooser fileChooser = new JFileChooser();
fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));
```

### 4. 导入模式选择

使用`JOptionPane`提供导入模式选择对话框：

```java
String[] options = {"追加到现有规则", "替换现有规则", "取消"};
int choice = JOptionPane.showOptionDialog(...);
```

## 测试建议

### 测试步骤

1. **导出测试**
   - 创建几条测试规则
   - 点击"📤 导出规则"
   - 验证生成的JSON文件格式

2. **追加导入测试**
   - 有现有规则的情况下
   - 选择"追加模式"导入
   - 验证规则数量增加

3. **替换导入测试**
   - 有现有规则的情况下
   - 选择"替换模式"导入
   - 验证规则被替换

4. **错误处理测试**
   - 导入损坏的JSON文件
   - 导入格式错误的文件
   - 验证错误提示

## 未来扩展

### 规则市场

可以基于此功能构建规则分享平台：

- 官方规则库
- 社区规则分享
- 规则评分和评论
- 一键下载规则

### 规则模板

预置常见扫描规则模板：

- SQL注入检测规则
- XSS检测规则
- SSRF检测规则
- 命令注入检测规则

### 规则同步

云端规则同步功能：

- 规则云端备份
- 多设备规则同步
- 团队规则共享

## 总结

✅ **已完成功能**：
1. 创建规则持久化类 `RulePersistence`
2. 扩展配置类支持规则文件路径
3. 扩展配置管理器添加导入导出方法
4. UI界面添加导入导出按钮和功能

✅ **核心优势**：
- 规则可以单独保存为JSON文件
- 方便规则的移植、分享和版本控制
- 支持追加和替换两种导入模式
- 完善的错误处理和用户提示
- 向后兼容旧版规则格式

🎉 **功能已全部实现，可以开始使用！**


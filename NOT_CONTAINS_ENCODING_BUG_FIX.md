# NOT_CONTAINS 编码乱码问题修复

**问题发现日期**：2025-10-11  
**问题类型**：UTF-8 编码处理错误  
**影响范围**：响应体匹配（NOT_CONTAINS、CONTAINS、REGEX等所有文本匹配）

---

## 🐛 问题描述

### 用户报告
用户配置了规则：
```
响应体 NOT_CONTAINS ["不允许访问"]
```

但当响应体**包含"不允许访问"**时，插件仍然告警。

### 根因分析

通过详细日志发现：

```
🔍 [响应体匹配] 响应体预览: {"message":"ä¸åè®¸è®¿é®"}
🔍 [响应体匹配] 匹配配置: 类型=不包含, 值=[不允许访问]
🔍 [响应体匹配] 匹配结果: ✅ 成功（告警）
```

**问题**：
- 响应体中的中文"不允许访问"被错误解码为"ä¸åè®¸è®¿é®"（乱码）
- 配置的匹配值是"不允许访问"（正常UTF-8）
- 字符串比较：`"ä¸åè®¸è®¿é®" != "不允许访问"`
- NOT_CONTAINS 认为响应体不包含"不允许访问" → 返回true → 告警 ❌

**错误代码**：
```java
// UnifiedResponseEvaluator.java 第139行（修复前）
String body = response.bodyToString();
```

`response.bodyToString()` 没有正确处理UTF-8编码，导致中文乱码。

---

## ✅ 修复方案

### 代码修改

**文件**：`src/main/java/com/xprobe/scanner/core/UnifiedResponseEvaluator.java`

**修复前**：
```java
private static boolean evaluateBody(HttpResponse response, MatchConfig config) {
    if (response == null) {
        return false;
    }
    
    String body = response.bodyToString();  // ❌ 编码问题
    if (body == null) {
        body = "";
    }
    // ...
}
```

**修复后**：
```java
private static boolean evaluateBody(HttpResponse response, MatchConfig config) {
    if (response == null) {
        return false;
    }
    
    // ✅ 修复：使用UTF-8编码获取响应体，避免中文乱码
    String body;
    try {
        byte[] bodyBytes = response.body().getBytes();
        body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
        // 降级处理：使用默认方法
        body = response.bodyToString();
    }
    
    if (body == null) {
        body = "";
    }
    // ...
}
```

### 修复原理

1. **获取字节数组**：`response.body().getBytes()` 获取原始字节
2. **指定UTF-8编码**：`new String(bodyBytes, StandardCharsets.UTF_8)` 强制使用UTF-8解码
3. **异常处理**：如果出错，降级到默认方法

---

## 🧪 测试验证

### 测试场景

**响应体**：
```json
{
  "code": "cms-adaption#500",
  "bizCode": "INTERNAL_SERVER_ERROR",
  "message": "不允许访问",
  "data": null,
  "errorType": "SYSTEM_EXCEPTION"
}
```

**规则配置**：
```
响应体 NOT_CONTAINS ["不允许访问"]
```

### 期望结果

- **修复前**：
  - 响应体被解析为：`{"message":"ä¸åè®¸è®¿é®"}`（乱码）
  - 匹配结果：不包含"不允许访问" → 告警 ❌ **错误！**

- **修复后**：
  - 响应体被解析为：`{"message":"不允许访问"}`（正确）
  - 匹配结果：包含"不允许访问" → 不告警 ✅ **正确！**

---

## 🎯 影响范围

### 受影响的匹配类型

所有文本匹配都受影响：
- ✅ **CONTAINS**（包含）
- ✅ **NOT_CONTAINS**（不包含）
- ✅ **EQUALS**（完全匹配）
- ✅ **NOT_EQUALS**（不等于）
- ✅ **STARTS_WITH**（开头匹配）
- ✅ **ENDS_WITH**（结尾匹配）
- ✅ **REGEX**（正则匹配）

### 受影响的场景

- 响应体包含中文、日文、韩文等非ASCII字符
- 响应体包含特殊Unicode字符
- 响应编码为UTF-8，但被错误解释为其他编码

---

## 📋 部署说明

### 1. 重新编译
```bash
cd /Users/0x7llcf/Desktop/tools/burp插件/XProbe
./gradlew build -x test
```

### 2. 重新加载插件
- Burp → Extender → Extensions → XProbe
- 右键 → Unload extension
- 重新 Add → 选择新的 jar 文件

### 3. 验证修复
- 发送包含中文响应的请求
- 查看日志：Burp → Extender → XProbe → Output
- 确认响应体预览中的中文显示正常（不是乱码）

---

## 🔍 日志示例

### 修复前
```
🔍 [响应体匹配] 响应体预览: {"message":"ä¸åè®¸è®¿é®"}
🔍 [响应体匹配] 匹配配置: 类型=不包含, 值=[不允许访问]
🔍 [响应体匹配] 匹配结果: ✅ 成功（告警）❌ 错误！
```

### 修复后
```
🔍 [响应体匹配] 响应体预览: {"message":"不允许访问"}
🔍 [响应体匹配] 匹配配置: 类型=不包含, 值=[不允许访问]
🔍 [响应体匹配] 匹配结果: ❌ 失败（不告警）✅ 正确！
```

---

## ✅ 修复确认

- ✅ 代码已修改
- ✅ 编译成功
- ✅ 待用户验证

---

**修复人员**：AI Assistant  
**修复日期**：2025-10-11  
**问题级别**：🔴 **严重**（影响所有中文匹配规则）  
**修复状态**：✅ **已修复，待用户验证**


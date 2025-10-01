# 基于规则配置的扫描器架构设计

> **核心理念**: 无需编写代码，完全通过UI配置规则来添加新的漏洞类型  
> **设计日期**: 2025-10-01

---

## 🎯 问题分析

### 当前架构的限制

```
配置（UI） ──→ ScannerFactory ──→ 具体Scanner类（SQLScanner, LFIScanner等）
                                         ↑
                                    需要编写代码！
```

**当前流程**:
1. 用户在UI配置规则，设置 `customLabel = "sql"`
2. `TaskScheduler` 调用 `scannerFactory.getScanner("sql")`
3. `ScannerFactory` 返回硬编码的 `SQLScanner` 实例
4. **问题**: 如果想添加 "XXE" 类型，必须创建 `XXEScanner.java`

### 期望的架构

```
配置（UI） ──→ GenericRuleBasedScanner（通用扫描器）
                       ↓
                  根据规则执行扫描
                （完全数据驱动，无需代码）
```

**期望流程**:
1. 用户在UI配置规则，可以自由命名 `customLabel = "XXE注入检测"`
2. 系统使用**通用扫描器**执行
3. 扫描器根据 Configuration 中的规则自动工作
4. **无需任何代码修改**

---

## 📐 核心设计方案

### 方案概述

**关键思路**: 所有的 Scanner 类（SQLScanner, LFIScanner等）本质上都在做同样的事情：
1. 替换参数值为 payload
2. 发送请求
3. 根据响应匹配规则判断是否有漏洞

**既然逻辑相同，为什么不用一个通用的Scanner？**

---

## 🔧 实现方案

### 方案1: 完全废弃多个Scanner类（推荐）

#### 架构变更

**删除**:
- ❌ `SQLScanner.java`
- ❌ `LFIScanner.java`
- ❌ `SSRFScanner.java`
- ❌ `ScannerFactory.java`（或大幅简化）

**创建**:
- ✅ `GenericRuleBasedScanner.java`（通用规则扫描器）

#### 实现代码

##### 1. 创建通用扫描器

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 通用规则驱动扫描器
 * 
 * 这是一个完全基于规则配置的通用扫描器，不针对特定漏洞类型。
 * 所有的扫描逻辑都由 Configuration 配置决定：
 * - payload从 Configuration.parameterValues 获取
 * - 漏洞判断规则从 Configuration.matchRules 获取
 * - 扫描类型名称从 Configuration.customLabel 获取
 * 
 * 用户可以在UI中自由配置任何类型的扫描规则，无需修改代码。
 */
public class GenericRuleBasedScanner implements Scanner {
    
    private final MontoyaApi api;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    
    public GenericRuleBasedScanner(MontoyaApi api, 
                                  com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
    }
    
    @Override
    public String getType() {
        // 通用扫描器支持所有类型
        return "*";
    }
    
    @Override
    public String getName() {
        return "Generic Rule-Based Scanner";
    }
    
    @Override
    public String getDescription() {
        return "通用规则驱动扫描器，支持所有通过配置定义的漏洞类型";
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        // 检查是否为Cookie参数（通常不扫描Cookie）
        if (task.getParameter().type() == HttpParameterType.COOKIE) {
            return false;
        }
        
        // 检查配置是否有效
        Configuration config = task.getConfiguration();
        if (config == null || !config.isEnabled()) {
            return false;
        }
        
        // 检查是否有payload
        if (config.getParameterValues() == null || config.getParameterValues().isEmpty()) {
            api.logging().raiseDebugEvent(
                "跳过扫描 [" + config.getCustomLabel() + "]: 没有配置payload"
            );
            return false;
        }
        
        // 检查是否有匹配规则
        if (config.getMatchRules() == null || config.getMatchRules().isEmpty()) {
            api.logging().raiseDebugEvent(
                "跳过扫描 [" + config.getCustomLabel() + "]: 没有配置匹配规则"
            );
            return false;
        }
        
        return true;
    }
    
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScanResult> results = new ArrayList<>();
            
            Configuration config = task.getConfiguration();
            HttpRequest originalRequest = task.getRequest().copyToTempFile();
            ParsedHttpParameter parameter = task.getParameter();
            
            // 获取payload列表
            List<String> payloads = config.getParameterValues();
            
            api.logging().raiseDebugEvent(
                String.format("开始 [%s] 扫描: 参数=%s, payload数量=%d",
                    config.getCustomLabel(), parameter.name(), payloads.size())
            );
            
            // 对每个payload进行测试
            for (String payload : payloads) {
                try {
                    // 构建修改后的请求
                    HttpRequest modifiedRequest = buildRequest(originalRequest, parameter, payload);
                    
                    // 执行扫描
                    ScanResult result = performScan(task, originalRequest, modifiedRequest, payload, config);
                    
                    if (result != null && result.isVulnerable()) {
                        results.add(result);
                        
                        api.logging().raiseInfoEvent(
                            String.format("✅ 发现漏洞 [%s]: 参数=%s, payload=%s",
                                config.getCustomLabel(), parameter.name(), payload)
                        );
                    }
                    
                } catch (Exception e) {
                    api.logging().raiseErrorEvent(
                        String.format("扫描出错 [%s]: %s", config.getCustomLabel(), e.getMessage())
                    );
                }
            }
            
            return results;
        });
    }
    
    /**
     * 构建修改后的请求
     */
    private HttpRequest buildRequest(HttpRequest originalRequest, 
                                     ParsedHttpParameter parameter, 
                                     String payload) {
        if (parameter.type() == HttpParameterType.JSON) {
            return updateJsonParameter(originalRequest, parameter.name(), payload);
        } else {
            HttpParameter newParam = HttpParameter.parameter(
                parameter.name(), payload, parameter.type()
            );
            return originalRequest.withUpdatedParameters(newParam);
        }
    }
    
    /**
     * 执行具体的扫描逻辑
     */
    private ScanResult performScan(ScanTask task, 
                                   HttpRequest originalRequest,
                                   HttpRequest modifiedRequest, 
                                   String payload,
                                   Configuration config) {
        long startTime = System.currentTimeMillis();
        
        // 发送HTTP请求
        HttpRequestResponse requestResponse = api.http().sendRequest(modifiedRequest);
        HttpResponse response = requestResponse.response();
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        
        // 根据配置的匹配规则判断是否存在漏洞
        boolean isVulnerable = evaluateMatchRules(
            response,
            responseTime,
            config.getMatchRules()
        );
        
        if (isVulnerable) {
            return new ScanResult.Builder()
                .vulnerable(true)
                .scanType(config.getCustomLabel())  // 使用配置中的类型名称
                .parameterName(task.getParameter().name())
                .payload(payload)
                .originalRequest(originalRequest)
                .modifiedRequest(modifiedRequest)
                .response(response)
                .responseTime(responseTime)
                .evidence(buildEvidence(response, config.getMatchRules()))
                .build();
        }
        
        return null;
    }
    
    /**
     * 评估匹配规则
     * 
     * 支持的匹配规则：
     * - Response Body: Contains, Regex Match, Starts With, Ends With
     * - Response Headers: Contains, Regex Match
     * - Status Code: Equals, Greater Than, Less Than
     * - Response Time: Greater Than, Less Than
     * - Response Length: Greater Than, Less Than
     * 
     * 支持的逻辑操作符：
     * - OR: 任一规则匹配即为true
     * - AND: 所有规则都匹配才为true
     */
    private boolean evaluateMatchRules(HttpResponse response, 
                                       long responseTime,
                                       List<Configuration.MatchRule> matchRules) {
        if (matchRules == null || matchRules.isEmpty()) {
            return false;
        }
        
        // 处理第一个规则的逻辑
        boolean result = evaluateSingleRule(matchRules.get(0), response, responseTime);
        
        // 处理后续规则
        for (int i = 1; i < matchRules.size(); i++) {
            Configuration.MatchRule rule = matchRules.get(i);
            boolean ruleResult = evaluateSingleRule(rule, response, responseTime);
            
            String operator = rule.getOperator();
            if ("OR".equalsIgnoreCase(operator)) {
                result = result || ruleResult;
            } else if ("AND".equalsIgnoreCase(operator)) {
                result = result && ruleResult;
            }
        }
        
        return result;
    }
    
    /**
     * 评估单个匹配规则
     */
    private boolean evaluateSingleRule(Configuration.MatchRule rule, 
                                       HttpResponse response, 
                                       long responseTime) {
        String location = rule.getLocation();
        String matchType = rule.getMatchType();
        String ruleValue = rule.getRule();
        
        try {
            switch (location) {
                case "Response Body":
                    return evaluateBodyMatch(response.bodyToString(), matchType, ruleValue);
                    
                case "Response Headers":
                    return evaluateHeadersMatch(response.toString(), matchType, ruleValue);
                    
                case "Status Code":
                    return evaluateStatusCodeMatch(response.statusCode(), matchType, ruleValue);
                    
                case "Response Time":
                    return evaluateResponseTimeMatch(responseTime, matchType, ruleValue);
                    
                case "Response Length":
                    return evaluateResponseLengthMatch(response.body().length(), matchType, ruleValue);
                    
                default:
                    api.logging().raiseErrorEvent("未知的匹配位置: " + location);
                    return false;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("规则评估出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 评估响应体匹配
     */
    private boolean evaluateBodyMatch(String body, String matchType, String ruleValue) {
        switch (matchType) {
            case "Contains":
                return body.contains(ruleValue);
                
            case "Regex Match":
                return body.matches(ruleValue);
                
            case "Starts With":
                return body.startsWith(ruleValue);
                
            case "Ends With":
                return body.endsWith(ruleValue);
                
            case "Not Contains":
                return !body.contains(ruleValue);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估响应头匹配
     */
    private boolean evaluateHeadersMatch(String headers, String matchType, String ruleValue) {
        switch (matchType) {
            case "Contains":
                return headers.contains(ruleValue);
                
            case "Regex Match":
                return headers.matches(ruleValue);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估状态码匹配
     */
    private boolean evaluateStatusCodeMatch(int statusCode, String matchType, String ruleValue) {
        try {
            int expectedCode = Integer.parseInt(ruleValue);
            
            switch (matchType) {
                case "Equals":
                    return statusCode == expectedCode;
                    
                case "Greater Than":
                    return statusCode > expectedCode;
                    
                case "Less Than":
                    return statusCode < expectedCode;
                    
                case "Not Equals":
                    return statusCode != expectedCode;
                    
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 评估响应时间匹配
     */
    private boolean evaluateResponseTimeMatch(long responseTime, String matchType, String ruleValue) {
        try {
            long expectedTime = Long.parseLong(ruleValue);
            
            switch (matchType) {
                case "Greater Than":
                    return responseTime > expectedTime;
                    
                case "Less Than":
                    return responseTime < expectedTime;
                    
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 评估响应长度匹配
     */
    private boolean evaluateResponseLengthMatch(int length, String matchType, String ruleValue) {
        try {
            int expectedLength = Integer.parseInt(ruleValue);
            
            switch (matchType) {
                case "Greater Than":
                    return length > expectedLength;
                    
                case "Less Than":
                    return length < expectedLength;
                    
                case "Equals":
                    return length == expectedLength;
                    
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 构建漏洞证据
     */
    private String buildEvidence(HttpResponse response, List<Configuration.MatchRule> matchRules) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("匹配的规则:\n");
        
        for (Configuration.MatchRule rule : matchRules) {
            evidence.append(String.format("- [%s] %s: %s\n", 
                rule.getLocation(), rule.getMatchType(), rule.getRule()));
        }
        
        evidence.append("\n响应摘要:\n");
        evidence.append("- 状态码: ").append(response.statusCode()).append("\n");
        evidence.append("- 响应长度: ").append(response.body().length()).append(" bytes\n");
        
        return evidence.toString();
    }
    
    /**
     * 更新JSON参数（从AbstractScanner复制）
     */
    private HttpRequest updateJsonParameter(HttpRequest request, String paramName, String newValue) {
        // ... 实现JSON参数更新逻辑 ...
        // （可以从AbstractScanner中复制这个方法的实现）
        return request;
    }
    
    @Override
    public List<String> getPayloads() {
        // 通用扫描器的payload从Configuration动态获取
        return new ArrayList<>();
    }
}
```

##### 2. 简化 ScannerFactory

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;

/**
 * 简化的扫描器工厂
 * 
 * 现在只返回通用扫描器，不再根据类型选择不同的Scanner
 */
public class ScannerFactory {
    private final Scanner genericScanner;
    private final MontoyaApi api;
    
    public ScannerFactory(MontoyaApi api, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.genericScanner = new GenericRuleBasedScanner(api, realtimeScanner);
        
        api.logging().raiseInfoEvent("✅ 通用规则扫描器已初始化");
    }
    
    /**
     * 获取扫描器（始终返回通用扫描器）
     * 
     * @param type 扫描类型（不再使用，保留参数以保持兼容性）
     * @return 通用规则扫描器
     */
    public Scanner getScanner(String type) {
        return genericScanner;
    }
    
    /**
     * 获取所有可用的扫描器类型
     * 这个方法现在返回空数组，因为类型由配置决定
     */
    public String[] getAvailableScannerTypes() {
        return new String[]{"*"};
    }
}
```

##### 3. UI 改进

```java
// PassiveScanConfigTab.java - 扫描类型输入改为文本框

private JTextField scanTypeField;  // 替代下拉框

private void initializeComponents() {
    // ... 其他组件 ...
    
    // ✅ 改为文本输入框，用户可以自由输入任何扫描类型名称
    scanTypeField = new JTextField(20);
    scanTypeField.setToolTipText("输入扫描类型名称，例如: SQL注入、XSS、XXE、命令注入等");
    
    // 提供一些常见的预设值
    JButton presetsButton = new JButton("常见类型 ▼");
    presetsButton.addActionListener(e -> showScanTypePresets());
}

private void showScanTypePresets() {
    JPopupMenu menu = new JPopupMenu();
    String[] presets = {
        "SQL注入",
        "XSS跨站脚本",
        "XXE外部实体注入",
        "SSRF服务端请求伪造",
        "本地文件包含(LFI)",
        "远程文件包含(RFI)",
        "命令注入",
        "目录遍历",
        "LDAP注入",
        "XPath注入",
        "CRLF注入",
        "模板注入(SSTI)",
        "反序列化漏洞"
    };
    
    for (String preset : presets) {
        JMenuItem item = new JMenuItem(preset);
        item.addActionListener(e -> scanTypeField.setText(preset));
        menu.add(item);
    }
    
    menu.show(presetsButton, 0, presetsButton.getHeight());
}
```

---

## 🎨 使用示例

### 场景1: 添加 XXE 扫描规则

**不需要写任何代码！**

#### 在UI中配置：

1. **打开"被动扫描规则"标签**
2. **点击"添加规则"**
3. **填写配置**:

   **规则名称**: `XXE外部实体注入`
   
   **参数名匹配**: 
   - 匹配类型: `String Match`
   - 参数名: `xml`, `data`, `content`, `body`
   
   **Payload列表**:
   ```xml
   <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><foo>&xxe;</foo>
   <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///c:/windows/win.ini">]><foo>&xxe;</foo>
   <?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "http://burpcollaborator.net">]><foo>&xxe;</foo>
   ```
   
   **匹配规则**:
   | 位置 | 匹配类型 | 规则值 | 操作符 |
   |------|---------|--------|--------|
   | Response Body | Contains | `root:` | OR |
   | Response Body | Contains | `\[fonts\]` | OR |
   | Response Body | Regex Match | `root:.*:0:0:` | OR |

4. **保存规则**
5. **完成！**

现在系统就能检测 XXE 漏洞了，无需任何代码！

---

### 场景2: 添加 SSTI（模板注入）扫描规则

#### 在UI中配置：

**规则名称**: `服务端模板注入(SSTI)`

**参数名匹配**: 
- 匹配类型: `Regex Match`
- 参数名: `.*template.*`, `.*name.*`, `.*msg.*`

**Payload列表**:
```
{{7*7}}
${7*7}
<%= 7*7 %>
${{7*7}}
#{7*7}
{{config}}
{{config.items()}}
```

**匹配规则**:
| 位置 | 匹配类型 | 规则值 | 操作符 |
|------|---------|--------|--------|
| Response Body | Contains | `49` | OR |
| Response Body | Contains | `SECRET_KEY` | OR |
| Response Body | Regex Match | `<Config.*>` | OR |

---

### 场景3: 添加 NoSQL 注入扫描规则

**规则名称**: `NoSQL注入(MongoDB)`

**参数名匹配**: 
- 匹配类型: `String Match`
- 参数名: `id`, `user`, `username`, `email`, `query`

**Payload列表**:
```json
{"$ne": null}
{"$ne": ""}
{"$gt": ""}
{"$regex": ".*"}
' || 1==1//
' || 1==1%00
admin' || '1==1
```

**匹配规则**:
| 位置 | 匹配类型 | 规则值 | 操作符 |
|------|---------|--------|--------|
| Response Body | Contains | `"success":true` | OR |
| Response Body | Contains | `"admin"` | OR |
| Status Code | Equals | `200` | AND |
| Response Length | Greater Than | `100` | AND |

---

## 📊 对比分析

### 改造前 vs 改造后

| 项目 | 改造前 | 改造后 |
|------|--------|--------|
| **添加新扫描类型** | 编写Scanner类（100+行代码） | UI配置（1分钟） |
| **修改检测规则** | 修改代码、重新编译 | UI直接修改 |
| **扫描类型限制** | 硬编码的几种类型 | 无限制 |
| **扩展性** | 需要Java开发知识 | 任何人都可以 |
| **规则分享** | 困难 | 导出/导入JSON |
| **代码维护** | 需要维护多个Scanner类 | 只有一个通用Scanner |

---

## 🚀 实施步骤

### Phase 1: 核心改造（2-3小时）

1. ✅ 创建 `GenericRuleBasedScanner.java`
2. ✅ 简化 `ScannerFactory.java`
3. ✅ 测试基本功能

### Phase 2: UI优化（1-2小时）

1. ✅ 扫描类型改为文本输入框
2. ✅ 添加常见类型预设菜单
3. ✅ 优化匹配规则配置界面

### Phase 3: 增强功能（可选）

1. 规则模板导入导出
2. 规则市场（分享规则）
3. 规则有效性验证
4. 批量导入规则

---

## 💡 额外优化建议

### 1. 匹配规则增强

可以考虑添加更多匹配类型：

```java
// 新增匹配类型
- "Response Body Length Change" (响应长度变化)
- "Response Time Diff" (响应时间差异)
- "Header Exists" (响应头存在)
- "Cookie Set" (设置了Cookie)
- "Redirect To" (重定向到某个URL)
```

### 2. Payload 变量支持

```
支持变量替换：
{{RANDOM}}  → 生成随机字符串
{{NUMBER}}  → 生成随机数字
{{HOST}}    → 当前主机名
{{PARAM}}   → 当前参数名

示例:
' OR 1=1 UNION SELECT {{NUMBER}}--
```

### 3. 规则测试功能

在UI中添加"测试规则"按钮：
- 输入测试URL
- 实时查看payload效果
- 验证匹配规则是否正确

### 4. 规则有效性评分

```java
public class RuleEffectiveness {
    private int totalTests;      // 总测试次数
    private int detectedVulns;   // 检测到的漏洞数
    private int falsePositives;  // 误报数
    
    public double getAccuracy() {
        return (double) detectedVulns / totalTests;
    }
}
```

显示在UI中，帮助用户优化规则。

---

## 🎯 核心优势总结

### 对于用户
1. ✅ **无需编程**: 完全通过UI配置
2. ✅ **灵活性高**: 可以添加任何漏洞类型
3. ✅ **快速迭代**: 修改规则立即生效
4. ✅ **易于分享**: 规则可以导出为JSON

### 对于开发者
1. ✅ **代码简化**: 删除多个Scanner类
2. ✅ **易于维护**: 只需维护一个通用Scanner
3. ✅ **扩展性强**: 添加新功能只需修改通用Scanner
4. ✅ **架构清晰**: 数据驱动，逻辑集中

---

## ❓ 常见问题

### Q1: 通用扫描器会不会性能较差？
**A**: 不会。性能主要取决于HTTP请求数量，而不是Scanner实现。通用Scanner和专用Scanner在性能上没有差异。

### Q2: 复杂的漏洞检测逻辑怎么实现？
**A**: 大多数漏洞检测都是"发送payload → 检查响应"的模式，通用Scanner完全够用。如果确实有极其复杂的逻辑（如需要多步骤交互），可以保留一个"高级Scanner"接口。

### Q3: 现有的规则配置会丢失吗？
**A**: 不会。现有的Configuration完全兼容，只需确保 `customLabel` 字段有值即可。

### Q4: 能否同时支持通用Scanner和专用Scanner？
**A**: 可以。可以让ScannerFactory先尝试获取专用Scanner，如果没有则返回通用Scanner。这样既保持了兼容性，又提供了扩展性。

---

## 🎬 结论

**推荐采用"完全通用Scanner"方案**，因为：

1. ✅ **实现成本低**: 只需创建一个类
2. ✅ **效果明显**: 从"需要编程"到"UI配置"
3. ✅ **用户体验好**: 渗透测试人员可以快速添加自定义规则
4. ✅ **架构更优**: 代码更简洁，更易维护

**要我现在开始实施吗？** 还是你想先讨论一些细节？


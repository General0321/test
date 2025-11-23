# XProbe - Burp Suite 被动安全扫描器

> 基于多阶段链式检测架构（MSCDA）的智能漏洞检测插件，支持链式检测、跨阶段变量传递和自动参数发现

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/xprobe/xprobe)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Burp Suite](https://img.shields.io/badge/Burp%20Suite-2023.1+-green.svg)](https://portswigger.net/burp)

## 📋 目录

- [简介](#简介)
- [核心特性](#核心特性)
- [架构设计](#架构设计)
- [快速开始](#快速开始)
- [详细使用指南](#详细使用指南)
- [配置详解](#配置详解)
- [高级功能](#高级功能)
- [API 文档](#api-文档)
- [开发指南](#开发指南)
- [故障排查](#故障排查)
- [常见问题](#常见问题)
- [更新日志](#更新日志)
- [许可证](#许可证)

---

## 📌 简介

XProbe 是一款专为 Burp Suite Professional 设计的被动安全扫描器插件，采用创新的**多阶段链式检测架构（Multi-Stage Chain Detection Architecture, MSCDA）**，能够检测复杂的多阶段漏洞，如 IDOR、CSRF Token 绕过、时间盲注、布尔盲注、SQL 注入、XSS 等。

### 为什么选择 XProbe？

#### 🎯 多阶段链式检测架构（MSCDA）
- **灵活组合**：通过多个检测阶段（Stage），构建复杂的链式检测流程
- **变量传递**：自动提取和传递变量，支持跨阶段数据流转
- **逻辑表达式**：支持 AND/OR/NOT 逻辑组合，精确控制检测逻辑

#### 🤖 智能参数发现
- **Java 原生 Arjun**：无需外部 Python 依赖，跨平台运行
- **实时触发**：参数数量达到阈值时自动触发扫描
- **增量扫描**：只扫描新发现的参数，避免重复

#### ⚡ 高性能
- **LRU 缓存**：原始响应缓存，避免重复请求
- **智能去重**：多级去重机制，支持多种去重颗粒度
- **线程池优化**：可配置线程池，充分利用多核 CPU

#### 🎨 易用界面
- **图形化配置**：直观的图形界面，无需编写代码
- **实时监控**：仪表板实时显示扫描统计和活动日志
- **结果管理**：强大的结果查看和管理功能

### 适用场景

- **Web 应用安全测试**：自动化漏洞检测
- **渗透测试**：辅助安全测试人员发现漏洞
- **代码审计**：验证修复后的漏洞是否真正修复
- **安全研究**：研究新的漏洞检测技术

---

## ✨ 核心特性

### 1. 多阶段链式检测架构（MSCDA）

#### 什么是检测阶段（Stage）？
一个**检测阶段（Stage）**包含：
- **请求配置**：定义哪些请求需要检测（匹配条件）和如何修改请求（注入点）
- **响应配置**：定义如何判断响应是否匹配（匹配条件）

#### 简单模式 vs 高级模式

**简单模式**：单个检测阶段，适合简单漏洞检测
```
请求匹配 → 注入Payload → 响应匹配 → 结果
```

**高级模式**：多个检测阶段 + 逻辑表达式，适合复杂漏洞检测
```
Stage 1: 创建订单 → 提取 order_id
Stage 2: 使用 order_id 访问订单
表达式: 1 AND 2  (两个阶段都成功才算漏洞)
```

### 2. 多模式检测

XProbe 支持 5 种检测模式：

#### 标准模式（STANDARD）
- **适用场景**：基础检测、单一条件验证
- **逻辑**：请求匹配 + 响应匹配 = 成功

#### 时间验证模式（TIME_BASED_VERIFICATION）
- **适用场景**：SQL 时间盲注、命令注入延迟
- **逻辑**：检查多个请求的响应时间呈线性增长关系
- **案例**：HackerOne #1024984, #1034625

#### 布尔对比模式（BOOLEAN_COMPARISON）
- **适用场景**：SQL 布尔盲注、逻辑漏洞
- **逻辑**：TRUE/FALSE payload 响应存在显著差异
- **案例**：HackerOne #1102591, #1107536

#### 反射确认模式（REFLECTION_CONFIRMATION）
- **适用场景**：XSS、模板注入、SSTI
- **逻辑**：注入的唯一标记在响应中可见
- **案例**：HackerOne #1003433, #1040533

#### 链式验证模式（MULTI_STAGE_CHAINED）
- **适用场景**：IDOR 链、会话固定、状态依赖漏洞
- **逻辑**：按顺序执行多个检测阶段，提取中间结果传递给下一阶段
- **案例**：IDOR 账户接管、订单篡改

### 3. 变量传递系统

#### 变量提取
从响应中提取变量，供后续检测阶段使用：

```json
{
    "order_id": "\"order_id\":\"(\\d+)\"",
    "csrf_token": "name=\"csrf_token\" value=\"([^\"]+)\""
}
```

#### 变量使用
在 Payload 中使用变量：

```
GET /api/order/{{STAGE:1:order_id}}
POST /api/submit?csrf_token={{STAGE:1:csrf_token}}
```

#### 支持的变量格式
- `{{STAGE:id:name}}` - 从指定检测阶段获取变量
- `{{VAR:name}}` - 从变量映射中获取变量
- `{{ORIGINAL}}` - 原始值
- `{{RANDOM_STRING}}` - 随机字符串
- `{{UUID}}` - UUID
- `{{TIMESTAMP}}` - 时间戳
- `{{COLLABORATOR}}` - Burp Collaborator 域名

### 4. 智能参数发现（Arjun）

#### Java 原生实现
- **无需外部依赖**：纯 Java 实现，无需 Python 环境
- **跨平台**：不受 macOS SIP 等安全限制
- **更强大的算法**：改进的异常检测算法

#### 核心功能
- **自动发现隐藏参数**：GET/POST/POST-JSON 全支持
- **实时参数收集**：从请求和响应中自动收集参数
- **智能触发**：参数数量达到阈值时自动触发扫描
- **增量扫描**：只扫描新发现的参数，避免重复

#### 检测算法
1. **基线建立**：发送原始请求，建立响应基线
2. **批量探测**：分块发送参数探测请求
3. **异常检测**：对比响应与基线，检测异常
4. **参数验证**：验证候选参数（多次请求确认）

### 5. 性能优化

#### 缓存策略
- **LRU 缓存**：原始响应缓存（容量：2000条）
- **FIFO 缓存**：被动扫描去重（容量：100,000条）
- **O(1) 查找**：快速查找原始响应

#### 线程池优化
- **可配置线程池**：核心线程数、最大线程数、队列大小
- **自动调整**：根据 CPU 核心数自动调整
- **优雅关闭**：等待任务完成后再关闭

#### 去重机制
- **多级去重**：请求去重、参数去重、结果去重
- **多种颗粒度**：全局、主机、路径、参数等
- **自动检测**：根据规则类型自动选择去重颗粒度

---

## 🏗️ 架构设计

### 整体架构

XProbe 采用分层架构设计，主要分为以下层次：

```
┌─────────────────────────────────────────┐
│         Burp Suite Montoya API          │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          XProbe 主入口层                 │
│  (XProbe.java - 初始化所有组件)          │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          HTTP 请求处理层                 │
│  (RequestHandler - 拦截和处理流量)        │
└─────────────────────────────────────────┘
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
┌───────────────┐      ┌───────────────┐
│  实时扫描层    │      │  任务调度层    │
│ (参数收集)     │      │ (扫描任务)     │
└───────────────┘      └───────────────┘
        ↓                       ↓
┌───────────────┐      ┌───────────────┐
│  参数发现层    │      │  扫描引擎层    │
│  (Arjun)      │      │ (配对评估)     │
└───────────────┘      └───────────────┘
```

### 核心模块

详细架构文档请参考：[ARCHITECTURE.md](ARCHITECTURE.md)

#### 1. 配置管理层
- `XProbeConfigManager`: 统一配置管理器
- `ConfigurationManager`: 扫描规则管理器
- `XProbeConfig`: 统一配置类
- `Configuration`: 扫描规则配置
- `RuleMatchPair`: 请求-响应配对

#### 2. HTTP 请求处理层
- `RequestHandler`: HTTP 请求处理器
- `RequestFilter`: 请求过滤器
- `GlobalFilter`: 全局过滤器
- `OriginalResponseCache`: 原始响应缓存

#### 3. 实时扫描层
- `RealtimeScannerRefactored`: 实时扫描器
- `ParameterCollector`: 参数收集器
- `ParameterManager`: 参数管理器

#### 4. 任务调度层
- `TaskScheduler`: 任务调度器
- `ScannerFactory`: 扫描器工厂
- `UniversalScanner`: 通用扫描器

#### 5. 参数发现层
- `ArjunService`: Arjun 服务
- `ParamDiscoveryEngine`: 参数发现引擎
- `AnomalyDetector`: 异常检测器
- `ChunkProcessor`: 分块处理器

#### 6. 用户界面层
- `DashboardTab`: 仪表板
- `ScanResultTab`: 扫描结果
- `PassiveScanConfigTab`: 被动扫描规则
- `ActiveProbeTab`: 主动探测
- `UnifiedConfigTab`: 配置中心

---

## 🚀 快速开始

### 系统要求

- **Burp Suite Professional** 2023.1 或更高版本
- **Java** 17 或更高版本
- **操作系统**：Windows、macOS、Linux

### 安装步骤

#### 1. 构建项目

```bash
# 克隆项目（如果有）
git clone <repository-url>
cd XProbe

# 构建项目
./gradlew build

# 构建产物位于
build/libs/XProbe-1.0.0.jar
```

#### 2. 加载插件

1. 打开 **Burp Suite Professional**
2. 进入 **Extensions** → **Installed**
3. 点击 **Add** 按钮
4. 选择：
   - **Extension type**: Java
   - **Extension file**: 选择 `build/libs/XProbe-1.0.0.jar`
5. 点击 **Next**，等待插件加载完成

#### 3. 验证安装

- 插件加载成功后，Burp Suite 顶部会出现 **XProbe** 标签页
- 点击进入，应该能看到仪表板界面
- 检查 **Extensions** → **Output** 标签页，应该看到初始化日志

### 第一个扫描规则

#### 示例：检测 SQL 注入

1. **进入规则配置**
   - 点击 **XProbe** 标签页
   - 切换到 **🔍 被动扫描规则** 标签

2. **添加规则**
   - 点击 **添加规则** 按钮
   - 输入规则名称：`SQL Injection Detection`
   - 选择模式：**简单模式**

3. **配置请求匹配**
   - 点击 **请求配置** → **匹配条件**
   - 添加条件：
     - 类型：`URL Path`
     - 匹配类型：`Contains`
     - 值：`/api/query`
   - 点击 **确定**

4. **配置注入点**
   - 点击 **请求配置** → **注入点**
   - 添加注入点：
     - 类型：`Parameter Value`
     - 目标名称：`id`
   - 点击 **确定**

5. **配置 Payload**
   - 点击 **请求配置** → **Payload**
   - 添加 Payload：`' OR '1'='1--`
   - 点击 **确定**

6. **配置响应匹配**
   - 点击 **响应配置** → **匹配条件**
   - 添加条件：
     - 位置：`Body`
     - 匹配类型：`Contains`
     - 规则：`SQL syntax`
   - 点击 **确定**

7. **保存规则**
   - 点击 **保存** 按钮
   - 确保规则已启用（复选框已勾选）

8. **测试规则**
   - 在 Burp Proxy 中访问包含 `/api/query?id=1` 的请求
   - 查看 **📋 扫描结果** 标签页，应该能看到检测结果

---

## 📖 详细使用指南

### 1. 检测阶段配置详解

#### 请求配置（UnifiedHttpConfig）

##### 匹配条件（RequestCondition）

定义哪些请求需要检测：

**条件类型**：
- `Content-Type`: Content-Type 匹配
- `URL Path`: URL 路径匹配
- `HTTP Method`: HTTP 方法匹配
- `Request Header`: 请求头匹配
- `Parameter Name`: 参数名匹配
- `Parameter Exists`: 参数存在性检查
- `Body Contains`: 请求体包含

**匹配类型**：
- `Equals`: 等于
- `Contains`: 包含
- `Starts With`: 开始于
- `Ends With`: 结束于
- `Regex Match`: 正则匹配
- `Not Equals`: 不等于
- `Not Contains`: 不包含

**逻辑操作符**：
- `AND`: 逻辑与
- `OR`: 逻辑或
- `NOT`: 逻辑非

**示例**：
```
条件1: URL Path Contains /api/user
操作符: AND
条件2: HTTP Method Equals POST
结果: 只检测 POST /api/user/* 的请求
```

##### 注入点（InjectionPoint）

定义如何修改请求：

**注入点类型**：
- `Parameter Value`: 参数值注入
- `Parameter Name`: 参数名注入
- `URL Path`: URL 路径注入
- `URL Path Segment`: URL 路径段注入
- `Request Header Value`: 请求头值注入
- `Request Header Name`: 请求头名注入
- `Request Body`: 整个请求体注入
- `Request Body Part`: 请求体部分注入
- `Cookie Value`: Cookie 值注入

**目标名称**：
- 参数名（如：`id`, `username`）
- Header 名（如：`X-Forwarded-For`）
- Cookie 名（如：`session_id`）

**示例**：
```
注入点类型: Parameter Value
目标名称: id
Payload: ' OR '1'='1--
结果: ?id=' OR '1'='1--
```

##### Payload 配置

**单个 Payload**：
```
' OR '1'='1--
```

**多个 Payload**：
```
' OR '1'='1--
' OR '1'='1'--
' OR '1'='1'/*
```

**使用变量**：
```
{{ORIGINAL}}' OR '1'='1--
{{RANDOM_STRING}}
{{UUID}}
{{TIMESTAMP}}
{{PAIR:1:order_id}}
{{VAR:csrf_token}}
```

##### 注入模式（InjectionMode）

**批量模式（BATCH）**：
- 所有匹配的参数同时注入相同的 Payload
- 优点：速度快，请求数少
- 缺点：无法区分是哪个参数触发的漏洞

**示例**：
```
参数: [id, uid]
Payload: [p1, p2]

请求1: ?id=p1&uid=p1
请求2: ?id=p2&uid=p2
```

**逐个模式（INDIVIDUAL）**：
- 每次只对一个匹配的参数注入 Payload
- 优点：精确定位漏洞参数
- 缺点：速度慢，请求数多

**示例**：
```
参数: [id, uid]
Payload: [p1, p2]

请求1: ?id=p1&uid=原值
请求2: ?id=p2&uid=原值
请求3: ?id=原值&uid=p1
请求4: ?id=原值&uid=p2
```

#### 响应配置（UnifiedResponseConfig）

##### 匹配条件（ResponseCondition）

定义如何判断响应是否匹配：

**匹配位置**：
- `Body`: 响应体
- `HTTP-Status_Code`: HTTP 状态码
- `HTTP-Response_Time`: 响应时间
- `HTTP-Response_Headers`: 响应头

**匹配类型**：
- `String Match`: 字符串匹配
- `Regex Match`: 正则匹配
- `Equals`: 等于
- `Not Equals`: 不等于

**示例**：
```
位置: Body
匹配类型: Contains
规则: SQL syntax
结果: 响应体包含 "SQL syntax" 时匹配
```

##### 响应对比（ResponseComparisonConfig）

用于高级检测模式：

**对比模式**：
- `STATUS_CODE`: 状态码对比
- `LENGTH`: 长度对比
- `TIME`: 时间对比
- `BODY`: 响应体对比

**时间对比模式**：
- `ABSOLUTE`: 绝对时间（响应时间在指定范围内）
- `RELATIVE_BASELINE`: 相对基线（响应时间相对于基线的倍数）
- `RELATIVE_PAIR`: 相对配对（响应时间相对于指定配对的倍数）

**示例**：
```
对比模式: TIME
时间对比模式: RELATIVE_PAIR
引用配对: 1
倍数: 3.0
结果: 响应时间应该是 Pair 1 的 3 倍
```

#### 跨阶段配置

##### 变量提取（extractVariables）

从响应中提取变量，供后续检测阶段使用：

**格式**：
```json
{
    "变量名": "正则表达式（必须包含捕获组）"
}
```

**示例**：
```json
{
    "order_id": "\"order_id\":\"(\\d+)\"",
    "csrf_token": "name=\"csrf_token\" value=\"([^\"]+)\"",
    "user_id": "id=(\\d+)"
}
```

**注意事项**：
- 正则表达式必须包含**捕获组**（括号）
- 提取的是第一个捕获组的值
- 如果正则表达式错误，会抛出异常

##### 依赖关系（dependsOnStages）

定义检测阶段之间的依赖关系：

**示例**：
```
Stage 1: 创建订单
Stage 2: 访问订单（依赖 Stage 1）
dependsOnStages: [1]
```

**执行顺序**：
- Stage 1 先执行
- Stage 2 等待 Stage 1 完成后再执行
- Stage 2 可以使用 Stage 1 提取的变量

#### 阶段表达式（stageExpression）

定义多个检测阶段之间的逻辑关系：

**支持的运算符**：
- `AND`: 逻辑与
- `OR`: 逻辑或
- `NOT`: 逻辑非
- `()`: 括号（改变优先级）

**示例**：
```
表达式: 1 AND 2
含义: Stage 1 和 Stage 2 都成功才算漏洞

表达式: 1 OR 2
含义: Stage 1 或 Stage 2 成功就算漏洞

表达式: 1 AND (2 OR 3)
含义: Stage 1 成功，且 (Stage 2 或 Stage 3) 成功
```

### 2. 典型使用场景

#### 场景1：SQL 注入检测

**配置**：
- **请求匹配**：URL 包含 `/api/query`
- **注入点**：参数 `id`
- **Payload**：`' OR '1'='1--`
- **响应匹配**：响应体包含 `SQL syntax`

#### 场景2：IDOR 漏洞检测

**Stage 1 - 创建资源**：
- **请求**：`POST /api/order`，Body: `{"product_id": 123}`
- **响应匹配**：状态码 200
- **变量提取**：`{"order_id": "\"order_id\":\"(\\d+)\""}`

**Stage 2 - 访问资源**：
- **请求**：`GET /api/order/{{STAGE:1:order_id}}`
- **响应匹配**：状态码 200

**阶段表达式**：`1 AND 2`

#### 场景3：时间盲注检测

**Stage 1 - 基线请求**：
- **请求**：`GET /api/user?id=1`
- **用途**：记录正常响应时间

**Stage 2 - 时间延迟注入**：
- **请求**：`GET /api/user?id=1' AND SLEEP(5)--`
- **响应对比**：
  - 对比模式：`TIME`
  - 时间对比模式：`RELATIVE_STAGE`
  - 引用阶段：`1`
  - 倍数：`3.0`（响应时间应该是 Stage 1 的 3 倍）

**阶段表达式**：`1 AND 2`

#### 场景4：CSRF Token 绕过

**Stage 1 - 获取 Token**：
- **请求**：`GET /api/form`
- **变量提取**：`{"csrf_token": "name=\"csrf_token\" value=\"([^\"]+)\""}`

**Stage 2 - 使用 Token 提交**：
- **请求**：`POST /api/submit`
- **Body**：`csrf_token={{STAGE:1:csrf_token}}&data=test`
- **响应匹配**：状态码 200

**阶段表达式**：`1 AND 2`

#### 场景5：XSS 检测

**配置**：
- **请求匹配**：URL 包含 `/api/search`
- **注入点**：参数 `q`
- **Payload**：`<script>alert('{{RANDOM_STRING}}')</script>`
- **响应匹配**：响应体包含 `{{RANDOM_STRING}}`（反射确认）

### 3. 去重配置

#### 去重颗粒度（DeduplicationGranularity）

**AUTO（自动检测）**：
- 根据规则类型自动选择去重颗粒度
- **推荐使用**

**GLOBAL（全局）**：
- 整个规则只测试一次
- Key: `ruleId`

**HOST（主机级）**：
- 每个主机只测试一次
- Key: `ruleId + host`

**PATH（路径级）**：
- 每个路径只测试一次
- Key: `ruleId + host + path`

**REQUEST（请求级）**：
- 每个完整请求只测试一次
- Key: `ruleId + method + host + path + contentType`

**PARAMETER_NAME_GLOBAL（参数名全局）**：
- 相同参数名只测试一次（全局）
- Key: `ruleId + parameterName`

**PARAMETER_NAME_PER_PATH（参数名路径）**：
- 每个路径下的参数名分别测试
- Key: `ruleId + host + path + parameterName`

**PARAMETER（参数级）**：
- 每个请求中的参数分别测试
- Key: `ruleId + method + host + path + contentType + parameterName`

**INJECTION_POINT（注入点级）**：
- 每个注入点分别测试
- Key: `ruleId + method + host + path + contentType + injectionPointHash`

**NONE（无去重）**：
- 每次都测试（Fuzzing 模式）
- Key: `ruleId + timestamp + random`

---

## ⚙️ 配置详解

### 全局配置

在 **⚙️ 配置中心** 标签页中配置：

#### 黑白名单

**白名单**：
- 启用后，只有匹配白名单规则的请求才会被处理
- 支持正则表达式

**黑名单**：
- 启用后，匹配黑名单规则的请求会被忽略
- 支持正则表达式

**示例**：
```
白名单:
- ^https://example\.com/.*
- ^https://api\.example\.com/.*

黑名单:
- .*\.(js|css|jpg|png|gif)$
- .*/static/.*
```

#### Arjun 配置

**基础配置**：
- **启用 Arjun**：是否启用参数发现功能
- **Chunk 大小**：每次扫描的参数数量（10-1000，默认：250）
- **超时时间**：请求超时时间（5-60秒，默认：15秒）

**高级配置**：
- **稳定模式**：启用后，每个请求之间随机延迟 3-10 秒
- **并发线程数**：并发扫描线程数（1-20，默认：5）
- **最大重试次数**：失败重试次数（1-10，默认：5）
- **速率限制**：每秒最大请求数（1-10000，默认：9999）
- **自定义 HTTP 头**：添加或覆盖 HTTP 头

**实时模式配置**：
- **参数阈值**：达到此数量时自动触发扫描（1-100，默认：15）
- **扫描间隔**：两次扫描之间的最小间隔（60-3600秒，默认：300秒）

#### 参数收集模式

**仅参数名（PARAMETERS_ONLY）**：
- 只收集参数名
- 性能更好，内存占用更少

**参数名+关键词（PARAMETERS_AND_KEYWORDS）**：
- 收集参数名和响应中的关键词
- 可以发现更多隐藏参数

#### 全局参数字典

添加全局参数，这些参数会被所有 Arjun 扫描使用：

```
id
user_id
username
email
token
csrf_token
...
```

#### 线程池配置

**核心线程数**：
- `-1`：自动（CPU核心数 × 2）
- 其他值：手动指定

**最大线程数**：
- `-1`：自动（核心线程数 × 2）
- 其他值：手动指定

**队列大小**：
- 任务队列大小（默认：2000）
- 队列满时，由调用线程执行任务

**空闲线程存活时间**：
- 空闲线程的存活时间（默认：120秒）

#### 日志模式

**记录所有流量（ALL_REQUESTS）**：
- 记录被动扫描发出的所有请求（包括未命中的）
- 优点：完整记录
- 缺点：内存占用大

**仅记录命中（MATCHED_ONLY）**：
- 只记录命中规则的请求
- 优点：节省内存和性能
- 缺点：无法查看未命中的请求

### 扫描规则配置

在 **🔍 被动扫描规则** 标签页中：

#### 添加规则

1. 点击 **添加规则** 按钮
2. 输入规则信息：
   - **规则名称**：自定义标签
   - **描述**：规则描述（可选）
   - **模式**：简单模式或高级模式

#### 编辑规则

1. 在规则列表中，点击规则名称
2. 编辑规则配置
3. 点击 **保存** 按钮

#### 删除规则

1. 在规则列表中，选择要删除的规则
2. 点击 **删除** 按钮
3. 确认删除

#### 启用/禁用规则

- 勾选/取消勾选规则前的复选框

#### 导入/导出规则

**导出规则**：
1. 选择要导出的规则
2. 点击 **导出** 按钮
3. 选择保存位置

**导入规则**：
1. 点击 **导入** 按钮
2. 选择规则文件
3. 确认导入

---

## 🎯 高级功能

### 1. 批量模式 vs 逐个模式

**批量模式（BATCH）**：
- 所有匹配的参数同时注入相同的 Payload
- 优点：速度快，请求数少
- 缺点：无法区分是哪个参数触发的漏洞
- **适用场景**：快速测试多个参数

**逐个模式（INDIVIDUAL）**：
- 每次只对一个匹配的参数注入 Payload
- 优点：精确定位漏洞参数
- 缺点：速度慢，请求数多
- **适用场景**：需要精确定位漏洞参数

### 2. 被动检测 vs 主动注入

**被动检测**：
- 不修改请求，直接使用原始响应进行匹配
- 适用场景：检测响应中的敏感信息泄露、默认配置等

**主动注入**：
- 修改请求，注入 Payload，分析响应
- 适用场景：SQL 注入、XSS、命令注入等需要注入 Payload 的漏洞

### 3. 变量提取规则

**格式**：`{"变量名": "正则表达式"}`

**要求**：
- 正则表达式必须包含**捕获组**（括号）
- 提取的是第一个捕获组的值

**示例**：
```json
{
  "order_id": "\"order_id\":\"(\\d+)\"",
  "csrf_token": "name=\"csrf_token\" value=\"([^\"]+)\"",
  "user_id": "id=(\\d+)"
}
```

**常见提取场景**：

**JSON 响应**：
```json
{
    "id": "\"id\":(\\d+)",
    "token": "\"token\":\"([^\"]+)\""
}
```

**HTML 响应**：
```json
{
    "csrf_token": "name=\"csrf_token\" value=\"([^\"]+)\"",
    "user_id": "data-user-id=\"(\\d+)\""
}
```

**响应头**：
```json
{
    "session_id": "Set-Cookie: session_id=([^;]+)"
}
```

### 4. Payload 变量系统

#### 支持的变量

**原始值变量**：
- `{{ORIGINAL}}` - 原始值
- `{{ORIGINAL_URL_ENCODED}}` - URL 编码的原始值
- `{{ORIGINAL_BASE64}}` - Base64 编码的原始值

**随机变量**：
- `{{RANDOM_STRING}}` - 随机字符串（8-16 字符）
- `{{RANDOM_INT}}` - 随机整数（0-999999）
- `{{UUID}}` - UUID（标准格式）
- `{{TIMESTAMP}}` - 时间戳（毫秒）

**编码变量**：
- `{{BASE64:xxx}}` - Base64 编码
- `{{URL_ENCODE:xxx}}` - URL 编码

**跨阶段变量**：
- `{{VAR:name}}` - 从变量映射中获取变量
- `{{STAGE:id:name}}` - 从指定检测阶段获取变量

**Burp 集成**：
- `{{COLLABORATOR}}` - Burp Collaborator 域名

#### 使用示例

**追加模式**：
```
{{ORIGINAL}}' OR '1'='1--
结果: 原值 + ' OR '1'='1--
```

**前置模式**：
```
admin{{ORIGINAL}}
结果: admin + 原值
```

**替换模式**：
```
' OR '1'='1--
结果: 完全替换原值
```

**组合模式**：
```
{{ORIGINAL}}_{{RANDOM_STRING}}
结果: 原值_随机字符串
```

**跨阶段使用**：
```
GET /api/order/{{STAGE:1:order_id}}
POST /api/submit?token={{STAGE:1:csrf_token}}
```

### 5. 响应对比详解

#### 状态码对比

**相等**：
- 响应状态码等于指定值
- 示例：状态码 = 200

**不等**：
- 响应状态码不等于指定值
- 示例：状态码 ≠ 200

#### 长度对比

**相等**：
- 响应长度等于指定值
- 示例：长度 = 1024

**差值**：
- 响应长度差值在阈值内
- 示例：|长度 - 基线长度| < 100

#### 时间对比

**绝对时间**：
- 响应时间在指定范围内
- 示例：1000ms < 响应时间 < 5000ms

**相对基线**：
- 响应时间相对于基线的倍数
- 示例：响应时间 = 基线时间 × 3.0

**相对阶段**：
- 响应时间相对于指定检测阶段的倍数
- 示例：Stage 2 响应时间 = Stage 1 响应时间 × 3.0

#### 响应体对比

**相等**：
- 响应体完全相同
- 使用：字符串比较

**不等**：
- 响应体不完全相同
- 使用：字符串比较

**相似**：
- 响应体相似度高于阈值
- 使用：Jaccard 相似度或 TF-IDF 相似度

**不相似**：
- 响应体相似度低于阈值
- 使用：Jaccard 相似度或 TF-IDF 相似度

---

## 📚 API 文档

### 核心类

#### XProbe

主入口类，实现 `BurpExtension` 接口。

**方法**：
- `initialize(MontoyaApi api)`: 初始化插件

#### RequestHandler

HTTP 请求处理器，实现 `HttpHandler` 接口。

**方法**：
- `handleHttpRequestToBeSent(HttpRequestToBeSent)`: 处理请求发送事件
- `handleHttpResponseReceived(HttpResponseReceived)`: 处理响应接收事件

#### RealtimeScannerRefactored

实时扫描器，负责参数收集和 Arjun 触发。

**主要方法**：
- `processNewRequest(HttpRequest)`: 处理新请求
- `processResponse(HttpRequest, HttpResponseReceived)`: 处理响应
- `triggerManualArjunScan()`: 手动触发 Arjun 扫描
- `setCollectionMode(CollectionMode)`: 设置收集模式
- `setMinParameterThreshold(int)`: 设置参数阈值
- `setCooldownSeconds(int)`: 设置冷却时间

#### TaskScheduler

任务调度器，负责调度和执行扫描任务。

**主要方法**：
- `scheduleScan(List<ScanTask>)`: 调度扫描任务
- `shutdown()`: 关闭调度器

#### UniversalScanner

通用扫描器，实现多阶段链式检测架构的核心扫描逻辑。

**主要方法**：
- `canScan(ScanTask)`: 判断是否可以扫描
- `scan(ScanTask)`: 执行扫描

#### ArjunService

Arjun 参数发现服务。

**主要方法**：
- `scan(HttpRequest, Set<String>)`: 扫描 URL 查找隐藏参数
- `setUserCustomDictionary(Set<String>)`: 设置用户自定义字典
- `getStatistics()`: 获取统计信息

### 数据模型

#### ScanTask

扫描任务。

**字段**：
- `parameter`: 参数（可为 null）
- `configuration`: 扫描规则配置
- `request`: 请求
- `context`: 请求上下文

#### ScanResult

扫描结果。

**字段**：
- `isVulnerable`: 是否发现漏洞
- `scanType`: 扫描类型
- `parameterName`: 参数名
- `payload`: Payload
- `originalRequest`: 原始请求
- `modifiedRequest`: 修改后的请求
- `response`: 响应
- `responseTime`: 响应时间
- `evidence`: 证据

#### Configuration

扫描规则配置。

**字段**：
- `ruleId`: 规则 ID（UUID）
- `customLabel`: 规则名称
- `description`: 描述
- `enabled`: 是否启用
- `pairs`: 配对列表
- `pairExpression`: 配对表达式
- `deduplicationGranularity`: 去重颗粒度

#### RuleMatchPair

请求-响应配对。

**字段**：
- `id`: 配对 ID
- `label`: 配对标签
- `enabled`: 是否启用
- `requestConfig`: 请求配置
- `responseConfig`: 响应配置
- `mode`: 检测模式
- `comparisonConfig`: 响应对比配置
- `dependsOnPairs`: 依赖的前置配对
- `extractVariables`: 变量提取规则

---

## 🛠️ 开发指南

### 项目结构

```
XProbe/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/xprobe/scanner/
│   │   │       ├── XProbe.java              # 主入口
│   │   │       ├── config/                   # 配置类
│   │   │       ├── core/                     # 核心功能
│   │   │       ├── active/                   # 主动扫描
│   │   │       ├── scanners/                 # 扫描器
│   │   │       ├── ui/                        # 用户界面
│   │   │       ├── models/                   # 数据模型
│   │   │       ├── utils/                    # 工具类
│   │   │       └── Logs/                     # 日志
│   │   └── resources/
│   └── test/
│       └── java/
├── build.gradle                               # Gradle 构建文件
├── settings.gradle                            # Gradle 设置
├── gradle.properties                          # Gradle 属性
└── README.md                                  # 本文档
```

### 构建项目

```bash
# 清理
./gradlew clean

# 构建
./gradlew build

# 运行测试
./gradlew test

# 生成 JAR
./gradlew jar
```

### 添加新扫描器

1. 实现 `Scanner` 接口
2. 在 `ScannerFactory` 中注册
3. 实现 `canScan()` 和 `scan()` 方法

**示例**：
```java
public class CustomScanner extends AbstractScanner {
    @Override
    public String getType() {
        return "CUSTOM_SCANNER";
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        // 判断是否可以扫描
    }
    
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        // 执行扫描
    }
}
```

### 添加新 UI 组件

1. 创建新的 Tab 类
2. 在 `XProbe.constructMainTab()` 中添加
3. 实现必要的接口

### 调试技巧

1. **查看日志**：
   - Burp Suite → Extensions → Output
   - 使用 `api.logging().raiseInfoEvent()` 记录日志

2. **断点调试**：
   - 在 IDE 中设置断点
   - 使用 Burp Suite 的调试模式

3. **性能分析**：
   - 使用 Burp Suite 的性能分析工具
   - 检查线程池使用情况

---

## 🔍 故障排查

### 常见问题

#### 1. 插件无法加载

**症状**：插件加载失败，显示错误信息

**解决方案**：
- 检查 Java 版本（需要 17+）
- 检查 Burp Suite 版本（需要 2023.1+）
- 检查 JAR 文件是否完整
- 查看 Extensions → Output 中的错误信息

#### 2. 扫描结果为空

**症状**：规则配置正确，但没有扫描结果

**解决方案**：
- 检查规则是否已启用
- 检查请求是否匹配规则的条件
- 检查响应是否匹配规则的匹配条件
- 查看日志了解详细信息
- 检查去重配置是否过于严格

#### 3. Arjun 扫描失败

**症状**：Arjun 扫描没有发现参数或报错

**解决方案**：
- 检查 Arjun 是否已启用
- 检查参数阈值和扫描间隔配置
- 检查网络连接
- 查看日志了解详细错误信息
- 尝试手动触发扫描

#### 4. 内存占用过高

**症状**：Burp Suite 内存占用持续增长

**解决方案**：
- 调整缓存大小配置
- 使用 "仅记录命中" 日志模式
- 定期清理缓存
- 减少并发线程数

#### 5. 变量提取失败

**症状**：变量提取返回空值

**解决方案**：
- 检查正则表达式是否正确（必须包含捕获组）
- 检查响应中是否真的包含要提取的内容
- 使用 Burp Suite 的响应查看器验证
- 查看日志了解详细错误信息

### 日志分析

#### 日志级别

- **INFO**：一般信息
- **DEBUG**：调试信息
- **ERROR**：错误信息

#### 关键日志

**初始化日志**：
```
✅ 配置管理器初始化成功
✅ 加载了 X 条扫描规则
✅ 实时扫描器已初始化
✅ 线程池初始化完成
```

**扫描日志**：
```
✅ 规则 [规则名] 匹配，准备扫描: URL
配对 [1] 匹配成功
✅ 发现漏洞: 规则名 in parameter '参数名' with payload: Payload
```

**Arjun 日志**：
```
🔍 Arjun扫描开始: METHOD URL (字典: X 个参数)
✅ Arjun发现参数: METHOD URL - [参数列表]
```

### 性能优化建议

1. **合理使用去重**：根据场景选择合适的去重颗粒度
2. **优化 Payload**：使用变量减少重复配置
3. **调整线程池**：根据 CPU 核心数调整线程池大小
4. **使用缓存**：充分利用原始响应缓存
5. **定期清理**：长时间运行后，定期清理缓存

---

## ❓ 常见问题

### Q: 为什么扫描结果中没有显示漏洞？

**A**: 检查以下几点：
1. 规则是否已启用
2. 请求是否匹配规则的条件
3. 响应是否匹配规则的匹配条件
4. 查看日志了解详细信息
5. 检查去重配置是否过于严格

### Q: 如何提取 JSON 响应中的变量？

**A**: 使用正则表达式，例如：
```json
{
    "user_id": "\"id\":(\\d+)",
    "token": "\"token\":\"([^\"]+)\""
}
```

### Q: 变量提取失败怎么办？

**A**: 检查：
1. 正则表达式是否正确（必须包含捕获组）
2. 响应中是否真的包含要提取的内容
3. 查看日志了解详细错误信息
4. 使用 Burp Suite 的响应查看器验证

### Q: 如何检测时间盲注？

**A**: 使用高级模式：
1. Stage 1：基线请求（正常请求）
2. Stage 2：注入时间延迟 Payload
3. 配置响应对比：时间对比模式选择 "相对阶段"，引用 Stage 1，倍数设置为 3.0

### Q: Arjun 扫描很慢怎么办？

**A**: 优化建议：
1. 减少 Chunk 大小
2. 增加并发线程数
3. 禁用稳定模式
4. 调整速率限制

### Q: 如何导出扫描结果？

**A**: 在 **📋 扫描结果** 标签页中：
1. 选择要导出的结果
2. 点击 **导出** 按钮
3. 选择导出格式（JSON/CSV）

### Q: 支持哪些 HTTP 方法？

**A**: 支持所有 HTTP 方法：
- GET
- POST
- PUT
- DELETE
- PATCH
- HEAD
- OPTIONS

### Q: 可以检测哪些类型的漏洞？

**A**: XProbe 可以检测各种类型的漏洞：
- SQL 注入
- XSS（跨站脚本）
- 命令注入
- IDOR（不安全的直接对象引用）
- CSRF Token 绕过
- 时间盲注
- 布尔盲注
- 路径遍历
- XXE（XML 外部实体）
- SSRF（服务器端请求伪造）
- 等等...

---

## 📝 更新日志

### Version 1.0.0 (2024)

#### 新增功能
- ✅ 多阶段链式检测架构（Multi-Stage Chain Detection Architecture, MSCDA）
- ✅ 多模式检测（标准、时间验证、布尔对比、反射确认、链式验证）
- ✅ 变量传递系统
- ✅ Java 原生 Arjun 实现
- ✅ 智能参数发现
- ✅ 实时参数收集
- ✅ 响应对比引擎
- ✅ 多级去重机制
- ✅ 可配置线程池
- ✅ LRU 缓存优化
- ✅ 图形化配置界面

#### 性能优化
- ✅ 原始响应缓存（LRU，2000条）
- ✅ 被动扫描去重（FIFO，100,000条）
- ✅ 线程池优化
- ✅ 并发控制优化

#### Bug 修复
- ✅ 修复内存泄漏问题
- ✅ 修复线程安全问题
- ✅ 修复变量提取错误处理
- ✅ 修复响应对比算法

---

## 📄 许可证

本项目采用专有许可证，详见 [LICENSE](LICENSE) 文件。

## ⚠️ 免责声明

本工具仅供授权的安全测试使用。使用者必须确保在合法授权的范围内使用本工具，作者不对任何非法使用行为承担责任。

---

## 🙏 致谢

- **Burp Suite** - 提供强大的安全测试平台
- **Arjun** - 参数发现工具（本项目使用 Java 原生实现）
- **所有贡献者** - 感谢所有为项目做出贡献的开发者

---

**版本**: 1.0.0  
**最后更新**: 2024
**作者**: XProbe Team  
**项目地址**: [GitHub Repository](https://github.com/xprobe/xprobe)

---

## 📞 支持与反馈

如有问题或建议，请通过以下方式联系：

- **Issues**: [GitHub Issues](https://github.com/xprobe/xprobe/issues)
- **Email**: support@xprobe.com
- **文档**: [完整文档](ARCHITECTURE.md)

---

**Happy Hacking! 🚀**

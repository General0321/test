# XProbe 快速参考卡片

## 🚀 快速导航

### 核心类速查表

| 类名 | 文件路径 | 主要职责 | 关键方法 |
|------|---------|---------|---------|
| **XProbe** | `src/main/java/com/xprobe/scanner/XProbe.java` | 插件主入口 | `initialize()`, `constructMainTab()` |
| **RequestHandler** | `src/main/java/com/xprobe/scanner/core/RequestHandler.java` | HTTP 请求处理 | `handleHttpRequestToBeSent()`, `handleHttpResponseReceived()` |
| **RealtimeScannerRefactored** | `src/main/java/com/xprobe/scanner/active/RealtimeScannerRefactored.java` | 实时扫描器 | `processNewRequest()`, `triggerManualArjunScan()` |
| **TaskScheduler** | `src/main/java/com/xprobe/scanner/core/TaskScheduler.java` | 任务调度 | `scheduleScan()`, `executeScanTask()` |
| **UniversalScanner** | `src/main/java/com/xprobe/scanner/scanners/UniversalScanner.java` | 通用扫描器 | `scan()`, `evaluatePair()` |
| **ParameterCollector** | `src/main/java/com/xprobe/scanner/active/ParameterCollector.java` | 参数收集 | `collectFromRequest()`, `collectFromResponse()` |
| **ArjunService** | `src/main/java/com/xprobe/scanner/active/arjun/ArjunService.java` | 参数发现 | `scan()`, `getStatistics()` |
| **ParamDiscoveryEngine** | `src/main/java/com/xprobe/scanner/active/arjun/ParamDiscoveryEngine.java` | 参数发现引擎 | `scan()` |
| **OriginalResponseCache** | `src/main/java/com/xprobe/scanner/core/OriginalResponseCache.java` | 响应缓存 | `put()`, `get()` |
| **XProbeConfigManager** | `src/main/java/com/xprobe/scanner/config/XProbeConfigManager.java` | 配置管理 | `getConfig()`, `updateConfig()`, `subscribe()` |

---

## 📊 三大业务流程

### 1️⃣ 被动扫描流程 (Passive Scan)

```
Burp Proxy 
  ↓ (HTTP 响应)
RequestHandler.handleHttpResponseReceived()
  ↓
OriginalResponseCache.put() [缓存原始响应]
  ↓
RealtimeScanner.processResponse() [收集参数]
  ↓
TaskScheduler.scheduleScan() [调度扫描任务]
  ↓
UniversalScanner.scan() [执行规则配对]
  ├─ evaluatePair() [评估每个检测阶段]
  ├─ PayloadVariableResolver.resolvePayload() [解析变量]
  ├─ InjectionPointExecutor.applySingleInjection() [注入 Payload]
  ├─ api.http().sendRequest() [发送请求]
  ├─ UnifiedResponseEvaluator.evaluate() [评估响应]
  └─ ResponseComparisonEngine.hasSignificantDifference() [对比差异]
  ↓
TaskScheduler.logResult() [记录结果]
  ↓
LogModel.add() [添加到日志]
```

**关键参数**:
- 被动扫描启用: `XProbeConfig.enablePassiveScan = true`
- 结果记录模式: `XProbeConfig.scanResultLogMode = "MATCHED_ONLY" | "ALL_REQUESTS"`
- 全局注入模式: `XProbeConfig.globalInjectionMode = "BATCH" | "INDIVIDUAL"`

---

### 2️⃣ 主动探测流程 (Active Probe)

```
UI / 自动触发
  ↓
RealtimeScanner.triggerManualArjunScan(request, params, boolean)
  ↓
ArjunService.scan(request, dictionary)
  ↓
ParamDiscoveryEngine.scan(request, dictionary)
  ├─ initialize() [建立基线]
  │   └─ ResponseBaseline.define()
  ├─ narrowDown() [分块爆破]
  │   ├─ ChunkProcessor.createChunks()
  │   └─ ConcurrentProcessor.processChunks()
  │       └─ AnomalyDetector.compare() [异常检测]
  └─ verify() [参数验证]
      └─ ParamVerifier.verify()
  ↓
ArjunService.convertToArjunResult()
  ↓
RealtimeScanner.notifyArjunResult()
  ↓
ActiveProbeTab.onArjunResult() [UI 更新]
  ↓
RealtimeScanner.triggerVulnerabilityScan() [触发漏洞扫描]
  ↓
TaskScheduler.scheduleScan() [回到被动扫描流程]
```

**关键参数**:
- Arjun 启用: `XProbeConfig.arjunEnabled = true`
- 参数阈值: `XProbeConfig.arjunRealtimeThreshold = 15`
- 冷却时间: `XProbeConfig.arjunRealtimeInterval = 300` (秒)
- 线程数: `XProbeConfig.arjunThreads = 5`
- 速率限制: `XProbeConfig.arjunRateLimit = 9999` (请求/秒)

---

### 3️⃣ 参数收集流程 (Parameter Collection)

```
Burp Proxy
  ↓ (HTTP 请求)
RequestHandler.handleHttpRequestToBeSent()
  ↓
RealtimeScanner.processNewRequest(request)
  ↓
ParameterCollector.collectFromRequest(request)
  ├─ extractUrlParameters()
  ├─ extractBodyParameters()
  └─ extractHeaderParameters()
  ↓
RealtimeScanner.checkAndAutoTriggerArjun(request)
  ↓
检查: 参数数量 >= 15 && 冷却时间已过 300 秒
  ↓ (YES)
RealtimeScanner.triggerArjunForMainDomain(mainDomain)
  ↓
ArjunService.scan() [进入主动探测流程]
```

**收集模式**:
- `PARAMETERS_ONLY` - 仅收集参数名
- `PARAMETERS_AND_KEYWORDS` - 收集参数名和关键词

---

## 🔧 配置速查

### XProbeConfig 关键字段

```json
{
  "enablePassiveScan": true,
  "globalInjectionMode": "BATCH",
  "scanResultLogMode": "MATCHED_ONLY",
  
  "arjunEnabled": true,
  "arjunRealtimeThreshold": 15,
  "arjunRealtimeInterval": 300,
  "arjunThreads": 5,
  "arjunRateLimit": 9999,
  "arjunStableMode": false,
  "arjunMaxRetries": 5,
  
  "collectionMode": "PARAMETERS_ONLY",
  "globalParameters": [],
  
  "scannerCoreThreads": -1,
  "scannerMaxThreads": -1,
  "scannerQueueSize": 2000,
  "scannerKeepAliveSeconds": 120,
  
  "whitelistEnabled": false,
  "blacklistEnabled": false,
  "whitelist": [],
  "blacklist": []
}
```

---

## 💾 缓存与去重

### 三层缓存

| 缓存 | 类型 | 容量 | Key | TTL |
|------|------|------|-----|-----|
| **OriginalResponseCache** | LRU | 2000 | `method\|url` | - |
| **PassiveScanProcessedKeys** | FIFO | 100000 | 去重键 | - |
| **RandomPathBaselineCache** | TTL | - | `method+host+contentType+endpoint` | 300s |

### 去重颗粒度

```
GLOBAL                    → ruleId
HOST                      → ruleId + host
PATH                      → ruleId + host + path
REQUEST                   → ruleId + method + host + path + contentType
PARAMETER_NAME_GLOBAL     → ruleId + parameterName
PARAMETER_NAME_PER_PATH   → ruleId + host + path + parameterName
PARAMETER                 → ruleId + method + host + path + contentType + parameterName
INJECTION_POINT           → ruleId + method + host + path + contentType + injectionPointHash
NONE                      → ruleId + timestamp + random
```

---

## 🎯 Payload 变量支持

| 变量 | 说明 | 示例 |
|------|------|------|
| `{{ORIGINAL}}` | 原始值 | `{{ORIGINAL}}` |
| `{{ORIGINAL_URL_ENCODED}}` | URL 编码原始值 | `{{ORIGINAL_URL_ENCODED}}` |
| `{{ORIGINAL_BASE64}}` | Base64 编码原始值 | `{{ORIGINAL_BASE64}}` |
| `{{RANDOM_STRING}}` | 随机字符串 | `{{RANDOM_STRING:10}}` |
| `{{RANDOM_INT}}` | 随机整数 | `{{RANDOM_INT}}` |
| `{{UUID}}` | UUID | `{{UUID}}` |
| `{{TIMESTAMP}}` | 时间戳 | `{{TIMESTAMP}}` |
| `{{COLLABORATOR}}` | Collaborator 域名 | `{{COLLABORATOR}}` |
| `{{VAR:name}}` | 从变量表获取 | `{{VAR:csrf_token}}` |
| `{{STAGE:id:name}}` | 从检测阶段获取 | `{{STAGE:1:order_id}}` |
| `{{BASE64:xxx}}` | Base64 编码 | `{{BASE64:test}}` |
| `{{URL_ENCODE:xxx}}` | URL 编码 | `{{URL_ENCODE:a&b}}` |

---

## 📍 注入点类型

```
Parameter Value           → 参数值注入
Parameter Name            → 参数名注入
URL Path                  → URL 路径注入
URL Path Segment          → URL 路径段注入
Request Header Value      → 请求头值注入
Request Header Name       → 请求头名注入
Request Body              → 整个请求体注入
Request Body Part         → 请求体部分注入
Cookie Value              → Cookie 值注入
```

---

## 🔍 响应元素类型

```
STATUS_CODE               → 状态码
RESPONSE_HEADERS          → 响应头
RESPONSE_BODY             → 响应体
RESPONSE_TIME             → 响应时间
RESPONSE_LENGTH           → 响应长度
COLLABORATOR              → Collaborator 交互
```

---

## ⚙️ 线程池配置

### 自动计算

```
corePoolSize = CPU核心数 × 2
maximumPoolSize = CPU核心数 × 4
queueSize = 2000
keepAliveTime = 120 秒
rejectionPolicy = CallerRunsPolicy
```

### 手动覆盖

```json
{
  "scannerCoreThreads": 4,
  "scannerMaxThreads": 8
}
```

---

## ⚠️ 关键问题与修复

### 问题 1: 跨子域请求克隆丢失会话上下文

**症状**: Cookie、Authorization 头被清除

**修复**:
```java
// ❌ 错误: 重建请求
HttpRequest newRequest = HttpRequest.httpRequest(...)

// ✅ 正确: 克隆并修改
HttpRequest newRequest = request
    .withService(newService)
    .withHeader("Host", newHost)
    .withHeader("X-XProbe-Arjun", "true");
```

### 问题 2: POST/JSON 请求体被置空

**症状**: 请求体变为 `{}` 或被清空

**修复**:
```java
// ❌ 错误: 替换 body
request.withBody(newBody)

// ✅ 正确: 保留原始 body，通过 API 合并参数
byte[] originalBody = request.body();
// 合并参数到 originalBody
request.withBody(mergedBody);
```

### 问题 3: Content-Length 手写导致截断

**症状**: 请求体被截断

**修复**:
```java
// ❌ 错误: 手写 Content-Length
request.withHeader("Content-Length", String.valueOf(body.length))

// ✅ 正确: 使用 API 自动计算
request.withBody(body);  // API 自动计算 Content-Length
```

### 问题 4: IPv6 地址格式不一致

**症状**: 请求失败或被拒绝

**修复**:
```java
// ✅ 统一使用 [addr]:port 格式
String host = "[::1]:8080";
request.withHeader("Host", host);
```

---

## 📞 常用方法速查

### 获取配置

```java
XProbeConfig config = configManager.getConfig();
boolean passiveScanEnabled = config.isEnablePassiveScan();
String injectionMode = config.getGlobalInjectionMode();
```

### 订阅配置变更

```java
configManager.subscribe(newConfig -> {
    System.out.println("配置已更新");
});
```

### 手动触发 Arjun

```java
Set<String> params = new HashSet<>();
realtimeScanner.triggerManualArjunScan(request, params, true);
```

### 获取参数统计

```java
ParameterCollector.CollectorStatistics stats = 
    parameterCollector.getStatistics();
int totalParams = stats.getTotalParameters();
```

### 获取扫描进度

```java
TaskScheduler.TaskProgressStatistics progress = 
    taskScheduler.getProgressStatistics();
int scanning = progress.getScanningCount();
int waiting = progress.getWaitingCount();
```

---

## 🔗 文件位置速查

| 功能 | 文件路径 |
|------|---------|
| 主入口 | `src/main/java/com/xprobe/scanner/XProbe.java` |
| HTTP 处理 | `src/main/java/com/xprobe/scanner/core/RequestHandler.java` |
| 被动扫描 | `src/main/java/com/xprobe/scanner/active/RealtimeScannerRefactored.java` |
| 任务调度 | `src/main/java/com/xprobe/scanner/core/TaskScheduler.java` |
| 扫描引擎 | `src/main/java/com/xprobe/scanner/scanners/UniversalScanner.java` |
| 参数收集 | `src/main/java/com/xprobe/scanner/active/ParameterCollector.java` |
| 参数发现 | `src/main/java/com/xprobe/scanner/active/arjun/ParamDiscoveryEngine.java` |
| 配置管理 | `src/main/java/com/xprobe/scanner/config/XProbeConfigManager.java` |
| 配置持久化 | `src/main/java/com/xprobe/scanner/config/ConfigPersistence.java` |
| UI 仪表板 | `src/main/java/com/xprobe/scanner/ui/DashboardTab.java` |
| UI 结果 | `src/main/java/com/xprobe/scanner/ui/ScanResultTab.java` |
| UI 主动探测 | `src/main/java/com/xprobe/scanner/ui/ActiveProbeTab.java` |

---

**最后更新**: 2025-12-02  
**版本**: 2025-12-02.2


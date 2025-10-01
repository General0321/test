# XProbe 去重逻辑和黑白名单分析报告

生成时间：2025-10-01

## 一、被动扫描去重逻辑 ✅（已正确实现）

### 当前实现（RealtimeScannerRefactored.java:400-407）

```java
private String generatePassiveScanKey(String method, String host, String path, 
                                     String contentType, String parameterName, 
                                     String scanType) {
    String cleanPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
    String normalizedContentType = normalizeContentType(contentType);
    return method + "|" + host + "|" + cleanPath + "|" + normalizedContentType + 
           "|" + parameterName + "|" + scanType;
}
```

### 去重颗粒度

- ✅ **method** - HTTP方法
- ✅ **host** - 主机名
- ✅ **path** - 路径（去掉query string）
- ✅ **contentType** - 标准化后的Content-Type
- ✅ **parameterName** - 参数名
- ✅ **scanType** - 扫描类型（lfi/sql/ssrf等）

### 实现方式

1. 在 `RequestHandler.checkAndMarkParameterAsScanning()` 中调用去重检查
2. 使用原子操作 `isPassiveScanProcessed()` 检查
3. 立即调用 `markPassiveScanProcessed()` 标记

### 结论

**✅ 被动扫描去重逻辑完全符合要求，无需修改**

---

## 二、主动探测（Arjun）去重逻辑 ❌（存在问题）

### 问题1：ParameterManager 的去重颗粒度不足

#### 当前实现（ParameterManager.java:284-286）

```java
private String generateKey(String mainDomain, String endpoint) {
    return mainDomain + ":" + endpoint;
}
```

#### 当前去重颗粒度

- ✅ **mainDomain** - 主域名（如 example.com）
- ✅ **endpoint** - 路径（如 /api/user）
- ❌ **缺少 method** - 未区分 GET/POST/PUT 等方法
- ❌ **缺少 contentType** - 未区分 JSON/表单 等类型

#### 用户要求的去重颗粒度

```
method + host + content-type + uri + 已探测过的参数
```

#### 影响

同一个endpoint，使用不同的HTTP方法或Content-Type时，会被错误地认为已经探测过，导致漏扫。

**示例：**
```
POST /api/user (application/json) - 探测过
GET  /api/user (application/x-www-form-urlencoded) - 会被跳过（错误！）
```

### 问题2：ParameterCollector 的URL去重颗粒度过粗

#### 当前实现（ParameterCollector.java:74-77）

```java
// 去重检查
if (processedUrls.contains(url)) {
    return false;
}
```

#### 当前去重颗粒度

- ✅ **url** - 完整URL
- ❌ **缺少 method** - 未区分 GET/POST/PUT 等方法
- ❌ **缺少 contentType** - 未区分 JSON/表单 等类型

#### 影响

同一个URL，使用不同的HTTP方法或Content-Type时，会被认为已处理，导致参数收集不完整。

**示例：**
```
POST /api/user?id=1 (application/json) - 已收集
GET  /api/user?id=1 (表单) - 会被跳过（可能丢失新参数）
```

### 问题3：EndpointInfo 未保存 method 和 contentType

#### 当前实现（ParameterCollector.java:488-490）

```java
endpointMap.computeIfAbsent(endpointKey, 
    k -> new EndpointInfo(host, endpoint, method, contentType, request));
```

#### 问题

虽然 `EndpointInfo` 存储了 method 和 contentType，但：
1. `endpointKey` 只使用了 `host:endpoint`，没有包含 method 和 contentType
2. 同一个 `host:endpoint` 的不同 method/contentType 会被覆盖

**示例：**
```java
// 第一次调用
addEndpoint("api.example.com", "/user", "POST", "application/json", request1)
// endpointKey = "api.example.com:/user"

// 第二次调用（会被忽略，因为key相同）
addEndpoint("api.example.com", "/user", "GET", "application/x-www-form-urlencoded", request2)
// endpointKey = "api.example.com:/user" (相同！)
```

---

## 三、黑白名单逻辑 ✅（整体正确，有轻微优化空间）

### 整体流程

```
被动扫描流量:
  RequestHandler
    ↓
  RequestFilter.shouldScan()
    ↓
  GlobalFilter.shouldProcessPassive()

主动探测流量:
  RealtimeScannerRefactored.processNewRequest()
    ↓
  GlobalFilter.shouldProcessActive()

Arjun扫描（从SiteMap）:
  RealtimeScannerRefactored.groupRequestsByMainDomain()
    ↓
  GlobalFilter.shouldProcessActive()
```

### GlobalFilter 实现逻辑

#### 检查顺序（GlobalFilter.java:35-84）

1. **白名单检查**（如果启用）
   - 字符串包含匹配
   - 正则表达式匹配
   - 不在白名单 → 拒绝处理

2. **黑名单检查**（如果启用）
   - 字符串包含匹配
   - 正则表达式匹配
   - 在黑名单 → 拒绝处理

3. **通过所有检查** → 允许处理

#### 特点

- ✅ 白名单和黑名单独立开关控制
- ✅ 支持字符串匹配和正则表达式
- ✅ 白名单优先级高于黑名单
- ✅ 正则表达式预编译（性能优化）

### 轻微问题：重复检查

#### RequestFilter 的检查（RequestFilter.java:53-56）

```java
// 3. 检查黑白名单
if (!passBlackWhiteList(request)) {
    return false;
}

// 调用 GlobalFilter.shouldProcessPassive()
```

#### RealtimeScannerRefactored 的检查（RealtimeScannerRefactored.java:68-72）

```java
// 检查全局过滤器
if (!globalFilter.shouldProcessActive(url)) {
    api.logging().raiseDebugEvent("URL 被过滤器阻止: " + url);
    return;
}
```

#### 说明

虽然目前 `shouldProcessPassive()` 和 `shouldProcessActive()` 实现相同，但：
- RequestFilter 已经在被动扫描入口处过滤了
- RealtimeScannerRefactored 又过滤了一次（冗余检查）

**影响：** 性能影响很小，但存在冗余检查。

### 结论

**✅ 黑白名单逻辑正确，无严重问题**  
**💡 建议：移除 RealtimeScannerRefactored 中的重复检查**

---

## 四、修复建议

### 修复1：更新 ParameterManager 的去重 key

#### 修改文件：`ParameterManager.java`

```java
// 当前实现
private String generateKey(String mainDomain, String endpoint) {
    return mainDomain + ":" + endpoint;
}

// 建议修改为
private String generateKey(String method, String host, String contentType, String endpoint) {
    String normalizedContentType = normalizeContentType(contentType);
    return method + "|" + host + "|" + normalizedContentType + "|" + endpoint;
}

// 添加 Content-Type 标准化方法
private String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isEmpty()) {
        return "application/x-www-form-urlencoded";
    }
    
    String lower = contentType.toLowerCase();
    if (lower.contains("json")) {
        return "application/json";
    } else if (lower.contains("xml")) {
        return "application/xml";
    } else if (lower.contains("form")) {
        return "application/x-www-form-urlencoded";
    } else if (lower.contains("multipart")) {
        return "multipart/form-data";
    }
    return contentType;
}
```

#### 相关调用处需要修改

1. `getIncrementalParameters()` - 增加 method 和 contentType 参数
2. `markParametersAsScanned()` - 增加 method 和 contentType 参数
3. `getScannedParameters()` - 增加 method 和 contentType 参数

### 修复2：更新 ParameterCollector 的 URL 去重

#### 修改文件：`ParameterCollector.java`

```java
// 当前实现
private final Set<String> processedUrls = Collections.newSetFromMap(
    new ConcurrentHashMap<String, Boolean>()
);

if (processedUrls.contains(url)) {
    return false;
}

// 建议修改为
private final Set<String> processedRequests = Collections.newSetFromMap(
    new ConcurrentHashMap<String, Boolean>()
);

// 生成去重key
String dedupeKey = method + "|" + url + "|" + contentType;
if (processedRequests.contains(dedupeKey)) {
    return false;
}

// 后续标记
processedRequests.add(dedupeKey);
```

### 修复3：更新 EndpointInfo 的 key

#### 修改文件：`ParameterCollector.java` (DomainData.addEndpoint)

```java
// 当前实现
public void addEndpoint(String host, String endpoint, String method, 
                       String contentType, HttpRequest request) {
    String endpointKey = host + ":" + endpoint;
    hosts.add(host);
    
    endpointMap.computeIfAbsent(endpointKey, 
        k -> new EndpointInfo(host, endpoint, method, contentType, request));
}

// 建议修改为
public void addEndpoint(String host, String endpoint, String method, 
                       String contentType, HttpRequest request) {
    String normalizedContentType = normalizeContentType(contentType);
    String endpointKey = method + "|" + host + "|" + normalizedContentType + "|" + endpoint;
    hosts.add(host);
    
    endpointMap.computeIfAbsent(endpointKey, 
        k -> new EndpointInfo(host, endpoint, method, contentType, request));
}
```

### 修复4：更新 RealtimeScannerRefactored 的 Arjun 扫描逻辑

#### 修改文件：`RealtimeScannerRefactored.java`

```java
// 当前实现
for (String endpoint : endpoints) {
    // 计算增量参数（未扫描过的）
    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
        mainDomain, endpoint, collectedParams
    );
    // ...
}

// 建议修改为
// 需要从 EndpointInfo 获取 method 和 contentType
for (EndpointKey epKey : endpoints) {
    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
        epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
    );
    // ...
}
```

### 修复5：移除冗余的黑白名单检查

#### 修改文件：`RealtimeScannerRefactored.java`

```java
// 当前实现（第68-72行）
// 检查全局过滤器
if (!globalFilter.shouldProcessActive(url)) {
    api.logging().raiseDebugEvent("URL 被过滤器阻止: " + url);
    return;
}

// 建议：
// 因为 RequestHandler 已经通过 RequestFilter 调用了黑白名单检查
// 这里可以移除，或者保留作为二次验证（看需求）
```

---

## 五、总结

### 当前状态

| 模块 | 状态 | 问题 |
|------|------|------|
| 被动扫描去重 | ✅ 正确 | 无 |
| 主动探测（Arjun）去重 | ❌ 有问题 | 缺少 method 和 contentType 维度 |
| 参数收集去重 | ❌ 有问题 | URL 去重颗粒度过粗 |
| 黑白名单逻辑 | ✅ 正确 | 存在轻微冗余检查 |

### 修复优先级

1. **高优先级**：修复 ParameterManager 和 ParameterCollector 的去重逻辑
2. **中优先级**：更新 EndpointInfo 的 key 生成逻辑
3. **低优先级**：移除冗余的黑白名单检查

### 修复影响范围

- ✅ 不影响现有功能
- ✅ 向后兼容（历史数据会被重新扫描一次）
- ⚠️ 需要更新多个文件的方法签名
- ⚠️ 需要全面测试 Arjun 扫描流程

---

## 六、用户要求对比

### 用户要求的去重颗粒度

```
method + host + content-type + uri + 已经探测过的参数
```

### 修复后的去重颗粒度

✅ **被动扫描**：
```
method + host + path + contentType + parameterName + scanType
```

✅ **主动探测（修复后）**：
```
method + host + contentType + endpoint + 已扫描参数集合
```

✅ **参数收集（修复后）**：
```
method + url + contentType
```

### 结论

修复后的去重逻辑将完全符合用户要求。


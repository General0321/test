# 🔍 Arjun实时模式详细解析

## 📊 你的问题解答

### 问题1：实时模式的探测方式

**你的理解：** 实时模式是如果当前这个http数据包没有探测过，那么直接在这个数据包的基础上进行探测，保留原数据包的参数等不改变，再这基础上探测未探测的参数

**✅ 答案：完全正确！**

#### 工作流程

```java
// Step 1: 从ParameterCollector获取请求模板
HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);

// Step 2: 计算增量参数（未探测的）
Set<String> incrementalParams = parameterManager.getIncrementalParameters(
    epKey.method, epKey.host, epKey.contentType, epKey.endpoint, collectedParams
);

// Step 3: 使用原始请求模板 + 增量参数进行探测
arjunService.scan(templateRequest, incrementalParams);
```

#### 关键点

1. **保留原始请求**
   ```java
   // ✅ templateRequest是原始数据包的副本
   // ✅ 原始参数、headers、body都保留
   HttpRequest templateRequest = parameterCollector.getEndpointTemplate(mainDomain, epKey);
   ```

2. **只添加增量参数**
   ```java
   // ✅ incrementalParams只包含未探测的参数
   // ✅ 已探测的参数不会再次添加
   Set<String> incrementalParams = parameterManager.getIncrementalParameters(...);
   ```

3. **Arjun内部处理**
   ```java
   // ParamDiscoveryEngine会在原始请求基础上添加新参数
   // 例如：
   // 原始: GET /api/user?id=123
   // 探测: GET /api/user?id=123&newParam1=test&newParam2=test...
   ```

**结论：** ✅ **完全正确！Arjun在原数据包基础上进行探测，保留原参数，只添加未探测的参数**

---

### 问题2：主域名划分和参数传递

**你的理解：** 收集参数的时候是按照主域名划分的吗？传递给arjun的时候是只要这个主域名在host中就把这个参数列表给这个host吗？不会出现混乱？

**✅ 答案：完全正确！非常严格的主域名划分！**

#### 参数收集（按主域名划分）

```java
// ParameterCollector内部结构
private final Map<String, Set<String>> domainToParameters = new ConcurrentHashMap<>();
// Key: 主域名 (example.com)
// Value: 该主域名的所有参数

// 收集时
String mainDomain = extractMainDomain(host);  // api.example.com → example.com
domainToParameters.computeIfAbsent(mainDomain, k -> ConcurrentHashMap.newKeySet()).add(param);
```

#### 参数传递（严格匹配主域名）

```java
// Step 1: 遍历每个主域名
for (String mainDomain : allMainDomains) {
    
    // Step 2: 获取该主域名的参数
    Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
    
    // Step 3: 获取该主域名的所有接口
    Set<EndpointKey> endpointKeys = parameterCollector.getEndpointKeysForMainDomain(mainDomain);
    
    // Step 4: 对每个接口进行扫描
    for (EndpointKey epKey : endpointKeys) {
        // epKey.host = 具体host (api.example.com)
        // 但参数来自主域名 (example.com)
        
        // ✅ 只使用该主域名的参数
        arjunService.scan(request, collectedParams);
    }
}
```

#### 实例说明

```
收集阶段：
  api.example.com/user?id=1&name=test
    → 主域名: example.com
    → 参数: {id, name}
  
  admin.example.com/login?username=admin
    → 主域名: example.com
    → 参数: {id, name, username}  // ✅ 合并到同一主域名
  
  api.other.com/data?key=abc
    → 主域名: other.com
    → 参数: {key}  // ✅ 独立的主域名

探测阶段：
  对 api.example.com 探测:
    ✅ 使用 example.com 的参数: {id, name, username}
    ❌ 不使用 other.com 的参数
  
  对 admin.example.com 探测:
    ✅ 使用 example.com 的参数: {id, name, username}
    ❌ 不使用 other.com 的参数
  
  对 api.other.com 探测:
    ✅ 使用 other.com 的参数: {key}
    ❌ 不使用 example.com 的参数
```

**结论：** ✅ **完全正确！按主域名严格划分，不会出现参数混乱！**

---

### 问题3：实时模式的触发机制

**你的理解：** 实时模式，收集的参数是定时传给arjun的吗？

**❌ 答案：不是定时触发，是手动触发！**

#### 实际触发机制

**方式1：UI按钮触发**
```java
// ActiveProbeTab.java (Line 498)
JButton proxyArjunButton = new JButton("🔍 开始Arjun扫描（Proxy实时流量）");
proxyArjunButton.addActionListener(e -> {
    // ✅ 用户点击按钮后触发
    activeScanner.getRealtimeScanner().triggerArjunScanFromProxy();
});
```

**方式2：调度器触发（如果启用）**
```java
// ActiveProbeTab.java (Line 644)
if (isRealtimeMode) {
    // ✅ 调度器定时检查并触发
    api.logging().raiseInfoEvent("从Proxy实时流量触发Arjun探测");
    activeScanner.getRealtimeScanner().triggerArjunScanFromProxy();
}
```

#### 工作流程

```
参数收集（持续进行）：
  Proxy流量 → RequestHandler.processNewRequest()
            → ParameterCollector.collectFromRequest()
            → 按主域名存储参数
  
Arjun探测（手动/调度触发）：
  用户点击按钮 OR 调度器定时检查
            ↓
  triggerArjunScanFromProxy()
            ↓
  performIncrementalArjunScanFromCollectedData()
            ↓
  遍历所有主域名 → 计算增量参数 → 调用Arjun
```

#### 关键区别

```
参数收集：
  ✅ 实时进行（每个请求都收集）
  ✅ 自动触发
  ✅ 持续更新

Arjun探测：
  ❌ 不是实时触发
  ✅ 手动触发（点击按钮）
  ✅ 或调度器定时触发
```

**结论：** ❌ **不是定时传给Arjun，而是手动触发或调度器触发后，一次性使用所有已收集的参数进行探测**

---

### 问题4：手动添加接口的处理

**你的要求：** 
1. 手动添加接口要符合主域名分组
2. 要GET/POST/POST-JSON全都探测
3. 要记录在去重里面避免重复

**✅ 答案：完全符合要求！**

#### 1. 符合主域名分组 ✅

```java
// scanManualEndpoint() - Line 461
private int scanManualEndpoint(String url) {
    URI uri = new URI(url);
    String host = uri.getHost();
    String mainDomain = extractMainDomain(host);  // ✅ 提取主域名
    
    // ✅ 获取该主域名的参数
    Set<String> collectedParams = parameterCollector.getParametersForMainDomain(mainDomain);
    
    // ✅ 使用该主域名的参数进行探测
    arjunService.scan(request, collectedParams);
}
```

#### 2. GET/POST/POST-JSON全都探测 ✅

```java
// Line 479-483
String[][] combinations = {
    {"GET", "application/x-www-form-urlencoded"},   // ✅ GET
    {"POST", "application/x-www-form-urlencoded"},  // ✅ POST表单
    {"POST", "application/json"}                    // ✅ POST-JSON
};

// 遍历所有组合
for (String[] combo : combinations) {
    String method = combo[0];
    String contentType = combo[1];
    
    // ✅ 对每个组合都进行探测
    arjunService.scan(request, incrementalParams);
}
```

#### 3. 记录在去重里面避免重复 ✅

```java
// Line 497-507
for (String[] combo : combinations) {
    String method = combo[0];
    String contentType = combo[1];
    
    // ✅ 计算增量参数（已探测的会被过滤）
    Set<String> incrementalParams = parameterManager.getIncrementalParameters(
        method, host, contentType, endpoint, collectedParams
    );
    
    if (incrementalParams.isEmpty()) {
        // ✅ 如果该组合已探测过，跳过
        continue;
    }
}

// Line 540-543
// ✅ 探测后标记为已扫描
parameterManager.markParametersAsScanned(
    finalMethod, host, finalContentType, endpoint, 
    finalIncrementalParams
);
```

#### 去重维度（4个维度）

```
去重Key = method + host + contentType + endpoint

例如：
  GET + api.example.com + application/x-www-form-urlencoded + /api/user
  POST + api.example.com + application/x-www-form-urlencoded + /api/user
  POST + api.example.com + application/json + /api/user
  
每个组合独立去重，互不影响
```

**结论：** ✅ **完全符合要求！手动接口符合主域名分组，支持GET/POST/POST-JSON，并且完整去重！**

---

## 🎯 完整流程图

### 实时模式流程

```
1. 参数收集（自动）
   ┌─────────────────────────────────────┐
   │ Proxy流量                            │
   │   ↓                                  │
   │ RequestHandler.processNewRequest()   │
   │   ↓                                  │
   │ ParameterCollector.collectFromRequest() │
   │   ↓                                  │
   │ 按主域名存储:                         │
   │   example.com → {id, name, token}    │
   │   other.com → {key, value}           │
   └─────────────────────────────────────┘

2. Arjun探测（手动/调度触发）
   ┌─────────────────────────────────────┐
   │ 用户点击按钮                          │
   │   ↓                                  │
   │ triggerArjunScanFromProxy()          │
   │   ↓                                  │
   │ 遍历所有主域名:                       │
   │   ├─ example.com                     │
   │   │   ├─ 获取参数: {id, name, token} │
   │   │   ├─ 获取接口: api.example.com/user │
   │   │   ├─ 计算增量: {newParam1, ...}  │
   │   │   └─ 调用Arjun                   │
   │   │                                  │
   │   └─ other.com                       │
   │       ├─ 获取参数: {key, value}      │
   │       ├─ 获取接口: api.other.com/data │
   │       ├─ 计算增量: {newParam2, ...}  │
   │       └─ 调用Arjun                   │
   └─────────────────────────────────────┘

3. Arjun执行
   ┌─────────────────────────────────────┐
   │ 原始请求: GET /user?id=123           │
   │   ↓                                  │
   │ 添加增量参数:                         │
   │   GET /user?id=123&newParam1=test&... │
   │   ↓                                  │
   │ 探测异常响应                          │
   │   ↓                                  │
   │ 发现有效参数: {debug, admin}         │
   │   ↓                                  │
   │ 触发漏洞扫描                          │
   │   ↓                                  │
   │ 标记参数为已扫描（去重）              │
   └─────────────────────────────────────┘
```

### 手动添加接口流程

```
手动输入: https://api.example.com/admin
   ↓
提取信息:
   host: api.example.com
   主域名: example.com
   endpoint: /admin
   ↓
获取主域名参数:
   collectedParams = {id, name, token}  // ✅ example.com的参数
   ↓
尝试3种组合:
   ┌─────────────────────────────────────┐
   │ 组合1: GET + application/x-www-form-urlencoded │
   │   ├─ 增量参数: {newParam1, ...}      │
   │   ├─ 调用Arjun                       │
   │   └─ 标记已扫描                      │
   │                                      │
   │ 组合2: POST + application/x-www-form-urlencoded │
   │   ├─ 增量参数: {newParam2, ...}      │
   │   ├─ 调用Arjun                       │
   │   └─ 标记已扫描                      │
   │                                      │
   │ 组合3: POST + application/json       │
   │   ├─ 增量参数: {newParam3, ...}      │
   │   ├─ 调用Arjun                       │
   │   └─ 标记已扫描                      │
   └─────────────────────────────────────┘
```

---

## 📊 去重机制详解

### 去重维度（4个）

```java
String key = method + "|" + host + "|" + contentType + "|" + endpoint;

例如：
  GET|api.example.com|application/x-www-form-urlencoded|/api/user
  POST|api.example.com|application/x-www-form-urlencoded|/api/user
  POST|api.example.com|application/json|/api/user
```

### 去重逻辑

```java
// 1. 计算增量参数
Set<String> incrementalParams = parameterManager.getIncrementalParameters(
    method, host, contentType, endpoint, collectedParams
);
// 返回: 所有参数 - 已扫描参数 = 增量参数

// 2. 检查是否有新参数
if (incrementalParams.isEmpty()) {
    // ✅ 该组合已全部扫描过，跳过
    continue;
}

// 3. 扫描后标记
parameterManager.markParametersAsScanned(
    method, host, contentType, endpoint, 
    incrementalParams
);
```

### 去重效果

```
第一次扫描:
  GET|api.example.com|form|/user + {param1, param2, param3}
  → 标记: {param1, param2, param3}

第二次扫描（新收集了param4）:
  GET|api.example.com|form|/user + {param1, param2, param3, param4}
  → 增量: {param4}  // ✅ 只扫描param4
  → 标记: {param1, param2, param3, param4}

第三次扫描（无新参数）:
  GET|api.example.com|form|/user + {param1, param2, param3, param4}
  → 增量: {}  // ✅ 空集，跳过扫描
```

---

## ✅ 总结

### 你的理解验证

| 问题 | 你的理解 | 实际情况 | 结果 |
|------|----------|----------|------|
| 1. 实时模式探测方式 | 在原数据包基础上探测，保留原参数，添加增量参数 | ✅ 完全正确 | ✅ |
| 2. 主域名划分 | 按主域名划分，参数不会混乱 | ✅ 完全正确 | ✅ |
| 3. 触发机制 | 定时传给Arjun | ❌ 手动/调度触发 | ⚠️ |
| 4. 手动接口处理 | 符合主域名分组，支持GET/POST/POST-JSON，去重 | ✅ 完全正确 | ✅ |

### 关键要点

1. **实时模式 = 手动触发 + 实时收集的参数**
   - 参数收集：自动进行
   - Arjun探测：手动/调度触发

2. **主域名严格划分**
   - ✅ api.example.com → example.com
   - ✅ admin.example.com → example.com
   - ✅ 同一主域名的参数共享

3. **去重机制完善**
   - 4维度去重：method + host + contentType + endpoint
   - 增量计算：只探测未扫描的参数
   - 自动跳过：无新参数时跳过

4. **手动接口支持**
   - ✅ 符合主域名分组
   - ✅ 支持GET/POST/POST-JSON
   - ✅ 完整去重机制

---

**完成时间：** 2025-10-02 23:00  
**状态：** ✅ **所有问题已解答！**


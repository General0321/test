# XProbe 流程分析与优化方案

## 🔍 当前流程分析

### 问题 1: 重复的 RealtimeScanner 实例 ⚠️

**发现的严重问题：**

```java
// XProbe.java
RealtimeScanner realtimeScanner = new RealtimeScanner(api, configManager, globalFilter);
RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, 
                                                   taskScheduler, realtimeScanner);

// RequestHandler.java
this.activeScanner = new ActiveScanner(api, configManager, realtimeScanner.getGlobalFilter());

// ActiveScanner.java
public ActiveScanner(MontoyaApi api, ConfigurationManager configManager, GlobalFilter globalFilter) {
    this.realtimeScanner = new RealtimeScanner(api, configManager, globalFilter);  // ❌ 创建了新实例！
}
```

**问题：**
- ❌ 存在 **两个独立的 RealtimeScanner 实例**
- ❌ 参数收集的数据存在两个不同的 `hostDataMap` 中
- ❌ Arjun 探测时使用的是 ActiveScanner 内部的实例，看不到 RequestHandler 收集的数据
- ❌ 数据完全不同步！

### 问题 2: 流量处理重复和混乱

**当前流程：**
```
HTTP 请求 → RequestHandler.handleHttpRequestToBeSent()
    ↓
    1. requestFilter.shouldScan() - 过滤
    ↓
    2. collectScanTasks() - 收集被动扫描任务
    ↓
    3. taskScheduler.scheduleScan() - 调度被动扫描
    ↓
    4. activeScanner.processNewRequest() - 参数收集（❌错误的实例）
    ↓
    5. realtimeScanner.processNewRequest() - ❌ 没有被调用！
```

**问题：**
- ❌ `activeScanner.processNewRequest()` 调用的是错误的实例
- ❌ 传给 RequestHandler 的 `realtimeScanner` 没有被调用
- ❌ 参数收集和 Arjun 探测使用不同的数据源

### 问题 3: Arjun 探测从 SiteMap 获取流量不合理

**当前方式：**
```java
// RealtimeScanner.performManualArjunScan()
SiteMap siteMap = api.siteMap();
List<HttpRequestResponse> requestResponses = siteMap.requestResponses();

// 每次都从 SiteMap 获取所有请求
for (HttpRequestResponse rr : requestResponses) {
    // 处理...
}
```

**问题：**
- ❌ 每次触发都要遍历整个 SiteMap（可能有几千个请求）
- ❌ SiteMap 的请求可能包含很多无关流量
- ❌ 已经在 `processNewRequest` 中收集了数据，为什么还要从 SiteMap 获取？

### 问题 4: 增量探测逻辑不完善

**当前逻辑：**
```java
// 计算本次需要探测的"新增参数名"
Set<String> hostParams = new HashSet<>(hostData.getParameters());
Set<String> scanned = new HashSet<>(hostData.getArjunScannedParams(endpoint));
hostParams.removeAll(scanned);

if (hostParams.isEmpty()) {
    continue;  // 跳过
}
```

**问题场景：**
```
T1: host 有参数 [a, b]
    endpoint1 用 [a, b] 扫描过
    scanned[endpoint1] = [a, b]

T2: 发现新参数 c
    host 有参数 [a, b, c]
    
T3: 发现新接口 endpoint2
    问题：endpoint2 应该用 [a, b, c] 扫描
    但当前逻辑：
      hostParams = [a, b, c]
      scanned[endpoint2] = []  ← 空的，因为是新接口
      增量 = [a, b, c]  ✅ 正确
      
T4: endpoint2 扫描完成
    scanned[endpoint2] = [a, b, c]
    
T5: 再次发现新参数 d
    host 有参数 [a, b, c, d]
    
    endpoint1:
      hostParams = [a, b, c, d]
      scanned = [a, b]  ← 只记录了首次扫描的参数
      增量 = [c, d]  ✅ 正确
      
    endpoint2:
      hostParams = [a, b, c, d]
      scanned = [a, b, c]
      增量 = [d]  ✅ 正确
```

**看起来逻辑是对的，但有个问题：**
- 如果新接口是在有参数后才发现的，会立即用所有参数扫描 ✅
- 但如果新参数是在已有接口扫描后才发现的，需要再次扫描所有接口 ✅

**实际问题在于触发时机：**
- 参数收集是实时的
- Arjun 扫描是手动触发的
- 两者之间可能有时间差，导致数据不一致

---

## 🔧 优化方案

### 修复 1: 统一 RealtimeScanner 实例

**问题：** 两个独立的实例导致数据不同步

**解决方案：** 删除 ActiveScanner，所有功能整合到 RealtimeScanner

```java
// XProbe.java - 保持不变
RealtimeScanner realtimeScanner = new RealtimeScanner(api, configManager, globalFilter);
RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, 
                                                   taskScheduler, realtimeScanner);

// RequestHandler.java - 修改
public RequestHandler(MontoyaApi api, ConfigurationManager configManager, 
                     RequestFilter requestFilter, TaskScheduler taskScheduler, 
                     RealtimeScanner realtimeScanner) {
    this.api = api;
    this.configManager = configManager;
    this.requestFilter = requestFilter;
    this.taskScheduler = taskScheduler;
    this.realtimeScanner = realtimeScanner;
    // ❌ 删除: this.activeScanner = new ActiveScanner(...)
}

@Override
public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
    // 1. 过滤
    if (!requestFilter.shouldScan(requestToBeSent)) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }
    
    // 2. 收集被动扫描任务
    List<ScanTask> scanTasks = collectScanTasks(requestToBeSent, context);
    if (!scanTasks.isEmpty()) {
        taskScheduler.scheduleScan(scanTasks);
    }
    
    // 3. 参数收集（使用正确的实例）
    realtimeScanner.processNewRequest(requestToBeSent);
    
    // 4. 返回
    return RequestToBeSentAction.continueWith(requestToBeSent);
}
```

### 修复 2: 不再从 SiteMap 获取流量

**问题：** 从 SiteMap 获取流量效率低且数据可能不一致

**解决方案：** 直接使用已收集的 HostData

```java
// RealtimeScanner.java

/**
 * 手动触发 Arjun 探测（优化版）
 */
public void triggerManualArjunScan() {
    api.logging().raiseInfoEvent("开始 Arjun 参数探测（使用已收集数据）...");
    
    scannerExecutor.submit(() -> {
        try {
            performArjunScanFromCollectedData();
        } catch (Exception e) {
            api.logging().raiseErrorEvent("Arjun 探测失败: " + e.getMessage());
        }
    });
}

/**
 * 从已收集的数据执行 Arjun 探测
 */
private void performArjunScanFromCollectedData() {
    int totalTasks = 0;
    int skippedTasks = 0;
    
    // 遍历所有已收集的 host
    for (Map.Entry<String, HostData> entry : hostDataMap.entrySet()) {
        String host = entry.getKey();
        HostData hostData = entry.getValue();
        
        // 获取该 host 的所有参数
        Set<String> allHostParams = new HashSet<>(hostData.getParameters());
        allHostParams.addAll(globalCustomDictionary);
        
        if (allHostParams.isEmpty()) {
            api.logging().raiseDebugEvent("跳过 " + host + ": 无可用参数");
            continue;
        }
        
        // 遍历该 host 的所有接口
        for (String endpoint : hostData.getEndpoints()) {
            // 获取该 endpoint 已扫描的参数
            Set<String> scannedParams = hostData.getArjunScannedParams(endpoint);
            
            // 计算增量参数
            Set<String> paramsToScan = new HashSet<>(allHostParams);
            paramsToScan.removeAll(scannedParams);
            
            if (paramsToScan.isEmpty()) {
                skippedTasks++;
                api.logging().raiseDebugEvent("跳过 " + host + endpoint + ": 无新参数");
                continue;
            }
            
            // 构建请求（从 endpointInfoMap 获取）
            EndpointInfo epInfo = hostData.getEndpointInfoMap().get(endpoint);
            if (epInfo == null) {
                api.logging().raiseDebugEvent("跳过 " + host + endpoint + ": 无接口信息");
                continue;
            }
            
            String url = "https://" + host + endpoint;
            HttpRequest request = buildRequestFromEndpointInfo(url, epInfo);
            
            // 执行 Arjun 扫描
            totalTasks++;
            arjunIntegration.scan(request, paramsToScan).thenAccept(result -> {
                if (result.isSuccess()) {
                    // 更新已扫描标记
                    hostData.markArjunScanned(endpoint, paramsToScan);
                    
                    // 将发现的参数添加到参数集合
                    for (String param : result.getFoundParameters()) {
                        hostData.addParameterToEndpoint(endpoint, param);
                    }
                    
                    saveArjunStateSafely();
                    
                    api.logging().raiseInfoEvent("✅ " + host + endpoint + 
                        " 发现 " + result.getFoundParameters().size() + " 个参数");
                } else {
                    api.logging().raiseErrorEvent("❌ " + host + endpoint + 
                        " 扫描失败: " + result.getErrorMessage());
                }
            });
        }
    }
    
    api.logging().raiseInfoEvent(String.format(
        "Arjun 探测完成：提交 %d 个任务，跳过 %d 个（无新参数）", 
        totalTasks, skippedTasks));
}

/**
 * 从 EndpointInfo 构建请求
 */
private HttpRequest buildRequestFromEndpointInfo(String url, EndpointInfo epInfo) {
    // 使用 epInfo 中保存的 method, contentType, headers 等信息
    // 如果没有保存完整信息，使用默认值
    
    HttpRequest request = HttpRequest.httpRequestFromUrl(url);
    
    // TODO: 如果 EndpointInfo 保存了完整的请求信息，应该使用它
    // 例如：headers, cookies, body 等
    
    return request;
}
```

### 修复 3: 增强 EndpointInfo 保存完整请求信息

**问题：** 手动添加的接口没有完整的请求上下文（headers, cookies 等）

**解决方案：** 在 EndpointInfo 中保存首次请求的完整信息

```java
class EndpointInfo {
    String endpoint;
    String method;
    String contentType;
    Set<String> parameters;
    Set<String> processedParameters;
    
    // 新增：保存完整的请求信息
    HttpRequest templateRequest;  // 首次请求的模板
    Map<String, String> headers;  // 请求头
    String body;                  // 请求体（如果有）
    
    public EndpointInfo(String endpoint, String method, String contentType) {
        this.endpoint = endpoint;
        this.method = method;
        this.contentType = contentType;
        this.parameters = new HashSet<>();
        this.processedParameters = new HashSet<>();
        this.headers = new HashMap<>();
    }
    
    // 保存请求模板
    public void setTemplateRequest(HttpRequest request) {
        this.templateRequest = request;
        
        // 提取并保存 headers
        for (var header : request.headers()) {
            String name = header.name();
            // 跳过自动生成的 headers
            if (!"Content-Length".equalsIgnoreCase(name) && 
                !"Host".equalsIgnoreCase(name)) {
                this.headers.put(name, header.value());
            }
        }
        
        // 保存 body（如果是 POST/PUT）
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            this.body = request.bodyToString();
        }
    }
    
    // 获取请求模板
    public HttpRequest getTemplateRequest() {
        return templateRequest;
    }
}
```

**更新参数收集逻辑：**
```java
// RealtimeScanner.processNewRequest()
private boolean updateTargetDataWithDetection(String host, String endpoint, String method, 
                                             String contentType, Set<String> parameters, 
                                             HttpRequest request) {  // 新增参数
    HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
    
    boolean hasNewParameters = false;
    for (String parameter : parameters) {
        if (!hostData.getParameters().contains(parameter)) {
            hasNewParameters = true;
            break;
        }
    }
    
    // 更新接口信息
    EndpointInfo epInfo = hostData.addEndpoint(endpoint, method, contentType, parameters);
    
    // 如果这是首次遇到这个接口，保存请求模板
    if (epInfo.getTemplateRequest() == null) {
        epInfo.setTemplateRequest(request);
    }
    
    // 更新参数
    for (String parameter : parameters) {
        hostData.addParameterToEndpoint(endpoint, parameter);
    }
    
    return hasNewParameters;
}
```

### 修复 4: 优化增量探测逻辑

**当前问题：**
- 新接口会立即用所有参数扫描 ✅
- 新参数出现后需要扫描所有接口 ✅
- 但触发时机是手动的，可能有延迟

**优化方案：提供自动触发选项**

```java
// RealtimeScanner.java

private volatile boolean autoTriggerArjun = false;  // 默认关闭
private volatile int autoTriggerThreshold = 5;      // 新参数达到5个时触发

/**
 * 设置自动触发 Arjun
 */
public void setAutoTriggerArjun(boolean enable, int threshold) {
    this.autoTriggerArjun = enable;
    this.autoTriggerThreshold = threshold;
    api.logging().raiseInfoEvent("自动触发 Arjun: " + 
        (enable ? "开启（阈值=" + threshold + "）" : "关闭"));
}

// 在 processNewRequest 中
private boolean updateTargetDataWithDetection(...) {
    // ... 原有逻辑 ...
    
    if (hasNewParameters && autoTriggerArjun) {
        // 检查新参数数量
        int newParamCount = countNewParametersSinceLastScan(host);
        if (newParamCount >= autoTriggerThreshold) {
            api.logging().raiseInfoEvent(
                "新参数数量达到阈值（" + newParamCount + "），自动触发 Arjun 探测");
            triggerManualArjunScan();
        }
    }
    
    return hasNewParameters;
}
```

---

## 📊 优化后的完整流程

### 流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                   1. HTTP 请求进入                                │
│                                                                   │
│   Burp Proxy/Repeater → RequestHandler.handleHttpRequestToBeSent│
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   2. 请求过滤                                     │
│                                                                   │
│   requestFilter.shouldScan(request)                              │
│     • 检查黑白名单                                                │
│     • 检查文件类型                                                │
│     • 检查 URL 模式                                               │
│                                                                   │
│   如果不应该扫描 → 直接返回                                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   3. 被动扫描任务收集                              │
│                                                                   │
│   collectScanTasks(request)                                      │
│     • 提取所有参数                                                │
│     • 匹配扫描规则（LFI/SQL/SSRF等）                              │
│     • 检查去重（是否已扫描）                                      │
│     • 生成 ScanTask 列表                                         │
│                                                                   │
│   taskScheduler.scheduleScan(tasks) → 异步执行被动扫描            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   4. 参数和接口收集（统一实例）                     │
│                                                                   │
│   realtimeScanner.processNewRequest(request)                     │
│     ↓                                                             │
│   1. 检查是否是 Arjun 流量（X-XProbe-Arjun 头）                   │
│      是 → 跳过收集（但仍执行被动扫描）                            │
│                                                                   │
│   2. 提取信息：                                                   │
│      • host, endpoint                                            │
│      • parameters                                                │
│      • method, contentType                                       │
│                                                                   │
│   3. 更新 HostData（唯一数据源）：                                │
│      • hostDataMap[host].addEndpoint(endpoint, ...)              │
│      • hostDataMap[host].addParameters(params)                   │
│      • endpointInfoMap[endpoint].setTemplateRequest(request)     │
│                                                                   │
│   4. 检测新参数：                                                 │
│      if (hasNewParameters && autoTriggerEnabled) {               │
│          检查新参数数量 >= 阈值                                   │
│          → 自动触发 Arjun 探测                                    │
│      }                                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   5. Arjun 探测（手动或自动触发）                  │
│                                                                   │
│   triggerManualArjunScan()                                       │
│     ↓                                                             │
│   performArjunScanFromCollectedData()                            │
│     ↓                                                             │
│   遍历 hostDataMap:                                              │
│     for each (host, hostData):                                   │
│       allParams = hostData.getParameters() + globalCustomDict    │
│                                                                   │
│       for each endpoint in hostData.getEndpoints():              │
│         scannedParams = hostData.getArjunScannedParams(endpoint) │
│         toScan = allParams - scannedParams                       │
│                                                                   │
│         if (toScan.isEmpty()):                                   │
│           跳过（无新参数）                                        │
│         else:                                                     │
│           // 构建请求                                             │
│           epInfo = hostData.getEndpointInfoMap().get(endpoint)   │
│           request = epInfo.getTemplateRequest()  ← 使用保存的模板 │
│                                                                   │
│           // 执行 Arjun                                          │
│           arjunIntegration.scan(request, toScan)                 │
│             ↓                                                     │
│           构建命令:                                               │
│             arjun -u <URL>                                       │
│                   -m <METHOD>                                    │
│                   --headers "<从模板继承>                        │
│                              X-XProbe-Arjun: 1"                  │
│                   -w <参数字典>                                  │
│                   -oB 127.0.0.1:8080                            │
│             ↓                                                     │
│           Arjun 发现有效参数 → 通过 -oB 发送到 Burp Proxy        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   6. Arjun 流量回到 RequestHandler                │
│                                                                   │
│   RequestHandler 再次处理 Arjun 的请求:                          │
│     ↓                                                             │
│   检测到 X-XProbe-Arjun 头:                                      │
│     • ✅ 仍然执行被动扫描（检测漏洞）                             │
│     • ❌ 跳过参数收集（避免循环）                                 │
│                                                                   │
│   被动扫描器检测 LFI/SQL/SSRF 等:                                │
│     • 匹配 payload                                               │
│     • 发送测试请求                                               │
│     • 分析响应                                                   │
│     • 报告漏洞                                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   7. 更新扫描状态                                 │
│                                                                   │
│   Arjun 扫描完成回调:                                            │
│     • hostData.markArjunScanned(endpoint, toScan)                │
│     • 将发现的参数添加到 hostData                                │
│     • 持久化到 ~/.xprobe/arjun_state.json                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   8. 持续循环                                     │
│                                                                   │
│   • 新的被动流量继续被收集                                        │
│   • 新参数自动/手动触发 Arjun                                    │
│   • 参数字典不断增长                                              │
│   • 所有接口都会用最新的参数探测                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎯 关键优化点总结

### 1. 统一数据源 ✅
- ❌ 删除 ActiveScanner 内部创建的 RealtimeScanner
- ✅ 只使用一个 RealtimeScanner 实例
- ✅ 所有参数收集都进入同一个 hostDataMap

### 2. 不再从 SiteMap 获取流量 ✅
- ❌ 删除从 SiteMap 遍历的逻辑
- ✅ 直接使用已收集的 HostData
- ✅ 效率更高，数据更准确

### 3. 保存完整请求上下文 ✅
- ✅ EndpointInfo 保存 templateRequest
- ✅ 包含 headers, cookies, body
- ✅ Arjun 探测时使用完整上下文

### 4. 增量探测优化 ✅
- ✅ 新接口 → 立即用所有参数扫描
- ✅ 新参数 → 扫描所有接口
- ✅ 自动触发选项（参数数量阈值）
- ✅ 去重机制防止重复探测

### 5. 去重机制完善 ✅
- ✅ Arjun 流量标记（X-XProbe-Arjun）
- ✅ 参数级别去重（endpoint + 参数集合）
- ✅ 持久化状态跨会话保留

---

## 📝 实现清单

- [ ] 修改 RequestHandler：删除 activeScanner 相关代码
- [ ] 修改 RealtimeScanner：实现 performArjunScanFromCollectedData()
- [ ] 增强 EndpointInfo：保存 templateRequest 和完整上下文
- [ ] 实现 buildRequestFromEndpointInfo()：从模板构建请求
- [ ] 添加自动触发功能：setAutoTriggerArjun()
- [ ] 删除 ActiveScanner.java（或重构为工具类）
- [ ] 更新 UI：添加自动触发选项
- [ ] 测试完整流程

---

通过这些优化，可以实现：
- 🚫 **不发重复流量** - 完善的去重机制
- 🔍 **尽可能多探测参数** - 主域名级参数共享
- ✅ **每个接口都探测** - 增量机制确保所有接口用最新参数
- 🎯 **数据一致性** - 统一的数据源和流程

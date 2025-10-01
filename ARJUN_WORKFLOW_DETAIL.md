# Arjun 工作流程详解

## 📊 完整流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                    1. 被动流量收集阶段                              │
│                                                                   │
│  Burp Proxy/Repeater → RequestHandler.processNewRequest()       │
│           ↓                                                       │
│  检查: 是否有 X-XProbe-Arjun 头? (跳过 Arjun 自己的流量)          │
│           ↓                                                       │
│  提取: host, endpoint, parameters, method, content-type          │
│           ↓                                                       │
│  存储到 HostData (按主域名级别):                                   │
│    - endpoints: Set<String>                                      │
│    - parameters: Set<String>                                     │
│    - endpointInfoMap: Map<endpoint, EndpointInfo>               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    2. Arjun 探测触发                              │
│                                                                   │
│  方式1: UI 手动触发 → triggerManualArjunScan()                    │
│  方式2: 程序化调用 → arjunIntegration.scan()                      │
│           ↓                                                       │
│  从 SiteMap 获取所有请求:                                          │
│    siteMap.requestResponses()                                    │
│           ↓                                                       │
│  过滤:                                                            │
│    1. 跳过有 X-XProbe-Arjun 头的请求 (Arjun 自己的流量)           │
│    2. 按 host 分组                                                │
│           ↓                                                       │
│  增量检测:                                                         │
│    hostParams = 当前host的所有参数                                │
│    scannedParams = 该endpoint已扫描的参数                         │
│    toScan = hostParams - scannedParams                           │
│           ↓                                                       │
│  如果 toScan.isEmpty() → 跳过 (避免重复)                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    3. 构建 Arjun 命令                             │
│                                                                   │
│  ArjunIntegration.buildArjunCommand(request, dictFile)           │
│           ↓                                                       │
│  完全继承原请求信息:                                               │
│    - URL: request.url()                                          │
│    - Method: GET/POST/JSON/XML (自动映射)                        │
│    - Headers: 继承所有原始headers + X-XProbe-Arjun: 1            │
│    - Body: 自动处理 (Arjun 会根据 -m 参数构建)                    │
│           ↓                                                       │
│  构建参数字典 (主域名级别):                                        │
│    1. 从被动流量收集的参数 (该host下所有参数)                      │
│    2. 全局自定义字典                                               │
│    3. 常见参数 (内置)                                              │
│           ↓                                                       │
│  生成命令:                                                         │
│    arjun -u <URL>                                                │
│          -m <METHOD>                                             │
│          --headers "<原始headers>\nX-XProbe-Arjun: 1"             │
│          -w <主域名参数字典>                                       │
│          -oB 127.0.0.1:8080  ← 关键: 发送到Burp代理               │
│          -t 5 -T 15                                              │
│          --disable-redirects -q                                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    4. Arjun 执行探测                              │
│                                                                   │
│  Arjun 使用继承的请求信息进行参数爆破:                             │
│    - 保留原始 URL                                                 │
│    - 保留原始 Headers (包括 Cookie, Authorization 等)             │
│    - 使用原始 HTTP 方法                                           │
│    - 尝试字典中的每个参数                                          │
│           ↓                                                       │
│  发现有效参数 → 通过 -oB 发送到 Burp Proxy                         │
│           ↓                                                       │
│  RequestHandler 再次拦截:                                          │
│    - 检测到 X-XProbe-Arjun 头 → 跳过参数收集 (避免循环)           │
│    - 但仍然进行被动漏洞扫描                                        │
│           ↓                                                       │
│  匹配扫描规则 → 执行 LFI/SQL/SSRF 等检测                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    5. 结果处理与去重                              │
│                                                                   │
│  更新 HostData:                                                   │
│    - 将发现的参数添加到 host 参数集合                              │
│    - 标记该 endpoint + 参数组合为已扫描                           │
│    - 持久化到 ~/.xprobe/arjun_state.json                         │
│           ↓                                                       │
│  去重机制:                                                         │
│    arjunScannedParams[endpoint] = 已扫描的参数集合                │
│           ↓                                                       │
│  下次扫描时:                                                       │
│    只使用新增的参数进行探测                                        │
└─────────────────────────────────────────────────────────────────┘
```

## 🔍 详细问题解答

### 1️⃣ Arjun 流量来源

**来源：Burp SiteMap**

```java
// 在 RealtimeScanner.performManualArjunScan() 中:
SiteMap siteMap = api.siteMap();
List<HttpRequestResponse> requestResponses = siteMap.requestResponses();
```

**为什么用 SiteMap？**
- ✅ SiteMap 包含了所有通过 Burp 的流量
- ✅ 可以获取完整的请求信息（headers、body、method 等）
- ✅ 支持从 Proxy、Repeater、Scanner 等多个来源收集

**过滤机制：**
```java
// 跳过 Arjun 自己产生的流量
for (var header : req.headers()) {
    if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
        isArjunTraffic = true;
        break;
    }
}
if (isArjunTraffic) continue;
```

### 2️⃣ Arjun 命令构建 - 完全继承原流量

**是的，Arjun 完全继承了原始请求！**

#### 继承的信息：

1. **URL 完全一致**
```java
String url = request.url();  // 使用原始 URL
command.add("-u");
command.add(url);
```

2. **HTTP 方法自动映射**
```java
private String mapMethod(String method, String contentType) {
    if ("POST".equalsIgnoreCase(method)) {
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            return "JSON";  // POST + JSON → Arjun 的 JSON 模式
        } else if (contentType != null && contentType.toLowerCase().contains("xml")) {
            return "XML";   // POST + XML → Arjun 的 XML 模式
        }
        return "POST";      // 普通 POST
    }
    return "GET";
}
```

3. **Headers 完全继承（除了自动生成的）**
```java
private String buildHeaders(HttpRequest request) {
    StringBuilder headersBuilder = new StringBuilder();
    
    for (var header : request.headers()) {
        String name = header.name();
        // 跳过由 Arjun 自动处理的 headers
        if ("Content-Length".equalsIgnoreCase(name) || 
            "Host".equalsIgnoreCase(name)) {
            continue;
        }
        // 保留其他所有 headers（包括 Cookie, Authorization 等）
        headersBuilder.append(name).append(": ").append(header.value()).append("\n");
    }
    
    // 添加标记头
    headersBuilder.append("X-XProbe-Arjun: 1\n");
    
    return headersBuilder.toString().trim();
}
```

**实际生成的命令示例：**
```bash
# 原始请求是 POST JSON 带 Authorization
arjun -u https://api.example.com/v1/users/profile \
      -m JSON \
      --headers "Authorization: Bearer eyJhbGc...
Cookie: session=abc123...
Content-Type: application/json
X-XProbe-Arjun: 1" \
      -w /tmp/xprobe_params_example.com_xxx.txt \
      -oB 127.0.0.1:8080 \
      -t 5 -T 15 --disable-redirects -q
```

### 3️⃣ 手动添加接口的处理

**当前实现支持手动添加，但需要完善！**

让我添加一个手动添加接口的功能：

```java
// 在 RealtimeScanner 中添加方法
public void addManualUrl(String url) {
    try {
        URL urlObj = new URL(url);
        String host = urlObj.getHost();
        String endpoint = urlObj.getPath();
        
        HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
        
        // 标记为手动添加的 URL
        hostData.addManualUrl(endpoint);
        
        api.logging().raiseInfoEvent("手动添加接口: " + url);
        
        // 可选：立即触发扫描
        // arjunIntegration.scan(HttpRequest.httpRequestFromUrl(url), hostData.getParameters());
    } catch (Exception e) {
        api.logging().raiseErrorEvent("添加手动接口失败: " + e.getMessage());
    }
}
```

### 4️⃣ 去重机制详解

**多层去重保证不重复探测：**

#### 层级1: Endpoint + 参数集合去重
```java
// 计算本次需要探测的"新增参数名"
Set<String> hostParams = new HashSet<>(hostData.getParameters());
Set<String> scanned = new HashSet<>(hostData.getArjunScannedParams(endpoint));
hostParams.removeAll(scanned);  // 只保留新增的参数

// 若无新增参数名，则跳过该endpoint
if (hostParams.isEmpty()) {
    continue;
}
```

#### 层级2: 持久化去重记录
```java
// 保存到 ~/.xprobe/arjun_state.json
{
  "hosts": {
    "example.com": {
      "/api/users": ["id", "name", "email"],        // 已扫描的参数
      "/api/posts": ["title", "content", "author"]
    },
    "admin.example.com": {
      "/dashboard": ["page", "section"]
    }
  }
}
```

#### 层级3: 实时更新
```java
// Arjun 扫描完成后
arjunIntegration.scan(req, hostParams).thenAccept(result -> {
    if (result.isSuccess()) {
        // 1. 更新已扫描标记
        hostData.markArjunScanned(endpoint, hostParams);
        
        // 2. 将发现的参数添加到 host 参数集合
        for (String param : result.getFoundParameters()) {
            hostData.addParameterToEndpoint(endpoint, param);
        }
        
        // 3. 持久化
        saveArjunStateSafely();
    }
});
```

### 5️⃣ 参数增量管理 - 主域名级别

**是的！参数是按主域名级别管理的！**

#### 主域名提取
```java
private String extractMainDomain(String host) {
    try {
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            // www.api.example.com → example.com
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    } catch (Exception e) {
        return host;
    }
}
```

#### 主域名级别参数字典
```java
private String createMainDomainDictionary(String mainDomain) throws IOException {
    String file = System.getProperty("java.io.tmpdir") + 
        "/xprobe_main_" + mainDomain + "_" + System.currentTimeMillis() + ".txt";
    
    Set<String> params = new HashSet<>();
    
    // 合并同主域名下所有 host 的参数
    for (Map.Entry<String, HostData> e : hostDataMap.entrySet()) {
        if (extractMainDomain(e.getKey()).equalsIgnoreCase(mainDomain)) {
            // 1. 从被动流量收集的参数
            params.addAll(e.getValue().getParameters());
            
            // 2. Arjun 已探测过的参数
            for (String ep : e.getValue().endpointInfoMap.keySet()) {
                params.addAll(e.getValue().getArjunScannedParams(ep));
            }
        }
    }
    
    // 3. 全局自定义字典
    params.addAll(globalCustomDictionary);
    
    // 4. 常见参数
    Collections.addAll(params, COMMON_PARAMETERS);
    
    // 写入文件
    try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
        for (String p : params) out.println(p);
    }
    
    return file;
}
```

#### 参数增量示例

**场景：扫描 example.com 下的多个子域**

```
时间线:
T1: 访问 www.example.com/api/users?id=1&name=alice
    → 收集参数: [id, name]
    → 字典: [id, name, 常见参数...]

T2: 访问 api.example.com/v2/posts?title=hello&author=bob
    → 收集参数: [title, author]
    → 字典: [id, name, title, author, 常见参数...]  ← 主域名级别合并

T3: 手动触发 Arjun 扫描
    → 对 www.example.com/api/users 探测:
        字典 = [id, name, title, author, 常见参数...]  ← 包含其他子域的参数
    → 发现新参数: [email]
    → 更新字典: [id, name, title, author, email, 常见参数...]

T4: 访问 admin.example.com/settings
    → Arjun 探测时使用的字典包含前面所有发现的参数
```

## 🔧 手动添加接口功能

### 使用方式

#### 1. 单个接口添加
```java
// 方式1: 使用默认 GET 方法
realtimeScanner.addManualUrl("https://api.example.com/v1/users");

// 方式2: 指定 HTTP 方法
realtimeScanner.addManualUrl("https://api.example.com/v1/login", "POST");
```

#### 2. 批量添加
```java
List<String> urls = Arrays.asList(
    "https://api.example.com/v1/users",
    "https://api.example.com/v1/posts",
    "https://api.example.com/v2/admin/settings"
);
realtimeScanner.addManualUrls(urls, "GET");
```

#### 3. 从文件导入
```bash
# urls.txt 文件内容：
https://api.example.com/v1/users
https://api.example.com/v1/posts
https://api.example.com/admin/dashboard
# 这是注释，会被跳过
```

```java
realtimeScanner.importUrlsFromFile("/path/to/urls.txt", "GET");
```

### 手动添加的处理流程

```
addManualUrl(url, method)
        ↓
1. 解析 URL → 提取 host 和 endpoint
        ↓
2. 获取/创建 HostData
        ↓
3. 标记为手动添加: hostData.addManualUrl(endpoint)
        ↓
4. 获取参数字典:
   - 该 host 从被动流量收集的参数
   - 全局自定义参数
   - Arjun 已发现的参数
        ↓
5. 立即执行 Arjun 扫描 (如果有参数字典)
        ↓
6. 扫描完成后:
   - 更新参数集合
   - 标记为已扫描 (避免下次重复)
   - 持久化状态
```

## 📋 去重机制详细说明

### 1. 数据结构设计

```java
class HostData {
    String host;                                    // 主机名
    Set<String> endpoints;                          // 所有接口
    Set<String> parameters;                         // 该 host 收集的所有参数
    Map<String, EndpointInfo> endpointInfoMap;     // 接口详细信息
    Map<String, Set<String>> arjunScannedParams;   // 已扫描记录
    Set<String> manualUrls;                         // 手动添加的接口
}

class EndpointInfo {
    String endpoint;
    String method;
    String contentType;
    Set<String> parameters;           // 该接口发现的参数
    Set<String> processedParameters;  // 已处理的参数
}
```

### 2. 去重检查点

#### 检查点 1: 扫描前检查
```java
// 获取该 host 的所有参数
Set<String> hostParams = new HashSet<>(hostData.getParameters());

// 获取该 endpoint 已经用哪些参数扫描过
Set<String> scanned = new HashSet<>(hostData.getArjunScannedParams(endpoint));

// 计算增量
hostParams.removeAll(scanned);

if (hostParams.isEmpty()) {
    // 没有新参数，跳过
    return;
}
```

#### 检查点 2: 持久化存储
```json
// ~/.xprobe/arjun_state.json
{
  "hosts": {
    "api.example.com": {
      "/v1/users": ["id", "name", "email"],      // 用这些参数扫描过
      "/v1/posts": ["id", "title", "author"]
    },
    "www.example.com": {
      "/login": ["username", "password", "token"]
    }
  }
}
```

#### 检查点 3: Arjun 流量标记
```java
// 所有 Arjun 发送的请求都带标记头
X-XProbe-Arjun: 1

// RequestHandler 中检查
for (var header : req.headers()) {
    if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
        // 这是 Arjun 的流量，跳过参数收集
        return;
    }
}
```

### 3. 增量场景示例

#### 场景 1: 正常增量
```
初始状态:
  host: api.example.com
  参数集合: [id, name]
  已扫描: {}

第一次扫描 /v1/users:
  使用参数: [id, name, 常见参数...]
  发现: [email, phone]
  更新:
    参数集合: [id, name, email, phone]
    已扫描: {"/v1/users": [id, name]}

被动流量新增参数 [age]:
  参数集合: [id, name, email, phone, age]

第二次扫描 /v1/users:
  hostParams = [id, name, email, phone, age]
  scanned = [id, name]
  增量 = [email, phone, age]  ← 只用新参数探测
  发现: [gender]
  更新:
    参数集合: [id, name, email, phone, age, gender]
    已扫描: {"/v1/users": [id, name, email, phone, age]}
```

#### 场景 2: 手动添加新接口
```
手动添加: https://api.example.com/v2/admin/settings

当前状态:
  参数集合: [id, name, email, phone, age, gender]
  已扫描: {"/v1/users": [...]}

扫描 /v2/admin/settings:
  使用参数: [id, name, email, phone, age, gender, 常见参数...]
  ↑ 复用同 host 下所有已知参数
  发现: [setting_key, value]
  更新:
    参数集合: [id, name, email, phone, age, gender, setting_key, value]
    已扫描: {
      "/v1/users": [...],
      "/v2/admin/settings": [id, name, email, phone, age, gender]
    }
```

#### 场景 3: 跨子域参数共享（主域名级别）
```
主域名: example.com

子域 1: www.example.com
  参数: [id, name]

子域 2: api.example.com
  参数: [token, key]

子域 3: admin.example.com (手动添加)
  使用字典: [id, name, token, key, 常见参数...]
  ↑ 合并了所有同主域名下的参数
```

## 🎯 完整使用示例

### 典型工作流程

```java
// 1. 初始化
RealtimeScanner scanner = new RealtimeScanner(api, configManager, globalFilter);

// 2. 添加自定义参数（可选）
scanner.addGlobalCustomParameter("api_key");
scanner.addGlobalCustomParameter("access_token");
scanner.addGlobalCustomParameter("debug");

// 3. 启动实时扫描（自动收集被动流量）
scanner.startRealtimeScanning();

// 4. 用户浏览网站
//    → 被动收集参数和接口
//    → 按 host 分组存储

// 5. 手动添加一些未被访问到的接口
scanner.addManualUrl("https://api.example.com/v1/admin/users", "GET");
scanner.addManualUrl("https://api.example.com/v1/settings", "POST");

// 或从文件批量导入
scanner.importUrlsFromFile("/path/to/endpoints.txt", "GET");

// 6. 查看当前收集的数据
Map<String, HostStatistics> stats = scanner.getHostStatistics();
for (HostStatistics stat : stats.values()) {
    System.out.println(stat.getHost() + ":");
    System.out.println("  接口数: " + stat.getEndpointCount());
    System.out.println("  参数数: " + stat.getParameterCount());
}

// 7. 手动触发 Arjun 全量扫描
scanner.triggerManualArjunScan();
//    → 对所有接口进行增量参数探测
//    → 发现的有效参数请求自动进入被动扫描器
//    → 检测 LFI/SQL/SSRF 等漏洞

// 8. 持续监控
//    - 新的被动流量继续被收集
//    - 定期触发 Arjun 扫描
//    - 参数集合不断增量更新
```

### Arjun 命令示例对比

#### 被动流量触发的 Arjun
```bash
# 原始请求:
POST /api/v1/login HTTP/1.1
Host: api.example.com
Cookie: session=abc123
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{"username":"test","password":"pass"}

# 生成的 Arjun 命令:
arjun -u https://api.example.com/api/v1/login \
      -m JSON \
      --headers "Cookie: session=abc123
Authorization: Bearer eyJhbGc...
Content-Type: application/json
X-XProbe-Arjun: 1" \
      -w /tmp/xprobe_main_example.com_xxx.txt \
      -oB 127.0.0.1:8080 \
      -t 5 -T 15 --disable-redirects -q
```

#### 手动添加接口的 Arjun
```bash
# 手动添加:
scanner.addManualUrl("https://api.example.com/admin/settings", "GET");

# 生成的 Arjun 命令:
arjun -u https://api.example.com/admin/settings \
      -m GET \
      --headers "X-XProbe-Arjun: 1" \
      -w /tmp/xprobe_main_example.com_xxx.txt \
      -oB 127.0.0.1:8080 \
      -t 5 -T 15 --disable-redirects -q

# 注意: 手动添加的接口没有原始 headers，但字典是共享的
```

## 🔍 调试和监控

### 查看收集的数据
```java
// 获取某个 host 的详细信息
HostData hostData = scanner.getHostData("api.example.com");

System.out.println("接口列表:");
for (String endpoint : hostData.getEndpoints()) {
    System.out.println("  " + endpoint);
    Set<String> params = hostData.getEndpointInfoMap().get(endpoint).getParameters();
    System.out.println("    参数: " + params);
}

System.out.println("\n所有参数: " + hostData.getParameters());
```

### 检查去重状态
```java
// 查看某个接口的扫描历史
String endpoint = "/api/v1/users";
Set<String> scanned = hostData.getArjunScannedParams(endpoint);
System.out.println("已用这些参数扫描过: " + scanned);

// 计算下次扫描会用哪些参数
Set<String> hostParams = new HashSet<>(hostData.getParameters());
hostParams.removeAll(scanned);
System.out.println("下次扫描将使用: " + hostParams);
```

### 清理和重置
```java
// 清空全局自定义字典
scanner.clearGlobalCustomDictionary();

// 重置某个 host 的扫描状态 (需要实现)
scanner.resetHostScanState("api.example.com");

// 删除持久化文件，完全重置
// rm ~/.xprobe/arjun_state.json
```

## 📊 性能优化建议

1. **分批处理大量接口**
```java
List<String> urls = loadManyUrls();  // 1000+ URLs
List<List<String>> batches = partition(urls, 50);  // 每批50个

for (List<String> batch : batches) {
    scanner.addManualUrls(batch, "GET");
    Thread.sleep(5000);  // 等待5秒
}
```

2. **限制字典大小**
```java
// 字典过大会影响 Arjun 性能
// 建议每个主域名的字典不超过 1000 个参数
Set<String> params = hostData.getParameters();
if (params.size() > 1000) {
    // 只使用最近发现的参数
    params = params.stream()
        .sorted(Comparator.reverseOrder())
        .limit(1000)
        .collect(Collectors.toSet());
}
```

3. **定期清理过期数据**
```java
// 清理7天未更新的 host 数据
scanner.cleanupOldHosts(7);
```

---

## 总结

通过这套机制，实现了：

✅ **完全继承原流量** - Arjun 使用原始请求的 URL、Method、Headers  
✅ **主域名级参数共享** - 同主域名下所有参数互相复用  
✅ **增量去重** - 只用新参数探测，避免重复  
✅ **手动接口支持** - 可以手动添加未被访问到的接口  
✅ **持久化状态** - 跨会话保留扫描记录  
✅ **无缝集成被动扫描** - 通过 `-oB` 自动触发漏洞检测

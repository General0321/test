# Arjun 集成总结 - 你的所有问题解答

## 📌 问题回顾

你提出的核心问题：
1. **Arjun 流量从哪里获取？**
2. **如何构建 Arjun 命令行？是否继承原流量？**
3. **手动添加接口如何处理？**
4. **去重问题如何解决？**
5. **参数增量和主域名分级如何实现？**

---

## ✅ 问题 1: Arjun 流量来源

### 回答：从 Burp SiteMap 获取

```java
// RealtimeScanner.performManualArjunScan()
SiteMap siteMap = api.siteMap();
List<HttpRequestResponse> requestResponses = siteMap.requestResponses();
```

### 为什么选择 SiteMap？

| 来源 | 优点 | 缺点 |
|------|------|------|
| **SiteMap** ✅ | • 包含所有流量<br>• 完整请求信息<br>• 支持多来源(Proxy/Repeater/Scanner) | • 可能包含重复 |
| Proxy History | 实时性好 | 只有代理流量 |
| Logger | 可自定义 | 需要额外配置 |

### 流量过滤机制

```java
// 跳过 Arjun 自己产生的流量（避免循环）
for (var header : req.headers()) {
    if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
        isArjunTraffic = true;
        break;
    }
}
if (isArjunTraffic) continue;  // 跳过
```

---

## ✅ 问题 2: Arjun 命令构建 - 完全继承原流量

### 回答：是的！完全继承！

### 继承的信息清单

| 项目 | 是否继承 | 说明 |
|------|---------|------|
| **URL** | ✅ 完全继承 | `request.url()` 原样使用 |
| **HTTP Method** | ✅ 自动映射 | GET/POST/JSON/XML |
| **Headers** | ✅ 完全继承 | Cookie, Authorization, 自定义头 |
| **Content-Type** | ✅ 自动识别 | 决定 Arjun 的 -m 参数 |
| **Body** | ⚠️ 部分继承 | Arjun 会根据 -m 参数重新构建 |

### 实际构建过程

```java
// ArjunIntegration.buildArjunCommand()

// 1. 继承 URL
String url = request.url();  // https://api.example.com/v1/users
command.add("-u");
command.add(url);

// 2. 映射 HTTP 方法
String method = request.method();          // POST
String contentType = getContentType(request);  // application/json
String arjunMethod = mapMethod(method, contentType);  // JSON
command.add("-m");
command.add(arjunMethod);

// 3. 继承 Headers
StringBuilder headersBuilder = new StringBuilder();
for (var header : request.headers()) {
    // 跳过 Arjun 自动处理的 headers
    if (!"Content-Length".equalsIgnoreCase(name) && 
        !"Host".equalsIgnoreCase(name)) {
        // 保留所有其他 headers
        headersBuilder.append(name).append(": ").append(value).append("\n");
    }
}
// 添加标记头
headersBuilder.append("X-XProbe-Arjun: 1\n");

command.add("--headers");
command.add(headersBuilder.toString());

// 4. 字典文件（主域名级别参数）
command.add("-w");
command.add(dictFile);  // /tmp/xprobe_main_example.com_xxx.txt

// 5. 输出到 Burp 代理 ← 关键！
command.add("-oB");
command.add("127.0.0.1:8080");
```

### 实际命令示例

**原始请求:**
```http
POST /api/v1/login HTTP/1.1
Host: api.example.com
Cookie: session=abc123; user_id=456
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
User-Agent: Mozilla/5.0

{"username":"admin","password":"pass123"}
```

**生成的 Arjun 命令:**
```bash
arjun -u https://api.example.com/api/v1/login \
      -m JSON \
      --headers "Cookie: session=abc123; user_id=456
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
User-Agent: Mozilla/5.0
X-XProbe-Arjun: 1" \
      -w /tmp/xprobe_main_example.com_1234567890.txt \
      -oB 127.0.0.1:8080 \
      -t 5 -T 15 --disable-redirects -q
```

### 继承效果

✅ **认证信息保留** - Cookie, Authorization 完全继承  
✅ **会话保持** - 同一个用户身份进行探测  
✅ **上下文完整** - User-Agent, Referer 等全部保留  
✅ **请求类型匹配** - JSON/XML 请求正确映射  

---

## ✅ 问题 3: 手动添加接口的处理

### 回答：新增了完整的手动接口支持

### 三种添加方式

#### 1. 单个添加
```java
// 默认 GET 方法
realtimeScanner.addManualUrl("https://api.example.com/v1/admin/users");

// 指定 HTTP 方法
realtimeScanner.addManualUrl("https://api.example.com/v1/settings", "POST");
```

#### 2. 批量添加
```java
List<String> urls = Arrays.asList(
    "https://api.example.com/admin/dashboard",
    "https://api.example.com/admin/settings",
    "https://api.example.com/internal/debug"
);
realtimeScanner.addManualUrls(urls, "GET");
```

#### 3. 从文件导入
```java
// urls.txt 文件内容：
// https://api.example.com/v1/users
// https://api.example.com/v1/posts
// # 这是注释

realtimeScanner.importUrlsFromFile("/path/to/urls.txt", "GET");
```

### 手动接口处理流程

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
   • 该 host 从被动流量收集的参数
   • 全局自定义参数
   • Arjun 已发现的参数
        ↓
5. 检查是否有可用参数
   if (hostParams.isEmpty()) {
       → 提示: "请先收集被动流量或添加自定义参数"
   } else {
       → 立即执行 Arjun 扫描
   }
        ↓
6. 扫描完成后:
   • 更新参数集合
   • 标记为已扫描 (避免下次重复)
   • 持久化状态
```

### 手动接口 vs 被动收集的区别

| 特性 | 被动收集的接口 | 手动添加的接口 |
|------|--------------|--------------|
| **Headers** | ✅ 完全继承原请求 | ❌ 只有基础头 + X-XProbe-Arjun |
| **认证信息** | ✅ Cookie, Token 继承 | ❌ 需要手动配置 |
| **参数字典** | ✅ 相同 | ✅ 相同（主域名级别共享） |
| **触发时机** | 手动触发批量扫描 | 立即触发单个扫描 |

### 如何为手动接口添加认证

**方案 1: 从 SiteMap 复制请求**
```java
// 从已有请求复制 headers
HttpRequest templateRequest = findRequestInSiteMap(host);
String headers = extractHeaders(templateRequest);

// 构建带认证的 Arjun 命令
// (需要修改 ArjunIntegration 支持自定义 headers)
```

**方案 2: 全局配置认证头**
```java
// 在 ExternalToolConfig 中添加
config.setGlobalHeaders(
    "Cookie: session=abc123\n" +
    "Authorization: Bearer xxx"
);
```

---

## ✅ 问题 4: 去重机制详解

### 回答：三层去重机制

### 层级 1: 实时增量检测

```java
// 扫描前检查
Set<String> hostParams = new HashSet<>(hostData.getParameters());  // 当前所有参数
Set<String> scanned = new HashSet<>(hostData.getArjunScannedParams(endpoint));  // 已扫描参数
hostParams.removeAll(scanned);  // 计算增量

if (hostParams.isEmpty()) {
    // 无新参数，跳过
    continue;
}
```

### 层级 2: 持久化状态记录

```json
// ~/.xprobe/arjun_state.json
{
  "hosts": {
    "api.example.com": {
      "/v1/users": ["id", "name", "email"],      // 已用这些参数扫描过
      "/v1/posts": ["id", "title", "author"],
      "/admin/settings": ["id", "name", "key"]
    },
    "www.example.com": {
      "/login": ["username", "password", "token"]
    }
  }
}
```

### 层级 3: 流量标记过滤

```java
// 所有 Arjun 发送的请求都带标记头
X-XProbe-Arjun: 1

// RequestHandler 检查
for (var header : req.headers()) {
    if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
        // 这是 Arjun 的流量
        // 1. 跳过参数收集（避免循环）
        // 2. 但仍然进行被动漏洞扫描
        return;
    }
}
```

### 去重数据结构

```java
class HostData {
    String host;
    Set<String> endpoints;                    // 所有接口
    Set<String> parameters;                   // 该 host 收集的所有参数
    Map<String, EndpointInfo> endpointInfoMap;
    Map<String, Set<String>> arjunScannedParams;  // ← 去重关键
    //                ↑endpoint  ↑已用这些参数扫描过
    Set<String> manualUrls;
}
```

### 增量场景示例

```
T0: 初始状态
  host: api.example.com
  参数: []
  已扫描: {}

T1: 被动收集
  访问: /v1/users?id=1&name=alice
  参数: [id, name]

T2: 第一次 Arjun 扫描
  扫描 /v1/users
  使用参数: [id, name, 常见参数...]
  发现: [email, phone]
  更新:
    参数: [id, name, email, phone]
    已扫描: {"/v1/users": [id, name]}

T3: 被动收集新参数
  访问: /v1/posts?age=25
  参数: [id, name, email, phone, age]

T4: 第二次 Arjun 扫描
  hostParams = [id, name, email, phone, age]
  scanned = [id, name]
  增量 = [email, phone, age]  ← 只用新参数
  
  扫描 /v1/users（使用新参数）
  发现: [gender]
  更新:
    参数: [id, name, email, phone, age, gender]
    已扫描: {"/v1/users": [id, name, email, phone, age]}

T5: 手动添加新接口
  addManualUrl("/admin/settings")
  
  扫描 /admin/settings
  使用参数: [id, name, email, phone, age, gender, 常见参数...]
  ↑ 复用同 host 所有参数
  发现: [setting_key, value]
  更新:
    参数: [id, name, email, phone, age, gender, setting_key, value]
    已扫描: {
      "/v1/users": [id, name, email, phone, age],
      "/admin/settings": [id, name, email, phone, age, gender]
    }
```

---

## ✅ 问题 5: 参数增量和主域名分级

### 回答：完整的主域名级参数管理

### 主域名提取

```java
private String extractMainDomain(String host) {
    // www.api.example.com → example.com
    // admin.example.com → example.com
    // example.com → example.com
    
    String[] parts = host.split("\\.");
    if (parts.length >= 2) {
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
    return host;
}
```

### 主域名级参数字典构建

```java
private String createMainDomainDictionary(String mainDomain) throws IOException {
    Set<String> params = new HashSet<>();
    
    // 1. 合并同主域名下所有 host 的参数
    for (Map.Entry<String, HostData> e : hostDataMap.entrySet()) {
        if (extractMainDomain(e.getKey()).equalsIgnoreCase(mainDomain)) {
            // 被动流量收集的参数
            params.addAll(e.getValue().getParameters());
            
            // Arjun 已探测过的参数
            for (String ep : e.getValue().endpointInfoMap.keySet()) {
                params.addAll(e.getValue().getArjunScannedParams(ep));
            }
        }
    }
    
    // 2. 全局自定义字典
    params.addAll(globalCustomDictionary);
    
    // 3. 常见参数（内置）
    Collections.addAll(params, COMMON_PARAMETERS);
    
    // 4. 写入临时文件
    String file = "/tmp/xprobe_main_" + mainDomain + "_" + timestamp + ".txt";
    try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
        for (String p : params) out.println(p);
    }
    
    return file;
}
```

### 跨子域参数共享示例

```
主域名: example.com

子域 1: www.example.com
  被动收集: [id, name, category]
  
子域 2: api.example.com
  被动收集: [token, api_key, version]
  
子域 3: admin.example.com (手动添加)
  
构建字典时:
  mainDomain = extractMainDomain("admin.example.com") = "example.com"
  
  合并参数:
    www.example.com: [id, name, category]
    api.example.com: [token, api_key, version]
    全局自定义: [debug, admin, secret]
    常见参数: [user, email, password, ...]
  
  最终字典: [id, name, category, token, api_key, version, debug, admin, secret, user, email, password, ...]
  
  扫描 admin.example.com 时使用这个合并字典
  ↑ 包含了所有同主域名下的参数
```

### 参数增量流程

```
初始:
  example.com 字典: [常见参数...]

被动收集 www.example.com:
  发现参数: [id, name]
  example.com 字典: [id, name, 常见参数...]

被动收集 api.example.com:
  发现参数: [token, key]
  example.com 字典: [id, name, token, key, 常见参数...]

Arjun 扫描 www.example.com/users:
  使用字典: [id, name, token, key, 常见参数...]
  发现: [email, phone]
  example.com 字典: [id, name, token, key, email, phone, 常见参数...]

手动添加 admin.example.com/settings:
  使用字典: [id, name, token, key, email, phone, 常见参数...]
  发现: [setting_key, value]
  example.com 字典: [id, name, token, key, email, phone, setting_key, value, 常见参数...]

持续增长... ♾️
```

---

## 🎯 核心流程总结

### 完整的自动化流程

```
1. 被动流量收集
   Burp Proxy → RequestHandler
   ↓
   提取: host, endpoint, parameters, method, content-type
   ↓
   存储到 HostData (按 host 分组)
   
2. 手动添加接口（可选）
   addManualUrl(url, method)
   ↓
   解析 URL → 标记为手动添加
   ↓
   立即触发 Arjun 扫描（如果有参数字典）
   
3. 触发 Arjun 探测
   triggerManualArjunScan()
   ↓
   从 SiteMap 获取所有请求
   ↓
   过滤: 跳过有 X-XProbe-Arjun 头的请求
   ↓
   按 host 分组
   
4. 增量检测
   for each (host, endpoint):
     hostParams = 当前 host 的所有参数
     scannedParams = 该 endpoint 已扫描的参数
     toScan = hostParams - scannedParams
     
     if toScan.isEmpty():
       跳过 (无新参数)
     else:
       执行 Arjun 扫描
       
5. 构建 Arjun 命令
   arjun -u <URL>                    # 继承原始 URL
         -m <METHOD>                 # 映射 HTTP 方法
         --headers "<原始headers>    # 继承所有 headers
                    X-XProbe-Arjun: 1"  # 标记头
         -w <主域名参数字典>          # 合并同主域名参数
         -oB 127.0.0.1:8080          # ← 关键: 输出到 Burp
         -t 5 -T 15 --disable-redirects -q
         
6. Arjun 执行探测
   使用继承的请求信息进行参数爆破
   ↓
   发现有效参数 → 通过 -oB 发送到 Burp Proxy
   ↓
   RequestHandler 再次拦截:
     • 检测到 X-XProbe-Arjun 头
     • 跳过参数收集 (避免循环)
     • 但仍然进行被动漏洞扫描
     
7. 结果处理
   更新 HostData:
     • 将发现的参数添加到 host 参数集合
     • 标记该 endpoint + 参数组合为已扫描
     • 持久化到 ~/.xprobe/arjun_state.json
     
8. 被动漏洞扫描
   Arjun 发现的有效参数请求 → RequestHandler
   ↓
   匹配扫描规则 → 执行 LFI/SQL/SSRF 等检测
   ↓
   发现漏洞 → 报告到 LogTab
```

---

## 📊 关键技术点

### 1. `-oB` 参数的关键作用

```bash
arjun ... -oB 127.0.0.1:8080
```

**效果：**
- ✅ Arjun 发现的有效参数请求自动发送到 Burp Proxy
- ✅ 不需要解析 Arjun 的 JSON 输出
- ✅ 不需要手动重放请求
- ✅ 被动扫描器自动接收并检测

**对比传统方式：**

| 方式 | 步骤 | 效率 |
|------|------|------|
| **传统** | Arjun 输出 JSON → 解析 → 构建请求 → 发送给 Burp | ❌ 复杂 |
| **-oB 方式** | Arjun 直接发送给 Burp | ✅ 简单 |

### 2. 去重的核心算法

```java
// 增量计算
Set<String> toScan = new HashSet<>(allParams);
toScan.removeAll(scannedParams);

if (toScan.isEmpty()) {
    // 无需扫描
} else {
    // 只用新参数扫描
}
```

### 3. 主域名级别参数共享

```
www.example.com: [a, b, c]
api.example.com: [d, e, f]
admin.example.com: 使用 [a, b, c, d, e, f, ...]
                       ↑ 合并了所有同主域名的参数
```

### 4. 流量标记防循环

```
Arjun 请求带标记 → X-XProbe-Arjun: 1
                 ↓
        RequestHandler 检测到标记
                 ↓
          跳过参数收集 (防止循环)
                 ↓
        但仍然进行漏洞扫描 (正常检测)
```

---

## 📁 相关文件

| 文件 | 说明 |
|------|------|
| `ARJUN_INTEGRATION.md` | Arjun 集成方案完整文档 |
| `ARJUN_WORKFLOW_DETAIL.md` | 工作流程详细说明 |
| `USAGE_EXAMPLES.md` | 使用示例和最佳实践 |
| `ArjunIntegration.java` | Arjun 集成核心类 |
| `RealtimeScanner.java` | 实时扫描器（增加手动接口支持） |
| `ExternalToolConfig.java` | 外部工具配置（从 x8 改为 Arjun） |

---

## 🚀 快速开始

```java
// 1. 启动被动收集
realtimeScanner.startRealtimeScanning();

// 2. 浏览网站（自动收集参数和接口）

// 3. 手动添加未访问的接口（可选）
realtimeScanner.addManualUrl("https://api.example.com/admin/users");

// 4. 触发 Arjun 探测
realtimeScanner.triggerManualArjunScan();

// 5. 自动检测漏洞
// Arjun 发现的有效参数请求 → Burp Proxy → 被动扫描器 → 漏洞报告
```

---

## ✅ 总结

通过这套实现，你的问题都得到了解决：

1. ✅ **流量来源** - 从 Burp SiteMap 获取，包含所有流量
2. ✅ **完全继承** - URL、Method、Headers 全部继承
3. ✅ **手动接口** - 支持单个/批量/文件导入三种方式
4. ✅ **智能去重** - 三层去重机制，只探测新参数
5. ✅ **参数增量** - 主域名级别参数共享和增量管理

核心优势：
- 🎯 **自动化** - 被动收集 → Arjun 探测 → 被动扫描，全程自动
- 🧠 **智能化** - 增量检测，避免重复，参数复用
- 🔗 **无缝集成** - `-oB` 参数直接输出到 Burp
- 💾 **持久化** - 跨会话保留扫描状态

这就是一个真正的"自动化被动渗透测试"流程！🚀

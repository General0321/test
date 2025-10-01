# Arjun 参数探测集成方案

## 🎯 核心优势

将 **x8** 替换为 **Arjun** 的关键优势在于 Arjun 的 `-oB` 参数特性：

```bash
arjun -u <URL> -oB 127.0.0.1:8080  # 直接将探测结果发送到 Burp 代理
```

这意味着：
- ✅ **无缝集成**：Arjun 发现的有效参数请求会自动进入 Burp 的被动扫描流程
- ✅ **自动触发**：不需要手动处理 Arjun 的输出，探测结果自动提交给被动扫描器
- ✅ **完整上下文**：保留原始请求的所有headers、cookies、认证信息

## 🔄 完整流程

### 1. 被动流量收集阶段
```
Burp Proxy/Repeater → RequestHandler → 收集参数和接口
                                    ↓
                         按主域名级别存储到 HostData
```

### 2. 参数字典构建
```
每个主域名维护：
- 从被动流量提取的参数
- 用户自定义参数字典  
- Arjun 发现的新参数
- 常见参数（内置）
```

### 3. Arjun 探测阶段
```
手动触发 (UI按钮) → 按 host 分组
                 ↓
         对每个 endpoint 执行增量探测
                 ↓
         arjun -u <URL> -w <dict> -oB 127.0.0.1:8080
                 ↓
         探测结果自动进入 Burp Proxy
                 ↓
         被动扫描器自动检测漏洞
```

### 4. 去重机制
```
记录已扫描的组合：
- Host + Endpoint + 参数集合 → 避免重复探测
- 持久化存储 → 跨会话保留扫描状态
```

## 📁 核心类说明

### 1. ArjunIntegration.java
**新增的专用 Arjun 集成类**

```java
// 使用方式
ArjunIntegration arjun = new ArjunIntegration(api, config);

// 单个URL扫描
CompletableFuture<ArjunResult> result = arjun.scan(request, customParams);

// 批量扫描
CompletableFuture<List<ArjunResult>> results = arjun.scanBatch(requests, customParams);
```

**关键特性**：
- 自动构建 Arjun 命令（方法映射、headers继承）
- 使用 `-oB` 参数将流量发送到 Burp
- 解析 Arjun 输出提取发现的参数
- 异步执行，不阻塞主线程

### 2. RealtimeScanner.java
**实时扫描器 - 管理被动流量和主动探测**

```java
// 启动实时扫描（自动收集被动流量）
realtimeScanner.startRealtimeScanning();

// 手动触发 Arjun 探测
realtimeScanner.triggerManualArjunScan();
```

**数据结构**：
```java
Map<String, HostData> hostDataMap;  // 按 host 分组

class HostData {
    Set<String> endpoints;           // 收集的接口
    Set<String> parameters;          // 收集的参数
    Map<String, EndpointInfo> endpointInfoMap;  // 接口详细信息
    Map<String, Set<String>> arjunScannedParams;  // 已扫描标记
}
```

### 3. ExternalToolConfig.java
**工具配置类**

```java
// Arjun 配置
config.setArjunPath("arjun");              // Arjun 路径
config.setBurpProxyAddress("127.0.0.1:8080");  // Burp 代理地址
config.setSendToBurp(true);                // 启用 -oB 选项
config.setThreadCount(5);                  // 并发线程数
config.setTimeout(15);                     // 超时时间
```

## 🚀 使用方法

### 方式一：UI 手动触发
1. 浏览目标网站，XProbe 自动收集被动流量
2. 点击"主动扫描"选项卡
3. 点击"执行 Arjun 参数探测"按钮
4. Arjun 开始探测，结果自动进入被动扫描

### 方式二：手动添加接口
```java
// 在 ActiveScanner 中手动添加需要探测的 URL
activeScanner.addManualUrl(host, "/api/v1/users");
activeScanner.addManualUrl(host, "/admin/settings");
```

### 方式三：程序化调用
```java
// 直接使用 ArjunIntegration
ArjunIntegration arjun = new ArjunIntegration(api, config);

// 自定义参数字典
Set<String> customParams = new HashSet<>();
customParams.add("api_key");
customParams.add("access_token");

// 执行扫描
arjun.scan(request, customParams).thenAccept(result -> {
    if (result.isSuccess()) {
        System.out.println("发现参数: " + result.getFoundParameters());
    }
});
```

## 🔧 Arjun 命令示例

### GET 请求探测
```bash
arjun -u https://example.com/api/users \
      -m GET \
      -w /tmp/xprobe_params.txt \
      -t 5 \
      -T 15 \
      --rate-limit 9999 \
      --headers "Authorization: Bearer token\nX-XProbe-Arjun: 1" \
      -oB 127.0.0.1:8080 \
      --disable-redirects \
      -q
```

### POST JSON 请求探测
```bash
arjun -u https://example.com/api/login \
      -m JSON \
      -w /tmp/xprobe_params.txt \
      -t 5 \
      -T 15 \
      --headers "Content-Type: application/json\nX-XProbe-Arjun: 1" \
      -oB 127.0.0.1:8080
```

## 📊 增量探测策略

### 主域名级别参数字典
```
example.com 域名的字典包含：
1. 从所有 *.example.com 子域收集的参数
2. Arjun 在该域名下发现的参数
3. 用户为该域名添加的自定义参数
4. 通用常见参数
```

### 去重逻辑
```java
// 生成唯一标识
String key = host + "|" + endpoint + "|" + sortedParams;

// 检查是否已探测
if (arjunProcessedKeys.contains(key)) {
    return;  // 跳过已探测的组合
}

// 执行探测后标记
arjunProcessedKeys.add(key);
```

### 持久化状态
```json
{
  "hosts": {
    "example.com": {
      "/api/users": ["id", "name", "email"],
      "/api/posts": ["title", "content", "author_id"]
    }
  }
}
```

## 🔍 与被动扫描的配合

### 流程图
```
Arjun 探测 (-oB 127.0.0.1:8080)
    ↓
发现有效参数的请求发送到 Burp Proxy
    ↓
XProbe 的 RequestHandler 拦截
    ↓
检查参数名是否匹配扫描规则
    ↓
执行被动漏洞扫描 (LFI/SQL/SSRF等)
    ↓
发现漏洞 → 记录到扫描结果
```

### 标记 Arjun 流量
所有 Arjun 发送的请求都带有标记头：
```http
X-XProbe-Arjun: 1
```

这样可以：
1. 识别哪些流量来自 Arjun
2. 避免循环采集 Arjun 自己的流量
3. 统计 Arjun 的探测效果

## ⚙️ 配置选项

### 1. 探测模式
```java
public enum ActiveProbeMode {
    BRUTE_FORCE_ONLY,          // 仅参数爆破
    BRUTE_FORCE_AND_PASSIVE    // 爆破 + 自动被动扫描
}

// 默认模式：爆破结果自动进入被动扫描
realtimeScanner.setActiveProbeMode(
    ActiveProbeMode.BRUTE_FORCE_AND_PASSIVE
);
```

### 2. Burp 代理配置
```java
config.setBurpProxyAddress("127.0.0.1:8080");  // 默认
config.setBurpProxyAddress("127.0.0.1:9090");  // 自定义端口
```

### 3. 性能调优
```java
config.setThreadCount(5);      // Arjun 并发线程数（默认5）
config.setTimeout(15);         // 单个请求超时（秒）
config.setRateLimit(9999);     // 每秒最大请求数
```

## 📝 最佳实践

### 1. 参数字典管理
```java
// 为特定域名添加自定义参数
realtimeScanner.addGlobalCustomParameter("internal_api_key");
realtimeScanner.addGlobalCustomParameter("debug_token");
```

### 2. 分批探测
```java
// 不要一次性探测所有URL，分批处理
List<HttpRequest> requests = collectRequests();
List<List<HttpRequest>> batches = partition(requests, 10);

for (List<HttpRequest> batch : batches) {
    arjun.scanBatch(batch, customParams);
    Thread.sleep(1000);  // 间隔1秒
}
```

### 3. 监控探测进度
```java
// 查看统计信息
Map<String, HostStatistics> stats = realtimeScanner.getHostStatistics();
for (HostStatistics stat : stats.values()) {
    System.out.println(stat.getHost() + ": " +
        stat.getEndpointCount() + " endpoints, " +
        stat.getParameterCount() + " params");
}
```

## 🐛 调试技巧

### 1. 启用详细日志
```java
config.setEnableVerboseOutput(true);  // Arjun 详细输出
```

### 2. 检查 Arjun 输出
查看 Burp 扩展控制台的日志：
```
Arjun输出: [FOUND] api_key
Arjun输出: [FOUND] access_token
Arjun发现参数: https://example.com/api - [api_key, access_token]
```

### 3. 验证 Burp 代理连接
```bash
# 测试 Burp 代理是否可用
curl -x http://127.0.0.1:8080 https://example.com
```

## 📈 性能优化

### 1. 避免重复探测
- 使用 `arjunProcessedKeys` 去重
- 持久化扫描状态
- 增量字典更新

### 2. 异步执行
- 所有 Arjun 调用都是异步的（CompletableFuture）
- 不阻塞主线程
- 支持批量并发探测

### 3. 内存管理
- LRU 缓存限制去重集合大小
- 定期清理过期的扫描记录

## 🔗 相关文件

- `src/main/java/com/xprobe/scanner/active/ArjunIntegration.java` - Arjun 集成核心
- `src/main/java/com/xprobe/scanner/active/RealtimeScanner.java` - 实时扫描器
- `src/main/java/com/xprobe/scanner/active/ExternalToolConfig.java` - 工具配置
- `src/main/java/com/xprobe/scanner/core/RequestHandler.java` - 被动流量处理

---

**总结**：通过 Arjun 的 `-oB` 特性，实现了参数探测和被动扫描的无缝集成，大大简化了流程并提高了自动化程度。

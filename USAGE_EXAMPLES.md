# XProbe + Arjun 使用示例

## 📖 快速开始

### 场景 1: 纯被动流量探测

```java
// 1. 自动收集被动流量
// 用户正常浏览目标网站，XProbe 自动收集：
//   - 访问的接口
//   - 发现的参数
//   - HTTP方法和Content-Type

// 浏览后的数据示例：
// Host: api.example.com
//   Endpoints: [/v1/users, /v1/posts, /v1/comments]
//   Parameters: [id, name, email, title, content]

// 2. 手动触发 Arjun 探测
realtimeScanner.triggerManualArjunScan();

// 3. Arjun 自动工作：
//   - 对每个接口使用收集到的参数探测
//   - 发现的有效参数通过 -oB 发送到 Burp
//   - 被动扫描器自动检测漏洞
```

### 场景 2: 被动流量 + 手动接口

```java
// 1. 浏览网站收集一些参数
// 假设收集到: [id, name, email]

// 2. 添加未被访问的管理接口
realtimeScanner.addManualUrl("https://api.example.com/admin/users", "GET");
realtimeScanner.addManualUrl("https://api.example.com/admin/settings", "POST");

// 3. Arjun 立即开始探测这些手动接口
//    使用已收集的参数 [id, name, email, 常见参数...]

// 4. 发现新参数后继续浏览
//    新参数会被添加到字典中
```

### 场景 3: 从文件批量导入接口

```bash
# endpoints.txt
https://api.example.com/v1/users
https://api.example.com/v1/posts
https://api.example.com/v2/admin/dashboard
https://api.example.com/v2/admin/settings
https://api.example.com/internal/debug
```

```java
// 1. 先添加自定义参数（可选）
realtimeScanner.addGlobalCustomParameter("api_key");
realtimeScanner.addGlobalCustomParameter("access_token");
realtimeScanner.addGlobalCustomParameter("debug");

// 2. 批量导入接口
realtimeScanner.importUrlsFromFile("/path/to/endpoints.txt", "GET");

// 3. Arjun 自动使用自定义参数探测所有接口
```

## 🎬 完整工作流示例

### 示例 1: API 测试完整流程

```java
public class APISecurityTest {
    private RealtimeScanner scanner;
    
    public void testAPI() {
        // === 第一阶段：准备工作 ===
        
        // 1. 添加域名特定的参数
        scanner.addGlobalCustomParameter("app_id");
        scanner.addGlobalCustomParameter("app_secret");
        scanner.addGlobalCustomParameter("nonce");
        scanner.addGlobalCustomParameter("timestamp");
        
        // 2. 启动被动收集
        scanner.startRealtimeScanning();
        
        System.out.println("开始浏览网站，收集被动流量...");
        
        // === 第二阶段：浏览网站 ===
        
        // 用户在 Burp 中浏览：
        // GET  /api/v1/users?id=1&name=alice
        // POST /api/v1/login (username=admin&password=pass)
        // GET  /api/v1/posts?page=1&limit=10
        
        // XProbe 自动收集：
        // Parameters: [id, name, username, password, page, limit]
        // Endpoints: [/api/v1/users, /api/v1/login, /api/v1/posts]
        
        // === 第三阶段：添加手动接口 ===
        
        System.out.println("添加未访问的接口...");
        
        List<String> adminEndpoints = Arrays.asList(
            "https://api.example.com/admin/users",
            "https://api.example.com/admin/settings",
            "https://api.example.com/admin/logs"
        );
        scanner.addManualUrls(adminEndpoints, "GET");
        
        // === 第四阶段：查看收集情况 ===
        
        Map<String, HostStatistics> stats = scanner.getHostStatistics();
        for (HostStatistics stat : stats.values()) {
            System.out.println("Host: " + stat.getHost());
            System.out.println("  接口数: " + stat.getEndpointCount());
            System.out.println("  参数数: " + stat.getParameterCount());
        }
        
        // === 第五阶段：触发 Arjun 探测 ===
        
        System.out.println("开始 Arjun 参数探测...");
        scanner.triggerManualArjunScan();
        
        // Arjun 工作：
        // - 对 /api/v1/users 探测，使用字典 [id, name, username, password, page, limit, app_id, ...]
        // - 对 /admin/users 探测，使用相同的字典（主域名级别共享）
        // - 发现新参数 [role, permission, token]
        
        // === 第六阶段：持续监控 ===
        
        // 继续浏览或等待一段时间后再次触发
        try {
            Thread.sleep(60000); // 等待60秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 再次触发（只会探测新增的参数）
        scanner.triggerManualArjunScan();
    }
}
```

### 示例 2: 多子域测试

```java
public void testMultipleSubdomains() {
    // === 场景：测试 example.com 的多个子域 ===
    
    // 1. 浏览 www.example.com
    //    收集参数: [id, name, category]
    
    // 2. 浏览 api.example.com
    //    收集参数: [token, api_key, version]
    
    // 3. 手动添加 admin.example.com 的接口
    scanner.addManualUrl("https://admin.example.com/dashboard", "GET");
    
    // 此时 admin.example.com 的探测会使用：
    // [id, name, category, token, api_key, version, 常见参数...]
    // ↑ 合并了所有同主域名下的参数
    
    // 4. 查看主域名级别的参数共享
    HostData www = scanner.getHostData("www.example.com");
    HostData api = scanner.getHostData("api.example.com");
    HostData admin = scanner.getHostData("admin.example.com");
    
    System.out.println("www 参数: " + www.getParameters());
    System.out.println("api 参数: " + api.getParameters());
    System.out.println("admin 参数: " + admin.getParameters());
    
    // 创建主域名字典时会合并所有参数
    String mainDomain = "example.com";
    Set<String> allParams = new HashSet<>();
    allParams.addAll(www.getParameters());
    allParams.addAll(api.getParameters());
    allParams.addAll(admin.getParameters());
    
    System.out.println("主域名 " + mainDomain + " 的总参数数: " + allParams.size());
}
```

## 🔍 实际案例分析

### 案例 1: 发现隐藏的管理接口参数

```
初始状态:
  浏览 /api/v1/users?id=1
  收集参数: [id]

第一次 Arjun 探测:
  探测 /api/v1/users
  字典: [id, 常见参数: user, name, email, ...]
  发现: [name, email, role]

更新后:
  参数集合: [id, name, email, role]

手动添加管理接口:
  addManualUrl("/api/v1/admin/users")
  
第二次探测:
  探测 /api/v1/admin/users
  字典: [id, name, email, role, 常见参数...]
  发现: [permission, is_admin, secret_key]  ← 管理接口特有的参数

漏洞检测:
  被动扫描器检测到:
    /api/v1/admin/users?secret_key=<LFI_PAYLOAD>
    → 发现 LFI 漏洞！
```

### 案例 2: 跨接口参数复用

```
接口 A: /api/posts
  原始参数: [id, title]
  Arjun 发现: [author, content, publish_date]

接口 B: /api/comments
  原始参数: [post_id, user_id]
  Arjun 探测时使用: [id, title, author, content, publish_date, post_id, user_id, ...]
  ↑ 包含了接口 A 的参数
  发现: [comment_text, parent_id]

接口 C: /api/users (手动添加)
  Arjun 探测时使用: [所有已知参数...]
  发现: [username, password, avatar_url]

结果:
  所有接口共享一个不断增长的参数字典
  参数探测的覆盖面越来越广
```

## 📊 监控和调试

### 查看实时状态

```java
// 定期打印统计信息
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    Map<String, HostStatistics> stats = scanner.getHostStatistics();
    
    System.out.println("\n=== XProbe 状态报告 ===");
    System.out.println("时间: " + LocalDateTime.now());
    
    for (HostStatistics stat : stats.values()) {
        System.out.println("\nHost: " + stat.getHost());
        System.out.println("  接口数: " + stat.getEndpointCount());
        System.out.println("  参数数: " + stat.getParameterCount());
        System.out.println("  最后更新: " + new Date(stat.getLastUpdateTime()));
    }
    
    System.out.println("\n全局自定义参数: " + scanner.getGlobalCustomDictionary().size() + " 个");
    System.out.println("======================\n");
    
}, 0, 5, TimeUnit.MINUTES);  // 每5分钟报告一次
```

### 检查去重效果

```java
// 检查某个接口的扫描历史
String host = "api.example.com";
String endpoint = "/v1/users";

HostData hostData = scanner.getHostData(host);
Set<String> allParams = hostData.getParameters();
Set<String> scannedParams = hostData.getArjunScannedParams(endpoint);

System.out.println("接口: " + endpoint);
System.out.println("该 host 的所有参数: " + allParams);
System.out.println("已用于扫描的参数: " + scannedParams);

Set<String> pendingParams = new HashSet<>(allParams);
pendingParams.removeAll(scannedParams);
System.out.println("待扫描的新参数: " + pendingParams);

if (pendingParams.isEmpty()) {
    System.out.println("✅ 无需重复扫描");
} else {
    System.out.println("⚠️ 有 " + pendingParams.size() + " 个新参数等待探测");
}
```

## 🎯 最佳实践

### 1. 参数字典策略

```java
// 方式1: 纯被动收集（推荐用于大型网站）
scanner.startRealtimeScanning();
// 优点: 参数都来自真实流量，准确度高
// 缺点: 需要时间积累

// 方式2: 混合模式（推荐用于API测试）
scanner.addGlobalCustomParameter("debug");
scanner.addGlobalCustomParameter("admin");
scanner.addGlobalCustomParameter("internal");
scanner.startRealtimeScanning();
// 优点: 立即可用，覆盖面广
// 缺点: 可能有噪音

// 方式3: 导入已知参数（推荐用于重测）
scanner.importUrlsFromFile("known_endpoints.txt", "GET");
scanner.addGlobalCustomParameters(loadFromFile("known_params.txt"));
// 优点: 直接复用之前的成果
// 缺点: 需要维护参数列表
```

### 2. 探测时机

```java
// 策略1: 定时探测
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    scanner.triggerManualArjunScan();
}, 10, 30, TimeUnit.MINUTES);  // 每30分钟探测一次

// 策略2: 参数数量触发
// 当新参数数量达到阈值时自动触发
int lastParamCount = 0;
while (true) {
    int currentParamCount = getCurrentTotalParamCount();
    if (currentParamCount - lastParamCount >= 10) {
        scanner.triggerManualArjunScan();
        lastParamCount = currentParamCount;
    }
    Thread.sleep(60000);
}

// 策略3: 手动控制（推荐）
// 浏览完成后手动触发
scanner.triggerManualArjunScan();
```

### 3. 性能优化

```java
// 1. 限制并发
ExternalToolConfig config = scanner.getToolConfig();
config.setThreadCount(3);  // Arjun 使用3个线程
config.setTimeout(10);     // 10秒超时

// 2. 分批处理大量URL
List<String> urls = loadManyUrls();
for (int i = 0; i < urls.size(); i += 50) {
    List<String> batch = urls.subList(i, Math.min(i + 50, urls.size()));
    scanner.addManualUrls(batch, "GET");
    Thread.sleep(10000);  // 每批间隔10秒
}

// 3. 清理过期数据
// 定期重置长时间未更新的数据
scanner.cleanupOldHosts(7);  // 清理7天未更新的host
```

## ⚡ 故障排查

### 问题 1: Arjun 没有发现任何参数

```java
// 检查点1: Arjun 是否正确安装
String arjunPath = scanner.getArjunPath();
if (arjunPath == null) {
    System.err.println("❌ Arjun 未安装或不在 PATH 中");
    // 解决: pip install arjun
}

// 检查点2: 参数字典是否为空
Set<String> params = hostData.getParameters();
if (params.isEmpty()) {
    System.err.println("❌ 参数字典为空，请先收集被动流量或添加自定义参数");
    // 解决: scanner.addGlobalCustomParameter("...")
}

// 检查点3: 是否重复探测
Set<String> scanned = hostData.getArjunScannedParams(endpoint);
if (scanned.equals(params)) {
    System.err.println("✅ 该接口已用所有参数探测过，跳过");
}
```

### 问题 2: 被动扫描器没有触发

```java
// 检查点1: -oB 参数是否启用
if (!config.isSendToBurp()) {
    System.err.println("❌ Burp 代理输出未启用");
    config.setSendToBurp(true);
}

// 检查点2: Burp 代理地址是否正确
System.out.println("Burp 代理地址: " + config.getBurpProxyAddress());
// 默认应该是: 127.0.0.1:8080

// 检查点3: 是否有 X-XProbe-Arjun 头阻止了扫描
// 查看 Burp HTTP History，确认 Arjun 的流量有进入
```

### 问题 3: 内存占用过高

```java
// 解决方案1: 限制字典大小
if (params.size() > 1000) {
    params = params.stream()
        .limit(1000)
        .collect(Collectors.toSet());
}

// 解决方案2: 定期清理
scanner.clearGlobalCustomDictionary();
scanner.cleanupOldHosts(7);

// 解决方案3: 删除持久化文件重新开始
// rm ~/.xprobe/arjun_state.json
```

---

## 🎓 总结

XProbe + Arjun 的核心优势：

1. **自动化**: 被动流量自动收集 → Arjun 自动探测 → 被动扫描自动检测
2. **智能增量**: 只探测新增的参数，避免重复
3. **参数复用**: 主域名级别参数共享，提高覆盖面
4. **无缝集成**: `-oB` 参数直接将结果送入被动扫描
5. **灵活控制**: 支持手动添加接口、批量导入、定时触发

通过这套机制，可以实现真正的"自动化渗透测试"工作流！🚀

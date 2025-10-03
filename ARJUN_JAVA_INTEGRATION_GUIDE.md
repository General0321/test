# Arjun Java 内置版 - 集成使用指南

## ✅ 实现完成状态

### 已完成的模块

#### 1. 数据模型层 ✅
- `BaselineFactors.java` - 基线因子模型
- `AnomalyResult.java` - 异常检测结果
- `DiscoveryResult.java` - 参数发现结果  
- `ScanContext.java` - 扫描上下文
- `ParamCandidate.java` - 参数候选

#### 2. 核心算法层 ✅
- `ResponseBaseline.java` - 基线建立（9种检测规则）
- `AnomalyDetector.java` - 异常检测
- `ParamExtractor.java` - 启发式参数提取
- `ChunkProcessor.java` - 分块处理
- `ParamVerifier.java` - 参数验证

#### 3. HTTP请求层 ✅
- `BurpHttpRequester.java` - 使用Burp API发送请求

#### 4. 核心引擎 ✅
- `ParamDiscoveryEngine.java` - 主控逻辑

#### 5. 配置管理 ✅
- `ArjunConfig.java` - 配置类

---

## 🚀 快速开始

### 基本使用

```java
import com.xprobe.scanner.arjun.ParamDiscoveryEngine;
import com.xprobe.scanner.arjun.model.DiscoveryResult;

// 1. 创建引擎实例
ParamDiscoveryEngine engine = new ParamDiscoveryEngine(api);

// 2. 准备字典
Set<String> dictionary = new LinkedHashSet<>();
dictionary.add("admin");
dictionary.add("debug");
dictionary.add("test");
// ... 更多参数

// 3. 启动扫描（异步）
engine.scan(httpRequest, dictionary).thenAccept(result -> {
    if (result.isSuccess()) {
        Set<String> foundParams = result.getFoundParams();
        api.logging().raiseInfoEvent(
            "发现 " + foundParams.size() + " 个隐藏参数: " + foundParams
        );
    } else {
        api.logging().raiseErrorEvent(
            "扫描失败: " + result.getErrorMessage()
        );
    }
});
```

### 自定义配置

```java
// 使用自定义配置
int chunkSize = 250;           // 分块大小
boolean enableHeuristic = true; // 启用启发式提取

ParamDiscoveryEngine engine = new ParamDiscoveryEngine(
    api, 
    chunkSize, 
    enableHeuristic
);
```

---

## 🔧 集成到现有系统

### 方案1: 替换 ArjunIntegration（推荐）

在 `RealtimeScannerRefactored.java` 中：

```java
// ❌ 删除旧的
// private ArjunIntegration arjunIntegration;

// ✅ 添加新的
private ParamDiscoveryEngine paramDiscovery;

// 初始化
public RealtimeScannerRefactored(MontoyaApi api, ...) {
    // ...
    this.paramDiscovery = new ParamDiscoveryEngine(api);
}

// 触发扫描
public void triggerManualArjunScan(HttpRequest request, Set<String> dictionary) {
    paramDiscovery.scan(request, dictionary).thenAccept(result -> {
        if (result.isSuccess()) {
            // 将发现的参数添加到参数管理器
            Set<String> found = result.getFoundParams();
            String host = extractHost(request);
            String endpoint = extractEndpoint(request);
            
            parameterManager.addParameters(host, endpoint, found);
            
            // 记录结果到LogModel
            for (String param : found) {
                api.logging().raiseInfoEvent(
                    "✅ Arjun发现参数: " + param + " (URL: " + request.url() + ")"
                );
            }
        }
    });
}

// 自动触发扫描
public void triggerArjunScanFromProxy(HttpRequest request) {
    // 获取已收集的参数作为字典
    String host = extractHost(request);
    String endpoint = extractEndpoint(request);
    Set<String> dictionary = parameterCollector.getCollectedParams(host, endpoint);
    
    // 添加全局自定义参数
    dictionary.addAll(parameterManager.getGlobalCustomParams());
    
    // 启动扫描
    paramDiscovery.scan(request, dictionary).thenAccept(result -> {
        if (result.isSuccess() && !result.getFoundParams().isEmpty()) {
            parameterManager.addParameters(host, endpoint, result.getFoundParams());
        }
    });
}
```

### 方案2: 保留兼容性（双模式）

如果需要保留外部Arjun的兼容性：

```java
// 配置枚举
public enum ArjunMode {
    INTERNAL,  // Java内置版
    EXTERNAL   // 外部Python版
}

// 在配置中添加
private ArjunMode arjunMode = ArjunMode.INTERNAL;

// 初始化时根据模式选择
if (config.getArjunMode() == ArjunMode.INTERNAL) {
    this.paramDiscovery = new ParamDiscoveryEngine(api);
} else {
    this.arjunIntegration = new ArjunIntegration(api, config);
}

// 调用时判断
public void triggerScan(HttpRequest request, Set<String> dictionary) {
    if (config.getArjunMode() == ArjunMode.INTERNAL) {
        paramDiscovery.scan(request, dictionary).thenAccept(...);
    } else {
        arjunIntegration.scan(request, dictionary).thenAccept(...);
    }
}
```

---

## 📊 工作流程详解

### 完整扫描流程

```
1. 稳定性探测 (Stability Probe)
   ├─ 发送2次随机参数请求
   │  └─ 例: ?zab123=abc456, ?zcd789=def012
   ├─ 对比响应，建立9种基线规则
   │  ├─ same_code: HTTP状态码
   │  ├─ same_body: 响应体
   │  ├─ same_headers: 响应头
   │  ├─ same_redirect: 重定向
   │  ├─ lines_num: 行数
   │  ├─ same_plaintext: 纯文本
   │  ├─ lines_diff: 差异行
   │  ├─ param_missing: 参数名反射
   │  └─ value_missing: 参数值反射
   └─ 发送第3次请求验证稳定性

2. 启发式提取 (Heuristic Extraction)
   ├─ 从响应中提取JSON字段名
   ├─ 提取HTML input name
   ├─ 提取URL参数
   └─ 添加到测试字典

3. 分块爆破 (Chunk Bruteforce)
   ├─ 将字典分成N组（默认250个/组）
   ├─ 每组参数一起发送
   │  └─ 例: ?admin=xyz&debug=xyz&test=xyz...
   ├─ 检测异常响应
   └─ 记录异常分块

4. 递归缩小 (Recursive Narrowing)
   ├─ 将异常分块再次细分
   ├─ 重复爆破过程
   └─ 直到缩小到单个参数

5. 最终验证 (Verification)
   ├─ 单独测试每个候选参数
   └─ 确认有效参数

6. 返回结果
   └─ DiscoveryResult{found=[admin, debug, ...]}
```

### 检测规则示例

#### 规则1: HTTP状态码变化
```
基线: GET /?random=xyz → 200 OK
测试: GET /?admin=xyz   → 403 Forbidden
结论: admin 参数有效（触发权限检查）
```

#### 规则2: 响应体内容变化
```
基线: {"error": "invalid"} (15 bytes)
测试: {"error": "invalid", "debug": true} (45 bytes)
结论: debug 参数有效（触发调试信息）
```

#### 规则3: 参数名反射
```
基线: 响应中不包含 "admin"
测试: 响应中出现 "admin parameter is restricted"
结论: admin 参数有效（参数名被反射）
```

---

## 🎯 使用场景

### 场景1: 手动端点测试

```java
// 用户在UI中手动添加端点
String manualUrl = "https://api.example.com/user/profile";
HttpRequest request = HttpRequest.httpRequestFromUrl(manualUrl);

// 使用已收集的参数 + 内置字典
Set<String> dictionary = new LinkedHashSet<>();
dictionary.addAll(parameterManager.getAllCollectedParams());
dictionary.addAll(DefaultDictionaries.COMMON_PARAMS);

// 启动扫描
paramDiscovery.scan(request, dictionary);
```

### 场景2: 自动流量扫描

```java
// RequestHandler中，当检测到新端点时
@Override
public void handleHttpRequest(HttpRequest request) {
    // ... 过滤逻辑
    
    if (shouldTriggerArjunScan(request)) {
        // 获取该端点的已知参数
        Set<String> knownParams = extractExistingParams(request);
        
        // 获取收集到的参数
        Set<String> collectedParams = parameterCollector.getParams(host, endpoint);
        
        // 合并字典
        Set<String> dictionary = new LinkedHashSet<>();
        dictionary.addAll(collectedParams);
        dictionary.addAll(DefaultDictionaries.MEDIUM);
        
        // 启动扫描
        paramDiscovery.scan(request, dictionary);
    }
}
```

### 场景3: 增量扫描

```java
// 只测试新收集到的参数
public void incrementalScan(HttpRequest request) {
    String host = extractHost(request);
    String endpoint = extractEndpoint(request);
    
    // 获取已扫描过的参数
    Set<String> scannedParams = parameterManager.getScannedParams(host, endpoint);
    
    // 获取新收集的参数
    Set<String> newParams = parameterCollector.getCollectedParams(host, endpoint);
    newParams.removeAll(scannedParams);
    
    if (!newParams.isEmpty()) {
        paramDiscovery.scan(request, newParams).thenAccept(result -> {
            // 记录已扫描
            parameterManager.markAsScanned(host, endpoint, newParams);
        });
    }
}
```

---

## 📋 配置选项

### ArjunConfig 配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|-------|-----|
| `enabled` | boolean | true | 是否启用参数发现 |
| `chunkSize` | int | 250 | 分块大小（10-1000） |
| `enableHeuristic` | boolean | true | 是否启用启发式提取 |
| `maxThreads` | int | 5 | 最大线程数（1-20） |
| `timeout` | int | 15 | 请求超时秒数（5-60） |

### 性能调优建议

| 场景 | 推荐配置 |
|-----|---------|
| **快速扫描** | chunkSize=500, enableHeuristic=false |
| **准确扫描** | chunkSize=100, enableHeuristic=true |
| **大字典** | chunkSize=500, maxThreads=10 |
| **慢速目标** | chunkSize=50, timeout=30 |

---

## 🔍 调试和日志

### 日志级别

引擎会输出详细的日志：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔍 参数发现开始: https://api.example.com/user
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 阶段1: 稳定性探测...
  发送基线请求1...
  发送基线请求2...
  发送稳定性验证请求...
  ✓ 目标稳定
  ✅ 基线规则建立: code=200, body=same (共2条规则)

🧠 阶段2: 启发式提取...
  ✓ 提取到 5 个候选参数

📚 字典大小: 150 个参数

🔄 阶段3: 分块爆破...
  分块数量: 3 (每块 250 个参数)
  ✓ 发现异常分块 1/3 (原因: body_content)
  进度: 3/3 (发现 1 个异常分块)
  第一轮完成: 1 个异常分块
    [深度1] 细分为 5 个子块 (每块 50 个)

✓ 阶段4: 最终验证 (12 个候选)...
  ✅ [1/12] 确认参数: admin (检测到: http_code)
  ✅ [2/12] 确认参数: debug (检测到: body_content)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ 参数发现完成: 发现 2 个参数 (耗时 3524ms)
  参数列表: [admin, debug]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🆚 对比：内置版 vs 外部版

| 特性 | Java内置版 | Python外部版 |
|-----|-----------|-------------|
| **跨平台兼容** | ✅ 完美 | ❌ Python依赖 |
| **macOS支持** | ✅ 无限制 | ❌ SIP限制 |
| **性能** | ✅ 原生速度 | 🟡 进程通信 |
| **集成度** | ✅ 深度集成 | ❌ 命令行调用 |
| **配置** | ✅ 统一配置 | ❌ 命令行参数 |
| **调试** | ✅ 统一日志 | ❌ 分离日志 |
| **去重** | ✅ 集成去重 | ❌ 无去重 |
| **字典管理** | ✅ 自动合并 | ❌ 手动管理 |
| **扩展性** | ✅ 易扩展 | ❌ 难扩展 |

---

## ⚡ 性能优化

### 1. 字典优化

```java
// 使用分级字典
public class DefaultDictionaries {
    public static final Set<String> SMALL = loadDictionary("small.txt");   // ~100个
    public static final Set<String> MEDIUM = loadDictionary("medium.txt"); // ~500个
    public static final Set<String> LARGE = loadDictionary("large.txt");   // ~2000个
}

// 根据场景选择
Set<String> dictionary;
if (isQuickScan) {
    dictionary = DefaultDictionaries.SMALL;
} else if (isThoroughScan) {
    dictionary = DefaultDictionaries.LARGE;
} else {
    dictionary = DefaultDictionaries.MEDIUM;
}
```

### 2. 并发控制

```java
// 使用线程池限制并发
ExecutorService executor = Executors.newFixedThreadPool(5);

List<CompletableFuture<DiscoveryResult>> futures = endpoints.stream()
    .map(request -> paramDiscovery.scan(request, dictionary))
    .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

### 3. 去重优化

```java
// 集成DeduplicationKeyGenerator
String dedupKey = DeduplicationKeyGenerator.generateKey(
    request.method(),
    request.url(),
    DeduplicationGranularity.PATH,
    // ...
);

if (scannedEndpoints.contains(dedupKey)) {
    return; // 跳过已扫描
}

scannedEndpoints.add(dedupKey);
paramDiscovery.scan(request, dictionary);
```

---

## 📝 下一步

### 待完成任务

- [ ] 集成到 `RealtimeScannerRefactored`
- [ ] 添加到 XProbe 配置管理
- [ ] UI控制面板
- [ ] 内置字典文件（small/medium/large）
- [ ] 测试和优化

### 快速集成步骤

1. **在 RealtimeScannerRefactored 中添加**
   ```java
   private ParamDiscoveryEngine paramDiscovery;
   ```

2. **初始化引擎**
   ```java
   this.paramDiscovery = new ParamDiscoveryEngine(api);
   ```

3. **替换现有的 Arjun 调用**
   ```java
   // 将所有 arjunIntegration.scan() 替换为
   paramDiscovery.scan(request, dictionary);
   ```

4. **删除旧的 ArjunIntegration 依赖**
   - 移除 Python 环境检测
   - 移除命令构建逻辑
   - 移除进程管理代码

---

## 🎉 总结

### ✅ 已实现的核心功能

1. **完整的Arjun算法**
   - 9种异常检测规则
   - 分块爆破 + 递归缩小
   - 最终单参数验证

2. **深度集成**
   - 使用Burp API发送请求
   - 统一的配置和日志
   - 无外部依赖

3. **高性能**
   - 纯Java实现
   - 异步执行
   - 智能分块

### 🚀 优势

- ✅ **跨平台** - 纯Java，任何系统都能运行
- ✅ **无限制** - 绕过macOS SIP限制
- ✅ **高性能** - 无进程通信开销
- ✅ **易维护** - 统一代码库
- ✅ **可扩展** - 易于添加新功能

---

**文档创建时间**: 2025-10-02  
**版本**: 1.0  
**状态**: ✅ 核心实现完成


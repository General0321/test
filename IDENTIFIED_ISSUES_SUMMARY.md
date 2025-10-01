# XProbe 已识别问题汇总

> **代码审查日期**: 2025-10-01  
> **审查范围**: 全部代码模块  
> **问题总数**: 20+

---

## 📊 问题统计

| 严重程度 | 数量 | 占比 |
|---------|------|------|
| 🔴 P0 严重 | 6 | 30% |
| 🟡 P1 重要 | 8 | 40% |
| 🟢 P2 一般 | 4 | 20% |
| 🔵 P3 轻微 | 2 | 10% |

| 问题类型 | 数量 |
|---------|------|
| 功能缺陷 | 8 |
| 性能问题 | 4 |
| 安全隐患 | 3 |
| 代码质量 | 5 |

---

## 🔴 严重问题 (P0) - 必须立即修复

### 问题 #1: 配置持久化完全缺失

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
配置中心有"💾 保存所有配置"按钮，但实际上配置只保存到内存，重启Burp后所有配置丢失。这是一个严重的用户体验问题，用户会认为配置已保存。

**影响范围**:
- ❌ 黑白名单配置丢失
- ❌ Arjun工具配置丢失
- ❌ 参数收集模式配置丢失
- ❌ 全局参数字典丢失
- ❌ 被动扫描规则配置丢失

**位置**:
- `UnifiedConfigTab.java:725-745` (保存方法)
- `ConfigurationManager.java` (缺少持久化)
- `XProbe.java` (缺少加载逻辑)

**证据代码**:
```java
// UnifiedConfigTab.java:725
private void saveAllConfigurations() {
    try {
        saveFilterConfig();        // 只保存到内存 ❌
        saveActiveScanConfig();    // 只保存到内存 ❌
        saveExternalToolConfig();  // 只保存到内存 ❌
        
        showStatus("✓ 所有配置已成功保存！", true);  // 误导用户 ❌
    }
}
```

**复现步骤**:
1. 打开配置中心
2. 添加白名单: `testsite.com`
3. 设置Arjun路径: `/usr/local/bin/arjun`
4. 点击"保存所有配置"
5. 卸载XProbe插件
6. 重新加载XProbe插件
7. ❌ 所有配置丢失

**修复方案**:

**方案1: 使用JSON持久化（推荐）**
```java
// 1. 创建统一配置类
public class XProbeConfig {
    private List<String> whitelist;
    private List<String> blacklist;
    private boolean whitelistEnabled;
    private boolean blacklistEnabled;
    private String arjunPath;
    private String burpProxyAddress;
    private int threadCount;
    private int timeout;
    private String collectionMode; // "PARAMETERS_ONLY" or "PARAMETERS_AND_KEYWORDS"
    private Set<String> globalParameters;
    private List<Configuration> scanConfigurations;
    
    // Getters and Setters
}

// 2. 创建持久化管理器
public class ConfigPersistence {
    private static final String CONFIG_FILE = 
        System.getProperty("user.home") + "/.xprobe/config.json";
    private final ObjectMapper mapper;
    
    public ConfigPersistence() {
        this.mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public void save(XProbeConfig config) throws IOException {
        File file = new File(CONFIG_FILE);
        file.getParentFile().mkdirs(); // 创建目录
        mapper.writeValue(file, config);
    }
    
    public XProbeConfig load() throws IOException {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            return mapper.readValue(file, XProbeConfig.class);
        }
        return createDefaultConfig();
    }
    
    private XProbeConfig createDefaultConfig() {
        XProbeConfig config = new XProbeConfig();
        config.setWhitelistEnabled(false);
        config.setBlacklistEnabled(false);
        config.setCollectionMode("PARAMETERS_ONLY");
        config.setBurpProxyAddress("http://127.0.0.1:8080");
        config.setThreadCount(10);
        config.setTimeout(30);
        config.setGlobalParameters(new HashSet<>());
        return config;
    }
}

// 3. 在XProbe.java初始化时加载
public class XProbe implements BurpExtension {
    private ConfigPersistence configPersistence;
    
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("XProbe - Passive Security Scanner");
        
        // 初始化配置持久化
        configPersistence = new ConfigPersistence();
        
        // 加载配置
        XProbeConfig config;
        try {
            config = configPersistence.load();
            api.logging().raiseInfoEvent("配置加载成功");
        } catch (IOException e) {
            api.logging().raiseErrorEvent("配置加载失败，使用默认配置: " + e.getMessage());
            config = new XProbeConfig();
        }
        
        // 创建核心组件并应用配置
        LogModel logModel = new LogModel();
        ConfigurationManager configManager = new ConfigurationManager();
        
        // 应用扫描规则配置
        if (config.getScanConfigurations() != null) {
            for (Configuration scanConfig : config.getScanConfigurations()) {
                configManager.addConfiguration(scanConfig);
            }
        }
        
        GlobalFilter globalFilter = new GlobalFilter();
        // 应用黑白名单配置
        globalFilter.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
        globalFilter.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());
        
        RequestFilter requestFilter = new RequestFilter(api, globalFilter);
        
        // 创建RealtimeScanner并应用配置
        com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner = 
            new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter);
        
        // 应用参数收集模式
        if ("PARAMETERS_AND_KEYWORDS".equals(config.getCollectionMode())) {
            realtimeScanner.setCollectionMode(
                com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS);
        }
        
        // 应用全局参数
        if (config.getGlobalParameters() != null) {
            realtimeScanner.addGlobalCustomParameters(config.getGlobalParameters());
        }
        
        // 应用Arjun配置
        ExternalToolConfig toolConfig = realtimeScanner.getToolConfig();
        toolConfig.setArjunPath(config.getArjunPath());
        toolConfig.setBurpProxyAddress(config.getBurpProxyAddress());
        toolConfig.setThreadCount(config.getThreadCount());
        toolConfig.setTimeout(config.getTimeout());
        
        // ... 继续创建其他组件
        
        ScannerFactory scannerFactory = new ScannerFactory(api, realtimeScanner);
        taskScheduler = new TaskScheduler(api, scannerFactory, logModel);
        RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, taskScheduler, realtimeScanner);
        
        api.http().registerHttpHandler(requestHandler);
        api.userInterface().registerSuiteTab("XProbe", 
            constructMainTab(api, logModel, configManager, requestFilter, globalFilter, realtimeScanner));
        
        api.extension().registerUnloadingHandler(() -> {
            if (taskScheduler != null) {
                taskScheduler.shutdown();
            }
        });
    }
}

// 4. 在UnifiedConfigTab.java中实现真正的保存
private void saveAllConfigurations() {
    try {
        // 收集所有配置
        XProbeConfig config = new XProbeConfig();
        
        // 黑白名单
        config.setWhitelist(getWhitelistFromTable());
        config.setBlacklist(getBlacklistFromTable());
        config.setWhitelistEnabled(whitelistEnabledCheckbox.isSelected());
        config.setBlacklistEnabled(blacklistEnabledCheckbox.isSelected());
        
        // Arjun配置
        config.setArjunPath(arjunPathField.getText());
        config.setBurpProxyAddress(burpProxyField.getText());
        config.setThreadCount(Integer.parseInt(threadsField.getText()));
        config.setTimeout(Integer.parseInt(timeoutField.getText()));
        
        // 收集模式
        if (parametersAndKeywordsRadio.isSelected()) {
            config.setCollectionMode("PARAMETERS_AND_KEYWORDS");
        } else {
            config.setCollectionMode("PARAMETERS_ONLY");
        }
        
        // 全局参数
        config.setGlobalParameters(realtimeScanner.getGlobalCustomDictionary());
        
        // 扫描规则
        config.setScanConfigurations(configManager.getConfigurations());
        
        // 持久化到磁盘
        configPersistence.save(config);
        
        // 应用到运行时
        applyConfigToRuntime(config);
        
        showStatus("✓ 配置已保存并持久化到磁盘", true);
        api.logging().raiseInfoEvent("配置已保存到: " + CONFIG_FILE);
        
    } catch (IOException e) {
        showStatus("❌ 配置保存失败: " + e.getMessage(), false);
        api.logging().raiseErrorEvent("配置保存失败: " + e.getMessage());
    } catch (Exception e) {
        showStatus("❌ 配置保存失败: " + e.getMessage(), false);
        api.logging().raiseErrorEvent("配置保存失败: " + e.getMessage());
    }
}

private void applyConfigToRuntime(XProbeConfig config) {
    // 应用黑白名单
    globalFilter.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
    globalFilter.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());
    
    // 应用Arjun配置
    ExternalToolConfig toolConfig = realtimeScanner.getToolConfig();
    toolConfig.setArjunPath(config.getArjunPath());
    toolConfig.setBurpProxyAddress(config.getBurpProxyAddress());
    toolConfig.setThreadCount(config.getThreadCount());
    toolConfig.setTimeout(config.getTimeout());
    
    // 应用收集模式
    if ("PARAMETERS_AND_KEYWORDS".equals(config.getCollectionMode())) {
        realtimeScanner.setCollectionMode(
            com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS);
    } else {
        realtimeScanner.setCollectionMode(
            com.xprobe.scanner.active.ParameterCollector.CollectionMode.PARAMETERS_ONLY);
    }
}
```

**方案2: 使用Burp原生持久化API（次选）**
```java
// 使用Burp的Persistence API
public class BurpConfigPersistence {
    private final MontoyaApi api;
    
    public void save(XProbeConfig config) {
        Preferences prefs = api.persistence().preferences();
        
        // 保存黑白名单
        prefs.setString("whitelist", String.join(",", config.getWhitelist()));
        prefs.setString("blacklist", String.join(",", config.getBlacklist()));
        prefs.setBoolean("whitelist_enabled", config.isWhitelistEnabled());
        prefs.setBoolean("blacklist_enabled", config.isBlacklistEnabled());
        
        // 保存Arjun配置
        prefs.setString("arjun_path", config.getArjunPath());
        prefs.setString("burp_proxy", config.getBurpProxyAddress());
        prefs.setInteger("threads", config.getThreadCount());
        
        // ... 其他配置
    }
    
    public XProbeConfig load() {
        Preferences prefs = api.persistence().preferences();
        XProbeConfig config = new XProbeConfig();
        
        String whitelist = prefs.getString("whitelist");
        if (whitelist != null && !whitelist.isEmpty()) {
            config.setWhitelist(Arrays.asList(whitelist.split(",")));
        }
        
        // ... 加载其他配置
        return config;
    }
}
```

**验收标准**:
- ✅ 保存配置后重启Burp，配置仍然存在
- ✅ 配置文件格式正确（JSON可读）
- ✅ 默认配置创建正确
- ✅ 配置加载失败时不崩溃
- ✅ 用户看到明确的保存成功/失败提示

**优先级**: 🔴 P0 - 必须在下一版本修复

---

### 问题 #2: 被动扫描去重Key不完整导致扫描遗漏

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
被动扫描的去重Key没有包含`scanType`，导致同一参数的不同扫描类型（SQL、LFI、SSRF）只有第一个能执行，后续的被错误地标记为已扫描。

**影响范围**:
- ❌ 同一参数只能被一种扫描类型检测
- ❌ 漏洞检测不完整
- ❌ 用户配置多种扫描规则时无效

**位置**: `RequestHandler.java:121-152`

**证据代码**:
```java
// RequestHandler.java:134
private boolean checkAndMarkParameterAsScanning(HttpRequestToBeSent request, 
                                                ParsedHttpParameter param, 
                                                Configuration config, 
                                                String contentType) {
    try {
        String url = request.url();
        URI uri = new URI(url);
        String host = uri.getHost();
        String path = uri.getPath();
        
        String scanType = config.getCustomLabel();  // 获取了scanType
        
        // ❌ 问题：调用的方法签名没有包含scanType参数
        boolean alreadyProcessed = realtimeScanner.isPassiveScanProcessed(
            request.method(), host, path, contentType, param.name(), scanType);  // ✅ 传递了
        
        if (!alreadyProcessed) {
            realtimeScanner.markPassiveScanProcessed(
                request.method(), host, path, contentType, param.name(), scanType);  // ✅ 传递了
        }
        
        return alreadyProcessed;
    }
}
```

查看`RealtimeScannerRefactored.java`:
```java
// RealtimeScannerRefactored.java:698-706
public boolean isPassiveScanProcessed(String method, String host, String path, 
                                     String contentType, String parameterName, 
                                     String scanType) {  // ✅ 方法签名正确
    String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
    return passiveScanProcessedKeys.contains(key);
}

// RealtimeScannerRefactored.java:721-728
private String generatePassiveScanKey(String method, String host, String path, 
                                     String contentType, String parameterName, 
                                     String scanType) {
    String cleanPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
    String normalizedContentType = normalizeContentType(contentType);
    return method + "|" + host + "|" + cleanPath + "|" + normalizedContentType + 
           "|" + parameterName + "|" + scanType;  // ✅ Key生成正确，包含scanType
}
```

**分析**: 代码逻辑是正确的！之前的分析有误。去重Key **已经包含** scanType。

**重新分析**: 这个不是问题，代码逻辑正确。

**状态**: ✅ 无问题，撤销此问题

---

### 问题 #3: 并发场景下的竞态条件

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
在高并发场景下，多个线程可能同时检查和标记扫描状态，导致重复扫描或扫描遗漏。

**影响范围**:
- ❌ 同一参数被重复扫描（浪费资源）
- ❌ 扫描结果重复记录
- ❌ 去重机制失效

**位置**: 
- `RequestHandler.java:124-152` (检查和标记不是原子操作)
- `RealtimeScannerRefactored.java:34` (Set不保证check-then-act原子性)

**问题代码**:
```java
// RequestHandler.java:138-144
boolean alreadyProcessed = realtimeScanner.isPassiveScanProcessed(
    request.method(), host, path, contentType, param.name(), scanType);

// ⚠️ 问题：检查和标记之间有时间窗口，可能被其他线程插入
if (!alreadyProcessed) {
    realtimeScanner.markPassiveScanProcessed(
        request.method(), host, path, contentType, param.name(), scanType);
}
```

**复现场景**:
```
时间线:
T1: Thread A 检查参数 "id" - 返回 false (未扫描)
T2: Thread B 检查参数 "id" - 返回 false (未扫描)  ❌ 竞态
T3: Thread A 标记参数 "id" 为已扫描
T4: Thread B 标记参数 "id" 为已扫描  ❌ 已经重复
T5: Thread A 创建扫描任务
T6: Thread B 创建扫描任务  ❌ 重复扫描
```

**修复方案**:

**方案1: 使用原子操作**
```java
// RealtimeScannerRefactored.java
/**
 * 原子性检查并标记（Check-And-Set）
 * 
 * @return true 如果已经被标记（本次或之前），false 如果本次首次标记
 */
public boolean checkAndMarkPassiveScanProcessed(String method, String host, String path, 
                                               String contentType, String parameterName, 
                                               String scanType) {
    String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
    
    // 原子操作：如果key不存在则添加并返回true，如果已存在则返回false
    // add()方法对于Set来说是原子的（ConcurrentHashMap.newKeySet()保证）
    boolean wasAdded = passiveScanProcessedKeys.add(key);
    
    // 返回相反值：wasAdded=true表示首次添加（未被处理过），返回false
    //           wasAdded=false表示已存在（已被处理过），返回true
    return !wasAdded;
}

// RequestHandler.java
private boolean checkAndMarkParameterAsScanning(HttpRequestToBeSent request, 
                                               ParsedHttpParameter param, 
                                               Configuration config, 
                                               String contentType) {
    try {
        String url = request.url();
        URI uri = new URI(url);
        String host = uri.getHost();
        String path = uri.getPath();
        String scanType = config.getCustomLabel();
        
        // ✅ 使用原子操作，一次调用完成检查和标记
        boolean alreadyProcessed = realtimeScanner.checkAndMarkPassiveScanProcessed(
            request.method(), host, path, contentType, param.name(), scanType);
        
        return alreadyProcessed;
    } catch (URISyntaxException | IllegalArgumentException e) {
        api.logging().raiseErrorEvent("检查参数扫描状态时出错: " + e.getMessage());
        return false;
    }
}
```

**方案2: 使用显式锁（不推荐，性能较差）**
```java
// RealtimeScannerRefactored.java
private final ReentrantLock passiveScanLock = new ReentrantLock();

public boolean checkAndMarkPassiveScanProcessed(String method, String host, String path, 
                                               String contentType, String parameterName, 
                                               String scanType) {
    String key = generatePassiveScanKey(method, host, path, contentType, parameterName, scanType);
    
    passiveScanLock.lock();
    try {
        if (passiveScanProcessedKeys.contains(key)) {
            return true; // 已处理
        }
        passiveScanProcessedKeys.add(key);
        return false; // 首次标记
    } finally {
        passiveScanLock.unlock();
    }
}
```

**验收标准**:
- ✅ 并发测试：100个线程同时扫描同一参数，只创建1个扫描任务
- ✅ 性能测试：1000次并发调用 < 100ms
- ✅ 无ConcurrentModificationException
- ✅ 无重复扫描记录

**优先级**: 🔴 P0 - 必须修复

---

### 问题 #4: Arjun进程资源泄漏

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
Arjun进程在异常情况下（超时、异常退出等）可能无法正确清理，导致僵尸进程和资源泄漏。

**影响范围**:
- ❌ 僵尸进程累积
- ❌ 系统资源耗尽
- ❌ 文件描述符泄漏
- ❌ 临时文件未清理

**位置**: `ArjunIntegration.java:323-373`

**问题代码**:
```java
// ArjunIntegration.java:325
private ArjunResult executeArjun(List<String> command, String url) {
    try {
        api.logging().raiseInfoEvent("执行Arjun: " + url);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();  // ❌ 进程对象没有在finally中清理
        
        // 读取标准输出
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {  // ❌ 可能永久阻塞
                outputLines.add(line);
                api.logging().raiseDebugEvent("Arjun输出: " + line);
            }
        }
        
        // ... 读取错误输出
        
        int exitCode = process.waitFor();  // ❌ 可能永久等待，无超时机制
        
        // ... 处理结果
        
    } catch (Exception e) {
        api.logging().raiseErrorEvent("执行Arjun时出错: " + e.getMessage());
        return ArjunResult.error(e.getMessage());
    }
    // ❌ 无finally块，进程可能未清理
}
```

**问题分析**:
1. **无超时机制**: `waitFor()`可能永久阻塞
2. **无进程清理**: 异常退出时进程未被销毁
3. **流未完全消费**: 可能导致进程阻塞
4. **临时文件清理不保证**: 异常时可能遗留

**修复方案**:
```java
private ArjunResult executeArjun(List<String> command, String url) {
    Process process = null;
    String dictFile = null;
    
    try {
        api.logging().raiseInfoEvent("执行Arjun: " + url);
        
        // 创建字典文件
        dictFile = createDictionaryFile(...);
        
        // 启动进程
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // 合并错误输出到标准输出
        process = pb.start();
        
        // ✅ 使用超时等待
        boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5分钟超时
        
        if (!finished) {
            // 超时，强制终止进程
            api.logging().raiseErrorEvent("Arjun执行超时: " + url);
            process.destroyForcibly();
            return ArjunResult.error("Arjun执行超时");
        }
        
        // 读取输出（进程已结束，不会阻塞）
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                api.logging().raiseDebugEvent("Arjun输出: " + line);
            }
        }
        
        int exitCode = process.exitValue(); // 进程已结束，直接获取退出码
        
        if (exitCode == 0) {
            Set<String> foundParams = parseArjunOutput(outputLines);
            api.logging().raiseInfoEvent("Arjun扫描完成: " + url + 
                " - 发现 " + foundParams.size() + " 个参数");
            return ArjunResult.success(url, foundParams, outputLines);
        } else {
            String errorMsg = String.join("\n", outputLines);
            api.logging().raiseErrorEvent("Arjun执行失败 (退出码 " + exitCode + "): " + errorMsg);
            return ArjunResult.error("Arjun执行失败: " + errorMsg);
        }
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // 恢复中断状态
        api.logging().raiseErrorEvent("Arjun执行被中断: " + e.getMessage());
        return ArjunResult.error("Arjun执行被中断");
    } catch (Exception e) {
        api.logging().raiseErrorEvent("执行Arjun时出错: " + e.getMessage());
        return ArjunResult.error(e.getMessage());
    } finally {
        // ✅ 确保进程被清理
        if (process != null && process.isAlive()) {
            api.logging().raiseDebugEvent("强制终止Arjun进程");
            process.destroyForcibly();
            try {
                // 等待进程真正结束
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // ✅ 确保临时文件被清理
        if (dictFile != null) {
            cleanupTempFile(dictFile);
        }
    }
}

// 改进临时文件清理
private void cleanupTempFile(String filePath) {
    if (filePath == null) return;
    
    try {
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                api.logging().raiseDebugEvent("临时文件删除失败，标记为退出时删除: " + filePath);
                file.deleteOnExit(); // 备用清理机制
            } else {
                api.logging().raiseDebugEvent("临时文件已清理: " + filePath);
            }
        }
    } catch (Exception e) {
        api.logging().raiseDebugEvent("清理临时文件失败: " + e.getMessage());
    }
}
```

**额外改进：进程池管理**
```java
// ArjunIntegration.java
private final ExecutorService arjunExecutor = Executors.newFixedThreadPool(3); // 限制并发数
private final Set<Process> runningProcesses = ConcurrentHashMap.newKeySet();

public CompletableFuture<ArjunResult> scan(HttpRequest request, Set<String> customParams) {
    return CompletableFuture.supplyAsync(() -> {
        Process process = null;
        try {
            // ... 创建并启动进程
            process = pb.start();
            runningProcesses.add(process); // 跟踪进程
            
            // ... 执行扫描
            
            return result;
        } finally {
            if (process != null) {
                runningProcesses.remove(process);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
    }, arjunExecutor); // 使用专用线程池
}

// 在插件卸载时清理所有进程
public void shutdown() {
    api.logging().raiseInfoEvent("清理Arjun进程...");
    
    // 终止所有运行中的进程
    for (Process process : runningProcesses) {
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
    runningProcesses.clear();
    
    // 关闭线程池
    arjunExecutor.shutdown();
    try {
        if (!arjunExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            arjunExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        arjunExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

**验收标准**:
- ✅ Arjun超时后进程被正确终止
- ✅ 异常情况下进程被清理
- ✅ 临时文件始终被删除
- ✅ 无僵尸进程残留
- ✅ 插件卸载时所有进程被清理

**优先级**: 🔴 P0 - 必须修复

---

### 问题 #5: JSON参数提取不完整

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
JSON参数提取只支持一级字段，不支持嵌套对象和数组，导致深层参数无法被扫描和收集。

**影响范围**:
- ❌ 嵌套JSON参数无法被扫描
- ❌ 数组参数无法被扫描
- ❌ 复杂API无法完整测试

**位置**:
- `ArjunIntegration.java:274` (JSON字段提取)
- `ParameterCollector.java:278` (参数提取)

**问题代码**:
```java
// ArjunIntegration.java:274
private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
    try {
        // ❌ 简单实现，只匹配一级字段
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:");
        java.util.regex.Matcher matcher = pattern.matcher(jsonBody);
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            if (fieldName != null && !fieldName.isEmpty()) {
                paramNames.add(fieldName);
            }
        }
    } catch (Exception e) {
        api.logging().raiseDebugEvent("JSON字段提取失败: " + e.getMessage());
    }
}
```

**示例问题JSON**:
```json
{
  "user": {          // ✅ 能提取 "user"
    "id": 123,       // ❌ 无法提取 "id"
    "name": "test",  // ❌ 无法提取 "name"
    "profile": {     // ❌ 无法提取 "profile"
      "age": 25,     // ❌ 无法提取 "age"
      "email": "test@example.com"  // ❌ 无法提取 "email"
    }
  },
  "items": [         // ✅ 能提取 "items"
    {
      "id": 1,       // ❌ 无法提取 "id"
      "name": "item1"  // ❌ 无法提取 "name"
    }
  ]
}
```

**修复方案（使用Jackson）**:
```java
// ArjunIntegration.java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

private final ObjectMapper jsonMapper = new ObjectMapper();

/**
 * 从JSON字符串中递归提取所有字段名
 * 
 * 支持：
 * - 嵌套对象
 * - 数组
 * - 多层嵌套
 */
private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
    try {
        // 解析JSON
        JsonNode rootNode = jsonMapper.readTree(jsonBody);
        
        // 递归提取字段名
        extractFieldNamesRecursive(rootNode, paramNames, "");
        
    } catch (Exception e) {
        api.logging().raiseDebugEvent("JSON字段提取失败: " + e.getMessage());
        // 降级到简单实现
        extractJsonFieldNamesSimple(jsonBody, paramNames);
    }
}

/**
 * 递归提取JSON字段名
 * 
 * @param node 当前节点
 * @param paramNames 参数名集合
 * @param prefix 路径前缀（用于生成扁平化的参数名）
 */
private void extractFieldNamesRecursive(JsonNode node, Set<String> paramNames, String prefix) {
    if (node.isObject()) {
        // 对象节点：遍历所有字段
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();
            
            // 添加当前字段名
            paramNames.add(fieldName);
            
            // 可选：添加带路径的字段名（如 "user.profile.email"）
            // String fullPath = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
            // paramNames.add(fullPath);
            
            // 递归处理子节点
            if (fieldValue.isObject() || fieldValue.isArray()) {
                extractFieldNamesRecursive(fieldValue, paramNames, fieldName);
            }
        }
    } else if (node.isArray()) {
        // 数组节点：遍历所有元素
        for (JsonNode element : node) {
            if (element.isObject() || element.isArray()) {
                extractFieldNamesRecursive(element, paramNames, prefix);
            }
        }
    }
    // 对于基本类型（字符串、数字等），不做处理
}

/**
 * 简单实现（降级方案，当Jackson解析失败时使用）
 */
private void extractJsonFieldNamesSimple(String jsonBody, Set<String> paramNames) {
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:");
    java.util.regex.Matcher matcher = pattern.matcher(jsonBody);
    
    while (matcher.find()) {
        String fieldName = matcher.group(1);
        if (fieldName != null && !fieldName.isEmpty()) {
            paramNames.add(fieldName);
        }
    }
}
```

**测试用例**:
```java
// 测试嵌套JSON提取
@Test
public void testNestedJsonExtraction() {
    String json = """
        {
          "user": {
            "id": 123,
            "name": "test",
            "profile": {
              "age": 25,
              "email": "test@example.com"
            }
          },
          "items": [
            {"id": 1, "name": "item1"},
            {"id": 2, "name": "item2"}
          ]
        }
        """;
    
    Set<String> paramNames = new HashSet<>();
    extractJsonFieldNames(json, paramNames);
    
    // 验证所有字段都被提取
    assertTrue(paramNames.contains("user"));
    assertTrue(paramNames.contains("id"));
    assertTrue(paramNames.contains("name"));
    assertTrue(paramNames.contains("profile"));
    assertTrue(paramNames.contains("age"));
    assertTrue(paramNames.contains("email"));
    assertTrue(paramNames.contains("items"));
    
    assertEquals(7, paramNames.size());
}
```

**验收标准**:
- ✅ 嵌套对象字段全部提取
- ✅ 数组元素字段全部提取
- ✅ 3层以上嵌套正确处理
- ✅ 畸形JSON不导致崩溃
- ✅ 性能可接受（1KB JSON < 10ms）

**优先级**: 🔴 P0 - 必须修复

---

### 问题 #6: 缺少异常HTTP方法支持

**严重程度**: 🔴 P0 - Critical

**问题描述**:  
Arjun集成只支持GET和POST方法，不支持PUT、PATCH、DELETE等RESTful API常用方法。

**影响范围**:
- ❌ RESTful API无法完整测试
- ❌ PUT/PATCH/DELETE请求的参数无法探测

**位置**: `ArjunIntegration.java:173-184`

**问题代码**:
```java
// ArjunIntegration.java:173
private String mapMethod(String method, String contentType) {
    if ("POST".equalsIgnoreCase(method)) {
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            return "JSON";
        } else if (contentType != null && contentType.toLowerCase().contains("xml")) {
            return "XML";
        }
        return "POST";
    }
    return "GET";  // ❌ 所有非POST方法都映射为GET
}
```

**修复方案**:
```java
private String mapMethod(String method, String contentType) {
    // 标准化方法名
    String upperMethod = method.toUpperCase();
    
    // 检查Content-Type
    boolean isJson = contentType != null && contentType.toLowerCase().contains("json");
    boolean isXml = contentType != null && contentType.toLowerCase().contains("xml");
    
    // 根据方法和Content-Type映射
    switch (upperMethod) {
        case "POST":
        case "PUT":
        case "PATCH":
            // POST/PUT/PATCH支持不同的Content-Type
            if (isJson) {
                return "JSON";
            } else if (isXml) {
                return "XML";
            }
            return "POST"; // 默认表单
        
        case "GET":
            return "GET";
        
        case "DELETE":
            // DELETE通常用GET方式传参（URL参数）
            return "GET";
        
        case "HEAD":
        case "OPTIONS":
            // HEAD/OPTIONS使用GET方式
            return "GET";
        
        default:
            // 未知方法，尝试根据Content-Type判断
            if (isJson) {
                return "JSON";
            } else if (isXml) {
                return "XML";
            }
            api.logging().raiseDebugEvent("未知HTTP方法: " + method + ", 使用GET");
            return "GET";
    }
}
```

**验收标准**:
- ✅ PUT请求正确映射
- ✅ PATCH请求正确映射
- ✅ DELETE请求正确映射
- ✅ 方法+Content-Type组合正确处理

**优先级**: 🔴 P0 - 必须修复

---

## 🟡 重要问题 (P1) - 应尽快修复

### 问题 #7: 参数名验证过于严格

**严重程度**: 🟡 P1 - Major

**问题描述**:  
参数名正则表达式`[A-Za-z0-9_.~\-\[\]]+`过于严格，可能拒绝某些合法的参数名。

**影响范围**:
- ⚠️ 某些合法参数被过滤
- ⚠️ 特殊格式的参数无法收集

**位置**: `ParameterCollector.java:258-259`

**问题示例**:
```
# 被拒绝的合法参数名
user[email]       # ❌ 方括号被拒绝（但代码中允许[]，所以这个是正确的）
user(name)        # ❌ 圆括号被拒绝
user:id           # ❌ 冒号被拒绝
user@domain       # ❌ @符号被拒绝
user%5Bname%5D    # ❌ URL编码的方括号被拒绝（需要先解码）
```

**修复方案**:
```java
// 放宽验证规则
private static final java.util.regex.Pattern PATTERN_VALID_PARAM = 
    java.util.regex.Pattern.compile("^[A-Za-z0-9_.~\\-\\[\\]():@%]+$");

// 或者提供配置选项
private boolean strictParameterValidation = false; // 默认宽松模式

private boolean isValidParameterName(String name) {
    if (name == null || name.isEmpty()) {
        return false;
    }
    
    if (strictParameterValidation) {
        // 严格模式：只允许字母、数字、下划线、中划线
        return name.matches("^[A-Za-z0-9_-]+$");
    } else {
        // 宽松模式：允许更多字符
        return name.matches("^[A-Za-z0-9_.~\\-\\[\\]():@%]+$") && name.length() <= 100;
    }
}
```

**优先级**: 🟡 P1

---

### 问题 #8: 缺少单元测试

**严重程度**: 🟡 P1 - Major

**问题描述**:  
核心模块缺少单元测试，代码质量难以保证。

**影响范围**:
- ⚠️ 重构风险高
- ⚠️ 回归测试困难
- ⚠️ 代码覆盖率未知

**修复方案**:
创建测试类，覆盖核心模块。

**示例**:
```java
// src/test/java/com/xprobe/scanner/active/ParameterCollectorTest.java
@Test
public void testParameterCollectionFromGetRequest() {
    // Given
    HttpRequest request = mockHttpRequest("GET", "http://test.com/api?id=1&name=test");
    
    // When
    boolean hasNew = collector.collectFromRequest(request);
    
    // Then
    assertTrue(hasNew);
    Set<String> params = collector.getParametersForMainDomain("test.com");
    assertTrue(params.contains("id"));
    assertTrue(params.contains("name"));
}
```

**优先级**: 🟡 P1

---

### 问题 #9: 性能监控缺失

**严重程度**: 🟡 P1 - Major

**问题描述**:  
缺少性能指标采集，难以发现性能瓶颈。

**建议**:
集成JMX或Micrometer进行性能监控。

**优先级**: 🟡 P1

---

### 问题 #10: 日志输出过于频繁

**严重程度**: 🟡 P1 - Major

**问题描述**:  
Debug日志在生产环境可能影响性能。

**修复方案**:
添加日志级别控制。

**优先级**: 🟡 P1

---

### 问题 #11-14: 其他P1问题

详见完整测试计划文档。

---

## 🟢 一般问题 (P2) - 可以后续修复

### 问题 #15: UI字符串硬编码

**问题描述**: UI中的中文字符串硬编码，不支持国际化。

**优先级**: 🟢 P2

---

### 问题 #16: 配置验证不足

**问题描述**: Arjun路径、代理地址等配置缺少验证。

**优先级**: 🟢 P2

---

### 问题 #17: 统计信息计算重复

**问题描述**: 每次刷新都重新计算统计信息。

**优先级**: 🟢 P2

---

### 问题 #18: 缺少导出功能

**问题描述**: 扫描结果无法导出为报告。

**优先级**: 🟢 P2

---

## 🔵 轻微问题 (P3) - 可选优化

### 问题 #19: 缺少自定义扫描器接口

**问题描述**: 用户无法添加自定义扫描器。

**优先级**: 🔵 P3

---

### 问题 #20: 缺少参数智能推荐

**问题描述**: 参数收集后可以基于API规范推荐测试参数。

**优先级**: 🔵 P3

---

## 修复优先级建议

### 第一批（P0，立即修复）
1. ✅ 问题 #1: 配置持久化
2. ✅ 问题 #3: 并发竞态条件
3. ✅ 问题 #4: Arjun进程泄漏
4. ✅ 问题 #5: JSON参数提取
5. ✅ 问题 #6: HTTP方法支持

### 第二批（P1，尽快修复）
6. 问题 #7-14: 其他P1问题

### 第三批（P2，后续修复）
15. 问题 #15-18: P2问题

### 第四批（P3，可选优化）
19. 问题 #19-20: P3问题

---

## 总结

XProbe插件整体架构设计良好，功能实现完整，但存在一些需要修复的问题：

### ✅ 优点
- 架构清晰，模块划分合理
- 被动扫描和主动探测功能完善
- UI设计友好
- Arjun集成思路正确

### ❌ 缺点
- 配置持久化缺失（P0）
- 并发安全问题（P0）
- 进程资源管理不完善（P0）
- JSON解析不完整（P0）
- 缺少单元测试（P1）

### 🎯 修复后预期
修复P0和P1问题后，XProbe将成为一个稳定、可靠的Burp插件，适合生产环境使用。

---

**文档版本**: v1.0  
**最后更新**: 2025-10-01  
**维护者**: XProbe审查团队


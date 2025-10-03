# XProbe 代码审计报告 - 严重问题分析

**审计时间**: 2025-10-02  
**审计范围**: 全部 Java 源代码  
**审计重点**: 资源泄漏、线程安全、死锁风险、异常处理

---

## 🔴 严重问题 (P0 - 必须修复)

### 1. ExecutorService 资源泄漏风险

**文件**: `TaskScheduler.java:191-193`

**问题描述**:
```java
public void shutdown() {
    executorService.shutdown();  // ❌ 只调用shutdown，没有等待任务完成
    api.logging().raiseInfoEvent("Task scheduler shutdown");
}
```

**风险**:
- `shutdown()` 不会立即停止已提交的任务
- 如果 Burp 关闭时有任务正在执行，可能导致:
  - 任务被强制中断（数据不一致）
  - 线程泄漏（JVM 无法正常退出）
  - HTTP 请求未正确完成

**影响**: 插件卸载时可能导致 Burp 无法正常关闭或内存泄漏

**建议修复**:
```java
public void shutdown() {
    executorService.shutdown();
    try {
        // 等待最多30秒让任务完成
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
            // 强制停止所有任务
            List<Runnable> pendingTasks = executorService.shutdownNow();
            api.logging().raiseInfoEvent(
                "强制终止了 " + pendingTasks.size() + " 个待执行任务"
            );
            
            // 再等待5秒确保线程停止
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                api.logging().raiseErrorEvent(
                    "⚠️ 部分线程可能未正确关闭！"
                );
            }
        }
    } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
    }
    api.logging().raiseInfoEvent("Task scheduler shutdown");
}
```

---

### 2. Process 和 Stream 资源泄漏

**文件**: `ArjunIntegration.java:625-667`

**问题描述**:
```java
private List<String> executeArjun(List<String> command) throws Exception {
    List<String> output = new ArrayList<>();
    Process process = null;
    
    try {
        process = pb.start();
        BufferedReader reader = new BufferedReader(  // ❌ reader 没有被关闭
            new InputStreamReader(process.getInputStream())
        );
        
        String line;
        while ((line = reader.readLine()) != null) {
            output.add(line);
        }
        // ...
    } finally {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        // ❌ reader 没有关闭，导致流泄漏
    }
}
```

**风险**:
- `BufferedReader` 和 `InputStreamReader` 未关闭
- 即使 `process.destroyForcibly()` 被调用，底层流可能仍未释放
- 大量调用 Arjun 会导致文件描述符耗尽

**影响**: 长时间运行可能导致 "Too many open files" 错误

**建议修复**:
```java
private List<String> executeArjun(List<String> command) throws Exception {
    List<String> output = new ArrayList<>();
    Process process = null;
    BufferedReader reader = null;
    
    try {
        process = pb.start();
        reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        
        String line;
        while ((line = reader.readLine()) != null) {
            output.add(line);
            if (config.isEnableVerboseOutput()) {
                api.logging().raiseDebugEvent("Arjun: " + line);
            }
        }
        
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        
        if (!finished) {
            throw new Exception("Arjun执行超时（5分钟）");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            api.logging().raiseErrorEvent("Arjun退出码: " + exitCode);
        }
        
        return output;
        
    } finally {
        // ✅ 关闭流资源
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // 记录但不抛出
                api.logging().raiseDebugEvent("关闭reader失败: " + e.getMessage());
            }
        }
        
        // ✅ 销毁进程
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

同样的问题也存在于:
- `ArjunIntegration.java:273` (tryCommand方法)
- `ArjunIntegration.java:947` (verifyPythonExecutable方法)  
- `ArjunIntegration.java:1069` (findArjunUsingSystemCommand方法)

**统一修复建议**: 所有这些方法都应该使用 try-with-resources

---

### 3. LogModel 性能瓶颈（过度同步）

**文件**: `LogModel.java:39-172`

**问题描述**:
```java
public synchronized int getRowCount() { ... }
public synchronized Object getValueAt(int rowIndex, int columnIndex) { ... }
public synchronized void add(...) { ... }
public synchronized void clear() { ... }
// ... 所有方法都是 synchronized
```

**风险**:
- `getValueAt()` 是 Swing UI 线程频繁调用的方法
- 如果在 `add()` 时持有锁，UI 会卡顿
- 大量扫描结果同时写入时，会形成锁竞争

**影响**: UI 响应变慢，特别是在扫描结果快速增长时

**建议修复**: 使用 `CopyOnWriteArrayList` 或者读写锁
```java
private final List<LogEntry> log = new CopyOnWriteArrayList<>();

@Override
public int getRowCount() {
    return log.size();  // 不需要锁
}

@Override
public Object getValueAt(int rowIndex, int columnIndex) {
    try {
        LogEntry entry = log.get(rowIndex);  // 不需要锁
        // ...
    } catch (IndexOutOfBoundsException e) {
        return "";
    }
}

public void add(...) {
    synchronized (this) {  // 只在修改时加锁
        if (log.size() >= maxEntries.get()) {
            log.remove(0);
        }
        int index = log.size();
        log.add(new LogEntry(...));
        
        // ✅ 在锁外触发UI更新
        SwingUtilities.invokeLater(() -> {
            fireTableRowsInserted(index, index);
        });
    }
}
```

---

## ⚠️ 中等问题 (P1 - 建议修复)

### 4. 无限循环风险

**文件**: `UniversalScanner.java:1001-1011`

**问题描述**:
```java
while (expr.contains("(")) {
    int start = expr.lastIndexOf('(');
    int end = expr.indexOf(')', start);
    if (end == -1) {
        throw new IllegalArgumentException("括号不匹配");
    }
    
    String subExpr = expr.substring(start + 1, end);
    boolean subResult = evaluateBooleanExpression(subExpr);  // ❌ 递归调用
    expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
}
```

**风险**:
- 如果用户输入嵌套过深的表达式，可能导致 StackOverflowError
- 递归深度没有限制
- 格式错误的表达式可能导致死循环

**建议修复**: 添加递归深度检查
```java
private static final int MAX_RECURSION_DEPTH = 10;

private boolean evaluateBooleanExpression(String expr) {
    return evaluateBooleanExpression(expr, 0);
}

private boolean evaluateBooleanExpression(String expr, int depth) {
    if (depth > MAX_RECURSION_DEPTH) {
        throw new IllegalArgumentException(
            "表达式嵌套过深（最大: " + MAX_RECURSION_DEPTH + "）"
        );
    }
    
    expr = expr.trim();
    
    // 处理括号
    int iterations = 0;
    while (expr.contains("(")) {
        if (++iterations > 100) {  // 防止死循环
            throw new IllegalArgumentException("表达式格式错误（括号处理异常）");
        }
        
        int start = expr.lastIndexOf('(');
        int end = expr.indexOf(')', start);
        if (end == -1) {
            throw new IllegalArgumentException("括号不匹配");
        }
        
        String subExpr = expr.substring(start + 1, end);
        boolean subResult = evaluateBooleanExpression(subExpr, depth + 1);
        expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
    }
    // ...
}
```

---

### 5. CompletableFuture 异常处理不完整

**文件**: `TaskScheduler.java:57-61`

**问题描述**:
```java
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .exceptionally(ex -> {
        api.logging().raiseErrorEvent("Error during batch scan: " + ex.getMessage());
        return null;  // ❌ 返回null但没有后续处理
    });
    // ❌ 没有调用 .join() 或 .get()，异常可能被忽略
```

**风险**:
- CompletableFuture 的异常可能被静默吞掉
- 没有等待任务完成，可能导致资源提前释放

**建议修复**:
```java
public void scheduleScan(List<ScanTask> tasks) {
    if (tasks.isEmpty()) {
        return;
    }
    
    List<CompletableFuture<Void>> futures = tasks.stream()
        .map(task -> CompletableFuture.runAsync(
            () -> executeScanTask(task), 
            executorService
        ))
        .collect(java.util.stream.Collectors.toList());
    
    // ✅ 使用 whenComplete 而不是 exceptionally
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .whenComplete((result, throwable) -> {
            if (throwable != null) {
                api.logging().raiseErrorEvent(
                    "批量扫描时发生错误: " + throwable.getMessage()
                );
                throwable.printStackTrace();
            } else {
                api.logging().raiseDebugEvent(
                    "批量扫描完成: " + tasks.size() + " 个任务"
                );
            }
        });
}
```

---

### 6. 临时文件清理不可靠

**文件**: `ArjunIntegration.java:56-58`

**问题描述**:
```java
// 程序退出时删除
wrapperFile.deleteOnExit();  // ❌ 只在JVM正常退出时才删除
```

**风险**:
- 如果 Burp 崩溃，临时文件不会被清理
- 如果创建大量 wrapper 文件，可能导致磁盘空间浪费

**建议修复**:
```java
// 1. 使用固定文件名（不用每次创建新的）
private static final String WRAPPER_FILE_PATH = 
    System.getProperty("java.io.tmpdir") + "/xprobe-arjun-wrapper.sh";

private String createArjunWrapper(String arjunPath) {
    if (cachedWrapperPath != null) {
        File wrapper = new File(cachedWrapperPath);
        if (wrapper.exists() && wrapper.canExecute()) {
            return cachedWrapperPath;
        }
    }
    
    try {
        File wrapperFile = new File(WRAPPER_FILE_PATH);
        
        // 如果已存在，先删除
        if (wrapperFile.exists()) {
            wrapperFile.delete();
        }
        
        // ... 写入内容 ...
        
        wrapperFile.setExecutable(true, false);
        
        // ✅ 注册 shutdown hook 确保清理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (wrapperFile.exists()) {
                    wrapperFile.delete();
                }
            } catch (Exception e) {
                // Ignore
            }
        }));
        
        cachedWrapperPath = wrapperFile.getAbsolutePath();
        return cachedWrapperPath;
        
    } catch (Exception e) {
        api.logging().raiseDebugEvent("⚠️ 创建 wrapper 脚本失败: " + e.getMessage());
        return null;
    }
}
```

---

## 📝 轻微问题 (P2 - 可选修复)

### 7. 正则表达式编译性能

**文件**: 多处正则匹配（如 `UniversalScanner.java:827`）

**问题**: 每次匹配都重新编译正则表达式

**建议**: 使用 Pattern 缓存
```java
private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

private boolean matchesRegex(String value, String regex) {
    Pattern pattern = patternCache.computeIfAbsent(regex, r -> {
        try {
            return Pattern.compile(r);
        } catch (PatternSyntaxException e) {
            return null;
        }
    });
    
    if (pattern == null) {
        return false;
    }
    
    return pattern.matcher(value).find();
}
```

---

### 8. 字典文件清理时机

**文件**: `ArjunIntegration.java:186-192`

**问题**: 在 `finally` 块中删除临时字典文件，如果 Arjun 执行失败，文件会立即被删除

**建议**: 改为在扫描成功后再删除，或者添加延迟删除

---

## ✅ 良好实践

1. **并发控制**: 使用了 `ConcurrentHashMap.newKeySet()` 和 `volatile` 变量
2. **配置管理**: 使用了缓存机制避免频繁磁盘 IO
3. **去重机制**: 统一的去重逻辑，避免重复扫描
4. **异常日志**: 大部分异常都有记录

---

## 🎯 修复优先级总结

| 问题 | 严重程度 | 影响范围 | 修复难度 | 优先级 |
|------|---------|---------|---------|--------|
| ExecutorService 泄漏 | P0 | 插件卸载 | 简单 | **立即修复** |
| Process 流泄漏 | P0 | 长时间运行 | 简单 | **立即修复** |
| LogModel 过度同步 | P1 | UI 性能 | 中等 | 建议修复 |
| 无限循环风险 | P1 | 表达式评估 | 简单 | 建议修复 |
| CompletableFuture 异常 | P1 | 错误处理 | 简单 | 建议修复 |
| 临时文件清理 | P2 | 磁盘空间 | 简单 | 可选修复 |

---

## 📊 代码质量评分

- **资源管理**: 6/10 (存在泄漏风险)
- **线程安全**: 8/10 (使用了正确的并发工具)
- **异常处理**: 7/10 (记录了但恢复不足)
- **性能优化**: 7/10 (有缓存机制但存在瓶颈)
- **代码可维护性**: 8/10 (结构清晰，注释详细)

**综合评分**: 7.2/10

---

## 💡 建议

1. **立即修复 P0 问题**，否则可能导致严重的资源泄漏
2. **添加单元测试**，特别是针对并发场景和资源清理
3. **添加压力测试**，模拟大量并发扫描，检查内存和线程泄漏
4. **考虑添加监控**，记录活跃线程数、打开的文件描述符数等

---

**审计员**: AI Code Auditor  
**审计完成时间**: 2025-10-02


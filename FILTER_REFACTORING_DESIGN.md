# 🔧 过滤器重构设计方案

**设计日期**：2025-10-02  
**需求**：将白名单/黑名单和去重逻辑集中管理  
**目标**：提升代码可维护性、可测试性和可扩展性

---

## 📊 当前问题分析

### 当前分散的逻辑

**白名单/黑名单**：
- `GlobalFilter.java` - 存储白名单/黑名单规则
- `RequestFilter.java` - 调用白名单/黑名单检查

**去重逻辑**：
- `RequestFilter.java` - 请求hash去重（粗粒度）
- `RealtimeScannerRefactored.java` - 存储去重key
- `UniversalScanner.java` - 去重过滤逻辑（细粒度）
- `DeduplicationKeyGenerator.java` - 生成去重key

**问题**：
- ❌ 逻辑分散在4-5个类中
- ❌ 职责不清晰
- ❌ 难以测试
- ❌ 修改需要动多个文件

---

## ✅ 重构方案对比

### 方案1：单一过滤器类（不推荐）

**设计**：
```java
public class ScanFilter {
    // 白名单/黑名单
    private GlobalFilter globalFilter;
    
    // 请求去重
    private Set<Integer> processedRequests;
    
    // 目标去重
    private Set<String> processedTargets;
    
    public boolean shouldScan(HttpRequest request, Configuration config, String targetId) {
        // 检查白名单/黑名单
        // 检查请求去重
        // 检查目标去重
    }
}
```

**优点**：
- ✅ 简单直接
- ✅ 统一入口

**缺点**：
- ❌ 违反单一职责原则（一个类做太多事）
- ❌ 难以测试（必须测试所有逻辑）
- ❌ 难以扩展（添加新过滤器需要修改类）

**评分**：⭐⭐ (2/5)

---

### 方案2：独立过滤器类（推荐）

**设计**：
```java
// 白名单/黑名单过滤器
public class BlackWhiteListFilter {
    public boolean shouldProcess(String url);
}

// 请求去重过滤器
public class RequestDeduplicationFilter {
    public boolean isDuplicate(HttpRequest request);
    public void markAsProcessed(HttpRequest request);
}

// 目标去重过滤器
public class TargetDeduplicationFilter {
    public boolean isDuplicate(String key);
    public void markAsProcessed(String key);
}

// 统一调用
public class RequestHandler {
    private BlackWhiteListFilter blackWhiteListFilter;
    private RequestDeduplicationFilter requestDeduplicationFilter;
    
    public void handle(HttpRequest request) {
        if (!blackWhiteListFilter.shouldProcess(request.url())) return;
        if (requestDeduplicationFilter.isDuplicate(request)) return;
        // ...
    }
}
```

**优点**：
- ✅ 单一职责
- ✅ 易于测试
- ✅ 易于扩展

**缺点**：
- ⚠️ 需要协调多个类
- ⚠️ 调用代码稍微复杂

**评分**：⭐⭐⭐⭐ (4/5)

---

### 方案3：责任链模式（最推荐）

**设计**：
```java
// 过滤器接口
public interface ScanFilter {
    FilterResult filter(FilterContext context);
    void setNext(ScanFilter next);
}

// 过滤器链
public class FilterChain {
    private ScanFilter head;
    
    public FilterChain addFilter(ScanFilter filter) {
        // 构建链
    }
    
    public FilterResult execute(FilterContext context) {
        return head.filter(context);
    }
}

// 具体过滤器
public class BlackWhiteListFilter implements ScanFilter {
    public FilterResult filter(FilterContext context) {
        if (!shouldProcess(context.getUrl())) {
            return FilterResult.reject("Blocked by blacklist/whitelist");
        }
        return next.filter(context);  // 传递给下一个
    }
}

public class RequestDeduplicationFilter implements ScanFilter {
    public FilterResult filter(FilterContext context) {
        if (isDuplicate(context.getRequest())) {
            return FilterResult.reject("Duplicate request");
        }
        markAsProcessed(context.getRequest());
        return next.filter(context);
    }
}

public class TargetDeduplicationFilter implements ScanFilter {
    public FilterResult filter(FilterContext context) {
        // 过滤重复的目标
        List<InjectionTarget> filtered = filterDuplicates(context.getTargets());
        context.setTargets(filtered);
        return next.filter(context);
    }
}

// 使用
FilterChain chain = new FilterChain()
    .addFilter(new BlackWhiteListFilter())
    .addFilter(new RequestDeduplicationFilter())
    .addFilter(new TargetDeduplicationFilter());

FilterResult result = chain.execute(new FilterContext(request));
```

**优点**：
- ✅✅ 单一职责
- ✅✅ 易于扩展（添加/删除/调整顺序）
- ✅✅ 易于测试
- ✅ 符合开闭原则
- ✅ 代码优雅

**缺点**：
- ⚠️ 需要更多的类
- ⚠️ 初次理解稍复杂

**评分**：⭐⭐⭐⭐⭐ (5/5)

---

### 方案4：Facade模式（推荐）

**设计**：
```java
// Facade类：统一入口
public class ScanFilterFacade {
    private final BlackWhiteListFilter blackWhiteListFilter;
    private final RequestDeduplicationFilter requestDeduplicationFilter;
    private final TargetDeduplicationFilter targetDeduplicationFilter;
    
    public ScanFilterFacade(...) {
        // 依赖注入
    }
    
    /**
     * 全局过滤：检查请求是否应该被扫描
     */
    public boolean shouldScanRequest(HttpRequest request) {
        // 1. 检查白名单/黑名单
        if (!blackWhiteListFilter.shouldProcess(request.url())) {
            return false;
        }
        
        // 2. 检查请求去重
        if (requestDeduplicationFilter.isDuplicate(request)) {
            return false;
        }
        
        // 3. 标记请求为已处理
        requestDeduplicationFilter.markAsProcessed(request);
        
        return true;
    }
    
    /**
     * 目标过滤：过滤重复的注入目标
     */
    public List<InjectionTarget> filterTargets(List<InjectionTarget> targets, 
                                               HttpRequest request, 
                                               Configuration config) {
        return targetDeduplicationFilter.filter(targets, request, config);
    }
    
    /**
     * 标记目标为已处理
     */
    public void markTargetAsProcessed(String dedupKey) {
        targetDeduplicationFilter.markAsProcessed(dedupKey);
    }
}

// 使用
public class RequestHandler {
    private final ScanFilterFacade scanFilter;
    
    public void handle(HttpRequest request) {
        // ✅ 统一入口，简单调用
        if (!scanFilter.shouldScanRequest(request)) {
            return;
        }
        // ...
    }
}

public class UniversalScanner {
    private final ScanFilterFacade scanFilter;
    
    public void scan(ScanTask task) {
        // ✅ 统一入口，简单调用
        List<InjectionTarget> validTargets = scanFilter.filterTargets(
            allTargets, request, config
        );
        // ...
    }
}
```

**优点**：
- ✅✅ 统一入口，简单易用
- ✅✅ 内部保持单一职责
- ✅✅ 易于测试（可以mock各个子过滤器）
- ✅ 调用代码简洁
- ✅ 易于维护

**缺点**：
- ⚠️ 需要额外的Facade类

**评分**：⭐⭐⭐⭐⭐ (5/5)

---

## 🎯 推荐方案：方案4（Facade模式）

### 为什么选择Facade模式？

1. **简单易用**：调用代码简洁，不需要理解责任链
2. **职责分离**：内部各个过滤器职责单一
3. **易于测试**：可以分别测试各个过滤器
4. **渐进式重构**：可以逐步迁移现有代码
5. **团队友好**：容易理解和维护

---

## 📝 详细实现方案（Facade模式）

### 第1步：创建独立的过滤器接口和实现

#### 1.1 黑白名单过滤器

**文件**：`src/main/java/com/xprobe/scanner/core/filter/BlackWhiteListFilter.java`

```java
package com.xprobe.scanner.core.filter;

import com.xprobe.scanner.core.GlobalFilter;
import java.util.List;

/**
 * 黑白名单过滤器
 * 
 * 职责：
 * - 检查URL是否在白名单中（如果启用）
 * - 检查URL是否在黑名单中（如果启用）
 * - 管理白名单/黑名单规则
 */
public class BlackWhiteListFilter {
    
    private final GlobalFilter globalFilter;
    
    public BlackWhiteListFilter(GlobalFilter globalFilter) {
        this.globalFilter = globalFilter;
    }
    
    /**
     * 检查URL是否应该被处理
     * 
     * @param url 请求URL
     * @return true=应该处理，false=应该拒绝
     */
    public boolean shouldProcess(String url) {
        return globalFilter.shouldProcessPassive(url);
    }
    
    /**
     * 更新白名单
     */
    public void updateWhitelist(List<String> whitelist, boolean enabled) {
        globalFilter.updateWhitelist(whitelist, enabled);
    }
    
    /**
     * 更新黑名单
     */
    public void updateBlacklist(List<String> blacklist, boolean enabled) {
        globalFilter.updateBlacklist(blacklist, enabled);
    }
}
```

---

#### 1.2 请求去重过滤器

**文件**：`src/main/java/com/xprobe/scanner/core/filter/RequestDeduplicationFilter.java`

```java
package com.xprobe.scanner.core.filter;

import burp.api.montoya.http.message.requests.HttpRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 请求去重过滤器
 * 
 * 职责：
 * - 检查请求是否已经处理过（基于请求特征）
 * - 标记请求为已处理
 * - 维护已处理请求的缓存（LRU）
 */
public class RequestDeduplicationFilter {
    
    // LRU缓存，限制最大10000个条目
    private final Set<String> processedRequests = Collections.synchronizedSet(
        Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 10000;
                }
            }
        )
    );
    
    /**
     * 检查请求是否已经处理过
     * 
     * @param request HTTP请求
     * @return true=已处理（重复），false=未处理
     */
    public boolean isDuplicate(HttpRequest request) {
        String requestKey = generateRequestKey(request);
        return processedRequests.contains(requestKey);
    }
    
    /**
     * 标记请求为已处理
     * 
     * @param request HTTP请求
     */
    public void markAsProcessed(HttpRequest request) {
        String requestKey = generateRequestKey(request);
        processedRequests.add(requestKey);
    }
    
    /**
     * 生成稳定的请求key
     */
    private String generateRequestKey(HttpRequest request) {
        // ✅ 使用稳定的特征生成key
        return String.format("%s|%s|%s|%d",
            request.method(),
            request.url(),
            request.bodyToString(),
            request.headers().hashCode()
        );
    }
    
    /**
     * 清空缓存
     */
    public void clear() {
        processedRequests.clear();
    }
    
    /**
     * 获取缓存大小
     */
    public int size() {
        return processedRequests.size();
    }
}
```

---

#### 1.3 目标去重过滤器

**文件**：`src/main/java/com/xprobe/scanner/core/filter/TargetDeduplicationFilter.java`

```java
package com.xprobe.scanner.core.filter;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.core.DeduplicationKeyGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标去重过滤器
 * 
 * 职责：
 * - 根据配置的去重颗粒度过滤重复的注入目标
 * - 标记目标为已处理
 * - 维护已处理目标的集合
 * 
 * 去重颗粒度支持：
 * - GLOBAL: 整个规则只测试一次
 * - HOST: 每个主机只测试一次
 * - PATH: 每个路径只测试一次
 * - REQUEST: 每个完整请求只测试一次
 * - PARAMETER_NAME_GLOBAL: 相同参数名只测试一次
 * - PARAMETER_NAME_PER_PATH: 每个路径下的参数名分别测试
 * - PARAMETER: 每个请求中的参数分别扫描
 * - INJECTION_POINT: 每个注入点分别扫描
 * - NONE: 无去重（Fuzzing模式）
 */
public class TargetDeduplicationFilter {
    
    private final Set<String> processedTargets = ConcurrentHashMap.newKeySet();
    
    /**
     * 过滤重复的目标
     * 
     * @param targets 所有目标
     * @param request HTTP请求
     * @param config 扫描配置（包含去重颗粒度）
     * @return 未处理过的目标列表
     */
    public <T extends Deduplicatable> List<T> filter(List<T> targets, 
                                                      HttpRequest request, 
                                                      Configuration config) {
        // 提取请求信息
        String method = request.method();
        String host = request.httpService().host();
        String path = request.path();
        String contentType = extractContentType(request);
        
        List<T> validTargets = new ArrayList<>();
        
        for (T target : targets) {
            // 生成去重key（根据配置的颗粒度）
            String dedupKey = DeduplicationKeyGenerator.generateKey(
                method, host, path, contentType, config, target.getTargetIdentifier()
            );
            
            // 检查是否重复
            if (!processedTargets.contains(dedupKey)) {
                // 保存key到target（后续标记时使用）
                target.setDedupKey(dedupKey);
                validTargets.add(target);
            }
        }
        
        return validTargets;
    }
    
    /**
     * 检查目标是否已处理
     * 
     * @param dedupKey 去重key
     * @return true=已处理，false=未处理
     */
    public boolean isDuplicate(String dedupKey) {
        return processedTargets.contains(dedupKey);
    }
    
    /**
     * 标记目标为已处理
     * 
     * @param dedupKey 去重key
     */
    public void markAsProcessed(String dedupKey) {
        if (dedupKey != null && !dedupKey.isEmpty()) {
            processedTargets.add(dedupKey);
        }
    }
    
    /**
     * 提取Content-Type
     */
    private String extractContentType(HttpRequest request) {
        return request.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 清空缓存
     */
    public void clear() {
        processedTargets.clear();
    }
    
    /**
     * 获取缓存大小
     */
    public int size() {
        return processedTargets.size();
    }
}

/**
 * 可去重的目标接口
 */
public interface Deduplicatable {
    String getTargetIdentifier();
    void setDedupKey(String key);
}
```

---

### 第2步：创建Facade类

**文件**：`src/main/java/com/xprobe/scanner/core/filter/ScanFilterFacade.java`

```java
package com.xprobe.scanner.core.filter;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import java.util.List;

/**
 * 扫描过滤器Facade
 * 
 * 职责：
 * - 提供统一的过滤入口
 * - 协调各个子过滤器
 * - 简化调用代码
 * 
 * 设计模式：Facade（外观模式）
 * 
 * 优势：
 * - 统一入口：调用代码简洁
 * - 职责分离：内部各过滤器单一职责
 * - 易于测试：可以mock各个子过滤器
 * - 易于维护：逻辑集中，修改方便
 */
public class ScanFilterFacade {
    
    private final BlackWhiteListFilter blackWhiteListFilter;
    private final RequestDeduplicationFilter requestDeduplicationFilter;
    private final TargetDeduplicationFilter targetDeduplicationFilter;
    
    /**
     * 构造函数（依赖注入）
     */
    public ScanFilterFacade(BlackWhiteListFilter blackWhiteListFilter,
                           RequestDeduplicationFilter requestDeduplicationFilter,
                           TargetDeduplicationFilter targetDeduplicationFilter) {
        this.blackWhiteListFilter = blackWhiteListFilter;
        this.requestDeduplicationFilter = requestDeduplicationFilter;
        this.targetDeduplicationFilter = targetDeduplicationFilter;
    }
    
    /**
     * 全局过滤：检查请求是否应该被扫描
     * 
     * 执行顺序：
     * 1. 检查白名单/黑名单（全局规则）
     * 2. 检查请求去重（性能优化）
     * 3. 标记请求为已处理
     * 
     * @param request HTTP请求
     * @return true=应该扫描，false=应该拒绝
     */
    public boolean shouldScanRequest(HttpRequest request) {
        // 1. 检查白名单/黑名单
        if (!blackWhiteListFilter.shouldProcess(request.url())) {
            return false;
        }
        
        // 2. 检查请求去重
        if (requestDeduplicationFilter.isDuplicate(request)) {
            return false;
        }
        
        // 3. 标记请求为已处理
        requestDeduplicationFilter.markAsProcessed(request);
        
        return true;
    }
    
    /**
     * 目标过滤：过滤重复的注入目标
     * 
     * 根据配置的去重颗粒度过滤：
     * - GLOBAL: 整个规则只测试一次
     * - HOST: 每个主机只测试一次
     * - PATH: 每个路径只测试一次
     * - PARAMETER_NAME_GLOBAL: 相同参数名只测试一次
     * - ... 等8种颗粒度
     * 
     * @param targets 所有目标
     * @param request HTTP请求
     * @param config 扫描配置
     * @return 未处理过的目标列表
     */
    public <T extends Deduplicatable> List<T> filterTargets(List<T> targets, 
                                                            HttpRequest request, 
                                                            Configuration config) {
        return targetDeduplicationFilter.filter(targets, request, config);
    }
    
    /**
     * 标记目标为已处理
     * 
     * 在真正发送请求并注入payload后调用
     * 
     * @param dedupKey 去重key
     */
    public void markTargetAsProcessed(String dedupKey) {
        targetDeduplicationFilter.markAsProcessed(dedupKey);
    }
    
    /**
     * 检查目标是否已处理
     * 
     * @param dedupKey 去重key
     * @return true=已处理，false=未处理
     */
    public boolean isTargetDuplicate(String dedupKey) {
        return targetDeduplicationFilter.isDuplicate(dedupKey);
    }
    
    /**
     * 更新白名单
     */
    public void updateWhitelist(List<String> whitelist, boolean enabled) {
        blackWhiteListFilter.updateWhitelist(whitelist, enabled);
    }
    
    /**
     * 更新黑名单
     */
    public void updateBlacklist(List<String> blacklist, boolean enabled) {
        blackWhiteListFilter.updateBlacklist(blacklist, enabled);
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAll() {
        requestDeduplicationFilter.clear();
        targetDeduplicationFilter.clear();
    }
    
    /**
     * 获取统计信息
     */
    public FilterStats getStats() {
        return new FilterStats(
            requestDeduplicationFilter.size(),
            targetDeduplicationFilter.size()
        );
    }
    
    /**
     * 过滤器统计信息
     */
    public static class FilterStats {
        public final int processedRequests;
        public final int processedTargets;
        
        public FilterStats(int processedRequests, int processedTargets) {
            this.processedRequests = processedRequests;
            this.processedTargets = processedTargets;
        }
    }
}
```

---

### 第3步：重构调用代码

#### 3.1 XProbe.java（初始化）

```java
@Override
public void initialize(MontoyaApi api) {
    // ... existing code ...
    
    // ✅ 创建过滤器组件
    GlobalFilter globalFilter = new GlobalFilter();
    BlackWhiteListFilter blackWhiteListFilter = new BlackWhiteListFilter(globalFilter);
    RequestDeduplicationFilter requestDeduplicationFilter = new RequestDeduplicationFilter();
    TargetDeduplicationFilter targetDeduplicationFilter = new TargetDeduplicationFilter();
    
    // ✅ 创建Facade
    ScanFilterFacade scanFilterFacade = new ScanFilterFacade(
        blackWhiteListFilter,
        requestDeduplicationFilter,
        targetDeduplicationFilter
    );
    
    // ✅ 应用黑白名单配置
    scanFilterFacade.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
    scanFilterFacade.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());
    
    // ✅ 创建RequestHandler，注入Facade
    RequestHandler requestHandler = new RequestHandler(
        api, configManager, scanFilterFacade, taskScheduler, ...
    );
    
    // ... existing code ...
}
```

---

#### 3.2 RequestHandler.java（简化）

```java
public class RequestHandler implements HttpHandler {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final ScanFilterFacade scanFilter;  // ✅ 使用Facade
    private final TaskScheduler taskScheduler;
    // ...
    
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // ✅ 0. 检查被动扫描总开关
        if (!xprobeConfigManager.isPassiveScanEnabled()) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }
        
        // ✅ 1. 统一过滤检查（白名单/黑名单/去重）
        if (!scanFilter.shouldScanRequest(requestToBeSent)) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }
        
        // 2. 创建请求上下文
        RequestContext context = new RequestContext(...);
        
        // 3. 收集扫描任务
        List<ScanTask> scanTasks = collectScanTasks(requestToBeSent, context);
        
        // 4. 调度扫描任务
        if (!scanTasks.isEmpty()) {
            taskScheduler.scheduleScan(scanTasks);
        }
        
        // ... existing code ...
    }
}
```

---

#### 3.3 UniversalScanner.java（简化）

```java
public class UniversalScanner extends AbstractScanner {
    
    private final ScanFilterFacade scanFilter;  // ✅ 使用Facade
    
    public UniversalScanner(MontoyaApi api, 
                           XProbeConfigManager xprobeConfigManager,
                           ScanFilterFacade scanFilter) {  // ✅ 注入Facade
        this.api = api;
        this.xprobeConfigManager = xprobeConfigManager;
        this.scanFilter = scanFilter;
    }
    
    private PairEvaluationResult evaluatePair(...) {
        // ... 收集所有targets ...
        
        // ✅ 统一去重过滤
        List<InjectionTarget> validTargets = scanFilter.filterTargets(
            allTargets, originalRequest, config
        );
        
        if (validTargets.isEmpty()) {
            return new PairEvaluationResult(false);
        }
        
        // 执行注入...
    }
    
    // ✅ 标记方法简化
    private void markTargetAsProcessed(InjectionTarget target) {
        if (target.dedupKey != null) {
            scanFilter.markTargetAsProcessed(target.dedupKey);
        }
    }
}
```

---

## 📊 重构前后对比

### 代码复杂度

**重构前**：
```
RequestHandler
    └─ RequestFilter
           ├─ GlobalFilter (黑白名单)
           └─ processedRequests (请求去重)

UniversalScanner
    └─ filterDuplicateTargets (目标去重)
           └─ RealtimeScannerRefactored.isAlreadyProcessed()
```

**重构后**：
```
ScanFilterFacade (统一入口)
    ├─ BlackWhiteListFilter (黑白名单)
    ├─ RequestDeduplicationFilter (请求去重)
    └─ TargetDeduplicationFilter (目标去重)

RequestHandler → ScanFilterFacade.shouldScanRequest()
UniversalScanner → ScanFilterFacade.filterTargets()
```

---

### 调用复杂度

**重构前**：
```java
// RequestHandler
if (!isFromValidTool(request)) return false;
int hash = request.toString().hashCode();
if (processedRequests.contains(hash)) return false;
if (!passBlackWhiteList(request)) return false;
processedRequests.add(hash);

// UniversalScanner
List<InjectionTarget> validTargets = filterDuplicateTargets(...);
// 内部逻辑很复杂...
```

**重构后**：
```java
// RequestHandler
if (!scanFilter.shouldScanRequest(request)) return;  // ✅ 一行搞定

// UniversalScanner
List<InjectionTarget> validTargets = scanFilter.filterTargets(
    allTargets, request, config  // ✅ 一行搞定
);
```

---

### 可测试性

**重构前**：
```java
// ❌ 难以测试：必须创建整个RequestHandler
@Test
public void testFiltering() {
    RequestHandler handler = new RequestHandler(api, configManager, ...);
    // 很多依赖...
}
```

**重构后**：
```java
// ✅ 易于测试：可以单独测试各个过滤器
@Test
public void testBlackWhiteList() {
    BlackWhiteListFilter filter = new BlackWhiteListFilter(globalFilter);
    assertFalse(filter.shouldProcess("http://blocked.com"));
}

@Test
public void testRequestDeduplication() {
    RequestDeduplicationFilter filter = new RequestDeduplicationFilter();
    assertFalse(filter.isDuplicate(request1));
    filter.markAsProcessed(request1);
    assertTrue(filter.isDuplicate(request1));
}

@Test
public void testTargetDeduplication() {
    TargetDeduplicationFilter filter = new TargetDeduplicationFilter();
    List<InjectionTarget> filtered = filter.filter(targets, request, config);
    assertEquals(2, filtered.size());
}

// ✅ 测试Facade
@Test
public void testFacade() {
    BlackWhiteListFilter mockBlack = mock(BlackWhiteListFilter.class);
    ScanFilterFacade facade = new ScanFilterFacade(mockBlack, ...);
    
    when(mockBlack.shouldProcess(anyString())).thenReturn(false);
    assertFalse(facade.shouldScanRequest(request));
}
```

---

## 🎯 重构收益

| 维度 | 重构前 | 重构后 | 改进 |
|------|--------|--------|------|
| 代码行数 | ~300行 | ~400行 | +33%（但更清晰） |
| 类数量 | 4个类 | 5个类 | +1个Facade |
| 调用复杂度 | 5-10行 | 1行 | ✅ -80% |
| 可测试性 | ⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ +150% |
| 可维护性 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ +67% |
| 可扩展性 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ +67% |
| 职责清晰度 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ +67% |

---

## 📝 实施步骤

### 阶段1：创建新类（1-2小时）
1. 创建 `BlackWhiteListFilter`
2. 创建 `RequestDeduplicationFilter`
3. 创建 `TargetDeduplicationFilter`
4. 创建 `ScanFilterFacade`
5. 添加单元测试

### 阶段2：迁移代码（2-3小时）
1. 修改 `XProbe.java` 初始化逻辑
2. 重构 `RequestHandler.java`
3. 重构 `UniversalScanner.java`
4. 测试验证

### 阶段3：清理旧代码（30分钟）
1. 移除 `RequestFilter` 中的去重逻辑
2. 移除 `UniversalScanner` 中的 `filterDuplicateTargets`
3. 清理 `RealtimeScannerRefactored` 的去重相关代码
4. 更新文档

**总计时间**：3.5-5.5小时

---

## 🚀 建议

### 立即实施？

**如果你希望**：
- ✅ 代码更清晰易维护
- ✅ 易于测试和扩展
- ✅ 遵循最佳实践
- ✅ 长期维护项目

→ **强烈建议实施方案4（Facade模式）**

**如果你希望**：
- ⚠️ 快速交付，暂不重构
- ⚠️ 代码能用就行

→ 可以保持现状，但记录技术债务

---

## 📖 相关资料

- 📖 [Facade Pattern](https://refactoring.guru/design-patterns/facade)
- 📖 [Chain of Responsibility Pattern](https://refactoring.guru/design-patterns/chain-of-responsibility)
- 📖 [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- 📄 **CLEAN_ARCHITECTURE_REFACTORING.md** - Clean Architecture重构方案

---

**设计完成时间**：2025-10-02  
**推荐方案**：⭐⭐⭐⭐⭐ 方案4（Facade模式）  
**建议**：立即实施，投资回报率高


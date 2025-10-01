# 代码清理和修复计划

## 🎯 用户需求

1. **Arjun 扫描应该从 SiteMap 获取流量**（继承原始请求的完整上下文）
2. **所有流程都应用全局黑白名单过滤**（包括从 SiteMap 获取流量）
3. **清理所有 x8 残留代码**，完全替换为 Arjun

---

## 📋 需要修复的问题

### 1. performManualArjunScan() 应该从 SiteMap 获取
**当前问题：** 从 HostData 获取（我之前的错误优化）  
**应该：** 从 SiteMap 获取，这样可以继承原始请求的完整上下文

### 2. 缺少全局黑白名单过滤
**当前问题：** 从 SiteMap 获取流量时没有应用黑白名单  
**应该：** 使用 `globalFilter.shouldProcessActive(url)` 过滤

### 3. x8 残留代码
**发现：** 93 处 x8 相关代码  
**需要：** 全部清理或替换为 Arjun

---

## 🔧 修复方案

### 方案 1: 重写 performManualArjunScan()

```java
private void performManualArjunScan() {
    try {
        SiteMap siteMap = api.siteMap();
        List<HttpRequestResponse> requestResponses = siteMap.requestResponses();

        // 按 host 分组
        Map<String, List<HttpRequest>> hostToRequests = new HashMap<>();
        
        for (HttpRequestResponse rr : requestResponses) {
            HttpRequest req = rr.request();
            String url = req.url();
            
            // 1. 跳过 Arjun 自己产生的流量
            boolean isArjunTraffic = false;
            for (var header : req.headers()) {
                if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
                    isArjunTraffic = true;
                    break;
                }
            }
            if (isArjunTraffic) continue;
            
            // 2. 应用全局黑白名单过滤 ✅ 关键！
            if (!globalFilter.shouldProcessActive(url)) {
                api.logging().raiseDebugEvent("URL 被全局过滤器阻止: " + url);
                continue;
            }
            
            String host = new URL(url).getHost();
            hostToRequests.computeIfAbsent(host, k -> new ArrayList<>()).add(req);
        }

        int processed = 0;
        int totalRequests = 0;
        
        for (Map.Entry<String, List<HttpRequest>> entry : hostToRequests.entrySet()) {
            String host = entry.getKey();
            HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
            
            for (HttpRequest req : entry.getValue()) {
                String endpoint = extractEndpoint(req.url());
                
                // 计算增量参数
                Set<String> hostParams = new HashSet<>(hostData.getParameters());
                hostParams.addAll(globalCustomDictionary);
                Set<String> scanned = new HashSet<>(hostData.getArjunScannedParams(endpoint));
                hostParams.removeAll(scanned);

                if (hostParams.isEmpty()) {
                    continue;
                }

                totalRequests++;
                
                // 使用 ArjunIntegration 执行扫描（继承原始请求）✅
                arjunIntegration.scan(req, hostParams).thenAccept(result -> {
                    if (result.isSuccess()) {
                        api.logging().raiseInfoEvent("Arjun 发现参数: " + result.getUrl() + 
                            " - " + result.getFoundParameters());
                        
                        hostData.markArjunScanned(endpoint, hostParams);
                        
                        for (String param : result.getFoundParameters()) {
                            hostData.addParameterToEndpoint(endpoint, param);
                        }
                        
                        saveArjunStateSafely();
                    } else {
                        api.logging().raiseErrorEvent("Arjun 扫描失败: " + result.getErrorMessage());
                    }
                }).exceptionally(ex -> {
                    api.logging().raiseErrorEvent("Arjun 异步执行失败: " + ex.getMessage());
                    return null;
                });
                
                processed++;
            }
        }

        api.logging().raiseInfoEvent("Arjun 参数探测启动，计划处理 " + totalRequests + 
            " 个 URL，实际提交 " + processed + " 个扫描任务");
    } catch (Exception e) {
        api.logging().raiseErrorEvent("执行 Arjun 参数探测时出错: " + e.getMessage());
    }
}
```

**关键改进：**
- ✅ 从 SiteMap 获取流量（继承原始请求）
- ✅ 应用全局黑白名单过滤 `globalFilter.shouldProcessActive(url)`
- ✅ 保留增量探测机制
- ✅ 使用原始请求的完整上下文

### 方案 2: 清理 x8 相关代码

**需要删除/替换的文件和方法：**

#### RealtimeScanner.java
- `triggerManualX8Bruteforce()` → 已有 `triggerManualArjunScan()`，删除
- `performManualX8Bruteforce()` → 删除
- `executeX8Bruteforce()` → 删除
- `executeEnhancedX8Bruteforce()` → 删除
- `executeX8ForEndpoint()` → 删除
- `isX8Processed()` → 改为 `isArjunProcessed()`
- `markX8Processed()` → 改为 `markArjunProcessed()`
- `generateX8Key()` → 改为 `generateArjunKey()`
- `parseX8Output()` → 删除（已有 ArjunIntegration 处理）
- `x8ProcessedKeys` → 删除（使用 arjunScannedParams）

#### ActiveScanner.java
- 整个文件可能需要重构或删除（如果不需要）
- 或者保留但清理所有 x8 相关方法

### 方案 3: 全局过滤器的应用点

**需要应用 `globalFilter.shouldProcessActive(url)` 的地方：**

1. ✅ `RequestHandler.handleHttpRequestToBeSent()` - 已有 `requestFilter.shouldScan()`
2. ✅ `RealtimeScanner.processNewRequest()` - 已有过滤
3. ⚠️ `RealtimeScanner.performManualArjunScan()` - **需要添加**（从 SiteMap 获取时）
4. ⚠️ 任何其他从 SiteMap 获取流量的地方

---

## 📝 实施步骤

1. ✅ 重写 `performManualArjunScan()` - 从 SiteMap 获取 + 应用黑白名单
2. ✅ 清理 RealtimeScanner 中的所有 x8 方法
3. ✅ 清理 ActiveScanner 中的所有 x8 方法
4. ✅ 删除 `x8ProcessedKeys` 变量
5. ✅ 测试编译和功能

---

## 🎯 最终流程

```
手动触发 Arjun:
    ↓
从 SiteMap 获取所有请求
    ↓
for each 请求:
  1. 检查是否是 Arjun 流量（X-XProbe-Arjun 头）→ 跳过
  2. 应用全局黑白名单过滤 ✅
  3. 按 host 分组
    ↓
for each (host, 请求列表):
  for each 请求:
    1. 提取 endpoint
    2. 计算增量参数（host 所有参数 - endpoint 已扫描参数）
    3. 如果有新参数:
       - 使用原始请求（完整上下文）✅
       - 执行 Arjun 扫描
       - 更新已扫描标记
```

---

这个方案：
- ✅ 从 SiteMap 获取流量（保留完整请求上下文）
- ✅ 应用全局黑白名单
- ✅ 清理所有 x8 代码
- ✅ 保留增量探测机制

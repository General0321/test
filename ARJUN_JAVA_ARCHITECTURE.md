# Arjun Java 内置实现 - 架构设计文档

## 📋 目录
- [1. 背景和目标](#1-背景和目标)
- [2. Arjun核心功能分析](#2-arjun核心功能分析)
- [3. 架构设计](#3-架构设计)
- [4. 目录结构](#4-目录结构)
- [5. 核心类设计](#5-核心类设计)
- [6. 实现策略](#6-实现策略)
- [7. 与现有系统集成](#7-与现有系统集成)
- [8. 优势分析](#8-优势分析)

---

## 1. 背景和目标

### 1.1 为什么要内置实现？

**问题：**
- ❌ macOS SIP 安全限制阻止外部进程调用
- ❌ 跨平台兼容性差（Python环境依赖）
- ❌ 进程间通信复杂、易出错
- ❌ 无法充分利用Burp API的优势

**目标：**
- ✅ 纯Java实现，无外部依赖
- ✅ 完全绕过macOS安全限制
- ✅ 深度集成Burp API
- ✅ 更好的性能和稳定性
- ✅ 统一的配置和管理

### 1.2 核心功能保留

保留Arjun的核心算法：
1. **参数爆破** - 字典攻击发现隐藏参数
2. **异常检测** - 响应差异对比
3. **智能缩小** - 分块测试 + 递归缩小
4. **最终验证** - 单参数确认

---

## 2. Arjun核心功能分析

### 2.1 工作流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    Arjun 参数发现流程                              │
└─────────────────────────────────────────────────────────────────┘

1. 稳定性探测 (Stability Probe)
   ├─ 发送随机参数请求2次
   ├─ 对比响应差异
   └─ 建立基线规则 (Baseline Factors)

2. 启发式提取 (Heuristic)
   ├─ 从响应中提取可能的参数名
   └─ 添加到测试字典

3. 分块爆破 (Chunk Bruteforce)
   ├─ 将字典分成N组（每组250-500个参数）
   ├─ 每组参数一起发送（提高效率）
   └─ 检测异常 → 找出可疑组

4. 递归缩小 (Narrowing)
   ├─ 将可疑组再次分块
   ├─ 重复爆破过程
   └─ 直到缩小到单个参数

5. 最终验证 (Verification)
   ├─ 单独测试每个参数
   └─ 确认有效参数

6. 输出结果
   └─ 返回发现的参数列表
```

### 2.2 核心算法

#### 2.2.1 基线建立 (define)

```python
# Arjun: arjun/core/anomaly.py - define()

factors = {
    'same_code': None,        # HTTP状态码相同
    'same_body': None,        # 响应体完全相同
    'same_plaintext': None,   # 去HTML后文本相同
    'lines_num': None,        # 行数相同
    'lines_diff': None,       # 不同的行
    'same_headers': None,     # 响应头相同
    'same_redirect': None,    # 重定向相同
    'param_missing': None,    # 参数名未在响应中出现
    'value_missing': None     # 参数值未在响应中出现
}
```

#### 2.2.2 异常对比 (compare)

```python
# Arjun: arjun/core/anomaly.py - compare()

# 依次检查每个factor：
if factors['same_code'] != None and response.status_code != factors['same_code']:
    return ('http code', params, 'same_code')

if factors['same_body'] != None and response.text != factors['same_body']:
    return ('body length', params, 'same_body')

# ... 其他检查
```

#### 2.2.3 分块测试 (bruter)

```python
# Arjun: arjun/core/bruter.py - bruter()

def bruter(request, factors, params, mode='bruteforce'):
    response = requester(request, params)  # 发送请求
    comparison_result = compare(response, factors, params)  # 对比
    
    if mode == 'verify':
        return comparison_result[0]  # 返回异常类型
    return comparison_result[1]      # 返回可疑参数
```

---

## 3. 架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                  Java Arjun 内置实现架构                           │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  调用层 (Existing Code)                                         │
│  - RealtimeScannerRefactored                                  │
│  - ArjunIntegration (已存在，需改造)                            │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│  控制层 (Control Layer)                                        │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ParamDiscoveryEngine (核心引擎)                        │  │
│  │  - scan()         启动扫描                             │  │
│  │  - initialize()   初始化和稳定性探测                    │  │
│  │  - narrowDown()   递归缩小                             │  │
│  │  - verify()       最终验证                             │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│  核心组件层 (Core Components)                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ResponseBaseline│ │AnomalyDetector│ │ParamExtractor│       │
│  │  建立基线规则   │  │  检测异常响应  │  │  提取参数     │       │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ChunkProcessor│  │ParamVerifier │  │DictManager   │        │
│  │  分块处理     │  │  参数验证     │  │  字典管理     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│  HTTP请求层 (HTTP Layer)                                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  BurpHttpRequester (使用Burp API)                       │  │
│  │  - sendRequest()      发送HTTP请求                      │  │
│  │  - buildRequest()     构建测试请求                       │  │
│  │  - handleResponse()   处理响应                          │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│  数据模型层 (Data Models)                                      │
│  - ScanContext          扫描上下文                            │
│  - BaselineFactors      基线因子                              │
│  - AnomalyResult        异常检测结果                           │
│  - DiscoveryResult      发现结果                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 关键设计决策

| 决策点 | 选择 | 理由 |
|-------|------|-----|
| **HTTP请求** | 使用 Burp MontoyaApi | 直接集成，无需额外配置 |
| **并发模型** | 使用现有的 `TaskScheduler` | 复用已有的线程池管理 |
| **字典来源** | 集成 `ParameterManager` | 利用已收集的参数 + 内置字典 |
| **去重机制** | 复用 `DeduplicationKeyGenerator` | 避免重复扫描 |
| **结果存储** | 通过 `LogModel` 记录 | 统一的结果管理 |

---

## 4. 目录结构

```
src/main/java/com/xprobe/scanner/arjun/
├── ParamDiscoveryEngine.java         # 核心引擎
├── core/
│   ├── ResponseBaseline.java         # 基线管理
│   ├── AnomalyDetector.java          # 异常检测
│   ├── ParamExtractor.java           # 参数提取（启发式）
│   ├── ChunkProcessor.java           # 分块处理
│   ├── ParamVerifier.java            # 参数验证
│   └── DictManager.java              # 字典管理
├── http/
│   ├── BurpHttpRequester.java        # HTTP请求执行
│   ├── RequestBuilder.java           # 请求构造
│   └── ResponseAnalyzer.java         # 响应分析
├── model/
│   ├── ScanContext.java              # 扫描上下文
│   ├── BaselineFactors.java          # 基线因子
│   ├── AnomalyResult.java            # 异常结果
│   ├── DiscoveryResult.java          # 发现结果
│   └── ParamCandidate.java           # 参数候选
└── config/
    ├── ArjunConfig.java              # 配置管理
    └── DefaultDictionaries.java      # 内置字典
```

---

## 5. 核心类设计

### 5.1 ParamDiscoveryEngine（核心引擎）

```java
package com.xprobe.scanner.arjun;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.arjun.core.*;
import com.xprobe.scanner.arjun.http.BurpHttpRequester;
import com.xprobe.scanner.arjun.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 参数发现引擎 - Arjun核心实现
 */
public class ParamDiscoveryEngine {
    
    private final MontoyaApi api;
    private final BurpHttpRequester requester;
    private final ResponseBaseline baseline;
    private final AnomalyDetector detector;
    private final ParamExtractor extractor;
    private final ChunkProcessor chunkProcessor;
    private final ParamVerifier verifier;
    private final ArjunConfig config;
    
    public ParamDiscoveryEngine(MontoyaApi api, ArjunConfig config) {
        this.api = api;
        this.config = config;
        this.requester = new BurpHttpRequester(api);
        this.baseline = new ResponseBaseline(api);
        this.detector = new AnomalyDetector(api);
        this.extractor = new ParamExtractor(api);
        this.chunkProcessor = new ChunkProcessor(api, config);
        this.verifier = new ParamVerifier(api, requester);
    }
    
    /**
     * 启动参数发现扫描
     */
    public CompletableFuture<DiscoveryResult> scan(HttpRequest originalRequest, 
                                                     Set<String> dictionary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.logging().raiseInfoEvent("🔍 开始参数发现: " + originalRequest.url());
                
                // 1. 初始化：稳定性探测 + 建立基线
                ScanContext context = initialize(originalRequest, dictionary);
                if (!context.isHealthy()) {
                    return DiscoveryResult.error("目标不稳定，跳过扫描");
                }
                
                // 2. 启发式提取（从响应中提取可能的参数）
                Set<String> heuristicParams = extractor.extractFromResponse(
                    context.getBaselineResponse()
                );
                context.addDictionary(heuristicParams);
                
                // 3. 分块爆破 + 递归缩小
                Set<ParamCandidate> candidates = narrowDown(context);
                
                // 4. 最终验证
                Set<String> confirmedParams = verify(context, candidates);
                
                api.logging().raiseInfoEvent(String.format(
                    "✅ 参数发现完成: %s (发现 %d 个参数)",
                    originalRequest.url(),
                    confirmedParams.size()
                ));
                
                return DiscoveryResult.success(originalRequest.url(), confirmedParams);
                
            } catch (Exception e) {
                api.logging().raiseErrorEvent("参数发现失败: " + e.getMessage());
                return DiscoveryResult.error(e.getMessage());
            }
        });
    }
    
    /**
     * 初始化：稳定性探测 + 建立基线
     */
    private ScanContext initialize(HttpRequest originalRequest, Set<String> dictionary) {
        api.logging().raiseDebugEvent("📊 稳定性探测中...");
        
        // 发送随机参数请求（2次）
        String randomParam1 = "z" + generateRandomString(6);
        String randomParam2 = "z" + generateRandomString(6);
        
        HttpRequest testRequest1 = requester.buildTestRequest(
            originalRequest, 
            Map.of(randomParam1, randomParam1)
        );
        HttpRequest testRequest2 = requester.buildTestRequest(
            originalRequest, 
            Map.of(randomParam2, randomParam2)
        );
        
        var response1 = requester.sendRequest(testRequest1);
        var response2 = requester.sendRequest(testRequest2);
        
        // 建立基线规则
        BaselineFactors factors = baseline.define(
            response1, 
            response2, 
            randomParam1, 
            randomParam2, 
            dictionary
        );
        
        // 验证稳定性（发送第3次请求）
        String randomParam3 = "z" + generateRandomString(6);
        HttpRequest testRequest3 = requester.buildTestRequest(
            originalRequest,
            Map.of(randomParam3, randomParam3)
        );
        var response3 = requester.sendRequest(testRequest3);
        
        AnomalyResult anomaly = detector.compare(response3, factors, Map.of(randomParam3, randomParam3));
        boolean isHealthy = !anomaly.hasAnomaly();
        
        if (!isHealthy) {
            api.logging().raiseErrorEvent("⚠️ 目标不稳定: " + anomaly.getReason());
        }
        
        return new ScanContext(
            originalRequest,
            factors,
            dictionary,
            response1,
            isHealthy
        );
    }
    
    /**
     * 递归缩小参数范围
     */
    private Set<ParamCandidate> narrowDown(ScanContext context) {
        Set<ParamCandidate> allCandidates = new HashSet<>();
        List<Set<String>> chunks = chunkProcessor.createChunks(
            context.getDictionary(), 
            config.getChunkSize()
        );
        
        api.logging().raiseDebugEvent(String.format(
            "🔄 开始分块测试 (共 %d 个分块)", chunks.size()
        ));
        
        // 第一轮：分块测试
        List<Set<String>> anomalousChunks = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            Set<String> chunk = chunks.get(i);
            
            // 构建测试请求（chunk中所有参数一起发送）
            Map<String, String> testParams = new HashMap<>();
            for (String param : chunk) {
                testParams.put(param, generateRandomValue());
            }
            
            HttpRequest testRequest = requester.buildTestRequest(
                context.getOriginalRequest(),
                testParams
            );
            var response = requester.sendRequest(testRequest);
            
            // 检测异常
            AnomalyResult anomaly = detector.compare(
                response, 
                context.getFactors(), 
                testParams
            );
            
            if (anomaly.hasAnomaly()) {
                api.logging().raiseDebugEvent(String.format(
                    "  ✓ 发现异常分块 %d/%d (原因: %s)", 
                    i + 1, chunks.size(), anomaly.getReason()
                ));
                anomalousChunks.add(chunk);
            }
            
            if (i % 10 == 0) {
                api.logging().raiseDebugEvent(String.format(
                    "  进度: %d/%d", i + 1, chunks.size()
                ));
            }
        }
        
        // 递归缩小
        for (Set<String> anomalousChunk : anomalousChunks) {
            if (anomalousChunk.size() == 1) {
                // 已经缩小到单个参数
                String param = anomalousChunk.iterator().next();
                allCandidates.add(new ParamCandidate(param));
            } else if (anomalousChunk.size() <= 5) {
                // 小于5个参数，全部作为候选
                for (String param : anomalousChunk) {
                    allCandidates.add(new ParamCandidate(param));
                }
            } else {
                // 继续分块
                List<Set<String>> subChunks = chunkProcessor.createChunks(
                    anomalousChunk, 
                    Math.max(2, anomalousChunk.size() / 5)
                );
                
                // 递归（使用新的上下文，复用基线）
                ScanContext subContext = context.withDictionary(anomalousChunk);
                Set<ParamCandidate> subCandidates = narrowDown(subContext);
                allCandidates.addAll(subCandidates);
            }
        }
        
        return allCandidates;
    }
    
    /**
     * 最终验证（单独测试每个参数）
     */
    private Set<String> verify(ScanContext context, Set<ParamCandidate> candidates) {
        Set<String> confirmedParams = new LinkedHashSet<>();
        
        api.logging().raiseDebugEvent(String.format(
            "🔍 开始验证候选参数 (共 %d 个)", candidates.size()
        ));
        
        for (ParamCandidate candidate : candidates) {
            // 单独测试这个参数
            Map<String, String> testParams = Map.of(
                candidate.getName(), 
                generateRandomValue()
            );
            
            HttpRequest testRequest = requester.buildTestRequest(
                context.getOriginalRequest(),
                testParams
            );
            var response = requester.sendRequest(testRequest);
            
            AnomalyResult anomaly = detector.compare(
                response,
                context.getFactors(),
                testParams
            );
            
            if (anomaly.hasAnomaly()) {
                confirmedParams.add(candidate.getName());
                api.logging().raiseInfoEvent(String.format(
                    "  ✅ 确认参数: %s (检测到: %s)", 
                    candidate.getName(), 
                    anomaly.getReason()
                ));
            }
        }
        
        return confirmedParams;
    }
    
    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 生成随机值（用于测试）
     */
    private String generateRandomValue() {
        return generateRandomString(6);
    }
}
```

### 5.2 ResponseBaseline（基线管理）

```java
package com.xprobe.scanner.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.arjun.model.BaselineFactors;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 响应基线管理 - 建立异常检测规则
 */
public class ResponseBaseline {
    
    private final MontoyaApi api;
    
    public ResponseBaseline(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 建立基线规则（对比两个响应）
     */
    public BaselineFactors define(HttpResponse response1, 
                                   HttpResponse response2,
                                   String testParam,
                                   String testValue,
                                   Set<String> wordlist) {
        
        BaselineFactors factors = new BaselineFactors();
        
        String body1 = response1.bodyToString();
        String body2 = response2.bodyToString();
        
        // 1. 检查HTTP状态码
        if (response1.statusCode() == response2.statusCode()) {
            factors.setSameCode(response1.statusCode());
        }
        
        // 2. 检查响应头
        if (headersEqual(response1, response2)) {
            factors.setSameHeaders(getHeaderKeys(response1));
        }
        
        // 3. 检查重定向
        String redirect1 = getRedirectLocation(response1);
        String redirect2 = getRedirectLocation(response2);
        if (redirect1 != null && redirect1.equals(redirect2)) {
            factors.setSameRedirect(redirect1);
        }
        
        // 4. 检查响应体
        if (body1.equals(body2)) {
            factors.setSameBody(body1);
        } 
        // 5. 检查行数
        else if (countLines(body1) == countLines(body2)) {
            factors.setLinesNum(countLines(body1));
        }
        // 6. 检查纯文本（去HTML）
        else {
            String plaintext1 = removeTags(body1);
            String plaintext2 = removeTags(body2);
            if (plaintext1.equals(plaintext2)) {
                factors.setSamePlaintext(plaintext1);
            }
            // 7. 检查不同的行
            else {
                List<String> diffLines = findDifferentLines(body1, body2);
                if (!diffLines.isEmpty()) {
                    factors.setLinesDiff(diffLines);
                }
            }
        }
        
        // 8. 检查参数名反射
        if (!body2.contains(testParam)) {
            Set<String> existingWords = new HashSet<>();
            for (String word : wordlist) {
                if (body2.contains(word)) {
                    existingWords.add(word);
                }
            }
            factors.setParamMissing(existingWords);
        }
        
        // 9. 检查参数值反射
        if (!body2.contains(testValue)) {
            factors.setValueMissing(true);
        }
        
        api.logging().raiseDebugEvent("✅ 基线规则建立完成: " + factors.summary());
        
        return factors;
    }
    
    /**
     * 检查响应头是否相同
     */
    private boolean headersEqual(HttpResponse r1, HttpResponse r2) {
        Set<String> keys1 = getHeaderKeys(r1);
        Set<String> keys2 = getHeaderKeys(r2);
        return keys1.equals(keys2);
    }
    
    /**
     * 获取响应头键列表
     */
    private Set<String> getHeaderKeys(HttpResponse response) {
        Set<String> keys = new TreeSet<>();  // 自动排序
        for (var header : response.headers()) {
            keys.add(header.name());
        }
        return keys;
    }
    
    /**
     * 获取重定向位置
     */
    private String getRedirectLocation(HttpResponse response) {
        for (var header : response.headers()) {
            if ("Location".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
    
    /**
     * 统计行数
     */
    private int countLines(String text) {
        return text.split("\n").length;
    }
    
    /**
     * 移除HTML标签
     */
    private String removeTags(String html) {
        // 简单实现：移除所有 <tag> 和 </tag>
        return html.replaceAll("<[^>]+>", "").trim();
    }
    
    /**
     * 找出不同的行（返回共同的行）
     */
    private List<String> findDifferentLines(String text1, String text2) {
        String[] lines1 = text1.split("\n");
        String[] lines2 = text2.split("\n");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(lines1));
        Set<String> set2 = new HashSet<>(Arrays.asList(lines2));
        
        // 找交集（共同的行）
        set1.retainAll(set2);
        
        return new ArrayList<>(set1);
    }
}
```

### 5.3 AnomalyDetector（异常检测）

```java
package com.xprobe.scanner.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.arjun.model.AnomalyResult;
import com.xprobe.scanner.arjun.model.BaselineFactors;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 异常检测器 - 对比响应与基线
 */
public class AnomalyDetector {
    
    private final MontoyaApi api;
    
    public AnomalyDetector(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 检测响应异常
     */
    public AnomalyResult compare(HttpResponse response, 
                                 BaselineFactors factors,
                                 Map<String, String> testParams) {
        
        // 1. 检查HTTP状态码
        if (factors.getSameCode() != null && 
            response.statusCode() != factors.getSameCode()) {
            return AnomalyResult.detected(
                "http_code", 
                testParams.keySet(),
                "HTTP状态码变化: " + factors.getSameCode() + " → " + response.statusCode()
            );
        }
        
        // 2. 检查响应头
        if (factors.getSameHeaders() != null) {
            Set<String> currentHeaders = getHeaderKeys(response);
            if (!currentHeaders.equals(factors.getSameHeaders())) {
                return AnomalyResult.detected(
                    "http_headers",
                    testParams.keySet(),
                    "响应头变化"
                );
            }
        }
        
        // 3. 检查重定向
        if (factors.getSameRedirect() != null) {
            String currentRedirect = getRedirectLocation(response);
            if (currentRedirect != null && 
                !currentRedirect.equals(factors.getSameRedirect())) {
                return AnomalyResult.detected(
                    "redirection",
                    testParams.keySet(),
                    "重定向变化"
                );
            }
        }
        
        String body = response.bodyToString();
        
        // 4. 检查响应体
        if (factors.getSameBody() != null && 
            !body.equals(factors.getSameBody())) {
            return AnomalyResult.detected(
                "body_content",
                testParams.keySet(),
                "响应体内容变化"
            );
        }
        
        // 5. 检查行数
        if (factors.getLinesNum() != null && 
            countLines(body) != factors.getLinesNum()) {
            return AnomalyResult.detected(
                "line_count",
                testParams.keySet(),
                "响应行数变化"
            );
        }
        
        // 6. 检查纯文本
        if (factors.getSamePlaintext() != null) {
            String plaintext = removeTags(body);
            if (!plaintext.equals(factors.getSamePlaintext())) {
                return AnomalyResult.detected(
                    "plaintext",
                    testParams.keySet(),
                    "纯文本内容变化"
                );
            }
        }
        
        // 7. 检查行差异
        if (factors.getLinesDiff() != null) {
            for (String line : factors.getLinesDiff()) {
                if (!body.contains(line)) {
                    return AnomalyResult.detected(
                        "line_diff",
                        testParams.keySet(),
                        "特定行缺失"
                    );
                }
            }
        }
        
        // 8. 检查参数名反射
        if (factors.getParamMissing() != null) {
            for (String param : testParams.keySet()) {
                if (param.length() >= 5 && 
                    !factors.getParamMissing().contains(param) &&
                    Pattern.compile("['\"]" + Pattern.quote(param) + "['\"]")
                           .matcher(body).find()) {
                    return AnomalyResult.detected(
                        "param_reflection",
                        testParams.keySet(),
                        "参数名反射: " + param
                    );
                }
            }
        }
        
        // 9. 检查参数值反射
        if (factors.isValueMissing()) {
            for (String value : testParams.values()) {
                if (value.length() == 6 &&
                    Pattern.compile("['\"]" + Pattern.quote(value) + "['\"]")
                           .matcher(body).find()) {
                    return AnomalyResult.detected(
                        "value_reflection",
                        testParams.keySet(),
                        "参数值反射: " + value
                    );
                }
            }
        }
        
        // 无异常
        return AnomalyResult.normal();
    }
    
    private Set<String> getHeaderKeys(HttpResponse response) {
        Set<String> keys = new TreeSet<>();
        for (var header : response.headers()) {
            keys.add(header.name());
        }
        return keys;
    }
    
    private String getRedirectLocation(HttpResponse response) {
        for (var header : response.headers()) {
            if ("Location".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
    
    private int countLines(String text) {
        return text.split("\n").length;
    }
    
    private String removeTags(String html) {
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
```

### 5.4 BurpHttpRequester（HTTP请求执行）

```java
package com.xprobe.scanner.arjun.http;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.*;

/**
 * HTTP请求执行器 - 使用Burp API
 */
public class BurpHttpRequester {
    
    private final MontoyaApi api;
    
    public BurpHttpRequester(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 发送HTTP请求
     */
    public HttpResponse sendRequest(HttpRequest request) {
        try {
            HttpRequestResponse result = api.http().sendRequest(request);
            return result.response();
        } catch (Exception e) {
            api.logging().raiseErrorEvent("请求失败: " + e.getMessage());
            throw new RuntimeException("请求失败", e);
        }
    }
    
    /**
     * 构建测试请求（添加测试参数）
     */
    public HttpRequest buildTestRequest(HttpRequest originalRequest, 
                                         Map<String, String> testParams) {
        
        HttpRequest modifiedRequest = originalRequest;
        
        // 获取Content-Type
        String contentType = getContentType(originalRequest);
        
        // 根据请求类型添加参数
        if ("GET".equalsIgnoreCase(originalRequest.method())) {
            // GET: 添加URL参数
            for (Map.Entry<String, String> entry : testParams.entrySet()) {
                modifiedRequest = modifiedRequest.withAddedParameters(
                    HttpParameter.urlParameter(entry.getKey(), entry.getValue())
                );
            }
        } else if (contentType != null && contentType.contains("application/json")) {
            // JSON: 合并到JSON body
            modifiedRequest = buildJsonRequest(originalRequest, testParams);
        } else {
            // POST表单: 添加body参数
            for (Map.Entry<String, String> entry : testParams.entrySet()) {
                modifiedRequest = modifiedRequest.withAddedParameters(
                    HttpParameter.bodyParameter(entry.getKey(), entry.getValue())
                );
            }
        }
        
        // 添加标记header
        modifiedRequest = modifiedRequest.withAddedHeader(
            "X-XProbe-ParamDiscovery", "1"
        );
        
        return modifiedRequest;
    }
    
    /**
     * 构建JSON请求
     */
    private HttpRequest buildJsonRequest(HttpRequest originalRequest, 
                                          Map<String, String> testParams) {
        try {
            String originalBody = originalRequest.bodyToString();
            
            // 解析原始JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            
            Map<String, Object> jsonMap;
            if (originalBody.trim().isEmpty()) {
                jsonMap = new HashMap<>();
            } else {
                jsonMap = mapper.readValue(originalBody, Map.class);
            }
            
            // 添加测试参数
            jsonMap.putAll(testParams);
            
            // 序列化回JSON
            String newBody = mapper.writeValueAsString(jsonMap);
            
            return originalRequest.withBody(newBody);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("JSON构建失败: " + e.getMessage());
            return originalRequest;
        }
    }
    
    /**
     * 获取Content-Type
     */
    private String getContentType(HttpRequest request) {
        for (var header : request.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
}
```

### 5.5 数据模型

```java
// BaselineFactors.java
package com.xprobe.scanner.arjun.model;

import java.util.*;

public class BaselineFactors {
    private Integer sameCode;
    private String sameBody;
    private String samePlaintext;
    private Integer linesNum;
    private List<String> linesDiff;
    private Set<String> sameHeaders;
    private String sameRedirect;
    private Set<String> paramMissing;
    private boolean valueMissing;
    
    // Getters and Setters...
    
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (sameCode != null) sb.append("code=").append(sameCode).append(", ");
        if (sameBody != null) sb.append("body=same, ");
        if (linesNum != null) sb.append("lines=").append(linesNum).append(", ");
        return sb.toString();
    }
}

// AnomalyResult.java
package com.xprobe.scanner.arjun.model;

import java.util.*;

public class AnomalyResult {
    private final boolean hasAnomaly;
    private final String anomalyType;
    private final Set<String> params;
    private final String reason;
    
    private AnomalyResult(boolean hasAnomaly, String anomalyType, 
                          Set<String> params, String reason) {
        this.hasAnomaly = hasAnomaly;
        this.anomalyType = anomalyType;
        this.params = params != null ? params : new HashSet<>();
        this.reason = reason;
    }
    
    public static AnomalyResult detected(String type, Set<String> params, String reason) {
        return new AnomalyResult(true, type, params, reason);
    }
    
    public static AnomalyResult normal() {
        return new AnomalyResult(false, null, null, null);
    }
    
    // Getters...
}

// DiscoveryResult.java
package com.xprobe.scanner.arjun.model;

import java.util.*;

public class DiscoveryResult {
    private final boolean success;
    private final String url;
    private final Set<String> foundParams;
    private final String errorMessage;
    
    private DiscoveryResult(boolean success, String url, 
                            Set<String> foundParams, String errorMessage) {
        this.success = success;
        this.url = url;
        this.foundParams = foundParams != null ? foundParams : new HashSet<>();
        this.errorMessage = errorMessage;
    }
    
    public static DiscoveryResult success(String url, Set<String> params) {
        return new DiscoveryResult(true, url, params, null);
    }
    
    public static DiscoveryResult error(String errorMessage) {
        return new DiscoveryResult(false, null, null, errorMessage);
    }
    
    // Getters...
}

// ScanContext.java
package com.xprobe.scanner.arjun.model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.*;

public class ScanContext {
    private final HttpRequest originalRequest;
    private final BaselineFactors factors;
    private Set<String> dictionary;
    private final HttpResponse baselineResponse;
    private final boolean isHealthy;
    
    public ScanContext(HttpRequest originalRequest, BaselineFactors factors,
                       Set<String> dictionary, HttpResponse baselineResponse,
                       boolean isHealthy) {
        this.originalRequest = originalRequest;
        this.factors = factors;
        this.dictionary = new HashSet<>(dictionary);
        this.baselineResponse = baselineResponse;
        this.isHealthy = isHealthy;
    }
    
    public void addDictionary(Set<String> newParams) {
        this.dictionary.addAll(newParams);
    }
    
    public ScanContext withDictionary(Set<String> newDict) {
        return new ScanContext(originalRequest, factors, newDict, 
                               baselineResponse, isHealthy);
    }
    
    // Getters...
}
```

---

## 6. 实现策略

### 6.1 分阶段实现

#### Phase 1: 核心框架（1-2天）
- [ ] 创建目录结构
- [ ] 实现数据模型类
- [ ] 实现 `BurpHttpRequester`
- [ ] 实现 `ResponseBaseline`
- [ ] 实现 `AnomalyDetector`

#### Phase 2: 核心引擎（2-3天）
- [ ] 实现 `ParamDiscoveryEngine`
- [ ] 实现稳定性探测逻辑
- [ ] 实现分块测试逻辑
- [ ] 实现递归缩小逻辑
- [ ] 实现最终验证逻辑

#### Phase 3: 辅助功能（1-2天）
- [ ] 实现 `ParamExtractor`（启发式提取）
- [ ] 实现 `ChunkProcessor`（分块管理）
- [ ] 实现 `DictManager`（字典管理）
- [ ] 实现 `DefaultDictionaries`（内置字典）

#### Phase 4: 集成和优化（1-2天）
- [ ] 集成到 `RealtimeScannerRefactored`
- [ ] 替换现有的 `ArjunIntegration`
- [ ] 配置管理集成
- [ ] UI集成（进度显示）
- [ ] 性能优化

### 6.2 性能优化策略

| 优化点 | 策略 |
|-------|-----|
| **并发请求** | 利用 `TaskScheduler` 的线程池 |
| **请求复用** | 缓存HTTP连接，使用Keep-Alive |
| **智能分块** | 根据响应时间动态调整chunk大小 |
| **早停机制** | 目标不稳定时立即停止 |
| **去重** | 集成 `DeduplicationKeyGenerator` |

---

## 7. 与现有系统集成

### 7.1 替换 ArjunIntegration

```java
// RealtimeScannerRefactored.java (修改)

// ❌ 删除
private ArjunIntegration arjunIntegration;

// ✅ 新增
private ParamDiscoveryEngine paramDiscovery;

// 初始化
this.paramDiscovery = new ParamDiscoveryEngine(api, arjunConfig);

// 使用
public void triggerManualArjunScan(HttpRequest request, Set<String> dictionary) {
    paramDiscovery.scan(request, dictionary).thenAccept(result -> {
        if (result.isSuccess()) {
            // 处理发现的参数
            Set<String> found = result.getFoundParams();
            parameterManager.addParameters(host, endpoint, found);
            
            // 记录结果
            api.logging().raiseInfoEvent(String.format(
                "参数发现完成: %s (发现 %d 个)",
                request.url(),
                found.size()
            ));
        } else {
            api.logging().raiseErrorEvent("参数发现失败: " + result.getErrorMessage());
        }
    });
}
```

### 7.2 配置集成

```java
// XProbeConfig.java (新增字段)

public class XProbeConfig {
    // ... 现有字段
    
    // ✅ 新增：Arjun配置
    private ArjunConfig arjunConfig = new ArjunConfig();
    
    public static class ArjunConfig {
        private boolean enabled = true;
        private int chunkSize = 250;              // 分块大小
        private int maxThreads = 5;               // 最大线程数
        private int timeout = 15;                 // 超时（秒）
        private boolean useHeuristic = true;      // 启用启发式提取
        private String dictionaryLevel = "medium"; // small/medium/large
        
        // Getters and Setters...
    }
}
```

### 7.3 UI集成

在 **ActiveProbeTab** 中添加控制：

```java
// ActiveProbeTab.java

JPanel arjunPanel = new JPanel();
arjunPanel.setBorder(BorderFactory.createTitledBorder("参数发现 (Arjun内置)"));

JCheckBox enabledCheckbox = new JCheckBox("启用参数发现", true);
JComboBox<String> levelCombo = new JComboBox<>(new String[]{"small", "medium", "large"});
JSpinner chunkSpinner = new JSpinner(new SpinnerNumberModel(250, 10, 1000, 50));
JCheckBox heuristicCheckbox = new JCheckBox("启用启发式提取", true);

// 配置绑定...
```

---

## 8. 优势分析

### 8.1 技术优势

| 对比项 | 外部Arjun | Java内置版 |
|-------|----------|-----------|
| **跨平台** | ❌ Python依赖 | ✅ 纯Java |
| **macOS兼容** | ❌ SIP限制 | ✅ 无限制 |
| **性能** | 🟡 进程间通信 | ✅ 直接调用 |
| **集成度** | ❌ 命令行解析 | ✅ 深度集成 |
| **配置** | ❌ 命令行参数 | ✅ 统一配置 |
| **调试** | ❌ 日志分离 | ✅ 统一日志 |

### 8.2 功能优势

1. **更智能的去重**
   - 集成 `DeduplicationKeyGenerator`
   - 避免重复扫描同一端点

2. **更好的字典管理**
   - 自动利用已收集的参数
   - 支持全局自定义字典
   - 内置分级字典（small/medium/large）

3. **更灵活的控制**
   - 实时进度显示
   - 支持暂停/恢复
   - 细粒度配置

4. **更强的扩展性**
   - 可以添加新的异常检测规则
   - 可以自定义参数提取器
   - 可以集成机器学习模型

---

## 9. 实施计划

### 9.1 立即开始

```bash
# 创建目录结构
mkdir -p src/main/java/com/xprobe/scanner/arjun/{core,http,model,config}

# 开始实现核心类
# 1. 数据模型（最简单）
# 2. BurpHttpRequester（依赖Burp API）
# 3. ResponseBaseline（核心算法）
# 4. AnomalyDetector（核心算法）
# 5. ParamDiscoveryEngine（主控逻辑）
```

### 9.2 测试策略

1. **单元测试**：每个核心类独立测试
2. **集成测试**：完整流程测试
3. **性能测试**：大字典压力测试
4. **对比测试**：与原版Arjun对比准确率

---

## 10. 总结

### ✅ 核心要点

1. **纯Java实现** - 无外部依赖
2. **算法保留** - 保持Arjun的核心检测逻辑
3. **深度集成** - 充分利用Burp API和现有架构
4. **性能优化** - 并发、缓存、智能分块

### 📝 下一步

1. 创建目录结构
2. 实现数据模型类
3. 实现核心算法（ResponseBaseline + AnomalyDetector）
4. 实现主控逻辑（ParamDiscoveryEngine）
5. 集成到现有系统
6. 测试和优化

---

**文档创建时间**: 2025-10-02  
**版本**: 1.0  
**作者**: XProbe Team


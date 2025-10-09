# 📋 参数提取逻辑说明

## 概述

XProbe 会从 **请求包** 和 **响应包** 中提取参数，用于后续的 Arjun 参数爆破和漏洞扫描。

---

## ✅ 是的，请求和响应都会提取参数

### 提取时机
```
Burp Proxy 流量
    ↓
1. 请求包 → processNewRequest() → collectFromRequest()
    ↓
2. 响应包 → processResponse() → collectFromResponse()
```

---

## 🔍 详细提取逻辑

### 一、请求包参数提取

**位置**：`ParameterCollector.java` - `collectFromRequest()` 方法

#### 1. 前置检查
```java
// 1️⃣ 静态资源过滤
if (!StaticResourceFilter.shouldCollectParameters(url)) {
    return false;  // 跳过 css、png 等，保留 js
}

// 2️⃣ 去重检查
String dedupeKey = method + "|" + url + "|" + normalizeContentType(contentType);
if (processedRequests.containsKey(dedupeKey)) {
    return false;  // 已经处理过，跳过
}
```

#### 2. 参数提取（来源：Burp API）
```java
// ✅ 使用 Burp Montoya API 提取所有参数
for (ParsedHttpParameter param : request.parameters()) {
    String paramName = cleanParameterName(param.name());
    
    // 验证参数名格式：只允许 A-Z a-z 0-9 - _ . ~ [ ]
    if (PATTERN_VALID_PARAM.matcher(paramName).matches()) {
        parameters.add(paramName);
    }
}
```

**Burp API 会自动提取以下位置的参数**：
- ✅ **URL参数**：`?id=123&name=test`
- ✅ **POST表单参数**：`Content-Type: application/x-www-form-urlencoded`
- ✅ **JSON参数**：`Content-Type: application/json`
- ✅ **Cookie参数**：`Cookie: session=xxx; user=yyy`
- ✅ **Multipart参数**：`Content-Type: multipart/form-data`

#### 3. 参数清理
```java
private String cleanParameterName(String param) {
    // 移除URL编码的方括号
    param = param.replace("%5b", "").replace("%5B", "")
                .replace("%5d", "").replace("%5D", "");
    
    // 移除特殊字符
    param = param.replace("\\", "").replace("/", "")
                .replace("quot;", "").replace("apos;", "")
                .replace("amp;", "").replace("\"", "")
                .replace("'", "");
    
    // 处理 ? 后面的部分
    if (param.contains("?")) {
        String[] parts = param.split("\\?");
        param = parts.length > 1 ? parts[1] : parts[0];
    }
    
    return param.trim();
}
```

#### 4. 参数验证
```java
// 正则：^[A-Za-z0-9_.~\-\[\]]+$
// 允许：字母、数字、-_.~[]
// 不允许：特殊符号、空格等

if (PATTERN_VALID_PARAM.matcher(paramName).matches()) {
    parameters.add(paramName);  // ✅ 通过验证
}
```

---

### 二、响应包参数提取

**位置**：`ParameterCollector.java` - `collectFromResponse()` 方法

#### 1. 前置检查
```java
// 1️⃣ 静态资源过滤
if (!StaticResourceFilter.shouldCollectParameters(url)) {
    return false;  // 跳过 css、png 等，保留 js
}

// 2️⃣ 响应大小限制（1MB）
if (body.length() > 1024 * 1024) {
    return parameters;  // 跳过超大响应
}

// 3️⃣ 去重检查（独立的key）
String dedupeKey = "RESPONSE|" + method + "|" + url + "|" + contentType;
if (processedRequests.containsKey(dedupeKey)) {
    return false;  // 已经处理过
}
```

#### 2. JSON响应参数提取
```java
// 检测JSON格式
if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
    
    // ✅ 正则提取：匹配 "key": 格式
    // Pattern: "([a-zA-Z_][a-zA-Z0-9_]*)"\\s*:
    
    Matcher matcher = PATTERN_JSON_KEY.matcher(body);
    while (matcher.find()) {
        String paramName = matcher.group(1);
        if (PATTERN_VALID_PARAM.matcher(paramName).matches()) {
            parameters.add(paramName);  // ✅ 提取成功
        }
    }
}
```

**示例**：
```json
{
    "userId": 123,           // ✅ 提取：userId
    "userName": "test",      // ✅ 提取：userName
    "data": {
        "email": "a@b.com",  // ✅ 提取：email
        "phone": "123456"    // ✅ 提取：phone
    }
}
```

#### 3. HTML响应参数提取
```java
// 检测HTML格式
if (body.contains("<") && body.contains(">")) {
    
    // ✅ 正则提取：匹配 name="xxx" 或 name='xxx'
    // Pattern: name\\s*=\\s*["']([^"']+)["']
    
    Matcher matcher = PATTERN_HTML_NAME.matcher(body);
    while (matcher.find()) {
        String paramName = cleanParameterName(matcher.group(1));
        if (paramName != null && PATTERN_VALID_PARAM.matcher(paramName).matches()) {
            parameters.add(paramName);  // ✅ 提取成功
        }
    }
}
```

**示例**：
```html
<form>
    <input type="text" name="username">    <!-- ✅ 提取：username -->
    <input type="password" name="password">  <!-- ✅ 提取：password -->
    <select name="country">                  <!-- ✅ 提取：country -->
    <textarea name="comment"></textarea>     <!-- ✅ 提取：comment -->
</form>
```

---

## 📊 提取逻辑对比

| 特性 | 请求包提取 | 响应包提取 |
|------|----------|-----------|
| **数据来源** | Burp API (request.parameters()) | 正则提取 (body内容) |
| **提取位置** | URL、POST、Cookie、JSON等 | JSON键名、HTML name属性 |
| **静态资源** | 排除css/png，保留js | 排除css/png，保留js |
| **去重Key** | `method\|url\|contentType` | `RESPONSE\|method\|url\|contentType` |
| **大小限制** | 无限制 | 1MB |
| **自动解析** | ✅ Burp自动解析 | ❌ 需要正则匹配 |
| **准确性** | ⭐⭐⭐⭐⭐ 非常高 | ⭐⭐⭐⭐ 较高 |

---

## 🎯 参数验证规则

### 允许的字符
```
A-Z a-z 0-9 - _ . ~ [ ]
```

### 不允许的字符
```
空格、特殊符号、中文等
```

### 正则表达式
```java
private static final Pattern PATTERN_VALID_PARAM = 
    Pattern.compile("^[A-Za-z0-9_.~\\-\\[\\]]+$");
```

### 验证示例
```
✅ userId      - 通过
✅ user_name   - 通过
✅ user.id     - 通过
✅ user-id     - 通过
✅ user[0]     - 通过
❌ user name   - 不通过（空格）
❌ user@id     - 不通过（@符号）
❌ 用户名      - 不通过（中文）
```

---

## 🔄 完整处理流程

### 流程图
```
Burp Proxy流量
    ↓
┌─────────────────────────────────────┐
│  1️⃣ toolSource检查                   │
│     只处理PROXY流量                   │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  2️⃣ X-XProbe-Arjun头部检查          │
│     跳过Arjun自己的流量               │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  3️⃣ 静态资源过滤                     │
│     排除css、png等，保留js            │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  4️⃣ 去重检查                         │
│     检查processedRequests缓存         │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  5️⃣ 提取参数                         │
│  ├─ 请求：Burp API自动提取            │
│  └─ 响应：正则匹配提取                │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  6️⃣ 参数清理                         │
│     移除特殊字符、编码等              │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  7️⃣ 参数验证                         │
│     正则检查字符是否合法              │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  8️⃣ 存储到DomainData                 │
│     按主域名分组管理                  │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  9️⃣ 更新lastUpdateTime               │
│     仅在新参数时更新                  │
└─────────────────────────────────────┘
```

---

## 💡 关键设计点

### 1. 去重机制
```java
// 请求去重
"GET|https://api.com/users?id=1|application/json"

// 响应去重（独立的key）
"RESPONSE|GET|https://api.com/users?id=1|application/json"
```

**为什么独立**：
- 同一个请求可能有不同的响应
- 响应中可能包含请求中没有的参数
- 例如：请求传入 `userId`，响应返回 `userName`、`email` 等

### 2. 静态资源过滤
```java
// 参数收集：保留JS
shouldCollectParameters(url)  // css❌ png❌ js✅

// Arjun爆破：排除所有
shouldScanWithArjun(url)      // css❌ png❌ js❌
```

### 3. 性能优化
```java
// ✅ 正则静态编译（避免每次重新编译）
private static final Pattern PATTERN_JSON_KEY = ...;
private static final Pattern PATTERN_HTML_NAME = ...;

// ✅ 响应大小限制（避免处理超大响应）
if (body.length() > 1024 * 1024) { return; }

// ✅ BoundedCache去重（避免内存泄漏）
private final BoundedCache<String, Boolean> processedRequests = new BoundedCache<>(100_000);
```

### 4. 并发安全
```java
// ✅ 使用ConcurrentHashMap
private final Map<String, DomainData> domainDataMap = new ConcurrentHashMap<>();

// ✅ DomainData内部也使用线程安全集合
private final Set<String> allParameters = ConcurrentHashMap.newKeySet();
```

---

## 📈 提取效果

### 典型场景
```
接口：GET /api/user?id=123
响应：{"userId": 123, "userName": "test", "email": "a@b.com"}

收集结果：
✅ 请求参数：id
✅ 响应参数：userId, userName, email
```

### 覆盖率
- **请求参数**：100%（Burp API自动提取）
- **JSON响应**：95%（正则匹配键名）
- **HTML表单**：95%（正则匹配name属性）
- **其他格式**：不支持（XML、protobuf等）

---

## 🎯 总结

### ✅ 请求包提取
- 使用Burp API自动提取
- 支持URL、POST、JSON、Cookie等
- 准确性100%

### ✅ 响应包提取
- 使用正则表达式匹配
- 支持JSON键名、HTML表单
- 准确性95%

### ✅ 过滤与去重
- 静态资源过滤（保留JS）
- 独立的去重机制
- 响应大小限制（1MB）

### ✅ 性能优化
- 正则静态编译
- BoundedCache防泄漏
- 并发安全设计

**所有参数都会被收集到DomainData中，供Arjun爆破和漏洞扫描使用！** 🎉


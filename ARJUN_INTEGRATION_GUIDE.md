# Arjun 集成完整指南

## 📌 核心修复（重要！）

### 🔴 问题：之前的实现丢失了原始请求的参数

**错误示例（修复前）**：
```bash
# ❌ 错误：只使用 -w，丢失了原始参数
arjun -u "https://api.example.com/user?id=123" \
      -m POST \
      --headers "Authorization: Bearer xxx..." \
      -w new_params.txt \
      -oB 127.0.0.1:8080
# 结果：原始的 id=123 参数丢失！
```

**正确实现（修复后）**：
```bash
# ✅ 正确：使用 --include 保留原始参数
arjun -u "https://api.example.com/user?id=123" \
      -m POST \
      --headers "Authorization: Bearer xxx..." \
      --include "id,token,session_id" \    # 保留原有参数
      -w new_params.txt \                   # 测试新参数
      -oB 127.0.0.1:8080
# 结果：每次探测都会保留 id, token, session_id 参数
```

---

## 🎯 参数关系核心理解

### 参数合并机制

```
最终请求 = 原始参数（--include）+ 当前测试参数（-w字典中的某一个）

示例：
原始参数：id=123, token=abc
测试参数：admin=1 （从字典中逐个测试）

Arjun发送的请求：
Request 1: ?id=123&token=abc&admin=1
Request 2: ?id=123&token=abc&debug=1
Request 3: ?id=123&token=abc&role=admin
...
```

### 两个关键参数的区别

| 参数 | 作用 | 值的来源 | 是否变化 |
|------|------|---------|---------|
| `--include` | 保留原有参数 | 从原始请求提取 | ❌ 每次请求都保持不变 |
| `-w` | 测试新参数 | 字典文件 | ✅ 逐个测试字典中的参数 |

---

## 📋 Arjun 命令行参数详解

### 核心参数

```bash
arjun \
  -u "https://api.example.com/user" \     # 目标URL
  -m POST \                                # HTTP方法：GET/POST/JSON/XML
  --headers "Authorization: xxx\nCookie: yyy" \  # Headers（\n分隔）
  --include "user_id,token,session_id" \  # 🔴 原有参数（保持不变）
  -w params.txt \                          # 🔴 测试参数字典
  -t 5 \                                   # 线程数
  -T 15 \                                  # 超时（秒）
  --rate-limit 9999 \                      # 速率限制
  -oB 127.0.0.1:8080 \                    # 发送到Burp代理
  --disable-redirects \                    # 禁用重定向
  -q                                       # 安静模式
```

### Method 映射规则

| HTTP Method | Content-Type | Arjun -m 参数 | 实际行为 |
|-------------|--------------|--------------|---------|
| GET | - | `GET` | 参数附加到URL: `?param=value` |
| POST | `application/x-www-form-urlencoded` | `POST` | 参数放在Body: `param=value` |
| POST | `application/json` | `JSON` | 参数转为JSON: `{"param": "value"}` |
| POST | `application/xml` | `XML` | 参数放在 `$arjun$` 占位符处 |

---

## 🔍 参数提取逻辑（Java实现）

### extractExistingParameters() 方法

```java
private String extractExistingParameters(HttpRequest request) {
    Set<String> paramNames = new LinkedHashSet<>();
    
    // 1. 提取URL参数（GET参数）
    // 示例：?id=123&page=1 → "id,page"
    for (var param : request.parameters()) {
        paramNames.add(param.name());
    }
    
    // 2. 提取POST表单参数
    // Content-Type: application/x-www-form-urlencoded
    // 已自动包含在 request.parameters() 中
    
    // 3. 提取JSON字段名
    // Content-Type: application/json
    // 示例：{"user_id": 123, "token": "xxx"} → "user_id,token"
    if (contentType.contains("application/json")) {
        String body = request.bodyToString();
        extractJsonFieldNames(body, paramNames); // 使用正则提取
    }
    
    // 返回逗号分隔的字符串：user_id,token,page
    return String.join(",", paramNames);
}
```

### JSON字段提取（简单正则）

```java
private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
    // 匹配模式: "字段名":\s*值
    Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:");
    Matcher matcher = pattern.matcher(jsonBody);
    
    while (matcher.find()) {
        String fieldName = matcher.group(1);
        paramNames.add(fieldName);
    }
}
```

---

## 📝 完整场景示例

### 场景1：GET请求（URL已有参数）

**原始请求**：
```http
GET /api/user?id=123&page=1 HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJhbGc...
```

**Arjun命令**：
```bash
arjun \
  -u "https://api.example.com/api/user?id=123&page=1" \
  -m GET \
  --headers "Authorization: Bearer eyJhbGc...\nX-XProbe-Arjun: 1" \
  --include "id,page" \              # 🔴 保留原有参数
  -w collected_params.txt \          # 测试字典中的参数
  -oB 127.0.0.1:8080
```

**Arjun实际发送的请求**：
```http
GET /api/user?id=123&page=1&admin=1 HTTP/1.1
GET /api/user?id=123&page=1&debug=1 HTTP/1.1
GET /api/user?id=123&page=1&role=admin HTTP/1.1
...
```

---

### 场景2：POST表单（Body已有参数）

**原始请求**：
```http
POST /api/login HTTP/1.1
Host: api.example.com
Content-Type: application/x-www-form-urlencoded

username=admin&password=123456&captcha=abcd
```

**Arjun命令**：
```bash
arjun \
  -u "https://api.example.com/api/login" \
  -m POST \
  --headers "Content-Type: application/x-www-form-urlencoded\nX-XProbe-Arjun: 1" \
  --include "username,password,captcha" \  # 🔴 保留原有参数
  -w collected_params.txt \
  -oB 127.0.0.1:8080
```

**Arjun实际发送的请求**：
```http
POST /api/login HTTP/1.1
Content-Type: application/x-www-form-urlencoded

username=admin&password=123456&captcha=abcd&remember_me=1

POST /api/login HTTP/1.1
Content-Type: application/x-www-form-urlencoded

username=admin&password=123456&captcha=abcd&token=xxx
...
```

---

### 场景3：POST JSON（带JWT认证）

**原始请求**：
```http
POST /api/user/update HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{"user_id": 123, "name": "Alice", "email": "alice@example.com"}
```

**Arjun命令**：
```bash
arjun \
  -u "https://api.example.com/api/user/update" \
  -m JSON \
  --headers "Authorization: Bearer eyJhbGc...\nX-XProbe-Arjun: 1" \
  --include "user_id,name,email" \  # 🔴 保留原有JSON字段
  -w collected_params.txt \
  -oB 127.0.0.1:8080
```

**Arjun实际发送的请求**：
```http
POST /api/user/update HTTP/1.1
Content-Type: application/json
Authorization: Bearer eyJhbGc...

{"user_id": 123, "name": "Alice", "email": "alice@example.com", "role": "admin"}

POST /api/user/update HTTP/1.1
Content-Type: application/json

{"user_id": 123, "name": "Alice", "email": "alice@example.com", "is_admin": true}
...
```

---

### 场景4：POST XML（SOAP接口）

**原始请求**：
```http
POST /api/soap HTTP/1.1
Host: api.example.com
Content-Type: application/xml

<soapenv:Envelope>
  <soapenv:Body>
    <userId>123</userId>
    <token>abc</token>
    $arjun$
  </soapenv:Body>
</soapenv:Envelope>
```

**Arjun命令**：
```bash
arjun \
  -u "https://api.example.com/api/soap" \
  -m XML \
  --headers "Content-Type: application/xml\nX-XProbe-Arjun: 1" \
  --include "userId,token" \  # 🔴 保留原有XML字段
  -w collected_params.txt \
  -oB 127.0.0.1:8080
```

**注意**：XML模式需要在请求模板中预留 `$arjun$` 占位符！

---

### 场景5：手动添加端点（多种组合）

**用户手动添加**：`https://api.example.com/api/order`

**插件自动尝试3种组合**：

```bash
# 组合1：GET + form
arjun -u "https://api.example.com/api/order" \
      -m GET \
      --include "" \    # 手动添加的URL通常没有原始参数
      -w collected_params.txt

# 组合2：POST + form
arjun -u "https://api.example.com/api/order" \
      -m POST \
      --include "" \
      -w collected_params.txt

# 组合3：POST + json
arjun -u "https://api.example.com/api/order" \
      -m JSON \
      --include "" \
      -w collected_params.txt
```

---

## 💻 Java代码示例

### 完整的Arjun集成类

```java
public class ArjunIntegration {
    
    /**
     * 构建Arjun命令
     */
    private List<String> buildArjunCommand(HttpRequest request, String dictFile) {
        List<String> command = new ArrayList<>();
        
        // 1. 提取原始参数（--include）
        String existingParams = extractExistingParameters(request);
        
        // 2. 基础命令
        command.add(config.getArjunPath());
        command.add("-u");
        command.add(request.url());
        
        // 3. HTTP方法映射
        String arjunMethod = mapMethod(request.method(), getContentType(request));
        command.add("-m");
        command.add(arjunMethod);
        
        // 4. Headers
        command.add("--headers");
        command.add(buildHeaders(request));
        
        // 5. 🔴 保留原始参数（关键！）
        if (!existingParams.isEmpty()) {
            command.add("--include");
            command.add(existingParams);  // "user_id,token,page"
        }
        
        // 6. 🔴 测试参数字典
        command.add("-w");
        command.add(dictFile);
        
        // 7. 发送到Burp
        command.add("-oB");
        command.add(config.getBurpProxyAddress());
        
        return command;
    }
    
    /**
     * 提取原始参数
     */
    private String extractExistingParameters(HttpRequest request) {
        Set<String> paramNames = new LinkedHashSet<>();
        
        // URL参数 + Body参数
        for (var param : request.parameters()) {
            paramNames.add(param.name());
        }
        
        // JSON字段
        String contentType = getContentType(request);
        if (contentType != null && contentType.contains("application/json")) {
            String body = request.bodyToString();
            extractJsonFieldNames(body, paramNames);
        }
        
        return String.join(",", paramNames);
    }
    
    /**
     * JSON字段提取
     */
    private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:");
        Matcher matcher = pattern.matcher(jsonBody);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
    }
}
```

---

## 🔧 常见问题

### Q1: Headers为什么用 `\n` 分隔？

**A**: Arjun的 `--headers` 参数要求使用换行符分隔多个header。

```bash
# ✅ 正确
--headers "Authorization: Bearer xxx\nCookie: session=yyy\nX-Custom: zzz"

# ❌ 错误
--headers "Authorization: Bearer xxx, Cookie: session=yyy"
```

### Q2: `--include` 参数的值从哪里来？

**A**: 从原始请求中提取：
- GET请求：URL参数（Query String）
- POST表单：Body参数
- POST JSON：JSON字段名
- 合并去重后用逗号连接

### Q3: 如何确保不重复探测？

**A**: 插件使用去重机制：
```
去重Key = method + host + contentType + endpoint + 已探测参数集合
```

### Q4: 为什么要发送到Burp代理（-oB）？

**A**: 
1. Arjun探测的结果会自动进入Burp的被动扫描
2. 可以在Burp的HTTP History中看到所有探测请求
3. 便于进一步分析和手动测试

### Q5: 流量如何标记为Arjun发起的？

**A**: 添加自定义Header：
```java
headersBuilder.append("X-XProbe-Arjun: 1\n");
```

插件会识别这个Header，跳过对Arjun流量的重复收集。

---

## ✅ 修复前后对比

### 修复前（❌ 错误）

```java
// ❌ 只使用 -w，丢失原始参数
command.add("-w");
command.add(dictFile);

// 结果：原始请求的 token, session_id 等参数全部丢失
// 导致探测时可能因为认证失败而无法发现参数
```

### 修复后（✅ 正确）

```java
// ✅ 1. 提取原始参数
String existingParams = extractExistingParameters(request);

// ✅ 2. 使用 --include 保留原始参数
if (!existingParams.isEmpty()) {
    command.add("--include");
    command.add(existingParams);  // "token,session_id,user_id"
}

// ✅ 3. 使用 -w 测试新参数
command.add("-w");
command.add(dictFile);

// 结果：每次探测都会保留 token, session_id 等参数
// 确保探测过程中认证有效，能准确发现隐藏参数
```

---

## 📚 参考资源

- Arjun 官方仓库: https://github.com/s0md3v/Arjun
- Arjun 源码中的参数合并逻辑: `core/requester.py` → `payload.update(request['include'])`
- Burp Montoya API: https://portswigger.github.io/burp-extensions-montoya-api/

---

## 🎯 总结

### 关键要点

1. **必须使用 `--include`** 保留原始请求的参数
2. **参数合并** = 原始参数（不变）+ 测试参数（逐个测试）
3. **参数提取** 需要处理 GET、POST表单、JSON 三种类型
4. **Method映射** 需要根据Content-Type正确转换
5. **流量标记** 使用 `X-XProbe-Arjun` Header避免重复收集

### 实现检查清单

- [x] 提取URL参数（GET）
- [x] 提取Body参数（POST表单）
- [x] 提取JSON字段（POST JSON）
- [x] 使用 `--include` 参数
- [x] 使用 `-w` 参数
- [x] Headers正确换行
- [x] Method正确映射
- [x] 添加流量标记
- [x] 发送到Burp代理

---

**文档创建时间**: 2025-10-01  
**最后更新**: 2025-10-01  
**版本**: 1.0


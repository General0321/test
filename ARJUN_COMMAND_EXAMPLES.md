# Arjun 命令构造示例

根据代码实际实现，以下是各种场景下Arjun构造出来的完整命令。

---

## 场景1️⃣：普通GET请求（带Cookie认证）

### 原始SiteMap/Proxy流量
```http
GET /api/user?id=123&page=1 HTTP/1.1
Host: example.com
Cookie: session=abc123def456; user_id=789
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)
Accept: application/json, text/plain, */*
Referer: https://example.com/dashboard
```

### 收集到的参数
```
主域名: example.com
接口: /api/user
已收集参数: id, page, user, name, email (假设从其他接口收集到)
全局参数: api_key, token, callback, debug (默认字典)
```

### 临时字典文件 `/tmp/xprobe_arjun_1727780001.txt`
```
id
page
user
name
email
api_key
token
callback
debug
```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://example.com/api/user?id=123&page=1" \
  -m GET \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Cookie: session=abc123def456; user_id=789
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)
Accept: application/json, text/plain, */*
Referer: https://example.com/dashboard
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780001.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 预期行为
- Arjun会尝试字典中的所有参数：`?id=123&page=1&user=fuzz&name=fuzz...`
- 发现的有效参数会发送到Burp代理（127.0.0.1:8080）
- 带着完整的Cookie和Headers

---

## 场景2️⃣：POST表单提交（登录接口）

### 原始SiteMap/Proxy流量
```http
POST /auth/login HTTP/1.1
Host: example.com
Cookie: csrf_token=xyz789
Content-Type: application/x-www-form-urlencoded
Content-Length: 35
Origin: https://example.com
Referer: https://example.com/login

username=admin&password=test123
```

### 收集到的参数
```
主域名: example.com
接口: /auth/login
已收集参数: username, password, email, phone (从多个接口收集)
全局参数: token, api_key, callback
```

### 临时字典文件 `/tmp/xprobe_arjun_1727780002.txt`
```
username
password
email
phone
token
api_key
callback
```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://example.com/auth/login" \
  -m POST \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Cookie: csrf_token=xyz789
Content-Type: application/x-www-form-urlencoded
Origin: https://example.com
Referer: https://example.com/login
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780002.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 预期行为
- Arjun会POST测试所有参数：`username=fuzz&password=fuzz&email=fuzz...`
- 保留原始的CSRF token
- 发现的参数（如`remember_me`, `redirect_url`等）会发送到Burp

---

## 场景3️⃣：POST JSON API（带JWT认证）

### 原始SiteMap/Proxy流量
```http
POST /api/v1/users HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json; charset=utf-8
Accept: application/json
X-Api-Version: 2.0
User-Agent: MyApp/1.0

{"name":"test","email":"test@example.com","role":"user"}
```

### 收集到的参数
```
主域名: example.com
接口: /api/v1/users
已收集参数: name, email, role, user_id, status
全局参数: api_key, token, debug, test
关键词（如果启用）: admin, manager, active
```

### 临时字典文件 `/tmp/xprobe_arjun_1727780003.txt`
```
name
email
role
user_id
status
api_key
token
debug
test
admin
manager
active
```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://api.example.com/api/v1/users" \
  -m JSON \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json; charset=utf-8
Accept: application/json
X-Api-Version: 2.0
User-Agent: MyApp/1.0
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780003.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 预期行为
- Arjun会以JSON格式测试：`{"name":"fuzz","email":"fuzz",...}`
- 保留JWT认证头
- 保留自定义API版本头

---

## 场景4️⃣：手动添加端点（尝试3种组合）

### 用户手动输入
```
https://target.com/admin/config
```

### 从Proxy收集到的该主域名参数
```
主域名: target.com
已收集参数: id, user, action, token, key (从其他接口收集)
全局参数: api_key, debug, test, admin
```

### 组合1：GET + form

**临时字典** `/tmp/xprobe_arjun_1727780004.txt`
```
id
user
action
token
key
api_key
debug
test
admin
```

**命令**
```bash
arjun \
  -u "https://target.com/admin/config" \
  -m GET \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Content-Type: application/x-www-form-urlencoded
User-Agent: Mozilla/5.0
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780004.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 组合2：POST + form

**命令**
```bash
arjun \
  -u "https://target.com/admin/config" \
  -m POST \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Content-Type: application/x-www-form-urlencoded
User-Agent: Mozilla/5.0
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780005.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 组合3：POST + json

**命令**
```bash
arjun \
  -u "https://target.com/admin/config" \
  -m JSON \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Content-Type: application/json
User-Agent: Mozilla/5.0
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780006.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

---

## 场景5️⃣：POST XML请求（SOAP接口）

### 原始SiteMap/Proxy流量
```http
POST /soap/service HTTP/1.1
Host: soap.example.com
Content-Type: application/xml; charset=utf-8
SOAPAction: "http://example.com/GetUser"
Authorization: Basic YWRtaW46cGFzc3dvcmQ=

<?xml version="1.0"?>
<soap:Envelope>
  <soap:Body>
    <GetUser>
      <userId>123</userId>
    </GetUser>
  </soap:Body>
</soap:Envelope>
```

### 收集到的参数
```
主域名: example.com
接口: /soap/service
已收集参数: userId, userName, userEmail
```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://soap.example.com/soap/service" \
  -m XML \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Content-Type: application/xml; charset=utf-8
SOAPAction: \"http://example.com/GetUser\"
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780007.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

---

## 场景6️⃣：带多个Cookie的请求

### 原始SiteMap/Proxy流量
```http
GET /dashboard HTTP/1.1
Host: app.example.com
Cookie: session_id=abc123; user_token=xyz789; preferences=dark_mode; lang=en_US
Authorization: Bearer token123
X-CSRF-Token: csrf_xyz
User-Agent: Mozilla/5.0

```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://app.example.com/dashboard" \
  -m GET \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Cookie: session_id=abc123; user_token=xyz789; preferences=dark_mode; lang=en_US
Authorization: Bearer token123
X-CSRF-Token: csrf_xyz
User-Agent: Mozilla/5.0
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780008.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

### 关键点
- ✅ 所有Cookie完整保留
- ✅ CSRF Token保留
- ✅ Authorization保留

---

## 场景7️⃣：GraphQL API请求

### 原始SiteMap/Proxy流量
```http
POST /graphql HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGc...
X-Request-ID: req-12345

{"query":"query { user(id: 123) { name email } }"}
```

### Arjun命令（实际执行）
```bash
arjun \
  -u "https://api.example.com/graphql" \
  -m JSON \
  -t 5 \
  -T 15 \
  --rate-limit 9999 \
  --headers "Content-Type: application/json
Authorization: Bearer eyJhbGc...
X-Request-ID: req-12345
X-XProbe-Arjun: 1" \
  -w /tmp/xprobe_arjun_1727780009.txt \
  -oB 127.0.0.1:8080 \
  --disable-redirects \
  -q
```

---

## 🔍 命令参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| `-u` | URL | 完整的目标URL（包括query参数） |
| `-m` | GET/POST/JSON/XML | HTTP方法（自动映射） |
| `-t` | 5 | 线程数（配置中设置） |
| `-T` | 15 | 超时秒数（配置中设置） |
| `--rate-limit` | 9999 | 速率限制（几乎无限制） |
| `--headers` | Headers字符串 | 所有原始Headers（换行分隔） |
| `-w` | 字典文件路径 | 临时生成的参数字典 |
| `-oB` | 127.0.0.1:8080 | **最关键**：发送结果到Burp代理 |
| `--disable-redirects` | - | 禁用重定向 |
| `-q` | - | 安静模式（减少输出） |

---

## ⚙️ 配置参数影响

### ExternalToolConfig.java 默认配置
```java
arjunPath = "arjun"
burpProxyAddress = "127.0.0.1:8080"
threadCount = 5
timeout = 15
sendToBurp = true
enableVerboseOutput = false
```

### 如果修改配置
```java
threadCount = 10           // -t 10
timeout = 30               // -T 30
burpProxyAddress = "127.0.0.1:9090"  // -oB 127.0.0.1:9090
enableVerboseOutput = true // 不加 -q
```

**命令会变成**：
```bash
arjun -u "..." -m GET -t 10 -T 30 --headers "..." -w "..." -oB 127.0.0.1:9090 --disable-redirects
# 注意：没有 -q 参数了
```

---

## 🎯 特殊标记

### X-XProbe-Arjun 头的作用

所有Arjun发出的请求都会带上：
```
X-XProbe-Arjun: 1
```

**作用**：
1. **避免重复扫描**：你的被动扫描器检测到这个头，会跳过这些请求
2. **流量识别**：在Burp中可以通过这个头过滤Arjun流量

代码位置：
```java
// ArjunIntegration.java (行175)
headersBuilder.append("X-XProbe-Arjun: 1\n");

// RealtimeScannerRefactored.java (行62-66)
for (var header : request.headers()) {
    if ("X-XProbe-Arjun".equalsIgnoreCase(header.name())) {
        return;  // 跳过Arjun流量
    }
}
```

---

## 📊 总结对比

| 场景 | Method映射 | Headers继承 | 字典来源 |
|------|-----------|------------|---------|
| GET请求 | GET | ✅ 全部 | 收集参数+全局参数 |
| POST表单 | POST | ✅ 全部 | 收集参数+全局参数 |
| POST JSON | JSON | ✅ 全部 | 收集参数+关键词+全局参数 |
| POST XML | XML | ✅ 全部 | 收集参数+全局参数 |
| 手动添加 | GET/POST/JSON | ⚠️ 默认Headers | 收集参数+全局参数（3种组合） |

**关键结论**：
- ✅ 从SiteMap/Proxy获取的流量，**完整继承**所有特征
- ✅ 手动添加的端点，使用**默认Headers**，但会尝试3种组合
- ✅ 所有场景都会添加 `X-XProbe-Arjun: 1` 标记


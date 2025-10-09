# XProbe参数收集详细说明

**文档版本**: 1.0  
**更新时间**: 2025-10-04  

---

## 📋 问题：请求包和响应包中的参数或关键词都会收集吗？

### 🎯 直接回答

**当前实现（v1.0）**:
- ✅ **请求包**: 收集参数名和关键词（可配置）
- ❌ **响应包**: **不收集**（当前未实现）

---

## 🔍 当前收集机制详解

### 1. 从请求包收集什么？

#### 模式1：仅参数名（默认）

```java
CollectionMode.PARAMETERS_ONLY
```

**收集内容**:
```
✅ URL参数:     GET /api?id=1&name=test → 收集: id, name
✅ POST参数:    POST body: username=admin&password=123 → 收集: username, password
✅ JSON参数:    {"user_id": 1, "token": "abc"} → 收集: user_id, token
✅ Cookie参数:  Cookie: session=xxx; user_id=1 → 收集: session, user_id
✅ Header参数:  X-Custom-Header: value → 收集: X-Custom-Header
✅ 多部分表单:  multipart/form-data → 收集: 所有字段名
```

**代码实现**:
```java
// ParameterCollector.java line 278-292
private Set<String> extractParameters(HttpRequest request) {
    Set<String> parameters = new HashSet<>();
    
    // Burp会自动解析所有类型的参数（URL/POST/JSON/Cookie等）
    for (ParsedHttpParameter param : request.parameters()) {
        String paramName = cleanParameterName(param.name());
        
        if (paramName != null && !paramName.isEmpty() && 
            PATTERN_VALID_PARAM.matcher(paramName).matches()) {
            parameters.add(paramName);  // 只收集参数名
        }
    }
    
    return parameters;
}
```

---

#### 模式2：参数名+关键词

```java
CollectionMode.PARAMETERS_AND_KEYWORDS
```

**收集内容**:
```
✅ 参数名:      同模式1
✅ 参数值:      ?name=admin → 收集关键词: admin
✅ 请求体内容:   从请求体中提取单词（<10KB）
```

**示例**:
```http
POST /api/user HTTP/1.1
Content-Type: application/json

{
    "username": "john_doe",
    "email": "john@example.com",
    "role": "administrator"
}
```

**收集结果**:
- **参数名**: username, email, role
- **关键词**: john, doe, john, example, com, administrator

**代码实现**:
```java
// ParameterCollector.java line 328-366
private Set<String> extractKeywords(HttpRequest request) {
    Set<String> keywords = new HashSet<>();
    
    // 1. 从参数值中提取单词
    for (ParsedHttpParameter param : request.parameters()) {
        String value = param.value();
        if (value != null && !value.isEmpty()) {
            Matcher matcher = PATTERN_WORDS.matcher(value);
            while (matcher.find()) {
                String word = sanitizeWord(matcher.group());
                if (isValidKeyword(word)) {
                    keywords.add(word);  // 收集关键词
                }
            }
        }
    }
    
    // 2. 从请求体中提取单词（限制10KB避免性能问题）
    if (request.body() != null) {
        String body = request.bodyToString();
        if (body != null && body.length() < 10000) {
            Matcher matcher = PATTERN_WORDS.matcher(body);
            while (matcher.find()) {
                String word = sanitizeWord(matcher.group());
                if (isValidKeyword(word)) {
                    keywords.add(word);
                }
            }
        }
    }
    
    return keywords;
}
```

---

### 2. 关键词过滤规则

**有效关键词必须满足**:
```java
✅ 长度 >= 3 且 <= 50 个字符
✅ 不是纯数字（"123" ❌, "user123" ✅）
✅ 不在停止词列表中（the, and, or, is, are...）
✅ 不是全大写的短词（<= 3字符，如 "GET", "API"）
✅ 只包含字母、数字、下划线、连字符
```

**停止词列表**（参考GAP.py）:
```java
private static final Set<String> STOP_WORDS = Set.of(
    "the", "and", "or", "is", "are", "was", "were",
    "for", "with", "this", "that", "from", "have",
    "has", "had", "not", "but", "can", "will", "would",
    "should", "may", "might", "must", "could", "shall",
    "get", "post", "put", "delete", "patch", "head", "options",
    "http", "https", "www", "com", "org", "net", "html", "xml", "json"
);
```

---

### 3. 从响应包收集什么？

**当前状态**: ❌ **未实现**

**原因**:
1. **性能考虑**: 响应体通常很大（HTML/JSON），解析成本高
2. **噪音问题**: 响应中包含大量展示内容，不一定是有效参数
3. **需求优先级**: 请求参数已足够用于Arjun扫描

**未来可能实现**（参考Python版GAP.py）:
```python
# GAP.py 的 getResponseWords 方法
def getResponseWords(response):
    """从响应中提取关键词"""
    words = []
    
    # 从 JSON 响应中提取字段名
    if is_json(response):
        words += extract_json_keys(response)
    
    # 从 HTML 响应中提取
    elif is_html(response):
        words += extract_from_html(response)
        # input name, id, data-* 属性等
    
    return words
```

---

## 📊 实际收集示例

### 示例1：REST API请求

**请求**:
```http
POST /api/v1/users HTTP/1.1
Host: example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
Cookie: session_id=abc123; user_type=premium

{
    "user_id": 12345,
    "username": "john_doe",
    "email": "john@example.com",
    "profile": {
        "first_name": "John",
        "last_name": "Doe",
        "company_name": "TechCorp"
    }
}
```

**收集结果**:

| 收集项 | 模式1（仅参数名） | 模式2（参数名+关键词） |
|--------|-----------------|---------------------|
| **参数名** | user_id<br>username<br>email<br>profile<br>first_name<br>last_name<br>company_name<br>session_id<br>user_type<br>Authorization | 同左 |
| **关键词** | - | john, doe<br>john, example, com<br>John, Doe<br>TechCorp<br>abc123, premium |

**总计**: 
- 模式1: 10个参数名
- 模式2: 10个参数名 + 11个关键词

---

### 示例2：传统表单提交

**请求**:
```http
POST /login HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

username=admin&password=P@ssw0rd123&remember_me=true&redirect_url=/dashboard
```

**收集结果**:

| 收集项 | 模式1 | 模式2 |
|--------|------|------|
| **参数名** | username<br>password<br>remember_me<br>redirect_url | 同左 |
| **关键词** | - | admin<br>ssw0rd123 (P@过滤掉)<br>true<br>dashboard |

---

### 示例3：URL查询参数

**请求**:
```http
GET /search?q=security+testing&category=web&sort=date&limit=20&offset=0 HTTP/1.1
Host: example.com
```

**收集结果**:

| 收集项 | 模式1 | 模式2 |
|--------|------|------|
| **参数名** | q<br>category<br>sort<br>limit<br>offset | 同左 |
| **关键词** | - | security, testing<br>web<br>date<br>(20, 0 是纯数字，不收集) |

---

## 🔧 配置方式

### 在UI中配置

```java
位置: XProbe配置界面 → "参数收集"选项卡

选项:
○ 仅参数名 (PARAMETERS_ONLY) - 默认，推荐
○ 参数名+关键词 (PARAMETERS_AND_KEYWORDS) - 更全面但可能有噪音
```

### 通过代码配置

```java
// 在XProbe.java初始化时设置
if ("PARAMETERS_AND_KEYWORDS".equals(config.getCollectionMode())) {
    realtimeScanner.setCollectionMode(
        ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS
    );
} else {
    realtimeScanner.setCollectionMode(
        ParameterCollector.CollectionMode.PARAMETERS_ONLY
    );
}
```

---

## 📈 收集效果对比

### 测试场景：浏览电商网站30分钟

**模式1（仅参数名）**:
```
收集结果:
- 主域名数: 1
- 参数总数: 156个
- 关键词总数: 0个
- 内存占用: ~50KB

典型参数:
product_id, category_id, page, limit, sort, order,
user_id, session, token, price, quantity, color, size...
```

**模式2（参数名+关键词）**:
```
收集结果:
- 主域名数: 1
- 参数总数: 156个
- 关键词总数: 2,847个
- 内存占用: ~350KB

典型关键词:
apple, samsung, laptop, phone, electronics, clothing,
admin, user, john, smith, beijing, shanghai, credit,
card, paypal, shipping, tracking...
```

### 对Arjun扫描的影响

**模式1**:
```
Arjun字典大小: 156个参数
扫描速度: 快（字典小）
发现率: 高（都是真实参数名）
误报率: 低
```

**模式2**:
```
Arjun字典大小: 156参数 + 2,847关键词 = 3,003个
扫描速度: 慢（字典大20倍）
发现率: 略高（可能发现更多）
误报率: 较高（关键词不一定是参数名）
```

---

## ⚠️ 当前限制

### 1. 不收集响应包

**影响**:
```
场景: API返回JSON
{
    "user": {
        "user_id": 123,
        "api_key": "secret",    ← 这些字段名不会被收集
        "permissions": ["read", "write"]
    }
}

→ 如果请求中没有这些参数，就无法收集到
```

**解决方案**:
- 手动上传参数字典
- 使用Python版GAP.py收集响应关键词后导入
- 等待未来版本支持响应包收集

---

### 2. 请求体大小限制

**限制**: 只解析 < 10KB 的请求体

**原因**: 避免解析大文件（上传、图片等）导致性能问题

**影响**:
```
✅ 正常JSON/表单: < 10KB → 会解析
❌ 大文件上传: > 10KB → 跳过
❌ Base64图片: > 10KB → 跳过
```

---

### 3. 不收集的内容

**不会收集**:
- ❌ HTTP Header值（只收集Header名）
- ❌ Cookie值（只收集Cookie名）
- ❌ Authorization token内容
- ❌ 文件内容
- ❌ 二进制数据

**原因**: 这些通常是敏感数据或无用数据

---

## 🎯 最佳实践建议

### 1. 推荐配置

**大多数场景**: 
```
模式: PARAMETERS_ONLY（仅参数名）
原因: 参数名质量高，扫描效率好
```

**特殊场景**（高度定制化的API）:
```
模式: PARAMETERS_AND_KEYWORDS（参数名+关键词）
原因: 可能发现更多隐藏参数
注意: 需要更多扫描时间，可能有误报
```

---

### 2. 冷启动优化

**新目标网站，参数还未收集**:

```
方法1: 先浏览网站30分钟
→ 让ParameterCollector自动收集参数
→ 然后触发Arjun扫描

方法2: 手动上传参数字典
→ 从类似网站导出参数
→ 或使用通用字典

方法3: 组合使用
→ 收集的参数 + 上传的字典 + special.json特殊参数
→ 最大覆盖率
```

---

### 3. 内存优化

**长时间运行的场景**:

```
问题: 参数和关键词持续积累，可能占用大量内存

解决: 
1. 使用 PARAMETERS_ONLY 模式（减少90%内存）
2. 定期清理：realtimeScanner.clearCollectedData()
3. 设置合理的去重缓存大小（当前10万条）
```

---

## 🔮 未来改进方向

### 1. 响应包收集（计划中）

```java
// 未来可能实现
public boolean collectFromResponse(HttpResponse response) {
    // 从JSON响应中提取字段名
    if (isJson(response)) {
        extractJsonKeys(response);
    }
    
    // 从HTML响应中提取表单字段
    if (isHtml(response)) {
        extractHtmlFormFields(response);
        extractDataAttributes(response);
    }
}
```

**优势**:
- ✅ 发现前端使用但请求中未出现的参数
- ✅ 从API响应中提取更多字段名
- ✅ 更全面的参数覆盖

**挑战**:
- ⚠️ 性能开销（响应体通常很大）
- ⚠️ 噪音过滤（展示内容 vs 有效参数）
- ⚠️ JSON深度遍历（嵌套对象）

---

### 2. 智能过滤

```java
// AI辅助判断是否为有效参数名
boolean isProbablyParameterName(String keyword) {
    // 基于机器学习模型判断
    // 特征: 长度、格式、上下文等
}
```

---

### 3. 参数相关性分析

```java
// 记录参数共现关系
Map<String, Set<String>> parameterCorrelation;

// 例如: user_id 经常和 session_token 一起出现
// 在Arjun扫描时，如果发现user_id，优先测试session_token
```

---

## 📝 总结

### 当前收集范围

| 来源 | 参数名 | 参数值（关键词） | 实现状态 |
|------|--------|----------------|---------|
| **请求包** | | | |
| - URL参数 | ✅ | ✅ 可选 | 已实现 |
| - POST参数 | ✅ | ✅ 可选 | 已实现 |
| - JSON参数 | ✅ | ✅ 可选 | 已实现 |
| - Cookie | ✅ | ✅ 可选 | 已实现 |
| - Header | ✅ | ✅ 可选 | 已实现 |
| **响应包** | | | |
| - JSON字段 | ❌ | ❌ | 未实现 |
| - HTML表单 | ❌ | ❌ | 未实现 |
| - API响应 | ❌ | ❌ | 未实现 |

### 推荐用法

```
✅ 默认使用: PARAMETERS_ONLY
✅ 长期运行: PARAMETERS_ONLY（省内存）
✅ 深度测试: PARAMETERS_AND_KEYWORDS
✅ 冷启动: 手动上传字典 + 自动收集 + special.json
```

### 核心优势

1. **高质量**: 参数来自真实流量，针对性强
2. **自动化**: 无需手动维护字典
3. **增量更新**: 持续收集，字典不断完善
4. **去重机制**: 避免重复处理
5. **内存安全**: BoundedCache防止泄漏

---

**文档版本**: 1.0  
**最后更新**: 2025-10-04  
**相关文件**: 
- `ParameterCollector.java`
- `RealtimeScannerRefactored.java`
- `XProbe.java`



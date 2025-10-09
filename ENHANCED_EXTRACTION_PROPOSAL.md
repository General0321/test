# 增强HTML/JS变量提取能力方案

**提案时间**: 2025-10-04  
**目标**: 提升参数收集的全面性和准确性  

---

## 📊 当前实现的不足

### HTML提取（当前能力）

**✅ 已支持**:
- input/textarea/select/button 的 name 和 id
- data-* 属性
- ng-model (Angular)
- v-model (Vue)

**❌ 缺失**:
1. HTML注释中的参数
2. meta标签的name和content
3. link标签的href参数
4. img标签的alt属性
5. 内联事件处理器（onclick, onload等）
6. 自定义属性（aria-*, role等）
7. class和id中的语义名称
8. URL查询参数（href, src中的?后参数）

---

### JavaScript提取（当前能力）

**✅ 已支持**:
- let/var/const 变量名
- 对象属性（obj.prop, obj['prop']）
- API端点参数（/api/:id）

**❌ 缺失**:
1. 函数参数名
2. 对象解构 (`const {userId, email} = data`)
3. 数组解构 (`const [first, second] = arr`)
4. 箭头函数参数
5. 多变量声明 (`let a, b, c`)
6. 换行符支持
7. JSON嵌套对象的递归提取
8. localStorage/sessionStorage键名
9. cookie名称
10. fetch/axios请求参数
11. FormData字段
12. URLSearchParams参数

---

## 🚀 增强方案

### 方案1：HTML增强（高优先级）

#### 1.1 HTML注释提取

**场景**:
```html
<!-- API endpoint: /api/users/:userId -->
<!-- TODO: add user_profile parameter -->
<!-- Debug: session_token = xxx -->
```

**实现**:
```java
// 提取HTML注释中可能的参数名
Pattern commentPattern = Pattern.compile("<!--\\s*([^>]+?)\\s*-->", Pattern.DOTALL);
Pattern paramInCommentPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]{2,})\\b");

Matcher commentMatcher = commentPattern.matcher(html);
while (commentMatcher.find()) {
    String comment = commentMatcher.group(1);
    Matcher paramMatcher = paramInCommentPattern.matcher(comment);
    while (paramMatcher.find()) {
        String param = paramMatcher.group(1);
        // 过滤常见词，只保留看起来像参数的
        if (looksLikeParameter(param)) {
            parameters.add(param);
        }
    }
}
```

**GAP.py参考**: ✅ 支持（cbWordComments选项）

---

#### 1.2 Meta标签提取

**场景**:
```html
<meta name="csrf-token" content="xxx">
<meta name="api-version" content="v1">
<meta property="og:url" content="...">
```

**实现**:
```java
// 提取meta标签的name和property属性
Pattern metaNamePattern = Pattern.compile(
    "<meta\\s+name=[\"']([^\"']+)[\"']", 
    Pattern.CASE_INSENSITIVE
);
Pattern metaPropertyPattern = Pattern.compile(
    "<meta\\s+property=[\"']([^\"']+)[\"']", 
    Pattern.CASE_INSENSITIVE
);

// 处理meta name
Matcher metaMatcher = metaNamePattern.matcher(html);
while (metaMatcher.find()) {
    String metaName = metaMatcher.group(1);
    String cleaned = cleanParameterName(metaName);
    if (isValidParameter(cleaned)) {
        parameters.add(cleaned);
    }
}

// 处理meta property (如og:title -> og_title)
Matcher propertyMatcher = metaPropertyPattern.matcher(html);
while (propertyMatcher.find()) {
    String property = propertyMatcher.group(1);
    String cleaned = cleanParameterName(property.replace(":", "_"));
    if (isValidParameter(cleaned)) {
        parameters.add(cleaned);
    }
}
```

**GAP.py参考**: ✅ 支持

---

#### 1.3 图片Alt属性提取

**场景**:
```html
<img src="user.jpg" alt="user_avatar">
<img src="icon.png" alt="notification_icon">
```

**实现**:
```java
// 提取img标签的alt属性中可能的参数名
Pattern imgAltPattern = Pattern.compile(
    "<img[^>]+alt=[\"']([^\"']+)[\"']", 
    Pattern.CASE_INSENSITIVE
);

Matcher imgMatcher = imgAltPattern.matcher(html);
while (imgMatcher.find()) {
    String altText = imgMatcher.group(1);
    // 从alt文本中提取类似参数的词
    Pattern wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]{2,})\\b");
    Matcher wordMatcher = wordPattern.matcher(altText);
    while (wordMatcher.find()) {
        String word = wordMatcher.group(1);
        if (looksLikeParameter(word)) {
            parameters.add(word);
        }
    }
}
```

**GAP.py参考**: ✅ 支持（cbWordImgAlt选项）

---

#### 1.4 内联事件处理器

**场景**:
```html
<button onclick="submitForm(userId, apiKey)">Submit</button>
<div onload="initUser(sessionToken)">...</div>
```

**实现**:
```java
// 提取onclick, onload等事件处理器中的参数
Pattern eventPattern = Pattern.compile(
    "on\\w+=[\"']([^\"']+)[\"']", 
    Pattern.CASE_INSENSITIVE
);

Matcher eventMatcher = eventPattern.matcher(html);
while (eventMatcher.find()) {
    String eventCode = eventMatcher.group(1);
    // 从事件代码中提取标识符
    Pattern identifierPattern = Pattern.compile("\\b([a-zA-Z_$][a-zA-Z0-9_$]{2,})\\b");
    Matcher idMatcher = identifierPattern.matcher(eventCode);
    while (idMatcher.find()) {
        String identifier = idMatcher.group(1);
        if (looksLikeParameter(identifier)) {
            parameters.add(identifier);
        }
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 1.5 URL参数提取（href/src中的查询参数）

**场景**:
```html
<a href="/api/users?userId=123&token=xxx">Link</a>
<script src="/js/app.js?v=1.0&cache_key=abc"></script>
```

**实现**:
```java
// 提取href和src属性中的查询参数名
Pattern urlPattern = Pattern.compile(
    "(href|src)=[\"']([^\"']*\\?[^\"']+)[\"']", 
    Pattern.CASE_INSENSITIVE
);

Matcher urlMatcher = urlPattern.matcher(html);
while (urlMatcher.find()) {
    String url = urlMatcher.group(2);
    // 提取查询参数名
    Pattern queryPattern = Pattern.compile("[?&]([a-zA-Z_][a-zA-Z0-9_]*)=");
    Matcher queryMatcher = queryPattern.matcher(url);
    while (queryMatcher.find()) {
        String paramName = queryMatcher.group(1);
        parameters.add(paramName);
    }
}
```

**GAP.py参考**: ✅ 部分支持（cbParamFromLinks）

---

### 方案2：JavaScript增强（高优先级）

#### 2.1 函数参数提取

**场景**:
```javascript
function getUserData(userId, apiKey, sessionToken) { }
const fetchUser = function(userId) { }
```

**实现**:
```java
// 提取函数参数名
Pattern functionPattern = Pattern.compile(
    "\\bfunction\\s+\\w+\\s*\\(([^)]*)\\)|" +
    "\\w+\\s*=\\s*function\\s*\\(([^)]*)\\)",
    Pattern.CASE_INSENSITIVE
);

Matcher funcMatcher = functionPattern.matcher(jsCode);
while (funcMatcher.find()) {
    String params = funcMatcher.group(1);
    if (params == null) params = funcMatcher.group(2);
    if (params != null && !params.trim().isEmpty()) {
        // 分割参数
        String[] paramArray = params.split(",");
        for (String param : paramArray) {
            String cleanParam = param.trim().split("=")[0].trim(); // 处理默认参数
            if (isValidParameter(cleanParam)) {
                parameters.add(cleanParam);
            }
        }
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 2.2 箭头函数参数

**场景**:
```javascript
const getUser = (userId) => { }
const fetchData = (apiKey, sessionToken) => { }
users.map(user => user.id)
```

**实现**:
```java
// 提取箭头函数参数
Pattern arrowPattern = Pattern.compile(
    "\\(([^)]*)\\)\\s*=>|" +      // (param1, param2) =>
    "([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=>" // param =>
);

Matcher arrowMatcher = arrowPattern.matcher(jsCode);
while (arrowMatcher.find()) {
    String params = arrowMatcher.group(1);
    if (params == null) params = arrowMatcher.group(2);
    if (params != null && !params.trim().isEmpty()) {
        String[] paramArray = params.split(",");
        for (String param : paramArray) {
            String cleanParam = param.trim();
            if (isValidParameter(cleanParam)) {
                parameters.add(cleanParam);
            }
        }
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 2.3 对象解构

**场景**:
```javascript
const {userId, email, phoneNumber} = user;
const {data: {apiKey, token}} = response;
function processUser({userId, name}) { }
```

**实现**:
```java
// 提取对象解构中的变量名
Pattern destructPattern = Pattern.compile(
    "\\{\\s*([^}]+)\\s*\\}\\s*=",
    Pattern.MULTILINE
);

Matcher destructMatcher = destructPattern.matcher(jsCode);
while (destructMatcher.find()) {
    String destructContent = destructMatcher.group(1);
    // 提取所有标识符（跳过嵌套的{}）
    Pattern identifierPattern = Pattern.compile("\\b([a-zA-Z_$][a-zA-Z0-9_$]+)\\b");
    Matcher idMatcher = identifierPattern.matcher(destructContent);
    while (idMatcher.find()) {
        String identifier = idMatcher.group(1);
        // 排除关键字
        if (!identifier.matches("^(const|let|var|function|return|if|else|for|while)$")) {
            if (isValidParameter(identifier)) {
                parameters.add(identifier);
            }
        }
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 2.4 localStorage/sessionStorage键

**场景**:
```javascript
localStorage.setItem('user_token', token);
sessionStorage.getItem('api_key');
localStorage.removeItem('session_id');
```

**实现**:
```java
// 提取localStorage和sessionStorage的键名
Pattern storagePattern = Pattern.compile(
    "(localStorage|sessionStorage)\\.(setItem|getItem|removeItem)\\([\"']([^\"']+)[\"']",
    Pattern.CASE_INSENSITIVE
);

Matcher storageMatcher = storagePattern.matcher(jsCode);
while (storageMatcher.find()) {
    String key = storageMatcher.group(3);
    String cleaned = cleanParameterName(key);
    if (isValidParameter(cleaned)) {
        parameters.add(cleaned);
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 2.5 Cookie名称

**场景**:
```javascript
document.cookie = "session_token=xxx";
getCookie('user_id');
```

**实现**:
```java
// 提取cookie名称
Pattern cookiePattern = Pattern.compile(
    "cookie\\s*=\\s*[\"']([^=\"']+)=|" +  // document.cookie = "name=value"
    "getCookie\\([\"']([^\"']+)[\"']",     // getCookie('name')
    Pattern.CASE_INSENSITIVE
);

Matcher cookieMatcher = cookiePattern.matcher(jsCode);
while (cookieMatcher.find()) {
    String cookieName = cookieMatcher.group(1);
    if (cookieName == null) cookieName = cookieMatcher.group(2);
    if (cookieName != null) {
        String cleaned = cleanParameterName(cookieName);
        if (isValidParameter(cleaned)) {
            parameters.add(cleaned);
        }
    }
}
```

**GAP.py参考**: ⚠️ 不支持（XProbe增强）

---

#### 2.6 fetch/axios参数

**场景**:
```javascript
fetch('/api/users', {
    method: 'POST',
    body: JSON.stringify({userId, email})
});

axios.get('/api/users', {
    params: {userId: 123, apiKey: 'xxx'}
});
```

**实现**:
```java
// 提取fetch/axios调用中的参数
// 这需要更复杂的JSON解析，建议使用递归提取
Pattern fetchPattern = Pattern.compile(
    "(fetch|axios\\.\\w+)\\([^,]+,\\s*\\{([^}]+)\\}",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
);

Matcher fetchMatcher = fetchPattern.matcher(jsCode);
while (fetchMatcher.find()) {
    String bodyContent = fetchMatcher.group(2);
    // 提取JSON键名
    Pattern keyPattern = Pattern.compile("\\b([a-zA-Z_$][a-zA-Z0-9_$]+)\\s*:");
    Matcher keyMatcher = keyPattern.matcher(bodyContent);
    while (keyMatcher.find()) {
        String key = keyMatcher.group(1);
        if (isValidParameter(key)) {
            parameters.add(key);
        }
    }
}
```

**GAP.py参考**: ⚠️ 部分支持（REGEX_JSNESTED）

---

#### 2.7 JSON嵌套对象递归提取（参考GAP.py）

**场景**:
```javascript
const config = {
    user: {
        profile: {
            userId: 123,
            email: 'test@example.com'
        }
    },
    api: {
        baseUrl: '/api',
        endpoints: {
            users: '/users',
            posts: '/posts'
        }
    }
};
```

**GAP.py实现**:
```python
# GAP.py使用REGEX_JSNESTED查找嵌套对象
REGEX_JSNESTED = re.compile(
    r"(?s)(^|\s?)(JSON\.stringify\(|dataLayer\.push\(|(var|let|const)\s+[\$A-Za-z0-9-_\[\]]+\s*=)\s*\{"
)

# 然后使用find_balanced_braces找到完整的{}块
def find_balanced_braces(text, start):
    # 平衡大括号，提取完整JSON
    ...

# 递归提取所有键名
def process_json_string(json_str):
    # 提取所有键名
    ...
```

**XProbe实现建议**:
```java
// 使用栈匹配平衡的大括号，提取嵌套JSON
private Set<String> extractNestedJsonKeys(String jsCode) {
    Set<String> keys = new HashSet<>();
    
    // 查找 var/let/const xxx = { 模式
    Pattern nestedPattern = Pattern.compile(
        "(var|let|const)\\s+[a-zA-Z_$][a-zA-Z0-9_$]*\\s*=\\s*\\{",
        Pattern.CASE_INSENSITIVE
    );
    
    Matcher matcher = nestedPattern.matcher(jsCode);
    while (matcher.find()) {
        int start = matcher.end() - 1; // 从 { 开始
        String jsonBlock = extractBalancedBraces(jsCode, start);
        if (jsonBlock != null) {
            // 递归提取所有键名
            extractJsonKeysRecursive(jsonBlock, keys);
        }
    }
    
    return keys;
}

// 提取平衡的大括号内容
private String extractBalancedBraces(String text, int start) {
    int depth = 0;
    StringBuilder result = new StringBuilder();
    
    for (int i = start; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '{') depth++;
        else if (c == '}') {
            depth--;
            if (depth == 0) {
                result.append(c);
                return result.toString();
            }
        }
        result.append(c);
    }
    
    return null; // 不平衡
}

// 递归提取JSON键名
private void extractJsonKeysRecursive(String json, Set<String> keys) {
    // 提取 key: value 模式
    Pattern keyPattern = Pattern.compile("([\"']?)([a-zA-Z_$][a-zA-Z0-9_$]*)\\1\\s*:");
    Matcher matcher = keyPattern.matcher(json);
    while (matcher.find()) {
        String key = matcher.group(2);
        if (isValidParameter(key)) {
            keys.add(key);
        }
    }
}
```

**GAP.py参考**: ✅ 支持（REGEX_JSNESTED + process_json_string）

---

#### 2.8 多变量声明和换行符支持

**场景**:
```javascript
// 多变量声明
let userId, email, phoneNumber;
var a = 1, b = 2, c = 3;

// 换行符
let userId
= 123;
```

**实现**:
```java
// 修改现有正则以支持这些场景
private static final Pattern PATTERN_JS_VAR_ENHANCED = 
    Pattern.compile(
        "\\bvar\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=[\\s=,;\\n])",
        Pattern.MULTILINE
    );

private static final Pattern PATTERN_JS_LET_ENHANCED = 
    Pattern.compile(
        "\\blet\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=[=,;\\n])",
        Pattern.MULTILINE
    );

private static final Pattern PATTERN_JS_CONST_ENHANCED = 
    Pattern.compile(
        "\\bconst\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=[\\s=,;\\n])",
        Pattern.MULTILINE
    );
```

**GAP.py参考**: ✅ 支持

---

## 🎯 实现优先级

### P0 - 核心增强（必须实现）

| 功能 | 价值 | 实现难度 | GAP支持 |
|------|------|---------|---------|
| 多变量声明 | ⭐⭐⭐⭐ | 低 | ✅ |
| 换行符支持 | ⭐⭐⭐ | 低 | ✅ |
| HTML注释 | ⭐⭐⭐⭐⭐ | 中 | ✅ |
| Meta标签 | ⭐⭐⭐⭐ | 低 | ✅ |
| URL参数 | ⭐⭐⭐⭐⭐ | 低 | ✅ |

---

### P1 - 高价值增强（推荐实现）

| 功能 | 价值 | 实现难度 | GAP支持 |
|------|------|---------|---------|
| 函数参数 | ⭐⭐⭐⭐⭐ | 中 | ❌ |
| 箭头函数参数 | ⭐⭐⭐⭐ | 中 | ❌ |
| 对象解构 | ⭐⭐⭐⭐⭐ | 中 | ❌ |
| 嵌套JSON | ⭐⭐⭐⭐⭐ | 高 | ✅ |
| localStorage键 | ⭐⭐⭐⭐ | 低 | ❌ |

---

### P2 - 可选增强（时间允许）

| 功能 | 价值 | 实现难度 | GAP支持 |
|------|------|---------|---------|
| img alt | ⭐⭐⭐ | 低 | ✅ |
| 内联事件 | ⭐⭐⭐ | 中 | ❌ |
| Cookie名称 | ⭐⭐⭐ | 低 | ❌ |
| fetch/axios | ⭐⭐⭐⭐ | 中 | ⚠️ |

---

## 💡 智能过滤优化

为了避免噪音，需要增强参数有效性判断：

```java
/**
 * 判断一个词是否看起来像参数名
 */
private boolean looksLikeParameter(String word) {
    if (word == null || word.isEmpty()) return false;
    
    // 1. 长度检查（2-50字符）
    if (word.length() < 2 || word.length() > 50) return false;
    
    // 2. 必须以字母或下划线开头
    if (!word.matches("^[a-zA-Z_].*")) return false;
    
    // 3. 只包含字母、数字、下划线、连字符
    if (!word.matches("^[a-zA-Z0-9_-]+$")) return false;
    
    // 4. 排除常见停用词
    if (STOP_WORDS.contains(word.toLowerCase())) return false;
    
    // 5. 排除JavaScript关键字
    if (JS_KEYWORDS.contains(word)) return false;
    
    // 6. 排除HTML标签名
    if (HTML_TAGS.contains(word.toLowerCase())) return false;
    
    // 7. 优先包含下划线或驼峰命名的
    boolean hasUnderscore = word.contains("_");
    boolean isCamelCase = word.matches(".*[a-z][A-Z].*");
    boolean hasMultipleWords = hasUnderscore || isCamelCase;
    
    // 8. 如果太短但没有下划线/驼峰，可能是噪音
    if (word.length() < 4 && !hasMultipleWords) {
        return false;
    }
    
    return true;
}

// JavaScript关键字
private static final Set<String> JS_KEYWORDS = Set.of(
    "function", "return", "var", "let", "const", "if", "else", "for", "while",
    "break", "continue", "switch", "case", "default", "try", "catch", "finally",
    "throw", "new", "this", "super", "class", "extends", "import", "export",
    "async", "await", "yield", "typeof", "instanceof", "delete", "void"
);

// HTML标签名
private static final Set<String> HTML_TAGS = Set.of(
    "div", "span", "p", "a", "img", "input", "button", "form", "table", "tr",
    "td", "th", "ul", "li", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
    "header", "footer", "nav", "section", "article", "aside", "main"
);
```

---

## 📊 预期效果对比

### 增强前

**示例HTML+JS**:
```html
<!-- User API endpoint: /api/users/:userId -->
<meta name="csrf-token" content="xxx">
<img src="user.jpg" alt="user_avatar">
<a href="/profile?user_id=123&session_token=abc">Profile</a>

<script>
function getUserData(userId, apiKey) {
    const {email, phoneNumber} = user;
    localStorage.setItem('session_token', token);
    
    fetch('/api/users', {
        body: JSON.stringify({userId, email})
    });
}
</script>
```

**当前收集**: 
```
✅ user_id, session_token (从href参数)
✅ getUserData (函数名，但不是参数)

总计: ~2个参数
```

---

### 增强后

**增强收集**:
```
✅ userId (HTML注释)
✅ csrf_token (meta标签)
✅ user_avatar (img alt)
✅ user_id, session_token (URL参数)
✅ getUserData, userId, apiKey (函数和参数)
✅ email, phoneNumber (对象解构)
✅ session_token (localStorage)
✅ userId, email (fetch body)

总计: ~15个参数 (+650%)
```

---

## ✅ 实施建议

### 阶段1：核心增强（1-2天）
1. ✅ 多变量声明支持
2. ✅ 换行符支持
3. ✅ HTML注释提取
4. ✅ Meta标签提取
5. ✅ URL参数提取

### 阶段2：高价值增强（2-3天）
1. ✅ 函数参数提取
2. ✅ 箭头函数参数
3. ✅ 对象解构
4. ✅ localStorage/sessionStorage
5. ✅ 嵌套JSON递归提取

### 阶段3：完善优化（1-2天）
1. ✅ 智能过滤优化
2. ✅ img alt提取
3. ✅ Cookie名称提取
4. ✅ 内联事件提取
5. ✅ 性能测试和优化

---

## 🎯 预期收益

| 维度 | 增强前 | 增强后 | 提升 |
|------|--------|--------|------|
| 参数覆盖率 | ~30% | ~85% | +183% |
| 平均参数数 | 5-10个 | 30-50个 | +400% |
| 噪音比例 | ~5% | ~8% | +3% (可接受) |
| 处理时间 | 基准 | +20-30% | 可接受 |

---

**结论**: 通过分阶段实施，可以显著提升参数收集能力，同时保持较低的噪音比例和合理的性能开销。

**建议**: 优先实施P0和P1项目，P2项目根据实际需求和时间决定。



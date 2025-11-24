# HTTP元素类型处理逻辑说明

## 概述
本文档说明各种HTTP元素类型（PARAMETER、COOKIE、HEADER、BODY、METHOD、HOST、PATH）的处理逻辑和区分方式。

## 类型分类

### 1. PARAMETER（URL参数 + POST参数）
**数据来源：** `request.parameters()` 中类型为 `URL_PARAMETER` 或 `BODY_PARAMETER` 的参数
**排除内容：** Cookie参数（`COOKIE` 类型）
**处理方式：**
- 收集目标：遍历 `request.parameters()`，排除 `param.type() == COOKIE`
- 匹配评估：只检查URL和POST参数
- 注入操作：使用 `HttpParameter.parameter(name, value, paramType)` 更新参数

**关键代码：**
```java
case PARAMETER:
    for (var param : request.parameters()) {
        if (param.type() == HttpParameterType.COOKIE) {
            continue;  // ✅ 排除Cookie参数
        }
        // 处理URL和POST参数
    }
```

---

### 2. COOKIE（Cookie参数）
**数据来源：** `request.parameters()` 中类型为 `COOKIE` 的参数
**排除内容：** URL参数和POST参数
**处理方式：**
- 收集目标：遍历 `request.parameters()`，只处理 `param.type() == COOKIE`
- 匹配评估：只检查Cookie参数
- 注入操作：使用 `HttpParameter.cookieParameter(name, value)` 更新Cookie

**关键代码：**
```java
case COOKIE:
    for (var param : request.parameters()) {
        if (param.type() != HttpParameterType.COOKIE) {
            continue;  // ✅ 只处理Cookie参数
        }
        // 处理Cookie
    }
```

---

### 3. HEADER（HTTP请求头）
**数据来源：** `request.headers()` - 独立的Header集合
**排除内容：** 不涉及参数，完全独立
**处理方式：**
- 收集目标：遍历 `request.headers()`，不涉及 `parameters()`
- 匹配评估：检查Header名称和值
- 注入操作：使用 `withUpdatedHeader()` 或 `withAddedHeader()` 更新Header
- **安全措施：** 自动移除换行符（`\r\n`），防止Header注入攻击

**关键代码：**
```java
case HEADER:
    for (var header : request.headers()) {
        // 处理Header，不涉及parameters()
    }
    // 注入时移除换行符
    String safePayload = payload.replace("\r", "").replace("\n", "");
    modified = modified.withUpdatedHeader(header.name(), safePayload);
```

---

### 4. BODY（请求体）
**数据来源：** `request.bodyToString()` - 请求体内容
**排除内容：** 不涉及参数、Header等，完全独立
**处理方式：**
- 收集目标：只有一个目标（整个请求体）
- 匹配评估：检查请求体内容
- 注入操作：使用 `withBody()` 替换或追加请求体

**关键代码：**
```java
case BODY:
    // 只有一个目标
    targets.add(new InjectionTarget("", request.bodyToString(), null, element));
    // 注入
    modified = modified.withBody(payload);
```

---

### 5. METHOD（HTTP方法）
**数据来源：** `request.method()` - GET、POST等
**排除内容：** 不涉及其他元素
**处理方式：**
- 收集目标：只有一个目标（当前方法）
- 匹配评估：检查方法名称
- 注入操作：使用 `withMethod()` 替换方法

**关键代码：**
```java
case METHOD:
    targets.add(new InjectionTarget("", request.method(), null, element));
    modified = modified.withMethod(payload);
```

---

### 6. HOST（主机名）
**数据来源：** `request.httpService().host()` - 主机名
**排除内容：** 不涉及其他元素
**处理方式：**
- 收集目标：只有一个目标（当前主机）
- 匹配评估：检查主机名
- 注入操作：**不支持注入**，只用于匹配

**关键代码：**
```java
case HOST:
    targets.add(new InjectionTarget("", host, null, element));
    // 注入时返回原请求
    api.logging().raiseDebugEvent("HOST类型不支持注入，跳过");
    return originalRequest;
```

---

### 7. PATH（URL路径）
**数据来源：** `request.path()` - URL路径
**排除内容：** 不涉及其他元素
**处理方式：**
- 收集目标：只有一个目标（当前路径）
- 匹配评估：检查路径内容
- 注入操作：使用 `withPath()` 替换路径

**关键代码：**
```java
case PATH:
    targets.add(new InjectionTarget("", request.path(), null, element));
    modified = modified.withPath(payload);
```

---

## 类型隔离保证

### 数据源隔离
| 类型 | 数据源 | 是否涉及parameters() | 是否涉及headers() |
|------|--------|---------------------|------------------|
| PARAMETER | `request.parameters()` | ✅ 是（但排除COOKIE） | ❌ 否 |
| COOKIE | `request.parameters()` | ✅ 是（只处理COOKIE） | ❌ 否 |
| HEADER | `request.headers()` | ❌ 否 | ✅ 是 |
| BODY | `request.bodyToString()` | ❌ 否 | ❌ 否 |
| METHOD | `request.method()` | ❌ 否 | ❌ 否 |
| HOST | `request.httpService().host()` | ❌ 否 | ❌ 否 |
| PATH | `request.path()` | ❌ 否 | ❌ 否 |

### 处理逻辑隔离
1. **PARAMETER 和 COOKIE**：虽然都使用 `request.parameters()`，但通过 `param.type()` 严格区分
2. **HEADER**：使用独立的 `request.headers()` API，完全不涉及参数
3. **BODY/METHOD/HOST/PATH**：使用各自的独立API，互不干扰

### 注入操作隔离
- **PARAMETER**：`HttpParameter.parameter()` - 更新URL/POST参数
- **COOKIE**：`HttpParameter.cookieParameter()` - 更新Cookie参数
- **HEADER**：`withUpdatedHeader()` / `withAddedHeader()` - 更新Header
- **BODY**：`withBody()` - 替换请求体
- **METHOD**：`withMethod()` - 替换方法
- **PATH**：`withPath()` - 替换路径
- **HOST**：不支持注入

---

## 配置示例说明

### 示例1：Header匹配但不注入
```
类型：HEADER
名称：user-agent
匹配：✅ 已勾选
注入：❌ 未勾选
```
**逻辑：**
- 用于匹配请求（检查user-agent header是否存在/匹配）
- 不用于注入Payload（不会修改user-agent header）

### 示例2：Body匹配并注入
```
类型：BODY
内容：{josnjson}
匹配：✅ 已勾选
注入：✅ 已勾选
```
**逻辑：**
- 用于匹配请求（检查请求体是否包含 `{josnjson}`）
- 用于注入Payload（将Payload注入到请求体中）

---

## 总结

1. **类型严格区分**：每种类型使用独立的数据源和处理逻辑
2. **PARAMETER/COOKIE隔离**：虽然都来自 `parameters()`，但通过类型检查严格区分
3. **HEADER独立**：使用 `headers()` API，完全不涉及参数
4. **其他类型独立**：BODY、METHOD、HOST、PATH各自使用独立API
5. **注入操作隔离**：每种类型使用对应的Burp API方法，互不干扰

**不会出现类型混淆的问题！**


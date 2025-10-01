# 🎯 完整的去重颗粒度系统

## 📅 日期
2025年10月1日

## 🎯 用户需求

> "去重的颗粒度应该支持好几种，比如 基于参数名的 基于path的，基于host的，还有就是你上面提到的那几种"

**完全实现！** ✅

---

## 📊 完整的去重颗粒度选项

### 1. 🔍 AUTO（自动检测）**推荐**

**说明**: 根据规则类型智能选择最合适的颗粒度

**自动检测逻辑**:
```
if (有参数/Header/Cookie注入) → PARAMETER
else → REQUEST
```

**适用场景**: 
- 大部分情况（推荐）
- 不确定选哪个时

---

### 2. 🌍 GLOBAL（全局）

**去重Key**: `ruleId`

**说明**: 整个规则只测试一次，不管有多少请求

**示例**:
```
✅ GET http://example.com/api/user?id=1    ← 测试
❌ GET http://example.com/api/user?id=2    ← 跳过
❌ GET http://example.com/api/post?id=1    ← 跳过
❌ POST http://api.example.com/data        ← 跳过
```

**适用场景**:
- 只需要验证一次的检测
- 全局性的扫描（如版本检测）

---

### 3. 🏢 HOST（主机级）

**去重Key**: `ruleId + host`

**说明**: 每个主机只测试一次

**示例**:
```
✅ GET http://example.com/api/user?id=1      ← 测试example.com
❌ GET http://example.com/api/post?id=2      ← 跳过（同一主机）
❌ GET http://example.com/admin/login        ← 跳过（同一主机）
✅ GET http://api.example.com/data           ← 测试api.example.com（不同主机）
```

**适用场景**:
- 主机级别的漏洞检测
- 服务器配置检测
- 框架版本检测

---

### 4. 🛣️ PATH（路径级）

**去重Key**: `ruleId + host + path`

**说明**: 每个路径只测试一次

**示例**:
```
✅ GET http://example.com/api/user?id=1      ← 测试/api/user
❌ GET http://example.com/api/user?id=2      ← 跳过（同一路径）
❌ GET http://example.com/api/user?name=x    ← 跳过（同一路径）
✅ GET http://example.com/api/post?id=1      ← 测试/api/post（不同路径）
✅ GET http://api.example.com/api/user?id=1  ← 测试（不同主机）
```

**适用场景**:
- 路径遍历检测
- 目录扫描
- 端点级别的漏洞

---

### 5. 📝 REQUEST（请求级）

**去重Key**: `ruleId + method + host + path + contentType`

**说明**: 每个完整请求只测试一次（包含Method和Content-Type）

**示例**:
```
✅ GET  http://example.com/api/user (JSON)     ← 测试
❌ GET  http://example.com/api/user (JSON)     ← 跳过（完全相同）
✅ POST http://example.com/api/user (JSON)     ← 测试（不同Method）
✅ GET  http://example.com/api/user (XML)      ← 测试（不同Content-Type）
✅ GET  http://example.com/api/post (JSON)     ← 测试（不同路径）
```

**适用场景**:
- 整体Body替换
- URL Path注入
- Method特定的漏洞

---

### 6. 🏷️ PARAMETER_NAME_GLOBAL（参数名-全局）

**去重Key**: `ruleId + parameterName`

**说明**: 相同参数名在任何请求中都只测试一次

**示例**:
```
✅ GET http://example.com/api/user?id=1          ← 测试参数id
❌ GET http://example.com/api/user?id=2          ← 跳过（参数id已测试）
❌ GET http://example.com/api/post?id=3          ← 跳过（参数id已测试）
❌ GET http://api.example.com/data?id=4          ← 跳过（参数id已测试）
✅ GET http://example.com/api/user?name=test     ← 测试参数name（不同参数）
```

**适用场景**:
- 参数级别的全局检测
- 快速扫描（减少重复测试）
- 参数名有明确含义的场景（如id、token）

---

### 7. 🗂️ PARAMETER_NAME_PER_PATH（参数名-路径级）

**去重Key**: `ruleId + host + path + parameterName`

**说明**: 每个路径下的参数名分别测试

**示例**:
```
✅ GET http://example.com/api/user?id=1          ← 测试/api/user的id
❌ GET http://example.com/api/user?id=2          ← 跳过（同一路径的id）
✅ GET http://example.com/api/post?id=3          ← 测试/api/post的id（不同路径）
✅ GET http://api.example.com/api/user?id=4      ← 测试（不同主机）
✅ GET http://example.com/api/user?name=test     ← 测试（不同参数）
```

**适用场景**:
- 不同端点可能有不同的参数处理逻辑
- 需要测试每个路径的参数
- 平衡效率和覆盖率

---

### 8. 🎯 PARAMETER（参数级）

**去重Key**: `ruleId + method + host + path + contentType + parameterName`

**说明**: 每个请求中的参数分别测试

**示例**:
```
✅ GET  /api/user?id=1 (JSON)                    ← 测试参数id
✅ GET  /api/user?name=x (JSON)                  ← 测试参数name
❌ GET  /api/user?id=2 (JSON)                    ← 跳过（id已测试）
✅ POST /api/user?id=1 (JSON)                    ← 测试（不同Method）
✅ GET  /api/user?id=1 (XML)                     ← 测试（不同Content-Type）
✅ GET  /api/post?id=1 (JSON)                    ← 测试（不同路径）
```

**适用场景**:
- 参数注入（SQL、XSS、LFI等）
- 需要测试每个参数的场景
- 最常用的颗粒度

---

### 9. 🔧 INJECTION_POINT（注入点级）

**去重Key**: `ruleId + method + host + path + contentType + injectionPointHash`

**说明**: 每个注入点分别测试（基于注入点的完整特征）

**示例**:
```
规则有3个注入点：
  - 参数id的值
  - Header User-Agent
  - Body中的JSON字段

✅ GET /api/user?id=1 (注入点1)                  ← 测试
✅ GET /api/user?id=1 (注入点2)                  ← 测试（不同注入点）
✅ GET /api/user?id=1 (注入点3)                  ← 测试（不同注入点）
```

**适用场景**:
- 多个注入点的复杂规则
- 需要测试所有注入组合
- 精确控制

---

### 10. 🚫 NONE（无去重）

**去重Key**: `ruleId + timestamp + random`

**说明**: 每次都测试，不进行去重

**示例**:
```
✅ GET /api/user?id=1                            ← 测试
✅ GET /api/user?id=1                            ← 测试（相同请求也测试）
✅ GET /api/user?id=1                            ← 测试（每次都测试）
```

**适用场景**:
- Fuzzing模式
- 压力测试
- 需要大量重复测试的场景

---

## 📊 颗粒度对比表

| 颗粒度 | Key组成 | 测试频率 | 性能 | 覆盖率 | 适用场景 |
|--------|---------|----------|------|--------|----------|
| **GLOBAL** | ruleId | 最低 | ⭐⭐⭐⭐⭐ | ⭐ | 全局检测 |
| **HOST** | ruleId + host | 很低 | ⭐⭐⭐⭐ | ⭐⭐ | 主机级检测 |
| **PATH** | ruleId + host + path | 低 | ⭐⭐⭐ | ⭐⭐⭐ | 路径扫描 |
| **REQUEST** | ruleId + method + host + path + contentType | 中 | ⭐⭐⭐ | ⭐⭐⭐⭐ | 常规检测 |
| **PARAMETER_NAME_GLOBAL** | ruleId + parameterName | 低 | ⭐⭐⭐⭐ | ⭐⭐ | 快速参数扫描 |
| **PARAMETER_NAME_PER_PATH** | ruleId + host + path + parameterName | 中 | ⭐⭐⭐ | ⭐⭐⭐ | 平衡模式 |
| **PARAMETER** | ruleId + method + host + path + contentType + parameterName | 中高 | ⭐⭐ | ⭐⭐⭐⭐ | 参数注入（**推荐**） |
| **INJECTION_POINT** | ruleId + ... + injectionPointHash | 高 | ⭐ | ⭐⭐⭐⭐⭐ | 多注入点规则 |
| **NONE** | ruleId + timestamp + random | 最高 | - | ⭐⭐⭐⭐⭐ | Fuzzing |

---

## 🎯 实际应用场景

### 场景1: SQL注入检测

**推荐颗粒度**: `PARAMETER` 或 `PARAMETER_NAME_PER_PATH`

**理由**:
- 每个参数可能有不同的注入点
- 需要测试所有参数
- 但同一路径的相同参数不需要重复测试

**示例**:
```
规则: SQL注入检测
颗粒度: PARAMETER

✅ GET /api/user?id=1          ← 测试id
✅ GET /api/user?name=admin    ← 测试name
❌ GET /api/user?id=2          ← 跳过（id已测试）
✅ GET /api/post?id=1          ← 测试（不同路径）
```

---

### 场景2: XSS检测

**推荐颗粒度**: `PARAMETER`

**理由**:
- 每个参数都可能有XSS漏洞
- 需要逐个测试

**示例**:
```
规则: XSS检测
颗粒度: PARAMETER

✅ GET /search?q=<script>       ← 测试q
✅ GET /search?type=user        ← 测试type
❌ GET /search?q=test           ← 跳过（q已测试）
```

---

### 场景3: 路径遍历

**推荐颗粒度**: `PATH`

**理由**:
- 路径遍历通常是路径级别的漏洞
- 同一路径不需要重复测试

**示例**:
```
规则: 路径遍历
颗粒度: PATH

✅ GET /api/user?id=1          ← 测试/api/user
❌ GET /api/user?id=2          ← 跳过（同一路径）
✅ GET /api/post?id=1          ← 测试/api/post
```

---

### 场景4: SSRF检测

**推荐颗粒度**: `PARAMETER` 或 `PARAMETER_NAME_GLOBAL`

**理由**:
- 参数级别的漏洞
- url、redirect、callback等参数名通常有SSRF风险
- 可以使用全局去重快速扫描

**示例**:
```
规则: SSRF检测
颗粒度: PARAMETER_NAME_GLOBAL

✅ GET /api/user?url=http://...        ← 测试url
❌ GET /api/post?url=http://...        ← 跳过（url已全局测试）
✅ GET /api/user?callback=http://...   ← 测试callback
```

---

### 场景5: 服务器版本检测

**推荐颗粒度**: `HOST` 或 `GLOBAL`

**理由**:
- 服务器版本是主机级别的
- 同一主机不需要重复检测

**示例**:
```
规则: Nginx版本检测
颗粒度: HOST

✅ GET http://example.com/              ← 测试example.com
❌ GET http://example.com/api/user      ← 跳过（同一主机）
✅ GET http://api.example.com/          ← 测试api.example.com
```

---

### 场景6: Fuzzing测试

**推荐颗粒度**: `NONE`

**理由**:
- 需要大量重复测试
- 不进行去重

**示例**:
```
规则: Fuzzing
颗粒度: NONE

✅ GET /api/user?id=1          ← 测试
✅ GET /api/user?id=1          ← 测试（重复也测试）
✅ GET /api/user?id=1          ← 测试（每次都测试）
```

---

## 🔄 颗粒度选择建议

### 1. 从粗到细的梯度

```
GLOBAL
  ↓ 细化
HOST
  ↓ 细化
PATH
  ↓ 细化
REQUEST
  ↓ 细化
PARAMETER_NAME_GLOBAL
  ↓ 细化
PARAMETER_NAME_PER_PATH
  ↓ 细化
PARAMETER
  ↓ 细化
INJECTION_POINT
  ↓ 细化
NONE
```

### 2. 性能 vs 覆盖率

**高性能（少测试）**:
```
GLOBAL → HOST → PATH → PARAMETER_NAME_GLOBAL
```

**高覆盖率（多测试）**:
```
PARAMETER → INJECTION_POINT → NONE
```

**平衡模式（推荐）**:
```
AUTO → REQUEST → PARAMETER → PARAMETER_NAME_PER_PATH
```

---

## 💡 最佳实践

### 1. 常规扫描

```
✅ 使用 AUTO（自动检测）
✅ 或使用 PARAMETER（参数级）
```

### 2. 快速扫描

```
✅ 使用 PATH（路径级）
✅ 或使用 PARAMETER_NAME_GLOBAL（参数名-全局）
```

### 3. 深度扫描

```
✅ 使用 PARAMETER（参数级）
✅ 或使用 INJECTION_POINT（注入点级）
```

### 4. Fuzzing

```
✅ 使用 NONE（无去重）
```

---

## 🎨 UI显示

在去重颗粒度下拉框中，每个选项显示为：

```
自动检测 - 根据规则类型智能选择
全局 - 整个规则只测试一次
主机级 - 每个主机只测试一次
路径级 - 每个路径只测试一次
请求级 - 每个完整请求只测试一次
参数名(全局) - 相同参数名只测试一次
参数名(路径) - 每个路径下的参数名分别测试
参数级 - 每个请求中的参数分别测试
注入点级 - 每个注入点分别测试
无去重 - 每次都测试（Fuzzing模式）
```

---

## 📝 代码示例

### Configuration枚举

```java
public enum DeduplicationGranularity {
    AUTO("自动检测", "根据规则类型智能选择"),
    GLOBAL("全局", "整个规则只测试一次"),
    HOST("主机级", "每个主机只测试一次"),
    PATH("路径级", "每个路径只测试一次"),
    REQUEST("请求级", "每个完整请求只测试一次"),
    PARAMETER_NAME_GLOBAL("参数名(全局)", "相同参数名只测试一次"),
    PARAMETER_NAME_PER_PATH("参数名(路径)", "每个路径下的参数名分别测试"),
    PARAMETER("参数级", "每个请求中的参数分别测试"),
    INJECTION_POINT("注入点级", "每个注入点分别测试"),
    NONE("无去重", "每次都测试（Fuzzing模式）");
    
    private final String displayName;
    private final String description;
    
    // ... getter方法
}
```

### Key生成逻辑

```java
switch (granularity) {
    case GLOBAL:
        return ruleId;
        
    case HOST:
        return String.format("%s|%s", ruleId, host);
        
    case PATH:
        return String.format("%s|%s|%s", ruleId, host, cleanPath);
        
    case REQUEST:
        return String.format("%s|%s|%s|%s|%s",
            ruleId, method, host, cleanPath, normalizedContentType);
        
    case PARAMETER_NAME_GLOBAL:
        return String.format("%s|%s", ruleId, targetIdentifier);
        
    case PARAMETER_NAME_PER_PATH:
        return String.format("%s|%s|%s|%s",
            ruleId, host, cleanPath, targetIdentifier);
        
    case PARAMETER:
        return String.format("%s|%s|%s|%s|%s|%s",
            ruleId, method, host, cleanPath, normalizedContentType, targetIdentifier);
        
    case INJECTION_POINT:
        return String.format("%s|%s|%s|%s|%s|%s",
            ruleId, method, host, cleanPath, normalizedContentType, injectionPointHash);
        
    case NONE:
        return String.format("%s|%s|%d",
            ruleId, System.currentTimeMillis(), random());
}
```

---

## ✅ 功能清单

- [x] 10种去重颗粒度选项
- [x] 自动检测模式（AUTO）
- [x] 全局去重（GLOBAL）
- [x] 主机级去重（HOST）
- [x] 路径级去重（PATH）
- [x] 请求级去重（REQUEST）
- [x] 参数名全局去重（PARAMETER_NAME_GLOBAL）
- [x] 参数名路径级去重（PARAMETER_NAME_PER_PATH）
- [x] 参数级去重（PARAMETER）
- [x] 注入点级去重（INJECTION_POINT）
- [x] 无去重模式（NONE）
- [x] UI显示优化（displayName + description）
- [x] 新旧架构兼容
- [x] 编译成功

---

## 🎉 总结

### 实现前

- ❌ 只有4种颗粒度
- ❌ 不支持基于主机的去重
- ❌ 不支持基于路径的去重
- ❌ 不支持参数名全局去重
- ❌ 不支持无去重模式

### 实现后

- ✅ **10种完整的去重颗粒度**
- ✅ 从全局到注入点的完整梯度
- ✅ 支持基于主机、路径、参数名的去重
- ✅ 支持Fuzzing模式（无去重）
- ✅ 自动检测模式（智能选择）
- ✅ UI友好（displayName + description）
- ✅ 新旧架构完全兼容

---

## 🚀 立即使用

最新JAR包：`build/libs/XProbe-1.0.0.jar`

**现在你可以根据不同的扫描需求，灵活选择最合适的去重颗粒度！** 🎯

---

**🌟 完整的去重颗粒度系统已实现，支持从全局到注入点的10种细粒度控制！**


# 🎯 去重颗粒度快速参考

## 📊 10种去重颗粒度一览

| # | 颗粒度 | Key包含 | 测试频率 | 典型场景 |
|---|--------|---------|----------|----------|
| 1 | **AUTO** 🌟 | *自动* | 自动 | **推荐使用** |
| 2 | **GLOBAL** | ruleId | ⭐ | 版本检测、全局检测 |
| 3 | **HOST** | ruleId + host | ⭐⭐ | 主机配置、框架检测 |
| 4 | **PATH** | ruleId + host + path | ⭐⭐⭐ | 路径遍历、目录扫描 |
| 5 | **REQUEST** | ruleId + method + host + path + ct | ⭐⭐⭐⭐ | Body注入、Path注入 |
| 6 | **PARAM_GLOBAL** | ruleId + paramName | ⭐⭐ | 快速参数扫描 |
| 7 | **PARAM_PATH** | ruleId + host + path + paramName | ⭐⭐⭐ | 平衡扫描 |
| 8 | **PARAMETER** 🌟 | ruleId + request + paramName | ⭐⭐⭐⭐ | **SQL/XSS/LFI** |
| 9 | **INJECTION_POINT** | ruleId + request + injectionHash | ⭐⭐⭐⭐⭐ | 多注入点规则 |
| 10 | **NONE** | ruleId + timestamp + random | ⭐⭐⭐⭐⭐ | Fuzzing模式 |

---

## 🎯 快速选择指南

### 我应该选哪个？

```
┌─────────────────────────────────┐
│  不确定用哪个？                   │
│  ✅ 选 AUTO（自动检测）          │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  SQL注入、XSS、LFI？             │
│  ✅ 选 PARAMETER（参数级）       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  路径遍历、目录扫描？             │
│  ✅ 选 PATH（路径级）            │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  SSRF、快速扫描？                │
│  ✅ 选 PARAM_GLOBAL（参数名全局）│
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  Fuzzing、压力测试？             │
│  ✅ 选 NONE（无去重）            │
└─────────────────────────────────┘
```

---

## 📏 从粗到细的梯度

```
GLOBAL                          ← 最粗（测试最少）
  ↓
HOST
  ↓
PATH
  ↓
REQUEST
  ↓
PARAMETER_NAME_GLOBAL
  ↓
PARAMETER_NAME_PER_PATH
  ↓
PARAMETER
  ↓
INJECTION_POINT
  ↓
NONE                            ← 最细（测试最多）
```

---

## 💡 典型场景示例

### 场景1: SQL注入检测

```yaml
规则名称: SQL注入检测
去重颗粒度: PARAMETER

效果:
  ✅ /api/user?id=1          ← 测试id
  ✅ /api/user?name=admin    ← 测试name
  ❌ /api/user?id=2          ← 跳过（id已测试）
  ✅ /api/post?id=1          ← 测试（不同路径）
```

### 场景2: 路径遍历

```yaml
规则名称: 路径遍历
去重颗粒度: PATH

效果:
  ✅ /api/user?id=1          ← 测试/api/user
  ❌ /api/user?id=2          ← 跳过（同一路径）
  ❌ /api/user?name=x        ← 跳过（同一路径）
  ✅ /api/post?id=1          ← 测试/api/post
```

### 场景3: 服务器版本检测

```yaml
规则名称: Nginx版本检测
去重颗粒度: HOST

效果:
  ✅ http://example.com/              ← 测试example.com
  ❌ http://example.com/api/user      ← 跳过（同一主机）
  ✅ http://api.example.com/          ← 测试api.example.com
```

### 场景4: SSRF快速扫描

```yaml
规则名称: SSRF检测
去重颗粒度: PARAMETER_NAME_GLOBAL

效果:
  ✅ /api/user?url=...         ← 测试url
  ❌ /api/post?url=...         ← 跳过（url已全局测试）
  ✅ /api/user?callback=...    ← 测试callback
```

---

## ⚖️ 性能 vs 覆盖率

| 颗粒度 | 性能 | 覆盖率 | 推荐度 |
|--------|------|--------|--------|
| GLOBAL | ⭐⭐⭐⭐⭐ | ⭐ | 特殊场景 |
| HOST | ⭐⭐⭐⭐ | ⭐⭐ | 主机检测 |
| PATH | ⭐⭐⭐ | ⭐⭐⭐ | 路径扫描 |
| REQUEST | ⭐⭐⭐ | ⭐⭐⭐⭐ | 常规扫描 |
| PARAM_GLOBAL | ⭐⭐⭐⭐ | ⭐⭐ | 快速扫描 |
| PARAM_PATH | ⭐⭐⭐ | ⭐⭐⭐ | 平衡模式 |
| **PARAMETER** | ⭐⭐ | ⭐⭐⭐⭐ | **推荐** ⭐⭐⭐⭐⭐ |
| INJECTION_POINT | ⭐ | ⭐⭐⭐⭐⭐ | 深度扫描 |
| NONE | - | ⭐⭐⭐⭐⭐ | Fuzzing |

---

## 🎨 UI中的显示

下拉框选项:

```
┌─────────────────────────────────────────────────┐
│ [▼] 去重颗粒度                                   │
├─────────────────────────────────────────────────┤
│ ○ 自动检测 - 根据规则类型智能选择                │
│ ○ 全局 - 整个规则只测试一次                     │
│ ○ 主机级 - 每个主机只测试一次                   │
│ ○ 路径级 - 每个路径只测试一次                   │
│ ○ 请求级 - 每个完整请求只测试一次               │
│ ○ 参数名(全局) - 相同参数名只测试一次           │
│ ○ 参数名(路径) - 每个路径下的参数名分别测试     │
│ ● 参数级 - 每个请求中的参数分别测试  ← 推荐    │
│ ○ 注入点级 - 每个注入点分别测试                │
│ ○ 无去重 - 每次都测试（Fuzzing模式）           │
└─────────────────────────────────────────────────┘
```

---

## 🔍 实际效果对比

### 同一个扫描任务，不同颗粒度的效果

假设有10个请求:
```
1. GET  /api/user?id=1
2. GET  /api/user?id=2
3. GET  /api/user?name=admin
4. POST /api/user?id=1
5. GET  /api/post?id=1
6. GET  /api/post?id=2
7. GET  /admin/login?user=admin
8. GET  /admin/login?pass=123
9. GET  http://api.example.com/data?id=1
10. GET http://www.example.com/api/user?id=1
```

| 颗粒度 | 测试数量 | 测试哪些 |
|--------|----------|----------|
| **GLOBAL** | 1 | 1 |
| **HOST** | 2 | 1, 9 |
| **PATH** | 4 | 1, 5, 7, 9 |
| **REQUEST** | 5 | 1, 4, 5, 7, 9 |
| **PARAM_GLOBAL** | 4 | 1, 3, 7, 8 |
| **PARAM_PATH** | 7 | 1, 3, 5, 7, 8, 9, 10 |
| **PARAMETER** | 8 | 1, 3, 4, 5, 7, 8, 9, 10 |
| **INJECTION_POINT** | 根据注入点配置 | - |
| **NONE** | 10 | 全部 |

---

## 💡 最佳实践建议

### ✅ 常规使用

1. **首选**: `AUTO` - 让系统智能选择
2. **SQL/XSS/LFI**: `PARAMETER` - 参数级别
3. **路径扫描**: `PATH` - 路径级别

### ✅ 性能优化

1. **快速扫描**: `PARAM_GLOBAL` - 参数名全局
2. **大量请求**: `HOST` 或 `PATH` - 降低测试频率
3. **针对性扫描**: 明确选择颗粒度

### ✅ 覆盖率优化

1. **深度扫描**: `PARAMETER` - 参数级别
2. **全面测试**: `INJECTION_POINT` - 注入点级别
3. **Fuzzing**: `NONE` - 无去重

### ❌ 避免

1. 不要随意使用`GLOBAL`（除非真的只需要测试一次）
2. 不要在参数注入时使用`PATH`（会漏掉很多参数）
3. 不要在路径扫描时使用`PARAMETER`（会重复很多次）

---

## 🎯 总结

### 最常用的3种

1. **AUTO** 🌟🌟🌟🌟🌟
   - 推荐指数: ⭐⭐⭐⭐⭐
   - 适合: 所有场景（不确定时选这个）

2. **PARAMETER** 🌟🌟🌟🌟
   - 推荐指数: ⭐⭐⭐⭐⭐
   - 适合: SQL、XSS、LFI等参数注入

3. **PATH** 🌟🌟🌟
   - 推荐指数: ⭐⭐⭐⭐
   - 适合: 路径遍历、目录扫描

### 特殊场景

- **快速扫描**: `PARAM_GLOBAL`
- **深度扫描**: `INJECTION_POINT`
- **Fuzzing**: `NONE`
- **主机检测**: `HOST`

---

**🎉 现在你可以根据实际需求，灵活选择最合适的去重颗粒度了！**


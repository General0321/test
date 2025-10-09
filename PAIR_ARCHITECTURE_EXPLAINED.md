# 配对架构（Pair Architecture）详解

## 📋 概述

XProbe支持**多对请求-响应匹配**机制，每个规则可以包含：
- **多个配对（Pairs）**：每个配对定义一组请求条件和响应匹配
- **逻辑表达式（Pair Expression）**：定义配对之间的逻辑关系

## 🔍 配对架构在JSON中的体现

### 完整示例

```json
{
  "ruleId": "uuid-here",
  "customLabel": "SQL注入检测",
  "enabled": true,
  
  "pairs": [
    {
      "id": 1,
      "label": "数据库错误检测",
      "enabled": true,
      "requestConfig": { /* 请求配置 */ },
      "responseConfig": { /* 响应匹配 */ }
    },
    {
      "id": 2,
      "label": "布尔盲注检测",
      "enabled": true,
      "requestConfig": { /* 请求配置 */ },
      "responseConfig": { /* 响应匹配 */ }
    },
    {
      "id": 3,
      "label": "时间盲注检测",
      "enabled": true,
      "requestConfig": { /* 请求配置 */ },
      "responseConfig": { /* 响应匹配 */ }
    }
  ],
  
  "pairExpression": "1 OR (2 AND 3)"
}
```

## 🎯 配对（Pair）结构

每个配对包含两部分：

### 1. 请求配置（requestConfig）

```json
{
  "conditions": [
    {
      "conditionType": "Content-Type",
      "matchType": "Contains",
      "value": "application/json",
      "operator": "AND"
    }
  ],
  "injectionPoints": [
    {
      "pointType": "Parameter Value",
      "targetName": "id",
      "description": "注入到id参数"
    }
  ],
  "payloads": [
    "' OR '1'='1",
    "1' AND '1'='2"
  ]
}
```

### 2. 响应配置（responseConfig）

```json
{
  "matchRules": [
    {
      "location": "Response Body",
      "matchType": "Contains",
      "rule": "SQL syntax error",
      "operator": "OR"
    },
    {
      "location": "Response Body",
      "matchType": "Regex Match",
      "rule": "ORA-\\d+",
      "operator": "OR"
    }
  ],
  "logicOperator": "OR"
}
```

## 🧮 配对表达式（Pair Expression）

### 支持的逻辑运算符

| 运算符 | 说明 | 示例 |
|--------|------|------|
| `AND` | 与运算 | `1 AND 2` - 配对1和配对2都匹配 |
| `OR` | 或运算 | `1 OR 2` - 配对1或配对2匹配 |
| `NOT` | 非运算 | `NOT 1` - 配对1不匹配 |
| `()` | 括号分组 | `(1 OR 2) AND 3` - 先计算括号内 |

### 表达式示例

#### 1. 单配对
```json
"pairExpression": "1"
```
- 只要配对1匹配就判定为漏洞

#### 2. 全部匹配（AND）
```json
"pairExpression": "1 AND 2 AND 3"
```
- 配对1、2、3都匹配才判定为漏洞

#### 3. 任一匹配（OR）
```json
"pairExpression": "1 OR 2 OR 3"
```
- 配对1、2、3任意一个匹配就判定为漏洞

#### 4. 复杂逻辑
```json
"pairExpression": "1 OR (2 AND 3)"
```
- 配对1匹配，**或者** 配对2和3都匹配

#### 5. 排除逻辑
```json
"pairExpression": "1 AND NOT 2"
```
- 配对1匹配，**且** 配对2不匹配

#### 6. 多层嵌套
```json
"pairExpression": "(1 OR 2) AND (3 OR 4)"
```
- (配对1或2匹配) **且** (配对3或4匹配)

### 默认逻辑

如果 `pairExpression` 为空或null：
- 默认使用 **AND** 逻辑
- 即所有配对都必须匹配

## 📊 实际应用场景

### 场景1：SQL注入多层检测

```json
{
  "pairs": [
    {"id": 1, "label": "错误回显"},
    {"id": 2, "label": "布尔盲注"},
    {"id": 3, "label": "时间盲注"}
  ],
  "pairExpression": "1 OR (2 AND 3)"
}
```

**含义**：
- 如果有错误回显（配对1），直接判定为漏洞
- 如果没有错误回显，则需要布尔盲注（配对2）和时间盲注（配对3）都成功才判定

### 场景2：XSS检测（多payload测试）

```json
{
  "pairs": [
    {"id": 1, "label": "基础XSS"},
    {"id": 2, "label": "绕过过滤"},
    {"id": 3, "label": "事件注入"}
  ],
  "pairExpression": "1 OR 2 OR 3"
}
```

**含义**：任意一种XSS测试成功即判定为漏洞

### 场景3：SSRF检测（双重验证）

```json
{
  "pairs": [
    {"id": 1, "label": "DNS外带"},
    {"id": 2, "label": "HTTP外带"}
  ],
  "pairExpression": "1 AND 2"
}
```

**含义**：DNS和HTTP都有外带才确认SSRF

### 场景4：命令注入（排除误报）

```json
{
  "pairs": [
    {"id": 1, "label": "命令执行回显"},
    {"id": 2, "label": "正常业务响应"}
  ],
  "pairExpression": "1 AND NOT 2"
}
```

**含义**：有命令执行回显，且不是正常业务响应

## 🔄 评估流程

```
1. 发送每个配对的请求
   ↓
2. 检查响应是否匹配
   ↓
3. 记录每个配对的结果（true/false）
   ↓
4. 将配对ID替换为结果值
   例: "1 OR 2" → "true OR false"
   ↓
5. 计算布尔表达式
   ↓
6. 得出最终结果（是否存在漏洞）
```

## 📝 JSON字段说明

### Configuration对象

```json
{
  // 基础信息
  "ruleId": "规则唯一ID",
  "customLabel": "规则名称",
  "description": "规则描述",
  "enabled": true,
  
  // ✅ 配对架构（最新）
  "pairs": [                    // 配对列表
    {
      "id": 1,                  // 配对ID（用于表达式引用）
      "label": "配对名称",
      "enabled": true,
      "requestConfig": { },     // 请求配置
      "responseConfig": { }     // 响应配置
    }
  ],
  "pairExpression": "1 OR 2",   // 配对间逻辑表达式
  
  // 去重配置
  "deduplicationGranularity": "PATH",
  
  // 旧字段（向后兼容，可为空）
  "parameterNames": [],
  "parameterValues": [],
  "matchRules": [],
  "requestConditions": [],
  "injectionPoints": []
}
```

### RuleMatchPair对象

```json
{
  "id": 1,                      // 配对ID（必须）
  "label": "数据库错误检测",    // 配对标签
  "enabled": true,              // 是否启用
  
  "requestConfig": {
    "conditions": [ ],          // 请求匹配条件
    "injectionPoints": [ ],     // 注入点
    "payloads": [ ]             // Payload列表
  },
  
  "responseConfig": {
    "matchRules": [ ],          // 响应匹配规则
    "logicOperator": "OR"       // 规则间逻辑（AND/OR）
  }
}
```

## 🛡️ 安全特性

### 1. 递归深度限制
```java
private static final int MAX_RECURSION_DEPTH = 10;
```
防止表达式嵌套过深导致栈溢出

### 2. 迭代次数限制
```java
// 括号处理最多100次
// NOT处理最多50次
```
防止死循环

### 3. 表达式验证
- 括号匹配检查
- 非法字符过滤
- 语法错误处理

## ⚙️ 技术实现

### 表达式评估代码

```java
private boolean evaluatePairExpression(String expression, Map<Integer, Boolean> pairResults) {
    // 1. 将配对ID替换为结果值
    for (Map.Entry<Integer, Boolean> entry : pairResults.entrySet()) {
        String id = String.valueOf(entry.getKey());
        String value = entry.getValue() ? "true" : "false";
        expr = expr.replaceAll("\\b" + id + "\\b", value);
    }
    
    // 2. 评估布尔表达式
    return evaluateBooleanExpression(expr);
}
```

### 布尔表达式评估

1. **处理括号**：从内到外递归评估
2. **处理NOT**：取反操作
3. **处理AND**：全部为true才返回true
4. **处理OR**：任一为true就返回true

## 📚 总结

✅ **配对架构的优势**：
1. **灵活性高**：支持复杂的检测逻辑
2. **可维护性强**：每个配对独立配置
3. **可扩展性好**：轻松添加新的检测维度
4. **完整保存**：所有信息都在JSON中体现

✅ **JSON中完整保存**：
- `pairs` 数组：所有配对的完整定义
- `pairExpression` 字符串：配对间的逻辑关系
- 导入导出不会丢失任何信息

🎯 **使用建议**：
- 简单规则使用单配对（`pairExpression: "1"`）
- 复杂检测使用多配对+逻辑表达式
- 合理使用AND/OR组合提高检测准确性
- 利用NOT排除误报场景


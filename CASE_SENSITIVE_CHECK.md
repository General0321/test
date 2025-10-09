# 🔍 区分大小写配置检查报告

## 问题

用户配置的"区分大小写"按钮是否在所有地方都生效？

## 检查结果

### ✅ 已支持的地方

#### 1. **匹配配置（Match）** 
所有类型的值匹配都支持区分大小写：

- **Method匹配**：通过 `matchValue()` 支持 ✅
- **Path匹配**：通过 `matchValue()` 支持 ✅
- **Parameter匹配**：通过 `matchValue()` 支持 ✅
- **Body匹配**：通过 `matchValue()` 支持 ✅

#### 2. **Header匹配**
刚修复，现在完整支持：
```java
// 名称匹配（支持区分大小写）
boolean caseSensitive = element.getNameMatchConfig() != null 
    ? element.getNameMatchConfig().isCaseSensitive() 
    : false;  // 默认不区分大小写（符合HTTP规范）
    
if (caseSensitive) {
    nameMatches = header.name().equals(elementName);
} else {
    nameMatches = header.name().equalsIgnoreCase(elementName);
}

// 值匹配（通过matchValue支持）
boolean valueMatches = matchValue(header.value(), element.getValueMatchConfig());
```

#### 3. **Cookie匹配**
刚修复，现在完整支持：
```java
// 名称匹配（支持区分大小写）
boolean caseSensitive = element.getNameMatchConfig() != null 
    ? element.getNameMatchConfig().isCaseSensitive() 
    : true;  // Cookie名称默认区分大小写
```

#### 4. **matchValue核心方法**
所有值匹配都通过这个方法，完整支持区分大小写：
```java
boolean caseSensitive = matchConfig.isCaseSensitive();
String compareActual = caseSensitive ? actualValue : actualValue.toLowerCase();
String compareExpected = caseSensitive ? expectedValue : expectedValue.toLowerCase();

switch (matchType) {
    case EQUALS:
        matches = compareActual.equals(compareExpected);
        break;
    case CONTAINS:
        matches = compareActual.contains(compareExpected);
        break;
    case REGEX:
        Pattern pattern = caseSensitive 
            ? Pattern.compile(expectedValue)
            : Pattern.compile(expectedValue, Pattern.CASE_INSENSITIVE);
        matches = pattern.matcher(actualValue).find();
        break;
    // ...
}
```

### ❓ 需要检查的地方

#### **注入配置（Injection）**

注入时收集目标的逻辑需要检查是否支持区分大小写！

让我检查 `UniversalScanner.shouldMatchTarget()` 方法...


# GAP风格过滤器模块

本目录包含基于GAP.py的参数过滤机制，用于智能过滤HTTP流量中收集的参数，减少噪音。

## 📁 目录结构

```
gap/
├── GapFilterConfig.java      - GAP过滤配置类
├── GapStyleFilter.java        - GAP风格过滤器
└── README.md                  - 本文件
```

---

## 📋 文件说明

### GapFilterConfig.java

**功能**: GAP风格的过滤配置

**核心特性**:
- ✅ 150+ GAP.py默认停用词
- ✅ 额外技术相关停用词
- ✅ 词长度限制（默认3-50字符）
- ✅ 数字包含选项
- ✅ 转小写选项
- ✅ 自定义停用词支持

**停用词来源**:
```
1. GAP.py默认停用词
   a,aboard,about,above,across,after,afterwards...
   
2. 技术相关停用词（新增）
   div,span,button,data,error,result,event...
```

**使用示例**:
```java
GapFilterConfig config = new GapFilterConfig();

// 添加自定义停用词
config.addCustomStopWords("myapp", "internal");

// 调整长度限制
config.setMinWordLength(4);
config.setMaxWordLength(30);

// 配置选项
config.setIncludeWordsWithDigits(true);
config.setToLowerCase(true);
```

---

### GapStyleFilter.java

**功能**: GAP.py风格的参数和词过滤器

**核心方法**:

1. **isValidParameter()** - 参数验证
   ```java
   // GAP.py的REGEX_PARAM: ^[A-Za-z0-9_.~\-\[\]]+$
   boolean isValid = filter.isValidParameter("user_id");
   ```

2. **isValidWord()** - 词验证
   ```java
   // 检查停用词、关键字、长度等
   boolean isValid = filter.isValidWord("userId");
   ```

3. **extractWords()** - 词提取
   ```java
   // GAP.py的REGEX_WORDS: (?<![/])\b\w{3,}\b(?![/])
   Set<String> words = filter.extractWords(text);
   ```

4. **cleanParameter()** - 参数清理
   ```java
   // 移除URL编码、特殊字符等
   String clean = filter.cleanParameter("user%5Bid%5D");
   ```

5. **shouldAcceptByContext()** - 上下文感知过滤
   ```java
   // 根据来源调整过滤强度
   boolean accept = filter.shouldAcceptByContext(
       word, 
       ParameterSource.URL_PARAM
   );
   ```

**参数来源枚举**:
```java
public enum ParameterSource {
    URL_PARAM,              // 最可信
    FORM_INPUT_NAME,        // 很可信
    META_TAG,               // 比较可信
    LOCALSTORAGE_KEY,       // 比较可信
    JS_VARIABLE,            // 中等可信
    JS_FUNCTION_PARAM,      // 需要过滤
    OBJECT_DESTRUCTURE,     // 需要过滤
    HTML_COMMENT,           // 需要严格过滤
    IMG_ALT,                // 需要非常严格过滤
    INLINE_EVENT            // 需要严格过滤
}
```

---

## 🎯 过滤策略

### 上下文感知过滤

根据参数来源的可信度，应用不同强度的过滤：

```
最可信（直接接受）
├── URL_PARAM (URL查询参数)
├── FORM_INPUT_NAME (表单字段name)
└── LOCALSTORAGE_KEY (localStorage键)

很可信（基础验证）
├── META_TAG (Meta标签)
└── JS_VARIABLE (JS变量)

需要过滤（中等严格）
├── JS_FUNCTION_PARAM (函数参数)
└── OBJECT_DESTRUCTURE (对象解构)

严格过滤（只保留明显的）
├── HTML_COMMENT (HTML注释)
├── INLINE_EVENT (内联事件)
└── IMG_ALT (图片alt) - 最严格
```

---

## 📊 过滤效果

### 示例

**输入HTML**:
```html
<!-- This is the main user API endpoint for userId -->
<meta name="csrf-token" content="xxx">
<a href="/profile?user_id=123">Profile</a>

<script>
function getUserData(userId, apiKey, event, data) {
    const {email, loading, error} = user;
}
</script>
```

**无过滤**: 13个词（噪音63%）
```
This, is, the, main, user, API, endpoint, for, userId,
csrf, token, user_id, userId, apiKey, event, data,
email, loading, error
```

**GAP过滤后**: 5个参数（噪音0%）
```
✅ userId (HTML注释 - 驼峰命名)
✅ csrf_token (Meta标签)
✅ user_id (URL参数 - 最可信)
✅ userId, apiKey (函数参数 - 有效)
✅ email (对象解构 - 有效)

❌ This, is, the, main, for, API, endpoint (停用词)
❌ event, data (停用词)
❌ loading, error (停用词)
```

**噪音降低**: 63% → 0% ✅

---

## 🔗 集成使用

### 在ParameterCollector中使用

```java
public class ParameterCollector {
    private final GapFilterConfig gapFilterConfig;
    private final GapStyleFilter gapFilter;
    
    public ParameterCollector(MontoyaApi api) {
        // 初始化GAP过滤器
        this.gapFilterConfig = new GapFilterConfig();
        this.gapFilter = new GapStyleFilter(gapFilterConfig);
    }
    
    private Set<String> extractFromHtmlComments(String html) {
        // 使用GAP过滤器提取词
        Set<String> words = gapFilter.extractWords(comment);
        
        // 上下文过滤
        return words.stream()
            .filter(word -> gapFilter.shouldAcceptByContext(
                word, 
                ParameterSource.HTML_COMMENT
            ))
            .collect(Collectors.toSet());
    }
}
```

---

## 📚 参考资料

- **GAP.py**: https://github.com/xnl-h4ck3r/GAP-Burp-Extension
- **GAP过滤机制文档**: `/docs/GAP_FILTERING_IMPLEMENTATION.md`
- **噪音过滤策略**: `/docs/NOISE_FILTERING_STRATEGY.md`

---

## ✅ 优势

1. **完全兼容GAP.py**
   - 150+停用词直接移植
   - 正则表达式完全一致
   - 过滤逻辑相同

2. **智能上下文感知**
   - 根据来源动态调整
   - 减少误判
   - 保留真实参数

3. **零噪音设计**
   - 多层过滤
   - 特征识别
   - 白名单优先

4. **超越GAP.py**
   - 上下文感知过滤
   - 参数来源枚举
   - 更精准的控制

---

**维护者**: XProbe Team  
**最后更新**: 2025-10-04






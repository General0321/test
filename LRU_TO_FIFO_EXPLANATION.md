# 为什么用FIFO而不是LRU？

## 📝 问题背景

在P0-1修复（去重集合内存泄漏）时，最初使用了命名为`LRUCache`的类，但这是**命名不当**的。

根据用户反馈："我的是日志，而且是大量的日志，应该丢弃最早进入的"。

---

## 🎯 正确的选择：FIFO（先进先出）

### 我们的场景特点

**去重集合/日志场景**：
- ✅ 只需要记录"这个请求处理过了"
- ✅ 不关心某条记录被访问了多少次
- ✅ 应该严格按插入时间淘汰

**代码实际使用方式**：
```java
// 检查是否已处理
if (processedRequests.containsKey(key)) {
    return true;  // 已处理，跳过
}

// 标记为已处理
processedRequests.put(key, true);
```

**关键点**：
- 每个key只插入一次（去重）
- 插入后几乎不再访问（只在下次遇到相同请求时检查）
- **不存在"热点数据"需要保留**

---

## ❌ 为什么LRU不合适？

### LRU（Least Recently Used - 最近最少使用）

**设计目标**：
- 保留频繁访问的"热点数据"
- 淘汰长时间未访问的"冷数据"

**工作方式**：
```
每次访问（get/containsKey）都会更新访问时间
访问频率高的数据会一直留在缓存中
```

**问题示例**：
```java
// 场景：扫描大型网站
processedRequests.put("url1", true);  // 第1个URL
processedRequests.put("url2", true);  // 第2个URL
// ... 插入99,998个URL ...
processedRequests.put("url100000", true);  // 第10万个URL

// ❌ LRU的问题：
// 如果url1被频繁检查（比如重复请求），它会一直留在缓存中
// 而url50000可能从未被检查过，反而被淘汰了
// 这导致：新的请求可能重复扫描，旧的记录却被保留

// ✅ FIFO的正确行为：
// 第100,001个URL进来 → 删除url1（最早的）
// 第100,002个URL进来 → 删除url2（第二早的）
// 严格按时间顺序，符合日志场景
```

---

## ✅ FIFO（First In First Out - 先进先出）

### 设计目标
- 严格按插入时间排序
- 最早插入的最先被淘汰
- 不考虑访问频率

### 工作方式
```
插入顺序：A → B → C → D → E
当容量满时（假设max=3）：
1. 插入D → 删除A（最早的）
2. 插入E → 删除B（第二早的）
```

### 适用场景
✅ 日志缓存  
✅ 去重集合  
✅ 请求历史记录  
✅ 任何"只关心时间，不关心访问频率"的场景

---

## 📊 性能对比

| 操作 | FIFO (BoundedCache) | LRU | 手动管理 |
|------|---------------------|-----|---------|
| 插入 | O(1) | O(1) | O(n) |
| 查询 | O(1) | O(1) | O(1) |
| 删除最早的 | O(1) 自动 | O(1) 自动 | O(n) 需遍历 |
| 访问是否影响顺序 | ❌ 否 | ✅ 是 | N/A |
| 适合去重场景 | ✅ 完美 | ⚠️ 不适合 | ❌ 复杂 |

---

## 🔧 实现对比

### ❌ 错误的LRU实现
```java
// accessOrder = true → LRU模式
new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}

// 问题：containsKey()会更新访问顺序，不适合去重场景
```

### ✅ 正确的FIFO实现
```java
// accessOrder = false → FIFO模式（按插入顺序）
new LinkedHashMap<K, V>(maxSize, 0.75f, false) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}

// ✅ 访问不影响顺序，严格按插入时间淘汰
```

---

## 📋 修改总结

### 修改内容
1. ✅ 删除: `LRUCache.java` (命名不当)
2. ✅ 新增: `BoundedCache.java` (准确命名)
3. ✅ 修改: `RealtimeScannerRefactored.java` (使用BoundedCache)
4. ✅ 更新: 所有文档中的描述

### 关键变化
```java
// 修改前（错误）
private final LinkedHashMap<K, V> cache = 
    new LinkedHashMap<>(maxSize, 0.75f, true);  // ❌ LRU

// 修改后（正确）
private final LinkedHashMap<K, V> cache = 
    new LinkedHashMap<>(maxSize, 0.75f, false);  // ✅ FIFO
```

---

## 🎓 学习要点

### 什么时候用LRU？
✅ 缓存数据库查询结果（频繁查询的保留）  
✅ 缓存网页/图片（热门内容保留）  
✅ 浏览器缓存（常访问的页面保留）  

### 什么时候用FIFO？
✅ 日志缓存（按时间滚动）  
✅ 去重集合（防止重复处理）  
✅ 请求历史（最新N条）  
✅ 消息队列（先进先出）  

### 核心区别
| 场景 | 关键问题 | 选择 |
|------|---------|------|
| 关心访问频率 | 热点数据需要保留？ | LRU |
| 关心插入时间 | 最早的应该先淘汰？ | FIFO |

---

## ✨ 总结

用户的反馈是**完全正确**的！

**问题**：
- 最初命名为`LRUCache`是**误导性的**
- 实现上也应该用FIFO而不是LRU

**正确做法**：
- ✅ 使用`BoundedCache`（明确表达"有界"的含义）
- ✅ 使用FIFO模式（`accessOrder=false`）
- ✅ 文档明确说明"先进先出"

**结果**：
- 代码更清晰
- 命名更准确  
- 行为更符合预期

---

**感谢用户的反馈！这个问题发现得非常及时！** 🎉

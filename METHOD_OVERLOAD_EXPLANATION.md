# 方法重载说明文档

## 📋 问题

为什么代码中有两个版本的同一个方法？例如：
- `collectScanTasks(HttpRequest, ...)`
- `collectScanTasks(HttpRequestToBeSent, ...)`

## 🎯 核心原因

### Burp API 的两种请求类型

Burp Suite Montoya API 提供了两种不同的请求接口：

```java
// 1. HttpRequestToBeSent - 请求即将发送时
public interface HttpRequestToBeSent extends HttpRequest { ... }

// 2. HttpRequest - 基础请求接口
public interface HttpRequest { ... }
```

**关键区别**：
- `HttpRequestToBeSent` **继承** `HttpRequest`
- 但在不同的处理器中，你只能获取到特定的类型

---

## 🔄 时序修复前后的变化

### 修复前：被动扫描在请求发送前触发

```java
// RequestHandler.java - 修复前

@Override
public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent requestToBeSent) {  // ← 只有这个类型
    
    // ❌ 旧逻辑：在这里触发被动扫描
    List<ScanTask> scanTasks = collectScanTasks(requestToBeSent, context);
    
    // 这里可以直接使用 HttpRequestToBeSent 类型 ✅
}

// 只需要一个版本
private List<ScanTask> collectScanTasks(
    HttpRequestToBeSent request,  // ← 匹配参数类型
    RequestContext context) { ... }
```

### 修复后：被动扫描在响应收到后触发

```java
// RequestHandler.java - 修复后

@Override
public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent requestToBeSent) {
    
    // ✅ 新逻辑：不在这里触发被动扫描
    // 只处理参数收集
}

@Override
public ResponseReceivedAction handleHttpResponseReceived(
    HttpResponseReceived responseReceived) {
    
    // ✅ 新逻辑：在这里触发被动扫描
    HttpRequest initiatingRequest = responseReceived.initiatingRequest();
    //          ^^^^^^^^^ 注意：返回的是 HttpRequest，不是 HttpRequestToBeSent！
    
    List<ScanTask> scanTasks = collectScanTasks(initiatingRequest, context);
    //                                          ^^^^^^^^^^^^^^^^
    //                                          这是 HttpRequest 类型
}
```

---

## 🚨 类型不匹配问题

### 问题演示

```java
// 如果只有一个版本（接受 HttpRequestToBeSent）

private List<ScanTask> collectScanTasks(
    HttpRequestToBeSent request,  // ← 只接受 HttpRequestToBeSent
    RequestContext context) { ... }

// 在响应处理器中调用
public ResponseReceivedAction handleHttpResponseReceived(...) {
    HttpRequest initiatingRequest = responseReceived.initiatingRequest();
    //          ^^^^^^^^^ 这是 HttpRequest 类型
    
    collectScanTasks(initiatingRequest, context);
    //               ^^^^^^^^^^^^^^^^^
    //               ❌ 编译错误！类型不匹配！
    //               HttpRequest 不能直接赋值给 HttpRequestToBeSent
}
```

**错误信息**：
```
The method collectScanTasks(HttpRequestToBeSent, RequestContext) 
is not applicable for the arguments (HttpRequest, RequestContext)
```

---

## ✅ 解决方案：方法重载

### 提供两个版本

```java
// 版本1：接受 HttpRequest（用于响应处理器）
private List<ScanTask> collectScanTasks(
    HttpRequest request,  // ← 基础类型
    RequestContext context) {
    
    // 实现逻辑
    List<ParsedHttpParameter> parameters = request.parameters();
    String contentType = getContentType(request);
    // ...
}

// 版本2：接受 HttpRequestToBeSent（用于请求处理器）
private List<ScanTask> collectScanTasks(
    HttpRequestToBeSent request,  // ← 派生类型
    RequestContext context) {
    
    // 实现相同的逻辑
    List<ParsedHttpParameter> parameters = request.parameters();
    String contentType = getContentType(request);
    // ...
}
```

### Java 方法重载机制

Java 编译器会根据**参数类型**自动选择正确的版本：

```java
// 在响应处理器中
HttpRequest req1 = responseReceived.initiatingRequest();
collectScanTasks(req1, context);  // → 自动调用版本1 ✅

// 在请求处理器中
HttpRequestToBeSent req2 = requestToBeSent;
collectScanTasks(req2, context);  // → 自动调用版本2 ✅
```

---

## 📝 需要重载的方法列表

### 1. collectScanTasks
```java
// 响应处理器版本
private List<ScanTask> collectScanTasks(HttpRequest, RequestContext)

// 请求处理器版本
private List<ScanTask> collectScanTasks(HttpRequestToBeSent, RequestContext)
```

### 2. getContentType
```java
// 响应处理器版本
private String getContentType(HttpRequest)

// 请求处理器版本
private String getContentType(HttpRequestToBeSent)
```

### 3. checkAndMarkParameterAsScanning
```java
// 响应处理器版本
private boolean checkAndMarkParameterAsScanning(HttpRequest, ...)

// 请求处理器版本
private boolean checkAndMarkParameterAsScanning(HttpRequestToBeSent, ...)
```

---

## 🤔 为什么不用类型转换？

### 方案A：强制类型转换（❌ 不推荐）

```java
HttpRequest initiatingRequest = responseReceived.initiatingRequest();

// 强制转换
HttpRequestToBeSent converted = (HttpRequestToBeSent) initiatingRequest;
collectScanTasks(converted, context);

// ❌ 问题：
// 1. 运行时可能抛出 ClassCastException
// 2. initiatingRequest 不一定是 HttpRequestToBeSent 的实例
// 3. 不安全，容易出错
```

### 方案B：方法重载（✅ 推荐）

```java
HttpRequest initiatingRequest = responseReceived.initiatingRequest();

// 直接调用，类型安全
collectScanTasks(initiatingRequest, context);

// ✅ 优点：
// 1. 编译时类型检查
// 2. 不需要类型转换
// 3. 安全可靠
```

---

## 💡 实际调用示例

### 场景1：响应处理器

```java
@Override
public ResponseReceivedAction handleHttpResponseReceived(
    HttpResponseReceived responseReceived) {
    
    // 1. 获取原始请求（HttpRequest 类型）
    HttpRequest initiatingRequest = responseReceived.initiatingRequest();
    
    // 2. 调用重载方法（自动匹配 HttpRequest 版本）
    List<ScanTask> scanTasks = collectScanTasks(initiatingRequest, context);
    //                         ^^^^^^^^^^^^^^^^ 版本1被调用
    
    String contentType = getContentType(initiatingRequest);
    //                   ^^^^^^^^^^^^^^ 版本1被调用
    
    boolean isDuplicate = checkAndMarkParameterAsScanning(
        initiatingRequest, param, config, contentType);
    //  ^^^^^^^^^^^^^^^^^^^ 版本1被调用
}
```

### 场景2：请求处理器（如果将来还需要）

```java
@Override
public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent requestToBeSent) {
    
    // 1. 已经有 HttpRequestToBeSent 类型
    
    // 2. 调用重载方法（自动匹配 HttpRequestToBeSent 版本）
    List<ScanTask> scanTasks = collectScanTasks(requestToBeSent, context);
    //                         ^^^^^^^^^^^^^^^ 版本2被调用
    
    String contentType = getContentType(requestToBeSent);
    //                   ^^^^^^^^^^^^^^ 版本2被调用
    
    boolean isDuplicate = checkAndMarkParameterAsScanning(
        requestToBeSent, param, config, contentType);
    //  ^^^^^^^^^^^^^^^^^^^ 版本2被调用
}
```

---

## 🎯 总结

### 为什么需要两个版本？

| 原因 | 说明 |
|------|------|
| **API 限制** | Burp API 在不同处理器中返回不同类型 |
| **类型安全** | 避免强制类型转换的风险 |
| **时序修复** | 被动扫描从请求处理器移到响应处理器 |
| **可维护性** | 代码清晰，编译时检查 |

### 重载的优势

1. ✅ **类型安全**：编译时检查，不会有运行时错误
2. ✅ **自动匹配**：Java 自动选择正确的版本
3. ✅ **代码清晰**：明确支持两种类型
4. ✅ **易于维护**：逻辑相同，修改时同步更新

### 是否可以合并？

**不可以**，因为：
- `HttpRequest` 和 `HttpRequestToBeSent` 是不同的接口
- 虽然 `HttpRequestToBeSent` 继承 `HttpRequest`
- 但 `responseReceived.initiatingRequest()` 返回的是基类 `HttpRequest`
- 不能假定它一定是 `HttpRequestToBeSent` 的实例

---

## 📚 相关概念

### Java 方法重载（Method Overloading）

**定义**：同一个类中，方法名相同，但参数列表不同

**示例**：
```java
public void print(String s) { ... }      // 版本1
public void print(int i) { ... }         // 版本2
public void print(String s, int i) { ... } // 版本3

// 调用
print("hello");      // → 版本1
print(123);          // → 版本2
print("hello", 123); // → 版本3
```

### 多态（Polymorphism）

虽然 `HttpRequestToBeSent` 继承 `HttpRequest`，但：

```java
HttpRequest req = responseReceived.initiatingRequest();
// req 的编译时类型是 HttpRequest
// req 的运行时类型可能是 HttpRequest 或 HttpRequestToBeSent
// 为了安全，我们只能把它当作 HttpRequest 使用
```

---

**文档作者**：AI Assistant  
**日期**：2025-10-11  
**适用版本**：XProbe 最新版


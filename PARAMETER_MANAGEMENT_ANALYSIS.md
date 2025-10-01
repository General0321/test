# XProbe 参数管理逻辑详细分析

## 一、参数管理架构

### 1. 参数分类（三层结构）

```
全局层（Global）
├── globalCustomDictionary (所有域名共享)
│
Host 层（按 host 区分）
├── HostData.parameters (该 host 收集的所有参数)
│
Endpoint 层（按接口区分）
└── EndpointInfo.parameters (该接口的参数)
```

### 2. 核心数据结构

```java
// 全局自定义字典（应用于所有域名）
private final Set<String> globalCustomDictionary = ConcurrentHashMap.newKeySet();

// 按 host 分组的数据存储
private final Map<String, HostData> hostDataMap = new ConcurrentHashMap<>();

class HostData {
    private final String host;              // 完整host，如 www.example.com
    private final String mainDomain;        // 主域名，如 example.com
    private final Set<String> parameters;   // 该 host 收集的所有参数
    private final Map<String, EndpointInfo> endpointInfoMap; // 接口信息
    
    class EndpointInfo {
        private final Set<String> parameters; // 该接口的参数
    }
}
```

---

## 二、回答你的具体问题

### ✅ Q1: Arjun 支持上传参数吗？

**答案：是的，支持！**

提供了以下 API：

1. **单个添加**：
```java
public void addGlobalCustomParameter(String parameter)
```

2. **批量添加**：
```java
public void addGlobalCustomParameters(Set<String> parameters)
```

3. **从文件导入**（间接支持）：
```java
public void importUrlsFromFile(String filePath, String method)
// 虽然这个是导入 URL，但可以扩展支持参数导入
```

4. **获取和清空**：
```java
public Set<String> getGlobalCustomDictionary()
public void clearGlobalCustomDictionary()
```

---

### ✅ Q2: 上传的参数和收集的参数会合并并去重吗？

**答案：会的！自动合并和去重！**

#### 合并时机
在执行 Arjun 扫描时，会合并三种参数：

```java
// 代码位置：performManualArjunScan() - Line 297-298
Set<String> hostParams = new HashSet<>(hostData.getParameters());  // 1. Host 收集的参数
hostParams.addAll(globalCustomDictionary);                          // 2. 全局自定义参数
// (还会添加 COMMON_PARAMETERS)                                     // 3. 常见参数

// 代码位置：addManualUrl() - Line 915-916
Set<String> hostParams = new HashSet<>(hostData.getParameters());
hostParams.addAll(globalCustomDictionary);
```

#### 去重机制
使用 `Set` 数据结构，**自动去重**：
- `globalCustomDictionary` 是 `ConcurrentHashMap.newKeySet()`
- `HostData.parameters` 是 `ConcurrentHashMap.newKeySet()`
- 合并时创建 `new HashSet<>()`，自动去重

#### 示例
```
Host 收集: [id, name, email]
全局上传: [email, token, api_key]
常见参数: [id, user, password]

合并结果: [id, name, email, token, api_key, user, password]
         ↑ 自动去重，不会重复
```

---

### ⚠️ Q3: 上传的参数是不是应用在所有域名？

**答案：是的，但有设计问题！**

#### 当前实现
```java
// 全局自定义字典应用于所有域名
private final Set<String> globalCustomDictionary = ConcurrentHashMap.newKeySet();
```

**应用场景**：
- ✅ 手动触发 Arjun 扫描时：`globalCustomDictionary` 会与每个 host 的参数合并
- ✅ 手动添加 URL 扫描时：`globalCustomDictionary` 会被使用

#### 设计问题
**当前是按 `host` 区分，不是按 `mainDomain` 区分！**

```java
// HostData 虽然有 mainDomain 字段，但实际使用的是 host
class HostData {
    private final String host;         // www.example.com
    private final String mainDomain;   // example.com (未充分使用)
}
```

**问题示例**：
```
www.example.com   → HostData1 (parameters: [id, name])
api.example.com   → HostData2 (parameters: [token, key])
admin.example.com → HostData3 (parameters: [user, pass])

这三个都是 example.com 的子域名，但参数是分开管理的！
```

**有一个 `createMainDomainDictionary()` 方法可以合并主域名参数，但未被使用！**

---

### ⚠️ Q4: 主动收集参数是不是按照主域名来区分的？

**答案：不是！当前是按 `host` 区分，不是按主域名！**

#### 当前实现

```java
// hostDataMap 的 key 是完整的 host
private final Map<String, HostData> hostDataMap = new ConcurrentHashMap<>();

// 在 processNewRequest 中
String host = new URL(url).getHost();  // 完整 host
HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
```

#### 证据

```java
// Line 1080-1093
private static class HostData {
    private final String host;              // www.example.com
    private final String mainDomain;        // example.com
    
    public HostData(String host) {
        this.host = host;
        this.mainDomain = extractMainDomain(host);  // 提取了但很少使用
    }
}
```

**mainDomain 只在以下地方使用**：
1. `createMainDomainDictionary()` - 创建主域名字典（但这个方法未被调用）
2. `getMainDomain()` - getter 方法

#### 问题影响

**场景**：同一个主域名的多个子域名
```
访问 www.example.com/api/users?id=1&name=test
  → hostDataMap["www.example.com"].parameters = {id, name}

访问 api.example.com/v1/data?token=xxx&key=yyy
  → hostDataMap["api.example.com"].parameters = {token, key}

访问 admin.example.com/login?user=admin&pass=123
  → hostDataMap["admin.example.com"].parameters = {user, pass}
```

**结果**：
- 三个子域名的参数是**分开管理**的
- 在对 `www.example.com` 进行 Arjun 扫描时，**不会**使用 `api.example.com` 收集的参数
- 但**都会**使用 `globalCustomDictionary` 中的参数

---

### ✅ Q5: 收集参数的逻辑是什么？

**答案：完整的参数收集流程如下**

#### 流程图
```
HTTP 请求到达
    ↓
processNewRequest()
    ↓
extractParameters(request)  ← 从请求中提取参数
    ↓
updateTargetDataWithDetection()
    ├── 检查是否有新参数
    ├── 添加 endpoint 信息
    └── 调用 addParameterToEndpoint()
            ↓
        hostData.parameters.add(parameter)      ← 添加到 host 级别
        endpointInfo.parameters.add(parameter)  ← 添加到 endpoint 级别
```

#### 详细代码分析

**1. 提取参数** (Line 621-627)
```java
private Set<String> extractParameters(HttpRequest request) {
    Set<String> parameters = new HashSet<>();
    for (ParsedHttpParameter param : request.parameters()) {
        parameters.add(param.name());  // 只提取参数名，不关心值
    }
    return parameters;
}
```

**2. 更新目标数据** (Line 195-222)
```java
private boolean updateTargetDataWithDetection(String host, String endpoint, 
                                             String method, String contentType, 
                                             Set<String> parameters, 
                                             HttpRequest request) {
    // 获取或创建 HostData
    HostData hostData = hostDataMap.computeIfAbsent(host, HostData::new);
    
    // 检查是否有新参数
    boolean hasNewParameters = false;
    for (String parameter : parameters) {
        if (!hostData.getParameters().contains(parameter)) {
            hasNewParameters = true;
            break;
        }
    }
    
    // 更新接口信息
    EndpointInfo epInfo = hostData.addEndpoint(endpoint, method, contentType, parameters);
    
    // 保存请求模板
    epInfo.setTemplateRequest(request);
    
    // 更新参数
    for (String parameter : parameters) {
        hostData.addParameterToEndpoint(endpoint, parameter);
    }
    
    return hasNewParameters;
}
```

**3. 添加参数到接口** (Line 1117-1121)
```java
public void addParameterToEndpoint(String endpoint, String parameter) {
    parameters.add(parameter);  // ← 添加到 host 级别的 parameters
    endpointInfoMap.computeIfAbsent(endpoint, 
        k -> new EndpointInfo(endpoint, "GET", "application/x-www-form-urlencoded", new HashSet<>()))
        .addParameter(parameter);  // ← 添加到 endpoint 级别的 parameters
}
```

#### 收集来源
参数来自以下几个途径：

1. **被动流量收集**：
   - 在 `processNewRequest()` 中自动提取
   - 来源：Proxy、Repeater 等工具

2. **手动添加**：
   - 全局自定义参数：`addGlobalCustomParameter()`
   - 不会添加到 host 的 parameters，而是单独存储

3. **Arjun 扫描发现**：
   - Arjun 扫描成功后，会将发现的参数添加到 host
   - 代码位置：Line 323-325

```java
// 将发现的参数添加到 host 参数集合
for (String param : result.getFoundParameters()) {
    hostData.addParameterToEndpoint(finalEndpoint, param);
}
```

---

### ❌ Q6: 这块代码独立出来了吗？

**答案：没有完全独立，耦合较重！**

#### 当前状态

**参数管理逻辑分散在多个地方**：

1. **RealtimeScanner.java** (1297 行)
   - 包含参数收集、存储、管理的主要逻辑
   - 包含 HostData、EndpointInfo 内部类
   - 包含全局字典管理
   - **耦合度：极高**

2. **ArjunIntegration.java**
   - 负责调用 Arjun 工具
   - 创建参数字典文件
   - 相对独立，但依赖 ExternalToolConfig

3. **ExternalToolConfig.java**
   - 管理外部工具配置
   - 包含默认自定义字典
   - 相对独立

#### 问题分析

**职责混乱**：
```
RealtimeScanner 承担了太多职责：
├── 实时扫描控制
├── 参数收集和管理  ← 应该独立
├── Host 数据管理    ← 应该独立
├── Arjun 集成调用
├── 状态持久化      ← 应该独立
├── 全局字典管理    ← 应该独立
└── 队列和线程管理
```

#### 建议重构

**应该拆分成以下独立模块**：

```
1. ParameterManager (参数管理器)
   ├── 全局参数字典管理
   ├── 参数上传/导入
   ├── 参数合并去重逻辑
   └── 参数持久化

2. HostDataManager (Host 数据管理器)
   ├── HostData 存储
   ├── 主域名分组逻辑
   ├── 参数收集
   └── 接口信息管理

3. ArjunOrchestrator (Arjun 编排器)
   ├── 扫描任务调度
   ├── 参数字典生成
   ├── 结果处理
   └── 去重管理

4. StateManager (状态管理器)
   ├── 状态持久化
   ├── 状态加载
   └── JSON 序列化
```

---

## 三、发现的问题

### 🔴 严重问题

#### 1. 按 Host 区分，未按主域名分组

**问题**：
- 虽然提取了 `mainDomain`，但实际使用的是完整 `host`
- 同一主域名的不同子域名，参数不共享

**影响**：
```
www.example.com   收集了 10 个参数
api.example.com   收集了 15 个参数
admin.example.com 收集了 8 个参数

对 www.example.com 扫描时，只用自己的 10 个参数，
不会使用 api 和 admin 收集的 23 个参数！
```

**建议修复**：
```java
// 选项1: 使用主域名作为 key
hostDataMap.computeIfAbsent(mainDomain, HostData::new);

// 选项2: 提供配置选项
private boolean groupByMainDomain = true;  // 用户可配置

// 选项3: 在扫描时合并主域名参数
Set<String> params = getParametersForMainDomain(mainDomain);
```

---

#### 2. createMainDomainDictionary() 方法未被使用

**位置**：Line 675-693

```java
private String createMainDomainDictionary(String mainDomain) throws IOException {
    // 这个方法可以合并同主域名下所有 host 的参数
    // 但从未被调用！
}
```

**建议**：
- 要么删除这个方法
- 要么在 Arjun 扫描时使用它

---

### 🟡 中等问题

#### 3. 参数管理逻辑未独立

**问题**：
- 所有参数管理逻辑都在 `RealtimeScanner` 中
- 1297 行的单一文件，职责过多
- 难以单独测试和维护

**建议**：
- 拆分成独立的 `ParameterManager` 类
- 提取 `HostData` 为独立的数据模型
- 使用依赖注入降低耦合

---

#### 4. 缺少参数导入功能

**当前**：
- 只能通过代码 API 添加参数
- 没有从文件批量导入参数的功能

**建议添加**：
```java
/**
 * 从文件导入参数到全局字典
 * @param filePath 参数文件路径，每行一个参数名
 */
public void importParametersFromFile(String filePath) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                addGlobalCustomParameter(line);
            }
        }
    }
}

/**
 * 导出全局参数到文件
 */
public void exportParametersToFile(String filePath) throws IOException {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
        for (String param : globalCustomDictionary) {
            writer.println(param);
        }
    }
}
```

---

## 四、优化建议

### 建议 1: 添加主域名分组选项

```java
// 在 RealtimeScanner 中添加配置
private boolean useMainDomainGrouping = true;  // 默认启用主域名分组

public void setUseMainDomainGrouping(boolean enabled) {
    this.useMainDomainGrouping = enabled;
}

// 修改 processNewRequest
private void processNewRequest(HttpRequest request) {
    String url = request.url();
    String host = new URL(url).getHost();
    
    // 根据配置决定使用完整 host 还是主域名
    String key = useMainDomainGrouping ? extractMainDomain(host) : host;
    
    HostData hostData = hostDataMap.computeIfAbsent(key, HostData::new);
    // ...
}
```

---

### 建议 2: 提取参数管理器

```java
/**
 * 参数管理器 - 统一管理所有参数
 */
public class ParameterManager {
    private final Set<String> globalParameters = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> hostParameters = new ConcurrentHashMap<>();
    
    // 全局参数管理
    public void addGlobalParameter(String param) { }
    public void addGlobalParameters(Set<String> params) { }
    public Set<String> getGlobalParameters() { }
    
    // Host 参数管理
    public void addHostParameter(String host, String param) { }
    public Set<String> getHostParameters(String host) { }
    
    // 合并参数（去重）
    public Set<String> getMergedParameters(String host) {
        Set<String> merged = new HashSet<>(globalParameters);
        merged.addAll(getHostParameters(host));
        return merged;
    }
    
    // 导入导出
    public void importFromFile(String filePath) { }
    public void exportToFile(String filePath) { }
}
```

---

### 建议 3: 使用主域名字典

```java
// 在执行 Arjun 扫描时
private void performManualArjunScan() {
    // ...
    for (Map.Entry<String, List<HttpRequest>> entry : hostToRequests.entrySet()) {
        String host = entry.getKey();
        String mainDomain = extractMainDomain(host);
        
        // 使用主域名级别的参数字典
        Set<String> params = getParametersForMainDomain(mainDomain);
        params.addAll(globalCustomDictionary);
        
        // 执行扫描...
    }
}

// 辅助方法
private Set<String> getParametersForMainDomain(String mainDomain) {
    Set<String> params = new HashSet<>();
    for (Map.Entry<String, HostData> entry : hostDataMap.entrySet()) {
        if (extractMainDomain(entry.getKey()).equals(mainDomain)) {
            params.addAll(entry.getValue().getParameters());
        }
    }
    return params;
}
```

---

## 五、总结

### 当前实现优点 ✅

1. **自动去重**：使用 `Set` 数据结构
2. **线程安全**：使用 `ConcurrentHashMap.newKeySet()`
3. **三层管理**：全局、Host、Endpoint 层次清晰
4. **支持上传**：提供 API 添加自定义参数
5. **自动合并**：Arjun 扫描时会合并多个来源的参数

### 主要问题 ❌

1. **未按主域名分组**：同主域名子域名参数不共享
2. **代码未独立**：参数管理逻辑在 RealtimeScanner 中
3. **缺少导入功能**：无法从文件批量导入参数
4. **有未使用方法**：`createMainDomainDictionary()` 未被调用

### 优先修复建议 🔧

**高优先级**：
1. 实现主域名分组功能（或提供配置选项）
2. 添加参数导入/导出功能

**中优先级**：
3. 提取 ParameterManager 独立类
4. 使用或删除 `createMainDomainDictionary()`

**低优先级**：
5. 重构 RealtimeScanner 拆分职责
6. 添加更多参数管理功能（统计、清理等）

---

**分析时间**: 2025-10-01  
**代码版本**: XProbe 1.0.0


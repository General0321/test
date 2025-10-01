# XProbe 可扩展性架构设计

> **设计目标**: 让添加新的漏洞扫描类型变得简单，无需修改核心代码  
> **设计日期**: 2025-10-01

---

## 🎯 设计目标

### 当前痛点
1. ❌ 添加新扫描器需要修改 `ScannerFactory.initializeScanners()`
2. ❌ 扫描器类型硬编码
3. ❌ 无法动态加载外部扫描器
4. ❌ 规则配置不够灵活

### 期望目标
1. ✅ 添加新扫描器只需创建一个类，无需修改其他代码
2. ✅ 支持外部JAR加载自定义扫描器
3. ✅ 规则配置可以导入导出和分享
4. ✅ UI自动识别可用的扫描器类型

---

## 📐 架构设计方案

### 方案1: 注解驱动的自动注册（推荐）

**优点**: 简单易用，Java原生支持  
**缺点**: 需要编译到主JAR中

#### 实现步骤

##### 1. 创建扫描器注解

```java
// src/main/java/com/xprobe/scanner/scanners/ScannerPlugin.java
package com.xprobe.scanner.scanners;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScannerPlugin {
    /**
     * 扫描器类型标识（唯一）
     */
    String type();
    
    /**
     * 扫描器名称
     */
    String name();
    
    /**
     * 扫描器描述
     */
    String description() default "";
    
    /**
     * 版本号
     */
    String version() default "1.0.0";
    
    /**
     * 作者
     */
    String author() default "";
    
    /**
     * 是否默认启用
     */
    boolean enabled() default true;
}
```

##### 2. 使用注解标记扫描器

```java
// SQLScanner.java
@ScannerPlugin(
    type = "sql",
    name = "SQL注入扫描器",
    description = "检测SQL注入漏洞",
    version = "1.0.0",
    author = "XProbe Team"
)
public class SQLScanner extends AbstractScanner {
    // ... 实现代码
}

// 添加新的XSS扫描器 - 只需创建这个类！
@ScannerPlugin(
    type = "xss",
    name = "XSS扫描器",
    description = "检测跨站脚本漏洞",
    version = "1.0.0",
    author = "XProbe Team"
)
public class XSSScanner extends AbstractScanner {
    
    public XSSScanner(MontoyaApi api, RealtimeScannerRefactored realtimeScanner) {
        super(api, realtimeScanner);
    }
    
    @Override
    public String getType() {
        return "xss";
    }
    
    @Override
    public String getName() {
        return "XSS Scanner";
    }
    
    @Override
    public String getDescription() {
        return "检测跨站脚本漏洞";
    }
    
    @Override
    protected ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                   HttpRequest modifiedRequest, String payload) {
        // 发送请求
        HttpRequestResponse requestResponse = sendRequest(modifiedRequest);
        HttpResponse response = requestResponse.response();
        
        // 检查响应
        boolean isVulnerable = isResponseMatch(
            response.bodyToString(),
            response.statusCode(),
            System.currentTimeMillis(),
            task.getConfiguration().getMatchRules()
        );
        
        if (isVulnerable) {
            return new ScanResult.Builder()
                .vulnerable(true)
                .scanType(getType())
                .parameterName(task.getParameter().name())
                .payload(payload)
                .originalRequest(originalRequest)
                .modifiedRequest(modifiedRequest)
                .response(response)
                .evidence("XSS vulnerability detected with payload: " + payload)
                .build();
        }
        
        return null;
    }
    
    @Override
    public List<String> getPayloads() {
        return Arrays.asList(); // 从配置获取
    }
}
```

##### 3. 改进 ScannerFactory 支持自动注册

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import org.reflections.Reflections;
import java.lang.reflect.Constructor;
import java.util.*;

public class ScannerFactory {
    private final Map<String, Scanner> scanners = new HashMap<>();
    private final Map<String, ScannerMetadata> scannerMetadata = new HashMap<>();
    private final MontoyaApi api;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    
    public ScannerFactory(MontoyaApi api, 
                         com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
        autoDiscoverScanners();
    }
    
    /**
     * 自动发现并注册所有标记了 @ScannerPlugin 的扫描器
     */
    private void autoDiscoverScanners() {
        api.logging().raiseInfoEvent("开始自动发现扫描器...");
        
        // 扫描包下所有带 @ScannerPlugin 注解的类
        Reflections reflections = new Reflections("com.xprobe.scanner.scanners");
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(ScannerPlugin.class);
        
        int registered = 0;
        for (Class<?> clazz : annotatedClasses) {
            if (Scanner.class.isAssignableFrom(clazz)) {
                try {
                    registerScannerClass((Class<? extends Scanner>) clazz);
                    registered++;
                } catch (Exception e) {
                    api.logging().raiseErrorEvent(
                        "注册扫描器失败: " + clazz.getName() + " - " + e.getMessage()
                    );
                }
            }
        }
        
        api.logging().raiseInfoEvent(
            String.format("扫描器自动发现完成，共注册 %d 个扫描器", registered)
        );
    }
    
    /**
     * 注册扫描器类
     */
    private void registerScannerClass(Class<? extends Scanner> scannerClass) throws Exception {
        ScannerPlugin annotation = scannerClass.getAnnotation(ScannerPlugin.class);
        
        if (!annotation.enabled()) {
            api.logging().raiseDebugEvent(
                "跳过禁用的扫描器: " + annotation.name()
            );
            return;
        }
        
        // 使用反射创建实例
        Constructor<? extends Scanner> constructor = 
            scannerClass.getConstructor(MontoyaApi.class, 
                com.xprobe.scanner.active.RealtimeScannerRefactored.class);
        Scanner scanner = constructor.newInstance(api, realtimeScanner);
        
        // 保存元数据
        ScannerMetadata metadata = new ScannerMetadata(
            annotation.type(),
            annotation.name(),
            annotation.description(),
            annotation.version(),
            annotation.author()
        );
        
        // 注册
        scanners.put(annotation.type(), scanner);
        scannerMetadata.put(annotation.type(), metadata);
        
        api.logging().raiseInfoEvent(
            String.format("✅ 注册扫描器: %s (%s) - %s", 
                annotation.name(), annotation.type(), annotation.version())
        );
    }
    
    /**
     * 手动注册扫描器（支持运行时动态添加）
     */
    public void registerCustomScanner(Scanner scanner) {
        ScannerPlugin annotation = scanner.getClass().getAnnotation(ScannerPlugin.class);
        
        if (annotation != null) {
            scanners.put(annotation.type(), scanner);
            scannerMetadata.put(annotation.type(), new ScannerMetadata(
                annotation.type(),
                annotation.name(),
                annotation.description(),
                annotation.version(),
                annotation.author()
            ));
            api.logging().raiseInfoEvent("手动注册扫描器: " + annotation.name());
        } else {
            // 降级处理：如果没有注解，使用默认元数据
            scanners.put(scanner.getType(), scanner);
            api.logging().raiseInfoEvent("注册扫描器: " + scanner.getName());
        }
    }
    
    /**
     * 根据类型获取扫描器
     */
    public Scanner getScanner(String type) {
        Scanner scanner = scanners.get(type);
        if (scanner == null) {
            api.logging().raiseErrorEvent("未知的扫描器类型: " + type);
        }
        return scanner;
    }
    
    /**
     * 获取所有可用的扫描器类型
     */
    public String[] getAvailableScannerTypes() {
        return scanners.keySet().toArray(new String[0]);
    }
    
    /**
     * 获取扫描器元数据
     */
    public ScannerMetadata getScannerMetadata(String type) {
        return scannerMetadata.get(type);
    }
    
    /**
     * 获取所有扫描器元数据
     */
    public List<ScannerMetadata> getAllScannerMetadata() {
        return new ArrayList<>(scannerMetadata.values());
    }
    
    /**
     * 扫描器元数据
     */
    public static class ScannerMetadata {
        private final String type;
        private final String name;
        private final String description;
        private final String version;
        private final String author;
        
        public ScannerMetadata(String type, String name, String description, 
                              String version, String author) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.version = version;
            this.author = author;
        }
        
        // Getters
        public String getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getVersion() { return version; }
        public String getAuthor() { return author; }
    }
}
```

##### 4. 添加依赖（build.gradle）

```gradle
dependencies {
    // ... 现有依赖
    
    // 用于扫描器自动发现
    implementation 'org.reflections:reflections:0.10.2'
}
```

---

### 方案2: 基于配置文件的扫描器注册

**优点**: 不需要额外依赖，配置灵活  
**缺点**: 需要手动维护配置文件

#### scanners.yaml

```yaml
scanners:
  - type: sql
    class: com.xprobe.scanner.scanners.SQLScanner
    name: SQL注入扫描器
    description: 检测SQL注入漏洞
    version: 1.0.0
    enabled: true
    
  - type: lfi
    class: com.xprobe.scanner.scanners.LFIScanner
    name: LFI扫描器
    description: 检测本地文件包含漏洞
    version: 1.0.0
    enabled: true
    
  - type: ssrf
    class: com.xprobe.scanner.scanners.SSRFScanner
    name: SSRF扫描器
    description: 检测服务端请求伪造漏洞
    version: 1.0.0
    enabled: true
    
  # 添加新扫描器只需在这里添加配置！
  - type: xss
    class: com.xprobe.scanner.scanners.XSSScanner
    name: XSS扫描器
    description: 检测跨站脚本漏洞
    version: 1.0.0
    enabled: true
```

---

### 方案3: 外部JAR加载（最灵活）

**优点**: 完全插件化，无需重新编译主程序  
**缺点**: 实现复杂，需要类加载器

#### 实现概要

```java
public class ExternalScannerLoader {
    private final MontoyaApi api;
    
    /**
     * 从外部JAR加载扫描器
     */
    public List<Scanner> loadScannersFromJar(String jarPath) {
        List<Scanner> scanners = new ArrayList<>();
        
        try {
            // 创建自定义类加载器
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{new File(jarPath).toURI().toURL()},
                this.getClass().getClassLoader()
            );
            
            // 扫描JAR中的所有类
            // ... 查找实现了Scanner接口的类
            // ... 实例化并返回
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("加载外部扫描器失败: " + e.getMessage());
        }
        
        return scanners;
    }
}
```

---

## 🎨 规则模板系统

### 规则模板结构

```json
{
  "template_name": "SQL注入检测规则",
  "scanner_type": "sql",
  "version": "1.0.0",
  "author": "XProbe Team",
  "description": "通用SQL注入检测规则，适用于大多数场景",
  "parameter_names": ["id", "user_id", "product_id", "category"],
  "parameter_name_type": "String Match",
  "payloads": [
    "' OR 1=1--",
    "' AND SLEEP(5)--",
    "' UNION SELECT NULL--",
    "admin'--",
    "1' AND '1'='1"
  ],
  "match_rules": [
    {
      "location": "Response Body",
      "match_type": "Contains",
      "rule": "SQL syntax",
      "operator": "OR"
    },
    {
      "location": "Response Body",
      "match_type": "Contains",
      "rule": "mysql_fetch",
      "operator": "OR"
    },
    {
      "location": "Response Time",
      "match_type": "Greater Than",
      "rule": "5000",
      "operator": "OR"
    }
  ]
}
```

### 规则导入导出功能

```java
public class RuleTemplateManager {
    
    /**
     * 导出规则为JSON模板
     */
    public String exportRuleTemplate(Configuration config) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        RuleTemplate template = new RuleTemplate();
        template.setTemplateName(config.getCustomLabel() + " 规则");
        template.setScannerType(config.getCustomLabel());
        template.setParameterNames(config.getParameterNames());
        template.setPayloads(config.getParameterValues());
        template.setMatchRules(config.getMatchRules());
        
        return mapper.writeValueAsString(template);
    }
    
    /**
     * 从JSON模板导入规则
     */
    public Configuration importRuleTemplate(String json) {
        ObjectMapper mapper = new ObjectMapper();
        RuleTemplate template = mapper.readValue(json, RuleTemplate.class);
        
        Configuration config = new Configuration(
            template.getParameterNames(),
            template.getParameterNameType(),
            template.getPayloads(),
            template.getMatchRules(),
            template.getScannerType(),
            true
        );
        
        return config;
    }
}
```

---

## 🔌 UI集成

### 动态显示可用扫描器

```java
// PassiveScanConfigTab.java
public class PassiveScanConfigTab {
    
    private void initializeScannerTypeComboBox() {
        // 从ScannerFactory获取所有可用扫描器
        String[] availableTypes = scannerFactory.getAvailableScannerTypes();
        
        // 动态填充下拉框
        scannerTypeComboBox.setModel(new DefaultComboBoxModel<>(availableTypes));
        
        // 显示扫描器信息
        scannerTypeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                        int index, boolean isSelected,
                                                        boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (value != null) {
                    String type = value.toString();
                    ScannerMetadata metadata = scannerFactory.getScannerMetadata(type);
                    if (metadata != null) {
                        setText(String.format("%s (%s v%s)", 
                            metadata.getName(), 
                            metadata.getType(), 
                            metadata.getVersion()
                        ));
                    }
                }
                
                return this;
            }
        });
    }
}
```

### 扫描器管理界面

```java
// 新增：ScannerManagementTab.java
public class ScannerManagementTab {
    
    public Component createUI() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 扫描器列表
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"类型", "名称", "版本", "作者", "状态"}, 0
        );
        
        // 填充数据
        for (ScannerMetadata metadata : scannerFactory.getAllScannerMetadata()) {
            model.addRow(new Object[]{
                metadata.getType(),
                metadata.getName(),
                metadata.getVersion(),
                metadata.getAuthor(),
                "✅ 已启用"
            });
        }
        
        JTable table = new JTable(model);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        
        // 操作按钮
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(new JButton("➕ 加载外部扫描器"));
        buttonPanel.add(new JButton("📥 导入规则模板"));
        buttonPanel.add(new JButton("📤 导出规则模板"));
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
}
```

---

## 📝 添加新扫描器的完整示例

### 场景：添加XXE扫描器

#### 步骤1: 创建扫描器类

```java
package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.active.RealtimeScannerRefactored;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.Arrays;
import java.util.List;

@ScannerPlugin(
    type = "xxe",
    name = "XXE扫描器",
    description = "检测XML外部实体注入漏洞",
    version = "1.0.0",
    author = "Security Team"
)
public class XXEScanner extends AbstractScanner {
    
    public XXEScanner(MontoyaApi api, RealtimeScannerRefactored realtimeScanner) {
        super(api, realtimeScanner);
    }
    
    @Override
    public String getType() {
        return "xxe";
    }
    
    @Override
    public String getName() {
        return "XXE Scanner";
    }
    
    @Override
    public String getDescription() {
        return "检测XML外部实体注入漏洞";
    }
    
    @Override
    protected ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                   HttpRequest modifiedRequest, String payload) {
        long startTime = System.currentTimeMillis();
        
        // 发送请求
        HttpRequestResponse requestResponse = sendRequest(modifiedRequest);
        HttpResponse response = requestResponse.response();
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        
        // 检查响应是否匹配规则
        boolean isVulnerable = isResponseMatch(
            response.bodyToString(),
            response.statusCode(),
            responseTime,
            task.getConfiguration().getMatchRules()
        );
        
        if (isVulnerable) {
            return new ScanResult.Builder()
                .vulnerable(true)
                .scanType(getType())
                .parameterName(task.getParameter().name())
                .payload(payload)
                .originalRequest(originalRequest)
                .modifiedRequest(modifiedRequest)
                .response(response)
                .responseTime(responseTime)
                .evidence("XXE vulnerability detected with payload: " + payload)
                .build();
        }
        
        return null;
    }
    
    @Override
    public List<String> getPayloads() {
        return Arrays.asList(); // 从配置获取
    }
}
```

#### 步骤2: 在UI中配置规则

1. 打开"被动扫描规则"标签
2. 点击"添加规则"
3. 扫描类型：选择"xxe"（自动出现）
4. 配置参数名：`xml`, `data`, `content`
5. 添加Payload：
   ```xml
   <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><foo>&xxe;</foo>
   ```
6. 配置匹配规则：
   - 响应体包含 "root:"
   - 或响应体包含 "nobody:"

#### 步骤3: 保存并测试

就这么简单！无需修改任何其他代码。

---

## 📊 对比总结

| 特性 | 方案1: 注解驱动 | 方案2: 配置文件 | 方案3: 外部JAR |
|------|----------------|----------------|----------------|
| 易用性 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 灵活性 | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 实现复杂度 | ⭐⭐ | ⭐ | ⭐⭐⭐⭐ |
| 维护成本 | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| 推荐程度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

**推荐**: 使用**方案1（注解驱动）**作为主要扩展方式，未来可以考虑添加**方案3（外部JAR）**支持高级用户。

---

## 🚀 实施建议

### Phase 1: 基础扩展（立即实施）
1. ✅ 添加 `@ScannerPlugin` 注解
2. ✅ 改进 `ScannerFactory` 支持自动注册
3. ✅ 为现有扫描器添加注解
4. ✅ 测试自动发现机制

### Phase 2: 规则模板（2周内）
1. 创建 `RuleTemplateManager`
2. UI添加导入导出按钮
3. 提供常见漏洞的规则模板库

### Phase 3: 高级功能（1个月内）
1. 扫描器管理界面
2. 外部JAR加载支持
3. 规则市场（可选）

---

## 📚 开发文档模板

### 如何创建自定义扫描器

```markdown
# 创建自定义扫描器指南

## 1. 创建扫描器类

继承 `AbstractScanner` 并添加 `@ScannerPlugin` 注解。

## 2. 实现必要方法

- `getType()`: 返回唯一标识
- `getName()`: 返回显示名称
- `getDescription()`: 返回描述
- `performScan()`: 实现扫描逻辑

## 3. 编译并重新加载

扫描器会自动被发现和注册。

## 4. 配置规则

在UI中配置参数名、payload和匹配规则。

## 示例代码

[XXEScanner 示例代码]
```

---

**是否立即实施方案1（注解驱动）？**


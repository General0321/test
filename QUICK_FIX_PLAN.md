# XProbe 快速修复计划

## 🔴 P0 - 紧急修复 (今天完成)

### 1. 配置持久化修复

**问题**: "保存配置"按钮不会真正保存，重启后丢失

**快速修复方案** (2小时):

```java
// 1. 添加依赖 (build.gradle)
dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
}

// 2. 创建 XProbeConfig.java
package com.xprobe.scanner.config;

public class XProbeConfig {
    public List<String> whitelist;
    public List<String> blacklist;
    public boolean whitelistEnabled;
    public boolean blacklistEnabled;
    public String arjunPath = "arjun";
    public String collectionMode = "PARAMETERS_ONLY";
    public int bruteforceInterval = 60;
    public int minParameterCount = 3;
    public int maxConcurrentHosts = 5;
}

// 3. 创建 ConfigStorage.java
package com.xprobe.scanner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;

public class ConfigStorage {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.xprobe";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public void save(XProbeConfig config) throws IOException {
        Files.createDirectories(Paths.get(CONFIG_DIR));
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        }
    }
    
    public XProbeConfig load() {
        try {
            if (Files.exists(Paths.get(CONFIG_FILE))) {
                try (Reader reader = new FileReader(CONFIG_FILE)) {
                    return gson.fromJson(reader, XProbeConfig.class);
                }
            }
        } catch (IOException e) {
            // Log error
        }
        return new XProbeConfig(); // 默认配置
    }
}

// 4. 修改 UnifiedConfigTab.java
public class UnifiedConfigTab {
    private final ConfigStorage configStorage = new ConfigStorage();
    private XProbeConfig config;
    
    public UnifiedConfigTab(...) {
        this.config = configStorage.load(); // 加载保存的配置
        initializeComponents();
        setupLayout();
        loadConfigToUI(); // 填充UI
    }
    
    private void saveAllConfigurations() {
        try {
            // 从UI收集
            config.whitelist = Arrays.asList(whitelistTextArea.getText().split("\n"));
            config.blacklist = Arrays.asList(blacklistTextArea.getText().split("\n"));
            config.whitelistEnabled = whitelistEnabledCheckBox.isSelected();
            config.arjunPath = arjunPathField.getText();
            config.collectionMode = parameterCollectionModeComboBox.getSelectedIndex() == 0 ? 
                "PARAMETERS_ONLY" : "PARAMETERS_AND_KEYWORDS";
            
            // 持久化
            configStorage.save(config);
            
            // 应用到运行时
            globalFilter.updateWhitelist(config.whitelist, config.whitelistEnabled);
            globalFilter.updateBlacklist(config.blacklist, config.blacklistEnabled);
            realtimeScanner.setCollectionMode(
                CollectionMode.valueOf(config.collectionMode));
            
            showStatus("✓ 配置已保存到 " + CONFIG_FILE, true);
        } catch (IOException e) {
            showStatus("✗ 保存失败: " + e.getMessage(), false);
        }
    }
}
```

**验证**:
1. 修改配置 → 点击"保存配置"
2. 重启 Burp Suite
3. 检查配置是否保留

---

## 🟡 P1 - 重要修复 (本周完成)

### 2. 删除旧版 RealtimeScanner (30分钟)

```bash
# 1. 删除旧文件
rm src/main/java/com/xprobe/scanner/active/RealtimeScanner.java

# 2. 重命名新文件
mv src/main/java/com/xprobe/scanner/active/RealtimeScannerRefactored.java \
   src/main/java/com/xprobe/scanner/active/RealtimeScanner.java

# 3. 全局搜索替换
# RealtimeScannerRefactored → RealtimeScanner
```

### 3. Dashboard 自动更新 (1小时)

```java
// 1. ParameterCollector.java - 添加监听器
public class ParameterCollector {
    private final List<Runnable> updateListeners = new CopyOnWriteArrayList<>();
    
    public void addUpdateListener(Runnable listener) {
        updateListeners.add(listener);
    }
    
    public boolean collectFromRequest(HttpRequest request) {
        boolean hasNew = // ... 原有逻辑
        
        if (hasNew) {
            // 通知所有监听器
            updateListeners.forEach(listener -> {
                try {
                    listener.run();
                } catch (Exception e) {
                    // Log error
                }
            });
        }
        
        return hasNew;
    }
}

// 2. DashboardTab.java - 订阅更新
public void setParameterCollector(ParameterCollector collector) {
    this.parameterCollector = collector;
    
    // 订阅更新
    collector.addUpdateListener(() -> {
        SwingUtilities.invokeLater(() -> {
            updateStatistics();
        });
    });
    
    updateStatistics();
}
```

### 4. 配置验证 (1小时)

```java
// UnifiedConfigTab.java
private boolean validateConfig() {
    // Arjun 路径验证
    String arjunPath = arjunPathField.getText().trim();
    if (!arjunPath.isEmpty()) {
        File arjunFile = new File(arjunPath);
        if (!arjunFile.exists()) {
            JOptionPane.showMessageDialog(panel,
                "Arjun工具不存在: " + arjunPath,
                "配置错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    // Burp代理地址验证
    String proxy = burpProxyField.getText().trim();
    if (!proxy.matches("^[^:]+:\\d+$")) {
        JOptionPane.showMessageDialog(panel,
            "代理地址格式错误，应为: host:port",
            "配置错误", JOptionPane.ERROR_MESSAGE);
        return false;
    }
    
    return true;
}

private void saveAllConfigurations() {
    if (!validateConfig()) {
        return; // 验证失败，不保存
    }
    
    try {
        // ... 保存逻辑
    }
}
```

---

## 🔵 P2 - 改进优化 (下周完成)

### 5. 异常处理改进

```java
// 统一异常处理
public class XProbeException extends Exception {
    public XProbeException(String message) {
        super(message);
    }
    
    public XProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}

// ConfigStorage.java
public void save(XProbeConfig config) throws XProbeException {
    try {
        Files.createDirectories(Paths.get(CONFIG_DIR));
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        }
    } catch (IOException e) {
        throw new XProbeException("保存配置失败: " + e.getMessage(), e);
    }
}

// UI层处理
private void saveAllConfigurations() {
    try {
        configStorage.save(config);
        showStatus("✓ 配置已保存", true);
    } catch (XProbeException e) {
        showStatus("✗ " + e.getMessage(), false);
        api.logging().raiseErrorEvent(e.getMessage());
    }
}
```

### 6. 配置默认值管理

```java
// DefaultConfig.java
public class DefaultConfig {
    public static final String DEFAULT_ARJUN_PATH = "arjun";
    public static final String DEFAULT_PROXY = "127.0.0.1:8080";
    public static final int DEFAULT_THREADS = 5;
    public static final int DEFAULT_TIMEOUT = 15;
    public static final int DEFAULT_BRUTEFORCE_INTERVAL = 60;
    public static final int DEFAULT_MIN_PARAM_COUNT = 3;
    public static final int DEFAULT_MAX_CONCURRENT = 5;
    public static final String DEFAULT_COLLECTION_MODE = "PARAMETERS_ONLY";
}

// XProbeConfig.java
public class XProbeConfig {
    public String arjunPath = DefaultConfig.DEFAULT_ARJUN_PATH;
    public String burpProxy = DefaultConfig.DEFAULT_PROXY;
    public int threadCount = DefaultConfig.DEFAULT_THREADS;
    // ...
}
```

---

## 📋 执行检查清单

### Phase 1 - 紧急修复 (今天)
- [ ] 添加 Gson 依赖
- [ ] 创建 XProbeConfig.java
- [ ] 创建 ConfigStorage.java
- [ ] 修改 UnifiedConfigTab 实现真正的保存
- [ ] 测试配置持久化

### Phase 2 - 重要修复 (本周)
- [ ] 删除 RealtimeScanner.java 旧版
- [ ] 重命名 RealtimeScannerRefactored
- [ ] 更新所有引用
- [ ] 实现 ParameterCollector 监听器
- [ ] Dashboard 订阅自动更新
- [ ] 添加配置验证
- [ ] 测试所有功能

### Phase 3 - 优化改进 (下周)
- [ ] 统一异常处理
- [ ] 创建 DefaultConfig
- [ ] 移除硬编码值
- [ ] 添加配置迁移逻辑
- [ ] 编写单元测试
- [ ] 更新文档

---

## 🧪 测试计划

### 配置持久化测试
1. 修改黑白名单 → 保存 → 重启 → 验证恢复
2. 修改 Arjun 配置 → 保存 → 重启 → 验证恢复
3. 修改参数收集模式 → 保存 → 重启 → 验证恢复
4. 删除配置文件 → 重启 → 验证使用默认值

### Dashboard 更新测试
1. 启动扩展 → 浏览网站 → 观察 Dashboard 实时更新
2. 收集参数 → 验证统计自动刷新
3. 多个域名 → 验证分别统计

### 配置验证测试
1. 输入无效 Arjun 路径 → 验证拒绝保存
2. 输入无效代理地址 → 验证拒绝保存
3. 输入负数参数 → 验证拒绝保存

---

## 📊 进度追踪

| 任务 | 优先级 | 预估时间 | 实际时间 | 状态 |
|------|--------|----------|----------|------|
| 配置持久化 | P0 | 2h | - | ⏳ 待开始 |
| 删除旧代码 | P1 | 0.5h | - | ⏳ 待开始 |
| Dashboard更新 | P1 | 1h | - | ⏳ 待开始 |
| 配置验证 | P1 | 1h | - | ⏳ 待开始 |
| 异常处理 | P2 | 2h | - | ⏳ 待开始 |
| 默认值管理 | P2 | 1h | - | ⏳ 待开始 |

---

**创建日期**: 2025-10-01  
**预计完成**: 2025-10-08 (1周)  
**总工时**: 8-10小时


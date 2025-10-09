# XProbe配置持久化完整性验证

## 📋 配置保存概述

XProbe的配置保存分为**两部分**：

### 1. 主配置文件（`~/.xprobe/config.json`）
保存所有插件配置，**包括扫描规则**

### 2. 规则导出文件（可选，用户自定义路径）
仅保存扫描规则，方便跨环境迁移

---

## ✅ 配置项完整性检查

### 🔒 已修复的Bug
**问题**：`copy()`方法遗漏了4个Arjun高级配置的复制
- ❌ 遗漏：`arjunStableMode`、`arjunThreads`、`arjunMaxRetries`、`arjunRateLimit`
- ✅ 已修复：现在所有配置项都会正确复制

### 📊 完整配置项列表（共36个配置项）

#### 1️⃣ 黑白名单配置（4项）
- [x] `whitelist` - 白名单列表
- [x] `blacklist` - 黑名单列表
- [x] `whitelistEnabled` - 白名单开关
- [x] `blacklistEnabled` - 黑名单开关

#### 2️⃣ Arjun基础配置（4项）
- [x] `arjunEnabled` - Arjun启用状态
- [x] `arjunChunkSize` - 批次大小（默认250）
- [x] `arjunTimeout` - 超时时间（默认15秒）
- [x] `arjunCustomDictionary` - 自定义字典

#### 3️⃣ Arjun高级配置（4项）✅ 已修复
- [x] `arjunStableMode` - 稳定模式（随机延迟3-10秒）
- [x] `arjunThreads` - 并发线程数（1-20）
- [x] `arjunMaxRetries` - 最大重试次数（1-10）
- [x] `arjunRateLimit` - 速率限制（req/s）

#### 4️⃣ Arjun实时模式配置（2项）
- [x] `arjunRealtimeInterval` - 定时检查间隔（默认300秒）
- [x] `arjunRealtimeThreshold` - 参数阈值（默认15个）

#### 5️⃣ 参数收集配置（2项）
- [x] `collectionMode` - 收集模式（仅参数 / 参数+关键词）
- [x] `globalParameters` - 全局参数字典

#### 6️⃣ 被动扫描配置（4项）
- [x] `enablePassiveScan` - 被动扫描总开关（默认启用）
- [x] `globalInjectionMode` - 全局注入模式（SINGLE/BATCH）
- [x] `scanResultLogMode` - 结果记录模式（ALL/MATCHED_ONLY）
- [x] `scanConfigurations` - 扫描规则列表

#### 7️⃣ 规则文件配置（2项）
- [x] `useExternalRuleFile` - 是否使用外部规则文件
- [x] `ruleFilePath` - 外部规则文件路径

#### 8️⃣ 主动探测配置（6项）
- [x] `enableActiveScan` - 主动扫描开关
- [x] `bruteforceInterval` - 暴力破解间隔（默认300秒）
- [x] `minParameterCount` - 最小参数数量（默认15）
- [x] `maxConcurrentHosts` - 最大并发主机数（默认3）
- [x] `autoStart` - 自动启动
- [x] `verboseLogging` - 详细日志

#### 9️⃣ 线程池配置（4项）
- [x] `scannerCoreThreads` - 核心线程数（-1=自动，CPU×2）
- [x] `scannerMaxThreads` - 最大线程数（-1=自动，CPU×4）
- [x] `scannerQueueSize` - 任务队列大小（默认2000）
- [x] `scannerKeepAliveSeconds` - 空闲线程存活时间（默认120秒）

#### 🔟 代理池配置（4项）
- [x] `enableProxyPool` - 代理池开关
- [x] `proxyTimeout` - 代理超时（默认10秒）
- [x] `maxRetries` - 最大重试次数（默认3次）
- [x] `proxyList` - 代理列表

---

## 🔄 配置保存流程

### 保存时机
1. **自动保存**：规则增删改时（`PassiveScanConfigTab`中的`updateConfig`调用）
2. **手动保存**：点击"保存配置"按钮（`UnifiedConfigTab`）
3. **导入规则**：导入规则后自动保存

### 保存路径
```
主配置文件：~/.xprobe/config.json
规则导出文件：用户自定义（如 ~/Desktop/xprobe_rules_20251009.json）
```

### 保存流程
```
用户修改配置
    ↓
调用 xprobeConfigManager.updateConfig(config -> { ... })
    ↓
ConfigPersistence.save() - 原子写入
    ↓
写入临时文件 → 备份旧文件 → 原子重命名
    ↓
✅ 配置持久化成功
```

---

## 🧪 验证配置持久化的步骤

### 测试步骤
1. **修改配置**：在"全局配置"中修改任意配置项（如Arjun线程数、黑白名单等）
2. **保存配置**：点击"保存所有配置"按钮
3. **关闭Burp**：完全退出Burp Suite
4. **重新启动**：重新打开Burp Suite
5. **检查配置**：查看配置是否保持不变

### 预期结果
- ✅ 所有配置项（包括Arjun高级配置）都应该保持用户设置的值
- ✅ 扫描规则列表保持不变
- ✅ 黑白名单、代理池等配置保持不变

---

## 📁 配置文件示例

### config.json结构
```json
{
  "whitelist": ["example.com"],
  "blacklist": ["static.example.com"],
  "whitelistEnabled": true,
  "blacklistEnabled": false,
  "arjunEnabled": true,
  "arjunChunkSize": 250,
  "arjunTimeout": 15,
  "arjunStableMode": false,
  "arjunThreads": 5,
  "arjunMaxRetries": 5,
  "arjunRateLimit": 9999,
  "collectionMode": "PARAMETERS_ONLY",
  "enablePassiveScan": true,
  "globalInjectionMode": "BATCH",
  "scanResultLogMode": "MATCHED_ONLY",
  "scanConfigurations": [
    {
      "ruleId": "rule-001",
      "customLabel": "XSS检测",
      "enabled": true,
      ...
    }
  ],
  ...
}
```

---

## ⚠️ 注意事项

### 1. 规则保存的两种方式
- **方式一**：规则随主配置保存到`config.json`（**默认，自动**）
- **方式二**：通过"导出规则"保存到独立的JSON文件（**手动，用于迁移**）

### 2. 配置同步逻辑
- **规则增删改**：自动调用`updateConfig()`保存到主配置
- **导入规则**：覆盖主配置中的规则，并调用`updateConfig()`持久化
- **导出规则**：从主配置读取规则，保存到独立文件（不影响主配置）

### 3. 配置加载优先级
```
启动时加载顺序：
1. 尝试加载 ~/.xprobe/config.json
2. 如果失败，尝试从备份文件恢复
3. 如果都失败，使用默认配置并保存
```

---

## ✅ 总结

### 已修复的配置持久化问题
1. ✅ **copy()方法补全**：Arjun高级配置现在会正确复制
2. ✅ **规则自动保存**：规则增删改时自动持久化
3. ✅ **导入规则同步**：导入后确保ConfigurationManager和XProbeConfig同步

### 配置持久化保证
- 所有36个配置项都会正确保存到`~/.xprobe/config.json`
- 配置在Burp重启后完整恢复
- 原子写入机制防止配置损坏
- 备份机制提供故障恢复能力

### 使用建议
- **日常使用**：配置会自动保存，无需手动操作
- **跨环境迁移**：使用"导出规则"功能导出规则到独立文件
- **备份配置**：定期备份`~/.xprobe/config.json`


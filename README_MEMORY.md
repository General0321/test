# XProbe 项目记忆库 - 完整指南

**最后更新**: 2025-12-02T11:40:36.342Z  
**版本**: 2025-12-02.2

---

## 🎯 快速开始

### 1️⃣ 恢复项目上下文 (5 分钟)

```bash
# 打开记忆文件
cat .xprobe_assistant_memory_full.json | head -100

# 或使用快速参考卡片
cat QUICK_REFERENCE.md
```

### 2️⃣ 理解项目架构 (15 分钟)

```bash
# 阅读更新总结
cat MEMORY_UPDATE_SUMMARY.md

# 查看三大业务流程
grep -A 20 "call_chains" .xprobe_assistant_memory_full.json
```

### 3️⃣ 定位具体代码 (10 分钟)

```bash
# 查找某个类的方法
grep -A 10 '"UniversalScanner"' .xprobe_assistant_memory_full.json

# 查看已知问题
grep -A 5 "known_issues_and_fixes" .xprobe_assistant_memory_full.json
```

---

## 📚 文件说明

### 核心文件

| 文件 | 大小 | 说明 | 用途 |
|------|------|------|------|
| **.xprobe_assistant_memory_full.json** | 40KB | 完整项目记忆库 | AI 上下文恢复、架构查询、方法查找 |
| **QUICK_REFERENCE.md** | 11KB | 快速参考卡片 | 速查表、常用方法、配置参数 |
| **MEMORY_UPDATE_SUMMARY.md** | 7.3KB | 更新总结 | 了解新增内容、改进点、维护建议 |
| **COMPLETION_REPORT.txt** | 8.3KB | 完成报告 | 项目统计、质量保证、后续计划 |

### 源文档

| 文件 | 说明 |
|------|------|
| **代码调用关系文档.md** | 详细的函数调用关系（1993+ 个方法） |
| **ARCHITECTURE.md** | 架构设计与模块详解（Mermaid 图表） |

---

## 🔍 查询指南

### 查找某个类的方法

```json
.xprobe_assistant_memory_full.json
  → class_methods_summary
    → [layer_name]
      → [class_name]
        → public_methods / private_methods
```

**示例**: 查找 UniversalScanner 的 scan 方法
```bash
grep -A 30 '"UniversalScanner"' .xprobe_assistant_memory_full.json | grep -A 5 '"scan"'
```

### 理解业务流程

```json
.xprobe_assistant_memory_full.json
  → call_chains
    → passive_scan_flow / active_scan_flow / parameter_collection_flow
```

**示例**: 理解被动扫描的完整流程
```bash
grep -A 30 '"passive_scan_flow"' .xprobe_assistant_memory_full.json
```

### 查看缓存与去重

```json
.xprobe_assistant_memory_full.json
  → dedup_and_cache_summary
```

**示例**: 查看所有缓存类型
```bash
grep -A 20 '"dedup_and_cache_summary"' .xprobe_assistant_memory_full.json
```

### 查看已知问题

```json
.xprobe_assistant_memory_full.json
  → known_issues_and_fixes
```

**示例**: 查看所有已知问题
```bash
grep -A 50 '"known_issues_and_fixes"' .xprobe_assistant_memory_full.json
```

---

## 💡 常见查询

### Q1: 如何修复跨子域请求克隆问题？

**答**: 查看 `request_cloning_strategy_for_active_probe` 章节

```bash
grep -A 20 '"request_cloning_strategy_for_active_probe"' .xprobe_assistant_memory_full.json
```

**关键修复**:
- 使用 `request.withService(newService).withHeader('Host', newHost)` 而不是重建
- 保留所有原始 headers (Cookie, Authorization 等)
- 通过 `withBody()` 保留原始 body，不手写 Content-Length
- 添加 `X-XProbe-Arjun: true` header 避免自扫

### Q2: 被动扫描的完整流程是什么？

**答**: 查看 `call_chains` 中的 `passive_scan_flow`

```bash
grep -A 25 '"passive_scan_flow"' .xprobe_assistant_memory_full.json
```

**关键步骤**:
1. Burp Proxy 捕获响应
2. RequestHandler 缓存原始响应
3. RealtimeScanner 收集参数
4. TaskScheduler 调度扫描任务
5. UniversalScanner 执行规则配对
6. 结果记录到日志

### Q3: 支持哪些 Payload 变量？

**答**: 查看 `class_methods_summary` 中的 `PayloadVariableResolver`

```bash
grep -A 15 '"PayloadVariableResolver"' .xprobe_assistant_memory_full.json | grep -A 12 '"supported_variables"'
```

**支持的变量** (12 种):
- `{{ORIGINAL}}` - 原始值
- `{{RANDOM_STRING}}` - 随机字符串
- `{{COLLABORATOR}}` - Collaborator 域名
- `{{STAGE:id:name}}` - 从检测阶段获取
- 等等...

### Q4: 线程池如何配置？

**答**: 查看 `thread_pool_configuration` 章节

```bash
grep -A 15 '"thread_pool_configuration"' .xprobe_assistant_memory_full.json
```

**自动配置**:
- corePoolSize = CPU × 2
- maximumPoolSize = CPU × 4

**手动覆盖**:
```json
{
  "scannerCoreThreads": 4,
  "scannerMaxThreads": 8
}
```

### Q5: 有哪些已知问题需要修复？

**答**: 查看 `known_issues_and_fixes` 章节

```bash
grep -A 50 '"known_issues_and_fixes"' .xprobe_assistant_memory_full.json
```

**5 个关键问题**:
1. 跨子域请求克隆丢失会话上下文 (HIGH)
2. POST/JSON 请求体被置空 (HIGH)
3. Content-Length 手写导致截断 (MEDIUM)
4. IPv6 地址格式不一致 (MEDIUM)
5. 内容相似度算法过于轻量 (LOW)

---

## 🛠️ 维护指南

### 增量更新

当源码发生变化时，按以下步骤更新记忆文件：

1. **更新版本号**
   ```json
   "memory_version": "2025-12-09.1",
   "last_synced_utc": "2025-12-09T10:00:00.000Z"
   ```

2. **更新类方法清单**
   ```json
   "class_methods_summary": {
     "[layer]": {
       "[class]": {
         "public_methods": [
           "新增方法1(...) -> 说明",
           "新增方法2(...) -> 说明"
         ]
       }
     }
   }
   ```

3. **记录新问题**
   ```json
   "known_issues_and_fixes": [
     {
       "id": "issue-6",
       "title": "新问题标题",
       "severity": "HIGH|MEDIUM|LOW",
       "status": "PENDING_FIX|OPEN|FIXED",
       "fix": "修复方案"
     }
   ]
   ```

4. **更新不一致分析**
   ```json
   "doc_code_inconsistencies": {
     "naming_mismatch": [
       "新的命名不匹配..."
     ]
   }
   ```

### 定期同步

建议每周或每次重大变更后同步一次：

```bash
# 1. 构建项目
./gradlew build

# 2. 更新记忆文件
# 手动或通过脚本更新 .xprobe_assistant_memory_full.json

# 3. 验证 JSON 格式
python3 -m json.tool .xprobe_assistant_memory_full.json > /dev/null && echo "✓ JSON 格式正确"

# 4. 提交更改
git add .xprobe_assistant_memory_full.json
git commit -m "Update memory file to version 2025-12-09.1"
```

---

## 📊 项目统计

### 代码规模

| 指标 | 数值 |
|------|------|
| 核心类数 | 34 |
| 公共方法数 | 150+ |
| 私有方法数 | 80+ |
| 总方法数 | 230+ |
| 支持的 Payload 变量 | 12 |
| 支持的注入点类型 | 9 |
| 支持的响应元素类型 | 6 |
| 去重颗粒度选项 | 9 |

### 记忆库规模

| 指标 | 数值 |
|------|------|
| 文件大小 | 40KB |
| 总行数 | 876 |
| JSON 字段数 | 50+ |
| 调用链路条数 | 3 |
| 已知问题数 | 5 |

---

## 🔗 相关资源

### 内部文档

- `代码调用关系文档.md` - 详细的函数调用关系
- `ARCHITECTURE.md` - 架构设计与模块详解
- `MEMORY_UPDATE_SUMMARY.md` - 更新总结
- `QUICK_REFERENCE.md` - 快速参考卡片
- `COMPLETION_REPORT.txt` - 完成报告

### 外部资源

- [Burp Suite Montoya API](https://portswigger.net/burp/documentation/desktop/api)
- [Java Documentation](https://docs.oracle.com/en/java/)
- [Gradle Documentation](https://gradle.org/documentation/)

---

## ❓ 常见问题

### Q: 记忆文件多久需要更新一次？

A: 建议每周或每次重大代码变更后更新一次。可以在 CI/CD 流程中自动化这个过程。

### Q: 如何快速查找某个方法的实现？

A: 
1. 在 QUICK_REFERENCE.md 中查找类名
2. 在 .xprobe_assistant_memory_full.json 中查找 class_methods_summary
3. 使用 grep 快速定位: `grep -n "methodName" .xprobe_assistant_memory_full.json`

### Q: 如何理解某个业务流程？

A: 
1. 查看 call_chains 中的对应流程
2. 按照调用链路逐步查看每个方法的实现
3. 参考 QUICK_REFERENCE.md 中的流程图

### Q: 如何贡献改进？

A: 
1. 发现问题或改进点
2. 在 known_issues_and_fixes 中记录
3. 在 doc_code_inconsistencies 中记录
4. 提交 PR 或 Issue

---

## 📞 联系方式

如有问题或建议，请：

1. 查看 QUICK_REFERENCE.md 快速参考卡片
2. 查看 MEMORY_UPDATE_SUMMARY.md 了解详细信息
3. 查看 COMPLETION_REPORT.txt 了解项目统计
4. 查看 .xprobe_assistant_memory_full.json 获取完整信息

---

## ✅ 检查清单

在使用记忆文件前，请确认：

- [x] 记忆文件已扩展到 40KB
- [x] 所有 34 个核心类都有方法清单
- [x] 三条主要业务流程都有完整调用链
- [x] 所有缓存和去重机制都有说明
- [x] 所有已知问题都有修复建议
- [x] 文档-源码不一致都有分析
- [x] JSON 格式验证通过
- [x] 内容完整性检查通过
- [x] 一致性检查通过

---

**版本**: 2025-12-02.2  
**最后更新**: 2025-12-02T11:40:36.342Z  
**下次建议更新**: 2025-12-09 (或有重大代码变更时)

---

**祝您使用愉快！** 🚀


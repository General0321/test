# XProbe 记忆文件完善总结

**更新时间**: 2025-12-02T11:40:36.342Z  
**版本**: 2025-12-02.2  
**文件大小**: 40KB (876 行)

---

## 📋 更新内容概览

基于 `代码调用关系文档.md` 和 `ARCHITECTURE.md` 两份详细文档，对 `.xprobe_assistant_memory_full.json` 进行了大幅扩展和完善。

### 新增主要章节

#### 1. **class_methods_summary** (类方法清单)
包含所有核心模块的公共方法、私有方法、调用流程：

- **entry_layer**: XProbe 主入口初始化链
- **request_processing_layer**: RequestHandler、RequestFilter、GlobalFilter、OriginalResponseCache
- **realtime_scanning_layer**: RealtimeScannerRefactored、ParameterCollector、ParameterManager
- **task_scheduling_layer**: TaskScheduler、ScannerFactory
- **scanning_engine_layer**: UniversalScanner、PayloadVariableResolver、CrossPairVariableExtractor、ResponseComparisonEngine、InjectionPointExecutor、UnifiedHttpEvaluator、UnifiedResponseEvaluator、DeduplicationKeyGenerator、PairResponseFeatures
- **parameter_discovery_layer**: ArjunService、ParamDiscoveryEngine、BurpHttpRequester、AnomalyDetector、ResponseBaseline、ChunkProcessor、ParamVerifier、ConcurrentProcessor、ErrorHandler、RetryStrategy、RateLimiter
- **config_management_layer**: XProbeConfigManager、ConfigurationManager、ConfigPersistence、RulePersistence

**覆盖范围**:
- 每个类的公共方法签名与功能说明
- 关键私有方法与内部流程
- 方法间的调用关系
- 核心算法与数据结构

#### 2. **call_chains** (调用链路)
三条主要业务流程的完整调用链：

- **passive_scan_flow**: 被动扫描从 Burp Proxy 到日志记录的完整链路
- **active_scan_flow**: 主动探测从 UI 触发到漏洞扫描的完整链路
- **parameter_collection_flow**: 参数收集从请求拦截到自动触发 Arjun 的完整链路

#### 3. **request_cloning_strategy_for_active_probe** (请求克隆策略)
针对跨子域请求的重要修复建议：

- **当前问题**: 会话上下文丢失、Content-Length 手写、IPv6 格式不一致、POST/JSON 请求体置空
- **推荐修复**: 使用 Burp API 的 `withService()` 和 `withHeader()` 而不是重建请求
- **关键点**:
  - 保留所有原始 headers (Cookie, Authorization, X-CSRF-Token 等)
  - 通过 `withBody()` 保留原始 body，不手写 Content-Length
  - IPv6 地址统一使用 `[addr]:port` 格式
  - 添加 `X-XProbe-Arjun: true` header 避免自扫

#### 4. **dedup_and_cache_summary** (去重与缓存汇总)
三层缓存的统一说明：

| 缓存名称 | 类型 | 容量 | Key | 用途 |
|---------|------|------|-----|------|
| OriginalResponseCache | LRU | 2000 | method\|url | 存储原始响应 |
| PassiveScanProcessedKeys | FIFO | 100000 | 去重键 | 被动扫描去重 |
| RandomPathBaselineCache | TTL | - | method+host+contentType+endpoint | 随机路径基线 |

去重键生成的 9 种颗粒度完整列表

#### 5. **ui_event_flow** (UI 事件流)
三个主要 UI 标签页的监听器与回调：

- **ActiveProbeTab**: ArjunResultListener 回调字段定义
- **DashboardTab**: ScanTaskListener 进度更新
- **ScanResultTab**: LogListener 结果添加

#### 6. **thread_pool_configuration** (线程池配置)
自动/手动线程池参数的完整说明：

- 自动计算规则: CPU × 2 (core), CPU × 4 (max)
- 手动覆盖: scannerCoreThreads, scannerMaxThreads
- 队列与拒绝策略: CallerRunsPolicy

#### 7. **response_similarity_algorithm** (相似度算法)
当前实现与未来替换方案：

- **当前**: 轻量级位置字符比较 (commonCharacters / max(len1, len2))
- **未来可选**: Jaccard、TF-IDF、Levenshtein 距离

#### 8. **doc_code_inconsistencies** (文档-源码不一致)
详细列举两份文档与源码的差异：

- **命名不匹配**: getDisplayLabel vs getLabel, PairEvaluator 概念映射
- **行为缺口**: 被动扫描结果记录模式、Arjun 触发优化逻辑、去重键影响
- **算法细节**: 相似度算法、异常检测阈值
- **UI 事件**: 回调字段定义缺失
- **线程池**: 兜底规则未详细说明

#### 9. **nomenclature_map** (术语映射)
概念组件与实际类的对应关系

#### 10. **known_issues_and_fixes** (已知问题与修复)
5 个关键问题的严重级别、状态与修复方案

---

## 📊 数据统计

| 指标 | 数值 |
|------|------|
| 总行数 | 876 |
| 文件大小 | 40KB |
| 类方法清单条目 | 50+ |
| 调用链路条目 | 3 |
| 缓存类型 | 3 |
| 去重颗粒度 | 9 |
| 已知问题 | 5 |
| 支持的 Payload 变量 | 12 |
| 支持的注入点类型 | 9 |
| 支持的响应元素类型 | 6 |

---

## 🔍 核心改进点

### 1. 完整的方法签名清单
- 每个核心类的所有公共方法都有详细说明
- 包含参数类型、返回值、功能描述
- 关键私有方法也有记录

### 2. 清晰的调用链路
- 三条主要业务流程的完整链路
- 每个步骤都有明确的方法调用
- 便于快速定位代码位置

### 3. 实现细节补充
- Payload 变量解析支持的 12 种格式
- 注入点执行器支持的 9 种注入类型
- 响应评估器支持的 6 种响应元素类型
- 去重颗粒度的 9 种选项

### 4. 问题与修复建议
- 跨子域请求克隆的具体修复方案
- 5 个关键问题的严重级别与状态
- 每个问题都有明确的修复建议

### 5. 文档-源码一致性检查
- 详细列举了两份文档与源码的不一致之处
- 提出了文档更新建议
- 建立了术语映射表

---

## 🎯 使用指南

### 快速查找

1. **查找某个类的方法**:
   ```json
   class_methods_summary -> [layer] -> [class] -> public_methods
   ```

2. **理解完整业务流程**:
   ```json
   call_chains -> [flow_name]
   ```

3. **了解缓存机制**:
   ```json
   dedup_and_cache_summary
   ```

4. **查看已知问题**:
   ```json
   known_issues_and_fixes
   ```

### 恢复上下文

当需要快速恢复项目上下文时：

1. 读取 `memory_version` 和 `last_updated_from` 确认版本
2. 查看 `architecture` 了解整体架构
3. 查看 `class_methods_summary` 了解具体实现
4. 查看 `call_chains` 理解业务流程
5. 查看 `known_issues_and_fixes` 了解当前问题

---

## 📝 维护建议

### 增量更新

当源码发生变化时，建议：

1. 更新 `memory_version` (格式: YYYY-MM-DD.N)
2. 更新 `last_synced_utc`
3. 在对应的 `class_methods_summary` 中增量更新
4. 在 `known_issues_and_fixes` 中记录新问题
5. 在 `doc_code_inconsistencies` 中记录新的不一致

### 定期同步

建议每周或每次重大变更后同步一次：

```bash
# 生成新的方法清单
./gradlew build

# 更新记忆文件
# 手动或通过脚本更新 .xprobe_assistant_memory_full.json
```

---

## 🔗 相关文档

- `代码调用关系文档.md` - 详细的函数调用关系
- `ARCHITECTURE.md` - 架构设计与模块详解
- `.xprobe_assistant_memory_full.json` - 本记忆文件

---

## ✅ 完善清单

- [x] 添加完整的类方法清单
- [x] 添加三条主要调用链路
- [x] 添加请求克隆策略说明
- [x] 添加缓存与去重汇总
- [x] 添加 UI 事件流说明
- [x] 添加线程池配置说明
- [x] 添加相似度算法说明
- [x] 添加文档-源码不一致分析
- [x] 添加已知问题与修复建议
- [x] 添加术语映射表

---

**最后更新**: 2025-12-02T11:40:36.342Z  
**下次建议更新**: 2025-12-09 (或有重大代码变更时)


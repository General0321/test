# XProbe 插件全面测试计划

> **测试版本**: XProbe v1.0.0  
> **制定日期**: 2025-10-01  
> **测试目标**: 确保所有模块功能正常、性能稳定、数据准确

---

## 📋 目录

1. [测试策略概述](#测试策略概述)
2. [模块测试清单](#模块测试清单)
3. [功能测试详细计划](#功能测试详细计划)
4. [性能与压力测试](#性能与压力测试)
5. [安全性测试](#安全性测试)
6. [已识别问题清单](#已识别问题清单)
7. [测试数据准备](#测试数据准备)
8. [测试环境要求](#测试环境要求)

---

## 测试策略概述

### 测试层次
- **单元测试**: 各个类和方法的独立测试
- **集成测试**: 模块间交互测试
- **系统测试**: 端到端功能测试
- **压力测试**: 大规模流量和并发测试
- **兼容性测试**: 不同Burp版本和操作系统

### 测试原则
1. ✅ **完整性**: 覆盖所有模块和功能点
2. ✅ **真实性**: 使用真实应用场景和数据
3. ✅ **可重复性**: 测试步骤明确，可重现
4. ✅ **自动化优先**: 尽可能自动化测试流程

---

## 模块测试清单

### 1. 核心模块 (Core Modules)

#### 1.1 XProbe 主入口
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-CORE-001 | 插件初始化成功，所有组件创建 | P0 | ⏳ |
| TC-CORE-002 | HTTP处理器注册成功 | P0 | ⏳ |
| TC-CORE-003 | UI标签页正确创建并显示 | P0 | ⏳ |
| TC-CORE-004 | 插件卸载时资源正确释放 | P1 | ⏳ |
| TC-CORE-005 | 多次加载卸载无内存泄漏 | P1 | ⏳ |

#### 1.2 RequestHandler (请求处理器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-REQ-001 | GET请求正确处理 | P0 | ⏳ |
| TC-REQ-002 | POST请求正确处理 | P0 | ⏳ |
| TC-REQ-003 | JSON请求正确解析 | P0 | ⏳ |
| TC-REQ-004 | XML请求正确解析 | P1 | ⏳ |
| TC-REQ-005 | URL参数正确提取 | P0 | ⏳ |
| TC-REQ-006 | Body参数正确提取 | P0 | ⏳ |
| TC-REQ-007 | 请求过滤器正确应用 | P0 | ⏳ |
| TC-REQ-008 | 参数名匹配（字符串）正确 | P0 | ⏳ |
| TC-REQ-009 | 参数名匹配（正则）正确 | P0 | ⏳ |
| TC-REQ-010 | 无效正则表达式正确处理 | P1 | ⏳ |
| TC-REQ-011 | 并发请求处理无冲突 | P0 | ⏳ |
| TC-REQ-012 | 大请求体处理不崩溃 | P1 | ⏳ |
| TC-REQ-013 | 去重机制正确工作 | P0 | ⏳ |
| TC-REQ-014 | Content-Type缺失时默认处理 | P1 | ⏳ |

#### 1.3 GlobalFilter (全局过滤器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-FILTER-001 | 白名单启用时只处理匹配URL | P0 | ⏳ |
| TC-FILTER-002 | 黑名单启用时阻止匹配URL | P0 | ⏳ |
| TC-FILTER-003 | 白名单+黑名单组合逻辑正确 | P0 | ⏳ |
| TC-FILTER-004 | 字符串匹配正确 | P0 | ⏳ |
| TC-FILTER-005 | 正则匹配正确 | P0 | ⏳ |
| TC-FILTER-006 | 无效正则不导致崩溃 | P1 | ⏳ |
| TC-FILTER-007 | 空白名单不阻止任何请求 | P1 | ⏳ |
| TC-FILTER-008 | 动态更新名单立即生效 | P0 | ⏳ |
| TC-FILTER-009 | 大量规则性能可接受 | P1 | ⏳ |
| TC-FILTER-010 | 被动和主动过滤独立工作 | P0 | ⏳ |

#### 1.4 TaskScheduler (任务调度器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-SCHED-001 | 任务正确加入队列 | P0 | ⏳ |
| TC-SCHED-002 | 并发任务正确执行 | P0 | ⏳ |
| TC-SCHED-003 | 任务失败不影响其他任务 | P0 | ⏳ |
| TC-SCHED-004 | 线程池正确管理 | P1 | ⏳ |
| TC-SCHED-005 | 大量任务不导致OOM | P1 | ⏳ |
| TC-SCHED-006 | 扫描结果正确记录到LogModel | P0 | ⏳ |
| TC-SCHED-007 | shutdown()正确关闭线程池 | P1 | ⏳ |
| TC-SCHED-008 | 异步任务超时处理 | P1 | ⏳ |

---

### 2. 被动扫描模块 (Passive Scanning)

#### 2.1 SQLScanner (SQL注入扫描器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-SQL-001 | 基础SQL注入检测（单引号） | P0 | ⏳ |
| TC-SQL-002 | 时间盲注检测 | P0 | ⏳ |
| TC-SQL-003 | 布尔盲注检测 | P0 | ⏳ |
| TC-SQL-004 | {value}占位符正确替换 | P0 | ⏳ |
| TC-SQL-005 | URL编码正确处理 | P0 | ⏳ |
| TC-SQL-006 | JSON参数注入 | P0 | ⏳ |
| TC-SQL-007 | 多payload测试不重复 | P0 | ⏳ |
| TC-SQL-008 | 响应匹配规则（关键词）正确 | P0 | ⏳ |
| TC-SQL-009 | 响应匹配规则（正则）正确 | P0 | ⏳ |
| TC-SQL-010 | 响应匹配规则（状态码）正确 | P0 | ⏳ |
| TC-SQL-011 | 响应匹配规则（响应时间）正确 | P0 | ⏳ |
| TC-SQL-012 | 误报率评估 | P1 | ⏳ |
| TC-SQL-013 | 漏报率评估 | P1 | ⏳ |
| TC-SQL-014 | 自定义payload生效 | P0 | ⏳ |

#### 2.2 LFIScanner (本地文件包含扫描器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-LFI-001 | 基础路径遍历检测 | P0 | ⏳ |
| TC-LFI-002 | ../../../etc/passwd检测 | P0 | ⏳ |
| TC-LFI-003 | Windows路径检测 | P1 | ⏳ |
| TC-LFI-004 | 编码绕过检测 | P1 | ⏳ |
| TC-LFI-005 | 响应匹配正确（root:x:） | P0 | ⏳ |
| TC-LFI-006 | NULL字节绕过检测 | P1 | ⏳ |
| TC-LFI-007 | 误报率评估 | P1 | ⏳ |
| TC-LFI-008 | 自定义payload生效 | P0 | ⏳ |

#### 2.3 SSRFScanner (SSRF扫描器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-SSRF-001 | 基础内网IP检测 | P0 | ⏳ |
| TC-SSRF-002 | 127.0.0.1回环检测 | P0 | ⏳ |
| TC-SSRF-003 | DNS日志检测（Collaborator） | P0 | ⏳ |
| TC-SSRF-004 | {dnslog}占位符替换 | P0 | ⏳ |
| TC-SSRF-005 | DNS交互等待超时处理 | P1 | ⏳ |
| TC-SSRF-006 | Collaborator不可用时降级 | P1 | ⏳ |
| TC-SSRF-007 | 云元数据接口检测 | P1 | ⏳ |
| TC-SSRF-008 | 响应匹配正确 | P0 | ⏳ |
| TC-SSRF-009 | 误报率评估 | P1 | ⏳ |

#### 2.4 AbstractScanner (扫描器基类)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-ABS-001 | buildRequest正确构建请求 | P0 | ⏳ |
| TC-ABS-002 | JSON参数更新正确 | P0 | ⏳ |
| TC-ABS-003 | 响应匹配规则引擎正确 | P0 | ⏳ |
| TC-ABS-004 | 多规则AND逻辑正确 | P0 | ⏳ |
| TC-ABS-005 | 多规则OR逻辑正确 | P0 | ⏳ |
| TC-ABS-006 | 状态码范围匹配正确 | P1 | ⏳ |
| TC-ABS-007 | 正则匹配性能可接受 | P1 | ⏳ |
| TC-ABS-008 | 去重机制防止重复扫描 | P0 | ⏳ |

---

### 3. 主动探测模块 (Active Probing)

#### 3.1 ParameterCollector (参数收集器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-PARAM-001 | GET参数正确收集 | P0 | ⏳ |
| TC-PARAM-002 | POST参数正确收集 | P0 | ⏳ |
| TC-PARAM-003 | JSON字段正确收集 | P0 | ⏳ |
| TC-PARAM-004 | 参数名清理逻辑正确 | P0 | ⏳ |
| TC-PARAM-005 | 参数名正则验证正确 | P0 | ⏳ |
| TC-PARAM-006 | 去重机制正确工作 | P0 | ⏳ |
| TC-PARAM-007 | 主域名提取正确 | P0 | ⏳ |
| TC-PARAM-008 | 按主域名分组正确 | P0 | ⏳ |
| TC-PARAM-009 | 按host细分正确 | P1 | ⏳ |
| TC-PARAM-010 | 按endpoint细分正确 | P0 | ⏳ |
| TC-PARAM-011 | EndpointKey正确（含method+contentType） | P0 | ⏳ |
| TC-PARAM-012 | 请求模板正确保存 | P0 | ⏳ |
| TC-PARAM-013 | 仅参数模式正常工作 | P0 | ⏳ |
| TC-PARAM-014 | 参数+关键词模式正常工作 | P0 | ⏳ |
| TC-PARAM-015 | 关键词提取正确（GAP.py兼容） | P1 | ⏳ |
| TC-PARAM-016 | 关键词停用词过滤正确 | P1 | ⏳ |
| TC-PARAM-017 | 关键词长度限制正确 | P1 | ⏳ |
| TC-PARAM-018 | 纯数字关键词被过滤 | P1 | ⏳ |
| TC-PARAM-019 | 统计信息正确 | P1 | ⏳ |
| TC-PARAM-020 | 清空功能正确 | P1 | ⏳ |
| TC-PARAM-021 | 并发收集无数据丢失 | P0 | ⏳ |
| TC-PARAM-022 | 大量参数不导致性能问题 | P1 | ⏳ |

#### 3.2 ParameterManager (参数管理器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-PMGR-001 | 全局参数添加正确 | P0 | ⏳ |
| TC-PMGR-002 | 全局参数批量添加正确 | P0 | ⏳ |
| TC-PMGR-003 | 全局参数获取正确 | P0 | ⏳ |
| TC-PMGR-004 | 默认参数初始化正确 | P1 | ⏳ |
| TC-PMGR-005 | 增量参数计算正确 | P0 | ⏳ |
| TC-PMGR-006 | 首次扫描返回所有参数 | P0 | ⏳ |
| TC-PMGR-007 | 再次扫描只返回新参数 | P0 | ⏳ |
| TC-PMGR-008 | 已扫描标记正确 | P0 | ⏳ |
| TC-PMGR-009 | 参数去重Key正确（method+host+ct+ep） | P0 | ⏳ |
| TC-PMGR-010 | Content-Type标准化正确 | P0 | ⏳ |
| TC-PMGR-011 | 参数名验证正确 | P1 | ⏳ |
| TC-PMGR-012 | 导入参数从文件正确 | P1 | ⏳ |
| TC-PMGR-013 | 导出参数到文件正确 | P1 | ⏳ |
| TC-PMGR-014 | 清空功能正确 | P1 | ⏳ |
| TC-PMGR-015 | 统计信息正确 | P1 | ⏳ |
| TC-PMGR-016 | 并发标记无冲突 | P0 | ⏳ |

#### 3.3 ArjunIntegration (Arjun集成)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-ARJUN-001 | Arjun路径检测正确 | P0 | ⏳ |
| TC-ARJUN-002 | 临时字典文件创建正确 | P0 | ⏳ |
| TC-ARJUN-003 | 命令行构建正确 | P0 | ⏳ |
| TC-ARJUN-004 | --include参数保留原始参数 | P0 | ⏳ |
| TC-ARJUN-005 | -w参数指定测试字典 | P0 | ⏳ |
| TC-ARJUN-006 | -oB参数发送到Burp代理 | P0 | ⏳ |
| TC-ARJUN-007 | Headers正确传递 | P0 | ⏳ |
| TC-ARJUN-008 | X-XProbe-Arjun标记添加 | P0 | ⏳ |
| TC-ARJUN-009 | HTTP方法映射正确（GET/POST/JSON/XML） | P0 | ⏳ |
| TC-ARJUN-010 | JSON参数提取正确 | P1 | ⏳ |
| TC-ARJUN-011 | 进程输出正确捕获 | P1 | ⏳ |
| TC-ARJUN-012 | 进程错误处理 | P1 | ⏳ |
| TC-ARJUN-013 | 超时机制工作 | P1 | ⏳ |
| TC-ARJUN-014 | 临时文件清理 | P1 | ⏳ |
| TC-ARJUN-015 | 异步扫描正确返回结果 | P0 | ⏳ |
| TC-ARJUN-016 | 批量扫描正确 | P1 | ⏳ |
| TC-ARJUN-017 | Arjun不存在时优雅降级 | P1 | ⏳ |
| TC-ARJUN-018 | 自定义字典合并正确 | P0 | ⏳ |

#### 3.4 RealtimeScannerRefactored (实时扫描器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-RT-001 | 被动收集正常工作 | P0 | ⏳ |
| TC-RT-002 | Arjun流量正确跳过（X-XProbe-Arjun） | P0 | ⏳ |
| TC-RT-003 | 全局过滤器正确应用 | P0 | ⏳ |
| TC-RT-004 | SiteMap手动触发正确 | P0 | ⏳ |
| TC-RT-005 | Proxy实时触发正确 | P0 | ⏳ |
| TC-RT-006 | 手动端点扫描正确 | P0 | ⏳ |
| TC-RT-007 | 增量扫描逻辑正确 | P0 | ⏳ |
| TC-RT-008 | 按主域名分组正确 | P0 | ⏳ |
| TC-RT-009 | 无新参数时跳过扫描 | P0 | ⏳ |
| TC-RT-010 | 手动端点method+contentType组合正确 | P1 | ⏳ |
| TC-RT-011 | 被动扫描去重正确 | P0 | ⏳ |
| TC-RT-012 | 收集模式切换生效 | P0 | ⏳ |
| TC-RT-013 | 全局参数添加正确 | P1 | ⏳ |
| TC-RT-014 | 参数导入导出正确 | P1 | ⏳ |
| TC-RT-015 | 域统计信息正确 | P1 | ⏳ |
| TC-RT-016 | 启动停止正确 | P1 | ⏳ |
| TC-RT-017 | Arjun失败也标记（避免无限重试） | P0 | ⏳ |

#### 3.5 ActiveScanner (主动扫描器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-ACT-001 | SiteMap收集正确 | P0 | ⏳ |
| TC-ACT-002 | 按host过滤正确 | P0 | ⏳ |
| TC-ACT-003 | 外部工具配置正确保存 | P1 | ⏳ |
| TC-ACT-004 | 异步扫描正确 | P1 | ⏳ |
| TC-ACT-005 | URL验证正确 | P1 | ⏳ |

---

### 4. 配置管理模块 (Configuration Management)

#### 4.1 ConfigurationManager (配置管理器)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-CFG-001 | 配置添加正确 | P0 | ⏳ |
| TC-CFG-002 | 配置删除正确 | P0 | ⏳ |
| TC-CFG-003 | 配置更新正确 | P0 | ⏳ |
| TC-CFG-004 | 获取所有配置正确 | P0 | ⏳ |
| TC-CFG-005 | 获取已启用配置正确 | P0 | ⏳ |
| TC-CFG-006 | 按名称查找配置正确 | P1 | ⏳ |
| TC-CFG-007 | 保存到磁盘正确 | P0 | ⏳ |
| TC-CFG-008 | 从磁盘加载正确 | P0 | ⏳ |
| TC-CFG-009 | 序列化反序列化正确 | P0 | ⏳ |
| TC-CFG-010 | 文件损坏时处理 | P1 | ⏳ |

#### 4.2 Configuration (配置类)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-CONF-001 | 参数名列表正确 | P0 | ⏳ |
| TC-CONF-002 | 参数值列表正确 | P0 | ⏳ |
| TC-CONF-003 | 匹配规则列表正确 | P0 | ⏳ |
| TC-CONF-004 | 自定义标签正确 | P0 | ⏳ |
| TC-CONF-005 | 启用状态正确 | P0 | ⏳ |
| TC-CONF-006 | MatchRule创建正确 | P0 | ⏳ |
| TC-CONF-007 | MatchRule序列化正确 | P1 | ⏳ |

#### 4.3 ExternalToolConfig (外部工具配置)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-EXT-001 | Arjun路径设置正确 | P0 | ⏳ |
| TC-EXT-002 | Burp代理地址设置正确 | P0 | ⏳ |
| TC-EXT-003 | 线程数设置正确 | P1 | ⏳ |
| TC-EXT-004 | 超时设置正确 | P1 | ⏳ |
| TC-EXT-005 | 自定义字典设置正确 | P1 | ⏳ |
| TC-EXT-006 | 输出选项设置正确 | P1 | ⏳ |

---

### 5. UI模块 (User Interface)

#### 5.1 DashboardTab (仪表盘)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-UI-DASH-001 | 界面正确加载 | P0 | ⏳ |
| TC-UI-DASH-002 | 统计卡片数据正确 | P0 | ⏳ |
| TC-UI-DASH-003 | 实时刷新正常工作 | P0 | ⏳ |
| TC-UI-DASH-004 | 手动刷新正常工作 | P1 | ⏳ |
| TC-UI-DASH-005 | 活动日志正常显示 | P1 | ⏳ |
| TC-UI-DASH-006 | 最近发现表格正确 | P1 | ⏳ |
| TC-UI-DASH-007 | 参数收集统计正确 | P0 | ⏳ |
| TC-UI-DASH-008 | 配色方案美观 | P2 | ⏳ |
| TC-UI-DASH-009 | 定时器正确释放 | P1 | ⏳ |

#### 5.2 ActiveProbeTab (主动探测标签)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-UI-PROBE-001 | 界面正确加载 | P0 | ⏳ |
| TC-UI-PROBE-002 | 参数收集模式切换正确 | P0 | ⏳ |
| TC-UI-PROBE-003 | 主域名列表正确显示 | P0 | ⏳ |
| TC-UI-PROBE-004 | 参数列表正确显示 | P0 | ⏳ |
| TC-UI-PROBE-005 | 关键词列表正确显示 | P1 | ⏳ |
| TC-UI-PROBE-006 | 手动Arjun触发正确 | P0 | ⏳ |
| TC-UI-PROBE-007 | Proxy实时触发正确 | P0 | ⏳ |
| TC-UI-PROBE-008 | 手动添加端点正确 | P0 | ⏳ |
| TC-UI-PROBE-009 | 全局参数添加正确 | P1 | ⏳ |
| TC-UI-PROBE-010 | 参数导入导出正确 | P1 | ⏳ |
| TC-UI-PROBE-011 | 清空功能正确 | P1 | ⏳ |
| TC-UI-PROBE-012 | 统计信息正确 | P1 | ⏳ |

#### 5.3 UnifiedConfigTab (配置中心)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-UI-CFG-001 | 界面正确加载 | P0 | ⏳ |
| TC-UI-CFG-002 | 黑白名单添加正确 | P0 | ⏳ |
| TC-UI-CFG-003 | 黑白名单删除正确 | P0 | ⏳ |
| TC-UI-CFG-004 | 黑白名单启用切换正确 | P0 | ⏳ |
| TC-UI-CFG-005 | Arjun配置保存正确 | P0 | ⏳ |
| TC-UI-CFG-006 | 收集模式切换正确 | P0 | ⏳ |
| TC-UI-CFG-007 | 全局参数管理正确 | P1 | ⏳ |
| TC-UI-CFG-008 | 保存所有配置正确 | P0 | ⏳ |
| TC-UI-CFG-009 | 状态消息正确显示 | P1 | ⏳ |
| TC-UI-CFG-010 | 表单验证正确 | P1 | ⏳ |

#### 5.4 PassiveScanConfigTab (被动扫描配置)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-UI-PSCAN-001 | 界面正确加载 | P0 | ⏳ |
| TC-UI-PSCAN-002 | 规则列表正确显示 | P0 | ⏳ |
| TC-UI-PSCAN-003 | 添加规则正确 | P0 | ⏳ |
| TC-UI-PSCAN-004 | 编辑规则正确 | P0 | ⏳ |
| TC-UI-PSCAN-005 | 删除规则正确 | P0 | ⏳ |
| TC-UI-PSCAN-006 | 启用/禁用规则正确 | P0 | ⏳ |
| TC-UI-PSCAN-007 | 导入配置正确 | P1 | ⏳ |
| TC-UI-PSCAN-008 | 导出配置正确 | P1 | ⏳ |
| TC-UI-PSCAN-009 | 参数名匹配类型切换正确 | P0 | ⏳ |
| TC-UI-PSCAN-010 | Payload多行编辑正确 | P1 | ⏳ |

#### 5.5 ScanResultTab (扫描结果标签)
| 测试项 | 测试内容 | 优先级 | 状态 |
|--------|---------|--------|------|
| TC-UI-RESULT-001 | 界面正确加载 | P0 | ⏳ |
| TC-UI-RESULT-002 | 结果表格正确显示 | P0 | ⏳ |
| TC-UI-RESULT-003 | 请求详情正确显示 | P0 | ⏳ |
| TC-UI-RESULT-004 | 响应详情正确显示 | P0 | ⏳ |
| TC-UI-RESULT-005 | 过滤功能正确 | P1 | ⏳ |
| TC-UI-RESULT-006 | 排序功能正确 | P1 | ⏳ |
| TC-UI-RESULT-007 | 导出结果正确 | P1 | ⏳ |
| TC-UI-RESULT-008 | 清空结果正确 | P1 | ⏳ |
| TC-UI-RESULT-009 | 实时更新正确 | P0 | ⏳ |

---

## 功能测试详细计划

### 场景1: 基础被动扫描流程

#### 测试场景描述
用户在浏览器中访问一个测试网站，XProbe应该自动捕获流量并执行被动扫描。

#### 测试步骤
1. **前置条件**:
   - Burp Suite正常启动
   - XProbe插件已加载
   - 配置至少一条SQL注入规则（参数名：id，payload：' OR 1=1--）
   - 未启用黑白名单

2. **执行步骤**:
   ```
   Step 1: 在Burp代理中访问 http://testsite.com/products?id=123
   Step 2: 观察XProbe仪表盘统计数据
   Step 3: 检查扫描结果标签页
   Step 4: 验证Burp日志中的扫描活动
   ```

3. **预期结果**:
   - ✅ 仪表盘显示"Total Requests: 1"
   - ✅ 仪表盘显示"Scanned Requests: 1"
   - ✅ 如果存在SQL注入，扫描结果中出现记录
   - ✅ 扫描结果包含：原始请求、修改请求、响应、Payload
   - ✅ Burp日志中有"Scanning parameter 'id'"消息

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景2: 黑白名单过滤

#### 测试场景描述
验证黑白名单能正确过滤请求。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载
   - 配置至少一条扫描规则

2. **执行步骤 - 白名单测试**:
   ```
   Step 1: 在配置中心添加白名单：testsite.com
   Step 2: 启用白名单
   Step 3: 访问 http://testsite.com/page1 (应扫描)
   Step 4: 访问 http://example.com/page1 (应跳过)
   Step 5: 检查仪表盘统计
   ```

3. **执行步骤 - 黑名单测试**:
   ```
   Step 1: 禁用白名单，添加黑名单：/admin/
   Step 2: 启用黑名单
   Step 3: 访问 http://testsite.com/user/list (应扫描)
   Step 4: 访问 http://testsite.com/admin/users (应跳过)
   Step 5: 检查仪表盘统计
   ```

4. **预期结果**:
   - ✅ 白名单：只有testsite.com的请求被扫描
   - ✅ 黑名单：包含/admin/的请求被跳过
   - ✅ Burp日志中有过滤消息

5. **实际结果**: ___________________

6. **状态**: ⏳ 待测试

---

### 场景3: JSON请求扫描

#### 测试场景描述
验证JSON格式的请求能正确解析和扫描。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载
   - 配置SQL注入规则（参数名：userId）

2. **执行步骤**:
   ```
   Step 1: 使用Burp Repeater发送JSON请求:
   POST /api/user HTTP/1.1
   Host: testsite.com
   Content-Type: application/json
   
   {"userId": 123, "action": "view"}
   
   Step 2: 观察扫描活动
   Step 3: 检查修改后的请求
   ```

3. **预期结果**:
   - ✅ JSON字段"userId"被识别为参数
   - ✅ Payload正确注入：{"userId": "123' OR 1=1--", "action": "view"}
   - ✅ 扫描结果中有记录

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景4: 参数收集功能

#### 测试场景描述
验证主动探测模块能正确收集参数。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载
   - 主动探测标签页打开
   - 选择"仅参数名"模式

2. **执行步骤**:
   ```
   Step 1: 访问以下URL：
      - http://testsite.com/user?id=1&name=test&email=test@example.com
      - http://testsite.com/product?pid=100&category=books
      - http://api.testsite.com/search?q=test&limit=10
   
   Step 2: 在主动探测标签页点击刷新
   Step 3: 选择主域名"testsite.com"
   Step 4: 查看参数列表
   Step 5: 查看接口列表
   ```

3. **预期结果**:
   - ✅ 主域名列表显示"testsite.com"
   - ✅ 参数列表包含：id, name, email, pid, category, q, limit
   - ✅ 接口列表包含：/user, /product, /search
   - ✅ 统计信息正确：参数数=7, 接口数=3

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景5: Arjun集成测试

#### 测试场景描述
验证Arjun参数探测正确工作。

#### 测试步骤
1. **前置条件**:
   - Arjun已安装（pip install arjun）
   - XProbe插件已加载
   - 配置Arjun路径
   - Burp代理监听在127.0.0.1:8080

2. **执行步骤**:
   ```
   Step 1: 访问 http://testsite.com/api/user?id=1
   Step 2: 在主动探测标签页，选择"仅参数名"模式
   Step 3: 等待参数收集（应收集到"id"）
   Step 4: 点击"从SiteMap触发Arjun扫描"
   Step 5: 观察Burp日志和代理历史
   Step 6: 等待扫描完成（约30秒）
   ```

3. **预期结果**:
   - ✅ Burp日志显示"执行Arjun: http://testsite.com/api/user"
   - ✅ Burp代理历史中出现带有"X-XProbe-Arjun: 1"的请求
   - ✅ 临时字典文件创建并清理
   - ✅ Arjun命令包含--include参数（保留原参数id）
   - ✅ Arjun命令包含-w参数（指定测试字典）
   - ✅ Arjun命令包含-oB参数（发送到Burp代理）
   - ✅ 如果发现新参数，Burp日志显示"Arjun 发现参数"

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景6: 去重机制测试

#### 测试场景描述
验证去重机制防止重复扫描。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载
   - 配置SQL注入规则（参数名：id）

2. **执行步骤**:
   ```
   Step 1: 连续3次访问同一URL：
      http://testsite.com/user?id=1
   Step 2: 观察仪表盘统计
   Step 3: 检查扫描结果数量
   Step 4: 检查Burp日志
   ```

3. **预期结果**:
   - ✅ 仪表盘显示"Total Requests: 3"
   - ✅ 仪表盘显示"Scanned Requests: 1"（只扫描一次）
   - ✅ 扫描结果中只有一条记录（如果漏洞存在）
   - ✅ Burp日志显示"跳过已扫描参数"消息

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景7: 配置持久化测试

#### 测试场景描述
验证配置能正确保存和加载。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载

2. **执行步骤**:
   ```
   Step 1: 在配置中心添加以下配置：
      - 白名单：testsite.com
      - 黑名单：/admin/
      - Arjun路径：/usr/local/bin/arjun
      - 全局参数：custom_token, api_key
   Step 2: 点击"保存所有配置"
   Step 3: 记录当前配置
   Step 4: 卸载XProbe插件
   Step 5: 重新加载XProbe插件
   Step 6: 检查配置中心
   ```

3. **预期结果**:
   - ✅ 白名单仍然是testsite.com
   - ✅ 黑名单仍然是/admin/
   - ✅ Arjun路径仍然是/usr/local/bin/arjun
   - ✅ 全局参数仍然包含custom_token, api_key

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景8: 大规模流量测试

#### 测试场景描述
验证在大量请求下的性能和稳定性。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载
   - 配置3条扫描规则

2. **执行步骤**:
   ```
   Step 1: 使用脚本生成1000个不同的请求
      for i in {1..1000}; do
        curl "http://testsite.com/page?id=$i&name=test$i" -x 127.0.0.1:8080
      done
   Step 2: 观察仪表盘统计
   Step 3: 观察内存使用（使用jvisualvm或类似工具）
   Step 4: 检查响应时间
   Step 5: 检查是否有错误日志
   ```

3. **预期结果**:
   - ✅ 所有请求正常处理（Total Requests: 1000）
   - ✅ 内存增长在可接受范围内（< 500MB增长）
   - ✅ 无OutOfMemoryError
   - ✅ 响应时间不显著增加
   - ✅ 线程池未耗尽
   - ✅ 无数据丢失

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景9: 并发扫描测试

#### 测试场景描述
验证并发场景下的数据一致性。

#### 测试步骤
1. **前置条件**:
   - XProbe插件已加载

2. **执行步骤**:
   ```
   Step 1: 使用多线程工具同时发送100个请求到不同endpoint
   Step 2: 观察仪表盘统计
   Step 3: 检查参数收集是否有重复
   Step 4: 检查扫描记录是否有丢失
   Step 5: 检查是否有并发异常
   ```

3. **预期结果**:
   - ✅ 统计数据正确
   - ✅ 参数去重正确
   - ✅ 无ConcurrentModificationException
   - ✅ 无数据丢失

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

### 场景10: SSRF DNS日志检测

#### 测试场景描述
验证SSRF扫描器的DNS日志检测功能。

#### 测试步骤
1. **前置条件**:
   - Burp Collaborator可用
   - 配置SSRF规则（payload包含{dnslog}）

2. **执行步骤**:
   ```
   Step 1: 访问存在SSRF漏洞的URL：
      http://testsite.com/fetch?url=http://example.com
   Step 2: 观察扫描活动
   Step 3: 等待5秒（DNS交互检测）
   Step 4: 检查扫描结果
   Step 5: 检查Burp Collaborator交互
   ```

3. **预期结果**:
   - ✅ Payload中{dnslog}被替换为Collaborator域名
   - ✅ 如果存在SSRF，收到DNS交互
   - ✅ 扫描结果中evidence包含DNS交互详情
   - ✅ 无超时错误

4. **实际结果**: ___________________

5. **状态**: ⏳ 待测试

---

## 性能与压力测试

### 性能测试指标

| 指标 | 目标值 | 测试方法 | 状态 |
|------|--------|---------|------|
| 请求处理延迟 | < 50ms | 使用时间戳记录 | ⏳ |
| 内存占用（1000请求） | < 500MB增长 | jvisualvm监控 | ⏳ |
| 线程池利用率 | < 80% | JMX监控 | ⏳ |
| 参数收集性能 | 10000参数 < 1s | 计时测试 | ⏳ |
| 去重查询性能 | 100000次 < 100ms | 计时测试 | ⏳ |
| UI刷新性能 | < 200ms | 用户感知测试 | ⏳ |

### 压力测试场景

#### PT-001: 高频请求压力测试
```
目标: 验证高频请求下的稳定性
方法: 每秒100个请求，持续10分钟
预期: 无崩溃，内存稳定
```

#### PT-002: 大参数量压力测试
```
目标: 验证大量参数存储和查询
方法: 收集100个域名，每个1000个参数
预期: 查询响应 < 1s
```

#### PT-003: 长时间运行测试
```
目标: 验证长时间运行稳定性
方法: 持续扫描24小时
预期: 无内存泄漏，无崩溃
```

#### PT-004: Arjun并发测试
```
目标: 验证多个Arjun进程同时运行
方法: 同时触发10个Arjun扫描
预期: 进程管理正确，无死锁
```

---

## 安全性测试

### ST-001: Payload注入安全
- **测试**: 确保payload不会导致命令注入
- **方法**: 使用恶意payload（如包含`;rm -rf /`）
- **预期**: payload被正确转义或验证

### ST-002: 路径遍历安全
- **测试**: 确保配置文件路径不会被遍历
- **方法**: 尝试加载`../../etc/passwd`
- **预期**: 路径验证阻止非法访问

### ST-003: 反序列化安全
- **测试**: 确保配置反序列化不会导致RCE
- **方法**: 尝试加载恶意序列化对象
- **预期**: 反序列化失败或被拦截

### ST-004: 日志注入安全
- **测试**: 确保日志输出不会被注入
- **方法**: 使用包含`\n`的参数名
- **预期**: 日志输出被转义

---

## 已识别问题清单

### 🔴 严重问题 (Critical)

#### 问题 #1: 配置持久化缺失
**描述**: 配置中心的"保存所有配置"按钮只保存到内存，重启后丢失。

**影响**: 
- 用户配置无法持久化
- 误导用户以为配置已保存

**复现步骤**:
1. 配置黑白名单和Arjun设置
2. 点击"保存所有配置"
3. 卸载并重新加载插件
4. 配置丢失

**解决方案**:
```java
// 1. 创建统一配置类
public class XProbeConfig {
    private List<String> whitelist;
    private List<String> blacklist;
    private String arjunPath;
    private String burpProxyAddress;
    private CollectionMode collectionMode;
    // ... 其他配置
}

// 2. 使用Jackson序列化到JSON
public class ConfigPersistence {
    private static final String CONFIG_FILE = "xprobe-config.json";
    
    public void save(XProbeConfig config) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
              .writeValue(new File(CONFIG_FILE), config);
    }
    
    public XProbeConfig load() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            return mapper.readValue(file, XProbeConfig.class);
        }
        return new XProbeConfig(); // 默认配置
    }
}

// 3. 在初始化时加载
public void initialize(MontoyaApi api) {
    try {
        XProbeConfig config = configPersistence.load();
        applyConfig(config);
    } catch (IOException e) {
        api.logging().raiseErrorEvent("加载配置失败，使用默认配置");
    }
}
```

**优先级**: P0

**状态**: ⏳ 待修复

---

#### 问题 #2: 被动扫描去重Key不完整
**描述**: 被动扫描去重Key没有包含scanType，导致不同扫描类型可能被误判为重复。

**影响**:
- 同一参数的SQL注入和LFI扫描可能被跳过

**复现步骤**:
1. 配置SQL和LFI两种扫描规则，参数名都是"file"
2. 访问`http://test.com/page?file=test.txt`
3. 只有第一个扫描类型执行，第二个被跳过

**位置**: `RequestHandler.java:134`

**当前代码**:
```java
String key = method + "|" + host + "|" + cleanPath + "|" + normalizedContentType + 
           "|" + paramName;  // 缺少 scanType
```

**修复代码**:
```java
String key = method + "|" + host + "|" + cleanPath + "|" + normalizedContentType + 
           "|" + paramName + "|" + scanType;  // 添加 scanType
```

**优先级**: P0

**状态**: ⏳ 待修复

---

### 🟡 重要问题 (Major)

#### 问题 #3: 线程安全问题
**描述**: 多个线程同时修改`passiveScanProcessedKeys`可能导致竞态条件。

**位置**: `RealtimeScannerRefactored.java:34`

**当前代码**:
```java
private final Set<String> passiveScanProcessedKeys = ConcurrentHashMap.newKeySet();
```

**分析**: ConcurrentHashMap.newKeySet()是线程安全的，但问题在于检查和添加不是原子操作。

**修复方案**: 使用`computeIfAbsent`或显式同步。

**优先级**: P1

**状态**: ⏳ 待修复

---

#### 问题 #4: Arjun进程管理不完善
**描述**: Arjun进程可能在异常情况下无法正确清理。

**影响**:
- 僵尸进程残留
- 资源泄漏

**位置**: `ArjunIntegration.java:325`

**修复方案**:
```java
private ArjunResult executeArjun(List<String> command, String url) {
    Process process = null;
    try {
        ProcessBuilder pb = new ProcessBuilder(command);
        process = pb.start();
        
        // 添加超时机制
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ArjunResult.error("Arjun执行超时");
        }
        
        // ... 处理输出
    } finally {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
```

**优先级**: P1

**状态**: ⏳ 待修复

---

#### 问题 #5: JSON参数提取不完整
**描述**: JSON字段提取只支持一级字段，不支持嵌套对象。

**影响**:
- 嵌套JSON参数无法被扫描

**示例**:
```json
{
  "user": {
    "id": 123,        // 无法提取
    "name": "test"    // 无法提取
  }
}
```

**位置**: `ArjunIntegration.java:274`

**修复方案**: 使用Jackson库递归解析JSON。

**优先级**: P1

**状态**: ⏳ 待修复

---

#### 问题 #6: 参数名验证过于严格
**描述**: 参数名正则`[A-Za-z0-9_.~\-\[\]]+`可能拒绝合法参数。

**影响**:
- 某些合法参数（如`user[email]`）可能被过滤

**位置**: `ParameterCollector.java:258`

**建议**: 放宽验证规则或添加配置选项。

**优先级**: P2

**状态**: ⏳ 待评估

---

### 🟢 轻微问题 (Minor)

#### 问题 #7: 日志输出过于频繁
**描述**: Debug日志在生产环境可能影响性能。

**位置**: 多处`api.logging().raiseDebugEvent()`

**修复方案**: 添加日志级别控制。

**优先级**: P2

---

#### 问题 #8: UI字符串硬编码
**描述**: UI中的中文字符串硬编码，不支持国际化。

**修复方案**: 使用ResourceBundle。

**优先级**: P3

---

#### 问题 #9: 配置验证不足
**描述**: Arjun路径、代理地址等配置缺少验证。

**影响**:
- 无效配置导致运行时错误

**修复方案**: 添加表单验证。

**优先级**: P2

---

#### 问题 #10: 统计信息计算重复
**描述**: 每次刷新都重新计算统计信息，可能影响性能。

**修复方案**: 缓存统计结果，定期更新。

**优先级**: P2

---

### 🔵 潜在改进 (Enhancement)

#### 改进 #1: 添加单元测试
**描述**: 当前缺少单元测试，难以保证代码质量。

**建议**: 使用JUnit 5添加核心模块测试。

**优先级**: P1

---

#### 改进 #2: 添加性能监控
**描述**: 缺少性能指标采集和监控。

**建议**: 集成Micrometer或JMX。

**优先级**: P2

---

#### 改进 #3: 导出测试报告
**描述**: 扫描结果只能在UI查看，无法导出。

**建议**: 支持导出CSV、JSON、HTML格式报告。

**优先级**: P2

---

#### 改进 #4: 支持自定义扫描器
**描述**: 当前只支持SQL、LFI、SSRF三种扫描器。

**建议**: 提供扫描器插件接口。

**优先级**: P3

---

#### 改进 #5: 参数智能推荐
**描述**: 参数收集后可以基于API规范自动推荐测试参数。

**建议**: 集成OpenAPI解析或机器学习模型。

**优先级**: P3

---

## 测试数据准备

### 测试网站要求
建议使用以下测试环境：
1. **DVWA** (Damn Vulnerable Web Application)
2. **WebGoat** (OWASP测试平台)
3. **SQLi-labs** (SQL注入练习平台)
4. **自建测试站点**

### 测试数据集

#### SQL注入测试数据
```
# 参数名
id, user_id, product_id, category, search, q, query

# Payload
' OR 1=1--
' AND SLEEP(5)--
' UNION SELECT NULL--
{value}' OR '1'='1
```

#### LFI测试数据
```
# 参数名
file, page, template, include, document

# Payload
../../../etc/passwd
..\\..\\..\\windows\\system.ini
....//....//....//etc/passwd
%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd
```

#### SSRF测试数据
```
# 参数名
url, link, callback, redirect, target

# Payload
http://127.0.0.1
http://localhost
http://{dnslog}
http://169.254.169.254/latest/meta-data/
```

#### 参数收集测试数据
```
# 目标主域名
testsite.com
api.testsite.com
admin.testsite.com

# 测试接口
/api/user?id=1&name=test&email=test@example.com
/api/product?pid=100&category=books&sort=price
/api/search?q=test&limit=10&offset=0&filter=active
/admin/users?page=1&per_page=20
```

---

## 测试环境要求

### 软件要求
| 软件 | 版本 | 用途 |
|------|------|------|
| Burp Suite Pro | 2023.1+ | 运行插件 |
| Java JDK | 17+ | 编译和运行 |
| Arjun | 2.1.5+ | 参数探测 |
| Python | 3.8+ | Arjun依赖 |
| Gradle | 7.0+ | 构建工具 |

### 硬件要求
| 资源 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 双核 2.0GHz | 四核 3.0GHz |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 500MB | 2GB+ |

### 网络要求
- 能够访问测试网站
- Burp Collaborator可用（用于SSRF测试）
- 本地代理端口8080可用

---

## 测试执行计划

### 第一阶段: 核心功能测试 (3天)
- 被动扫描模块所有测试用例
- 参数收集基础功能
- 黑白名单过滤
- 配置管理基础功能

### 第二阶段: 主动探测测试 (2天)
- Arjun集成完整测试
- 参数管理器所有功能
- 实时扫描器所有功能

### 第三阶段: UI和集成测试 (2天)
- 所有UI组件测试
- 端到端场景测试
- 用户体验测试

### 第四阶段: 性能和压力测试 (2天)
- 所有性能指标测试
- 压力测试场景
- 稳定性测试

### 第五阶段: 回归测试和修复验证 (1天)
- 修复问题后的验证测试
- 完整回归测试

---

## 测试报告模板

### 测试执行报告

#### 基本信息
- **测试日期**: YYYY-MM-DD
- **测试人员**: _______________
- **测试版本**: XProbe v1.0.0
- **测试环境**: _______________

#### 测试统计
| 指标 | 数量 |
|------|------|
| 计划测试用例 | ___ |
| 执行测试用例 | ___ |
| 通过用例 | ___ |
| 失败用例 | ___ |
| 阻塞用例 | ___ |
| 通过率 | ___% |

#### 缺陷统计
| 严重程度 | 数量 | 已修复 | 待修复 |
|---------|------|--------|--------|
| P0 严重 | ___ | ___ | ___ |
| P1 重要 | ___ | ___ | ___ |
| P2 一般 | ___ | ___ | ___ |
| P3 轻微 | ___ | ___ | ___ |

#### 测试结论
- [ ] ✅ 通过测试，可发布
- [ ] ⚠️ 部分功能有问题，需修复
- [ ] ❌ 存在严重问题，不可发布

#### 遗留问题
1. _______________
2. _______________
3. _______________

#### 测试建议
1. _______________
2. _______________
3. _______________

---

## 附录

### A. 测试工具
- **Burp Suite Pro**: 主要测试环境
- **JMeter**: 压力测试工具
- **jVisualVM**: 性能监控工具
- **curl**: 命令行测试工具
- **Postman**: API测试工具

### B. 参考文档
- [Burp Extensions API文档](https://portswigger.github.io/burp-extensions-montoya-api/)
- [Arjun文档](https://github.com/s0md3v/Arjun)
- [OWASP测试指南](https://owasp.org/www-project-web-security-testing-guide/)

### C. 问题追踪
建议使用GitHub Issues或Jira追踪测试中发现的问题。

---

**文档版本**: v1.0  
**最后更新**: 2025-10-01  
**维护者**: XProbe测试团队


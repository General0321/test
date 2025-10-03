# 🧪 Arjun Java集成 - 完整测试指南

## 📋 测试前准备

### 1. 编译插件
```bash
cd /Users/0x7llcf/Desktop/tools/burp插件/XProbe
./gradlew build -x test

# 确认JAR生成
ls -lh build/libs/XProbe-1.0.0.jar
```

### 2. 加载到Burp Suite
1. 打开Burp Suite Professional
2. Extensions → Installed → Add
3. 选择 `build/libs/XProbe-1.0.0.jar`
4. 确认加载成功（Output显示"✅ 实时扫描器已初始化"）

---

## 🎯 核心功能测试

### 测试1：参数收集功能

**目的：** 验证ParameterCollector能否正确收集参数

**步骤：**
1. 在Burp中浏览测试网站（如：http://testphp.vulnweb.com）
2. 访问几个包含参数的页面：
   - GET: `/listproducts.php?cat=1`
   - POST: `/login.php` (username=admin&password=123)
   - JSON: `/api/user` ({"id": 1, "name": "test"})

**验证：**
- 切换到XProbe → 📊 仪表板
- 检查"参数"统计卡片数字是否增加
- 检查"接口"统计卡片数字是否增加
- 查看"参数收集统计"区域，确认收集到参数

**预期结果：**
```
参数收集统计：
  主域名: testphp.vulnweb.com
  参数总数: 3 (cat, username, password)
  关键词总数: 0
  接口总数: 3
```

---

### 测试2：手动触发Arjun扫描（从SiteMap）

**目的：** 验证Arjun能否从SiteMap获取流量并发现隐藏参数

**步骤：**
1. 确保已经浏览了一些页面（SiteMap有内容）
2. 在XProbe → 📋 Active Probe（或API接口）中点击"开始Arjun扫描"
3. 或在代码中调用：`realtimeScanner.triggerManualArjunScan()`

**验证：**
- 观察Burp Suite → Extensions → Output窗口
- 应该看到类似以下日志：

```
从 SiteMap 历史流量触发 Arjun 扫描...
从 SiteMap 获取了 50 个请求，分组为 3 个主域名
主域名 testphp.vulnweb.com: 收集了 5 个参数, 8 个接口组合
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔍 参数发现开始: http://testphp.vulnweb.com/listproducts.php
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 阶段1: 稳定性探测...
  ✓ 目标稳定（尝试 3 次）
📦 阶段2: 准备字典...
📚 字典大小: 157 个参数 (普通: 5, 特殊: 152)
🔄 阶段3: 分块爆破...
✅ 参数发现完成，耗时: 2345ms
Arjun 发现参数: GET testphp.vulnweb.com (application/x-www-form-urlencoded) /listproducts.php - [debug, id]
```

**预期结果：**
- Arjun成功扫描多个接口
- 发现一些隐藏参数（如debug、admin、id等）
- Dashboard的"Arjun扫描"计数增加
- LogModel中出现Arjun日志

---

### 测试3：Arjun → 漏洞扫描闭环

**目的：** 验证Arjun发现的参数能否自动触发漏洞扫描

**步骤：**
1. 确保至少启用了一个检测规则（如SQL注入、XSS）
2. 触发Arjun扫描
3. 等待Arjun发现参数

**验证：**
- 观察Output窗口，应该看到：

```
✅ Arjun发现参数: POST /api/user - [id, token, debug]
🔍 触发漏洞扫描: 3 个参数 × 15 个规则 = 45 个任务
```

- 切换到XProbe → 📋 扫描结果
- 应该看到新增的扫描结果，来源标记为"ARJUN"

**预期结果：**
```
| 来源   | Method | URL          | 响应码 | 命中规则                  |
|--------|--------|--------------|--------|---------------------------|
| ARJUN  | POST   | /api/user    | 200    | SQL注入: id参数疑似存在注入 |
| ARJUN  | POST   | /api/user    | 200    | XSS: debug参数疑似存在XSS   |
```

---

### 测试4：GET/POST/POST-JSON支持

**目的：** 验证Arjun对不同HTTP方法的支持

**测试用例：**

#### 4.1 GET请求
```http
GET /search?q=test HTTP/1.1
Host: example.com
```

**预期：** Arjun添加参数到URL
```http
GET /search?q=test&debug=test_value&admin=test_value HTTP/1.1
```

#### 4.2 POST表单
```http
POST /login HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

username=admin&password=123
```

**预期：** Arjun添加参数到Body
```http
POST /login HTTP/1.1
Host: example.com
Content-Type: application/x-www-form-urlencoded

username=admin&password=123&debug=test_value&token=test_value
```

#### 4.3 POST-JSON
```http
POST /api/user HTTP/1.1
Host: example.com
Content-Type: application/json

{"username": "admin"}
```

**预期：** Arjun合并参数到JSON
```http
POST /api/user HTTP/1.1
Host: example.com
Content-Type: application/json

{"username": "admin", "id": "test_value", "debug": "test_value"}
```

**验证：**
- 在Burp Proxy → HTTP History中观察Arjun发送的请求
- 过滤包含`X-XProbe-ParamDiscovery: 1`的请求
- 确认参数注入方式正确

---

### 测试5：去重机制

**目的：** 验证Arjun不会重复扫描同一接口

**步骤：**
1. 第一次触发Arjun扫描
2. 观察扫描了哪些接口
3. 立即再次触发Arjun扫描

**预期结果：**
```
第一次扫描：
  扫描 10 个接口, 跳过 0 个, 总增量参数 150 个

第二次扫描：
  扫描 0 个接口, 跳过 10 个, 总增量参数 0 个
  （所有接口已扫描过，跳过）
```

**验证：**
- 观察Output窗口的日志
- 确认第二次扫描时，所有接口都被跳过
- 确认"无新参数"的消息

---

### 测试6：特殊参数发现

**目的：** 验证Arjun能否发现特殊参数（debug、admin、waf等）

**步骤：**
1. 准备一个包含隐藏参数的测试接口：
   ```php
   // test.php
   if (isset($_GET['debug'])) {
       echo "Debug mode enabled!";
   }
   ```

2. 对该接口触发Arjun扫描

**验证：**
- 观察Output窗口
- 应该看到：`Arjun 发现参数: ... - [debug]`
- Arjun应该使用特殊值测试：`debug=yes`、`debug=true`等

**预期结果：**
- 成功发现`debug`参数
- 使用特殊值测试（yes/true/1/on）
- 响应异常被正确检测

---

### 测试7：动态稳定性因子移除

**目的：** 验证Arjun能否处理不稳定的目标

**场景：** 目标响应包含动态内容（如时间戳、随机数）

**步骤：**
1. 准备一个返回动态内容的接口：
   ```php
   // dynamic.php
   echo "Time: " . time();
   echo "Random: " . rand();
   ```

2. 对该接口触发Arjun扫描

**验证：**
- 观察Output窗口
- 应该看到：

```
📊 阶段1: 稳定性探测...
  开始稳定性验证（动态因子调整）...
  移除不稳定因子: body_content (响应体不同)
  移除不稳定因子: plaintext (纯文本不同)
  ✓ 目标稳定（尝试 5 次）
```

**预期结果：**
- Arjun自动移除不稳定的因子
- 最终找到稳定状态
- 能够正常发现参数

---

### 测试8：Dashboard统计显示

**目的：** 验证Dashboard能正确显示Arjun统计

**步骤：**
1. 执行几次Arjun扫描（成功和失败都有）
2. 切换到XProbe → 📊 仪表板

**验证：**
检查以下统计卡片：
- **Arjun扫描**: 显示总扫描次数
- **参数**: 显示收集的参数总数
- **接口**: 显示接口组合数

**预期结果：**
```
┌─────────────────┐
│ 🚀 Arjun扫描    │
│     15          │
└─────────────────┘

日志表格：
| 来源   | Method | URL          | 响应码 | 命中规则                      |
|--------|--------|--------------|--------|-------------------------------|
| Arjun  | POST   | /api/user    | 0      | 发现: [id, token] | 耗时: 1234ms |
| Arjun  | GET    | /search      | 0      | 无新参数 | 耗时: 567ms        |
| ARJUN  | POST   | /api/user    | 200    | SQL注入: id参数疑似存在注入    |
```

---

## 🔍 边界条件测试

### 边界1：空字典
**场景：** ParameterCollector未收集到任何参数

**预期：** Arjun仍然使用152个特殊参数进行扫描

### 边界2：大量参数
**场景：** 收集到500+个参数

**预期：** Arjun使用分块爆破，正确处理大字典

### 边界3：目标不稳定
**场景：** 目标每次返回不同内容

**预期：** Arjun动态移除不稳定因子，或标记为"目标不稳定"

### 边界4：目标返回错误状态码
**场景：** 目标返回400、429、503等

**预期：** Arjun检测到不健康状态码，跳过扫描

---

## 📊 性能测试

### 性能1：扫描速度
**测试：** 对100个接口进行Arjun扫描

**指标：**
- 每个接口扫描时间：< 5秒（250参数/块）
- CPU使用率：< 50%
- 内存占用：< 500MB

### 性能2：并发扫描
**测试：** 同时触发多个Arjun扫描

**预期：** 
- 异步执行，不阻塞主线程
- 任务队列正确管理
- 无死锁或资源竞争

---

## ✅ 测试检查清单

### 基础功能
- [ ] ParameterCollector正确收集参数
- [ ] ParameterManager正确管理参数
- [ ] Arjun能从SiteMap获取流量
- [ ] Arjun能从ParameterCollector获取字典
- [ ] Arjun正确发现隐藏参数

### HTTP方法支持
- [ ] GET请求：参数添加到URL
- [ ] POST表单：参数添加到Body
- [ ] POST-JSON：参数合并到JSON

### 去重机制
- [ ] 跳过Arjun自己的流量
- [ ] 应用全局黑白名单
- [ ] 接口级去重（method+host+contentType+endpoint）
- [ ] 参数级去重（只扫描未测试的参数）
- [ ] 扫描后标记，避免重复

### 闭环集成
- [ ] Arjun发现参数后触发漏洞扫描
- [ ] 构造包含新参数的HTTP请求
- [ ] 为每个参数创建ScanTask
- [ ] TaskScheduler正确调度任务
- [ ] UniversalScanner执行漏洞检测

### 日志和统计
- [ ] Output窗口显示详细日志
- [ ] LogModel记录Arjun扫描日志
- [ ] Dashboard显示Arjun统计
- [ ] 扫描结果正确显示来源（Arjun/ARJUN）

### 稳定性
- [ ] 动态稳定性因子移除
- [ ] 健康状态码检测
- [ ] 特殊参数支持（152个）
- [ ] 异常处理正确

---

## 🐛 已知问题检查

1. **类型转换警告**
   - 位置：`RealtimeScannerRefactored.triggerVulnerabilityScan()`
   - 解决：使用`@SuppressWarnings("unchecked")`抑制
   - 状态：✅ 已处理

2. **资源泄漏**
   - 位置：已在所有`ArjunIntegration`流资源中修复
   - 状态：✅ 已修复

3. **线程池关闭**
   - 位置：`TaskScheduler.shutdown()`
   - 状态：✅ 已增强（awaitTermination + shutdownNow）

---

## 📝 测试报告模板

### 测试环境
- Burp Suite版本：___________
- XProbe版本：1.0.0
- 测试目标：___________
- 测试日期：___________

### 测试结果

#### 1. 参数收集
- [ ] 通过 / [ ] 失败
- 收集参数数：___________
- 备注：___________

#### 2. Arjun扫描
- [ ] 通过 / [ ] 失败
- 扫描接口数：___________
- 发现参数数：___________
- 平均扫描时间：___________
- 备注：___________

#### 3. 漏洞扫描闭环
- [ ] 通过 / [ ] 失败
- 触发任务数：___________
- 发现漏洞数：___________
- 备注：___________

#### 4. 性能表现
- CPU使用率：___________%
- 内存占用：___________MB
- 备注：___________

### 问题记录
1. ___________
2. ___________

### 总结
___________

---

## 🎯 快速测试脚本

### 测试场景1：基础功能验证
```bash
# 1. 编译
./gradlew build -x test

# 2. 加载到Burp
# 手动操作：Extensions → Add → 选择JAR

# 3. 访问测试网站
# 手动操作：在Burp中浏览 http://testphp.vulnweb.com

# 4. 触发Arjun扫描
# 在XProbe UI中点击"开始Arjun扫描"

# 5. 观察结果
# - Extensions → Output：查看日志
# - XProbe → 仪表板：查看统计
# - XProbe → 扫描结果：查看发现的漏洞
```

### 测试场景2：验证HTTP方法支持
```bash
# 准备测试接口
# GET: http://example.com/search?q=test
# POST: http://example.com/login (username=admin)
# JSON: http://example.com/api/user ({"id": 1})

# 触发Arjun扫描

# 在Burp Proxy → HTTP History中过滤：
# Header包含: X-XProbe-ParamDiscovery: 1

# 验证参数注入位置：
# - GET: 参数在URL
# - POST表单: 参数在Body
# - POST-JSON: 参数在JSON对象中
```

---

## 🚀 推荐测试流程

1. **环境准备** (5分钟)
   - 编译插件
   - 加载到Burp
   - 准备测试目标

2. **基础功能测试** (10分钟)
   - 参数收集
   - Arjun扫描
   - 结果查看

3. **核心流程测试** (15分钟)
   - Arjun → 漏洞扫描闭环
   - 多种HTTP方法
   - 去重机制

4. **边界条件测试** (10分钟)
   - 不稳定目标
   - 错误状态码
   - 空字典

5. **性能测试** (10分钟)
   - 扫描速度
   - 资源占用
   - 并发能力

---

**总测试时间：约1小时**

**成功标准：**
- ✅ 所有基础功能测试通过
- ✅ Arjun → 漏洞扫描闭环正常工作
- ✅ 支持GET/POST/POST-JSON
- ✅ 去重机制正确
- ✅ 无明显性能问题
- ✅ 日志和统计正确显示


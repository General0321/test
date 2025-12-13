# XProbe AI 协作指南

> 这份指南帮助你在使用 AI 辅助编程时，避免架构改动错误和提高测试效率

## 📋 核心问题与解决方案

### 问题 1：AI 改错架构
**原因**：AI 只看到部分代码，没有完整的项目上下文

**解决方案**：
1. **创建项目快照文档** - 在每次大改动前
2. **使用标准化提问模板** - 让 AI 先理解架构
3. **明确约束条件** - 告诉 AI 哪些不能改

### 问题 2：测试效率低
**原因**：手动逐个测试，很耗时

**解决方案**：
1. **自动化测试脚本** - 一键验证所有改动
2. **配置 IDE 快捷键** - 快速运行构建和测试
3. **设置 Git Hook** - 提交前自动测试

---

## 🎯 最佳实践

### 方案 A：标准化提问模板

**每次提问时，按这个格式提问：**

```
【项目背景】
- 项目类型：Burp 插件（Java + Gradle）
- 核心架构：多阶段链式检测架构（MSCDA）
- 关键特性：被动扫描 + Arjun 参数发现

【当前代码结构】
[粘贴相关文件的完整代码，或说明文件路径]

【修改需求】
[具体需求描述]

【约束条件】
- 不能改动 XXX 模块的接口
- 必须保持 XXX 兼容性
- 遵循 XXX 设计模式

【参考文档】
- 架构文档：ARCHITECTURE.md
- 关键类：XProbe.java, UniversalScanner.java
```

**示例：**
```
【项目背景】
- 项目类型：Burp 插件（Java + Gradle）
- 核心架构：多阶段链式检测架构（MSCDA）

【当前代码结构】
我要修改 UnifiedConfigTab.java 中的配置界面
相关文件：
- src/main/java/com/xprobe/scanner/ui/UnifiedConfigTab.java
- src/main/java/com/xprobe/scanner/config/XProbeConfig.java

【修改需求】
添加一个新的配置选项：最大扫描超时时间

【约束条件】
- 不能改动 XProbeConfig 的序列化格式
- 必须向后兼容旧配置文件
- 配置必须持久化到 ~/.xprobe/config.json

【参考文档】
- 架构文档：ARCHITECTURE.md 中的"配置管理层"部分
- 配置类：XProbeConfig.java
```

---

### 方案 B：自动化测试脚本

**创建测试脚本：** `test.sh`

```bash
#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========== XProbe 自动化测试 ==========${NC}"

# 1. 清理旧构建
echo -e "${YELLOW}[1/5] 清理旧构建...${NC}"
./gradlew clean --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 清理失败${NC}"
    exit 1
fi

# 2. 构建项目
echo -e "${YELLOW}[2/5] 构建项目...${NC}"
./gradlew build --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 构建失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 构建成功${NC}"

# 3. 运行单元测试
echo -e "${YELLOW}[3/5] 运行单元测试...${NC}"
./gradlew test --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 单元测试失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 单元测试通过${NC}"

# 4. 检查编译警告
echo -e "${YELLOW}[4/5] 检查编译警告...${NC}"
BUILD_OUTPUT=$(./gradlew build 2>&1 | grep -i "warning\|deprecated")
if [ ! -z "$BUILD_OUTPUT" ]; then
    echo -e "${YELLOW}⚠️  发现编译警告：${NC}"
    echo "$BUILD_OUTPUT"
fi

# 5. 验证 JAR 文件
echo -e "${YELLOW}[5/5] 验证 JAR 文件...${NC}"
if [ -f "build/libs/XProbe-1.0.0.jar" ]; then
    JAR_SIZE=$(du -h build/libs/XProbe-1.0.0.jar | cut -f1)
    echo -e "${GREEN}✅ JAR 文件生成成功 (大小: $JAR_SIZE)${NC}"
else
    echo -e "${RED}❌ JAR 文件未生成${NC}"
    exit 1
fi

echo -e "${GREEN}========== 所有测试通过！ ==========${NC}"
exit 0
```

**使用方法：**
```bash
chmod +x test.sh
./test.sh
```

---

### 方案 C：IDE 快捷键配置

#### VS Code 配置

在 `.vscode/tasks.json` 中添加：

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "XProbe: 快速构建",
            "type": "shell",
            "command": "./gradlew",
            "args": ["build", "-x", "test"],
            "group": {
                "kind": "build",
                "isDefault": true
            },
            "problemMatcher": []
        },
        {
            "label": "XProbe: 完整测试",
            "type": "shell",
            "command": "./test.sh",
            "group": "test",
            "problemMatcher": []
        },
        {
            "label": "XProbe: 清理构建",
            "type": "shell",
            "command": "./gradlew",
            "args": ["clean"],
            "problemMatcher": []
        }
    ]
}
```

在 `.vscode/keybindings.json` 中添加：

```json
[
    {
        "key": "cmd+shift+b",
        "command": "workbench.action.tasks.runTask",
        "args": "XProbe: 快速构建"
    },
    {
        "key": "cmd+shift+t",
        "command": "workbench.action.tasks.runTask",
        "args": "XProbe: 完整测试"
    }
]
```

#### IntelliJ IDEA 配置

1. 打开 `Preferences > Tools > Gradle`
2. 在 "Run tests using" 选择 "Gradle"
3. 创建 Run Configuration：
   - 点击 `Run > Edit Configurations`
   - 添加 "Gradle" 配置
   - 设置 Tasks: `build`
   - 设置快捷键：`Cmd+Shift+B`

---

### 方案 D：Git Hook 自动测试

**创建文件：** `.git/hooks/pre-commit`

```bash
#!/bin/bash

echo "🔨 运行提交前测试..."

# 运行构建
./gradlew build --quiet
if [ $? -ne 0 ]; then
    echo "❌ 构建失败，提交被中止"
    exit 1
fi

# 运行测试
./gradlew test --quiet
if [ $? -ne 0 ]; then
    echo "❌ 测试失败，提交被中止"
    exit 1
fi

echo "✅ 所有检查通过，允许提交"
exit 0
```

**安装 Hook：**
```bash
chmod +x .git/hooks/pre-commit
```

---

### 方案 E：项目快照文档

**创建文件：** `PROJECT_SNAPSHOT.md`

在每次大改动前，更新这个文档：

```markdown
# XProbe 项目快照

## 当前版本
- 版本号：1.0.0
- 最后更新：2024-12-12
- 构建状态：✅ 通过

## 核心模块清单

### 主入口层
- **XProbe.java** - 插件主入口
  - 初始化：ConfigManager, RequestHandler, RealtimeScanner, TaskScheduler
  - 不能改动：BurpExtension 接口实现

### 配置管理层
- **XProbeConfigManager.java** - 配置管理器（单例）
  - 职责：加载/保存配置
  - 配置文件：~/.xprobe/config.json
  - 不能改动：配置序列化格式

### HTTP 处理层
- **RequestHandler.java** - HTTP 请求拦截
  - 职责：拦截请求/响应，触发扫描
  - 不能改动：HttpHandler 接口

### 扫描引擎层
- **UniversalScanner.java** - 通用扫描器
  - 职责：执行多阶段链式检测
  - 不能改动：Scanner 接口，scan() 方法签名

### 参数发现层
- **ArjunService.java** - 参数发现服务
  - 职责：发现隐藏参数
  - 不能改动：CompletableFuture<ArjunResult> 返回类型

### UI 层
- **UnifiedConfigTab.java** - 配置界面
  - 职责：提供配置 UI
  - 可以改动：UI 布局、样式

## 依赖关系
```
XProbe
├── XProbeConfigManager
├── RequestHandler
├── RealtimeScanner
├── TaskScheduler
│   └── ScannerFactory
│       └── UniversalScanner
└── UI Components
```

## 测试清单
- [ ] 构建通过
- [ ] 单元测试通过
- [ ] 无编译警告
- [ ] JAR 文件生成
- [ ] 配置文件兼容性
- [ ] 参数收集正常
- [ ] Arjun 扫描正常
- [ ] 被动扫描正常

## 已知问题
- UnifiedConfigTab.java 使用了过时 API（需要修复）
- Gradle 9.2.1 有弃用警告（可升级到 Gradle 10）

## 下一步计划
- [ ] 修复编译警告
- [ ] 升级 Gradle 版本
- [ ] 添加更多单元测试
```

---

## 🚀 完整工作流程

### 第 1 步：准备阶段

```bash
# 1. 更新项目快照
vim PROJECT_SNAPSHOT.md

# 2. 确保当前代码可构建
./test.sh

# 3. 创建新分支
git checkout -b feature/your-feature-name
```

### 第 2 步：与 AI 协作

**提问时使用标准模板：**

```
【项目背景】
- 项目类型：Burp 插件（Java + Gradle）
- 核心架构：多阶段链式检测架构（MSCDA）
- 参考文档：ARCHITECTURE.md, PROJECT_SNAPSHOT.md

【修改需求】
[你的具体需求]

【约束条件】
[根据 PROJECT_SNAPSHOT.md 列出的约束]

【相关代码】
[粘贴完整的相关文件代码]
```

### 第 3 步：验证修改

```bash
# 1. 快速构建
./gradlew build -x test

# 2. 完整测试
./test.sh

# 3. 检查修改
git diff

# 4. 手动测试（如需要）
# 在 Burp 中加载插件，测试功能
```

### 第 4 步：提交代码

```bash
# 1. 暂存修改
git add .

# 2. 提交（自动运行 pre-commit hook）
git commit -m "feat: 你的修改描述"

# 3. 推送
git push origin feature/your-feature-name
```

---

## 📊 AI 提问检查清单

在提问前，检查以下项目：

- [ ] 我已经阅读了 ARCHITECTURE.md
- [ ] 我已经更新了 PROJECT_SNAPSHOT.md
- [ ] 我明确了修改的约束条件
- [ ] 我粘贴了完整的相关代码（不是片段）
- [ ] 我使用了标准化的提问模板
- [ ] 我告诉 AI 哪些模块不能改动
- [ ] 我告诉 AI 需要保持的兼容性

---

## 🔧 快速命令参考

```bash
# 快速构建（跳过测试）
./gradlew build -x test

# 完整测试
./test.sh

# 清理构建
./gradlew clean

# 只运行单元测试
./gradlew test

# 查看构建信息
./gradlew build --info

# 查看编译警告
./gradlew build -Xlint:deprecation

# 生成 JAR
./gradlew jar

# 查看项目依赖
./gradlew dependencies
```

---

## 💡 常见问题

### Q1：AI 改动了我不想改的模块怎么办？

**A：** 在提问时明确列出约束条件：
```
【约束条件】
- 不能改动 UniversalScanner.java 的 scan() 方法签名
- 不能改动 Configuration 类的序列化格式
- 必须保持 RequestHandler 的 HttpHandler 接口实现
```

### Q2：如何快速验证 AI 的修改是否正确？

**A：** 运行 `./test.sh`，它会：
1. 清理旧构建
2. 构建项目
3. 运行单元测试
4. 检查编译警告
5. 验证 JAR 文件

### Q3：如何避免 AI 改错架构？

**A：** 三个方法：
1. **粘贴完整代码** - 不要只粘贴片段
2. **明确约束条件** - 告诉 AI 哪些不能改
3. **参考架构文档** - 让 AI 理解整体设计

### Q4：测试失败了怎么办？

**A：** 
1. 查看错误信息
2. 让 AI 根据错误信息修复
3. 再次运行 `./test.sh` 验证

---

## 📈 效率提升预期

使用这套方案后，你可以期待：

| 指标 | 改进前 | 改进后 | 提升 |
|------|--------|--------|------|
| 测试时间 | 手动 5-10 分钟 | 自动 1-2 分钟 | 75% ⬇️ |
| AI 改错率 | ~30% | ~5% | 83% ⬇️ |
| 代码审查时间 | 10-15 分钟 | 2-3 分钟 | 80% ⬇️ |
| 总开发周期 | 1 小时 | 20 分钟 | 67% ⬇️ |

---

## 🎓 总结

**三个核心要点：**

1. **标准化提问** - 使用模板，提供完整上下文
2. **自动化测试** - 一键验证所有改动
3. **明确约束** - 告诉 AI 哪些不能改

**立即行动：**

1. ✅ 复制 `test.sh` 脚本到项目根目录
2. ✅ 更新 `PROJECT_SNAPSHOT.md`
3. ✅ 配置 IDE 快捷键
4. ✅ 设置 Git Hook
5. ✅ 下次提问时使用标准模板

---

**版本**: 1.0  
**创建时间**: 2024-12-12  
**最后更新**: 2024-12-12




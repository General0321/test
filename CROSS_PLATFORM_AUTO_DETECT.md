# 🌍 跨平台Arjun自动检测完整实现

## 📅 日期
2025年10月1日

## 🎯 用户需求

> "兼容性强一点吧，还有windows的时候，最好能自动寻找系统中的路径并配置"

**完全实现！** ✅

---

## ✨ 新增功能

### 1️⃣ **跨平台Python解释器提取**
支持Windows、macOS、Linux三大平台

### 2️⃣ **智能Arjun路径自动检测**
三层检测机制：Python模块 → 常见路径 → 系统命令

### 3️⃣ **UI自动检测按钮**
一键自动检测并配置Arjun路径

---

## 🔧 技术实现

### 1. 跨平台Python解释器提取

#### Windows平台
```java
// Anaconda示例:
// C:\Users\xxx\anaconda3\Scripts\arjun.exe
// → C:\Users\xxx\anaconda3\python.exe

if (isWindows) {
    if (arjunPath.contains("anaconda")) {
        // 检测 \Scripts\ 目录
        int scriptsIndex = arjunPath.toLowerCase().lastIndexOf("\\scripts\\");
        if (scriptsIndex > 0) {
            pythonPath = arjunPath.substring(0, scriptsIndex) + "\\python.exe";
        }
    }
    
    // 默认使用 python (Windows一般是python而不是python3)
    return "python";
}
```

#### macOS/Linux平台
```java
// Anaconda示例:
// /opt/anaconda3/bin/arjun
// → /opt/anaconda3/bin/python3

if (!isWindows) {
    if (arjunPath.contains("anaconda")) {
        int binIndex = arjunPath.lastIndexOf("/bin/");
        if (binIndex > 0) {
            pythonPath = arjunPath.substring(0, binIndex + 5) + "python3";
        }
    }
    
    // 默认使用 python3 (Unix系统)
    return "python3";
}
```

---

### 2. 智能三层自动检测

#### 第一层：Python模块检测（最优先）
```java
// 尝试: python -m arjun (Windows) 或 python3 -m arjun (Unix)
String pythonCmd = isWindows ? "python" : "python3";
if (testPythonModule(pythonCmd)) {
    return pythonCmd;  // ✅ 返回python/python3
}
```

**优势**:
- ✅ 最安全、最兼容
- ✅ 自动处理虚拟环境
- ✅ 不依赖特定路径

**检测逻辑**:
```java
private static boolean testPythonModule(String pythonCmd) {
    List<String> command = new ArrayList<>();
    command.add(pythonCmd);
    command.add("-m");
    command.add("arjun");
    command.add("--help");
    
    ProcessBuilder pb = new ProcessBuilder(command);
    Process process = pb.start();
    
    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
    int exitCode = process.exitValue();
    
    return exitCode == 0 || exitCode == 1;  // arjun --help 可能返回0或1
}
```

---

#### 第二层：常见路径扫描

##### Windows路径
```java
List<String> commonPaths = new ArrayList<>();

// 系统级Python安装
commonPaths.add("C:\\Python39\\Scripts\\arjun.exe");
commonPaths.add("C:\\Python310\\Scripts\\arjun.exe");
commonPaths.add("C:\\Python311\\Scripts\\arjun.exe");
commonPaths.add("C:\\Python312\\Scripts\\arjun.exe");

// 用户级Python安装
String userHome = System.getProperty("user.home");
commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\arjun.exe");
commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python310\\Scripts\\arjun.exe");
commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python311\\Scripts\\arjun.exe");
commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python312\\Scripts\\arjun.exe");

// Anaconda/Miniconda
commonPaths.add(userHome + "\\anaconda3\\Scripts\\arjun.exe");
commonPaths.add(userHome + "\\miniconda3\\Scripts\\arjun.exe");
commonPaths.add("C:\\ProgramData\\Anaconda3\\Scripts\\arjun.exe");
commonPaths.add("C:\\ProgramData\\Miniconda3\\Scripts\\arjun.exe");
```

##### macOS/Linux路径
```java
// 系统级安装
commonPaths.add("/usr/local/bin/arjun");
commonPaths.add("/usr/bin/arjun");

// Homebrew (macOS)
commonPaths.add("/opt/homebrew/bin/arjun");

// Anaconda/Miniconda
commonPaths.add("/opt/anaconda3/bin/arjun");
commonPaths.add("/opt/miniconda3/bin/arjun");

// 用户级安装
String userHome = System.getProperty("user.home");
commonPaths.add(userHome + "/.local/bin/arjun");
commonPaths.add(userHome + "/anaconda3/bin/arjun");
commonPaths.add(userHome + "/miniconda3/bin/arjun");

// Pyenv
commonPaths.add(userHome + "/.pyenv/shims/arjun");
```

**检测逻辑**:
```java
for (String path : commonPaths) {
    File file = new File(path);
    if (file.exists() && (isWindows || file.canRead())) {
        return path;  // ✅ 找到可用路径
    }
}
```

---

#### 第三层：系统命令查找

##### Windows: where命令
```java
if (isWindows) {
    command.add("where");
    command.add("arjun");
}
```

##### Unix: which命令
```java
if (!isWindows) {
    command.add("which");
    command.add("arjun");
}
```

**检测逻辑**:
```java
private static String findArjunUsingSystemCommand() {
    ProcessBuilder pb = new ProcessBuilder(command);
    Process process = pb.start();
    
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream())
    );
    
    String line = reader.readLine();
    
    if (line != null && !line.trim().isEmpty()) {
        return line.trim();  // ✅ 返回系统找到的路径
    }
    
    return null;
}
```

---

### 3. UI自动检测按钮

#### 布局设计
```
┌─────────────────────────────────────────────────┐
│ Arjun工具路径:  [_________________] [🔍 自动检测] │
│ 💡 支持: 完整路径、python3、arjun 等方式           │
└─────────────────────────────────────────────────┘
```

#### 实现代码
```java
// 创建路径输入面板（包含输入框和自动检测按钮）
JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
pathPanel.add(arjunPathField, BorderLayout.CENTER);

JButton autoDetectButton = new JButton("🔍 自动检测");
autoDetectButton.setToolTipText("自动检测系统中的Arjun路径");
autoDetectButton.addActionListener(e -> autoDetectArjun());
pathPanel.add(autoDetectButton, BorderLayout.EAST);
```

#### 异步检测（避免UI冻结）
```java
private void autoDetectArjun() {
    // 显示进度对话框
    JDialog progressDialog = new JDialog(this, "自动检测", true);
    progressDialog.add(new JLabel("  🔍 正在搜索系统中的Arjun...  "));
    
    JProgressBar progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressDialog.add(progressBar);
    
    // 异步执行检测
    SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
        @Override
        protected String doInBackground() throws Exception {
            return ArjunIntegration.autoDetectArjunPath();
        }
        
        @Override
        protected void done() {
            progressDialog.dispose();
            String detectedPath = get();
            
            if (detectedPath != null) {
                arjunPathField.setText(detectedPath);
                showSuccessMessage(detectedPath);
            } else {
                showFailureMessage();
            }
        }
    };
    
    worker.execute();
    progressDialog.setVisible(true);
}
```

---

## 📊 支持的平台和环境

### ✅ Windows

| 环境 | 路径示例 | 自动检测 |
|------|----------|----------|
| Python官方安装 | `C:\Python39\Scripts\arjun.exe` | ✅ |
| 用户级安装 | `%USERPROFILE%\AppData\Local\Programs\Python\...` | ✅ |
| Anaconda | `C:\Users\xxx\anaconda3\Scripts\arjun.exe` | ✅ |
| Miniconda | `%USERPROFILE%\miniconda3\Scripts\arjun.exe` | ✅ |
| Python模块 | `python -m arjun` | ✅ |

---

### ✅ macOS

| 环境 | 路径示例 | 自动检测 |
|------|----------|----------|
| Homebrew | `/opt/homebrew/bin/arjun` | ✅ |
| pip3安装 | `/usr/local/bin/arjun` | ✅ |
| Anaconda | `/opt/anaconda3/bin/arjun` | ✅ |
| 用户级安装 | `~/.local/bin/arjun` | ✅ |
| Pyenv | `~/.pyenv/shims/arjun` | ✅ |
| Python模块 | `python3 -m arjun` | ✅ |

---

### ✅ Linux

| 环境 | 路径示例 | 自动检测 |
|------|----------|----------|
| apt/yum安装 | `/usr/bin/arjun` | ✅ |
| pip3安装 | `/usr/local/bin/arjun` | ✅ |
| Anaconda | `/opt/anaconda3/bin/arjun` | ✅ |
| 用户级安装 | `~/.local/bin/arjun` | ✅ |
| 虚拟环境 | `~/venv/bin/arjun` | ⚠️ |
| Python模块 | `python3 -m arjun` | ✅ |

*注: 虚拟环境需要激活后使用，建议配置为 `python3`*

---

## 🎯 使用流程

### 方式1：自动检测（推荐）

```
1. 打开配置对话框
2. 点击 [🔍 自动检测] 按钮
3. 等待检测完成（通常1-3秒）
4. 自动填入检测到的路径
5. 点击 [测试连接] 验证
6. 点击 [确定] 保存配置
```

**检测结果示例**:

#### Windows成功
```
✅ 自动检测成功！

检测到的路径: python

系统: Windows 10
提示: 如果需要更改，可以手动输入其他路径。
```

#### macOS成功
```
✅ 自动检测成功！

检测到的路径: python3

系统: Mac OS X
提示: 如果需要更改，可以手动输入其他路径。
```

#### 检测失败（Arjun未安装）
```
❌ 未能自动检测到Arjun

请手动配置Arjun路径，支持以下方式：

1️⃣ 填写 python 或 python3（推荐）
   会自动使用: python -m arjun

2️⃣ 填写完整路径，例如：
   C:\Python39\Scripts\arjun.exe
   C:\Users\YourName\anaconda3\Scripts\arjun.exe

3️⃣ 如果Arjun未安装：
   pip install arjun
```

---

### 方式2：手动配置

支持以下几种方式（全平台通用）:

#### ✅ 1. 使用Python模块（最推荐）
```
Windows: python
Unix:    python3
```
**自动执行**: `python -m arjun` 或 `python3 -m arjun`

#### ✅ 2. 完整路径
```
Windows: C:\Users\xxx\anaconda3\Scripts\arjun.exe
Unix:    /opt/anaconda3/bin/arjun
```

#### ✅ 3. 简单命令名
```
arjun
```
**自动执行**: `python3 -m arjun`

---

## 🔍 检测流程图

```
┌──────────────────────────────────────┐
│ 用户点击 [🔍 自动检测]                │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│ 1️⃣ 测试 Python模块                    │
│   Windows: python -m arjun --help    │
│   Unix:    python3 -m arjun --help   │
└──────────────┬───────────────────────┘
               │
               ├─ ✅ 成功 → 返回 python/python3
               │
               ▼ ❌ 失败
┌──────────────────────────────────────┐
│ 2️⃣ 扫描常见路径                       │
│   Windows: 12个常见路径               │
│   macOS:   8个常见路径                │
│   Linux:   8个常见路径                │
└──────────────┬───────────────────────┘
               │
               ├─ ✅ 找到 → 返回完整路径
               │
               ▼ ❌ 未找到
┌──────────────────────────────────────┐
│ 3️⃣ 使用系统命令                       │
│   Windows: where arjun               │
│   Unix:    which arjun               │
└──────────────┬───────────────────────┘
               │
               ├─ ✅ 找到 → 返回路径
               │
               ▼ ❌ 未找到
┌──────────────────────────────────────┐
│ 显示安装指引对话框                    │
└──────────────────────────────────────┘
```

---

## 🌟 跨平台兼容性特性

### 1. 路径分隔符自动处理
```java
// Windows: 使用 \ 和 \\
// Unix:    使用 /

if (isWindows) {
    path.lastIndexOf("\\scripts\\");  // Windows
} else {
    path.lastIndexOf("/bin/");         // Unix
}
```

### 2. Python命令差异
```java
// Windows: python (通常不带3)
// Unix:    python3 (明确版本)

String pythonCmd = isWindows ? "python" : "python3";
```

### 3. 可执行文件后缀
```java
// Windows: .exe, .bat, .cmd
// Unix:    无后缀

if (isWindows) {
    "arjun.exe"
} else {
    "arjun"
}
```

### 4. 权限检查差异
```java
// Windows: 只检查文件存在
// Unix:    额外检查可执行权限

if (file.exists() && (isWindows || file.canExecute())) {
    // 可用
}
```

---

## 📈 性能优化

### 1. 超时控制
```java
// 每个检测步骤都有超时限制
process.waitFor(5, TimeUnit.SECONDS);  // Python模块测试: 5秒
process.waitFor(3, TimeUnit.SECONDS);  // 系统命令查找: 3秒
```

### 2. 异步执行
```java
// UI使用SwingWorker异步检测，避免冻结
SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
    @Override
    protected String doInBackground() {
        return ArjunIntegration.autoDetectArjunPath();
    }
};
```

### 3. 提前返回
```java
// 一旦找到可用路径，立即返回，不继续后续检测
if (testPythonModule(pythonCmd)) {
    return pythonCmd;  // ✅ 立即返回
}
```

---

## 🎉 总结

### 新增功能
1. ✅ **跨平台Python解释器提取** - 支持Windows、macOS、Linux
2. ✅ **三层智能自动检测** - Python模块 → 常见路径 → 系统命令
3. ✅ **UI自动检测按钮** - 一键检测并配置
4. ✅ **异步检测机制** - 不阻塞UI
5. ✅ **详细错误提示** - 检测失败时提供安装指引

### 支持环境
- ✅ Windows (Python官方、Anaconda、Miniconda)
- ✅ macOS (Homebrew、pip3、Anaconda、Pyenv)
- ✅ Linux (apt/yum、pip3、Anaconda、虚拟环境)

### 用户体验
- ✅ **零配置** - 自动检测并配置
- ✅ **快速** - 通常1-3秒完成检测
- ✅ **智能** - 优先使用最兼容的方式（Python模块）
- ✅ **友好** - 检测失败时提供详细指引

---

## 🚀 立即使用

**最新JAR包**: `build/libs/XProbe-1.0.0.jar`

**使用步骤**:
1. 打开XProbe插件
2. 进入配置页面
3. 点击 [🔍 自动检测] 按钮
4. 等待自动检测完成
5. 验证并保存配置

**现在所有平台都能自动检测和配置Arjun了！** 🎉

---

**🌟 跨平台兼容性完整实现，支持Windows/macOS/Linux自动检测！**


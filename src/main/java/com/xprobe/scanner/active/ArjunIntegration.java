package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Arjun集成类，用于调用Arjun工具进行参数爆破
 * 
 * ✅ 完整重构版本 - 更强大、更可靠
 */
public class ArjunIntegration {
    private final MontoyaApi api;
    private final ExternalToolConfig config;
    private volatile String cachedPython3Path = null;  // ✅ 缓存 Python3 绝对路径
    private volatile String cachedWrapperPath = null;  // ✅ 缓存 wrapper 脚本路径
    
    public ArjunIntegration(MontoyaApi api, ExternalToolConfig config) {
        this.api = api;
        this.config = config;
    }
    
    /**
     * ✅ 创建 Arjun wrapper 脚本（绕过 macOS 安全限制）
     * 这个脚本使用系统 Python3 执行 Arjun，避免 shebang 和架构问题
     */
    private String createArjunWrapper(String arjunPath) {
        if (cachedWrapperPath != null) {
            File wrapper = new File(cachedWrapperPath);
            if (wrapper.exists() && wrapper.canExecute()) {
                return cachedWrapperPath;
            }
        }
        
        try {
            // 在系统临时目录创建 wrapper 脚本
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File wrapperFile = new File(tempDir, "arjun-wrapper-" + System.currentTimeMillis() + ".sh");
            
            // 写入wrapper脚本内容
            String python3 = findPython3AbsolutePath();
            String scriptContent = "#!/bin/bash\n" +
                                 "# Arjun Wrapper - Auto-generated\n" +
                                 "exec \"" + python3 + "\" \"" + arjunPath + "\" \"$@\"\n";
            
            try (FileWriter writer = new FileWriter(wrapperFile)) {
                writer.write(scriptContent);
            }
            
            // 设置执行权限
            wrapperFile.setExecutable(true, false);
            
            // 程序退出时删除
            wrapperFile.deleteOnExit();
            
            cachedWrapperPath = wrapperFile.getAbsolutePath();
            api.logging().raiseDebugEvent("✅ 创建 wrapper 脚本: " + cachedWrapperPath);
            
            return cachedWrapperPath;
        } catch (Exception e) {
            api.logging().raiseDebugEvent("⚠️ 创建 wrapper 脚本失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * ✅ 查找系统中 python3 的绝对路径
     * 策略：直接尝试常见路径，避免依赖 which/where 命令
     * 支持：macOS、Linux、Windows
     */
    private String findPython3AbsolutePath() {
        if (cachedPython3Path != null) {
            return cachedPython3Path;
        }
        
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        
        // ✅ 根据操作系统选择路径列表
        String[] commonPaths;
        
        if (isWindows) {
            // Windows 常见路径
            commonPaths = new String[]{
                "C:\\Python312\\python.exe",        // Python 3.12
                "C:\\Python311\\python.exe",        // Python 3.11
                "C:\\Python310\\python.exe",        // Python 3.10
                "C:\\Python39\\python.exe",         // Python 3.9
                "C:\\Python38\\python.exe",         // Python 3.8
                System.getenv("LOCALAPPDATA") + "\\Programs\\Python\\Python312\\python.exe",
                System.getenv("LOCALAPPDATA") + "\\Programs\\Python\\Python311\\python.exe",
                System.getenv("LOCALAPPDATA") + "\\Programs\\Python\\Python310\\python.exe",
                "C:\\ProgramData\\Anaconda3\\python.exe",
                System.getenv("USERPROFILE") + "\\Anaconda3\\python.exe",
                "python",                           // 回退：依赖 PATH
            };
        } else {
            // Unix/macOS 路径
            commonPaths = new String[]{
                "/usr/bin/python3",                 // 最常见（系统自带）
                "/usr/local/bin/python3",           // Homebrew (Intel Mac)
                "/opt/homebrew/bin/python3",        // Homebrew (Apple Silicon)
                "/opt/anaconda3/bin/python3",       // Anaconda
                "/usr/bin/python",                  // 某些系统只有 python
                "python3"                           // 回退：依赖 PATH
            };
        }
        
        // ✅ 遍历路径列表，找到第一个可用的
        for (String path : commonPaths) {
            // 跳过 null 路径（环境变量可能不存在）
            if (path == null || path.contains("null")) {
                continue;
            }
            
            // 相对路径（回退选项）
            if (path.equals("python3") || path.equals("python")) {
                cachedPython3Path = path;
                api.logging().raiseDebugEvent("⚠️ 使用相对路径: " + path + " (依赖系统PATH)");
                return cachedPython3Path;
            }
            
            // 检查绝对路径
            File pythonFile = new File(path);
            if (pythonFile.exists() && pythonFile.isFile()) {
                // Windows 不需要检查可执行权限
                if (isWindows || pythonFile.canExecute()) {
                    cachedPython3Path = path;
                    api.logging().raiseDebugEvent("✅ 找到 Python: " + cachedPython3Path);
                    return cachedPython3Path;
                }
            }
        }
        
        // 不应该到这里，但以防万一
        cachedPython3Path = isWindows ? "python" : "python3";
        api.logging().raiseDebugEvent("⚠️ 未找到 Python，使用回退: " + cachedPython3Path);
        return cachedPython3Path;
    }
    
    /**
     * 扫描URL查找隐藏参数（异步）
     * 
     * @param request HTTP请求对象
     * @param customDictionary 自定义参数字典（可选）
     * @return CompletableFuture<ArjunResult> 异步扫描结果
     */
    public CompletableFuture<ArjunResult> scan(HttpRequest request, Set<String> customDictionary) {
        return CompletableFuture.supplyAsync(() -> {
            String url = request.url();
            api.logging().raiseInfoEvent("开始Arjun扫描: " + url);
            
            String dictFile = null;
            try {
                // 创建自定义字典文件
                if (customDictionary != null && !customDictionary.isEmpty()) {
                    dictFile = createDictionary(customDictionary);
                }
                
                // 构建Arjun命令
                List<String> command = buildArjunCommand(request, dictFile);
                
                // 执行Arjun
                List<String> output = executeArjun(command);
                
                // 解析结果
                Set<String> foundParams = parseArjunOutput(output);
                
                api.logging().raiseInfoEvent(String.format(
                    "✅ Arjun扫描完成: %s (发现 %d 个参数)", 
                    url, foundParams.size()
                ));
                
                return ArjunResult.success(url, foundParams, output);
                
            } catch (Exception e) {
                String errorMsg = "Arjun扫描失败: " + e.getMessage();
                api.logging().raiseErrorEvent(errorMsg);
                return ArjunResult.error(errorMsg);
            } finally {
                // 清理临时文件
                if (dictFile != null) {
                    try {
                        new File(dictFile).delete();
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
            }
        });
    }
    
    /**
     * 测试Arjun连接
     * ✅ 使用多重回退策略，绕过 macOS 安全限制
     */
    public boolean testConnection() {
        String arjunPath = config.getArjunPath();
        if (arjunPath == null || arjunPath.trim().isEmpty()) {
            arjunPath = "arjun";
        }
        
        api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        api.logging().raiseInfoEvent("🔍 Arjun测试连接开始");
        api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        api.logging().raiseInfoEvent("📝 配置的Arjun路径: " + arjunPath);
        api.logging().raiseInfoEvent("🖥️  操作系统: " + System.getProperty("os.name"));
        
        // ✅ 多重回退策略
        File arjunFile = new File(arjunPath);
        boolean isScriptFile = arjunFile.exists() && arjunFile.isFile();
        
        if (isScriptFile) {
            // 策略1: 使用 wrapper 脚本（推荐，绕过 macOS 限制）
            api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            api.logging().raiseInfoEvent("🎯 策略1: 尝试 wrapper 脚本...");
            String wrapperPath = createArjunWrapper(arjunPath);
            if (wrapperPath != null) {
                boolean success = tryCommand(Arrays.asList(wrapperPath, "--help"), "Wrapper脚本");
                if (success) {
                    api.logging().raiseInfoEvent("✅ Arjun测试成功！（使用 wrapper 脚本）");
                    return true;
                }
            }
            
            // 策略2: 直接用系统 Python3
            api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            api.logging().raiseInfoEvent("🎯 策略2: 尝试系统 Python3...");
            String python3 = findPython3AbsolutePath();
            boolean success = tryCommand(Arrays.asList(python3, arjunPath, "--help"), "系统Python3");
            if (success) {
                api.logging().raiseInfoEvent("✅ Arjun测试成功！（使用系统 Python3）");
                return true;
            }
        } else if (arjunPath.equals("python") || arjunPath.equals("python3")) {
            // Python 模块模式
            String python3 = findPython3AbsolutePath();
            boolean success = tryCommand(Arrays.asList(python3, "-m", "arjun", "--help"), "Python模块");
            if (success) {
                api.logging().raiseInfoEvent("✅ Arjun测试成功！（Python模块模式）");
                return true;
            }
        } else {
            // 策略3: 系统命令
            api.logging().raiseInfoEvent("🎯 尝试系统命令...");
            boolean success = tryCommand(Arrays.asList(arjunPath, "--help"), "系统命令");
            if (success) {
                api.logging().raiseInfoEvent("✅ Arjun测试成功！（系统命令）");
                return true;
            }
        }
        
        api.logging().raiseErrorEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        api.logging().raiseErrorEvent("❌ 所有策略都失败，Arjun测试失败！");
        api.logging().raiseErrorEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return false;
    }
    
    /**
     * 尝试执行命令
     */
    private boolean tryCommand(List<String> command, String description) {
        try {
            api.logging().raiseInfoEvent("📌 " + description + ": " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 5) {
                output.append(line).append("\n");
                lineCount++;
            }
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                api.logging().raiseErrorEvent("   ❌ 超时");
                return false;
            }
            
            int exitCode = process.exitValue();
            api.logging().raiseInfoEvent("   退出码: " + exitCode);
            if (output.length() > 0) {
                api.logging().raiseInfoEvent("   输出: " + output.toString().substring(0, Math.min(80, output.length())));
            }
            
            if (exitCode == 0 || exitCode == 1) {
                api.logging().raiseInfoEvent("   ✅ 成功");
                return true;
            } else {
                api.logging().raiseErrorEvent("   ❌ 失败（退出码: " + exitCode + "）");
                return false;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("   ❌ 异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 构建Arjun命令
     */
    private List<String> buildArjunCommand(HttpRequest request, String dictFile) {
        List<String> command = new ArrayList<>();
        
        String url = request.url();
        String method = request.method();
        String contentType = getContentType(request);
        
        // Arjun方法映射
        String arjunMethod = mapMethod(method, contentType);
        
        // 构建headers（换行分隔）
        String headers = buildHeaders(request);
        
        // 提取原始请求的参数（用于 --include）
        String existingParams = extractExistingParameters(request);
        
        // 记录提取到的参数（方便调试）
        if (!existingParams.isEmpty()) {
            api.logging().raiseDebugEvent(String.format(
                "Arjun --include 参数: %s (共%d个)", 
                existingParams, existingParams.split(",").length
            ));
        } else {
            api.logging().raiseDebugEvent("Arjun: 原始请求无参数，仅探测新参数");
        }
        
        // ✅ 智能处理Arjun命令
        String arjunPath = config.getArjunPath();
        if (arjunPath == null || arjunPath.trim().isEmpty()) {
            arjunPath = "arjun";  // 默认值
        }
        
        // ✅ 使用 wrapper 策略（与 testConnection 保持一致）
        
        File arjunFile = new File(arjunPath);
        boolean isScriptFile = arjunFile.exists() && arjunFile.isFile();
        
        if (isScriptFile) {
            // 优先使用 wrapper 脚本（绕过 macOS 限制）
            String wrapperPath = createArjunWrapper(arjunPath);
            if (wrapperPath != null) {
                command.add(wrapperPath);
            } else {
                // 回退：直接用系统 Python3
                String python3 = findPython3AbsolutePath();
                command.add(python3);
                command.add(arjunPath);
            }
        } else if (arjunPath.equals("python") || arjunPath.equals("python3")) {
            // Python 模块模式
            String python3 = findPython3AbsolutePath();
            command.add(python3);
            command.add("-m");
            command.add("arjun");
        } else {
            // 系统命令
            command.add(arjunPath);
        }
        
        command.add("-u");
        command.add(url);
        
        // HTTP方法
        command.add("-m");
        command.add(arjunMethod);
        
        // 线程数
        command.add("-t");
        command.add(String.valueOf(config.getThreadCount()));
        
        // 超时
        command.add("-T");
        command.add(String.valueOf(config.getTimeout()));
        
        // 速率限制
        command.add("--rate-limit");
        command.add("9999");
        
        // Headers
        if (!headers.isEmpty()) {
            command.add("--headers");
            command.add(headers);
        }
        
        // 字典文件
        if (dictFile != null) {
            command.add("-w");
            command.add(dictFile);
        }
        
        // 保留原始参数
        if (!existingParams.isEmpty()) {
            command.add("--include");
            command.add(existingParams);
        }
        
        // 通过Burp代理
        if (config.isSendToBurp()) {
            command.add("--proxy-url");
            command.add(config.getBurpProxyAddress());
        }
        
        // 可选：JSON输出
        if (config.isEnableJsonOutput()) {
            command.add("-oJ");
        }
        
        // 可选：禁用重定向
        command.add("--disable-redirects");
        
        // 可选：安静模式
        if (!config.isEnableVerboseOutput()) {
            command.add("-q");
        }
        
        return command;
    }
    
    /**
     * 映射HTTP方法到Arjun支持的格式
     */
    private String mapMethod(String method, String contentType) {
        String upperMethod = method.toUpperCase();
        
        boolean isJson = contentType != null && contentType.toLowerCase().contains("json");
        boolean isXml = contentType != null && contentType.toLowerCase().contains("xml");
        
        switch (upperMethod) {
            case "POST":
            case "PUT":
            case "PATCH":
                if (isJson) {
                return "JSON";
                } else if (isXml) {
                return "XML";
            }
            return "POST";
            
            case "GET":
                return "GET";
            
            case "DELETE":
            case "HEAD":
            case "OPTIONS":
                return "GET";
            
            default:
                if (isJson) {
                    return "JSON";
                } else if (isXml) {
                    return "XML";
                }
                return "GET";
        }
    }
    
    /**
     * 构建headers字符串
     */
    private String buildHeaders(HttpRequest request) {
        StringBuilder headersBuilder = new StringBuilder();
        
        for (var header : request.headers()) {
            String name = header.name();
            if ("Content-Length".equalsIgnoreCase(name) || 
                "Host".equalsIgnoreCase(name)) {
                continue;
            }
            headersBuilder.append(name).append(": ").append(header.value()).append("\n");
        }
        
        headersBuilder.append("X-XProbe-Arjun: 1\n");
        
        return headersBuilder.toString().trim();
    }
    
    /**
     * 获取Content-Type
     */
    private String getContentType(HttpRequest request) {
        for (var header : request.headers()) {
            if ("Content-Type".equalsIgnoreCase(header.name())) {
                return header.value();
            }
        }
        return null;
    }
    
    /**
     * 提取原始请求中的所有参数名
     */
    private String extractExistingParameters(HttpRequest request) {
        Set<String> paramNames = new LinkedHashSet<>();
        
        try {
            var urlParams = request.parameters();
            for (var param : urlParams) {
                String name = param.name();
                if (name != null && !name.isEmpty()) {
                    paramNames.add(name);
                }
            }
            
            String contentType = getContentType(request);
            if (contentType != null && contentType.contains("application/json")) {
                String body = request.bodyToString();
                if (body != null && !body.isEmpty()) {
                    extractJsonFieldNames(body, paramNames);
                }
            }
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("提取参数时出错: " + e.getMessage());
        }
        
        return String.join(",", paramNames);
    }
    
    /**
     * 从JSON字符串中递归提取所有字段名
     */
    private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonBody);
            
            extractFieldNamesRecursive(rootNode, paramNames);
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("JSON解析失败，使用简单提取: " + e.getMessage());
            extractJsonFieldNamesSimple(jsonBody, paramNames);
        }
    }
    
    /**
     * 递归提取JSON字段名
     */
    private void extractFieldNamesRecursive(com.fasterxml.jackson.databind.JsonNode node, 
                                           Set<String> paramNames) {
        if (node.isObject()) {
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = 
                node.fields();
            
            while (fields.hasNext()) {
                java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
                String fieldName = field.getKey();
                com.fasterxml.jackson.databind.JsonNode fieldValue = field.getValue();
                
                paramNames.add(fieldName);
                
                if (fieldValue.isObject() || fieldValue.isArray()) {
                    extractFieldNamesRecursive(fieldValue, paramNames);
                }
            }
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                if (element.isObject() || element.isArray()) {
                    extractFieldNamesRecursive(element, paramNames);
                }
            }
        }
    }
    
    /**
     * 简单实现（降级方案）
     */
    private void extractJsonFieldNamesSimple(String jsonBody, Set<String> paramNames) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:");
            java.util.regex.Matcher matcher = pattern.matcher(jsonBody);
            
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                if (fieldName != null && !fieldName.isEmpty()) {
                    paramNames.add(fieldName);
                }
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("JSON字段提取失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建参数字典文件
     */
    private String createDictionary(Set<String> customParams) throws IOException {
        String dictFile = System.getProperty("java.io.tmpdir") + 
            "/xprobe_arjun_" + System.currentTimeMillis() + ".txt";
        
        Set<String> allParams = new LinkedHashSet<>();
        
        if (customParams != null) {
            allParams.addAll(customParams);
        }
        
        allParams.addAll(config.getCustomDictionary());
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(dictFile))) {
            for (String param : allParams) {
                writer.println(param);
            }
        }
        
        api.logging().raiseDebugEvent("创建Arjun字典文件: " + dictFile + 
            " (包含 " + allParams.size() + " 个参数)");
        
        return dictFile;
    }
    
    /**
     * 执行Arjun命令
     */
    private List<String> executeArjun(List<String> command) throws Exception {
        List<String> output = new ArrayList<>();
        Process process = null;
        
        try {
            api.logging().raiseDebugEvent("执行Arjun命令: " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
                String line;
                while ((line = reader.readLine()) != null) {
                output.add(line);
                if (config.isEnableVerboseOutput()) {
                    api.logging().raiseDebugEvent("Arjun: " + line);
                }
            }
            
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                throw new Exception("Arjun执行超时（5分钟）");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                api.logging().raiseErrorEvent("Arjun退出码: " + exitCode);
            }
            
            return output;
            
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
    
    /**
     * 解析Arjun输出
     */
    private Set<String> parseArjunOutput(List<String> output) {
        Set<String> foundParams = new LinkedHashSet<>();
        
        for (String line : output) {
            if (line.contains("Valid parameter found:") || 
                line.contains("Parameter discovered:")) {
                
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    String param = parts[1].trim();
                    if (!param.isEmpty()) {
                        foundParams.add(param);
                    }
                }
            } else if (line.contains("[+]")) {
                String cleaned = line.replaceAll("\\[\\+\\]", "").trim();
                if (!cleaned.isEmpty() && !cleaned.contains(" ")) {
                    foundParams.add(cleaned);
                }
            }
        }
        
        return foundParams;
    }
    
    /**
     * ✅ 判断文件是否是Python脚本并提取shebang解释器（跨平台）
     * 
     * 逻辑：
     * 1. Windows: .exe/.bat 文件直接执行，.py 文件需要Python
     * 2. Unix: 检查shebang，无扩展名文件检查是否是脚本
     * 
     * @param filePath 文件路径
     * @return String数组 [isPythonScript, shebangInterpreter]
     */
    private String[] checkPythonScriptAndGetInterpreter(String filePath) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            File file = new File(filePath);
            
            if (!file.exists()) {
                api.logging().raiseDebugEvent("文件不存在: " + filePath);
                return new String[]{null, null};
            }
            
            String lowerPath = filePath.toLowerCase();
            
            // ✅ Windows特殊处理
            if (isWindows) {
                // Windows: .exe/.bat/.cmd 可以直接执行
                if (lowerPath.endsWith(".exe") || lowerPath.endsWith(".bat") || lowerPath.endsWith(".cmd")) {
                    api.logging().raiseDebugEvent("Windows可执行文件，直接执行: " + filePath);
                    return new String[]{null, null};  // 不是Python脚本
                }
                
                // Windows: .py 文件需要Python解释器
                if (lowerPath.endsWith(".py")) {
                    api.logging().raiseDebugEvent("Windows Python脚本，需要解释器: " + filePath);
                    return new String[]{"true", null};  // 是Python脚本，但没有shebang
                }
                
                // Windows: Scripts目录下的无扩展名文件通常是包装器脚本
                if (lowerPath.contains("\\scripts\\") && !lowerPath.contains(".")) {
                    api.logging().raiseDebugEvent("Windows Scripts目录下的文件，尝试直接执行: " + filePath);
                    return new String[]{null, null};
                }
                
                api.logging().raiseDebugEvent("Windows未知文件类型，尝试直接执行: " + filePath);
                return new String[]{null, null};
            }
            
            // ✅ Unix/Linux/macOS 处理
            if (!file.canRead()) {
                api.logging().raiseDebugEvent("文件无读取权限: " + filePath);
                return new String[]{null, null};
            }
            
            // Unix: 先检查扩展名
            if (lowerPath.endsWith(".py")) {
                api.logging().raiseDebugEvent("Unix Python脚本(.py): " + filePath);
                return new String[]{"true", null};
            }
            
            // Unix: 读取文件第一行检查shebang
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String firstLine = reader.readLine();
                if (firstLine != null && firstLine.startsWith("#!") && firstLine.contains("python")) {
                    // 提取shebang中的解释器路径
                    String interpreter = firstLine.substring(2).trim(); // 去掉 #!
                    
                    // 处理带参数的情况，如 #!/usr/bin/env python
                    String[] parts = interpreter.split("\\s+");
                    if (parts.length > 0) {
                        String interpreterPath = parts[0];
                        
                        // 如果是 /usr/bin/env python，提取实际的python命令
                        if (interpreterPath.endsWith("/env") && parts.length > 1) {
                            interpreterPath = parts[1]; // python3 或 python
                        }
                        
                        api.logging().raiseDebugEvent("从shebang提取解释器: " + interpreterPath);
                        return new String[]{"true", interpreterPath};
                    }
                }
            }
            
            // Unix: 如果没有shebang但路径包含python/anaconda，可能是脚本
            if (lowerPath.contains("anaconda/bin/") || lowerPath.contains("python")) {
                api.logging().raiseDebugEvent("Unix可能的Python环境路径: " + filePath);
                // 尝试直接执行（可能有执行权限）
                if (file.canExecute()) {
                    api.logging().raiseDebugEvent("文件有执行权限，尝试直接执行");
                    return new String[]{null, null};
                }
                // 否则当作Python脚本
                api.logging().raiseDebugEvent("文件无执行权限，当作Python脚本处理");
                return new String[]{"true", null};
            }
            
            // Unix: 默认认为可以直接执行
            api.logging().raiseDebugEvent("Unix默认直接执行: " + filePath);
            return new String[]{null, null};
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("检测Python脚本时出错: " + e.getMessage());
            // 出错时保守处理：.py 文件认为是脚本，其他直接执行
            if (filePath.toLowerCase().endsWith(".py")) {
                return new String[]{"true", null};
            }
            return new String[]{null, null};
        }
    }
    
    /**
     * ✅ 从Arjun路径中智能提取Python解释器（通用版本）
     * 
     * 通用策略（不依赖特定环境名称）：
     * 1. Windows: 从Scripts目录向上查找python.exe
     * 2. Unix: 从bin目录查找python/python3
     * 3. 兜底：使用系统默认Python
     */
    private String extractPythonInterpreter(String arjunPath) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            File arjunFile = new File(arjunPath);
            
            if (isWindows) {
                // ========== Windows 通用策略 ==========
                // 策略：从arjun所在目录向上查找，直到找到python.exe
                
                File currentDir = arjunFile.getParentFile();
                
                // 1️⃣ 如果arjun在Scripts目录下，向上一级查找python.exe
                if (currentDir != null && "Scripts".equalsIgnoreCase(currentDir.getName())) {
                    File pythonRoot = currentDir.getParentFile();
                    if (pythonRoot != null) {
                        File pythonExe = new File(pythonRoot, "python.exe");
                        if (pythonExe.exists()) {
                            api.logging().raiseDebugEvent("✅ 找到Python解释器 (Scripts目录): " + pythonExe.getAbsolutePath());
                            return pythonExe.getAbsolutePath();
                        }
                    }
                }
                
                // 2️⃣ 在当前目录及其父目录中查找python.exe（最多向上3级）
                File searchDir = currentDir;
                for (int i = 0; i < 3 && searchDir != null; i++) {
                    File pythonExe = new File(searchDir, "python.exe");
                    if (pythonExe.exists()) {
                        api.logging().raiseDebugEvent("✅ 找到Python解释器 (向上" + i + "级): " + pythonExe.getAbsolutePath());
                        return pythonExe.getAbsolutePath();
                    }
                    searchDir = searchDir.getParentFile();
                }
                
                // 3️⃣ 尝试在同目录查找python3.exe
                if (currentDir != null) {
                    File python3Exe = new File(currentDir, "python3.exe");
                    if (python3Exe.exists()) {
                        api.logging().raiseDebugEvent("✅ 找到Python解释器 (同目录): " + python3Exe.getAbsolutePath());
                        return python3Exe.getAbsolutePath();
                    }
                }
                
                // 4️⃣ 兜底：使用系统PATH中的python
                api.logging().raiseDebugEvent("⚠️ 未在本地找到Python，使用系统默认: python");
                return "python";
                
            } else {
                // ========== Unix/Linux/macOS 通用策略 ==========
                // 策略：从arjun所在目录查找python/python3，并验证可执行性
                
                File binDir = arjunFile.getParentFile();
                List<String> candidatePaths = new ArrayList<>();
                
                if (binDir != null) {
                    // 1️⃣ 收集候选的Python解释器路径
                    candidatePaths.add(new File(binDir, "python3").getAbsolutePath());  // 优先python3
                    candidatePaths.add(new File(binDir, "python").getAbsolutePath());
                    candidatePaths.add(new File(binDir, "python3.11").getAbsolutePath());
                    candidatePaths.add(new File(binDir, "python3.10").getAbsolutePath());
                    candidatePaths.add(new File(binDir, "python3.9").getAbsolutePath());
                    
                    // 2️⃣ 向上查找环境根目录
                    File envRoot = binDir.getParentFile();
                    if (envRoot != null) {
                        File envBin = new File(envRoot, "bin");
                        candidatePaths.add(new File(envBin, "python3").getAbsolutePath());
                        candidatePaths.add(new File(envBin, "python").getAbsolutePath());
                    }
                }
                
                // 3️⃣ 验证每个候选路径（存在、可执行、真实可用）
                for (String candidatePath : candidatePaths) {
                    File pythonFile = new File(candidatePath);
                    
                    api.logging().raiseDebugEvent("🔍 检查Python候选: " + candidatePath);
                    
                    if (!pythonFile.exists()) {
                        api.logging().raiseDebugEvent("   ✗ 文件不存在");
                        continue;
                    }
                    
                    if (!pythonFile.canExecute()) {
                        api.logging().raiseDebugEvent("   ✗ 文件不可执行");
                        continue;
                    }
                    
                    // ✅ 额外验证：尝试执行 python --version 确保真实可用
                    if (verifyPythonExecutable(candidatePath)) {
                        api.logging().raiseDebugEvent("   ✓ Python解释器验证成功: " + candidatePath);
                        return candidatePath;
                    } else {
                        api.logging().raiseDebugEvent("   ✗ Python解释器执行失败");
                    }
                }
                
                // 4️⃣ 如果本地都不可用，尝试系统PATH中的python3
                api.logging().raiseDebugEvent("⚠️ 未在本地找到可用Python，验证系统默认 python3");
                if (verifyPythonExecutable("python3")) {
                    api.logging().raiseDebugEvent("✓ 使用系统默认: python3");
                    return "python3";
                }
                
                // 5️⃣ 最后尝试 python
                api.logging().raiseDebugEvent("⚠️ python3 不可用，尝试 python");
                if (verifyPythonExecutable("python")) {
                    api.logging().raiseDebugEvent("✓ 使用系统默认: python");
                    return "python";
                }
                
                // 6️⃣ 彻底兜底：返回 python3（让它在实际执行时报错）
                api.logging().raiseErrorEvent("❌ 未找到任何可用的Python解释器！");
                return "python3";
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("提取Python解释器失败: " + e.getMessage());
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            return isWindows ? "python" : "python3";
        }
    }
    
    /**
     * ✅ 验证Python解释器是否真实可用
     * 通过执行 python --version 来验证
     */
    private boolean verifyPythonExecutable(String pythonPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonPath);
            command.add("--version");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            return exitCode == 0;
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("   验证Python失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ✅ 自动检测系统中的Arjun路径
     */
    public static String autoDetectArjunPath() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        // 1️⃣ 最优先：尝试 python -m arjun
        String pythonCmd = isWindows ? "python" : "python3";
        if (testPythonModule(pythonCmd)) {
            return pythonCmd;
        }
        
        // 2️⃣ 检查常见路径
        List<String> commonPaths = new ArrayList<>();
        
        if (isWindows) {
            String userHome = System.getProperty("user.home");
            commonPaths.add("C:\\Python39\\Scripts\\arjun.exe");
            commonPaths.add("C:\\Python310\\Scripts\\arjun.exe");
            commonPaths.add("C:\\Python311\\Scripts\\arjun.exe");
            commonPaths.add("C:\\Python312\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python310\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python311\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\AppData\\Local\\Programs\\Python\\Python312\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\anaconda3\\Scripts\\arjun.exe");
            commonPaths.add(userHome + "\\miniconda3\\Scripts\\arjun.exe");
            commonPaths.add("C:\\ProgramData\\Anaconda3\\Scripts\\arjun.exe");
            commonPaths.add("C:\\ProgramData\\Miniconda3\\Scripts\\arjun.exe");
        } else {
            String userHome = System.getProperty("user.home");
            commonPaths.add("/usr/local/bin/arjun");
            commonPaths.add("/usr/bin/arjun");
            commonPaths.add("/opt/homebrew/bin/arjun");
            commonPaths.add("/opt/anaconda3/bin/arjun");
            commonPaths.add("/opt/miniconda3/bin/arjun");
            commonPaths.add(userHome + "/.local/bin/arjun");
            commonPaths.add(userHome + "/anaconda3/bin/arjun");
            commonPaths.add(userHome + "/miniconda3/bin/arjun");
            commonPaths.add(userHome + "/.pyenv/shims/arjun");
        }
        
        for (String path : commonPaths) {
            File file = new File(path);
            if (file.exists() && (isWindows || file.canRead())) {
                return path;
            }
        }
        
        // 3️⃣ 尝试使用系统命令查找
        String foundPath = findArjunUsingSystemCommand();
        if (foundPath != null) {
            return foundPath;
        }
        
        return null;
    }
    
    /**
     * ✅ 测试Python模块是否可用
     */
    private static boolean testPythonModule(String pythonCmd) {
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonCmd);
            command.add("-m");
            command.add("arjun");
            command.add("--help");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            return exitCode == 0 || exitCode == 1;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * ✅ 使用系统命令查找Arjun
     */
    private static String findArjunUsingSystemCommand() {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            List<String> command = new ArrayList<>();
            
            if (isWindows) {
                command.add("where");
                command.add("arjun");
            } else {
                command.add("which");
                command.add("arjun");
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line = reader.readLine();
            process.waitFor(3, TimeUnit.SECONDS);
            
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
            
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }
    
    /**
     * Arjun扫描结果
     */
    public static class ArjunResult {
        private final boolean success;
        private final String url;
        private final Set<String> foundParameters;
        private final List<String> output;
        private final String errorMessage;
        
        private ArjunResult(boolean success, String url, Set<String> foundParameters, 
                           List<String> output, String errorMessage) {
            this.success = success;
            this.url = url;
            this.foundParameters = foundParameters != null ? foundParameters : new LinkedHashSet<>();
            this.output = output != null ? output : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
        
        public static ArjunResult success(String url, Set<String> foundParameters, List<String> output) {
            return new ArjunResult(true, url, foundParameters, output, null);
        }
        
        public static ArjunResult error(String errorMessage) {
            return new ArjunResult(false, null, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getUrl() { return url; }
        public Set<String> getFoundParameters() { return new LinkedHashSet<>(foundParameters); }
        public List<String> getOutput() { return new ArrayList<>(output); }
        public String getErrorMessage() { return errorMessage; }
        
        @Override
        public String toString() {
            if (success) {
                return "ArjunResult{url='" + url + "', foundParameters=" + foundParameters.size() + "}";
            } else {
                return "ArjunResult{error='" + errorMessage + "'}";
            }
        }
    }
}

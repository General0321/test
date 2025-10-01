package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Arjun参数探测工具集成
 * 
 * 核心特性：
 * 1. 使用 -oB 参数将探测结果直接发送到Burp代理
 * 2. 自动继承原始请求的headers和方法
 * 3. 支持主域名级别的参数字典管理
 * 4. 🔴 使用 --include 保留原始请求的参数（认证、会话等）
 * 5. 🔴 使用 -w 指定测试参数字典（新增探测参数）
 * 
 * 参数合并机制：
 * - 原始参数（--include）：在每次探测时保持不变
 * - 测试参数（-w）：逐个测试，寻找隐藏参数
 * - 最终请求 = 原始参数 + 当前测试参数
 */
public class ArjunIntegration {
    private final MontoyaApi api;
    private final ExternalToolConfig config;
    
    public ArjunIntegration(MontoyaApi api, ExternalToolConfig config) {
        this.api = api;
        this.config = config;
    }
    
    /**
     * 对单个URL执行Arjun探测
     * 
     * @param request 原始HTTP请求
     * @param customParams 自定义参数字典（会合并到默认字典）
     * @return 异步执行结果
     */
    public CompletableFuture<ArjunResult> scan(HttpRequest request, Set<String> customParams) {
        return CompletableFuture.supplyAsync(() -> {
            String dictFile = null;
            
            try {
                String arjunPath = getArjunPath();
                if (arjunPath == null) {
                    return ArjunResult.error("未找到arjun工具，请确保arjun已安装");
                }
                
                // 创建临时字典文件
                dictFile = createDictionary(customParams);
                
                // 构建Arjun命令
                List<String> command = buildArjunCommand(request, dictFile);
                
                // 执行Arjun
                ArjunResult result = executeArjun(command, request.url());
                
                return result;
                
            } catch (Exception e) {
                api.logging().raiseErrorEvent("Arjun扫描失败: " + e.getMessage());
                return ArjunResult.error(e.getMessage());
            } finally {
                // ✅ 确保临时文件被清理（即使发生异常）
                if (dictFile != null) {
                    cleanupTempFile(dictFile);
                }
            }
        });
    }
    
    /**
     * 批量扫描多个URL
     */
    public CompletableFuture<List<ArjunResult>> scanBatch(List<HttpRequest> requests, Set<String> customParams) {
        List<CompletableFuture<ArjunResult>> futures = new ArrayList<>();
        
        for (HttpRequest request : requests) {
            futures.add(scan(request, customParams));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
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
        
        // 基础命令
        command.add(config.getArjunPath());
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
        
        // 🔴 关键：保留原始请求的参数（--include）
        // 这些参数会在每次探测时保持不变，确保认证、会话等参数不丢失
        if (!existingParams.isEmpty()) {
            command.add("--include");
            command.add(existingParams);
        }
        
        // 🔴 测试参数字典（-w）
        // 这些参数会被逐个测试，寻找隐藏参数
        command.add("-w");
        command.add(dictFile);
        
        // 最重要：输出到Burp代理（探测结果会自动进入被动扫描）
        if (config.isSendToBurp()) {
            command.add("-oB");
            command.add(config.getBurpProxyAddress());
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
     * 
     * ✅ 改进点：
     * 1. 支持PUT、PATCH、DELETE等RESTful方法
     * 2. 根据方法和Content-Type智能映射
     * 3. 提供未知方法的降级处理
     */
    private String mapMethod(String method, String contentType) {
        // 标准化方法名
        String upperMethod = method.toUpperCase();
        
        // 检查Content-Type
        boolean isJson = contentType != null && contentType.toLowerCase().contains("json");
        boolean isXml = contentType != null && contentType.toLowerCase().contains("xml");
        
        // 根据方法和Content-Type映射
        switch (upperMethod) {
            case "POST":
            case "PUT":
            case "PATCH":
                // POST/PUT/PATCH支持不同的Content-Type
                if (isJson) {
                    return "JSON";
                } else if (isXml) {
                    return "XML";
                }
                return "POST"; // 默认表单
            
            case "GET":
                return "GET";
            
            case "DELETE":
                // DELETE通常用GET方式传参（URL参数）
                return "GET";
            
            case "HEAD":
            case "OPTIONS":
                // HEAD/OPTIONS使用GET方式
                return "GET";
            
            default:
                // 未知方法，尝试根据Content-Type判断
                api.logging().raiseDebugEvent("未知HTTP方法: " + method + ", 尝试智能映射");
                if (isJson) {
                    return "JSON";
                } else if (isXml) {
                    return "XML";
                }
                // 默认使用GET
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
            // 跳过由Arjun自动处理的headers
            if ("Content-Length".equalsIgnoreCase(name) || 
                "Host".equalsIgnoreCase(name)) {
                continue;
            }
            headersBuilder.append(name).append(": ").append(header.value()).append("\n");
        }
        
        // 添加标记头，用于识别Arjun流量
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
     * 提取原始请求中的所有参数名（用于 --include）
     * 
     * 作用：
     * 1. 保留认证参数（token, session_id等）
     * 2. 保留业务参数（user_id, order_id等）
     * 3. 确保Arjun探测时这些参数始终存在
     * 
     * 格式：逗号分隔的参数名列表，例如 "user_id,token,page"
     */
    private String extractExistingParameters(HttpRequest request) {
        Set<String> paramNames = new LinkedHashSet<>();
        
        try {
            // 1. 提取URL参数（Query String）
            var urlParams = request.parameters();
            for (var param : urlParams) {
                String name = param.name();
                if (name != null && !name.isEmpty()) {
                    paramNames.add(name);
                }
            }
            
            // 2. 提取Body参数（仅针对POST表单）
            String contentType = getContentType(request);
            if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                // Body参数已经包含在 request.parameters() 中
                // Montoya API会自动解析
            }
            
            // 3. 提取JSON字段（如果是JSON请求）
            if (contentType != null && contentType.contains("application/json")) {
                String body = request.bodyToString();
                if (body != null && !body.isEmpty()) {
                    extractJsonFieldNames(body, paramNames);
                }
            }
            
            // 4. 提取Cookie参数（可选，根据需要）
            // Cookie通常不需要加入--include，因为会通过headers传递
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("提取参数时出错: " + e.getMessage());
        }
        
        // 转换为逗号分隔的字符串
        return String.join(",", paramNames);
    }
    
    /**
     * 从JSON字符串中递归提取所有字段名
     * 
     * ✅ 改进点：
     * 1. 使用Jackson库递归解析JSON
     * 2. 支持嵌套对象
     * 3. 支持数组
     * 4. 降级到简单实现如果JSON解析失败
     * 
     * 示例：
     * {
     *   "user": {
     *     "id": 123,
     *     "name": "test"
     *   }
     * }
     * → user, id, name
     */
    private void extractJsonFieldNames(String jsonBody, Set<String> paramNames) {
        try {
            // 使用Jackson解析JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonBody);
            
            // 递归提取字段名
            extractFieldNamesRecursive(rootNode, paramNames);
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("JSON解析失败，使用简单提取: " + e.getMessage());
            // 降级到简单实现
            extractJsonFieldNamesSimple(jsonBody, paramNames);
        }
    }
    
    /**
     * 递归提取JSON字段名
     * 
     * @param node 当前节点
     * @param paramNames 参数名集合
     */
    private void extractFieldNamesRecursive(com.fasterxml.jackson.databind.JsonNode node, 
                                           Set<String> paramNames) {
        if (node.isObject()) {
            // 对象节点：遍历所有字段
            java.util.Iterator<java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = 
                node.fields();
            
            while (fields.hasNext()) {
                java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
                String fieldName = field.getKey();
                com.fasterxml.jackson.databind.JsonNode fieldValue = field.getValue();
                
                // 添加当前字段名
                paramNames.add(fieldName);
                
                // 递归处理子节点
                if (fieldValue.isObject() || fieldValue.isArray()) {
                    extractFieldNamesRecursive(fieldValue, paramNames);
                }
            }
        } else if (node.isArray()) {
            // 数组节点：遍历所有元素
            for (com.fasterxml.jackson.databind.JsonNode element : node) {
                if (element.isObject() || element.isArray()) {
                    extractFieldNamesRecursive(element, paramNames);
                }
            }
        }
        // 对于基本类型（字符串、数字等），不做处理
    }
    
    /**
     * 简单实现（降级方案，当Jackson解析失败时使用）
     * 
     * 使用正则表达式提取字段名，不支持嵌套
     */
    private void extractJsonFieldNamesSimple(String jsonBody, Set<String> paramNames) {
        try {
            // 匹配模式: "字段名":\s*值
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
        
        // 1. 自定义参数（优先级最高）
        if (customParams != null) {
            allParams.addAll(customParams);
        }
        
        // 2. 配置中的自定义字典
        allParams.addAll(config.getCustomDictionary());
        
        // 写入文件
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
     * 
     * ✅ 改进点：
     * 1. 添加超时机制（5分钟）
     * 2. finally块确保进程清理
     * 3. 合并错误输出到标准输出，避免阻塞
     */
    private ArjunResult executeArjun(List<String> command, String url) {
        Process process = null;
        
        try {
            api.logging().raiseInfoEvent("执行Arjun: " + url);
            
            // 创建进程，合并错误输出到标准输出（避免进程阻塞）
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            
            // ✅ 使用超时等待（5分钟）
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            
            if (!finished) {
                // 超时，强制终止进程
                api.logging().raiseErrorEvent("Arjun执行超时: " + url);
                process.destroyForcibly();
                return ArjunResult.error("Arjun执行超时（超过5分钟）");
            }
            
            // 读取输出（进程已结束，不会阻塞）
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                    api.logging().raiseDebugEvent("Arjun输出: " + line);
                }
            }
            
            // 获取退出码
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                // 解析发现的参数
                Set<String> foundParams = parseArjunOutput(outputLines);
                
                api.logging().raiseInfoEvent("Arjun扫描完成: " + url + 
                    " - 发现 " + foundParams.size() + " 个参数");
                
                return ArjunResult.success(url, foundParams, outputLines);
            } else {
                String errorMsg = String.join("\n", outputLines);
                api.logging().raiseErrorEvent("Arjun执行失败 (退出码 " + exitCode + "): " + errorMsg);
                return ArjunResult.error("Arjun执行失败: " + errorMsg);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            api.logging().raiseErrorEvent("Arjun执行被中断: " + e.getMessage());
            return ArjunResult.error("Arjun执行被中断");
        } catch (Exception e) {
            api.logging().raiseErrorEvent("执行Arjun时出错: " + e.getMessage());
            return ArjunResult.error(e.getMessage());
        } finally {
            // ✅ 确保进程被清理
            if (process != null && process.isAlive()) {
                api.logging().raiseDebugEvent("强制终止Arjun进程");
                process.destroyForcibly();
                try {
                    // 等待进程真正结束（最多5秒）
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    /**
     * 解析Arjun输出，提取发现的参数
     */
    private Set<String> parseArjunOutput(List<String> outputLines) {
        Set<String> foundParams = new LinkedHashSet<>();
        
        // Arjun的输出格式通常是：
        // [FOUND] parameter_name
        // 或者在详细模式下会有更多信息
        
        for (String line : outputLines) {
            if (line.contains("[FOUND]") || line.contains("Valid parameter")) {
                // 提取参数名
                String[] parts = line.split("\\s+");
                for (String part : parts) {
                    if (!part.isEmpty() && 
                        !part.equals("[FOUND]") && 
                        !part.equals("Valid") && 
                        !part.equals("parameter")) {
                        foundParams.add(part);
                    }
                }
            }
        }
        
        return foundParams;
    }
    
    /**
     * 获取Arjun路径
     */
    private String getArjunPath() {
        // 首先检查配置中的路径
        if (config.getArjunPath() != null && isExecutable(config.getArjunPath())) {
            return config.getArjunPath();
        }
        
        // 检查常见路径
        String[] possiblePaths = {
            "arjun",
            "/usr/local/bin/arjun",
            "/opt/homebrew/bin/arjun",
            "/usr/bin/arjun",
            System.getProperty("user.home") + "/.local/bin/arjun"
        };
        
        for (String path : possiblePaths) {
            if (isExecutable(path)) {
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * 检查文件是否可执行
     */
    private boolean isExecutable(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "-h");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0 || exitCode == 1;  // -h 可能返回0或1
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 清理临时文件
     * 
     * ✅ 改进点：
     * 1. 检查删除是否成功
     * 2. 如果删除失败，标记为退出时删除
     */
    private void cleanupTempFile(String filePath) {
        if (filePath == null) {
            return;
        }
        
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    // 删除失败，标记为退出时删除
                    api.logging().raiseDebugEvent("临时文件删除失败，标记为退出时删除: " + filePath);
                    file.deleteOnExit();
                } else {
                    api.logging().raiseDebugEvent("临时文件已清理: " + filePath);
                }
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("清理临时文件失败: " + e.getMessage());
        }
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

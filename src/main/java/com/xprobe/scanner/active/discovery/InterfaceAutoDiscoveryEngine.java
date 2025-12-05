package com.xprobe.scanner.active.discovery;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动接口采集引擎（精确解析 HTML/JS/JSON 中的潜在接口路径）
 *
 * 目标：从响应/静态资源中提取“相对路径接口”，仅返回以 '/' 开头的路径，
 * 并做多层过滤（去重、静态资源、可疑路径、越界路径等）。
 */
public class InterfaceAutoDiscoveryEngine {

    // 常见静态资源后缀（避免误收集）
    private static final String[] STATIC_EXTS = new String[]{
        ".css", ".js", ".map", ".png", ".jpg", ".jpeg", ".gif", ".svg",
        ".ico", ".woff", ".woff2", ".ttf", ".eot", ".otf", ".mp4", ".webm", ".mp3"
    };

    // HTML 提取：href/src/action
    private static final Pattern HTML_ATTR_PATTERN = Pattern.compile(
        "(?i)(?:href|src|action)\\s*=\\s*\\\"([^\\\"]+)\\\"|(?:href|src|action)\\s*=\\s*'([^']+)'"
    );

    // JS 提取：fetch / axios / $.ajax / xhr.open / location / URL literals
    private static final Pattern JS_URL_PATTERN = Pattern.compile(
        "(?i)(?:fetch|axios\\.(?:get|post|put|delete)|\\$\\.ajax)\\s*\"?\'?\\(\\s*['\"]([^'\"]+)['\"][^)]*\\)"
        + "|XMLHttpRequest\\s*\\(|xhr\\.open\\s*\\(\\s*['\"][A-Z]+['\"],\\s*['\"]([^'\"]+)['\"]"
        + "|location\\.(?:href|assign|replace)\\s*=\\s*['\"]([^'\"]+)['\"]"
    );

    // JSON/通用：匹配字符串字面量中的以 / 开头的路径
    private static final Pattern GENERIC_PATH_PATTERN = Pattern.compile(
        "['\"][/]([A-Za-z0-9_\\-./]{2,})['\"]"
    );

    /**
     * 从任意文本中提取相对路径（以 '/' 开头），并做多层过滤。
     */
    public static Set<String> extractRelativePaths(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return result;

        // HTML attributes
        Matcher m1 = HTML_ATTR_PATTERN.matcher(text);
        while (m1.find()) {
            addCandidate(result, firstNonNull(m1.group(1), m1.group(2)));
        }

        // JS url patterns
        Matcher m2 = JS_URL_PATTERN.matcher(text);
        while (m2.find()) {
            String url = firstNonNull(m2.group(1), m2.group(2), m2.group(3));
            addCandidate(result, url);
        }

        // Generic JSON-like strings
        Matcher m3 = GENERIC_PATH_PATTERN.matcher(text);
        while (m3.find()) {
            addCandidate(result, "/" + m3.group(1));
        }

        // 过滤静态资源、越界路径等
        result.removeIf(InterfaceAutoDiscoveryEngine::isNoisePath);
        return result;
    }

    private static void addCandidate(Set<String> set, String url) {
        if (url == null || url.isEmpty()) return;
        // 只接受相对路径
        if (url.startsWith("/")) {
            // 去掉重复的多个斜杠 //
            String norm = url.replaceAll("/{2,}", "/");
            set.add(norm);
        }
    }

    private static boolean isNoisePath(String path) {
        if (path == null || path.isEmpty()) return true;
        // 静态资源
        String lower = path.toLowerCase(Locale.ROOT);
        for (String ext : STATIC_EXTS) {
            if (lower.endsWith(ext)) return true;
        }
        // 可疑：包含 .. 越界
        if (path.contains("..")) return true;
        // 太短/明显目录占位
        if (path.length() < 3) return true;
        // 常见纯根路径
        if ("/".equals(path.trim())) return true;
        return false;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... arr) {
        for (T t : arr) if (t != null) return t;
        return null;
    }
}


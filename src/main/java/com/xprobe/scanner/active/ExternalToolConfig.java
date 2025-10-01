package com.xprobe.scanner.active;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部工具配置
 */
public class ExternalToolConfig {
    private String arjunPath;  // 改用Arjun
    private String burpProxyAddress;  // Burp代理地址
    private int threadCount;
    private int timeout;
    private List<String> customDictionary;
    private boolean enableJsonOutput;
    private boolean enableVerboseOutput;
    private boolean sendToBurp;  // 是否使用-oB发送到Burp

    public ExternalToolConfig() {
        this.arjunPath = "arjun";  // 默认在PATH中查找
        this.burpProxyAddress = "127.0.0.1:8080";  // 默认Burp代理地址
        this.threadCount = 5;  // Arjun默认线程数
        this.timeout = 15;  // Arjun默认超时
        this.customDictionary = new ArrayList<>();
        this.enableJsonOutput = false;  // Arjun使用-oB时通常不需要JSON输出
        this.enableVerboseOutput = false;
        this.sendToBurp = true;  // 默认启用发送到Burp
        
        // 添加默认的自定义字典
        addDefaultCustomDictionary();
    }

    private void addDefaultCustomDictionary() {
        String[] defaultParams = {
            "api_key", "token", "access_token", "auth", "authorization",
            "callback", "jsonp", "format", "output", "response",
            "page", "limit", "offset", "sort", "order",
            "filter", "search", "query", "q", "keyword",
            "id", "user", "name", "email", "phone",
            "date", "time", "timestamp", "version", "v",
            "debug", "test", "admin", "root", "system"
        };
        
        for (String param : defaultParams) {
            customDictionary.add(param);
        }
    }

    public String getArjunPath() {
        return arjunPath;
    }

    public void setArjunPath(String arjunPath) {
        this.arjunPath = arjunPath;
    }

    public String getBurpProxyAddress() {
        return burpProxyAddress;
    }

    public void setBurpProxyAddress(String burpProxyAddress) {
        this.burpProxyAddress = burpProxyAddress;
    }

    public boolean isSendToBurp() {
        return sendToBurp;
    }

    public void setSendToBurp(boolean sendToBurp) {
        this.sendToBurp = sendToBurp;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public List<String> getCustomDictionary() {
        return customDictionary;
    }

    public void setCustomDictionary(List<String> customDictionary) {
        this.customDictionary = customDictionary;
    }

    public void addCustomParameter(String parameter) {
        if (parameter != null && !parameter.trim().isEmpty()) {
            customDictionary.add(parameter.trim());
        }
    }

    public void removeCustomParameter(String parameter) {
        customDictionary.remove(parameter);
    }

    public boolean isEnableJsonOutput() {
        return enableJsonOutput;
    }

    public void setEnableJsonOutput(boolean enableJsonOutput) {
        this.enableJsonOutput = enableJsonOutput;
    }

    public boolean isEnableVerboseOutput() {
        return enableVerboseOutput;
    }

    public void setEnableVerboseOutput(boolean enableVerboseOutput) {
        this.enableVerboseOutput = enableVerboseOutput;
    }
}

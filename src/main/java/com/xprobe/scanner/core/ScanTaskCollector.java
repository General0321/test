package com.xprobe.scanner.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.models.RequestContext;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描任务收集器
 * 封装了根据请求/响应和配置生成扫描任务的逻辑
 */
public class ScanTaskCollector {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;

    public ScanTaskCollector(MontoyaApi api, ConfigurationManager configManager) {
        this.api = api;
        this.configManager = configManager;
    }

    /**
     * 收集所有需要扫描的任务
     */
    public List<ScanTask> collectScanTasks(HttpRequest request, HttpResponse response, RequestContext context) {
        List<ScanTask> tasks = new ArrayList<>();
        
        // 遍历所有启用的配置
        for (Configuration config : configManager.getEnabledConfigurations()) {
            // 检查规则过滤器
            Configuration.RuleFilter filter = config.getRuleFilter();
            if (filter != null && filter.isEnabled()) {
                if (RuleFilterHelper.shouldFilter(request, response, filter)) {
                    // 被过滤器排除，跳过此规则
                    String ruleName = config.getCustomLabel();
                    if (ruleName == null || ruleName.isEmpty()) {
                        ruleName = "未命名规则";
                    }
                    api.logging().raiseDebugEvent(
                        "规则 [" + ruleName + "] 被过滤器排除: " + request.url()
                    );
                    continue;
                }
            }
            
            // 配对架构：创建一个基于整个请求的扫描任务
            if (config.getPairs() != null && !config.getPairs().isEmpty()) {
                ScanTask task = new ScanTask(null, config, request, context);
                tasks.add(task);
            }
        }
        
        return tasks;
    }
}



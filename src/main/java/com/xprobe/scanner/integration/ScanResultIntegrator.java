package com.xprobe.scanner.integration;

import com.xprobe.scanner.active.ScanResult;
import com.xprobe.scanner.active.ScanTarget;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.RequestHandler;
import burp.api.montoya.MontoyaApi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 扫描结果集成器 - 将主动扫描结果集成到被动扫描器
 */
public class ScanResultIntegrator {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;

    public ScanResultIntegrator(MontoyaApi api, ConfigurationManager configManager, RequestHandler requestHandler) {
        this.api = api;
        this.configManager = configManager;
    }

    /**
     * 将主动扫描结果转换为被动扫描任务
     */
    public void integrateActiveScanResults(List<ScanResult> activeResults) {
        for (ScanResult result : activeResults) {
            if (shouldIntegrateResult(result)) {
                createPassiveScanTasks(result);
            }
        }
    }

    /**
     * 判断是否应该集成此扫描结果
     */
    private boolean shouldIntegrateResult(ScanResult result) {
        // 只集成发现参数的结果
        if (!"PARAMETER".equals(result.getType())) {
            return false;
        }

        // 检查是否有匹配的配置
        return configManager.getEnabledConfigurations().stream()
            .anyMatch(config -> isParameterMatch(result.getParameter(), config));
    }

    /**
     * 检查参数是否匹配配置
     * 配对架构：检查请求配置中是否有匹配的参数
     */
    private boolean isParameterMatch(String parameter, com.xprobe.scanner.config.Configuration config) {
        // 配对架构：检查所有配对的请求配置
        if (config.getPairs() != null && !config.getPairs().isEmpty()) {
            for (var pair : config.getPairs()) {
                if (pair.getRequestConfig() != null && pair.getRequestConfig().getElements() != null) {
                    for (var element : pair.getRequestConfig().getElements()) {
                        if (element.getType() == com.xprobe.scanner.config.UnifiedHttpConfig.ElementType.PARAMETER &&
                            element.getName() != null && element.getName().equals(parameter)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 为主动扫描结果创建被动扫描任务
     */
    private void createPassiveScanTasks(ScanResult result) {
        try {
            // 为每个匹配的配置创建扫描任务
            for (com.xprobe.scanner.config.Configuration ignored : configManager.getEnabledConfigurations()) {
                if (isParameterMatch(result.getParameter(), ignored)) {
                    // 异步执行扫描
                    CompletableFuture.runAsync(() -> {
                        try {
                            // 这里可以调用被动扫描器的扫描逻辑
                            api.logging().raiseInfoEvent("为主动扫描结果创建被动扫描任务: " + 
                                result.getTarget().getUrl() + " - " + result.getParameter());
                        } catch (Exception e) {
                            api.logging().raiseErrorEvent("执行被动扫描任务时出错: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("创建被动扫描任务时出错: " + e.getMessage());
        }
    }

    /**
     * 批量集成主动扫描结果
     */
    public CompletableFuture<Void> integrateActiveScanResultsAsync(List<ScanResult> activeResults) {
        return CompletableFuture.runAsync(() -> {
            integrateActiveScanResults(activeResults);
        });
    }

    /**
     * 从主动扫描目标创建被动扫描任务
     */
    public void createPassiveScanTasksFromTarget(ScanTarget target) {
        try {
            // 为所有启用的配置创建扫描任务
            int configCount = configManager.getEnabledConfigurations().size();
            for (int i = 0; i < configCount; i++) {
                // 这里可以添加更复杂的逻辑来确定哪些参数应该被扫描
                // 暂时为每个配置创建一个通用的扫描任务
                CompletableFuture.runAsync(() -> {
                    try {
                        api.logging().raiseInfoEvent("为目标创建被动扫描任务: " + target.getUrl());
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("执行目标扫描任务时出错: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("从目标创建被动扫描任务时出错: " + e.getMessage());
        }
    }
}

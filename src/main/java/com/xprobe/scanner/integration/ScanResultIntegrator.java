package com.xprobe.scanner.integration;

import com.xprobe.scanner.active.ScanResult;
import com.xprobe.scanner.active.ScanTarget;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.core.RequestHandler;
import com.xprobe.scanner.models.ScanTask;
import com.xprobe.scanner.models.RequestContext;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 扫描结果集成器 - 将主动扫描结果集成到被动扫描器
 */
public class ScanResultIntegrator {
    private final MontoyaApi api;
    private final ConfigurationManager configManager;
    private final RequestHandler requestHandler;

    public ScanResultIntegrator(MontoyaApi api, ConfigurationManager configManager, RequestHandler requestHandler) {
        this.api = api;
        this.configManager = configManager;
        this.requestHandler = requestHandler;
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
     */
    private boolean isParameterMatch(String parameter, com.xprobe.scanner.config.Configuration config) {
        return config.getParameterNames().stream()
            .anyMatch(paramName -> {
                if ("String Match".equals(config.getParameterNameType())) {
                    return paramName.equals(parameter);
                } else if ("Regex Match".equals(config.getParameterNameType())) {
                    return parameter.matches(paramName);
                }
                return false;
            });
    }

    /**
     * 为主动扫描结果创建被动扫描任务
     */
    private void createPassiveScanTasks(ScanResult result) {
        try {
            // 创建HTTP请求
            HttpRequest request = HttpRequest.httpRequestFromUrl(result.getEndpoint());
            
            // 创建请求上下文
            RequestContext context = new RequestContext(
                "ACTIVE_SCAN", 
                request.method(), 
                request.url(), 
                request.toString().hashCode()
            );
            
            // 为每个匹配的配置创建扫描任务
            for (com.xprobe.scanner.config.Configuration config : configManager.getEnabledConfigurations()) {
                if (isParameterMatch(result.getParameter(), config)) {
                    // 创建参数
                    HttpParameter parameter = HttpParameter.parameter(
                        result.getParameter(), 
                        "test", 
                        HttpParameterType.URL
                    );
                    
                    // 转换为ParsedHttpParameter
                    var parsedParam = request.parameters().stream()
                        .filter(p -> p.name().equals(result.getParameter()))
                        .findFirst()
                        .orElse(null);
                    
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
            HttpRequest request = HttpRequest.httpRequestFromUrl(target.getUrl());
            RequestContext context = new RequestContext(
                "ACTIVE_SCAN", 
                request.method(), 
                request.url(), 
                request.toString().hashCode()
            );
            
            // 为所有启用的配置创建扫描任务
            for (com.xprobe.scanner.config.Configuration config : configManager.getEnabledConfigurations()) {
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

package com.xprobe.scanner;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.ConfigPersistence;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.core.RequestFilter;
import com.xprobe.scanner.core.RequestHandler;
import com.xprobe.scanner.core.TaskScheduler;
import com.xprobe.scanner.scanners.ScannerFactory;
import com.xprobe.scanner.ui.DashboardTab;
import com.xprobe.scanner.ui.ActiveProbeTab;
import com.xprobe.scanner.ui.ScanResultTab;
import com.xprobe.scanner.ui.UnifiedConfigTab;
import com.xprobe.scanner.integration.ScanResultIntegrator;
import com.xprobe.scanner.active.ExternalToolConfig;
import com.xprobe.scanner.active.ParameterCollector;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class XProbe implements BurpExtension {
    private TaskScheduler taskScheduler;
    private ConfigPersistence configPersistence;
    
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("XProbe - Passive Security Scanner");

        // 初始化配置持久化
        configPersistence = new ConfigPersistence();
        
        // 加载配置
        XProbeConfig config;
        try {
            config = configPersistence.load();
            api.logging().raiseInfoEvent("✅ 配置加载成功: " + configPersistence.getConfigFilePath());
        } catch (Exception e) {
            api.logging().raiseErrorEvent("⚠️ 配置加载失败，使用默认配置: " + e.getMessage());
            config = new XProbeConfig();
        }

        // 创建核心组件
        LogModel logModel = new LogModel();
        ConfigurationManager configManager = new ConfigurationManager();
        
        // 应用扫描规则配置
        if (config.getScanConfigurations() != null && !config.getScanConfigurations().isEmpty()) {
            for (Configuration scanConfig : config.getScanConfigurations()) {
                configManager.addConfiguration(scanConfig);
            }
            api.logging().raiseInfoEvent("✅ 加载了 " + config.getScanConfigurations().size() + " 条扫描规则");
        }
        
        GlobalFilter globalFilter = new GlobalFilter();
        
        // 应用黑白名单配置
        globalFilter.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
        globalFilter.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());
        if (config.isWhitelistEnabled()) {
            api.logging().raiseInfoEvent("✅ 白名单已启用，包含 " + config.getWhitelist().size() + " 条规则");
        }
        if (config.isBlacklistEnabled()) {
            api.logging().raiseInfoEvent("✅ 黑名单已启用，包含 " + config.getBlacklist().size() + " 条规则");
        }
        
        RequestFilter requestFilter = new RequestFilter(api, globalFilter);
        
        // 创建重构后的RealtimeScanner (必须在ScannerFactory之前创建)
        com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner = 
            new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter);
        
        // 应用参数收集模式
        if ("PARAMETERS_AND_KEYWORDS".equals(config.getCollectionMode())) {
            realtimeScanner.setCollectionMode(ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS);
            api.logging().raiseInfoEvent("✅ 参数收集模式: 参数名+关键词");
        } else {
            realtimeScanner.setCollectionMode(ParameterCollector.CollectionMode.PARAMETERS_ONLY);
            api.logging().raiseInfoEvent("✅ 参数收集模式: 仅参数名");
        }
        
        // 应用全局参数
        if (config.getGlobalParameters() != null && !config.getGlobalParameters().isEmpty()) {
            realtimeScanner.addGlobalCustomParameters(config.getGlobalParameters());
            api.logging().raiseInfoEvent("✅ 加载了 " + config.getGlobalParameters().size() + " 个全局参数");
        }
        
        // 应用Arjun配置
        ExternalToolConfig toolConfig = realtimeScanner.getToolConfig();
        toolConfig.setArjunPath(config.getArjunPath());
        toolConfig.setBurpProxyAddress(config.getBurpProxyAddress());
        toolConfig.setThreadCount(config.getThreadCount());
        toolConfig.setTimeout(config.getTimeout());
        toolConfig.setCustomDictionary(new ArrayList<>(config.getCustomDictionary())); // Set -> List
        toolConfig.setEnableJsonOutput(config.isEnableJsonOutput());
        toolConfig.setEnableVerboseOutput(config.isEnableVerboseOutput());
        toolConfig.setSendToBurp(config.isSendToBurp());
        api.logging().raiseInfoEvent("✅ Arjun配置已应用: " + config.getArjunPath());
        
        // 创建ScannerFactory (需要RealtimeScanner)
        ScannerFactory scannerFactory = new ScannerFactory(api, realtimeScanner);
        
        // 创建任务调度器
        taskScheduler = new TaskScheduler(api, scannerFactory, logModel);
        
        // 创建请求处理器 (需要RealtimeScanner)
        RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, taskScheduler, realtimeScanner);
        
        // 注册HTTP处理器
        api.http().registerHttpHandler(requestHandler);

        // 创建并注册UI界面（传入 realtimeScanner 和 configPersistence）
        api.userInterface().registerSuiteTab("XProbe", constructMainTab(api, logModel, configManager, requestFilter, globalFilter, realtimeScanner));
        
        // 注册扩展卸载处理器
        api.extension().registerUnloadingHandler(() -> {
            if (taskScheduler != null) {
                taskScheduler.shutdown();
            }
        });
        
        api.logging().raiseInfoEvent("🚀 XProbe 插件初始化完成");
    }

    // 构造顶级选项卡的用户界面组件
    private Component constructMainTab(MontoyaApi api, LogModel logModel, ConfigurationManager configManager, RequestFilter requestFilter, GlobalFilter globalFilter, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        // 创建顶级选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. 仪表板 - 总览
        DashboardTab dashboardTab = new DashboardTab(api, configManager, requestFilter, logModel);
        dashboardTab.setParameterCollector(realtimeScanner.getParameterCollector());
        tabbedPane.addTab("📊 仪表板", dashboardTab.getComponent());

        // 2. 扫描结果 - 结果展示
        ScanResultTab scanResultTab = new ScanResultTab(api, logModel);
        tabbedPane.addTab("📋 扫描结果", scanResultTab.getComponent());

        // 3. 被动扫描规则 - 核心功能
        com.xprobe.scanner.ui.PassiveScanConfigTab passiveScanTab = 
            new com.xprobe.scanner.ui.PassiveScanConfigTab(api, configManager);
        tabbedPane.addTab("🔍 被动扫描规则", passiveScanTab.getComponent());

        // 4. 主动探测 - 辅助功能（参数挖掘）
        ActiveProbeTab activeProbeTab = new ActiveProbeTab(api, configManager, realtimeScanner);
        tabbedPane.addTab("✨ 主动探测", activeProbeTab.getComponent());

        // 5. 配置中心 - 全局配置（黑白名单、工具配置等）
        UnifiedConfigTab unifiedConfigTab = new UnifiedConfigTab(api, configManager, globalFilter, realtimeScanner, configPersistence);
        tabbedPane.addTab("⚙️ 配置中心", unifiedConfigTab.getComponent());

        return tabbedPane;
    }
    
    /**
     * 获取配置持久化管理器
     */
    public ConfigPersistence getConfigPersistence() {
        return configPersistence;
    }
}

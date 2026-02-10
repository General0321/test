package com.xprobe.scanner;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import com.xprobe.scanner.Logs.LogModel;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigurationManager;
import com.xprobe.scanner.config.ConfigPersistence;
import com.xprobe.scanner.config.XProbeConfig;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.core.GlobalFilter;
import com.xprobe.scanner.core.OriginalResponseCache;
import com.xprobe.scanner.core.RequestFilter;
import com.xprobe.scanner.core.RequestHandler;
import com.xprobe.scanner.core.TaskScheduler;
import com.xprobe.scanner.core.ScanTaskCollector;
import com.xprobe.scanner.scanners.ScannerFactory;
import com.xprobe.scanner.ui.DashboardTab;
import com.xprobe.scanner.ui.ActiveProbeTab;
import com.xprobe.scanner.ui.ScanResultTab;
import com.xprobe.scanner.ui.UnifiedConfigTab;
import com.xprobe.scanner.active.ParameterCollector;

import javax.swing.*;
import java.awt.*;

public class XProbe implements BurpExtension {
    private TaskScheduler taskScheduler;
    private XProbeConfigManager xprobeConfigManager;
    private com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;  // ✅ P0修复：保存引用以便关闭
    private OriginalResponseCache responseCache;  // ✅ 保存响应缓存引用（用于清空缓存功能）
    
    // ✅ 修复：保存UI Tab引用以便清理资源
    private DashboardTab dashboardTab;
    private ScanResultTab scanResultTab;
    private ActiveProbeTab activeProbeTab;
    private UnifiedConfigTab unifiedConfigTab;
    
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("XProbe - Passive Security Scanner");

        // ✅ 卸载清理处理器应尽早注册：避免初始化中途失败时无法注册卸载清理
        api.extension().registerUnloadingHandler(() -> {
            api.logging().raiseInfoEvent("🛑 正在关闭XProbe插件...");
            performSafeCleanup(api);
            api.logging().raiseInfoEvent("✅ XProbe插件已安全关闭");
        });

        try {
            // 1. 初始化配置管理器
            xprobeConfigManager = new XProbeConfigManager(new ConfigPersistence());
            try {
                xprobeConfigManager.initialize();
                api.logging().raiseInfoEvent("✅ 配置管理器初始化成功: " + xprobeConfigManager.getConfigFilePath());
            } catch (Exception e) {
                api.logging().raiseErrorEvent("⚠️ 配置加载失败，尝试保存默认配置: " + e.getMessage());
                XProbeConfig defaultConfig = new XProbeConfig();
                xprobeConfigManager.saveConfig(defaultConfig);
                api.logging().raiseInfoEvent("✅ 默认配置已保存");
            }

            // 2. 获取配置并初始化核心逻辑组件
            XProbeConfig config = xprobeConfigManager.getConfig();
            LogModel logModel = new LogModel();
            ConfigurationManager configManager = new ConfigurationManager();

            // 应用扫描规则
            if (!config.getScanConfigurations().isEmpty()) {
                for (Configuration scanConfig : config.getScanConfigurations()) {
                    configManager.addConfiguration(scanConfig);
                }
                api.logging().raiseInfoEvent("✅ 加载了 " + config.getScanConfigurations().size() + " 条扫描规则");
            }

            GlobalFilter globalFilter = new GlobalFilter();
            globalFilter.updateWhitelist(config.getWhitelist(), config.isWhitelistEnabled());
            globalFilter.updateBlacklist(config.getBlacklist(), config.isBlacklistEnabled());

            RequestFilter requestFilter = new RequestFilter(api, globalFilter);

            // 3. 创建实时扫描器与任务调度器
            realtimeScanner = new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter, logModel, config);
            
            // 应用参数收集与Arjun配置
            if ("PARAMETERS_AND_KEYWORDS".equals(config.getCollectionMode())) {
                realtimeScanner.setCollectionMode(ParameterCollector.CollectionMode.PARAMETERS_AND_KEYWORDS);
            }
            if (!config.getGlobalParameters().isEmpty()) {
                realtimeScanner.addGlobalCustomParameters(config.getGlobalParameters());
            }
            realtimeScanner.setMinParameterThreshold(config.getArjunRealtimeThreshold());
            realtimeScanner.setCooldownSeconds(config.getArjunRealtimeInterval());

            // 4. 创建响应缓存（容量 5000）
            this.responseCache = new OriginalResponseCache(5000);

            // 5. 创建工厂与调度器并建立引用
            ScannerFactory scannerFactory = new ScannerFactory(api, realtimeScanner, xprobeConfigManager, responseCache);
            taskScheduler = new TaskScheduler(api, scannerFactory, logModel, xprobeConfigManager, responseCache);
            realtimeScanner.setTaskScheduler(taskScheduler);
            realtimeScanner.setResponseCache(responseCache);

            // 6. 注册处理器与UI
            RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, taskScheduler, realtimeScanner, xprobeConfigManager, responseCache, globalFilter);
            api.http().registerHttpHandler(requestHandler);

            api.userInterface().registerSuiteTab("XProbe", constructMainTab(api, logModel, configManager, requestFilter, globalFilter, realtimeScanner, responseCache));
            
            ScanTaskCollector scanTaskCollector = new ScanTaskCollector(api, configManager);
            api.userInterface().registerContextMenuItemsProvider(new com.xprobe.scanner.ui.XProbeContextMenuProvider(api, scanTaskCollector, taskScheduler, responseCache));

            api.logging().raiseInfoEvent("🚀 XProbe 插件初始化完成");

        } catch (Exception e) {
            api.logging().raiseErrorEvent("❌ XProbe 初始化致命错误: " + e.getMessage());
            e.printStackTrace();
            // 失败时清理资源
            try {
                performSafeCleanup(api);
            } catch (Exception ignored) {}
            // 抛出异常让 Burp 知道加载失败
            throw new RuntimeException("XProbe 初始化失败", e);
        }
    }

    private void performSafeCleanup(MontoyaApi api) {
        try {
            if (dashboardTab != null) {
                dashboardTab.cleanup();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("清理 DashboardTab 失败: " + e.getMessage());
        }

        try {
            if (scanResultTab != null) {
                scanResultTab.cleanup();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("清理 ScanResultTab 失败: " + e.getMessage());
        }

        try {
            if (activeProbeTab != null) {
                activeProbeTab.cleanup();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("清理 ActiveProbeTab 失败: " + e.getMessage());
        }

        try {
            if (unifiedConfigTab != null) {
                unifiedConfigTab.cleanup();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("清理 UnifiedConfigTab 失败: " + e.getMessage());
        }

        try {
            if (taskScheduler != null) {
                taskScheduler.shutdown();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("关闭 TaskScheduler 失败: " + e.getMessage());
        }

        try {
            if (realtimeScanner != null) {
                realtimeScanner.shutdown();
            }
        } catch (Exception e) {
            api.logging().raiseDebugEvent("关闭 RealtimeScanner 失败: " + e.getMessage());
        }
    }

    // 构造顶级选项卡的用户界面组件
    private Component constructMainTab(MontoyaApi api, LogModel logModel, ConfigurationManager configManager, RequestFilter requestFilter, GlobalFilter globalFilter, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner, OriginalResponseCache responseCache) {
        // 创建顶级选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. 仪表板 - 总览
        this.dashboardTab = new DashboardTab(api, configManager, requestFilter, logModel);
        this.dashboardTab.setParameterCollector(realtimeScanner.getParameterCollector());
        this.dashboardTab.setArjunService(realtimeScanner.getArjunService());  // ✅ 设置Arjun服务
        this.dashboardTab.setTaskScheduler(taskScheduler);  // ✅ 设置TaskScheduler引用（内部会注册监听器）
        tabbedPane.addTab("📊 仪表板", this.dashboardTab.getComponent());

        // 2. 扫描结果 - 结果展示（传入requestFilter和realtimeScanner用于清空扫描缓存）
        this.scanResultTab = new ScanResultTab(api, logModel, realtimeScanner, responseCache);
        tabbedPane.addTab("🔍 扫描结果", this.scanResultTab.getComponent());

        // 3. 被动扫描规则 - 核心功能
        com.xprobe.scanner.ui.PassiveScanConfigTab passiveScanTab = 
            new com.xprobe.scanner.ui.PassiveScanConfigTab(api, configManager, xprobeConfigManager);
        tabbedPane.addTab("📋 被动规则", passiveScanTab.getComponent());

        // 4. 主动探测 - 辅助功能（参数挖掘）
        this.activeProbeTab = new ActiveProbeTab(api, configManager, realtimeScanner);
        tabbedPane.addTab("🎯 主动探测", this.activeProbeTab.getComponent());

        // 5. 配置中心 - 全局配置（黑白名单、工具配置等）
        this.unifiedConfigTab = new UnifiedConfigTab(api, configManager, globalFilter, realtimeScanner, xprobeConfigManager);
        this.unifiedConfigTab.setArjunService(realtimeScanner.getArjunService());  // ✅ 设置Arjun服务
        tabbedPane.addTab("⚙️ 配置中心", this.unifiedConfigTab.getComponent());

        return tabbedPane;
    }
    
    /**
     * ✅ 获取配置管理器（新架构）
     */
    public XProbeConfigManager getConfigManager() {
        return xprobeConfigManager;
    }
}

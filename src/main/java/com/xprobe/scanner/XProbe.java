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
    
    // ✅ 修复：保存UI Tab引用以便清理资源
    private DashboardTab dashboardTab;
    private ScanResultTab scanResultTab;
    private ActiveProbeTab activeProbeTab;
    private UnifiedConfigTab unifiedConfigTab;
    
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("XProbe - Passive Security Scanner");

        // ✅ 初始化配置管理器（单例模式）
        xprobeConfigManager = new XProbeConfigManager(new ConfigPersistence());
        
        // ✅ 初始化配置（加载一次到内存）
        XProbeConfig config;
        try {
            xprobeConfigManager.initialize();
            api.logging().raiseInfoEvent("✅ 配置管理器初始化成功: " + xprobeConfigManager.getConfigFilePath());
        } catch (Exception e) {
            api.logging().raiseErrorEvent("⚠️ 配置加载失败，使用默认配置: " + e.getMessage());
            
            // ✅ 关键修复：保存默认配置到configManager
            try {
                XProbeConfig defaultConfig = new XProbeConfig();
                xprobeConfigManager.saveConfig(defaultConfig);
                api.logging().raiseInfoEvent("✅ 默认配置已保存到: " + xprobeConfigManager.getConfigFilePath());
            } catch (Exception ex) {
                api.logging().raiseErrorEvent("❌ 致命错误：无法保存默认配置: " + ex.getMessage());
                api.logging().raiseErrorEvent("❌ 插件可能无法正常工作，请检查磁盘空间和权限");
            }
        }
        
        // ✅ 获取配置（现在一定有配置了）
        config = xprobeConfigManager.getConfig();

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
        // ✅ 传入 XProbeConfig 以正确初始化 ArjunService
        realtimeScanner = 
            new com.xprobe.scanner.active.RealtimeScannerRefactored(api, configManager, globalFilter, logModel, config);
        
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
        
        // ✅ 应用Arjun实时模式配置（智能触发）
        realtimeScanner.setMinParameterThreshold(config.getArjunRealtimeThreshold());
        realtimeScanner.setCooldownSeconds(config.getArjunRealtimeInterval());
        api.logging().raiseInfoEvent(String.format(
            "✅ Arjun实时模式配置: 阈值=%d个参数, 定时=%d秒", 
            config.getArjunRealtimeThreshold(), config.getArjunRealtimeInterval()
        ));
        
        // ✅ Arjun配置（Java原生Arjun，配置在ArjunService中）
        // 外部工具配置已废弃，使用Java原生实现
        api.logging().raiseInfoEvent("✅ 使用Java原生Arjun（无需外部工具配置）");
        
        // ✅ 创建ScannerFactory (需要RealtimeScanner和XProbeConfigManager以支持全局注入模式)
        ScannerFactory scannerFactory = new ScannerFactory(api, realtimeScanner, xprobeConfigManager);
        
        // ✅ 创建原始响应缓存（LRU，最多缓存2000条记录）
        OriginalResponseCache responseCache = new OriginalResponseCache(2000);
        api.logging().raiseInfoEvent("✅ 原始响应缓存已创建（容量: 2000）");
        
        // ✅ 创建任务调度器（传入响应缓存）
        taskScheduler = new TaskScheduler(api, scannerFactory, logModel, xprobeConfigManager, responseCache);
        
        // ✅ 建立RealtimeScanner和TaskScheduler的双向引用（用于Arjun→漏洞扫描）
        realtimeScanner.setTaskScheduler(taskScheduler);
        
        // ✅ 创建请求处理器 (需要RealtimeScanner和响应缓存)
        RequestHandler requestHandler = new RequestHandler(api, configManager, requestFilter, taskScheduler, realtimeScanner, xprobeConfigManager, responseCache);
        
        // 注册HTTP处理器
        api.http().registerHttpHandler(requestHandler);

        // ✅ 创建并注册UI界面（传入 realtimeScanner 和 xprobeConfigManager）
        api.userInterface().registerSuiteTab("XProbe", constructMainTab(api, logModel, configManager, requestFilter, globalFilter, realtimeScanner));
        
        // ✅ P0修复：注册完整的资源清理处理器
        api.extension().registerUnloadingHandler(() -> {
            api.logging().raiseInfoEvent("🛑 正在关闭XProbe插件...");
            
            // ✅ 修复：清理UI Tab资源
            if (dashboardTab != null) {
                dashboardTab.cleanup();
            }
            
            if (scanResultTab != null) {
                scanResultTab.cleanup();
            }
            
            if (activeProbeTab != null) {
                activeProbeTab.cleanup();
            }
            
            if (unifiedConfigTab != null) {
                unifiedConfigTab.cleanup();
            }
            
            if (taskScheduler != null) {
                taskScheduler.shutdown();
            }
            
            if (realtimeScanner != null) {
                realtimeScanner.shutdown();
            }
            
            api.logging().raiseInfoEvent("✅ XProbe插件已安全关闭");
        });
        
        api.logging().raiseInfoEvent("🚀 XProbe 插件初始化完成");
    }

    // 构造顶级选项卡的用户界面组件
    private Component constructMainTab(MontoyaApi api, LogModel logModel, ConfigurationManager configManager, RequestFilter requestFilter, GlobalFilter globalFilter, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        // 创建顶级选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. 仪表板 - 总览
        this.dashboardTab = new DashboardTab(api, configManager, requestFilter, logModel);
        this.dashboardTab.setParameterCollector(realtimeScanner.getParameterCollector());
        this.dashboardTab.setArjunService(realtimeScanner.getArjunService());  // ✅ 设置Arjun服务
        tabbedPane.addTab("📊 仪表板", this.dashboardTab.getComponent());

        // 2. 扫描结果 - 结果展示（传入requestFilter和realtimeScanner用于清空扫描缓存）
        this.scanResultTab = new ScanResultTab(api, logModel, realtimeScanner);
        tabbedPane.addTab("📋 扫描结果", this.scanResultTab.getComponent());

        // 3. 被动扫描规则 - 核心功能
        com.xprobe.scanner.ui.PassiveScanConfigTab passiveScanTab = 
            new com.xprobe.scanner.ui.PassiveScanConfigTab(api, configManager, xprobeConfigManager);
        tabbedPane.addTab("🔍 被动扫描规则", passiveScanTab.getComponent());

        // 4. 主动探测 - 辅助功能（参数挖掘）
        this.activeProbeTab = new ActiveProbeTab(api, configManager, realtimeScanner);
        tabbedPane.addTab("✨ 主动探测", this.activeProbeTab.getComponent());

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

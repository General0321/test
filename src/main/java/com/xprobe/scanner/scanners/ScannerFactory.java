package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import com.xprobe.scanner.config.XProbeConfigManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫描器工厂，负责创建和管理扫描器实例
 */
public class ScannerFactory {
    private final Map<String, Scanner> scanners = new HashMap<>();
    private final MontoyaApi api;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    
    public ScannerFactory(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner, XProbeConfigManager xprobeConfigManager) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
        initializeScanners();
    }
    
    private void initializeScanners() {
        // ✅ 注册UniversalScanner（通用扫描器，支持灵活的配对架构）
        registerScanner(new UniversalScanner(api, realtimeScanner, xprobeConfigManager));
        
        // 注: 旧的专用扫描器（LFIScanner、SQLScanner、SSRFScanner）已移除
        // 所有扫描功能现在统一由UniversalScanner + 配对规则实现
    }
    
    private void registerScanner(Scanner scanner) {
        scanners.put(scanner.getType(), scanner);
        api.logging().raiseInfoEvent("Registered scanner: " + scanner.getName());
    }
    
    /**
     * 根据类型获取扫描器
     */
    public Scanner getScanner(String type) {
        Scanner scanner = scanners.get(type);
        if (scanner == null) {
            api.logging().raiseErrorEvent("Unknown scanner type: " + type);
        }
        return scanner;
    }
    
    /**
     * 注册自定义扫描器
     */
    public void registerCustomScanner(Scanner scanner) {
        registerScanner(scanner);
    }
    
    /**
     * 获取所有可用的扫描器类型
     */
    public String[] getAvailableScannerTypes() {
        return scanners.keySet().toArray(new String[0]);
    }
}

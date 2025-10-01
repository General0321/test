package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫描器工厂，负责创建和管理扫描器实例
 */
public class ScannerFactory {
    private final Map<String, Scanner> scanners = new HashMap<>();
    private final MontoyaApi api;
    private final com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner;
    
    public ScannerFactory(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner) {
        this.api = api;
        this.realtimeScanner = realtimeScanner;
        initializeScanners();
    }
    
    private void initializeScanners() {
        // 注册所有扫描器
        registerScanner(new LFIScanner(api, realtimeScanner));
        registerScanner(new SQLScanner(api, realtimeScanner));
        registerScanner(new SSRFScanner(api, realtimeScanner));
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

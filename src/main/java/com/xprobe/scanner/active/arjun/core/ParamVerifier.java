package com.xprobe.scanner.active.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.xprobe.scanner.active.arjun.http.BurpHttpRequester;
import com.xprobe.scanner.active.arjun.model.AnomalyResult;
import com.xprobe.scanner.active.arjun.model.BaselineFactors;

import java.util.*;

/**
 * 参数验证器 - 单独验证每个参数
 */
public class ParamVerifier {
    
    // ✅ 常量定义（避免硬编码，与ParamDiscoveryEngine保持一致）
    private static final int BATCH_VERIFY_THRESHOLD = 5;  // 批量验证阈值（参数数<=此值时，批量验证发现异常则全部标记为有效）
    
    private final MontoyaApi api;
    private final BurpHttpRequester requester;
    private final AnomalyDetector detector;
    
    public ParamVerifier(MontoyaApi api, BurpHttpRequester requester) {
        this.api = api;
        this.requester = requester;
        this.detector = new AnomalyDetector(api);
    }
    
    /**
     * 验证单个参数
     * 
     * @param originalRequest 原始请求
     * @param param 待验证的参数名
     * @param factors 基线因子
     * @return 验证结果（如果有异常，返回异常类型）
     */
    public String verifySingle(HttpRequest originalRequest, 
                               String param, 
                               BaselineFactors factors) {
        
        // ✅ 所有参数使用随机值（不使用特殊参数）
        String testValue = generateRandomValue(6);
        
        // 构建测试请求
        Map<String, String> testParams = new HashMap<>();
        testParams.put(param, testValue);
        
        HttpRequest testRequest = requester.buildTestRequest(originalRequest, testParams);
        
        // ✅ 发送请求（使用新的RequestResult）
        BurpHttpRequester.RequestResult result = requester.sendRequest(testRequest);
        
        if (!result.isSuccess()) {
            // 请求失败
            return null;
        }
        
        // 检测异常
        AnomalyResult anomaly = detector.compare(result.getResponse(), factors, testParams);
        
        if (anomaly.hasAnomaly()) {
            return anomaly.getAnomalyType();
        }
        
        return null;
    }
    
    /**
     * 批量验证参数（✅ 优化：将所有参数合并到一个请求中测试）
     * 
     * @param originalRequest 原始请求
     * @param params 待验证的参数集合
     * @param factors 基线因子
     * @return 验证通过的参数集合
     */
    public Set<String> verifyBatch(HttpRequest originalRequest,
                                   Set<String> params,
                                   BaselineFactors factors) {
        
        Set<String> confirmed = new LinkedHashSet<>();
        
        if (params == null || params.isEmpty()) {
            return confirmed;
        }
        
        // ✅ 优化：将所有参数合并到一个请求中测试，而不是循环单独测试
        // 构建包含所有参数的测试请求
        Map<String, String> testParams = new HashMap<>();
        for (String param : params) {
            testParams.put(param, generateRandomValue(6));
        }
        
        HttpRequest testRequest = requester.buildTestRequest(originalRequest, testParams);
        
        // 发送请求
        BurpHttpRequester.RequestResult result = requester.sendRequest(testRequest);
        
        if (!result.isSuccess()) {
            // 请求失败，回退到单独验证
            api.logging().raiseDebugEvent(
                "  批量验证请求失败，回退到单独验证"
            );
            return verifyBatchFallback(originalRequest, params, factors);
        }
        
        // 检测异常
        AnomalyResult anomaly = detector.compare(result.getResponse(), factors, testParams);
        
        if (anomaly.hasAnomaly()) {
            // ✅ 如果批量请求发现异常，说明至少有一个参数有效
            // 由于无法确定具体是哪个参数，我们有两种策略：
            // 1. 如果参数数量较少（<=BATCH_VERIFY_THRESHOLD），全部标记为有效（保守策略）
            // 2. 如果参数数量较多，回退到单独验证以精确定位
            
            if (params.size() <= BATCH_VERIFY_THRESHOLD) {
                // 参数数量少，全部标记为有效（避免遗漏）
                confirmed.addAll(params);
                api.logging().raiseInfoEvent(String.format(
                    "  ✅ 批量验证发现异常，确认 %d 个参数有效 (原因: %s)",
                    params.size(), anomaly.getReason()
                ));
            } else {
                // 参数数量多，回退到单独验证以精确定位
                api.logging().raiseDebugEvent(String.format(
                    "  批量验证发现异常，但参数数量较多 (%d个)，回退到单独验证以精确定位",
                    params.size()
                ));
                return verifyBatchFallback(originalRequest, params, factors);
            }
        } else {
            // 批量请求未发现异常，说明这些参数可能都无效
            api.logging().raiseDebugEvent(
                "  批量验证未发现异常，这些参数可能都无效"
            );
        }
        
        return confirmed;
    }
    
    /**
     * 批量验证的回退方案：单独验证每个参数（用于精确定位）
     */
    private Set<String> verifyBatchFallback(HttpRequest originalRequest,
                                            Set<String> params,
                                            BaselineFactors factors) {
        Set<String> confirmed = new LinkedHashSet<>();
        
        for (String param : params) {
            String anomalyType = verifySingle(originalRequest, param, factors);
            
            if (anomalyType != null) {
                confirmed.add(param);
                api.logging().raiseDebugEvent(
                    "  ✅ 确认参数: " + param + " (检测到: " + anomalyType + ")"
                );
            }
        }
        
        return confirmed;
    }
    
    /**
     * 生成随机值
     */
    private String generateRandomValue(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
}


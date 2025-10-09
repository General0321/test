package com.xprobe.scanner.active.arjun.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.http.BurpHttpRequester;
import com.xprobe.scanner.active.arjun.model.AnomalyResult;
import com.xprobe.scanner.active.arjun.model.BaselineFactors;

import java.util.*;

/**
 * 参数验证器 - 单独验证每个参数
 */
public class ParamVerifier {
    
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
     * 批量验证参数
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


package com.xprobe.scanner.active.arjun;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.core.*;
import com.xprobe.scanner.active.arjun.http.BurpHttpRequester;
import com.xprobe.scanner.active.arjun.model.*;
import com.xprobe.scanner.active.arjun.config.SpecialParams;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 参数发现引擎 - Arjun核心实现
 * 
 * 工作流程：
 * 1. 稳定性探测 + 建立基线
 * 2. 启发式提取参数
 * 3. 分块爆破 + 递归缩小
 * 4. 最终验证
 */
public class ParamDiscoveryEngine {
    
    private final MontoyaApi api;
    private final BurpHttpRequester requester;
    private final ResponseBaseline baseline;
    private final AnomalyDetector detector;
    // 移除：private final ParamExtractor extractor;  // 不需要，参数收集由ParameterCollector完成
    private final ChunkProcessor chunkProcessor;
    private final ParamVerifier verifier;
    
    // 配置
    private final int chunkSize;
    // 移除：private final boolean enableHeuristic;  // 不需要启发式提取
    
    // ✅ P1: 健康状态码检查
    private static final Set<Integer> UNHEALTHY_CODES = Set.of(400, 413, 418, 429, 503);
    
    public ParamDiscoveryEngine(MontoyaApi api) {
        this(api, 250, true);
    }
    
    public ParamDiscoveryEngine(MontoyaApi api, int chunkSize, boolean enableHeuristic) {
        this.api = api;
        this.chunkSize = chunkSize;
        // enableHeuristic参数保留兼容性，但不使用（参数由ParameterCollector收集）
        
        this.requester = new BurpHttpRequester(api);
        this.baseline = new ResponseBaseline(api);
        this.detector = new AnomalyDetector(api);
        // 移除：this.extractor - 参数收集由ParameterCollector完成，Arjun只负责爆破验证
        this.chunkProcessor = new ChunkProcessor(chunkSize);
        this.verifier = new ParamVerifier(api, requester);
    }
    
    /**
     * 启动参数发现扫描（异步）
     */
    public CompletableFuture<DiscoveryResult> scan(HttpRequest originalRequest, 
                                                     Set<String> dictionary) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                api.logging().raiseInfoEvent("🔍 参数发现开始: " + originalRequest.url());
                api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // 1. 初始化：稳定性探测 + 建立基线
                api.logging().raiseInfoEvent("📊 阶段1: 稳定性探测...");
                ScanContext context = initialize(originalRequest, dictionary);
                
                if (!context.isHealthy()) {
                    api.logging().raiseErrorEvent("❌ 目标不稳定，跳过扫描");
                    return DiscoveryResult.error("目标不稳定");
                }
                
                // 2. 合并特殊参数（专注爆破，不做参数发现）
                // 参数收集已由 ParameterCollector 完成，Arjun只负责验证有效性
                api.logging().raiseInfoEvent("📦 阶段2: 准备字典...");
                
                // ✅ P1修复：合并特殊参数
                int originalSize = context.getDictionary().size();
                Set<String> specialParams = SpecialParams.getSpecialParamNames();
                context.addDictionary(specialParams);
                
                api.logging().raiseInfoEvent(String.format(
                    "📚 字典大小: %d 个参数 (普通: %d, 特殊: %d)",
                    context.getDictionary().size(),
                    originalSize,
                    specialParams.size()
                ));
                
                // 3. 分块爆破 + 递归缩小
                api.logging().raiseInfoEvent("🔄 阶段3: 分块爆破...");
                Set<ParamCandidate> candidates = narrowDown(context);
                
                if (candidates.isEmpty()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    api.logging().raiseInfoEvent("ℹ️ 未发现异常参数");
                    return DiscoveryResult.success(
                        originalRequest.url(), 
                        new LinkedHashSet<>(), 
                        elapsed
                    );
                }
                
                // 4. 最终验证
                api.logging().raiseInfoEvent("✓ 阶段4: 最终验证 (" + candidates.size() + " 个候选)...");
                Set<String> confirmedParams = verify(context, candidates);
                
                long elapsed = System.currentTimeMillis() - startTime;
                
                api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                api.logging().raiseInfoEvent(String.format(
                    "✅ 参数发现完成: 发现 %d 个参数 (耗时 %dms)",
                    confirmedParams.size(),
                    elapsed
                ));
                if (!confirmedParams.isEmpty()) {
                    api.logging().raiseInfoEvent("  参数列表: " + confirmedParams);
                }
                api.logging().raiseInfoEvent("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                return DiscoveryResult.success(originalRequest.url(), confirmedParams, elapsed);
                
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                api.logging().raiseErrorEvent("参数发现失败: " + e.getMessage());
                e.printStackTrace();
                return DiscoveryResult.error(e.getMessage());
            }
        });
    }
    
    /**
     * 初始化：稳定性探测 + 建立基线
     */
    private ScanContext initialize(HttpRequest originalRequest, Set<String> dictionary) {
        // 发送随机参数请求（2次）
        String randomParam1 = "z" + generateRandomString(6);
        String randomValue1 = generateRandomString(6);
        
        String randomParam2 = "z" + generateRandomString(6);
        String randomValue2 = generateRandomString(6);
        
        HttpRequest testRequest1 = requester.buildTestRequest(
            originalRequest, 
            Map.of(randomParam1, randomValue1)
        );
        HttpRequest testRequest2 = requester.buildTestRequest(
            originalRequest, 
            Map.of(randomParam2, randomValue2)
        );
        
        api.logging().raiseDebugEvent("  发送基线请求1...");
        HttpResponse response1 = requester.sendRequest(testRequest1);
        
        // ✅ P1: 健康状态码检查
        int statusCode = Integer.valueOf(response1.statusCode());
        if (UNHEALTHY_CODES.contains(statusCode)) {
            api.logging().raiseErrorEvent(
                "  ⚠️ 目标返回错误状态码: " + statusCode + "，这可能影响扫描"
            );
        }
        
        api.logging().raiseDebugEvent("  发送基线请求2...");
        HttpResponse response2 = requester.sendRequest(testRequest2);
        
        // 建立基线规则
        BaselineFactors factors = baseline.define(
            response1, 
            response2, 
            randomParam1, 
            randomValue1, 
            dictionary
        );
        
        // ✅ P0修复：动态移除不稳定因子（关键！）
        api.logging().raiseDebugEvent("  开始稳定性验证（动态因子调整）...");
        
        int maxRetries = 10;  // 最多尝试10次
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            // 生成新的随机参数
            String randomParam = "z" + generateRandomString(6);
            String randomValue = generateRandomString(6);
            
            HttpRequest testRequest = requester.buildTestRequest(
                originalRequest,
                Map.of(randomParam, randomValue)
            );
            
            HttpResponse response = requester.sendRequest(testRequest);
            
            // 检测异常
            AnomalyResult anomaly = detector.compare(
                response, 
                factors, 
                Map.of(randomParam, randomValue)
            );
            
            if (!anomaly.hasAnomaly()) {
                // 找到稳定状态
                api.logging().raiseInfoEvent("  ✓ 目标稳定（尝试 " + (retryCount + 1) + " 次）");
                break;
            }
            
            // 移除不稳定的因子
            String unstableFactor = anomaly.getAnomalyType();
            factors.removeFactor(unstableFactor);
            
            api.logging().raiseDebugEvent(
                "  移除不稳定因子: " + unstableFactor + " (" + anomaly.getReason() + ")"
            );
            
            retryCount++;
        }
        
        // 检查健康状态
        boolean isHealthy = factors.hasAnyFactor();
        
        if (!isHealthy || retryCount >= maxRetries) {
            api.logging().raiseErrorEvent(
                "  ⚠️ 目标不稳定或所有因子都被移除（尝试 " + retryCount + " 次）"
            );
        }
        
        return new ScanContext(
            originalRequest,
            factors,
            dictionary,
            response1,
            isHealthy
        );
    }
    
    /**
     * 递归缩小参数范围
     */
    private Set<ParamCandidate> narrowDown(ScanContext context) {
        Set<ParamCandidate> allCandidates = new LinkedHashSet<>();
        
        // 创建分块
        List<Set<String>> chunks = chunkProcessor.createChunks(context.getDictionary());
        
        api.logging().raiseInfoEvent(String.format(
            "  分块数量: %d (每块 %d 个参数)", 
            chunks.size(), 
            chunkSize
        ));
        
        // 第一轮：分块测试
        List<Set<String>> anomalousChunks = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            Set<String> chunk = chunks.get(i);
            
            // ✅ P1修复：使用特殊参数的特定值
            Map<String, String> testParams = new HashMap<>();
            Map<String, String> specialParams = SpecialParams.getSpecialParams();
            
            for (String param : chunk) {
                if (specialParams.containsKey(param)) {
                    // 使用特殊值
                    testParams.put(param, specialParams.get(param));
                } else {
                    // 使用随机值
                    testParams.put(param, generateRandomValue());
                }
            }
            
            HttpRequest testRequest = requester.buildTestRequest(
                context.getOriginalRequest(),
                testParams
            );
            HttpResponse response = requester.sendRequest(testRequest);
            
            // 检测异常
            AnomalyResult anomaly = detector.compare(
                response, 
                context.getFactors(), 
                testParams
            );
            
            if (anomaly.hasAnomaly()) {
                api.logging().raiseDebugEvent(String.format(
                    "  ✓ 发现异常分块 %d/%d (原因: %s)", 
                    i + 1, chunks.size(), anomaly.getReason()
                ));
                anomalousChunks.add(chunk);
            }
            
            // 定期输出进度
            if ((i + 1) % 10 == 0 || i == chunks.size() - 1) {
                api.logging().raiseInfoEvent(String.format(
                    "  进度: %d/%d (发现 %d 个异常分块)", 
                    i + 1, chunks.size(), anomalousChunks.size()
                ));
            }
        }
        
        api.logging().raiseInfoEvent(
            "  第一轮完成: " + anomalousChunks.size() + " 个异常分块"
        );
        
        // 递归缩小异常分块
        for (Set<String> anomalousChunk : anomalousChunks) {
            allCandidates.addAll(recursiveNarrow(context, anomalousChunk, 1));
        }
        
        return allCandidates;
    }
    
    /**
     * 递归缩小单个异常分块
     */
    private Set<ParamCandidate> recursiveNarrow(ScanContext context, 
                                                 Set<String> params, 
                                                 int depth) {
        Set<ParamCandidate> candidates = new LinkedHashSet<>();
        
        // 终止条件
        if (params.size() == 1) {
            // 已经缩小到单个参数
            String param = params.iterator().next();
            candidates.add(new ParamCandidate(param));
            return candidates;
        }
        
        if (params.size() <= 5 || depth > 5) {
            // 小于5个参数，或递归深度超过5层，全部作为候选
            for (String param : params) {
                candidates.add(new ParamCandidate(param));
            }
            return candidates;
        }
        
        // 继续分块
        int subChunkSize = Math.max(2, params.size() / 5);
        List<Set<String>> subChunks = chunkProcessor.createChunks(params, subChunkSize);
        
        api.logging().raiseDebugEvent(String.format(
            "    [深度%d] 细分为 %d 个子块 (每块 %d 个)", 
            depth, subChunks.size(), subChunkSize
        ));
        
        // 测试每个子块
        List<Set<String>> anomalousSubChunks = new ArrayList<>();
        Map<String, String> specialParams = SpecialParams.getSpecialParams();
        
        for (Set<String> subChunk : subChunks) {
            Map<String, String> testParams = new HashMap<>();
            for (String param : subChunk) {
                if (specialParams.containsKey(param)) {
                    testParams.put(param, specialParams.get(param));
                } else {
                    testParams.put(param, generateRandomValue());
                }
            }
            
            HttpRequest testRequest = requester.buildTestRequest(
                context.getOriginalRequest(),
                testParams
            );
            HttpResponse response = requester.sendRequest(testRequest);
            
            AnomalyResult anomaly = detector.compare(
                response,
                context.getFactors(),
                testParams
            );
            
            if (anomaly.hasAnomaly()) {
                anomalousSubChunks.add(subChunk);
            }
        }
        
        // 递归处理异常子块
        for (Set<String> anomalousSubChunk : anomalousSubChunks) {
            candidates.addAll(recursiveNarrow(context, anomalousSubChunk, depth + 1));
        }
        
        return candidates;
    }
    
    /**
     * 最终验证（单独测试每个参数）
     */
    private Set<String> verify(ScanContext context, Set<ParamCandidate> candidates) {
        Set<String> confirmedParams = new LinkedHashSet<>();
        
        int total = candidates.size();
        int current = 0;
        
        for (ParamCandidate candidate : candidates) {
            current++;
            
            // 单独测试这个参数
            String anomalyType = verifier.verifySingle(
                context.getOriginalRequest(),
                candidate.getName(),
                context.getFactors()
            );
            
            if (anomalyType != null) {
                confirmedParams.add(candidate.getName());
                api.logging().raiseInfoEvent(String.format(
                    "  ✅ [%d/%d] 确认参数: %s (检测到: %s)", 
                    current, total,
                    candidate.getName(), 
                    anomalyType
                ));
            } else {
                api.logging().raiseDebugEvent(String.format(
                    "  ❌ [%d/%d] 排除参数: %s", 
                    current, total,
                    candidate.getName()
                ));
            }
        }
        
        return confirmedParams;
    }
    
    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * 生成随机值（用于测试）
     */
    private String generateRandomValue() {
        return generateRandomString(6);
    }
}


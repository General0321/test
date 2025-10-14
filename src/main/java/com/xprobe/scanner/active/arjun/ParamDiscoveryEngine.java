package com.xprobe.scanner.active.arjun;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.active.arjun.core.*;
import com.xprobe.scanner.active.arjun.http.BurpHttpRequester;
import com.xprobe.scanner.active.arjun.model.*;
import com.xprobe.scanner.active.arjun.error.*;
import com.xprobe.scanner.active.arjun.concurrent.ConcurrentProcessor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 参数发现引擎 - Arjun核心实现（✅ 完全对应Python版本）
 * 
 * 工作流程：
 * 1. 稳定性探测 + 建立基线
 * 2. 分块爆破 + 递归缩小（✅ 并发处理）
 * 3. 最终验证（✅ 带重试）
 * 
 * ✅ 新增功能（对应Python）：
 * - ErrorHandler：错误处理和重试决策
 * - RetryStrategy：递归重试机制
 * - RateLimiter：速率限制
 * - ConcurrentProcessor：并发处理chunks
 */
public class ParamDiscoveryEngine {
    
    private final MontoyaApi api;
    private final BurpHttpRequester requester;
    private final ResponseBaseline baseline;
    private final AnomalyDetector detector;
    private final ChunkProcessor chunkProcessor;
    private final ParamVerifier verifier;
    
    // ✅ 新增：Python功能组件
    private final ErrorHandler errorHandler;
    private final RetryStrategy retryStrategy;
    private final ConcurrentProcessor concurrentProcessor;
    
    // 配置
    private final int chunkSize;
    private final int threads;
    
    /**
     * 默认构造函数
     */
    public ParamDiscoveryEngine(MontoyaApi api) {
        this(api, 250, 9999, false, 5, 5, new HashMap<>());
    }
    
    /**
     * 完整构造函数（兼容旧版本）
     */
    public ParamDiscoveryEngine(MontoyaApi api, 
                                 int chunkSize,
                                 int maxRequestsPerSecond,
                                 boolean stableMode,
                                 int threads,
                                 int maxRetries) {
        this(api, chunkSize, maxRequestsPerSecond, stableMode, threads, maxRetries, new HashMap<>());
    }
    
    /**
     * 完整构造函数（含自定义HTTP头）
     * @param chunkSize chunk大小（默认250）
     * @param maxRequestsPerSecond 最大请求速率（默认9999）
     * @param stableMode 稳定模式（默认false）
     * @param threads 并发线程数（默认5）
     * @param maxRetries 最大重试次数（默认5）
     * @param customHeaders 自定义HTTP头（覆盖/添加）
     */
    public ParamDiscoveryEngine(MontoyaApi api, 
                                 int chunkSize,
                                 int maxRequestsPerSecond,
                                 boolean stableMode,
                                 int threads,
                                 int maxRetries,
                                 Map<String, String> customHeaders) {
        this.api = api;
        this.chunkSize = chunkSize;
        this.threads = threads;
        
        // ✅ 初始化Python功能组件（含自定义HTTP头）
        this.errorHandler = new ErrorHandler(api, 15, 60, stableMode);
        this.retryStrategy = new RetryStrategy(api, maxRetries);
        this.concurrentProcessor = new ConcurrentProcessor(api, threads);
        this.requester = new BurpHttpRequester(api, maxRequestsPerSecond, stableMode, 15, customHeaders);
        
        // 初始化核心组件
        this.baseline = new ResponseBaseline(api);
        this.detector = new AnomalyDetector(api);
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
                // ✅ 重置错误处理器状态（清除之前扫描的 killSwitch）
                errorHandler.reset();
                
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
                
                // 2. 检查字典
                // ✅ 不使用默认参数，完全由用户自定义（ParameterCollector收集 + 用户上传）
                api.logging().raiseInfoEvent("📦 阶段2: 准备字典...");
                
                int dictSize = context.getDictionary().size();
                
                // ⚠️ 如果字典为空，直接跳过扫描
                if (dictSize == 0) {
                    api.logging().raiseErrorEvent("❌ 字典为空，跳过扫描");
                    return DiscoveryResult.error("字典为空");
                }
                
                api.logging().raiseInfoEvent(String.format(
                    "📚 字典大小: %d 个参数（完全由用户自定义）",
                    dictSize
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
     * 初始化：稳定性探测 + 建立基线（✅ 带重试）
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
        
        // ✅ 使用RetryStrategy发送请求（带重试）
        api.logging().raiseDebugEvent("  发送基线请求1（带重试）...");
        HttpResponse response1 = retryStrategy.executeWithRetry(
            () -> sendRequestWithRetry(testRequest1, null, true),
            errorHandler,
            "基线请求1"
        );
        
        // ✅ P1: 健康状态码检查
        int statusCode = response1.statusCode();
        boolean isHealthy = !isUnhealthyStatusCode(statusCode);
        
        if (!isHealthy) {
            api.logging().raiseErrorEvent(
                "  ⚠️ 目标返回错误状态码: " + statusCode + "，这可能影响扫描"
            );
        }
        
        api.logging().raiseDebugEvent("  发送基线请求2（带重试）...");
        HttpResponse response2 = retryStrategy.executeWithRetry(
            () -> sendRequestWithRetry(testRequest2, null, isHealthy),
            errorHandler,
            "基线请求2"
        );
        
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
            
            // ✅ 使用RequestResult发送请求
            BurpHttpRequester.RequestResult reqResult = requester.sendRequest(testRequest);
            
            if (!reqResult.isSuccess()) {
                // 请求失败，继续尝试
                retryCount++;
                continue;
            }
            
            // 检测异常
            AnomalyResult anomaly = detector.compare(
                reqResult.getResponse(), 
                factors, 
                Map.of(randomParam, randomValue)
            );
            
            if (!anomaly.hasAnomaly()) {
                // 找到稳定状态
                api.logging().raiseInfoEvent("  ✓ 目标稳定（尝试 " + (retryCount + 1) + " 次）");
                break;
            }
            
            // ✅ 关键修复：检查因子数量，至少保留1个
            int remainingFactors = countRemainingFactors(factors);
            if (remainingFactors <= 1) {
                api.logging().raiseInfoEvent(
                    "  ⚠️ 已达最少因子数量（" + remainingFactors + "个），停止移除不稳定因子"
                );
                api.logging().raiseInfoEvent(
                    "  将使用当前剩余因子继续扫描（准确度可能降低）"
                );
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
        
        // 检查健康状态（重命名避免重复）
        boolean targetIsStable = factors.hasAnyFactor();
        
        if (!targetIsStable) {
            // ✅ 所有因子都被移除（理论上不应发生，因为至少保留1个）
            api.logging().raiseErrorEvent(
                "  ❌ 所有检测因子都被移除，目标过于不稳定，无法扫描"
            );
        } else if (retryCount >= maxRetries) {
            api.logging().raiseErrorEvent(
                "  ⚠️ 达到最大重试次数（" + maxRetries + "），目标可能不稳定"
            );
            api.logging().raiseInfoEvent(
                "  当前剩余 " + countRemainingFactors(factors) + " 个检测因子，将继续扫描"
            );
        }
        
        return new ScanContext(
            originalRequest,
            factors,
            dictionary,
            response1,
            targetIsStable && isHealthy
        );
    }
    
    /**
     * 递归缩小参数范围（✅ 并发处理，对应Python的narrower()）
     */
    private Set<ParamCandidate> narrowDown(ScanContext context) {
        Set<ParamCandidate> allCandidates = new LinkedHashSet<>();
        
        // 创建分块
        List<Set<String>> chunks = chunkProcessor.createChunks(context.getDictionary());
        
        api.logging().raiseInfoEvent(String.format(
            "  分块数量: %d (每块 %d 个参数), 并发线程: %d", 
            chunks.size(), chunkSize, threads
        ));
        
        // ✅ 使用ConcurrentProcessor并发处理chunks（对应Python的ThreadPoolExecutor）
        List<Set<String>> anomalousChunks = concurrentProcessor.processConcurrently(
            chunks,
            chunk -> testChunkForAnomaly(context, chunk),  // 处理函数
            (completed, total) -> {                        // 进度回调
                if (completed % 10 == 0 || completed == total) {
                    api.logging().raiseInfoEvent(String.format(
                        "  进度: %d/%d", completed, total));
                }
            },
            () -> errorHandler.isKilled()                  // kill开关
        );
        
        // 过滤null结果
        anomalousChunks = anomalousChunks.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        api.logging().raiseInfoEvent(
            "  第一轮完成: " + anomalousChunks.size() + " 个异常分块"
        );
        
        // 递归缩小异常分块
        for (Set<String> anomalousChunk : anomalousChunks) {
            if (errorHandler.isKilled()) {
                api.logging().raiseInfoEvent("⚠️ 扫描被终止（kill开关）");
                break;
            }
            allCandidates.addAll(recursiveNarrow(context, anomalousChunk, 1));
        }
        
        return allCandidates;
    }
    
    /**
     * 测试单个chunk是否有异常（带重试）
     */
    private Set<String> testChunkForAnomaly(ScanContext context, Set<String> chunk) {
        try {
            // ✅ 所有参数使用随机值
            Map<String, String> testParams = new HashMap<>();
            for (String param : chunk) {
                testParams.put(param, generateRandomValue());
            }
            
            HttpRequest testRequest = requester.buildTestRequest(
                context.getOriginalRequest(), testParams);
            
            // ✅ 使用重试机制发送请求（对应Python的bruter()）
            AnomalyResult anomaly = retryStrategy.executeWithRetry(
                () -> {
                    BurpHttpRequester.RequestResult result = requester.sendRequest(testRequest);
                    
                    if (!result.isSuccess()) {
                        return RetryStrategy.RetryableResult.error(
                            result.getException(), context.getFactors(), context.isHealthy());
                    }
                    
                    AnomalyResult anom = detector.compare(
                        result.getResponse(), context.getFactors(), testParams);
                    
                    return RetryStrategy.RetryableResult.success(
                        anom, result.getResponse(), context.getFactors(), context.isHealthy());
                },
                errorHandler,
                "chunk测试"
            );
            
            if (anomaly.hasAnomaly()) {
                api.logging().raiseDebugEvent(String.format(
                    "  ✓ 发现异常chunk (原因: %s)", anomaly.getReason()));
                return chunk;
            }
            
            return null;
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("chunk测试失败: " + e.getMessage());
            return null;
        }
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
        
        // 测试每个子块（✅ 带重试）
        List<Set<String>> anomalousSubChunks = new ArrayList<>();
        
        for (Set<String> subChunk : subChunks) {
            // 检查kill开关
            if (errorHandler.isKilled()) {
                break;
            }
            
            // 使用testChunkForAnomaly（已包含重试机制）
            Set<String> result = testChunkForAnomaly(context, subChunk);
            if (result != null) {
                anomalousSubChunks.add(result);
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
     * 发送请求并包装为RetryableResult（用于重试机制）
     */
    private RetryStrategy.RetryableResult<HttpResponse> sendRequestWithRetry(
            HttpRequest request,
            BaselineFactors factors,
            boolean isHealthy) {
        
        BurpHttpRequester.RequestResult result = requester.sendRequest(request);
        
        if (!result.isSuccess()) {
            return RetryStrategy.RetryableResult.error(
                result.getException(), factors, isHealthy);
        }
        
        return RetryStrategy.RetryableResult.success(
            result.getResponse(), result.getResponse(), factors, isHealthy);
    }
    
    /**
     * 检查是否为不健康状态码
     */
    private boolean isUnhealthyStatusCode(int statusCode) {
        return statusCode == 400 || statusCode == 413 || 
               statusCode == 418 || statusCode == 429 || statusCode == 503;
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
    
    /**
     * ✅ P0修复：关闭资源，防止线程池泄漏
     */
    public void shutdown() {
        api.logging().raiseDebugEvent("关闭ParamDiscoveryEngine资源...");
        
        if (concurrentProcessor != null) {
            concurrentProcessor.shutdown();
        }
        
        // ErrorHandler的kill标志已设置为volatile，无需额外清理
        // RateLimiter无需清理（无资源）
    }
    
    /**
     * ✅ 辅助方法：统计剩余因子数量
     * 用于确保至少保留1个因子，防止全部移除导致检测失效
     */
    private int countRemainingFactors(BaselineFactors factors) {
        int count = 0;
        if (factors.getSameCode() != null) count++;
        if (factors.getSameBody() != null) count++;
        if (factors.getSamePlaintext() != null) count++;
        if (factors.getLinesNum() != null) count++;
        if (factors.getLinesDiff() != null && !factors.getLinesDiff().isEmpty()) count++;
        if (factors.getSameHeaders() != null && !factors.getSameHeaders().isEmpty()) count++;
        if (factors.getSameRedirect() != null) count++;
        if (factors.getParamMissing() != null && !factors.getParamMissing().isEmpty()) count++;
        if (factors.isValueMissing()) count++;
        return count;
    }
}


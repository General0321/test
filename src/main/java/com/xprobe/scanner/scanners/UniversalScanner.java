package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.XProbeConfigManager;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;
import com.xprobe.scanner.core.*;
import com.xprobe.scanner.core.PayloadVariableResolver.PayloadContext;
import com.xprobe.scanner.models.ScanResult;
import com.xprobe.scanner.models.ScanTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 通用扫描器 - 基于配对架构的灵活扫描器
 * 
 * 新架构：使用请求-响应配对进行扫描
 * 每个配对代表一种检测方法
 * 
 * ✅ P0修复: Payload编码说明
 * ==========================
 * Burp API会自动处理以下编码:
 * - URL参数: HttpParameter.urlParameter() 会自动URL编码
 * - POST表单参数: HttpParameter.bodyParameter() 会自动URL编码
 * - Cookie参数: HttpParameter.cookieParameter() 会自动URL编码
 * - JSON Body: Jackson ObjectMapper会自动转义JSON特殊字符
 * 
 * 需要手动处理的场景:
 * - Header值: 需要移除\r\n防止Header注入 (已修复)
 * - Path: withPath()不会URL编码,但通常不需要
 * - Body字符串拼接: 根据Content-Type需要手动编码
 */
public class UniversalScanner extends AbstractScanner {
    
    public static final String SCANNER_TYPE = "UNIVERSAL_RULE_SCANNER";
    private final XProbeConfigManager xprobeConfigManager;  // ✅ 改为配置管理器
    
    // ✅ 内部类：配对评估结果（包含响应对象）
    private static class PairEvaluationResult {
        boolean matched;
        HttpResponse response;
        HttpRequest modifiedRequest;
        long responseTime;
        
        PairEvaluationResult(boolean matched, HttpResponse response, HttpRequest modifiedRequest, long responseTime) {
            this.matched = matched;
            this.response = response;
            this.modifiedRequest = modifiedRequest;
            this.responseTime = responseTime;
        }
        
        PairEvaluationResult(boolean matched) {
            this(matched, null, null, 0);
        }
    }
    
    public UniversalScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner, XProbeConfigManager xprobeConfigManager) {
        super(api, realtimeScanner);
        this.xprobeConfigManager = xprobeConfigManager;  // ✅ 改为配置管理器
    }
    
    @Override
    public String getType() {
        return SCANNER_TYPE;
    }
    
    @Override
    public String getName() {
        return "通用规则扫描器";
    }
    
    @Override
    public String getDescription() {
        return "基于配对架构的灵活扫描器，支持任意请求-响应配对组合。";
    }
    
    @Override
    public boolean canScan(ScanTask task) {
        Configuration config = task.getConfiguration();
        HttpRequest request = task.getRequest();
        
        // 1. 检查规则是否启用
        if (!config.isEnabled()) {
            return false;
        }
        
        // 2. 检查是否有配对
        List<RuleMatchPair> pairs = config.getPairs();
        if (pairs == null || pairs.isEmpty()) {
            return false;
        }
        
        // 3. 检查是否至少有一个配对的请求条件匹配
        for (RuleMatchPair pair : pairs) {
            UnifiedHttpConfig requestConfig = pair.getRequestConfig();
            if (requestConfig != null && UnifiedHttpEvaluator.evaluate(request, requestConfig)) {
                // 匹配成功，输出一条INFO日志
                api.logging().raiseInfoEvent("✅ 规则 [" + config.getCustomLabel() + "] 匹配，准备扫描: " + request.url());
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    protected ScanResult performScan(ScanTask task, HttpRequest originalRequest, 
                                     HttpRequest modifiedRequest, String payload) {
        // 此方法在配对架构中不使用
        return null;
    }
    
    @Override
    public List<String> getPayloads() {
        // Payloads现在来自配对配置
        return new ArrayList<>();
    }
    
    /**
     * 执行基于配对的扫描
     */
    @Override
    public CompletableFuture<List<ScanResult>> scan(ScanTask task) {
        return CompletableFuture.supplyAsync(() -> {
            List<ScanResult> results = new ArrayList<>();
            Configuration config = task.getConfiguration();
            HttpRequest originalRequest = task.getRequest().copyToTempFile();
            
            // 获取配对列表
            List<RuleMatchPair> pairs = config.getPairs();
            if (pairs == null || pairs.isEmpty()) {
                api.logging().raiseDebugEvent("规则 " + config.getCustomLabel() + " 没有配置任何配对");
                return results;
            }
            
            // 初始化Payload解析器
            PayloadVariableResolver payloadResolver = new PayloadVariableResolver(api);
            
            // ✅ 评估每个配对（保存评估结果和响应）
            Map<Integer, Boolean> pairResults = new HashMap<>();
            Map<Integer, PairEvaluationResult> pairEvaluations = new HashMap<>();
            List<PairEvaluationResult> allEvaluations = new ArrayList<>();  // ✅ 保存所有评估（包括未命中的）
            
            for (RuleMatchPair pair : pairs) {
                try {
                    // ✅ 传递allEvaluations列表，让评估方法能够记录所有请求
                    PairEvaluationResult evaluation = evaluatePair(pair, originalRequest, payloadResolver, config, allEvaluations);
                    pairResults.put(pair.getId(), evaluation.matched);
                    pairEvaluations.put(pair.getId(), evaluation);
                    
                    // ✅ 主评估结果已经在evaluatePair中添加到allEvaluations了
                    
                    if (evaluation.matched) {
                        api.logging().raiseDebugEvent("配对 [" + pair.getId() + "] " + 
                            pair.getDisplayLabel() + " 匹配成功");
                    }
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("评估配对 [" + pair.getId() + "] 时出错: " + 
                        e.getMessage());
                    pairResults.put(pair.getId(), false);
                    pairEvaluations.put(pair.getId(), new PairEvaluationResult(false));
                }
            }
            
            // ✅ 调试日志：配对结果
            System.out.println("🔍 [最终评估] 规则: " + config.getCustomLabel());
            System.out.println("🔍 [最终评估] 配对结果: " + pairResults);
            System.out.println("🔍 [最终评估] 配对表达式: " + (config.getPairExpression() != null && !config.getPairExpression().isEmpty() ? config.getPairExpression() : "默认(AND)"));
            
            // 根据配对表达式评估最终结果
            boolean finalResult = evaluatePairExpression(config.getPairExpression(), pairResults);
            System.out.println("🔍 [最终评估] 最终结果: " + (finalResult ? "✅ 漏洞" : "❌ 未命中"));
            
            // ✅ 为所有发送的请求创建结果条目
            if (!allEvaluations.isEmpty()) {
                // 第一个结果作为主结果
                PairEvaluationResult firstEval = allEvaluations.get(0);
                ScanResult mainResult = new ScanResult.Builder()
                    .vulnerable(finalResult)  // 使用最终评估结果
                    .scanType(config.getCustomLabel())
                    .evidence(finalResult ? "检测到漏洞" : "测试请求")
                    .originalRequest(originalRequest)
                    .modifiedRequest(firstEval.modifiedRequest)
                    .response(firstEval.response)
                    .responseTime(firstEval.responseTime)
                    .build();
                results.add(mainResult);
                
                if (finalResult) {
                    api.logging().raiseInfoEvent("✓ 规则 " + config.getCustomLabel() + " 检测到漏洞！");
                } else {
                    System.out.println("⚠️ [最终评估] 虽然有评估结果，但finalResult=false，未记录为漏洞");
                }
                
                // 其余的请求作为额外条目（标记为未命中）
                for (int i = 1; i < allEvaluations.size(); i++) {
                    PairEvaluationResult eval = allEvaluations.get(i);
                    ScanResult additionalResult = new ScanResult.Builder()
                        .vulnerable(false)  // 额外的流量条目标记为未命中
                        .scanType(config.getCustomLabel())
                        .evidence("测试请求 #" + (i + 1))
                        .originalRequest(originalRequest)
                        .modifiedRequest(eval.modifiedRequest)
                        .response(eval.response)
                        .responseTime(eval.responseTime)
                        .build();
                    results.add(additionalResult);
                }
            }
            
            return results;
        });
    }
    
    /**
     * 评估单个配对
     * @param allEvaluations 用于保存所有评估结果的列表（包括未命中的）
     */
    private PairEvaluationResult evaluatePair(RuleMatchPair pair, HttpRequest originalRequest, 
                                              PayloadVariableResolver payloadResolver, Configuration config,
                                              List<PairEvaluationResult> allEvaluations) {
        UnifiedHttpConfig requestConfig = pair.getRequestConfig();
        UnifiedResponseConfig responseConfig = pair.getResponseConfig();
        
        if (requestConfig == null || responseConfig == null) {
            return new PairEvaluationResult(false);
        }
        
        // 1. 检查请求是否匹配
        if (!UnifiedHttpEvaluator.evaluate(originalRequest, requestConfig)) {
            return new PairEvaluationResult(false);
        }
        
        // 2. 获取注入点（启用了注入的元素）
        List<UnifiedHttpConfig.HttpElementConfig> injectionPoints = requestConfig.getElements()
            .stream()
            .filter(UnifiedHttpConfig.HttpElementConfig::isUseForInjection)
            .collect(Collectors.toList());
        
        // ✅ 如果没有注入点，执行被动检测（检查原始响应）
        if (injectionPoints.isEmpty()) {
            try {
                // 发送原始请求
                long startTime = System.currentTimeMillis();
                HttpResponse response = api.http().sendRequest(originalRequest).response();
                long responseTime = System.currentTimeMillis() - startTime;
                
                // ✅ 安全检查：确保响应不为null
                if (response == null) {
                    api.logging().raiseErrorEvent("⚠️ 配对 [" + pair.getId() + "] 被动检测收到null响应");
                    return new PairEvaluationResult(false);
                }
                
                // 评估响应（无payload上下文）
                boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                    response, responseConfig, null, responseTime
                );
                
                if (responseMatched) {
                    api.logging().raiseDebugEvent("配对 [" + pair.getId() + "] 被动检测成功");
                    return new PairEvaluationResult(true, response, originalRequest, responseTime);
                }
            } catch (Exception e) {
                api.logging().raiseErrorEvent("❌ 被动检测时发送请求失败: " + e.getMessage());
            }
            return new PairEvaluationResult(false);
        }
        
        // 3. ✅ 收集所有需要注入的目标（参数、Header、Cookie等）
        List<InjectionTarget> allTargets = new ArrayList<>();
        for (UnifiedHttpConfig.HttpElementConfig injectionPoint : injectionPoints) {
            List<InjectionTarget> targets = collectInjectionTargets(originalRequest, injectionPoint);
            allTargets.addAll(targets);
        }
        
        if (allTargets.isEmpty()) {
            return new PairEvaluationResult(false);
        }
        
        // 4. ✅ 统一去重过滤（与批量/逐个模式无关）
        //    去重决定"打不打"，批量/逐个决定"怎么打"
        List<InjectionTarget> validTargets = filterDuplicateTargets(allTargets, config, originalRequest);
        
        if (validTargets.isEmpty()) {
            // 所有目标都被去重过滤掉了
            return new PairEvaluationResult(false);
        }
        
        // 5. 根据全局注入模式执行注入并检查响应
        Configuration.InjectionMode injectionMode = getGlobalInjectionMode();
        
        // 📌 批量模式 vs 逐个模式
        if (injectionMode == Configuration.InjectionMode.BATCH) {
            // ========== 批量模式（BATCH）：所有validTargets同时注入相同payload ==========
            return evaluateBatchMode(validTargets, injectionPoints, originalRequest, responseConfig, payloadResolver, config, pair, allEvaluations);
        } else {
            // ========== 逐个模式（INDIVIDUAL）：每个validTarget分别注入 ==========
            return evaluateIndividualMode(validTargets, injectionPoints, originalRequest, responseConfig, payloadResolver, config, pair, allEvaluations);
        }
    }
    
    /**
     * ✅ 去重过滤：根据配置的去重颗粒度，过滤掉已经测试过的目标
     * 这个方法与批量/逐个模式无关，统一处理去重逻辑
     * 
     * ⚠️ 注意：这里只检查不标记，真正的标记在注入发送请求后进行
     */
    private List<InjectionTarget> filterDuplicateTargets(List<InjectionTarget> allTargets, 
                                                         Configuration config, 
                                                         HttpRequest originalRequest) {
        // 获取请求上下文信息
        String method = originalRequest.method();
        String host = originalRequest.httpService().host();
        String path = originalRequest.path();
        String contentType = originalRequest.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
            .map(h -> h.value())
            .findFirst()
            .orElse(null);
        
        List<InjectionTarget> validTargets = new ArrayList<>();
        
        for (InjectionTarget target : allTargets) {
            // ✅ 只检查是否重复，不标记（使用生成key的方式检查）
            String dedupKey = com.xprobe.scanner.core.DeduplicationKeyGenerator.generateKey(
                method, host, path, contentType, config, target.name
            );
            
            // 检查这个key是否已经在去重集合中
            boolean isDuplicate = realtimeScanner != null && 
                realtimeScanner.isAlreadyProcessed(dedupKey);
            
            if (!isDuplicate) {
                // 未测试过，添加到有效目标列表
                // 同时保存dedupKey到target中，后续标记时使用
                target.dedupKey = dedupKey;
                validTargets.add(target);
            }
        }
        
        return validTargets;
    }
    
    /**
     * ✅ 标记目标为已测试
     * 在真正发送请求并注入payload后调用
     */
    private void markTargetAsProcessed(InjectionTarget target) {
        if (realtimeScanner != null && target.dedupKey != null) {
            realtimeScanner.markAsProcessed(target.dedupKey);
        }
    }
    
    /**
     * ✅ 获取全局注入模式（零开销）
     */
    private Configuration.InjectionMode getGlobalInjectionMode() {
        return xprobeConfigManager.getGlobalInjectionMode();
    }
    
    /**
     * 批量模式评估：所有validTargets同时注入相同payload
     * ✅ 去重已在外部统一处理，这里只负责"怎么打"
     * @param validTargets 已经过去重过滤的有效目标列表
     * @param allEvaluations 用于保存所有评估结果的列表（包括未命中的）
     */
    private PairEvaluationResult evaluateBatchMode(List<InjectionTarget> validTargets,
                                                   List<UnifiedHttpConfig.HttpElementConfig> injectionPoints,
                                                   HttpRequest originalRequest,
                                                   UnifiedResponseConfig responseConfig,
                                                   PayloadVariableResolver payloadResolver,
                                                   Configuration config,
                                                   RuleMatchPair pair,
                                                   List<PairEvaluationResult> allEvaluations) {
        // ✅ 保存最后一个响应（用于记录未命中的请求）
        HttpResponse lastResponse = null;
        HttpRequest lastModifiedRequest = null;
        long lastResponseTime = 0;
        
        // 批量模式：所有validTargets都会被同时注入相同的payload
        for (UnifiedHttpConfig.HttpElementConfig injectionPoint : injectionPoints) {
            List<String> payloads = injectionPoint.getPayloads();
            if (payloads == null || payloads.isEmpty()) {
                continue;
            }
            
            // 获取属于这个injectionPoint的validTargets
            List<InjectionTarget> pointTargets = validTargets.stream()
                .filter(t -> t.injectionPoint == injectionPoint)
                .collect(Collectors.toList());
            
            if (pointTargets.isEmpty()) {
                continue;
            }
            
            // 获取注入点的原始值（使用第一个有效目标的值）
            String originalValue = pointTargets.get(0).originalValue;
            
            // 对每个payload进行测试
            for (String rawPayload : payloads) {
                try {
                    // 解析payload变量
                    Map<String, String> context = new HashMap<>();
                    context.put("original", originalValue);
                    PayloadContext payloadContext = payloadResolver.resolvePayload(rawPayload, context);
                    String resolvedPayload = payloadContext.getResolvedPayload();
                    
                    // 执行注入（批量模式：所有匹配参数都会被注入）
                    HttpRequest modifiedRequest = injectPayload(originalRequest, injectionPoint, resolvedPayload);
                    if (modifiedRequest == null) {
                        continue;
                    }
                    
                    // 发送请求
                    long startTime = System.currentTimeMillis();
                    HttpResponse response = api.http().sendRequest(modifiedRequest).response();
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    // ✅ 立即标记所有pointTargets为已处理（遵循颗粒度）
                    // 批量模式：这一个请求测试了所有pointTargets
                    for (InjectionTarget target : pointTargets) {
                        markTargetAsProcessed(target);
                    }
                    
                    // ✅ 安全检查：确保响应不为null
                    if (response == null) {
                        api.logging().raiseErrorEvent("⚠️ 批量注入收到null响应");
                        continue;
                    }
                    
                    // ✅ 保存最后一个有效响应
                    lastResponse = response;
                    lastModifiedRequest = modifiedRequest;
                    lastResponseTime = responseTime;
                    
                    // ✅ 将此评估添加到共享列表（确保所有请求都被记录）
                    PairEvaluationResult evalResult = new PairEvaluationResult(false, response, modifiedRequest, responseTime);
                    allEvaluations.add(evalResult);
                    
                // 评估响应
                System.out.println("🔍 [批量注入] 开始评估响应，配对ID: " + pair.getId());
                boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                    response, responseConfig, payloadContext, responseTime
                );
                System.out.println("🔍 [批量注入] 响应评估结果: " + (responseMatched ? "✅ 匹配" : "❌ 不匹配"));
                
                if (responseMatched) {
                    api.logging().raiseDebugEvent(
                        "配对 [" + pair.getId() + "] 批量注入匹配: " + 
                        injectionPoint.getType().getDisplayName() + 
                        ", Payload: " + resolvedPayload.substring(0, Math.min(50, resolvedPayload.length()))
                    );
                    return new PairEvaluationResult(true, response, modifiedRequest, responseTime);
                }
                    
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("❌ 批量注入时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // ✅ 即使没有匹配，也返回最后一个响应（确保请求被记录）
        if (lastResponse != null) {
            return new PairEvaluationResult(false, lastResponse, lastModifiedRequest, lastResponseTime);
        }
        
        return new PairEvaluationResult(false);
    }
    
    /**
     * 逐个模式评估：每个validTarget分别注入
     * ✅ 去重已在外部统一处理，这里只负责"怎么打"
     * @param validTargets 已经过去重过滤的有效目标列表
     * @param allEvaluations 用于保存所有评估结果的列表（包括未命中的）
     */
    private PairEvaluationResult evaluateIndividualMode(List<InjectionTarget> validTargets,
                                                        List<UnifiedHttpConfig.HttpElementConfig> injectionPoints,
                                                        HttpRequest originalRequest,
                                                        UnifiedResponseConfig responseConfig,
                                                        PayloadVariableResolver payloadResolver,
                                                        Configuration config,
                                                        RuleMatchPair pair,
                                                        List<PairEvaluationResult> allEvaluations) {
        // ✅ 保存最后一个响应（用于记录未命中的请求）
        HttpResponse lastResponse = null;
        HttpRequest lastModifiedRequest = null;
        long lastResponseTime = 0;
        
        // 逐个模式：每个validTarget分别测试
        for (InjectionTarget target : validTargets) {
            UnifiedHttpConfig.HttpElementConfig injectionPoint = target.injectionPoint;
            List<String> payloads = injectionPoint.getPayloads();
            if (payloads == null || payloads.isEmpty()) {
                continue;
            }
            
            // ✅ 标记：开始测试这个target（在第一个payload发送前标记）
            boolean targetMarked = false;
            
            // 对每个payload进行测试
            for (String rawPayload : payloads) {
                    try {
                        // 解析payload变量
                        Map<String, String> context = new HashMap<>();
                        context.put("original", target.originalValue);
                        PayloadContext payloadContext = payloadResolver.resolvePayload(rawPayload, context);
                        String resolvedPayload = payloadContext.getResolvedPayload();
                        
                        // 执行单个注入
                        HttpRequest modifiedRequest = injectPayloadToSingleTarget(
                            originalRequest, injectionPoint, resolvedPayload, target
                        );
                        if (modifiedRequest == null) {
                            continue;
                        }
                        
                        // 发送请求
                        long startTime = System.currentTimeMillis();
                        HttpResponse response = api.http().sendRequest(modifiedRequest).response();
                        long responseTime = System.currentTimeMillis() - startTime;
                        
                        // ✅ 立即标记此target为已处理（只标记一次）
                        // 逐个模式：一旦开始测试某个target，立即标记，防止重复打
                        if (!targetMarked) {
                            markTargetAsProcessed(target);
                            targetMarked = true;
                        }
                        
                        // ✅ 安全检查：确保响应不为null
                        if (response == null) {
                            api.logging().raiseErrorEvent("⚠️ 逐个注入收到null响应");
                            continue;
                        }
                        
                        // ✅ 保存最后一个有效响应
                        lastResponse = response;
                        lastModifiedRequest = modifiedRequest;
                        lastResponseTime = responseTime;
                        
                        // ✅ 将此评估添加到共享列表（确保所有请求都被记录）
                        PairEvaluationResult evalResult = new PairEvaluationResult(false, response, modifiedRequest, responseTime);
                        allEvaluations.add(evalResult);
                        
                        // 评估响应
                        System.out.println("🔍 [逐个注入] 开始评估响应，配对ID: " + pair.getId() + ", 目标: " + target.name);
                        boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                            response, responseConfig, payloadContext, responseTime
                        );
                        System.out.println("🔍 [逐个注入] 响应评估结果: " + (responseMatched ? "✅ 匹配" : "❌ 不匹配"));
                        
                        if (responseMatched) {
                            api.logging().raiseDebugEvent(
                                "配对 [" + pair.getId() + "] 逐个注入匹配: " + 
                                injectionPoint.getType().getDisplayName() + 
                                " [" + target.name + "], Payload: " + resolvedPayload.substring(0, Math.min(50, resolvedPayload.length()))
                            );
                            return new PairEvaluationResult(true, response, modifiedRequest, responseTime);
                        }
                        
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("❌ 逐个注入时出错: " + e.getMessage());
                    }
                }
        }
        
        // ✅ 即使没有匹配，也返回最后一个响应（确保请求被记录）
        if (lastResponse != null) {
            return new PairEvaluationResult(false, lastResponse, lastModifiedRequest, lastResponseTime);
        }
        
        return new PairEvaluationResult(false);
    }
    
    /**
     * 收集注入目标（用于逐个模式）
     */
    private List<InjectionTarget> collectInjectionTargets(HttpRequest request, 
                                                          UnifiedHttpConfig.HttpElementConfig element) {
        List<InjectionTarget> targets = new ArrayList<>();
        UnifiedHttpConfig.ElementType type = element.getType();
        
        switch (type) {
            case PARAMETER:
                for (var param : request.parameters()) {
                    if (shouldMatchTarget(param.name(), element)) {
                        targets.add(new InjectionTarget(param.name(), param.value(), param.type(), element));
                    }
                }
                break;
                
            case HEADER:
                for (var header : request.headers()) {
                    if (shouldMatchTarget(header.name(), element)) {
                        targets.add(new InjectionTarget(header.name(), header.value(), null, element));
                    }
                }
                break;
                
            case COOKIE:
                for (var param : request.parameters()) {
                    if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.COOKIE &&
                        shouldMatchTarget(param.name(), element)) {
                        targets.add(new InjectionTarget(param.name(), param.value(), param.type(), element));
                    }
                }
                break;
                
            case METHOD:
            case PATH:
            case BODY:
                // 这些类型不需要逐个处理，只有一个目标
                targets.add(new InjectionTarget("", UnifiedHttpEvaluator.getOriginalValue(request, element), null, element));
                break;
        }
        
        return targets;
    }
    
    /**
     * 判断目标名称是否匹配元素配置
     * ✅ 修复：支持区分大小写配置
     */
    private boolean shouldMatchTarget(String targetName, UnifiedHttpConfig.HttpElementConfig element) {
        // 1. 优先使用element.name（支持区分大小写）
        String elementName = element.getName();
        if (elementName != null && !elementName.isEmpty()) {
            // ✅ 使用nameMatchConfig的caseSensitive设置
            boolean caseSensitive = element.getNameMatchConfig() != null 
                ? element.getNameMatchConfig().isCaseSensitive() 
                : true;  // 默认区分大小写（参数/Header/Cookie名称通常区分）
                
            if (caseSensitive) {
                return targetName.equals(elementName);
            } else {
                return targetName.equalsIgnoreCase(elementName);
            }
        }
        
        // 2. 使用nameMatchConfig
        if (element.getNameMatchConfig() != null && 
            element.getNameMatchConfig().getValues() != null &&
            !element.getNameMatchConfig().getValues().isEmpty()) {
            boolean caseSensitive = element.getNameMatchConfig().isCaseSensitive();
            UnifiedHttpConfig.MatchType matchType = element.getNameMatchConfig().getMatchType();
            
            // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
            boolean isNegativeMatch = (matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS || 
                                      matchType == UnifiedHttpConfig.MatchType.NOT_CONTAINS);
            
            if (isNegativeMatch) {
                // ✅ 反向匹配：所有值都不匹配才返回true（AND逻辑）
                for (String matchValue : element.getNameMatchConfig().getValues()) {
                    if (matchValue == null || matchValue.isEmpty()) {
                        continue;
                    }
                    
                    // 检查是否匹配（使用正向匹配逻辑）
                    UnifiedHttpConfig.MatchType positiveType = matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS 
                        ? UnifiedHttpConfig.MatchType.EQUALS 
                        : UnifiedHttpConfig.MatchType.CONTAINS;
                    
                    if (matchesValue(targetName, matchValue, positiveType, caseSensitive)) {
                        return false;  // 找到一个匹配的，不满足"都不匹配"
                    }
                }
                return true;  // 所有值都不匹配
                
            } else {
                // ✅ 正向匹配：任意一个匹配就返回true（OR逻辑）
                for (String matchValue : element.getNameMatchConfig().getValues()) {
                    if (matchValue != null && !matchValue.isEmpty() &&
                        matchesValue(targetName, matchValue, matchType, caseSensitive)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 注入payload到单个目标（用于逐个模式）
     */
    private HttpRequest injectPayloadToSingleTarget(HttpRequest originalRequest,
                                                   UnifiedHttpConfig.HttpElementConfig element,
                                                   String payload,
                                                   InjectionTarget target) {
        HttpRequest modified = originalRequest;
        UnifiedHttpConfig.ElementType type = element.getType();
        UnifiedHttpConfig.InjectionTarget injTarget = element.getInjectionTarget();
        
        try {
            switch (type) {
                case METHOD:
                    modified = modified.withMethod(payload);
                    break;
                    
                case PATH:
                    modified = modified.withPath(payload);
                    break;
                    
                case PARAMETER:
                    if (injTarget == UnifiedHttpConfig.InjectionTarget.VALUE) {
                        var newParam = burp.api.montoya.http.message.params.HttpParameter
                            .parameter(target.name, payload, target.paramType);
                        modified = modified.withUpdatedParameters(newParam);
                    } else if (injTarget == UnifiedHttpConfig.InjectionTarget.NAME) {
                        modified = modified.withRemovedParameters(
                            burp.api.montoya.http.message.params.HttpParameter.parameter(target.name, target.originalValue, target.paramType)
                        );
                        var newParam = burp.api.montoya.http.message.params.HttpParameter
                            .parameter(payload, target.originalValue, target.paramType);
                        modified = modified.withAddedParameters(newParam);
                    }
                    break;
                    
                case HEADER:
                    // ✅ P0修复: Header注入时移除换行符
                    if (injTarget == UnifiedHttpConfig.InjectionTarget.VALUE) {
                        String safePayload = payload.replace("\r", "").replace("\n", "");
                        modified = modified.withUpdatedHeader(target.name, safePayload);
                    } else if (injTarget == UnifiedHttpConfig.InjectionTarget.NAME) {
                        String safeName = payload.replace("\r", "").replace("\n", "");
                        modified = modified.withRemovedHeader(target.name);
                        modified = modified.withAddedHeader(safeName, target.originalValue);
                    }
                    break;
                    
                case COOKIE:
                    var cookieParam = burp.api.montoya.http.message.params.HttpParameter
                        .cookieParameter(target.name, payload);
                    modified = modified.withUpdatedParameters(cookieParam);
                    break;
                    
                case BODY:
                    modified = modified.withBody(payload);
                    break;
            }
            
            return modified;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("单目标注入时出错: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 注入目标类（内部类）
     */
    private static class InjectionTarget {
        String name;
        String originalValue;
        burp.api.montoya.http.message.params.HttpParameterType paramType;
        UnifiedHttpConfig.HttpElementConfig injectionPoint;  // ✅ 关联的注入点配置
        String dedupKey;  // ✅ 去重key（在filterDuplicateTargets中生成）
        
        InjectionTarget(String name, String originalValue, burp.api.montoya.http.message.params.HttpParameterType paramType, UnifiedHttpConfig.HttpElementConfig injectionPoint) {
            this.name = name;
            this.originalValue = originalValue;
            this.paramType = paramType;
            this.injectionPoint = injectionPoint;
            this.dedupKey = null;
        }
    }
    
    /**
     * 辅助方法：检查值是否匹配
     */
    /**
     * ✅ 修复：支持区分大小写配置
     */
    private boolean matchesValue(String actualValue, String matchValue, 
                                 UnifiedHttpConfig.MatchType matchType, 
                                 boolean caseSensitive) {
        if (actualValue == null || matchValue == null) {
            return false;
        }
        
        // 准备比较用的值
        String compareActual = caseSensitive ? actualValue : actualValue.toLowerCase();
        String compareMatch = caseSensitive ? matchValue : matchValue.toLowerCase();
        
        switch (matchType) {
            case ANY:
                return true;
            case EQUALS:
                return compareActual.equals(compareMatch);
            case CONTAINS:
                return compareActual.contains(compareMatch);
            case REGEX:
                try {
                    java.util.regex.Pattern pattern = caseSensitive
                        ? java.util.regex.Pattern.compile(matchValue)
                        : java.util.regex.Pattern.compile(matchValue, java.util.regex.Pattern.CASE_INSENSITIVE);
                    return pattern.matcher(actualValue).find();
                } catch (Exception e) {
                    return false;
                }
            case STARTS_WITH:
                return compareActual.startsWith(compareMatch);
            case ENDS_WITH:
                return compareActual.endsWith(compareMatch);
            case NOT_EQUALS:
                return !compareActual.equals(compareMatch);
            case NOT_CONTAINS:
                return !compareActual.contains(compareMatch);
            default:
                return compareActual.contains(compareMatch);
        }
    }
    
    /**
     * 注入payload到请求中
     */
    private HttpRequest injectPayload(HttpRequest originalRequest, 
                                     UnifiedHttpConfig.HttpElementConfig element, 
                                     String payload) {
        HttpRequest modified = originalRequest;
        
        UnifiedHttpConfig.ElementType type = element.getType();
        UnifiedHttpConfig.InjectionTarget target = element.getInjectionTarget();
        
        try {
            switch (type) {
                case METHOD:
                    // 注入到Method（通常不常见，但支持）
                    modified = modified.withMethod(payload);
                    break;
                    
                case PATH:
                    // ✅ P0修复: 注入到路径（检查是否需要URL编码）
                    if (target == UnifiedHttpConfig.InjectionTarget.VALUE || 
                        target == UnifiedHttpConfig.InjectionTarget.ENTIRE) {
                        // 注意: withPath可能不会自动URL编码,如果payload包含特殊字符可能导致请求无效
                        // 但URL编码的Path可能也不符合预期,所以保持原样,让用户在payload中控制
                        modified = modified.withPath(payload);
                    }
                    break;
                    
                case PARAMETER:
                    // ✅ 改进：支持通过nameMatchConfig匹配多个参数名
                    // 遍历所有请求参数
                    for (var param : originalRequest.parameters()) {
                        boolean shouldInject = false;
                        
                        // 1. 如果element.name有值，优先使用name匹配
                        String elementName = element.getName();
                        if (elementName != null && !elementName.isEmpty()) {
                            shouldInject = param.name().equals(elementName);
                        } 
                        // 2. 否则，使用nameMatchConfig匹配
                        else if (element.getNameMatchConfig() != null && 
                                 element.getNameMatchConfig().getValues() != null &&
                                 !element.getNameMatchConfig().getValues().isEmpty()) {
                            // 检查参数名是否匹配配置的任何一个值
                            boolean caseSensitive = element.getNameMatchConfig().isCaseSensitive();
                            UnifiedHttpConfig.MatchType matchType = element.getNameMatchConfig().getMatchType();
                            
                            // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
                            boolean isNegativeMatch = (matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS || 
                                                      matchType == UnifiedHttpConfig.MatchType.NOT_CONTAINS);
                            
                            if (isNegativeMatch) {
                                // 反向匹配：所有值都不匹配才注入
                                shouldInject = true;
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty()) {
                                        UnifiedHttpConfig.MatchType positiveType = matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS 
                                            ? UnifiedHttpConfig.MatchType.EQUALS 
                                            : UnifiedHttpConfig.MatchType.CONTAINS;
                                        
                                        if (matchesValue(param.name(), matchValue, positiveType, caseSensitive)) {
                                            shouldInject = false;  // 找到一个匹配的，不注入
                                            break;
                                        }
                                    }
                                }
                            } else {
                                // 正向匹配：任意一个匹配就注入
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty() && 
                                        matchesValue(param.name(), matchValue, matchType, caseSensitive)) {
                                        shouldInject = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            if (target == UnifiedHttpConfig.InjectionTarget.VALUE) {
                                // ✅ 使用withUpdatedParameters()直接更新参数值
                                var newParam = burp.api.montoya.http.message.params.HttpParameter
                                    .parameter(param.name(), payload, param.type());
                                modified = modified.withUpdatedParameters(newParam);
                            } else if (target == UnifiedHttpConfig.InjectionTarget.NAME) {
                                // 替换参数名（必须先删除旧的，再添加新的）
                                modified = modified.withRemovedParameters(param);
                                var newParam = burp.api.montoya.http.message.params.HttpParameter
                                    .parameter(payload, param.value(), param.type());
                                modified = modified.withAddedParameters(newParam);
                            }
                        }
                    }
                    break;
                    
                case HEADER:
                    // ✅ P0修复: 支持通过nameMatchConfig匹配多个Header名，并防止Header注入
                    for (var header : originalRequest.headers()) {
                        boolean shouldInject = false;
                        
                        // 1. 如果element.name有值，优先使用name匹配（支持区分大小写）
                        String elementName = element.getName();
                        if (elementName != null && !elementName.isEmpty()) {
                            boolean caseSensitive = element.getNameMatchConfig() != null 
                                ? element.getNameMatchConfig().isCaseSensitive() 
                                : false;  // Header名称默认不区分大小写
                            shouldInject = caseSensitive 
                                ? header.name().equals(elementName)
                                : header.name().equalsIgnoreCase(elementName);
                        } 
                        // 2. 否则，使用nameMatchConfig匹配
                        else if (element.getNameMatchConfig() != null && 
                                 element.getNameMatchConfig().getValues() != null &&
                                 !element.getNameMatchConfig().getValues().isEmpty()) {
                            // 检查Header名是否匹配配置的任何一个值
                            boolean caseSensitive = element.getNameMatchConfig().isCaseSensitive();
                            UnifiedHttpConfig.MatchType matchType = element.getNameMatchConfig().getMatchType();
                            
                            // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
                            boolean isNegativeMatch = (matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS || 
                                                      matchType == UnifiedHttpConfig.MatchType.NOT_CONTAINS);
                            
                            if (isNegativeMatch) {
                                // 反向匹配：所有值都不匹配才注入
                                shouldInject = true;
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty()) {
                                        UnifiedHttpConfig.MatchType positiveType = matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS 
                                            ? UnifiedHttpConfig.MatchType.EQUALS 
                                            : UnifiedHttpConfig.MatchType.CONTAINS;
                                        
                                        if (matchesValue(header.name(), matchValue, positiveType, caseSensitive)) {
                                            shouldInject = false;  // 找到一个匹配的，不注入
                                            break;
                                        }
                                    }
                                }
                            } else {
                                // 正向匹配：任意一个匹配就注入
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty() && 
                                        matchesValue(header.name(), matchValue, matchType, caseSensitive)) {
                                        shouldInject = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            if (target == UnifiedHttpConfig.InjectionTarget.VALUE) {
                                // ✅ P0修复: 移除payload中的换行符，防止Header注入攻击
                                String safePayload = payload.replace("\r", "").replace("\n", "");
                                modified = modified.withUpdatedHeader(header.name(), safePayload);
                            } else if (target == UnifiedHttpConfig.InjectionTarget.NAME) {
                                // Header名称也需要防止换行符
                                String safeName = payload.replace("\r", "").replace("\n", "");
                                modified = modified.withRemovedHeader(header.name());
                                modified = modified.withAddedHeader(safeName, header.value());
                            }
                        }
                    }
                    break;
                    
                case COOKIE:
                    // ✅ 改进：支持通过nameMatchConfig匹配多个Cookie名
                    for (var param : originalRequest.parameters()) {
                        if (param.type() != burp.api.montoya.http.message.params.HttpParameterType.COOKIE) {
                            continue;
                        }
                        
                        boolean shouldInject = false;
                        
                        // 1. 如果element.name有值，优先使用name匹配（支持区分大小写）
                        String elementName = element.getName();
                        if (elementName != null && !elementName.isEmpty()) {
                            boolean caseSensitive = element.getNameMatchConfig() != null 
                                ? element.getNameMatchConfig().isCaseSensitive() 
                                : true;  // Cookie名称默认区分大小写
                            shouldInject = caseSensitive 
                                ? param.name().equals(elementName)
                                : param.name().equalsIgnoreCase(elementName);
                        } 
                        // 2. 否则，使用nameMatchConfig匹配
                        else if (element.getNameMatchConfig() != null && 
                                 element.getNameMatchConfig().getValues() != null &&
                                 !element.getNameMatchConfig().getValues().isEmpty()) {
                            // 检查Cookie名是否匹配配置的任何一个值
                            boolean caseSensitive = element.getNameMatchConfig().isCaseSensitive();
                            UnifiedHttpConfig.MatchType matchType = element.getNameMatchConfig().getMatchType();
                            
                            // ✅ 修复：区分正向匹配（OR）和反向匹配（AND）
                            boolean isNegativeMatch = (matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS || 
                                                      matchType == UnifiedHttpConfig.MatchType.NOT_CONTAINS);
                            
                            if (isNegativeMatch) {
                                // 反向匹配：所有值都不匹配才注入
                                shouldInject = true;
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty()) {
                                        UnifiedHttpConfig.MatchType positiveType = matchType == UnifiedHttpConfig.MatchType.NOT_EQUALS 
                                            ? UnifiedHttpConfig.MatchType.EQUALS 
                                            : UnifiedHttpConfig.MatchType.CONTAINS;
                                        
                                        if (matchesValue(param.name(), matchValue, positiveType, caseSensitive)) {
                                            shouldInject = false;  // 找到一个匹配的，不注入
                                            break;
                                        }
                                    }
                                }
                            } else {
                                // 正向匹配：任意一个匹配就注入
                                for (String matchValue : element.getNameMatchConfig().getValues()) {
                                    if (matchValue != null && !matchValue.isEmpty() && 
                                        matchesValue(param.name(), matchValue, matchType, caseSensitive)) {
                                        shouldInject = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            var cookieParam = burp.api.montoya.http.message.params.HttpParameter
                                .cookieParameter(param.name(), payload);
                            modified = modified.withUpdatedParameters(cookieParam);
                        }
                    }
                    break;
                    
                case BODY:
                    // 注入到Body
                    if (target == UnifiedHttpConfig.InjectionTarget.ENTIRE) {
                        modified = modified.withBody(payload);
                    } else {
                        // 部分替换（简单实现：直接追加）
                        String originalBody = modified.bodyToString();
                        modified = modified.withBody(originalBody + payload);
                    }
                    break;
            }
            
            return modified;
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("注入payload时出错: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 评估配对表达式
     */
    private boolean evaluatePairExpression(String expression, Map<Integer, Boolean> pairResults) {
        if (pairResults.isEmpty()) {
            return false;
        }
        
        // 如果表达式为空，默认使用AND逻辑
        if (expression == null || expression.trim().isEmpty()) {
            return pairResults.values().stream().allMatch(b -> b);
        }
        
        try {
            // 简化版表达式评估
            String expr = expression;
            
            // 替换配对ID为其结果值
            for (Map.Entry<Integer, Boolean> entry : pairResults.entrySet()) {
                String id = String.valueOf(entry.getKey());
                String value = entry.getValue() ? "true" : "false";
                expr = expr.replaceAll("\\b" + id + "\\b", value);
            }
            
            // 评估布尔表达式
            return evaluateBooleanExpression(expr);
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("评估配对表达式时出错: " + e.getMessage());
            // 默认使用AND逻辑
            return pairResults.values().stream().allMatch(b -> b);
        }
    }
    
    /**
     * 评估布尔表达式（入口方法）
     */
    private boolean evaluateBooleanExpression(String expr) {
        return evaluateBooleanExpressionInternal(expr, 0);
    }
    
    /**
     * 评估布尔表达式（内部实现，带递归深度检查）
     * ✅ 修复：添加递归深度限制，防止栈溢出
     */
    private static final int MAX_RECURSION_DEPTH = 10;
    
    private boolean evaluateBooleanExpressionInternal(String expr, int depth) {
        // ✅ 检查递归深度
        if (depth > MAX_RECURSION_DEPTH) {
            throw new IllegalArgumentException(
                "表达式嵌套过深（最大: " + MAX_RECURSION_DEPTH + "层）"
            );
        }
        
        expr = expr.trim();
        
        // ✅ 处理括号（添加循环次数限制，防止死循环）
        int iterations = 0;
        while (expr.contains("(")) {
            if (++iterations > 100) {
                throw new IllegalArgumentException(
                    "表达式格式错误（括号处理超过100次迭代）"
                );
            }
            
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            if (end == -1) {
                throw new IllegalArgumentException("括号不匹配");
            }
            
            String subExpr = expr.substring(start + 1, end);
            boolean subResult = evaluateBooleanExpressionInternal(subExpr, depth + 1);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        
        // ✅ 处理NOT（添加循环次数限制）
        int notIterations = 0;
        while (expr.toUpperCase().contains("NOT")) {
            if (++notIterations > 50) {
                throw new IllegalArgumentException(
                    "表达式格式错误（NOT处理超过50次迭代）"
                );
            }
            
            int notPos = expr.toUpperCase().indexOf("NOT");
            String remaining = expr.substring(notPos + 3).trim();
            String[] tokens = remaining.split("\\s+");
            if (tokens.length > 0) {
                boolean value = Boolean.parseBoolean(tokens[0]);
                expr = expr.substring(0, notPos) + (!value) + 
                       (tokens.length > 1 ? " " + remaining.substring(tokens[0].length()).trim() : "");
            }
        }
        
        // 处理AND
        if (expr.toUpperCase().contains("AND")) {
            String[] parts = expr.split("(?i)\\s+AND\\s+");
            for (String part : parts) {
                if (!Boolean.parseBoolean(part.trim())) {
                    return false;
                }
            }
            return true;
        }
        
        // 处理OR
        if (expr.toUpperCase().contains("OR")) {
            String[] parts = expr.split("(?i)\\s+OR\\s+");
            for (String part : parts) {
                if (Boolean.parseBoolean(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        
        // 单个布尔值
        return Boolean.parseBoolean(expr.trim());
    }
    
    /**
     * 构建扫描结果
     */
    private ScanResult buildScanResult(Configuration config, HttpRequest request, 
                                      Map<Integer, Boolean> pairResults,
                                      Map<Integer, PairEvaluationResult> pairEvaluations,
                                      boolean vulnerable) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("规则: ").append(config.getCustomLabel()).append("\n");
        evidence.append("配对匹配结果:\n");
        
        // ✅ 查找第一个有响应的评估结果（优先匹配成功的）
        PairEvaluationResult selectedEvaluation = null;
        for (Map.Entry<Integer, Boolean> entry : pairResults.entrySet()) {
            evidence.append("  配对 [").append(entry.getKey()).append("]: ")
                   .append(entry.getValue() ? "✓ 匹配" : "✗ 不匹配")
                   .append("\n");
            
            // 优先选择匹配成功的评估结果
            if (selectedEvaluation == null && entry.getValue()) {
                PairEvaluationResult eval = pairEvaluations.get(entry.getKey());
                if (eval != null && eval.response != null) {
                    selectedEvaluation = eval;
                }
            }
        }
        
        // 如果没有匹配成功的，选择任意一个有响应的
        if (selectedEvaluation == null) {
            selectedEvaluation = pairEvaluations.values().stream()
                .filter(e -> e.response != null)
                .findFirst()
                .orElse(null);
        }
        
        if (config.getPairExpression() != null && !config.getPairExpression().isEmpty()) {
            evidence.append("配对逻辑: ").append(config.getPairExpression()).append("\n");
        }
        
        // ✅ 构建结果，包含响应对象
        ScanResult.Builder builder = new ScanResult.Builder()
            .vulnerable(vulnerable)  // ✅ 使用传入的vulnerable标志
            .scanType(config.getCustomLabel())
            .evidence(evidence.toString())
            .originalRequest(request);
        
        // ✅ 如果有评估结果，添加响应信息
        if (selectedEvaluation != null) {
            if (selectedEvaluation.response != null) {
                builder.response(selectedEvaluation.response);
            }
            if (selectedEvaluation.modifiedRequest != null) {
                builder.modifiedRequest(selectedEvaluation.modifiedRequest);
            }
            if (selectedEvaluation.responseTime > 0) {
                builder.responseTime(selectedEvaluation.responseTime);
            }
        }
        
        return builder.build();
    }
    
    /**
     * 根据配置确定严重程度
     */
    private String determineSeverity(Configuration config) {
        // 可以根据规则名称或其他信息判断严重程度
        String label = config.getCustomLabel().toLowerCase();
        
        if (label.contains("sql") || label.contains("rce") || label.contains("命令注入")) {
            return "High";
        } else if (label.contains("xss") || label.contains("ssrf") || label.contains("xxe")) {
            return "Medium";
        } else if (label.contains("信息泄露") || label.contains("disclosure")) {
            return "Low";
        }
        
        return "Medium";  // 默认
    }
}

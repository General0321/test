package com.xprobe.scanner.scanners;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.xprobe.scanner.config.Configuration;
import com.xprobe.scanner.config.ConfigPersistence;
import com.xprobe.scanner.config.RuleMatchPair;
import com.xprobe.scanner.config.UnifiedHttpConfig;
import com.xprobe.scanner.config.UnifiedResponseConfig;
import com.xprobe.scanner.config.XProbeConfig;
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
 */
public class UniversalScanner extends AbstractScanner {
    
    public static final String SCANNER_TYPE = "UNIVERSAL_RULE_SCANNER";
    private final ConfigPersistence configPersistence;
    
    public UniversalScanner(MontoyaApi api, com.xprobe.scanner.active.RealtimeScannerRefactored realtimeScanner, ConfigPersistence configPersistence) {
        super(api, realtimeScanner);
        this.configPersistence = configPersistence;
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
            
            // 评估每个配对
            Map<Integer, Boolean> pairResults = new HashMap<>();
            
            for (RuleMatchPair pair : pairs) {
                try {
                    boolean pairMatched = evaluatePair(pair, originalRequest, payloadResolver, config);
                    pairResults.put(pair.getId(), pairMatched);
                    
                    if (pairMatched) {
                        api.logging().raiseDebugEvent("配对 [" + pair.getId() + "] " + 
                            pair.getDisplayLabel() + " 匹配成功");
                    }
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("评估配对 [" + pair.getId() + "] 时出错: " + 
                        e.getMessage());
                    pairResults.put(pair.getId(), false);
                }
            }
            
            // 根据配对表达式评估最终结果
            boolean finalResult = evaluatePairExpression(config.getPairExpression(), pairResults);
            
            if (finalResult) {
                // 创建扫描结果
                ScanResult result = buildScanResult(config, originalRequest, pairResults);
                results.add(result);
                
                api.logging().raiseInfoEvent("规则 " + config.getCustomLabel() + " 检测到漏洞！");
            }
            
            return results;
        });
    }
    
    /**
     * 评估单个配对
     */
    private boolean evaluatePair(RuleMatchPair pair, HttpRequest originalRequest, 
                                 PayloadVariableResolver payloadResolver, Configuration config) {
        UnifiedHttpConfig requestConfig = pair.getRequestConfig();
        UnifiedResponseConfig responseConfig = pair.getResponseConfig();
        
        if (requestConfig == null || responseConfig == null) {
            return false;
        }
        
        // 1. 检查请求是否匹配
        if (!UnifiedHttpEvaluator.evaluate(originalRequest, requestConfig)) {
            return false;
        }
        
        // 2. 获取注入点（启用了注入的元素）
        // 🔍 调试：打印所有元素信息
        api.logging().raiseInfoEvent("🔍 [调试] 配对 [" + pair.getId() + "] 总元素数: " + 
            (requestConfig.getElements() != null ? requestConfig.getElements().size() : 0));
        
        if (requestConfig.getElements() != null) {
            for (int i = 0; i < requestConfig.getElements().size(); i++) {
                var elem = requestConfig.getElements().get(i);
                api.logging().raiseInfoEvent(String.format(
                    "🔍 [调试] 元素[%d]: 类型=%s, 名称=%s, useForMatch=%s, useForInjection=%s, payloads数=%d, injectionTarget=%s",
                    i, elem.getType(), elem.getName(), elem.isUseForMatch(), elem.isUseForInjection(),
                    (elem.getPayloads() != null ? elem.getPayloads().size() : 0),
                    elem.getInjectionTarget()
                ));
            }
        }
        
        List<UnifiedHttpConfig.HttpElementConfig> injectionPoints = requestConfig.getElements()
            .stream()
            .filter(UnifiedHttpConfig.HttpElementConfig::isUseForInjection)
            .collect(Collectors.toList());
        
        api.logging().raiseInfoEvent("🔍 [调试] 过滤后的注入点数: " + injectionPoints.size());
        
        // ✅ 如果没有注入点，执行被动检测（检查原始响应）
        if (injectionPoints.isEmpty()) {
            api.logging().raiseInfoEvent("⚠️ 配对 [" + pair.getId() + "] 没有注入点，执行被动检测");
            try {
                // 发送原始请求
                long startTime = System.currentTimeMillis();
                HttpResponse response = api.http().sendRequest(originalRequest).response();
                long responseTime = System.currentTimeMillis() - startTime;
                
                // 评估响应（无payload上下文）
                boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                    response, responseConfig, null, responseTime
                );
                
                if (responseMatched) {
                    api.logging().raiseDebugEvent("配对 [" + pair.getId() + "] 被动检测成功");
                    return true;
                }
            } catch (Exception e) {
                api.logging().raiseErrorEvent("被动检测时发送请求失败: " + e.getMessage());
            }
            return false;
        }
        
        // 3. 根据全局注入模式执行注入并检查响应
        Configuration.InjectionMode injectionMode = getGlobalInjectionMode();
        
        // 📌 批量模式 vs 逐个模式
        if (injectionMode == Configuration.InjectionMode.BATCH) {
            // ========== 批量模式（BATCH）：所有匹配参数同时注入相同payload ==========
            return evaluateBatchMode(injectionPoints, originalRequest, responseConfig, payloadResolver, pair);
        } else {
            // ========== 逐个模式（INDIVIDUAL）：每次只注入一个参数 ==========
            return evaluateIndividualMode(injectionPoints, originalRequest, responseConfig, payloadResolver, pair);
        }
    }
    
    /**
     * 获取全局注入模式
     */
    private Configuration.InjectionMode getGlobalInjectionMode() {
        try {
            XProbeConfig xProbeConfig = configPersistence.load();
            return xProbeConfig.getGlobalInjectionMode();
        } catch (Exception e) {
            api.logging().raiseErrorEvent("读取全局注入模式失败，使用默认批量模式: " + e.getMessage());
            return Configuration.InjectionMode.BATCH;  // 默认批量模式
        }
    }
    
    /**
     * 批量模式评估：所有匹配的参数同时注入相同payload
     */
    private boolean evaluateBatchMode(List<UnifiedHttpConfig.HttpElementConfig> injectionPoints,
                                     HttpRequest originalRequest,
                                     UnifiedResponseConfig responseConfig,
                                     PayloadVariableResolver payloadResolver,
                                     RuleMatchPair pair) {
        for (UnifiedHttpConfig.HttpElementConfig injectionPoint : injectionPoints) {
            List<String> payloads = injectionPoint.getPayloads();
            if (payloads == null || payloads.isEmpty()) {
                continue;
            }
            
            // 获取注入点的原始值
            String originalValue = UnifiedHttpEvaluator.getOriginalValue(originalRequest, injectionPoint);
            
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
                    
                    // 评估响应
                    boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                        response, responseConfig, payloadContext, responseTime
                    );
                    
                    if (responseMatched) {
                        api.logging().raiseDebugEvent(
                            "配对 [" + pair.getId() + "] 批量注入匹配: " + 
                            injectionPoint.getType().getDisplayName() + 
                            ", Payload: " + resolvedPayload.substring(0, Math.min(50, resolvedPayload.length()))
                        );
                        return true;
                    }
                    
                } catch (Exception e) {
                    api.logging().raiseErrorEvent("批量注入时出错: " + e.getMessage());
                }
            }
        }
        return false;
    }
    
    /**
     * 逐个模式评估：每次只注入一个匹配的参数
     */
    private boolean evaluateIndividualMode(List<UnifiedHttpConfig.HttpElementConfig> injectionPoints,
                                          HttpRequest originalRequest,
                                          UnifiedResponseConfig responseConfig,
                                          PayloadVariableResolver payloadResolver,
                                          RuleMatchPair pair) {
        for (UnifiedHttpConfig.HttpElementConfig injectionPoint : injectionPoints) {
            List<String> payloads = injectionPoint.getPayloads();
            if (payloads == null || payloads.isEmpty()) {
                continue;
            }
            
            // 获取所有匹配的目标（参数/Header/Cookie等）
            List<InjectionTarget> targets = collectInjectionTargets(originalRequest, injectionPoint);
            
            // 对每个目标分别测试
            for (InjectionTarget target : targets) {
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
                        
                        // 评估响应
                        boolean responseMatched = UnifiedResponseEvaluator.evaluate(
                            response, responseConfig, payloadContext, responseTime
                        );
                        
                        if (responseMatched) {
                            api.logging().raiseDebugEvent(
                                "配对 [" + pair.getId() + "] 逐个注入匹配: " + 
                                injectionPoint.getType().getDisplayName() + 
                                " [" + target.name + "], Payload: " + resolvedPayload.substring(0, Math.min(50, resolvedPayload.length()))
                            );
                            return true;
                        }
                        
                    } catch (Exception e) {
                        api.logging().raiseErrorEvent("逐个注入时出错: " + e.getMessage());
                    }
                }
            }
        }
        return false;
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
                        targets.add(new InjectionTarget(param.name(), param.value(), param.type()));
                    }
                }
                break;
                
            case HEADER:
                for (var header : request.headers()) {
                    if (shouldMatchTarget(header.name(), element)) {
                        targets.add(new InjectionTarget(header.name(), header.value(), null));
                    }
                }
                break;
                
            case COOKIE:
                for (var param : request.parameters()) {
                    if (param.type() == burp.api.montoya.http.message.params.HttpParameterType.COOKIE &&
                        shouldMatchTarget(param.name(), element)) {
                        targets.add(new InjectionTarget(param.name(), param.value(), param.type()));
                    }
                }
                break;
                
            case METHOD:
            case PATH:
            case BODY:
                // 这些类型不需要逐个处理，只有一个目标
                targets.add(new InjectionTarget("", UnifiedHttpEvaluator.getOriginalValue(request, element), null));
                break;
        }
        
        return targets;
    }
    
    /**
     * 判断目标名称是否匹配元素配置
     */
    private boolean shouldMatchTarget(String targetName, UnifiedHttpConfig.HttpElementConfig element) {
        // 1. 优先使用element.name
        String elementName = element.getName();
        if (elementName != null && !elementName.isEmpty()) {
            return targetName.equals(elementName);
        }
        
        // 2. 使用nameMatchConfig
        if (element.getNameMatchConfig() != null && 
            element.getNameMatchConfig().getValues() != null &&
            !element.getNameMatchConfig().getValues().isEmpty()) {
            for (String matchValue : element.getNameMatchConfig().getValues()) {
                if (matchValue != null && !matchValue.isEmpty() &&
                    matchesValue(targetName, matchValue, element.getNameMatchConfig().getMatchType())) {
                    return true;
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
                    if (injTarget == UnifiedHttpConfig.InjectionTarget.VALUE) {
                        modified = modified.withUpdatedHeader(target.name, payload);
                    } else if (injTarget == UnifiedHttpConfig.InjectionTarget.NAME) {
                        modified = modified.withRemovedHeader(target.name);
                        modified = modified.withAddedHeader(payload, target.originalValue);
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
        
        InjectionTarget(String name, String originalValue, burp.api.montoya.http.message.params.HttpParameterType paramType) {
            this.name = name;
            this.originalValue = originalValue;
            this.paramType = paramType;
        }
    }
    
    /**
     * 辅助方法：检查值是否匹配
     */
    private boolean matchesValue(String actualValue, String matchValue, UnifiedHttpConfig.MatchType matchType) {
        if (actualValue == null || matchValue == null) {
            return false;
        }
        
        switch (matchType) {
            case ANY:
                return true;
            case EQUALS:
                return actualValue.equals(matchValue);
            case CONTAINS:
                return actualValue.contains(matchValue);
            case REGEX:
                try {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(matchValue);
                    return pattern.matcher(actualValue).find();
                } catch (Exception e) {
                    return false;
                }
            case STARTS_WITH:
                return actualValue.startsWith(matchValue);
            case ENDS_WITH:
                return actualValue.endsWith(matchValue);
            case NOT_EQUALS:
                return !actualValue.equals(matchValue);
            case NOT_CONTAINS:
                return !actualValue.contains(matchValue);
            default:
                return actualValue.contains(matchValue);
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
                    // 注入到路径
                    if (target == UnifiedHttpConfig.InjectionTarget.VALUE || 
                        target == UnifiedHttpConfig.InjectionTarget.ENTIRE) {
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
                            for (String matchValue : element.getNameMatchConfig().getValues()) {
                                if (matchValue != null && !matchValue.isEmpty()) {
                                    // 根据匹配类型进行匹配
                                    boolean matches = false;
                                    UnifiedHttpConfig.MatchType matchType = element.getNameMatchConfig().getMatchType();
                                    switch (matchType) {
                                        case ANY:
                                            matches = true;  // 匹配任何值
                                            break;
                                        case EQUALS:
                                            matches = param.name().equals(matchValue);
                                            break;
                                        case STARTS_WITH:
                                            matches = param.name().startsWith(matchValue);
                                            break;
                                        case ENDS_WITH:
                                            matches = param.name().endsWith(matchValue);
                                            break;
                                        case NOT_EQUALS:
                                            matches = !param.name().equals(matchValue);
                                            break;
                                        case CONTAINS:
                                            matches = param.name().contains(matchValue);
                                            break;
                                        case REGEX:
                                            try {
                                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(matchValue);
                                                matches = pattern.matcher(param.name()).find();
                                            } catch (Exception e) {
                                                matches = false;
                                            }
                                            break;
                                        case NOT_CONTAINS:
                                            matches = !param.name().contains(matchValue);
                                            break;
                                        default:
                                            matches = param.name().contains(matchValue);  // 默认部分匹配
                                            break;
                                    }
                                    
                                    if (matches) {
                                        shouldInject = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            // 🔍 调试日志
                            api.logging().raiseInfoEvent(String.format(
                                "🎯 [Payload注入] 参数='%s', 原值='%s', Payload='%s', 注入目标=%s", 
                                param.name(), param.value(), payload, target
                            ));
                            
                            if (target == UnifiedHttpConfig.InjectionTarget.VALUE) {
                                // ✅ 使用withUpdatedParameters()直接更新参数值
                                var newParam = burp.api.montoya.http.message.params.HttpParameter
                                    .parameter(param.name(), payload, param.type());
                                modified = modified.withUpdatedParameters(newParam);
                                api.logging().raiseInfoEvent("✅ [Payload注入] 参数值替换成功: " + param.name());
                            } else if (target == UnifiedHttpConfig.InjectionTarget.NAME) {
                                // 替换参数名（必须先删除旧的，再添加新的）
                                modified = modified.withRemovedParameters(param);
                                var newParam = burp.api.montoya.http.message.params.HttpParameter
                                    .parameter(payload, param.value(), param.type());
                                modified = modified.withAddedParameters(newParam);
                                api.logging().raiseInfoEvent("✅ [Payload注入] 参数名替换成功");
                            } else {
                                api.logging().raiseErrorEvent("⚠️ [Payload注入] 注入目标为null或不支持: " + target);
                            }
                        }
                    }
                    break;
                    
                case HEADER:
                    // ✅ 改进：支持通过nameMatchConfig匹配多个Header名
                    for (var header : originalRequest.headers()) {
                        boolean shouldInject = false;
                        
                        // 1. 如果element.name有值，优先使用name匹配
                        String elementName = element.getName();
                        if (elementName != null && !elementName.isEmpty()) {
                            shouldInject = header.name().equals(elementName);
                        } 
                        // 2. 否则，使用nameMatchConfig匹配
                        else if (element.getNameMatchConfig() != null && 
                                 element.getNameMatchConfig().getValues() != null &&
                                 !element.getNameMatchConfig().getValues().isEmpty()) {
                            // 检查Header名是否匹配配置的任何一个值
                            for (String matchValue : element.getNameMatchConfig().getValues()) {
                                if (matchValue != null && !matchValue.isEmpty() && 
                                    matchesValue(header.name(), matchValue, element.getNameMatchConfig().getMatchType())) {
                                    shouldInject = true;
                                    break;
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            if (target == UnifiedHttpConfig.InjectionTarget.VALUE) {
                                modified = modified.withUpdatedHeader(header.name(), payload);
                                api.logging().raiseInfoEvent("✅ [Payload注入] Header值替换成功: " + header.name());
                            } else if (target == UnifiedHttpConfig.InjectionTarget.NAME) {
                                modified = modified.withRemovedHeader(header.name());
                                modified = modified.withAddedHeader(payload, header.value());
                                api.logging().raiseInfoEvent("✅ [Payload注入] Header名替换成功");
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
                        
                        // 1. 如果element.name有值，优先使用name匹配
                        String elementName = element.getName();
                        if (elementName != null && !elementName.isEmpty()) {
                            shouldInject = param.name().equals(elementName);
                        } 
                        // 2. 否则，使用nameMatchConfig匹配
                        else if (element.getNameMatchConfig() != null && 
                                 element.getNameMatchConfig().getValues() != null &&
                                 !element.getNameMatchConfig().getValues().isEmpty()) {
                            // 检查Cookie名是否匹配配置的任何一个值
                            for (String matchValue : element.getNameMatchConfig().getValues()) {
                                if (matchValue != null && !matchValue.isEmpty() && 
                                    matchesValue(param.name(), matchValue, element.getNameMatchConfig().getMatchType())) {
                                    shouldInject = true;
                                    break;
                                }
                            }
                        }
                        
                        // 如果匹配成功，执行注入
                        if (shouldInject) {
                            var cookieParam = burp.api.montoya.http.message.params.HttpParameter
                                .cookieParameter(param.name(), payload);
                            modified = modified.withUpdatedParameters(cookieParam);
                            api.logging().raiseInfoEvent("✅ [Payload注入] Cookie值替换成功: " + param.name());
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
     * 评估布尔表达式
     */
    private boolean evaluateBooleanExpression(String expr) {
        expr = expr.trim();
        
        // 处理括号
        while (expr.contains("(")) {
            int start = expr.lastIndexOf('(');
            int end = expr.indexOf(')', start);
            if (end == -1) {
                throw new IllegalArgumentException("括号不匹配");
            }
            
            String subExpr = expr.substring(start + 1, end);
            boolean subResult = evaluateBooleanExpression(subExpr);
            expr = expr.substring(0, start) + subResult + expr.substring(end + 1);
        }
        
        // 处理NOT
        while (expr.toUpperCase().contains("NOT")) {
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
                                      Map<Integer, Boolean> pairResults) {
        StringBuilder evidence = new StringBuilder();
        evidence.append("规则: ").append(config.getCustomLabel()).append("\n");
        evidence.append("配对匹配结果:\n");
        
        for (Map.Entry<Integer, Boolean> entry : pairResults.entrySet()) {
            evidence.append("  配对 [").append(entry.getKey()).append("]: ")
                   .append(entry.getValue() ? "✓ 匹配" : "✗ 不匹配")
                   .append("\n");
        }
        
        if (config.getPairExpression() != null && !config.getPairExpression().isEmpty()) {
            evidence.append("配对逻辑: ").append(config.getPairExpression()).append("\n");
        }
        
        return new ScanResult.Builder()
            .vulnerable(true)
            .scanType(config.getCustomLabel())
            .evidence(evidence.toString())
            .originalRequest(request)
            .build();
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

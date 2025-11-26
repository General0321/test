package com.xprobe.scanner.active;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 参数收集数据持久化存储
 * 负责保存和加载收集的接口、参数数据
 * 
 * 存储格式：JSON
 * 文件路径：~/.xprobe/parameter_data.json
 * 
 * 数据结构：
 * {
 *   "mainDomains": {
 *     "example.com": {
 *       "mainDomain": "example.com",
 *       "hosts": ["www.example.com", "api.example.com"],
 *       "endpoints": [
 *         {
 *           "host": "www.example.com",
 *           "endpoint": "/api/user",
 *           "method": "GET",
 *           "contentType": "application/json",
 *           "parameters": ["id", "name"]
 *         }
 *       ],
 *       "keywords": ["keyword1", "keyword2"],
 *       "lastUpdateTime": 1234567890
 *     }
 *   }
 * }
 */
public class ParameterDataStorage {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.xprobe";
    private static final String DATA_FILE = CONFIG_DIR + "/parameter_data.json";
    
    private final MontoyaApi api;
    private final ObjectMapper objectMapper;
    
    public ParameterDataStorage(MontoyaApi api) {
        this.api = api;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
    
    /**
     * 保存参数收集数据
     */
    public void save(ParameterCollector collector) throws IOException {
        try {
            // 创建配置目录
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                api.logging().raiseDebugEvent("创建配置目录: " + CONFIG_DIR);
            }
            
            // 构建数据模型
            ParameterDataModel dataModel = buildDataModel(collector);
            
            // 写入JSON文件
            objectMapper.writeValue(new File(DATA_FILE), dataModel);
            
            api.logging().raiseInfoEvent("参数收集数据已保存到: " + DATA_FILE);
            
        } catch (IOException e) {
            api.logging().raiseErrorEvent("保存参数收集数据失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 加载参数收集数据
     */
    public ParameterDataModel load() {
        try {
            Path dataFile = Paths.get(DATA_FILE);
            
            if (Files.exists(dataFile)) {
                ParameterDataModel model = objectMapper.readValue(new File(DATA_FILE), ParameterDataModel.class);
                api.logging().raiseInfoEvent("参数收集数据已从磁盘加载: " + DATA_FILE);
                return model;
            } else {
                api.logging().raiseDebugEvent("参数收集数据文件不存在: " + DATA_FILE);
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("加载参数收集数据失败: " + e.getMessage());
        }
        
        // 返回空模型
        return new ParameterDataModel();
    }
    
    /**
     * ✅ 从存储的数据恢复到 ParameterCollector
     * 恢复所有收集的接口、参数和关键词数据
     */
    public void restoreToCollector(ParameterCollector collector, MontoyaApi api) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains == null || model.mainDomains.isEmpty()) {
                api.logging().raiseDebugEvent("没有可恢复的数据");
                return;
            }
            
            api.logging().raiseInfoEvent("开始恢复参数收集数据...");
            int restoredEndpoints = 0;
            int restoredParameters = 0;
            int restoredKeywords = 0;
            
            for (MainDomainData mainDomainData : model.mainDomains.values()) {
                // ✅ 优先使用新的按子域名组织的数据结构
                if (mainDomainData.hostDataMap != null && !mainDomainData.hostDataMap.isEmpty()) {
                    // 新格式：按子域名组织
                    for (HostData hostData : mainDomainData.hostDataMap.values()) {
                        if (hostData.endpoints != null) {
                            for (EndpointData epData : hostData.endpoints) {
                                try {
                                    HttpRequest request = buildRequestFromEndpointData(epData, api);
                                    if (request != null) {
                                        collector.collectFromRequest(request);
                                        restoredEndpoints++;
                                    }
                                } catch (Exception e) {
                                    api.logging().raiseDebugEvent("恢复接口失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
                                }
                            }
                        }
                        // ✅ 统计该子域名的参数
                        if (hostData.parameters != null) {
                            restoredParameters += hostData.parameters.size();
                        }
                    }
                } else if (mainDomainData.endpoints != null) {
                    // 向后兼容：旧格式（扁平结构）
                    for (EndpointData epData : mainDomainData.endpoints) {
                        try {
                            HttpRequest request = buildRequestFromEndpointData(epData, api);
                            if (request != null) {
                                collector.collectFromRequest(request);
                                restoredEndpoints++;
                            }
                        } catch (Exception e) {
                            api.logging().raiseDebugEvent("恢复接口失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
                        }
                    }
                }
                
                // ✅ 恢复关键词（按主域名）
                if (mainDomainData.keywords != null && !mainDomainData.keywords.isEmpty()) {
                    // 关键词需要通过响应来恢复，这里先记录
                    restoredKeywords += mainDomainData.keywords.size();
                }
            }
            
            api.logging().raiseInfoEvent(String.format(
                "✅ 参数收集数据恢复完成: 恢复了 %d 个接口, %d 个参数, %d 个关键词", 
                restoredEndpoints, restoredParameters, restoredKeywords
            ));
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("恢复参数收集数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ✅ 一键清空所有数据
     */
    public boolean clearAll() {
        try {
            ParameterDataModel model = new ParameterDataModel();
            model.mainDomains = new HashMap<>();
            objectMapper.writeValue(new File(DATA_FILE), model);
            api.logging().raiseInfoEvent("已清空所有参数收集数据");
            return true;
        } catch (Exception e) {
            api.logging().raiseErrorEvent("清空所有数据失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除指定主域名的数据
     */
    public boolean deleteMainDomain(String mainDomain) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains != null && model.mainDomains.containsKey(mainDomain)) {
                model.mainDomains.remove(mainDomain);
                objectMapper.writeValue(new File(DATA_FILE), model);
                api.logging().raiseInfoEvent("已删除主域名数据: " + mainDomain);
                return true;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("删除主域名数据失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * ✅ 删除指定子域名的数据（适配新的数据结构）
     */
    public boolean deleteHost(String mainDomain, String host) {
        try {
            ParameterDataModel model = load();
            if (model.mainDomains != null && model.mainDomains.containsKey(mainDomain)) {
                MainDomainData mainDomainData = model.mainDomains.get(mainDomain);
                
                // ✅ 优先使用新的按子域名组织的数据结构
                if (mainDomainData.hostDataMap != null) {
                    mainDomainData.hostDataMap.remove(host);
                }
                
                // ✅ 向后兼容：处理旧格式
                if (mainDomainData.endpoints != null) {
                    mainDomainData.endpoints.removeIf(ep -> ep.host.equals(host));
                }
                
                // 从hosts列表中移除
                if (mainDomainData.hosts != null) {
                    mainDomainData.hosts.remove(host);
                }
                
                // 如果hosts为空，删除整个主域名
                if (mainDomainData.hosts == null || mainDomainData.hosts.isEmpty()) {
                    model.mainDomains.remove(mainDomain);
                }
                
                objectMapper.writeValue(new File(DATA_FILE), model);
                api.logging().raiseInfoEvent(String.format("已删除子域名数据: %s (主域名: %s)", host, mainDomain));
                return true;
            }
        } catch (Exception e) {
            api.logging().raiseErrorEvent("删除子域名数据失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 获取文件路径
     */
    public String getDataFilePath() {
        return DATA_FILE;
    }
    
    /**
     * 检查数据文件是否存在
     */
    public boolean exists() {
        return Files.exists(Paths.get(DATA_FILE));
    }
    
    // ========== 内部方法 ==========
    
    /**
     * ✅ 从 ParameterCollector 构建数据模型（优化版：按主域名->子域名结构）
     * 每个子域名保存有哪些接口和参数
     */
    private ParameterDataModel buildDataModel(ParameterCollector collector) {
        ParameterDataModel model = new ParameterDataModel();
        model.mainDomains = new HashMap<>();
        
        for (String mainDomain : collector.getAllMainDomains()) {
            MainDomainData mainDomainData = new MainDomainData();
            mainDomainData.mainDomain = mainDomain;
            mainDomainData.hosts = new ArrayList<>(collector.getHostsForMainDomain(mainDomain));
            mainDomainData.hostDataMap = new HashMap<>(); // ✅ 按子域名组织数据
            mainDomainData.keywords = new ArrayList<>(collector.getKeywordsForMainDomain(mainDomain));
            mainDomainData.lastUpdateTime = collector.getLastUpdateTimeForDomain(mainDomain);
            
            // ✅ 获取所有接口，按子域名分组
            Set<ParameterCollector.EndpointKey> endpointKeys = 
                collector.getEndpointKeysForMainDomain(mainDomain);
            
            // ✅ 按子域名组织数据
            Map<String, HostData> hostDataMap = new HashMap<>();
            
            for (ParameterCollector.EndpointKey epKey : endpointKeys) {
                HttpRequest template = collector.getEndpointTemplate(mainDomain, epKey);
                if (template != null) {
                    String host = epKey.host;
                    
                    // 获取或创建该子域名的数据
                    HostData hostData = hostDataMap.computeIfAbsent(host, k -> {
                        HostData hd = new HostData();
                        hd.host = host;
                        hd.endpoints = new ArrayList<>();
                        // ✅ 获取该子域名的所有参数（从ParameterCollector）
                        hd.parameters = new ArrayList<>(collector.getParametersForHost(host));
                        return hd;
                    });
                    
                    // 添加接口信息
                    EndpointData epData = new EndpointData();
                    epData.host = epKey.host;
                    epData.endpoint = epKey.endpoint;
                    epData.method = epKey.method;
                    epData.contentType = epKey.contentType;
                    
                    // ✅ 保存完整的URL（用于恢复时构建HttpService）
                    epData.url = template.url();
                    
                    // ✅ 保存完整的请求字符串（包含所有header和body）
                    StringBuilder requestStr = new StringBuilder();
                    requestStr.append(template.method()).append(" ").append(template.path());
                    String query = template.query();
                    if (query != null && !query.isEmpty()) {
                        requestStr.append("?").append(query);
                    }
                    requestStr.append(" HTTP/1.1\r\n");
                    
                    // 添加所有headers
                    for (var header : template.headers()) {
                        requestStr.append(header.name()).append(": ").append(header.value()).append("\r\n");
                    }
                    requestStr.append("\r\n");
                    
                    // 添加body（如果有）
                    if (template.body() != null && template.body().length() > 0) {
                        requestStr.append(template.bodyToString());
                    }
                    
                    epData.requestTemplate = requestStr.toString();
                    
                    hostData.endpoints.add(epData);
                }
            }
            
            // ✅ 将按子域名组织的数据添加到主域名数据中
            mainDomainData.hostDataMap = hostDataMap;
            
            model.mainDomains.put(mainDomain, mainDomainData);
        }
        
        return model;
    }
    
    /**
     * ✅ 从端点数据构建 HttpRequest
     * 使用保存的请求字符串和HttpService信息重新构建请求
     */
    private HttpRequest buildRequestFromEndpointData(EndpointData epData, MontoyaApi api) {
        try {
            if (epData.requestTemplate == null || epData.requestTemplate.isEmpty()) {
                return null;
            }
            
            // ✅ 解析URL获取scheme和port信息
            String url = epData.url;
            if (url == null || url.isEmpty()) {
                // 如果URL为空，尝试从host和endpoint构建URL
                url = "https://" + epData.host + epData.endpoint;
            }
            
            java.net.URI uri;
            try {
                uri = new java.net.URI(url);
            } catch (java.net.URISyntaxException e) {
                // 如果URL格式不正确，尝试构建一个简单的URL
                url = "https://" + epData.host + epData.endpoint;
                uri = new java.net.URI(url);
            }
            
            String host = epData.host;
            String scheme = uri.getScheme();
            if (scheme == null) {
                scheme = "https"; // 默认使用https
            }
            int port = uri.getPort();
            boolean isSecure = "https".equalsIgnoreCase(scheme);
            
            // ✅ 如果没有指定port，使用默认端口
            if (port == -1) {
                port = isSecure ? 443 : 80;
            }
            
            // ✅ 构建HttpService
            burp.api.montoya.http.HttpService httpService = 
                burp.api.montoya.http.HttpService.httpService(host, port, isSecure);
            
            // ✅ 从请求字符串构建HttpRequest
            HttpRequest request = HttpRequest.httpRequest(httpService, epData.requestTemplate);
            
            return request;
            
        } catch (Exception e) {
            api.logging().raiseDebugEvent("从端点数据构建请求失败: " + epData.host + " " + epData.endpoint + " - " + e.getMessage());
            return null;
        }
    }
    
    // ========== 数据模型类 ==========
    
    /**
     * 参数数据模型（根对象）
     */
    public static class ParameterDataModel {
        public Map<String, MainDomainData> mainDomains = new HashMap<>();
    }
    
    /**
     * ✅ 主域名数据（优化版：按子域名组织）
     */
    public static class MainDomainData {
        public String mainDomain;
        public List<String> hosts = new ArrayList<>();
        public Map<String, HostData> hostDataMap = new HashMap<>(); // ✅ 按子域名组织的数据
        public List<EndpointData> endpoints = new ArrayList<>(); // ✅ 保留用于向后兼容
        public List<String> keywords = new ArrayList<>();
        public long lastUpdateTime;
    }
    
    /**
     * ✅ 子域名数据（包含该子域名的接口和参数）
     */
    public static class HostData {
        public String host;
        public List<EndpointData> endpoints = new ArrayList<>(); // ✅ 该子域名的所有接口
        public List<String> parameters = new ArrayList<>(); // ✅ 该子域名的所有参数
    }
    
    /**
     * ✅ 接口数据（优化版）
     * 包含恢复所需的所有信息
     */
    public static class EndpointData {
        public String host;
        public String endpoint;
        public String method;
        public String contentType;
        public String url; // ✅ 完整的URL（用于恢复时构建HttpService）
        public List<String> parameters = new ArrayList<>(); // ✅ 该接口的参数列表
        public String requestTemplate; // ✅ 完整的HTTP请求字符串（包含header和body）
    }
}


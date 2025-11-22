package com.xprobe.scanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则持久化管理器
 * 
 * 功能：
 * - 将扫描规则单独保存到JSON文件
 * - 从JSON文件加载规则
 * - 支持规则的导入导出
 * - 方便规则的移植和分享
 */
public class RulePersistence {
    
    private final ObjectMapper mapper;
    
    public RulePersistence() {
        this.mapper = new ObjectMapper();
        // 美化输出，便于阅读和编辑
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
            false
        );
    }
    
    /**
     * 保存规则列表到JSON文件
     * 
     * @param rules 规则列表
     * @param filePath 文件路径
     * @throws IOException 如果保存失败
     */
    public void saveRules(List<Configuration> rules, String filePath) throws IOException {
        File file = new File(filePath);
        
        // 创建父目录（如果不存在）
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("无法创建目录: " + parentDir.getAbsolutePath());
            }
        }
        
        // 创建规则包装对象
        RulePackage rulePackage = new RulePackage();
        rulePackage.setVersion("1.0");
        rulePackage.setRules(rules != null ? rules : new ArrayList<>());
        rulePackage.setExportTime(System.currentTimeMillis());
        
        // 写入文件
        mapper.writeValue(file, rulePackage);
    }
    
    /**
     * 从JSON文件加载规则列表
     * 
     * @param filePath 文件路径
     * @return 规则列表
     * @throws IOException 如果加载失败
     */
    public List<Configuration> loadRules(String filePath) throws IOException {
        File file = new File(filePath);
        
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        // ✅ 检查文件大小（限制10MB，防止DoS）
        if (file.length() > 10 * 1024 * 1024) {
            throw new IOException("规则文件过大（最大10MB），实际: " + 
                (file.length() / 1024 / 1024) + "MB");
        }
        
        // ✅ 检查文件可读性
        if (!file.canRead()) {
            throw new IOException("无法读取规则文件: " + file.getAbsolutePath());
        }
        
        // 加载规则文件（带版本信息）
        RulePackage rulePackage = mapper.readValue(file, RulePackage.class);
        return rulePackage.getRules() != null ? rulePackage.getRules() : new ArrayList<>();
    }
    
    /**
     * 导出规则到指定文件
     * 
     * @param rules 规则列表
     * @param file 目标文件
     * @throws IOException 如果导出失败
     */
    public void exportRules(List<Configuration> rules, File file) throws IOException {
        saveRules(rules, file.getAbsolutePath());
    }
    
    /**
     * 从文件导入规则
     * 
     * @param file 源文件
     * @return 规则列表
     * @throws IOException 如果导入失败
     */
    public List<Configuration> importRules(File file) throws IOException {
        return loadRules(file.getAbsolutePath());
    }
    
    /**
     * 验证规则文件格式
     * 
     * @param filePath 文件路径
     * @return true 如果格式正确
     */
    public boolean validateRuleFile(String filePath) {
        try {
            loadRules(filePath);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 规则包装类
     * 用于在JSON中添加元数据
     */
    public static class RulePackage implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private String version;           // 规则格式版本
        private long exportTime;          // 导出时间戳
        private String description;       // 描述信息
        private List<Configuration> rules; // 规则列表
        
        public RulePackage() {
        }
        
        public String getVersion() {
            return version;
        }
        
        public void setVersion(String version) {
            this.version = version;
        }
        
        public long getExportTime() {
            return exportTime;
        }
        
        public void setExportTime(long exportTime) {
            this.exportTime = exportTime;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public List<Configuration> getRules() {
            return rules;
        }
        
        public void setRules(List<Configuration> rules) {
            this.rules = rules;
        }
    }
}


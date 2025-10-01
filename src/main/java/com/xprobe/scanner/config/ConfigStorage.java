package com.xprobe.scanner.config;

import burp.api.montoya.MontoyaApi;

import java.io.*;
import java.nio.file.*;

/**
 * 配置持久化存储
 * 负责将配置保存到磁盘和从磁盘加载
 */
public class ConfigStorage {
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.xprobe";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.ser";
    
    private final MontoyaApi api;
    
    public ConfigStorage(MontoyaApi api) {
        this.api = api;
    }
    
    /**
     * 保存配置到磁盘（使用Java序列化）
     */
    public void save(XProbeConfig config) throws IOException {
        try {
            // 创建配置目录
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                api.logging().raiseDebugEvent("创建配置目录: " + CONFIG_DIR);
            }
            
            // 序列化并写入文件
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(CONFIG_FILE))) {
                oos.writeObject(config);
            }
            
            api.logging().raiseInfoEvent("配置已保存到: " + CONFIG_FILE);
            
        } catch (IOException e) {
            api.logging().raiseErrorEvent("保存配置失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 从磁盘加载配置
     * 如果文件不存在或加载失败，返回默认配置
     */
    public XProbeConfig load() {
        try {
            Path configFile = Paths.get(CONFIG_FILE);
            
            if (Files.exists(configFile)) {
                try (ObjectInputStream ois = new ObjectInputStream(
                        new FileInputStream(CONFIG_FILE))) {
                    XProbeConfig config = (XProbeConfig) ois.readObject();
                    
                    if (config != null) {
                        api.logging().raiseInfoEvent("配置已从磁盘加载: " + CONFIG_FILE);
                        return config;
                    }
                }
            } else {
                api.logging().raiseInfoEvent("配置文件不存在，使用默认配置");
            }
            
        } catch (Exception e) {
            api.logging().raiseErrorEvent("加载配置失败: " + e.getMessage() + "，使用默认配置");
        }
        
        // 返回默认配置
        return new XProbeConfig();
    }
    
    /**
     * 检查配置文件是否存在
     */
    public boolean exists() {
        return Files.exists(Paths.get(CONFIG_FILE));
    }
    
    /**
     * 获取配置文件路径
     */
    public String getConfigFilePath() {
        return CONFIG_FILE;
    }
    
    /**
     * 删除配置文件（用于重置）
     */
    public boolean delete() {
        try {
            Path configFile = Paths.get(CONFIG_FILE);
            if (Files.exists(configFile)) {
                Files.delete(configFile);
                api.logging().raiseInfoEvent("配置文件已删除: " + CONFIG_FILE);
                return true;
            }
        } catch (IOException e) {
            api.logging().raiseErrorEvent("删除配置文件失败: " + e.getMessage());
        }
        return false;
    }
}


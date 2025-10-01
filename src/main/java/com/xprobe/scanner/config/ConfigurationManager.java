package com.xprobe.scanner.config;

import java.io.*;                // 导入IO相关的类
import java.util.ArrayList;      // 导入ArrayList类
import java.util.List;           // 导入List接口

public class ConfigurationManager {
    private final List<Configuration> configurations;  // 用于存储配置的列表

    public ConfigurationManager() {
        this.configurations = new ArrayList<>();  // 初始化配置列表
    }

    // 添加新配置到列表中
    public void addConfiguration(Configuration configuration) {
        configurations.add(configuration);
    }

    // 根据索引移除配置
    public void removeConfiguration(int index) {
        if (index >= 0 && index < configurations.size()) {  // 检查索引是否有效
            configurations.remove(index);
        }
    }

    // 根据索引更新配置
    public void updateConfiguration(int index, Configuration newConfiguration) {
        if (index >= 0 && index < configurations.size()) {  // 检查索引是否有效
            configurations.set(index, newConfiguration);
        }
    }

    // 返回配置列表的副本
    public List<Configuration> getConfigurations() {
        return new ArrayList<>(configurations);  // 返回列表的副本以保护原始数据
    }

    // 获取所有配置（包括未启用的）
    public List<Configuration> getAllConfigurations() {
        return configurations;  // 返回存储的配置
    }
    
    // 根据名称获取配置
    public Configuration getConfigurationByName(String name) {
        // 简化实现，通过索引查找
        try {
            int index = Integer.parseInt(name.replace("规则 ", "")) - 1;
            if (index >= 0 && index < configurations.size()) {
                return configurations.get(index);
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return null;
    }

    // 获取启用的配置
    public List<Configuration> getEnabledConfigurations() {
        List<Configuration> enabledConfigs = new ArrayList<>();
        for (Configuration config : configurations) {
            if (config.isEnabled()) {
                enabledConfigs.add(config);
            }
        }
        return enabledConfigs;  // 返回启用的配置
    }

    // 将配置列表保存到磁盘
    public void saveToDisk(String filePath) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(configurations);  // 将配置列表写入文件
        } catch (IOException e) {
            e.printStackTrace();  // 打印异常堆栈信息
        }
    }

    // 从磁盘加载配置列表
    @SuppressWarnings("unchecked")
    public void loadFromDisk(String filePath) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            List<Configuration> loadedConfigurations = (List<Configuration>) ois.readObject();  // 读取配置列表
            configurations.clear();  // 清除当前列表
            configurations.addAll(loadedConfigurations);  // 添加加载的配置
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();  // 打印异常堆栈信息
        }
    }
}

package com.xprobe.scanner.config;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConfigurationManager {
    private final List<Configuration> configurations;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ConfigurationManager() {
        this.configurations = new ArrayList<>();
    }

    public void addConfiguration(Configuration configuration) {
        if (configuration == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            configurations.add(configuration);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeConfiguration(int index) {
        lock.writeLock().lock();
        try {
            if (index >= 0 && index < configurations.size()) {
                configurations.remove(index);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateConfiguration(int index, Configuration newConfiguration) {
        if (newConfiguration == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (index >= 0 && index < configurations.size()) {
                configurations.set(index, newConfiguration);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Configuration> getConfigurations() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(configurations);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Configuration> getAllConfigurations() {
        return getConfigurations();
    }

    public Configuration getConfigurationByName(String name) {
        if (name == null) {
            return null;
        }

        lock.readLock().lock();
        try {
            try {
                int index = Integer.parseInt(name.replace("规则 ", "")) - 1;
                if (index >= 0 && index < configurations.size()) {
                    return configurations.get(index);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Configuration> getEnabledConfigurations() {
        lock.readLock().lock();
        try {
            List<Configuration> enabledConfigs = new ArrayList<>();
            for (Configuration config : configurations) {
                if (config != null && config.isEnabled()) {
                    enabledConfigs.add(config);
                }
            }
            return enabledConfigs;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveToDisk(String filePath) {
        // 锁内只做快照，锁外做IO，避免高并发下长时间持锁
        List<Configuration> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(configurations);
        } finally {
            lock.readLock().unlock();
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(snapshot);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadFromDisk(String filePath) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            List<Configuration> loadedConfigurations = (List<Configuration>) ois.readObject();
            lock.writeLock().lock();
            try {
                configurations.clear();
                if (loadedConfigurations != null) {
                    configurations.addAll(loadedConfigurations);
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}

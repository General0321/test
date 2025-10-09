package com.xprobe.scanner.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ✅ 线程安全的有界缓存（FIFO - 先进先出）
 * 
 * 设计说明：
 * - 对于日志/去重场景，应该使用FIFO而不是LRU
 * - FIFO: 严格按插入时间淘汰最早的条目
 * - LRU: 淘汰最久未访问的条目（会保留热点数据）
 * 
 * 使用场景：
 * - 去重集合：防止重复扫描同一URL
 * - 日志缓存：固定大小的滚动窗口
 * - 已处理请求记录
 * 
 * 工作原理：
 * 1. 设置容量上限（如100,000条）
 * 2. 当第100,001条数据进来时
 * 3. 自动删除最早插入的那一条
 * 4. 保持总数始终 ≤ 上限
 * 
 * 性能：
 * - 插入: O(1)
 * - 查询: O(1)
 * - 删除最早的: O(1)
 * - 线程安全: 读写锁分离
 * 
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class BoundedCache<K, V> {
    
    private final int maxSize;
    private final LinkedHashMap<K, V> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * 创建有界缓存（FIFO）
     * 
     * @param maxSize 最大容量，超出后自动删除最早插入的条目
     */
    public BoundedCache(int maxSize) {
        this.maxSize = maxSize;
        
        // ✅ accessOrder = false → FIFO模式（按插入顺序）
        // - false: 按插入顺序，最早插入的在头部，超出容量时删除头部
        // - true: LRU模式，访问过的移到尾部（不适合日志场景）
        this.cache = new LinkedHashMap<K, V>(maxSize, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                // 当size > maxSize时，自动删除最早插入的条目
                return size() > BoundedCache.this.maxSize;
            }
        };
    }
    
    /**
     * 添加条目（如果缓存已满，自动删除最早的）
     * 
     * @param key 键
     * @param value 值
     * @return 如果是新增返回true，如果键已存在返回false
     */
    public boolean put(K key, V value) {
        lock.writeLock().lock();
        try {
            boolean wasNew = !cache.containsKey(key);
            cache.put(key, value);
            return wasNew;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 检查是否包含键
     * 
     * 注意：在FIFO模式下，containsKey不会改变条目的顺序
     * 
     * @param key 键
     * @return 是否存在
     */
    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            return cache.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取值
     * 
     * 注意：在FIFO模式下，get不会改变条目的顺序
     * 
     * @param key 键
     * @return 值，如果不存在返回null
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            return cache.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 移除条目
     * 
     * @param key 键
     * @return 被移除的值
     */
    public V remove(K key) {
        lock.writeLock().lock();
        try {
            return cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 清空缓存
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前大小
     * 
     * @return 当前条目数
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取最大容量
     * 
     * @return 最大条目数
     */
    public int getMaxSize() {
        return maxSize;
    }
}

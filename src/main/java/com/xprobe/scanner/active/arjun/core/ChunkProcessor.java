package com.xprobe.scanner.active.arjun.core;

import java.util.*;

/**
 * 分块处理器 - 将参数列表分成多个组
 */
public class ChunkProcessor {
    
    private final int defaultChunkSize;
    
    public ChunkProcessor(int defaultChunkSize) {
        this.defaultChunkSize = defaultChunkSize;
    }
    
    /**
     * 创建参数分块
     * 
     * @param params 参数集合
     * @param chunkSize 每块大小
     * @return 参数分块列表
     */
    public List<Set<String>> createChunks(Set<String> params, int chunkSize) {
        List<Set<String>> chunks = new ArrayList<>();
        
        if (params == null || params.isEmpty()) {
            return chunks;
        }
        
        // 确保chunk大小合理
        int effectiveChunkSize = Math.max(1, chunkSize);
        
        List<String> paramList = new ArrayList<>(params);
        
        for (int i = 0; i < paramList.size(); i += effectiveChunkSize) {
            int end = Math.min(i + effectiveChunkSize, paramList.size());
            Set<String> chunk = new LinkedHashSet<>(paramList.subList(i, end));
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * 创建参数分块（使用默认大小）
     */
    public List<Set<String>> createChunks(Set<String> params) {
        return createChunks(params, defaultChunkSize);
    }
    
    /**
     * 动态计算最优chunk大小
     * 
     * @param totalParams 总参数数量
     * @param maxChunks 最大分块数
     * @return 推荐的chunk大小
     */
    public int calculateOptimalChunkSize(int totalParams, int maxChunks) {
        if (totalParams <= maxChunks) {
            return 1;  // 每个参数单独一块
        }
        
        // 计算每块应该有多少参数
        int chunkSize = (int) Math.ceil((double) totalParams / maxChunks);
        
        // 限制最小和最大值
        return Math.max(1, Math.min(chunkSize, defaultChunkSize));
    }
}


package com.tenxengage.admin.config;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidationPublisher {
    private static final String CHANNEL = "tenx:cache-invalidation";
    private final RedisTemplate<String, Object> redisTemplate;

    public CacheInvalidationPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictAll(String cacheName) {
        redisTemplate.convertAndSend(CHANNEL, cacheName + ":*");
    }

    public void evict(String cacheName, String key) {
        redisTemplate.convertAndSend(CHANNEL, cacheName + ":" + key);
    }
}

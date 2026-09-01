package com.tenxengage.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);
    private static final String CHANNEL = "tenx:cache-invalidation";

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheManager cacheManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (Message message, byte[] pattern) -> {
            String payload = new String(message.getBody());
            handleInvalidation(payload, cacheManager);
        };

        container.addMessageListener(listener, new ChannelTopic(CHANNEL));
        return container;
    }

    private void handleInvalidation(String payload, CacheManager cacheManager) {
        // Payload format: "cacheName:key" or "cacheName:*" for evict all
        int colonIdx = payload.indexOf(':');
        if (colonIdx < 0) {
            return;
        }

        String cacheName = payload.substring(0, colonIdx);
        String key = payload.substring(colonIdx + 1);

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }

        if ("*".equals(key)) {
            cache.clear();
            log.info("Evicted all entries from cache '{}'", cacheName);
        } else {
            cache.evict(key);
            log.info("Evicted key '{}' from cache '{}'", key, cacheName);
        }
    }
}

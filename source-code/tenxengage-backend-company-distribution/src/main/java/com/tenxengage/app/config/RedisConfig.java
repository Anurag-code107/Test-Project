package com.tenxengage.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RedisConfig {

    /**
     * Jackson serializer with JavaTimeModule so LocalDate/Instant fields in cached DTOs
     * (e.g. DateWindowDto.from/to in RedemptionAnalyticsSummaryResponse) serialize correctly.
     * Type info is required by GenericJackson2JsonRedisSerializer for polymorphic deserialization.
     */
    private static GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // EVERYTHING (not NON_FINAL) is required because our cached DTOs are Java records,
        // which are implicitly final. NON_FINAL skips type-info for final classes, so Redis
        // would deserialize record elements as LinkedHashMap and the cache GET would fail.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(Map.of(
                "clientBySubdomain", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "enabledFeatures", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "productCategories", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "lmsCourseCategories", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "notificationTypes", defaultConfig.entryTtl(Duration.ofHours(1)),
                "giftCardCatalog", defaultConfig.entryTtl(Duration.ofHours(6)),
                "forecastResult", defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "effectivePermissions", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "redemption-analytics", defaultConfig.entryTtl(Duration.ofSeconds(60)),
                "advanced-analytics-item-breakdown", defaultConfig.entryTtl(Duration.ofSeconds(60))
            ))
            // Defer cache writes/evicts to AFTER the surrounding transaction commits. Without this,
            // PermissionService evicts the effectivePermissions cache *before* the override write
            // commits, so a concurrent read re-caches stale permissions that then survive until the
            // 5-min TTL — the "permission change not reflected until later" bug.
            .transactionAware()
            .build();
    }
}

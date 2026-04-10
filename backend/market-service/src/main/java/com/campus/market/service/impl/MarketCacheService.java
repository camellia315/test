package com.campus.market.service.impl;

import com.campus.market.entity.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class MarketCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MarketCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Product> getProducts(String cacheKey) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, new TypeReference<List<Product>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    public void putProducts(String cacheKey, List<Product> products, Duration ttl) {
        if (products == null) {
            return;
        }
        try {
            String raw = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(cacheKey, raw, ttl);
        } catch (RedisConnectionFailureException ignore) {
            // Redis is optional for local dev.
        } catch (Exception ignore) {
            // Ignore cache serialization failure and keep main flow available.
        }
    }

    public void evictByPrefix(String prefix) {
        try {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignore) {
            // Ignore cache cleanup failure.
        }
    }
}


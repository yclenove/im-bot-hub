package com.sov.telegram.bot.service.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sov.telegram.bot.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token-bucket rate limit per (botId, chatId) using Bucket4j; bucket entries evicted by Caffeine (TTL + max entries).
 */
@Component
@RequiredArgsConstructor
public class WebhookRateLimiter {

    private final AppProperties appProperties;

    private Bandwidth bandwidth;
    private Cache<String, Bucket> bucketCache;

    @PostConstruct
    void init() {
        int capacity = appProperties.getRateLimit().getCapacity();
        int refillPerMinute = appProperties.getRateLimit().getRefillPerMinute();
        bandwidth =
                Bandwidth.classic(
                        capacity,
                        Refill.greedy(refillPerMinute, Duration.ofMinutes(1)));
        bucketCache =
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterAccess(Duration.ofHours(24))
                        .build();
    }

    public boolean allow(long botId, long chatId) {
        return allowKey("tg:" + botId + ":" + chatId);
    }

    /** 任意 IM 限流键，例如 {@code tg:1:123} 或 {@code LARK:1:5:ou_xxx} */
    public boolean allowKey(String key) {
        Bucket bucket =
                bucketCache.get(key, k -> Bucket.builder().addLimit(bandwidth).build());
        return bucket.tryConsume(1);
    }
}

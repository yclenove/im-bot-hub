package com.sov.imhub.performance;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能优化服务：缓存、查询优化、性能监控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // 多级缓存（使用 Caffeine 自动管理大小和过期）
    private final Cache<String, Object> l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .build();

    private final Cache<String, CacheEntry<?>> queryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(5000)
            .build();

    /**
     * 缓存查询结果。
     */
    @SuppressWarnings("unchecked")
    public <T> T cacheQuery(String key, long ttlSeconds, QuerySupplier<T> supplier) {
        CacheEntry<?> entry = queryCache.getIfPresent(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("cache hit: {}", key);
            return (T) entry.getValue();
        }

        T result = supplier.get();
        queryCache.put(key, new CacheEntry<>(result, ttlSeconds));
        log.debug("cache miss: {}", key);
        return result;
    }

    /**
     * 清除缓存。
     */
    public void clearCache(String prefix) {
        queryCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        log.info("cache cleared: prefix={}", prefix);
    }

    /**
     * 获取缓存统计。
     */
    public CacheStats getCacheStats() {
        CacheStats stats = new CacheStats();
        stats.setL1Size(l1Cache.estimatedSize());
        stats.setL2Size(queryCache.estimatedSize());
        return stats;
    }

    /**
     * 查询优化建议。
     */
    public List<OptimizationHint> getOptimizationHints() {
        List<OptimizationHint> hints = new java.util.ArrayList<>();

        // 1. 检查慢查询
        List<Map<String, Object>> slowQueries = jdbcTemplate.queryForList(
                """
                SELECT command, AVG(duration_ms) as avg_duration, COUNT(*) as count
                FROM t_command_log
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 DAY) AND success = 1
                GROUP BY command
                HAVING avg_duration > 5000
                ORDER BY avg_duration DESC
                LIMIT 10
                """,
                Map.of());

        for (Map<String, Object> row : slowQueries) {
            OptimizationHint hint = new OptimizationHint();
            hint.setType("SLOW_QUERY");
            hint.setSeverity("HIGH");
            hint.setTitle("慢查询: " + row.get("command"));
            hint.setDescription("平均耗时 " + String.format("%.0f", ((Number) row.get("avg_duration")).doubleValue()) + "ms");
            hint.setSuggestion("建议优化查询或添加索引");
            hints.add(hint);
        }

        // 2. 检查高频查询
        List<Map<String, Object>> highFreqQueries = jdbcTemplate.queryForList(
                """
                SELECT command, COUNT(*) as count
                FROM t_command_log
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
                GROUP BY command
                HAVING count > 1000
                ORDER BY count DESC
                LIMIT 10
                """,
                Map.of());

        for (Map<String, Object> row : highFreqQueries) {
            OptimizationHint hint = new OptimizationHint();
            hint.setType("HIGH_FREQUENCY");
            hint.setSeverity("MEDIUM");
            hint.setTitle("高频查询: " + row.get("command"));
            hint.setDescription("今日执行 " + row.get("count") + " 次");
            hint.setSuggestion("建议启用缓存");
            hints.add(hint);
        }

        return hints;
    }

    /**
     * 数据库连接池监控。
     */
    public Map<String, Object> getConnectionPoolStats() {
        // 这里应该从 HikariCP 获取连接池统计
        return Map.of(
                "active", 0,
                "idle", 0,
                "total", 0,
                "waiting", 0);
    }

    /**
     * 查询供应商。
     */
    @FunctionalInterface
    public interface QuerySupplier<T> {
        T get();
    }

    /**
     * 缓存条目。
     */
    @Data
    private static class CacheEntry<T> {
        private final T value;
        private final long expireAt;

        public CacheEntry(T value, long ttlSeconds) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 缓存统计。
     */
    @Data
    public static class CacheStats {
        private long l1Size;
        private long l2Size;
    }

    /**
     * 优化提示。
     */
    @Data
    public static class OptimizationHint {
        private String type;
        private String severity;
        private String title;
        private String description;
        private String suggestion;
    }
}

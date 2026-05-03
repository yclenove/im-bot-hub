package com.sov.imhub.gateway;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 网关服务：统一 API 入口，支持限流、认证、监控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiGatewayService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // 限流计数器
    private final ConcurrentHashMap<String, AtomicLong> rateLimitCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> rateLimitResetTimes = new ConcurrentHashMap<>();

    /**
     * 检查 API Key 是否有效。
     */
    public boolean validateApiKey(String apiKey) {
        List<Map<String, Object>> keys = jdbcTemplate.queryForList(
                "SELECT * FROM t_api_key WHERE api_key = :apiKey AND enabled = 1",
                Map.of("apiKey", apiKey));

        if (keys.isEmpty()) {
            return false;
        }

        Map<String, Object> key = keys.get(0);

        // 检查是否过期
        LocalDateTime expiresAt = (LocalDateTime) key.get("expires_at");
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }

        // 更新最后使用时间
        jdbcTemplate.update(
                "UPDATE t_api_key SET last_used_at = NOW() WHERE api_key = :apiKey",
                Map.of("apiKey", apiKey));

        return true;
    }

    /**
     * 检查限流。
     */
    public boolean checkRateLimit(String apiKey) {
        List<Map<String, Object>> keys = jdbcTemplate.queryForList(
                "SELECT rate_limit FROM t_api_key WHERE api_key = :apiKey",
                Map.of("apiKey", apiKey));

        if (keys.isEmpty()) {
            return true;
        }

        int rateLimit = ((Number) keys.get(0).get("rate_limit")).intValue();
        String counterKey = apiKey + ":" + LocalDateTime.now().getMinute();

        AtomicLong counter = rateLimitCounters.computeIfAbsent(counterKey, k -> new AtomicLong(0));
        long currentCount = counter.incrementAndGet();

        // 每分钟重置
        rateLimitResetTimes.computeIfAbsent(apiKey, k -> LocalDateTime.now().plusMinutes(1));

        if (currentCount > rateLimit) {
            log.warn("rate limit exceeded: apiKey={}, count={}", apiKey, currentCount);
            return false;
        }

        return true;
    }

    /**
     * 获取 API 使用统计。
     */
    public List<Map<String, Object>> getApiUsageStats(LocalDateTime startDate, LocalDateTime endDate) {
        return jdbcTemplate.queryForList(
                """
                SELECT api_key, COUNT(*) as request_count, AVG(response_time_ms) as avg_response_time
                FROM t_api_usage_log
                WHERE created_at BETWEEN :startDate AND :endDate
                GROUP BY api_key
                ORDER BY request_count DESC
                """,
                Map.of("startDate", startDate, "endDate", endDate));
    }

    /**
     * 记录 API 使用。
     */
    public void logApiUsage(String apiKey, String endpoint, int statusCode, long responseTimeMs) {
        jdbcTemplate.update(
                """
                INSERT INTO t_api_usage_log (api_key, endpoint, status_code, response_time_ms, created_at)
                VALUES (:apiKey, :endpoint, :statusCode, :responseTimeMs, NOW())
                """,
                Map.of(
                        "apiKey", apiKey,
                        "endpoint", endpoint,
                        "statusCode", statusCode,
                        "responseTimeMs", responseTimeMs));
    }

    /**
     * 获取 API Key 列表。
     */
    public List<Map<String, Object>> getApiKeys() {
        return jdbcTemplate.queryForList(
                "SELECT id, key_name, api_key, description, permissions, rate_limit, enabled, expires_at, last_used_at, created_at FROM t_api_key ORDER BY created_at DESC",
                Map.of());
    }

    /**
     * 创建 API Key。
     */
    public Map<String, Object> createApiKey(String keyName, String description, String permissions,
                                             int rateLimit, LocalDateTime expiresAt) {
        String apiKey = generateApiKey();
        String secretKey = generateSecretKey();

        jdbcTemplate.update(
                """
                INSERT INTO t_api_key (key_name, api_key, secret_key, description, permissions, rate_limit, enabled, expires_at, created_at)
                VALUES (:keyName, :apiKey, :secretKey, :description, :permissions, :rateLimit, 1, :expiresAt, NOW())
                """,
                Map.of(
                        "keyName", keyName,
                        "apiKey", apiKey,
                        "secretKey", secretKey,
                        "description", description,
                        "permissions", permissions,
                        "rateLimit", rateLimit,
                        "expiresAt", expiresAt));

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);

        log.info("API key created: id={}, keyName={}", id, keyName);

        return Map.of(
                "id", id,
                "apiKey", apiKey,
                "secretKey", secretKey,
                "keyName", keyName);
    }

    /**
     * 禁用 API Key。
     */
    public void disableApiKey(Long id) {
        jdbcTemplate.update("UPDATE t_api_key SET enabled = 0 WHERE id = :id", Map.of("id", id));
    }

    /**
     * 启用 API Key。
     */
    public void enableApiKey(Long id) {
        jdbcTemplate.update("UPDATE t_api_key SET enabled = 1 WHERE id = :id", Map.of("id", id));
    }

    private String generateApiKey() {
        return "imhub_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    private String generateSecretKey() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * API 网关统计。
     */
    @Data
    public static class GatewayStats {
        private long totalRequests;
        private long successRequests;
        private long failedRequests;
        private double avgResponseTime;
        private int activeApiKeys;
    }
}

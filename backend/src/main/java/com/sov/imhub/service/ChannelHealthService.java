package com.sov.imhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.ChannelTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道健康检查服务：定时检测各渠道连通性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelHealthService {

    private final BotChannelMapper botChannelMapper;
    private final ChannelTestService channelTestService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 缓存的健康状态：channelId -> status */
    private final ConcurrentHashMap<Long, HealthStatus> healthCache = new ConcurrentHashMap<>();

    /**
     * 每 5 分钟检查所有启用的渠道。
     */
    @Scheduled(fixedRate = 300_000)
    public void checkAllChannels() {
        List<BotChannelEntity> channels = botChannelMapper.selectList(
                new LambdaQueryWrapper<BotChannelEntity>()
                        .eq(BotChannelEntity::getEnabled, true));

        log.info("channel health check start count={}", channels.size());

        for (BotChannelEntity channel : channels) {
            try {
                HealthStatus status = checkChannel(channel);
                healthCache.put(channel.getId(), status);

                // 记录到数据库
                saveHealthLog(channel.getId(), status);

                if (!status.isHealthy()) {
                    log.warn("channel unhealthy channelId={} platform={} message={}",
                            channel.getId(), channel.getPlatform(), status.message());
                }
            } catch (Exception e) {
                log.warn("channel health check failed channelId={}: {}", channel.getId(), e.getMessage());
            }
        }

        log.info("channel health check complete");
    }

    /**
     * 检查单个渠道健康状态。
     */
    private HealthStatus checkChannel(BotChannelEntity channel) {
        long start = System.currentTimeMillis();
        try {
            var result = channelTestService.testChannel(channel.getId(), null);
            long duration = System.currentTimeMillis() - start;

            if (result.success()) {
                return new HealthStatus("HEALTHY", result.message(), duration);
            } else {
                return new HealthStatus("UNHEALTHY", result.message(), duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new HealthStatus("UNHEALTHY", e.getMessage(), duration);
        }
    }

    /**
     * 保存健康检查日志。
     */
    private void saveHealthLog(Long channelId, HealthStatus status) {
        String sql = """
            INSERT INTO t_channel_health_log
                (channel_id, status, check_type, message, response_time_ms, checked_at)
            VALUES (:channelId, :status, :checkType, :message, :responseTimeMs, NOW())
            """;
        jdbcTemplate.update(sql, Map.of(
                "channelId", channelId,
                "status", status.status(),
                "checkType", "CONNECTIVITY",
                "message", status.message() != null ? status.message() : "",
                "responseTimeMs", status.responseTimeMs()));
    }

    /**
     * 获取所有渠道的健康状态。
     */
    public Map<Long, HealthStatus> getAllHealthStatus() {
        return new HashMap<>(healthCache);
    }

    /**
     * 获取指定渠道的健康状态。
     */
    public HealthStatus getChannelHealth(Long channelId) {
        return healthCache.get(channelId);
    }

    /**
     * 获取渠道健康历史。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHealthHistory(Long channelId, int limit) {
        String sql = """
            SELECT status, check_type, message, response_time_ms, checked_at
            FROM t_channel_health_log
            WHERE channel_id = :channelId
            ORDER BY checked_at DESC
            LIMIT :limit
            """;
        return (List<Map<String, Object>>) (List<?>) jdbcTemplate.queryForList(sql, Map.of("channelId", channelId, "limit", limit));
    }

    /**
     * 健康状态记录。
     */
    public record HealthStatus(String status, String message, long responseTimeMs) {
        public boolean isHealthy() {
            return "HEALTHY".equals(status);
        }
    }
}

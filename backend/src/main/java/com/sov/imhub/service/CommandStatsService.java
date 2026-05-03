package com.sov.imhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 命令统计服务：每日聚合 + 统计查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandStatsService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨 1 点聚合前一天的命令统计。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateDailyStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("aggregating command stats for date={}", yesterday);

        String sql = """
            INSERT INTO t_command_stats_daily
                (stat_date, bot_id, platform, command, total_count, success_count, fail_count, avg_duration_ms, unique_users)
            SELECT
                DATE(created_at) as stat_date,
                bot_id,
                platform,
                command,
                COUNT(*) as total_count,
                SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count,
                SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as fail_count,
                AVG(duration_ms) as avg_duration_ms,
                COUNT(DISTINCT external_user_id) as unique_users
            FROM t_command_log
            WHERE DATE(created_at) = :statDate
            GROUP BY DATE(created_at), bot_id, platform, command
            ON DUPLICATE KEY UPDATE
                total_count = VALUES(total_count),
                success_count = VALUES(success_count),
                fail_count = VALUES(fail_count),
                avg_duration_ms = VALUES(avg_duration_ms),
                unique_users = VALUES(unique_users)
            """;

        int rows = jdbcTemplate.update(sql, Map.of("statDate", yesterday));
        log.info("aggregated command stats date={} rows={}", yesterday, rows);
    }

    /**
     * 获取指定时间范围的命令统计。
     */
    public List<Map<String, Object>> getStats(Long botId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                stat_date,
                command,
                SUM(total_count) as total_count,
                SUM(success_count) as success_count,
                SUM(fail_count) as fail_count,
                AVG(avg_duration_ms) as avg_duration_ms,
                SUM(unique_users) as unique_users
            FROM t_command_stats_daily
            WHERE bot_id = :botId
              AND stat_date BETWEEN :startDate AND :endDate
            GROUP BY stat_date, command
            ORDER BY stat_date DESC, total_count DESC
            """;
        return jdbcTemplate.queryForList(sql, Map.of(
                "botId", botId,
                "startDate", java.sql.Date.valueOf(startDate),
                "endDate", java.sql.Date.valueOf(endDate)));
    }

    /**
     * 获取热门命令排行。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTopCommands(Long botId, int limit) {
        String sql = """
            SELECT
                command,
                SUM(total_count) as total_count,
                SUM(success_count) as success_count,
                SUM(fail_count) as fail_count,
                AVG(avg_duration_ms) as avg_duration_ms,
                SUM(unique_users) as unique_users
            FROM t_command_stats_daily
            WHERE bot_id = :botId
              AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY command
            ORDER BY total_count DESC
            LIMIT :limit
            """;
        return (List<Map<String, Object>>) (List<?>) jdbcTemplate.queryForList(sql, Map.of("botId", botId, "limit", limit));
    }

    /**
     * 获取平台维度统计。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPlatformStats(Long botId) {
        String sql = """
            SELECT
                platform,
                SUM(total_count) as total_count,
                SUM(success_count) as success_count,
                SUM(fail_count) as fail_count,
                AVG(avg_duration_ms) as avg_duration_ms
            FROM t_command_stats_daily
            WHERE bot_id = :botId
              AND stat_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY platform
            ORDER BY total_count DESC
            """;
        return (List<Map<String, Object>>) (List<?>) jdbcTemplate.queryForList(sql, Map.of("botId", botId));
    }
}

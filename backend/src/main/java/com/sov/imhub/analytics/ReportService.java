package com.sov.imhub.analytics;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表服务：数据可视化和定时报表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 获取仪表盘数据。
     */
    public DashboardData getDashboardData(Long botId) {
        DashboardData data = new DashboardData();

        // 1. 统计卡片
        data.setBotCount(getBotCount());
        data.setChannelCount(getChannelCount());
        data.setQueryCount(getQueryCount());
        data.setTodayCommands(getTodayCommands());

        // 2. 7天趋势
        data.setWeeklyTrend(getWeeklyTrend(botId));

        // 3. 平台分布
        data.setPlatformDistribution(getPlatformDistribution(botId));

        // 4. 热门命令
        data.setTopCommands(getTopCommands(botId, 10));

        // 5. 最近命令
        data.setRecentCommands(getRecentCommands(botId, 5));

        return data;
    }

    private long getBotCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_bot WHERE deleted = 0", Map.of(), Long.class);
    }

    private long getChannelCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_bot_channel", Map.of(), Long.class);
    }

    private long getQueryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_query_definition WHERE deleted = 0", Map.of(), Long.class);
    }

    private long getTodayCommands() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_command_log WHERE DATE(created_at) = CURDATE()",
                Map.of(), Long.class);
    }

    private List<Map<String, Object>> getWeeklyTrend(Long botId) {
        return jdbcTemplate.queryForList(
                """
                SELECT DATE(created_at) as date,
                       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success,
                       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as fail
                FROM t_command_log
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY DATE(created_at)
                ORDER BY date
                """,
                Map.of());
    }

    private List<Map<String, Object>> getPlatformDistribution(Long botId) {
        return jdbcTemplate.queryForList(
                """
                SELECT platform, COUNT(*) as count
                FROM t_command_log
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                GROUP BY platform
                ORDER BY count DESC
                """,
                Map.of());
    }

    private List<Map<String, Object>> getTopCommands(Long botId, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT command, COUNT(*) as count
                FROM t_command_log
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                GROUP BY command
                ORDER BY count DESC
                LIMIT :limit
                """,
                Map.of("limit", limit));
    }

    private List<Map<String, Object>> getRecentCommands(Long botId, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM t_command_log
                ORDER BY created_at DESC
                LIMIT :limit
                """,
                Map.of("limit", limit));
    }

    /**
     * 生成定时报表（CSV 格式）。
     */
    public byte[] generateReport(String reportType, LocalDate startDate, LocalDate endDate) {
        log.info("generating report: type={}, startDate={}, endDate={}", reportType, startDate, endDate);

        StringBuilder csv = new StringBuilder();

        // 表头
        csv.append("日期,命令,总数,成功数,失败数,平均耗时(ms),独立用户数\n");

        // 查询数据
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT stat_date, command, total_count, success_count, fail_count, avg_duration_ms, unique_users
                FROM t_command_stats_daily
                WHERE stat_date BETWEEN :startDate AND :endDate
                ORDER BY stat_date DESC, total_count DESC
                """,
                Map.of("startDate", startDate, "endDate", endDate));

        for (Map<String, Object> row : rows) {
            csv.append(row.get("stat_date")).append(",");
            csv.append(row.get("command")).append(",");
            csv.append(row.get("total_count")).append(",");
            csv.append(row.get("success_count")).append(",");
            csv.append(row.get("fail_count")).append(",");
            csv.append(row.get("avg_duration_ms")).append(",");
            csv.append(row.get("unique_users")).append("\n");
        }

        // 添加 UTF-8 BOM 以支持中文
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);

        return result;
    }

    /**
     * 仪表盘数据。
     */
    @Data
    public static class DashboardData {
        private long botCount;
        private long channelCount;
        private long queryCount;
        private long todayCommands;
        private List<Map<String, Object>> weeklyTrend;
        private List<Map<String, Object>> platformDistribution;
        private List<Map<String, Object>> topCommands;
        private List<Map<String, Object>> recentCommands;
    }
}

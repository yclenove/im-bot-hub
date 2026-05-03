package com.sov.imhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 告警服务：规则检查 + 通知发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 每 5 分钟检查告警规则。
     */
    @Scheduled(fixedRate = 300_000)
    public void checkAlerts() {
        log.info("checking alert rules");

        // 检查成功率告警
        checkSuccessRateAlert();

        // 检查响应时间告警
        checkResponseTimeAlert();

        log.info("alert check complete");
    }

    @SuppressWarnings("unchecked")
    private void checkSuccessRateAlert() {
        String sql = """
            SELECT
                bot_id,
                SUM(success_count) as success,
                SUM(total_count) as total
            FROM t_command_stats_daily
            WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY)
            GROUP BY bot_id
            """;

        List<Map<String, Object>> stats = (List<Map<String, Object>>) (List<?>) jdbcTemplate.queryForList(sql, Map.of());
        for (Map<String, Object> row : stats) {
            long success = ((Number) row.get("success")).longValue();
            long total = ((Number) row.get("total")).longValue();
            if (total > 0) {
                double rate = (double) success / total * 100;
                if (rate < 90) {
                    triggerAlert("SUCCESS_RATE",
                            "成功率低于90%: " + String.format("%.1f", rate) + "%",
                            BigDecimal.valueOf(rate), BigDecimal.valueOf(90));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkResponseTimeAlert() {
        String sql = """
            SELECT bot_id, AVG(avg_duration_ms) as avg_ms
            FROM t_command_stats_daily
            WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY)
            GROUP BY bot_id
            HAVING avg_ms > 5000
            """;

        List<Map<String, Object>> stats = (List<Map<String, Object>>) (List<?>) jdbcTemplate.queryForList(sql, Map.of());
        for (Map<String, Object> row : stats) {
            double avgMs = ((Number) row.get("avg_ms")).doubleValue();
            triggerAlert("RESPONSE_TIME",
                    "平均响应时间超过5秒: " + String.format("%.0f", avgMs) + "ms",
                    BigDecimal.valueOf(avgMs), BigDecimal.valueOf(5000));
        }
    }

    private void triggerAlert(String alertType, String message, BigDecimal currentValue, BigDecimal threshold) {
        log.warn("ALERT: {}", message);

        String insertSql = """
            INSERT INTO t_alert_log (alert_type, message, current_value, threshold, notified, created_at)
            VALUES (:alertType, :message, :currentValue, :threshold, 0, NOW())
            """;
        jdbcTemplate.update(insertSql, Map.of(
                "alertType", alertType,
                "message", message,
                "currentValue", currentValue,
                "threshold", threshold));
    }

    /**
     * 获取告警历史。
     */
    public List<Map<String, Object>> getAlertHistory(int limit) {
        String sql = """
            SELECT alert_type, message, current_value, threshold, notified, created_at
            FROM t_alert_log
            ORDER BY created_at DESC
            LIMIT :limit
            """;
        return jdbcTemplate.queryForList(sql, Map.of("limit", limit));
    }

    /**
     * 获取告警配置。
     */
    public List<Map<String, Object>> getAlertConfigs() {
        return jdbcTemplate.queryForList("SELECT * FROM t_alert_config ORDER BY id", Map.of());
    }
}

package com.sov.imhub.ai;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 异常检测服务：时序数据异常检测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 每 5 分钟执行异常检测。
     */
    @Scheduled(fixedRate = 300_000)
    public void runDetection() {
        log.info("running anomaly detection");

        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT * FROM t_anomaly_detection WHERE enabled = 1", Map.of());

        for (Map<String, Object> rule : rules) {
            try {
                detectAnomaly(rule);
            } catch (Exception e) {
                log.warn("anomaly detection failed for rule {}: {}", rule.get("name"), e.getMessage());
            }
        }
    }

    /**
     * 检测单个规则的异常。
     */
    private void detectAnomaly(Map<String, Object> rule) {
        String metricName = (String) rule.get("metric_name");
        String detectionType = (String) rule.get("detection_type");
        int windowSize = ((Number) rule.get("window_size")).intValue();

        // 获取历史数据
        List<DataPoint> history = getMetricHistory(metricName, windowSize);
        if (history.size() < 10) {
            return; // 数据不足
        }

        // 计算基线
        Baseline baseline = calculateBaseline(history);

        // 检测异常
        List<Anomaly> anomalies = new ArrayList<>();
        for (DataPoint point : history) {
            double zscore = calculateZScore(point, baseline);
            double threshold = ((BigDecimal) rule.get("zscore_threshold")).doubleValue();

            if (Math.abs(zscore) > threshold) {
                Anomaly anomaly = new Anomaly();
                anomaly.setDetectionId(((Number) rule.get("id")).longValue());
                anomaly.setMetricName(metricName);
                anomaly.setDetectedValue(point.getValue());
                anomaly.setBaselineValue(baseline.getMean());
                anomaly.setZscore(zscore);
                anomaly.setSeverity(classifySeverity(zscore));
                anomaly.setAnomalyType(classifyAnomalyType(point, baseline));
                anomalies.add(anomaly);
            }
        }

        // 保存异常记录
        for (Anomaly anomaly : anomalies) {
            saveAnomaly(anomaly);
        }

        if (!anomalies.isEmpty()) {
            log.warn("detected {} anomalies for metric {}", anomalies.size(), metricName);
        }
    }

    /**
     * 指标计算配置（支持动态扩展）。
     */
    private static final Map<String, String> METRIC_EXPRESSIONS = Map.of(
            "success_rate", "CASE WHEN SUM(total_count) > 0 THEN SUM(success_count) * 100.0 / SUM(total_count) ELSE 0 END",
            "avg_response_time", "AVG(avg_duration_ms)",
            "request_count", "SUM(total_count)",
            "unique_users", "SUM(unique_users)",
            "fail_count", "SUM(fail_count)"
    );

    /**
     * 获取指标历史数据（支持自定义指标表达式）。
     */
    private List<DataPoint> getMetricHistory(String metricName, int windowMinutes) {
        String expression = METRIC_EXPRESSIONS.getOrDefault(metricName, "0");

        String sql = "SELECT created_at as timestamp, " + expression + " as value " +
                "FROM t_command_stats_daily " +
                "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL :windowDays DAY) " +
                "GROUP BY stat_date ORDER BY stat_date";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, Map.of(
                "windowDays", Math.max(1, windowMinutes / 1440)));

        List<DataPoint> points = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DataPoint point = new DataPoint();
            point.setTimestamp(((java.sql.Date) row.get("timestamp")).toLocalDate().atStartOfDay());
            point.setValue(((Number) row.get("value")).doubleValue());
            points.add(point);
        }
        return points;
    }

    /**
     * 计算基线（均值 + 标准差）。
     */
    private Baseline calculateBaseline(List<DataPoint> history) {
        double sum = 0;
        for (DataPoint point : history) {
            sum += point.getValue();
        }
        double mean = sum / history.size();

        double sumSquaredDiff = 0;
        for (DataPoint point : history) {
            double diff = point.getValue() - mean;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / history.size());

        Baseline baseline = new Baseline();
        baseline.setMean(mean);
        baseline.setStdDev(stdDev);
        return baseline;
    }

    /**
     * 计算 Z-score。
     */
    private double calculateZScore(DataPoint point, Baseline baseline) {
        if (baseline.getStdDev() == 0) return 0;
        return (point.getValue() - baseline.getMean()) / baseline.getStdDev();
    }

    /**
     * 分类异常严重程度。
     */
    private String classifySeverity(double zscore) {
        double absZscore = Math.abs(zscore);
        if (absZscore > 5) return "CRITICAL";
        if (absZscore > 4) return "HIGH";
        if (absZscore > 3) return "MEDIUM";
        return "LOW";
    }

    /**
     * 分类异常类型。
     */
    private String classifyAnomalyType(DataPoint point, Baseline baseline) {
        if (point.getValue() > baseline.getMean()) {
            return "SPIKE";
        } else {
            return "DROP";
        }
    }

    /**
     * 保存异常记录。
     */
    private void saveAnomaly(Anomaly anomaly) {
        String sql = """
            INSERT INTO t_anomaly_log
            (detection_id, metric_name, anomaly_type, detected_value, baseline_value, zscore, severity, created_at)
            VALUES (:detectionId, :metricName, :anomalyType, :detectedValue, :baselineValue, :zscore, :severity, NOW())
            """;
        jdbcTemplate.update(sql, Map.of(
                "detectionId", anomaly.getDetectionId(),
                "metricName", anomaly.getMetricName(),
                "anomalyType", anomaly.getAnomalyType(),
                "detectedValue", anomaly.getDetectedValue(),
                "baselineValue", anomaly.getBaselineValue(),
                "zscore", anomaly.getZscore(),
                "severity", anomaly.getSeverity()));
    }

    /**
     * 获取异常历史。
     */
    public List<Map<String, Object>> getAnomalyHistory(int limit) {
        String sql = """
            SELECT * FROM t_anomaly_log
            ORDER BY created_at DESC
            LIMIT :limit
            """;
        return jdbcTemplate.queryForList(sql, Map.of("limit", limit));
    }

    /**
     * 数据点。
     */
    @Data
    public static class DataPoint {
        private LocalDateTime timestamp;
        private double value;
    }

    /**
     * 基线。
     */
    @Data
    public static class Baseline {
        private double mean;
        private double stdDev;
    }

    /**
     * 异常记录。
     */
    @Data
    public static class Anomaly {
        private Long id;
        private Long detectionId;
        private String metricName;
        private String anomalyType;
        private double detectedValue;
        private double baselineValue;
        private double zscore;
        private String severity;
        private String rootCause;
    }
}

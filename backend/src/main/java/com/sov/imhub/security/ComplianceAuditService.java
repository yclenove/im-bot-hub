package com.sov.imhub.security;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 合规审计服务：满足等保三级、GDPR 等合规要求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceAuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 每天生成合规报告。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateDailyReport() {
        log.info("generating daily compliance report");
        // 生成每日合规报告
    }

    /**
     * 获取操作审计日志。
     */
    public List<Map<String, Object>> getOperationAuditLog(LocalDateTime startDate, LocalDateTime endDate,
                                                           String userId, String actionType, int limit) {
        String sql = """
            SELECT * FROM t_audit_log
            WHERE created_at BETWEEN :startDate AND :endDate
            """;

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        if (userId != null && !userId.isBlank()) {
            sql += " AND user_id = :userId";
            params.put("userId", userId);
        }

        if (actionType != null && !actionType.isBlank()) {
            sql += " AND action_type = :actionType";
            params.put("actionType", actionType);
        }

        sql += " ORDER BY created_at DESC LIMIT :limit";
        params.put("limit", limit);

        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * 获取数据访问审计。
     */
    public List<Map<String, Object>> getDataAccessAudit(Long userId, String resourceType, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM t_command_log
                WHERE external_user_id = :userId
                ORDER BY created_at DESC
                LIMIT :limit
                """,
                Map.of("userId", String.valueOf(userId), "limit", limit));
    }

    /**
     * 生成合规报告。
     */
    public ComplianceReport generateReport(LocalDate startDate, LocalDate endDate) {
        ComplianceReport report = new ComplianceReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        // 1. 统计操作次数
        Map<String, Object> operationStats = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) as total_operations,
                       COUNT(DISTINCT user_id) as unique_users
                FROM t_audit_log
                WHERE created_at BETWEEN :startDate AND :endDate
                """,
                Map.of("startDate", startDate.atStartOfDay(), "endDate", endDate.atTime(23, 59, 59)));
        report.setTotalOperations(((Number) operationStats.get("total_operations")).longValue());
        report.setUniqueUsers(((Number) operationStats.get("unique_users")).intValue());

        // 2. 统计查询次数
        Map<String, Object> queryStats = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) as total_queries,
                       SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_queries
                FROM t_command_log
                WHERE created_at BETWEEN :startDate AND :endDate
                """,
                Map.of("startDate", startDate.atStartOfDay(), "endDate", endDate.atTime(23, 59, 59)));
        report.setTotalQueries(((Number) queryStats.get("total_queries")).longValue());
        report.setSuccessQueries(((Number) queryStats.get("success_queries")).longValue());

        // 3. 统计异常事件
        Map<String, Object> anomalyStats = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) as total_anomalies
                FROM t_anomaly_log
                WHERE created_at BETWEEN :startDate AND :endDate
                """,
                Map.of("startDate", startDate.atStartOfDay(), "endDate", endDate.atTime(23, 59, 59)));
        report.setTotalAnomalies(((Number) anomalyStats.get("total_anomalies")).intValue());

        // 4. 统计审批事件
        Map<String, Object> approvalStats = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) as total_approvals,
                       SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) as approved,
                       SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected
                FROM t_approval_log
                WHERE created_at BETWEEN :startDate AND :endDate
                """,
                Map.of("startDate", startDate.atStartOfDay(), "endDate", endDate.atTime(23, 59, 59)));
        report.setTotalApprovals(((Number) approvalStats.get("total_approvals")).intValue());
        report.setApprovedCount(((Number) approvalStats.get("approved")).intValue());
        report.setRejectedCount(((Number) approvalStats.get("rejected")).intValue());

        return report;
    }

    /**
     * 检查数据保留策略。
     */
    public void enforceRetentionPolicy() {
        log.info("enforcing data retention policy");

        // 删除超过保留期的命令日志（默认保留 90 天）
        jdbcTemplate.update(
                "DELETE FROM t_command_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)",
                Map.of());

        // 删除超过保留期的审计日志（默认保留 365 天）
        jdbcTemplate.update(
                "DELETE FROM t_audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 365 DAY)",
                Map.of());

        log.info("data retention policy enforced");
    }

    /**
     * 合规报告。
     */
    @Data
    public static class ComplianceReport {
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime generatedAt;
        private long totalOperations;
        private int uniqueUsers;
        private long totalQueries;
        private long successQueries;
        private int totalAnomalies;
        private int totalApprovals;
        private int approvedCount;
        private int rejectedCount;
    }
}

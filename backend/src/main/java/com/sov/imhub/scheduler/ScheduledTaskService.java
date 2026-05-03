package com.sov.imhub.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务增强服务：Cron 调度、依赖任务、任务队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 记录每个工作流上次执行时间，防止重复触发 */
    private final ConcurrentHashMap<Long, LocalDateTime> lastExecutionTimes = new ConcurrentHashMap<>();

    /**
     * 每分钟检查待执行的定时任务。
     */
    @Scheduled(fixedRate = 60_000)
    public void checkScheduledTasks() {
        log.debug("checking scheduled tasks");

        List<Map<String, Object>> workflows = jdbcTemplate.queryForList(
                """
                SELECT * FROM t_workflow_definition
                WHERE enabled = 1 AND trigger_type = 'CRON'
                """, Map.of());

        for (Map<String, Object> workflow : workflows) {
            try {
                checkAndExecuteWorkflow(workflow);
            } catch (Exception e) {
                log.warn("scheduled task failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查并执行工作流（基于 Cron 表达式）。
     */
    private void checkAndExecuteWorkflow(Map<String, Object> workflow) {
        Long workflowId = ((Number) workflow.get("id")).longValue();
        String triggerConfig = (String) workflow.get("trigger_config");

        if (triggerConfig == null || triggerConfig.isBlank()) {
            return;
        }

        try {
            Map<String, Object> config = objectMapper.readValue(triggerConfig, Map.class);
            String cronExpression = (String) config.get("cron");

            if (cronExpression == null || cronExpression.isBlank()) {
                return;
            }

            // 检查是否应该执行（基于 Cron 表达式）
            if (!shouldExecuteNow(cronExpression)) {
                return;
            }

            // 防止重复执行（同一分钟内不重复）
            LocalDateTime lastExecution = lastExecutionTimes.get(workflowId);
            LocalDateTime now = LocalDateTime.now();
            if (lastExecution != null && lastExecution.getMinute() == now.getMinute()
                    && lastExecution.getHour() == now.getHour()) {
                return;
            }

            // 触发工作流执行
            log.info("triggering scheduled workflow: {}", workflowId);
            triggerWorkflowExecution(workflowId, config);
            lastExecutionTimes.put(workflowId, now);

        } catch (Exception e) {
            log.error("failed to check workflow {}: {}", workflowId, e.getMessage());
        }
    }

    /**
     * 检查 Cron 表达式是否应该在当前时间执行。
     * 简化实现：支持基本的 Cron 格式（分 时 日 月 周）
     */
    private boolean shouldExecuteNow(String cronExpression) {
        String[] parts = cronExpression.split("\\s+");
        if (parts.length < 5) {
            log.warn("invalid cron expression: {}", cronExpression);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查分钟
        if (!matchesCronField(parts[0], now.getMinute())) return false;
        // 检查小时
        if (!matchesCronField(parts[1], now.getHour())) return false;
        // 检查日
        if (!matchesCronField(parts[2], now.getDayOfMonth())) return false;
        // 检查月
        if (!matchesCronField(parts[3], now.getMonthValue())) return false;
        // 检查星期
        if (!matchesCronField(parts[4], now.getDayOfWeek().getValue() % 7)) return false;

        return true;
    }

    /**
     * 匹配 Cron 字段。
     * 支持: 星号, 星号/N, N, N-M
     */
    private boolean matchesCronField(String field, int value) {
        if ("*".equals(field)) {
            return true;
        }
        if (field.startsWith("*/")) {
            int interval = Integer.parseInt(field.substring(2));
            return value % interval == 0;
        }
        if (field.contains("-")) {
            String[] range = field.split("-");
            int min = Integer.parseInt(range[0]);
            int max = Integer.parseInt(range[1]);
            return value >= min && value <= max;
        }
        return Integer.parseInt(field) == value;
    }

    /**
     * 触发工作流执行。
     */
    private void triggerWorkflowExecution(Long workflowId, Map<String, Object> config) {
        String sql = """
            INSERT INTO t_workflow_execution (workflow_id, status, input_json, started_at, triggered_by)
            VALUES (:workflowId, 'RUNNING', '{}', NOW(), 0)
            """;
        jdbcTemplate.update(sql, Map.of("workflowId", workflowId));

        Long executionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("scheduled workflow triggered: workflowId={}, executionId={}", workflowId, executionId);
    }

    /**
     * 创建定时任务。
     */
    public Long createScheduledTask(String name, String description, String cronExpression,
                                     Long workflowId, Long createdBy) {
        String sql = """
            INSERT INTO t_workflow_definition (name, description, trigger_type, trigger_config, enabled, created_by, created_at)
            VALUES (:name, :description, 'CRON', :triggerConfig, 1, :createdBy, NOW())
            """;

        String triggerConfig = "{\"cron\":\"" + cronExpression + "\",\"workflowId\":" + workflowId + "}";
        jdbcTemplate.update(sql, Map.of(
                "name", name,
                "description", description,
                "triggerConfig", triggerConfig,
                "createdBy", createdBy));

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("scheduled task created: id={}, name={}, cron={}", id, name, cronExpression);
        return id;
    }

    /**
     * 获取任务执行历史。
     */
    public List<Map<String, Object>> getTaskHistory(Long workflowId, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM t_workflow_execution
                WHERE workflow_id = :workflowId
                ORDER BY started_at DESC
                LIMIT :limit
                """,
                Map.of("workflowId", workflowId, "limit", limit));
    }

    /**
     * 获取任务队列状态。
     */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new java.util.HashMap<>();

        long runningCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'RUNNING'",
                Map.of(), Long.class);
        status.put("running", runningCount);

        long pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'PENDING'",
                Map.of(), Long.class);
        status.put("pending", pendingCount);

        long completedToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'COMPLETED' AND DATE(completed_at) = CURDATE()",
                Map.of(), Long.class);
        status.put("completedToday", completedToday);

        long failedToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'FAILED' AND DATE(started_at) = CURDATE()",
                Map.of(), Long.class);
        status.put("failedToday", failedToday);

        return status;
    }

    /**
     * 取消任务。
     */
    public void cancelTask(Long executionId) {
        jdbcTemplate.update(
                "UPDATE t_workflow_execution SET status = 'CANCELLED', completed_at = NOW() WHERE id = :id AND status = 'RUNNING'",
                Map.of("id", executionId));
        log.info("task cancelled: {}", executionId);
    }

    /**
     * 重试失败任务。
     */
    public Long retryTask(Long executionId) {
        Map<String, Object> execution = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_execution WHERE id = :id",
                Map.of("id", executionId));

        if (execution.isEmpty()) {
            throw new IllegalArgumentException("执行记录不存在");
        }

        Long workflowId = ((Number) execution.get("workflow_id")).longValue();
        String inputJson = (String) execution.get("input_json");
        Long triggeredBy = execution.get("triggered_by") != null ? ((Number) execution.get("triggered_by")).longValue() : 0L;

        String sql = """
            INSERT INTO t_workflow_execution (workflow_id, status, input_json, started_at, triggered_by)
            VALUES (:workflowId, 'RUNNING', :inputJson, NOW(), :triggeredBy)
            """;
        jdbcTemplate.update(sql, Map.of(
                "workflowId", workflowId,
                "inputJson", inputJson != null ? inputJson : "{}",
                "triggeredBy", triggeredBy));

        Long newExecutionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("task retried: old={}, new={}", executionId, newExecutionId);
        return newExecutionId;
    }
}

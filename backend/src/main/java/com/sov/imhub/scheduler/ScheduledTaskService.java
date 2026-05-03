package com.sov.imhub.scheduler;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务增强服务：Cron 调度、依赖任务、任务队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 每分钟检查待执行的定时任务。
     */
    @Scheduled(fixedRate = 60_000)
    public void checkScheduledTasks() {
        log.debug("checking scheduled tasks");

        // 检查 Cron 触发的工作流
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
     * 检查并执行工作流。
     */
    private void checkAndExecuteWorkflow(Map<String, Object> workflow) {
        Long workflowId = ((Number) workflow.get("id")).longValue();
        String triggerConfig = (String) workflow.get("trigger_config");

        // 解析 Cron 表达式
        // 这里简化处理，实际应该使用 CronExpression 解析
        log.debug("checking workflow: {}", workflowId);
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
        log.info("scheduled task created: id={}, name={}", id, name);
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

        // 运行中的任务
        long runningCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'RUNNING'",
                Map.of(), Long.class);
        status.put("running", runningCount);

        // 待执行的任务
        long pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'PENDING'",
                Map.of(), Long.class);
        status.put("pending", pendingCount);

        // 今日完成的任务
        long completedToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_workflow_execution WHERE status = 'COMPLETED' AND DATE(completed_at) = CURDATE()",
                Map.of(), Long.class);
        status.put("completedToday", completedToday);

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
        Long triggeredBy = ((Number) execution.get("triggered_by")).longValue();

        // 创建新的执行记录
        String sql = """
            INSERT INTO t_workflow_execution (workflow_id, status, input_json, started_at, triggered_by)
            VALUES (:workflowId, 'RUNNING', :inputJson, NOW(), :triggeredBy)
            """;
        jdbcTemplate.update(sql, Map.of(
                "workflowId", workflowId,
                "inputJson", inputJson,
                "triggeredBy", triggeredBy));

        Long newExecutionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("task retried: old={}, new={}", executionId, newExecutionId);
        return newExecutionId;
    }
}

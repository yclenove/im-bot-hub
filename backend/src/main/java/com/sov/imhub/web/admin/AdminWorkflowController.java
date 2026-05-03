package com.sov.imhub.web.admin;

import com.sov.imhub.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流 API。
 */
@RestController
@RequestMapping("/api/admin/workflows")
@RequiredArgsConstructor
public class AdminWorkflowController {

    private final WorkflowEngine workflowEngine;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 获取工作流列表。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM t_workflow_definition ORDER BY created_at DESC", Map.of());
    }

    /**
     * 获取单个工作流。
     */
    @GetMapping("/{workflowId}")
    public Map<String, Object> get(@PathVariable Long workflowId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_definition WHERE id = :id", Map.of("id", workflowId));
    }

    /**
     * 创建工作流。
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String sql = """
            INSERT INTO t_workflow_definition (name, description, steps_json, variables_json, trigger_type, trigger_config, enabled, created_by, created_at)
            VALUES (:name, :description, :stepsJson, :variablesJson, :triggerType, :triggerConfig, 1, :createdBy, NOW())
            """;
        jdbcTemplate.update(sql, Map.of(
                "name", body.get("name"),
                "description", body.get("description"),
                "stepsJson", body.get("stepsJson"),
                "variablesJson", body.get("variablesJson"),
                "triggerType", body.get("triggerType"),
                "triggerConfig", body.get("triggerConfig"),
                "createdBy", body.get("createdBy")));

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        return Map.of("success", true, "id", id);
    }

    /**
     * 执行工作流。
     */
    @PostMapping("/{workflowId}/execute")
    public Map<String, Object> execute(
            @PathVariable Long workflowId,
            @RequestBody Map<String, Object> body) {
        Long triggeredBy = ((Number) body.get("triggeredBy")).longValue();
        Long executionId = workflowEngine.execute(workflowId, body, triggeredBy);
        return Map.of("success", true, "executionId", executionId);
    }

    /**
     * 获取执行记录。
     */
    @GetMapping("/{workflowId}/executions")
    public List<Map<String, Object>> getExecutions(@PathVariable Long workflowId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM t_workflow_execution WHERE workflow_id = :workflowId ORDER BY started_at DESC",
                Map.of("workflowId", workflowId));
    }

    /**
     * 获取执行详情。
     */
    @GetMapping("/executions/{executionId}")
    public Map<String, Object> getExecution(@PathVariable Long executionId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_execution WHERE id = :id", Map.of("id", executionId));
    }

    /**
     * 获取步骤日志。
     */
    @GetMapping("/executions/{executionId}/steps")
    public List<Map<String, Object>> getStepLogs(@PathVariable Long executionId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM t_workflow_step_log WHERE execution_id = :executionId ORDER BY started_at",
                Map.of("executionId", executionId));
    }
}

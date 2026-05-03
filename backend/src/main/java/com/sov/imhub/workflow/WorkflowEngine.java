package com.sov.imhub.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 执行工作流。
     *
     * @param workflowId 工作流 ID
     * @param input 输入参数
     * @param triggeredBy 触发者用户 ID
     * @return 执行记录 ID
     */
    @Async
    @Transactional
    public Long execute(Long workflowId, Map<String, Object> input, Long triggeredBy) {
        log.info("workflow execute workflowId={} triggeredBy={}", workflowId, triggeredBy);

        // 1. 获取工作流定义
        Map<String, Object> workflow = jdbcTemplate.queryForMap(
                "SELECT * FROM t_workflow_definition WHERE id = :id AND enabled = 1",
                Map.of("id", workflowId));

        if (workflow.isEmpty()) {
            throw new IllegalArgumentException("工作流不存在或已禁用");
        }

        // 2. 创建执行记录
        Long executionId = createExecution(workflowId, input, triggeredBy);

        try {
            // 3. 解析步骤
            List<WorkflowStep> steps = objectMapper.readValue(
                    (String) workflow.get("steps_json"),
                    new TypeReference<List<WorkflowStep>>() {});

            // 4. 执行步骤
            executeSteps(steps, executionId, input);

            // 5. 更新执行状态
            updateExecutionStatus(executionId, "COMPLETED", null);

        } catch (Exception e) {
            log.error("workflow execution failed: {}", e.getMessage());
            updateExecutionStatus(executionId, "FAILED", e.getMessage());
        }

        return executionId;
    }

    /**
     * 执行步骤列表（支持并行执行）。
     */
    private void executeSteps(List<WorkflowStep> steps, Long executionId, Map<String, Object> context) {
        // 分离可并行和必须串行的步骤
        List<WorkflowStep> parallelSteps = steps.stream()
                .filter(s -> s.getConfig() != null && Boolean.TRUE.equals(s.getConfig().get("parallel")))
                .toList();
        List<WorkflowStep> sequentialSteps = steps.stream()
                .filter(s -> s.getConfig() == null || !Boolean.TRUE.equals(s.getConfig().get("parallel")))
                .toList();

        // 先并行执行可并行的步骤
        if (!parallelSteps.isEmpty()) {
            List<java.util.concurrent.CompletableFuture<Void>> futures = parallelSteps.stream()
                    .map(step -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            executeStep(step, executionId, context);
                        } catch (Exception e) {
                            log.error("parallel step failed: {}", e.getMessage());
                        }
                    }))
                    .toList();

            // 等待所有并行步骤完成
            java.util.concurrent.CompletableFuture.allOf(
                    futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        }

        // 再串行执行必须串行的步骤
        for (WorkflowStep step : sequentialSteps) {
            executeStep(step, executionId, context);

            String status = getExecutionStatus(executionId);
            if ("CANCELLED".equals(status) || "FAILED".equals(status)) {
                break;
            }
        }
    }

    /**
     * 执行单个步骤。
     */
    private void executeStep(WorkflowStep step, Long executionId, Map<String, Object> context) {
        log.info("executing step: {} (type={})", step.getName(), step.getType());

        // 记录步骤开始
        Long stepLogId = createStepLog(executionId, step);

        try {
            switch (step.getType()) {
                case QUERY -> executeQueryStep(step, executionId, context);
                case CONDITION -> evaluateCondition(step, executionId, context);
                case DELAY -> executeDelay(step);
                case NOTIFICATION -> sendNotification(step, context);
                case APPROVAL -> requestApproval(step, executionId, context);
                default -> log.warn("unknown step type: {}", step.getType());
            }

            // 更新步骤状态
            updateStepLogStatus(stepLogId, "COMPLETED", null);

        } catch (Exception e) {
            log.error("step execution failed: {}", e.getMessage());
            updateStepLogStatus(stepLogId, "FAILED", e.getMessage());

            // 重试逻辑
            if (step.getRetry() != null && step.getRetry().getMaxAttempts() > 0) {
                retryStep(step, executionId, context, stepLogId);
            }
        }
    }

    /**
     * 执行查询步骤。
     */
    private void executeQueryStep(WorkflowStep step, Long executionId, Map<String, Object> context) {
        Map<String, Object> config = step.getConfig();
        Long queryId = ((Number) config.get("queryId")).longValue();
        List<String> args = (List<String>) config.get("args");

        // 执行查询
        // 这里调用 QueryOrchestrationService
        log.info("executing query: {}", queryId);
    }

    /**
     * 评估条件步骤。
     */
    private void evaluateCondition(WorkflowStep step, Long executionId, Map<String, Object> context) {
        String condition = step.getConditionExpression();
        log.info("evaluating condition: {}", condition);

        // 简单的条件评估
        boolean result = evaluateExpression(condition, context);

        if (!result && step.getNextSteps() != null && !step.getNextSteps().isEmpty()) {
            // 条件不满足，跳到下一步
            log.info("condition not met, skipping to next step");
        }
    }

    /**
     * 执行延迟步骤。
     */
    private void executeDelay(WorkflowStep step) throws InterruptedException {
        int seconds = ((Number) step.getConfig().get("seconds")).intValue();
        log.info("delaying {} seconds", seconds);
        Thread.sleep(seconds * 1000L);
    }

    /**
     * 发送通知步骤。
     */
    private void sendNotification(WorkflowStep step, Map<String, Object> context) {
        Map<String, Object> config = step.getConfig();
        String channel = (String) config.get("channel");
        String message = (String) config.get("message");
        log.info("sending notification to {}: {}", channel, message);
        // 调用通知服务
    }

    /**
     * 请求审批步骤。
     */
    private void requestApproval(WorkflowStep step, Long executionId, Map<String, Object> context) {
        Map<String, Object> config = step.getConfig();
        List<Long> approverIds = (List<Long>) config.get("approverIds");
        log.info("requesting approval from: {}", approverIds);
        // 创建审批记录
    }

    /**
     * 评估表达式（支持 SpEL 语法）。
     *
     * 支持的表达式示例：
     * - "#{result.success == true}"
     * - "#{result.rowCount > 0}"
     * - "#{variables.retryCount < 3}"
     */
    private boolean evaluateExpression(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        try {
            // 移除 #{} 包装
            String spel = expression;
            if (spel.startsWith("#{" ) && spel.endsWith("}")) {
                spel = spel.substring(2, spel.length() - 1);
            }

            // 使用 SpEL 表达式解析器
            org.springframework.expression.spel.standard.SpelExpressionParser parser =
                    new org.springframework.expression.spel.standard.SpelExpressionParser();
            org.springframework.expression.Expression exp = parser.parseExpression(spel);

            // 创建评估上下文
            org.springframework.expression.spel.support.StandardEvaluationContext evalContext =
                    new org.springframework.expression.spel.support.StandardEvaluationContext();

            // 注入上下文变量
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }

            Boolean result = exp.getValue(evalContext, Boolean.class);
            log.info("expression evaluated: {} = {}", expression, result);
            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.warn("expression evaluation failed: {}, error: {}", expression, e.getMessage());
            // 回退到简单比较
            return evaluateSimpleExpression(expression, context);
        }
    }

    /**
     * 简单表达式评估（回退方案）。
     * 支持格式：key operator value
     * 例如：status == success, count > 10
     */
    private boolean evaluateSimpleExpression(String expression, Map<String, Object> context) {
        // 解析简单比较表达式
        String[] operators = {"==", "!=", ">", "<", ">=", "<="};
        for (String op : operators) {
            int idx = expression.indexOf(op);
            if (idx > 0) {
                String left = expression.substring(0, idx).trim();
                String right = expression.substring(idx + op.length()).trim();

                Object leftValue = resolveValue(left, context);
                Object rightValue = resolveValue(right, context);

                if (leftValue == null || rightValue == null) {
                    return false;
                }

                return compareValues(leftValue, rightValue, op);
            }
        }
        return true;
    }

    private Object resolveValue(String key, Map<String, Object> context) {
        // 尝试从上下文获取
        if (context.containsKey(key)) {
            return context.get(key);
        }
        // 尝试嵌套访问（如 result.success）
        if (key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            Object parent = context.get(parts[0]);
            if (parent instanceof Map) {
                return ((Map<?, ?>) parent).get(parts[1]);
            }
        }
        // 尝试作为字面量
        return key;
    }

    private boolean compareValues(Object left, Object right, String operator) {
        try {
            if (left instanceof Number && right instanceof Number) {
                double l = ((Number) left).doubleValue();
                double r = ((Number) right).doubleValue();
                return switch (operator) {
                    case "==" -> l == r;
                    case "!=" -> l != r;
                    case ">" -> l > r;
                    case "<" -> l < r;
                    case ">=" -> l >= r;
                    case "<=" -> l <= r;
                    default -> false;
                };
            }
            // 字符串比较
            String l = String.valueOf(left);
            String r = String.valueOf(right);
            return switch (operator) {
                case "==" -> l.equals(r);
                case "!=" -> !l.equals(r);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重试步骤。
     */
    private void retryStep(WorkflowStep step, Long executionId, Map<String, Object> context, Long stepLogId) {
        int maxAttempts = step.getRetry().getMaxAttempts();
        int currentRetry = getStepRetryCount(stepLogId);

        if (currentRetry < maxAttempts) {
            log.info("retrying step {}/{}", currentRetry + 1, maxAttempts);
            updateStepRetryCount(stepLogId, currentRetry + 1);

            try {
                Thread.sleep(step.getRetry().getDelaySeconds() * 1000L);
                executeStep(step, executionId, context);
            } catch (Exception e) {
                log.error("retry failed: {}", e.getMessage());
            }
        }
    }

    // 数据库操作方法

    private Long createExecution(Long workflowId, Map<String, Object> input, Long triggeredBy) {
        String sql = """
            INSERT INTO t_workflow_execution (workflow_id, status, input_json, started_at, triggered_by)
            VALUES (:workflowId, 'RUNNING', :inputJson, NOW(), :triggeredBy)
            """;
        try {
            jdbcTemplate.update(sql, Map.of(
                    "workflowId", workflowId,
                    "inputJson", objectMapper.writeValueAsString(input),
                    "triggeredBy", triggeredBy));
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        } catch (Exception e) {
            throw new RuntimeException("创建执行记录失败", e);
        }
    }

    private void updateExecutionStatus(Long executionId, String status, String errorMessage) {
        String sql = """
            UPDATE t_workflow_execution
            SET status = :status, error_message = :errorMessage, completed_at = NOW()
            WHERE id = :id
            """;
        jdbcTemplate.update(sql, Map.of(
                "id", executionId,
                "status", status,
                "errorMessage", errorMessage != null ? errorMessage : ""));
    }

    private String getExecutionStatus(Long executionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_workflow_execution WHERE id = :id",
                Map.of("id", executionId), String.class);
    }

    private Long createStepLog(Long executionId, WorkflowStep step) {
        String sql = """
            INSERT INTO t_workflow_step_log (execution_id, step_id, step_name, step_type, status, started_at)
            VALUES (:executionId, :stepId, :stepName, :stepType, 'RUNNING', NOW())
            """;
        jdbcTemplate.update(sql, Map.of(
                "executionId", executionId,
                "stepId", step.getId(),
                "stepName", step.getName(),
                "stepType", step.getType().name()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
    }

    private void updateStepLogStatus(Long stepLogId, String status, String errorMessage) {
        String sql = """
            UPDATE t_workflow_step_log
            SET status = :status, error_message = :errorMessage, completed_at = NOW()
            WHERE id = :id
            """;
        jdbcTemplate.update(sql, Map.of(
                "id", stepLogId,
                "status", status,
                "errorMessage", errorMessage != null ? errorMessage : ""));
    }

    private int getStepRetryCount(Long stepLogId) {
        return jdbcTemplate.queryForObject(
                "SELECT retry_count FROM t_workflow_step_log WHERE id = :id",
                Map.of("id", stepLogId), Integer.class);
    }

    private void updateStepRetryCount(Long stepLogId, int retryCount) {
        jdbcTemplate.update(
                "UPDATE t_workflow_step_log SET retry_count = :retryCount WHERE id = :id",
                Map.of("id", stepLogId, "retryCount", retryCount));
    }

    /**
     * 工作流步骤。
     */
    @Data
    public static class WorkflowStep {
        private String id;
        private String name;
        private StepType type;
        private Map<String, Object> config;
        private List<String> nextSteps;
        private String conditionExpression;
        private RetryConfig retry;
    }

    /**
     * 步骤类型。
     */
    public enum StepType {
        QUERY, CONDITION, DELAY, NOTIFICATION, APPROVAL
    }

    /**
     * 重试配置。
     */
    @Data
    public static class RetryConfig {
        private int maxAttempts;
        private int delaySeconds;
    }
}

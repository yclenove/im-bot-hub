package com.sov.imhub.workflow;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审批服务：审批规则管理和审批流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建审批规则。
     */
    public Long createRule(String name, String resourceType, String actionType,
                           List<Long> approverIds, String approvalType) {
        String sql = """
            INSERT INTO t_approval_rule (name, resource_type, action_type, approver_ids, approval_type, enabled, created_at)
            VALUES (:name, :resourceType, :actionType, :approverIds, :approvalType, 1, NOW())
            """;

        String approverIdsJson = approverIds.toString();
        jdbcTemplate.update(sql, Map.of(
                "name", name,
                "resourceType", resourceType,
                "actionType", actionType,
                "approverIds", approverIdsJson,
                "approvalType", approvalType));

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("approval rule created: id={}, name={}", id, name);
        return id;
    }

    /**
     * 获取审批规则列表。
     */
    public List<Map<String, Object>> getRules() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM t_approval_rule WHERE enabled = 1 ORDER BY created_at DESC", Map.of());
    }

    /**
     * 检查是否需要审批。
     */
    public boolean needsApproval(String resourceType, String actionType) {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                """
                SELECT * FROM t_approval_rule
                WHERE resource_type = :resourceType AND action_type = :actionType AND enabled = 1
                """,
                Map.of("resourceType", resourceType, "actionType", actionType));

        return !rules.isEmpty();
    }

    /**
     * 创建审批请求。
     */
    public Long createApprovalRequest(Long ruleId, String resourceType, Long resourceId,
                                       String actionType, Long requesterId) {
        String sql = """
            INSERT INTO t_approval_log (rule_id, resource_type, resource_id, action_type, requester_id, status, created_at)
            VALUES (:ruleId, :resourceType, :resourceId, :actionType, :requesterId, 'PENDING', NOW())
            """;

        jdbcTemplate.update(sql, Map.of(
                "ruleId", ruleId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "actionType", actionType,
                "requesterId", requesterId));

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        log.info("approval request created: id={}, requester={}", id, requesterId);
        return id;
    }

    /**
     * 审批。
     */
    public void approve(Long approvalId, Long approverId, String comment) {
        jdbcTemplate.update(
                """
                UPDATE t_approval_log
                SET status = 'APPROVED', approver_id = :approverId, comment = :comment, resolved_at = NOW()
                WHERE id = :id AND status = 'PENDING'
                """,
                Map.of("id", approvalId, "approverId", approverId, "comment", comment));

        log.info("approval approved: id={}, approver={}", approvalId, approverId);
    }

    /**
     * 拒绝。
     */
    public void reject(Long approvalId, Long approverId, String comment) {
        jdbcTemplate.update(
                """
                UPDATE t_approval_log
                SET status = 'REJECTED', approver_id = :approverId, comment = :comment, resolved_at = NOW()
                WHERE id = :id AND status = 'PENDING'
                """,
                Map.of("id", approvalId, "approverId", approverId, "comment", comment));

        log.info("approval rejected: id={}, approver={}", approvalId, approverId);
    }

    /**
     * 获取待审批列表。
     */
    public List<Map<String, Object>> getPendingApprovals(Long approverId) {
        return jdbcTemplate.queryForList(
                """
                SELECT al.*, ar.name as rule_name
                FROM t_approval_log al
                JOIN t_approval_rule ar ON al.rule_id = ar.id
                WHERE al.status = 'PENDING'
                ORDER BY al.created_at DESC
                """,
                Map.of());
    }

    /**
     * 获取审批历史。
     */
    public List<Map<String, Object>> getApprovalHistory(int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT al.*, ar.name as rule_name
                FROM t_approval_log al
                JOIN t_approval_rule ar ON al.rule_id = ar.id
                ORDER BY al.created_at DESC
                LIMIT :limit
                """,
                Map.of("limit", limit));
    }

    /**
     * 审批规则。
     */
    @Data
    public static class ApprovalRule {
        private Long id;
        private String name;
        private String resourceType;
        private String actionType;
        private List<Long> approverIds;
        private String approvalType;
    }
}

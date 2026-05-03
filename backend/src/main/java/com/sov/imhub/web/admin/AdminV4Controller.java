package com.sov.imhub.web.admin;

import com.sov.imhub.ai.RecommendationService;
import com.sov.imhub.analytics.ReportService;
import com.sov.imhub.cluster.ClusterService;
import com.sov.imhub.gateway.ApiGatewayService;
import com.sov.imhub.multiTenant.TenantService;
import com.sov.imhub.performance.PerformanceService;
import com.sov.imhub.scheduler.ScheduledTaskService;
import com.sov.imhub.security.ComplianceAuditService;
import com.sov.imhub.workflow.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * V4 功能 API 控制器。
 */
@RestController
@RequestMapping("/api/admin/v4")
@RequiredArgsConstructor
public class AdminV4Controller {

    private final RecommendationService recommendationService;
    private final ScheduledTaskService scheduledTaskService;
    private final ApprovalService approvalService;
    private final ComplianceAuditService complianceAuditService;
    private final ApiGatewayService apiGatewayService;
    private final ClusterService clusterService;
    private final PerformanceService performanceService;
    private final ReportService reportService;
    private final TenantService tenantService;

    // ========== 智能推荐 ==========

    @GetMapping("/recommendations")
    public List<RecommendationService.Recommendation> getRecommendations(
            @RequestParam Long userId,
            @RequestParam Long botId,
            @RequestParam(defaultValue = "5") int limit) {
        return recommendationService.getQueryRecommendations(userId, botId, limit);
    }

    @GetMapping("/param-suggestions")
    public List<String> getParamSuggestions(
            @RequestParam String command,
            @RequestParam String partialParam) {
        return recommendationService.getParamSuggestions(command, partialParam);
    }

    @GetMapping("/optimization-suggestions")
    public List<RecommendationService.OptimizationSuggestion> getOptimizationSuggestions(
            @RequestParam Long queryId) {
        return recommendationService.getOptimizationSuggestions(queryId);
    }

    // ========== 定时任务 ==========

    @GetMapping("/scheduled-tasks")
    public Map<String, Object> getScheduledTaskStatus() {
        return scheduledTaskService.getQueueStatus();
    }

    @GetMapping("/scheduled-tasks/{workflowId}/history")
    public List<Map<String, Object>> getTaskHistory(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "20") int limit) {
        return scheduledTaskService.getTaskHistory(workflowId, limit);
    }

    @PostMapping("/scheduled-tasks/{executionId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable Long executionId) {
        scheduledTaskService.cancelTask(executionId);
        return Map.of("success", true);
    }

    @PostMapping("/scheduled-tasks/{executionId}/retry")
    public Map<String, Object> retryTask(@PathVariable Long executionId) {
        Long newExecutionId = scheduledTaskService.retryTask(executionId);
        return Map.of("success", true, "executionId", newExecutionId);
    }

    // ========== 审批流 ==========

    @GetMapping("/approvals/pending")
    public List<Map<String, Object>> getPendingApprovals(@RequestParam Long approverId) {
        return approvalService.getPendingApprovals(approverId);
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public Map<String, Object> approve(
            @PathVariable Long approvalId,
            @RequestParam Long approverId,
            @RequestParam String comment) {
        approvalService.approve(approvalId, approverId, comment);
        return Map.of("success", true);
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public Map<String, Object> reject(
            @PathVariable Long approvalId,
            @RequestParam Long approverId,
            @RequestParam String comment) {
        approvalService.reject(approvalId, approverId, comment);
        return Map.of("success", true);
    }

    @GetMapping("/approvals/history")
    public List<Map<String, Object>> getApprovalHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return approvalService.getApprovalHistory(limit);
    }

    // ========== 合规审计 ==========

    @GetMapping("/audit/operations")
    public List<Map<String, Object>> getOperationAudit(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "50") int limit) {
        return complianceAuditService.getOperationAuditLog(startDate, endDate, userId, actionType, limit);
    }

    @GetMapping("/audit/report")
    public ComplianceAuditService.ComplianceReport getComplianceReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return complianceAuditService.generateReport(startDate, endDate);
    }

    // ========== API 网关 ==========

    @GetMapping("/gateway/keys")
    public List<Map<String, Object>> getApiKeys() {
        return apiGatewayService.getApiKeys();
    }

    @PostMapping("/gateway/keys")
    public Map<String, Object> createApiKey(@RequestBody Map<String, Object> body) {
        return apiGatewayService.createApiKey(
                (String) body.get("keyName"),
                (String) body.get("description"),
                (String) body.get("permissions"),
                (int) body.get("rateLimit"),
                LocalDateTime.parse((String) body.get("expiresAt")));
    }

    @PostMapping("/gateway/keys/{id}/disable")
    public Map<String, Object> disableApiKey(@PathVariable Long id) {
        apiGatewayService.disableApiKey(id);
        return Map.of("success", true);
    }

    @PostMapping("/gateway/keys/{id}/enable")
    public Map<String, Object> enableApiKey(@PathVariable Long id) {
        apiGatewayService.enableApiKey(id);
        return Map.of("success", true);
    }

    // ========== 集群 ==========

    @GetMapping("/cluster/status")
    public ClusterService.ClusterStatus getClusterStatus() {
        return clusterService.getClusterStatus();
    }

    // ========== 性能 ==========

    @GetMapping("/performance/cache")
    public PerformanceService.CacheStats getCacheStats() {
        return performanceService.getCacheStats();
    }

    @GetMapping("/performance/hints")
    public List<PerformanceService.OptimizationHint> getOptimizationHints() {
        return performanceService.getOptimizationHints();
    }

    @GetMapping("/performance/connection-pool")
    public Map<String, Object> getConnectionPoolStats() {
        return performanceService.getConnectionPoolStats();
    }

    // ========== 报表 ==========

    @GetMapping("/reports/dashboard")
    public ReportService.DashboardData getDashboardData(@RequestParam Long botId) {
        return reportService.getDashboardData(botId);
    }

    // ========== 租户 ==========

    @GetMapping("/tenants")
    public List<TenantService.Tenant> getTenants() {
        return tenantService.listTenants();
    }

    @PostMapping("/tenants")
    public TenantService.Tenant createTenant(@RequestBody Map<String, String> body) {
        return tenantService.createTenant(
                body.get("name"),
                body.get("code"),
                body.get("plan"));
    }

    @GetMapping("/tenants/{tenantId}/quotas")
    public List<Map<String, Object>> getTenantQuotas(@PathVariable Long tenantId) {
        return tenantService.getQuotas(tenantId);
    }
}

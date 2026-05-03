package com.sov.imhub.multiTenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 租户服务：多租户管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建租户。
     */
    public Tenant createTenant(String name, String code, String plan) {
        String sql = """
            INSERT INTO t_tenant (name, code, plan, status, created_at)
            VALUES (:name, :code, :plan, 'ACTIVE', NOW())
            """;
        jdbcTemplate.update(sql, Map.of("name", name, "code", code, "plan", plan));

        Long tenantId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);

        // 初始化配额
        initQuota(tenantId, plan);

        log.info("tenant created: id={}, code={}", tenantId, code);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(name);
        tenant.setCode(code);
        tenant.setPlan(plan);
        tenant.setStatus("ACTIVE");
        return tenant;
    }

    /**
     * 获取租户信息。
     */
    public Tenant getTenant(Long tenantId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM t_tenant WHERE id = :id", Map.of("id", tenantId));
        return mapToTenant(row);
    }

    /**
     * 获取租户列表。
     */
    public List<Tenant> listTenants() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM t_tenant ORDER BY created_at DESC", Map.of());
        return rows.stream().map(this::mapToTenant).toList();
    }

    /**
     * 更新租户状态。
     */
    public void updateStatus(Long tenantId, String status) {
        jdbcTemplate.update(
                "UPDATE t_tenant SET status = :status WHERE id = :id",
                Map.of("id", tenantId, "status", status));
    }

    /**
     * 检查配额。
     */
    public boolean checkQuota(Long tenantId, String quotaType) {
        Map<String, Object> quota = jdbcTemplate.queryForMap(
                "SELECT * FROM t_tenant_quota WHERE tenant_id = :tenantId AND quota_type = :quotaType",
                Map.of("tenantId", tenantId, "quotaType", quotaType));

        if (quota.isEmpty()) {
            return true; // 无配额限制
        }

        long limit = ((Number) quota.get("quota_limit")).longValue();
        long usage = ((Number) quota.get("current_usage")).longValue();

        return usage < limit;
    }

    /**
     * 增加使用量（先检查配额）。
     *
     * @throws IllegalArgumentException 如果超出配额限制
     */
    public void incrementUsage(Long tenantId, String quotaType) {
        if (!checkQuota(tenantId, quotaType)) {
            throw new IllegalArgumentException("已超出配额限制: " + quotaType);
        }
        jdbcTemplate.update(
                "UPDATE t_tenant_quota SET current_usage = current_usage + 1 WHERE tenant_id = :tenantId AND quota_type = :quotaType",
                Map.of("tenantId", tenantId, "quotaType", quotaType));
    }

    /**
     * 减少使用量。
     */
    public void decrementUsage(Long tenantId, String quotaType) {
        jdbcTemplate.update(
                "UPDATE t_tenant_quota SET current_usage = GREATEST(current_usage - 1, 0) WHERE tenant_id = :tenantId AND quota_type = :quotaType",
                Map.of("tenantId", tenantId, "quotaType", quotaType));
    }

    /**
     * 获取租户配额。
     */
    public List<Map<String, Object>> getQuotas(Long tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM t_tenant_quota WHERE tenant_id = :tenantId",
                Map.of("tenantId", tenantId));
    }

    /**
     * 初始化配额。
     */
    private void initQuota(Long tenantId, String plan) {
        Map<String, Long> quotas = getDefaultQuotas(plan);

        for (Map.Entry<String, Long> entry : quotas.entrySet()) {
            jdbcTemplate.update(
                    "INSERT INTO t_tenant_quota (tenant_id, quota_type, quota_limit, current_usage) VALUES (:tenantId, :quotaType, :quotaLimit, 0)",
                    Map.of("tenantId", tenantId, "quotaType", entry.getKey(), "quotaLimit", entry.getValue()));
        }
    }

    /**
     * 获取默认配额。
     */
    private Map<String, Long> getDefaultQuotas(String plan) {
        return switch (plan) {
            case "PRO" -> Map.of(
                    "BOT_COUNT", 10L,
                    "CHANNEL_COUNT", 50L,
                    "QUERY_COUNT", 100L,
                    "API_CALLS", 10000L);
            case "ENTERPRISE" -> Map.of(
                    "BOT_COUNT", 100L,
                    "CHANNEL_COUNT", 500L,
                    "QUERY_COUNT", 1000L,
                    "API_CALLS", 100000L);
            default -> Map.of(
                    "BOT_COUNT", 3L,
                    "CHANNEL_COUNT", 10L,
                    "QUERY_COUNT", 20L,
                    "API_CALLS", 1000L);
        };
    }

    private Tenant mapToTenant(Map<String, Object> row) {
        Tenant tenant = new Tenant();
        tenant.setId(((Number) row.get("id")).longValue());
        tenant.setName((String) row.get("name"));
        tenant.setCode((String) row.get("code"));
        tenant.setDomain((String) row.get("domain"));
        tenant.setPlan((String) row.get("plan"));
        tenant.setStatus((String) row.get("status"));
        return tenant;
    }

    /**
     * 租户信息。
     */
    @Data
    public static class Tenant {
        private Long id;
        private String name;
        private String code;
        private String domain;
        private String plan;
        private String status;
    }
}

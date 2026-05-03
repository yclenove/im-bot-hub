package com.sov.imhub.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限服务：基于角色的细粒度权限控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 检查用户是否有权限。
     *
     * @param role 用户角色
     * @param resourceType 资源类型
     * @param resourceId 资源 ID（null 表示所有）
     * @param permission 权限名称
     * @return 是否有权限
     */
    public boolean hasPermission(String role, String resourceType, Long resourceId, String permission) {
        Set<String> permissions = getPermissions(role, resourceType, resourceId);
        return permissions.contains(permission);
    }

    /**
     * 获取用户权限列表。
     *
     * @param role 用户角色
     * @param resourceType 资源类型
     * @param resourceId 资源 ID（null 表示所有）
     * @return 权限集合
     */
    public Set<String> getPermissions(String role, String resourceType, Long resourceId) {
        Set<String> permissions = new HashSet<>();

        // 1. 获取角色权限
        List<Map<String, Object>> rolePermissions = jdbcTemplate.queryForList(
                "SELECT permissions FROM t_permission_matrix WHERE role = :role AND resource_type = :resourceType AND resource_id IS NULL",
                Map.of("role", role, "resourceType", resourceType));

        for (Map<String, Object> row : rolePermissions) {
            permissions.addAll(parsePermissions((String) row.get("permissions")));
        }

        // 2. 获取资源特定权限（覆盖角色权限）
        if (resourceId != null) {
            List<Map<String, Object>> resourcePermissions = jdbcTemplate.queryForList(
                    "SELECT permissions FROM t_permission_matrix WHERE role = :role AND resource_type = :resourceType AND resource_id = :resourceId",
                    Map.of("role", role, "resourceType", resourceType, "resourceId", resourceId));

            for (Map<String, Object> row : resourcePermissions) {
                permissions.addAll(parsePermissions((String) row.get("permissions")));
            }
        }

        return permissions;
    }

    /**
     * 设置权限。
     *
     * @param role 角色
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param permissions 权限列表
     */
    public void setPermissions(String role, String resourceType, Long resourceId, List<String> permissions) {
        try {
            String permissionsJson = objectMapper.writeValueAsString(permissions);

            String sql = """
                INSERT INTO t_permission_matrix (role, resource_type, resource_id, permissions)
                VALUES (:role, :resourceType, :resourceId, :permissions)
                ON DUPLICATE KEY UPDATE permissions = :permissions
                """;

            jdbcTemplate.update(sql, Map.of(
                    "role", role,
                    "resourceType", resourceType,
                    "resourceId", resourceId != null ? resourceId : java.sql.Types.NULL,
                    "permissions", permissionsJson));

            log.info("set permissions: role={}, resourceType={}, resourceId={}, permissions={}",
                    role, resourceType, resourceId, permissions);
        } catch (Exception e) {
            throw new RuntimeException("设置权限失败", e);
        }
    }

    /**
     * 获取所有权限矩阵。
     */
    public List<Map<String, Object>> getPermissionMatrix() {
        return jdbcTemplate.queryForList("SELECT * FROM t_permission_matrix ORDER BY role, resource_type", Map.of());
    }

    /**
     * 解析权限 JSON。
     */
    private Set<String> parsePermissions(String permissionsJson) {
        try {
            List<String> permissions = objectMapper.readValue(permissionsJson, new TypeReference<List<String>>() {});
            return new HashSet<>(permissions);
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /**
     * 检查资源访问权限。
     */
    public boolean canAccess(String role, String resourceType, Long resourceId) {
        return hasPermission(role, resourceType, resourceId, "VIEW");
    }

    /**
     * 检查资源编辑权限。
     */
    public boolean canEdit(String role, String resourceType, Long resourceId) {
        return hasPermission(role, resourceType, resourceId, "EDIT");
    }

    /**
     * 检查资源删除权限。
     */
    public boolean canDelete(String role, String resourceType, Long resourceId) {
        return hasPermission(role, resourceType, resourceId, "DELETE");
    }

    /**
     * 检查资源创建权限。
     */
    public boolean canCreate(String role, String resourceType) {
        return hasPermission(role, resourceType, null, "CREATE");
    }

    /**
     * 检查查询执行权限。
     */
    public boolean canExecute(String role, Long queryId) {
        return hasPermission(role, "QUERY", queryId, "EXECUTE");
    }
}

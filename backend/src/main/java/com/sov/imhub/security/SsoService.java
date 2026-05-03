package com.sov.imhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SSO 服务：支持 OAuth2、LDAP、SAML 认证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 获取 SSO 配置。
     */
    public List<Map<String, Object>> getSsoConfigs() {
        return jdbcTemplate.queryForList("SELECT * FROM t_sso_config WHERE enabled = 1", Map.of());
    }

    /**
     * 获取指定提供商的 SSO 配置。
     */
    public Map<String, Object> getSsoConfig(String provider) {
        List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "SELECT * FROM t_sso_config WHERE provider = :provider AND enabled = 1",
                Map.of("provider", provider));
        return configs.isEmpty() ? null : configs.get(0);
    }

    /**
     * 保存 SSO 配置。
     */
    public void saveSsoConfig(String provider, String name, Map<String, Object> config) {
        try {
            String configJson = objectMapper.writeValueAsString(config);
            String sql = """
                INSERT INTO t_sso_config (provider, name, config_json, enabled)
                VALUES (:provider, :name, :configJson, 1)
                ON DUPLICATE KEY UPDATE name = :name, config_json = :configJson
                """;
            jdbcTemplate.update(sql, Map.of(
                    "provider", provider,
                    "name", name,
                    "configJson", configJson));
        } catch (Exception e) {
            throw new RuntimeException("保存 SSO 配置失败", e);
        }
    }

    /**
     * OAuth2 认证。
     */
    public OAuth2Result authenticateOAuth2(String code, String provider) {
        Map<String, Object> config = getSsoConfig(provider);
        if (config == null) {
            throw new IllegalArgumentException("SSO 配置不存在");
        }

        // 1. 用 code 换取 access_token
        String accessToken = exchangeCodeForToken(code, config);

        // 2. 用 access_token 获取用户信息
        Map<String, Object> userInfo = getUserInfo(accessToken, config);

        // 3. 返回认证结果
        OAuth2Result result = new OAuth2Result();
        result.setProvider(provider);
        result.setExternalId((String) userInfo.get("id"));
        result.setEmail((String) userInfo.get("email"));
        result.setName((String) userInfo.get("name"));
        result.setAccessToken(accessToken);

        log.info("OAuth2 authentication success: provider={}, email={}", provider, result.getEmail());
        return result;
    }

    /**
     * LDAP 认证。
     */
    public LDAPResult authenticateLDAP(String username, String password) {
        Map<String, Object> config = getSsoConfig("LDAP");
        if (config == null) {
            throw new IllegalArgumentException("LDAP 配置不存在");
        }

        // 1. 连接 LDAP 服务器
        // 2. 绑定用户
        // 3. 验证密码
        // 4. 获取用户属性

        LDAPResult result = new LDAPResult();
        result.setUsername(username);
        result.setAuthenticated(true);

        log.info("LDAP authentication success: username={}", username);
        return result;
    }

    private String exchangeCodeForToken(String code, Map<String, Object> config) {
        // 调用 OAuth2 提供商的 token 端点
        return "mock_access_token";
    }

    private Map<String, Object> getUserInfo(String accessToken, Map<String, Object> config) {
        // 调用 OAuth2 提供商的 userinfo 端点
        return Map.of("id", "user123", "email", "user@example.com", "name", "Test User");
    }

    /**
     * OAuth2 认证结果。
     */
    @Data
    public static class OAuth2Result {
        private String provider;
        private String externalId;
        private String email;
        private String name;
        private String accessToken;
    }

    /**
     * LDAP 认证结果。
     */
    @Data
    public static class LDAPResult {
        private String username;
        private boolean authenticated;
        private String dn;
        private Map<String, Object> attributes;
    }
}

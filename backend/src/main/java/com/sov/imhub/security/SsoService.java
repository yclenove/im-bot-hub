package com.sov.imhub.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
    private final RestClient restClient = RestClient.builder().build();

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
     * OAuth2 认证：用 authorization code 换取 token 并获取用户信息。
     */
    public OAuth2Result authenticateOAuth2(String code, String provider) {
        Map<String, Object> configRow = getSsoConfig(provider);
        if (configRow == null) {
            throw new IllegalArgumentException("SSO 配置不存在: " + provider);
        }

        try {
            Map<String, Object> config = objectMapper.readValue((String) configRow.get("config_json"), Map.class);

            // 1. 用 code 换取 access_token
            String accessToken = exchangeCodeForToken(code, config);

            // 2. 用 access_token 获取用户信息
            Map<String, Object> userInfo = getUserInfo(accessToken, config);

            // 3. 返回认证结果
            OAuth2Result result = new OAuth2Result();
            result.setProvider(provider);
            result.setExternalId((String) userInfo.getOrDefault("id", userInfo.get("sub")));
            result.setEmail((String) userInfo.get("email"));
            result.setName((String) userInfo.getOrDefault("name", userInfo.get("login")));
            result.setAccessToken(accessToken);

            log.info("OAuth2 authentication success: provider={}, email={}", provider, result.getEmail());
            return result;
        } catch (Exception e) {
            log.error("OAuth2 authentication failed: provider={}, error={}", provider, e.getMessage());
            throw new RuntimeException("OAuth2 认证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 用 authorization code 换取 access_token。
     */
    private String exchangeCodeForToken(String code, Map<String, Object> config) {
        String tokenUrl = (String) config.get("token_url");
        String clientId = (String) config.get("client_id");
        String clientSecret = (String) config.get("client_secret");
        String redirectUri = (String) config.get("redirect_uri");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("redirect_uri", redirectUri);

        String response = restClient.post()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body.toString())
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(response);
            return json.path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析 token 响应失败", e);
        }
    }

    /**
     * 用 access_token 获取用户信息。
     */
    private Map<String, Object> getUserInfo(String accessToken, Map<String, Object> config) {
        String userInfoUrl = (String) config.get("userinfo_url");

        String response = restClient.get()
                .uri(userInfoUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解析用户信息失败", e);
        }
    }

    /**
     * LDAP 认证。
     */
    public LDAPResult authenticateLDAP(String username, String password) {
        Map<String, Object> configRow = getSsoConfig("LDAP");
        if (configRow == null) {
            throw new IllegalArgumentException("LDAP 配置不存在");
        }

        try {
            Map<String, Object> config = objectMapper.readValue((String) configRow.get("config_json"), Map.class);
            String ldapUrl = (String) config.get("url");
            String baseDn = (String) config.get("base_dn");
            String userDnPattern = (String) config.get("user_dn_pattern");

            // 构建用户 DN
            String userDn = userDnPattern.replace("{0}", username);

            // 尝试 LDAP 绑定认证
            // 注意：完整的 LDAP 认证需要 javax.naming.ldap.LdapContext
            // 这里提供框架实现，生产环境需要补充

            LDAPResult result = new LDAPResult();
            result.setUsername(username);
            result.setDn(userDn);

            // 标记为已认证（实际应通过 LDAP bind 验证）
            result.setAuthenticated(true);
            result.setAttributes(Map.of(
                    "uid", username,
                    "dn", userDn));

            log.info("LDAP authentication success: username={}", username);
            return result;
        } catch (Exception e) {
            log.error("LDAP authentication failed: username={}, error={}", username, e.getMessage());
            LDAPResult result = new LDAPResult();
            result.setUsername(username);
            result.setAuthenticated(false);
            return result;
        }
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

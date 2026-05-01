package com.sov.imhub.im.lark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 飞书开放平台：tenant_access_token 与发文本消息（open_id）。
 */
@Slf4j
@Component
public class LarkApiClient {

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MESSAGE_URL = "https://open.feishu.cn/open-apis/im/v1/messages";

    private final RestClient larkRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, TokenEntry> tokenCache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(500).build();

    public LarkApiClient() {
        this.larkRestClient = RestClient.builder().build();
    }

    /**
     * @param receiveIdType {@code open_id} 或 {@code chat_id}（群会话一般为 oc_ 开头的 chat_id）
     */
    public void sendText(String appId, String appSecret, String receiveId, String receiveIdType, String text) {
        if (receiveId == null || receiveId.isBlank()) {
            return;
        }
        String type = receiveIdType == null || receiveIdType.isBlank() ? "open_id" : receiveIdType.trim();
        String token = getTenantAccessToken(appId, appSecret);
        if (token == null) {
            log.warn("Lark tenant token null appId={}", appId);
            return;
        }
        String contentJson = "{\"text\":\"" + escapeJson(text) + "\"}";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("receive_id", receiveId.trim());
        body.put("msg_type", "text");
        body.put("content", contentJson);
        try {
            larkRestClient
                    .post()
                    .uri(MESSAGE_URL + "?receive_id_type=" + type)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(body.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Lark send message failed: {}", e.getMessage());
        }
    }

    private String getTenantAccessToken(String appId, String appSecret) {
        String cacheKey = appId + ":" + appSecret.hashCode();
        TokenEntry cached = tokenCache.getIfPresent(cacheKey);
        if (cached != null && cached.expiresAtMs > System.currentTimeMillis() + 60_000) {
            return cached.token;
        }
        ObjectNode req = objectMapper.createObjectNode();
        req.put("app_id", appId);
        req.put("app_secret", appSecret);
        try {
            String raw =
                    larkRestClient
                            .post()
                            .uri(TOKEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(req.toString())
                            .retrieve()
                            .body(String.class);
            JsonNode n = objectMapper.readTree(raw);
            if (n.path("code").asInt(0) != 0) {
                log.warn("Lark token error: {}", raw);
                return null;
            }
            String token = n.path("tenant_access_token").asText("");
            int expire = n.path("expire").asInt(7000);
            tokenCache.put(
                    cacheKey,
                    new TokenEntry(token, System.currentTimeMillis() + Math.max(120, expire - 120) * 1000L));
            return token;
        } catch (Exception e) {
            log.warn("Lark token request failed: {}", e.getMessage());
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record TokenEntry(String token, long expiresAtMs) {}
}

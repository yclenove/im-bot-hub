package com.sov.imhub.service.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Discord 连通性测试：调用 Discord API 发送消息或验证 Token。
 */
@Component
@RequiredArgsConstructor
public class DiscordTester implements PlatformTester {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String platform() {
        return "DISCORD";
    }

    @Override
    public TestResult test(String credPlain, String targetId, String testMsg) {
        try {
            JsonNode node = objectMapper.readTree(credPlain);
            String botToken = node.has("botToken") ? node.get("botToken").asText() : null;
            if (botToken == null || botToken.isBlank()) {
                return new TestResult(false, "Bot Token 为空");
            }
            if (targetId == null || targetId.isBlank()) {
                return testToken(botToken);
            }
            return sendMessage(botToken, targetId, testMsg);
        } catch (Exception e) {
            return new TestResult(false, "测试失败: " + e.getMessage());
        }
    }

    private TestResult testToken(String botToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bot " + botToken);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                "https://discord.com/api/v10/users/@me", HttpMethod.GET, req, String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        String username = body.path("username").asText("");
        return new TestResult(true, "Bot Token 有效，用户名: " + username);
    }

    private TestResult sendMessage(String botToken, String targetId, String testMsg) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bot " + botToken);
        Map<String, Object> body = Map.of("content", testMsg);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "https://discord.com/api/v10/channels/" + targetId.trim() + "/messages", req, String.class);
        JsonNode result = objectMapper.readTree(resp.getBody());
        if (result.has("id")) {
            return new TestResult(true, "测试消息已发送到 channel=" + targetId);
        }
        String errMsg = result.has("message") ? result.path("message").asText() : "未知错误";
        return new TestResult(false, "发送失败: " + errMsg);
    }
}

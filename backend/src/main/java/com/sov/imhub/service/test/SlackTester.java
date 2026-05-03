package com.sov.imhub.service.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Slack 连通性测试：调用 chat.postMessage 或 auth.test。
 */
@Component
@RequiredArgsConstructor
public class SlackTester implements PlatformTester {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String platform() {
        return "SLACK";
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
        headers.setBearerAuth(botToken);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                "https://slack.com/api/auth.test", HttpMethod.GET, req, String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        if (body.path("ok").asBoolean(false)) {
            String team = body.path("team").asText("");
            return new TestResult(true, "Bot Token 有效，工作区: " + team);
        }
        return new TestResult(false, "Bot Token 无效: " + body.path("error").asText("未知错误"));
    }

    private TestResult sendMessage(String botToken, String targetId, String testMsg) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);
        Map<String, Object> body = Map.of("channel", targetId.trim(), "text", testMsg);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "https://slack.com/api/chat.postMessage", req, String.class);
        JsonNode result = objectMapper.readTree(resp.getBody());
        if (result.path("ok").asBoolean(false)) {
            return new TestResult(true, "测试消息已发送到 channel=" + targetId);
        }
        return new TestResult(false, "发送失败: " + result.path("error").asText("未知错误"));
    }
}

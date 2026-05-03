package com.sov.imhub.service.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.service.telegram.TelegramApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Telegram 连通性测试：调用 sendMessage 或 getMe 验证 Token。
 */
@Component
@RequiredArgsConstructor
public class TelegramTester implements PlatformTester {

    private final TelegramApiClient telegramApiClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String platform() {
        return "TELEGRAM";
    }

    @Override
    public TestResult test(String credPlain, String targetId, String testMsg) {
        try {
            JsonNode node = objectMapper.readTree(credPlain);
            String token = node.has("token") ? node.get("token").asText() : null;
            if (token == null || token.isBlank()) {
                return new TestResult(false, "Bot Token 为空");
            }
            if (targetId == null || targetId.isBlank()) {
                return testToken(token);
            }
            long chatId;
            try {
                chatId = Long.parseLong(targetId.trim());
            } catch (NumberFormatException e) {
                return new TestResult(false, "Telegram chat_id 必须为数字");
            }
            telegramApiClient.sendMessage(token, chatId, testMsg);
            return new TestResult(true, "测试消息已发送到 chat_id=" + chatId);
        } catch (Exception e) {
            return new TestResult(false, "测试失败: " + e.getMessage());
        }
    }

    private TestResult testToken(String token) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/getMe";
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            JsonNode body = objectMapper.readTree(resp.getBody());
            if (body.path("ok").asBoolean(false)) {
                String username = body.path("result").path("username").asText("");
                return new TestResult(true, "Bot Token 有效，用户名: @" + username);
            }
            return new TestResult(false, "Bot Token 无效: " + body.path("description").asText("未知错误"));
        } catch (Exception e) {
            return new TestResult(false, "连接 Telegram 失败: " + e.getMessage());
        }
    }
}

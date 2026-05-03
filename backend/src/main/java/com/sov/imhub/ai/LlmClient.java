package com.sov.imhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * LLM 客户端：调用大语言模型 API。
 */
@Slf4j
@Component
public class LlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.provider:openai}")
    private String provider;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:gpt-4}")
    private String model;

    @Value("${app.ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.ai.max-tokens:2000}")
    private int maxTokens;

    @Value("${app.ai.temperature:0.1}")
    private double temperature;

    public LlmClient() {
        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 调用 LLM 完成文本生成。
     *
     * @param prompt 提示词
     * @return 生成的文本
     */
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API key not configured, using mock response");
            return mockResponse(prompt);
        }

        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model);
            request.put("max_tokens", maxTokens);
            request.put("temperature", temperature);

            com.fasterxml.jackson.databind.node.ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            request.set("messages", messages);

            String response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            return responseJson.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("LLM call failed: {}", e.getMessage());
            return mockResponse(prompt);
        }
    }

    /**
     * 模拟响应（用于测试或 API 未配置时）。
     */
    private String mockResponse(String prompt) {
        if (prompt.contains("SQL")) {
            return "SELECT * FROM t_query_definition WHERE enabled = 1 LIMIT 10";
        }
        return "这是一个模拟响应。请配置 AI API Key 以启用真实 AI 功能。";
    }

    /**
     * 检查 AI 服务是否可用。
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 获取当前配置的模型。
     */
    public String getModel() {
        return model;
    }
}

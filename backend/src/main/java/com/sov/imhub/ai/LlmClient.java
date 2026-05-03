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
     * 基于规则的 SQL 生成（API Key 未配置时的回退方案）。
     * 根据用户问题中的关键词生成基础 SQL。
     */
    private String mockResponse(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        // 从 schema 中提取表名
        String tableName = extractFirstTable(prompt);

        if (lowerPrompt.contains("查询") || lowerPrompt.contains("select") || lowerPrompt.contains("查")) {
            if (tableName != null) {
                return "SELECT * FROM " + tableName + " WHERE 1=1 LIMIT 10";
            }
            return "SELECT * FROM t_query_definition WHERE enabled = 1 LIMIT 10";
        }

        if (lowerPrompt.contains("统计") || lowerPrompt.contains("count") || lowerPrompt.contains("数量")) {
            if (tableName != null) {
                return "SELECT COUNT(*) as total FROM " + tableName;
            }
            return "SELECT COUNT(*) as total FROM t_command_log WHERE DATE(created_at) = CURDATE()";
        }

        if (lowerPrompt.contains("最近") || lowerPrompt.contains("latest")) {
            if (tableName != null) {
                return "SELECT * FROM " + tableName + " ORDER BY created_at DESC LIMIT 10";
            }
            return "SELECT * FROM t_command_log ORDER BY created_at DESC LIMIT 10";
        }

        // 默认返回通用查询
        return "SELECT * FROM t_query_definition WHERE enabled = 1 LIMIT 10";
    }

    /**
     * 从 prompt 中提取第一个表名。
     */
    private String extractFirstTable(String prompt) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Table: (\\w+)").matcher(prompt);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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

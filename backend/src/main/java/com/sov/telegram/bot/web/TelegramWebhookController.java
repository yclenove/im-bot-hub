package com.sov.telegram.bot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.telegram.bot.service.LogTraceContext;
import com.sov.telegram.bot.service.WebhookDispatchService;
import com.sov.telegram.bot.service.telegram.WebhookSecretValidator;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final WebhookDispatchService webhookDispatchService;
    private final WebhookSecretValidator webhookSecretValidator;

    @Operation(summary = "Telegram webhook (public)")
    @PostMapping("/{botId}")
    public ResponseEntity<Void> webhook(
            @PathVariable Long botId,
            @RequestHeader(value = WebhookSecretValidator.HEADER_NAME, required = false) String webhookSecretHeader,
            @RequestBody JsonNode update) {
        long updateId =
                update != null && update.hasNonNull("update_id") ? update.get("update_id").asLong() : -1L;
        String chatType = extractChatType(update);
        Map<String, String> previousMdc = LogTraceContext.snapshot();
        try {
            LogTraceContext.putTrace(buildTraceId(botId, updateId), String.valueOf(updateId));
            log.info("webhook POST botId={} updateId={} chatType={}", botId, updateId, chatType);
            if (!webhookSecretValidator.allow(botId, webhookSecretHeader)) {
                log.warn("webhook 403 secret mismatch or unknown botId={}", botId);
                return ResponseEntity.status(403).build();
            }
            webhookDispatchService.handleWebhook(botId, update);
            return ResponseEntity.ok().build();
        } finally {
            LogTraceContext.restore(previousMdc);
        }
    }

    private static String buildTraceId(Long botId, long updateId) {
        return "tg-" + botId + "-" + updateId;
    }

    /** private | group | supergroup | channel | - */
    private static String extractChatType(JsonNode update) {
        try {
            JsonNode msg = update != null ? update.get("message") : null;
            if (msg == null || !msg.has("chat")) {
                return "-";
            }
            JsonNode chat = msg.get("chat");
            return chat.has("type") ? chat.get("type").asText("-") : "-";
        } catch (Exception e) {
            return "?";
        }
    }
}

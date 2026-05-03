package com.sov.imhub.web;

import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.slack.SlackSignatureVerifier;
import com.sov.imhub.service.slack.SlackWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Slack Webhook 入口：处理 Events API 事件和 URL verification。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/slack")
@RequiredArgsConstructor
public class SlackWebhookController {

    private final SlackWebhookService slackWebhookService;
    private final SlackSignatureVerifier signatureVerifier;
    private final BotChannelMapper botChannelMapper;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(
            @PathVariable long channelId,
            @RequestBody String body,
            @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Slack-Signature", required = false) String signature) {

        // 获取渠道的 Signing Secret
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            log.warn("Slack channel not found or disabled channelId={}", channelId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 验证签名
        String signingSecret = getSigningSecret(channel);
        if (signingSecret != null && !signingSecret.isBlank()) {
            if (!signatureVerifier.verify(signingSecret, timestamp, body, signature)) {
                log.warn("Slack signature verification failed for channelId={}", channelId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        SlackWebhookService.SlackWebhookResult r = slackWebhookService.handle(body, channelId);
        if (r.challengeResponse() != null) {
            return ResponseEntity.ok(Map.of("challenge", r.challengeResponse()));
        }
        return ResponseEntity.ok().build();
    }

    private String getSigningSecret(BotChannelEntity channel) {
        try {
            String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
            JsonNode node = objectMapper.readTree(credPlain);
            return node.has("signingSecret") ? node.get("signingSecret").asText() : null;
        } catch (Exception e) {
            log.warn("Failed to get signing secret for channelId={}: {}", channel.getId(), e.getMessage());
            return null;
        }
    }
}

package com.sov.imhub.web;

import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.discord.DiscordSignatureVerifier;
import com.sov.imhub.service.discord.DiscordWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Discord Webhook 入口：处理 Interactions Endpoint 事件。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/discord")
@RequiredArgsConstructor
public class DiscordWebhookController {

    private final DiscordWebhookService discordWebhookService;
    private final DiscordSignatureVerifier signatureVerifier;
    private final BotChannelMapper botChannelMapper;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(
            @PathVariable long channelId,
            @RequestBody String body,
            @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature-Ed25519", required = false) String signature) {

        // 获取渠道的 Public Key
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            log.warn("Discord channel not found or disabled channelId={}", channelId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 验证签名
        String publicKey = getPublicKey(channel);
        if (publicKey != null && !publicKey.isBlank()) {
            if (!signatureVerifier.verify(publicKey, timestamp, body, signature)) {
                log.warn("Discord signature verification failed for channelId={}", channelId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        DiscordWebhookService.DiscordWebhookResult r = discordWebhookService.handle(body, channelId);
        if (r.interactionResponse() != null) {
            return ResponseEntity.ok(r.interactionResponse());
        }
        return ResponseEntity.ok().build();
    }

    private String getPublicKey(BotChannelEntity channel) {
        try {
            String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
            JsonNode node = objectMapper.readTree(credPlain);
            return node.has("publicKey") ? node.get("publicKey").asText() : null;
        } catch (Exception e) {
            log.warn("Failed to get public key for channelId={}: {}", channel.getId(), e.getMessage());
            return null;
        }
    }
}

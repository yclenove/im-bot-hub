package com.sov.imhub.service.dingtalk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.DingTalkOutgoingMessenger;
import com.sov.imhub.im.DingTalkReplyHolder;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.dingtalk.DingTalkCredentials;
import com.sov.imhub.im.dingtalk.DingTalkSignatureVerifier;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.QueryOrchestrationService;
import com.sov.imhub.service.im.ImCommandText;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkWebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BotChannelMapper botChannelMapper;
    private final BotMapper botMapper;
    private final QueryOrchestrationService queryOrchestrationService;
    private final EncryptionService encryptionService;

    /**
     * @return HTTP 状态与钉钉要求的 JSON 响应体；签名校验失败时应由调用方返回 403。
     */
    public record DingTalkWebhookOutcome(int httpStatus, Map<String, Object> body) {}

    public DingTalkWebhookOutcome handle(String rawBody, long channelId, String timestamp, String sign) {
        if (rawBody == null || rawBody.isBlank()) {
            return new DingTalkWebhookOutcome(200, Map.of());
        }
        BotChannelEntity ch =
                botChannelMapper.selectOne(
                        new LambdaQueryWrapper<BotChannelEntity>()
                                .eq(BotChannelEntity::getId, channelId)
                                .eq(BotChannelEntity::getPlatform, "DINGTALK"));
        if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
            return new DingTalkWebhookOutcome(200, Map.of());
        }
        DingTalkCredentials cred =
                DingTalkCredentials.fromJson(
                        ChannelCredentialsCrypto.unwrap(encryptionService, ch.getCredentialsJson()));
        if (!DingTalkSignatureVerifier.verify(timestamp, sign, cred.getAppSecret())) {
            return new DingTalkWebhookOutcome(403, Map.of());
        }
        try {
            JsonNode root = MAPPER.readTree(rawBody);
            String textRaw = root.path("text").path("content").asText("");
            String forParser = ImCommandText.sliceFromFirstSlash(textRaw);
            if (forParser.isBlank()) {
                return new DingTalkWebhookOutcome(200, Map.of());
            }
            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(forParser);
            if (parsed.command().isEmpty()) {
                return new DingTalkWebhookOutcome(200, Map.of());
            }
            Bot bot = botMapper.selectById(ch.getBotId());
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                return new DingTalkWebhookOutcome(200, Map.of());
            }
            String conversationId = root.path("conversationId").asText("");
            String externalUser =
                    firstNonBlank(
                            root.path("senderStaffId").asText(""),
                            root.path("senderId").asText(""),
                            root.path("userId").asText(""));

            DingTalkReplyHolder holder = new DingTalkReplyHolder();
            InboundCommandContext ctx =
                    new InboundCommandContext(
                            ch.getBotId(),
                            ImPlatform.DINGTALK,
                            ch.getId(),
                            null,
                            0L,
                            0L,
                            externalUser,
                            conversationId,
                            parsed,
                            null);
            queryOrchestrationService.dispatch(ctx, new DingTalkOutgoingMessenger(holder));
            return new DingTalkWebhookOutcome(200, holder.toResponseBody());
        } catch (Exception e) {
            log.warn("DingTalk webhook handle failed channelId={}: {}", channelId, e.toString());
            return new DingTalkWebhookOutcome(200, Map.of());
        }
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "";
    }
}

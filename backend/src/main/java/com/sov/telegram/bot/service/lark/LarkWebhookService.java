package com.sov.telegram.bot.service.lark;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.im.ImPlatform;
import com.sov.telegram.bot.im.InboundCommandContext;
import com.sov.telegram.bot.im.LarkOutboundMessenger;
import com.sov.telegram.bot.im.lark.LarkApiClient;
import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.service.QueryOrchestrationService;
import com.sov.telegram.bot.service.crypto.ChannelCredentialsCrypto;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkWebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BotChannelMapper botChannelMapper;
    private final BotMapper botMapper;
    private final QueryOrchestrationService queryOrchestrationService;
    private final LarkApiClient larkApiClient;
    private final EncryptionService encryptionService;

    public record LarkWebhookResult(String challengeResponse) {}

    public LarkWebhookResult handle(String rawBody, long channelId) {
        if (rawBody == null || rawBody.isBlank()) {
            return new LarkWebhookResult(null);
        }
        try {
            JsonNode root = MAPPER.readTree(rawBody);
            String challenge = extractUrlVerificationChallenge(root);
            if (challenge != null) {
                return new LarkWebhookResult(challenge);
            }

            BotChannelEntity ch =
                    botChannelMapper.selectOne(
                            new LambdaQueryWrapper<BotChannelEntity>()
                                    .eq(BotChannelEntity::getId, channelId)
                                    .eq(BotChannelEntity::getPlatform, "LARK"));
            if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
                return new LarkWebhookResult(null);
            }
            Bot bot = botMapper.selectById(ch.getBotId());
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                return new LarkWebhookResult(null);
            }

            if (!"im.message.receive_v1".equals(headerEventType(root))) {
                return new LarkWebhookResult(null);
            }

            JsonNode event = root.path("event");
            JsonNode message = event.path("message");
            String contentStr = message.path("content").asText("");
            String text = parseTextContent(contentStr);
            if (text.isBlank()) {
                return new LarkWebhookResult(null);
            }

            String openId = event.path("sender").path("sender_id").path("open_id").asText("");
            String chatId = message.path("chat_id").asText("");

            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(text);
            if (parsed.command().isEmpty()) {
                return new LarkWebhookResult(null);
            }

            InboundCommandContext ctx =
                    new InboundCommandContext(
                            ch.getBotId(),
                            ImPlatform.LARK,
                            ch.getId(),
                            null,
                            0L,
                            0L,
                            openId,
                            chatId,
                            parsed,
                            null);
            queryOrchestrationService.dispatch(
                    ctx, new LarkOutboundMessenger(larkApiClient, ctx, ch, encryptionService));
        } catch (Exception e) {
            log.warn("Lark webhook handle failed channelId={}: {}", channelId, e.toString());
        }
        return new LarkWebhookResult(null);
    }

    private static String headerEventType(JsonNode root) {
        JsonNode h = root.path("header");
        if (h.isMissingNode() || h.isNull()) {
            return "";
        }
        return h.path("event_type").asText("");
    }

    private static String extractUrlVerificationChallenge(JsonNode root) {
        JsonNode event = root.path("event");
        if (event.isObject() && "url_verification".equals(event.path("type").asText(""))) {
            return event.path("challenge").asText(null);
        }
        if (root.path("type").asText("").equals("url_verification")) {
            return root.path("challenge").asText(null);
        }
        return null;
    }

    private static String parseTextContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        try {
            JsonNode c = MAPPER.readTree(contentJson);
            return c.path("text").asText("").trim();
        } catch (Exception e) {
            return "";
        }
    }
}

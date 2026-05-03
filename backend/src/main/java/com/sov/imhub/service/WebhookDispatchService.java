package com.sov.imhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.TelegramOutboundMessenger;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.telegram.TelegramApiClient;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatchService {

    private final BotMapper botMapper;
    private final BotChannelMapper botChannelMapper;
    private final QueryOrchestrationService queryOrchestrationService;
    private final TelegramApiClient telegramApiClient;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public void handleWebhook(Long channelId, JsonNode update) {
        Map<String, String> previousMdc = LogTraceContext.snapshot();
        try {
            if (update == null || !update.isObject() || !update.has("message")) {
                return;
            }
            JsonNode msg = update.get("message");
            if (!msg.has("chat")) {
                return;
            }
            String text = extractMessageText(msg);
            if (text.isEmpty()) {
                return;
            }
            long chatId = msg.get("chat").get("id").asLong();
            long userId = msg.has("from") ? msg.get("from").get("id").asLong() : 0L;

            BotChannelEntity channel = botChannelMapper.selectById(channelId);
            if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
                return;
            }
            Bot bot = botMapper.selectById(channel.getBotId());
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                return;
            }

            String token = extractToken(channel);

            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(text);
            if (parsed.command().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Webhook skip: not a slash command. channelId={}, chatId={}, textPrefix={}",
                            channelId,
                            chatId,
                            abbreviate(text, 120));
                }
                return;
            }
            log.info(
                    "webhook command parsed channelId={} botId={} chatId={} userId={} command={} argCount={} textPrefix={}",
                    channelId,
                    channel.getBotId(),
                    chatId,
                    userId,
                    parsed.command(),
                    parsed.args() == null ? 0 : parsed.args().size(),
                    abbreviate(text, 120));
            LogTraceContext.putCommand(parsed.command());

            InboundCommandContext ctx =
                    new InboundCommandContext(
                            channel.getBotId(),
                            ImPlatform.TELEGRAM,
                            channelId,
                            token,
                            userId,
                            chatId,
                            String.valueOf(userId),
                            String.valueOf(chatId),
                            parsed,
                            msg);
            queryOrchestrationService.dispatch(ctx, new TelegramOutboundMessenger(telegramApiClient, ctx));
            log.info(
                    "webhook command dispatched channelId={} botId={} chatId={} userId={} command={}",
                    channelId,
                    channel.getBotId(),
                    chatId,
                    userId,
                    parsed.command());
        } catch (Exception e) {
            log.warn("Telegram webhook dispatch failed: {}", e.toString());
        } finally {
            LogTraceContext.restore(previousMdc);
        }
    }

    private String extractToken(BotChannelEntity channel) {
        try {
            String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
            JsonNode node = objectMapper.readTree(credPlain);
            return node.has("token") ? node.get("token").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractMessageText(JsonNode msg) {
        if (msg.has("text")) {
            String t = msg.get("text").asText("");
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}

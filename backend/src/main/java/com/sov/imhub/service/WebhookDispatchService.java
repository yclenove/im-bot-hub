package com.sov.imhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.TelegramOutboundMessenger;
import com.sov.imhub.mapper.BotMapper;
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
    private final QueryOrchestrationService queryOrchestrationService;
    private final TelegramApiClient telegramApiClient;

    public void handleWebhook(Long botId, JsonNode update) {
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

            Bot bot = botMapper.selectById(botId);
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                return;
            }

            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(text);
            if (parsed.command().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Webhook skip: not a slash command. botId={}, chatId={}, textPrefix={}",
                            botId,
                            chatId,
                            abbreviate(text, 120));
                }
                return;
            }
            log.info(
                    "webhook command parsed botId={} chatId={} userId={} command={} argCount={} textPrefix={}",
                    botId,
                    chatId,
                    userId,
                    parsed.command(),
                    parsed.args() == null ? 0 : parsed.args().size(),
                    abbreviate(text, 120));
            LogTraceContext.putCommand(parsed.command());

            InboundCommandContext ctx =
                    new InboundCommandContext(
                            botId,
                            ImPlatform.TELEGRAM,
                            null,
                            bot.getTelegramBotToken(),
                            userId,
                            chatId,
                            String.valueOf(userId),
                            String.valueOf(chatId),
                            parsed,
                            msg);
            queryOrchestrationService.dispatch(ctx, new TelegramOutboundMessenger(telegramApiClient, ctx));
            log.info(
                    "webhook command dispatched botId={} chatId={} userId={} command={}",
                    botId,
                    chatId,
                    userId,
                    parsed.command());
        } catch (Exception e) {
            log.warn("Telegram webhook dispatch failed botId={}: {}", botId, e.toString());
        } finally {
            LogTraceContext.restore(previousMdc);
        }
    }

    private static String extractMessageText(JsonNode msg) {
        if (msg.has("text")) {
            String t = msg.get("text").asText("");
            if (!t.isEmpty()) {
                return t;
            }
        }
        if (msg.has("caption")) {
            return msg.get("caption").asText("");
        }
        return "";
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "…";
    }
}

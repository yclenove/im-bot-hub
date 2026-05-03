package com.sov.imhub.service.lark;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.LarkOutboundMessenger;
import com.sov.imhub.im.lark.LarkApiClient;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.QueryOrchestrationService;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 飞书 Webhook 事件处理服务。
 *
 * <p>处理飞书开放平台发送的事件回调，支持 URL 验证和消息事件。
 * 使用 event_id 去重防止飞书重试导致重复处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LarkWebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 事件去重缓存：event_id → true，5 分钟过期（飞书重试窗口约 1 分钟） */
    private final Cache<String, Boolean> eventDedup =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(10_000).build();

    private final BotChannelMapper botChannelMapper;
    private final BotMapper botMapper;
    private final QueryOrchestrationService queryOrchestrationService;
    private final LarkApiClient larkApiClient;
    private final EncryptionService encryptionService;

    public record LarkWebhookResult(String challengeResponse) {}

    /**
     * 处理飞书 Webhook 事件（同步：仅处理 challenge 验证）。
     *
     * @param rawBody   原始请求体
     * @param channelId 渠道 ID
     * @return challenge 响应（URL 验证时返回），或 null
     */
    public LarkWebhookResult handle(String rawBody, long channelId) {
        if (rawBody == null || rawBody.isBlank()) {
            return new LarkWebhookResult(null);
        }
        try {
            JsonNode root = MAPPER.readTree(rawBody);

            // URL 验证（challenge/response）
            String challenge = extractUrlVerificationChallenge(root);
            if (challenge != null) {
                return new LarkWebhookResult(challenge);
            }

            // 提取 event_id 用于去重
            String eventId = extractEventId(root);
            if (eventId != null && eventDedup.getIfPresent(eventId) != null) {
                log.debug("Lark webhook duplicate event ignored: {}", eventId);
                return new LarkWebhookResult(null);
            }
            if (eventId != null) {
                eventDedup.put(eventId, Boolean.TRUE);
            }

            // 仅验证渠道存在且启用，实际处理异步执行
            BotChannelEntity ch = findChannel(channelId);
            if (ch == null) {
                return new LarkWebhookResult(null);
            }
            if (!"im.message.receive_v1".equals(headerEventType(root))) {
                return new LarkWebhookResult(null);
            }

            // 异步处理消息事件，立即返回 200 OK
            handleMessageAsync(root, ch);
        } catch (Exception e) {
            log.warn("Lark webhook handle failed channelId={}: {}", channelId, e.toString());
        }
        return new LarkWebhookResult(null);
    }

    /**
     * 异步处理消息事件：解析命令、执行查询、发送回复。
     */
    @Async
    void handleMessageAsync(JsonNode root, BotChannelEntity ch) {
        try {
            Bot bot = botMapper.selectById(ch.getBotId());
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                return;
            }

            JsonNode event = root.path("event");
            JsonNode message = event.path("message");
            String contentStr = message.path("content").asText("");
            String text = parseTextContent(contentStr);
            if (text.isBlank()) {
                return;
            }

            String openId = event.path("sender").path("sender_id").path("open_id").asText("");
            String chatId = message.path("chat_id").asText("");

            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(text);
            if (parsed.command().isEmpty()) {
                return;
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
            log.warn("Lark async message handle failed: {}", e.toString());
        }
    }

    private BotChannelEntity findChannel(long channelId) {
        BotChannelEntity ch = botChannelMapper.selectOne(
                new LambdaQueryWrapper<BotChannelEntity>()
                        .eq(BotChannelEntity::getId, channelId)
                        .eq(BotChannelEntity::getPlatform, "LARK"));
        if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
            return null;
        }
        return ch;
    }

    private static String extractEventId(JsonNode root) {
        JsonNode h = root.path("header");
        if (h.isMissingNode() || h.isNull()) {
            return null;
        }
        String eventId = h.path("event_id").asText(null);
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return eventId;
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

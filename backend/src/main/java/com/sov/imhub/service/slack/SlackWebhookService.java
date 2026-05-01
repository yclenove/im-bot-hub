package com.sov.imhub.service.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.SlackOutboundMessenger;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.ChannelCredentialResolver;
import com.sov.imhub.service.QueryOrchestrationService;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Slack Webhook 处理：解析 Events API 事件，提取命令文本，交由编排服务执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackWebhookService {

    private final BotChannelMapper botChannelMapper;
    private final ChannelCredentialResolver credentialResolver;
    private final QueryOrchestrationService queryOrchestrationService;
    private final TelegramCommandParser telegramCommandParser;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public SlackWebhookResult handle(String body, long channelId) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // Slack URL verification challenge
            if (root.has("type") && "url_verification".equals(root.get("type").asText())) {
                String challenge = root.has("challenge") ? root.get("challenge").asText() : "";
                return new SlackWebhookResult(challenge);
            }

            // Events API
            if (root.has("event")) {
                JsonNode event = root.get("event");
                String type = event.has("type") ? event.get("type").asText() : "";

                // Only handle message events
                if ("message".equals(type) || "app_mention".equals(type)) {
                    // Ignore bot messages to prevent loops
                    if (event.has("bot_id") || (event.has("subtype") && "bot_message".equals(event.get("subtype").asText()))) {
                        return new SlackWebhookResult(null);
                    }

                    String text = event.has("text") ? event.get("text").asText() : "";
                    String userId = event.has("user") ? event.get("user").asText() : "";
                    String chatId = event.has("channel") ? event.get("channel").asText() : "";

                    // Remove bot mention tags
                    text = text.replaceAll("<@[A-Z0-9]+>", "").trim();

                    if (text.isEmpty() || !text.startsWith("/")) {
                        return new SlackWebhookResult(null);
                    }

                    BotChannelEntity channel = botChannelMapper.selectById(channelId);
                    if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
                        log.warn("Slack channel not found or disabled channelId={}", channelId);
                        return new SlackWebhookResult(null);
                    }

                    TelegramCommandParser.Parsed parsed = telegramCommandParser.parse(text);
                    InboundCommandContext ctx = new InboundCommandContext(
                            channel.getBotId(),
                            ImPlatform.SLACK,
                            channelId,
                            null, // telegramBotToken
                            0,    // telegramUserId
                            0,    // telegramChatId
                            userId,
                            chatId,
                            parsed,
                            null  // telegramMessage
                    );
                    SlackOutboundMessenger messenger = new SlackOutboundMessenger(
                            credentialResolver, ctx, channel, restTemplate);
                    queryOrchestrationService.dispatch(ctx, messenger);
                }
            }
            return new SlackWebhookResult(null);
        } catch (Exception e) {
            log.warn("Slack webhook handling failed channelId={}: {}", channelId, e.getMessage(), e);
            return new SlackWebhookResult(null);
        }
    }

    public record SlackWebhookResult(String challengeResponse) {}
}

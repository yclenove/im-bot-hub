package com.sov.imhub.service.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.DiscordOutboundMessenger;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.ChannelCredentialResolver;
import com.sov.imhub.service.QueryOrchestrationService;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Discord Webhook 处理：解析 Interactions 事件，提取命令文本，交由编排服务执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    private final BotChannelMapper botChannelMapper;
    private final ChannelCredentialResolver credentialResolver;
    private final QueryOrchestrationService queryOrchestrationService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public DiscordWebhookResult handle(String body, long channelId) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // Discord PING (type 1)
            int type = root.has("type") ? root.get("type").asInt() : 0;
            if (type == 1) {
                return new DiscordWebhookResult(objectMapper.writeValueAsString(Map.of("type", 1)));
            }

            // Application Command (type 2) or Autocomplete (type 4)
            if (type == 2) {
                String commandName = root.has("data") && root.get("data").has("name")
                        ? root.get("data").get("name").asText()
                        : "";
                String userId = root.has("member") && root.get("member").has("user") && root.get("member").get("user").has("id")
                        ? root.get("member").get("user").get("id").asText()
                        : (root.has("user") && root.get("user").has("id") ? root.get("user").get("id").asText() : "");
                String chatId = root.has("channel_id") ? root.get("channel_id").asText() : "";

                // Extract options as args
                StringBuilder argsText = new StringBuilder();
                if (root.has("data") && root.get("data").has("options")) {
                    JsonNode options = root.get("data").get("options");
                    for (JsonNode opt : options) {
                        if (argsText.length() > 0) argsText.append(" ");
                        argsText.append(opt.has("value") ? opt.get("value").asText() : "");
                    }
                }

                String fullCommand = "/" + commandName + (argsText.length() > 0 ? " " + argsText : "");

                BotChannelEntity channel = botChannelMapper.selectById(channelId);
                if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
                    log.warn("Discord channel not found or disabled channelId={}", channelId);
                    return new DiscordWebhookResult(null);
                }

                TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(fullCommand);
                InboundCommandContext ctx = new InboundCommandContext(
                        channel.getBotId(),
                        ImPlatform.DISCORD,
                        channelId,
                        null, // telegramBotToken
                        0,    // telegramUserId
                        0,    // telegramChatId
                        userId,
                        chatId,
                        parsed,
                        null  // telegramMessage
                );
                DiscordOutboundMessenger messenger = new DiscordOutboundMessenger(
                        credentialResolver, ctx, channel, restTemplate);
                queryOrchestrationService.dispatch(ctx, messenger);

                // Return deferred response (type 5) - Discord expects a response within 3 seconds
                return new DiscordWebhookResult(objectMapper.writeValueAsString(Map.of("type", 5)));
            }

            return new DiscordWebhookResult(null);
        } catch (Exception e) {
            log.warn("Discord webhook handling failed channelId={}: {}", channelId, e.getMessage(), e);
            return new DiscordWebhookResult(null);
        }
    }

    public record DiscordWebhookResult(String interactionResponse) {}
}

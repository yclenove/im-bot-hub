package com.sov.imhub.im;

import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.service.ChannelCredentialResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Slack 出站消息：使用 Slack Web API chat.postMessage 发送消息。
 */
@Slf4j
@RequiredArgsConstructor
public final class SlackOutboundMessenger implements OutboundMessenger {

    private final ChannelCredentialResolver credentialResolver;
    private final InboundCommandContext ctx;
    private final BotChannelEntity channel;
    private final RestTemplate restTemplate;

    private String getBotToken() {
        return credentialResolver.getCredentialField(channel.getId(), "botToken");
    }

    private void sendPlain(String text) {
        String botToken = getBotToken();
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot token not configured for channelId={}", channel.getId());
            return;
        }
        String chatId = ctx.externalChatId();
        if (chatId == null || chatId.isBlank()) {
            chatId = ctx.externalUserId();
        }
        if (chatId == null || chatId.isBlank()) {
            log.warn("Slack chat_id and user_id both empty for channelId={}", channel.getId());
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(botToken);
            Map<String, Object> body = Map.of(
                    "channel", chatId,
                    "text", text);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://slack.com/api/chat.postMessage", request, String.class);
        } catch (Exception e) {
            log.warn("Slack sendPlain failed channelId={}: {}", channel.getId(), e.getMessage());
        }
    }

    @Override
    public void sendRateLimited() {
        sendPlain("请求过于频繁，请稍后再试。");
    }

    @Override
    public void sendNotAllowed() {
        sendPlain("你没有权限使用此机器人。");
    }

    @Override
    public void sendUnknownCommand(String command) {
        sendPlain("未知命令：/" + command);
    }

    @Override
    public void sendMissingParam(String paramName) {
        sendPlain("缺少参数：" + paramName);
    }

    @Override
    public void sendParamUsageReminder(String usageTelegramHtml, String usagePlain) {
        sendPlain(usagePlain);
    }

    @Override
    public void sendHelp(String helpTelegramHtml, String helpPlain) {
        sendPlain(helpPlain);
    }

    @Override
    public void sendQueryResult(String bodyTelegramHtml, String bodyPlain) {
        sendPlain(bodyPlain);
    }

    @Override
    public void sendQueryFailed() {
        sendPlain("查询执行失败，请稍后再试或联系管理员。");
    }
}

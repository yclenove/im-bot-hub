package com.sov.imhub.service.telegram;

import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates {@code X-Telegram-Bot-Api-Secret-Token} when the channel's webhook_secret_token is configured.
 */
@Component
@RequiredArgsConstructor
public class WebhookSecretValidator {

    public static final String HEADER_NAME = "X-Telegram-Bot-Api-Secret-Token";

    private final BotChannelMapper botChannelMapper;

    /** @return true if request may proceed */
    public boolean allow(Long channelId, String headerValue) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            return false;
        }
        String expected = channel.getWebhookSecretToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (headerValue == null) {
            return false;
        }
        return expected.equals(headerValue.trim());
    }
}

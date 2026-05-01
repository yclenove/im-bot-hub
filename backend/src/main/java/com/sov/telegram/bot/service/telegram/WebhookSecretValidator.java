package com.sov.telegram.bot.service.telegram;

import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.mapper.BotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates {@code X-Telegram-Bot-Api-Secret-Token} when {@link Bot#getWebhookSecretToken()} is configured.
 */
@Component
@RequiredArgsConstructor
public class WebhookSecretValidator {

    public static final String HEADER_NAME = "X-Telegram-Bot-Api-Secret-Token";

    private final BotMapper botMapper;

    /** @return true if request may proceed */
    public boolean allow(Long botId, String headerValue) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null) {
            return false;
        }
        String expected = bot.getWebhookSecretToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (headerValue == null) {
            return false;
        }
        return expected.equals(headerValue.trim());
    }
}

package com.sov.telegram.bot.service.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelegramWebhookUrlBuilderTest {

    @Test
    void normalizePublicBase_trimsAndStripsTrailingSlashes() {
        assertNull(TelegramWebhookUrlBuilder.normalizePublicBase(null));
        assertNull(TelegramWebhookUrlBuilder.normalizePublicBase("  "));
        assertEquals(
                "https://a.trycloudflare.com",
                TelegramWebhookUrlBuilder.normalizePublicBase("  https://a.trycloudflare.com///  "));
    }

    @Test
    void fullWebhookUrl() {
        assertEquals(
                "https://x.com/api/webhook/1",
                TelegramWebhookUrlBuilder.fullWebhookUrl("https://x.com", 1L));
    }
}

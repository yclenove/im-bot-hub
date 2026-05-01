package com.sov.telegram.bot.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BotResponse {
    Long id;
    String name;
    String telegramBotTokenMasked;
    String telegramBotUsername;
    /** Masked; empty if not configured. */
    String webhookSecretTokenMasked;
    boolean enabled;

    String telegramChatScope;

    List<Long> telegramAllowedChatIds;
}

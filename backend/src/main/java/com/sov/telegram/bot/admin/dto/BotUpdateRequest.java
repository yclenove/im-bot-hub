package com.sov.telegram.bot.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class BotUpdateRequest {
    private String name;
    private String telegramBotToken;
    private String telegramBotUsername;
    /** Null = leave unchanged; empty string = clear secret. */
    private String webhookSecretToken;
    private Boolean enabled;

    private String telegramChatScope;

    private List<Long> telegramAllowedChatIds;
}

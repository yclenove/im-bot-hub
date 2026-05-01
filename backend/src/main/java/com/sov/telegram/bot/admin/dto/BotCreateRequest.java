package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class BotCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String telegramBotToken;

    private String telegramBotUsername;
    /** Optional; must match Telegram's webhook secret header when set. */
    private String webhookSecretToken;

    private boolean enabled = true;

    /** ALL 或 GROUPS_ONLY（仅处理下方 ID 对应群，私聊忽略） */
    private String telegramChatScope = "ALL";

    private List<Long> telegramAllowedChatIds;
}

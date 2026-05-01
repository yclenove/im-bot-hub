package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class BotCreateRequest {

    @NotBlank
    private String name;

    private boolean enabled = true;

    /** @deprecated Use Channel-based credential management instead. */
    @Deprecated
    private String telegramBotToken;

    /** @deprecated Use Channel-based credential management instead. */
    @Deprecated
    private String telegramBotUsername;

    /** @deprecated Use Channel-based webhook secret instead. */
    @Deprecated
    private String webhookSecretToken;

    /** @deprecated Use Channel-based chat scope instead. */
    @Deprecated
    private String telegramChatScope = "ALL";

    /** @deprecated Use Channel-based allowed chat IDs instead. */
    @Deprecated
    private List<Long> telegramAllowedChatIds;
}

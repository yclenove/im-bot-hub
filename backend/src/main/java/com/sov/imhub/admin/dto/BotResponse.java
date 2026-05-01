package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BotResponse {
    Long id;
    String name;
    Long primaryChannelId;
    boolean enabled;

    /** @deprecated Use Channel-based credential management instead. */
    @Deprecated
    String telegramBotTokenMasked;

    /** @deprecated Use Channel-based credential management instead. */
    @Deprecated
    String telegramBotUsername;

    /** @deprecated Use Channel-based webhook secret instead. */
    @Deprecated
    String webhookSecretTokenMasked;

    /** @deprecated Use Channel-based chat scope instead. */
    @Deprecated
    String telegramChatScope;

    /** @deprecated Use Channel-based allowed chat IDs instead. */
    @Deprecated
    List<Long> telegramAllowedChatIds;
}

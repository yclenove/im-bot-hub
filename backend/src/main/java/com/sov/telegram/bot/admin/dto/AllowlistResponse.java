package com.sov.telegram.bot.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AllowlistResponse {
    Long id;
    Long botId;
    Long telegramUserId;
    boolean enabled;
}

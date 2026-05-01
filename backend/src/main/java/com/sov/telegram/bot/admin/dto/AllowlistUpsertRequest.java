package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AllowlistUpsertRequest {
    @NotNull
    private Long telegramUserId;

    private boolean enabled = true;
}

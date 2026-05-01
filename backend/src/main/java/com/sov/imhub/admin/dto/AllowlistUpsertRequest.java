package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AllowlistUpsertRequest {
    @NotNull
    private Long telegramUserId;

    private boolean enabled = true;
}

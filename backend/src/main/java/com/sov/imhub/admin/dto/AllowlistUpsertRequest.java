package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AllowlistUpsertRequest {
    private Long telegramUserId;
    private Long channelId;
    private String externalUserId;
    private boolean enabled = true;
}

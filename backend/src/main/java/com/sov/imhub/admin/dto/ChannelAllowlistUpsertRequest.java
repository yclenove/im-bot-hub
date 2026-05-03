package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChannelAllowlistUpsertRequest {
    @NotBlank
    private String externalUserId;

    private Long channelId;
    private boolean enabled = true;
}

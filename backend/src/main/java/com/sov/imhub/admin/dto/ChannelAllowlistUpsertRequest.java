package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChannelAllowlistUpsertRequest {
    @NotBlank
    private String externalUserId;

    private boolean enabled = true;
}

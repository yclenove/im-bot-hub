package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FieldMappingUpsertRequest {
    @NotBlank
    private String columnName;

    @NotBlank
    private String label;

    private int sortOrder;
    private String maskType = "NONE";
    private String formatType;
    /** JSON array of display pipeline ops; optional */
    private String displayPipelineJson;
}

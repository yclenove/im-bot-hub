package com.sov.telegram.bot.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FieldMappingResponse {
    Long id;
    Long queryId;
    String columnName;
    String label;
    int sortOrder;
    String maskType;
    String formatType;
    /** JSON array of { op, ... } steps; null if unset */
    String displayPipelineJson;
}

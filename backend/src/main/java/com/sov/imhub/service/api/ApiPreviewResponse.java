package com.sov.imhub.service.api;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ApiPreviewResponse {
    List<DiscoveredField> fields;
    List<Map<String, Object>> sampleRows;

    @Value
    @Builder
    public static class DiscoveredField {
        String key;
        String label;
        String jsonPointer;
        String sampleValue;
    }
}

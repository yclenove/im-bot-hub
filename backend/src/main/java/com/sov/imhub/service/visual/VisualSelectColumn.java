package com.sov.imhub.service.visual;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class VisualSelectColumn {
    private String column;
    private String label;
    /** Value display labels keyed by stringified raw value */
    @JsonProperty("enum")
    private Map<String, String> enumLabels = new LinkedHashMap<>();
}

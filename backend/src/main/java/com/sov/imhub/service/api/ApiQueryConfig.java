package com.sov.imhub.service.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApiQueryConfig {

    private String presetKey;
    private String name;
    private String method = "GET";
    private String path = "";
    private String responseRootPointer = "";
    private String bodyTemplate = "";
    private String localResultLimitParamName = "";
    private Integer localResultLimit;
    private List<ApiRequestValue> queryParams = new ArrayList<>();
    private List<ApiRequestValue> headers = new ArrayList<>();
    private List<ApiOutputField> outputs = new ArrayList<>();

    @Data
    public static class ApiRequestValue {
        private String key;
        private String valueSource = "LITERAL";
        private String value;
        private String paramName;
        private String sampleValue;
    }

    @Data
    public static class ApiOutputField {
        private String key;
        private String label;
        private String jsonPointer;
        private Integer sortOrder;
        private String maskType = "NONE";
        private String formatType;
        private String displayPipelineJson;
    }
}

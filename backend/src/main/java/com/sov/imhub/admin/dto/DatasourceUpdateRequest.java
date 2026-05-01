package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DatasourceUpdateRequest {

    private String name;
    private String sourceType;
    private String jdbcUrl;
    private String apiBaseUrl;
    private String apiPresetKey;
    private String authType;
    private String authConfigJson;
    private String defaultHeadersJson;
    private String defaultQueryParamsJson;
    private Integer requestTimeoutMs;
    private String configJson;
    private String username;
    /** If null or omitted, keep existing password cipher. */
    private String passwordPlain;

    @Min(1)
    @Max(200)
    private Integer poolMax;
}

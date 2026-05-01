package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DatasourceCreateRequest {

    private String name;

    private String jdbcUrl;

    private String sourceType = "DATABASE";

    private String apiBaseUrl;

    private String apiPresetKey;

    private String authType;

    private String authConfigJson;

    private String defaultHeadersJson;

    private String defaultQueryParamsJson;

    private Integer requestTimeoutMs = 5000;

    private String configJson;

    private String username;

    private String passwordPlain;

    @Min(1)
    @Max(200)
    private int poolMax = 5;
}

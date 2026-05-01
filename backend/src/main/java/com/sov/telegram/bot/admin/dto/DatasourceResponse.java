package com.sov.telegram.bot.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DatasourceResponse {
    Long id;
    String name;
    String sourceType;
    String jdbcUrl;
    String apiBaseUrl;
    String apiPresetKey;
    String authType;
    String defaultHeadersJson;
    String defaultQueryParamsJson;
    int requestTimeoutMs;
    String configJson;
    String username;
    int poolMax;
}

package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QueryDefinitionResponse {
    Long id;
    Long botId;
    Long datasourceId;
    String command;
    String name;
    String telegramMenuDescription;
    String sqlTemplate;
    String queryMode;
    String visualConfigJson;
    String apiConfigJson;
    String paramSchemaJson;
    int timeoutMs;
    int maxRows;
    boolean enabled;
    /** LIST | LIST_DOT | LIST_CODE | LIST_BLOCKQUOTE | SECTION | MONO_PRE | CODE_BLOCK | KV_SINGLE_LINE | VALUES_JOIN_SPACE | VALUES_JOIN_PIPE | VALUES_JOIN_CUSTOM:<delimiter> | TABLE_PRE */
    String telegramReplyStyle;
}

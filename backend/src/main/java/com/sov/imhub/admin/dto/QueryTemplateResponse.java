package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 查询模板响应 DTO。
 */
@Value
@Builder
public class QueryTemplateResponse {
    Long id;
    String name;
    String category;
    String description;
    String configJson;
    int version;
    String author;
    int downloads;
    boolean enabled;
    LocalDateTime createdAt;
}

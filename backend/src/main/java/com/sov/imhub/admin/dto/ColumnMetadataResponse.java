package com.sov.imhub.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JDBC column name and optional REMARKS (e.g. MySQL COLUMN_COMMENT).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnMetadataResponse(String name, String comment) {}

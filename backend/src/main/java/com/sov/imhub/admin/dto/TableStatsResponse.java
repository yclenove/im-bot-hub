package com.sov.imhub.admin.dto;

import lombok.Data;

@Data
public class TableStatsResponse {
    /** From information_schema.TABLE_ROWS (estimate). */
    private Long tableRowsEstimate;
    private String engine;
    /** Filled when exactCount=true succeeds. */
    private Long exactCount;
    /** e.g. timeout, error message when exact count fails. */
    private String exactCountError;
}

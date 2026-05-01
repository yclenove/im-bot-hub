package com.sov.imhub.admin.dto;

import lombok.Data;

@Data
public class VisualBenchmarkResponse {
    private StrategyResult legacyOr;
    private StrategyResult unionAll;
    /** Whether alternating OR/UNION/OR/UNION runs were used for averaging. */
    private boolean alternatingRuns;
    private String note;

    @Data
    public static class StrategyResult {
        private String strategy;
        private boolean ok;
        private Long durationMsAvg;
        private Integer rowCountLast;
        private String error;
        private String sqlTemplate;
    }
}

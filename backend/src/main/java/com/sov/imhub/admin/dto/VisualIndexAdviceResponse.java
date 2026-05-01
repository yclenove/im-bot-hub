package com.sov.imhub.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VisualIndexAdviceResponse {
    private String summary;
    /** 当前表已有索引的可读摘要，例如 {@code `idx_x`: a, b} */
    private List<String> existingIndexSummaries = new ArrayList<>();
    /** 因已有索引覆盖而未生成的建议说明 */
    private List<String> coverageSkips = new ArrayList<>();
    private List<Recommendation> recommendations = new ArrayList<>();
    private List<String> ddlStatements = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class Recommendation {
        private String rationale;
        private List<String> columns = new ArrayList<>();
    }
}

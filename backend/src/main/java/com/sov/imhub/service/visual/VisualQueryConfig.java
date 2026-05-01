package com.sov.imhub.service.visual;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted wizard JSON (single-table SELECT, optional multi-column OR on one keyword param).
 */
@Data
public class VisualQueryConfig {
    /** Physical table name (from metadata) */
    private String table;
    /** Optional; MySQL uses default catalog from JDBC URL */
    private String schema;
    private List<VisualSelectColumn> select = new ArrayList<>();
    /** Columns combined with OR for the search keyword */
    private List<String> searchOrColumns = new ArrayList<>();
    /** Placeholder name in template, default {@code kw} */
    private String searchParamName = "kw";
    /** Order of #{params}; should include {@link #searchParamName} when OR clause is used */
    private List<String> paramOrder = new ArrayList<>();
    /** 固定 AND 条件，如 {@code is_deleted = 0}；仅 INT/BOOL 字面量 */
    private List<VisualFixedPredicate> fixedPredicates = new ArrayList<>();
    /**
     * Multi-column keyword composition; {@code null} or {@link OrCompositionStrategy#LEGACY_OR} keeps
     * {@code (c1=#{kw} OR c2=#{kw})}.
     */
    private OrCompositionStrategy orCompositionStrategy;
    /** Optional snapshot of TABLE_ROWS when user saved (for display only). */
    private Long tableRowsEstimate;
}

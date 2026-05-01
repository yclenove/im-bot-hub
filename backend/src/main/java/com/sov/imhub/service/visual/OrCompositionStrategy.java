package com.sov.imhub.service.visual;

/**
 * How multi-column keyword search is composed in generated SQL.
 */
public enum OrCompositionStrategy {
    /** {@code (c1=#{kw} OR c2=#{kw})} */
    LEGACY_OR,
    /**
     * Per-column branches merged with SQL {@code UNION} (distinct, dedupes rows across branches).
     * JSON name remains {@code UNION_ALL} for backward compatibility with saved configs.
     */
    UNION_ALL
}

package com.sov.imhub.service.visual;

import lombok.Data;

/**
 * 向导固定 WHERE 片段：仅支持整型或布尔字面量（写入 SQL，不经由用户参数），避免字符串拼接注入。
 */
@Data
public class VisualFixedPredicate {

    /** 表列名（须存在于元数据） */
    private String column;

    /**
     * Comparison operator; {@code null} / blank / {@code EQ} means equality.
     * {@code NE} means not equal (supported for INT and BOOL literals only).
     */
    private String operator;

    /** {@code INT} 或 {@code BOOL}（大小写不敏感） */
    private String valueType;

    /** valueType=INT 时使用 */
    private Long intValue;

    /** valueType=BOOL 时使用 */
    private Boolean boolValue;
}

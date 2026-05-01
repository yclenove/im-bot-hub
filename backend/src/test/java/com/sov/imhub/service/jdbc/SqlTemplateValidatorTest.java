package com.sov.imhub.service.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SqlTemplateValidator} 单元测试：覆盖允许与应拒绝的 SQL 模板。
 */
class SqlTemplateValidatorTest {

    private SqlTemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlTemplateValidator();
    }

    @Test
    void shouldAcceptSimpleSelectWithPlaceholders() {
        assertDoesNotThrow(
                () ->
                        validator.validate(
                                "SELECT id, status FROM orders WHERE order_no = #{orderNo}"));
    }

    @Test
    void shouldRejectNonSelect() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("UPDATE orders SET x=1"));
    }

    @Test
    void shouldRejectMultiStatement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("SELECT 1; SELECT 2"));
    }

    @Test
    void shouldRejectEmpty() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("   "));
    }

    @Test
    void shouldAcceptSelectUnionAllSelect() {
        assertDoesNotThrow(
                () ->
                        validator.validate(
                                "SELECT * FROM ((SELECT id FROM t WHERE a = #{kw} LIMIT 50) UNION ALL (SELECT id FROM t WHERE b = #{kw} LIMIT 50)) _vq"));
    }

    @Test
    void shouldAcceptSelectUnionDistinctSelect() {
        assertDoesNotThrow(
                () ->
                        validator.validate(
                                "SELECT * FROM ((SELECT id FROM t WHERE a = #{kw} LIMIT 50) UNION (SELECT id FROM t WHERE b = #{kw} LIMIT 50)) _vq"));
    }
}

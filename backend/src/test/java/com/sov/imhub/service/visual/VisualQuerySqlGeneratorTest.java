package com.sov.imhub.service.visual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualQuerySqlGeneratorTest {

    private final VisualQuerySqlGenerator generator = new VisualQuerySqlGenerator();

    @Test
    void generatesOrWhereAndParamSchema() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("orders");
        VisualSelectColumn a = new VisualSelectColumn();
        a.setColumn("internal_no");
        a.setLabel("内部单号");
        VisualSelectColumn b = new VisualSelectColumn();
        b.setColumn("external_no");
        c.setSelect(List.of(a, b));
        c.setSearchOrColumns(List.of("internal_no", "external_no"));
        c.setSearchParamName("kw");
        c.setParamOrder(List.of("kw"));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals(
                "SELECT `internal_no`, `external_no` FROM `orders` WHERE (`internal_no` = #{kw} OR `external_no` = #{kw})",
                g.sqlTemplate());
        assertEquals("{\"params\":[\"kw\"]}", g.paramSchemaJson());
    }

    @Test
    void unionAllStrategyWithTwoOrColumnsWrapsSubqueryAndPerBranchLimit() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("orders");
        VisualSelectColumn a = new VisualSelectColumn();
        a.setColumn("internal_no");
        VisualSelectColumn b = new VisualSelectColumn();
        b.setColumn("external_no");
        c.setSelect(List.of(a, b));
        c.setSearchOrColumns(List.of("internal_no", "external_no"));
        c.setSearchParamName("kw");
        c.setParamOrder(List.of("kw"));
        c.setOrCompositionStrategy(OrCompositionStrategy.UNION_ALL);

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertTrue(g.sqlTemplate().startsWith("SELECT * FROM ("));
        assertTrue(g.sqlTemplate().contains(" UNION "));
        assertFalse(g.sqlTemplate().contains(" UNION ALL "));
        assertTrue(g.sqlTemplate().endsWith(") _vq"));
        assertTrue(g.sqlTemplate().contains("LIMIT 50"));
        assertEquals("{\"params\":[\"kw\"]}", g.paramSchemaJson());
    }

    @Test
    void unionAllWithFixedPredicates() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of("a", "b"));
        c.setSearchParamName("kw");
        c.setParamOrder(List.of("kw"));
        c.setOrCompositionStrategy(OrCompositionStrategy.UNION_ALL);
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("active");
        fp.setValueType("BOOL");
        fp.setBoolValue(true);
        c.setFixedPredicates(List.of(fp));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 10);
        assertTrue(g.sqlTemplate().contains("`active` = TRUE"));
        assertTrue(g.sqlTemplate().contains(" UNION "));
        assertFalse(g.sqlTemplate().contains(" UNION ALL "));
    }

    @Test
    void singleOrColumnWithUnionStrategyFallsBackToLegacyShape() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of("name"));
        c.setSearchParamName("kw");
        c.setParamOrder(List.of("kw"));
        c.setOrCompositionStrategy(OrCompositionStrategy.UNION_ALL);

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals("SELECT `id` FROM `t` WHERE (`name` = #{kw})", g.sqlTemplate());
    }

    @Test
    void noWhereWhenNoSearchColumns() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of());
        c.setParamOrder(List.of());

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals("SELECT `id` FROM `t`", g.sqlTemplate());
        assertEquals("{\"params\":[]}", g.paramSchemaJson());
    }

    @Test
    void fixedPredicateIntOnlyWhere() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("orders");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of());
        c.setParamOrder(List.of());
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("is_deleted");
        fp.setValueType("INT");
        fp.setIntValue(0L);
        c.setFixedPredicates(List.of(fp));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals("SELECT `id` FROM `orders` WHERE `is_deleted` = 0", g.sqlTemplate());
        assertEquals("{\"params\":[]}", g.paramSchemaJson());
    }

    @Test
    void fixedPredicateIntNotEqual() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("orders");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of());
        c.setParamOrder(List.of());
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("status");
        fp.setOperator("NE");
        fp.setValueType("INT");
        fp.setIntValue(9L);
        c.setFixedPredicates(List.of(fp));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals("SELECT `id` FROM `orders` WHERE `status` <> 9", g.sqlTemplate());
    }

    @Test
    void fixedPredicateBoolNotEqual() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("archived");
        fp.setOperator("NE");
        fp.setValueType("BOOL");
        fp.setBoolValue(true);
        c.setFixedPredicates(List.of(fp));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals("SELECT `id` FROM `t` WHERE `archived` <> TRUE", g.sqlTemplate());
    }

    @Test
    void orWhereAndFixedBool() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        c.setSearchOrColumns(List.of("name"));
        c.setSearchParamName("kw");
        c.setParamOrder(List.of("kw"));
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("active");
        fp.setValueType("BOOL");
        fp.setBoolValue(true);
        c.setFixedPredicates(List.of(fp));

        VisualQuerySqlGenerator.GeneratedSql g = generator.generate(c, 50);
        assertEquals(
                "SELECT `id` FROM `t` WHERE (`name` = #{kw}) AND `active` = TRUE",
                g.sqlTemplate());
        assertEquals("{\"params\":[\"kw\"]}", g.paramSchemaJson());
    }

    @Test
    void rejectsFixedBoolWithoutValue() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable("t");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        VisualFixedPredicate fp = new VisualFixedPredicate();
        fp.setColumn("x");
        fp.setValueType("BOOL");
        c.setFixedPredicates(List.of(fp));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(c, 50));
    }

    @Test
    void quotesBackticksInIdentifiers() {
        assertEquals("`a``b`", VisualQuerySqlGenerator.quoteId("a`b"));
    }

    @Test
    void rejectsEmptyTable() {
        VisualQueryConfig c = new VisualQueryConfig();
        c.setTable(" ");
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("id");
        c.setSelect(List.of(col));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(c, 50));
    }

    @Test
    void enumOnColumnSerializesInConfigOnly() {
        VisualSelectColumn col = new VisualSelectColumn();
        col.setColumn("status");
        col.setEnumLabels(Map.of("0", "待支付", "1", "已付"));
        assertEquals(2, col.getEnumLabels().size());
    }
}

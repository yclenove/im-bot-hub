package com.sov.imhub.service.visual;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles {@link VisualQueryConfig} into a {@code sql_template} and param schema JSON.
 */
@Component
public class VisualQuerySqlGenerator {

    /**
     * @param maxRows bound for per-branch LIMIT (UNION) and must be {@code >= 1} when used
     */
    public GeneratedSql generate(VisualQueryConfig c, int maxRows) {
        int branchLimit = Math.max(1, maxRows);
        if (c.getTable() == null || c.getTable().isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        if (c.getSelect() == null || c.getSelect().isEmpty()) {
            throw new IllegalArgumentException("select columns are required");
        }
        String table = quoteId(c.getTable().trim());
        List<String> selectParts = new ArrayList<>();
        for (VisualSelectColumn col : c.getSelect()) {
            if (col.getColumn() == null || col.getColumn().isBlank()) {
                throw new IllegalArgumentException("select.column is required");
            }
            selectParts.add(quoteId(col.getColumn().trim()));
        }
        String selectList = String.join(", ", selectParts);
        String searchParam = (c.getSearchParamName() == null || c.getSearchParamName().isBlank())
                ? "kw"
                : c.getSearchParamName().trim();
        List<String> paramOrder = resolveParamOrder(c, searchParam);

        OrCompositionStrategy strategy =
                c.getOrCompositionStrategy() == null ? OrCompositionStrategy.LEGACY_OR : c.getOrCompositionStrategy();

        String sql;
        if (strategy == OrCompositionStrategy.UNION_ALL
                && c.getSearchOrColumns() != null
                && c.getSearchOrColumns().size() >= 2) {
            sql = buildUnionMergedSql(c, table, selectList, searchParam, branchLimit);
        } else {
            sql = buildLegacyOrSql(c, table, selectList, searchParam);
        }

        String paramJson = toParamSchemaJson(paramOrder);
        return new GeneratedSql(sql, paramJson);
    }

    private static String buildLegacyOrSql(
            VisualQueryConfig c, String table, String selectList, String searchParam) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        sb.append(selectList);
        sb.append(" FROM ").append(table);
        boolean hasOr = c.getSearchOrColumns() != null && !c.getSearchOrColumns().isEmpty();
        boolean hasFixed = c.getFixedPredicates() != null && !c.getFixedPredicates().isEmpty();
        if (hasOr || hasFixed) {
            sb.append(" WHERE ");
            if (hasOr) {
                sb.append("(");
                List<String> ors = new ArrayList<>();
                for (String col : c.getSearchOrColumns()) {
                    if (col == null || col.isBlank()) {
                        throw new IllegalArgumentException("searchOrColumns entry is blank");
                    }
                    ors.add(quoteId(col.trim()) + " = #{" + searchParam + "}");
                }
                sb.append(String.join(" OR ", ors));
                sb.append(")");
            }
            if (hasFixed) {
                if (hasOr) {
                    sb.append(" AND ");
                }
                sb.append(fixedAndSql(c.getFixedPredicates()));
            }
        }
        return sb.toString();
    }

    /** Branches joined with SQL {@code UNION} (distinct). */
    private static String buildUnionMergedSql(
            VisualQueryConfig c, String table, String selectList, String searchParam, int branchLimit) {
        String fixedPart = "";
        if (c.getFixedPredicates() != null && !c.getFixedPredicates().isEmpty()) {
            fixedPart = " AND " + fixedAndSql(c.getFixedPredicates());
        }
        List<String> branches = new ArrayList<>();
        for (String col : c.getSearchOrColumns()) {
            if (col == null || col.isBlank()) {
                throw new IllegalArgumentException("searchOrColumns entry is blank");
            }
            String w = quoteId(col.trim()) + " = #{" + searchParam + "}" + fixedPart;
            branches.add(
                    "(SELECT "
                            + selectList
                            + " FROM "
                            + table
                            + " WHERE "
                            + w
                            + " LIMIT "
                            + branchLimit
                            + ")");
        }
        // SQL UNION (default DISTINCT) dedupes rows that appear in multiple branches; heavier than UNION ALL.
        String inner = String.join(" UNION ", branches);
        return "SELECT * FROM (" + inner + ") _vq";
    }

    private static List<String> resolveParamOrder(VisualQueryConfig c, String searchParam) {
        List<String> order = c.getParamOrder() != null ? new ArrayList<>(c.getParamOrder()) : new ArrayList<>();
        order = order.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (order.isEmpty()
                && c.getSearchOrColumns() != null
                && !c.getSearchOrColumns().isEmpty()) {
            order = List.of(searchParam);
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>(order);
        return new ArrayList<>(dedup);
    }

    private static String toParamSchemaJson(List<String> paramOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"params\":[");
        for (int i = 0; i < paramOrder.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(paramOrder.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String fixedAndSql(List<VisualFixedPredicate> preds) {
        List<String> parts = new ArrayList<>();
        for (VisualFixedPredicate p : preds) {
            if (p == null || p.getColumn() == null || p.getColumn().isBlank()) {
                throw new IllegalArgumentException("fixedPredicates entry missing column");
            }
            String vt = p.getValueType() == null ? "" : p.getValueType().trim().toUpperCase();
            String col = quoteId(p.getColumn().trim());
            String op = p.getOperator() == null ? "" : p.getOperator().trim().toUpperCase();
            boolean ne = "NE".equals(op);
            if (!ne && !op.isEmpty() && !"EQ".equals(op)) {
                throw new IllegalArgumentException("fixedPredicates operator must be EQ, NE, or omitted");
            }
            String cmp = ne ? " <> " : " = ";
            if ("INT".equals(vt)) {
                if (p.getIntValue() == null) {
                    throw new IllegalArgumentException("fixedPredicates INT requires intValue");
                }
                parts.add(col + cmp + p.getIntValue());
            } else if ("BOOL".equals(vt)) {
                if (p.getBoolValue() == null) {
                    throw new IllegalArgumentException("fixedPredicates BOOL requires boolValue");
                }
                parts.add(col + cmp + (Boolean.TRUE.equals(p.getBoolValue()) ? "TRUE" : "FALSE"));
            } else {
                throw new IllegalArgumentException("fixedPredicates valueType must be INT or BOOL");
            }
        }
        return String.join(" AND ", parts);
    }

    static String quoteId(String id) {
        return "`" + id.replace("`", "``") + "`";
    }

    public record GeneratedSql(String sqlTemplate, String paramSchemaJson) {}
}

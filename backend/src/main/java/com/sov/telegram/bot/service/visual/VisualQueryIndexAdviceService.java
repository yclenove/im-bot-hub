package com.sov.telegram.bot.service.visual;

import com.sov.telegram.bot.admin.dto.VisualIndexAdviceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Heuristic CREATE INDEX suggestions for wizard configs; never executes DDL.
 */
@Service
@RequiredArgsConstructor
public class VisualQueryIndexAdviceService {

    private final VisualQueryCompilationService compilationService;
    private final DatasourceMetadataService metadataService;

    public VisualIndexAdviceResponse advise(long datasourceId, String visualConfigJson) {
        VisualQueryConfig c = compilationService.parseConfig(visualConfigJson);
        String canonical = metadataService.resolveTableName(datasourceId, c.getTable());
        c.setTable(canonical);
        metadataService.validateAgainstMetadata(datasourceId, c);

        List<DatasourceMetadataService.MysqlIndexDefinition> defs =
                metadataService.listMysqlIndexDefinitions(datasourceId, canonical);
        Set<String> existingLower =
                defs.stream()
                        .map(d -> d.keyName().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());

        String quotedTable = VisualQuerySqlGenerator.quoteId(canonical);
        VisualIndexAdviceResponse out = new VisualIndexAdviceResponse();
        out.setSummary(
                "以下为基于向导配置的启发式建议，不保证最优；请在主库审阅后执行，大表建议使用 Online DDL 或 pt-online-schema-change。");
        out.getWarnings().add("当前连接可能为只读从库：请勿在本工具中执行 DDL，仅在主库复制执行下列语句。");

        for (DatasourceMetadataService.MysqlIndexDefinition d : defs) {
            String cols = String.join(", ", d.columns());
            String u = d.unique() ? " [UNIQUE]" : "";
            out.getExistingIndexSummaries().add("`" + d.keyName() + "`" + u + ": " + cols);
        }

        List<String> searchCols =
                c.getSearchOrColumns() == null
                        ? List.of()
                        : c.getSearchOrColumns().stream()
                                .filter(s -> s != null && !s.isBlank())
                                .map(String::trim)
                                .toList();

        List<VisualIndexAdviceResponse.Recommendation> recs = new ArrayList<>();
        List<String> ddls = new ArrayList<>();

        for (String sc : searchCols) {
            if (hasOrderedPrefixCover(defs, List.of(sc))) {
                String kn = findCoveringIndexKeyName(defs, List.of(sc));
                out.getCoverageSkips()
                        .add(
                                "列 `"
                                        + sc
                                        + "` 已由现有索引「"
                                        + kn
                                        + "」的列前缀覆盖，未生成单列 CREATE INDEX。");
                continue;
            }
            String idx = makeIndexName("idx_vq", canonical, sc, null);
            String ddl =
                    "CREATE INDEX `"
                            + escapeBacktick(idx)
                            + "` ON "
                            + quotedTable
                            + " ("
                            + VisualQuerySqlGenerator.quoteId(sc)
                            + ");";
            VisualIndexAdviceResponse.Recommendation rec = new VisualIndexAdviceResponse.Recommendation();
            rec.setRationale("多列 OR / UNION 分支上，单列等值检索通常需要该列上的索引（或复合索引前缀包含该列）。");
            rec.setColumns(List.of(sc));
            recs.add(rec);
            ddls.add(ddl);
            if (existingLower.contains(idx.toLowerCase(Locale.ROOT))) {
                out.getWarnings().add("索引名可能已存在，请改名: " + idx);
            }
        }

        if (c.getFixedPredicates() != null
                && !c.getFixedPredicates().isEmpty()
                && !searchCols.isEmpty()) {
            String kwCol = searchCols.get(0);
            List<String> logicalComposite = new ArrayList<>();
            for (VisualFixedPredicate fp : c.getFixedPredicates()) {
                if (fp == null || fp.getColumn() == null || fp.getColumn().isBlank()) {
                    continue;
                }
                logicalComposite.add(fp.getColumn().trim());
            }
            logicalComposite.add(kwCol);
            if (logicalComposite.size() >= 2) {
                if (hasOrderedPrefixCover(defs, logicalComposite)) {
                    String kn = findCoveringIndexKeyName(defs, logicalComposite);
                    out.getCoverageSkips()
                            .add(
                                    "列序 ("
                                            + String.join(", ", logicalComposite)
                                            + ") 已由现有索引「"
                                            + kn
                                            + "」的前缀覆盖，未生成复合 CREATE INDEX。");
                } else {
                    List<String> colParts = new ArrayList<>();
                    for (VisualFixedPredicate fp : c.getFixedPredicates()) {
                        if (fp == null || fp.getColumn() == null || fp.getColumn().isBlank()) {
                            continue;
                        }
                        colParts.add(VisualQuerySqlGenerator.quoteId(fp.getColumn().trim()));
                    }
                    colParts.add(VisualQuerySqlGenerator.quoteId(kwCol));
                    String idx = makeIndexName("idx_vq", canonical, kwCol, "fx");
                    String ddl =
                            "CREATE INDEX `"
                                    + escapeBacktick(idx)
                                    + "` ON "
                                    + quotedTable
                                    + " ("
                                    + String.join(", ", colParts)
                                    + ");";
                    VisualIndexAdviceResponse.Recommendation rec = new VisualIndexAdviceResponse.Recommendation();
                    rec.setRationale(
                            "固定等值条件列在前、关键词列在后的复合索引（中性顺序）；请按实际选择性调整列顺序。");
                    rec.setColumns(
                            c.getFixedPredicates().stream()
                                    .map(VisualFixedPredicate::getColumn)
                                    .filter(s -> s != null && !s.isBlank())
                                    .map(String::trim)
                                    .collect(Collectors.toCollection(ArrayList::new)));
                    rec.getColumns().add(kwCol);
                    recs.add(rec);
                    ddls.add(ddl);
                    if (existingLower.contains(idx.toLowerCase(Locale.ROOT))) {
                        out.getWarnings().add("索引名可能已存在，请改名: " + idx);
                    }
                }
            }
        }

        out.setRecommendations(recs);
        out.setDdlStatements(ddls);

        if (!ddls.isEmpty()) {
            ddls.add(0, "-- 请在主库执行；执行前请用 EXPLAIN 与业务峰值评估影响。");
        } else if (searchCols.isEmpty()) {
            ddls.add("-- 未配置 OR 检索列，无单列索引 DDL。");
        } else if (!out.getCoverageSkips().isEmpty()) {
            ddls.add("-- 已有索引覆盖了上述检索列/列序，未输出新的 CREATE INDEX；仍建议用 EXPLAIN 核实。");
        } else {
            ddls.add("-- 未生成新的 CREATE INDEX。");
        }

        dedupList(out.getCoverageSkips());
        dedupWarnings(out);
        return out;
    }

    /**
     * True when some index column list starts with {@code columns} in order (case-insensitive identifiers).
     */
    static boolean hasOrderedPrefixCover(
            List<DatasourceMetadataService.MysqlIndexDefinition> defs, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return false;
        }
        List<String> want = columns.stream().map(VisualQueryIndexAdviceService::normCol).toList();
        if (want.stream().anyMatch(String::isEmpty)) {
            return false;
        }
        for (DatasourceMetadataService.MysqlIndexDefinition d : defs) {
            List<String> have = d.columns().stream().map(VisualQueryIndexAdviceService::normCol).toList();
            if (have.size() < want.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < want.size(); i++) {
                if (!have.get(i).equals(want.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    static String findCoveringIndexKeyName(
            List<DatasourceMetadataService.MysqlIndexDefinition> defs, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return "?";
        }
        List<String> want = columns.stream().map(VisualQueryIndexAdviceService::normCol).toList();
        if (want.stream().anyMatch(String::isEmpty)) {
            return "?";
        }
        for (DatasourceMetadataService.MysqlIndexDefinition d : defs) {
            List<String> have = d.columns().stream().map(VisualQueryIndexAdviceService::normCol).toList();
            if (have.size() < want.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < want.size(); i++) {
                if (!have.get(i).equals(want.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return d.keyName();
            }
        }
        return "?";
    }

    private static String normCol(String c) {
        return c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
    }

    private static void dedupList(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        list.clear();
        list.addAll(new LinkedHashSet<>(copy));
    }

    private static void dedupWarnings(VisualIndexAdviceResponse out) {
        out.setWarnings(new ArrayList<>(new LinkedHashSet<>(out.getWarnings())));
    }

    private static String escapeBacktick(String id) {
        return id.replace("`", "``");
    }

    /** MySQL identifier max length 64. */
    static String makeIndexName(String prefix, String table, String col, String extraSuffix) {
        String t = table.replaceAll("[^a-zA-Z0-9_]", "_");
        if (t.length() > 18) {
            t = t.substring(0, 18);
        }
        String c = col.replaceAll("[^a-zA-Z0-9_]", "_");
        if (c.length() > 22) {
            c = c.substring(0, 22);
        }
        String s = extraSuffix != null ? "_" + extraSuffix.replaceAll("[^a-zA-Z0-9_]", "_") : "";
        String base = prefix + "_" + t + "_" + c + s;
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        return base;
    }
}

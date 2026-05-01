package com.sov.telegram.bot.service.visual;

import com.sov.telegram.bot.admin.dto.ColumnMetadataResponse;
import com.sov.telegram.bot.service.jdbc.BusinessDataSourceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Read-only JDBC metadata for admin UI; identifiers returned are safe to use in generated SQL after quoting.
 */
@Service
@RequiredArgsConstructor
public class DatasourceMetadataService {

    private final BusinessDataSourceRegistry businessDataSourceRegistry;

    public List<String> listTables(long datasourceId, String q) {
        String pattern = (q == null || q.isBlank()) ? "%" : "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> out = new ArrayList<>();
        try (Connection conn = businessDataSourceRegistry.dataSource(datasourceId).getConnection()) {
            String catalog = conn.getCatalog();
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(catalog, null, pattern, new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isBlank()) {
                        out.add(name);
                    }
                }
            }
            try (ResultSet rs = md.getTables(catalog, null, pattern, new String[] {"VIEW"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isBlank()) {
                        out.add(name);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list tables: " + e.getMessage(), e);
        }
        Collections.sort(out);
        return dedupSorted(out);
    }

    /**
     * Column identifiers and optional REMARKS (MySQL maps COLUMN_COMMENT to REMARKS in JDBC).
     */
    public List<ColumnMetadataResponse> listColumns(long datasourceId, String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        LinkedHashMap<String, ColumnMetadataResponse> byName = new LinkedHashMap<>();
        try (Connection conn = businessDataSourceRegistry.dataSource(datasourceId).getConnection()) {
            String catalog = conn.getCatalog();
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(catalog, null, table.trim(), "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    if (byName.containsKey(name)) {
                        continue;
                    }
                    String remarks = rs.getString("REMARKS");
                    String comment = remarks == null || remarks.isBlank() ? null : remarks.trim();
                    byName.put(name, new ColumnMetadataResponse(name.trim(), comment));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list columns: " + e.getMessage(), e);
        }
        return new ArrayList<>(byName.values());
    }

    public List<String> listColumnNames(long datasourceId, String table) {
        return listColumns(datasourceId, table).stream().map(ColumnMetadataResponse::name).toList();
    }

    /**
     * Ensures table and all referenced columns exist in metadata (case-sensitive match to JDBC, MySQL often lowercases).
     */
    public void validateAgainstMetadata(long datasourceId, VisualQueryConfig config) {
        String canonicalTable = resolveTableName(datasourceId, config.getTable().trim());
        List<String> cols = listColumnNames(datasourceId, canonicalTable);
        java.util.Set<String> colSet = new java.util.HashSet<>();
        for (String c : cols) {
            colSet.add(c);
            colSet.add(c.toLowerCase(Locale.ROOT));
        }
        for (VisualSelectColumn sc : config.getSelect()) {
            ensureColumn(colSet, sc.getColumn(), "select");
        }
        if (config.getSearchOrColumns() != null) {
            for (String sc : config.getSearchOrColumns()) {
                ensureColumn(colSet, sc, "searchOrColumns");
            }
        }
        if (config.getFixedPredicates() != null) {
            for (VisualFixedPredicate fp : config.getFixedPredicates()) {
                if (fp == null || fp.getColumn() == null || fp.getColumn().isBlank()) {
                    throw new IllegalArgumentException("Empty column in fixedPredicates");
                }
                ensureColumn(colSet, fp.getColumn(), "fixedPredicates");
            }
        }
    }

    /** Match requested table name to JDBC-returned identifier (MySQL may change case). */
    public String resolveTableName(long datasourceId, String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        String want = table.trim();
        List<String> tables = listTables(datasourceId, "");
        for (String t : tables) {
            if (t.equals(want) || t.equalsIgnoreCase(want)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown table for this datasource: " + want);
    }

    private static void ensureColumn(java.util.Set<String> colSet, String name, String ctx) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Empty column in " + ctx);
        }
        String n = name.trim();
        if (!colSet.contains(n) && !colSet.contains(n.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown column in " + ctx + ": " + n);
        }
    }

    private static List<String> dedupSorted(List<String> in) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String s : in) {
            seen.add(s);
        }
        List<String> out = new ArrayList<>(seen);
        Collections.sort(out);
        return out;
    }

    /**
     * {@code TABLE_ROWS} and {@code ENGINE} from information_schema for the current catalog.
     */
    public TableEngineStats loadTableEngineStats(long datasourceId, String canonicalTable) {
        if (canonicalTable == null || canonicalTable.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        try (Connection conn = businessDataSourceRegistry.dataSource(datasourceId).getConnection()) {
            String sql =
                    "SELECT TABLE_ROWS, ENGINE FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, canonicalTable.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new TableEngineStats(null, null);
                    }
                    long rows = rs.getLong("TABLE_ROWS");
                    if (rs.wasNull()) {
                        rows = -1;
                    }
                    String engine = rs.getString("ENGINE");
                    Long est = rows < 0 ? null : rows;
                    return new TableEngineStats(est, engine == null ? null : engine.trim());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read table stats: " + e.getMessage(), e);
        }
    }

    /**
     * Exact row count with JDBC query timeout (may be slow on large tables).
     */
    public long exactTableRowCount(long datasourceId, String canonicalTable, int timeoutSeconds) {
        if (canonicalTable == null || canonicalTable.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        String quoted = VisualQuerySqlGenerator.quoteId(canonicalTable.trim());
        try (Connection conn = businessDataSourceRegistry.dataSource(datasourceId).getConnection();
                Statement st = conn.createStatement()) {
            st.setQueryTimeout(Math.max(1, timeoutSeconds));
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM " + quoted)) {
                if (!rs.next()) {
                    throw new IllegalStateException("COUNT returned no row");
                }
                return rs.getLong("c");
            }
        } catch (Exception e) {
            throw new IllegalStateException("COUNT(*) failed: " + e.getMessage(), e);
        }
    }

    /**
     * One physical index from {@code SHOW INDEX}, columns ordered by {@code Seq_in_index}.
     *
     * @param unique {@code Non_unique == 0} on the first row of that key (MySQL convention).
     */
    public record MysqlIndexDefinition(String keyName, boolean unique, List<String> columns) {}

    /** Distinct MySQL index names from {@code SHOW INDEX} (order preserved). */
    public List<String> listMysqlIndexNames(long datasourceId, String canonicalTable) {
        return listMysqlIndexDefinitions(datasourceId, canonicalTable).stream()
                .map(MysqlIndexDefinition::keyName)
                .toList();
    }

    /**
     * Parses {@code SHOW INDEX} into definitions with ordered column lists per {@code Key_name}.
     */
    public List<MysqlIndexDefinition> listMysqlIndexDefinitions(long datasourceId, String canonicalTable) {
        if (canonicalTable == null || canonicalTable.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        String quoted = VisualQuerySqlGenerator.quoteId(canonicalTable.trim());
        LinkedHashMap<String, IndexBuild> byKey = new LinkedHashMap<>();
        try (Connection conn = businessDataSourceRegistry.dataSource(datasourceId).getConnection();
                Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SHOW INDEX FROM " + quoted)) {
                while (rs.next()) {
                    String kn = rs.getString("Key_name");
                    if (kn == null || kn.isBlank()) {
                        continue;
                    }
                    String col = rs.getString("Column_name");
                    if (col == null || col.isBlank()) {
                        continue;
                    }
                    IndexBuild b = byKey.computeIfAbsent(kn.trim(), k -> new IndexBuild());
                    if (!b.seenFirstRow) {
                        b.seenFirstRow = true;
                        b.unique = rs.getInt("Non_unique") == 0;
                    }
                    b.columns.add(col.trim());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("SHOW INDEX failed: " + e.getMessage(), e);
        }
        List<MysqlIndexDefinition> out = new ArrayList<>();
        for (var e : byKey.entrySet()) {
            out.add(new MysqlIndexDefinition(e.getKey(), e.getValue().unique, List.copyOf(e.getValue().columns)));
        }
        return out;
    }

    private static final class IndexBuild {
        boolean seenFirstRow;
        boolean unique;
        final List<String> columns = new ArrayList<>();
    }

    public record TableEngineStats(Long tableRowsEstimate, String engine) {}
}

package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.ColumnMetadataResponse;
import com.sov.telegram.bot.admin.dto.TableStatsResponse;
import com.sov.telegram.bot.service.visual.DatasourceMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/datasources/{datasourceId}/metadata")
@RequiredArgsConstructor
public class AdminDatasourceMetadataController {

    private final DatasourceMetadataService datasourceMetadataService;

    @GetMapping("/tables")
    public List<String> listTables(
            @PathVariable long datasourceId, @RequestParam(name = "q", required = false) String q) {
        return datasourceMetadataService.listTables(datasourceId, q);
    }

    @GetMapping("/tables/{table}/columns")
    public List<ColumnMetadataResponse> listColumns(@PathVariable long datasourceId, @PathVariable String table) {
        return datasourceMetadataService.listColumns(datasourceId, table);
    }

    /**
     * Table row estimate ({@code information_schema.TABLE_ROWS}) and optional exact {@code COUNT(*)} with timeout.
     */
    @GetMapping("/tables/{table}/stats")
    public TableStatsResponse tableStats(
            @PathVariable long datasourceId,
            @PathVariable String table,
            @RequestParam(name = "exactCount", defaultValue = "false") boolean exactCount,
            @RequestParam(name = "exactCountTimeoutSeconds", defaultValue = "8") int exactCountTimeoutSeconds) {
        String canonical = datasourceMetadataService.resolveTableName(datasourceId, table);
        DatasourceMetadataService.TableEngineStats te = datasourceMetadataService.loadTableEngineStats(datasourceId, canonical);
        TableStatsResponse r = new TableStatsResponse();
        r.setTableRowsEstimate(te.tableRowsEstimate());
        r.setEngine(te.engine());
        if (exactCount) {
            int cap = Math.min(60, Math.max(1, exactCountTimeoutSeconds));
            try {
                r.setExactCount(datasourceMetadataService.exactTableRowCount(datasourceId, canonical, cap));
            } catch (RuntimeException ex) {
                r.setExactCount(null);
                String msg = ex.getMessage() != null ? ex.getMessage() : "exact count failed";
                r.setExactCountError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            }
        }
        return r;
    }
}

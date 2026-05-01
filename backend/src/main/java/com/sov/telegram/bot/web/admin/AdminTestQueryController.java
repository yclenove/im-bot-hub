package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.TestQueryRequest;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.api.ApiQueryConfig;
import com.sov.telegram.bot.service.api.ApiQueryConfigService;
import com.sov.telegram.bot.service.jdbc.BusinessDataSourceRegistry;
import com.sov.telegram.bot.service.jdbc.SqlTemplateQueryExecutor;
import com.sov.telegram.bot.service.visual.OrCompositionStrategy;
import com.sov.telegram.bot.service.visual.VisualQueryCompilationService;
import com.sov.telegram.bot.service.visual.VisualQueryConfig;
import com.sov.telegram.bot.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/queries")
@RequiredArgsConstructor
public class AdminTestQueryController {

    private final QueryDefinitionMapper queryDefinitionMapper;
    private final DatasourceMapper datasourceMapper;
    private final BusinessDataSourceRegistry businessDataSourceRegistry;
    private final SqlTemplateQueryExecutor sqlTemplateQueryExecutor;
    private final AuditLogService auditLogService;
    private final VisualQueryCompilationService visualQueryCompilationService;
    private final ApiQueryConfigService apiQueryConfigService;
    private final ApiDatasourceSupport apiDatasourceSupport;

    @PostMapping("/{queryId}/test")
    public List<Map<String, Object>> test(@PathVariable Long queryId, @RequestBody(required = false) TestQueryRequest req) {
        QueryDefinitionEntity qd = queryDefinitionMapper.selectById(queryId);
        if (qd == null) {
            throw new NotFoundException("query not found");
        }
        DatasourceEntity datasource = datasourceMapper.selectById(qd.getDatasourceId());
        if (datasource == null) {
            throw new NotFoundException("datasource not found");
        }
        String sqlTemplate = qd.getSqlTemplate();
        String paramSchemaJson = qd.getParamSchemaJson();
        List<String> args = req != null && req.getArgs() != null ? req.getArgs() : Collections.emptyList();
        if (ApiQueryConfigService.isApi(qd.getQueryMode())) {
            ApiQueryConfig cfg = apiQueryConfigService.parseConfig(qd.getApiConfigJson());
            List<Map<String, Object>> rows = apiDatasourceSupport.executeQuery(datasource, paramSchemaJson, cfg, args);
            auditLogService.log("TEST_QUERY", "QUERY", String.valueOf(queryId), "rows=" + rows.size());
            return rows;
        }
        if (req != null
                && req.getOrCompositionStrategy() != null
                && !req.getOrCompositionStrategy().isBlank()
                && VisualQueryCompilationService.isVisual(qd.getQueryMode())) {
            String visualJson = qd.getVisualConfigJson();
            if (req.getVisualConfigJsonOverride() != null && !req.getVisualConfigJsonOverride().isBlank()) {
                visualJson = req.getVisualConfigJsonOverride().trim();
            }
            if (visualJson != null && !visualJson.isBlank()) {
                OrCompositionStrategy strat;
                try {
                    strat = OrCompositionStrategy.valueOf(req.getOrCompositionStrategy().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                            "Invalid orCompositionStrategy: "
                                    + req.getOrCompositionStrategy()
                                    + " (expected LEGACY_OR or UNION_ALL)");
                }
                VisualQueryConfig cfg = visualQueryCompilationService.parseConfig(visualJson);
                cfg.setOrCompositionStrategy(strat);
                int maxRows = qd.getMaxRows() != null ? qd.getMaxRows() : 5;
                var gen = visualQueryCompilationService.compileAndNormalize(qd.getDatasourceId(), cfg, maxRows);
                sqlTemplate = gen.sqlTemplate();
                paramSchemaJson = gen.paramSchemaJson();
            }
        }
        Map<String, Object> params = sqlTemplateQueryExecutor.buildParamMap(args, paramSchemaJson);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() == null || ((e.getValue() instanceof String s) && s.isBlank())) {
                throw new IllegalArgumentException("Missing parameter: " + e.getKey());
            }
        }
        NamedParameterJdbcTemplate jdbc = businessDataSourceRegistry.namedJdbc(qd.getDatasourceId());
        int timeoutSec = Math.max(1, (qd.getTimeoutMs() != null ? qd.getTimeoutMs() : 5000) / 1000);
        List<Map<String, Object>> rows =
                sqlTemplateQueryExecutor.query(
                        jdbc,
                        sqlTemplate,
                        params,
                        qd.getMaxRows() != null ? qd.getMaxRows() : 5,
                        timeoutSec);
        auditLogService.log("TEST_QUERY", "QUERY", String.valueOf(queryId), "rows=" + rows.size());
        return rows;
    }
}

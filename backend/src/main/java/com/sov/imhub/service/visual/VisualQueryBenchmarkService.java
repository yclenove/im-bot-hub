package com.sov.imhub.service.visual;

import com.sov.imhub.admin.dto.VisualBenchmarkRequest;
import com.sov.imhub.admin.dto.VisualBenchmarkResponse;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.jdbc.BusinessDataSourceRegistry;
import com.sov.imhub.service.jdbc.SqlTemplateQueryExecutor;
import com.sov.imhub.service.jdbc.SqlTemplateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VisualQueryBenchmarkService {

    private static final int MAX_BENCH_ROWS = 200;
    private static final int MAX_TIMEOUT_MS = 60_000;

    private final VisualQueryCompilationService compilationService;
    private final SqlTemplateQueryExecutor sqlTemplateQueryExecutor;
    private final BusinessDataSourceRegistry businessDataSourceRegistry;
    private final SqlTemplateValidator sqlTemplateValidator;
    private final AuditLogService auditLogService;

    public VisualBenchmarkResponse run(long datasourceId, VisualBenchmarkRequest req) {
        VisualQueryConfig parsed = compilationService.parseConfig(req.getVisualConfigJson());
        if (parsed.getSearchOrColumns() == null || parsed.getSearchOrColumns().size() < 2) {
            throw new IllegalArgumentException("至少需要两个 OR 检索列才能对比 OR 与 UNION");
        }
        int maxRows = Math.min(MAX_BENCH_ROWS, Math.max(1, req.getMaxRows()));
        int timeoutSec = Math.min(MAX_TIMEOUT_MS / 1000, Math.max(1, req.getTimeoutMs() / 1000));

        String frozen = compilationService.serializeConfig(parsed);
        VisualQueryConfig orCfg = compilationService.parseConfig(frozen);
        orCfg.setOrCompositionStrategy(OrCompositionStrategy.LEGACY_OR);
        VisualQueryConfig unionCfg = compilationService.parseConfig(frozen);
        unionCfg.setOrCompositionStrategy(OrCompositionStrategy.UNION_ALL);

        VisualQuerySqlGenerator.GeneratedSql genOr =
                compilationService.compileAndNormalize(datasourceId, orCfg, maxRows);
        VisualQuerySqlGenerator.GeneratedSql genUnion =
                compilationService.compileAndNormalize(datasourceId, unionCfg, maxRows);
        sqlTemplateValidator.validate(genOr.sqlTemplate());
        sqlTemplateValidator.validate(genUnion.sqlTemplate());

        NamedParameterJdbcTemplate jdbc = businessDataSourceRegistry.namedJdbc(datasourceId);
        List<String> args = req.getArgs() != null ? req.getArgs() : List.of();

        List<Long> orSamples = new ArrayList<>();
        List<Long> unionSamples = new ArrayList<>();
        String orFirstErr = null;
        String unionFirstErr = null;
        int lastOrRows = 0;
        int lastUnionRows = 0;

        // OR -> UNION -> UNION -> OR (alternating to reduce buffer-pool order bias)
        RunOnce r0 = runOnce(jdbc, genOr.sqlTemplate(), genOr.paramSchemaJson(), args, maxRows, timeoutSec);
        recordOrUnion(orSamples, unionSamples, true, r0);
        if (r0.ok()) {
            lastOrRows = r0.rowCount();
        } else if (orFirstErr == null) {
            orFirstErr = r0.error();
        }

        RunOnce r1 = runOnce(jdbc, genUnion.sqlTemplate(), genUnion.paramSchemaJson(), args, maxRows, timeoutSec);
        recordOrUnion(orSamples, unionSamples, false, r1);
        if (r1.ok()) {
            lastUnionRows = r1.rowCount();
        } else if (unionFirstErr == null) {
            unionFirstErr = r1.error();
        }

        RunOnce r2 = runOnce(jdbc, genUnion.sqlTemplate(), genUnion.paramSchemaJson(), args, maxRows, timeoutSec);
        recordOrUnion(orSamples, unionSamples, false, r2);
        if (r2.ok()) {
            lastUnionRows = r2.rowCount();
        } else if (unionFirstErr == null) {
            unionFirstErr = r2.error();
        }

        RunOnce r3 = runOnce(jdbc, genOr.sqlTemplate(), genOr.paramSchemaJson(), args, maxRows, timeoutSec);
        recordOrUnion(orSamples, unionSamples, true, r3);
        if (r3.ok()) {
            lastOrRows = r3.rowCount();
        } else if (orFirstErr == null) {
            orFirstErr = r3.error();
        }

        VisualBenchmarkResponse resp = new VisualBenchmarkResponse();
        resp.setAlternatingRuns(true);
        resp.setNote("各策略执行 2 次取平均毫秒数；仍受 InnoDB 缓冲池与负载影响，仅供参考。请在只读从库、低峰使用。");
        resp.setLegacyOr(
                buildStrategy("LEGACY_OR", genOr.sqlTemplate(), orSamples, lastOrRows, orSamples.isEmpty() ? orFirstErr : null));
        resp.setUnionAll(
                buildStrategy(
                        "UNION_ALL",
                        genUnion.sqlTemplate(),
                        unionSamples,
                        lastUnionRows,
                        unionSamples.isEmpty() ? unionFirstErr : null));

        Long orAvg = avg(orSamples);
        Long unAvg = avg(unionSamples);
        auditLogService.log(
                "VISUAL_BENCHMARK",
                "DATASOURCE",
                String.valueOf(datasourceId),
                "orAvgMs="
                        + (orAvg != null ? orAvg : "na")
                        + ";unionAvgMs="
                        + (unAvg != null ? unAvg : "na"));
        return resp;
    }

    private static void recordOrUnion(
            List<Long> orSamples, List<Long> unionSamples, boolean isOr, RunOnce r) {
        if (!r.ok()) {
            return;
        }
        if (isOr) {
            orSamples.add(r.durationMs());
        } else {
            unionSamples.add(r.durationMs());
        }
    }

    private static Long avg(List<Long> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        long s = 0;
        for (Long v : samples) {
            s += v;
        }
        return Math.round((double) s / samples.size());
    }

    private VisualBenchmarkResponse.StrategyResult buildStrategy(
            String name,
            String sql,
            List<Long> samples,
            int lastRowCount,
            String error) {
        VisualBenchmarkResponse.StrategyResult r = new VisualBenchmarkResponse.StrategyResult();
        r.setStrategy(name);
        r.setSqlTemplate(sql);
        r.setRowCountLast(lastRowCount);
        if (error != null) {
            r.setOk(false);
            r.setError(error);
            r.setDurationMsAvg(null);
            return r;
        }
        if (samples.isEmpty()) {
            r.setOk(false);
            r.setError("no successful runs");
            r.setDurationMsAvg(null);
            return r;
        }
        r.setOk(true);
        r.setError(null);
        r.setDurationMsAvg(avg(samples));
        return r;
    }

    private RunOnce runOnce(
            NamedParameterJdbcTemplate jdbc,
            String sqlTemplate,
            String paramSchemaJson,
            List<String> args,
            int maxRows,
            int timeoutSec) {
        Map<String, Object> params = sqlTemplateQueryExecutor.buildParamMap(args, paramSchemaJson);
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() == null || (e.getValue() instanceof String s && s.isBlank())) {
                return new RunOnce(false, 0, 0, "Missing parameter: " + e.getKey());
            }
        }
        long t0 = System.nanoTime();
        try {
            List<Map<String, Object>> rows =
                    sqlTemplateQueryExecutor.query(jdbc, sqlTemplate, params, maxRows, timeoutSec);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            return new RunOnce(true, ms, rows.size(), null);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 800) {
                msg = msg.substring(0, 800);
            }
            return new RunOnce(false, ms, 0, msg);
        }
    }

    private record RunOnce(boolean ok, long durationMs, int rowCount, String error) {}
}

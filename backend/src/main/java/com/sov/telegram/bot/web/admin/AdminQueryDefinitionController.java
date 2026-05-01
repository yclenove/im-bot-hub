package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.telegram.bot.admin.dto.QueryDefinitionResponse;
import com.sov.telegram.bot.admin.dto.QueryDefinitionUpsertRequest;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.service.api.ApiQueryConfig;
import com.sov.telegram.bot.service.api.ApiQueryConfigService;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.QueryParamSchema;
import com.sov.telegram.bot.service.jdbc.SqlTemplateValidator;
import com.sov.telegram.bot.service.telegram.TelegramApiClient;
import com.sov.telegram.bot.service.visual.VisualQueryCompilationService;
import com.sov.telegram.bot.service.visual.VisualQueryConfig;
import com.sov.telegram.bot.service.visual.VisualQuerySqlGenerator;
import com.sov.telegram.bot.web.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bots/{botId}/queries")
@RequiredArgsConstructor
public class AdminQueryDefinitionController {

    private static final ObjectMapper MENU_LABEL_JSON = new ObjectMapper();

    private static final Set<String> TELEGRAM_REPLY_STYLES =
            Set.of(
                    "LIST",
                    "LIST_DOT",
                    "LIST_CODE",
                    "LIST_BLOCKQUOTE",
                    "SECTION",
                    "MONO_PRE",
                    "CODE_BLOCK",
                    "KV_SINGLE_LINE",
                    "VALUES_JOIN_SPACE",
                    "VALUES_JOIN_PIPE",
                    "TABLE_PRE");

    private final QueryDefinitionMapper queryDefinitionMapper;
    private final BotMapper botMapper;
    private final DatasourceMapper datasourceMapper;
    private final AdminDtoMapper adminDtoMapper;
    private final SqlTemplateValidator sqlTemplateValidator;
    private final AuditLogService auditLogService;
    private final VisualQueryCompilationService visualQueryCompilationService;
    private final ApiQueryConfigService apiQueryConfigService;
    private final TelegramApiClient telegramApiClient;

    @GetMapping
    public List<QueryDefinitionResponse> list(@PathVariable Long botId) {
        return queryDefinitionMapper
                .selectList(new LambdaQueryWrapper<QueryDefinitionEntity>().eq(QueryDefinitionEntity::getBotId, botId))
                .stream()
                .map(adminDtoMapper::toQueryResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{queryId}")
    public QueryDefinitionResponse get(@PathVariable Long botId, @PathVariable Long queryId) {
        QueryDefinitionEntity q =
                queryDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<QueryDefinitionEntity>()
                                .eq(QueryDefinitionEntity::getId, queryId)
                                .eq(QueryDefinitionEntity::getBotId, botId));
        if (q == null) {
            throw new NotFoundException("query not found");
        }
        return adminDtoMapper.toQueryResponse(q);
    }

    @PostMapping
    public QueryDefinitionResponse create(@PathVariable Long botId, @Valid @RequestBody QueryDefinitionUpsertRequest req) {
        String command = req.getCommand().trim().toLowerCase();
        assertCommandUniqueForBot(botId, command, null);
        CompiledQuery compiled = compileQuery(req);
        QueryDefinitionEntity e = new QueryDefinitionEntity();
        e.setBotId(botId);
        e.setDatasourceId(req.getDatasourceId());
        e.setCommand(command);
        e.setName(blankToNull(req.getName()));
        e.setTelegramMenuDescription(blankToNull(req.getTelegramMenuDescription()));
        e.setSqlTemplate(compiled.sqlTemplate());
        e.setParamSchemaJson(compiled.paramSchemaJson());
        e.setQueryMode(compiled.queryMode());
        e.setVisualConfigJson(compiled.visualConfigJson());
        e.setApiConfigJson(compiled.apiConfigJson());
        e.setTimeoutMs(req.getTimeoutMs());
        e.setMaxRows(req.getMaxRows());
        e.setEnabled(req.isEnabled());
        e.setTelegramReplyStyle(normalizeTelegramReplyStyle(req.getTelegramReplyStyle()));
        queryDefinitionMapper.insert(e);
        if (compiled.syncFieldMappings()) {
            syncFieldMappings(e.getId(), compiled);
        }
        syncTelegramMenuCommands(botId);
        auditLogService.log("CREATE", "QUERY", String.valueOf(e.getId()), e.getCommand());
        return adminDtoMapper.toQueryResponse(queryDefinitionMapper.selectById(e.getId()));
    }

    @PutMapping("/{queryId}")
    public QueryDefinitionResponse update(
            @PathVariable Long botId, @PathVariable Long queryId, @Valid @RequestBody QueryDefinitionUpsertRequest req) {
        QueryDefinitionEntity e =
                queryDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<QueryDefinitionEntity>()
                                .eq(QueryDefinitionEntity::getId, queryId)
                                .eq(QueryDefinitionEntity::getBotId, botId));
        if (e == null) {
            throw new NotFoundException("query not found");
        }
        String command = req.getCommand().trim().toLowerCase();
        assertCommandUniqueForBot(botId, command, queryId);
        CompiledQuery compiled = compileQuery(req);
        e.setDatasourceId(req.getDatasourceId());
        e.setCommand(command);
        e.setName(blankToNull(req.getName()));
        e.setTelegramMenuDescription(blankToNull(req.getTelegramMenuDescription()));
        e.setSqlTemplate(compiled.sqlTemplate());
        e.setParamSchemaJson(compiled.paramSchemaJson());
        e.setQueryMode(compiled.queryMode());
        e.setVisualConfigJson(compiled.visualConfigJson());
        e.setApiConfigJson(compiled.apiConfigJson());
        e.setTimeoutMs(req.getTimeoutMs());
        e.setMaxRows(req.getMaxRows());
        e.setEnabled(req.isEnabled());
        e.setTelegramReplyStyle(normalizeTelegramReplyStyle(req.getTelegramReplyStyle()));
        queryDefinitionMapper.updateById(e);
        if (compiled.syncFieldMappings()) {
            syncFieldMappings(queryId, compiled);
        }
        syncTelegramMenuCommands(botId);
        auditLogService.log("UPDATE", "QUERY", String.valueOf(queryId), e.getCommand());
        return adminDtoMapper.toQueryResponse(queryDefinitionMapper.selectById(queryId));
    }

    @DeleteMapping("/{queryId}")
    public void delete(@PathVariable Long botId, @PathVariable Long queryId) {
        QueryDefinitionEntity e =
                queryDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<QueryDefinitionEntity>()
                                .eq(QueryDefinitionEntity::getId, queryId)
                                .eq(QueryDefinitionEntity::getBotId, botId));
        if (e == null) {
            throw new NotFoundException("query not found");
        }
        queryDefinitionMapper.deleteById(queryId);
        syncTelegramMenuCommands(botId);
        auditLogService.log("DELETE", "QUERY", String.valueOf(queryId), null);
    }

    /**
     * 同一机器人下命令全局唯一（见 uk_bot_command）。{@code excludeQueryId} 更新时传入当前行 id，避免与自身冲突。
     */
    private String normalizeTelegramReplyStyle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LIST";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("VALUES_JOIN_CUSTOM:")) {
            String delimiter = raw.trim().substring("VALUES_JOIN_CUSTOM:".length());
            if (delimiter.isEmpty()) {
                throw new IllegalArgumentException("自定义连接符不能为空");
            }
            return "VALUES_JOIN_CUSTOM:" + delimiter;
        }
        if (!TELEGRAM_REPLY_STYLES.contains(u)) {
            throw new IllegalArgumentException(
                    "telegramReplyStyle 仅支持: LIST, LIST_DOT, LIST_CODE, LIST_BLOCKQUOTE, SECTION, MONO_PRE, CODE_BLOCK, KV_SINGLE_LINE, VALUES_JOIN_SPACE, VALUES_JOIN_PIPE, VALUES_JOIN_CUSTOM:<连接符>, TABLE_PRE");
        }
        return u;
    }

    private void assertCommandUniqueForBot(Long botId, String command, Long excludeQueryId) {
        LambdaQueryWrapper<QueryDefinitionEntity> w =
                new LambdaQueryWrapper<QueryDefinitionEntity>()
                        .eq(QueryDefinitionEntity::getBotId, botId)
                        .eq(QueryDefinitionEntity::getCommand, command);
        if (excludeQueryId != null) {
            w.ne(QueryDefinitionEntity::getId, excludeQueryId);
        }
        if (queryDefinitionMapper.selectCount(w) > 0) {
            throw new IllegalArgumentException(
                    "该机器人下已存在命令 \""
                            + command
                            + "\"。请换一个命令名，或在列表中点「编辑」修改已有那条查询。");
        }
    }

    private CompiledQuery compileQuery(QueryDefinitionUpsertRequest req) {
        if (VisualQueryCompilationService.isVisual(req.getQueryMode())) {
            VisualQueryConfig cfg = visualQueryCompilationService.parseConfig(req.getVisualConfigJson());
            int maxRows = Math.max(1, req.getMaxRows());
            VisualQuerySqlGenerator.GeneratedSql gen =
                    visualQueryCompilationService.compileAndNormalize(req.getDatasourceId(), cfg, maxRows);
            sqlTemplateValidator.validate(gen.sqlTemplate());
            String normalizedJson = visualQueryCompilationService.serializeConfig(cfg);
            String paramJson =
                    QueryParamSchema.mergeExamplesIntoParamSchema(gen.paramSchemaJson(), req.getParamSchemaJson());
            return new CompiledQuery(
                    gen.sqlTemplate(),
                    paramJson,
                    "VISUAL",
                    normalizedJson,
                    null,
                    true,
                    cfg,
                    null);
        }
        if (ApiQueryConfigService.isApi(req.getQueryMode())) {
            ApiQueryConfig cfg = apiQueryConfigService.parseConfig(req.getApiConfigJson());
            String normalizedJson = apiQueryConfigService.serializeConfig(cfg);
            String paramJson = req.getParamSchemaJson();
            if (paramJson == null || paramJson.isBlank()) {
                paramJson = "{\"params\":[]}";
            }
            return new CompiledQuery(
                    "SELECT 1",
                    paramJson,
                    "API",
                    null,
                    normalizedJson,
                    true,
                    null,
                    cfg);
        }
        if (req.getSqlTemplate() == null || req.getSqlTemplate().isBlank()) {
            throw new IllegalArgumentException("sqlTemplate is required for SQL mode");
        }
        sqlTemplateValidator.validate(req.getSqlTemplate());
        String paramJson = req.getParamSchemaJson();
        if (paramJson == null || paramJson.isBlank()) {
            paramJson = "{\"params\":[]}";
        }
        return new CompiledQuery(
                req.getSqlTemplate().trim(),
                paramJson,
                "SQL",
                null,
                null,
                false,
                null,
                null);
    }

    private void syncFieldMappings(long queryId, CompiledQuery compiled) {
        if (compiled.visualConfig() != null) {
            visualQueryCompilationService.replaceFieldMappingsFromVisual(queryId, compiled.visualConfig());
            return;
        }
        if (compiled.apiConfig() != null) {
            apiQueryConfigService.replaceFieldMappingsFromApi(queryId, compiled.apiConfig());
        }
    }

    private void syncTelegramMenuCommands(Long botId) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null || bot.getTelegramBotToken() == null || bot.getTelegramBotToken().isBlank()) {
            return;
        }
        List<TelegramApiClient.CommandSpec> commands =
                queryDefinitionMapper
                        .selectList(
                                new LambdaQueryWrapper<QueryDefinitionEntity>()
                                        .eq(QueryDefinitionEntity::getBotId, botId)
                                        .eq(QueryDefinitionEntity::getEnabled, true)
                                        .orderByAsc(QueryDefinitionEntity::getCommand))
                        .stream()
                        .map(this::toCommandSpec)
                        .toList();
        try {
            telegramApiClient.setMyCommands(bot.getTelegramBotToken(), commands);
        } catch (Exception ex) {
            auditLogService.log("WARN", "BOT", String.valueOf(botId), "同步 Telegram 菜单失败: " + ex.getMessage());
        }
    }

    private TelegramApiClient.CommandSpec toCommandSpec(QueryDefinitionEntity query) {
        String command = query.getCommand() == null ? "" : query.getCommand().trim().toLowerCase(Locale.ROOT);
        String mode = query.getQueryMode() == null ? "SQL" : query.getQueryMode().trim().toUpperCase(Locale.ROOT);
        String overrideDesc = query.getTelegramMenuDescription();
        if (overrideDesc != null && !overrideDesc.isBlank()) {
            String desc = overrideDesc.trim();
            if (desc.length() > 255) {
                desc = desc.substring(0, 255);
            }
            return new TelegramApiClient.CommandSpec(command, desc);
        }
        String datasourceName = "";
        if (query.getDatasourceId() != null) {
            var ds = datasourceMapper.selectById(query.getDatasourceId());
            if (ds != null && ds.getName() != null) {
                datasourceName = ds.getName().trim();
            }
        }
        String menuSubtitle = resolveTelegramMenuSubtitle(query, mode, datasourceName, command);
        String description = buildTelegramCommandBaseDescription(mode, menuSubtitle);
        List<String> paramNames = QueryParamSchema.parseParamNames(query.getParamSchemaJson());
        String exampleSuffix = telegramMenuExampleSuffix(command, paramNames, query.getParamSchemaJson());
        if (exampleSuffix != null && !exampleSuffix.isBlank()) {
            description = description + " · " + exampleSuffix;
        }
        if (description.length() > 255) {
            description = description.substring(0, 255);
        }
        return new TelegramApiClient.CommandSpec(command, description);
    }

    /**
     * 「查询」后展示每条查询自己的名称，避免同一数据源下多条命令菜单文案雷同。
     * 优先 {@link QueryDefinitionEntity#getName()}；否则 API：{@code apiConfigJson.name}；向导：{@code
     * visualConfigJson.table}；SQL：数据源名等。
     */
    private static String resolveTelegramMenuSubtitle(
            QueryDefinitionEntity query, String mode, String datasourceName, String command) {
        String cmd = command == null ? "" : command.trim();
        String qName = query.getName();
        if (qName != null && !qName.isBlank()) {
            return truncateTelegramMenuText(qName.trim(), 72);
        }
        if ("API".equals(mode)) {
            String apiName = readJsonTextField(query.getApiConfigJson(), "name");
            if (!apiName.isBlank()) {
                return truncateTelegramMenuText(apiName, 72);
            }
            return cmd.isBlank() ? "…" : truncateTelegramMenuText(cmd, 72);
        }
        if ("VISUAL".equals(mode)) {
            String table = readJsonTextField(query.getVisualConfigJson(), "table");
            if (!table.isBlank()) {
                return truncateTelegramMenuText(table, 72);
            }
        }
        // SQL：同一 JDBC 源下多命令时用「命令 · 数据源」区分；仅有其一则单写。
        if (!cmd.isBlank() && !datasourceName.isBlank()) {
            return truncateTelegramMenuText(cmd + " · " + datasourceName, 72);
        }
        if (!datasourceName.isBlank()) {
            return truncateTelegramMenuText(datasourceName, 72);
        }
        return cmd.isBlank() ? "…" : truncateTelegramMenuText(cmd, 72);
    }

    private static String readJsonTextField(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode root = MENU_LABEL_JSON.readTree(json.trim());
            return root.path(field).asText("").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncateTelegramMenuText(String s, int maxChars) {
        if (s == null || s.isBlank()) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    /** API 为「查询 · 名称」；SQL/向导为「模式 查询 · 名称」。{@code subtitle} 已含查询侧展示名。 */
    private static String buildTelegramCommandBaseDescription(String mode, String subtitle) {
        String sub = subtitle == null ? "" : subtitle.trim();
        if ("API".equals(mode)) {
            return sub.isBlank() ? "查询" : "查询 · " + sub;
        }
        String modeLabel =
                switch (mode) {
                    case "VISUAL" -> "向导";
                    default -> "SQL";
                };
        return sub.isBlank() ? modeLabel + " 查询" : modeLabel + " 查询 · " + sub;
    }

    private static String telegramMenuExampleSuffix(
            String command, List<String> paramNames, String paramSchemaJson) {
        if (paramNames == null || paramNames.isEmpty() || command == null || command.isBlank()) {
            return null;
        }
        List<String> exampleValues =
                QueryParamSchema.menuArgExamplesForTelegram(paramNames, paramSchemaJson);
        String cmd = "/" + command.trim().toLowerCase(Locale.ROOT);
        return "示例: " + cmd + " " + String.join(" ", exampleValues);
    }

    private record CompiledQuery(
            String sqlTemplate,
            String paramSchemaJson,
            String queryMode,
            String visualConfigJson,
            String apiConfigJson,
            boolean syncFieldMappings,
            VisualQueryConfig visualConfig,
            ApiQueryConfig apiConfig) {}

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}

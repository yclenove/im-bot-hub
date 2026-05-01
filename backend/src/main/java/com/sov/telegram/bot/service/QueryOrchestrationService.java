package com.sov.telegram.bot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.FieldMappingEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.im.ImPlatform;
import com.sov.telegram.bot.im.InboundCommandContext;
import com.sov.telegram.bot.im.OutboundMessenger;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.mapper.FieldMappingMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.mapper.UserAllowlistMapper;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.api.ApiQueryConfig;
import com.sov.telegram.bot.service.api.ApiQueryConfigService;
import com.sov.telegram.bot.service.jdbc.BusinessDataSourceRegistry;
import com.sov.telegram.bot.service.jdbc.SqlTemplateQueryExecutor;
import com.sov.telegram.bot.service.telegram.TelegramChatAccessService;
import com.sov.telegram.bot.service.telegram.TelegramCommandParser;
import com.sov.telegram.bot.service.telegram.WebhookRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 斜杠命令 → 鉴权 → 限流 → 查库 → 渲染 → 由 {@link OutboundMessenger} 发出；与具体 IM 解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryOrchestrationService {

    private final BotMapper botMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final DatasourceMapper datasourceMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final UserAllowlistMapper userAllowlistMapper;
    private final BusinessDataSourceRegistry businessDataSourceRegistry;
    private final SqlTemplateQueryExecutor sqlTemplateQueryExecutor;
    private final ApiQueryConfigService apiQueryConfigService;
    private final ApiDatasourceSupport apiDatasourceSupport;
    private final FieldRenderService fieldRenderService;
    private final WebhookRateLimiter webhookRateLimiter;
    private final TelegramChatAccessService telegramChatAccessService;
    private final TelegramQueryLogService telegramQueryLogService;

    public void dispatch(InboundCommandContext ctx, OutboundMessenger messenger) {
        Map<String, String> previousMdc = LogTraceContext.snapshot();
        String logCommand = "";
        Long logQueryId = null;
        long logStarted = 0L;
        try {
            TelegramCommandParser.Parsed parsed = ctx.parsed();
            if (parsed.command().isEmpty()) {
                return;
            }
            logCommand = parsed.command();
            LogTraceContext.putCommand(logCommand);
            logStarted = System.currentTimeMillis();
            log.info(
                    "dispatch start botId={} platform={} command={} argCount={} externalUserId={} externalChatId={}",
                    ctx.botId(),
                    ctx.platform(),
                    logCommand,
                    parsed.args() == null ? 0 : parsed.args().size(),
                    safeId(ctx.externalUserId()),
                    safeId(ctx.externalChatId()));

            Bot bot = botMapper.selectById(ctx.botId());
            if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
                log.warn("dispatch skip bot unavailable botId={} command={}", ctx.botId(), logCommand);
                return;
            }

            if (ctx.platform() == ImPlatform.TELEGRAM) {
                if (ctx.telegramMessage() != null && !telegramChatAccessService.allows(bot, ctx.telegramMessage())) {
                    log.warn("dispatch blocked by chat access botId={} command={} chatId={}", ctx.botId(), logCommand, ctx.telegramChatId());
                    return;
                }
            }

            if (!webhookRateLimiter.allowKey(rateLimitKey(ctx))) {
                log.warn("dispatch rate limited botId={} command={} platform={} chatId={}", ctx.botId(), logCommand, ctx.platform(), safeId(String.valueOf(ctx.telegramChatId())));
                messenger.sendRateLimited();
                telegramQueryLogService.insertImSafe(
                        ctx.botId(),
                        ctx.platform(),
                        ctx.telegramUserId(),
                        ctx.telegramChatId(),
                        ctx.externalUserId(),
                        ctx.externalChatId(),
                        logCommand,
                        null,
                        false,
                        TelegramQueryLogService.KIND_RATE_LIMIT,
                        logStarted,
                        null);
                return;
            }

            if (!isAllowlisted(ctx)) {
                log.warn("dispatch blocked by allowlist botId={} command={} userId={} chatId={}", ctx.botId(), logCommand, safeId(String.valueOf(ctx.telegramUserId())), safeId(String.valueOf(ctx.telegramChatId())));
                messenger.sendNotAllowed();
                telegramQueryLogService.insertImSafe(
                        ctx.botId(),
                        ctx.platform(),
                        ctx.telegramUserId(),
                        ctx.telegramChatId(),
                        ctx.externalUserId(),
                        ctx.externalChatId(),
                        logCommand,
                        null,
                        false,
                        TelegramQueryLogService.KIND_NOT_ALLOWED,
                        logStarted,
                        null);
                return;
            }

            QueryDefinitionEntity qd =
                    queryDefinitionMapper.selectOne(
                            new LambdaQueryWrapper<QueryDefinitionEntity>()
                                    .eq(QueryDefinitionEntity::getBotId, ctx.botId())
                                    .eq(QueryDefinitionEntity::getCommand, parsed.command())
                                    .eq(QueryDefinitionEntity::getEnabled, true));
            if (qd == null) {
                log.warn("dispatch command not found botId={} command={}", ctx.botId(), logCommand);
                if ("help".equals(parsed.command()) || "start".equals(parsed.command())) {
                    messenger.sendHelp(buildHelpHtml(ctx.botId()), buildHelpPlain(ctx.botId()));
                    telegramQueryLogService.insertImSafe(
                            ctx.botId(),
                            ctx.platform(),
                            ctx.telegramUserId(),
                            ctx.telegramChatId(),
                            ctx.externalUserId(),
                            ctx.externalChatId(),
                            logCommand,
                            null,
                            true,
                            TelegramQueryLogService.KIND_HELP,
                            logStarted,
                            null);
                    return;
                }
                messenger.sendUnknownCommand(parsed.command());
                telegramQueryLogService.insertImSafe(
                        ctx.botId(),
                        ctx.platform(),
                        ctx.telegramUserId(),
                        ctx.telegramChatId(),
                        ctx.externalUserId(),
                        ctx.externalChatId(),
                        logCommand,
                        null,
                        false,
                        TelegramQueryLogService.KIND_UNKNOWN_COMMAND,
                        logStarted,
                        null);
                return;
            }
            logQueryId = qd.getId();
            LogTraceContext.putQueryId(logQueryId);
            log.info(
                    "dispatch query matched botId={} queryId={} command={} queryMode={} datasourceId={} timeoutMs={} maxRows={}",
                    ctx.botId(),
                    qd.getId(),
                    logCommand,
                    qd.getQueryMode(),
                    qd.getDatasourceId(),
                    qd.getTimeoutMs(),
                    qd.getMaxRows());

            DatasourceEntity datasource = datasourceMapper.selectById(qd.getDatasourceId());
            if (datasource == null) {
                throw new IllegalArgumentException("datasource not found");
            }
            log.info(
                    "dispatch datasource ready queryId={} datasourceId={} datasourceName={} sourceType={}",
                    qd.getId(),
                    datasource.getId(),
                    datasource.getName(),
                    datasource.getSourceType());

            Map<String, Object> params = null;
            String missing;
            if (ApiQueryConfigService.isApi(qd.getQueryMode())) {
                // API 只校验 paramSchemaJson 列出的参数；不可沿用 SQL 在空 schema 时默认的 orderNo。
                missing =
                        QueryParamSchema.firstMissingPositionalValue(
                                QueryParamSchema.parseParamNames(qd.getParamSchemaJson()), parsed.args());
            } else {
                params = sqlTemplateQueryExecutor.buildParamMap(parsed.args(), qd.getParamSchemaJson());
                missing = firstMissingParam(params);
            }
            if (missing != null) {
                log.warn(
                        "dispatch missing param botId={} queryId={} command={} missing={} argCount={} paramNames={}",
                        ctx.botId(),
                        qd.getId(),
                        logCommand,
                        missing,
                        parsed.args() == null ? 0 : parsed.args().size(),
                        QueryParamSchema.parseParamNames(qd.getParamSchemaJson()));
                if (isAllArgsBlank(parsed)) {
                    messenger.sendParamUsageReminder(
                            buildParamUsageHintTelegramHtml(qd, logCommand),
                            buildParamUsageHintPlain(qd, logCommand));
                    telegramQueryLogService.insertImSafe(
                            ctx.botId(),
                            ctx.platform(),
                            ctx.telegramUserId(),
                            ctx.telegramChatId(),
                            ctx.externalUserId(),
                            ctx.externalChatId(),
                            logCommand,
                            logQueryId,
                            false,
                            TelegramQueryLogService.KIND_MISSING_PARAM,
                            logStarted,
                            "usage_hint;missing=" + missing);
                } else {
                    messenger.sendMissingParam(missing);
                    telegramQueryLogService.insertImSafe(
                            ctx.botId(),
                            ctx.platform(),
                            ctx.telegramUserId(),
                            ctx.telegramChatId(),
                            ctx.externalUserId(),
                            ctx.externalChatId(),
                            logCommand,
                            logQueryId,
                            false,
                            TelegramQueryLogService.KIND_MISSING_PARAM,
                            logStarted,
                            "missing=" + missing);
                }
                return;
            }

            List<Map<String, Object>> rows;
            if (ApiQueryConfigService.isApi(qd.getQueryMode())) {
                ApiQueryConfig cfg = apiQueryConfigService.parseConfig(qd.getApiConfigJson());
                log.info(
                        "dispatch api query start botId={} queryId={} command={} datasourceId={} method={} path={} resultPointer={} argCount={} localLimitParam={} localLimitFixed={}",
                        ctx.botId(),
                        qd.getId(),
                        logCommand,
                        datasource.getId(),
                        cfg.getMethod(),
                        cfg.getPath(),
                        cfg.getResponseRootPointer(),
                        parsed.args() == null ? 0 : parsed.args().size(),
                        cfg.getLocalResultLimitParamName(),
                        cfg.getLocalResultLimit());
                rows = apiDatasourceSupport.executeQuery(datasource, qd.getParamSchemaJson(), cfg, parsed.args());
            } else {
                NamedParameterJdbcTemplate jdbc = businessDataSourceRegistry.namedJdbc(qd.getDatasourceId());
                int timeoutSec = Math.max(1, (qd.getTimeoutMs() != null ? qd.getTimeoutMs() : 5000) / 1000);
                log.info(
                        "dispatch sql query start botId={} queryId={} command={} datasourceId={} timeoutSec={} maxRows={} paramNames={}",
                        ctx.botId(),
                        qd.getId(),
                        logCommand,
                        datasource.getId(),
                        timeoutSec,
                        qd.getMaxRows() != null ? qd.getMaxRows() : 1,
                        params == null ? List.of() : params.keySet());
                rows = sqlTemplateQueryExecutor.query(
                        jdbc, qd.getSqlTemplate(), params, qd.getMaxRows() != null ? qd.getMaxRows() : 1, timeoutSec);
            }
            log.info(
                    "dispatch query rows ready botId={} queryId={} command={} rowCount={}",
                    ctx.botId(),
                    qd.getId(),
                    logCommand,
                    rows == null ? 0 : rows.size());

            List<FieldMappingEntity> fms =
                    fieldMappingMapper.selectList(
                            new LambdaQueryWrapper<FieldMappingEntity>()
                                    .eq(FieldMappingEntity::getQueryId, qd.getId()));

            String style =
                    qd.getTelegramReplyStyle() != null && !qd.getTelegramReplyStyle().isBlank()
                            ? qd.getTelegramReplyStyle()
                            : "LIST";
            String htmlBody = renderQueryRowsTelegramHtml(fms, rows, style);
            String plainBody = fieldRenderService.renderPlainMultiRow(fms, rows, style);
            messenger.sendQueryResult(htmlBody, plainBody);
            log.info(
                    "dispatch result sent botId={} queryId={} command={} rowCount={} fieldMappingCount={} style={} durationMs={}",
                    ctx.botId(),
                    qd.getId(),
                    logCommand,
                    rows == null ? 0 : rows.size(),
                    fms == null ? 0 : fms.size(),
                    style,
                    System.currentTimeMillis() - logStarted);
            List<String> pnames = new ArrayList<>(QueryParamSchema.parseParamNames(qd.getParamSchemaJson()));
            telegramQueryLogService.insertImSafe(
                    ctx.botId(),
                    ctx.platform(),
                    ctx.telegramUserId(),
                    ctx.telegramChatId(),
                    ctx.externalUserId(),
                    ctx.externalChatId(),
                    logCommand,
                    logQueryId,
                    true,
                    TelegramQueryLogService.KIND_SUCCESS,
                    logStarted,
                    "rows=" + rows.size() + "; " + TelegramQueryLogService.paramNamesOnly(pnames));
        } catch (Exception e) {
            log.warn(
                    "dispatch failed botId={} platform={} command={} queryId={} durationMs={} errorType={} message={}",
                    ctx.botId(),
                    ctx.platform(),
                    logCommand,
                    logQueryId,
                    logStarted > 0 ? (System.currentTimeMillis() - logStarted) : -1,
                    e.getClass().getSimpleName(),
                    safeMessage(e),
                    e);
            if (!logCommand.isEmpty()) {
                telegramQueryLogService.insertImSafe(
                        ctx.botId(),
                        ctx.platform(),
                        ctx.telegramUserId(),
                        ctx.telegramChatId(),
                        ctx.externalUserId(),
                        ctx.externalChatId(),
                        logCommand,
                        logQueryId,
                        false,
                        TelegramQueryLogService.KIND_QUERY_FAILED,
                        logStarted > 0 ? logStarted : System.currentTimeMillis(),
                        e.getClass().getSimpleName());
            }
            try {
                messenger.sendQueryFailed();
            } catch (Exception ignored) {
            }
        } finally {
            LogTraceContext.restore(previousMdc);
        }
    }

    private static String rateLimitKey(InboundCommandContext ctx) {
        if (ctx.platform() == ImPlatform.TELEGRAM) {
            return "tg:" + ctx.botId() + ":" + ctx.telegramChatId();
        }
        if (ctx.platform() == ImPlatform.DINGTALK) {
            String chat = ctx.externalChatId();
            if (chat != null && !chat.isBlank()) {
                return "ding:" + ctx.botId() + ":" + ctx.channelId() + ":" + chat;
            }
            return "ding:" + ctx.botId() + ":" + ctx.channelId() + ":" + ctx.externalUserId();
        }
        if (ctx.platform() == ImPlatform.WEWORK) {
            return "wxcp:" + ctx.botId() + ":" + ctx.channelId() + ":" + ctx.externalUserId();
        }
        return ctx.platform().wireName() + ":" + ctx.botId() + ":" + ctx.channelId() + ":" + ctx.externalUserId();
    }

    private boolean isAllowlisted(InboundCommandContext ctx) {
        if (ctx.platform() != ImPlatform.TELEGRAM) {
            return true;
        }
        return userAllowlistMapper.isTelegramUserAllowed(ctx.botId(), ctx.telegramUserId()) == 1;
    }

    private String firstMissingParam(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null || (entry.getValue() instanceof String s && s.isBlank())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 用户从 Telegram 命令菜单点选时往往只发出 {@code /cmd} 无参数；与「少填了中间某段参数」区分对待。
     */
    private static boolean isAllArgsBlank(TelegramCommandParser.Parsed parsed) {
        List<String> a = parsed.args();
        if (a == null || a.isEmpty()) {
            return true;
        }
        for (String s : a) {
            if (s != null && !s.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String safeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw.length() <= 6 ? raw : raw.substring(0, 3) + "***" + raw.substring(raw.length() - 3);
    }

    private static String safeMessage(Exception e) {
        String msg = e == null ? "" : e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "-";
        }
        return msg.length() <= 240 ? msg : msg.substring(0, 240) + "...";
    }

    private String buildParamUsageHintTelegramHtml(QueryDefinitionEntity qd, String command) {
        List<String> names = new ArrayList<>(QueryParamSchema.parseParamNames(qd.getParamSchemaJson()));
        if (names.isEmpty() && !ApiQueryConfigService.isApi(qd.getQueryMode())) {
            names.add("orderNo");
        }
        List<String> examples = QueryParamSchema.menuArgExamplesForTelegram(names, qd.getParamSchemaJson());
        String cmd = "/" + escapeHtml(command.trim().toLowerCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder();
        sb.append("<b>用法</b>\n");
        sb.append("若只选了命令菜单，请在输入框<strong>同一条消息里</strong>补全参数，例如：\n<code>")
                .append(cmd);
        for (String ex : examples) {
            sb.append(" ").append(escapeHtml(ex));
        }
        sb.append("</code>");
        if (!names.isEmpty()) {
            sb.append("\n\n参数顺序：");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sb.append(" → ");
                }
                sb.append("<code>").append(escapeHtml(names.get(i))).append("</code>");
            }
        }
        return sb.toString();
    }

    private String buildParamUsageHintPlain(QueryDefinitionEntity qd, String command) {
        List<String> names = new ArrayList<>(QueryParamSchema.parseParamNames(qd.getParamSchemaJson()));
        if (names.isEmpty() && !ApiQueryConfigService.isApi(qd.getQueryMode())) {
            names.add("orderNo");
        }
        List<String> examples = QueryParamSchema.menuArgExamplesForTelegram(names, qd.getParamSchemaJson());
        String cmd = "/" + command.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("用法\n");
        sb.append("若只选了命令菜单，请在同一条消息里补全参数，例如：\n");
        sb.append(cmd);
        for (String ex : examples) {
            sb.append(" ").append(ex);
        }
        if (!names.isEmpty()) {
            sb.append("\n\n参数顺序：").append(String.join(" → ", names));
        }
        return sb.toString();
    }

    private String renderQueryRowsTelegramHtml(
            List<FieldMappingEntity> fms, List<Map<String, Object>> rows, String style) {
        return fieldRenderService.renderMarkdownHtmlMultiRow(fms, rows, style);
    }

    private String buildHelpHtml(long botId) {
        List<QueryDefinitionEntity> defs =
                queryDefinitionMapper.selectList(
                        new LambdaQueryWrapper<QueryDefinitionEntity>()
                                .eq(QueryDefinitionEntity::getBotId, botId)
                                .eq(QueryDefinitionEntity::getEnabled, true)
                                .orderByAsc(QueryDefinitionEntity::getCommand));
        StringBuilder sb = new StringBuilder();
        sb.append("<b>使用说明</b>\n");
        sb.append("发送 <code>/命令 参数1 参数2…</code> 进行查询。\n\n");
        if (defs.isEmpty()) {
            sb.append("<i>暂无已启用的查询命令，请在管理端配置。</i>");
            return sb.toString();
        }
        for (QueryDefinitionEntity d : defs) {
            List<String> names = new ArrayList<>(QueryParamSchema.parseParamNames(d.getParamSchemaJson()));
            if (names.isEmpty() && !ApiQueryConfigService.isApi(d.getQueryMode())) {
                names.add("orderNo");
            }
            sb.append("• <code>/").append(escapeHtml(d.getCommand())).append("</code>");
            for (String n : names) {
                sb.append(" <code>").append(escapeHtml(n)).append("</code>");
            }
            sb.append("\n");
        }
        sb.append("\n<i>发送 /help 或 /start 可再次查看本列表。</i>");
        return sb.toString();
    }

    private String buildHelpPlain(long botId) {
        List<QueryDefinitionEntity> defs =
                queryDefinitionMapper.selectList(
                        new LambdaQueryWrapper<QueryDefinitionEntity>()
                                .eq(QueryDefinitionEntity::getBotId, botId)
                                .eq(QueryDefinitionEntity::getEnabled, true)
                                .orderByAsc(QueryDefinitionEntity::getCommand));
        StringBuilder sb = new StringBuilder();
        sb.append("使用说明\n");
        sb.append("发送 /命令 参数1 参数2… 进行查询。\n\n");
        if (defs.isEmpty()) {
            sb.append("暂无已启用的查询命令，请在管理端配置。");
            return sb.toString();
        }
        for (QueryDefinitionEntity d : defs) {
            List<String> names = new ArrayList<>(QueryParamSchema.parseParamNames(d.getParamSchemaJson()));
            if (names.isEmpty() && !ApiQueryConfigService.isApi(d.getQueryMode())) {
                names.add("orderNo");
            }
            sb.append("• /").append(d.getCommand());
            for (String n : names) {
                sb.append(" ").append(n);
            }
            sb.append("\n");
        }
        sb.append("\n发送 /help 或 /start 可再次查看本列表。");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

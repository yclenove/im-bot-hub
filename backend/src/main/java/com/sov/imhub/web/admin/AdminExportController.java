package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据导出导入 API：支持机器人配置和查询定义的 JSON 导出/导入。
 *
 * <p>导出的 JSON 文件可用于备份、迁移或批量配置。</p>
 * <p>导入为追加模式，不覆盖已有数据。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class AdminExportController {

    private final BotMapper botMapper;
    private final BotChannelMapper botChannelMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * 导出所有机器人配置（含渠道，不含凭证明文）。
     */
    @GetMapping("/bots")
    public ResponseEntity<byte[]> exportBots() {
        List<Map<String, Object>> bots = botMapper.selectList(new LambdaQueryWrapper<Bot>().orderByAsc(Bot::getId))
                .stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("name", b.getName());
                    m.put("enabled", Boolean.TRUE.equals(b.getEnabled()));
                    // 导出关联渠道（脱敏）
                    List<Map<String, Object>> channels = botChannelMapper.selectList(
                            new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, b.getId()))
                            .stream()
                            .map(c -> {
                                Map<String, Object> cm = new LinkedHashMap<>();
                                cm.put("platform", c.getPlatform());
                                cm.put("name", c.getName());
                                cm.put("enabled", Boolean.TRUE.equals(c.getEnabled()));
                                cm.put("chatScope", c.getChatScope());
                                return cm;
                            })
                            .collect(Collectors.toList());
                    m.put("channels", channels);
                    return m;
                })
                .collect(Collectors.toList());

        return buildJsonResponse("bots-export.json", bots);
    }

    /**
     * 导出所有查询定义。
     */
    @GetMapping("/queries")
    public ResponseEntity<byte[]> exportQueries() {
        List<Map<String, Object>> queries = queryDefinitionMapper.selectList(
                new LambdaQueryWrapper<QueryDefinitionEntity>().orderByAsc(QueryDefinitionEntity::getId))
                .stream()
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("botId", q.getBotId());
                    m.put("datasourceId", q.getDatasourceId());
                    m.put("command", q.getCommand());
                    m.put("name", q.getName());
                    m.put("queryMode", q.getQueryMode());
                    m.put("sqlTemplate", q.getSqlTemplate());
                    m.put("visualConfigJson", q.getVisualConfigJson());
                    m.put("apiConfigJson", q.getApiConfigJson());
                    m.put("paramSchemaJson", q.getParamSchemaJson());
                    m.put("timeoutMs", q.getTimeoutMs());
                    m.put("maxRows", q.getMaxRows());
                    m.put("enabled", Boolean.TRUE.equals(q.getEnabled()));
                    m.put("telegramReplyStyle", q.getTelegramReplyStyle());
                    m.put("channelScopeJson", q.getChannelScopeJson());
                    return m;
                })
                .collect(Collectors.toList());

        auditLogService.log("EXPORT", "QUERY_DEFINITION", "ALL", "导出 " + queries.size() + " 条查询定义");
        return buildJsonResponse("queries-export.json", queries);
    }

    /**
     * 导入查询定义（追加模式，不覆盖已有）。
     */
    @PostMapping("/import/queries")
    public Map<String, Object> importQueries(@RequestBody List<Map<String, Object>> items) {
        int created = 0;
        int skipped = 0;
        for (Map<String, Object> item : items) {
            try {
                QueryDefinitionEntity q = new QueryDefinitionEntity();
                q.setBotId(toLong(item.get("botId")));
                q.setDatasourceId(toLong(item.get("datasourceId")));
                q.setCommand((String) item.get("command"));
                q.setName((String) item.get("name"));
                q.setQueryMode((String) item.get("queryMode"));
                q.setSqlTemplate((String) item.get("sqlTemplate"));
                q.setVisualConfigJson((String) item.get("visualConfigJson"));
                q.setApiConfigJson((String) item.get("apiConfigJson"));
                q.setParamSchemaJson((String) item.get("paramSchemaJson"));
                q.setTimeoutMs(toInt(item.get("timeoutMs")));
                q.setMaxRows(toInt(item.get("maxRows")));
                q.setEnabled(toBool(item.get("enabled")));
                q.setTelegramReplyStyle((String) item.get("telegramReplyStyle"));
                q.setChannelScopeJson((String) item.get("channelScopeJson"));
                q.setCreatedAt(LocalDateTime.now());
                queryDefinitionMapper.insert(q);
                created++;
            } catch (Exception e) {
                log.warn("Import query failed: {}", e.getMessage());
                skipped++;
            }
        }
        auditLogService.log("IMPORT", "QUERY_DEFINITION", "ALL", "导入 " + created + " 条，跳过 " + skipped + " 条");
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        return result;
    }

    private ResponseEntity<byte[]> buildJsonResponse(String filename, Object data) {
        try {
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private static Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(o.toString());
    }
}

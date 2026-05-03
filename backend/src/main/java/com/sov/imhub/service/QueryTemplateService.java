package com.sov.imhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.admin.dto.QueryTemplateResponse;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.domain.QueryTemplateEntity;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.mapper.QueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询模板服务：模板市场 CRUD + 一键导入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryTemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QueryTemplateMapper templateMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final AuditLogService auditLogService;

    /**
     * 获取所有启用的模板。
     */
    public List<QueryTemplateResponse> listEnabled(String category) {
        LambdaQueryWrapper<QueryTemplateEntity> w = new LambdaQueryWrapper<QueryTemplateEntity>()
                .eq(QueryTemplateEntity::getEnabled, true)
                .orderByDesc(QueryTemplateEntity::getDownloads);
        if (category != null && !category.isBlank()) {
            w.eq(QueryTemplateEntity::getCategory, category.trim());
        }
        return templateMapper.selectList(w).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个模板。
     */
    public QueryTemplateResponse getById(Long id) {
        QueryTemplateEntity e = templateMapper.selectById(id);
        if (e == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        return toResponse(e);
    }

    /**
     * 一键导入模板到指定机器人。
     *
     * @param templateId 模板 ID
     * @param botId      目标机器人 ID
     * @param datasourceId 目标数据源 ID
     * @return 创建的查询定义 ID
     */
    @Transactional
    public Long importTemplate(Long templateId, Long botId, Long datasourceId) {
        QueryTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }

        try {
            Map<String, Object> config = MAPPER.readValue(template.getConfigJson(), Map.class);

            QueryDefinitionEntity qd = new QueryDefinitionEntity();
            qd.setBotId(botId);
            qd.setDatasourceId(datasourceId);
            qd.setCommand((String) config.get("command"));
            qd.setName(template.getName());
            qd.setQueryMode((String) config.getOrDefault("queryMode", "SQL"));
            qd.setSqlTemplate((String) config.get("sqlTemplate"));
            qd.setApiConfigJson((String) config.get("apiConfigJson"));
            qd.setParamSchemaJson((String) config.get("paramSchemaJson"));
            qd.setTimeoutMs((Integer) config.getOrDefault("timeoutMs", 5000));
            qd.setMaxRows((Integer) config.getOrDefault("maxRows", 1));
            qd.setEnabled(true);

            queryDefinitionMapper.insert(qd);

            // 更新下载次数
            template.setDownloads(template.getDownloads() + 1);
            templateMapper.updateById(template);

            auditLogService.log("IMPORT_TEMPLATE", "QUERY", String.valueOf(qd.getId()),
                    "从模板 " + template.getName() + " 导入");

            log.info("import template success templateId={} botId={} queryId={}", templateId, botId, qd.getId());
            return qd.getId();
        } catch (Exception e) {
            log.warn("import template failed templateId={}: {}", templateId, e.getMessage());
            throw new IllegalArgumentException("模板配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 创建自定义模板。
     */
    public QueryTemplateResponse create(String name, String category, String description,
                                         String configJson, String author) {
        QueryTemplateEntity e = new QueryTemplateEntity();
        e.setName(name);
        e.setCategory(category != null ? category : "custom");
        e.setDescription(description);
        e.setConfigJson(configJson);
        e.setVersion(1);
        e.setAuthor(author);
        e.setDownloads(0);
        e.setEnabled(true);
        templateMapper.insert(e);

        auditLogService.log("CREATE", "TEMPLATE", String.valueOf(e.getId()), name);
        return toResponse(templateMapper.selectById(e.getId()));
    }

    private QueryTemplateResponse toResponse(QueryTemplateEntity e) {
        return QueryTemplateResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .category(e.getCategory())
                .description(e.getDescription())
                .configJson(e.getConfigJson())
                .version(e.getVersion())
                .author(e.getAuthor())
                .downloads(e.getDownloads())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .createdAt(e.getCreatedAt())
                .build();
    }
}

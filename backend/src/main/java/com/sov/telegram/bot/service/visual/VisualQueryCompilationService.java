package com.sov.telegram.bot.service.visual;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.telegram.bot.domain.FieldMappingEntity;
import com.sov.telegram.bot.domain.QueryMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.telegram.bot.mapper.FieldMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses visual JSON, validates against JDBC metadata, generates SQL, and optionally syncs field mappings.
 */
@Service
@RequiredArgsConstructor
public class VisualQueryCompilationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VisualQuerySqlGenerator sqlGenerator;
    private final DatasourceMetadataService metadataService;
    private final FieldMappingMapper fieldMappingMapper;

    public VisualQueryConfig parseConfig(String visualConfigJson) {
        if (visualConfigJson == null || visualConfigJson.isBlank()) {
            throw new IllegalArgumentException("visualConfigJson is required for VISUAL mode");
        }
        try {
            return MAPPER.readValue(visualConfigJson.trim(), VisualQueryConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid visualConfigJson: " + e.getOriginalMessage());
        }
    }

    /**
     * Validates metadata, normalizes table name, compiles SQL and param schema, returns normalized JSON.
     */
    public VisualQuerySqlGenerator.GeneratedSql compileAndNormalize(long datasourceId, VisualQueryConfig config, int maxRows) {
        String canonical = metadataService.resolveTableName(datasourceId, config.getTable());
        config.setTable(canonical);
        metadataService.validateAgainstMetadata(datasourceId, config);
        return sqlGenerator.generate(config, maxRows);
    }

    public String serializeConfig(VisualQueryConfig config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void replaceFieldMappingsFromVisual(long queryId, VisualQueryConfig config) {
        List<FieldMappingEntity> previous =
                fieldMappingMapper.selectList(
                        new LambdaQueryWrapper<FieldMappingEntity>().eq(FieldMappingEntity::getQueryId, queryId));
        Map<String, String> pipelineByColumn = new HashMap<>();
        for (FieldMappingEntity p : previous) {
            if (p.getColumnName() == null || p.getDisplayPipelineJson() == null || p.getDisplayPipelineJson().isBlank()) {
                continue;
            }
            pipelineByColumn.put(p.getColumnName().trim().toLowerCase(Locale.ROOT), p.getDisplayPipelineJson());
        }
        fieldMappingMapper.delete(new LambdaQueryWrapper<FieldMappingEntity>().eq(FieldMappingEntity::getQueryId, queryId));
        int order = 0;
        for (VisualSelectColumn sc : config.getSelect()) {
            FieldMappingEntity e = new FieldMappingEntity();
            String col = sc.getColumn().trim();
            e.setQueryId(queryId);
            e.setColumnName(col);
            e.setLabel(sc.getLabel() != null && !sc.getLabel().isBlank() ? sc.getLabel().trim() : col);
            e.setSortOrder(order++);
            e.setMaskType("NONE");
            if (sc.getEnumLabels() != null && !sc.getEnumLabels().isEmpty()) {
                try {
                    e.setFormatType("ENUM_JSON:" + MAPPER.writeValueAsString(sc.getEnumLabels()));
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException(ex);
                }
            } else {
                e.setFormatType(null);
            }
            e.setDisplayPipelineJson(pipelineByColumn.get(col.toLowerCase(Locale.ROOT)));
            fieldMappingMapper.insert(e);
        }
    }

    public static boolean isVisual(String queryMode) {
        return QueryMode.VISUAL.name().equalsIgnoreCase(queryMode != null ? queryMode.trim() : "");
    }
}

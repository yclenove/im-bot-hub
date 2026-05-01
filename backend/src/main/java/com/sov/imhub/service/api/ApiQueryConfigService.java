package com.sov.imhub.service.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.FieldMappingEntity;
import com.sov.imhub.domain.QueryMode;
import com.sov.imhub.mapper.FieldMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApiQueryConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FieldMappingMapper fieldMappingMapper;

    public ApiQueryConfig parseConfig(String apiConfigJson) {
        if (apiConfigJson == null || apiConfigJson.isBlank()) {
            throw new IllegalArgumentException("apiConfigJson is required for API mode");
        }
        try {
            ApiQueryConfig cfg = MAPPER.readValue(apiConfigJson.trim(), ApiQueryConfig.class);
            validateConfig(cfg);
            return cfg;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid apiConfigJson: " + e.getOriginalMessage());
        }
    }

    public String serializeConfig(ApiQueryConfig config) {
        validateConfig(config);
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void validateConfig(ApiQueryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("api config is required");
        }
        if (config.getPath() == null || config.getPath().isBlank()) {
            throw new IllegalArgumentException("API path is required");
        }
        if (config.getMethod() == null || config.getMethod().isBlank()) {
            config.setMethod("GET");
        }
        if (config.getLocalResultLimit() != null && config.getLocalResultLimit() <= 0) {
            throw new IllegalArgumentException("localResultLimit 必须大于 0");
        }
        if (config.getOutputs() == null || config.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个返回字段");
        }
        for (ApiQueryConfig.ApiOutputField output : config.getOutputs()) {
            if (output.getKey() == null || output.getKey().isBlank()) {
                throw new IllegalArgumentException("输出字段 key 不能为空");
            }
            if (output.getJsonPointer() == null || output.getJsonPointer().isBlank()) {
                throw new IllegalArgumentException("输出字段 jsonPointer 不能为空");
            }
            if (output.getLabel() == null || output.getLabel().isBlank()) {
                output.setLabel(output.getKey());
            }
        }
    }

    @Transactional
    public void replaceFieldMappingsFromApi(long queryId, ApiQueryConfig config) {
        List<FieldMappingEntity> previous =
                fieldMappingMapper.selectList(
                        new LambdaQueryWrapper<FieldMappingEntity>().eq(FieldMappingEntity::getQueryId, queryId));
        Map<String, FieldMappingEntity> previousByColumn = new HashMap<>();
        for (FieldMappingEntity item : previous) {
            if (item.getColumnName() == null || item.getColumnName().isBlank()) {
                continue;
            }
            previousByColumn.put(item.getColumnName().trim().toLowerCase(Locale.ROOT), item);
        }
        fieldMappingMapper.delete(new LambdaQueryWrapper<FieldMappingEntity>().eq(FieldMappingEntity::getQueryId, queryId));
        int sort = 0;
        for (ApiQueryConfig.ApiOutputField output : config.getOutputs()) {
            String columnName = output.getKey().trim();
            FieldMappingEntity previousField = previousByColumn.get(columnName.toLowerCase(Locale.ROOT));
            FieldMappingEntity entity = new FieldMappingEntity();
            entity.setQueryId(queryId);
            entity.setColumnName(columnName);
            entity.setLabel(output.getLabel().trim());
            entity.setSortOrder(output.getSortOrder() != null ? output.getSortOrder() : sort);
            entity.setMaskType(output.getMaskType() != null && !output.getMaskType().isBlank() ? output.getMaskType() : "NONE");
            entity.setFormatType(output.getFormatType());
            entity.setDisplayPipelineJson(
                    output.getDisplayPipelineJson() != null
                            ? output.getDisplayPipelineJson()
                            : previousField != null ? previousField.getDisplayPipelineJson() : null);
            fieldMappingMapper.insert(entity);
            sort++;
        }
    }

    public static boolean isApi(String queryMode) {
        return QueryMode.API.name().equalsIgnoreCase(queryMode != null ? queryMode.trim() : "");
    }
}

package com.sov.imhub.service.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sov.imhub.domain.FieldMappingEntity;
import com.sov.imhub.mapper.FieldMappingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiQueryConfigServiceTest {

    private final FieldMappingMapper fieldMappingMapper = mock(FieldMappingMapper.class);
    private final ApiQueryConfigService service = new ApiQueryConfigService(fieldMappingMapper);

    @Test
    void parseConfig_defaultsMethodAndLabel() {
        ApiQueryConfig config = service.parseConfig("""
                {
                  "path": "/api/v3/ticker/price",
                  "outputs": [
                    {"key": "price", "jsonPointer": "/price", "label": ""}
                  ]
                }
                """);

        assertEquals("GET", config.getMethod());
        assertEquals("price", config.getOutputs().get(0).getLabel());
    }

    @Test
    void parseConfig_rejectsInvalidJson() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.parseConfig("{bad json}"));

        assertTrue(ex.getMessage().startsWith("Invalid apiConfigJson:"));
    }

    @Test
    void validateConfig_requiresOutputs() {
        ApiQueryConfig config = new ApiQueryConfig();
        config.setPath("/health");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateConfig(config));

        assertEquals("请至少选择一个返回字段", ex.getMessage());
    }

    @Test
    void replaceFieldMappingsFromApi_preservesExistingDisplayPipeline() {
        FieldMappingEntity previous = new FieldMappingEntity();
        previous.setId(99L);
        previous.setQueryId(12L);
        previous.setColumnName("price");
        previous.setLabel("旧价格");
        previous.setSortOrder(3);
        previous.setMaskType("NONE");
        previous.setDisplayPipelineJson("[{\"op\":\"prefix\",\"value\":\"¥\"}]");
        when(fieldMappingMapper.selectList(any())).thenReturn(List.of(previous));

        ApiQueryConfig.ApiOutputField output = new ApiQueryConfig.ApiOutputField();
        output.setKey("price");
        output.setLabel("现价");
        output.setJsonPointer("/price");

        ApiQueryConfig config = new ApiQueryConfig();
        config.setPath("/api/v3/ticker/price");
        config.setOutputs(List.of(output));

        service.replaceFieldMappingsFromApi(12L, config);

        verify(fieldMappingMapper).delete(any(Wrapper.class));
        verify(fieldMappingMapper).selectList(any(Wrapper.class));
        verify(fieldMappingMapper).insert(org.mockito.ArgumentMatchers.argThat(inserted -> {
            assertEquals(12L, inserted.getQueryId());
            assertEquals("price", inserted.getColumnName());
            assertEquals("现价", inserted.getLabel());
            assertEquals(0, inserted.getSortOrder());
            assertEquals("NONE", inserted.getMaskType());
            assertEquals(previous.getDisplayPipelineJson(), inserted.getDisplayPipelineJson());
            return true;
        }));
    }

    @Test
    void replaceFieldMappingsFromApi_usesProvidedSortOrderAndMaskType() {
        when(fieldMappingMapper.selectList(any())).thenReturn(List.of());

        ApiQueryConfig.ApiOutputField output = new ApiQueryConfig.ApiOutputField();
        output.setKey("weather");
        output.setLabel("天气");
        output.setJsonPointer("/current_condition/0/weatherDesc/0/value");
        output.setSortOrder(5);
        output.setMaskType("PHONE_LAST4");
        output.setDisplayPipelineJson("[{\"op\":\"suffix\",\"value\":\"!\"}]");

        ApiQueryConfig config = new ApiQueryConfig();
        config.setPath("/{{city}}?format=j1");
        config.setOutputs(List.of(output));

        service.replaceFieldMappingsFromApi(20L, config);

        verify(fieldMappingMapper, times(1)).insert(org.mockito.ArgumentMatchers.argThat(inserted -> {
            assertEquals(5, inserted.getSortOrder());
            assertEquals("PHONE_LAST4", inserted.getMaskType());
            assertEquals(output.getDisplayPipelineJson(), inserted.getDisplayPipelineJson());
            return true;
        }));
    }

    @Test
    void isApi_matchesOnlyApiMode() {
        assertTrue(ApiQueryConfigService.isApi("API"));
        assertTrue(ApiQueryConfigService.isApi(" api "));
        assertEquals(false, ApiQueryConfigService.isApi("SQL"));
    }
}

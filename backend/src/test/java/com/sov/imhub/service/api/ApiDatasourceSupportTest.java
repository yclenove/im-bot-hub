package com.sov.imhub.service.api;

import com.sov.imhub.domain.DatasourceEntity;
import com.sov.imhub.service.crypto.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ApiDatasourceSupportTest {

    private final ApiDatasourceSupport service = new ApiDatasourceSupport(RestClient.builder(), mock(EncryptionService.class));

    @Test
    void validateDatasource_requiresHttpBaseUrl() {
        DatasourceEntity entity = baseDatasource();
        entity.setApiBaseUrl("api.example.com");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateDatasource(entity, "token"));

        assertEquals("API 基础地址必须以 http:// 或 https:// 开头", ex.getMessage());
    }

    @Test
    void validateDatasource_requiresBearerSecret() {
        DatasourceEntity entity = baseDatasource();
        entity.setAuthType("BEARER_TOKEN");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateDatasource(entity, ""));

        assertEquals("Bearer Token 不能为空", ex.getMessage());
    }

    @Test
    void validateDatasource_requiresBasicUsername() {
        DatasourceEntity entity = baseDatasource();
        entity.setAuthType("BASIC");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateDatasource(entity, "secret"));

        assertEquals("Basic 鉴权用户名不能为空", ex.getMessage());
    }

    @Test
    void validateDatasource_requiresApiKeyName() {
        DatasourceEntity entity = baseDatasource();
        entity.setAuthType("API_KEY_HEADER");
        entity.setAuthConfigJson("{}");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateDatasource(entity, "secret"));

        assertEquals("API Key 名称不能为空", ex.getMessage());
    }

    @Test
    void validateDatasource_defaultsTooSmallTimeout() {
        DatasourceEntity entity = baseDatasource();
        entity.setRequestTimeoutMs(100);

        service.validateDatasource(entity, "");

        assertEquals(5000, entity.getRequestTimeoutMs());
    }

    @Test
    void ensureRequiredParams_rejectsBlankValues() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.ensureRequiredParams(Map.of("city", " ")));

        assertEquals("Missing parameter: city", ex.getMessage());
    }

    @Test
    void preview_rejectsMissingResponseRoot() {
        DatasourceEntity entity = baseDatasource();
        ApiQueryConfig.ApiOutputField output = new ApiQueryConfig.ApiOutputField();
        output.setKey("price");
        output.setLabel("价格");
        output.setJsonPointer("/price");

        ApiQueryConfig config = new ApiQueryConfig();
        config.setMethod("GET");
        config.setPath("https://postman-echo.com/get?symbol={{symbol}}");
        config.setResponseRootPointer("/not_found");
        config.setOutputs(java.util.List.of(output));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.preview(entity, "{\"params\":[\"symbol\"]}", config, java.util.List.of("BTCUSDT")));

        assertEquals("响应中未找到指定结果位置", ex.getMessage());
    }

    @Test
    void executeQuery_supportsAbsolutePathAndMapsOutput() {
        DatasourceEntity entity = baseDatasource();

        ApiQueryConfig.ApiOutputField output = new ApiQueryConfig.ApiOutputField();
        output.setKey("symbol");
        output.setLabel("Symbol");
        output.setJsonPointer("/args/symbol");

        ApiQueryConfig config = new ApiQueryConfig();
        config.setMethod("GET");
        config.setPath("https://postman-echo.com/get?symbol={{symbol}}");
        config.setOutputs(java.util.List.of(output));

        java.util.List<java.util.Map<String, Object>> rows = service.executeQuery(
                entity,
                "{\"params\":[\"symbol\"]}",
                config,
                java.util.List.of("BTCUSDT"));

        assertEquals(1, rows.size());
        assertEquals("BTCUSDT", rows.get(0).get("symbol"));
    }

    @Test
    void validateDatasource_rejectsInvalidConfigJsonMap() {
        DatasourceEntity entity = baseDatasource();
        entity.setConfigJson("[]");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.testConnection(entity, ""));

        assertTrue(ex.getMessage().contains("配置 JSON 格式不正确"));
    }

    @Test
    void applyLocalResultLimit_truncatesRowsByUserParam() {
        ApiQueryConfig config = new ApiQueryConfig();
        config.setLocalResultLimitParamName("limit");

        List<Map<String, Object>> rows = List.of(
                Map.of("i", 1),
                Map.of("i", 2),
                Map.of("i", 3),
                Map.of("i", 4));

        List<Map<String, Object>> limited = service.applyLocalResultLimit(rows, config, Map.of("limit", "2"));

        assertEquals(2, limited.size());
        assertEquals(1, limited.get(0).get("i"));
        assertEquals(2, limited.get(1).get("i"));
    }

    @Test
    void applyLocalResultLimit_rejectsNonNumericLimitParam() {
        ApiQueryConfig config = new ApiQueryConfig();
        config.setLocalResultLimitParamName("limit");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.applyLocalResultLimit(List.of(Map.of("i", 1)), config, Map.of("limit", "abc")));

        assertEquals("条数参数必须为正整数：limit", ex.getMessage());
    }

    private DatasourceEntity baseDatasource() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setName("公开 API");
        entity.setApiBaseUrl("https://postman-echo.com");
        entity.setAuthType("NONE");
        entity.setRequestTimeoutMs(5000);
        return entity;
    }
}

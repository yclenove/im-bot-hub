package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.DatasourceTestRequest;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.DatasourceType;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.service.jdbc.JdbcConnectionTester;
import com.sov.telegram.bot.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 与 {@code /api/admin/datasources/{id}} 分离，避免路径与字面量 test-connection 在部分环境下的匹配歧义（405）。
 */
@RestController
@RequestMapping("/api/admin/datasource-connection")
@RequiredArgsConstructor
public class AdminDatasourceConnectionController {

    private final DatasourceMapper datasourceMapper;
    private final EncryptionService encryptionService;
    private final JdbcConnectionTester jdbcConnectionTester;
    private final ApiDatasourceSupport apiDatasourceSupport;

    @PostMapping("/test")
    public Map<String, String> testConnection(@Valid @RequestBody DatasourceTestRequest req) {
        String sourceType = req.getSourceType();
        DatasourceEntity existing = null;
        if (req.getId() != null) {
            existing = datasourceMapper.selectById(req.getId());
            if (existing == null) {
                throw new NotFoundException("datasource not found");
            }
            if (sourceType == null || sourceType.isBlank()) {
                sourceType = existing.getSourceType();
            }
        }
        if (DatasourceType.fromString(sourceType) == DatasourceType.API) {
            DatasourceEntity api = new DatasourceEntity();
            api.setName("test-api");
            api.setSourceType("API");
            api.setApiBaseUrl(req.getApiBaseUrl());
            api.setApiPresetKey(req.getApiPresetKey());
            api.setAuthType(req.getAuthType());
            api.setAuthConfigJson(req.getAuthConfigJson());
            api.setDefaultHeadersJson(req.getDefaultHeadersJson());
            api.setDefaultQueryParamsJson(req.getDefaultQueryParamsJson());
            api.setConfigJson(req.getConfigJson());
            api.setRequestTimeoutMs(req.getRequestTimeoutMs() != null ? req.getRequestTimeoutMs() : 5000);
            api.setUsername(req.getUsername());
            if ((req.getPasswordPlain() == null || req.getPasswordPlain().isBlank()) && existing != null) {
                api.setPasswordCipher(existing.getPasswordCipher());
            }
            apiDatasourceSupport.validateDatasource(api, req.getPasswordPlain());
            apiDatasourceSupport.testConnection(api, req.getPasswordPlain());
            return Map.of("ok", "true", "message", "API 连通成功");
        }
        String password = req.getPasswordPlain();
        if (password == null || password.isBlank()) {
            if (req.getId() == null) {
                throw new IllegalArgumentException("新建数据源测试时请填写数据库密码");
            }
            password = encryptionService.decrypt(existing.getPasswordCipher());
        }
        jdbcConnectionTester.validate(req.getJdbcUrl().trim(), req.getUsername().trim(), password);
        return Map.of("ok", "true", "message", "连接成功");
    }
}

package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.DatasourceCreateRequest;
import com.sov.telegram.bot.admin.dto.DatasourceUpdateRequest;
import com.sov.telegram.bot.admin.dto.DatasourceResponse;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.DatasourceType;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.service.jdbc.BusinessDataSourceRegistry;
import com.sov.telegram.bot.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/datasources")
@RequiredArgsConstructor
public class AdminDatasourceController {

    private final DatasourceMapper datasourceMapper;
    private final AdminDtoMapper adminDtoMapper;
    private final EncryptionService encryptionService;
    private final BusinessDataSourceRegistry businessDataSourceRegistry;
    private final AuditLogService auditLogService;
    private final ApiDatasourceSupport apiDatasourceSupport;

    @GetMapping
    public List<DatasourceResponse> list() {
        return datasourceMapper.selectList(null).stream()
                .map(adminDtoMapper::toDatasourceResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public DatasourceResponse get(@PathVariable Long id) {
        DatasourceEntity e = datasourceMapper.selectById(id);
        if (e == null) {
            throw new NotFoundException("datasource not found");
        }
        return adminDtoMapper.toDatasourceResponse(e);
    }

    @PostMapping
    public DatasourceResponse create(@Valid @RequestBody DatasourceCreateRequest req) {
        DatasourceEntity e = new DatasourceEntity();
        e.setName(req.getName());
        applyRequest(e, req);
        e.setPoolMax(req.getPoolMax());
        e.setRequestTimeoutMs(req.getRequestTimeoutMs());
        datasourceMapper.insert(e);
        businessDataSourceRegistry.reloadOne(e.getId());
        auditLogService.log("CREATE", "DATASOURCE", String.valueOf(e.getId()), e.getName());
        return adminDtoMapper.toDatasourceResponse(datasourceMapper.selectById(e.getId()));
    }

    @PutMapping("/{id}")
    public DatasourceResponse update(@PathVariable Long id, @RequestBody DatasourceUpdateRequest req) {
        DatasourceEntity e = datasourceMapper.selectById(id);
        if (e == null) {
            throw new NotFoundException("datasource not found");
        }
        if (req.getName() != null) {
            e.setName(req.getName());
        }
        applyRequest(e, req);
        if (req.getPoolMax() != null) {
            e.setPoolMax(req.getPoolMax());
        }
        datasourceMapper.updateById(e);
        businessDataSourceRegistry.reloadOne(id);
        auditLogService.log("UPDATE", "DATASOURCE", String.valueOf(id), e.getName());
        return adminDtoMapper.toDatasourceResponse(datasourceMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        datasourceMapper.deleteById(id);
        businessDataSourceRegistry.reloadOne(id);
        auditLogService.log("DELETE", "DATASOURCE", String.valueOf(id), null);
    }

    private void applyRequest(DatasourceEntity e, DatasourceCreateRequest req) {
        applyCommonFields(
                e,
                req.getSourceType(),
                req.getJdbcUrl(),
                req.getApiBaseUrl(),
                req.getApiPresetKey(),
                req.getAuthType(),
                req.getAuthConfigJson(),
                req.getDefaultHeadersJson(),
                req.getDefaultQueryParamsJson(),
                req.getRequestTimeoutMs(),
                req.getConfigJson(),
                req.getUsername(),
                req.getPasswordPlain());
    }

    private void applyRequest(DatasourceEntity e, DatasourceUpdateRequest req) {
        applyCommonFields(
                e,
                req.getSourceType(),
                req.getJdbcUrl(),
                req.getApiBaseUrl(),
                req.getApiPresetKey(),
                req.getAuthType(),
                req.getAuthConfigJson(),
                req.getDefaultHeadersJson(),
                req.getDefaultQueryParamsJson(),
                req.getRequestTimeoutMs(),
                req.getConfigJson(),
                req.getUsername(),
                req.getPasswordPlain());
    }

    private void applyCommonFields(
            DatasourceEntity e,
            String sourceType,
            String jdbcUrl,
            String apiBaseUrl,
            String apiPresetKey,
            String authType,
            String authConfigJson,
            String defaultHeadersJson,
            String defaultQueryParamsJson,
            Integer requestTimeoutMs,
            String configJson,
            String username,
            String passwordPlain) {
        DatasourceType type = DatasourceType.fromString(sourceType != null ? sourceType : e.getSourceType());
        e.setSourceType(type.name());
        if (type == DatasourceType.DATABASE) {
            if (jdbcUrl != null) {
                e.setJdbcUrl(jdbcUrl);
            }
            if (username != null) {
                e.setUsername(username);
            }
            if (passwordPlain != null && !passwordPlain.isBlank()) {
                e.setPasswordCipher(encryptionService.encrypt(passwordPlain));
            }
            e.setApiBaseUrl(null);
            e.setApiPresetKey(null);
            e.setAuthType(null);
            e.setAuthConfigJson(null);
            e.setDefaultHeadersJson(null);
            e.setDefaultQueryParamsJson(null);
            e.setConfigJson(null);
            if (requestTimeoutMs != null) {
                e.setRequestTimeoutMs(requestTimeoutMs);
            } else if (e.getRequestTimeoutMs() == null) {
                e.setRequestTimeoutMs(5000);
            }
            return;
        }
        if (apiBaseUrl != null) {
            e.setApiBaseUrl(apiBaseUrl);
        }
        if (apiPresetKey != null) {
            e.setApiPresetKey(apiPresetKey);
        }
        if (authType != null) {
            e.setAuthType(authType);
        }
        if (authConfigJson != null) {
            e.setAuthConfigJson(authConfigJson);
        }
        if (defaultHeadersJson != null) {
            e.setDefaultHeadersJson(defaultHeadersJson);
        }
        if (defaultQueryParamsJson != null) {
            e.setDefaultQueryParamsJson(defaultQueryParamsJson);
        }
        if (configJson != null) {
            e.setConfigJson(configJson);
        }
        if (username != null) {
            e.setUsername(username);
        }
        if (passwordPlain != null && !passwordPlain.isBlank()) {
            e.setPasswordCipher(encryptionService.encrypt(passwordPlain));
        }
        if (requestTimeoutMs != null) {
            e.setRequestTimeoutMs(requestTimeoutMs);
        } else if (e.getRequestTimeoutMs() == null) {
            e.setRequestTimeoutMs(5000);
        }
        e.setJdbcUrl(null);
        apiDatasourceSupport.validateDatasource(e, passwordPlain);
    }
}

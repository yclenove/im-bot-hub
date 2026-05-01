package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.DatasourceCreateRequest;
import com.sov.telegram.bot.admin.dto.DatasourceResponse;
import com.sov.telegram.bot.admin.dto.DatasourceUpdateRequest;
import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.api.ApiDatasourceSupport;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.service.jdbc.BusinessDataSourceRegistry;
import com.sov.telegram.bot.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDatasourceControllerTest {

    private final DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
    private final AdminDtoMapper adminDtoMapper = mock(AdminDtoMapper.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final BusinessDataSourceRegistry businessDataSourceRegistry = mock(BusinessDataSourceRegistry.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ApiDatasourceSupport apiDatasourceSupport = mock(ApiDatasourceSupport.class);

    private final AdminDatasourceController controller = new AdminDatasourceController(
            datasourceMapper,
            adminDtoMapper,
            encryptionService,
            businessDataSourceRegistry,
            auditLogService,
            apiDatasourceSupport
    );

    @Test
    void create_apiDatasource_setsNameAndApiFields() {
        DatasourceCreateRequest req = new DatasourceCreateRequest();
        req.setName("天气");
        req.setSourceType("API");
        req.setApiBaseUrl("https://wttr.in");
        req.setApiPresetKey("weather-wttr");
        req.setAuthType("NONE");
        req.setRequestTimeoutMs(5000);
        req.setConfigJson("{\"healthcheckPath\":\"/Beijing?format=j1\"}");

        doAnswer(invocation -> {
            DatasourceEntity inserted = invocation.getArgument(0);
            inserted.setId(2L);
            return 1;
        }).when(datasourceMapper).insert(any(DatasourceEntity.class));

        DatasourceEntity persisted = new DatasourceEntity();
        persisted.setId(2L);
        persisted.setName("天气");
        when(datasourceMapper.selectById(2L)).thenReturn(persisted);

        DatasourceResponse expected = DatasourceResponse.builder().id(2L).name("天气").sourceType("API").build();
        when(adminDtoMapper.toDatasourceResponse(persisted)).thenReturn(expected);

        DatasourceResponse actual = controller.create(req);

        assertSame(expected, actual);
        ArgumentCaptor<DatasourceEntity> captor = ArgumentCaptor.forClass(DatasourceEntity.class);
        verify(datasourceMapper).insert(captor.capture());
        DatasourceEntity inserted = captor.getValue();
        assertEquals("天气", inserted.getName());
        assertEquals("API", inserted.getSourceType());
        assertEquals("https://wttr.in", inserted.getApiBaseUrl());
        assertNull(inserted.getJdbcUrl());
        verify(apiDatasourceSupport).validateDatasource(eq(inserted), eq(null));
        verify(businessDataSourceRegistry).reloadOne(2L);
    }

    @Test
    void create_databaseDatasource_encryptsPasswordAndClearsApiFields() {
        DatasourceCreateRequest req = new DatasourceCreateRequest();
        req.setName("shoptd");
        req.setSourceType("DATABASE");
        req.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/shoptd");
        req.setUsername("root");
        req.setPasswordPlain("plain");

        when(encryptionService.encrypt("plain")).thenReturn("cipher");
        doAnswer(invocation -> {
            DatasourceEntity inserted = invocation.getArgument(0);
            inserted.setId(5L);
            return 1;
        }).when(datasourceMapper).insert(any(DatasourceEntity.class));

        DatasourceEntity persisted = new DatasourceEntity();
        persisted.setId(5L);
        when(datasourceMapper.selectById(5L)).thenReturn(persisted);
        when(adminDtoMapper.toDatasourceResponse(persisted)).thenReturn(DatasourceResponse.builder().id(5L).build());

        controller.create(req);

        ArgumentCaptor<DatasourceEntity> captor = ArgumentCaptor.forClass(DatasourceEntity.class);
        verify(datasourceMapper).insert(captor.capture());
        DatasourceEntity inserted = captor.getValue();
        assertEquals("DATABASE", inserted.getSourceType());
        assertEquals("jdbc:mysql://127.0.0.1:3306/shoptd", inserted.getJdbcUrl());
        assertEquals("root", inserted.getUsername());
        assertEquals("cipher", inserted.getPasswordCipher());
        assertNull(inserted.getApiBaseUrl());
        verify(apiDatasourceSupport, never()).validateDatasource(any(), any());
    }

    @Test
    void update_missingDatasource_throwsNotFound() {
        when(datasourceMapper.selectById(99L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> controller.update(99L, new DatasourceUpdateRequest()));

        assertEquals("datasource not found", ex.getMessage());
    }
}

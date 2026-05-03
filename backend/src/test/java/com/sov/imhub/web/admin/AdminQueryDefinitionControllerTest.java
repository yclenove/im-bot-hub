package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.mapstruct.AdminDtoMapper;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.mapper.DatasourceMapper;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.api.ApiQueryConfigService;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.jdbc.SqlTemplateValidator;
import com.sov.imhub.service.telegram.TelegramApiClient;
import com.sov.imhub.service.visual.VisualQueryCompilationService;
import com.sov.imhub.web.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminQueryDefinitionControllerTest {

    private final QueryDefinitionMapper queryDefinitionMapper = mock(QueryDefinitionMapper.class);
    private final BotMapper botMapper = mock(BotMapper.class);
    private final BotChannelMapper botChannelMapper = mock(BotChannelMapper.class);
    private final DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
    private final AdminDtoMapper adminDtoMapper = mock(AdminDtoMapper.class);
    private final SqlTemplateValidator sqlTemplateValidator = mock(SqlTemplateValidator.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final VisualQueryCompilationService visualQueryCompilationService = mock(VisualQueryCompilationService.class);
    private final ApiQueryConfigService apiQueryConfigService = mock(ApiQueryConfigService.class);
    private final TelegramApiClient telegramApiClient = mock(TelegramApiClient.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);

    private final AdminQueryDefinitionController controller = new AdminQueryDefinitionController(
            queryDefinitionMapper,
            botMapper,
            botChannelMapper,
            datasourceMapper,
            adminDtoMapper,
            sqlTemplateValidator,
            auditLogService,
            visualQueryCompilationService,
            apiQueryConfigService,
            telegramApiClient,
            encryptionService
    );

    @Test
    void delete_softDeletesQuery() {
        QueryDefinitionEntity query = new QueryDefinitionEntity();
        query.setId(17L);
        query.setBotId(3L);
        query.setCommand("okx_p2p_buy");
        when(queryDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(query);
        when(botMapper.selectById(3L)).thenReturn(null);

        controller.delete(3L, 17L);

        verify(queryDefinitionMapper).updateById(query);
        assertEquals(17L, query.getDeleteToken());
        assertEquals(1, query.getDeleted());
        assertDeletedAtSet(query.getDeletedAt());
        verify(queryDefinitionMapper, never()).deleteById(anyLong());
        verify(auditLogService).log("DELETE", "QUERY", "17", "okx_p2p_buy");
    }

    @Test
    void delete_missingQuery_throwsNotFound() {
        when(queryDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> controller.delete(3L, 17L));

        assertEquals("query not found", ex.getMessage());
        verify(queryDefinitionMapper, never()).updateById(any());
    }

    @Test
    void assertCommandUniqueForBot_ignoresSoftDeletedRows() throws Exception {
        when(queryDefinitionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        java.lang.reflect.Method method = AdminQueryDefinitionController.class
                .getDeclaredMethod("assertCommandUniqueForBot", Long.class, String.class, Long.class);
        method.setAccessible(true);
        method.invoke(controller, 3L, "okx_p2p_buy", null);

        verify(queryDefinitionMapper).selectCount(any(Wrapper.class));
    }

    private static void assertDeletedAtSet(LocalDateTime deletedAt) {
        org.junit.jupiter.api.Assertions.assertNotNull(deletedAt);
    }
}

package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.AuditLogMapper;
import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBotControllerTest {

    private final BotMapper botMapper = mock(BotMapper.class);
    private final QueryDefinitionMapper queryDefinitionMapper = mock(QueryDefinitionMapper.class);
    private final BotChannelMapper botChannelMapper = mock(BotChannelMapper.class);
    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final AuditLogService auditLogService = new AuditLogService(auditLogMapper);
    private final AdminDtoMapper adminDtoMapper = mock(AdminDtoMapper.class);

    private final AdminBotController controller = new AdminBotController(
            botMapper,
            queryDefinitionMapper,
            botChannelMapper,
            adminDtoMapper,
            auditLogService);

    @Test
    void delete_removesDependenciesBeforeDeletingBot() {
        Bot bot = new Bot();
        bot.setId(7L);
        bot.setName("测试机器人");
        when(botMapper.selectById(7L)).thenReturn(bot);
        QueryDefinitionEntity q = new QueryDefinitionEntity();
        q.setId(17L);
        q.setBotId(7L);
        BotChannelEntity channel = new BotChannelEntity();
        channel.setId(27L);
        channel.setBotId(7L);
        when(queryDefinitionMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(q));
        when(botChannelMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(channel));

        controller.delete(7L);

        verify(queryDefinitionMapper).selectList(any(Wrapper.class));
        ArgumentCaptor<QueryDefinitionEntity> queryCaptor = ArgumentCaptor.forClass(QueryDefinitionEntity.class);
        verify(queryDefinitionMapper).update(queryCaptor.capture(), any(Wrapper.class));
        assertEquals(17L, queryCaptor.getValue().getDeleteToken());
        assertEquals(1, queryCaptor.getValue().getDeleted());
        assertDeletedAtSet(queryCaptor.getValue().getDeletedAt());

        verify(botChannelMapper).selectList(any(Wrapper.class));
        ArgumentCaptor<BotChannelEntity> channelCaptor = ArgumentCaptor.forClass(BotChannelEntity.class);
        verify(botChannelMapper).update(channelCaptor.capture(), any(Wrapper.class));
        assertEquals(1, channelCaptor.getValue().getDeleted());
        assertDeletedAtSet(channelCaptor.getValue().getDeletedAt());

        ArgumentCaptor<Bot> botCaptor = ArgumentCaptor.forClass(Bot.class);
        verify(botMapper).updateById(botCaptor.capture());
        assertEquals(1, botCaptor.getValue().getDeleted());
        assertDeletedAtSet(botCaptor.getValue().getDeletedAt());

        verify(auditLogMapper).insert(org.mockito.ArgumentMatchers.argThat(log -> {
            assertEquals("DELETE", log.getAction());
            assertEquals("BOT", log.getResourceType());
            assertEquals("7", log.getResourceId());
            assertEquals("测试机器人", log.getDetail());
            return true;
        }));
    }

    @Test
    void delete_throwsNotFoundWhenBotMissing() {
        when(botMapper.selectById(99L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> controller.delete(99L));

        assertEquals("bot not found", ex.getMessage());
        verify(queryDefinitionMapper, never()).selectList(any(Wrapper.class));
        verify(queryDefinitionMapper, never()).update(any(), any(Wrapper.class));
        verify(botChannelMapper, never()).selectList(any(Wrapper.class));
        verify(botChannelMapper, never()).update(any(), any(Wrapper.class));
        verify(botMapper, never()).updateById(any());
        verify(auditLogMapper, never()).insert(any());
    }

    private static void assertDeletedAtSet(LocalDateTime deletedAt) {
        org.junit.jupiter.api.Assertions.assertNotNull(deletedAt);
    }
}

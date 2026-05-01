package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sov.telegram.bot.config.AppProperties;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.web.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBotChannelControllerTest {

    private final BotMapper botMapper = mock(BotMapper.class);
    private final BotChannelMapper botChannelMapper = mock(BotChannelMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AppProperties appProperties = new AppProperties();
    private final EncryptionService encryptionService = mock(EncryptionService.class);

    private final AdminBotChannelController controller = new AdminBotChannelController(
            botMapper,
            botChannelMapper,
            auditLogService,
            appProperties,
            encryptionService
    );

    @Test
    void delete_softDeletesChannel() {
        Bot bot = new Bot();
        bot.setId(3L);
        when(botMapper.selectById(3L)).thenReturn(bot);

        BotChannelEntity channel = new BotChannelEntity();
        channel.setId(27L);
        channel.setBotId(3L);
        channel.setPlatform("LARK");
        when(botChannelMapper.selectById(27L)).thenReturn(channel);

        controller.delete(3L, 27L);

        verify(botChannelMapper).updateById(channel);
        assertEquals(1, channel.getDeleted());
        assertDeletedAtSet(channel.getDeletedAt());
        verify(botChannelMapper, never()).deleteById(anyLong());
        verify(auditLogService).log("DELETE", "BOT_CHANNEL", "27", "LARK");
    }

    @Test
    void delete_missingChannel_throwsNotFound() {
        Bot bot = new Bot();
        bot.setId(3L);
        when(botMapper.selectById(3L)).thenReturn(bot);
        when(botChannelMapper.selectById(99L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> controller.delete(3L, 99L));

        assertEquals("channel not found", ex.getMessage());
        verify(botChannelMapper, never()).updateById(any());
    }

    private static void assertDeletedAtSet(LocalDateTime deletedAt) {
        org.junit.jupiter.api.Assertions.assertNotNull(deletedAt);
    }
}

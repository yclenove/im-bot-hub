package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sov.imhub.admin.dto.TelegramQueryLogPageResponse;
import com.sov.imhub.domain.TelegramQueryLogEntity;
import com.sov.imhub.mapper.TelegramQueryLogMapper;
import com.sov.imhub.service.TelegramQueryLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/telegram-query-logs")
@RequiredArgsConstructor
public class AdminTelegramQueryLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TelegramQueryLogMapper telegramQueryLogMapper;

    @GetMapping
    public TelegramQueryLogPageResponse list(
            @RequestParam(required = false) Long botId,
            @RequestParam(required = false) String command,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String errorKind,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long telegramUserId,
            @RequestParam(required = false) Long chatId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long sz = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long pg = Math.max(1, page);
        String kindNorm = null;
        if (StringUtils.hasText(errorKind)) {
            kindNorm = errorKind.trim().toUpperCase(Locale.ROOT);
            if (!TelegramQueryLogService.ERROR_KINDS.contains(kindNorm)) {
                throw new IllegalArgumentException("errorKind 无效");
            }
        }
        LambdaQueryWrapper<TelegramQueryLogEntity> q =
                Wrappers.<TelegramQueryLogEntity>lambdaQuery()
                        .eq(botId != null, TelegramQueryLogEntity::getBotId, botId)
                        .like(
                                StringUtils.hasText(command),
                                TelegramQueryLogEntity::getCommand,
                                command != null ? command.trim() : "")
                        .eq(kindNorm != null, TelegramQueryLogEntity::getErrorKind, kindNorm)
                        .eq(success != null, TelegramQueryLogEntity::getSuccess, success)
                        .eq(telegramUserId != null, TelegramQueryLogEntity::getTelegramUserId, telegramUserId)
                        .eq(chatId != null, TelegramQueryLogEntity::getChatId, chatId)
                        .ge(from != null, TelegramQueryLogEntity::getCreatedAt, from)
                        .le(to != null, TelegramQueryLogEntity::getCreatedAt, to)
                        .orderByDesc(TelegramQueryLogEntity::getCreatedAt)
                        .orderByDesc(TelegramQueryLogEntity::getId);
        Page<TelegramQueryLogEntity> mp = new Page<>(pg, sz);
        Page<TelegramQueryLogEntity> result = telegramQueryLogMapper.selectPage(mp, q);
        return new TelegramQueryLogPageResponse(
                result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}

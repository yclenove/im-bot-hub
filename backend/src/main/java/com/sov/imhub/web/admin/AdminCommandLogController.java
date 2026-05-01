package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sov.imhub.admin.dto.CommandLogPageResponse;
import com.sov.imhub.domain.CommandLogEntity;
import com.sov.imhub.mapper.CommandLogMapper;
import com.sov.imhub.service.CommandLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 通用命令日志 API：替代 AdminTelegramQueryLogController，支持全平台筛选。
 */
@RestController
@RequestMapping("/api/admin/command-logs")
@RequiredArgsConstructor
public class AdminCommandLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CommandLogMapper commandLogMapper;

    @GetMapping
    public CommandLogPageResponse list(
            @RequestParam(required = false) Long botId,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String command,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String errorKind,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String externalUserId,
            @RequestParam(required = false) String externalChatId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long sz = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long pg = Math.max(1, page);
        String kindNorm = null;
        if (StringUtils.hasText(errorKind)) {
            kindNorm = errorKind.trim().toUpperCase(Locale.ROOT);
            if (!CommandLogService.ERROR_KINDS.contains(kindNorm)) {
                throw new IllegalArgumentException("errorKind 无效");
            }
        }
        String platformNorm = null;
        if (StringUtils.hasText(platform)) {
            platformNorm = platform.trim().toUpperCase(Locale.ROOT);
        }
        LambdaQueryWrapper<CommandLogEntity> q =
                Wrappers.<CommandLogEntity>lambdaQuery()
                        .eq(botId != null, CommandLogEntity::getBotId, botId)
                        .eq(channelId != null, CommandLogEntity::getChannelId, channelId)
                        .eq(platformNorm != null, CommandLogEntity::getPlatform, platformNorm)
                        .like(
                                StringUtils.hasText(command),
                                CommandLogEntity::getCommand,
                                command != null ? command.trim() : "")
                        .eq(kindNorm != null, CommandLogEntity::getErrorKind, kindNorm)
                        .eq(success != null, CommandLogEntity::getSuccess, success)
                        .eq(StringUtils.hasText(externalUserId), CommandLogEntity::getExternalUserId, externalUserId != null ? externalUserId.trim() : "")
                        .eq(StringUtils.hasText(externalChatId), CommandLogEntity::getExternalChatId, externalChatId != null ? externalChatId.trim() : "")
                        .ge(from != null, CommandLogEntity::getCreatedAt, from)
                        .le(to != null, CommandLogEntity::getCreatedAt, to)
                        .orderByDesc(CommandLogEntity::getCreatedAt)
                        .orderByDesc(CommandLogEntity::getId);
        Page<CommandLogEntity> mp = new Page<>(pg, sz);
        Page<CommandLogEntity> result = commandLogMapper.selectPage(mp, q);
        return new CommandLogPageResponse(
                result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}

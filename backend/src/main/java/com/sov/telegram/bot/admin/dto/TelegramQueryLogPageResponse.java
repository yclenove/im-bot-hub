package com.sov.telegram.bot.admin.dto;

import com.sov.telegram.bot.domain.TelegramQueryLogEntity;

import java.util.List;

public record TelegramQueryLogPageResponse(
        List<TelegramQueryLogEntity> records, long total, long page, long size) {}

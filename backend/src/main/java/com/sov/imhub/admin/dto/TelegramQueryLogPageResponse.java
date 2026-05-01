package com.sov.imhub.admin.dto;

import com.sov.imhub.domain.TelegramQueryLogEntity;

import java.util.List;

public record TelegramQueryLogPageResponse(
        List<TelegramQueryLogEntity> records, long total, long page, long size) {}

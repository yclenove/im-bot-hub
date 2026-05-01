package com.sov.imhub.admin.dto;

import com.sov.imhub.domain.CommandLogEntity;

import java.util.List;

public record CommandLogPageResponse(
        List<CommandLogEntity> records, long total, long page, long size) {}

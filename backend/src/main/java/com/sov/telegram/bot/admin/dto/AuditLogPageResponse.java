package com.sov.telegram.bot.admin.dto;

import com.sov.telegram.bot.domain.AuditLogEntity;

import java.util.List;

/** Paginated audit logs for admin UI. */
public record AuditLogPageResponse(List<AuditLogEntity> records, long total, long page, long size) {}

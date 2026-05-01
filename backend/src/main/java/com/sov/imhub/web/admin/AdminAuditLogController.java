package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sov.imhub.admin.dto.AuditLogPageResponse;
import com.sov.imhub.domain.AuditLogEntity;
import com.sov.imhub.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogMapper auditLogMapper;

    @GetMapping
    public AuditLogPageResponse list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long sz = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long pg = Math.max(1, page);
        LambdaQueryWrapper<AuditLogEntity> q =
                Wrappers.<AuditLogEntity>lambdaQuery()
                        .orderByDesc(AuditLogEntity::getCreatedAt)
                        .orderByDesc(AuditLogEntity::getId);
        Page<AuditLogEntity> mp = new Page<>(pg, sz);
        Page<AuditLogEntity> result = auditLogMapper.selectPage(mp, q);
        return new AuditLogPageResponse(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}

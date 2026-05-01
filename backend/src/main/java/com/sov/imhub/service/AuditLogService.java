package com.sov.imhub.service;

import com.sov.imhub.domain.AuditLogEntity;
import com.sov.imhub.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void log(String action, String resourceType, String resourceId, String detail) {
        AuditLogEntity e = new AuditLogEntity();
        e.setActor(currentActor());
        e.setAction(action);
        e.setResourceType(resourceType);
        e.setResourceId(resourceId);
        e.setDetail(detail);
        e.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(e);
    }

    private static String currentActor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getName() != null ? a.getName() : "system";
    }
}

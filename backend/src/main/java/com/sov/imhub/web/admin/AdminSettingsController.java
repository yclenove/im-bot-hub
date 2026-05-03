package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.domain.SystemSettingEntity;
import com.sov.imhub.mapper.SystemSettingMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.crypto.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统设置 API：管理系统级配置参数。
 *
 * <p>设置项存储在 t_system_setting 表中，支持动态增删改查。</p>
 * <p>加密状态字段为只读，从 {@link EncryptionService} 实时读取。</p>
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SystemSettingMapper systemSettingMapper;
    private final AuditLogService auditLogService;
    private final EncryptionService encryptionService;

    @GetMapping
    public List<Map<String, String>> list() {
        return systemSettingMapper.selectList(new LambdaQueryWrapper<SystemSettingEntity>().orderByAsc(SystemSettingEntity::getId))
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @PutMapping
    public List<Map<String, String>> update(@RequestBody Map<String, String> settings) {
        // 加密状态为只读
        settings.remove("encryption-status");

        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            SystemSettingEntity existing = systemSettingMapper.selectOne(
                    new LambdaQueryWrapper<SystemSettingEntity>().eq(SystemSettingEntity::getSettingKey, key));
            if (existing != null) {
                existing.setSettingVal(val);
                systemSettingMapper.updateById(existing);
            } else {
                SystemSettingEntity entity = new SystemSettingEntity();
                entity.setSettingKey(key);
                entity.setSettingVal(val);
                systemSettingMapper.insert(entity);
            }
            auditLogService.log("UPDATE", "SYSTEM_SETTING", key, val);
        }

        // 更新加密状态（只读字段，从 EncryptionService 读取）
        updateEncryptionStatus();

        return list();
    }

    /**
     * 更新加密状态字段（只读，反映当前配置）。
     */
    private void updateEncryptionStatus() {
        SystemSettingEntity existing = systemSettingMapper.selectOne(
                new LambdaQueryWrapper<SystemSettingEntity>().eq(SystemSettingEntity::getSettingKey, "encryption-status"));
        String status = encryptionService.isEnabled() ? "已启用" : "未配置";
        if (existing != null) {
            existing.setSettingVal(status);
            systemSettingMapper.updateById(existing);
        }
    }

    private Map<String, String> toMap(SystemSettingEntity e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", e.getSettingKey());
        m.put("value", e.getSettingVal() == null ? "" : e.getSettingVal());
        m.put("description", e.getDescription() == null ? "" : e.getDescription());
        return m;
    }
}

package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.admin.dto.AllowlistResponse;
import com.sov.imhub.admin.dto.AllowlistUpsertRequest;
import com.sov.imhub.domain.UserAllowlistEntity;
import com.sov.imhub.mapper.UserAllowlistMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bots/{botId}/allowlist")
@RequiredArgsConstructor
public class AdminAllowlistController {

    private final UserAllowlistMapper userAllowlistMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AllowlistResponse> list(@PathVariable Long botId) {
        return userAllowlistMapper
                .selectList(new LambdaQueryWrapper<UserAllowlistEntity>().eq(UserAllowlistEntity::getBotId, botId))
                .stream()
                .map(
                        e ->
                                AllowlistResponse.builder()
                                        .id(e.getId())
                                        .botId(e.getBotId())
                                        .telegramUserId(e.getTelegramUserId())
                                        .enabled(Boolean.TRUE.equals(e.getEnabled()))
                                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping
    public AllowlistResponse create(@PathVariable Long botId, @Valid @RequestBody AllowlistUpsertRequest req) {
        UserAllowlistEntity e = new UserAllowlistEntity();
        e.setBotId(botId);
        e.setTelegramUserId(req.getTelegramUserId());
        e.setEnabled(req.isEnabled());
        userAllowlistMapper.insert(e);
        auditLogService.log("CREATE", "ALLOWLIST", String.valueOf(e.getId()), String.valueOf(req.getTelegramUserId()));
        return toResp(userAllowlistMapper.selectById(e.getId()));
    }

    @PutMapping("/{id}")
    public AllowlistResponse update(
            @PathVariable Long botId, @PathVariable Long id, @Valid @RequestBody AllowlistUpsertRequest req) {
        UserAllowlistEntity e = userAllowlistMapper.selectById(id);
        if (e == null || !botId.equals(e.getBotId())) {
            throw new NotFoundException("allowlist entry not found");
        }
        e.setTelegramUserId(req.getTelegramUserId());
        e.setEnabled(req.isEnabled());
        userAllowlistMapper.updateById(e);
        auditLogService.log("UPDATE", "ALLOWLIST", String.valueOf(id), String.valueOf(req.getTelegramUserId()));
        return toResp(userAllowlistMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long botId, @PathVariable Long id) {
        UserAllowlistEntity e = userAllowlistMapper.selectById(id);
        if (e == null || !botId.equals(e.getBotId())) {
            throw new NotFoundException("allowlist entry not found");
        }
        userAllowlistMapper.deleteById(id);
        auditLogService.log("DELETE", "ALLOWLIST", String.valueOf(id), null);
    }

    private static AllowlistResponse toResp(UserAllowlistEntity e) {
        return AllowlistResponse.builder()
                .id(e.getId())
                .botId(e.getBotId())
                .telegramUserId(e.getTelegramUserId())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .build();
    }
}

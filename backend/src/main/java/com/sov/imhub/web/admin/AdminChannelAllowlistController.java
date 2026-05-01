package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.admin.dto.ChannelAllowlistResponse;
import com.sov.imhub.admin.dto.ChannelAllowlistUpsertRequest;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.domain.ChannelAllowlistEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.ChannelAllowlistMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 渠道白名单 API：基于 t_channel_allowlist，支持全平台用户 ID。
 */
@RestController
@RequestMapping("/api/admin/channels/{channelId}/allowlist")
@RequiredArgsConstructor
public class AdminChannelAllowlistController {

    private final ChannelAllowlistMapper channelAllowlistMapper;
    private final BotChannelMapper botChannelMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<ChannelAllowlistResponse> list(@PathVariable Long channelId) {
        return channelAllowlistMapper
                .selectList(new LambdaQueryWrapper<ChannelAllowlistEntity>()
                        .eq(ChannelAllowlistEntity::getChannelId, channelId))
                .stream()
                .map(e -> ChannelAllowlistResponse.builder()
                        .id(e.getId())
                        .channelId(e.getChannelId())
                        .platform(e.getPlatform())
                        .externalUserId(e.getExternalUserId())
                        .enabled(Boolean.TRUE.equals(e.getEnabled()))
                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping
    public ChannelAllowlistResponse create(
            @PathVariable Long channelId, @Valid @RequestBody ChannelAllowlistUpsertRequest req) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("channel not found");
        }
        ChannelAllowlistEntity e = new ChannelAllowlistEntity();
        e.setChannelId(channelId);
        e.setPlatform(channel.getPlatform());
        e.setExternalUserId(req.getExternalUserId());
        e.setEnabled(req.isEnabled());
        channelAllowlistMapper.insert(e);
        auditLogService.log("CREATE", "CHANNEL_ALLOWLIST", String.valueOf(e.getId()), req.getExternalUserId());
        return toResp(channelAllowlistMapper.selectById(e.getId()));
    }

    @PutMapping("/{id}")
    public ChannelAllowlistResponse update(
            @PathVariable Long channelId,
            @PathVariable Long id,
            @Valid @RequestBody ChannelAllowlistUpsertRequest req) {
        ChannelAllowlistEntity e = channelAllowlistMapper.selectById(id);
        if (e == null || !channelId.equals(e.getChannelId())) {
            throw new NotFoundException("allowlist entry not found");
        }
        e.setExternalUserId(req.getExternalUserId());
        e.setEnabled(req.isEnabled());
        channelAllowlistMapper.updateById(e);
        auditLogService.log("UPDATE", "CHANNEL_ALLOWLIST", String.valueOf(id), req.getExternalUserId());
        return toResp(channelAllowlistMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long channelId, @PathVariable Long id) {
        ChannelAllowlistEntity e = channelAllowlistMapper.selectById(id);
        if (e == null || !channelId.equals(e.getChannelId())) {
            throw new NotFoundException("allowlist entry not found");
        }
        channelAllowlistMapper.deleteById(id);
        auditLogService.log("DELETE", "CHANNEL_ALLOWLIST", String.valueOf(id), null);
    }

    private static ChannelAllowlistResponse toResp(ChannelAllowlistEntity e) {
        return ChannelAllowlistResponse.builder()
                .id(e.getId())
                .channelId(e.getChannelId())
                .platform(e.getPlatform())
                .externalUserId(e.getExternalUserId())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .build();
    }
}

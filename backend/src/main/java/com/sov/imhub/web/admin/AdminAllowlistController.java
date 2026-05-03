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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 机器人白名单 API：基于 t_channel_allowlist，按 botId 查询。
 */
@RestController
@RequestMapping("/api/admin/bots/{botId}/allowlist")
@RequiredArgsConstructor
public class AdminAllowlistController {

    private final ChannelAllowlistMapper channelAllowlistMapper;
    private final BotChannelMapper botChannelMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<ChannelAllowlistResponse> list(@PathVariable Long botId) {
        return channelAllowlistMapper
                .selectList(new LambdaQueryWrapper<ChannelAllowlistEntity>().eq(ChannelAllowlistEntity::getBotId, botId))
                .stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ChannelAllowlistResponse create(@PathVariable Long botId, @Valid @RequestBody ChannelAllowlistUpsertRequest req) {
        // 查找 bot 的第一个渠道获取 platform
        BotChannelEntity channel = null;
        if (req.getChannelId() != null) {
            channel = botChannelMapper.selectById(req.getChannelId());
        }
        if (channel == null) {
            channel = botChannelMapper.selectOne(
                    new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, botId).last("LIMIT 1"));
        }

        ChannelAllowlistEntity e = new ChannelAllowlistEntity();
        e.setBotId(botId);
        e.setChannelId(channel != null ? channel.getId() : req.getChannelId());
        e.setPlatform(channel != null ? channel.getPlatform() : "UNKNOWN");
        e.setExternalUserId(req.getExternalUserId());
        e.setEnabled(req.isEnabled());
        channelAllowlistMapper.insert(e);
        auditLogService.log("CREATE", "ALLOWLIST", String.valueOf(e.getId()), req.getExternalUserId());
        return toResp(channelAllowlistMapper.selectById(e.getId()));
    }

    /**
     * 批量添加白名单。
     */
    @PostMapping("/batch")
    public Map<String, Object> batchCreate(@PathVariable Long botId, @RequestBody Map<String, Object> body) {
        String ids = (String) body.get("externalUserIds");
        Long channelId = body.get("channelId") != null ? Long.valueOf(body.get("channelId").toString()) : null;
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());

        if (ids == null || ids.isBlank()) {
            throw new IllegalArgumentException("externalUserIds 不能为空");
        }

        // 查找 platform
        String platform = "UNKNOWN";
        if (channelId != null) {
            BotChannelEntity ch = botChannelMapper.selectById(channelId);
            if (ch != null) platform = ch.getPlatform();
        } else {
            BotChannelEntity ch = botChannelMapper.selectOne(
                    new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, botId).last("LIMIT 1"));
            if (ch != null) {
                platform = ch.getPlatform();
                channelId = ch.getId();
            }
        }

        int created = 0;
        for (String id : ids.split("[,\\n]")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty()) continue;
            ChannelAllowlistEntity e = new ChannelAllowlistEntity();
            e.setBotId(botId);
            e.setChannelId(channelId);
            e.setPlatform(platform);
            e.setExternalUserId(trimmed);
            e.setEnabled(enabled);
            channelAllowlistMapper.insert(e);
            created++;
        }
        auditLogService.log("BATCH_CREATE", "ALLOWLIST", String.valueOf(botId), "批量添加 " + created + " 条");
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        return result;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long botId, @PathVariable Long id) {
        ChannelAllowlistEntity e = channelAllowlistMapper.selectById(id);
        if (e == null || !botId.equals(e.getBotId())) {
            throw new NotFoundException("allowlist entry not found");
        }
        channelAllowlistMapper.deleteById(id);
        auditLogService.log("DELETE", "ALLOWLIST", String.valueOf(id), null);
    }

    private ChannelAllowlistResponse toResp(ChannelAllowlistEntity e) {
        return ChannelAllowlistResponse.builder()
                .id(e.getId())
                .channelId(e.getChannelId())
                .platform(e.getPlatform())
                .externalUserId(e.getExternalUserId())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .build();
    }
}

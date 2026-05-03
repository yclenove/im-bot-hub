package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.admin.dto.BotCreateRequest;
import com.sov.imhub.admin.dto.BotDetailResponse;
import com.sov.imhub.admin.dto.BotResponse;
import com.sov.imhub.admin.dto.BotUpdateRequest;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.domain.CommandLogEntity;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.mapstruct.AdminDtoMapper;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.mapper.CommandLogMapper;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 机器人管理 API（纯逻辑分组）。
 *
 * <p>机器人是 IM Bot Hub 的核心分组单元，不含任何平台专属配置。
 * 一个机器人可关联多个渠道（{@link AdminChannelController}），
 * 每个渠道对应一个 IM 平台的接入配置。</p>
 *
 * <p>平台凭证通过 {@link AdminBotChannelController} 管理。</p>
 *
 * @see AdminChannelController
 * @see AdminBotChannelController
 */
@RestController
@RequestMapping("/api/admin/bots")
@RequiredArgsConstructor
public class AdminBotController {

    private final BotMapper botMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final BotChannelMapper botChannelMapper;
    private final CommandLogMapper commandLogMapper;
    private final AdminDtoMapper adminDtoMapper;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<BotResponse> list() {
        return botMapper.selectList(new LambdaQueryWrapper<Bot>().orderByDesc(Bot::getId)).stream()
                .map(adminDtoMapper::toBotResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public BotResponse get(@PathVariable Long id) {
        Bot b = botMapper.selectById(id);
        if (b == null) {
            throw new NotFoundException("bot not found");
        }
        return adminDtoMapper.toBotResponse(b);
    }

    /**
     * 机器人详情：含关联渠道、查询定义、最近命令日志。
     */
    @GetMapping("/{id}/detail")
    public BotDetailResponse detail(@PathVariable Long id) {
        Bot b = botMapper.selectById(id);
        if (b == null) {
            throw new NotFoundException("bot not found");
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<BotDetailResponse.ChannelSummary> channels = botChannelMapper.selectList(
                new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, id))
                .stream()
                .map(c -> BotDetailResponse.ChannelSummary.builder()
                        .id(c.getId())
                        .platform(c.getPlatform())
                        .name(c.getName())
                        .enabled(Boolean.TRUE.equals(c.getEnabled()))
                        .build())
                .collect(Collectors.toList());

        List<BotDetailResponse.QuerySummary> queries = queryDefinitionMapper.selectList(
                new LambdaQueryWrapper<QueryDefinitionEntity>().eq(QueryDefinitionEntity::getBotId, id))
                .stream()
                .map(q -> BotDetailResponse.QuerySummary.builder()
                        .id(q.getId())
                        .command(q.getCommand())
                        .name(q.getName())
                        .queryMode(q.getQueryMode())
                        .datasourceId(q.getDatasourceId())
                        .enabled(Boolean.TRUE.equals(q.getEnabled()))
                        .build())
                .collect(Collectors.toList());

        List<BotDetailResponse.LogSummary> logs = commandLogMapper.selectList(
                new LambdaQueryWrapper<CommandLogEntity>()
                        .eq(CommandLogEntity::getBotId, id)
                        .orderByDesc(CommandLogEntity::getId)
                        .last("LIMIT 10"))
                .stream()
                .map(l -> BotDetailResponse.LogSummary.builder()
                        .id(l.getId())
                        .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().format(fmt) : "")
                        .platform(l.getPlatform())
                        .command(l.getCommand())
                        .success(Boolean.TRUE.equals(l.getSuccess()))
                        .errorKind(l.getErrorKind())
                        .durationMs(l.getDurationMs())
                        .build())
                .collect(Collectors.toList());

        return BotDetailResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .enabled(Boolean.TRUE.equals(b.getEnabled()))
                .channels(channels)
                .queries(queries)
                .recentLogs(logs)
                .build();
    }

    @PostMapping
    public BotResponse create(@Valid @RequestBody BotCreateRequest req) {
        Bot b = new Bot();
        b.setName(req.getName());
        b.setEnabled(req.isEnabled());
        botMapper.insert(b);
        auditLogService.log("CREATE", "BOT", String.valueOf(b.getId()), b.getName());
        return adminDtoMapper.toBotResponse(b);
    }

    @PutMapping("/{id}")
    public BotResponse update(@PathVariable Long id, @RequestBody BotUpdateRequest req) {
        Bot b = botMapper.selectById(id);
        if (b == null) {
            throw new NotFoundException("bot not found");
        }
        if (req.getName() != null) {
            b.setName(req.getName());
        }
        if (req.getEnabled() != null) {
            b.setEnabled(req.getEnabled());
        }
        botMapper.updateById(b);
        auditLogService.log("UPDATE", "BOT", String.valueOf(id), "update");
        return adminDtoMapper.toBotResponse(botMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        Bot b = botMapper.selectById(id);
        if (b == null) {
            throw new NotFoundException("bot not found");
        }
        LocalDateTime now = LocalDateTime.now();
        // 软删除关联的查询定义
        queryDefinitionMapper.selectList(new LambdaQueryWrapper<QueryDefinitionEntity>().eq(QueryDefinitionEntity::getBotId, id))
                .forEach(q -> {
                    q.setDeletedAt(now);
                    q.setDeleteToken(q.getId());
                    q.setDeleted(1);
                    queryDefinitionMapper.update(
                            q,
                            new LambdaQueryWrapper<QueryDefinitionEntity>().eq(QueryDefinitionEntity::getId, q.getId())
                    );
                });
        // 软删除关联的渠道
        botChannelMapper.selectList(new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, id))
                .forEach(channel -> {
                    channel.setDeletedAt(now);
                    channel.setDeleted(1);
                    botChannelMapper.update(
                            channel,
                            new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getId, channel.getId())
                    );
                });
        // 软删除机器人
        b.setDeletedAt(now);
        b.setDeleted(1);
        botMapper.updateById(b);
        auditLogService.log("DELETE", "BOT", String.valueOf(id), b.getName());
    }
}

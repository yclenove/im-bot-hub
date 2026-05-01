package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.telegram.bot.admin.dto.BotCreateRequest;
import com.sov.telegram.bot.admin.dto.BotResponse;
import com.sov.telegram.bot.admin.dto.BotUpdateRequest;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.domain.QueryDefinitionEntity;
import com.sov.telegram.bot.mapstruct.AdminDtoMapper;
import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.mapper.QueryDefinitionMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.util.TelegramChatIdsJson;
import com.sov.telegram.bot.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bots")
@RequiredArgsConstructor
public class AdminBotController {

    private final BotMapper botMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final BotChannelMapper botChannelMapper;
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

    @PostMapping
    public BotResponse create(@Valid @RequestBody BotCreateRequest req) {
        Bot b = new Bot();
        b.setName(req.getName());
        b.setTelegramBotToken(req.getTelegramBotToken());
        b.setTelegramBotUsername(req.getTelegramBotUsername());
        if (req.getWebhookSecretToken() != null && !req.getWebhookSecretToken().isBlank()) {
            b.setWebhookSecretToken(req.getWebhookSecretToken().trim());
        }
        b.setEnabled(req.isEnabled());
        applyChatScope(b, req.getTelegramChatScope(), req.getTelegramAllowedChatIds());
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
        if (req.getTelegramBotToken() != null) {
            b.setTelegramBotToken(req.getTelegramBotToken());
        }
        if (req.getTelegramBotUsername() != null) {
            b.setTelegramBotUsername(req.getTelegramBotUsername());
        }
        if (req.getEnabled() != null) {
            b.setEnabled(req.getEnabled());
        }
        // null = leave unchanged; blank = clear; non-blank = set
        if (req.getWebhookSecretToken() != null) {
            b.setWebhookSecretToken(
                    req.getWebhookSecretToken().isBlank() ? null : req.getWebhookSecretToken().trim());
        }
        if (req.getTelegramChatScope() != null || req.getTelegramAllowedChatIds() != null) {
            String scope =
                    req.getTelegramChatScope() != null
                            ? req.getTelegramChatScope()
                            : (b.getTelegramChatScope() != null ? b.getTelegramChatScope() : "ALL");
            List<Long> ids =
                    req.getTelegramAllowedChatIds() != null
                            ? req.getTelegramAllowedChatIds()
                            : TelegramChatIdsJson.parse(b.getTelegramAllowedChatIdsJson());
            applyChatScope(b, scope, ids);
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
        botChannelMapper.selectList(new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, id))
                .forEach(channel -> {
                    channel.setDeletedAt(now);
                    channel.setDeleted(1);
                    botChannelMapper.update(
                            channel,
                            new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getId, channel.getId())
                    );
                });
        b.setDeletedAt(now);
        b.setDeleted(1);
        botMapper.updateById(b);
        auditLogService.log("DELETE", "BOT", String.valueOf(id), b.getName());
    }

    private static void applyChatScope(Bot b, String rawScope, List<Long> allowedIds) {
        String scope = rawScope == null || rawScope.isBlank() ? "ALL" : rawScope.trim().toUpperCase();
        if (!"ALL".equals(scope) && !"GROUPS_ONLY".equals(scope)) {
            throw new IllegalArgumentException("telegramChatScope 仅支持 ALL 或 GROUPS_ONLY");
        }
        b.setTelegramChatScope(scope);
        if ("GROUPS_ONLY".equals(scope)) {
            if (allowedIds == null || allowedIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "选择「仅指定群」时须填写至少一个 Telegram 群 chat_id（一般为负整数，如 -100…）");
            }
            b.setTelegramAllowedChatIdsJson(TelegramChatIdsJson.toJson(allowedIds));
        } else {
            b.setTelegramAllowedChatIdsJson(null);
        }
    }
}

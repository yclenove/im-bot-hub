package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.admin.dto.BotChannelResponse;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.domain.QueryDefinitionEntity;
import com.sov.imhub.im.lark.LarkApiClient;
import com.sov.imhub.im.lark.LarkCredentials;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.QueryDefinitionMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.ChannelTestService;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.telegram.TelegramWebhookAdminService;
import com.sov.imhub.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 全局渠道管理 API：管理所有平台的渠道配置。
 *
 * <p>渠道是平台接入单元，每个渠道对应一个 IM 平台（Telegram/飞书/钉钉/企业微信/Slack/Discord）。
 * 支持渠道的 CRUD、启停、Webhook 管理和连通性测试。</p>
 *
 * @see AdminBotController
 * @see AdminChannelAllowlistController
 */
@RestController
@RequestMapping("/api/admin/channels")
@RequiredArgsConstructor
public class AdminChannelController {

    private final BotChannelMapper botChannelMapper;
    private final QueryDefinitionMapper queryDefinitionMapper;
    private final AuditLogService auditLogService;
    private final ChannelResponseAssembler channelResponseAssembler;
    private final TelegramWebhookAdminService telegramWebhookAdminService;
    private final ChannelTestService channelTestService;
    private final LarkApiClient larkApiClient;
    private final EncryptionService encryptionService;

    @GetMapping
    public List<BotChannelResponse> listAll(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Long botId) {
        LambdaQueryWrapper<BotChannelEntity> q = new LambdaQueryWrapper<BotChannelEntity>()
                .eq(StringUtils.hasText(platform), BotChannelEntity::getPlatform, platform != null ? platform.trim().toUpperCase() : null)
                .eq(botId != null, BotChannelEntity::getBotId, botId)
                .orderByDesc(BotChannelEntity::getId);
        return botChannelMapper.selectList(q)
                .stream()
                .map(channelResponseAssembler::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{channelId}")
    public BotChannelResponse getOne(@PathVariable Long channelId) {
        BotChannelEntity e = botChannelMapper.selectById(channelId);
        if (e == null) {
            throw new NotFoundException("channel not found");
        }
        return channelResponseAssembler.toResponse(e);
    }

    @PutMapping("/{channelId}/toggle")
    public BotChannelResponse toggleEnabled(@PathVariable Long channelId) {
        BotChannelEntity e = botChannelMapper.selectById(channelId);
        if (e == null) {
            throw new NotFoundException("channel not found");
        }
        e.setEnabled(!Boolean.TRUE.equals(e.getEnabled()));
        botChannelMapper.updateById(e);
        auditLogService.log("UPDATE", "BOT_CHANNEL", String.valueOf(channelId),
                "enabled=" + e.getEnabled());
        return channelResponseAssembler.toResponse(botChannelMapper.selectById(channelId));
    }

    /**
     * Telegram 渠道：向 Telegram 注册 Webhook。
     */
    @PostMapping("/{channelId}/register-webhook")
    public Object registerWebhook(@PathVariable Long channelId, @RequestBody(required = false) java.util.Map<String, String> body) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("channel not found");
        }
        if (!"TELEGRAM".equalsIgnoreCase(channel.getPlatform())) {
            throw new IllegalArgumentException("Webhook 注册仅支持 Telegram 渠道");
        }
        String publicBaseUrl = body != null ? body.get("publicBaseUrl") : null;
        return telegramWebhookAdminService.setWebhookForChannel(channelId, publicBaseUrl);
    }

    /**
     * Telegram 渠道：查看 Webhook 状态。
     */
    @GetMapping("/{channelId}/webhook-status")
    public Object webhookStatus(@PathVariable Long channelId) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("channel not found");
        }
        if (!"TELEGRAM".equalsIgnoreCase(channel.getPlatform())) {
            throw new IllegalArgumentException("Webhook 状态查询仅支持 Telegram 渠道");
        }
        return telegramWebhookAdminService.webhookInfoForChannel(channelId);
    }

    /**
     * 向渠道发送测试消息，验证连通性。
     */
    @PostMapping("/{channelId}/test")
    public com.sov.imhub.service.test.PlatformTester.TestResult testChannel(
            @PathVariable Long channelId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String targetId = body != null ? body.get("targetId") : null;
        return channelTestService.testChannel(channelId, targetId);
    }

    /**
     * 飞书渠道：同步机器人菜单（从查询定义自动生成）。
     */
    @PostMapping("/{channelId}/sync-lark-menu")
    public java.util.Map<String, Object> syncLarkMenu(@PathVariable Long channelId) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("channel not found");
        }
        if (!"LARK".equalsIgnoreCase(channel.getPlatform())) {
            throw new IllegalArgumentException("菜单同步仅支持飞书渠道");
        }

        // 获取凭证
        String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
        LarkCredentials creds = LarkCredentials.fromJson(credPlain);
        if (creds.getAppId() == null || creds.getAppSecret() == null) {
            throw new IllegalArgumentException("飞书凭证不完整");
        }

        // 查询该机器人下所有启用的查询定义
        List<QueryDefinitionEntity> queries = queryDefinitionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QueryDefinitionEntity>()
                        .eq(QueryDefinitionEntity::getBotId, channel.getBotId())
                        .eq(QueryDefinitionEntity::getEnabled, true));

        // 构建菜单 JSON
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode menuArray = mapper.createArrayNode();
        for (QueryDefinitionEntity q : queries) {
            ObjectNode item = mapper.createObjectNode();
            item.put("name", q.getName() != null ? q.getName() : "/" + q.getCommand());
            item.put("action_type", 0); // 0 = 发送消息
            item.put("key", q.getCommand());
            // 中文名
            ObjectNode nameI18n = mapper.createObjectNode();
            nameI18n.put("zh_cn", q.getName() != null ? q.getName() : "/" + q.getCommand());
            item.set("name_i18n", nameI18n);
            menuArray.add(item);
        }

        // 调用飞书 API 设置菜单
        com.fasterxml.jackson.databind.JsonNode result = larkApiClient.setBotMenus(
                creds.getAppId(), creds.getAppSecret(), menuArray.toString());

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        if (result != null && result.path("code").asInt(-1) == 0) {
            response.put("success", true);
            response.put("message", "飞书机器人菜单已同步，共 " + queries.size() + " 个命令");
            response.put("menuCount", queries.size());
            auditLogService.log("SYNC_MENU", "BOT_CHANNEL", String.valueOf(channelId),
                    "同步 " + queries.size() + " 个菜单项");
        } else {
            response.put("success", false);
            response.put("message", "菜单同步失败: " + (result != null ? result.path("msg").asText("未知错误") : "请求失败"));
        }
        return response;
    }
}

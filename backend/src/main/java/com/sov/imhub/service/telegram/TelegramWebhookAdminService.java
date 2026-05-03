package com.sov.imhub.service.telegram;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.admin.dto.SetWebhookRequest;
import com.sov.imhub.admin.dto.SetWebhookResponse;
import com.sov.imhub.admin.dto.WebhookInfoResponse;
import com.sov.imhub.config.AppProperties;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TelegramWebhookAdminService {

    private final BotMapper botMapper;
    private final BotChannelMapper botChannelMapper;
    private final TelegramApiClient telegramApiClient;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public SetWebhookResponse setWebhook(Long botId, SetWebhookRequest req) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null) {
            throw new NotFoundException("机器人不存在");
        }
        BotChannelEntity channel = findTelegramChannel(botId);
        if (channel == null) {
            throw new IllegalArgumentException("该机器人未配置 Telegram 渠道");
        }
        String token = extractToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("该机器人 Telegram 渠道未保存有效的 Bot Token");
        }
        String secret = channel.getWebhookSecretToken();
        String base = resolvePublicBase(req != null ? req.getPublicBaseUrl() : null);
        String normalized = TelegramWebhookUrlBuilder.normalizePublicBase(base);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "请填写公网 HTTPS 基址（无末尾 /），或在配置 app.telegram.public-base-url 中设置默认值");
        }
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException("公网基址必须以 https:// 开头（Telegram 要求 Webhook 为 HTTPS）");
        }
        String webhookUrl = TelegramWebhookUrlBuilder.fullWebhookUrl(normalized, channel.getId());
        JsonNode node = telegramApiClient.setWebhook(token, webhookUrl, secret);
        boolean ok = node.path("ok").asBoolean(false);
        String desc = node.path("description").asText(ok ? "Webhook was set" : null);
        SetWebhookResponse r = new SetWebhookResponse();
        r.setTelegramOk(ok);
        r.setDescription(desc);
        r.setWebhookUrl(webhookUrl);
        auditLogService.log("WEBHOOK_SET", "BOT_CHANNEL", String.valueOf(channel.getId()), webhookUrl);
        return r;
    }

    public WebhookInfoResponse webhookInfo(Long botId) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null) {
            throw new NotFoundException("机器人不存在");
        }
        BotChannelEntity channel = findTelegramChannel(botId);
        if (channel == null) {
            throw new IllegalArgumentException("该机器人未配置 Telegram 渠道");
        }
        String token = extractToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("该机器人 Telegram 渠道未保存有效的 Bot Token");
        }
        JsonNode root = telegramApiClient.getWebhookInfo(token);
        WebhookInfoResponse vo = new WebhookInfoResponse();
        vo.setTelegramOk(root.path("ok").asBoolean(false));
        vo.setDescription(root.path("description").asText(null));
        JsonNode res = root.path("result");
        if (res.isObject()) {
            vo.setUrl(emptyToNull(res.path("url").asText("")));
            vo.setPendingUpdateCount(
                    res.path("pending_update_count").isMissingNode()
                            ? null
                            : res.path("pending_update_count").asInt());
            vo.setLastErrorMessage(emptyToNull(res.path("last_error_message").asText("")));
            vo.setLastErrorDate(
                    res.path("last_error_date").isMissingNode()
                            || res.path("last_error_date").isNull()
                            ? null
                            : res.path("last_error_date").asInt());
            vo.setMaxConnections(
                    res.path("max_connections").isMissingNode()
                            ? null
                            : res.path("max_connections").asInt());
            vo.setIpAddress(emptyToNull(res.path("ip_address").asText("")));
            if (res.has("has_custom_certificate") && !res.path("has_custom_certificate").isNull()) {
                vo.setHasCustomCertificate(res.path("has_custom_certificate").asBoolean());
            }
        }
        return vo;
    }

    /**
     * 按渠道 ID 直接注册 Webhook（供 AdminChannelController 使用）。
     */
    public SetWebhookResponse setWebhookForChannel(Long channelId, String publicBaseUrl) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("渠道不存在");
        }
        String token = extractToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("该渠道未保存有效的 Bot Token");
        }
        String secret = channel.getWebhookSecretToken();
        String base = resolvePublicBase(publicBaseUrl);
        String normalized = TelegramWebhookUrlBuilder.normalizePublicBase(base);
        if (normalized == null) {
            throw new IllegalArgumentException("请填写公网 HTTPS 基址，或在配置 app.telegram.public-base-url 中设置默认值");
        }
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException("公网基址必须以 https:// 开头");
        }
        String webhookUrl = TelegramWebhookUrlBuilder.fullWebhookUrl(normalized, channelId);
        JsonNode node = telegramApiClient.setWebhook(token, webhookUrl, secret);
        boolean ok = node.path("ok").asBoolean(false);
        String desc = node.path("description").asText(ok ? "Webhook was set" : null);
        SetWebhookResponse r = new SetWebhookResponse();
        r.setTelegramOk(ok);
        r.setDescription(desc);
        r.setWebhookUrl(webhookUrl);
        auditLogService.log("WEBHOOK_SET", "BOT_CHANNEL", String.valueOf(channelId), webhookUrl);
        return r;
    }

    /**
     * 按渠道 ID 查询 Webhook 状态（供 AdminChannelController 使用）。
     */
    public WebhookInfoResponse webhookInfoForChannel(Long channelId) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("渠道不存在");
        }
        String token = extractToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("该渠道未保存有效的 Bot Token");
        }
        JsonNode root = telegramApiClient.getWebhookInfo(token);
        WebhookInfoResponse vo = new WebhookInfoResponse();
        vo.setTelegramOk(root.path("ok").asBoolean(false));
        vo.setDescription(root.path("description").asText(null));
        JsonNode res = root.path("result");
        if (res.isObject()) {
            vo.setUrl(emptyToNull(res.path("url").asText("")));
            vo.setPendingUpdateCount(
                    res.path("pending_update_count").isMissingNode() ? null : res.path("pending_update_count").asInt());
            vo.setLastErrorMessage(emptyToNull(res.path("last_error_message").asText("")));
            vo.setLastErrorDate(
                    res.path("last_error_date").isMissingNode() || res.path("last_error_date").isNull()
                            ? null : res.path("last_error_date").asInt());
            vo.setMaxConnections(
                    res.path("max_connections").isMissingNode() ? null : res.path("max_connections").asInt());
            vo.setIpAddress(emptyToNull(res.path("ip_address").asText("")));
            if (res.has("has_custom_certificate") && !res.path("has_custom_certificate").isNull()) {
                vo.setHasCustomCertificate(res.path("has_custom_certificate").asBoolean());
            }
        }
        return vo;
    }

    private BotChannelEntity findTelegramChannel(Long botId) {
        return botChannelMapper.selectOne(
                new LambdaQueryWrapper<BotChannelEntity>()
                        .eq(BotChannelEntity::getBotId, botId)
                        .eq(BotChannelEntity::getPlatform, "TELEGRAM")
                        .last("LIMIT 1"));
    }

    private String extractToken(BotChannelEntity channel) {
        try {
            String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
            JsonNode node = objectMapper.readTree(credPlain);
            return node.has("token") ? node.get("token").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    private String resolvePublicBase(String requestBase) {
        String fromReq = TelegramWebhookUrlBuilder.normalizePublicBase(requestBase);
        if (fromReq != null) {
            return fromReq;
        }
        String fromConfig = appProperties.getTelegram().getPublicBaseUrl();
        return TelegramWebhookUrlBuilder.normalizePublicBase(fromConfig);
    }
}

package com.sov.imhub.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.imhub.admin.dto.SetWebhookRequest;
import com.sov.imhub.admin.dto.SetWebhookResponse;
import com.sov.imhub.admin.dto.WebhookInfoResponse;
import com.sov.imhub.config.AppProperties;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TelegramWebhookAdminService {

    private final BotMapper botMapper;
    private final TelegramApiClient telegramApiClient;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    public SetWebhookResponse setWebhook(Long botId, SetWebhookRequest req) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null) {
            throw new NotFoundException("机器人不存在");
        }
        if (bot.getTelegramBotToken() == null || bot.getTelegramBotToken().isBlank()) {
            throw new IllegalArgumentException("该机器人未保存有效的 Bot Token，无法调用 Telegram");
        }
        String base =
                resolvePublicBase(req != null ? req.getPublicBaseUrl() : null);
        String normalized = TelegramWebhookUrlBuilder.normalizePublicBase(base);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "请填写公网 HTTPS 基址（无末尾 /），或在配置 app.telegram.public-base-url 中设置默认值；例如 https://xxxx.trycloudflare.com");
        }
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException("公网基址必须以 https:// 开头（Telegram 要求 Webhook 为 HTTPS）");
        }
        String webhookUrl = TelegramWebhookUrlBuilder.fullWebhookUrl(normalized, botId);
        JsonNode node =
                telegramApiClient.setWebhook(
                        bot.getTelegramBotToken(), webhookUrl, bot.getWebhookSecretToken());
        boolean ok = node.path("ok").asBoolean(false);
        String desc = node.path("description").asText(ok ? "Webhook was set" : null);
        SetWebhookResponse r = new SetWebhookResponse();
        r.setTelegramOk(ok);
        r.setDescription(desc);
        r.setWebhookUrl(webhookUrl);
        auditLogService.log("WEBHOOK_SET", "BOT", String.valueOf(botId), webhookUrl);
        return r;
    }

    public WebhookInfoResponse webhookInfo(Long botId) {
        Bot bot = botMapper.selectById(botId);
        if (bot == null) {
            throw new NotFoundException("机器人不存在");
        }
        if (bot.getTelegramBotToken() == null || bot.getTelegramBotToken().isBlank()) {
            throw new IllegalArgumentException("该机器人未保存有效的 Bot Token");
        }
        JsonNode root = telegramApiClient.getWebhookInfo(bot.getTelegramBotToken());
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

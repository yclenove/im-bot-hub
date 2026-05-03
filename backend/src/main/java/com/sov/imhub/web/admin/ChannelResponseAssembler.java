package com.sov.imhub.web.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.admin.dto.BotChannelResponse;
import com.sov.imhub.config.AppProperties;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.dingtalk.DingTalkCredentials;
import com.sov.imhub.im.lark.LarkCredentials;
import com.sov.imhub.im.wework.WeWorkCredentials;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 渠道响应组装器：将 BotChannelEntity 转换为 BotChannelResponse。
 *
 * <p>负责生成 Webhook URL 和凭证摘要（脱敏显示）。</p>
 */
@Component
@RequiredArgsConstructor
public class ChannelResponseAssembler {

    private final AppProperties appProperties;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    /**
     * 将渠道实体转换为响应 DTO。
     *
     * @param e 渠道实体
     * @return 渠道响应（含 Webhook URL 和凭证摘要）
     */
    public BotChannelResponse toResponse(BotChannelEntity e) {
        String webhookUrl = buildWebhookUrl(e);
        String summary = buildCredentialSummary(e);

        return BotChannelResponse.builder()
                .id(e.getId())
                .botId(e.getBotId())
                .platform(e.getPlatform())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .webhookUrl(webhookUrl)
                .credentialsSummary(summary)
                .build();
    }

    /**
     * 构建 Webhook URL。
     */
    private String buildWebhookUrl(BotChannelEntity e) {
        String base = appProperties.getTelegram().getPublicBaseUrl();
        String pl = e.getPlatform() == null ? "" : e.getPlatform().toUpperCase(Locale.ROOT);
        String path = switch (pl) {
            case "DINGTALK" -> "/api/webhook/dingtalk/" + e.getId();
            case "WEWORK" -> "/api/webhook/wework/" + e.getId();
            case "LARK" -> "/api/webhook/lark/" + e.getId();
            case "SLACK" -> "/api/webhook/slack/" + e.getId();
            case "DISCORD" -> "/api/webhook/discord/" + e.getId();
            case "TELEGRAM" -> "/api/webhook/telegram/" + e.getId();
            default -> "/api/webhook/unknown/" + e.getId();
        };

        if (StringUtils.hasText(base)) {
            String norm = base.trim().replaceAll("/+$", "");
            return norm + path;
        }
        return "(请配置 app.telegram.public-base-url 后显示完整 URL)" + path;
    }

    /**
     * 构建凭证摘要（脱敏显示）。
     */
    private String buildCredentialSummary(BotChannelEntity e) {
        String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, e.getCredentialsJson());
        String platform = e.getPlatform();
        if (platform == null) return "";

        return switch (platform.toUpperCase(Locale.ROOT)) {
            case "LARK" -> maskLark(credPlain);
            case "DINGTALK" -> maskDingTalk(credPlain);
            case "WEWORK" -> maskWeWork(credPlain);
            case "SLACK", "DISCORD" -> maskToken(credPlain, "botToken");
            case "TELEGRAM" -> maskToken(credPlain, "token");
            default -> "";
        };
    }

    private String maskLark(String credPlain) {
        LarkCredentials c = LarkCredentials.fromJson(credPlain);
        String aid = c.getAppId() == null ? "" : c.getAppId();
        return aid.length() > 6 ? aid.substring(0, 3) + "…" + aid.substring(aid.length() - 2) : aid;
    }

    private String maskDingTalk(String credPlain) {
        DingTalkCredentials c = DingTalkCredentials.fromJson(credPlain);
        String sec = c.getAppSecret() == null ? "" : c.getAppSecret();
        return sec.length() > 4 ? "…" + sec.substring(sec.length() - 4) : (sec.isEmpty() ? "" : "****");
    }

    private String maskWeWork(String credPlain) {
        WeWorkCredentials c = WeWorkCredentials.fromJson(credPlain);
        String cid = c.getCorpId() == null ? "" : c.getCorpId();
        return cid.length() > 8 ? cid.substring(0, 4) + "…" + cid.substring(cid.length() - 2) : cid;
    }

    private String maskToken(String credPlain, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(credPlain);
            String token = node.has(fieldName) ? node.get(fieldName).asText() : "";
            return token.length() > 10 ? token.substring(0, 6) + "…" + token.substring(token.length() - 4) : token;
        } catch (Exception e) {
            return "****";
        }
    }
}

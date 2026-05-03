package com.sov.imhub.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sov.imhub.admin.dto.BotChannelCreateRequest;
import com.sov.imhub.admin.dto.BotChannelResponse;
import com.sov.imhub.config.AppProperties;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.dingtalk.DingTalkCredentials;
import com.sov.imhub.im.wework.WeWorkCredentials;
import com.sov.imhub.im.lark.LarkCredentials;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.AuditLogService;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bots/{botId}/channels")
@RequiredArgsConstructor
public class AdminBotChannelController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BotMapper botMapper;
    private final BotChannelMapper botChannelMapper;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;
    private final EncryptionService encryptionService;

    @GetMapping
    public List<BotChannelResponse> list(@PathVariable long botId) {
        ensureBot(botId);
        return botChannelMapper
                .selectList(new LambdaQueryWrapper<BotChannelEntity>()
                        .eq(BotChannelEntity::getBotId, botId)
                        .orderByDesc(BotChannelEntity::getId))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    public BotChannelResponse create(@PathVariable long botId, @Valid @RequestBody BotChannelCreateRequest req) {
        ensureBot(botId);
        String p = req.getPlatform().trim().toUpperCase();
        try {
            BotChannelEntity e = new BotChannelEntity();
            e.setBotId(botId);
            e.setEnabled(true);
            if ("LARK".equals(p)) {
                if (!StringUtils.hasText(req.getAppId()) || !StringUtils.hasText(req.getAppSecret())) {
                    throw new IllegalArgumentException("LARK 渠道需提供 appId 与 appSecret");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("appId", req.getAppId().trim());
                cred.put("appSecret", req.getAppSecret().trim());
                e.setPlatform("LARK");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
            } else if ("DINGTALK".equals(p)) {
                if (!StringUtils.hasText(req.getAppSecret())) {
                    throw new IllegalArgumentException("DINGTALK 渠道需提供 appSecret（钉钉机器人 AppSecret，用于 Outgoing 签名校验）");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("appSecret", req.getAppSecret().trim());
                e.setPlatform("DINGTALK");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
            } else if ("WEWORK".equals(p)) {
                if (!StringUtils.hasText(req.getCorpId())
                        || req.getAgentId() == null
                        || !StringUtils.hasText(req.getCallbackToken())
                        || !StringUtils.hasText(req.getEncodingAesKey())) {
                    throw new IllegalArgumentException(
                            "WEWORK 渠道需提供 corpId、agentId、callbackToken、encodingAesKey（企业微信自建应用「接收消息」配置）");
                }
                if (req.getEncodingAesKey().trim().length() < 43) {
                    throw new IllegalArgumentException("encodingAesKey 长度不符合企业微信规范（应为 43 位）");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("corpId", req.getCorpId().trim());
                cred.put("agentId", req.getAgentId());
                cred.put("token", req.getCallbackToken().trim());
                cred.put("encodingAesKey", req.getEncodingAesKey().trim());
                e.setPlatform("WEWORK");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
            } else if ("TELEGRAM".equals(p)) {
                if (!StringUtils.hasText(req.getBotToken())) {
                    throw new IllegalArgumentException("TELEGRAM 渠道需提供 botToken");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("token", req.getBotToken().trim());
                if (StringUtils.hasText(req.getTelegramBotUsername())) {
                    cred.put("username", req.getTelegramBotUsername().trim());
                }
                e.setPlatform("TELEGRAM");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
                if (StringUtils.hasText(req.getWebhookSecretToken())) {
                    e.setWebhookSecretToken(req.getWebhookSecretToken().trim());
                }
                String scope = req.getChatScope() == null ? "ALL" : req.getChatScope().trim().toUpperCase();
                e.setChatScope(scope);
                if ("GROUPS_ONLY".equals(scope) && req.getAllowedChatIds() != null && !req.getAllowedChatIds().isEmpty()) {
                    try {
                        e.setAllowedChatIdsJson(MAPPER.writeValueAsString(req.getAllowedChatIds()));
                    } catch (Exception ex) {
                        throw new IllegalStateException("序列化 allowedChatIds 失败", ex);
                    }
                }
            } else if ("SLACK".equals(p)) {
                if (!StringUtils.hasText(req.getBotToken())) {
                    throw new IllegalArgumentException("SLACK 渠道需提供 botToken（Slack App Bot User OAuth Token）");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("botToken", req.getBotToken().trim());
                if (StringUtils.hasText(req.getSigningSecret())) {
                    cred.put("signingSecret", req.getSigningSecret().trim());
                }
                e.setPlatform("SLACK");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
            } else if ("DISCORD".equals(p)) {
                if (!StringUtils.hasText(req.getBotToken())) {
                    throw new IllegalArgumentException("DISCORD 渠道需提供 botToken（Discord Bot Token）");
                }
                ObjectNode cred = MAPPER.createObjectNode();
                cred.put("botToken", req.getBotToken().trim());
                if (StringUtils.hasText(req.getPublicKey())) {
                    cred.put("publicKey", req.getPublicKey().trim());
                }
                e.setPlatform("DISCORD");
                e.setCredentialsJson(ChannelCredentialsCrypto.seal(encryptionService, cred.toString()));
            } else {
                throw new IllegalArgumentException("不支持的平台: " + p + "（当前支持 TELEGRAM、LARK、DINGTALK、WEWORK、SLACK、DISCORD）");
            }
            botChannelMapper.insert(e);
            auditLogService.log("CREATE", "BOT_CHANNEL", String.valueOf(e.getId()), e.getPlatform() + " bot=" + botId);
            return toResponse(botChannelMapper.selectById(e.getId()));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IllegalStateException("保存渠道失败", e);
        }
    }

    @DeleteMapping("/{channelId}")
    public void delete(@PathVariable long botId, @PathVariable long channelId) {
        ensureBot(botId);
        BotChannelEntity e = botChannelMapper.selectById(channelId);
        if (e == null || !Objects.equals(botId, e.getBotId())) {
            throw new NotFoundException("channel not found");
        }
        e.setDeletedAt(LocalDateTime.now());
        e.setDeleted(1);
        botChannelMapper.updateById(e);
        auditLogService.log("DELETE", "BOT_CHANNEL", String.valueOf(channelId), e.getPlatform());
    }

    private void ensureBot(long botId) {
        Bot b = botMapper.selectById(botId);
        if (b == null) {
            throw new NotFoundException("bot not found");
        }
    }

    private BotChannelResponse toResponse(BotChannelEntity e) {
        String base = appProperties.getTelegram().getPublicBaseUrl();
        String pl = e.getPlatform() == null ? "" : e.getPlatform().toUpperCase(Locale.ROOT);
        String path =
                switch (pl) {
                    case "TELEGRAM" -> "/api/webhook/telegram/" + e.getId();
                    case "DINGTALK" -> "/api/webhook/dingtalk/" + e.getId();
                    case "WEWORK" -> "/api/webhook/wework/" + e.getId();
                    case "LARK" -> "/api/webhook/lark/" + e.getId();
                    case "SLACK" -> "/api/webhook/slack/" + e.getId();
                    case "DISCORD" -> "/api/webhook/discord/" + e.getId();
                    default -> "/api/webhook/unknown/" + e.getId();
                };
        String webhookUrl = "";
        if (StringUtils.hasText(base)) {
            String norm = base.trim().replaceAll("/+$", "");
            webhookUrl = norm + path;
        } else {
            webhookUrl = "(请配置 app.telegram.public-base-url 后显示完整 URL)" + path;
        }
        String summary = "";
        String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, e.getCredentialsJson());
        if ("TELEGRAM".equalsIgnoreCase(e.getPlatform())) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(credPlain);
                String token = node.has("token") ? node.get("token").asText() : "";
                summary = token.length() > 10 ? token.substring(0, 6) + "…" + token.substring(token.length() - 4) : token;
            } catch (Exception ignored) {
                summary = "****";
            }
        } else if ("LARK".equalsIgnoreCase(e.getPlatform())) {
            LarkCredentials c = LarkCredentials.fromJson(credPlain);
            String aid = c.getAppId() == null ? "" : c.getAppId();
            summary = aid.length() > 6 ? aid.substring(0, 3) + "…" + aid.substring(aid.length() - 2) : aid;
        } else if ("DINGTALK".equalsIgnoreCase(e.getPlatform())) {
            DingTalkCredentials c = DingTalkCredentials.fromJson(credPlain);
            String sec = c.getAppSecret() == null ? "" : c.getAppSecret();
            summary =
                    sec.length() > 4
                            ? "…" + sec.substring(sec.length() - 4)
                            : (sec.isEmpty() ? "" : "****");
        } else if ("WEWORK".equalsIgnoreCase(e.getPlatform())) {
            WeWorkCredentials c = WeWorkCredentials.fromJson(credPlain);
            String cid = c.getCorpId() == null ? "" : c.getCorpId();
            summary = cid.length() > 8 ? cid.substring(0, 4) + "…" + cid.substring(cid.length() - 2) : cid;
        } else if ("SLACK".equalsIgnoreCase(e.getPlatform()) || "DISCORD".equalsIgnoreCase(e.getPlatform())) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(credPlain);
                String token = node.has("botToken") ? node.get("botToken").asText() : "";
                summary = token.length() > 10 ? token.substring(0, 6) + "…" + token.substring(token.length() - 4) : token;
            } catch (Exception ignored) {
                summary = "****";
            }
        }
        return BotChannelResponse.builder()
                .id(e.getId())
                .botId(e.getBotId())
                .platform(e.getPlatform())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .webhookUrl(webhookUrl)
                .credentialsSummary(summary)
                .build();
    }
}

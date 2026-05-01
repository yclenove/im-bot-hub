package com.sov.telegram.bot.web.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sov.telegram.bot.admin.dto.BotChannelCreateRequest;
import com.sov.telegram.bot.admin.dto.BotChannelResponse;
import com.sov.telegram.bot.config.AppProperties;
import com.sov.telegram.bot.domain.Bot;
import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.im.dingtalk.DingTalkCredentials;
import com.sov.telegram.bot.im.wework.WeWorkCredentials;
import com.sov.telegram.bot.im.lark.LarkCredentials;
import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.service.AuditLogService;
import com.sov.telegram.bot.service.crypto.ChannelCredentialsCrypto;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.sov.telegram.bot.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
                .selectList(new LambdaQueryWrapper<BotChannelEntity>().eq(BotChannelEntity::getBotId, botId))
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
            } else {
                throw new IllegalArgumentException("不支持的平台: " + p + "（当前支持 LARK、DINGTALK、WEWORK）");
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
        botChannelMapper.deleteById(channelId);
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
                    case "DINGTALK" -> "/api/webhook/dingtalk/" + e.getId();
                    case "WEWORK" -> "/api/webhook/wework/" + e.getId();
                    case "LARK" -> "/api/webhook/lark/" + e.getId();
                    default -> "/api/webhook/lark/" + e.getId();
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
        if ("LARK".equalsIgnoreCase(e.getPlatform())) {
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

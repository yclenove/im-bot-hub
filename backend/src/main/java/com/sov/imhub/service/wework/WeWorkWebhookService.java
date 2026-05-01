package com.sov.imhub.service.wework;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.im.InboundCommandContext;
import com.sov.imhub.im.WeWorkOutboundMessenger;
import com.sov.imhub.im.WeWorkReplyHolder;
import com.sov.imhub.im.wework.WeWorkCredentials;
import com.sov.imhub.im.wework.WeWorkIncomingXml;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.mapper.BotMapper;
import com.sov.imhub.service.QueryOrchestrationService;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.im.ImCommandText;
import com.sov.imhub.service.telegram.TelegramCommandParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxRuntimeException;
import me.chanjar.weixin.common.util.crypto.WxCryptUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeWorkWebhookService {

    private final BotChannelMapper botChannelMapper;
    private final BotMapper botMapper;
    private final QueryOrchestrationService queryOrchestrationService;
    private final EncryptionService encryptionService;

    public record WeWorkWebhookOutcome(int httpStatus, String body, MediaType contentType) {}

    /** 企业微信「接收消息」URL 验证（GET）：返回解密后的 echostr 明文。 */
    public String verifyUrl(
            long channelId, String msgSignature, String timestamp, String nonce, String echostr) {
        BotChannelEntity ch = loadWeWorkChannel(channelId);
        if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
            throw new IllegalArgumentException("channel not found or disabled");
        }
        WeWorkCredentials cred = parseCred(ch);
        if (!credentialsComplete(cred)) {
            throw new IllegalArgumentException("incomplete credentials");
        }
        WxCryptUtil crypt = new WxCryptUtil(cred.getToken(), cred.getEncodingAesKey(), cred.getCorpId());
        return crypt.decryptContent(msgSignature, timestamp, nonce, echostr);
    }

    public WeWorkWebhookOutcome handlePost(
            long channelId, String msgSignature, String timestamp, String nonce, String rawBody) {
        BotChannelEntity ch = loadWeWorkChannel(channelId);
        if (ch == null || !Boolean.TRUE.equals(ch.getEnabled())) {
            return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
        }
        Bot bot = botMapper.selectById(ch.getBotId());
        if (bot == null || !Boolean.TRUE.equals(bot.getEnabled())) {
            return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
        }
        WeWorkCredentials cred = parseCred(ch);
        if (!credentialsComplete(cred)) {
            return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
        }
        try {
            WxCryptUtil crypt = new WxCryptUtil(cred.getToken(), cred.getEncodingAesKey(), cred.getCorpId());
            String plain = crypt.decryptXml(msgSignature, timestamp, nonce, rawBody);
            WeWorkIncomingXml in = WeWorkIncomingXml.parse(plain);
            if (!in.agentId().isBlank()
                    && cred.getAgentId() != null
                    && !cred.getAgentId().toString().equals(in.agentId())) {
                return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
            }
            if (!"text".equalsIgnoreCase(in.msgType())) {
                return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
            }
            String slice = ImCommandText.sliceFromFirstSlash(in.content());
            if (slice.isBlank()) {
                return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
            }
            TelegramCommandParser.Parsed parsed = TelegramCommandParser.parse(slice);
            if (parsed.command().isEmpty()) {
                return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
            }
            WeWorkReplyHolder holder = new WeWorkReplyHolder();
            InboundCommandContext ctx =
                    new InboundCommandContext(
                            ch.getBotId(),
                            ImPlatform.WEWORK,
                            ch.getId(),
                            null,
                            0L,
                            0L,
                            in.fromUserName(),
                            "",
                            parsed,
                            null);
            queryOrchestrationService.dispatch(
                    ctx,
                    new WeWorkOutboundMessenger(
                            holder, crypt, in.fromUserName(), in.toUserName()));
            MediaType ct =
                    holder.isEncrypted()
                            ? MediaType.parseMediaType("application/xml;charset=UTF-8")
                            : MediaType.TEXT_PLAIN;
            return new WeWorkWebhookOutcome(200, holder.getBody(), ct);
        } catch (WxRuntimeException e) {
            log.warn("WeWork webhook crypto failed channelId={}: {}", channelId, e.toString());
            return new WeWorkWebhookOutcome(403, "", MediaType.TEXT_PLAIN);
        } catch (Exception e) {
            log.warn("WeWork webhook handle failed channelId={}: {}", channelId, e.toString());
            return new WeWorkWebhookOutcome(200, "success", MediaType.TEXT_PLAIN);
        }
    }

    private BotChannelEntity loadWeWorkChannel(long channelId) {
        return botChannelMapper.selectOne(
                new LambdaQueryWrapper<BotChannelEntity>()
                        .eq(BotChannelEntity::getId, channelId)
                        .eq(BotChannelEntity::getPlatform, "WEWORK"));
    }

    private WeWorkCredentials parseCred(BotChannelEntity ch) {
        return WeWorkCredentials.fromJson(
                ChannelCredentialsCrypto.unwrap(encryptionService, ch.getCredentialsJson()));
    }

    private static boolean credentialsComplete(WeWorkCredentials c) {
        return c.getCorpId() != null
                && !c.getCorpId().isBlank()
                && c.getAgentId() != null
                && c.getToken() != null
                && !c.getToken().isBlank()
                && c.getEncodingAesKey() != null
                && c.getEncodingAesKey().trim().length() >= 43;
    }
}

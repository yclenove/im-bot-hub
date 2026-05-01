package com.sov.telegram.bot.im;

import com.sov.telegram.bot.domain.BotChannelEntity;
import com.sov.telegram.bot.im.lark.LarkApiClient;
import com.sov.telegram.bot.im.lark.LarkCredentials;
import com.sov.telegram.bot.service.crypto.ChannelCredentialsCrypto;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class LarkOutboundMessenger implements OutboundMessenger {

    private final LarkApiClient larkApiClient;
    private final InboundCommandContext ctx;
    private final BotChannelEntity channel;
    private final EncryptionService encryptionService;

    private LarkCredentials creds() {
        return LarkCredentials.fromJson(
                ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson()));
    }

    private void sendPlain(String text) {
        LarkCredentials c = creds();
        if (c.getAppId() == null || c.getAppSecret() == null) {
            return;
        }
        String chatId = ctx.externalChatId();
        if (chatId != null && !chatId.isBlank() && chatId.startsWith("oc_")) {
            larkApiClient.sendText(c.getAppId(), c.getAppSecret(), chatId.trim(), "chat_id", text);
        } else {
            larkApiClient.sendText(c.getAppId(), c.getAppSecret(), ctx.externalUserId(), "open_id", text);
        }
    }

    @Override
    public void sendRateLimited() {
        sendPlain("请求过于频繁，请稍后再试。");
    }

    @Override
    public void sendNotAllowed() {
        sendPlain("你没有权限使用此机器人。");
    }

    @Override
    public void sendUnknownCommand(String command) {
        sendPlain("未知命令：/" + command);
    }

    @Override
    public void sendMissingParam(String paramName) {
        sendPlain("缺少参数：" + paramName);
    }

    @Override
    public void sendParamUsageReminder(String usageTelegramHtml, String usagePlain) {
        sendPlain(usagePlain);
    }

    @Override
    public void sendHelp(String helpTelegramHtml, String helpPlain) {
        sendPlain(helpPlain);
    }

    @Override
    public void sendQueryResult(String bodyTelegramHtml, String bodyPlain) {
        sendPlain(bodyPlain);
    }

    @Override
    public void sendQueryFailed() {
        sendPlain("查询执行失败，请稍后再试或联系管理员。");
    }
}

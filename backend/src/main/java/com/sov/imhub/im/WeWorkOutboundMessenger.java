package com.sov.imhub.im;

import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.util.crypto.WxCryptUtil;

@RequiredArgsConstructor
public final class WeWorkOutboundMessenger implements OutboundMessenger {

    private final WeWorkReplyHolder holder;
    private final WxCryptUtil crypt;
    private final String replyToUser;
    private final String replyFromCorp;

    private static String cdataSafe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("]]>", "]]]]><![CDATA[>");
    }

    private void sendPlain(String text) {
        long ts = System.currentTimeMillis() / 1000L;
        String inner =
                "<xml>"
                        + "<ToUserName><![CDATA["
                        + cdataSafe(replyToUser)
                        + "]]></ToUserName>"
                        + "<FromUserName><![CDATA["
                        + cdataSafe(replyFromCorp)
                        + "]]></FromUserName>"
                        + "<CreateTime>"
                        + ts
                        + "</CreateTime>"
                        + "<MsgType><![CDATA[text]]></MsgType>"
                        + "<Content><![CDATA["
                        + cdataSafe(text)
                        + "]]></Content>"
                        + "</xml>";
        holder.setEncryptedXml(crypt.encrypt(inner));
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

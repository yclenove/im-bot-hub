package com.sov.imhub.im;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DingTalkOutgoingMessenger implements OutboundMessenger {

    private final DingTalkReplyHolder holder;

    @Override
    public void sendRateLimited() {
        holder.setTextReply("请求过于频繁，请稍后再试。");
    }

    @Override
    public void sendNotAllowed() {
        holder.setTextReply("你没有权限使用此机器人。");
    }

    @Override
    public void sendUnknownCommand(String command) {
        holder.setTextReply("未知命令：/" + command);
    }

    @Override
    public void sendMissingParam(String paramName) {
        holder.setTextReply("缺少参数：" + paramName);
    }

    @Override
    public void sendParamUsageReminder(String usageTelegramHtml, String usagePlain) {
        holder.setTextReply(usagePlain);
    }

    @Override
    public void sendHelp(String helpTelegramHtml, String helpPlain) {
        holder.setTextReply(helpPlain);
    }

    @Override
    public void sendQueryResult(String bodyTelegramHtml, String bodyPlain) {
        holder.setTextReply(bodyPlain);
    }

    @Override
    public void sendQueryFailed() {
        holder.setTextReply("查询执行失败，请稍后再试或联系管理员。");
    }
}

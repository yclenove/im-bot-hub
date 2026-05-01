package com.sov.telegram.bot.im;

/**
 * 将编排结果发回对应 IM；实现类负责 HTML / 纯文本等格式。
 */
public interface OutboundMessenger {

    void sendRateLimited();

    void sendNotAllowed();

    void sendUnknownCommand(String command);

    void sendMissingParam(String paramName);

    /**
     * 未带任何有效参数时（例如从 Telegram 命令菜单只插入 {@code /cmd}），发用法说明而非错误样式。
     *
     * @param usageTelegramHtml Telegram HTML
     * @param usagePlain        飞书/钉钉/企微等纯文本
     */
    void sendParamUsageReminder(String usageTelegramHtml, String usagePlain);

    /** @param helpTelegramHtml Telegram HTML；@param helpPlain 纯文本（飞书等） */
    void sendHelp(String helpTelegramHtml, String helpPlain);

    /** @param bodyTelegramHtml Telegram HTML；@param bodyPlain 纯文本 */
    void sendQueryResult(String bodyTelegramHtml, String bodyPlain);

    void sendQueryFailed();
}

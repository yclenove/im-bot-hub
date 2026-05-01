package com.sov.telegram.bot.service.telegram;

/** 与 Telegram setWebhook 对外 URL 拼接相关的纯函数（便于测试）。 */
public final class TelegramWebhookUrlBuilder {

    private TelegramWebhookUrlBuilder() {}

    /**
     * 去掉首尾空白与末尾 {@code /}；空串/纯空白返回 {@code null}。
     */
    public static String normalizePublicBase(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public static String fullWebhookUrl(String normalizedPublicBase, long botId) {
        return normalizedPublicBase + "/api/webhook/" + botId;
    }
}

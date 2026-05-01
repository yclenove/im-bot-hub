package com.sov.telegram.bot.service.im;

/** 从 IM 文本中截取从首个 {@code /} 起的命令片段，供 {@code TelegramCommandParser} 使用。 */
public final class ImCommandText {

    private ImCommandText() {}

    public static String sliceFromFirstSlash(String raw) {
        if (raw == null) {
            return "";
        }
        int i = raw.indexOf('/');
        if (i < 0) {
            return "";
        }
        return raw.substring(i).trim().replaceAll("[\r\n]+", " ").replaceAll(" +", " ");
    }
}

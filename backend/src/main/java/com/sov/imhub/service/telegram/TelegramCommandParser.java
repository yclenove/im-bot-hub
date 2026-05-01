package com.sov.imhub.service.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TelegramCommandParser {

    private TelegramCommandParser() {}

    public record Parsed(String command, List<String> args) {}

    public static Parsed parse(String text) {
        if (text == null) {
            return new Parsed("", List.of());
        }
        String t = normalizeInvisibleWhitespace(text.trim());
        /**
         * 群组里常见「先 at 机器人再写命令」：{@code @my_bot /cx 1}，整段不以 {@code /} 开头。
         * 去掉句首 {@code @username } 后若剩下以 {@code /} 开头则继续解析。
         */
        t = normalizeLeadingMentionForGroupCommand(t);
        if (!t.startsWith("/")) {
            return new Parsed("", List.of());
        }
        String[] first = t.split("\\s+", 2);
        String cmdPart = first[0];
        if (cmdPart.contains("@")) {
            cmdPart = cmdPart.substring(0, cmdPart.indexOf('@'));
        }
        String command = cmdPart.startsWith("/") ? cmdPart.substring(1).toLowerCase(Locale.ROOT) : cmdPart.toLowerCase(Locale.ROOT);
        String rest = first.length > 1 ? first[1].trim() : "";
        List<String> args = new ArrayList<>();
        if (!rest.isEmpty()) {
            args.addAll(Arrays.asList(rest.split("\\s+")));
        }
        return new Parsed(command, args);
    }

    /**
     * {@code @BotName /cmd args} → {@code /cmd args}；{@code /cmd@BotName} 等形式仍由后续逻辑处理。
     */
    /** Telegram 客户端偶发使用不间断空格等，可导致 {@code split} 与命令识别异常 */
    static String normalizeInvisibleWhitespace(String t) {
        if (t.isEmpty()) {
            return t;
        }
        // \uFEFF BOM, NBSP / narrow NBSP / figure space 等改成普通空格
        String s =
                t.replace('\uFEFF', ' ')
                        .replace('\u00A0', ' ')
                        .replace('\u202F', ' ')
                        .replace('\u2007', ' ');
        return s;
    }

    static String normalizeLeadingMentionForGroupCommand(String trimmed) {
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.startsWith("@")) {
            int slash = trimmed.indexOf('/');
            if (slash > 0) {
                return trimmed.substring(slash).trim();
            }
            return trimmed;
        }
        return trimmed;
    }
}

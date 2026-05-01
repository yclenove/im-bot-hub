package com.sov.telegram.bot.im;

import java.util.LinkedHashMap;
import java.util.Map;

/** 钉钉 Outgoing 通过同步 HTTP 响应体回包，由 Controller 序列化为 JSON。 */
public final class DingTalkReplyHolder {

    private static final int MAX_CONTENT_CHARS = 18_000;

    private volatile String textContent;

    public void setTextReply(String text) {
        if (text == null) {
            this.textContent = "";
            return;
        }
        String t = text;
        if (t.length() > MAX_CONTENT_CHARS) {
            t = t.substring(0, MAX_CONTENT_CHARS) + "\n…(已截断)";
        }
        this.textContent = t;
    }

    public boolean hasReply() {
        return textContent != null && !textContent.isBlank();
    }

    public Map<String, Object> toResponseBody() {
        if (!hasReply()) {
            return Map.of();
        }
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", textContent);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msgtype", "text");
        root.put("text", text);
        return root;
    }
}

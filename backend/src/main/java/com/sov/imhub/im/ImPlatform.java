package com.sov.imhub.im;

public enum ImPlatform {
    TELEGRAM,
    LARK,
    WEWORK,
    DINGTALK,
    SLACK,
    DISCORD;

    public String wireName() {
        return name();
    }

    public static ImPlatform fromWire(String s) {
        if (s == null || s.isBlank()) {
            return TELEGRAM;
        }
        return valueOf(s.trim().toUpperCase());
    }
}

package com.sov.telegram.bot.service.api;

public enum ApiAuthType {
    NONE,
    BEARER_TOKEN,
    API_KEY_HEADER,
    API_KEY_QUERY,
    BASIC;

    public static ApiAuthType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}

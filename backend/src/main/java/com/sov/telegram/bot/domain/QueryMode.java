package com.sov.telegram.bot.domain;

/**
 * How the query definition was authored. Runtime execution always uses {@code sql_template}.
 */
public enum QueryMode {
    SQL,
    VISUAL,
    API;

    public static QueryMode fromString(String s) {
        if (s == null || s.isBlank()) {
            return SQL;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SQL;
        }
    }
}

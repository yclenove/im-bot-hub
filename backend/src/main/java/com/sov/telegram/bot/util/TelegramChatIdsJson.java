package com.sov.telegram.bot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 存库 JSON 与 {@link List}{@code <Long>} 互转。 */
public final class TelegramChatIdsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TelegramChatIdsJson() {}

    public static List<Long> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> list = MAPPER.readValue(json, new TypeReference<List<Long>>() {});
            return list == null ? List.of() : List.copyOf(list);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(new ArrayList<>(ids));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Long> parseLines(String multiline) {
        if (multiline == null || multiline.isBlank()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String line : multiline.split("[\\s,;]+")) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return Collections.unmodifiableList(out);
    }
}

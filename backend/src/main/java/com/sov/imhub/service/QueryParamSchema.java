package com.sov.imhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class QueryParamSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Expect JSON: {"params":["a","b"]}. Returns empty list if null/blank/invalid.
     */
    public static List<String> parseParamNames(String paramSchemaJson) {
        if (paramSchemaJson == null || paramSchemaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(paramSchemaJson);
            JsonNode params = root.get("params");
            if (params == null || !params.isArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Iterator<JsonNode> it = params.elements(); it.hasNext(); ) {
                names.add(it.next().asText());
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 可选字段 {@code examples}，与 {@code params} 下标对齐；用于 Telegram 菜单「示例: /cmd …」。
     */
    public static List<String> parseStoredExamples(String paramSchemaJson) {
        if (paramSchemaJson == null || paramSchemaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(paramSchemaJson);
            JsonNode ex = root.get("examples");
            if (ex == null || !ex.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Iterator<JsonNode> it = ex.elements(); it.hasNext(); ) {
                out.add(it.next().asText(""));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 与 {@link #parseParamNames} 顺序对齐：优先用 JSON 里存的 {@code examples}，缺项再用参数名启发一条示例值。
     */
    /**
     * 以 {@code canonicalJson} 的 {@code params} 为准，把 {@code requestJson} 里同序的 {@code examples} 合并进去；
     * 若全部为空白则不写 {@code examples} 键（沿用启发式示例）。
     */
    public static String mergeExamplesIntoParamSchema(String canonicalJson, String requestJson) {
        List<String> params = parseParamNames(canonicalJson);
        if (params.isEmpty()) {
            if (canonicalJson == null || canonicalJson.isBlank()) {
                return "{\"params\":[]}";
            }
            return canonicalJson.trim();
        }
        List<String> overlay = parseStoredExamples(requestJson);
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode pa = root.putArray("params");
            for (String p : params) {
                pa.add(p);
            }
            boolean any = false;
            ArrayNode exa = root.putArray("examples");
            for (int i = 0; i < params.size(); i++) {
                String v = i < overlay.size() ? overlay.get(i) : "";
                String cell = v == null ? "" : v;
                exa.add(cell);
                if (!cell.isBlank()) {
                    any = true;
                }
            }
            if (!any) {
                root.remove("examples");
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return canonicalJson == null || canonicalJson.isBlank() ? "{\"params\":[]}" : canonicalJson.trim();
        }
    }

    public static List<String> menuArgExamplesForTelegram(List<String> paramNames, String paramSchemaJson) {
        if (paramNames == null || paramNames.isEmpty()) {
            return List.of();
        }
        List<String> stored = parseStoredExamples(paramSchemaJson);
        List<String> out = new ArrayList<>(paramNames.size());
        for (int i = 0; i < paramNames.size(); i++) {
            String fromStore = i < stored.size() ? stored.get(i) : null;
            if (fromStore != null && !fromStore.isBlank()) {
                out.add(fromStore.trim());
            } else {
                out.add(suggestExampleForParamName(paramNames.get(i)));
            }
        }
        return out;
    }

    private static String suggestExampleForParamName(String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return "…";
        }
        String key = paramName.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "coinid", "ids" -> "bitcoin";
            case "vs_currency" -> "usd";
            case "orderno", "order_no" -> "10001";
            case "symbol" -> "BTCUSDT";
            case "limit" -> "10";
            case "days" -> "7";
            case "city", "q", "query", "keyword", "kw" -> "Beijing";
            default -> key.length() <= 12 ? key : "…";
        };
    }

    /**
     * Telegram 等渠道按空格传参，与 {@code names} 下标一一对应；首个空值返回对应参数名。
     */
    public static String firstMissingPositionalValue(List<String> names, List<String> argValues) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        for (int i = 0; i < names.size(); i++) {
            String val = argValues != null && i < argValues.size() ? argValues.get(i) : null;
            if (val == null || val.isBlank()) {
                return names.get(i);
            }
        }
        return null;
    }
}

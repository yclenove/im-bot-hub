package com.sov.imhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Per-field display pipeline stored as JSON array {@code [{ "op": "trim" }, ...]}.
 * Applied after mask + formatType, before Telegram HTML escaping.
 */
public final class DisplayPipelineApplier {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_STEPS = 20;
    private static final int MAX_JSON_CHARS = 24_000;

    private DisplayPipelineApplier() {}

    public static String apply(String input, String pipelineJson) {
        if (pipelineJson == null || pipelineJson.isBlank()) {
            return input == null ? "" : input;
        }
        if (pipelineJson.length() > MAX_JSON_CHARS) {
            return input == null ? "" : input;
        }
        String value = input == null ? "" : input;
        try {
            JsonNode root = MAPPER.readTree(pipelineJson.trim());
            if (!root.isArray()) {
                return value;
            }
            ArrayNode arr = (ArrayNode) root;
            int n = Math.min(arr.size(), MAX_STEPS);
            for (int i = 0; i < n; i++) {
                JsonNode step = arr.get(i);
                if (step == null || !step.isObject()) {
                    continue;
                }
                JsonNode opNode = step.get("op");
                if (opNode == null || !opNode.isTextual()) {
                    continue;
                }
                String op = opNode.asText("").trim();
                try {
                    value = applyStep(value, op, step);
                } catch (Exception ignored) {
                    // skip malformed step
                }
            }
        } catch (Exception ignored) {
            return input == null ? "" : input;
        }
        return value;
    }

    private static String applyStep(String value, String op, JsonNode step) {
        return switch (op) {
            case "trim" -> value.trim();
            case "collapse_space" -> value.replaceAll("\\s+", " ").trim();
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            case "prefix" -> readStr(step, "value", "") + value;
            case "suffix" -> value + readStr(step, "value", "");
            case "truncate" -> truncate(value, readInt(step, "max", 80), readStr(step, "ellipsis", "…"));
            case "substring" -> substringOp(value, step);
            case "split_take" -> splitTake(value, readStr(step, "delimiter", ","), readInt(step, "index", 0), readBool(step, "fromEnd", false));
            case "replace_literal" -> replaceLiteral(
                    value, readStr(step, "from", ""), readStr(step, "to", ""), readInt(step, "maxReplacements", -1));
            case "extract_between" -> extractBetween(value, readStr(step, "left", ""), readStr(step, "right", ""));
            case "digits_only" -> value.replaceAll("\\D+", "");
            case "default_if_empty" -> value.isBlank() ? readStr(step, "value", "") : value;
            case "url_to_origin" -> urlToOrigin(value, readBool(step, "lowercaseHost", false));
            case "url_to_host" -> urlToHost(value, readBool(step, "lowercaseHost", false));
            case "url_to_path" -> urlToPath(value, readBool(step, "includeQuery", false));
            case "url_host_labels" -> urlHostLabels(value, readInt(step, "count", 2), readBool(step, "fromRight", true), readBool(step, "withScheme", false));
            case "url_path_segments" ->
                    urlPathSegments(value, readMaxPathSegments(step), readBool(step, "leadingSlash", true));
            case "regex_extract" -> regexExtract(value, readStr(step, "pattern", ""), readInt(step, "group", 0));
            default -> value;
        };
    }

    /** Accepts {@code maxSegments} or legacy {@code count} from JSON. */
    private static int readMaxPathSegments(JsonNode step) {
        if (step.has("maxSegments")) {
            return readInt(step, "maxSegments", 1);
        }
        if (step.has("count")) {
            return readInt(step, "count", 1);
        }
        return 1;
    }

    private static String readStr(JsonNode step, String key, String def) {
        JsonNode n = step.get(key);
        return n == null || !n.isTextual() ? def : n.asText(def);
    }

    private static int readInt(JsonNode step, String key, int def) {
        JsonNode n = step.get(key);
        if (n == null || !n.isNumber()) {
            return def;
        }
        return n.asInt(def);
    }

    private static boolean readBool(JsonNode step, String key, boolean def) {
        JsonNode n = step.get(key);
        if (n == null || !n.isBoolean()) {
            return def;
        }
        return n.asBoolean();
    }

    private static String truncate(String value, int max, String ellipsis) {
        if (max <= 0 || value.length() <= max) {
            return value;
        }
        String e = ellipsis == null ? "" : ellipsis;
        int take = Math.max(0, max - e.length());
        return value.substring(0, take) + e;
    }

    private static String substringOp(String value, JsonNode step) {
        int start = readInt(step, "start", 0);
        if (start < 0 || start > value.length()) {
            return "";
        }
        if (step.has("len")) {
            int len = Math.max(0, readInt(step, "len", 0));
            int e = Math.min(value.length(), start + len);
            return value.substring(start, e);
        }
        int end = readInt(step, "end", value.length());
        end = Math.min(value.length(), end);
        if (end <= start) {
            return "";
        }
        return value.substring(start, end);
    }

    private static String splitTake(String value, String delimiter, int index, boolean fromEnd) {
        if (delimiter.isEmpty()) {
            return value;
        }
        String[] parts = value.split(Pattern.quote(delimiter), -1);
        if (parts.length == 0) {
            return "";
        }
        int idx = index;
        if (fromEnd) {
            idx = parts.length - 1 - index;
        }
        if (idx < 0 || idx >= parts.length) {
            return "";
        }
        return parts[idx];
    }

    private static String replaceLiteral(String value, String from, String to, int maxRepl) {
        if (from.isEmpty()) {
            return value;
        }
        if (maxRepl < 0) {
            return value.replace(from, to);
        }
        int count = 0;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int j = value.indexOf(from, i);
            if (j < 0 || count >= maxRepl) {
                sb.append(value.substring(i));
                break;
            }
            sb.append(value, i, j).append(to);
            i = j + from.length();
            count++;
        }
        return sb.toString();
    }

    private static String extractBetween(String value, String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return value;
        }
        int a = value.indexOf(left);
        if (a < 0) {
            return value;
        }
        int start = a + left.length();
        int b = value.indexOf(right, start);
        if (b < 0) {
            return value;
        }
        return value.substring(start, b);
    }

    private static URI tryParseUri(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            URI u = URI.create(s);
            if (u.getScheme() == null || u.getHost() == null) {
                return null;
            }
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    private static String urlToOrigin(String value, boolean lowerHost) {
        URI u = tryParseUri(value);
        if (u == null) {
            return value;
        }
        String scheme = u.getScheme();
        String host = u.getHost();
        if (lowerHost && host != null) {
            host = host.toLowerCase();
        }
        int port = u.getPort();
        String auth = host;
        if (port > 0 && !isDefaultPort(scheme, port)) {
            auth = host + ":" + port;
        }
        return scheme + "://" + auth;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if ("http".equalsIgnoreCase(scheme)) {
            return port == 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return port == 443;
        }
        return false;
    }

    private static String urlToHost(String value, boolean lowerHost) {
        URI u = tryParseUri(value);
        if (u == null) {
            return value;
        }
        String host = u.getHost();
        if (host == null) {
            return value;
        }
        if (lowerHost) {
            host = host.toLowerCase();
        }
        int port = u.getPort();
        if (port > 0 && !isDefaultPort(u.getScheme(), port)) {
            return host + ":" + port;
        }
        return host;
    }

    private static String urlToPath(String value, boolean includeQuery) {
        URI u = tryParseUri(value);
        if (u == null) {
            return value;
        }
        String path = u.getPath() == null ? "" : u.getPath();
        if (includeQuery) {
            String q = u.getQuery();
            if (q != null && !q.isBlank()) {
                path = path + "?" + q;
            }
        }
        return path.isEmpty() ? "/" : path;
    }

    private static String urlHostLabels(String value, int count, boolean fromRight, boolean withScheme) {
        URI u = tryParseUri(value);
        if (u == null) {
            return value;
        }
        String host = u.getHost();
        if (host == null || count <= 0) {
            return value;
        }
        String[] labels = host.split("\\.");
        if (labels.length == 0) {
            return host;
        }
        List<String> parts = new ArrayList<>(List.of(labels));
        if (!fromRight) {
            if (count >= parts.size()) {
                return joinHost(parts, withScheme ? u.getScheme() : null);
            }
            return joinHost(parts.subList(0, count), withScheme ? u.getScheme() : null);
        }
        int n = Math.min(count, parts.size());
        int start = parts.size() - n;
        return joinHost(parts.subList(start, parts.size()), withScheme ? u.getScheme() : null);
    }

    private static String joinHost(List<String> sub, String scheme) {
        String h = String.join(".", sub);
        if (scheme != null && !scheme.isBlank()) {
            return scheme + "://" + h;
        }
        return h;
    }

    private static String urlPathSegments(String value, int maxSegments, boolean leadingSlash) {
        URI u = tryParseUri(value);
        if (u == null) {
            return value;
        }
        String path = u.getPath() == null ? "" : u.getPath();
        if (path.isEmpty() || "/".equals(path)) {
            return leadingSlash ? "/" : "";
        }
        String[] segs = path.split("/");
        List<String> nonEmpty = new ArrayList<>();
        for (String s : segs) {
            if (!s.isEmpty()) {
                nonEmpty.add(s);
            }
        }
        if (nonEmpty.isEmpty()) {
            return leadingSlash ? "/" : "";
        }
        int n = Math.max(1, Math.min(maxSegments, nonEmpty.size()));
        StringBuilder sb = new StringBuilder();
        if (leadingSlash) {
            sb.append('/');
        }
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(nonEmpty.get(i));
        }
        return sb.toString();
    }

    private static String regexExtract(String value, String pattern, int group) {
        if (pattern.isBlank()) {
            return value;
        }
        try {
            Pattern p = Pattern.compile(pattern);
            var m = p.matcher(value);
            if (!m.find()) {
                return value;
            }
            if (group < 0 || group > m.groupCount()) {
                return m.group(0);
            }
            String g = m.group(group);
            return g != null ? g : value;
        } catch (Exception e) {
            return value;
        }
    }
}

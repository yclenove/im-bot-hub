package com.sov.imhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.FieldMappingEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FieldRenderService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 当 formatType 仅为 {@code DATE_TIME} 时使用 */
    private static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final ZoneId DISPLAY_ZONE = ZoneId.systemDefault();

    public String renderMarkdownHtml(List<FieldMappingEntity> mappings, Map<String, Object> row) {
        return renderMarkdownHtml(mappings, row, "LIST");
    }

    /**
     * Telegram Bot API HTML（见 {@code TelegramApiClient#sendMessage} parse_mode=HTML）。
     *
     * @param replyStyle LIST | LIST_DOT | LIST_CODE | LIST_BLOCKQUOTE | SECTION | MONO_PRE | CODE_BLOCK |
     *     KV_SINGLE_LINE | VALUES_JOIN_SPACE | VALUES_JOIN_PIPE | TABLE_PRE
     */
    public String renderMarkdownHtml(List<FieldMappingEntity> mappings, Map<String, Object> row, String replyStyle) {
        if (row == null || row.isEmpty()) {
            return "<i>无数据</i>";
        }
        String customDelimiter = resolveCustomJoinDelimiter(replyStyle);
        if (customDelimiter != null) {
            return renderValuesJoined(buildItems(mappings, row), customDelimiter);
        }
        String style = resolveStyle(replyStyle);
        List<LabelValue> items = buildItems(mappings, row);
        return switch (style) {
            case "LIST_CODE" -> renderListCode(items);
            case "SECTION" -> renderSection(items);
            case "MONO_PRE" -> renderMonoPreColon(items);
            case "CODE_BLOCK" -> renderCodeBlockEquals(items);
            case "KV_SINGLE_LINE" -> renderKvSingleLine(items);
            case "VALUES_JOIN_SPACE" -> renderValuesJoined(items, " ");
            case "VALUES_JOIN_PIPE" -> renderValuesJoined(items, " | ");
            case "LIST_BLOCKQUOTE" -> renderListBlockquote(items);
            case "LIST_DOT" -> renderListDot(items);
            case "LIST" -> renderList(items);
            default -> renderList(items);
        };
    }

    private static String resolveStyle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LIST";
        }
        return raw.trim().toUpperCase();
    }

    private static String renderList(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (LabelValue lv : items) {
            sb.append("<b>").append(escape(lv.label())).append("</b>: ")
                    .append(escape(lv.value()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String renderListDot(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (LabelValue lv : items) {
            sb.append("<b>").append(escape(lv.label())).append("</b> · ")
                    .append(escape(lv.value()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String renderListCode(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (LabelValue lv : items) {
            sb.append("<b>").append(escape(lv.label())).append("</b>: <code>")
                    .append(escape(lv.value()))
                    .append("</code>\n");
        }
        return sb.toString();
    }

    private static String renderSection(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            LabelValue lv = items.get(i);
            if (i > 0) {
                sb.append("\n");
            }
            sb.append("<b>").append(escape(lv.label())).append("</b>\n");
            sb.append("<code>").append(escape(lv.value())).append("</code>");
        }
        return sb.toString();
    }

    private static String renderListBlockquote(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (LabelValue lv : items) {
            sb.append("<blockquote>")
                    .append("<b>")
                    .append(escape(lv.label()))
                    .append("</b>: ")
                    .append(escape(lv.value()))
                    .append("</blockquote>\n");
        }
        return sb.toString();
    }

    private static String renderKvSingleLine(List<LabelValue> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            LabelValue lv = items.get(i);
            sb.append("<b>").append(escape(lv.label())).append("</b>: ").append(escape(lv.value()));
        }
        return sb.toString();
    }

    private static String renderValuesJoined(List<LabelValue> items, String delimiter) {
        return items.stream().map(LabelValue::value).map(FieldRenderService::escape).collect(Collectors.joining(delimiter));
    }

    private static String renderMonoPreColon(List<LabelValue> items) {
        return wrapPre(linesColon(items));
    }

    /** Log-style {@code key=value} lines inside {@code <pre>}, no bold. */
    private static String renderCodeBlockEquals(List<LabelValue> items) {
        return wrapPre(linesEquals(items));
    }

    private static String linesColon(List<LabelValue> items) {
        StringBuilder inner = new StringBuilder();
        for (LabelValue lv : items) {
            inner.append(escape(lv.label())).append(": ").append(escape(lv.value())).append("\n");
        }
        return trimTrailingNewline(inner.toString());
    }

    private static String linesEquals(List<LabelValue> items) {
        StringBuilder inner = new StringBuilder();
        for (LabelValue lv : items) {
            inner.append(escape(lv.label())).append("=").append(escape(lv.value())).append("\n");
        }
        return trimTrailingNewline(inner.toString());
    }

    private static String wrapPre(String block) {
        return "<pre>" + block + "</pre>";
    }

    private static String trimTrailingNewline(String block) {
        if (block.endsWith("\n") && !block.isEmpty()) {
            return block.substring(0, block.length() - 1);
        }
        return block;
    }

    /** 纯文本多行结果（飞书等）；不含 HTML。 */
    public String renderPlainMultiRow(List<FieldMappingEntity> fms, List<Map<String, Object>> rows) {
        return renderPlainMultiRow(fms, rows, "LIST");
    }

    /** 纯文本多行结果（飞书等）；不含 HTML，可按 Telegram 样式对齐。 */
    public String renderPlainMultiRow(List<FieldMappingEntity> fms, List<Map<String, Object>> rows, String replyStyle) {
        if (rows == null || rows.isEmpty()) {
            return renderPlainSingleRow(fms, Map.of());
        }
        String customDelimiter = resolveCustomJoinDelimiter(replyStyle);
        if (customDelimiter != null) {
            return rows.stream()
                    .map(row -> buildItems(fms, row).stream().map(LabelValue::value).collect(Collectors.joining(customDelimiter)))
                    .collect(Collectors.joining("\n"));
        }
        String style = resolveStyle(replyStyle);
        if ("TABLE_PRE".equals(style)) {
            return renderPlainTable(fms, rows);
        }
        if ("VALUES_JOIN_SPACE".equals(style) || "VALUES_JOIN_PIPE".equals(style)) {
            String delimiter = "VALUES_JOIN_PIPE".equals(style) ? " | " : " ";
            return rows.stream()
                    .map(row -> buildItems(fms, row).stream().map(LabelValue::value).collect(Collectors.joining(delimiter)))
                    .collect(Collectors.joining("\n"));
        }
        if (rows.size() == 1) {
            return renderPlainSingleRow(fms, rows.get(0));
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                out.append("\n\n");
            }
            out.append("── 第 ")
                    .append(i + 1)
                    .append("/")
                    .append(rows.size())
                    .append(" 条 ──\n");
            out.append(renderPlainSingleRow(fms, rows.get(i)));
        }
        return out.toString();
    }

    public String renderMarkdownHtmlMultiRow(List<FieldMappingEntity> fms, List<Map<String, Object>> rows, String replyStyle) {
        if (rows == null || rows.isEmpty()) {
            return "<i>无数据</i>";
        }
        String customDelimiter = resolveCustomJoinDelimiter(replyStyle);
        if (customDelimiter != null) {
            return rows.stream()
                    .map(row -> buildItems(fms, row).stream().map(LabelValue::value).map(FieldRenderService::escape).collect(Collectors.joining(customDelimiter)))
                    .collect(Collectors.joining("\n"));
        }
        String style = resolveStyle(replyStyle);
        if ("TABLE_PRE".equals(style)) {
            return renderHtmlTable(fms, rows);
        }
        if ("VALUES_JOIN_SPACE".equals(style) || "VALUES_JOIN_PIPE".equals(style)) {
            String delimiter = "VALUES_JOIN_PIPE".equals(style) ? " | " : " ";
            return rows.stream()
                    .map(row -> buildItems(fms, row).stream().map(LabelValue::value).map(FieldRenderService::escape).collect(Collectors.joining(delimiter)))
                    .collect(Collectors.joining("\n"));
        }
        if (rows.size() == 1) {
            return renderMarkdownHtml(fms, rows.get(0), style);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                out.append("\n\n");
            }
            out.append("<b>── 第 ")
                    .append(i + 1)
                    .append("/")
                    .append(rows.size())
                    .append(" 条 ──</b>\n");
            out.append(renderMarkdownHtml(fms, rows.get(i), style));
        }
        return out.toString();
    }

    public String renderPlainSingleRow(List<FieldMappingEntity> fms, Map<String, Object> row) {
        List<LabelValue> items = buildItems(fms, row == null ? Map.of() : row);
        StringBuilder sb = new StringBuilder();
        for (LabelValue lv : items) {
            sb.append(lv.label()).append(": ").append(lv.value()).append('\n');
        }
        String s = sb.toString().trim();
        if (s.length() > 4500) {
            return s.substring(0, 4490) + "\n...(truncated)";
        }
        return s;
    }

    private List<LabelValue> buildItems(List<FieldMappingEntity> mappings, Map<String, Object> row) {
        List<FieldMappingEntity> sorted =
                mappings.stream()
                        .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                        .collect(Collectors.toList());
        List<LabelValue> items = new ArrayList<>();
        if (sorted.isEmpty()) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String display = formatDisplayValue(e.getValue(), "NONE", null);
                items.add(new LabelValue(e.getKey(), display));
            }
            return items;
        }
        for (FieldMappingEntity fm : sorted) {
            Object val = row.get(fm.getColumnName());
            if (val == null && row.containsKey(fm.getColumnName().toLowerCase())) {
                val = row.get(fm.getColumnName().toLowerCase());
            }
            String display = formatDisplayValue(val, fm.getMaskType(), fm.getFormatType());
            display = DisplayPipelineApplier.apply(display, fm.getDisplayPipelineJson());
            items.add(new LabelValue(fm.getLabel(), display));
        }
        return items;
    }

    private record LabelValue(String label, String value) {}

    /**
     * @param cell JDBC/MyBatis 单元格值（可为 {@link java.sql.Timestamp}、{@link Long} 时间戳、ISO 字符串等）
     */
    static String formatDisplayValue(Object cell, String maskType, String formatType) {
        String value = cell == null ? "" : String.valueOf(cell);
        if ("PHONE_LAST4".equalsIgnoreCase(maskType) && value.length() > 4) {
            value = "****" + value.substring(value.length() - 4);
        }
        if (formatType != null && !formatType.isBlank()) {
            value = applyFormat(cell, value, formatType);
        }
        return value;
    }

    private static String applyFormat(Object originalCell, String valueAfterMask, String formatType) {
        try {
            if (formatType != null && formatType.startsWith("ENUM_JSON:")) {
                String json = formatType.substring("ENUM_JSON:".length());
                Map<String, String> map = MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
                return map.getOrDefault(valueAfterMask, valueAfterMask);
            }
            if (isDateTimeFormatType(formatType)) {
                return formatDateTimeDisplay(originalCell, valueAfterMask, formatType);
            }
            if ("MONEY_2".equalsIgnoreCase(formatType)) {
                BigDecimal b = new BigDecimal(valueAfterMask);
                return new DecimalFormat("#,##0.00").format(b);
            }
        } catch (Exception ignored) {
        }
        return valueAfterMask;
    }

    private static boolean isDateTimeFormatType(String formatType) {
        String f = formatType.trim();
        if ("DATE_TIME".equalsIgnoreCase(f)) {
            return true;
        }
        return f.regionMatches(true, 0, "DATE_TIME:", 0, "DATE_TIME:".length());
    }

    /**
     * {@code DATE_TIME} 或 {@code DATE_TIME:yyyy-MM-dd HH:mm}（pattern 自第一个冒号后取到串尾）。
     * 支持：Unix 秒/毫秒（数字或纯数字串）、{@link java.sql.Timestamp}、{@link java.util.Date}、{@link Instant}、
     * {@link LocalDateTime}、常见 ISO-8601 字符串；无法解析则退回当前展示串。
     */
    private static String formatDateTimeDisplay(Object originalCell, String valueAfterMask, String formatType) {
        String pattern = DEFAULT_DATE_TIME_PATTERN;
        String ft = formatType.trim();
        if (ft.regionMatches(true, 0, "DATE_TIME:", 0, "DATE_TIME:".length())) {
            String p = ft.substring("DATE_TIME:".length()).trim();
            if (!p.isEmpty()) {
                pattern = p;
            }
        }
        ZonedDateTime zdt = parseToZonedDateTime(originalCell, valueAfterMask);
        if (zdt == null) {
            return valueAfterMask;
        }
        try {
            return DateTimeFormatter.ofPattern(pattern).format(zdt);
        } catch (IllegalArgumentException | DateTimeException e) {
            return valueAfterMask;
        }
    }

    private static ZonedDateTime parseToZonedDateTime(Object cell, String textFallback) {
        if (cell != null) {
            ZonedDateTime z = parseTemporalObject(cell);
            if (z != null) {
                return z;
            }
        }
        String s = textFallback == null ? "" : textFallback.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.chars().allMatch(Character::isDigit)) {
            try {
                long n = Long.parseLong(s);
                Instant i = instantFromEpochNumber(n);
                return i == null ? null : i.atZone(DISPLAY_ZONE);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Instant.parse(s).atZone(DISPLAY_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(DISPLAY_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(DISPLAY_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            java.sql.Timestamp ts = java.sql.Timestamp.valueOf(s);
            return ts.toInstant().atZone(DISPLAY_ZONE);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            LocalDate d = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.atStartOfDay(DISPLAY_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static ZonedDateTime parseTemporalObject(Object cell) {
        if (cell instanceof Instant instant) {
            return instant.atZone(DISPLAY_ZONE);
        }
        if (cell instanceof ZonedDateTime z) {
            return z.withZoneSameInstant(DISPLAY_ZONE);
        }
        if (cell instanceof OffsetDateTime o) {
            return o.atZoneSameInstant(DISPLAY_ZONE);
        }
        if (cell instanceof LocalDateTime l) {
            return l.atZone(DISPLAY_ZONE);
        }
        if (cell instanceof LocalDate d) {
            return d.atStartOfDay(DISPLAY_ZONE);
        }
        if (cell instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(DISPLAY_ZONE);
        }
        if (cell instanceof java.util.Date d) {
            return d.toInstant().atZone(DISPLAY_ZONE);
        }
        if (cell instanceof java.sql.Date sd) {
            return sd.toLocalDate().atStartOfDay(DISPLAY_ZONE);
        }
        if (cell instanceof Number n) {
            Instant i = instantFromEpochNumber(n.longValue());
            return i == null ? null : i.atZone(DISPLAY_ZONE);
        }
        return null;
    }

    /** 大于 1e11 视为毫秒，否则视为秒（与常见 10 位秒 / 13 位毫秒一致）。 */
    private static Instant instantFromEpochNumber(long epoch) {
        if (Math.abs(epoch) > 100_000_000_000L) {
            return Instant.ofEpochMilli(epoch);
        }
        return Instant.ofEpochSecond(epoch);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String resolveCustomJoinDelimiter(String rawStyle) {
        if (rawStyle == null) {
            return null;
        }
        String s = rawStyle.trim();
        if (!s.regionMatches(true, 0, "VALUES_JOIN_CUSTOM:", 0, "VALUES_JOIN_CUSTOM:".length())) {
            return null;
        }
        String delimiter = s.substring("VALUES_JOIN_CUSTOM:".length());
        return delimiter.isEmpty() ? " " : delimiter;
    }

    private String renderHtmlTable(List<FieldMappingEntity> fms, List<Map<String, Object>> rows) {
        List<List<LabelValue>> tableRows = rows.stream().map(row -> buildItems(fms, row)).toList();
        if (tableRows.isEmpty() || tableRows.get(0).isEmpty()) {
            return "<i>无数据</i>";
        }
        List<String> headers = tableRows.get(0).stream().map(LabelValue::label).toList();
        List<List<String>> dataRows = tableRows.stream().map(line -> line.stream().map(LabelValue::value).toList()).toList();
        return "<pre>" + renderAlignedTable(headers, dataRows, true) + "</pre>";
    }

    private String renderPlainTable(List<FieldMappingEntity> fms, List<Map<String, Object>> rows) {
        List<List<LabelValue>> tableRows = rows.stream().map(row -> buildItems(fms, row)).toList();
        if (tableRows.isEmpty() || tableRows.get(0).isEmpty()) {
            return "无数据";
        }
        List<String> headers = tableRows.get(0).stream().map(LabelValue::label).toList();
        List<List<String>> dataRows = tableRows.stream().map(line -> line.stream().map(LabelValue::value).toList()).toList();
        return renderAlignedTable(headers, dataRows, false);
    }

    private static String renderAlignedTable(List<String> headers, List<List<String>> dataRows, boolean htmlEscape) {
        int colCount = headers.size();
        int[] widths = new int[colCount];
        for (int i = 0; i < colCount; i++) {
            widths[i] = displayWidth(headers.get(i));
        }
        for (List<String> row : dataRows) {
            for (int i = 0; i < colCount && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], displayWidth(row.get(i)));
            }
        }
        StringBuilder out = new StringBuilder();
        out.append(formatAlignedRow(headers, widths, htmlEscape)).append('\n');
        out.append(formatSeparator(widths)).append('\n');
        for (int r = 0; r < dataRows.size(); r++) {
            out.append(formatAlignedRow(dataRows.get(r), widths, htmlEscape));
            if (r < dataRows.size() - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String formatAlignedRow(List<String> values, int[] widths, boolean htmlEscape) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String cell = i < values.size() && values.get(i) != null ? values.get(i) : "";
            String shown = htmlEscape ? escape(cell) : cell;
            sb.append(shown);
            int padding = widths[i] - displayWidth(cell);
            if (padding > 0) {
                sb.append(" ".repeat(padding));
            }
        }
        return sb.toString();
    }

    private static String formatSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append("-+-");
            }
            sb.append("-".repeat(Math.max(3, widths[i])));
        }
        return sb.toString();
    }

    /**
     * Approximate monospace display width; CJK/fullwidth chars count as 2 cells.
     */
    private static int displayWidth(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            boolean wide = script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
            width += wide ? 2 : 1;
        }
        return width;
    }
}

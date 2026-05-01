package com.sov.telegram.bot.service;

import com.sov.telegram.bot.domain.FieldMappingEntity;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRenderServiceTest {

    private final FieldRenderService svc = new FieldRenderService();

    @Test
    void listCode_wrapsValueInCode() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("id");
        fm.setLabel("订单");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType(null);
        String html =
                svc.renderMarkdownHtml(List.of(fm), Map.of("id", "abc123"), "LIST_CODE");
        assertTrue(html.contains("<code>abc123</code>"));
        assertTrue(html.contains("<b>"));
    }

    @Test
    void monoPre_usesPreBlock() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("a");
        fm.setLabel("A");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType(null);
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("a", "1"), "MONO_PRE");
        assertTrue(html.startsWith("<pre>"));
        assertTrue(html.endsWith("</pre>"));
    }

    @Test
    void displayPipeline_afterEnumFormat() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("st");
        fm.setLabel("状态");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType("ENUM_JSON:{\"1\":\"已付\"}");
        fm.setDisplayPipelineJson("[{\"op\":\"prefix\",\"value\":\"[\"},{\"op\":\"suffix\",\"value\":\"]\"}]");
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("st", "1"), "LIST");
        assertTrue(html.contains("[已付]"));
    }

    @Test
    void listDot_usesMiddleDot() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("x");
        fm.setLabel("X");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType(null);
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("x", "v"), "LIST_DOT");
        assertTrue(html.contains("·"));
    }

    @Test
    void kvSingleLine_oneLine() {
        FieldMappingEntity a = new FieldMappingEntity();
        a.setColumnName("a");
        a.setLabel("A");
        a.setSortOrder(0);
        a.setMaskType("NONE");
        a.setFormatType(null);
        FieldMappingEntity b = new FieldMappingEntity();
        b.setColumnName("b");
        b.setLabel("B");
        b.setSortOrder(1);
        b.setMaskType("NONE");
        b.setFormatType(null);
        String html = svc.renderMarkdownHtml(List.of(a, b), Map.of("a", "1", "b", "2"), "KV_SINGLE_LINE");
        assertFalse(html.contains("\n"));
        assertTrue(html.contains("; "));
    }

    @Test
    void codeBlock_usesEquals() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("k");
        fm.setLabel("key");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType(null);
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("k", "v"), "CODE_BLOCK");
        assertTrue(html.contains("key=v"));
        assertTrue(html.startsWith("<pre>"));
    }

    @Test
    void dateTime_formatsUnixSecondsString() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("t");
        fm.setLabel("时间");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType("DATE_TIME");
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("t", "1700000000"), "LIST");
        assertTrue(html.contains("2023-11-"));
    }

    @Test
    void dateTime_formatsUnixMillisNumber() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("t");
        fm.setLabel("时间");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType("DATE_TIME:yyyy-MM-dd");
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("t", 1700000000000L), "LIST");
        assertTrue(html.contains("2023-11-"));
    }

    @Test
    void dateTime_formatsSqlTimestamp() {
        FieldMappingEntity fm = new FieldMappingEntity();
        fm.setColumnName("t");
        fm.setLabel("时间");
        fm.setSortOrder(0);
        fm.setMaskType("NONE");
        fm.setFormatType("DATE_TIME:yyyy-MM-dd HH:mm:ss");
        Timestamp ts = Timestamp.valueOf("2024-06-01 12:34:56");
        String html = svc.renderMarkdownHtml(List.of(fm), Map.of("t", ts), "LIST");
        assertTrue(html.contains("2024-06-01 12:34:56"));
    }
}

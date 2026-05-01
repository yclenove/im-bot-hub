package com.sov.imhub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayPipelineApplierTest {

    @Test
    void trimAndUpper() {
        String out = DisplayPipelineApplier.apply("  ab ", "[{\"op\":\"trim\"},{\"op\":\"upper\"}]");
        assertEquals("AB", out);
    }

    @Test
    void urlToOrigin_keepsNonUrl() {
        String out = DisplayPipelineApplier.apply("ORD-123", "[{\"op\":\"url_to_origin\"}]");
        assertEquals("ORD-123", out);
    }

    @Test
    void urlToOrigin_stripsPath() {
        String out =
                DisplayPipelineApplier.apply(
                        "https://m.hrjgz.shop/pages/pay?x=1",
                        "[{\"op\":\"trim\"},{\"op\":\"url_to_origin\",\"lowercaseHost\":true}]");
        assertEquals("https://m.hrjgz.shop", out);
    }

    @Test
    void payLinkTemplate_hostLabelsWithScheme() {
        String json =
                "[{\"op\":\"trim\"},{\"op\":\"url_to_origin\"},{\"op\":\"url_host_labels\",\"count\":2,\"fromRight\":true,\"withScheme\":true}]";
        String out =
                DisplayPipelineApplier.apply("https://m.hrjgz.shop/pages/goods/cashier/pay", json);
        assertEquals("https://hrjgz.shop", out);
    }

    @Test
    void invalidJson_returnsInput() {
        String in = "hello";
        assertEquals(in, DisplayPipelineApplier.apply(in, "{not array"));
    }

    @Test
    void oversizedJson_skipped() {
        StringBuilder sb = new StringBuilder("[");
        while (sb.length() < 25_000) {
            sb.append("{\"op\":\"trim\"},");
        }
        sb.append("{\"op\":\"trim\"}]");
        String pipeline = sb.toString();
        assertTrue(pipeline.length() > 24_000);
        String in = "  hello  ";
        assertEquals(in, DisplayPipelineApplier.apply(in, pipeline));
    }

    @Test
    void maxSteps_truncates() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 25; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"op\":\"suffix\",\"value\":\"a\"}");
        }
        sb.append(']');
        String out = DisplayPipelineApplier.apply("", sb.toString());
        assertEquals("aaaaaaaaaaaaaaaaaaaa", out);
    }

    @Test
    void urlPathSegments_firstSegments() {
        String out =
                DisplayPipelineApplier.apply(
                        "https://x.test/a/b/c/d",
                        "[{\"op\":\"url_path_segments\",\"maxSegments\":2,\"leadingSlash\":true}]");
        assertEquals("/a/b", out);
    }
}

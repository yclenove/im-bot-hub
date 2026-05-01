package com.sov.telegram.bot.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryParamSchemaTest {

    @Test
    void firstMissingPositionalValue_emptyNamesMeansNothingRequired() {
        assertNull(QueryParamSchema.firstMissingPositionalValue(List.of(), List.of()));
        assertNull(QueryParamSchema.firstMissingPositionalValue(List.of(), List.of("x")));
    }

    @Test
    void firstMissingPositionalValue_detectsBlank() {
        assertEquals("a", QueryParamSchema.firstMissingPositionalValue(List.of("a", "b"), List.of()));
        assertEquals("b", QueryParamSchema.firstMissingPositionalValue(List.of("a", "b"), List.of("1", "")));
        assertEquals("b", QueryParamSchema.firstMissingPositionalValue(List.of("a", "b"), List.of("1")));
    }

    @Test
    void firstMissingPositionalValue_allPresent() {
        assertNull(
                QueryParamSchema.firstMissingPositionalValue(List.of("a", "b"), List.of("1", "2")));
    }

    @Test
    void menuArgExamples_usesStoredExamplesThenHeuristic() {
        String json = "{\"params\":[\"coinId\",\"vs_currency\"],\"examples\":[\"ethereum\",\"\"]}";
        assertEquals(
                List.of("ethereum", "usd"),
                QueryParamSchema.menuArgExamplesForTelegram(
                        QueryParamSchema.parseParamNames(json), json));
    }

    @Test
    void menuArgExamples_heuristicCoinId() {
        String json = "{\"params\":[\"coinId\"]}";
        assertEquals(
                List.of("bitcoin"),
                QueryParamSchema.menuArgExamplesForTelegram(
                        QueryParamSchema.parseParamNames(json), json));
    }

    @Test
    void mergeExamplesIntoParamSchema_takesParamsFromCanonical() {
        String merged =
                QueryParamSchema.mergeExamplesIntoParamSchema(
                        "{\"params\":[\"a\",\"b\"]}", "{\"params\":[\"x\"],\"examples\":[\"1\",\"2\"]}");
        assertEquals("{\"params\":[\"a\",\"b\"],\"examples\":[\"1\",\"2\"]}", merged);
    }

    @Test
    void mergeExamplesIntoParamSchema_omitsExamplesWhenAllBlank() {
        String merged =
                QueryParamSchema.mergeExamplesIntoParamSchema(
                        "{\"params\":[\"a\"]}", "{\"examples\":[\"  \",\"\"]}");
        assertEquals("{\"params\":[\"a\"]}", merged);
    }
}

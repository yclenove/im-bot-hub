package com.sov.telegram.bot.service.visual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualQueryIndexAdviceServiceTest {

    @Test
    void makeIndexNameRespectsMysqlIdentifierLimit() {
        String n = VisualQueryIndexAdviceService.makeIndexName("idx_vq", "very_long_table_name_here", "very_long_column_name", "fx");
        assertTrue(n.length() <= 64);
    }

    @Test
    void orderedPrefixCoverDetectsFirstColumnAndCompositePrefix() {
        List<DatasourceMetadataService.MysqlIndexDefinition> defs =
                List.of(
                        new DatasourceMetadataService.MysqlIndexDefinition("idx_a", false, List.of("A", "B")),
                        new DatasourceMetadataService.MysqlIndexDefinition("PRIMARY", true, List.of("id")));
        assertTrue(VisualQueryIndexAdviceService.hasOrderedPrefixCover(defs, List.of("a")));
        assertEquals("idx_a", VisualQueryIndexAdviceService.findCoveringIndexKeyName(defs, List.of("a")));
        assertTrue(VisualQueryIndexAdviceService.hasOrderedPrefixCover(defs, List.of("A", "b")));
        assertFalse(VisualQueryIndexAdviceService.hasOrderedPrefixCover(defs, List.of("B")));
        assertFalse(VisualQueryIndexAdviceService.hasOrderedPrefixCover(defs, List.of("A", "b", "c")));
    }
}

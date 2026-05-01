package com.sov.imhub.service.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TelegramCommandParser} 单元测试：覆盖命令与参数解析边界。
 */
class TelegramCommandParserTest {

    @Test
    void shouldParseCommandAndSingleArg() {
        TelegramCommandParser.Parsed p = TelegramCommandParser.parse("/cx ORD123");
        assertEquals("cx", p.command());
        assertEquals(List.of("ORD123"), p.args());
    }

    @Test
    void shouldStripBotSuffix() {
        TelegramCommandParser.Parsed p = TelegramCommandParser.parse("/cx@SomeBot ORD1");
        assertEquals("cx", p.command());
        assertEquals(List.of("ORD1"), p.args());
    }

    @Test
    void shouldReturnEmptyForPlainText() {
        TelegramCommandParser.Parsed p = TelegramCommandParser.parse("hello");
        assertEquals("", p.command());
        assertEquals(List.of(), p.args());
    }

    /** 群组中常见：先 {@code @mention} 机器人再写斜杠命令 */
    @Test
    void shouldParseLeadingMentionThenSlashCommand() {
        TelegramCommandParser.Parsed p =
                TelegramCommandParser.parse("@test_bot /cxdd 20260101000826975257MQ8olc");
        assertEquals("cxdd", p.command());
        assertEquals(List.of("20260101000826975257MQ8olc"), p.args());
    }

    @Test
    void shouldTolerateNonBreakingSpaceInCommand() {
        TelegramCommandParser.Parsed p =
                TelegramCommandParser.parse("/cxdd\u00A0arg1");
        assertEquals("cxdd", p.command());
        assertEquals(List.of("arg1"), p.args());
    }
}

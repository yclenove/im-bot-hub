package com.sov.telegram.bot.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.telegram.bot.service.telegram.TelegramCommandParser;

/**
 * 与 IM 无关的「斜杠命令」上下文；Telegram 填 telegram* 与 message；飞书等填 external* 与 channelId。
 */
public record InboundCommandContext(
        long botId,
        ImPlatform platform,
        Long channelId,
        /** Telegram Bot API token；非 Telegram 可为 null */
        String telegramBotToken,
        long telegramUserId,
        long telegramChatId,
        String externalUserId,
        String externalChatId,
        TelegramCommandParser.Parsed parsed,
        /** 仅 Telegram 群范围校验使用；其它平台为 null */
        JsonNode telegramMessage) {}

package com.sov.imhub.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.imhub.domain.Bot;
import com.sov.imhub.util.TelegramChatIdsJson;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按机器人配置判断是否处理该 Telegram 会话：{@code GROUPS_ONLY} 时忽略私聊与未在名单内的群。
 */
@Component
public class TelegramChatAccessService {

    /** @return true 则继续执行查询逻辑；false 则静默忽略（不回复、不报错） */
    public boolean allows(Bot bot, JsonNode message) {
        if (bot == null || message == null) {
            return false;
        }
        String scope =
                bot.getTelegramChatScope() == null || bot.getTelegramChatScope().isBlank()
                        ? "ALL"
                        : bot.getTelegramChatScope().trim();
        if ("ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        if (!"GROUPS_ONLY".equalsIgnoreCase(scope)) {
            return true;
        }
        if (!message.has("chat")) {
            return false;
        }
        JsonNode chat = message.get("chat");
        String type = chat.path("type").asText("");
        if ("private".equals(type)) {
            return false;
        }
        if (!"group".equals(type) && !"supergroup".equals(type)) {
            return false;
        }
        long chatId = chat.get("id").asLong();
        List<Long> allowed = TelegramChatIdsJson.parse(bot.getTelegramAllowedChatIdsJson());
        return !allowed.isEmpty() && allowed.contains(chatId);
    }
}

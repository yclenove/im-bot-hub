package com.sov.imhub.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.util.TelegramChatIdsJson;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按渠道配置判断是否处理该 Telegram 会话：{@code GROUPS_ONLY} 时忽略私聊与未在名单内的群。
 */
@Component
public class TelegramChatAccessService {

    /** @return true 则继续执行查询逻辑；false 则静默忽略（不回复、不报错） */
    public boolean allows(BotChannelEntity channel, JsonNode message) {
        if (channel == null || message == null) {
            return false;
        }
        String scope =
                channel.getChatScope() == null || channel.getChatScope().isBlank()
                        ? "ALL"
                        : channel.getChatScope().trim();
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
        List<Long> allowed = TelegramChatIdsJson.parse(channel.getAllowedChatIdsJson());
        return !allowed.isEmpty() && allowed.contains(chatId);
    }
}

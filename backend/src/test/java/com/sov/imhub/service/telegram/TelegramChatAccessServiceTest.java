package com.sov.imhub.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sov.imhub.domain.BotChannelEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChatAccessServiceTest {

    private final TelegramChatAccessService svc = new TelegramChatAccessService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allScope_acceptsPrivate() {
        BotChannelEntity ch = new BotChannelEntity();
        ch.setChatScope("ALL");
        assertTrue(svc.allows(ch, msg("private", 123L)));
    }

    @Test
    void groupsOnly_rejectsPrivate() {
        BotChannelEntity ch = new BotChannelEntity();
        ch.setChatScope("GROUPS_ONLY");
        ch.setAllowedChatIdsJson("[-100123]");
        assertFalse(svc.allows(ch, msg("private", 123L)));
    }

    @Test
    void groupsOnly_acceptsListedGroup() {
        BotChannelEntity ch = new BotChannelEntity();
        ch.setChatScope("GROUPS_ONLY");
        ch.setAllowedChatIdsJson("[-100123, -100456]");
        assertTrue(svc.allows(ch, msg("supergroup", -100123L)));
    }

    @Test
    void groupsOnly_rejectsUnlistedGroup() {
        BotChannelEntity ch = new BotChannelEntity();
        ch.setChatScope("GROUPS_ONLY");
        ch.setAllowedChatIdsJson("[-100123]");
        assertFalse(svc.allows(ch, msg("supergroup", -999L)));
    }

    private ObjectNode msg(String chatType, long chatId) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode chat = root.putObject("chat");
        chat.put("type", chatType);
        chat.put("id", chatId);
        return root;
    }
}

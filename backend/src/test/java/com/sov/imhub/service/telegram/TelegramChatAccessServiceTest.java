package com.sov.imhub.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sov.imhub.domain.Bot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChatAccessServiceTest {

    private final TelegramChatAccessService svc = new TelegramChatAccessService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allScope_acceptsPrivate() {
        Bot b = new Bot();
        b.setTelegramChatScope("ALL");
        assertTrue(svc.allows(b, msg("private", 123L)));
    }

    @Test
    void groupsOnly_rejectsPrivate() {
        Bot b = new Bot();
        b.setTelegramChatScope("GROUPS_ONLY");
        b.setTelegramAllowedChatIdsJson("[-100123]");
        assertFalse(svc.allows(b, msg("private", 123L)));
    }

    @Test
    void groupsOnly_acceptsListedGroup() {
        Bot b = new Bot();
        b.setTelegramChatScope("GROUPS_ONLY");
        b.setTelegramAllowedChatIdsJson("[-100123, -100456]");
        assertTrue(svc.allows(b, msg("supergroup", -100123L)));
    }

    @Test
    void groupsOnly_rejectsUnlistedGroup() {
        Bot b = new Bot();
        b.setTelegramChatScope("GROUPS_ONLY");
        b.setTelegramAllowedChatIdsJson("[-100123]");
        assertFalse(svc.allows(b, msg("supergroup", -999L)));
    }

    private ObjectNode msg(String chatType, long chatId) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode chat = root.putObject("chat");
        chat.put("type", chatType);
        chat.put("id", chatId);
        return root;
    }
}

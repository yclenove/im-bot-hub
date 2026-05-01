package com.sov.telegram.bot.service.lark;

import com.sov.telegram.bot.mapper.BotChannelMapper;
import com.sov.telegram.bot.mapper.BotMapper;
import com.sov.telegram.bot.service.QueryOrchestrationService;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class LarkWebhookServiceTest {

    @Mock private BotChannelMapper botChannelMapper;
    @Mock private BotMapper botMapper;
    @Mock private QueryOrchestrationService queryOrchestrationService;
    @Mock private com.sov.telegram.bot.im.lark.LarkApiClient larkApiClient;
    @Mock private EncryptionService encryptionService;

    @InjectMocks private LarkWebhookService service;

    @Test
    void urlVerification_returnsChallenge() {
        String body =
                "{\"schema\":\"2.0\",\"header\":{\"event_type\":\"\"},\"event\":{\"type\":\"url_verification\",\"token\":\"x\",\"challenge\":\"abc123\"}}";
        LarkWebhookService.LarkWebhookResult r = service.handle(body, 999L);
        assertNotNull(r.challengeResponse());
        assertEquals("abc123", r.challengeResponse());
    }
}

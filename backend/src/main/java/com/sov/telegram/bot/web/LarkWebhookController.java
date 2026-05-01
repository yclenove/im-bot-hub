package com.sov.telegram.bot.web;

import com.sov.telegram.bot.service.lark.LarkWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook/lark")
@RequiredArgsConstructor
public class LarkWebhookController {

    private final LarkWebhookService larkWebhookService;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(@PathVariable long channelId, @RequestBody String body) {
        LarkWebhookService.LarkWebhookResult r = larkWebhookService.handle(body, channelId);
        if (r.challengeResponse() != null) {
            return ResponseEntity.ok(Map.of("challenge", r.challengeResponse()));
        }
        return ResponseEntity.ok().build();
    }
}

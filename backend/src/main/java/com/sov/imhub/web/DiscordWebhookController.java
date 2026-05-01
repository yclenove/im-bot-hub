package com.sov.imhub.web;

import com.sov.imhub.service.discord.DiscordWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Discord Webhook 入口：处理 Interactions Endpoint 事件。
 */
@RestController
@RequestMapping("/api/webhook/discord")
@RequiredArgsConstructor
public class DiscordWebhookController {

    private final DiscordWebhookService discordWebhookService;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(@PathVariable long channelId, @RequestBody String body) {
        DiscordWebhookService.DiscordWebhookResult r = discordWebhookService.handle(body, channelId);
        if (r.interactionResponse() != null) {
            return ResponseEntity.ok(r.interactionResponse());
        }
        return ResponseEntity.ok().build();
    }
}

package com.sov.imhub.web;

import com.sov.imhub.service.slack.SlackWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Slack Webhook 入口：处理 Events API 事件和 URL verification。
 */
@RestController
@RequestMapping("/api/webhook/slack")
@RequiredArgsConstructor
public class SlackWebhookController {

    private final SlackWebhookService slackWebhookService;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(@PathVariable long channelId, @RequestBody String body) {
        SlackWebhookService.SlackWebhookResult r = slackWebhookService.handle(body, channelId);
        if (r.challengeResponse() != null) {
            return ResponseEntity.ok(Map.of("challenge", r.challengeResponse()));
        }
        return ResponseEntity.ok().build();
    }
}

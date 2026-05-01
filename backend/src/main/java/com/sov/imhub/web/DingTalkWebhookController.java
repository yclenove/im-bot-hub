package com.sov.imhub.web;

import com.sov.imhub.service.dingtalk.DingTalkWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook/dingtalk")
@RequiredArgsConstructor
public class DingTalkWebhookController {

    private final DingTalkWebhookService dingTalkWebhookService;

    @PostMapping("/{channelId}")
    public ResponseEntity<?> receive(
            @PathVariable long channelId,
            @RequestHeader(value = "timestamp", required = false) String timestamp,
            @RequestHeader(value = "sign", required = false) String sign,
            @RequestBody String body) {
        DingTalkWebhookService.DingTalkWebhookOutcome o = dingTalkWebhookService.handle(body, channelId, timestamp, sign);
        if (o.httpStatus() == 403) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(o.body());
    }
}

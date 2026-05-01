package com.sov.imhub.web;

import com.sov.imhub.service.wework.WeWorkWebhookService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxRuntimeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook/wework")
@RequiredArgsConstructor
public class WeWorkWebhookController {

    private final WeWorkWebhookService weWorkWebhookService;

    @GetMapping(value = "/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @PathVariable long channelId,
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr) {
        try {
            String echo =
                    weWorkWebhookService.verifyUrl(channelId, msgSignature, timestamp, nonce, echostr);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                    .body(echo);
        } catch (IllegalArgumentException | WxRuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/{channelId}")
    public ResponseEntity<String> receive(
            @PathVariable long channelId,
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String body) {
        WeWorkWebhookService.WeWorkWebhookOutcome o =
                weWorkWebhookService.handlePost(channelId, msgSignature, timestamp, nonce, body);
        if (o.httpStatus() == 403) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok().contentType(o.contentType()).body(o.body());
    }
}

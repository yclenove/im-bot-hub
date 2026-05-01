package com.sov.telegram.bot.web.admin;

import com.sov.telegram.bot.admin.dto.SetWebhookRequest;
import com.sov.telegram.bot.admin.dto.SetWebhookResponse;
import com.sov.telegram.bot.admin.dto.WebhookInfoResponse;
import com.sov.telegram.bot.service.telegram.TelegramWebhookAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bots/{botId}/telegram")
@RequiredArgsConstructor
public class AdminTelegramWebhookController {

    private final TelegramWebhookAdminService telegramWebhookAdminService;

    /** 向 Telegram 注册 {@code POST https://&lt;公网基址&gt;/api/webhook/&lt;botId&gt;} */
    @PostMapping("/set-webhook")
    public SetWebhookResponse setWebhook(
            @PathVariable Long botId,
            @RequestBody(required = false) SetWebhookRequest body) {
        return telegramWebhookAdminService.setWebhook(botId, body != null ? body : new SetWebhookRequest());
    }

    @GetMapping("/webhook-info")
    public WebhookInfoResponse webhookInfo(@PathVariable Long botId) {
        return telegramWebhookAdminService.webhookInfo(botId);
    }
}

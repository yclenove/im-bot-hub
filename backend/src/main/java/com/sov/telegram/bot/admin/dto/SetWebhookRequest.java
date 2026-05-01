package com.sov.telegram.bot.admin.dto;

import lombok.Data;

@Data
public class SetWebhookRequest {
    /**
     * 公网 HTTPS 基址，无末尾 {@code /}，如 {@code https://xxxx.trycloudflare.com}。可留空，则使用配置
     * {@code app.telegram.public-base-url}。
     */
    private String publicBaseUrl;
}

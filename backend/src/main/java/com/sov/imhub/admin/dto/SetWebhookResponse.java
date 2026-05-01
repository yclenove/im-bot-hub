package com.sov.imhub.admin.dto;

import lombok.Data;

@Data
public class SetWebhookResponse {
    /** Telegram API 返回的 ok。 */
    private boolean telegramOk;
    /** Telegram 的 description，失败时含原因。 */
    private String description;
    /** 实际向 Telegram 注册的完整 URL。 */
    private String webhookUrl;
}

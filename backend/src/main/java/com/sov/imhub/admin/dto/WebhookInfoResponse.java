package com.sov.imhub.admin.dto;

import lombok.Data;

/**
 * 从 Telegram {@code getWebhookInfo} 结果中抽取常用字段；未返回的字段为 null。
 */
@Data
public class WebhookInfoResponse {
    private boolean telegramOk;
    private String description;
    private String url;
    private Integer pendingUpdateCount;
    private String lastErrorMessage;
    private Integer lastErrorDate;
    private Integer maxConnections;
    private String ipAddress;
    private Boolean hasCustomCertificate;
}

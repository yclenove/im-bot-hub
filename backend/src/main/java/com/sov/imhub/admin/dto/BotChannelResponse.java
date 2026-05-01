package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BotChannelResponse {
    long id;
    long botId;
    String platform;
    boolean enabled;
    /** 回调 URL（须配置 app.telegram.public-base-url 或在管理端可见完整路径模板） */
    String webhookUrl;
    /** 脱敏后的 appId 等 */
    String credentialsSummary;
}

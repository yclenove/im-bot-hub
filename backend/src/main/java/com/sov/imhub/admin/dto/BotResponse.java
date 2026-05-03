package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 机器人响应（纯逻辑分组）。
 */
@Value
@Builder
public class BotResponse {
    Long id;
    String name;
    Long primaryChannelId;
    boolean enabled;
}

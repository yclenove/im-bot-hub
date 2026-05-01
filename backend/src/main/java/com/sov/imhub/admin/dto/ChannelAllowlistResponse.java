package com.sov.imhub.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChannelAllowlistResponse {
    Long id;
    Long channelId;
    String platform;
    String externalUserId;
    boolean enabled;
}

package com.sov.imhub.im.dingtalk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DingTalkCredentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 钉钉机器人「Outgoing」安全设置中的 AppSecret，用于校验请求头 sign */
    private String appSecret;

    public static DingTalkCredentials fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new DingTalkCredentials();
        }
        try {
            return MAPPER.readValue(json.trim(), DingTalkCredentials.class);
        } catch (Exception e) {
            return new DingTalkCredentials();
        }
    }
}

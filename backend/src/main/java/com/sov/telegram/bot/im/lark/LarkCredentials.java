package com.sov.telegram.bot.im.lark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LarkCredentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String appId;
    private String appSecret;

    public static LarkCredentials fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LarkCredentials();
        }
        try {
            return MAPPER.readValue(json.trim(), LarkCredentials.class);
        } catch (Exception e) {
            return new LarkCredentials();
        }
    }
}

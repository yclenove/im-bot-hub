package com.sov.telegram.bot.im.wework;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeWorkCredentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String corpId;
    private Long agentId;
    private String token;
    private String encodingAesKey;

    public static WeWorkCredentials fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new WeWorkCredentials();
        }
        try {
            return MAPPER.readValue(json.trim(), WeWorkCredentials.class);
        } catch (Exception e) {
            return new WeWorkCredentials();
        }
    }
}

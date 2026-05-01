package com.sov.imhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.im.ImPlatform;
import com.sov.imhub.mapper.BotChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 从 t_bot_channel.credentials_json 解析平台凭据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelCredentialResolver {

    private final BotChannelMapper botChannelMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取指定渠道的凭据 JSON 节点。
     */
    public JsonNode getCredentials(long channelId) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null || channel.getCredentialsJson() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(channel.getCredentialsJson());
        } catch (Exception e) {
            log.warn("Failed to parse credentials_json for channelId={}: {}", channelId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定渠道的某个凭据字段值。
     */
    public String getCredentialField(long channelId, String fieldName) {
        JsonNode creds = getCredentials(channelId);
        if (creds == null || !creds.has(fieldName)) {
            return null;
        }
        JsonNode node = creds.get(fieldName);
        return node.isTextual() ? node.asText() : node.toString();
    }

    /**
     * 获取 Telegram Bot Token（从渠道凭据中）。
     */
    public String getTelegramToken(long channelId) {
        return getCredentialField(channelId, "token");
    }

    /**
     * 获取 Telegram Bot Username（从渠道凭据中）。
     */
    public String getTelegramUsername(long channelId) {
        return getCredentialField(channelId, "username");
    }
}

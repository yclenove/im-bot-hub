package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class BotChannelCreateRequest {
    /** TELEGRAM / LARK / DINGTALK / WEWORK / SLACK / DISCORD */
    @NotBlank
    private String platform = "TELEGRAM";

    /** Telegram: Bot Token */
    private String botToken = "";

    /** Telegram: Bot 用户名（可选） */
    private String telegramBotUsername = "";

    /** Telegram: Webhook 密钥（可选） */
    private String webhookSecretToken = "";

    /** Telegram: ALL | GROUPS_ONLY */
    private String chatScope = "ALL";

    /** Telegram: 允许的群 chat_id 列表 */
    private List<Long> allowedChatIds;

    /** 飞书自建应用 App ID */
    private String appId = "";

    /** 飞书 App Secret，或钉钉机器人 AppSecret */
    private String appSecret = "";

    /** 企业微信：企业 CorpID */
    private String corpId = "";

    /** 企业微信：自建应用 AgentId */
    private Long agentId;

    /** 企业微信：接收消息 Token */
    private String callbackToken = "";

    /** 企业微信：EncodingAESKey（43 位） */
    private String encodingAesKey = "";

    /** Slack Signing Secret（用于验证请求签名） */
    private String signingSecret = "";

    /** Discord Public Key（用于验证 Interactions 签名） */
    private String publicKey = "";
}

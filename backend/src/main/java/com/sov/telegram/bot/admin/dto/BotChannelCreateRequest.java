package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BotChannelCreateRequest {
    /** LARK / DINGTALK / WEWORK */
    @NotBlank
    private String platform = "LARK";

    /** 飞书自建应用 App ID；钉钉 Outgoing 可不填 */
    private String appId = "";

    /** 飞书 App Secret，或钉钉机器人 AppSecret（Outgoing 签名校验） */
    private String appSecret = "";

    /** 企业微信：企业 CorpID */
    private String corpId = "";

    /** 企业微信：自建应用 AgentId */
    private Long agentId;

    /** 企业微信：接收消息 Token */
    private String callbackToken = "";

    /** 企业微信：EncodingAESKey（43 位） */
    private String encodingAesKey = "";
}

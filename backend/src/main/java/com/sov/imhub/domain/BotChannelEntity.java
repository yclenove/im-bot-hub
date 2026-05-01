package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_bot_channel")
public class BotChannelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long botId;
    /** TELEGRAM | LARK | WEWORK | DINGTALK | SLACK | DISCORD */
    private String platform;
    private String name;
    private Boolean enabled;
    @TableLogic
    private Integer deleted;
    /** JSON credentials per platform */
    private String credentialsJson;
    private String webhookSecretToken;
    /** ALL | GROUPS_ONLY | ALLOWED_IDS */
    private String chatScope;
    /** JSON array of chat IDs */
    private String allowedChatIdsJson;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

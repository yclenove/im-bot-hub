package com.sov.telegram.bot.domain;

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
    /** LARK | WEWORK | DINGTALK */
    private String platform;
    private Boolean enabled;
    @TableLogic
    private Integer deleted;
    /** JSON credentials per platform */
    private String credentialsJson;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

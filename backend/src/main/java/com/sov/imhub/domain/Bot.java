package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器人（纯逻辑分组，不含任何平台专属字段）。
 * 平台凭证、Webhook 配置等全部在 {@link BotChannelEntity} 中管理。
 */
@Data
@TableName("t_bot")
public class Bot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long primaryChannelId;
    private Boolean enabled;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}

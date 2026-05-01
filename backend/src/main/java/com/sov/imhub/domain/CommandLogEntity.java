package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_command_log")
public class CommandLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime createdAt;
    private Long botId;
    private Long channelId;
    private String platform;
    private String externalUserId;
    private String externalChatId;
    private String command;
    private Long queryDefinitionId;
    private Boolean success;
    private String errorKind;
    private Integer durationMs;
    private String detail;
}

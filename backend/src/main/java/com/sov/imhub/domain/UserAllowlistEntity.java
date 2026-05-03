package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_allowlist")
public class UserAllowlistEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long botId;
    private Long telegramUserId;
    /** 关联渠道 ID（可选，用于多平台白名单） */
    private Long channelId;
    /** 外部用户 ID（通用，各平台均可使用） */
    private String externalUserId;
    private Boolean enabled;
    private LocalDateTime createdAt;
}

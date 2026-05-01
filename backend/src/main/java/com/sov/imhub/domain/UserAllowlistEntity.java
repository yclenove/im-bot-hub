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
    private Boolean enabled;
    private LocalDateTime createdAt;
}

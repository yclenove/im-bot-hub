package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_system_setting")
public class SystemSettingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String settingKey;
    private String settingVal;
    private String description;
    private LocalDateTime updatedAt;
}

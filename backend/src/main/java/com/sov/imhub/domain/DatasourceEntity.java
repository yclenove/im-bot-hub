package com.sov.imhub.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_datasource")
public class DatasourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String sourceType;
    private String jdbcUrl;
    private String apiBaseUrl;
    private String apiPresetKey;
    private String authType;
    private String authConfigJson;
    private String defaultHeadersJson;
    private String defaultQueryParamsJson;
    private Integer requestTimeoutMs;
    private String configJson;
    private String username;
    private String passwordCipher;
    private Integer poolMax;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

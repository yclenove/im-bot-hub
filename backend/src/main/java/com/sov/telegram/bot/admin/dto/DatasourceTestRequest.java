package com.sov.telegram.bot.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试 JDBC 是否可达；新建须带密码，编辑可在密码留空时使用库中已存密码。
 */
@Data
public class DatasourceTestRequest {

    /** 编辑数据源时传入，密码留空则从库解密 */
    private Long id;

    private String sourceType = "DATABASE";

    private String jdbcUrl;

    private String username;

    private String apiBaseUrl;
    private String apiPresetKey;
    private String authType;
    private String authConfigJson;
    private String defaultHeadersJson;
    private String defaultQueryParamsJson;
    private Integer requestTimeoutMs;
    private String configJson;

    /** 明文密码；可与 id 配合：有 id 且为空则用已保存密码 */
    private String passwordPlain;
}

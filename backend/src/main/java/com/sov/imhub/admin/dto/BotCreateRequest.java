package com.sov.imhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建机器人请求（纯逻辑分组）。
 * 平台凭证通过渠道管理接口添加。
 */
@Data
public class BotCreateRequest {

    @NotBlank
    private String name;

    private boolean enabled = true;
}

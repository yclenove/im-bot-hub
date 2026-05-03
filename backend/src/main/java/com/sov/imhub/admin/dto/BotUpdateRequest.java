package com.sov.imhub.admin.dto;

import lombok.Data;

/**
 * 更新机器人请求（纯逻辑分组）。
 */
@Data
public class BotUpdateRequest {
    private String name;
    private Boolean enabled;
}

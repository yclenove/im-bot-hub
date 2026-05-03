-- im-bot-hub V2 全新初始化脚本
-- 库名：im_hub

-- 1. 机器人（纯逻辑分组，不含任何平台专属字段）
CREATE TABLE t_bot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    primary_channel_id BIGINT NULL COMMENT '主渠道 ID（可选）',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL
);

-- 2. 渠道（平台接入单元，每个渠道独立管理凭证）
CREATE TABLE t_bot_channel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL COMMENT 'TELEGRAM|LARK|DINGTALK|WEWORK|SLACK|DISCORD',
    name VARCHAR(128) NULL COMMENT '渠道显示名',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    credentials_json TEXT NOT NULL COMMENT '平台凭据 JSON（加密存储）',
    webhook_secret_token VARCHAR(256) NULL COMMENT 'Webhook 密钥',
    chat_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL|GROUPS_ONLY|ALLOWED_IDS',
    allowed_chat_ids_json TEXT NULL COMMENT 'JSON array of chat IDs',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_ch_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id),
    INDEX idx_ch_bot (bot_id),
    INDEX idx_ch_platform (platform)
);

-- 3. 数据源
CREATE TABLE t_datasource (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'DATABASE' COMMENT 'DATABASE|API',
    jdbc_url VARCHAR(1024) NULL,
    username VARCHAR(256) NULL,
    password_cipher VARCHAR(512) NULL,
    pool_max INT NOT NULL DEFAULT 5,
    api_base_url VARCHAR(1024) NULL,
    api_preset_key VARCHAR(64) NULL,
    auth_type VARCHAR(32) NULL,
    auth_config_json TEXT NULL,
    default_headers_json TEXT NULL,
    default_query_params_json TEXT NULL,
    request_timeout_ms INT NULL DEFAULT 5000,
    config_json TEXT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL
);

-- 4. 查询定义
CREATE TABLE t_query_definition (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    command VARCHAR(64) NOT NULL,
    name VARCHAR(128) NULL,
    telegram_menu_description VARCHAR(256) NULL,
    sql_template TEXT NULL,
    query_mode VARCHAR(16) NULL DEFAULT 'SQL' COMMENT 'SQL|VISUAL|API',
    visual_config_json TEXT NULL,
    api_config_json TEXT NULL,
    param_schema_json TEXT NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    max_rows INT NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    delete_token BIGINT NULL,
    telegram_reply_style VARCHAR(32) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_qd_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id),
    CONSTRAINT fk_qd_ds FOREIGN KEY (datasource_id) REFERENCES t_datasource (id),
    UNIQUE KEY uk_bot_command (bot_id, command)
);

-- 5. 字段映射
CREATE TABLE t_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    query_id BIGINT NOT NULL,
    column_name VARCHAR(256) NOT NULL,
    label VARCHAR(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    mask_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    format_type VARCHAR(32) NULL,
    display_pipeline_json TEXT NULL,
    CONSTRAINT fk_fm_q FOREIGN KEY (query_id) REFERENCES t_query_definition (id) ON DELETE CASCADE,
    UNIQUE KEY uk_q_col (query_id, column_name)
);

-- 6. 审计日志
CREATE TABLE t_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    detail TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created (created_at)
);

-- 7. 渠道白名单
CREATE TABLE t_channel_allowlist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL,
    external_user_id VARCHAR(256) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ca_channel FOREIGN KEY (channel_id) REFERENCES t_bot_channel (id) ON DELETE CASCADE,
    UNIQUE KEY uk_channel_user (channel_id, external_user_id)
);

-- 8. 命令日志
CREATE TABLE t_command_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bot_id BIGINT NOT NULL,
    channel_id BIGINT NULL,
    platform VARCHAR(32) NOT NULL COMMENT 'TELEGRAM|LARK|DINGTALK|WEWORK|SLACK|DISCORD',
    external_user_id VARCHAR(256) NULL,
    external_chat_id VARCHAR(256) NULL,
    command VARCHAR(64) NOT NULL,
    query_definition_id BIGINT NULL,
    success TINYINT(1) NOT NULL DEFAULT 0,
    error_kind VARCHAR(32) NOT NULL,
    duration_ms INT NULL,
    detail VARCHAR(512) NULL,
    CONSTRAINT fk_cmd_log_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id),
    CONSTRAINT fk_cmd_log_channel FOREIGN KEY (channel_id) REFERENCES t_bot_channel (id) ON DELETE SET NULL,
    CONSTRAINT fk_cmd_log_qd FOREIGN KEY (query_definition_id) REFERENCES t_query_definition (id) ON DELETE SET NULL,
    INDEX idx_cmd_log_bot_created (bot_id, created_at),
    INDEX idx_cmd_log_channel_created (channel_id, created_at),
    INDEX idx_cmd_log_created (created_at),
    INDEX idx_cmd_log_platform (platform, created_at)
);

-- 9. Flyway 历史表（Spring Boot 自动管理，此处仅作参考）
-- flyway_schema_history 由 Flyway 自动创建

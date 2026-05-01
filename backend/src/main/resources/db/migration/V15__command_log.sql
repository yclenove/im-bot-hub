-- 通用命令日志（替代 t_telegram_query_log，全平台统一）
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
    CONSTRAINT fk_cmd_log_qd FOREIGN KEY (query_definition_id) REFERENCES t_query_definition (id) ON DELETE SET NULL
);

CREATE INDEX idx_cmd_log_bot_created ON t_command_log (bot_id, created_at);
CREATE INDEX idx_cmd_log_channel_created ON t_command_log (channel_id, created_at);
CREATE INDEX idx_cmd_log_created ON t_command_log (created_at);
CREATE INDEX idx_cmd_log_platform ON t_command_log (platform, created_at);

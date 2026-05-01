-- 通用渠道白名单（替代 t_user_allowlist 的 Telegram 专属逻辑）
CREATE TABLE t_channel_allowlist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL COMMENT 'TELEGRAM|LARK|DINGTALK|WEWORK|SLACK|DISCORD',
    external_user_id VARCHAR(256) NOT NULL COMMENT '平台原生用户 ID',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_channel_allowlist_channel FOREIGN KEY (channel_id) REFERENCES t_bot_channel (id) ON DELETE CASCADE,
    INDEX idx_channel_allowlist_channel (channel_id),
    UNIQUE KEY uk_channel_user (channel_id, external_user_id)
);

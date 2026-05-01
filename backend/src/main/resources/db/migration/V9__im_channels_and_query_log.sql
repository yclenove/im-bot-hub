-- 多 IM 渠道（飞书 / 后续企微、钉钉）；Telegram 仍走 t_bot + /api/webhook/{botId}
CREATE TABLE t_bot_channel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL COMMENT 'LARK | WEWORK | DINGTALK',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    credentials_json TEXT NOT NULL COMMENT 'JSON: appId/appSecret 等',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bot_channel_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id) ON DELETE CASCADE,
    INDEX idx_bot_channel_bot (bot_id),
    INDEX idx_bot_channel_platform (bot_id, platform)
);

-- 审计日志扩展：非 Telegram 时填 im_platform + external_*；Telegram 行为不变
ALTER TABLE t_telegram_query_log
    ADD COLUMN im_platform VARCHAR(16) NULL COMMENT 'TELEGRAM | LARK ...' AFTER bot_id,
    ADD COLUMN external_user_id VARCHAR(128) NULL AFTER telegram_user_id,
    ADD COLUMN external_chat_id VARCHAR(128) NULL AFTER chat_id;

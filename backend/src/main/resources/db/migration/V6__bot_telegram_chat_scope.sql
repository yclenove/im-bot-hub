-- 限制机器人仅在指定 Telegram 群处理消息（可选）；私聊可一键关闭
ALTER TABLE t_bot
    ADD COLUMN telegram_chat_scope VARCHAR(16) NOT NULL DEFAULT 'ALL'
        COMMENT 'ALL|GROUPS_ONLY' AFTER enabled,
    ADD COLUMN telegram_allowed_chat_ids_json TEXT NULL
        COMMENT 'JSON array of long, e.g. [-1001234567890]' AFTER telegram_chat_scope;

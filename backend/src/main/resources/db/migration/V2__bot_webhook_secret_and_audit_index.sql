-- Telegram Bot API: optional secret for webhook verification (request header X-Telegram-Bot-Api-Secret-Token)
ALTER TABLE t_bot
    ADD COLUMN webhook_secret_token VARCHAR(256) NULL COMMENT 'optional; if set, webhook must send matching header' AFTER telegram_bot_username;

CREATE INDEX idx_audit_log_created_at ON t_audit_log (created_at);

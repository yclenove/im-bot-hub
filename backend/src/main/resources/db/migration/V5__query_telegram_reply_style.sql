-- Telegram 回复展现样式（见 FieldRenderService / 管理端「Telegram 展现」）
ALTER TABLE t_query_definition
    ADD COLUMN telegram_reply_style VARCHAR(32) NOT NULL DEFAULT 'LIST'
        COMMENT 'LIST|LIST_CODE|SECTION|MONO_PRE' AFTER enabled;

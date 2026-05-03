-- 白名单支持多平台：添加 bot_id 字段到 t_channel_allowlist
ALTER TABLE t_channel_allowlist
    ADD COLUMN bot_id BIGINT COMMENT '关联机器人 ID' AFTER id;

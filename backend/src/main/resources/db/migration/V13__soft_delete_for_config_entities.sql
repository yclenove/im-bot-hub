ALTER TABLE t_bot
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER enabled,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '逻辑删除时间' AFTER updated_at;

ALTER TABLE t_datasource
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER pool_max,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '逻辑删除时间' AFTER created_at;

ALTER TABLE t_query_definition
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER enabled,
    ADD COLUMN delete_token BIGINT NOT NULL DEFAULT 0 COMMENT '活动行为 0，删除后写入唯一值用于释放唯一约束' AFTER deleted,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '逻辑删除时间' AFTER updated_at;

ALTER TABLE t_bot_channel
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER enabled,
    ADD COLUMN deleted_at DATETIME NULL COMMENT '逻辑删除时间' AFTER created_at;

ALTER TABLE t_query_definition
    DROP INDEX uk_bot_command,
    ADD UNIQUE KEY uk_bot_command_delete_token (bot_id, command, delete_token);

CREATE INDEX idx_bot_deleted ON t_bot(deleted);
CREATE INDEX idx_datasource_deleted ON t_datasource(deleted);
CREATE INDEX idx_query_definition_deleted ON t_query_definition(deleted);
CREATE INDEX idx_bot_channel_deleted ON t_bot_channel(deleted);

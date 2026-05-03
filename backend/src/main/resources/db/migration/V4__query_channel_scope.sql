-- 查询定义：限定适用渠道（NULL 或空 = 所有渠道）
ALTER TABLE t_query_definition
    ADD COLUMN channel_scope_json TEXT NULL COMMENT 'JSON array of channel IDs, NULL = all channels';

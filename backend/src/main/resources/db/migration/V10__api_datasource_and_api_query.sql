ALTER TABLE t_datasource
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'DATABASE' AFTER name,
    ADD COLUMN api_base_url VARCHAR(1024) NULL AFTER jdbc_url,
    ADD COLUMN api_preset_key VARCHAR(64) NULL AFTER api_base_url,
    ADD COLUMN auth_type VARCHAR(32) NULL AFTER api_preset_key,
    ADD COLUMN auth_config_json TEXT NULL AFTER auth_type,
    ADD COLUMN default_headers_json TEXT NULL AFTER auth_config_json,
    ADD COLUMN default_query_params_json TEXT NULL AFTER default_headers_json,
    ADD COLUMN request_timeout_ms INT NOT NULL DEFAULT 5000 AFTER default_query_params_json,
    ADD COLUMN config_json TEXT NULL AFTER request_timeout_ms;

ALTER TABLE t_query_definition
    ADD COLUMN api_config_json TEXT NULL AFTER visual_config_json;

UPDATE t_datasource
SET source_type = 'DATABASE',
    request_timeout_ms = 5000
WHERE source_type IS NULL OR source_type = '';

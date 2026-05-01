-- Config / metadata database (not business replica data)
CREATE TABLE t_bot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    telegram_bot_token VARCHAR(512) NOT NULL,
    telegram_bot_username VARCHAR(128) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE t_datasource (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    jdbc_url VARCHAR(1024) NOT NULL,
    username VARCHAR(256) NOT NULL,
    password_cipher VARCHAR(512) NOT NULL,
    pool_max INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_query_definition (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    command VARCHAR(64) NOT NULL,
    sql_template TEXT NOT NULL,
    param_schema_json TEXT NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    max_rows INT NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_qd_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id),
    CONSTRAINT fk_qd_ds FOREIGN KEY (datasource_id) REFERENCES t_datasource (id),
    UNIQUE KEY uk_bot_command (bot_id, command)
);

CREATE TABLE t_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    query_id BIGINT NOT NULL,
    column_name VARCHAR(256) NOT NULL,
    label VARCHAR(256) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    mask_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    format_type VARCHAR(32) NULL,
    CONSTRAINT fk_fm_q FOREIGN KEY (query_id) REFERENCES t_query_definition (id) ON DELETE CASCADE,
    UNIQUE KEY uk_q_col (query_id, column_name)
);

CREATE TABLE t_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    detail TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_user_allowlist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_uw_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id) ON DELETE CASCADE,
    UNIQUE KEY uk_bot_uid (bot_id, telegram_user_id)
);

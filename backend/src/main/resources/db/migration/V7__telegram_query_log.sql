-- Telegram 用户发起的查询类命令审计（不含 Token / 业务参数明文）
CREATE TABLE t_telegram_query_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bot_id BIGINT NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    command VARCHAR(64) NOT NULL,
    query_definition_id BIGINT NULL,
    success TINYINT(1) NOT NULL DEFAULT 0,
    error_kind VARCHAR(32) NOT NULL,
    duration_ms INT NULL,
    detail VARCHAR(512) NULL,
    CONSTRAINT fk_tg_log_bot FOREIGN KEY (bot_id) REFERENCES t_bot (id),
    CONSTRAINT fk_tg_log_qd FOREIGN KEY (query_definition_id) REFERENCES t_query_definition (id) ON DELETE SET NULL
);

CREATE INDEX idx_tg_query_log_bot_created ON t_telegram_query_log (bot_id, created_at);
CREATE INDEX idx_tg_query_log_created ON t_telegram_query_log (created_at);

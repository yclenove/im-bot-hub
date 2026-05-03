-- 渠道健康检查日志
CREATE TABLE t_channel_health_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'HEALTHY/DEGRADED/UNHEALTHY',
    check_type VARCHAR(32) NOT NULL COMMENT 'WEBHOOK/CONNECTIVITY/TOKEN',
    message TEXT,
    response_time_ms INT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_checked_at (checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道健康检查日志';

-- 命令统计（每日聚合）
CREATE TABLE t_command_stats_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL COMMENT '统计日期',
    bot_id BIGINT NOT NULL,
    platform VARCHAR(32) COMMENT '平台：TELEGRAM/LARK/SLACK 等，NULL 表示全部',
    command VARCHAR(64) NOT NULL,
    total_count INT DEFAULT 0 COMMENT '总调用次数',
    success_count INT DEFAULT 0 COMMENT '成功次数',
    fail_count INT DEFAULT 0 COMMENT '失败次数',
    avg_duration_ms INT DEFAULT 0 COMMENT '平均耗时(ms)',
    unique_users INT DEFAULT 0 COMMENT '独立用户数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_bot_cmd (stat_date, bot_id, command, platform),
    INDEX idx_stat_date (stat_date),
    INDEX idx_bot_id (bot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='命令统计（每日聚合）';

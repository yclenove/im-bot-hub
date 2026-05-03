-- NL2SQL 查询历史
CREATE TABLE t_nl2sql_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bot_id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    question TEXT NOT NULL COMMENT '用户自然语言问题',
    generated_sql TEXT COMMENT 'AI 生成的 SQL',
    confidence DECIMAL(5,2) COMMENT '置信度 0-100',
    executed TINYINT(1) DEFAULT 0 COMMENT '是否已执行',
    execution_result TEXT COMMENT '执行结果 JSON',
    feedback_score INT COMMENT '用户反馈评分 1-5',
    feedback_comment TEXT COMMENT '用户反馈评论',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_bot_id (bot_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NL2SQL 查询历史';

-- 异常检测规则
CREATE TABLE t_anomaly_detection (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '规则名称',
    metric_name VARCHAR(128) NOT NULL COMMENT '指标名称',
    detection_type VARCHAR(32) NOT NULL COMMENT '检测类型：THRESHOLD/ZSCORE/IQR',
    threshold_value DECIMAL(20,4) COMMENT '阈值',
    threshold_operator VARCHAR(16) COMMENT '比较运算符：LT/GT/EQ/BETWEEN',
    zscore_threshold DECIMAL(5,2) DEFAULT 3.00 COMMENT 'Z-score 阈值',
    window_size INT DEFAULT 60 COMMENT '窗口大小（分钟）',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    notify_channels VARCHAR(256) COMMENT '通知渠道',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_metric_name (metric_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常检测规则';

-- 异常检测日志
CREATE TABLE t_anomaly_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    detection_id BIGINT NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    anomaly_type VARCHAR(32) NOT NULL COMMENT '异常类型：SPIKE/DROP/SHIFT/CYCLE',
    detected_value DECIMAL(20,4) NOT NULL,
    baseline_value DECIMAL(20,4),
    zscore DECIMAL(10,4),
    severity VARCHAR(16) NOT NULL COMMENT '严重程度：LOW/MEDIUM/HIGH/CRITICAL',
    root_cause TEXT COMMENT '根因分析',
    resolved TINYINT(1) DEFAULT 0,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_detection_id (detection_id),
    INDEX idx_metric_name (metric_name),
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常检测日志';

-- 预置异常检测规则
INSERT INTO t_anomaly_detection (name, metric_name, detection_type, threshold_value, threshold_operator, zscore_threshold, window_size) VALUES
('成功率骤降', 'success_rate', 'ZSCORE', NULL, NULL, 3.00, 60),
('响应时间飙升', 'avg_response_time', 'ZSCORE', NULL, NULL, 3.00, 60),
('请求量异常', 'request_count', 'ZSCORE', NULL, NULL, 3.00, 60);

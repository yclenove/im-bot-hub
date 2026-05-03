-- 告警配置
CREATE TABLE t_alert_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '告警名称',
    alert_type VARCHAR(64) NOT NULL COMMENT '告警类型：SUCCESS_RATE/RESPONSE_TIME/CHANNEL_DOWN',
    threshold DECIMAL(10,2) NOT NULL COMMENT '阈值',
    operator VARCHAR(16) NOT NULL DEFAULT 'LT' COMMENT '比较运算符：LT/GT/EQ',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    notify_channels VARCHAR(256) COMMENT '通知渠道：EMAIL/WEBHOOK/FEISHU',
    notify_target TEXT COMMENT '通知目标（邮箱/URL）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警配置';

-- 告警日志
CREATE TABLE t_alert_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_config_id BIGINT,
    alert_type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    current_value DECIMAL(10,2),
    threshold DECIMAL(10,2),
    notified TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_alert_type (alert_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警日志';

-- 预置告警规则
INSERT INTO t_alert_config (name, alert_type, threshold, operator, notify_channels) VALUES
('成功率低于90%', 'SUCCESS_RATE', 90.00, 'LT', 'WEBHOOK'),
('响应时间超过5秒', 'RESPONSE_TIME', 5000.00, 'GT', 'WEBHOOK'),
('渠道连续失败3次', 'CHANNEL_DOWN', 3.00, 'GT', 'WEBHOOK');

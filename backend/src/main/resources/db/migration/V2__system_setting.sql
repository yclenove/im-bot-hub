-- 系统设置表
CREATE TABLE IF NOT EXISTS t_system_setting (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(128) NOT NULL UNIQUE,
    setting_val TEXT,
    description VARCHAR(255),
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置';

-- 默认设置
INSERT INTO t_system_setting (setting_key, setting_val, description) VALUES
('public-base-url', '', '公网基址（HTTPS），用于生成 Webhook URL'),
('default-query-timeout-ms', '30000', '默认查询超时（毫秒）'),
('default-max-rows', '50', '默认最大返回行数'),
('encryption-status', '未配置', '加密密钥状态（只读）');

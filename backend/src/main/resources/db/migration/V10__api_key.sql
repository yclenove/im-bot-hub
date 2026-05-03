-- API Key 管理
CREATE TABLE t_api_key (
    id BIGINT NOT NULL AUTO_INCREMENT,
    key_name VARCHAR(128) NOT NULL COMMENT 'Key 名称',
    api_key VARCHAR(64) NOT NULL COMMENT 'API Key（唯一）',
    secret_key VARCHAR(128) NOT NULL COMMENT 'Secret Key',
    description TEXT,
    permissions VARCHAR(256) DEFAULT 'READ' COMMENT '权限：READ/WRITE/ADMIN',
    rate_limit INT DEFAULT 100 COMMENT '每分钟请求限制',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    expires_at TIMESTAMP NULL COMMENT '过期时间',
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_key (api_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key';

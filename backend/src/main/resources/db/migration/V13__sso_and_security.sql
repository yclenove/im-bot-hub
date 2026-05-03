-- SSO 配置
CREATE TABLE t_sso_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL COMMENT '提供商：OAUTH2/LDAP/SAML',
    name VARCHAR(128) NOT NULL COMMENT '配置名称',
    config_json TEXT NOT NULL COMMENT '配置 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 配置';

-- 数据脱敏规则
CREATE TABLE t_desensitization_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '规则名称',
    field_pattern VARCHAR(256) NOT NULL COMMENT '字段匹配模式（正则）',
    mask_type VARCHAR(32) NOT NULL COMMENT '脱敏类型：PARTIAL/FULL/HASH/MASK',
    mask_config TEXT COMMENT '脱敏配置 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据脱敏规则';

-- 权限矩阵
CREATE TABLE t_permission_matrix (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role VARCHAR(32) NOT NULL COMMENT '角色',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型：BOT/CHANNEL/QUERY/DATASOURCE',
    resource_id BIGINT COMMENT '资源 ID（NULL 表示所有）',
    permissions VARCHAR(256) NOT NULL COMMENT '权限列表 JSON：["VIEW","EDIT","DELETE"]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_resource (role, resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限矩阵';

-- 租户信息
CREATE TABLE t_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '租户名称',
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '租户编码',
    domain VARCHAR(256) COMMENT '自定义域名',
    plan VARCHAR(32) DEFAULT 'FREE' COMMENT '套餐：FREE/PRO/ENTERPRISE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/SUSPENDED/CANCELLED',
    config_json TEXT COMMENT '租户配置 JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户信息';

-- 租户配额
CREATE TABLE t_tenant_quota (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    quota_type VARCHAR(64) NOT NULL COMMENT '配额类型：BOT_COUNT/CHANNEL_COUNT/QUERY_COUNT/API_CALLS',
    quota_limit BIGINT NOT NULL DEFAULT 0 COMMENT '配额上限',
    current_usage BIGINT NOT NULL DEFAULT 0 COMMENT '当前使用量',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_quota (tenant_id, quota_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户配额';

-- 预置脱敏规则
INSERT INTO t_desensitization_rule (name, field_pattern, mask_type, mask_config) VALUES
('手机号脱敏', '.*phone.*|.*mobile.*', 'PARTIAL', '{"prefix":3,"suffix":4,"mask":"*"}'),
('邮箱脱敏', '.*email.*|.*mail.*', 'PARTIAL', '{"prefix":3,"suffix":0,"mask":"***@***"}'),
('身份证脱敏', '.*id_card.*|.*identity.*', 'PARTIAL', '{"prefix":6,"suffix":4,"mask":"*"}'),
('银行卡脱敏', '.*bank_card.*|.*card_no.*', 'PARTIAL', '{"prefix":4,"suffix":4,"mask":"*"}');

-- 预置权限矩阵
INSERT INTO t_permission_matrix (role, resource_type, resource_id, permissions) VALUES
('SUPER_ADMIN', 'BOT', NULL, '["VIEW","EDIT","DELETE","CREATE"]'),
('SUPER_ADMIN', 'CHANNEL', NULL, '["VIEW","EDIT","DELETE","CREATE"]'),
('SUPER_ADMIN', 'QUERY', NULL, '["VIEW","EDIT","DELETE","CREATE","EXECUTE"]'),
('SUPER_ADMIN', 'DATASOURCE', NULL, '["VIEW","EDIT","DELETE","CREATE"]'),
('ADMIN', 'BOT', NULL, '["VIEW","EDIT","DELETE","CREATE"]'),
('ADMIN', 'CHANNEL', NULL, '["VIEW","EDIT","DELETE","CREATE"]'),
('ADMIN', 'QUERY', NULL, '["VIEW","EDIT","DELETE","CREATE","EXECUTE"]'),
('ADMIN', 'DATASOURCE', NULL, '["VIEW","EDIT","CREATE"]'),
('OPERATOR', 'BOT', NULL, '["VIEW"]'),
('OPERATOR', 'CHANNEL', NULL, '["VIEW"]'),
('OPERATOR', 'QUERY', NULL, '["VIEW","EXECUTE"]'),
('OPERATOR', 'DATASOURCE', NULL, '["VIEW"]'),
('VIEWER', 'BOT', NULL, '["VIEW"]'),
('VIEWER', 'CHANNEL', NULL, '["VIEW"]'),
('VIEWER', 'QUERY', NULL, '["VIEW"]'),
('VIEWER', 'DATASOURCE', NULL, '["VIEW"]');

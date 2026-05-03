-- 管理用户表（角色权限）
CREATE TABLE t_admin_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(256) NOT NULL COMMENT '密码哈希',
    display_name VARCHAR(64) COMMENT '显示名称',
    role VARCHAR(32) NOT NULL DEFAULT 'VIEWER' COMMENT '角色：SUPER_ADMIN/ADMIN/OPERATOR/VIEWER',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理用户';

-- 默认超级管理员（密码：admin123，BCrypt 加密）
INSERT INTO t_admin_user (username, password_hash, display_name, role, enabled) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '超级管理员', 'SUPER_ADMIN', 1);

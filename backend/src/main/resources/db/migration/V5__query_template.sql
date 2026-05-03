-- 查询模板市场
CREATE TABLE t_query_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '模板名称',
    category VARCHAR(64) NOT NULL COMMENT '分类：ecommerce/saas/ops/custom',
    description TEXT COMMENT '模板描述',
    config_json TEXT NOT NULL COMMENT '完整查询配置 JSON',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    author VARCHAR(64) COMMENT '作者',
    downloads INT DEFAULT 0 COMMENT '下载次数',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_category (category),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询模板';

-- 预置模板数据
INSERT INTO t_query_template (name, category, description, config_json, version, author) VALUES
('订单查询', 'ecommerce', '按订单号查询订单详情', '{"command":"order","queryMode":"API","timeoutMs":5000,"maxRows":1}', 1, 'system'),
('库存查询', 'ecommerce', '按商品 SKU 查询库存', '{"command":"stock","queryMode":"SQL","timeoutMs":5000,"maxRows":1}', 1, 'system'),
('用户查询', 'saas', '按用户 ID 查询用户信息', '{"command":"user","queryMode":"SQL","timeoutMs":5000,"maxRows":1}', 1, 'system'),
('服务器状态', 'ops', '查询服务器 CPU/内存/磁盘使用率', '{"command":"server","queryMode":"API","timeoutMs":5000,"maxRows":1}', 1, 'system'),
('日志查询', 'ops', '按关键词查询最近日志', '{"command":"log","queryMode":"SQL","timeoutMs":10000,"maxRows":10}', 1, 'system');

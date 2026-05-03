-- 工作流定义
CREATE TABLE t_workflow_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '描述',
    steps_json TEXT NOT NULL COMMENT '步骤定义 JSON',
    variables_json TEXT COMMENT '变量定义 JSON',
    trigger_type VARCHAR(32) COMMENT '触发类型：MANUAL/CRON/EVENT',
    trigger_config TEXT COMMENT '触发配置 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 1,
    created_by BIGINT COMMENT '创建者用户 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义';

-- 工作流执行记录
CREATE TABLE t_workflow_execution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT '状态：RUNNING/COMPLETED/FAILED/CANCELLED',
    input_json TEXT COMMENT '输入参数 JSON',
    output_json TEXT COMMENT '输出结果 JSON',
    current_step VARCHAR(128) COMMENT '当前步骤 ID',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    error_message TEXT,
    triggered_by BIGINT COMMENT '触发者用户 ID',
    PRIMARY KEY (id),
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录';

-- 工作流步骤执行日志
CREATE TABLE t_workflow_step_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_id BIGINT NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    step_name VARCHAR(128),
    step_type VARCHAR(32) NOT NULL COMMENT '步骤类型：QUERY/CONDITION/DELAY/NOTIFICATION/APPROVAL',
    status VARCHAR(32) NOT NULL COMMENT '状态：RUNNING/COMPLETED/FAILED/SKIPPED',
    input_json TEXT,
    output_json TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_execution_id (execution_id),
    INDEX idx_step_id (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流步骤执行日志';

-- 审批规则
CREATE TABLE t_approval_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '规则名称',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型：WORKFLOW/QUERY/CHANNEL',
    action_type VARCHAR(32) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/EXECUTE',
    approver_ids TEXT COMMENT '审批人 ID 列表 JSON',
    approval_type VARCHAR(32) DEFAULT 'ANY' COMMENT '审批类型：ANY/ALL/SEQUENTIAL',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_resource_type (resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批规则';

-- 审批记录
CREATE TABLE t_approval_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    requester_id BIGINT NOT NULL COMMENT '申请人 ID',
    approver_id BIGINT COMMENT '审批人 ID',
    status VARCHAR(32) NOT NULL COMMENT '状态：PENDING/APPROVED/REJECTED/CANCELLED',
    comment TEXT COMMENT '审批意见',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    INDEX idx_rule_id (rule_id),
    INDEX idx_status (status),
    INDEX idx_requester_id (requester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';

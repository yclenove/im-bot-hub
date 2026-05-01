-- 查询展示名与 Telegram setMyCommands 描述覆盖（留空则沿用原有自动生成逻辑）
ALTER TABLE t_query_definition
    ADD COLUMN `name` VARCHAR(128) NULL COMMENT '查询名称，管理端与菜单副标题优先' AFTER `command`,
    ADD COLUMN `telegram_menu_description` VARCHAR(255) NULL COMMENT '整条菜单描述覆盖；为空则按模式+参数示例自动生成' AFTER `name`;

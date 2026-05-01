-- Bot-Channel 分离：增强 t_bot_channel，迁移 TG 凭据，添加 primary_channel_id

-- 1. 增强 t_bot_channel：新增 name / webhook_secret_token / chat_scope / allowed_chat_ids_json
ALTER TABLE t_bot_channel
    ADD COLUMN name VARCHAR(128) NULL COMMENT '渠道显示名' AFTER platform,
    ADD COLUMN webhook_secret_token VARCHAR(256) NULL COMMENT 'Webhook 密钥' AFTER credentials_json,
    ADD COLUMN chat_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL|GROUPS_ONLY|ALLOWED_IDS' AFTER webhook_secret_token,
    ADD COLUMN allowed_chat_ids_json TEXT NULL COMMENT 'JSON array of chat IDs' AFTER chat_scope;

-- 2. t_bot 新增 primary_channel_id
ALTER TABLE t_bot
    ADD COLUMN primary_channel_id BIGINT NULL COMMENT '主渠道 ID（可选）' AFTER name;

-- 3. 数据迁移：为每个有 telegram_bot_token 的 bot 创建 TELEGRAM 渠道
-- 使用存储过程避免复杂 SQL
DELIMITER //
CREATE PROCEDURE migrate_telegram_channels()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_bot_id BIGINT;
    DECLARE v_token VARCHAR(512);
    DECLARE v_username VARCHAR(128);
    DECLARE v_chat_scope VARCHAR(16);
    DECLARE v_allowed_chat_ids TEXT;
    DECLARE v_channel_id BIGINT;

    DECLARE cur CURSOR FOR
        SELECT id, telegram_bot_token, telegram_bot_username, telegram_chat_scope, telegram_allowed_chat_ids_json
        FROM t_bot
        WHERE telegram_bot_token IS NOT NULL AND telegram_bot_token != '';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_bot_id, v_token, v_username, v_chat_scope, v_allowed_chat_ids;
        IF done THEN LEAVE read_loop; END IF;

        -- 检查是否已有 TELEGRAM 渠道
        SELECT id INTO v_channel_id FROM t_bot_channel
        WHERE bot_id = v_bot_id AND platform = 'TELEGRAM' AND deleted = 0
        LIMIT 1;

        IF v_channel_id IS NULL THEN
            -- 创建 TELEGRAM 渠道
            INSERT INTO t_bot_channel (bot_id, platform, name, credentials_json, chat_scope, allowed_chat_ids_json, enabled, deleted)
            VALUES (v_bot_id, 'TELEGRAM', v_username,
                    JSON_OBJECT('token', v_token, 'username', v_username),
                    v_chat_scope, v_allowed_chat_ids, 1, 0);
            SET v_channel_id = LAST_INSERT_ID();
        END IF;

        -- 回填 primary_channel_id
        UPDATE t_bot SET primary_channel_id = v_channel_id WHERE id = v_bot_id;

        SET v_channel_id = NULL;
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

CALL migrate_telegram_channels();
DROP PROCEDURE migrate_telegram_channels;

-- 4. 迁移 t_user_allowlist 到 t_channel_allowlist
INSERT INTO t_channel_allowlist (channel_id, platform, external_user_id, enabled)
SELECT bc.id, 'TELEGRAM', CAST(ua.telegram_user_id AS CHAR), ua.enabled
FROM t_user_allowlist ua
JOIN t_bot_channel bc ON bc.bot_id = ua.bot_id AND bc.platform = 'TELEGRAM' AND bc.deleted = 0
WHERE ua.deleted = 0;

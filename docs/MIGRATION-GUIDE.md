# V1 → V2 迁移指南 / Migration Guide

> 本文档说明从 telegram-query-bot (V1) 迁移到 im-bot-hub (V2) 的步骤。

---

## 1. 数据库迁移 / Database migration

### 1.1 前置条件

- 备份 `tg_query_meta` 数据库
- 确认 Flyway 当前版本为 V13（软删除已应用）

### 1.2 Flyway 脚本（自动执行）

| 脚本 | 内容 | 回滚风险 |
|------|------|----------|
| V14 | `t_bot` 新增 `primary_channel_id`；`t_bot_channel` 新增 `name`/`webhook_secret_token`/`chat_scope`/`allowed_chat_ids_json`；数据迁移：TG bot 的 token 写入 t_bot_channel | 低（仅 ADD COLUMN + INSERT） |
| V15 | 新建 `t_channel_allowlist`；从 `t_user_allowlist` 迁移数据 | 低（新表 + 数据复制） |
| V16 | 新建 `t_command_log` | 低（仅新表） |

### 1.3 V14 数据迁移逻辑

```sql
-- 伪代码：对每个有 telegram_bot_token 的 t_bot
-- 1. 检查 t_bot_channel 中是否已有该 bot 的 TELEGRAM 渠道
-- 2. 若无，INSERT 一条：bot_id, platform='TELEGRAM',
--    credentials_json=json_object('token', telegram_bot_token, 'username', telegram_bot_username)
-- 3. 回填 t_bot.primary_channel_id 为新创建的 channel_id
-- 4. t_bot 的 telegram_bot_token 等字段保留不删（兼容期）
```

### 1.4 V15 数据迁移逻辑

```sql
-- 从 t_user_allowlist 复制到 t_channel_allowlist
-- 1. 对每条 allowlist，找到该 bot 的 TELEGRAM 渠道的 channel_id
-- 2. INSERT: channel_id, platform='TELEGRAM', external_user_id=CAST(telegram_user_id AS VARCHAR)
```

---

## 2. API 迁移 / API migration

| V1 API | V2 API | 兼容策略 |
|--------|--------|----------|
| `POST /api/admin/bots` (含 telegram_bot_token) | `POST /api/admin/bots` (仅 name + enabled) | V2 新增字段 optional，旧字段 deprecated 但保留 |
| `GET /api/admin/telegram-query-logs` | `GET /api/admin/command-logs` | V2 保留旧路径，返回 redirect 或相同数据 |
| `POST /api/admin/bots/{botId}/allowlist` | `POST /api/admin/channels/{channelId}/allowlist` | 旧 API 保留，写入 t_user_allowlist；新 API 写入 t_channel_allowlist |
| Webhook `POST /api/webhook/{botId}` | `POST /api/webhook/telegram/{channelId}` | 旧路径保留兼容（从 t_bot 反查 primary_channel_id） |

---

## 3. 前端迁移 / Frontend migration

- Bot 创建表单移除 Telegram token 字段
- 新增 Channel 管理 Tab（或独立页面）
- 查询日志 Tab 改为通用命令日志，支持平台筛选
- 白名单管理改为基于 Channel

---

## 4. 配置迁移 / Configuration migration

| V1 配置 | V2 变化 |
|---------|---------|
| `spring.application.name: telegram-query-bot` | → `im-bot-hub` |
| `com.sov.telegram.bot` 包名 | → `com.sov.imhub` |
| `application.yml` 其余配置 | 不变 |

---

## 5. 部署步骤 / Deployment steps

1. 备份数据库
2. 停止 V1 服务
3. 部署 V2 JAR
4. 启动 V2（Flyway 自动执行 V14-V16）
5. 验证：管理端登录 → 检查 Bot/Channel 数据 → 测试查询
6. 验证各平台 Webhook 可用

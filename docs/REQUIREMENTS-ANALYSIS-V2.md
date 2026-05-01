# 需求分析 V2（中英）/ Requirements Analysis V2

> **V2 变更范围**：品牌重塑（包名/项目名）+ Bot-Channel 分离 + 通用白名单/日志 + 新平台接入（Slack/Discord）。

---

## 1. 业务问题 / Business problem

**中文**

V1 以 Telegram 为核心，虽然已接入飞书/钉钉/企微，但存在以下问题：
1. **数据模型耦合**：`t_bot` 包含 Telegram 专属字段（token、username、chat_scope），其他平台的凭据放在 `t_bot_channel.credentials_json` 中，导致配置入口不一致。
2. **白名单平台绑定**：`t_user_allowlist` 仅支持 `telegram_user_id`，飞书/钉钉/企微用户无法使用白名单功能。
3. **日志归属模糊**：`t_telegram_query_log` 名称暗示仅 Telegram，但实际已记录多平台数据。
4. **编排服务 Telegram 分支**：`QueryOrchestrationService` 中存在 `if (platform == TELEGRAM)` 的特殊处理。
5. **品牌限制**：项目名和包名绑定 Telegram，不利于作为通用平台推广。

**English**

V1 is Telegram-centric despite supporting other platforms, causing:
1. Data model coupling: `t_bot` holds TG-specific fields; other platforms store credentials in `t_bot_channel`.
2. Allowlist is TG-only: `t_user_allowlist` only has `telegram_user_id`.
3. Log naming: `t_telegram_query_log` implies TG-only but records all platforms.
4. Orchestration has TG-specific branches.
5. Branding tied to Telegram, limiting generic adoption.

---

## 2. 需求分解 / Requirement breakdown

### 2.1 品牌重塑 / Branding

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| B-01 | 项目名改为 `im-bot-hub` | P0 | pom.xml、README、AGENTS、前端标题 |
| B-02 | Java 包名改为 `com.sov.imhub` | P0 | 全量目录移动 + import 替换 |
| B-03 | 文档体系统一更新 | P0 | PRD/需求/设计/测试/迁移指南 |

### 2.2 数据模型重构 / Data model

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| D-01 | Bot Entity 纯化 | P0 | 移除 TG 专属字段（token/username/chat_scope/allowed_chat_ids），仅保留 name + enabled + primary_channel_id |
| D-02 | Channel Entity 增强 | P0 | 新增 name / webhook_secret_token / chat_scope / allowed_chat_ids_json |
| D-03 | 通用白名单表 | P0 | `t_channel_allowlist`：channel_id + platform + external_user_id |
| D-04 | 通用命令日志表 | P0 | `t_command_log`：替代 t_telegram_query_log，全平台统一 |
| D-05 | Flyway 数据迁移 | P0 | V14 将 TG bot 的 token 迁入 t_bot_channel |

### 2.3 后端服务重构 / Backend services

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| S-01 | InboundCommandContext 通用化 | P0 | telegramUserId/chatId/message → userId/chatId/rawMessage |
| S-02 | QueryOrchestrationService 去耦合 | P0 | 白名单→ChannelAllowlistService；chatAccess→通用 chatScopeCheck；日志→CommandLogService |
| S-03 | WebhookDispatchService Channel 驱动 | P0 | 从 t_bot_channel 获取 token（而非 t_bot） |
| S-04 | ChannelCredentialResolver | P0 | 按 platform 解析 credentials_json |
| S-05 | CommandLogService | P0 | 替代 TelegramQueryLogService |
| S-06 | ChannelAllowlistService | P0 | 替代 UserAllowlistMapper 的 TG 逻辑 |
| S-07 | TelegramQueryLogService deprecated | P1 | 保留兼容，内部委托 CommandLogService |

### 2.4 API / DTO 重构

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| A-01 | Bot DTO 去 TG 字段 | P0 | BotCreateRequest/BotResponse 移除 telegram* 字段 |
| A-02 | 新 CommandLog API | P0 | `/api/admin/command-logs` 替代 `/api/admin/telegram-query-logs` |
| A-03 | Channel Allowlist API | P0 | `/api/admin/channels/{channelId}/allowlist` |
| A-04 | 旧 API 兼容 | P1 | 旧 telegram-query-logs 保留一个版本周期，返回 redirect 或别名 |
| A-05 | Slack Webhook API | P1 | `/api/webhook/slack/{channelId}` |
| A-06 | Discord Webhook API | P1 | `/api/webhook/discord/{channelId}` |

### 2.5 前端重构 / Frontend

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| F-01 | Bot 创建流程重构 | P0 | 创建 Bot → 绑定 Channel 两步流程 |
| F-02 | Channel 管理页 | P0 | 独立管理各平台渠道，按平台类型动态表单 |
| F-03 | 通用日志查询页 | P0 | 替代 TG 专属日志 Tab，支持平台筛选 |
| F-04 | 通用白名单管理 | P1 | 基于 Channel 的白名单 CRUD |
| F-05 | Channel 健康面板 | P1 | 展示各 Channel Webhook 状态 |
| F-06 | 命令统计面板 | P2 | 按平台/命令/用户维度统计图表 |

### 2.6 新平台接入 / New platforms

| ID | 需求 | 优先级 | 说明 |
|----|------|--------|------|
| P-01 | Slack Bot 接入 | P1 | Events API + Slash Commands；Block Kit 回复 |
| P-02 | Discord Bot 接入 | P1 | Interactions Endpoint；Embed 回复 |
| P-03 | ImPlatformPlugin SPI | P2 | 插件式平台接入接口 |

---

## 3. 约束 / Constraints

| 约束 | 说明 |
|------|------|
| **向后兼容** | V1 的 t_bot / t_bot_channel 数据在 Flyway 迁移后不丢失 |
| **API 兼容** | 旧 API 保留一个版本周期（至少），新 API 优先 |
| **不改业务库** | 业务只读库不受影响，查询模板执行逻辑不变 |
| **安全红线** | 日志禁泄 token/密码；SQL 参数化绑定不变 |
| **单次迁移** | Flyway 脚本不可逆，需要在迁移前备份 |

---

## 4. 风险与差距 / Risks and gaps

| 风险 | 影响 | 当前差距 | 缓解 |
|------|------|----------|------|
| 包名全量重命名导致编译错误 | 高 | 127 个 Java 文件 + 所有 import | IDE 批量重构 + mvn test 验证 |
| TG token 从 t_bot 迁移到 t_bot_channel | 高 | 需要数据迁移脚本 + 代码双读兼容期 | V14 迁移脚本先复制再标记 deprecated |
| 多平台白名单 user ID 格式差异 | 中 | TG 用 long，飞书用 open_id string，钉钉用 staffId | 统一存 VARCHAR(256) |
| Slack/Discord 新增 API 需要 OAuth 流程 | 中 | 当前无 OAuth 基础设施 | V2 先支持 Bot Token 方式，OAuth 后续 |
| 前端改动量大 | 中 | Dashboard.vue 2000+ 行，TG 字段散落多处 | 分阶段：先改 Bot 流程，再加 Channel 页 |
| 旧 t_user_allowlist 数据迁移 | 低 | 需要映射到 t_channel_allowlist | V15 迁移脚本自动迁移 |

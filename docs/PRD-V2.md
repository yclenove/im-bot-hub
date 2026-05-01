# 产品需求文档 V2（中英）/ Product Requirements Document V2

> **V2 核心变更**：从「Telegram 查询机器人」升级为「通用 IM 查询机器人配置中心」，支持 Telegram / 飞书 / 钉钉 / 企微 / Slack / Discord 等多平台统一接入。  
> **V2 Key change**: Upgraded from "Telegram query bot" to "universal IM query bot configuration center" with multi-platform support.

---

## 1. 产品定位 / Product positioning

**中文**

- `im-bot-hub` 是一个**通用 IM 查询机器人配置中心**：管理员通过管理端配置逻辑机器人、多平台渠道（Channel）、数据源、查询命令、字段映射与白名单，把终端用户在 **Telegram / 飞书 / 钉钉 / 企业微信 / Slack / Discord** 等 IM 平台中的斜杠命令转换为**只读、参数化 SQL** 查询或第三方 **API** 请求，并以适配各渠道的消息格式返回结果。
- **核心理念变更**：Bot 是逻辑实体（名称+开关），Channel 是平台接入点（一个 Bot 可绑定多个 Channel），每个 Channel 持有自己的凭据和平台特定配置。
- 产品重点：**平台无关、配置驱动、低运维门槛、面向非开发管理员可操作**。

**English**

- `im-bot-hub` is a **universal IM query bot configuration center** that lets admins configure logical bots, multi-platform channels, datasources, queries, field mappings, and allowlists—mapping slash commands from **Telegram / Lark / DingTalk / WeWork / Slack / Discord** to **read-only, parameterized SQL** or **API** requests with channel-specific reply formats.
- **Core model change**: Bot = logical entity (name + toggle); Channel = platform ingress (one Bot binds to many Channels); each Channel owns its credentials and platform-specific config.
- Product focus: **platform-agnostic, config-driven, low-ops, operable by non-developer admins**.

---

## 2. 目标用户 / Target users

| 角色 | 中文职责 | English responsibility |
|------|----------|------------------------|
| 管理员 | 配置 Bot、Channel（多平台）、Datasource、Query、字段映射、白名单 | Configure bots, channels (multi-platform), datasources, queries, field mappings, allowlists |
| 业务使用者 | 在 Telegram / 飞书 / 钉钉 / 企微 / Slack / Discord 中发送命令获取结果 | Send commands in IM channels and receive results |
| 运维 / DBA | 维护部署、HTTPS、只读账号、索引、库容量、Channel 健康监控 | Maintain deployment, HTTPS, read-only accounts, indexes, retention, channel health monitoring |

---

## 3. 产品目标 / Product goals

**中文**

1. 让管理员能在**不改代码**的前提下快速配置查询机器人，并通过 Channel 绑定接入**任意支持的 IM 平台**。
2. 让终端用户以**最少步骤**在各自习惯的 IM 中完成查询，收到**可读、脱敏、格式适配**的结果。
3. 保证平台在安全、审计、只读访问、参数绑定上的边界清晰，且**平台无关**。
4. 支持多 Bot、多 Channel、多数据源扩展，维持统一管理体验。
5. 新增平台接入时，仅需实现 Channel Plugin 接口，无需改动核心编排逻辑。

**English**

1. Enable admins to configure query bots **without code changes** and bind them to **any supported IM platform** via Channels.
2. Let end users complete lookups in their preferred IM with minimal steps and receive stable, readable, masked, format-adapted results.
3. Preserve strict security, auditability, read-only access, and parameter-binding guarantees—**platform-agnostic**.
4. Scale to multiple bots, channels, and datasources with a consistent admin experience.
5. Adding a new platform requires only implementing the Channel Plugin interface—no core orchestration changes.

---

## 4. 核心能力 / Core capabilities

### 4.1 V1 已有能力（保留并增强）

1. 机器人与渠道管理 / Bot and channel management
2. 数据源管理：数据库与 API 双模式 / Datasource management: database and API modes
3. 查询定义：参数化 SQL、可视化向导、API 可视化 / Query definitions: SQL, visual wizard, visual API mode
4. 限流、审计、查询日志 / Rate limiting, audit, command logs
5. 可视化向导、JSON 字段点选映射、Benchmark、索引建议 / Visual wizard, JSON field mapping, benchmark, index advice

### 4.2 V2 新增 / 核心变更

| 能力 | 中文说明 | English |
|------|----------|---------|
| **Bot-Channel 分离** | Bot 为纯逻辑实体，Channel 承载平台凭据和配置 | Bot = logical; Channel = platform credentials + config |
| **通用白名单** | 基于 Channel 的白名单，支持各平台用户 ID（不再仅限 telegram_user_id） | Channel-scoped allowlist with platform-native user IDs |
| **统一命令日志** | `t_command_log` 替代 `t_telegram_query_log`，全平台统一 | `t_command_log` replaces TG-specific log; all platforms |
| **Slack 接入** | Slack Bot（Events API + Slash Commands） | Slack Bot integration |
| **Discord 接入** | Discord Bot（Interactions Endpoint + Slash Commands） | Discord Bot integration |
| **Channel 健康检查** | 管理端展示各 Channel 的 Webhook 状态、最近错误 | Channel health dashboard with webhook status |
| **查询模板市场** | 预置更多查询模板，一键导入 | Preset query templates marketplace |
| **命令使用统计** | 按命令/用户/平台维度统计 | Command usage analytics by platform |
| **品牌重塑** | 项目名 `im-bot-hub`，包名 `com.sov.imhub` | Project rename; package `com.sov.imhub` |

---

## 5. 用户体验目标 / UX goals

**中文**

- **Bot 创建流程**：创建 Bot → 绑定一个或多个 Channel → 配置查询。每步清晰、独立。
- **Channel 管理**：独立页面管理各平台渠道，按平台类型动态展示对应凭据表单（TG 要 token，飞书要 app_id/app_secret，钉钉要签名密钥，企微要 CorpID/AES Key）。
- **查询日志**：统一入口，支持按平台、Bot、Channel、命令、时间筛选。
- **白名单**：按 Channel 管理，用户 ID 格式由平台自动提示。
- 管理端优先做到**少输入、少跳转、少记忆**。
- 用户可见错误信息必须短、明确、可操作。

**English**

- **Bot creation flow**: Create Bot → Bind one or more Channels → Configure queries. Each step is clear and independent.
- **Channel management**: Dedicated page for platform channels; credential forms adapt dynamically by platform type.
- **Command logs**: Unified view with filters by platform, bot, channel, command, and time range.
- **Allowlist**: Managed per Channel; user ID format hints adapt to platform.
- Minimize input, navigation, and memorization.
- User-visible errors should be short, clear, and actionable.

---

## 6. 范围 / Scope

### 6.1 当前范围 / In scope

- 多平台 Webhook 接入（Telegram / 飞书 / 钉钉 / 企微 / **Slack / Discord**）
- Bot-Channel 分离数据模型
- 通用白名单与命令日志
- 只读业务库查询 + 第三方 HTTP API 查询
- 管理端配置、测试、日志与审计
- 查询结果字段映射、格式化、脱敏
- API 返回 JSON 的预览、字段发现、点选与拖拽排序
- Channel 健康检查与命令统计
- 查询模板市场（预置模板扩展）
- 品牌重塑（项目名、包名、前端标题）

### 6.2 暂不纳入 / Out of scope for now

- 复杂会话式对话状态机
- 跨库联邦查询
- 全文搜索引擎能力
- 自动执行索引 DDL
- 面向普通终端用户的图形化自助查询页面
- 移动端管理 App
- 多租户 SaaS 隔离

---

## 7. 平台支持矩阵 / Platform support matrix

| 平台 | 认证方式 | Webhook 格式 | 回复格式 | 群聊 | 白名单 | V2 状态 |
|------|----------|-------------|----------|------|--------|---------|
| Telegram | Bot Token (BotFather) | JSON (Update) | HTML / Markdown | ✓ | ✓ | 增强（凭据迁入 Channel） |
| 飞书 Lark | App ID + App Secret | JSON | 纯文本 / 富文本 | ✓ | ✓ | 保持 |
| 钉钉 DingTalk | 签名密钥 | JSON (Outgoing) | Markdown | ✓ | ✓ | 保持 |
| 企业微信 WeWork | CorpID + AES Key | XML (AES) | 文本 / Markdown | ✓ | ✓ | 保持 |
| **Slack** | Bot Token + Signing Secret | JSON (Events API) | Block Kit / Markdown | ✓ | ✓ | **新增** |
| **Discord** | Bot Token + Public Key | JSON (Interactions) | Embed / Markdown | ✓ | ✓ | **新增** |

---

## 8. 成功指标 / Success metrics

**中文**

- 新增一个 Bot + Channel + Query 的配置过程可在 **10 分钟**内完成。
- 从 V1 迁移到 V2 的数据迁移**零数据丢失**。
- 管理员可在同一界面管理 4+ 个 IM 平台的 Channel，无需切换上下文。
- 查询日志可按平台维度筛选，排障效率提升。
- 新增一个 IM 平台接入（如 Slack）仅需实现 Channel Plugin 接口，**不改动编排核心**。
- 所有业务值查询均走参数绑定，不出现明文拼接输入。

**English**

- A new bot + channel + query can be configured within 10 minutes.
- V1 → V2 data migration with zero data loss.
- Admins manage 4+ platform channels in one interface without context switching.
- Command logs filterable by platform for faster troubleshooting.
- Adding a new IM platform requires only implementing the Channel Plugin interface.
- All business values are queried through bound parameters only.

---

## 9. 发布质量门槛 / Release quality gates

1. 文档同步：`README`、`CHANGELOG`、`DESIGN-V2`、`PRD-V2`、需求分析、测试策略已更新。
2. 测试完成：后端 `mvn test` 通过，前端 `npm run build` 通过。
3. 数据迁移：Flyway V13-V15 在空库和已有库上均通过验证。
4. 安全检查：无 token、密码、密钥泄漏到日志与文档。
5. 体验检查：Bot 创建 → Channel 绑定 → 查询配置 → 命令测试完整链路可走通。
6. 提交流程：提交信息使用中文，且聚焦单一主题。

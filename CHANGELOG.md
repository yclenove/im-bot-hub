# 变更日志 / Changelog

本文件记录项目**可见变更**；格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)（中英资源均可）。  
**English:** Notable changes are listed below; format follows Keep a Changelog.

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)（适用时）。  
**English:** [Semantic Versioning](https://semver.org/) where applicable.

## [Unreleased] 未发布

### 变更 Changed

- **配置实体软删除**：`bot`、`datasource`、`query_definition`、`bot_channel` 已切换为逻辑删除方案；删除时默认只隐藏配置并保留可追溯关系，运行时与管理端默认过滤已删除记录，`query_definition` 通过 `delete_token` 释放 `(bot_id, command)` 唯一约束以支持命令复用。 / `bot`, `datasource`, `query_definition`, and `bot_channel` now use soft delete; runtime/admin queries filter deleted rows by default, and `query_definition` uses `delete_token` to release `(bot_id, command)` uniqueness for command reuse.

- **Telegram `setMyCommands`**：在默认范围之外，再对 **`all_group_chats`** 写入相同命令列表，改善群内输入 `/` 时的命令联想（仍受 Telegram 客户端差异影响）。 / Also call `setMyCommands` with `scope=all_group_chats` for group slash hints.

- **文档与管理端文案**：`UserGuide`、工作台折叠引导、`TELEGRAM-傻瓜配置`、`AGENTS` 本地运行说明、`DESIGN` 变更表与当前 **数据库/API 数据源 + SQL/向导/API 查询**、命令菜单及查询日志能力对齐。 / Refreshed UserGuide, Dashboard onboarding, Telegram doc, AGENTS local run notes, and DESIGN log for dual datasources, tri-mode queries, menu behavior, and TG logs.

- **运行链路日志补强**：Webhook → Dispatch → API 执行链路新增结构化日志，并统一串联 `traceId / updateId / queryId / command`，便于线上按单条命令追踪执行路径，同时继续避免泄露 token、鉴权头和值参数明文。 / Added structured tracing logs across Webhook → Dispatch → API execution with unified `traceId / updateId / queryId / command` correlation while keeping secrets out of logs.

- **`docs/TELEGRAM-傻瓜配置.md` 重构**：目录化 12 节（准备→数据源→机器人→查询→菜单/help→白名单→Webhook→验证→群→排障→安全），合并重复步骤，Webhook 与自检收拢为一章。 / Restructured Telegram setup doc into 12 sections with TOC and consolidated Webhook flow.

- **Telegram 命令菜单（`setMyCommands`）**：API 查询菜单副标题改为 **`apiConfigJson.name`**（无则退回命令名），不再用数据源名以免同源多条命令重复；向导优先 **表名**，SQL 为 **`命令 · 数据源`**；描述中带 **`示例: /cmd …`**（`param_schema_json.examples` + 启发值）。保存查询后同步菜单。 / Telegram bot menu descriptions: per-query titles from API config `name` (fallback command), visual table name, SQL `command · datasource`; example suffix; `setMyCommands` on query save.

- **缺参体验**：从菜单只发 **`/命令` 无参数** 时回复**用法说明**（各 IM `sendParamUsageReminder`）；仍保留对已填部分参数时的缺参提示。 / Usage reminder when command sent with no args; strict missing-param when partially filled.

- **API 查询校验**：编排层对 API 模式缺参校验与 SQL 默认 **`orderNo`** 解耦，避免空 `params` 时误报 `orderNo`。 / API dispatch no longer applies SQL default `orderNo` for empty param schema.

### 新增 Added

- **API 数据源与 API 查询**：支持数据库 / API 双数据源模式；新增 API 基础地址、超时、默认 Header / Query 参数、连通性测试；查询定义新增 API 可视化配置、JSON 预览、字段点选与拖拽排序；内置天气与虚拟币价格预制模板。 / Added API datasources and visual API queries with auth, connectivity test, JSON preview, click-and-drag field mapping, and built-in weather/crypto presets.

- **正式文档体系补全**：新增 [`docs/PRD.md`](docs/PRD.md)、[`docs/REQUIREMENTS-ANALYSIS.md`](docs/REQUIREMENTS-ANALYSIS.md)、[`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md)，将产品目标、需求拆解、风险分析、测试分层与交付门槛纳入版本化文档。 / Added formal PRD, requirements analysis, and test strategy documents.

- **交付自动化脚本**：新增 `scripts/dev/check-docs.ps1`、`scripts/dev/run-quality-gates.ps1`、`scripts/dev/deliver.ps1`，用于文档一致性检查、质量门禁与本地交付流程。 / Added delivery automation scripts for docs checks, quality gates, and local delivery.

- **迭代计划文档**：新增 [`docs/ITERATION-PLAN.md`](docs/ITERATION-PLAN.md)，明确近期阶段目标、优先级和交付要求。 / Added an iteration plan document.

### 变更 Changed

- **OKX P2P 模板语义重整**：`okx_p2p_buy`、`okx_p2p_sell`、`okx_p2p_cards` 不再把用户参数直接透传为上游 `limit` 语义，而是改为“本地按条数截取”模式；同时新增 `okx_p2p_buy_best` / `okx_p2p_sell_best` 单条最佳报价模板，避免用户把“金额”和“条数”混淆。 / OKX P2P presets now treat user input as local result count instead of blindly forwarding upstream `limit`, and add dedicated single-best-quote presets.

- **API 查询配置继续降噪**：`ApiQueryBuilder` 将结果位置、请求参数、请求头、请求体模板折叠到 **「高级请求选项（默认可不填）」**，并将样例 JSON、超时、最大条数、启用状态折叠到 **「结果预览与运行细项」**，让主流程更聚焦在“选模板 -> 填路径 -> 预览 -> 点选字段 -> 保存”。 / `ApiQueryBuilder` now folds advanced request controls and result/runtime details into optional sections so the main flow stays focused on preset -> path -> preview -> field selection -> save.

- **管理端结构收口**：将 `Dashboard.vue` 内联的 API 数据源表单抽为独立组件 `ApiDatasourceFormSection.vue`，保留原有保存/测试契约不变，降低页面耦合并便于后续继续拆分。 / Extracted the inline API datasource form from `Dashboard.vue` into `ApiDatasourceFormSection.vue` without changing save/test contracts, reducing coupling for future refactors.

- **管理端数据源 / 查询定义体验升级**：`Dashboard` 现按数据源类型自动约束可用配置模式；API 数据源显示连接信息、鉴权方式与超时；查询定义支持 SQL、向导、API 可视化三种模式并自动联动。 / Dashboard now adapts datasource and query flows by datasource type, with unified SQL, visual, and API modes.

- **设计文档增强**：[`docs/DESIGN.md`](docs/DESIGN.md) 补充用例图、泳道图、类图，以及文档与交付约束说明；[`docs/WORKFLOW.md`](docs/WORKFLOW.md)、[`README.md`](README.md)、[`AGENTS.md`](AGENTS.md) 同步更新文档索引与交付纪律。 / Expanded design docs with diagrams and delivery constraints; updated workflow/readme/agents indexes.

- **设计图示与 CI 增强**：[`docs/DESIGN.md`](docs/DESIGN.md) 继续补充时序图、ER 图、部署图；GitHub CI 新增正式文档一致性检查。 / Added sequence/ER/deployment diagrams and CI doc-consistency checks.

- **查询定义编辑 UI**：由居中弹窗改为**右侧全高抽屉**（更宽、可滚动）；向导内**固定条件**表增加 **等于 / 不等于**，表头与单元格留白加大；**调优与索引**改为常驻卡片（含建议摘要与 DDL 区块）；**管理端 SQL 测试**迁入抽屉（向导与高级 SQL 均可用，与列表「测试」同接口）。 / Drawer layout, fixed predicate NE, prominent index card, inline SQL test.

- **向导 `UNION_ALL` 策略**：生成的 SQL 由 **`UNION ALL`** 改为 **`UNION`**（默认去重），减少与多列 OR 不一致的重复行；配置 JSON 仍为 `orCompositionStrategy: UNION_ALL` 以保持兼容。 / Visual union strategy now emits SQL `UNION` (distinct); JSON enum name unchanged.

- **SpringDoc OpenAPI**：移除自定义 `springdoc.api-docs.path: /api-docs`，统一为默认 **`/v3/api-docs`**，与 `SecurityConfig`、Vite `/v3` 代理及 Nginx 示例一致。 / Removed custom OpenAPI path; default `/v3/api-docs` aligned with security, Vite, nginx.

- **默认 HTTP 端口**：由 `8080` 调整为 **`18089`**（`application.yml` / `server.port`），减少与本机其它服务冲突；Vite 开发代理与部署示例已同步。如需仍为 8080，可在配置或环境变量中覆盖。

### 新增 Added

- **多 IM 收尾**：企业微信自建应用 **接收消息**（`GET/POST /api/webhook/wework/{channelId}`，AES 被动回复，`weixin-java-common`）；管理端 **WEWORK** 渠道（CorpID、AgentId、Token、EncodingAESKey）；企微限流键；`ImCommandText` 统一斜杠截取（钉钉复用）。文档 [`docs/DEPLOY-宝塔.md`](docs/DEPLOY-宝塔.md)、[`docs/DESIGN.md`](docs/DESIGN.md)、[`AGENTS.md`](AGENTS.md) 已同步。

- **向导固定条件**：`fixedPredicates` 支持可选 **`operator: NE`**（`INT`/`BOOL` 字面量），生成 `<> ` 比较。 / Fixed predicates not-equal operator.

- **可视化向导增强**：`visual_config_json` 支持 **`orCompositionStrategy`**（`LEGACY_OR` / `UNION_ALL`）与可选 **`tableRowsEstimate`**；服务端按策略生成 SQL（`UNION_ALL` 至少两列 OR 时多支合并）。**`GET /api/admin/datasources/{id}/metadata/tables/{table}/stats`**（`TABLE_ROWS` + 可选 **`exactCount`**）；**`POST .../visual-query/benchmark`**（OR vs UNION 交替各跑 2 次取平均，行数/超时硬上限）；**`POST .../visual-query/index-advice`**（启发式 **`CREATE INDEX`** 文本，**不执行 DDL**）。`SqlTemplateValidator` 允许 **`UNION ALL`**、拒绝无 `ALL` 的 **`UNION`**。管理端向导：表规模展示、精确计数、OR/UNION 单选、耗时对比弹窗、索引建议折叠区。 / Visual OR/UNION strategy, table stats, benchmark, index DDL hints, validator + Dashboard.

- **Telegram 查询日志**：Flyway **`V7`** 表 `t_telegram_query_log`；斜杠命令处理结果写入配置库（不含业务参数明文）；**`GET /api/admin/telegram-query-logs`** 分页筛选（**`from`/`to`**、**`errorKind`**、**`success`**、**`telegramUserId`**、**`chatId`**）；管理端 Tab 对应筛选、非法 ID 提示、**导出当前页 CSV**；运维清理示例 **`scripts/mysql/03-purge-telegram-query-log.sql`**。 / TG log filters, ID validation, CSV export, purge SQL.

- **可视化向导**：`visual_config_json` 支持 **`fixedPredicates`**（仅 **INT/BOOL** 字面量 AND 条件）；管理端向导 **固定条件** 表与 **穿梭框** 选结果列、**折叠** 编辑展示名/枚举。 / Visual fixed INT/BOOL predicates; transfer + collapse UI.

- **Telegram**：内置 **`/help`**、**`/start`**（无同名已启用查询定义时）列出当前机器人可用命令与参数占位；查询 **`max_rows` > 1** 时在一条消息内 **逐条渲染** 多行结果（总长仍受发送端截断限制）。 / Built-in `/help` `/start`; multi-row query results rendered per row.

- **性能**：`TelegramApiClient` 复用单例 **`RestClient`**；**`BusinessDataSourceRegistry`** 按数据源缓存 **`NamedParameterJdbcTemplate`**，在 `reloadAll` / `reloadOne` 时失效。 / Singleton RestClient; cached NamedParameterJdbcTemplate per datasource.

- **运维文档**：[`docs/DEPLOY-宝塔.md`](docs/DEPLOY-宝塔.md) 增补 OpenAPI 自检、**Webhook 密钥生产检查清单**；[`deploy/README-DEPLOY.md`](deploy/README-DEPLOY.md) 标明 **`/v3/api-docs`**。 / Deploy docs: OpenAPI check + webhook secret checklist.

- **Webhook 密钥**：`t_bot.webhook_secret_token`（Flyway V2），校验 `X-Telegram-Bot-Api-Secret-Token`；管理端可配置。 / Per-bot Telegram webhook secret header validation + admin field.

- **审计分页**：`GET /api/admin/audit-logs?page=&size=`，MyBatis-Plus 分页插件；`t_audit_log(created_at)` 索引（V2）；管理端分页器。 / Paginated audit logs + index; admin pagination.

- **限流缓存**：Webhook 限流桶使用 **Caffeine** 淘汰（与计划 A3 一致）。 / Caffeine-backed bucket cache for webhook rate limiter.

- **白名单 SQL**：单条 `CASE` 查询替代两次 `count`（计划 A4）。 / Single SQL for allowlist check.

- **体验**：Telegram 用户可见提示中文化；`app.cors.allowed-origins`（含 5174）；前端 axios 错误拦截。 / Chinese user copy; CORS config; axios errors.

- **工程化**：`.github/workflows/ci.yml`（`mvn test` + `admin-ui` build）；Testcontainers 集成测试 `TelegramQueryBotApplicationIT`（需 Docker）。 / GitHub CI; optional Docker IT.

- **数据源**：`PUT /api/admin/datasources/{id}`；管理端编辑。 / Datasource update API + UI.

- **文档**：[`deploy/README-HORIZONTAL.md`](deploy/README-HORIZONTAL.md) 多实例与 Redis 说明。 / Horizontal scaling notes.

- 管理端补齐 **机器人** 编辑/删除、**数据源** 删除、**查询定义** 编辑/删除与 **字段映射** 完整 CRUD；`EncryptionService` 单元测试。 / Admin UI: bot edit/delete, datasource delete, query edit/delete, field-mapping CRUD; `EncryptionService` tests.

- `scripts/mysql/02-create-local-db-and-grant-mcp.sql`：管理员建 **`local`** 库并授权 MCP 账号，便于 **`use_database("local")`** 建表测试。 / Admin script for MCP `local` schema + grants.
- 默认 Spring **`local`** profile + `application-local.yml`：本机 **`127.0.0.1:3306`** 连接 `tg_query_meta`。 / Default **`local`** profile with local MySQL DSN.
- `docs/MCP-MYSQL-测试说明.md`：与 **本机 local** 对齐、MCP 连库说明、Flyway 步骤。 / MCP aligned with local profile and Flyway steps.
- `scripts/mysql/01-create-database-and-grant.sql`：管理员建库与授权模板。 / Admin SQL template for DB creation and grants.
- 工程流程与规范文档：`docs/WORKFLOW.md`、`docs/CODING-STANDARD.md`、`docs/DESIGN.md`。 / Engineering workflow docs: `docs/WORKFLOW.md`, `docs/CODING-STANDARD.md`, `docs/DESIGN.md`.
- `.gitignore` 排除 `.cursor/rules/`（规则仅本地）。 / `.gitignore` excludes `.cursor/rules/` (local-only rules).
- 后端初始单测：`SqlTemplateValidator`、`TelegramCommandParser`。 / Initial unit tests for `SqlTemplateValidator` and `TelegramCommandParser`.
- 根 `README.md`、`CHANGELOG` 与 `docs` 统一为**中文或中英双语**表述。 / Root `README`, `CHANGELOG`, and `docs` unified to Chinese or bilingual text.

### 变更 Changed

- 按团队要求扩展流程：Changelog、测试、设计文档、Code Review 等。 / Expanded process requirements (changelog, tests, design doc, code review).
- `README`、`AGENTS`、`docs/*`、`deploy/README-REPLICA-OPS` 全文改为中文或中英对照。 / Rewrote root and `docs/` to Chinese or bilingual content.

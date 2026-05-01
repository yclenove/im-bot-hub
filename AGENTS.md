# AGENTS.md — im-bot-hub

> **文档语言策略 / Doc language:** 仓库内**所有用户可读文档**采用 **中文** 或 **中英对照**（同一节内先中文后 English，或表格双列）。代码标识符、Maven 坐标、URL 保持英文原文。

---

## 1. 项目概要 / Overview

**中文**

- **后端：** Java 17 + Spring Boot 3 + Maven；根包 `com.sov.telegram.bot`；代码目录 [`backend/`](backend/)。  
- **管理端：** Vue 3 + Vite + Element Plus；[`admin-ui/`](admin-ui/)。  
- **配置库：** MySQL；Flyway 脚本目录见 `spring.flyway.locations`（默认 **`classpath:db/migration`**，对应源码路径 [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration)）；本地默认 **`local` profile**，连接见 [`application-local.yml`](backend/src/main/resources/application-local.yml)（本机 `127.0.0.1:3306` / `tg_query_meta`）。  
- **业务库：** 管理端配置为 **Datasource**；SQL 模板使用 `#{param}` 绑定，运行时转命名参数 JDBC，**禁止**拼接用户输入。

**English**

- Backend: Java 17, Spring Boot 3, Maven; package `com.sov.telegram.bot`; [`backend/`](backend/).  
- Admin UI: Vue 3 + Vite + Element Plus; [`admin-ui/`](admin-ui/).  
- Config DB: MySQL + Flyway; JDBC in [`application.yml`](backend/src/main/resources/application.yml).  
- Business DB: **Datasource** rows; templates use `#{param}` binding only.

---

## 2. 对外契约 / External contracts

| 项 | 中文 | English |
|----|------|---------|
| Webhook | Telegram：`POST /api/webhook/{botId}`（`botId`=`t_bot.id`）。飞书 / 钉钉 / 企业微信：`POST /api/webhook/lark|dingtalk|wework/{channelId}`（`channelId`=`t_bot_channel.id`）；企微含 **GET** URL 验证。均需公网 HTTPS | TG `setWebhook`; IM channels use `t_bot_channel` + public HTTPS |
| 管理 API | `/api/admin/**`，HTTP Basic，`app.security.admin.*`；含 **`GET /api/admin/telegram-query-logs`**（分页；可选 `botId`、`command`、`from`、`to`、`errorKind`、`success`、`telegramUserId`、`chatId`） | `/api/admin/**`, Basic; TG logs API with optional filters |
| OpenAPI | `/v3/api-docs`、`/swagger-ui/**`（Dev 可用 Vite 代理） | `/v3/api-docs`, `/swagger-ui/**` |

---

## 3. 版本化文档索引 / Versioned docs

| 文档 | 用途（中文） | Purpose (EN) |
|------|----------------|---------------|
| [`docs/PRD.md`](docs/PRD.md) | 产品定位、范围、目标用户、成功指标 | Product positioning, scope, target users, success metrics |
| [`docs/PRD-V2.md`](docs/PRD-V2.md) | V2 产品需求：通用 IM 配置中心、Bot-Channel 分离、多平台 | V2 PRD: universal IM config center, Bot-Channel separation |
| [`docs/REQUIREMENTS-ANALYSIS.md`](docs/REQUIREMENTS-ANALYSIS.md) | 业务问题、需求分解、约束、风险与当前差距 | Business problem, requirement breakdown, constraints, risks, current gaps |
| [`docs/REQUIREMENTS-ANALYSIS-V2.md`](docs/REQUIREMENTS-ANALYSIS-V2.md) | V2 需求：品牌重塑、数据模型重构、新平台接入 | V2 requirements: rebrand, data model refactor, new platforms |
| [`docs/DESIGN.md`](docs/DESIGN.md) | 架构、数据流、安全、可扩展、设计变更表 | Architecture, flow, security, extensibility, design log |
| [`docs/DESIGN-V2.md`](docs/DESIGN-V2.md) | V2 设计：Bot-Channel 分离、通用白名单/日志、包重命名 | V2 design: Bot-Channel separation, generic allowlist/logging |
| [`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md) | 测试分层、执行门槛、冒烟清单 | Test layers, execution gates, smoke checklist |
| [`docs/TEST-STRATEGY-V2.md`](docs/TEST-STRATEGY-V2.md) | V2 测试策略：迁移验证、多平台冒烟 | V2 test strategy: migration verification, multi-platform smoke |
| [`docs/MIGRATION-GUIDE.md`](docs/MIGRATION-GUIDE.md) | V1→V2 迁移指南：数据库、API、前端、配置 | V1→V2 migration guide: DB, API, frontend, config |
| [`docs/ITERATION-PLAN.md`](docs/ITERATION-PLAN.md) | 近期阶段目标、优先级与交付要求 | Near-term phases, priorities, and delivery requirements |
| [`docs/CODING-STANDARD.md`](docs/CODING-STANDARD.md) | 中文注释、Java/Vue、SOLID、可读/可扩 | Comments, Java/Vue, SOLID, readability |
| [`docs/WORKFLOW.md`](docs/WORKFLOW.md) | Changelog 纪律、测试、Code Review、Git | Changelog, tests, review, Git |
| [`CHANGELOG.md`](CHANGELOG.md) | 按 Keep a Changelog 维护 | Keep a Changelog |
| [`docs/MCP-MYSQL-测试说明.md`](docs/MCP-MYSQL-测试说明.md) | MCP MySQL 建库权限与 Flyway 验证步骤 | MCP MySQL privileges + Flyway smoke test |
| [`README.md`](README.md) | 项目入口、快速开始、安全与运维链接 | Entry point, quick start, links |

---

## 4. 本地 Cursor Rules（不提交 Git）/ Local rules (not in Git)

**中文：** [`.cursor/rules/`](.cursor/rules/) 已列入 `.gitignore`；可本地维护 `.mdc`，但与团队对齐时以 **`docs/*.md`** 与本文为准。

**English:** [`.cursor/rules/`](.cursor/rules/) is gitignored; align with **`docs/*.md`** and this file.

---

## 5. 文档与交付纪律 / Documentation and delivery discipline

**中文：** 中大型功能默认需要 **PRD/需求分析/设计文档/测试说明** 中的相应文档；实现完成后同步更新 `README.md`、`CHANGELOG.md` 与必要设计文档。提交信息使用**中文**。

**English:** Medium and large features should maintain the appropriate **PRD / requirements / design / test** docs. Keep `README.md`, `CHANGELOG.md`, and design docs in sync after implementation. Commit messages should be **Chinese**.

---

## 6. 禁止事项 / Do not

**中文：** 不得在日志与审计中输出 Bot Token、数据库密码、密钥；业务连接须**只读**；不得改写已发布环境中已执行的 Flyway 旧脚本内容（应新增版本脚本）。

**English:** No tokens/passwords in logs/audit; read-only business DB users; never rewrite applied Flyway migrations—add a new versioned script.

---

## 7. 本地运行 / Local run

**中文：** 配置 MySQL 与 `application.yml` / `application-local.yml` → 后端：`cd backend && .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`（Windows）或 `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`（Unix）；默认端口 **`18089`**。管理端：`cd admin-ui && npm install && npm run dev`（`http://localhost:5173`）。HTTP Basic 与 `app.security.admin` 一致。

**English:** Configure MySQL + YAML; run backend via **`mvnw`** with **`local`** profile (port **`18089`**); admin UI `npm run dev`; Basic creds match `app.security.admin`.

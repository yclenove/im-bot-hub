# telegram-query-bot

## 简介（中文）

可配置 Telegram 机器人：将命令（例如 `/cx`）映射到针对 **只读** MySQL 数据源的 **参数化 SQL**，或映射到第三方 **API** 接口，并通过 **REST + 管理端 Web** 维护机器人、数据源、查询配置与返回字段映射。

**English:** A configurable Telegram bot gateway that maps commands (e.g. `/cx`) either to **parameterized** SQL on **read-only** MySQL sources or to external **API** calls, with bots, datasources, queries, and field mappings managed via **REST + admin UI**.

---

## 技术栈

| 中文 | English |
|------|---------|
| Java 17、Spring Boot 3、Maven、MyBatis-Plus、Flyway | Java 17, Spring Boot 3, Maven, MyBatis-Plus, Flyway |
| 管理 API：`/api/admin/**`（HTTP Basic）、OpenAPI（Swagger UI） | Admin API: `/api/admin/**` (HTTP Basic), OpenAPI (Swagger UI) |
| 管理前端：Vue 3 + Vite + Element Plus（`admin-ui/`） | Admin UI: Vue 3 + Vite + Element Plus (`admin-ui/`) |

**文档 / Docs：** [`docs/PRD.md`](docs/PRD.md)、[`docs/REQUIREMENTS-ANALYSIS.md`](docs/REQUIREMENTS-ANALYSIS.md)、[`docs/DESIGN.md`](docs/DESIGN.md)、[`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md)、[`docs/ITERATION-PLAN.md`](docs/ITERATION-PLAN.md)、[**Telegram 配置说明（Token / Webhook / 命令菜单）**](docs/TELEGRAM-傻瓜配置.md)、[**服务器傻瓜部署（Linux）**](docs/DEPLOY-傻瓜部署.md)、[`docs/CODING-STANDARD.md`](docs/CODING-STANDARD.md)、[`docs/WORKFLOW.md`](docs/WORKFLOW.md)、[`CHANGELOG.md`](CHANGELOG.md)。生产部署细则另见 [`deploy/README-DEPLOY.md`](deploy/README-DEPLOY.md)。本地 Cursor 规则目录 `.cursor/rules/` **不纳入 Git**；团队规范以 `docs/` 为准。  
**Agent 摘要：** [`AGENTS.md`](AGENTS.md)。

---

## 快速开始

### 中文

1. 在本机安装并启动 **MySQL**（默认 `127.0.0.1:3306`），创建库 **`tg_query_meta`**（可用 [`scripts/mysql/01-create-database-and-grant.sql`](scripts/mysql/01-create-database-and-grant.sql)）。账号密码默认与 [`application-local.yml`](backend/src/main/resources/application-local.yml) 中一致（当前示例为 `root` / `change-me-local`，请按本机修改）。
2. 启动后端（默认激活 **`local`** profile，连接本机库）：`cd backend && mvn spring-boot:run`
3. 启动管理端（开发时代理到后端）：`cd admin-ui && npm install && npm run dev`  
4. 浏览器访问 `http://localhost:5173/login`，管理端账号与 `application.yml` 中 `app.security.admin.*` 一致（默认 `admin` / `change-me`）。  
5. 在管理端创建 **数据源**：**数据库**（副本 JDBC + 只读用户）或 **API**（Base URL、鉴权、连通性测试）；再创建 **机器人**（Telegram Token）和 **查询**（**向导 / 高级 SQL / API 可视化**，随数据源类型切换）。保存查询后会尽量刷新 Telegram **斜杠命令菜单**（`setMyCommands`）；详见 [`docs/TELEGRAM-傻瓜配置.md`](docs/TELEGRAM-傻瓜配置.md) **§6 命令、菜单与 /help**。  
6. 在 Telegram 配置 `setWebhook` 为 `https://<公网域名>/api/webhook/<botId>`（`botId` 见管理端 Bots 表主键）。

#### Telegram 机器人注册与本系统配置（中文）

1. **在 Telegram 创建机器人**  
   打开 [@BotFather](https://t.me/BotFather)，发送 `/newbot`，按提示起名、命名，完成后复制 **HTTP API Token**（形如 `数字:字母`）。无需在 BotFather 里配置 Webhook 也可以稍后用 API 设置。

2. **在本系统登记 Bot**  
   管理端登录后：**机器人** → **新建机器人**，填写名称、粘贴 **Token**；保存后记下表格中的 **ID**（即 `botId`）。可选：若使用 Webhook Secret，在「Webhook 密钥」填与 Telegram 一致的值。

3. **数据源与查询**  
   - **数据源**：可以添加你的业务库 JDBC（建议只读账号），也可以添加第三方 **API 数据源**。API 模式支持 `NONE`、`BEARER_TOKEN`、`API_KEY_HEADER`、`API_KEY_QUERY`、`BASIC` 等常见鉴权方式，并支持默认 Header、默认 Query 参数、连通性测试。  
   - **查询定义**：选中该机器人后，可配置三种模式：**SQL 模板**、**可视化向导**、**API 可视化**。API 模式可先选天气、币价等预制模板，再预览 JSON 返回，点选 / 拖拽字段决定机器人最终返回内容。  
   - 可用行内 **「测试」** 在后台先跑通 SQL 或 API（不经过 Telegram）。

4. **白名单（可选）**  
   **白名单**为空：所有 Telegram 用户可使用该机器人的已配置命令。若添加了 Telegram **用户 ID**，则仅这些用户可用。

5. **Webhook（在 Telegram 里真正收消息）**  
   - URL 必须是 **HTTPS** 公网地址，形如：  
     `https://你的域名/api/webhook/<botId>`  
   - **本机调试**：用 [ngrok](https://ngrok.com/) / [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/) 等把本机后端（默认端口 **18089**）暴露为 `https://xxxx...`，再把该 URL + `botId` 配到 Telegram。可用浏览器或 curl 调用 Telegram `setWebhook`。  
   - 若配置了 Webhook 密钥，请保证 Telegram 发过来的请求头 `X-Telegram-Bot-Api-Secret-Token` 与本系统里配置一致。

6. **在 Telegram 里测试**  
   打开与你的 Bot 的聊天窗口，发送 `/cx 你的参数`（命令与参数个数需与查询定义一致），应返回查询结果 HTML。

### English

1. Run MySQL locally (`127.0.0.1:3306`), create DB **`tg_query_meta`**, set credentials to match [`application-local.yml`](backend/src/main/resources/application-local.yml) (current sample: `root` / `change-me-local`).
2. Backend ( **`local`** profile by default): `cd backend && mvn spring-boot:run`
3. Admin UI: `cd admin-ui && npm install && npm run dev`  
4. Open `http://localhost:5173` — Basic auth matches `app.security.admin.*`.  
5. Create **Datasource**, **Bot**, **Query** as above.  
6. `setWebhook` to `https://<host>/api/webhook/<botId>`.

---

## 本地启动与生产打包 / Local dev and production artifacts

### 中文

| 步骤 | 命令 / 产物 |
|------|-------------|
| 后端（本机） | `cd backend`，Windows 推荐 **`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`**（与 CI 一致，不依赖全局 `mvn`）；默认 HTTP 端口 **`18089`**（见 `application.yml`）。 |
| 管理端（开发） | `cd admin-ui && npm install && npm run dev` → 浏览器 **`http://localhost:5173/login`**；Vite 将 `/api` 代理到上述后端。 |
| 管理端（生产静态资源） | `cd admin-ui && npm run build` → 产物目录 **`admin-ui/dist/`**，由 Nginx 等托管并反代 `/api` 到后端。 |
| 后端（生产 JAR） | `cd backend && .\mvnw.cmd clean package`（发布前建议去掉 `clean` 或保留均可；需全量测试时不要加 `-DskipTests`）→ **`backend/target/telegram-query-bot-<version>.jar`**（版本以 `pom.xml` 为准；CI 部署阶段会统一重命名为 `telegram-query-bot.jar`）。 |

可选：若本机访问 Telegram API 需代理，可在启动前设置 `JAVA_TOOL_OPTIONS`（如 SOCKS）**仅用于** Telegram 相关客户端时，请仍遵循仓库内 `AppConfig` 对「Telegram 代理 vs 普通 HTTP 出站」的拆分，避免把业务 JDBC 或第三方 API 误走代理。

### English

| Step | Command / output |
|------|------------------|
| Backend (local) | `cd backend` then **`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`** (Unix) or **`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`** (Windows); default port **`18089`**. |
| Admin UI (dev) | `cd admin-ui && npm install && npm run dev` → **`http://localhost:5173`**. |
| Admin UI (prod static) | `cd admin-ui && npm run build` → **`admin-ui/dist/`**. |
| Backend (prod JAR) | `cd backend && ./mvnw clean package` → **`backend/target/telegram-query-bot-<version>.jar`** (renamed to `telegram-query-bot.jar` in CI deploy stage). |

---

## 安全（中文 / English）

- **中文：**修改默认管理端密码；数据源密码可使用 `app.encryption.secret-key-base64`（32 字节 AES 密钥 Base64）加密存储。不得在日志中输出 Bot Token 或数据库密码。  
- **English:** Change default admin password; use `app.encryption.secret-key-base64` for encrypted datasource secrets. Never log bot tokens or DB passwords.

---

## 运维

- **中文：**服务器部署（MySQL + JAR + Nginx HTTPS、systemd、Webhook 域名）见 [`deploy/README-DEPLOY.md`](deploy/README-DEPLOY.md)。  
- **中文：**只读副本、索引与性能提示见 [`deploy/README-REPLICA-OPS.md`](deploy/README-REPLICA-OPS.md)。多实例限流说明见 [`deploy/README-HORIZONTAL.md`](deploy/README-HORIZONTAL.md)。  
- **English:** Server deployment: [`deploy/README-DEPLOY.md`](deploy/README-DEPLOY.md). Replica/ops: [`deploy/README-REPLICA-OPS.md`](deploy/README-REPLICA-OPS.md).

---

## 文档体系 / Documentation set

| 文档 | 中文用途 | English purpose |
|------|----------|-----------------|
| [`docs/PRD.md`](docs/PRD.md) | 产品定位、目标用户、范围、成功指标 | Product positioning, users, scope, success metrics |
| [`docs/REQUIREMENTS-ANALYSIS.md`](docs/REQUIREMENTS-ANALYSIS.md) | 业务问题、需求拆解、约束、风险与差距 | Business problem, requirement breakdown, constraints, risks, gaps |
| [`docs/DESIGN.md`](docs/DESIGN.md) | 架构、数据流、安全、图示、设计变更记录 | Architecture, flow, security, diagrams, design log |
| [`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md) | 测试分层、执行门槛、冒烟清单 | Test layers, execution gates, smoke checklist |
| [`docs/API-TEST-CASES.md`](docs/API-TEST-CASES.md) | API 功能完整用例矩阵（数据源/查询/映射）与 MCP 自动化步骤 | Full API test matrix (datasource/query/mapping) and MCP automation steps |
| [`docs/ITERATION-PLAN.md`](docs/ITERATION-PLAN.md) | 近期阶段目标、优先级与交付要求 | Near-term phases, priorities, and delivery requirements |
| [`docs/WORKFLOW.md`](docs/WORKFLOW.md) | 提交流程、文档纪律、交付顺序 | Delivery workflow and documentation discipline |

**中文：** 中大型功能建议按“需求分析 / PRD -> 设计文档 -> 实现与测试 -> 更新 README/CHANGELOG”推进。  
**English:** For medium and large features, prefer the flow: requirements/PRD -> design -> implementation and testing -> README/CHANGELOG updates.

---

## 产品能力概览 / Product capability overview

### 中文

- **双数据源模式**：同一套机器人平台同时支持数据库数据源与 API 数据源。
- **API 傻瓜化配置**：提供天气、虚拟币价格等预制模板，管理员只需选模板、填少量参数、点一次测试即可起步。
- **多鉴权方式**：支持无鉴权、Bearer Token、Basic、Header API Key、Query API Key。
- **可视化 JSON 映射**：API 查询可预览返回样例，自动识别字段，管理员通过点选与拖拽决定机器人返回顺序与字段标签。
- **统一字段渲染**：无论来源是数据库还是 API，最终都走统一的字段映射、格式化、脱敏和 Telegram 展现风格。
- **主流程降噪**：API 查询管理界面默认把高级请求项与结果细项折叠起来，优先突出“选模板、填路径、预览、点选字段、保存”的最短路径。
- **线上排查日志**：Webhook、命令编排、API 执行链路已补充结构化日志，默认可按 `traceId / updateId / queryId / command` 串联排查。

### English

- **Dual datasource model**: the same bot platform supports both database datasources and API datasources.
- **Preset-first API onboarding**: built-in templates such as weather and crypto price reduce setup steps.
- **Multiple auth methods**: none, bearer token, basic, API key in header, API key in query.
- **Visual JSON mapping**: preview API responses, auto-discover fields, and choose/reorder what the bot should reply with.
- **Unified rendering**: both SQL and API results flow through the same field mapping, formatting, masking, and Telegram reply styles.
- **Reduced-noise main flow**: advanced request options and result/runtime details are folded by default so the shortest path stays focused on preset, path, preview, field selection, and save.
- **Operational tracing logs**: structured logs now correlate Webhook, dispatch, and API execution with `traceId / updateId / queryId / command` for production troubleshooting.

---

## 自动化脚本 / Automation scripts

| 脚本 | 用途 |
|------|------|
| `scripts/dev/check-docs.ps1` | 检查正式文档是否齐备，README / AGENTS / WORKFLOW 是否包含关键索引与交付约束 |
| `scripts/dev/run-quality-gates.ps1` | 统一执行文档检查、后端测试、前端构建 |
| `scripts/dev/deliver.ps1` | 按“测试 -> git add -> commit -> push”顺序执行交付流程 |

**中文示例：** `powershell -ExecutionPolicy Bypass -File scripts/dev/run-quality-gates.ps1 -SkipBackend`  
**English example:** `powershell -ExecutionPolicy Bypass -File scripts/dev/deliver.ps1 -Message "docs: 更新测试说明" -SkipBackend -SkipFrontend`

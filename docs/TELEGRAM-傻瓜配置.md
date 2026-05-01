# Telegram 机器人配置说明（本系统）

面向「从零到能在 Telegram 里发出第一条查询」的**操作顺序**。涉及 **Bot Token**（等同密码）勿外泄、勿提交 Git；**泄露后请到 @BotFather 撤销并换新**。

**相关文档：** 服务器与 Nginx 部署见 [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md)；宝塔面板见 [`DEPLOY-宝塔.md`](./DEPLOY-宝塔.md)。

---

## 目录

1. [开始之前](#1-开始之前)
2. [登录管理端](#2-登录管理端)
3. [数据源（数据库或 API）](#3-数据源数据库或-api)
4. [机器人（Token 与 botId）](#4-机器人token-与-botid)
5. [查询定义（向导 / SQL / API）](#5-查询定义向导--sql--api)
6. [命令、菜单与 /help](#6-命令菜单与-help)
7. [白名单（可选）](#7-白名单可选)
8. [Webhook（HTTPS 与注册）](#8-webhookhttps-与注册)
9. [在 Telegram 里验证](#9-在-telegram-里验证)
10. [接收范围与群内用法](#10-接收范围与群内用法)
11. [常见问题](#11-常见问题)
12. [安全提示](#12-安全提示)

---

## 1. 开始之前

| 你需要 | 说明 |
|--------|------|
| 本系统已运行 | 后端连上 MySQL 元库（默认 `tg_query_meta`），管理端能打开 |
| Bot Token | Telegram **@BotFather** → `/newbot` 创建机器人后得到 `数字:字母` 整串 |
| 查什么数据 | **数据库**：只读 MySQL 业务库，在管理端建「数据库」数据源。**第三方接口**：在管理端建「API」数据源（Base URL、鉴权等） |

后端默认 HTTP 端口见 `application.yml` 的 `server.port`（当前一般为 **18089**）。本地开发管理端：`http://localhost:5173/login`（`cd admin-ui && npm run dev`）。

---

## 2. 登录管理端

1. 浏览器打开管理端登录页（开发时多为 `http://localhost:5173/login`）。  
2. 账号密码与 `application.yml` 中 **`app.security.admin`** 一致（生产务必改掉默认密码）。

---

## 3. 数据源（数据库或 API）

在 **「数据源」** 标签 → **新建数据源**。两类互斥，后续查询只能选对应类型。

### 3.1 数据库数据源

1. **名称**：便于自己辨认。  
2. **连接方式**：默认 **「简易」** — 填地址、端口、库名；可切换 **「自定义 JDBC」** 写整串 URL。  
3. **用户 / 密码**：建议业务库 **只读** 账号。  
4. **测试连接** → 保存，记下表格中的 **数据源 ID**。

### 3.2 API 数据源

1. **名称**、**API 基础地址**（`https://…`）、**鉴权方式**（无 / Bearer / Basic / API Key 等）及密钥或用户名密码。  
2. 可选：默认 Header、默认 Query、健康检查路径。  
3. **测试连接** → 保存，记下 **数据源 ID**。

---

## 4. 机器人（Token 与 botId）

在 **「机器人」** 标签 → **新建机器人**：

1. **名称**：任意。  
2. **Bot Token**：将 @BotFather 给的整串 **完整粘贴**（格式如 `123456789:AAH…`）。  
3. **Webhook 密钥**（可选）：若 Telegram `setWebhook` 使用 `secret_token`，此处须与之一致。  
4. **启用** → 保存。  
5. 记下表格 **「ID」** 列整数 — 即 **`botId`**，Webhook 路径必填。

> 列表中 Token 为脱敏展示；**无法再复制全文**，请自行安全保管。

**接收范围**（可选）：编辑机器人 → **全部** 或 **仅指定群**（仅所列 `chat_id` 处理命令，私聊与其它群静默）。详见 [§10](#10-接收范围与群内用法)。

---

## 5. 查询定义（向导 / SQL / API）

**「查询定义」** 标签 → 选择机器人 → **新建查询**。右侧抽屉内 **配置方式** 随数据源类型变化：

| 数据源类型 | 可选配置方式 |
|------------|----------------|
| 数据库 | **向导** 或 **高级 SQL** |
| API | 仅 **API 可视化** |

列表 **「模式」** 列会显示向导 / 高级 SQL / API。任意时候可用 **「测试」** 在后台试跑（不经 Telegram）。**保存已启用查询**后，系统会尽量调用 Telegram **`setMyCommands`** 更新 `/` 命令列表（见 [§6](#6-命令菜单与-help)）。

### 5.1 向导（仅数据库）

1. 选表、刷新 **结果列**，勾选要展示的列（展示标签默认取字段注释）。  
2. 可选：**OR 检索列**、**关键词参数名**（多为 `kw`）、**参数顺序**、固定条件等。  
3. 保存后服务端生成 SQL 并 **覆盖同步** 该查询下的字段映射。

### 5.2 高级 SQL（仅数据库）

1. **SQL 模板** 使用 `#{参数名}`，例如：  
   `SELECT order_no, status FROM orders WHERE order_no = #{orderNo}`  
2. **参数 JSON** 与占位符一致，例如：`{"params":["orderNo"]}`  
   用户发送：`/命令 参数1 参数2…`（空格分隔，顺序与 `params` 一致）。

### 5.3 API 可视化（仅 API）

1. 选预制模板或填写路径；**预览** 拉取 JSON，**点选 / 拖拽** 要返回的字段。  
2. **样例参数**（预览区）会写入 `param_schema_json` 的 **`examples`**，供命令菜单里的「示例: /命令 …」使用。  
3. 建议在 API 配置中保留 **`name`**（模板已预填），用作菜单副标题 **「查询 · 名称」**，避免多条查询共用同一数据源名时描述雷同。  
4. 保存。

---

## 6. 命令、菜单与 /help

### 6.1 用户怎么发

- 命令在管理端 **不要** 带 `/`；在 Telegram 里发 **`/命令`**，后接空格与参数。  
- 参数顺序与 **`{"params":[…]}`** 一致；向导里关键词名常见为 `kw`，则 **`/cx 整段关键词`** 作为 `kw` 参与 OR 条件。

### 6.2 命令菜单（输入 `/` 时的列表）

保存 / 更新 / 删除 **已启用** 的查询后，系统会尽量 **`setMyCommands`**，与列表中的命令同步。

| 项 | 说明 |
|----|------|
| 菜单副标题 | **API**：`apiConfigJson.name`（无则用命令名）。**向导**：配置中的 **表名**。**SQL**：`命令 · 数据源` 等，避免同源多条仅显示同一数据源名。 |
| 示例 | 描述中可有 **`示例: /命令 值1 …`**；API 与 `examples` 对齐。 |
| 只点菜单、没带参数 | Telegram **不会**自动补参数。本系统对 **整条消息无任何有效参数** 时发 **用法提示**（非错误样式）；已填部分参数仍缺后续参数时，会提示缺哪一项。 |
| 菜单未更新 | 检查 Token、后端日志/审计是否含「同步 Telegram 菜单失败」；可再 **保存一次任意查询** 触发同步。 |
| 群里输入 `/` 没有列表 | Telegram **客户端**在群内的联想列表表现不一；本系统在 `setMyCommands` 时除**默认范围**外会再写 **`all_group_chats`** 范围，尽量让**群 / 超级群**也能拿到同一套命令。仍建议用户可直接手打 **`/命令 参数`** 或 **`/命令@机器人用户名`**。 |

### 6.3 /help 与 /start

若无与 `help`、`start` **同名且已启用** 的查询定义，用户发 **`/help`** 或 **`/start`** 会列出当前机器人已启用命令及参数占位。

---

## 7. 白名单（可选）

- **白名单为空**：通常表示不限制 Telegram 用户（仍以你环境逻辑为准）。  
- **有记录**：仅表中 **Telegram 用户 ID** 可使用查询命令（与「仅指定群」不同维度）。  
- 自己的数字 ID：可用 @userinfobot 等工具查看。

---

## 8. Webhook（HTTPS 与注册）

Telegram 要求 Webhook URL 为 **HTTPS**，形态固定为：

```text
https://<公网基址>/api/webhook/<botId>
```

其中 **`<botId>`** 为管理端机器人列表中的数字 **ID**。`<公网基址>` **不要** 末尾多写 `/`。

### 8.1 本机调试（无自有域名）

1. 启动后端（端口以 `application.yml` 为准，常见 **18089**）。  
2. 使用 [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/) 或 ngrok 等暴露本机，例如：  
   `cloudflared tunnel --url http://127.0.0.1:18089`  
3. 记下终端给出的 `https://xxxx.trycloudflare.com`（穿透地址**可能每次变化**）。  
4. 完整 Webhook：`https://xxxx.trycloudflare.com/api/webhook/<botId>`。

### 8.2 已部署服务器

Nginx 终止 TLS 并反代到本机后端；示例与检查项见 [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md)。

### 8.3 在管理端注册（推荐）

**机器人** 表格对应行 → **Webhook** → **注册 Webhook**：

1. 填写 **公网 HTTPS 基址**（与穿透或域名一致，无尾 `/`）。  
2. 确认预览路径为 `/api/webhook/<botId>`。  
3. 若配置了 **Webhook 密钥**，注册时会一并提交 `secret_token`。  
4. 可在 `application.yml` 配置 **`app.telegram.public-base-url`**，对话框留空时使用默认基址。

穿透地址变更后需 **重新注册**。**Webhook 状态** 可对照 Telegram 当前 `url`。

### 8.4 手动 setWebhook（备选）

将 `<BOT_TOKEN>`、`<WEBHOOK_BASE>`、`<botId>` 换成真实值后访问：

```text
https://api.telegram.org/bot<BOT_TOKEN>/setWebhook?url=<WEBHOOK_BASE>/api/webhook/<botId>
```

返回 JSON 中 `"ok": true` 即成功。使用 Secret 时参阅 Bot API 文档增加 `secret_token`，并与管理端一致。

### 8.5 自检

浏览器或 curl：

```text
https://api.telegram.org/bot<BOT_TOKEN>/getWebhookInfo
```

核对 `url` 是否为预期地址。

---

## 9. 在 Telegram 里验证

私聊打开机器人，发送与查询定义一致的命令，例如：

```text
/cx 你的订单号
```

若 Webhook 正常、白名单与命令无误，应收到格式化后的查询结果。

---

## 10. 接收范围与群内用法

### 10.1 仅指定群（编辑机器人）

- **全部**：私聊与群均可（仍受白名单、命令等约束）。  
- **仅下方 chat_id 群**：仅所列 **群 / 超级群** 处理命令；**私聊与其它群静默**。  
- `chat_id` 多为负数（超级群常见 `-100…`），可用 @getidsbot 等查询。

### 10.2 群内多机器人时

- **推荐**：`/命令@你的机器人用户名 参数`，避免同群多 Bot 时更新被派发到错误 Bot。  
- **可尝试**：`/命令 参数`（仅一 Bot 时通常可行）、`@用户名 /命令 参数`（需后端支持从句首 `@` 截取到 `/` 的版本）。  
- **Bot 隐私模式**（BotFather 默认开启）：群内一般只处理 **命令**、含 **@你的机器人** 的消息、或 **回复该 Bot** 的内容。

**私聊正常、群里无后端日志**：多为群内 **`/命令@用户名` 写错**，更新进了另一只 Bot。请核对机器人资料里的 **@用户名** 与 Token 是否同属一只。

更细排障：服务端 `DEBUG` 下查看 `WebhookDispatchService` 是否出现 `Webhook skip: not a slash command`。

---

## 11. 常见问题

| 现象 | 可能原因 |
|------|----------|
| `setWebhook` 失败 | 非 HTTPS、路径不是 `/api/webhook/<botId>`、`botId` 与后台不一致 |
| 无回复 | 后端未启动、隧道断开、白名单不含当前用户、命令或参数错误 |
| 群里 `@机器人 /命令` 无反应 | 换用 **`/命令@正确用户名`** 或升级已修复该解析的后端 |
| 401 无法登录管理端 | `app.security.admin` 账号密码不一致 |

---

## 12. 安全提示

- 勿在公开渠道粘贴 **Bot Token**、业务库密码、API 密钥。  
- 本文不包含真实凭据。若 Token 已泄露：**@BotFather → /mybots → 选机器人 → API Token → Revoke**，再在管理端更新 Token。

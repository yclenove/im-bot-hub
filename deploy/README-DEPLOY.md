# 生产环境部署说明

本文描述在 **Linux 云服务器** 上使用 **MySQL + Spring Boot JAR + Nginx（HTTPS）** 部署本项目的常规方式。你可按实际环境（Docker、K8s、无 Nginx 仅反代网关）做等价替换。

---

## 1. 架构与访问路径

| 组件 | 说明 |
|------|------|
| **MySQL** | 存储元数据（机器人、数据源、查询、白名单、审计等），库名示例：`tg_query_meta` |
| **Spring Boot** | 监听本机 `127.0.0.1:18089`（见 `application.yml` 中 `server.port`，可覆盖），提供 `/api/webhook/**`、管理 API `/api/admin/**` 等 |
| **OpenAPI** | SpringDoc 默认 **`/v3/api-docs`**；Nginx 须反代 **`/v3/`**（与 `SecurityConfig` 放行一致），勿与已废弃的自定义 `/api-docs` 混淆 |
| **Nginx** | 对外 `443`，终止 TLS；`/` 指向管理端静态文件；`/api/`、`/v3/`、`/swagger-ui/` 等反代到后端 |
| **管理端** | `admin-ui` 执行 `npm run build` 后的 `dist/`，由 Nginx 托管 SPA |

Telegram Webhook 必须使用 **HTTPS**，公网地址示例：  
`https://你的域名/api/webhook/<botId>`（`botId` 为管理后台机器人列表中的 ID）。

---

## 2. 服务器前置条件

- **操作系统**：常见 64 位 Linux（Ubuntu 22.04 / Debian / CentOS 等）。
- **Java 运行环境**：**JDK 17**（仅运行可选用 JRE 17）。
- **MySQL**：5.7 或 8.x；提前创建数据库与用户，并授予该库权限。
- **可选**：`nginx`、`certbot`（Let’s Encrypt）、`systemd`。

构建可在 **本机或 CI** 完成，无需在服务器安装 Node/Maven（若你愿意在服务器编译也可以）。

---

## 3. 准备数据库

1. 创建数据库（名称与配置一致，默认示例为 `tg_query_meta`）：

   ```sql
   CREATE DATABASE tg_query_meta CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'tg_meta'@'%' IDENTIFIED BY '强密码';
   GRANT ALL PRIVILEGES ON tg_query_meta.* TO 'tg_meta'@'%';
   FLUSH PRIVILEGES;
   ```

2. 首次启动应用会由 **Flyway** 执行迁移脚本，**无需手工建表**（若库中已有历史对象而无 `flyway_schema_history`，请参考仓库内 `application-local.yml` 注释中的 baseline 说明）。

---

## 4. 构建产物

在仓库根目录：

```bash
# 后端可执行 JAR
cd backend
mvn -q -DskipTests package
# 生成路径示例：backend/target/telegram-query-bot-<version>.jar

# 管理端静态资源
cd ../admin-ui
npm ci
npm run build
# 产出目录：admin-ui/dist/
```

将 JAR 与 `admin-ui/dist/` 拷贝到服务器（见下一节目录示例）。

---

## 5. 服务器目录建议

示例（路径可按习惯调整）：

```text
/opt/telegram-query-bot/
  telegram-query-bot.jar          # 后端 JAR（可从带版本文件名拷贝或改名）
  config/
    application-prod.yml          # 生产配置（勿提交真实密码到 Git）
  admin-ui/dist/                  # 前端构建输出（即 index.html 所在目录）
```

从本仓库复制示例并修改：

- `deploy/application-prod.example.yml` → `config/application-prod.yml`

要点：

- **`spring.datasource.*`**：指向你的 MySQL。
- **`app.security.admin.*`**：务必改掉默认口令，使用强密码。
- **`app.cors.allowed-origins`**：加入你访问管理端的 **HTTPS 源**，例如 `https://bot.example.com`。
- **`app.encryption.secret-key-base64`**：生产环境建议配置 **32 字节 AES 密钥的 Base64**，用于加密数据源密码存储（见根目录 `README.md` 安全说明）。

启动命令示例：

```bash
java -jar /opt/telegram-query-bot/telegram-query-bot.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/opt/telegram-query-bot/config/
```

也可用环境变量覆盖部分项（如 `SPRING_DATASOURCE_PASSWORD`、`SPRING_PROFILES_ACTIVE=prod`），与 Spring Boot 文档一致即可。

---

## 6. systemd（可选，便于开机自启）

参考仓库内 `deploy/telegram-query-bot.service.example`。

```bash
sudo cp deploy/telegram-query-bot.service.example /etc/systemd/system/telegram-query-bot.service
# 编辑 User、路径、JVM 参数后：
sudo systemctl daemon-reload
sudo systemctl enable --now telegram-query-bot
sudo systemctl status telegram-query-bot
```

---

## 7. Nginx + HTTPS

1. 申请证书（Let’s Encrypt 示例）：

   ```bash
   sudo certbot certonly --nginx -d 你的域名.example.com
   ```

2. 参考 `deploy/nginx-tg-bot.example.conf` 修改 `server_name`、证书路径、`root`（指向 `admin-ui/dist`），再放入 Nginx 配置并重载：

   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

3. 确保防火墙放行 **80 / 443**（若仅 SSH 再确认 22）。

说明：

- 浏览器通过 **同一域名** 访问前端与 `/api`，可避免跨域问题；若管理端与 API 域名不同，必须把管理端 **HTTPS 源** 配进 `app.cors.allowed-origins`。
- **`/api/webhook/**` 随 `location /api/` 一并反代**，Telegram 即可 POST 到 `https://域名/api/webhook/<botId>`。

---

## 8. 部署后检查

```bash
# 健康检查（按 Nginx 是否暴露 actuator 调整 URL）
curl -sS http://127.0.0.1:18089/actuator/health

# 管理端（HTTPS）
curl -sS -o /dev/null -w "%{http_code}\n" https://你的域名/login
```

在管理后台配置机器人、数据源、查询定义后，向 Telegram 设置 Webhook 为：

`https://你的域名/api/webhook/<botId>`

若配置了 Webhook 密钥，须与 Telegram 侧 **Secret token** 一致。

---

## 9. 安全与运维提示

- 修改默认 **Basic** 管理账号密码；限制 **actuator**、**Swagger** 在生产环境的暴露范围（如仅内网或 VPN）。
- 数据源使用**只读账号**；参见 `deploy/README-REPLICA-OPS.md`。
- **多实例**时进程内限流不共享，见 `deploy/README-HORIZONTAL.md`。

---

## 10. English (short)

- Build: `mvn -f backend/pom.xml package`, `npm ci && npm run build` in `admin-ui/`.
- Run JAR with **Spring profile `prod`** and external YAML under `config/` (see `deploy/application-prod.example.yml`).
- Put **Nginx** on `:443` for TLS + SPA `dist/` + reverse proxy `/api` to `127.0.0.1:18089` (or your `server.port`).
- Set Telegram **setWebhook** to `https://<your-domain>/api/webhook/<botId>`.

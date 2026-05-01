# 服务器傻瓜部署（Linux）

面向「有一台 Linux 云服务器 + 一个域名」的场景：用 **MySQL + JAR + Nginx（HTTPS）** 跑起来，和本机开发一样用管理端配置机器人与查询。

若你使用 **宝塔面板**，步骤更偏图形化界面与 `/www/wwwroot` 目录习惯，请看：[`DEPLOY-宝塔.md`](./DEPLOY-宝塔.md)。

更偏运维的细节仍以仓库 [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md) 为准；本文只讲**按顺序该做什么**。

---

## 你要提前准备什么

| 项目 | 说明 |
|------|------|
| 服务器 | 常见 64 位 Linux（如 Ubuntu 22.04），能 SSH 登录 |
| 域名 | 已解析到该服务器公网 IP（**A 记录**） |
| 端口 | 安全组/防火墙放行 **80、443**（申请证书与 HTTPS 访问用） |
| 软件（可装时再装） | **JDK 17**、**MySQL**（5.7 或 8.x）、**Nginx** |
| 本机或 CI | 用来 **打 JAR、打前端**（服务器不必装 Node/Maven，也可以选在服务器编译） |

Telegram 要求 Webhook 为 **HTTPS**，所以生产环境**一定要有 TLS 证书**（下面用 Let’s Encrypt 免费证书举例）。

---

## 示例域名：api.hongtaiglobal.com（可复制粘贴）

以下为本仓库已填好域名的配置，**命令行 / systemd / Nginx** 部署时可直接打开复制，再改数据库与管理密码、以及 **`root`/证书路径**（若与你服务器目录不一致）：

| 说明 | 路径 |
|------|------|
| 索引与注意事项 | [`deploy/paste-ready/api.hongtaiglobal.com/README.md`](../deploy/paste-ready/api.hongtaiglobal.com/README.md) |
| `application-prod.yml` | [`deploy/paste-ready/api.hongtaiglobal.com/application-prod.yml`](../deploy/paste-ready/api.hongtaiglobal.com/application-prod.yml) |
| Nginx | [`deploy/paste-ready/api.hongtaiglobal.com/nginx.conf`](../deploy/paste-ready/api.hongtaiglobal.com/nginx.conf) |

**公网入口（证书与反代就绪后）**：

- 管理端：`https://api.hongtaiglobal.com/login`
- Webhook：`https://api.hongtaiglobal.com/api/webhook/<botId>`

`nginx.conf` 默认 `root` 为宝塔常见路径；若你使用文档中的 **`/opt/telegram-query-bot/admin-ui/dist`**，把该文件里的 `root` 改成你的目录即可。证书若用 Certbot，路径一般为 `/etc/letsencrypt/live/api.hongtaiglobal.com/`。

若使用 **宝塔**，见 [`DEPLOY-宝塔.md`](./DEPLOY-宝塔.md) 同一示例域名的「宝塔操作建议」。

---

## 第一步：服务器上安装运行环境

以 Ubuntu 为例（其他发行版用等价包名即可）：

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mysql-server nginx
java -version   # 确认为 17
```

启动 MySQL，并确保开机自启：

```bash
sudo systemctl enable --now mysql
```

---

## 第二步：建库与用户

登录 MySQL（首次可能要 `sudo mysql`）后执行（**把密码换成强密码**）：

```sql
CREATE DATABASE tg_query_meta CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tg_meta'@'127.0.0.1' IDENTIFIED BY '你的强密码';
GRANT ALL PRIVILEGES ON tg_query_meta.* TO 'tg_meta'@'127.0.0.1';
FLUSH PRIVILEGES;
```

**首次启动应用**时 Flyway 会自动建表，一般**不用手工导入表结构**。

---

## 第三步：在本机或 CI 打出产物

在**仓库根**执行：

```bash
cd backend
mvn -q -DskipTests package
# 得到：backend/target/telegram-query-bot-<version>.jar

cd ../admin-ui
npm ci
npm run build
# 得到：admin-ui/dist/
```

把下面两样上传到服务器（例如 `scp`、面板文件管理）：

- JAR（可改名为 `telegram-query-bot.jar` 方便写 systemd）
- 整个 **`admin-ui/dist/`** 目录

---

## 第四步：服务器目录与生产配置

建议目录（可按习惯改路径，下文以之为例）：

```text
/opt/telegram-query-bot/
  telegram-query-bot.jar
  config/
    application-prod.yml
  admin-ui/dist/          # 即 index.html 所在目录
```

1. 复制仓库里的示例：  
   [`deploy/application-prod.example.yml`](../deploy/application-prod.example.yml)  
   到服务器 `config/application-prod.yml`。

2. **务必修改**（至少）：

   - `spring.datasource.url / username / password`：指向第二步的数据库。
   - `app.security.admin.username / password`：管理后台登录（**不要用默认弱口令**）。
   - `app.cors.allowed-origins`：填你将来浏览器访问管理端的 **HTTPS 地址**，例如 `https://bot.你的域名.com`（不要漏 `https://`，不要末尾 `/`）。

3. **可选但推荐**：`app.encryption.secret-key-base64` 配置 **32 字节 AES 密钥的 Base64**，用于加密数据源密码存储（见根目录 [`README.md`](../README.md) 安全说明）。

4. **关于访问 Telegram API（发消息、一键注册 Webhook）**  
   - 服务器在**境外或能直连** `api.telegram.org`：不必配代理。  
   - 若机器在**国内且出站超时**（与本机 curl 测 Telegram 类似），可在 `application-prod.yml` 里增加与 [`application.yml`](../backend/src/main/resources/application.yml) 相同的 `app.telegram.outbound-proxy`（指向你可用的 HTTP 代理），再重启服务。

---

## 第五步：启动后端（先手动测通）

```bash
sudo mkdir -p /opt/telegram-query-bot/config
# 已上传 JAR 与 application-prod.yml 后：

sudo java -jar /opt/telegram-query-bot/telegram-query-bot.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/opt/telegram-query-bot/config/
```

另开一个终端检查（应返回含 `"UP"` 的 JSON）：

```bash
curl -sS http://127.0.0.1:18089/actuator/health
```

看到正常后，控制台 `Ctrl+C` 停掉，改用 systemd（下一步）。

---

## 第六步：systemd 开机自启（推荐）

```bash
sudo cp /path/to/repo/deploy/telegram-query-bot.service.example /etc/systemd/system/telegram-query-bot.service
sudo nano /etc/systemd/system/telegram-query-bot.service
```

把里面的 **`User`**、**`WorkingDirectory`**、**`ExecStart` 里的 JAR 路径**改成你的实际路径（示例见文件注释）。然后：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now telegram-query-bot
sudo systemctl status telegram-query-bot
```

---

## 第七步：HTTPS 证书 + Nginx

1. 安装 certbot（Ubuntu 示例）：

   ```bash
   sudo apt install -y certbot python3-certbot-nginx
   ```

2. 先放一个**最小**的 Nginx `server`（能访问到 80），或用 `certbot certonly --standalone -d 你的域名`（按 certbot 提示操作）。  
   证书一般在：`/etc/letsencrypt/live/你的域名/`。

3. 参考仓库 [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf)，把 **`server_name`**、**证书路径**、**root（指到 admin-ui/dist）** 改成你的值，保存为例如：

   `/etc/nginx/sites-available/telegram-bot.conf`

   再启用并重载：

   ```bash
   sudo ln -s /etc/nginx/sites-available/telegram-bot.conf /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   ```

4. 云厂商安全组确认 **80、443** 已对公网开放。

此时浏览器访问：`https://你的域名/login`  
用 **`application-prod.yml` 里配置的管理员账号** 登录（若 401，检查 Basic 账号密码与浏览器是否走了缓存）。

---

## 第八步：Telegram Webhook

公环境下的 Webhook 地址形如：

```text
https://你的域名/api/webhook/<botId>
```

其中 `<botId>` 是管理后台 **机器人列表里的数字 ID**。  
可在管理端使用 **「注册 Webhook」**（见 [`TELEGRAM-傻瓜配置.md`](./TELEGRAM-傻瓜配置.md)），或按该文档用手动 `setWebhook` / `getWebhookInfo` 自检。

若配置了 **Webhook 密钥**，务必与 Telegram 侧 **`secret_token`** 一致。

---

## 第九步：部署后自检清单

| 检查项 | 命令或操作 |
|--------|------------|
| 后端进程 | `systemctl status telegram-query-bot` |
| 本机健康检查 | `curl -sS http://127.0.0.1:18089/actuator/health` |
| HTTPS 管理端 | 浏览器打开 `https://域名/login` |
| Telegram 是否打到你的服务 | 看 Nginx 访问日志或后端日志里是否有 `POST /api/webhook/...` |

---

## 临时关闭管理端（保留 Telegram Webhook）

若希望 **暂时不对公网开放管理后台**，但 **机器人仍通过 Webhook 正常工作**，请用 **Nginx 按路径开关**，**不要**把整个 HTTPS 站点停掉（除非你的架构见下「分站点」说明）。

| 路径 | 作用 |
|------|------|
| `/` | 管理端 Vue 静态页 |
| `/api/admin/` | 管理 API |
| `/api/webhook/` | Telegram 推送，**必须保持反代可用** |

**推荐**：在 Nginx 的 `server { ... }` 里单独保留 `location ^~ /api/webhook/` 的反代；临时维护期间对 `location ^~ /api/admin/` 与 `location /` 使用 `return 403`（或与示例一致的策略），需要时再改回 [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf) 中的 `try_files` 与 `/api/` 反代，`nginx -t` 通过后重载。**不要停掉** 后端 Java 进程，否则本机 `18089` 不可用，Webhook 也会失败。

**若用宝塔**：见 [`DEPLOY-宝塔.md`](./DEPLOY-宝塔.md) 中 **「临时关闭管理后台」**（含 **能否只关 HTML 项目** 的说明）。

**分站点部署的例外**：若管理端与 Webhook **不同域名 / 不同站点**，且 Webhook **只注册在仍在线的域名**上，可以只停用「仅托管管理页」的那一个站点；与 **单域名、单站点同时托管静态 + `/api/`** 的常见写法不同，请勿混淆。

---

## 常见问题

**Q：管理端能打开，但 Telegram 没反应？**  
先看 [`TELEGRAM-傻瓜配置.md`](./TELEGRAM-傻瓜配置.md) 里 Webhook 与网络说明；再在服务器上 `curl -I https://api.telegram.org`：若连 Telegram 都超时，需要换网络环境或配置 **`app.telegram.outbound-proxy`**。

**Q：改配置后不生效？**  
改的是 `config/application-prod.yml` 时，需要 **`sudo systemctl restart telegram-query-bot`**。

**Q：多实例 / 限流？**  
见 [`deploy/README-HORIZONTAL.md`](../deploy/README-HORIZONTAL.md)。

---

## 与仓库文件对照

| 用途 | 文件 |
|------|------|
| 详细生产说明 | [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md) |
| 生产配置示例 | [`deploy/application-prod.example.yml`](../deploy/application-prod.example.yml) |
| systemd 示例 | [`deploy/telegram-query-bot.service.example`](../deploy/telegram-query-bot.service.example) |
| Nginx 示例 | [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf) |
| 示例域名可粘贴包（api.hongtaiglobal.com） | [`deploy/paste-ready/api.hongtaiglobal.com/`](../deploy/paste-ready/api.hongtaiglobal.com/) |
| 只读库与运维 | [`deploy/README-REPLICA-OPS.md`](../deploy/README-REPLICA-OPS.md) |

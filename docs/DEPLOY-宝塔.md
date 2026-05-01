# 服务器傻瓜部署（宝塔面板 / Linux）

面向「已装或打算装 **宝塔（BT Panel）**、有一台 Linux 云服务器 + 一个域名」的场景：用 **MySQL + JAR + Nginx（HTTPS）** 跑起来，逻辑与 [`DEPLOY-傻瓜部署.md`](./DEPLOY-傻瓜部署.md) 相同，只是把**安装、建站、后端进程**改成**面板操作**。

宝塔官方对 **JAR / Spring Boot** 的通用说明见：  
[宝塔面板 Java 项目部署教程](https://docs.bt.cn/practical-tutorials/Java-Project-Deployment-Tutorial)（面板版本更迭时界面文案可能略有差异，以你当前面板为准。）

更细的运维说明仍以仓库 [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md) 为准。

---

## 你要提前准备什么

| 项目 | 说明 |
|------|------|
| 服务器 | 常见 64 位 Linux（CentOS 7+ / Ubuntu 20+ 等），能 SSH 登录 |
| 域名 | 已解析到该服务器公网 IP（**A 记录**） |
| 端口 | **云安全组**与**宝塔防火墙**均放行 **80、443**（HTTPS 与证书验证用）；SSH **22** 按需 |
| 本机或 CI | 用来 **打 JAR、打前端**（服务器也可编译，非必须） |

Telegram Webhook 需要 **HTTPS**，生产环境请配置 TLS（下面用宝塔自带 **Let’s Encrypt**）。

---

## 示例域名：api.hongtaiglobal.com（可复制粘贴）

以下为本仓库已填好域名的配置文件，部署时可直接打开复制，再按文件内说明改密码与路径：

| 说明 | 路径 |
|------|------|
| 索引与注意事项 | [`deploy/paste-ready/api.hongtaiglobal.com/README.md`](../deploy/paste-ready/api.hongtaiglobal.com/README.md) |
| `application-prod.yml` | [`deploy/paste-ready/api.hongtaiglobal.com/application-prod.yml`](../deploy/paste-ready/api.hongtaiglobal.com/application-prod.yml) |
| Nginx `server` 段 | [`deploy/paste-ready/api.hongtaiglobal.com/nginx.conf`](../deploy/paste-ready/api.hongtaiglobal.com/nginx.conf) |
| 宝塔 Java 项目启动参数 | [`deploy/paste-ready/api.hongtaiglobal.com/java-project-start-args.txt`](../deploy/paste-ready/api.hongtaiglobal.com/java-project-start-args.txt) |

**公网入口（证书与反代就绪后）**：

- 管理端登录：`https://api.hongtaiglobal.com/login`
- Webhook 地址形如：`https://api.hongtaiglobal.com/api/webhook/<botId>`

**宝塔操作建议**：**网站** → 添加站点 → 域名填 `api.hongtaiglobal.com` → 根目录与 `nginx.conf` 里 **`root`** 一致（默认示例为 `/www/wwwroot/api.hongtaiglobal.com`），把 **`admin-ui/dist/`** 内容传进该目录；**SSL** 里为该域名申请证书后，若证书路径不是 `/etc/letsencrypt/live/...`，以面板显示为准修改 `nginx.conf` 前两行证书路径。**Java项目** 里 JAR 路径、`--spring.config.additional-location` 须与你实际上传的 `config/` 目录一致（示例假定在 [`java-project-start-args.txt`](../deploy/paste-ready/api.hongtaiglobal.com/java-project-start-args.txt) 中的 `/www/wwwroot/telegram-query-bot/config/`）。

---

## 第一步：安装宝塔并装运行环境

1. 按 [宝塔官网](https://www.bt.cn/new/download.html) 用脚本安装面板，浏览器登录（入口端口以安装提示为准）。
2. **软件商店**中安装（版本选新稳定版即可）：
   - **Nginx**
   - **MySQL**（5.7 或 8.x）

3. **JDK 17（本项目的硬性要求）**  
   - 新版面板：**网站** → 切到 **Java项目** 页签 → **Java环境管理**（或 **Java环境检测**）→ 安装 **JDK 17**；  
   - 若商店/面板未提供 17，可在服务器用系统包安装 `openjdk-17-jre`，再在终端确认：

   ```bash
   java -version   # 需为 17
   ```

---

## 第二步：在宝塔里建库与用户

1. 面板左侧 **数据库** → **添加数据库**：
   - 库名示例：`tg_query_meta`
   - 编码选 **utf8mb4**
   - 记下 **用户名、密码**（权限默认只对本机，与 `127.0.0.1` 连接方式兼容）。
2. 首次启动应用时 Flyway 会自动建表，一般**不用手工导入表结构**。

（若习惯命令行，也可用 root 登录 MySQL 后 `CREATE DATABASE ...`，与傻瓜部署文档写法一致。）

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

用 **宝塔文件管理**、`scp` 或 SFTP 上传到服务器，例如：

```text
/www/wwwroot/telegram-query-bot/
  telegram-query-bot.jar
  config/
    application-prod.yml
  www/                 # 下一步「网站根目录」，稍后放 dist
```

---

## 第四步：生产配置 `application-prod.yml`

1. 复制示例 [`deploy/application-prod.example.yml`](../deploy/application-prod.example.yml) 为服务器上的 `config/application-prod.yml`。
2. **务必修改**（至少）：
   - `spring.datasource.url / username / password`：指向第二步数据库（URL 一般为 `jdbc:mysql://127.0.0.1:3306/tg_query_meta?...`）。
   - `app.security.admin.username / password`：管理后台登录（**不要用弱口令**）。
   - `app.cors.allowed-origins`：填浏览器访问管理端的 **完整 HTTPS 根地址**，例如 `https://tg.example.com`（含 `https://`，**不要**末尾 `/`）。
3. **可选**：`app.encryption.secret-key-base64` 等见根目录 [`README.md`](../README.md)。**若配置了该密钥**：新建飞书 / 钉钉渠道时，`t_bot_channel.credentials_json` 会以 `enc:v1:` 前缀的密文存储；未配置时仍为明文 JSON。密钥须妥善备份，丢失后已加密行无法解密。
4. **国内服务器访问 Telegram**：若出站 `api.telegram.org` 不稳定，可在 `application-prod.yml` 配置 `app.telegram.outbound-proxy`（与 [`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml) 相同项）。

后端默认监听 **`18089`** 端口（见 `application.yml` 中 `server.port`），以下步骤请保持一致。

---

## 第五步：用宝塔「Java项目」跑后端 JAR（推荐）

新版面板路径：**网站** → 顶层选择 **Java项目**（与 PHP / Node 等并列）→ 先确认 **Java环境检测** 为已安装，必要时点 **Java环境管理** 装 **JDK 17** → 点击 **添加项目**。

按面板向导填写（名称以你当前宝塔版本为准，核心一致即可）：

| 要点 | 本项目建议值 |
|------|----------------|
| 项目类型 | **Spring Boot** / **JAR 项目**（不要选仅 Tomcat/WAR，除非你知道自己在做什么） |
| JAR 路径 | 例如 `/www/wwwroot/telegram-query-bot/telegram-query-bot.jar` |
| 运行端口 | **18089**（与 Nginx 反代、`server.port` 一致） |
| 启动参数 / JVM 参数 | 在「额外参数」或「启动命令」可编辑区域，在 `-jar ...jar` **之后**追加：  
  `--spring.profiles.active=prod --spring.config.additional-location=file:/www/wwwroot/telegram-query-bot/config/`  
  （路径按你实际上传位置改，**最后保留 `/`** 指向目录即可。） |
| 工作目录 | 可选设为 `/www/wwwroot/telegram-query-bot`（若面板提供该选项，便于相对路径；否则只靠绝对路径亦可。） |

说明：

- 部分版本支持在面板里直接编辑**外置** `application.yml` / 项目配置；若与你的 `config/application-prod.yml` 重复，**只保留一种来源**，避免混淆。
- 启动失败时，优先看该 **Java项目** 的 **日志 / 项目输出**，再 SSH 执行：  
  `curl -sS http://127.0.0.1:18089/actuator/health`  
  应返回含 `"UP"` 的 JSON。

**不要**把 Java 项目绑定的公网域名当成「唯一入口」直接指向 18089：本项目需要 **Nginx 同一域名**下既托管 **Vue 静态页**，又把 **`/api` 反代到 18089**，请继续第六、七步。

---

### 备选：用 systemd 跑 JAR（不用面板 Java 项目时）

若你更习惯系统服务，可复制 [`deploy/telegram-query-bot.service.example`](../deploy/telegram-query-bot.service.example) 到 `/etc/systemd/system/telegram-query-bot.service`，把 **`User`**（宝塔常见为 **`www`**）、路径与 `--spring.config.additional-location` 改成与你服务器一致，再 `systemctl enable --now telegram-query-bot`。**不要**同时用两种方式拉起**同一个** JAR，避免端口冲突。

---

## 第六步：添加网站与放置前端

1. **网站** → **添加站点** → 类型选 **纯静态**（或 PHP 均可）→ 域名填你的域名 → 根目录指向放前端的目录，例如：  
   `/www/wwwroot/telegram-query-bot/www`
2. 将 **`admin-ui/dist/` 里的全部文件**上传到该目录（保证该目录下有 `index.html`）。

---

## 第七步：Nginx：HTTPS + 反代后端

1. 在站点 **设置** → **SSL** → 选择 **Let’s Encrypt** 申请证书并开启 **强制 HTTPS**（Telegram Webhook 需要 HTTPS）。
2. 站点 **设置** → **配置文件**，在 **`server { ... }` 里**合并与 [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf) 一致的后端反代逻辑：

   - `root` 指向前一步站点根（含 `index.html`）。
   - `location /`：`try_files $uri $uri/ /index.html;`
   - `location /api/`、`/v3/`（OpenAPI 规范 JSON：`/v3/api-docs`）、`/swagger-ui/`、`/actuator/` 等：`proxy_pass http://127.0.0.1:18089;`，并保留示例里的超时与 `X-Forwarded-*` 头。

宝塔默认可能已有静态资源 `location`，**不要整段删除**，只插入/合并需要的 `location`，保存前 **测试配置** 再 **重载**。

若面板里 Java 项目提供 **外网映射**，通常也只能映射**整站或整端口**；本项目仍推荐在**网站**的 Nginx 里完成「静态 + `/api`」，可与官方教程中「配置 80 端口访问 / 反向代理」对照理解。

---

## 第八步：Telegram Webhook

公网 Webhook 地址：

```text
https://你的域名/api/webhook/<botId>
```

`<botId>` 为管理后台 **机器人列表中的数字 ID**。在管理端用 **「注册 Webhook」** 即可，详见 [`TELEGRAM-傻瓜配置.md`](./TELEGRAM-傻瓜配置.md)。若使用 **Webhook 密钥**，须与 Telegram 侧 `secret_token` 一致。

---

## 第九步：自检

| 检查项 | 操作 |
|--------|------|
| 后端（面板） | **网站 → Java项目** 中该项目为运行中；或 systemd 为 `active` |
| 本机健康 | `curl -sS http://127.0.0.1:18089/actuator/health` |
| 管理端 | 浏览器 `https://你的域名/login` |
| OpenAPI | 浏览器访问 `https://你的域名/v3/api-docs`（或 Swagger UI）能加载规范 JSON；须已在 Nginx 中反代 **`/v3/`**（与 SpringDoc 默认路径一致） |
| Telegram | 发消息后看日志是否有 `POST /api/webhook/...` |
| Webhook 密钥（生产建议） | 在管理端为机器人配置 **Webhook 密钥** 并 **注册 Webhook**；未配置时 Webhook 仅靠 URL 中的 `botId`，易被扫描猜测 |

### Webhook 密钥生产检查清单

1. 在管理端为该机器人填写 **Webhook 密钥**（与 Telegram `setWebhook` 的 `secret_token` 一致，长度 ≤256）。  
2. 使用管理端 **注册 Webhook**（或等价调用），确认 Telegram 侧已带上密钥。  
3. 确认 Nginx/网关 **透传** 请求头 `X-Telegram-Bot-Api-Secret-Token`（未剥离）。  
4. 用错误密钥或省略该头调用 `POST /api/webhook/<botId>` 应返回 **403**。  
5. 业务库账号保持 **只读**；管理端 Basic 与 `spring.datasource` 使用强密码且仅 HTTPS 暴露。

---

## 常见问题（宝塔场景）

**Q：面板里 Java 项目已启动，但网站 `/api` 502？**  
检查端口是否为 **18089**、Nginx `proxy_pass` 是否指向 `127.0.0.1:18089`、站点配置是否已重载。

**Q：改 `application-prod.yml` 后不生效？**  
在 **Java项目** 里对该项目执行 **重启**；若用 systemd则 `sudo systemctl restart telegram-query-bot`。

**Q：证书申请失败？**  
确认域名已解析到本机、80 未被占用、安全组放行 80；亦可参考 [`DEPLOY-傻瓜部署.md`](./DEPLOY-傻瓜部署.md) 里 certbot 思路。

**Q：和官方教程里的「网站 → Spring Boot」不一样？**  
新版 Linux 面板多把入口收到 **网站 → Java项目** 统一页签，官方文档若仍写「Spring Boot」子菜单，请以你面板实际菜单为准，见 [宝塔 Java 项目部署教程](https://docs.bt.cn/practical-tutorials/Java-Project-Deployment-Tutorial)。

**Q：想临时关掉管理页面，不用的时候再打开，能直接停「HTML 项目」吗？**  
见下文 **「临时关闭管理后台（保留 Telegram Webhook）」** 一节。

---

## 临时关闭管理后台（保留 Telegram Webhook）

本仓库部署方式下：

- **管理界面**：浏览器访问站点根路径 **`/`**（宝塔里多为 **HTML 项目** 或 **静态站点** 的 `root`）。
- **管理 API**：路径均在 **`/api/admin/`** 下。
- **Telegram Webhook**：路径为 **`/api/webhook/<botId>`**，必须由 Nginx **反代到后端**，且 **HTTPS 有效**，否则机器人收不到消息。

### 推荐做法：只改当前网站的 Nginx，不要整站停用

在 **网站 → 对应域名 → 设置 → 配置文件** 中：

1. **保留** 对 **`/api/webhook/`** 的反代（建议单独写 `location ^~ /api/webhook/ { ... }`，与 [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf) 一致）。
2. **临时关闭管理端时**：对 **`/api/admin/`** 与站点根 **`/`** 返回 **403**（或 **444**），不再 `try_files` 到 `index.html`。
3. **需要维护时**：把上述两段恢复为正常静态 + 反代，`nginx -t` 通过后重载。

示例（关闭管理端时的思路，按你现有 `server {}` 合并；**顺序上让更长的前缀先匹配**）：

```nginx
location ^~ /api/webhook/ {
    proxy_pass http://127.0.0.1:18089;
    proxy_http_version 1.1;
    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location ^~ /api/admin/ {
    return 403;
}

location / {
    return 403;
}
```

其余如 **`/v3/`**、Swagger 等若公网不需要，也可在「关闭管理端」时一并 `return 403`，避免误暴露。**不要**删除 **`Java项目`** 里的后端进程，否则连本机 `18089` 都没了，Webhook 反代也会挂。

**仍建议**在 `application-prod.yml` 中为管理账号设置**高强度密码**；仅靠「关页面」不能替代强认证。

### 不推荐：直接停用整个「HTML 项目」或暂停网站

若 **管理页与 `/api/` 反代写在同一个站点**（本文档的常规做法：`/` 静态 + `location /api/`），在宝塔里 **暂停该站点**、**删除该站点** 或 **关闭该 HTML 项目**（以实际合并方式为准），往往等于 **整段 Nginx `server` 不提供服务**，则  
`https://你的域名/api/webhook/...` **也会失效**，Telegram 无法推送更新。

**例外**：你若刻意拆成 **两个站点 / 两个域名**（例如 `admin.example.com` 只放静态管理端，`bot.example.com` 只负责反代 Webhook 且 Bot 的 Webhook **只注册到后者**），则可以 **只停管理端那个站点**；平时仍推荐用上文 **同一站点内挡路径** 的方式，少运维心智负担。

命令行部署、未用宝塔时的同类说明见 [`DEPLOY-傻瓜部署.md`](./DEPLOY-傻瓜部署.md) 中的同名小节。

---

## 飞书（Lark）Webhook 与 Cloudflare 提示

- **回调路径**：`https://<公网 HTTPS 基址>/api/webhook/lark/<渠道ID>`。`<渠道ID>` 在管理端「机器人 → 编辑机器人 → 飞书渠道」中创建渠道后由系统分配；将完整 URL 填到飞书开放平台事件订阅（`im.message.receive_v1`）的请求地址。
- **公网基址**：与 Telegram Webhook 相同，在 `application-prod.yml` 中配置 **`app.telegram.public-base-url`**（无末尾 `/`），管理端才能展示完整飞书回调 URL。
- **首版能力**：事件体按**明文 JSON**处理；若在飞书侧开启「Encrypt Key」加密，需后续版本接入解密逻辑。
- **Cloudflare**：与 Telegram 一致，建议使用 **Full / Full (strict)**，避免 **Flexible**；并保证源站证书链完整（`fullchain.pem`）。

---

## 钉钉（DingTalk）Outgoing 回调

- **回调路径**：`https://<公网 HTTPS 基址>/api/webhook/dingtalk/<渠道ID>`。`<渠道ID>` 在管理端「机器人 → 编辑机器人 → IM 渠道」里创建 **钉钉** 渠道后由系统分配；将该完整 URL 填到钉钉企业内部机器人的 **Outgoing（HTTP 回调）** 地址。
- **公网基址**：与 Telegram / 飞书相同，配置 **`app.telegram.public-base-url`**（无末尾 `/`），管理端才会展示完整回调 URL。
- **安全**：钉钉会带请求头 **`timestamp`**、**`sign`**；本服务用机器人安全设置里的 **AppSecret**（与钉钉文档一致：`sign` = Base64(HMAC_SHA256(AppSecret, `timestamp + "\n" + AppSecret`))）校验。管理端创建渠道时填写的 **App Secret** 必须与钉钉后台 **完全一致**。时间戳与服务器时间相差超过约 **1 小时** 会校验失败（注意服务器 NTP）。
- **回复方式**：与飞书「调开放平台发消息」不同，钉钉 Outgoing 通常以 **本次 HTTP 响应 JSON 正文** 作为机器人回复；本服务在成功处理命令后返回 `{"msgtype":"text","text":{"content":"..."}}`，无有效斜杠命令时返回空对象 `{}`。
- **消息格式**：用户消息体中的 `text.content` 须包含 **`/命令`**（群内常见「先 @ 机器人再换行写命令」也可，系统会从首个 **`/`** 起截取再解析）。与 Telegram 相同，使用 **`/命令 参数1 参数2`**。
- **Nginx**：与 Telegram / 飞书一样，**必须**把 **`/api/webhook/`** 反代到后端（`location ^~ /api/webhook/`），否则钉钉无法访问。
- **Cloudflare**：同上，建议 **Full / Full (strict)**，避免 **Flexible**。

---

## 企业微信（WeWork）自建应用「接收消息」

- **回调路径**：`https://<公网 HTTPS 基址>/api/webhook/wework/<渠道ID>`。在企业微信管理后台 → 应用管理 → 目标自建应用 → **接收消息**：URL 填此地址；**Token**、**EncodingAESKey**、企业 **CorpID**、应用 **AgentId** 与管理端创建渠道时填写的内容须**逐项一致**。
- **验证流程**：企业微信会先 **GET**（`msg_signature`、`timestamp`、`nonce`、`echostr`）做 URL 验证；通过后以 **POST** 同参 + **XML 密文**推送消息。本服务使用 `weixin-java-common` 的 **AES** 加解密与腾讯文档一致的签名算法。
- **能力与限制**：当前仅处理 **`MsgType=text`** 且正文含 **`/命令`** 的消息（与 Telegram 解析规则一致）；被动回复为 **加密 XML**；非文本、无斜杠命令或解析失败时返回明文 **`success`**。会话内容存档、上下游、客户联系等未接入。
- **公网 / Nginx / Cloudflare**：与飞书、钉钉相同；须反代 **`/api/webhook/`**；TLS 勿用 Cloudflare **Flexible**。

---

## 与仓库文件对照

| 用途 | 文件 |
|------|------|
| 命令行版傻瓜部署 | [`docs/DEPLOY-傻瓜部署.md`](./DEPLOY-傻瓜部署.md) |
| 宝塔官方 Java 通用教程 | [宝塔面板 Java 项目部署教程](https://docs.bt.cn/practical-tutorials/Java-Project-Deployment-Tutorial) |
| 详细生产说明 | [`deploy/README-DEPLOY.md`](../deploy/README-DEPLOY.md) |
| 生产配置示例 | [`deploy/application-prod.example.yml`](../deploy/application-prod.example.yml) |
| systemd 备选 | [`deploy/telegram-query-bot.service.example`](../deploy/telegram-query-bot.service.example) |
| Nginx 片段参考 | [`deploy/nginx-tg-bot.example.conf`](../deploy/nginx-tg-bot.example.conf) |
| 示例域名可粘贴包（api.hongtaiglobal.com） | [`deploy/paste-ready/api.hongtaiglobal.com/`](../deploy/paste-ready/api.hongtaiglobal.com/) |

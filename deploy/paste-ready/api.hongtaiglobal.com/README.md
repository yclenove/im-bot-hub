# api.hongtaiglobal.com 可粘贴配置

本目录为本仓库维护的**示例域名** `api.hongtaiglobal.com` 的生产配置片段，部署时**复制内容到服务器**后，务必替换：

- 数据库密码、`app.security.admin` 密码；
- 可选：`app.encryption.secret-key-base64`。

| 文件 | 用途 |
|------|------|
| [`application-prod.yml`](./application-prod.yml) | Spring Boot `config/application-prod.yml` |
| [`nginx.conf`](./nginx.conf) | 精简版 Nginx（两段 `server`）；证书路径见文件头 |
| [`nginx-full-baota.www-telegram-query-bot.conf`](./nginx-full-baota.www-telegram-query-bot.conf) | **宝塔整段粘贴**：`root=/www/wwwroot/telegram-query-bot/www`，含 SSL/扩展引用 |
| [`java-project-start-args.txt`](./java-project-start-args.txt) | 宝塔 **Java项目** → 添加/编辑项目时，跟在 `-jar xxx.jar` **后面**的启动参数 |

**请先完成**：域名 **A 记录**指向服务器公网 IP；**80 / 443** 放行；申请好该域名的 SSL 证书后再套 `nginx.conf`。

**公网地址备忘**（勿把 Token 提交到 Git）：

- 管理端：`https://api.hongtaiglobal.com/login`
- Webhook 注册基址：`https://api.hongtaiglobal.com`（已从 `application-prod.yml` 中 `app.telegram.public-base-url` 对齐）

通用说明仍以 [`docs/DEPLOY-傻瓜部署.md`](../../../docs/DEPLOY-傻瓜部署.md) 与 [`docs/DEPLOY-宝塔.md`](../../../docs/DEPLOY-宝塔.md) 为准。

# CI/CD 自动部署（Git Push -> 生产自动打包发布）

本文说明如何在本仓库启用 GitHub Actions：当你 `push` 到 `main/master` 后，自动构建后端与前端，并发布到宝塔服务器。

如需一步一步的完整执行版，请查看：`docs/CI-CD-自动部署-完整操作手册.md`。

---

## 1. 已包含的内容

仓库已新增：

- 工作流：`.github/workflows/deploy-prod.yml`
- 服务器部署脚本：`deploy/deploy-prod.sh`

流程：

1. CI 构建后端 JAR 与 `admin-ui/dist`；
2. 上传到服务器发布目录（按 commit SHA 分目录）；
3. 远程执行部署脚本：
   - 备份旧 JAR；
   - 覆盖前端静态文件；
   - 覆盖后端 JAR；
   - 执行重启命令；
   - 健康检查。

---

## 2. GitHub Secrets 配置

### 2.1 先选对位置（重要）

当前工作流 `.github/workflows/deploy-prod.yml` 中包含：

```yaml
environment: production
```

因此建议将密钥放在 **Environment secrets（production）**，不要只放在 Repository secrets。

推荐操作路径：

1. `Settings -> Environments -> New environment`，创建 `production`；
2. 进入 `production` 环境；
3. 在 **Environment secrets** 中添加下面所有 `PROD_*` 密钥。

> 备选：若你不想使用环境级密钥，可删除工作流里的 `environment: production`，然后改用 Repository secrets。两种方式选一种即可，不要混用。

### 2.2 需要添加的 Secrets

在 GitHub 仓库 `Settings -> Secrets and variables -> Actions`（推荐在 `production` 环境下）新增以下 Secrets：

| Secret | 示例 | 说明 |
|---|---|---|
| `PROD_HOST` | `1.2.3.4` | 生产服务器 IP 或域名 |
| `PROD_PORT` | `22` | SSH 端口 |
| `PROD_USER` | `root` 或 `www` | SSH 登录用户 |
| `PROD_SSH_KEY` | `-----BEGIN...` | 对应用户私钥（建议专用部署密钥） |
| `PROD_RELEASE_DIR` | `/www/wwwroot/telegram-query-bot/releases` | 发布目录（会按 SHA 创建子目录） |
| `PROD_APP_DIR` | `/www/wwwroot/telegram-query-bot` | 后端 JAR 所在目录 |
| `PROD_WEB_DIR` | `/www/wwwroot/telegram-query-bot/www` | Nginx 站点静态目录（放 `index.html`） |
| `PROD_RESTART_CMD` | `kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true` | 宝塔守护模式推荐：仅杀进程，由宝塔守护自动拉起 |
| `PROD_HEALTHCHECK_URL` | `http://127.0.0.1:18089/actuator/health` | **必填**；用于确认服务是否真正拉起 |
| `PROD_JAR_NAME` | `telegram-query-bot.jar` | 发布到 `PROD_APP_DIR` 下的 JAR 文件名 |

> `PROD_RESTART_CMD` 请先在服务器手工验证可执行，再填到 Secret。  
> 若你是宝塔菜单式 CLI（`bt`/`btcli` 不能非交互调用），建议改为执行服务器固定脚本，例如：`bash /www/wwwroot/telegram-query-bot/scripts/restart-java.sh`。

---

## 3. 宝塔/服务器前置准备

至少准备好这些目录（不存在就先创建）：

```bash
mkdir -p /www/wwwroot/telegram-query-bot/releases
mkdir -p /www/wwwroot/telegram-query-bot/www
```

并确认：

- 宝塔 Java 项目（或 systemd）启动命令已可用；
- Nginx 的 `root` 指向 `PROD_WEB_DIR`；
- `/api/` 已反代到后端（默认 `127.0.0.1:18089`）。

---

## 4. 推荐的重启命令写法（宝塔守护模式）

你当前环境建议使用「宝塔守护模式」：

- **只杀旧进程，不手动启动**，由宝塔 Java 项目守护自动拉起：

  ```text
  kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true
  ```

- 如需兜底可追加端口杀进程：

  ```text
  kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true; fuser -k 18089/tcp || true
  ```

把最终可执行的一行命令填入 `PROD_RESTART_CMD`。

### 为什么不再推荐手动 start

在宝塔 Java 项目已开启守护时，`kill + 手动 sh 启动` 可能和守护动作冲突，导致状态不一致。  
因此建议 CI 只负责“触发重启（kill）”，由宝塔负责“拉起进程”。

### 健康检查必须保留

`PROD_HEALTHCHECK_URL` 不建议留空。  
没有健康检查，CI 无法知道服务是否真的启动成功（例如 Flyway 校验失败、配置错误、端口未监听）。

### 宝塔菜单式 CLI 说明

部分服务器中 `bt` / `btcli` 只有交互菜单，无法在 CI 中直接执行。这种情况下：

1. 在服务器创建固定重启脚本（示例见仓库 `deploy/restart-java-prod.example.sh`）；
2. 赋予执行权限：`chmod +x /www/wwwroot/telegram-query-bot/scripts/restart-java.sh`；
3. 将 `PROD_RESTART_CMD` 设为上面的“只 kill”命令（守护模式推荐）。

建议统一固定文件名为 `telegram-query-bot.jar`，并确保重启脚本、宝塔启动命令、`PROD_JAR_NAME` 三者保持一致。

---

## 5. 触发方式

- 自动触发：`push` 到 `main` 或 `master`
- 手动触发：GitHub Actions 页面点击 `Deploy Production` 的 `Run workflow`

---

## 6. 排错建议

- SSH 失败：优先检查 `PROD_USER`、`PROD_PORT`、`PROD_SSH_KEY`。
- 上传成功但重启失败：在服务器手工执行 `PROD_RESTART_CMD` 查具体报错。
- 健康检查失败：确认后端端口、`application-prod.yml`、数据库可达、Nginx 反代无误。

---

## 7. 安全建议

- 使用专用部署密钥，不要复用个人私钥。
- 建议在 GitHub 使用 `Environment: production` + required reviewers 做发布审批。
- 生产配置（如数据库密码）继续保留在服务器本地配置文件，不要进 Git。

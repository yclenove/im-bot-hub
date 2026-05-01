# CI/CD 自动部署完整操作手册（宝塔 + GitHub Actions）

本文按你当前生产结构整理：

- 后端目录：`/www/wwwroot/telegram-query-bot`
- 前端目录：`/www/wwwroot/api.hongtaiglobal.com`
- 宝塔 Java 项目为菜单式 CLI（`bt` / `btcli` 交互）

---

## 1. 目标与发布流程

目标：本地代码 `git push` 后，自动在 GitHub Actions 完成构建并发布到生产服务器。

发布流程：

1. 构建后端 JAR 与前端 `dist`；
2. 上传产物到服务器 `releases/<commit_sha>/`；
3. 执行 `deploy/deploy-prod.sh`：
   - 备份当前 JAR；
   - 备份当前前端静态目录；
   - 替换新版本；
   - 执行重启命令；
   - 健康检查；
   - 若失败：自动回滚旧 JAR + 旧前端并再次重启。

---

## 2. 仓库中需要的文件

已在仓库提供：

- 工作流：`.github/workflows/deploy-prod.yml`
- 部署脚本：`deploy/deploy-prod.sh`
- 重启脚本模板：`deploy/restart-java-prod.example.sh`

---

## 3. 服务器一次性准备

### 3.1 创建目录

```bash
mkdir -p /www/wwwroot/telegram-query-bot/releases
mkdir -p /www/wwwroot/telegram-query-bot/backup
mkdir -p /www/wwwroot/telegram-query-bot/scripts
mkdir -p /www/wwwroot/telegram-query-bot/logs
mkdir -p /www/wwwroot/api.hongtaiglobal.com
```

### 3.2 宝塔守护模式推荐（不再自定义 start 脚本）

你的当前环境推荐使用「宝塔 Java 项目守护」模式：

- CI 只 kill 旧进程（触发重启）；
- 由宝塔守护自动拉起服务；
- 避免 `nohup` 与宝塔守护双启动冲突。

请在宝塔项目中确认：

- 已开启「项目意外停止时自动重启」；
- 未将 `application-prod.yml` 作为“环境变量从文件加载”执行（否则会出现 `spring:: command not found`）。

### 3.3 手工验证守护重启

```bash
kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true
sleep 5
curl -sS http://127.0.0.1:18089/actuator/health
```

返回包含 `"UP"` 代表守护拉起正常。

---

## 4. GitHub Secrets 完整配置（写入位置要正确）

### 4.1 推荐方式：Environment secrets（production）

因为工作流使用了 `environment: production`，建议把密钥放到 `production` 环境下。

操作步骤：

1. 打开仓库 `Settings -> Environments`；
2. 若没有环境，点 `New environment` 新建 `production`；
3. 进入 `production`；
4. 点 `Add environment secret`，逐条添加下表里的所有 `PROD_*`。

### 4.2 需要添加的密钥

在 `production` 环境中新增：

| Secret | 值 |
|---|---|
| `PROD_HOST` | 你的服务器 IP |
| `PROD_PORT` | `22` |
| `PROD_USER` | `root`（或有权限用户） |
| `PROD_SSH_KEY` | 对应私钥全文 |
| `PROD_RELEASE_DIR` | `/www/wwwroot/telegram-query-bot/releases` |
| `PROD_APP_DIR` | `/www/wwwroot/telegram-query-bot` |
| `PROD_WEB_DIR` | `/www/wwwroot/api.hongtaiglobal.com` |
| `PROD_RESTART_CMD` | `kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true` |
| `PROD_HEALTHCHECK_URL` | `http://127.0.0.1:18089/actuator/health`（必填） |
| `PROD_JAR_NAME` | `telegram-query-bot.jar` |

### 4.3 备选方式（仅在你明确需要时）

如果你想把密钥放到 Repository secrets，而不是 Environment secrets，则需要先删除工作流中的：

```yaml
environment: production
```

然后再到 `Settings -> Secrets and variables -> Actions -> Repository secrets` 添加同样的密钥。

不建议同时混用两套来源，容易排查困难。

---

## 5. 首次发布验证步骤

1. 在 GitHub Actions 中手动运行 `Deploy Production`（`Run workflow`）。
2. 确认步骤均成功，尤其是：
   - `Upload artifacts and deploy script`
   - `Run remote deploy`
3. 服务器验证：

```bash
curl -sS http://127.0.0.1:18089/actuator/health
```

4. 页面验证：
   - `https://api.hongtaiglobal.com/login`
   - 业务接口是否可用。

---

## 6. 日常使用

- 你平时只需 `git push main`（或 `master`）；
- 工作流会自动发布到生产；
- 每次发布都会在 `backup` 留历史备份（JAR + 前端快照）。

---

## 7. 自动回滚说明

`deploy/deploy-prod.sh` 在健康检查失败时会自动：

1. 还原上一次 JAR；
2. 还原上一次前端静态文件；
3. 再次执行重启命令（守护模式下为再次 kill，由宝塔重新拉起）；
4. 再次健康检查。

注意：

- 回滚成功后，工作流仍会标记失败（这是预期，便于你感知本次发布有问题）。
- 如果回滚后仍不健康，需要人工检查配置、数据库或依赖服务。

---

## 8. 常见问题排查

### 8.1 SSH 连接失败

- 检查 `PROD_HOST`、`PROD_PORT`、`PROD_USER`、`PROD_SSH_KEY` 是否对应；
- 确认服务器安全组放行 22 端口。

### 8.2 发布成功但服务未启动

- 手工执行：`kill -9 "$(cat /var/tmp/springboot/vhost/pids/telegram-query-bot.pid)" || true`
- 检查端口：`ss -lntp | grep 18089 || true`
- 在宝塔面板查看该 Java 项目日志与运行状态

### 8.3 健康检查失败

- 确认应用端口是 `18089`；
- 确认 `PROD_HEALTHCHECK_URL` 已配置且可从服务器本机访问；
- 检查 `application-prod.yml`（数据库、账号密码、外部依赖）；
- 直接看后端启动日志定位异常。

### 8.4 前端访问异常

- 检查 Nginx `root` 是否为 `/www/wwwroot/api.hongtaiglobal.com`；
- 确认站点目录下有 `index.html`。

---

## 9. 安全建议

- 使用专用部署密钥（不要复用个人私钥）；
- 建议给 `production` 环境加审批；
- 生产密码和密钥仅保留在服务器配置文件，不提交 Git。

# 测试策略 V2（中英）/ Test Strategy V2

---

## 1. 目标 / Objective

**中文**

- 确保 V1→V2 迁移零数据丢失
- 确保多平台 Webhook 主链路可用
- 确保品牌重命名后编译和构建通过
- 确保新功能（通用白名单/日志/Channel 管理）正确运行

**English**

- Zero data loss in V1→V2 migration
- Multi-platform webhook main flows working
- Brand rename compiles and builds successfully
- New features (generic allowlist/logging/channel management) working correctly

---

## 2. 测试分层 / Test layers

| 层级 | V2 新增重点 |
|------|-------------|
| 单元测试 | ChannelCredentialResolver、ChannelAllowlistService、CommandLogService |
| 集成测试 | Flyway V13-V15 迁移验证（Testcontainers）；多平台 Webhook 主流程 |
| API 测试 | 新旧 API 兼容性；CommandLog API；Channel Allowlist API |
| 前端验证 | Bot 创建 + Channel 绑定流程；日志页平台筛选 |
| 数据迁移验证 | V13 token 迁移完整性；V14 白名单迁移完整性 |

---

## 3. 测试矩阵 / Test matrix

| 变更类型 | 最低测试 | 推荐补充 |
|----------|----------|----------|
| 包名重命名 | `mvn test` + `npm run build` | 全量编译 + 启动验证 |
| Flyway V13-V15 | Testcontainers 空库 + 已有库 | 手动检查数据完整性 |
| Bot-Channel 分离 | API CRUD 测试 | 前端端到端流程 |
| 通用白名单 | 单元测试 + API 测试 | 多平台冒烟 |
| 通用命令日志 | 单元测试 + API 测试 | 多平台冒烟 |
| Slack/Discord 接入 | Webhook 本地测试 | tunnel 联调 |
| QueryOrchestrationService 重构 | 单元测试 | TG + 飞书双平台冒烟 |
| 前端重构 | `npm run build` | 手工验证关键流程 |

---

## 4. 冒烟清单 / Smoke checklist

- [ ] 后端 `mvn test` 全量通过
- [ ] 前端 `npm run build` 通过
- [ ] 管理端可登录
- [ ] 创建 Bot（无 TG 字段）
- [ ] 绑定 Telegram Channel
- [ ] 绑定飞书 Channel
- [ ] 创建查询定义
- [ ] 测试查询返回成功
- [ ] 命令日志页面可查看（按平台筛选）
- [ ] 白名单按 Channel 管理生效
- [ ] 旧 API `/api/admin/telegram-query-logs` 仍可访问
- [ ] Telegram Webhook 发送命令返回结果
- [ ] 日志中无 token / 密码泄漏

---

## 5. 数据迁移专项 / Migration-specific tests

| 场景 | 验证方法 |
|------|----------|
| V13 TG token 迁入 t_bot_channel | 迁移后 SELECT credentials_json FROM t_bot_channel WHERE platform='TELEGRAM' |
| V13 primary_channel_id 回填 | 迁移后 SELECT primary_channel_id FROM t_bot WHERE id=X |
| V14 白名单迁移 | 迁移后对比 t_user_allowlist 与 t_channel_allowlist 记录数 |
| V15 新建 t_command_log | 迁移后 SHOW CREATE TABLE t_command_log |
| 迁移后旧功能可用 | 管理端 CRUD + Telegram Webhook 冒烟 |

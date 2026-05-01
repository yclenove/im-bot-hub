# CLAUDE.md — im-bot-hub

## 项目背景

本项目由 `telegram-query-bot` 重构而来，目标是成为**通用 IM 查询机器人配置中心**（im-bot-hub）。

- **新包名**：`com.sov.imhub`（当前代码仍是 `com.sov.telegram.bot`，待阶段 0.5 重命名）
- **新 artifactId**：`im-bot-hub`（当前 pom.xml 仍是 `telegram-query-bot`，待修改）
- **旧仓库**：`H:\aicoding\telegram-query-bot`

## 当前进度

- [x] 阶段 0：文档准备 — PRD-V2、需求分析-V2、设计文档-V2、迁移指南、测试策略-V2 已完成
- [ ] **阶段 0.5：品牌重塑** ← 下一步
  - pom.xml artifactId → `im-bot-hub`
  - 目录移动：`com.sov.telegram.bot` → `com.sov.imhub`
  - 全局替换包名、import、Spring 应用名
  - application.yml: `spring.application.name: im-bot-hub`
  - admin-ui 标题更新
  - `mvn test` + `npm run build` 验证
- [ ] 阶段 1：数据层重构（Flyway V13-V15）
- [ ] 阶段 2：Service 层重构
- [ ] 阶段 3：API/DTO 层重构
- [ ] 阶段 4：前端重构
- [ ] 阶段 5：新功能 + 文档同步

## 关键文档

| 文档 | 用途 |
|------|------|
| `docs/PRD-V2.md` | 产品需求文档 V2 |
| `docs/REQUIREMENTS-ANALYSIS-V2.md` | 需求分析（23 项需求） |
| `docs/DESIGN-V2.md` | 设计文档（架构图、ER 图、包结构） |
| `docs/MIGRATION-GUIDE.md` | V1→V2 迁移指南 |
| `docs/TEST-STRATEGY-V2.md` | 测试策略 |
| `docs/ITERATION-PLAN.md` | 迭代计划（已更新 V2 优先级） |
| `AGENTS.md` | Agent 规范（已集成 Cursor Rules） |

## 技术约束

- 提交信息使用**中文**
- 改 backend 须 `mvn test`，改 frontend 须 `npm run build`
- Flyway 脚本不可逆（V13-V15 需要先备份数据库）
- 旧 API 保留兼容期（至少一个版本周期）
- 安全红线：日志禁泄 token/密码，SQL 参数化绑定

## 本地运行

```bash
# 后端（需要 MySQL 本地运行，密码见 application-local.yml）
cd backend && ./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 前端
cd admin-ui && npm install && npm run dev
```

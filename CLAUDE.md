# CLAUDE.md — im-bot-hub

## 项目背景

本项目由 `telegram-query-bot` 重构而来，目标是成为**通用 IM 查询机器人配置中心**（im-bot-hub）。

- **新包名**：`com.sov.imhub`（已完成重命名）
- **新 artifactId**：`im-bot-hub`（已完成）
- **旧仓库**：`H:\aicoding\telegram-query-bot`（V1 版本，V2 已迁至此仓库）

## 当前进度

- [x] 阶段 0：文档准备 — PRD-V2、需求分析-V2、设计文档-V2、迁移指南、测试策略-V2 已完成
- [x] **阶段 0.5：品牌重塑** ✅
  - pom.xml artifactId → `im-bot-hub`
  - 目录移动：`com.sov.telegram.bot` → `com.sov.imhub`
  - 全局替换包名、import、Spring 应用名
  - application.yml: `spring.application.name: im-bot-hub`
  - admin-ui 标题更新
  - `mvn compile` + `npm run build` 验证通过
- [x] 阶段 1：数据层重构 ✅（Flyway V14-V16，新 Domain 实体 + Mapper）
- [x] 阶段 2：Service 层重构 ✅（ChannelCredentialResolver / ChannelAllowlistService / CommandLogService / QueryOrchestrationService 去耦合）
- [x] 阶段 3：API/DTO 层重构 ✅（Bot DTO 废弃 TG 字段 / CommandLog API / Channel Allowlist API）
- [x] 阶段 4：前端重构 ✅（Bot 表单折叠 TG 配置 / 命令日志 Tab 适配新 API）
- [x] 阶段 5：新平台接入 ✅（Slack + Discord Webhook + OutboundMessenger）
- [x] **阶段 6：验证与兼容** ✅
  - `mvn test` 全量验证通过（96 个测试，0 失败）
  - SecurityConfig 已放行 `/api/webhook/**`（Slack/Discord 路径自动覆盖）
  - 旧 API `/api/admin/telegram-query-logs` 已添加 @Deprecated 注解
  - `npm run build` 前端构建成功
  - Slack/Discord 后端支持已添加（BotChannelCreateRequest + AdminBotChannelController）
- [x] **阶段 7：功能增强** ✅
  - 前端 Channel 管理页（独立页面管理各平台渠道）
  - Bot Entity 纯化（TG 专属字段添加 @Deprecated）
  - Webhook 签名验证（Slack Signing Secret、Discord Ed25519）
  - 集成测试（Flyway V14-V16 在 Testcontainers 上验证）
- [ ] **下一步**：重启前后端项目、CF 穿透、功能测试

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
| `docs/ROADMAP-V3.md` | V3 路线图（功能规划、技术架构、实施计划） |

## 技术约束

- 提交信息使用**中文**
- 改 backend 须 `mvn test`，改 frontend 须 `npm run build`
- Flyway 脚本不可逆（V14-V16 需要先备份数据库；V13 已用于软删除）
- 旧 API 保留兼容期（至少一个版本周期）
- 安全红线：日志禁泄 token/密码，SQL 参数化绑定

## 代码质量规则

**所有新代码必须符合以下规则，无需事后优化：**

- 📄 **完整规则文档**：`.claude/rules/CODE_QUALITY.md`
- 🎯 **核心原则**：单一职责、平台解耦、日志规范、安全第一
- ✅ **提交前检查**：运行 `mvn test`（后端）或 `npm run build`（前端）
- 📏 **行数限制**：Controller ≤200行、Service ≤300行、Vue组件 ≤300行
- 🔒 **安全红线**：日志禁泄凭证、SQL参数化、凭证加密存储
- 📝 **注释要求**：所有public方法必须有Javadoc，复杂逻辑必须有行内注释

## 本地运行

```bash
# 后端（需要 MySQL 本地运行，密码见 application-local.yml）
cd backend && ./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 前端
cd admin-ui && npm install && npm run dev
```


# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文代码审查规范——在保持专业严谨的同时，用符合国内团队文化的方式给出有效反馈
- **chinese-commit-conventions**: 中文 Git 提交规范 — 适配国内团队的 commit message 规范和 changelog 自动化
- **chinese-documentation**: 中文技术文档写作规范——排版、术语、结构一步到位，告别机翻味
- **chinese-git-workflow**: 适配国内 Git 平台和团队习惯的工作流规范——Gitee、Coding、极狐 GitLab、CNB 全覆盖
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发或执行实现计划之前使用——创建具有智能目录选择和安全验证的隔离 git 工作树
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。

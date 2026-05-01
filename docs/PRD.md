# 产品需求文档（中英）/ Product Requirements Document

---

## 1. 产品定位 / Product positioning

**中文**

- `telegram-query-bot` 是一个**可配置的查询机器人平台**：管理员通过管理端配置机器人、数据源、查询命令、字段映射与白名单，把终端用户的 IM 命令转换为**只读、参数化 SQL** 查询，或转换为第三方 **API** 请求，并以适配各渠道的消息格式返回结果。
- 当前产品重点是：**安全可控、配置驱动、低运维门槛、面向非开发管理员可操作**。

**English**

- `telegram-query-bot` is a **configurable query bot platform** that lets admins map IM commands either to **read-only, parameterized SQL** or to external **API** requests and return channel-specific messages.
- Current product focus: **safe, configurable, low-ops, and operable by non-developer admins**.

---

## 2. 目标用户 / Target users

| 角色 | 中文职责 | English responsibility |
|------|----------|------------------------|
| 管理员 | 配置 Bot、Datasource、Query、字段映射、白名单、渠道凭据 | Configure bots, datasources, queries, field mappings, allowlists, channel credentials |
| 业务使用者 | 在 Telegram / 飞书 / 钉钉 / 企业微信中发送命令获取结果 | Send commands in IM channels and receive results |
| 运维 / DBA | 维护部署、HTTPS、只读账号、索引、库容量与保留策略 | Maintain deployment, HTTPS, read-only accounts, indexes, retention |

---

## 3. 产品目标 / Product goals

**中文**

1. 让管理员能在**不改代码**的前提下快速配置查询机器人。
2. 让终端用户以**最少步骤**完成查询，收到**可读、脱敏、结构稳定**的结果。
3. 保证平台在安全、审计、只读访问、参数绑定上的边界清晰。
4. 支持多 Bot、多数据源、多渠道扩展，同时维持统一管理体验。

**English**

1. Enable admins to configure query bots **without code changes**.
2. Let end users complete lookups with **minimal steps** and receive stable, readable, masked results.
3. Preserve strict security, auditability, read-only access, and parameter-binding guarantees.
4. Scale to multiple bots, datasources, and channels with a consistent admin experience.

---

## 4. 核心能力 / Core capabilities

1. 机器人与渠道管理 / Bot and channel management
2. 数据源管理：数据库与 API 双模式 / Datasource management: database and API modes
3. 查询定义：参数化 SQL、可视化向导、API 可视化 / Query definitions: SQL, visual wizard, visual API mode
4. 白名单、限流、审计、查询日志 / Allowlists, rate limiting, audit, query logs
5. 可视化向导、JSON 字段点选映射、Benchmark、索引建议 / Visual wizard, JSON field selection/mapping, benchmark, index advice
6. 多 IM Webhook 接入 / Multi-IM webhook ingress

---

## 5. 用户体验目标 / UX goals

**中文**

- 管理端优先做到**少输入、少跳转、少记忆**。
- 管理员无需理解底层 JDBC/SQL 实现细节，也能完成主要配置路径。
- API 配置要尽量隐藏技术细节，优先通过模板、默认值、字段预览和点击式映射完成配置。
- 对用户可见的错误信息必须短、明确、可操作。
- 复杂配置优先通过默认值、提示、向导、预校验降低使用门槛。
- 页面不仅要可用，还要保持视觉一致、信息层级清晰、空态与错误态友好。

**English**

- Minimize input, navigation, and memorization in the admin UI.
- Keep core flows operable without requiring deep JDBC/SQL internals knowledge.
- Hide API-level complexity behind presets, defaults, previews, and click-based mapping whenever possible.
- User-visible errors should be short, clear, and actionable.
- Reduce friction with defaults, hints, wizards, and pre-validation.
- Maintain visual consistency, clear hierarchy, and friendly empty/error states.

---

## 6. 范围 / Scope

### 6.1 当前范围 / In scope

- Telegram 主链路配置与查询
- 飞书 / 钉钉 / 企业微信 webhook 接入
- 只读业务库查询
- 第三方 HTTP API 查询
- 管理端配置、测试、日志与审计
- 查询结果字段映射、格式化、脱敏
- API 返回 JSON 的预览、字段发现、点选与拖拽排序

### 6.2 暂不纳入 / Out of scope for now

- 复杂会话式对话状态机
- 跨库联邦查询
- 全文搜索引擎能力
- 自动执行索引 DDL
- 面向普通终端用户的图形化自助查询页面

---

## 7. 成功指标 / Success metrics

**中文**

- 新增一个 Bot + Datasource + QueryDefinition 的配置过程可在 10 分钟内完成；使用预制 API 模板时应进一步缩短到 5 分钟内。
- 常见查询链路中，终端用户能在 1 次命令发送内得到结果。
- 管理端常见配置页具备完整加载态、空态、错误提示。
- 所有业务值查询均走参数绑定，不出现明文拼接输入。
- 所有可见变更都可追溯到 `CHANGELOG` 与相关设计文档。

**English**

- A new bot + datasource + query can be configured within 10 minutes.
- Typical user queries complete within a single command interaction.
- Admin pages expose loading, empty, and error states consistently.
- All business values are queried through bound parameters only.
- Every visible change is traceable in `CHANGELOG` and related design docs.

---

## 8. 发布质量门槛 / Release quality gates

1. 文档同步：`README`、`CHANGELOG`、必要的 `DESIGN`/PRD/需求分析/测试文档已更新。
2. 测试完成：后端改动通过 `mvn test`，前端改动通过 `npm run build`。
3. 安全检查：无 token、密码、密钥、敏感参数值泄漏到日志与文档。
4. 体验检查：关键页面无明显断裂流程、错误提示或布局失衡。
5. 提交流程：提交信息使用中文，且聚焦单一主题。

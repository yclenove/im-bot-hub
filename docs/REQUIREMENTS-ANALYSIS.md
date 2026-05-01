# 需求分析（中英）/ Requirements Analysis

---

## 1. 业务问题 / Business problem

**中文**

- 业务侧常见诉求是：让非开发人员快速把内部查询能力暴露给 Telegram 或其他 IM 用户，同时保证数据库安全、结果可控和可审计。
- 若直接由开发写机器人脚本，容易出现：重复开发、逻辑分散、SQL 安全边界模糊、部署维护成本高、无法由管理员独立运营。

**English**

- The business needs a safe way for non-developers to expose internal read queries to Telegram and other IM channels.
- One-off custom bot scripts tend to create duplication, weak SQL safety, high maintenance cost, and poor operability.

---

## 2. 核心需求 / Core requirements

### 2.1 功能需求 / Functional requirements

1. 管理员可以创建和维护多个 Bot。
2. 管理员可以维护多个只读业务数据源，也可以维护多个 API 数据源。
3. 管理员可以配置查询命令、SQL 模板、API 请求配置、参数说明、字段映射、展示格式。
4. 用户可以通过 IM 命令触发查询并获取结果。
5. 系统能够记录审计日志和 Telegram 查询日志。
6. 系统应支持查询测试、元数据查看、向导生成 SQL、Benchmark、索引建议。
7. 系统应支持 Telegram、飞书、钉钉、企业微信等渠道接入。

### 2.2 非功能需求 / Non-functional requirements

1. 安全：所有值必须参数绑定；业务库连接只读；敏感信息不可泄漏。
2. 可用性：管理端操作路径短，错误提示清晰，默认值合理，API 配置尽量模板化和傻瓜化。
3. 可维护性：配置优先、文档齐全、分层清晰、改动可追溯。
4. 可观测性：关键链路有日志与审计，便于定位失败原因。
5. 可扩展性：可新增 Bot、Datasource、QueryDefinition、Channel 而不改核心流程。

---

## 3. 约束条件 / Constraints

1. 配置库使用 MySQL，结构演进通过 Flyway。
2. 业务查询仅面向只读数据源。
3. Webhook 需暴露公网 HTTPS。
4. 管理端当前采用 HTTP Basic。
5. 文档需使用中文或中英对照。
6. API 模式同样不能泄漏密钥、Token、Header 敏感值到日志、审计和前端错误信息中。

---

## 4. 典型用户场景 / Typical user scenarios

### 场景 A：新建机器人并上线命令

1. 管理员创建 Bot。
2. 管理员创建业务数据源。
3. 管理员配置查询定义与字段映射。
4. 管理员测试 SQL。
5. 管理员设置 Telegram Webhook。
6. 用户发送命令并收到结果。

### 场景 B：线上问题排查

1. 用户反馈命令失败。
2. 管理员查看 Telegram 查询日志与审计日志。
3. 管理员确认命令、参数格式、数据源和字段映射。
4. 必要时调整配置并重新测试。

### 场景 C：对接第三方 API 能力

1. 管理员在数据源页选择 API 模式。
2. 管理员直接套用天气或币价等预制模板，或手工填写 API 基础地址与鉴权方式。
3. 管理员执行 API 连通性测试，确认基础配置有效。
4. 管理员在查询定义页进入 API 可视化模式，预览 JSON 返回样例。
5. 管理员点击需要展示的字段，并拖拽排序、设置展示标签。
6. 管理员保存命令后，在后台测试实际 API 查询结果，再交给终端用户使用。

---

## 5. 风险分析 / Risk analysis

| 风险 | 中文说明 | 应对策略 |
|------|----------|----------|
| SQL 注入 | 管理员误配模板或开发绕过执行器 | 模板校验 + 仅允许受控执行器 + 值参数绑定 |
| 数据越权 | 使用了可写账号或错误库 | 业务数据源只读、配置与业务库分离 |
| 用户误操作 | 页面复杂、提示不清、字段过多 | 默认值、向导、预校验、清晰表单结构 |
| 结果不可读 | 字段展示混乱、文案差 | FieldMapping、格式化、标签与脱敏 |
| 排障困难 | 缺少日志或日志泄漏敏感信息 | 安全上下文日志 + 审计 + 脱敏 |
| API 接入门槛高 | 管理员不懂 Header、Query、JSON Pointer 等技术术语 | 预制模板、可视化预览、自动发现字段、点击式映射 |

---

## 6. 当前差距 / Current gaps before this update

**中文**

- 缺少正式 PRD。
- 缺少正式需求分析文档。
- 缺少独立测试策略/测试说明文档。
- `DESIGN.md` 具备架构与数据流，但对“文档体系、图示种类、交付纪律”的约束仍不够显式。
- `README.md` 与 `AGENTS.md` 尚未把 PRD/需求分析/测试文档纳入正式索引。

**English**

- No formal PRD.
- No dedicated requirements analysis document.
- No standalone testing strategy document.
- `DESIGN.md` covers architecture, but not the full doc-system and delivery-discipline expectations.
- `README.md` and `AGENTS.md` do not index PRD/requirements/testing docs yet.

---

## 7. 本次补全结论 / Conclusions for this update

1. 新增正式 PRD 文档。
2. 新增正式需求分析文档。
3. 新增正式测试策略与执行说明文档。
4. 扩展设计文档，加入更完整的分析与图示。
5. 更新 README、WORKFLOW、AGENTS、CHANGELOG，使文档体系进入可追踪状态。

---

## 8. 本次功能增量需求 / Requirement increment in this feature update

1. 数据源层必须从“仅数据库”扩展为“数据库 + API”双模式。
2. API 数据源必须支持基础地址、超时、默认 Header、默认 Query 参数、连通性测试。
3. API 鉴权至少覆盖 `NONE`、`BEARER_TOKEN`、`BASIC`、`API_KEY_HEADER`、`API_KEY_QUERY`。
4. 查询定义层必须支持 API 查询配置，并能与既有命令体系、启用状态、超时、最大返回条数共存。
5. API 返回必须支持 JSON 预览、字段发现、字段点选、拖拽排序、字段映射落库。
6. 产品体验上必须提供常见场景预制模板，减少管理员暴露在技术细节中的时间。

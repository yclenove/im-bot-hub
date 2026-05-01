# 测试策略与执行说明（中英）/ Test Strategy and Execution Guide

---

## 1. 目标 / Objective

**中文**

- 为本项目建立统一的测试分层、执行门槛与交付纪律，确保每个阶段的功能完成后都有可重复的验证动作。

**English**

- Define a shared testing strategy, execution bar, and delivery discipline so each completed stage has repeatable validation.

---

## 2. 测试分层 / Test layers

| 层级 | 中文说明 | 执行建议 |
|------|----------|----------|
| 单元测试 | 校验器、解析器、加解密、生成器、纯逻辑服务 | 默认必补，执行 `mvn test` |
| 集成测试 | DB、Flyway、Spring Boot 启动、Webhook 主流程 | 推荐通过 Testcontainers 或本地受控环境验证 |
| 前端构建验证 | TypeScript、Vite 构建、基本依赖完整性 | 修改前端时执行 `npm run build` |
| 联调验证 | 管理端到后端、Webhook 到出站行为 | 关键流程改动后人工或自动验证 |
| 发布前冒烟 | 登录、创建配置、测试查询、关键接口可达 | 发布前执行清单式验证 |

---

## 3. 默认执行规则 / Default execution rules

1. 修改 `backend/`：至少执行 `mvn test`。
2. 修改 `admin-ui/`：至少执行 `npm run build`。
3. 修改接口契约、数据库、Webhook 流程：补充主链路验证。
4. 仅修改文档：可不执行编译测试，但必须检查文档索引、链接与内容一致性。
5. 每阶段完成后再进入提交，不能跳过验证直接提交。

---

## 4. 推荐测试矩阵 / Recommended test matrix

| 变更类型 | 最低测试 | 推荐补充 |
|----------|----------|----------|
| SQL 模板校验 | `mvn test` | 非法模板、边界输入、注入案例 |
| 数据源管理 | `mvn test` | 本地连库验证、连接失败路径 |
| API 数据源 / API 查询 | `mvn test` + `npm run build` | 鉴权方式、JSON 预览、字段映射、连通性失败路径 |
| Webhook / 渠道 | `mvn test` | 本地 tunnel + 渠道回调冒烟 |
| 管理端 UI | `npm run build` | 手工验证关键页面流转 |
| Flyway 迁移 | 启动 + migrate | 空库/已有库两类验证 |
| 文档体系 | 链接与索引检查 | 交叉引用一致性检查 |

---

## 5. 手工冒烟清单 / Manual smoke checklist

- 后端 `/actuator/health` 可访问
- Swagger `/swagger-ui/index.html` 可访问
- 管理端 `/login` 可访问并能登录
- 机器人、数据源、查询定义列表页能正常加载
- 数据源页能分别完成数据库连接测试与 API 连通性测试
- API 查询可完成预览、字段点选、拖拽排序并成功保存
- 测试查询接口能返回成功或清晰错误
- Webhook 地址可通过公网 tunnel 访问
- 日志中无 token、密码、敏感参数明文

---

## 6. 失败处理原则 / Failure handling

1. 测试失败时先修问题，再提交。
2. 若测试环境受限，应明确记录“未执行项、原因、风险、建议后续验证”。
3. 不允许把明显失败状态带入正式提交并假设后续处理。

---

## 7. 与交付流程的关系 / Relation to delivery workflow

**中文**

- 推荐流程：开发完成 -> 自动/手工测试 -> 更新 README / CHANGELOG / 设计文档 -> 中文提交信息 -> push。

**English**

- Preferred flow: implementation complete -> automated/manual verification -> update README / CHANGELOG / design docs -> Chinese commit message -> push.

---

## 8. API 专项用例 / API dedicated cases

- API 数据源配置、API 查询配置、返回结果映射的完整用例矩阵与 MCP 页面自动化步骤见 [`docs/API-TEST-CASES.md`](API-TEST-CASES.md)。

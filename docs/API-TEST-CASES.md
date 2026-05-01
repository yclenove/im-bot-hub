# API 功能完整测试用例（数据源配置 / 查询配置 / 结果映射）

## 1. 范围与目标

本文覆盖以下三条主链路：

1. API 数据源配置（创建、编辑、连通性、鉴权、异常）。
2. API 查询配置（模板、预览、参数推导、保存）。
3. 返回结果映射（字段点选、排序、脱敏、格式化、落库）。

测试分层：

- 后端逻辑自动化（Unit/Service/Controller）。
- 后端接口联调（真实 API）。
- MCP 页面自动化回归（真实 API 页面流转 + 抓包证据）。

真实 API 基线：

- 天气模板：`https://wttr.in`
- 币价模板：`https://api.binance.com`

---

## 2. 测试环境与前置条件

### 2.1 环境

- 后端：`backend`（Spring Boot，profile=`local`）
- 前端：`admin-ui`（Vite Dev Server）
- 数据库：`tg_query_meta`
- 管理账号：`admin / change-me`
- 浏览器自动化：MCP `chrome-devtools`

### 2.2 前置数据

- 已存在一个机器人（如 `id=1`）。
- 至少存在一个 API 数据源模板可选（天气或币价）。
- Flyway 至少包含并执行：
  - `V10__api_datasource_and_api_query.sql`
  - `V11__datasource_nullable_fields_for_api.sql`

---

## 3. API 数据源配置测试矩阵

| 用例ID | 类型 | 场景 | 输入/操作 | 预期结果 |
|---|---|---|---|---|
| DS-001 | 正向 | 新建 API（NONE） | 名称+baseUrl+NONE | 200，列表新增，类型=API |
| DS-002 | 正向 | 新建 API（BASIC） | username+passwordPlain | 200，鉴权字段持久化 |
| DS-003 | 正向 | 新建 API（BEARER） | passwordPlain | 200 |
| DS-004 | 正向 | 新建 API（API_KEY_HEADER） | authConfig.keyName + passwordPlain | 200 |
| DS-005 | 正向 | 新建 API（API_KEY_QUERY） | authConfig.keyName + passwordPlain | 200 |
| DS-006 | 正向 | 编辑 API 数据源 | 更新 baseUrl/timeout/config | 200，字段更新 |
| DS-007 | 正向 | API 连通性测试 | 点击“测试连接” | 200，提示连通成功 |
| DS-008 | 校验 | 名称为空 | 直接创建 | 前端提示“请填写数据源名称” |
| DS-009 | 校验 | baseUrl 为空 | API 模式下创建 | 前端提示“请填写 API 基础地址” |
| DS-010 | 校验 | baseUrl 非 http(s) | `api.example.com` | 400，`API 基础地址必须...` |
| DS-011 | 校验 | BASIC 缺用户名 | authType=BASIC，username 空 | 400，`Basic 鉴权用户名不能为空` |
| DS-012 | 校验 | BASIC 缺密码 | authType=BASIC，password 空且无历史密文 | 400 |
| DS-013 | 校验 | API_KEY 缺 keyName | authConfigJson `{}` | 400，`API Key 名称不能为空` |
| DS-014 | 边界 | 超时过小 | requestTimeoutMs<500 | 自动回退/设置为 5000 |
| DS-015 | 回归 | API 创建不触发 NOT NULL 冲突 | 仅填 API 字段，不填 jdbc/username/password_cipher | 200（不应 409） |
| DS-016 | 回归 | DB->API 字段清理 | sourceType 改 API | jdbc 字段清空 |
| DS-017 | 回归 | API->DB 字段清理 | sourceType 改 DATABASE | API 字段清空 |
| DS-018 | 回归 | 更新不改密码 | passwordPlain 为空 | 保留原 `passwordCipher` |
| DS-019 | 异常 | 重名/唯一冲突 | 重复创建同唯一键 | 409，冲突提示可读 |
| DS-020 | 异常 | 外部 API 非 JSON | 连通/预览命中非 JSON 响应 | 400，`API 未返回有效 JSON` |
| DS-021 | 异常 | 外部 API 4xx/5xx | 无效 path 或参数 | 400，包含状态码与目标 URL |

---

## 4. API 查询配置测试矩阵

| 用例ID | 类型 | 场景 | 输入/操作 | 预期结果 |
|---|---|---|---|---|
| AQ-001 | 正向 | 模板应用 | 点击天气/币价模板 | path/query/outputs 被初始化 |
| AQ-002 | 正向 | 预览成功（GET） | path + args | `/api-query/preview` 返回 fields+sampleRows |
| AQ-003 | 正向 | 预览成功（POST） | method=POST+bodyTemplate | 返回解析成功 |
| AQ-004 | 正向 | 预览成功（PUT） | method=PUT | 返回解析成功 |
| AQ-005 | 正向 | 参数推导 | path/query/header/body 中含 `{{param}}` | `paramSchemaJson` 含去重后参数 |
| AQ-006 | 正向 | 保存 API 查询 | 输出字段至少1个 | `POST /api/admin/bots/{id}/queries` 200 |
| AQ-007 | 校验 | apiConfigJson 非法 | `{bad json}` | 400，`Invalid apiConfigJson` |
| AQ-008 | 校验 | path 为空 | 点击保存 | 前端提示“请填写接口路径”或后端拒绝 |
| AQ-009 | 校验 | outputs 为空 | 点击保存 | 前端/后端提示“请至少选择一个返回字段” |
| AQ-010 | 校验 | output.key 空 | 手工改空再保存 | 400，`输出字段 key 不能为空` |
| AQ-011 | 校验 | output.jsonPointer 空 | 手工改空再保存 | 400，`jsonPointer 不能为空` |
| AQ-012 | 功能 | responseRootPointer 有效 | `/current_condition/0` | 解析到指定节点 |
| AQ-013 | 功能 | responseRootPointer 无效 | `/not_found` | 400，`响应中未找到指定结果位置` |
| AQ-014 | 功能 | 相对路径含 queryString | `/path?a=1` | path/query 拆分正确 |
| AQ-015 | 功能 | 绝对路径覆盖 baseUrl | path=`https://...` | 以绝对地址发请求 |
| AQ-016 | 功能 | valueSource=PARAM | query/header 参数来自 args | 按参数注入 |
| AQ-017 | 功能 | valueSource=LITERAL | 固定值透传 | 请求值固定 |

---

## 5. 返回结果映射测试矩阵

| 用例ID | 类型 | 场景 | 输入/操作 | 预期结果 |
|---|---|---|---|---|
| MP-001 | 正向 | 点选字段入映射 | 预览后点选字段 | `field_mapping` 新增记录 |
| MP-002 | 正向 | 全部加入 | 点击“全部加入” | 全字段映射生成 |
| MP-003 | 正向 | 拖拽排序 | 调整顺序 | sortOrder 连续且顺序正确 |
| MP-004 | 正向 | 删除字段 | 删除某输出 | sortOrder 重新归一化 |
| MP-005 | 正向 | 脱敏保存 | maskType=PHONE_LAST4 | 落库值正确 |
| MP-006 | 正向 | 格式化保存 | formatType=DATE_TIME/MONEY_2 | 落库值正确 |
| MP-007 | 回归 | 同名列保留流水线 | 二次保存同列 | displayPipelineJson 尽量保留 |
| MP-008 | 回归 | API 模式不依赖 SQL | queryMode=API | 仅使用 api_config_json |
| MP-009 | 异常 | 重复点选同字段 | 再次点选已加入字段 | 前端提示“已经加入”且不重复 |

---

## 6. 后端自动化执行方案（可直接运行）

### 6.1 最小门槛

- 仅改 API 逻辑时：
  - `.\mvnw.cmd -q "-Dtest=ApiDatasourceSupportTest,ApiQueryConfigServiceTest,AdminDatasourceControllerTest" test`

### 6.2 推荐完整回归

- 后端：
  - `.\mvnw.cmd test`
- 前端（改了 UI 时）：
  - `npm run build`

### 6.3 分层说明

- `ApiDatasourceSupportTest`：鉴权/地址/参数/响应解析校验。
- `ApiQueryConfigServiceTest`：配置 JSON 校验、字段映射生成与保留策略。
- `AdminDatasourceControllerTest`：创建/更新主流程回归（含 name 赋值与字段清理）。

---

## 7. MCP 页面自动化回归步骤（真实 API）

### 7.1 标准回归路径

1. 登录管理端 -> 进入“数据源”。
2. 新建 API 数据源（天气模板）-> 创建。
3. 编辑同数据源 -> 测试连接。
4. 进入“查询定义”-> 新建查询 -> 切 API 可视化。
5. 选模板 -> 预览 -> 点选字段 -> 保存查询。
6. 打开“字段映射”确认排序、字段、脱敏与格式配置项。

### 7.2 抓包证据要求

每个场景至少记录：

- 页面快照（关键 UI 状态）。
- 请求 `reqid`。
- 请求体、响应体。
- 预期 vs 实际。

---

## 8. 本轮已执行的 MCP 自动化结果（样例证据）

### 8.1 数据源链路

- `POST /api/admin/datasources` -> `200`（创建 API 数据源成功，`reqid=42`）。
- `POST /api/admin/datasource-connection/test` -> `200`（连通成功，`reqid=45`）。

### 8.2 API 查询与映射链路

- `POST /api/admin/datasources/3/api-query/preview` -> `200`（预览成功，`reqid=46`）。
- `POST /api/admin/bots/1/queries` -> `200`（保存 API 查询成功，`reqid=47`）。
- `GET /api/admin/queries/2/fields` -> `200`（字段映射可见，`reqid=49`）。

### 8.3 UI 可见结果

- 查询列表出现 `weather_e2e`，模式 `API`，数据源 `天气-e2e`。
- 字段映射弹窗中存在并排序：
  - `weatherDesc`（0）
  - `temp_C`（1）
  - `FeelsLikeC`（2）
  - `humidity`（3）

---

## 9. 覆盖映射（用例 -> 代码入口）

| 能力 | 关键用例 | 代码入口 |
|---|---|---|
| API 数据源创建/编辑 | DS-001~DS-021 | `AdminDatasourceController`、`ApiDatasourceSupport` |
| API 预览与参数推导 | AQ-001~AQ-017 | `AdminDatasourceApiQueryController`、`ApiQueryConfigService`、`ApiDatasourceSupport` |
| 字段映射生成与保留 | MP-001~MP-009 | `ApiQueryConfigService.replaceFieldMappingsFromApi`、字段映射弹窗与 API Builder |

---

## 10. 验收清单

- [ ] 三条主链路均有正向/反向/边界用例。
- [ ] 后端 API 相关自动化测试通过。
- [ ] MCP 页面回归关键路径全部通过，且有 reqid 证据。
- [ ] 历史回归问题（名称丢失、API 字段 NOT NULL）持续被用例覆盖。
- [ ] 文档与代码入口映射可追溯。

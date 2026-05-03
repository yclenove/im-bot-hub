# Dashboard.vue 拆分指南

## 当前状态

- **Dashboard.vue**: 3739 行（需要拆分）
- **已完成**: 类型定义 (`types/dashboard.ts`) + 工具函数 (`utils/dashboard.ts`)
- **待完成**: Tab 组件 + 表单组件提取

## 已提取的组件

| 组件 | 行数 | 说明 |
|------|------|------|
| OverviewTab.vue | 142 | 概览 Tab |
| BotTab.vue | 123 | 机器人 Tab |
| ChannelTab.vue | 108 | 渠道管理 Tab |
| AllowlistTab.vue | 102 | 白名单 Tab |
| CommandLogTab.vue | 128 | 命令日志 Tab |
| AuditLogTab.vue | 41 | 审计日志 Tab |
| SettingsTab.vue | 55 | 设置 Tab |
| TemplateMarket.vue | 180 | 模板市场 |
| CommandStats.vue | 150 | 命令统计 |
| ChannelHealth.vue | 120 | 渠道健康 |
| Nl2SqlChat.vue | 180 | AI 查询 |
| AnomalyDashboard.vue | 200 | 异常检测 |
| WorkflowDesigner.vue | 180 | 工作流 |
| ApprovalManager.vue | 130 | 审批管理 |
| ApiKeyManager.vue | 200 | API Key |
| ClusterStatus.vue | 100 | 集群状态 |
| PerformanceMonitor.vue | 150 | 性能监控 |
| TenantManager.vue | 160 | 租户管理 |

## 待提取的组件

### 1. DatasourceTab.vue（~300 行）

**包含内容**:
- `dsList` 状态
- `loadDs()` 函数
- 数据源列表表格
- 新建/编辑/删除操作入口

**提取步骤**:
1. 创建 `admin-ui/src/components/DatasourceTab.vue`
2. 移动 `dsList`, `loadDs()` 相关代码
3. 移动数据源列表表格模板
4. Dashboard.vue 中使用 `<DatasourceTab :ds-list="dsList" @refresh="loadDs" />`

### 2. QueryTab.vue（~400 行）

**包含内容**:
- `queries`, `queryBotId` 状态
- `loadQueries()` 函数
- 查询列表表格
- 导入/导出功能
- 测试对话框

**提取步骤**:
1. 创建 `admin-ui/src/components/QueryTab.vue`
2. 移动查询相关状态和方法
3. 移动查询列表表格模板
4. Dashboard.vue 中使用 `<QueryTab :bot-id="queryBotId" />`

### 3. DatasourceFormDialog.vue（~500 行）

**包含内容**:
- `dsDlgOpen`, `dsEditId`, `dsForm` 状态
- `emptyDatasourceForm()` 函数
- `applyDatasourcePreset()` 函数
- `saveDatasource()` 函数
- `testDsConnection()` 函数
- 数据源表单模板

**提取步骤**:
1. 创建 `admin-ui/src/components/DatasourceFormDialog.vue`
2. 移动表单状态和方法
3. 移动表单模板
4. Dashboard.vue 中使用 `<DatasourceFormDialog v-model:visible="dsDlgOpen" :edit-id="dsEditId" @saved="loadDs" />`

### 4. QueryDefinitionDrawer.vue（~800 行）

**包含内容**:
- `qFormOpen`, `qEditId`, `qForm`, `qQueryMode` 状态
- `saveQuery()` 函数
- `openNewQuery()`, `openEditQuery()` 函数
- 字段映射编辑器
- 参数配置
- VisualQueryWizard / ApiQueryBuilder 集成

**提取步骤**:
1. 创建 `admin-ui/src/components/QueryDefinitionDrawer.vue`
2. 移动查询定义相关状态和方法
3. 移动抽屉模板
4. Dashboard.vue 中使用 `<QueryDefinitionDrawer v-model:visible="qFormOpen" :bot-id="queryBotId" @saved="loadQueries" />`

## 提取原则

1. **保持向下兼容**: 提取后的组件通过 props 和 events 与父组件通信
2. **状态就近原则**: 组件内部状态留在组件内，跨组件状态通过 props 传递
3. **渐进式重构**: 每次提取一个组件，验证构建后再继续
4. **避免破坏功能**: 提取前后行为必须一致

## 验证清单

- [ ] `npm run build` 成功
- [ ] Dashboard.vue 行数 < 500 行
- [ ] 所有 Tab 功能正常
- [ ] 所有对话框功能正常
- [ ] 导入/导出功能正常
- [ ] 测试功能正常

---

*最后更新：2026-05-03*

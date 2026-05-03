# 技术债清单

> V4 实现过程中留下的技术债，需要逐一修复。

---

## 严重程度：高

### 1. NL2SQL LLM 客户端是 Mock
**文件**: `backend/src/main/java/com/sov/imhub/ai/LlmClient.java`
**问题**: `mockResponse()` 方法返回硬编码 SQL，没有真正调用 LLM API
**影响**: NL2SQL 功能完全不可用
**修复方案**: 实现真正的 OpenAI/Claude API 调用

### 2. SSO 认证是 Stub
**文件**: `backend/src/main/java/com/sov/imhub/security/SsoService.java`
**问题**: `authenticateOAuth2()` 和 `authenticateLDAP()` 返回模拟数据
**影响**: SSO 登录功能不可用
**修复方案**: 实现真正的 OAuth2/LDAP 认证流程

### 3. 工作流条件评估是空实现
**文件**: `backend/src/main/java/com/sov/imhub/workflow/WorkflowEngine.java`
**问题**: `evaluateExpression()` 直接返回 `true`，不评估条件
**影响**: 条件分支功能不可用
**修复方案**: 集成 Spring Expression Language (SpEL)

### 4. 定时任务执行器是空实现
**文件**: `backend/src/main/java/com/sov/imhub/scheduler/ScheduledTaskService.java`
**问题**: `checkAndExecuteWorkflow()` 方法体为空
**影响**: Cron 定时任务不会自动执行
**修复方案**: 实现 Cron 表达式解析和任务触发

---

## 严重程度：中

### 5. 权限服务参数类型错误
**文件**: `backend/src/main/java/com/sov/imhub/security/PermissionService.java`
**问题**: `setPermissions()` 使用 `java.sql.Types.NULL` 作为参数值
**影响**: 设置权限时可能报错
**修复方案**: 改为 `null`

### 6. 租户配额检查缺失
**文件**: `backend/src/main/java/com/sov/imhub/multiTenant/TenantService.java`
**问题**: `incrementUsage()` 不检查配额是否超限
**影响**: 可能超出配额限制
**修复方案**: 先调用 `checkQuota()` 再增加使用量

### 7. 报表生成返回空数组
**文件**: `backend/src/main/java/com/sov/imhub/analytics/ReportService.java`
**问题**: `generateReport()` 方法返回 `new byte[0]`
**影响**: 报表导出功能不可用
**修复方案**: 实现 PDF/Excel 报表生成

### 8. 审批列表未按审批人过滤
**文件**: `backend/src/main/java/com/sov/imhub/workflow/ApprovalService.java`
**问题**: `getPendingApprovals()` 不按 `approverId` 过滤
**影响**: 所有待审批都显示给所有人
**修复方案**: 添加 WHERE 条件过滤

### 9. 缓存条目使用原始类型
**文件**: `backend/src/main/java/com/sov/imhub/performance/PerformanceService.java`
**问题**: `CacheEntry` 使用 raw type `Object`，有 unchecked cast
**影响**: 类型不安全，可能 ClassCastException
**修复方案**: 使用泛型 `CacheEntry<T>`

---

## 严重程度：低

### 10. 集群服务无分布式协调
**文件**: `backend/src/main/java/com/sov/imhub/cluster/ClusterService.java`
**问题**: 心跳和故障检测基于数据库轮询，无真正的分布式协调
**影响**: 多节点部署时可能有竞争条件
**修复方案**: 集成 Redis 或 ZooKeeper 做分布式锁

### 11. 异常检测 SQL 简化
**文件**: `backend/src/main/java/com/sov/imhub/ai/AnomalyDetectionService.java`
**问题**: `getMetricHistory()` 的 SQL 只查询 `t_command_stats_daily`，不支持自定义指标
**影响**: 只能检测命令统计相关异常
**修复方案**: 支持从 Prometheus 或自定义数据源获取指标

### 12. 缺少输入校验
**文件**: 多个 Controller
**问题**: API 端点缺少 `@Valid` 注解和参数校验
**影响**: 可能接收非法输入
**修复方案**: 添加 Jakarta Validation 注解

### 13. 缺少单元测试
**文件**: 所有新增 Service
**问题**: V4 新增的 Service 没有单元测试
**影响**: 无法保证代码质量
**修复方案**: 补充单元测试

---

## 修复优先级

| 优先级 | 编号 | 说明 |
|--------|------|------|
| P0 | 1, 2, 3, 4 | 核心功能不可用，必须修复 |
| P1 | 5, 6, 7, 8, 9 | 功能缺陷，应该修复 |
| P2 | 10, 11, 12, 13 | 代码质量，建议修复 |

---

*最后更新：2026-05-03*

# 技术债清单 V2

> V4 前后端实现后的第二轮扫描结果。

---

## 后端技术债

### HIGH - 必须修复

#### 1. Nl2SqlService.extractSchema() 使用 SHOW TABLES
**文件**: `backend/src/main/java/com/sov/imhub/ai/Nl2SqlService.java`
**问题**: `extractSchema()` 使用 `SHOW TABLES` 获取表结构，不支持指定数据库
**影响**: 多数据库场景下可能获取错误的表结构
**修复**: 使用 `INFORMATION_SCHEMA` 查询指定数据库的表和列

#### 2. AnomalyDetectionService.getMetricHistory() SQL 硬编码
**文件**: `backend/src/main/java/com/sov/imhub/ai/AnomalyDetectionService.java`
**问题**: 指标计算逻辑硬编码在 SQL 中，只支持 3 种指标
**影响**: 无法扩展自定义指标
**修复**: 使用指标注册表，支持动态指标计算

#### 3. WorkflowEngine.executeSteps() 无并发控制
**文件**: `backend/src/main/java/com/sov/imhub/workflow/WorkflowEngine.java`
**问题**: 工作流步骤顺序执行，无并行执行支持
**影响**: 复杂工作流执行效率低
**修复**: 使用 CompletableFuture 支持并行步骤

#### 4. SsoService.authenticateLDAP() 无真正 LDAP 绑定
**文件**: `backend/src/main/java/com/sov/imhub/security/SsoService.java`
**问题**: LDAP 认证只是框架代码，没有真正调用 LDAP bind
**影响**: LDAP 登录功能不可用
**修复**: 使用 JNDI LdapContext 实现真正的 LDAP 绑定认证

### MEDIUM - 应该修复

#### 5. PerformanceService 使用 ConcurrentHashMap 无大小限制
**文件**: `backend/src/main/java/com/sov/imhub/performance/PerformanceService.java`
**问题**: `queryCache` 无大小限制，可能导致内存溢出
**影响**: 长期运行可能 OOM
**修复**: 使用 Caffeine Cache 替代，设置最大条目数

#### 6. ClusterService 心跳无超时清理
**文件**: `backend/src/main/java/com/sov/imhub/cluster/ClusterService.java`
**问题**: 心跳记录只更新不清理，数据库会持续增长
**影响**: 数据库表越来越大
**修复**: 定期清理超过 24 小时的心跳记录

#### 7. ApiGatewayService rateLimitCounters 无过期清理
**文件**: `backend/src/main/java/com/sov/imhub/gateway/ApiGatewayService.java`
**问题**: 限流计数器不会自动清理，内存持续增长
**影响**: 长期运行内存泄漏
**修复**: 使用 Caffeine Cache 带 TTL 替代

#### 8. TenantService.checkQuota() 查询性能
**文件**: `backend/src/main/java/com/sov/imhub/multiTenant/TenantService.java`
**问题**: 每次检查配额都查询数据库
**影响**: 高并发时数据库压力大
**修复**: 使用本地缓存 + 定期同步

#### 9. ComplianceAuditService 缺少数据脱敏
**文件**: `backend/src/main/java/com/sov/imhub/security/ComplianceAuditService.java`
**问题**: 审计日志可能包含敏感信息
**影响**: 合规审计本身可能违反数据保护
**修复**: 在记录审计日志前进行脱敏处理

#### 10. ReportService.generateReport() 返回空数组
**文件**: `backend/src/main/java/com/sov/imhub/analytics/ReportService.java`
**问题**: 报表生成方法返回 `new byte[0]`
**影响**: 报表导出功能不可用
**修复**: 使用 Apache POI 或 iText 生成 PDF/Excel

### LOW - 建议修复

#### 11. 多个 Service 使用 Map<String, Object> 而非 DTO
**文件**: 多个 Service
**问题**: 大量使用 `Map<String, Object>` 作为数据载体
**影响**: 类型不安全，代码可读性差
**修复**: 为每个数据结构定义 DTO 类

#### 12. 缺少 @Transactional 注解
**文件**: 多个 Service
**问题**: 涉及多表操作的方法缺少事务注解
**影响**: 部分操作失败可能导致数据不一致
**修复**: 在涉及多表操作的方法上添加 `@Transactional`

#### 13. 日志格式不统一
**文件**: 多个 Service
**问题**: 部分日志使用字符串拼接，部分使用参数化
**影响**: 日志不规范
**修复**: 统一使用参数化日志 `log.info("msg param={}", param)`

---

## 前端技术债

### HIGH - 必须修复

#### 14. Nl2SqlChat 缺少 SQL 编辑器
**文件**: `admin-ui/src/components/Nl2SqlChat.vue`
**问题**: 生成的 SQL 用 `<pre><code>` 显示，无法编辑
**影响**: 用户无法修改 AI 生成的 SQL
**修复**: 集成 CodeMirror 或 Monaco Editor

#### 15. WorkflowDesigner 步骤配置是空的
**文件**: `admin-ui/src/components/WorkflowDesigner.vue`
**问题**: 步骤只有名称和类型，没有配置表单
**影响**: 无法配置步骤的具体参数
**修复**: 根据步骤类型显示不同的配置表单

#### 16. 组件未集成到 Dashboard
**文件**: 所有新组件
**问题**: 新组件没有添加到 Dashboard.vue 的 Tab 中
**影响**: 用户无法在界面上访问这些功能
**修复**: 在 Dashboard.vue 中添加新的 Tab

### MEDIUM - 应该修复

#### 17. 错误处理模式重复
**文件**: 所有新组件
**问题**: 每个 API 调用都有重复的 try-catch 错误处理
**影响**: 代码重复，维护困难
**修复**: 提取通用的 `useApi` composable

#### 18. 缺少加载状态骨架屏
**文件**: 所有新组件
**问题**: 加载时只显示 loading 状态，没有骨架屏
**影响**: 用户体验不佳
**修复**: 使用 `<el-skeleton>` 组件

#### 19. 表格缺少分页
**文件**: AnomalyDashboard, ApprovalManager, ApiKeyManager
**问题**: 表格数据量大时没有分页
**影响**: 大数据量时性能差
**修复**: 添加 `<el-pagination>` 组件

#### 20. 缺少空状态引导
**文件**: 所有新组件
**问题**: 数据为空时没有引导用户操作
**影响**: 新用户不知道如何开始
**修复**: 使用 `<el-empty>` 组件并添加操作引导

### LOW - 建议修复

#### 21. 样式未使用 CSS 变量
**文件**: 所有新组件
**问题**: 颜色值硬编码，不支持主题切换
**影响**: 无法实现暗色主题
**修复**: 使用 Element Plus CSS 变量

#### 22. 缺少国际化
**文件**: 所有新组件
**问题**: 文本硬编码为中文
**影响**: 不支持多语言
**修复**: 使用 vue-i18n

#### 23. 组件命名不一致
**文件**: 部分组件
**问题**: 部分组件使用 PascalCase，部分使用 kebab-case
**影响**: 命名不规范
**修复**: 统一使用 PascalCase

---

## 修复优先级

| 优先级 | 编号 | 说明 |
|--------|------|------|
| P0 | 1, 2, 3, 4 | 核心功能缺陷 |
| P1 | 5-10, 14-16 | 功能缺陷 + 用户体验 |
| P2 | 11-13, 17-23 | 代码质量 + 规范 |

---

*最后更新：2026-05-03*

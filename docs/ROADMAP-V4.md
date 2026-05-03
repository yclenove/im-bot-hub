# im-bot-hub V4 路线图 / Roadmap

> **定位**：从「智能 IM 数据服务平台」升级为「AI 驱动的企业级 IM 运维中枢」
> **核心理念**：数据驱动 → AI 驱动，被动查询 → 主动预测，单点工具 → 企业平台

---

## 一、V4 总览

### 1.1 版本主题

| 主题 | 说明 |
|------|------|
| **AI 智能** | 自然语言查询、智能推荐、异常预测、自动修复 |
| **工作流引擎** | 多步骤查询、条件分支、定时任务、审批流 |
| **企业级安全** | SSO/LDAP、审计合规、数据脱敏、权限矩阵 |
| **开发者生态** | SDK、CLI、IDE 插件、API 网关 |

### 1.2 版本目标

1. **AI 赋能**：自然语言查询，智能异常检测，自动根因分析
2. **流程自动化**：复杂运维场景一键执行，审批流集成
3. **企业合规**：等保三级、GDPR、SOC2 合规支持
4. **生态开放**：完整 SDK、CLI、IDE 插件，开发者友好

---

## 二、功能规划（按优先级）

### P0 - AI 智能增强

#### 2.1 自然语言查询 (NL2SQL)

**目标**：用户用自然语言描述需求，AI 自动生成 SQL 并执行

| 功能 | 说明 |
|------|------|
| NL2SQL 引擎 | 集成 LLM（GPT-4/Claude）将自然语言转为 SQL |
| 上下文感知 | 理解表结构、字段含义、业务术语 |
| 查询确认 | 生成 SQL 后让用户确认再执行 |
| 学习优化 | 记录用户反馈，持续优化生成质量 |
| 多语言支持 | 支持中英文自然语言输入 |

**技术方案**：
```java
@Service
public class Nl2SqlService {

    private final LlmClient llmClient;
    private final SchemaExtractor schemaExtractor;

    /**
     * 自然语言转 SQL。
     *
     * @param question 用户问题
     * @param datasourceId 数据源 ID
     * @return 生成的 SQL 和置信度
     */
    public Nl2SqlResult convert(String question, Long datasourceId) {
        // 1. 提取数据源 schema
        String schema = schemaExtractor.extract(datasourceId);

        // 2. 构建 prompt
        String prompt = buildPrompt(question, schema);

        // 3. 调用 LLM
        String sql = llmClient.complete(prompt);

        // 4. 验证 SQL 语法
        validateSql(sql);

        return new Nl2SqlResult(sql, calculateConfidence(question, sql));
    }

    private String buildPrompt(String question, String schema) {
        return """
            你是一个 SQL 专家。根据用户的自然语言问题和数据库 schema，生成对应的 SQL 查询。

            数据库 Schema:
            %s

            用户问题: %s

            要求:
            1. 只返回 SQL 语句，不要解释
            2. 使用参数化查询（:param 格式）
            3. 确保 SQL 语法正确
            4. 优先使用索引字段
            """.formatted(schema, question);
    }
}
```

**前端界面**：
- 聊天式界面：用户输入问题，AI 返回 SQL + 结果
- SQL 编辑器：可手动修改生成的 SQL
- 历史记录：保存查询历史，支持收藏

---

#### 2.2 智能异常检测

**目标**：AI 自动检测异常模式，提前预警

| 功能 | 说明 |
|------|------|
| 时序异常检测 | 基于历史数据检测异常波动 |
| 模式识别 | 识别周期性、趋势性异常 |
| 根因分析 | 自动分析异常原因 |
| 关联分析 | 多指标关联，定位根本原因 |

**技术方案**：
```java
@Service
public class AnomalyDetectionService {

    /**
     * 检测时序数据异常。
     *
     * @param metricName 指标名称
     * @param timeRange 时间范围
     * @return 异常列表
     */
    public List<Anomaly> detect(String metricName, TimeRange timeRange) {
        // 1. 获取历史数据
        List<DataPoint> history = getHistory(metricName, timeRange);

        // 2. 计算基线（移动平均 + 标准差）
        Baseline baseline = calculateBaseline(history);

        // 3. 检测异常点
        List<Anomaly> anomalies = new ArrayList<>();
        for (DataPoint point : history) {
            double zscore = calculateZScore(point, baseline);
            if (Math.abs(zscore) > 3) { // 3-sigma 规则
                anomalies.add(new Anomaly(
                    point.getTimestamp(),
                    point.getValue(),
                    baseline.getMean(),
                    zscore,
                    classifyAnomaly(point, baseline)
                ));
            }
        }

        return anomalies;
    }

    /**
     * 根因分析。
     */
    public RootCauseAnalysis analyzeRootCause(Anomaly anomaly) {
        // 1. 获取异常时间点的所有指标
        Map<String, List<DataPoint>> metrics = getAllMetrics(anomaly.getTimestamp());

        // 2. 计算指标相关性
        Map<String, Double> correlations = calculateCorrelations(metrics);

        // 3. 找出最相关的指标
        List<String> relatedMetrics = correlations.entrySet().stream()
            .filter(e -> Math.abs(e.getValue()) > 0.8)
            .map(Map.Entry::getKey)
            .toList();

        return new RootCauseAnalysis(anomaly, relatedMetrics, correlations);
    }
}
```

---

#### 2.3 智能推荐

**目标**：基于用户行为和上下文，智能推荐查询和配置

| 功能 | 说明 |
|------|------|
| 查询推荐 | 根据用户历史推荐相关查询 |
| 参数补全 | 智能补全查询参数 |
| 优化建议 | 推荐索引、查询优化 |
| 模板推荐 | 根据业务场景推荐模板 |

---

### P1 - 工作流引擎

#### 2.4 多步骤工作流

**目标**：支持复杂的多步骤运维流程

| 功能 | 说明 |
|------|------|
| 流程设计器 | 可视化拖拽设计工作流 |
| 条件分支 | 支持 if/else 条件判断 |
| 并行执行 | 支持并行任务执行 |
| 变量传递 | 步骤间数据传递 |
| 错误处理 | 异常捕获和重试机制 |

**技术方案**：
```java
/**
 * 工作流定义。
 */
@Data
public class WorkflowDefinition {
    private String name;
    private String description;
    private List<WorkflowStep> steps;
    private Map<String, Object> variables;
    private TriggerConfig trigger;
}

/**
 * 工作流步骤。
 */
@Data
public class WorkflowStep {
    private String id;
    private String name;
    private StepType type; // QUERY, CONDITION, DELAY, NOTIFICATION, APPROVAL
    private Map<String, Object> config;
    private List<String> nextSteps; // 下一步骤 ID
    private String conditionExpression; // 条件表达式
    private RetryConfig retry;
}

/**
 * 工作流执行引擎。
 */
@Service
public class WorkflowEngine {

    /**
     * 执行工作流。
     */
    public WorkflowExecution execute(WorkflowDefinition definition, Map<String, Object> input) {
        WorkflowExecution execution = new WorkflowExecution(definition);
        execution.setVariables(input);

        // 从第一个步骤开始执行
        executeStep(definition.getSteps().get(0), execution);

        return execution;
    }

    private void executeStep(WorkflowStep step, WorkflowExecution execution) {
        switch (step.getType()) {
            case QUERY -> executeQueryStep(step, execution);
            case CONDITION -> evaluateCondition(step, execution);
            case DELAY -> scheduleDelay(step, execution);
            case NOTIFICATION -> sendNotification(step, execution);
            case APPROVAL -> requestApproval(step, execution);
        }
    }
}
```

**应用场景**：
- **故障排查流程**：自动执行一系列诊断查询，生成报告
- **容量规划**：定期检查资源使用，预测扩容需求
- **合规检查**：定期执行安全检查，生成合规报告

---

#### 2.5 定时任务增强

**目标**：支持复杂的定时任务调度

| 功能 | 说明 |
|------|------|
| Cron 表达式 | 支持标准 Cron 调度 |
| 依赖任务 | 任务间依赖关系 |
| 任务队列 | 任务优先级和队列管理 |
| 执行历史 | 完整的执行记录和日志 |
| 失败重试 | 自动重试和告警 |

---

#### 2.6 审批流集成

**目标**：敏感操作需要审批

| 功能 | 说明 |
|------|------|
| 审批规则 | 配置哪些操作需要审批 |
| 审批人配置 | 支持多级审批、会签、或签 |
| 审批通知 | 飞书/钉钉/邮件通知 |
| 审批历史 | 完整的审批记录 |

---

### P2 - 企业级安全

#### 2.7 SSO/LDAP 集成

**目标**：支持企业统一身份认证

| 功能 | 说明 |
|------|------|
| OAuth2 集成 | 支持 Google、GitHub、企业微信等 |
| LDAP 集成 | 支持 Active Directory、OpenLDAP |
| SAML 集成 | 支持企业 SSO |
| 多因素认证 | 支持 TOTP、短信验证 |

**技术方案**：
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/")
            )
            .ldapAuthentication(ldap -> ldap
                .userDnPatterns("uid={0},ou=people")
                .groupSearchBase("ou=groups")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
            );
        return http.build();
    }
}
```

---

#### 2.8 数据脱敏

**目标**：敏感数据自动脱敏

| 功能 | 说明 |
|------|------|
| 字段级脱敏 | 手机号、邮箱、身份证等 |
| 动态脱敏 | 根据用户角色动态脱敏 |
| 脱敏规则 | 可配置脱敏规则 |
| 审计日志 | 记录谁查看了原始数据 |

---

#### 2.9 权限矩阵

**目标**：细粒度权限控制

| 功能 | 说明 |
|------|------|
| 资源权限 | 控制对机器人、渠道、查询的访问 |
| 操作权限 | 控制查看、编辑、删除等操作 |
| 数据权限 | 控制可见的数据范围 |
| 权限继承 | 支持角色继承和权限组合 |

---

#### 2.10 合规审计

**目标**：满足等保三级、GDPR 等合规要求

| 功能 | 说明 |
|------|------|
| 操作审计 | 记录所有用户操作 |
| 数据审计 | 记录数据访问和变更 |
| 合规报告 | 自动生成合规报告 |
| 数据保留 | 配置数据保留策略 |

---

### P3 - 开发者生态

#### 2.11 完整 SDK

**目标**：提供多语言 SDK，方便集成

| 语言 | 说明 |
|------|------|
| Java SDK | Spring Boot Starter |
| Python SDK | pip 安装 |
| Go SDK | go get 安装 |
| Node.js SDK | npm 安装 |

**Java SDK 示例**：
```java
// 依赖
// <dependency>
//     <groupId>com.sov</groupId>
//     <artifactId>im-bot-hub-sdk</artifactId>
//     <version>4.0.0</version>
// </dependency>

// 使用
ImBotHubClient client = ImBotHubClient.builder()
    .baseUrl("https://your-domain.com")
    .apiKey("your-api-key")
    .build();

// 执行查询
QueryResult result = client.query()
    .botId(1L)
    .command("order")
    .args("ORD2024001")
    .execute();

// 获取结果
List<Map<String, Object>> rows = result.getRows();
```

---

#### 2.12 CLI 工具增强

**目标**：完整的命令行工具

```bash
# 安装
npm install -g im-bot-hub-cli

# 登录
im-hub login https://your-domain.com

# 机器人管理
im-hub bot list
im-hub bot create --name "My Bot"
im-hub bot delete --id 1

# 渠道管理
im-hub channel list --bot-id 1
im-hub channel create --bot-id 1 --platform TELEGRAM --token "xxx"

# 查询管理
im-hub query list --bot-id 1
im-hub query test --id 1 --args "Shanghai"

# 配置管理
im-hub config export --output config.json
im-hub config import --file config.json

# 监控
im-hub monitor status
im-hub monitor alerts

# 工作流
im-hub workflow list
im-hub workflow run --id 1 --var "city=Shanghai"
```

---

#### 2.13 IDE 插件

**目标**：主流 IDE 集成

| IDE | 功能 |
|------|------|
| VS Code | 查询预览、语法高亮、自动补全 |
| IntelliJ | 代码生成、调试支持 |
| WebStorm | 前端组件预览 |

---

#### 2.14 API 网关

**目标**：统一 API 入口，支持限流、认证、监控

| 功能 | 说明 |
|------|------|
| 路由管理 | 动态路由配置 |
| 限流熔断 | 细粒度限流和熔断 |
| 协议转换 | REST/GraphQL/gRPC 转换 |
| 监控统计 | API 调用统计和监控 |

---

### P4 - 高级功能

#### 2.15 多租户支持

**目标**：支持 SaaS 多租户部署

| 功能 | 说明 |
|------|------|
| 租户隔离 | 数据完全隔离 |
| 资源配额 | 按租户分配资源 |
| 计费管理 | 按使用量计费 |
| 租户管理 | 租户自助管理 |

---

#### 2.16 高可用部署

**目标**：支持生产级高可用部署

| 功能 | 说明 |
|------|------|
| 集群部署 | 支持多节点集群 |
| 负载均衡 | 自动负载均衡 |
| 故障转移 | 自动故障转移 |
| 数据同步 | 多节点数据同步 |

---

#### 2.17 性能优化

**目标**：支持大规模数据和高并发

| 功能 | 说明 |
|------|------|
| 查询缓存 | 多级缓存策略 |
| 读写分离 | 数据库读写分离 |
| 分库分表 | 大数据量分片 |
| 异步处理 | 长耗时任务异步执行 |

---

#### 2.18 可视化报表

**目标**：丰富的数据可视化

| 功能 | 说明 |
|------|------|
| 报表设计器 | 拖拽式报表设计 |
| 图表类型 | 折线图、柱状图、饼图、热力图等 |
| 数据大屏 | 实时数据大屏 |
| 定时报表 | 定时生成和发送报表 |

---

## 三、技术架构演进

### 3.1 架构升级

```
V3 架构：
┌─────────────┐
│  Admin UI   │
└──────┬──────┘
       │
┌──────▼──────┐
│  Spring Boot │
│  (单体)     │
└──────┬──────┘
       │
┌──────▼──────┐
│ MySQL+Redis │
└─────────────┘

V4 架构：
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Admin UI   │  │  CLI / SDK  │  │  IDE 插件   │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │               │               │
       └───────────────┼───────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│              API Gateway (Kong)              │
│    限流 · 认证 · 监控 · 协议转换            │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│              Service Mesh (Istio)            │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│              微服务集群                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │ Core    │ │ AI      │ │Workflow │       │
│  │ Service │ │ Service │ │ Service │       │
│  └─────────┘ └─────────┘ └─────────┘       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │ Auth    │ │Analytics│ │ Gateway │       │
│  │ Service │ │ Service │ │ Service │       │
│  └─────────┘ └─────────┘ └─────────┘       │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│  MySQL  │  Redis  │  ES  │  MQ  │  MinIO   │
│ (配置)  │ (缓存)  │(日志)│(异步)│ (文件)   │
└─────────────────────────────────────────────┘
```

### 3.2 技术选型

| 组件 | V3 | V4 | 原因 |
|------|----|----|------|
| 网关 | 无 | Kong | 业界标准、插件丰富 |
| 服务网格 | 无 | Istio | 流量管理、可观测性 |
| AI 引擎 | 无 | LangChain | LLM 编排、RAG 支持 |
| 工作流 | 无 | Temporal | 可靠的工作流引擎 |
| 搜索引擎 | Elasticsearch | Elasticsearch + Milvus | 向量搜索支持 |
| 对象存储 | 无 | MinIO | 文件存储 |
| 消息队列 | RabbitMQ | Kafka | 高吞吐、流处理 |

---

## 四、实施计划

### 4.1 阶段划分

| 阶段 | 主题 | 时间 | 核心功能 |
|------|------|------|---------|
| V4.1 | AI 智能 | 6 周 | NL2SQL、异常检测、智能推荐 |
| V4.2 | 工作流 | 5 周 | 工作流引擎、定时任务、审批流 |
| V4.3 | 企业安全 | 4 周 | SSO/LDAP、数据脱敏、权限矩阵 |
| V4.4 | 开发者生态 | 5 周 | SDK、CLI、IDE 插件、API 网关 |
| V4.5 | 高级功能 | 6 周 | 多租户、高可用、性能优化、可视化 |

### 4.2 里程碑

```
2026 Q3 (7-9月)
├── V4.1-alpha：NL2SQL 引擎
├── V4.1-beta：异常检测 + 智能推荐
└── V4.2-alpha：工作流设计器

2026 Q4 (10-12月)
├── V4.2-beta：工作流执行引擎
├── V4.3-alpha：SSO/LDAP 集成
├── V4.3-beta：数据脱敏 + 权限矩阵
└── V4.4-alpha：Java/Python SDK

2027 Q1 (1-3月)
├── V4.4-beta：CLI + IDE 插件
├── V4.5-alpha：多租户支持
└── V4.5-beta：高可用部署
```

---

## 五、数据库变更预估

### 5.1 新增表

| 表名 | 用途 | 阶段 |
|------|------|------|
| t_nl2sql_history | NL2SQL 查询历史 | V4.1 |
| t_anomaly_detection | 异常检测规则 | V4.1 |
| t_anomaly_log | 异常检测日志 | V4.1 |
| t_workflow_definition | 工作流定义 | V4.2 |
| t_workflow_execution | 工作流执行记录 | V4.2 |
| t_workflow_step_log | 步骤执行日志 | V4.2 |
| t_approval_rule | 审批规则 | V4.2 |
| t_approval_log | 审批记录 | V4.2 |
| t_sso_config | SSO 配置 | V4.3 |
| t_desensitization_rule | 脱敏规则 | V4.3 |
| t_permission_matrix | 权限矩阵 | V4.3 |
| t_tenant | 租户信息 | V4.5 |
| t_tenant_quota | 租户配额 | V4.5 |

### 5.2 变更表

| 表名 | 变更 | 阶段 |
|------|------|------|
| t_admin_user | 新增 sso_id, mfa_enabled | V4.3 |
| t_query_definition | 新增 ai_generated, confidence | V4.1 |
| t_command_log | 新增 workflow_execution_id | V4.2 |

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AI 准确率不足 | 高 | 人工确认机制、持续优化 |
| 微服务复杂度 | 高 | 渐进式拆分、Service Mesh |
| 性能瓶颈 | 中 | 缓存优化、异步处理 |
| 安全合规 | 高 | 安全审计、渗透测试 |

---

## 七、成功指标

| 指标 | 目标 | 衡量方式 |
|------|------|---------|
| NL2SQL 准确率 | > 85% | 人工评估 |
| 异常检测召回率 | > 90% | 历史数据回测 |
| 工作流执行成功率 | > 99% | 执行日志 |
| API 响应时间 | < 100ms (P99) | 监控系统 |
| 系统可用性 | > 99.9% | 监控系统 |

---

*本文件最后更新：2026-05-03*
*维护者：im-bot-hub 产品团队*

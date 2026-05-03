# im-bot-hub V3 路线图 / Roadmap

> **定位**：从「查询机器人配置中心」升级为「智能 IM 数据服务平台」
> **核心理念**：配置驱动 → 数据驱动，被动查询 → 主动服务

---

## 一、V3 总览

### 1.1 版本主题

| 主题 | 说明 |
|------|------|
| **数据智能** | 查询结果缓存、定时推送、智能推荐 |
| **用户体验** | 多语言、主题、移动端、角色权限 |
| **运维可观测** | 实时监控、告警、健康检查、性能分析 |
| **平台生态** | 插件系统、SDK、API 开放平台 |

### 1.2 版本目标

1. **降低使用门槛**：非技术人员 5 分钟内完成首个查询配置
2. **提升数据价值**：从「查一下」到「持续监控」
3. **增强可观测性**：实时掌握系统健康状态
4. **开放生态**：支持第三方扩展和集成

---

## 二、功能规划（按优先级）

### P0 - 核心价值增强

#### 2.1 查询模板市场

**目标**：一键导入常用查询，降低配置成本

| 功能 | 说明 |
|------|------|
| 预置模板库 | 订单查询、库存查询、用户查询、日志查询等 |
| 模板分类 | 按业务场景（电商、SaaS、运维）分类 |
| 一键导入 | 选择模板 → 选择数据源 → 自动配置 |
| 自定义模板 | 用户可将配置保存为模板并分享 |
| 模板版本 | 模板支持版本管理，可回滚 |

**技术方案**：
```sql
-- 新增表：查询模板
CREATE TABLE t_query_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description TEXT,
    config_json TEXT NOT NULL,  -- 完整查询配置
    version INT NOT NULL DEFAULT 1,
    author VARCHAR(64),
    downloads INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**前端界面**：
- 模板市场 Tab（卡片式展示）
- 模板详情（预览、导入、评分）
- 我的模板（创建、编辑、发布）

---

#### 2.2 命令使用统计

**目标**：多维度分析命令使用情况，优化查询配置

| 维度 | 说明 |
|------|------|
| 命令维度 | 热门命令排行、成功率趋势 |
| 用户维度 | 活跃用户排行、使用偏好 |
| 平台维度 | 各平台使用量对比 |
| 时间维度 | 按小时/天/周/月统计 |
| 性能维度 | 平均响应时间、慢查询 Top10 |

**技术方案**：
```sql
-- 新增表：统计快照（每日聚合）
CREATE TABLE t_command_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL,
    bot_id BIGINT NOT NULL,
    platform VARCHAR(32),
    command VARCHAR(64),
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    avg_duration_ms INT DEFAULT 0,
    unique_users INT DEFAULT 0,
    UNIQUE KEY uk_date_bot_cmd (stat_date, bot_id, command, platform)
);
```

**前端界面**：
- 概览仪表盘（实时数据卡片 + 趋势图）
- 详细报表（可导出 CSV）
- 告警配置（成功率低于阈值告警）

---

#### 2.3 Channel 健康检查

**目标**：实时监控各渠道健康状态，快速定位问题

| 功能 | 说明 |
|------|------|
| Webhook 状态 | 各平台 Webhook 注册状态、最后同步时间 |
| 连通性监控 | 定时检测各渠道连通性（每 5 分钟） |
| 错误统计 | 最近 24 小时错误数、错误类型分布 |
| 自动恢复 | 检测到异常后自动尝试重新注册 Webhook |

**技术方案**：
```java
@Scheduled(fixedRate = 300_000) // 每 5 分钟
public void healthCheck() {
    List<BotChannelEntity> channels = botChannelMapper.selectList(
        new LambdaQueryWrapper<BotChannelEntity>()
            .eq(BotChannelEntity::getEnabled, true));
    
    for (BotChannelEntity channel : channels) {
        HealthStatus status = platformHealthChecker.check(channel);
        healthStatusCache.put(channel.getId(), status);
        
        if (status.isUnhealthy()) {
            alertService.sendAlert(channel, status);
        }
    }
}
```

**前端界面**：
- 渠道健康 Tab（状态卡片 + 历史趋势）
- 告警配置（邮件、Webhook 通知）
- 一键修复（重新注册 Webhook）

---

### P1 - 用户体验提升

#### 2.4 多语言支持 (i18n)

**目标**：支持中英文界面，查询结果可配置语言

| 功能 | 说明 |
|------|------|
| 界面语言 | 管理端支持中英文切换 |
| 机器人语言 | 各机器人可配置回复语言 |
| 字段映射语言 | 同一查询可配置多语言标签 |
| 错误提示语言 | 根据用户语言返回对应提示 |

**技术方案**：
```java
// 字段映射支持多语言
public class FieldMappingEntity {
    private String label;           // 默认标签
    private String labelEn;         // 英文标签
    private String labelJson;       // {"zh":"城市","en":"City"}
}

// 查询结果渲染时根据语言选择标签
public String renderLabel(FieldMappingEntity fm, Locale locale) {
    if (locale.equals(Locale.ENGLISH) && fm.getLabelEn() != null) {
        return fm.getLabelEn();
    }
    return fm.getLabel();
}
```

---

#### 2.5 暗色/亮色主题

**目标**：支持暗色主题，减少视觉疲劳

| 功能 | 说明 |
|------|------|
| 主题切换 | 管理端右上角一键切换 |
| 跟随系统 | 自动跟随系统主题设置 |
| 自定义主题 | 支持自定义主题色 |

**技术方案**：
```css
/* CSS 变量实现主题切换 */
:root {
  --admin-bg: #0b0d11;
  --admin-card-bg: #141720;
  --admin-text: #e6e8ee;
  --admin-border: #1e2230;
}

[data-theme="light"] {
  --admin-bg: #f5f7fa;
  --admin-card-bg: #ffffff;
  --admin-text: #303133;
  --admin-border: #dcdfe6;
}
```

---

#### 2.6 移动端响应式

**目标**：管理端在手机/平板上可用

| 功能 | 说明 |
|------|------|
| 响应式布局 | 卡片自动换行、表格横向滚动 |
| 移动端菜单 | 底部 Tab 导航 |
| 触摸优化 | 按钮加大、间距增加 |

---

#### 2.7 用户角色权限

**目标**：精细化权限控制，支持团队协作

| 角色 | 权限 |
|------|------|
| 超级管理员 | 所有权限 |
| 管理员 | 管理 Bot、Channel、Query、Datasource |
| 操作员 | 查看日志、测试查询、启用/禁用 |
| 只读用户 | 只能查看配置和日志 |

**技术方案**：
```sql
-- 新增表：用户
CREATE TABLE t_admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
    enabled TINYINT(1) DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### P2 - 运维可观测

#### 2.8 实时监控仪表盘

**目标**：一屏掌握系统全貌

| 指标 | 说明 |
|------|------|
| 请求量 | 实时 QPS、今日总量 |
| 成功率 | 实时成功率、失败原因分布 |
| 响应时间 | P50/P90/P99 响应时间 |
| 活跃用户 | 当前在线用户数、今日活跃用户 |
| 系统资源 | CPU、内存、数据库连接池 |

**技术方案**：
```java
// 指标采集
@Component
public class MetricsCollector {
    private final MeterRegistry meterRegistry;
    
    public void recordCommand(String platform, String command, 
                              boolean success, long durationMs) {
        meterRegistry.counter("command.total",
            "platform", platform,
            "command", command,
            "success", String.valueOf(success))
            .increment();
        
        meterRegistry.timer("command.duration",
            "platform", platform,
            "command", command)
            .record(Duration.ofMillis(durationMs));
    }
}
```

---

#### 2.9 告警系统

**目标**：异常自动通知，快速响应

| 告警类型 | 触发条件 |
|---------|---------|
| 成功率告警 | 成功率低于 90%（可配置） |
| 响应时间告警 | P95 响应时间超过 5 秒 |
| 渠道异常 | Webhook 失败连续 3 次 |
| 系统异常 | 数据库连接失败、内存不足 |

**技术方案**：
```java
@Service
public class AlertService {
    public void sendAlert(AlertType type, String message, 
                          Map<String, String> context) {
        // 1. 记录告警日志
        alertLogMapper.insert(new AlertLog(type, message, context));
        
        // 2. 发送通知（邮件、Webhook、飞书群机器人）
        if (alertConfig.isEmailEnabled()) {
            emailService.send(alertConfig.getEmailTo(), 
                "[" + type + "] " + message);
        }
        
        if (alertConfig.isWebhookEnabled()) {
            webhookService.post(alertConfig.getWebhookUrl(), 
                buildAlertPayload(type, message, context));
        }
    }
}
```

---

#### 2.10 慢查询分析

**目标**：识别性能瓶颈，优化查询配置

| 功能 | 说明 |
|------|------|
| 慢查询列表 | 按执行时间排序的查询列表 |
| 执行计划 | 数据库查询自动 EXPLAIN |
| 索引建议 | 基于查询模式推荐索引 |
| 趋势分析 | 慢查询数量趋势、优化效果对比 |

---

### P3 - 平台生态

#### 2.11 插件系统

**目标**：支持第三方扩展，构建生态

| 插件类型 | 说明 |
|---------|------|
| 数据源插件 | 支持更多数据库（PostgreSQL、MongoDB、Redis） |
| 平台插件 | 支持更多 IM 平台（企业微信、钉钉） |
| 渲染插件 | 自定义消息格式（Markdown、卡片、图片） |
| 认证插件 | 集成企业 SSO（OAuth2、LDAP、SAML） |

**技术方案**：
```java
// 插件接口
public interface DatasourcePlugin {
    String getType();  // "POSTGRESQL", "MONGODB", etc.
    Connection connect(DatasourceConfig config);
    List<Map<String, Object>> query(Connection conn, 
                                     String sql, 
                                     Map<String, Object> params);
}

// 插件注册
@Component
public class PluginRegistry {
    private final Map<String, DatasourcePlugin> plugins = new HashMap<>();
    
    @Autowired
    public PluginRegistry(List<DatasourcePlugin> pluginList) {
        for (DatasourcePlugin plugin : pluginList) {
            plugins.put(plugin.getType(), plugin);
        }
    }
}
```

---

#### 2.12 API 开放平台

**目标**：提供 RESTful API，支持第三方集成

| API | 说明 |
|-----|------|
| 查询执行 API | 通过 API 执行查询 |
| 配置管理 API | 通过 API 管理配置 |
| Webhook 推送 | 查询结果推送到指定 URL |
| SDK | Java/Python/Go SDK |

---

#### 2.13 CLI 工具

**目标**：命令行配置管理，支持自动化

```bash
# 安装
npm install -g im-bot-hub-cli

# 登录
im-hub login https://your-domain.com

# 导出配置
im-hub export --output config.json

# 导入配置
im-hub import --file config.json

# 测试查询
im-hub test-query --query-id 1 --args "Shanghai"
```

---

## 三、技术架构演进

### 3.1 架构升级

```
V2 架构：
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
│    MySQL    │
└─────────────┘

V3 架构：
┌─────────────┐  ┌─────────────┐
│  Admin UI   │  │  CLI / SDK  │
└──────┬──────┘  └──────┬──────┘
       │               │
       └───────┬───────┘
               │
┌──────────────▼──────────────┐
│         API Gateway         │
│    (限流、认证、监控)        │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│      Spring Boot App        │
│  ┌─────────┬─────────────┐  │
│  │ Service │ Plugin Mgr  │  │
│  └─────────┴─────────────┘  │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│  MySQL  │  Redis  │  MQ     │
│ (配置)  │ (缓存)  │ (异步)  │
└─────────────────────────────┘
```

### 3.2 技术选型

| 组件 | V2 | V3 | 原因 |
|------|----|----|------|
| 缓存 | Caffeine (本地) | Redis | 分布式支持、持久化 |
| 消息队列 | 无 | RabbitMQ | 异步任务、告警通知 |
| 监控 | 无 | Prometheus + Grafana | 业界标准、可视化 |
| 日志 | SLF4J + 文件 | ELK Stack | 集中日志、全文检索 |
| 配置中心 | application.yml | Nacos | 动态配置、多环境 |

---

## 四、实施计划

### 4.1 阶段划分

| 阶段 | 主题 | 时间 | 核心功能 |
|------|------|------|---------|
| V3.1 | 数据智能 | 4 周 | 查询模板市场、命令统计、Channel 健康检查 |
| V3.2 | 用户体验 | 3 周 | i18n、主题、移动端、角色权限 |
| V3.3 | 运维可观测 | 3 周 | 监控仪表盘、告警系统、慢查询分析 |
| V3.4 | 平台生态 | 4 周 | 插件系统、API 开放平台、CLI 工具 |

### 4.2 里程碑

```
2026 Q2 (5-6月)
├── V3.1-alpha：查询模板市场
├── V3.1-beta：命令统计 + Channel 健康检查
└── V3.1-rc：测试 + 文档

2026 Q3 (7-9月)
├── V3.2-alpha：i18n + 主题
├── V3.2-beta：移动端 + 角色权限
├── V3.3-alpha：监控仪表盘
└── V3.3-beta：告警系统

2026 Q4 (10-12月)
├── V3.4-alpha：插件系统
├── V3.4-beta：API 开放平台
└── V3.4-rc：CLI 工具 + SDK
```

---

## 五、数据库变更预估

### 5.1 新增表

| 表名 | 用途 | 阶段 |
|------|------|------|
| t_query_template | 查询模板 | V3.1 |
| t_command_stats_daily | 命令统计（每日聚合） | V3.1 |
| t_channel_health_log | 渠道健康日志 | V3.1 |
| t_admin_user | 管理用户 | V3.2 |
| t_alert_config | 告警配置 | V3.3 |
| t_alert_log | 告警日志 | V3.3 |
| t_plugin | 插件注册 | V3.4 |
| t_api_key | API Key 管理 | V3.4 |

### 5.2 变更表

| 表名 | 变更 | 阶段 |
|------|------|------|
| t_field_mapping | 新增 label_en, label_json | V3.2 |
| t_bot | 新增 language, theme | V3.2 |
| t_query_definition | 新增 cache_ttl_sec | V3.1 |

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 数据库迁移复杂 | 中 | 提前设计迁移脚本，灰度发布 |
| 前端重构工作量大 | 中 | 组件化拆分，渐进式迁移 |
| 插件系统设计复杂 | 高 | 参考成熟方案（如 Jenkins Plugin） |
| 性能影响 | 中 | Redis 缓存、异步处理 |

---

## 七、成功指标

| 指标 | 目标 | 衡量方式 |
|------|------|---------|
| 配置时间 | < 5 分钟 | 新用户首次配置耗时 |
| 查询成功率 | > 99% | 命令统计 |
| 平均响应时间 | < 2 秒 | 监控仪表盘 |
| 用户满意度 | > 4.5/5 | 用户反馈 |
| 平台覆盖率 | > 90% | 支持的 IM 平台数 |

---

*本文件最后更新：2026-05-03*
*维护者：im-bot-hub 产品团队*

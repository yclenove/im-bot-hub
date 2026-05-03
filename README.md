# IM Bot Hub

> 通用 IM 查询机器人配置中心 — AI 驱动的企业级 IM 运维中枢

[![Build Status](https://github.com/yclenove/im-bot-hub/actions/workflows/ci.yml/badge.svg)](https://github.com/yclenove/im-bot-hub/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 简介

**IM Bot Hub** 是一个通用 IM 查询机器人配置中心，支持 **Telegram / 飞书 / 钉钉 / 企业微信 / Slack / Discord** 六大平台。

通过 **机器人 → 渠道 → 数据源 → 查询定义** 的配置流程，即可在多个 IM 平台实现数据查询。

### 核心特性

- 🤖 **多平台支持**：6 大 IM 平台统一接入
- 🔍 **多模式查询**：SQL / 向导 / API 三种查询模式
- 🧠 **AI 智能**：NL2SQL 自然语言查询、异常检测、智能推荐
- ⚙️ **工作流引擎**：多步骤流程、条件分支、定时任务、审批流
- 🔐 **企业级安全**：SSO/LDAP、数据脱敏、权限矩阵、合规审计
- 📊 **运维监控**：实时指标、告警系统、性能分析、集群状态
- 🏢 **多租户**：租户管理、配额控制、资源隔离

---

## 架构

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Telegram   │  │    飞书      │  │  Slack/Discord│
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │               │               │
       └───────────────┼───────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│              IM Bot Hub Backend              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │   AI    │ │ Workflow│ │ Security│       │
│  │ Service │ │ Engine  │ │ Service │       │
│  └─────────┘ └─────────┘ └─────────┘       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │ Gateway │ │Analytics│ │ Tenant  │       │
│  │ Service │ │ Service │ │ Service │       │
│  └─────────┘ └─────────┘ └─────────┘       │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│  MySQL  │  Redis  │  ES  │  Prometheus      │
└─────────────────────────────────────────────┘
```

---

## 快速开始

### 环境要求

- JDK 17+
- MySQL 5.7+ / 8.0+
- Node.js 18+
- Redis 7+（可选，用于缓存）

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/yclenove/im-bot-hub.git
cd im-bot-hub

# 2. 启动后端
cd backend
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 3. 启动前端
cd admin-ui
npm install
npm run dev

# 4. 访问
# 前端：http://localhost:5173
# 后端：http://localhost:18089
# API 文档：http://localhost:18089/swagger-ui/index.html
```

### Docker 部署

```bash
# 使用 Docker Compose 一键启动
docker-compose up -d

# 访问
# 前端：http://localhost:5173
# 后端：http://localhost:18089
```

---

## 功能模块

### V2 - 核心功能

| 模块 | 说明 |
|------|------|
| 机器人管理 | 逻辑分组单元，支持多渠道 |
| 渠道管理 | 6 大 IM 平台统一接入 |
| 数据源管理 | 数据库 / API 双模式 |
| 查询定义 | SQL / 向导 / API 三种模式 |
| 白名单 | 多平台用户白名单 |
| 命令日志 | 全平台统一命令日志 |
| 审计日志 | 管理端操作审计 |

### V3 - 数据智能

| 模块 | 说明 |
|------|------|
| 查询模板市场 | 预置模板 + 一键导入 |
| 命令统计 | 多维度统计分析 |
| 渠道健康 | 实时健康监控 |
| 用户权限 | JWT 认证 + 角色控制 |
| 告警系统 | 规则配置 + 自动检查 |
| 插件系统 | 可扩展插件架构 |
| API 开放平台 | API Key 管理 |

### V4 - AI 驱动

| 模块 | 说明 |
|------|------|
| NL2SQL | 自然语言转 SQL 查询 |
| 异常检测 | 时序异常自动检测 |
| 智能推荐 | 查询推荐 + 优化建议 |
| 工作流引擎 | 多步骤流程 + 条件分支 |
| 定时任务 | Cron 调度 + 任务队列 |
| 审批流 | 多级审批 + 通知 |
| SSO/LDAP | 企业统一认证 |
| 数据脱敏 | 字段级自动脱敏 |
| 权限矩阵 | 细粒度权限控制 |
| 合规审计 | 等保三级 / GDPR |
| 集群部署 | 高可用 + 负载均衡 |
| 性能优化 | 多级缓存 + 查询优化 |
| 可视化报表 | 仪表盘 + 定时报表 |
| 多租户 | 租户管理 + 配额控制 |

---

## API 端点

### 核心 API

```
GET    /api/admin/bots                    # 机器人列表
GET    /api/admin/channels                # 渠道列表
GET    /api/admin/datasources             # 数据源列表
GET    /api/admin/bots/{id}/queries       # 查询定义列表
POST   /api/admin/channels/{id}/test      # 渠道连通性测试
```

### V3 API

```
GET    /api/admin/templates               # 模板列表
POST   /api/admin/templates/{id}/import   # 一键导入
GET    /api/admin/stats/commands          # 命令统计
GET    /api/admin/channel-health          # 渠道健康
POST   /api/auth/login                    # 用户登录
```

### V4 API

```
POST   /api/admin/ai/nl2sql               # NL2SQL 查询
GET    /api/admin/ai/anomalies            # 异常检测
GET    /api/admin/v4/recommendations      # 智能推荐
GET    /api/admin/v4/workflows            # 工作流管理
GET    /api/admin/v4/approvals/pending    # 待审批
GET    /api/admin/v4/audit/report         # 合规报告
GET    /api/admin/v4/gateway/keys         # API Key
GET    /api/admin/v4/cluster/status       # 集群状态
GET    /api/admin/v4/performance/hints    # 性能建议
GET    /api/admin/v4/tenants              # 租户列表
```

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用框架 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| Flyway | 10.10.0 | 数据库迁移 |
| Caffeine | - | 本地缓存 |
| Redis | 7+ | 分布式缓存 |
| Prometheus | - | 指标采集 |
| JWT | 0.12.5 | 认证令牌 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4+ | 前端框架 |
| Element Plus | 2.7+ | UI 组件库 |
| Vite | 5.0+ | 构建工具 |
| TypeScript | 5.0+ | 类型系统 |
| Pinia | 2.1+ | 状态管理 |

---

## 项目结构

```
im-bot-hub/
├── backend/                    # 后端 Spring Boot 项目
│   ├── src/main/java/com/sov/imhub/
│   │   ├── ai/                # AI 服务（NL2SQL、异常检测、推荐）
│   │   ├── analytics/         # 报表服务
│   │   ├── cluster/           # 集群服务
│   │   ├── config/            # 配置类
│   │   ├── domain/            # 实体类
│   │   ├── gateway/           # API 网关
│   │   ├── im/                # IM 平台集成
│   │   ├── mapper/            # MyBatis Mapper
│   │   ├── multiTenant/       # 多租户
│   │   ├── performance/       # 性能优化
│   │   ├── plugin/            # 插件系统
│   │   ├── scheduler/         # 定时任务
│   │   ├── security/          # 安全服务
│   │   ├── service/           # 业务服务
│   │   ├── web/               # Web 控制器
│   │   └── workflow/          # 工作流引擎
│   └── src/main/resources/
│       ├── db/migration/      # Flyway 迁移脚本
│       └── application*.yml   # 配置文件
├── admin-ui/                   # 前端 Vue 项目
│   ├── src/
│   │   ├── components/        # 组件
│   │   ├── views/             # 页面
│   │   ├── api/               # API 客户端
│   │   ├── utils/             # 工具函数
│   │   └── auth/              # 认证相关
│   └── package.json
├── docs/                       # 文档
│   ├── PRD-V2.md              # 产品需求文档
│   ├── DESIGN-V2.md           # 设计文档
│   ├── ROADMAP-V3.md          # V3 路线图
│   ├── ROADMAP-V4.md          # V4 路线图
│   └── TECHNICAL-DEBT*.md     # 技术债清单
└── scripts/                    # 脚本
    └── install-v3-deps.sh     # 依赖安装脚本
```

---

## 数据库

### 迁移脚本

| 版本 | 说明 |
|------|------|
| V1 | 初始化表结构 |
| V2 | 系统设置表 |
| V3 | 白名单多平台支持 |
| V4 | 查询渠道作用域 |
| V5 | 查询模板市场 |
| V6 | 命令统计 |
| V7 | 渠道健康日志 |
| V8 | 管理用户 |
| V9 | 告警系统 |
| V10 | API Key |
| V11 | NL2SQL + 异常检测 |
| V12 | 工作流引擎 |
| V13 | SSO + 安全 + 多租户 |

---

## 配置

### application-local.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/im_hub
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6380

app:
  security:
    admin:
      username: admin
      password: your_password
  ai:
    provider: openai
    api-key: your_openai_api_key
    model: gpt-4
```

---

## 开发规范

- 提交信息使用**中文**
- 后端改动后运行 `mvn test`
- 前端改动后运行 `npm run build`
- 代码规范见 `.claude/rules/CODE_QUALITY.md`

---

## 文档

| 文档 | 说明 |
|------|------|
| [PRD-V2.md](docs/PRD-V2.md) | 产品需求文档 |
| [DESIGN-V2.md](docs/DESIGN-V2.md) | 架构设计文档 |
| [ROADMAP-V3.md](docs/ROADMAP-V3.md) | V3 路线图 |
| [ROADMAP-V4.md](docs/ROADMAP-V4.md) | V4 路线图 |
| [CODE_QUALITY.md](.claude/rules/CODE_QUALITY.md) | 代码质量规范 |
| [TECHNICAL-DEBT.md](docs/TECHNICAL-DEBT.md) | 技术债清单 |

---

## 许可证

MIT License

---

## 贡献

欢迎提交 Issue 和 Pull Request！

---

*最后更新：2026-05-03*

# 迭代计划（中英）/ Iteration Plan

---

## 1. 目的 / Purpose

**中文**

- 将当前项目从“功能已可运行”推进到“文档齐备、交付稳定、体验持续优化”的产品化节奏。

**English**

- Move the project from “working features” to a product rhythm with complete docs, stable delivery, and ongoing UX improvement.

---

## 2. 当前阶段 / Current phase

1. 基础后端与管理端已具备可运行能力。
2. 多 IM、查询日志、向导能力已具备基础实现。
3. 正式文档体系已补齐第一版。
4. 下一步重点从“补文档”转向“固化流程 + 优化体验 + 提升可运维性”。

---

## 3. 迭代主题 / Iteration themes

### 阶段 A：交付自动化 / Delivery automation

目标：让“测试 -> 文档检查 -> 提交 -> push”流程可执行、可复用、可审查。

- 固化本地脚本与 CI 门禁
- 明确哪些改动必须跑哪些检查
- 避免把本地敏感配置误提交

### 阶段 B：管理端体验优化 / Admin UX optimization

目标：降低管理员使用门槛，让配置流程更顺畅。

- 梳理 Bot / Datasource / Query 主任务流
- 优化默认值、校验、空态、错误提示
- 为复杂 SQL / 向导配置补充更清晰的交互层级

### 阶段 C：运维与安全完善 / Ops and security hardening

目标：提高上线稳定性和安全边界清晰度。

- 完善 Webhook 生产部署清单
- 强化配置脱敏、日志规范、只读账号说明
- 评估限流与多实例共享状态方案

### 阶段 D：产品化能力增强 / Product capability expansion

目标：在不牺牲安全和可维护性的前提下增强配置能力。

- 提升多渠道统一抽象
- 增强查询模板可视化配置
- 完善审计、查询日志分析和辅助诊断能力

---

## 4. 近期优先级 / Near-term priorities

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P0 | 交付脚本与 CI 稳定运行 | 确保质量门禁与文档检查可落地 |
| P0 | 管理端关键页面 UX 评估 | 优先围绕 Datasource / QueryDefinition / Bot |
| P1 | 部署与 tunnel 调试体验 | 降低本地接入 Telegram 的操作门槛 |
| P1 | 安全配置检查清单 | 避免默认弱密码、明文配置、错误 webhook 暴露 |
| P2 | 日志与指标增强 | 提升排障效率 |

---

## 5. 每阶段交付要求 / Delivery requirements per stage

1. 有需求分析或 PRD 更新。
2. 有必要的设计文档更新。
3. 有对应测试执行记录。
4. 有 README / CHANGELOG 同步。
5. 提交信息使用中文。

---

## 6. 成功标准 / Success criteria

- 本地与 CI 的质量门禁脚本可重复执行。
- 中大型功能能按“需求 -> 设计 -> 实现 -> 测试 -> 文档 -> 提交”的流程推进。
- 管理端关键路径的用户操作明显减少、反馈更清晰。
- 安全和运维边界在文档与实现中保持一致。

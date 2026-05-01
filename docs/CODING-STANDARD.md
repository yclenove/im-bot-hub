# 编码规范（中英）/ Coding standards

> **中文：** 本仓库已提交的 `docs/` 与业务源码均须遵守；本地 `.cursor/rules/` 不入库，团队以本文与 `WORKFLOW.md` 为准。  
> **English:** Follow this document for committed code; `.cursor/rules/` is not versioned—use `docs/` + `WORKFLOW.md` as the shared contract.

---

## 1. 注释 / Comments（简体中文 + 技术术语可英文）

**中文**

- 类、公开方法、非显然逻辑须有 **简体中文** JavaDoc 或块注释：说明用途、入参、异常与安全注意点。  
- 禁止「注释 === 代码字面翻译」；应写清**为何**与不变量（例如为何限流、为何必须绑定参数）。  
- Vue：`<script setup` 顶部说明页面职责；复杂 `computed`/`watch` 写明业务含义。

**English**

- Public APIs and non-obvious logic: **Simplified Chinese** Javadoc/block comments; technical terms may stay English.  
- Explain **why** and invariants, not redundant restatement.  
- Vue: top-of-script summary; document complex `computed`/`watch`.

---

## 2. Java 规范 / Java conventions

**中文：** 遵循团队格式化（可参考 Google Java Style）；命名见第 4 节；`jakarta.validation` 用于入参；业务规则在 service；MyBatis-Plus 实体与表映射清晰；动态 SQL 仅在受控执行路径。  
**English:** Formatting (e.g. Google Java Style); validation on DTOs; business rules in services; clear MP entities; dynamic SQL only in guarded executors.

---

## 3. Vue 规范 / Vue conventions

**中文：** TypeScript 严格模式；单文件组件；API 经 `src/api/client.ts`；`ElMessage` 等反馈用户可读错误；避免模板内复杂表达式。  
**English:** Strict TS; SFC; centralized API client; user-visible errors; no heavy logic in templates.

---

## 4. 设计原则 / Design principles（SOLID 等）

**中文**

- **S：** Webhook 解析、SQL 执行、字段渲染、Telegram 发送分属不同类。  
- **O：** 新命令优先走**配置**而非改核心分支。  
- **L：** 可替换实现（如未来更换 Telegram 客户端）。  
- **I：** 管理端 DTO 细分，避免「上帝接口」。  
- **D：** 依赖 `*Service` / Registry 抽象，而非直接操作连接细节。  
- **KISS / YAGNI、DRY：** 不过度设计；绑定与校验集中。

**English**

- **S/O/L/I/D** as mapped above; prefer configuration for new commands; keep DRY for binding/validation/masking.

---

## 5. 可读性 · 可用性 · 可扩展性 / Readability · usability · extensibility

**中文：** 短函数、早返回、命名达意；管理端表单校验与友好报错；机器人回复简短可操作；通过**元数据表**扩展命令与展示列，复杂分支用策略接口代替巨型 `switch`。  
**English:** Small functions, early returns; validated admin forms; short bot messages; extend via metadata tables and strategy hooks instead of huge switches.

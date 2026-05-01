# 研发流程（中英）/ Engineering workflow

---

## 1. Changelog / 变更日志

**中文**

- 根目录 [`CHANGELOG.md`](../CHANGELOG.md) 遵循 **Keep a Changelog**。  
- 每次合并或发布须更新：在 `[Unreleased]` 下按 **Added / Changed / Fixed / Removed**（可用中英对照条目）；发版时写入版本号与日期。

**English**

- Update [`CHANGELOG.md`](../CHANGELOG.md) each merge/release; move `[Unreleased]` into a version when tagging.

---

## 2. 测试要求 / Testing

| 类型 Type | 最低要求（中文） | Minimum (EN) |
|-----------|------------------|----------------|
| 单元测试 | 校验器、解析器、加解密等纯逻辑须有 **JUnit 5** | JUnit 5 for validators/parsers/crypto helpers |
| 功能/集成 | 涉及 DB/API 的改动须在本地跑通主流程；推荐 Testcontainers | Run main flows locally; Testcontainers recommended |
| 前端 | 修改 `admin-ui` 时执行 `npm run build` | Run `npm run build` when UI changes |
| 后端 | 修改 `backend` 时执行 `mvn test` | Run `mvn test` when backend changes |

---

## 3. Git 提交与推送 / Git commit & push

**中文**

- 提交信息推荐 **Conventional Commits**（`feat:`、`fix:`、`chore:`、`docs:`）。  
- **禁止**未评审脚本在每次保存后自动 `push` 到共享分支。推荐：本地测试通过 → 更新 `CHANGELOG` / `DESIGN`（若涉架构）→ `commit` → `push` 功能分支 → **Code Review**。  
- CI 负责自动化测试与制品时，以 CI 为准；本文约束本地与 MR 纪律。

**English**

- Conventional Commits; no auto-push on every save; commit after tests; push feature branches; open PRs for review.

---

## 4. 设计文档 / Design documentation

**中文：** 架构、数据模型、安全与部署变更须更新 [`docs/DESIGN.md`](DESIGN.md) 第七节与相关章节。编码风格见 [`docs/CODING-STANDARD.md`](CODING-STANDARD.md)。  
**English:** Update [`docs/DESIGN.md`](DESIGN.md) section 7 + relevant sections; coding rules in [`docs/CODING-STANDARD.md`](CODING-STANDARD.md).

---

## 5. 需求与产品文档 / Requirements and product docs

**中文**

- 中大型功能在编码前应至少具备一份 **需求分析** 或 **PRD**。
- 涉及用户流程、信息架构、交互路径变化的功能，应明确记录目标用户、核心场景、范围、非目标、成功指标与 UX 要求。
- 建议文档体系至少包含：`PRD`、需求分析、设计文档、测试策略/测试说明。

**English**

- Medium and large features should have at least a **requirements analysis** or **PRD** before implementation.
- UX-affecting changes should document target users, scenarios, scope, non-goals, success metrics, and UX expectations.
- Recommended doc set: PRD, requirements analysis, design docs, and testing strategy.

---

## 6. 测试与交付 / Testing and delivery

**中文**

- 每一阶段开发完成后应先执行对应自动测试，再更新文档和变更记录。
- 推荐交付顺序：**实现 -> 测试 -> 更新 `README` / `CHANGELOG` / 相关设计文档 -> 中文提交信息 -> push**。
- 仅文档改动可不执行构建/单测，但必须自查索引、链接、交叉引用和内容一致性。
- 仓库内脚本建议使用：`scripts/dev/check-docs.ps1`、`scripts/dev/run-quality-gates.ps1`、`scripts/dev/deliver.ps1`。

**English**

- After each development stage, run the relevant automated checks before updating docs and committing.
- Preferred delivery order: **implement -> test -> update docs/changelog -> Chinese commit message -> push**.
- Docs-only changes may skip builds/tests, but link/index consistency should still be checked.
- Recommended repo scripts: `scripts/dev/check-docs.ps1`, `scripts/dev/run-quality-gates.ps1`, `scripts/dev/deliver.ps1`.

---

## 7. 代码评审 Code Review

**中文 · 建议清单**

- [ ] 安全：SQL 绑定、无密钥日志、管理端鉴权  
- [ ] 测试：新逻辑有测试或可解释为何不测  
- [ ] 文档：`CHANGELOG` + 必要时 `DESIGN`  
- [ ] 可读性：命名、**中文注释**、函数长度  
- [ ] 兼容：Flyway、API 破坏性变更  

至少一名非作者通过后方可合并主分支（团队可约定例外）。  
**English:** Same checklist; at least one non-author approval before merging to main.

---

## 8. Cursor Rules 与 Git / Cursor rules & Git

**中文：** `.cursor/rules/` **不提交**（见根 `.gitignore`）。团队共识以 `docs/*.md` 与 `AGENTS.md` 为准。  
**English:** `.cursor/rules/` is gitignored; `docs/*.md` and `AGENTS.md` are the source of truth.

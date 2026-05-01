# MCP MySQL 建库与测试说明（中英）

## 0. 你要的操作：MCP 切到 `local` 库再建库/测表（必读）

**中文（说人话）**

1. **MCP 不会魔法**：`use_database` 只能切到**已经存在**、且**当前 MCP 账号有权访问**的库。  
2. 你现在用的 MCP 账号（如 `cursortest_java`）一般 **不能 `CREATE DATABASE`**，所以 **先在管理员（root）里建库 `local` 并授权给 MCP 账号**。  
3. 在仓库根执行（按你本机改密码/用户名）：

```bash
mysql -u root -p < scripts/mysql/02-create-local-db-and-grant-mcp.sql
```

4. **重启 Cursor 或重载 MCP**（让连接带上新权限），然后在对话里让 Agent 依次调用：  
   - `use_database`，参数 `database` = **`local`**  
   - `batch_execute` 跑建表 SQL，或 `query` 做 `SHOW TABLES`  
5. 若 **`use_database`("local")仍报错**，把 `SHOW GRANTS FOR '你的用户'@'%';` 结果给 DBA——缺的是 **对库 `local` 的 GRANT**，不是 Cursor 配置写错。

**English**

1. MCP `use_database` only works if schema **`local` exists** and the **MCP user is granted** on it.  
2. App-like users usually **cannot** `CREATE DATABASE`; run [`scripts/mysql/02-create-local-db-and-grant-mcp.sql`](../scripts/mysql/02-create-local-db-and-grant-mcp.sql) **as root** first.  
3. Reload MCP, then call **`use_database` → `local`**, then **`batch_execute`** / **`query`**.

---

## 1. 结论摘要（本次会话实测）

| 步骤 | 结果 |
|------|------|
| MCP `test_connection` | 成功（示例：`version=8.x`，延迟约毫秒级） |
| `show_databases` | 可见当前实例上的库列表 |
| `CREATE DATABASE tg_query_meta`（经 `batch_execute`） | **失败**：应用账号无建库权限 `Access denied ... to database 'tg_query_meta'` |

**中文：** 在多数环境中，应用库账号只有**某个库**的 `ALL`，没有 **`CREATE DATABASE`**。建库需使用 **root/DBA** 执行 [`scripts/mysql/01-create-database-and-grant.sql`](../scripts/mysql/01-create-database-and-grant.sql)，再授权给业务用户。

**English:** App users often cannot `CREATE DATABASE`. Create `tg_query_meta` with a **privileged** account using the script above, then `GRANT` to the app user.

---

## 2. 与本项目 Spring 对齐：都连「本机 local」

**中文**

- 后端默认 **`spring.profiles.active=local`**，数据源在 [`backend/src/main/resources/application-local.yml`](../backend/src/main/resources/application-local.yml)，一般为 **`127.0.0.1:3306`**、库名 **`tg_query_meta`**。
- **MCP mysql-mcp** 请配置**同一台本机 MySQL**（同样在 `127.0.0.1` 或 `localhost`），这样用 MCP 查表与用应用/Flyway 是**同一实例、同一库**（账号可不同，只要对该库有权限）。

**English**

- The app defaults to profile **`local`** ([`application-local.yml`](../backend/src/main/resources/application-local.yml)): usually **`127.0.0.1:3306`**, database **`tg_query_meta`**.
- Point **mysql-mcp** at the **same local MySQL instance** so MCP queries match what Spring/Flyway use.

---

## 3. 在 Cursor 中使用 MCP（mysql-mcp）

**中文**

1. 确认 `~/.cursor/mcp.json`（或项目 MCP 配置）已启用 `mysql-mcp`，且 **`MYSQL_URL`（或等价变量）指向本机**，例如：`jdbc:mysql://127.0.0.1:3306/cursortest_java` 或与你的用户权限匹配的库；需与上节「本机实例」一致。  
2. 在对话中由 Agent 调用工具时，服务器标识多为 **`user-mysql-mcp`**（以当前 Cursor 项目 `mcps` 目录为准）。  
3. 只读查询用 **`query`**；元数据用 **`show_databases`**、**`use_database`**、**`list_tables`**。  
4. 写操作（建表、批量 DDL）用 **`batch_execute`**，且 MCP 需 **非只读** 模式（`MYSQL_READONLY` 未置为 `true`）。  
5. **建库**通常仍需 DBA 账号；MCP 应用账号往往仅能连已有库。

**English**

1. Enable `mysql-mcp` with a valid DSN.  
2. Tool calls may use server id **`user-mysql-mcp`**.  
3. Read: `query`; metadata: `show_databases`, `use_database`, `list_tables`.  
4. Writes: `batch_execute` when not read-only.  
5. `CREATE DATABASE` usually requires a privileged user, not the app user.

---

## 4. 本项目的推荐本地测试步骤

**中文**

1. 用管理员执行 [`scripts/mysql/01-create-database-and-grant.sql`](../scripts/mysql/01-create-database-and-grant.sql)，取消注释并填写 `GRANT`。  
2. 确认 [`application-local.yml`](../backend/src/main/resources/application-local.yml) 中为本机 **`127.0.0.1:3306`** 与库名 **`tg_query_meta`**（按需改账号密码）。默认已激活 **`local`** profile。  
3. 启动：`cd backend && mvn spring-boot:run`，由 **Flyway** 执行 `db/migration/V1__*.sql` 建表。  
4. 用 MCP：`use_database` → **`tg_query_meta`**，再 `query`：`SHOW TABLES;`，确认出现 `t_bot` 等表。

**English**

1. Create DB + grants via the SQL script (as admin).  
2. Check [`application-local.yml`](../backend/src/main/resources/application-local.yml) points to **`127.0.0.1:3306`** / **`tg_query_meta`**; profile **`local`** is active by default.  
3. Run the app; Flyway applies migrations.  
4. With MCP: `use_database` → **`tg_query_meta`**, then `SHOW TABLES`.

---

## 5. 替代：暂无建库权限时

**中文：** 仅在**空白测试实例**或**专用沙箱库**中，可把 `application.yml` 指到已有空库名；**切勿**在与其它项目共用的生产库中直接跑 Flyway，以免表名冲突。

**English:** Point `application.yml` to an empty sandbox schema only—never mix with unrelated production tables.

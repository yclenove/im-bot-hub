# MySQL 只读副本与点查加速（中英）/ Read replica & point-query tuning

---

## 1. 账号与连接 / Account & connection

**中文**

1. 在**副本**上创建仅 `SELECT` 的数据库用户；应用中「业务数据源」JDBC URL 指向副本。  
2. 配置库（元数据）与业务库账号分离。

**English**

1. Create a **read-only** DB user (`SELECT` only) on the replica; point the bot **Datasource** JDBC URL to the replica host.  
2. Keep config DB credentials separate from business DB users.

---

## 2. 索引与执行计划 / Indexing & EXPLAIN

**中文**

- 对查询唯一键（如 `order_no`）建立 **BTREE**，优先 **UNIQUE**（业务允许时）。  
- 用 `EXPLAIN` 确认走索引，`type` 宜为 `const` / `ref`，避免全表扫。

**English**

- Add **BTREE** (prefer **UNIQUE** when valid) on the lookup key.  
- Validate with `EXPLAIN` (`const`/`ref`), avoid full scans on hot paths.

---

## 3. 复制延迟 / Replication lag

**中文：** 主从异步；可在机器人回复中提示「数据来自只读库」。强一致场景不要依赖机器人直查副本，除非已评估延迟。  
**English:** Replication is asynchronous; disclose “read replica” if needed; do not assume immediate consistency.

---

## 4. 连接池 / Pooling

**中文：** 在管理端为每个数据源设置合理 `pool_max`；多机器人共享同一副本时可增大池并压测。  
**English:** Tune `pool_max` per datasource; load-test when sharing one replica across many bots.

---

## 5. 可选缓存 / Optional cache

**中文：** 热点查询可考虑 Redis（短 TTL、键含参数）；需业务接受短暂不一致。  
**English:** Redis may cache hot keys (short TTL); only if stale reads are acceptable.

# 模块拆分与架构设计

## 1. 总体思路

产品采用“一套核心、两种运行模式”设计：

1. CLI 模式直接调用核心同步逻辑。
2. daemon 模式以相同核心逻辑为基础，只增加调度、监听和状态管理。

## 2. 模块拆分图

```text
opencode-sync
├─ cmd/
│  ├─ root
│  ├─ init
│  ├─ push
│  ├─ pull
│  ├─ sync
│  ├─ run
│  ├─ status
│  ├─ doctor
│  └─ daemon
├─ internal/
│  ├─ app/
│  │  ├─ cli
│  │  └─ daemon
│  ├─ opencode/
│  │  ├─ adapter
│  │  ├─ exporter
│  │  ├─ importer
│  │  └─ sessions
│  ├─ sync/
│  │  ├─ planner
│  │  ├─ executor
│  │  ├─ differ
│  │  └─ conflict
│  ├─ store/
│  │  ├─ interface
│  │  ├─ github
│  │  ├─ s3
│  │  └─ api
│  ├─ manifest/
│  │  ├─ model
│  │  ├─ codec
│  │  └─ validator
│  ├─ config/
│  ├─ state/
│  ├─ project/
│  ├─ hash/
│  └─ log/
└─ docs/
```

## 3. 核心模块说明

### 3.1 `opencode`

职责：

1. 调用 `opencode session list --format json`。
2. 调用 `opencode export <sessionID>`。
3. 调用 `opencode import <file>`。
4. 做基础输出解析与错误包装。

边界：

- 不负责同步策略。
- 不负责远端存储。

### 3.2 `sync`

职责：

1. 计算本地 session 与远端 manifest 差异。
2. 生成 push/pull 计划。
3. 执行导出、上传、下载、导入。
4. 处理去重与冲突策略。

### 3.3 `store`

职责：

1. 读写 manifest。
2. 上传下载 session blob。
3. 屏蔽 GitHub / S3 / API 的底层差异。

建议接口：

```text
Store
├─ GetManifest(projectKey)
├─ PutManifest(projectKey, manifest)
├─ GetSessionBlob(projectKey, sessionID)
├─ PutSessionBlob(projectKey, sessionID, data)
├─ DeleteSessionBlob(projectKey, sessionID)
└─ HealthCheck()
```

### 3.4 `manifest`

职责：

1. 统一定义远端元数据结构。
2. 负责编码、解码、校验。
3. 保持与存储后端无关。

### 3.5 `config`

职责：

1. 读取全局配置。
2. 读取项目配置。
3. 合并默认值。

### 3.6 `state`

职责：

1. 记录本地同步状态。
2. 减少重复 push/pull。
3. 为 daemon 模式保留状态扩展点。

## 4. CLI 与 daemon 的关系

### 4.1 CLI

- 一期主入口。
- 直接使用 `sync` 模块。
- 不保留常驻状态。

### 4.2 daemon

- 二期增强入口。
- 复用 `sync` 模块。
- 只附加以下能力：
  1. 定时器
  2. 监听器
  3. 通知
  4. 常驻进程状态管理

## 5. 数据流

### 5.1 `push`

```text
OpenCode sessions
  -> export session json
  -> hash
  -> compare local state + remote manifest
  -> upload changed blobs
  -> update manifest
```

### 5.2 `pull`

```text
Remote manifest
  -> compare local state
  -> download missing/changed blobs
  -> save temp file
  -> opencode import
  -> update local state
```

## 6. 实施建议

建议先实现以下顺序：

1. adapter
2. config
3. manifest
4. github store
5. push
6. pull
7. sync
8. run
9. status
10. doctor

`daemon` 可以先保留空壳命令和接口，不在一期实现完整能力。

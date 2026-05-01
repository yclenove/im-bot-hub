# CLI 命令树设计

## 1. 设计目标

- 命令命名直观。
- 默认面向纯 CLI 工具模式。
- 后台模式在命令树中可选接入，但不影响一期交付。

## 2. 命令树

```text
opencode-sync
├─ init
├─ status
├─ push
├─ pull
├─ sync
├─ run
├─ doctor
├─ backend
│  ├─ list
│  └─ test
└─ daemon
   ├─ start
   ├─ stop
   ├─ status
   └─ logs
```

## 3. 一期命令说明

### 3.1 `init`

用途：初始化项目配置。

建议参数：

- `--backend <name>`
- `--project-key <key>`
- `--workspace <path>`
- `--default`

行为：

1. 识别当前项目根目录。
2. 生成或确认 `projectKey`。
3. 写入项目级配置文件。
4. 校验所选 backend 所需配置是否齐全。

### 3.2 `status`

用途：显示本地与远端差异。

输出建议包含：

1. 本地 session 总数。
2. 远端 session 总数。
3. 待上传 session 数量。
4. 待下载 session 数量。
5. 冲突 session 数量。

### 3.3 `push`

用途：把本地新增或变更 session 推送到远端。

建议参数：

- `--all`
- `--since <duration>`
- `--session <id>`
- `--dry-run`
- `--force`

### 3.4 `pull`

用途：把远端新增或变更 session 拉回本地并导入 OpenCode。

建议参数：

- `--all`
- `--session <id>`
- `--dry-run`
- `--force`

### 3.5 `sync`

用途：执行一次双向同步。

推荐默认顺序：

1. 先 `pull`
2. 再 `push`

建议参数：

- `--dry-run`
- `--pull-only`
- `--push-only`
- `--force`

### 3.6 `run`

用途：作为 OpenCode 包装入口。

推荐行为：

1. 启动前 `pull`
2. 启动 `opencode`
3. 退出后 `push`

建议参数：

- `--no-pull`
- `--no-push`
- `--session <id>`
- `-- <opencode args...>`

### 3.7 `doctor`

用途：诊断运行环境。

检查项建议：

1. `opencode` 是否可用。
2. `opencode session list --format json` 是否可用。
3. `opencode export` / `import` 是否可用。
4. 项目配置是否完整。
5. backend 凭据是否可用。

## 4. 二期命令说明

### 4.1 `daemon start`

启动后台同步进程。

### 4.2 `daemon stop`

停止后台进程。

### 4.3 `daemon status`

查看后台运行状态。

### 4.4 `daemon logs`

查看后台同步日志。

## 5. 命令输出风格建议

1. 默认输出人类可读摘要。
2. 重要命令支持 `--json`。
3. 错误信息保持短句、可操作。
4. 对破坏性操作使用明确提示。

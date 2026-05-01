# Manifest 数据结构设计

## 1. 设计目标

manifest 用于描述某个 `projectKey` 下所有已同步的 session 元数据，负责：

1. 增量同步判断。
2. 会话去重。
3. 远端状态查询。
4. 为后续冲突处理与后台模式提供基础。

## 2. 存储布局

建议布局：

```text
projects/{projectKey}/manifest.json
projects/{projectKey}/sessions/{sessionId}.json
projects/{projectKey}/meta/{machineId}.json
```

一期必须：

- `manifest.json`
- `sessions/{sessionId}.json`

一期可选：

- `meta/{machineId}.json`

## 3. manifest 顶层结构

```json
{
  "manifestVersion": "1",
  "projectKey": "github.com/acme/demo",
  "updatedAt": "2026-04-25T10:30:00Z",
  "sessions": {
    "ses_123": {
      "sessionId": "ses_123",
      "title": "Fix sync bug",
      "createdAt": "2026-04-24T09:00:00Z",
      "updatedAt": "2026-04-25T10:20:00Z",
      "sha256": "sha256:abcd",
      "blobPath": "projects/github.com__acme__demo/sessions/ses_123.json",
      "sourceMachine": "machine-a",
      "opencodeVersion": "1.14.22"
    }
  }
}
```

## 4. 字段定义

### 4.1 顶层字段

| 字段 | 必填 | 说明 |
|------|------|------|
| `manifestVersion` | 是 | manifest 版本号 |
| `projectKey` | 是 | 当前项目的稳定标识 |
| `updatedAt` | 是 | manifest 最近更新时间 |
| `sessions` | 是 | session 元数据集合 |

### 4.2 session 元数据字段

| 字段 | 必填 | 说明 |
|------|------|------|
| `sessionId` | 是 | OpenCode session ID |
| `title` | 否 | 会话标题 |
| `createdAt` | 是 | 会话创建时间 |
| `updatedAt` | 是 | 会话更新时间 |
| `sha256` | 是 | 导出 JSON 内容 hash |
| `blobPath` | 是 | 远端 session blob 路径 |
| `sourceMachine` | 否 | 最后写入该 session 的机器 |
| `opencodeVersion` | 否 | 导出时的 OpenCode 版本 |

## 5. session blob 规则

1. 每个 session 对应一个独立 JSON blob。
2. 内容优先直接保存 OpenCode 原始导出结果。
3. 不对原始导出内容进行结构重写，最多附加外围元数据文件。

## 6. 同步判断规则

### 6.1 本地到远端

如果本地导出的 session 满足任一条件，则视为待上传：

1. 远端 manifest 中不存在该 `sessionId`。
2. 本地导出 hash 与远端 `sha256` 不一致。
3. 本地 `updatedAt` 晚于远端记录。

### 6.2 远端到本地

如果远端 manifest 中的 session 满足任一条件，则视为待下载：

1. 本地 state 不存在该 `sessionId`。
2. 本地 `lastImportedHash` 与远端 `sha256` 不一致。

## 7. 冲突策略建议

一期建议默认 `keep-both`，也就是：

1. 发现同 `sessionId` 但内容 hash 不同。
2. 若不能明确判断哪边是最新版本，则保留冲突副本。
3. 不做静默覆盖。

## 8. 兼容性要求

1. `manifestVersion` 未来可升级。
2. 未识别字段应尽量保留。
3. 不同 backend 只影响存储实现，不应改变 manifest 语义。

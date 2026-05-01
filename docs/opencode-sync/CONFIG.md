# 配置文件设计

## 1. 配置原则

1. 全局配置负责 backend 注册和凭据引用。
2. 项目配置负责当前项目绑定哪个 backend、哪个 `projectKey`、同步策略是什么。
3. 密钥尽量优先走环境变量或系统密钥链，配置文件只保留引用。

## 2. 全局配置

建议路径：

- Windows: `%APPDATA%/opencode-sync/config.json`
- macOS/Linux: `~/.config/opencode-sync/config.json`

示例：

```json
{
  "defaultBackend": "github-main",
  "backends": {
    "github-main": {
      "type": "github",
      "owner": "your-org",
      "repo": "opencode-sync-store",
      "branch": "main",
      "tokenEnv": "GITHUB_TOKEN"
    },
    "r2-prod": {
      "type": "s3",
      "endpoint": "https://<account>.r2.cloudflarestorage.com",
      "bucket": "opencode-sync",
      "region": "auto",
      "accessKeyEnv": "SYNC_S3_ACCESS_KEY",
      "secretKeyEnv": "SYNC_S3_SECRET_KEY"
    },
    "selfhosted": {
      "type": "api",
      "baseUrl": "https://sync.example.com",
      "tokenEnv": "SYNC_API_TOKEN"
    }
  }
}
```

## 3. 项目配置

建议路径：

- 项目根目录：`.opencode-sync.json`

示例：

```json
{
  "projectKey": "github.com/acme/opencode-sync-demo",
  "backend": "github-main",
  "workspaceRoot": ".",
  "sync": {
    "defaultMode": "manual",
    "runPullBefore": true,
    "runPushAfter": true,
    "conflictPolicy": "keep-both",
    "includeDirectories": [
      "."
    ]
  }
}
```

## 4. 本地状态文件

建议路径：

- `.opencode-sync/state.json`

用途：

1. 记录上次同步时间。
2. 记录每个 session 最近一次已同步的 hash。
3. 记录最近一次 pull/import 结果。
4. 降低重复导入和重复上传。

示例：

```json
{
  "projectKey": "github.com/acme/opencode-sync-demo",
  "lastSyncAt": "2026-04-25T10:30:00Z",
  "sessions": {
    "ses_123": {
      "lastExportHash": "sha256:abc",
      "lastPushedAt": "2026-04-25T10:25:00Z",
      "lastPulledAt": "2026-04-25T10:20:00Z",
      "lastImportedHash": "sha256:abc"
    }
  }
}
```

## 5. 配置字段说明

### 5.1 `projectKey`

规则建议：

1. 优先使用 Git remote URL 规范化结果。
2. 否则允许用户手动指定。
3. 最后才退化为目录名加 hash。

### 5.2 `backend`

指向全局配置中的某个 backend 名称。

### 5.3 `conflictPolicy`

建议首版支持：

- `keep-both`
- `latest-wins`
- `manual`

## 6. 兼容性要求

1. 配置文件需要版本号时，建议新增 `configVersion`。
2. 未识别字段应尽量忽略而不是报错退出。
3. 敏感值不直接写明文，优先环境变量引用。

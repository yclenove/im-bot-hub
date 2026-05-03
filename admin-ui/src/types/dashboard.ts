/**
 * Dashboard 相关类型定义
 */

export type Bot = {
  id: number
  name: string
  primaryChannelId?: number | null
  enabled: boolean
  /** @deprecated Use Channel-based credential management instead. */
  telegramBotTokenMasked?: string
  /** @deprecated Use Channel-based credential management instead. */
  telegramBotUsername?: string | null
  /** @deprecated Use Channel-based webhook secret instead. */
  webhookSecretTokenMasked?: string
  /** @deprecated Use Channel-based chat scope instead. */
  telegramChatScope?: string
  /** @deprecated Use Channel-based allowed chat IDs instead. */
  telegramAllowedChatIds?: number[]
}

export type Ds = {
  id: number
  name: string
  sourceType?: 'DATABASE' | 'API'
  jdbcUrl?: string | null
  apiBaseUrl?: string | null
  apiPresetKey?: string | null
  authType?: string | null
  authConfigJson?: string | null
  defaultHeadersJson?: string | null
  defaultQueryParamsJson?: string | null
  requestTimeoutMs?: number
  configJson?: string | null
  username?: string | null
  poolMax: number
}

export type Qd = {
  id: number
  botId: number
  datasourceId: number
  command: string
  name?: string | null
  telegramMenuDescription?: string | null
  sqlTemplate: string
  queryMode?: string
  visualConfigJson?: string | null
  apiConfigJson?: string | null
  paramSchemaJson: string | null
  timeoutMs: number
  maxRows: number
  enabled: boolean
  /** 回复展现样式（兼容字段名 telegramReplyStyle），见 FieldRenderService */
  telegramReplyStyle?: string
  /** 限定适用渠道 ID 列表（JSON 数组），null = 所有渠道 */
  channelScopeJson?: string | null
}

export type TelegramReplyStyle =
  | 'LIST'
  | 'LIST_DOT'
  | 'LIST_CODE'
  | 'LIST_BLOCKQUOTE'
  | 'SECTION'
  | 'MONO_PRE'
  | 'CODE_BLOCK'
  | 'KV_SINGLE_LINE'
  | 'VALUES_JOIN_SPACE'
  | 'VALUES_JOIN_PIPE'
  | 'VALUES_JOIN_CUSTOM'
  | 'TABLE_PRE'

export type BenchmarkStrategyResult = {
  strategy: string
  ok: boolean
  durationMsAvg: number | null
  rowCountLast: number
  error: string | null
  sqlTemplate: string
}

export type VisualBenchmarkResult = {
  legacyOr: BenchmarkStrategyResult
  unionAll: BenchmarkStrategyResult
  alternatingRuns: boolean
  note: string
}

export type IndexAdviceRecommendation = { rationale: string; columns: string[] }
export type VisualIndexAdviceResult = {
  summary: string
  existingIndexSummaries?: string[]
  coverageSkips?: string[]
  recommendations: IndexAdviceRecommendation[]
  ddlStatements: string[]
  warnings: string[]
}

export type Audit = {
  id: number
  actor: string
  action: string
  resourceType: string
  resourceId: string | null
  detail: string | null
  createdAt: string
}

export type TgLog = {
  id: number
  botId: number
  telegramUserId: number
  chatId: number
  command: string
  queryDefinitionId: number | null
  success: boolean
  errorKind: string
  durationMs: number | null
  detail: string | null
  createdAt: string
}

export type FieldMap = {
  id: number
  queryId: number
  columnName: string
  label: string
  sortOrder: number
  maskType: string
  formatType: string | null
  displayPipelineJson?: string | null
}

/** 与后端 DisplayPipelineApplier 的 op 一致；多余字段在序列化时按步写入 JSON */
export type DisplayPipelineStep = {
  op: string
  value?: string
  max?: number
  ellipsis?: string
  start?: number
  end?: number
  len?: number
  delimiter?: string
  index?: number
  fromEnd?: boolean
  from?: string
  to?: string
  maxReplacements?: number
  left?: string
  right?: string
  lowercaseHost?: boolean
  includeQuery?: boolean
  count?: number
  fromRight?: boolean
  withScheme?: boolean
  maxSegments?: number
  leadingSlash?: boolean
  pattern?: string
  group?: number
}

export type BotChannelRow = {
  id: number
  botId: number
  platform: string
  enabled: boolean
  webhookUrl: string
  credentialsSummary: string
}

export const PIPELINE_OP_OPTIONS: { value: string; label: string }[] = [
  { value: 'trim', label: 'trim — 去首尾空白' },
  { value: 'collapse_space', label: 'collapse_space — 连续空白压成空格' },
  { value: 'upper', label: 'upper — 大写' },
  { value: 'lower', label: 'lower — 小写' },
  { value: 'prefix', label: 'prefix — 前缀 value' },
  { value: 'suffix', label: 'suffix — 后缀 value' },
  { value: 'truncate', label: 'truncate — 截断 max / ellipsis' },
  { value: 'substring', label: 'substring — 子串 start / end 或 len' },
  { value: 'split_take', label: 'split_take — 按分隔符取段' },
  { value: 'replace_literal', label: 'replace_literal — 字面替换' },
  { value: 'extract_between', label: 'extract_between — 取左右标记之间' },
  { value: 'digits_only', label: 'digits_only — 只保留数字' },
  { value: 'default_if_empty', label: 'default_if_empty — 空串占位 value' },
  { value: 'url_to_origin', label: 'url_to_origin — scheme+主机[:端口]' },
  { value: 'url_to_host', label: 'url_to_host — 主机[:端口]' },
  { value: 'url_to_path', label: 'url_to_path — path（可选含 query）' },
  { value: 'url_host_labels', label: 'url_host_labels — 域名按点分段从右取 count 段' },
  { value: 'url_path_segments', label: 'url_path_segments — path 前 N 段' },
  { value: 'regex_extract', label: 'regex_extract — 正则提取（高级）' },
]

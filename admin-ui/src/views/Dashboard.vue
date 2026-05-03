<script setup lang="ts">
import Sortable from 'sortablejs'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, unref, watch } from 'vue'
import type { ElTable } from 'element-plus'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import { clearCredentials } from '../auth/session'
import { platformLabel, platformTagType } from '../utils/platform'
import { buildMysqlJdbcUrl, parseMysqlJdbcUrl } from '../utils/mysqlJdbc'
import ApiDatasourceFormSection from '../components/datasource/ApiDatasourceFormSection.vue'
import VisualQueryWizard from '../components/query-def/VisualQueryWizard.vue'
import ApiQueryBuilder from '../components/query-def/ApiQueryBuilder.vue'
import {
  API_DATASOURCE_PRESETS,
  findDatasourcePreset,
  type ApiQueryPresetAppliedPayload,
} from '../utils/apiPresets'
import OverviewTab from '../components/OverviewTab.vue'
import BotTab from '../components/BotTab.vue'
import ChannelTab from '../components/ChannelTab.vue'
import AllowlistTab from '../components/AllowlistTab.vue'
import CommandLogTab from '../components/CommandLogTab.vue'
import AuditLogTab from '../components/AuditLogTab.vue'
import SettingsTab from '../components/SettingsTab.vue'

const router = useRouter()

function logout() {
  clearCredentials()
  void router.replace('/login')
}

type Bot = {
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
type Ds = {
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
type Qd = {
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

type TelegramReplyStyle =
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

type BenchmarkStrategyResult = {
  strategy: string
  ok: boolean
  durationMsAvg: number | null
  rowCountLast: number
  error: string | null
  sqlTemplate: string
}

type VisualBenchmarkResult = {
  legacyOr: BenchmarkStrategyResult
  unionAll: BenchmarkStrategyResult
  alternatingRuns: boolean
  note: string
}

type IndexAdviceRecommendation = { rationale: string; columns: string[] }
type VisualIndexAdviceResult = {
  summary: string
  existingIndexSummaries?: string[]
  coverageSkips?: string[]
  recommendations: IndexAdviceRecommendation[]
  ddlStatements: string[]
  warnings: string[]
}

type Audit = {
  id: number
  actor: string
  action: string
  resourceType: string
  resourceId: string | null
  detail: string | null
  createdAt: string
}
type TgLog = {
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
type FieldMap = {
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
type DisplayPipelineStep = {
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

const PIPELINE_OP_OPTIONS: { value: string; label: string }[] = [
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

function parseDisplayPipelineJson(json: string | null | undefined): DisplayPipelineStep[] {
  if (!json?.trim()) return []
  try {
    const arr = JSON.parse(json) as unknown
    if (!Array.isArray(arr)) return []
    return arr.map((x) => {
      if (x && typeof x === 'object' && 'op' in (x as object)) {
        return { ...(x as Record<string, unknown>) } as DisplayPipelineStep
      }
      return { op: 'trim' }
    })
  } catch {
    return []
  }
}

function buildDisplayPipelineJson(steps: DisplayPipelineStep[]): string | null {
  const rows: Record<string, unknown>[] = []
  for (const raw of steps) {
    if (!raw.op?.trim()) continue
    const o: Record<string, unknown> = { op: raw.op.trim() }
    for (const [k, v] of Object.entries(raw)) {
      if (k === 'op') continue
      if (v === undefined || v === null) continue
      if (typeof v === 'string' && v.trim() === '') continue
      if (typeof v === 'number' && !Number.isFinite(v)) continue
      o[k] = v
    }
    rows.push(o)
  }
  if (!rows.length) return null
  const json = JSON.stringify(rows)
  if (json.length > 24000) {
    throw new Error('PIPELINE_TOO_LONG')
  }
  return json
}

function payLinkPipelinePreset(): DisplayPipelineStep[] {
  return [
    { op: 'trim' },
    { op: 'url_to_origin', lowercaseHost: true },
    { op: 'url_host_labels', count: 2, fromRight: true, withScheme: true },
  ]
}

const bots = ref<Bot[]>([])
const botTabRef = ref<InstanceType<typeof BotTab> | null>(null)
const channelTabRef = ref<InstanceType<typeof ChannelTab> | null>(null)
const overviewTabRef = ref<InstanceType<typeof OverviewTab> | null>(null)

// 从 BotTab 同步 bots 列表
watch(() => botTabRef.value?.bots, (val) => {
  if (val) bots.value = val as Bot[]
}, { deep: true })

const dsList = ref<Ds[]>([])
const allowBotId = ref<number | null>(null)
const queryBotId = ref<number | null>(null)
const queries = ref<Qd[]>([])
const audits = ref<Audit[]>([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditSize = ref(20)

const tgLogs = ref<TgLog[]>([])
const tgLogTotal = ref(0)
const tgLogPage = ref(1)
const tgLogSize = ref(20)
const tgLogBotId = ref<number | null>(null)
const tgLogPlatform = ref('')
const tgLogCommand = ref('')
/** Telegram 查询日志：时间范围 [from, to]，与后端 `from`/`to` ISO 一致 */
const tgLogTimeRange = ref<[string, string] | null>(null)
/** 与 TelegramQueryLogService 写入的 errorKind 一致 */
const tgLogErrorKind = ref('')
const tgLogSuccess = ref<'all' | 'yes' | 'no'>('all')
const tgLogTelegramUserId = ref('')
const tgLogChatId = ref('')


async function exportQueries() {
  try {
    const { data } = await api.get('/admin/export/queries')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'queries-export.json'; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('查询定义已导出')
  } catch { ElMessage.error('导出失败') }
}

function triggerImportQueries() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const items = JSON.parse(text)
      if (!Array.isArray(items)) { ElMessage.error('JSON 格式错误：应为数组'); return }
      const { data } = await api.post('/admin/export/import/queries', items)
      ElMessage.success(`导入完成：新增 ${data.created} 条，跳过 ${data.skipped} 条`)
      await loadQueries()
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '导入失败')
    }
  }
  input.click()
}

const botDlgOpen = ref(false)
const botEditId = ref<number | null>(null)
const botForm = ref({
  name: '',
  enabled: true,
})

type BotChannelRow = {
  id: number
  botId: number
  platform: string
  enabled: boolean
  webhookUrl: string
  credentialsSummary: string
}
const botChannels = ref<BotChannelRow[]>([])

// 全局渠道管理
const allChannels = ref<BotChannelRow[]>([])
const channelFilterPlatform = ref('')
const channelFilterBotId = ref<number | undefined>(undefined)

async function loadAllChannels() {
  try {
    const params: Record<string, any> = {}
    if (channelFilterPlatform.value) params.platform = channelFilterPlatform.value
    if (channelFilterBotId.value != null) params.botId = channelFilterBotId.value
    const { data } = await api.get<BotChannelRow[]>('/admin/channels', { params })
    allChannels.value = data
  } catch {
    allChannels.value = []
  }
}

async function toggleChannel(row: BotChannelRow) {
  try {
    await api.put(`/admin/channels/${row.id}/toggle`)
    ElMessage.success(`渠道 ${row.id} 已${row.enabled ? '禁用' : '启用'}`)
    await loadAllChannels()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function deleteChannel(row: BotChannelRow) {
  try {
    await ElMessageBox.confirm(`确认删除渠道 ${row.id}（${platformLabel(row.platform)}）？`, '删除确认', { type: 'warning' })
    await api.delete(`/admin/bots/${row.botId}/channels/${row.id}`)
    ElMessage.success('渠道已删除')
    await loadAllChannels()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || '删除失败')
    }
  }
}

// 新建渠道对话框
const addChannelDlgOpen = ref(false)
const addChannelForm = ref({
  botId: undefined as number | undefined,
  platform: 'TELEGRAM',
  botToken: '',
  telegramBotUsername: '',
  webhookSecretToken: '',
  appId: '',
  appSecret: '',
  corpId: '',
  agentId: undefined as number | undefined,
  callbackToken: '',
  encodingAesKey: '',
  signingSecret: '',
  publicKey: '',
})

function openAddChannel() {
  addChannelForm.value = {
    botId: undefined,
    platform: 'TELEGRAM',
    botToken: '',
    telegramBotUsername: '',
    webhookSecretToken: '',
    appId: '',
    appSecret: '',
    corpId: '',
    agentId: undefined,
    callbackToken: '',
    encodingAesKey: '',
    signingSecret: '',
    publicKey: '',
  }
  addChannelDlgOpen.value = true
}

function onChannelPlatformChange() {
  addChannelForm.value.botToken = ''
  addChannelForm.value.telegramBotUsername = ''
  addChannelForm.value.webhookSecretToken = ''
  addChannelForm.value.appId = ''
  addChannelForm.value.appSecret = ''
  addChannelForm.value.corpId = ''
  addChannelForm.value.agentId = undefined
  addChannelForm.value.callbackToken = ''
  addChannelForm.value.encodingAesKey = ''
  addChannelForm.value.signingSecret = ''
  addChannelForm.value.publicKey = ''
}

// 渠道详情对话框
const channelDetailDlgOpen = ref(false)
const channelDetail = ref<BotChannelRow | null>(null)
const channelWebhookStatus = ref<any>(null)
const channelWebhookLoading = ref(false)
const channelWebhookRegLoading = ref(false)
const channelWebhookPublicBase = ref('')
const channelTestTargetId = ref('')
const channelTestLoading = ref(false)
const channelTestResult = ref<{ success: boolean; message: string } | null>(null)

function openChannelDetail(row: BotChannelRow) {
  channelDetail.value = row
  channelWebhookStatus.value = null
  channelWebhookPublicBase.value = ''
  channelTestTargetId.value = ''
  channelTestResult.value = null
  channelDetailDlgOpen.value = true
}

async function loadChannelWebhookStatus() {
  if (!channelDetail.value) return
  channelWebhookLoading.value = true
  try {
    const { data } = await api.get(`/admin/channels/${channelDetail.value.id}/webhook-status`)
    channelWebhookStatus.value = data
  } catch (e: any) {
    channelWebhookStatus.value = { error: e?.response?.data?.message || '查询失败' }
  } finally {
    channelWebhookLoading.value = false
  }
}

async function registerChannelWebhook() {
  if (!channelDetail.value) return
  channelWebhookRegLoading.value = true
  try {
    const body: Record<string, string> = {}
    if (channelWebhookPublicBase.value.trim()) body.publicBaseUrl = channelWebhookPublicBase.value.trim()
    const { data } = await api.post(`/admin/channels/${channelDetail.value.id}/register-webhook`, body)
    if (data.telegramOk) {
      ElMessage.success(data.description || 'Webhook 已注册')
    } else {
      ElMessage.error(data.description || '注册失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '注册失败')
  } finally {
    channelWebhookRegLoading.value = false
  }
}

async function testChannel() {
  if (!channelDetail.value) return
  channelTestLoading.value = true
  channelTestResult.value = null
  try {
    const body: Record<string, string> = {}
    if (channelTestTargetId.value.trim()) body.targetId = channelTestTargetId.value.trim()
    const { data } = await api.post(`/admin/channels/${channelDetail.value.id}/test`, body)
    channelTestResult.value = data
    if (data.success) {
      ElMessage.success(data.message)
    } else {
      ElMessage.error(data.message)
    }
  } catch (e: any) {
    channelTestResult.value = { success: false, message: e?.response?.data?.message || '测试失败' }
    ElMessage.error('测试失败')
  } finally {
    channelTestLoading.value = false
  }
}

async function saveAddChannel() {
  const f = addChannelForm.value
  if (!f.botId) { ElMessage.warning('请选择机器人'); return }
  if (!f.platform) { ElMessage.warning('请选择平台'); return }

  const body: Record<string, any> = { platform: f.platform }

  if (f.platform === 'TELEGRAM') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.telegramBotUsername.trim()) body.telegramBotUsername = f.telegramBotUsername.trim()
    if (f.webhookSecretToken.trim()) body.webhookSecretToken = f.webhookSecretToken.trim()
  } else if (f.platform === 'LARK') {
    if (!f.appId.trim() || !f.appSecret.trim()) { ElMessage.warning('请填写 App ID 和 App Secret'); return }
    body.appId = f.appId.trim()
    body.appSecret = f.appSecret.trim()
  } else if (f.platform === 'DINGTALK') {
    if (!f.appSecret.trim()) { ElMessage.warning('请填写 App Secret'); return }
    body.appSecret = f.appSecret.trim()
  } else if (f.platform === 'WEWORK') {
    if (!f.corpId.trim() || !f.agentId || !f.callbackToken.trim() || !f.encodingAesKey.trim()) {
      ElMessage.warning('请填写所有必填字段'); return
    }
    body.corpId = f.corpId.trim()
    body.agentId = f.agentId
    body.callbackToken = f.callbackToken.trim()
    body.encodingAesKey = f.encodingAesKey.trim()
  } else if (f.platform === 'SLACK') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.signingSecret.trim()) body.signingSecret = f.signingSecret.trim()
  } else if (f.platform === 'DISCORD') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.publicKey.trim()) body.publicKey = f.publicKey.trim()
  }

  try {
    await api.post(`/admin/bots/${f.botId}/channels`, body)
    ElMessage.success('渠道已创建')
    addChannelDlgOpen.value = false
    await loadAllChannels()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  }
}

async function loadBotChannels(botId: number) {
  try {
    const { data } = await api.get<BotChannelRow[]>(`/admin/bots/${botId}/channels`)
    botChannels.value = data
  } catch {
    botChannels.value = []
  }
}

const dsDlgOpen = ref(false)
const dsEditId = ref<number | null>(null)
const dsSourceType = ref<'DATABASE' | 'API'>('DATABASE')
const dsApiAdvancedSections = ref<string[]>([])
const dsNameAutoManaged = ref(true)
const dsNameUpdatingByPreset = ref(false)
/** simple：主机+端口+库名；advanced：完整 JDBC URL */
const dsForm = ref({
  name: '',
  jdbcMode: 'simple' as 'simple' | 'advanced',
  mysqlHost: '127.0.0.1',
  mysqlPort: 3306,
  mysqlDatabase: '',
  jdbcUrl: '',
  username: '',
  passwordPlain: '',
  poolMax: 5,
  apiBaseUrl: '',
  apiPresetKey: '',
  authType: 'NONE',
  authConfigMode: 'simple' as 'simple' | 'advanced',
  apiAuthKeyName: '',
  authConfigJson: '',
  defaultHeadersJson: '',
  defaultQueryParamsJson: '',
  requestTimeoutMs: 5000,
  configJson: '',
})

const dsTestLoading = ref(false)

function emptyDatasourceForm() {
  return {
    name: '',
    jdbcMode: 'simple' as 'simple' | 'advanced',
    mysqlHost: '127.0.0.1',
    mysqlPort: 3306,
    mysqlDatabase: '',
    jdbcUrl: '',
    username: '',
    passwordPlain: '',
    poolMax: 5,
    apiBaseUrl: '',
    apiPresetKey: '',
    authType: 'NONE',
    authConfigMode: 'simple' as 'simple' | 'advanced',
    apiAuthKeyName: '',
    authConfigJson: '',
    defaultHeadersJson: '',
    defaultQueryParamsJson: '',
    requestTimeoutMs: 5000,
    configJson: '',
  }
}

function applyDatasourcePreset(presetKey: string) {
  const preset = findDatasourcePreset(presetKey)
  if (!preset) return
  if (dsEditId.value == null && (dsNameAutoManaged.value || !dsForm.value.name.trim())) {
    dsNameUpdatingByPreset.value = true
    dsForm.value.name = preset.title
  }
  dsForm.value.apiPresetKey = preset.key
  dsForm.value.apiBaseUrl = preset.baseUrl
  dsForm.value.authType = preset.authType
  dsForm.value.configJson = JSON.stringify({ healthcheckPath: preset.healthcheckPath }, null, 2)
}

watch(
  () => dsForm.value.name,
  (next, prev) => {
    if (!dsDlgOpen.value || dsEditId.value != null || next === prev) return
    if (dsNameUpdatingByPreset.value) {
      dsNameUpdatingByPreset.value = false
      return
    }
    dsNameAutoManaged.value = next.trim().length === 0
  },
)

function currentDatasourceType(datasourceId: number): 'DATABASE' | 'API' {
  return dsList.value.find((item) => item.id === datasourceId)?.sourceType === 'API' ? 'API' : 'DATABASE'
}

function resetVisualQueryEditor() {
  visualWizardRef.value?.resetVisualWizard()
  visualWizardRef.value?.resetWizardTestState()
  visualWizardRef.value?.setWizardStep(0)
}

function resetApiQueryEditor() {
  apiBuilderRef.value?.resetApiQueryBuilder()
}

function normalizeJsonInput(raw: string, label: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null
  try {
    return JSON.stringify(JSON.parse(trimmed))
  } catch {
    ElMessage.warning(`${label} 不是合法 JSON`)
    return '__INVALID__'
  }
}

function prettyJsonText(raw: string | null | undefined): string {
  if (!raw?.trim()) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function parseStringMapText(raw: string | null | undefined): Record<string, string> {
  if (!raw?.trim()) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.entries(parsed).reduce<Record<string, string>>((acc, [key, value]) => {
      if (value == null) return acc
      acc[key] = String(value)
      return acc
    }, {})
  } catch {
    return {}
  }
}

function syncApiAuthFieldsFromJson(raw: string | null | undefined) {
  const authConfig = parseStringMapText(raw)
  dsForm.value.apiAuthKeyName = authConfig.keyName ?? ''
}

function buildApiAuthConfigJson(): string | null | '__INVALID__' {
  if (dsForm.value.authConfigMode === 'advanced') {
    return normalizeJsonInput(dsForm.value.authConfigJson, '鉴权配置')
  }
  if (dsForm.value.authType === 'API_KEY_HEADER' || dsForm.value.authType === 'API_KEY_QUERY') {
    const keyName = dsForm.value.apiAuthKeyName.trim()
    if (!keyName) {
      ElMessage.warning('请填写 API Key 的参数名或 Header 名')
      return '__INVALID__'
    }
    return JSON.stringify({ keyName })
  }
  return null
}

const dsJdbcPreview = computed(() => {
  if (dsForm.value.jdbcMode !== 'simple') return ''
  try {
    return buildMysqlJdbcUrl(
      dsForm.value.mysqlHost,
      dsForm.value.mysqlPort,
      dsForm.value.mysqlDatabase,
    )
  } catch {
    return ''
  }
})

const dsAuthTypeHint = computed(() => {
  switch (dsForm.value.authType) {
    case 'BEARER_TOKEN':
      return '适合大多数 Token 型接口。通常只需要把 Token 填到下方密码框，系统会自动拼成 Authorization: Bearer ...。'
    case 'BASIC':
      return '适合传统账号密码接口。用户名填在下方用户名框，密码填在密码框，系统会自动生成 Basic Authorization。'
    case 'API_KEY_HEADER':
      return '适合要求在请求头中携带 API Key 的接口。通常只需要告诉系统 Header 名称，例如 X-API-Key。'
    case 'API_KEY_QUERY':
      return '适合要求把 API Key 放进 URL 参数的接口。通常只需要告诉系统参数名，例如 api_key。'
    default:
      return '公开接口可选择“无鉴权”。如果目标平台文档没有明确要求，一般不需要额外配置。'
  }
})

const dsAuthConfigPlaceholder = computed(() => {
  switch (dsForm.value.authType) {
    case 'API_KEY_HEADER':
    case 'API_KEY_QUERY':
      return '{"keyName":"X-API-Key"}'
    case 'BASIC':
      return '通常可留空'
    case 'BEARER_TOKEN':
      return '通常可留空'
    default:
      return '通常可留空'
  }
})

const dsAuthConfigHint = computed(() => {
  switch (dsForm.value.authType) {
    case 'API_KEY_HEADER':
    case 'API_KEY_QUERY':
      return '这里只需填写 keyName，真正的密钥值请放在密码框里，便于统一加密存储。'
    case 'BASIC':
      return 'Basic 模式下通常不需要额外 JSON；用户名和密码分别填写在账号、密码输入框即可。'
    case 'BEARER_TOKEN':
      return 'Bearer 模式下通常不需要额外 JSON；把 Token 填在密码框即可。'
    default:
      return '没有特殊要求时可留空。只有目标 API 文档明确要求额外配置时再填写。'
  }
})

const showSimpleApiAuthSecret = computed(
  () => dsForm.value.authType === 'BEARER_TOKEN' || dsForm.value.authType === 'API_KEY_HEADER' || dsForm.value.authType === 'API_KEY_QUERY',
)

const showSimpleApiAuthUsername = computed(() => dsForm.value.authType === 'BASIC')

const showSimpleApiAuthPassword = computed(() => dsForm.value.authType === 'BASIC')

const dsAuthSecretLabel = computed(() => {
  switch (dsForm.value.authType) {
    case 'BEARER_TOKEN':
      return 'Token'
    case 'API_KEY_HEADER':
    case 'API_KEY_QUERY':
      return 'API Key 密钥'
    default:
      return '密钥'
  }
})

const dsAuthSecretHint = computed(() => {
  switch (dsForm.value.authType) {
    case 'BEARER_TOKEN':
      return dsEditId.value != null ? '留空则沿用原有 Token；重新填写后会覆盖。' : '把平台发给你的 Token 粘贴进来即可。'
    case 'API_KEY_HEADER':
    case 'API_KEY_QUERY':
      return dsEditId.value != null ? '留空则沿用原有 API Key；重新填写后会覆盖。' : '把平台发给你的 API Key 粘贴进来即可。'
    default:
      return ''
  }
})

const dsAuthUsernameHint = computed(() => (dsEditId.value != null ? '留空会保留当前用户名。' : '填写 API 平台提供的账号名。'))

const dsAuthPasswordHint = computed(() => (dsEditId.value != null ? '留空则沿用原有密码；重新填写后会覆盖。' : '填写 API 平台提供的密码或 Secret。'))

watch(
  () => dsForm.value.authType,
  (type, prev) => {
    if (type === prev) return
    if (type === 'NONE') {
      dsForm.value.username = ''
      dsForm.value.passwordPlain = ''
      dsForm.value.apiAuthKeyName = ''
      if (dsForm.value.authConfigMode === 'simple') dsForm.value.authConfigJson = ''
      return
    }
    if (type === 'BEARER_TOKEN') {
      dsForm.value.username = ''
      dsForm.value.apiAuthKeyName = ''
      if (dsForm.value.authConfigMode === 'simple') dsForm.value.authConfigJson = ''
      return
    }
    if (type === 'BASIC') {
      dsForm.value.apiAuthKeyName = ''
      if (dsForm.value.authConfigMode === 'simple') dsForm.value.authConfigJson = ''
      return
    }
    if (type === 'API_KEY_HEADER') {
      dsForm.value.username = ''
      if (!dsForm.value.apiAuthKeyName) dsForm.value.apiAuthKeyName = 'X-API-Key'
      return
    }
    if (type === 'API_KEY_QUERY') {
      dsForm.value.username = ''
      if (!dsForm.value.apiAuthKeyName) dsForm.value.apiAuthKeyName = 'api_key'
    }
  },
)

watch(
  () => dsForm.value.jdbcMode,
  (mode, prev) => {
    if (prev === undefined || !dsDlgOpen.value) return
    if (mode === 'advanced' && prev === 'simple') {
      const h = dsForm.value.mysqlHost.trim()
      const d = dsForm.value.mysqlDatabase.trim()
      if (h && d) {
        dsForm.value.jdbcUrl = buildMysqlJdbcUrl(
          dsForm.value.mysqlHost,
          dsForm.value.mysqlPort,
          dsForm.value.mysqlDatabase,
        )
      }
    } else if (mode === 'simple' && prev === 'advanced') {
      const p = parseMysqlJdbcUrl(dsForm.value.jdbcUrl)
      if (p) {
        dsForm.value.mysqlHost = p.host
        dsForm.value.mysqlPort = Number(p.port) || 3306
        dsForm.value.mysqlDatabase = p.database
      }
    }
  },
)

watch(
  () => dsForm.value.apiBaseUrl,
  (next, prev) => {
    if (next === prev || dsSourceType.value !== 'API') return
    const preset = findDatasourcePreset(dsForm.value.apiPresetKey)
    if (!preset) return
    const normalizedNext = next.trim()
    if (!normalizedNext || normalizedNext === preset.baseUrl) return
    dsForm.value.apiPresetKey = ''
    const presetConfigJson = JSON.stringify({ healthcheckPath: preset.healthcheckPath }, null, 2)
    if (dsForm.value.configJson.trim() === presetConfigJson.trim()) {
      dsForm.value.configJson = ''
    }
    ElMessage.info('你已手动修改 API 基础地址，已自动解除模板绑定；若需要测试，请检查健康检查路径是否仍适用。')
  },
)

const qFormOpen = ref(false)
const qEditId = ref<number | null>(null)
/** SQL = 手写模板；VISUAL = 向导（服务端生成 sql_template） */
const qQueryMode = ref<'SQL' | 'VISUAL' | 'API'>('SQL')
const qForm = ref({
  datasourceId: 0,
  command: '',
  name: '',
  telegramMenuDescription: '',
  sqlTemplate: '',
  paramSchemaJson: '{"params":["orderNo"]}',
  timeoutMs: 5000,
  maxRows: 1,
  enabled: true,
  telegramReplyStyle: 'LIST' as TelegramReplyStyle,
  telegramJoinDelimiter: ' ',
  apiConfigJson: '',
  channelScopeJson: null as string | null, // null = 所有渠道
})
const visualWizardRef = ref<InstanceType<typeof VisualQueryWizard> | null>(null)
const apiBuilderRef = ref<InstanceType<typeof ApiQueryBuilder> | null>(null)
const pendingEditQuery = ref<Qd | null>(null)

// 当前机器人下的渠道列表（用于渠道作用域多选）
const queryBotChannels = computed(() => {
  if (!queryBotId.value) return []
  return allChannels.value.filter((ch) => ch.botId === queryBotId.value)
})

// 渠道作用域：JSON 字符串 ↔ 数组 双向绑定
const qFormChannelScope = computed({
  get: () => {
    try {
      const arr = JSON.parse(qForm.value.channelScopeJson || '[]')
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  },
  set: (val: number[]) => {
    qForm.value.channelScopeJson = val.length > 0 ? JSON.stringify(val) : null
  },
})

const positionalExampleDrafts = ref<string[]>([])
const lastPositionalParamsKey = ref('')

function safeParseParamSchema(json: string): { params: string[]; examples: string[] } {
  try {
    const o = JSON.parse(json || '{}') as { params?: unknown[]; examples?: unknown[] }
    const params = Array.isArray(o.params) ? o.params.map((x) => String(x)) : []
    const examples = Array.isArray(o.examples) ? o.examples.map((x) => String(x ?? '')) : []
    return { params, examples }
  } catch {
    return { params: [], examples: [] }
  }
}

const positionalParamNames = computed(() => {
  if (qQueryMode.value === 'API') return []
  return safeParseParamSchema(qForm.value.paramSchemaJson).params
})

function flushPositionalExamplesIntoParamSchemaJson() {
  if (qQueryMode.value === 'API') return
  const { params } = safeParseParamSchema(qForm.value.paramSchemaJson)
  if (params.length === 0) return
  const drafts = positionalExampleDrafts.value
  const ex = params.map((_, i) => (drafts[i] ?? '').trim())
  const o: Record<string, unknown> = { params: [...params] }
  if (ex.some((s) => s !== '')) {
    o.examples = ex
  }
  qForm.value.paramSchemaJson = JSON.stringify(o)
}

watch(qFormOpen, (open) => {
  if (!open || qQueryMode.value === 'API') return
  void nextTick(() => {
    if (!qFormOpen.value || qQueryMode.value === 'API') return
    const { params, examples } = safeParseParamSchema(qForm.value.paramSchemaJson)
    lastPositionalParamsKey.value = params.join('\0')
    positionalExampleDrafts.value = params.map((_, i) => examples[i] ?? '')
  })
})

watch(
  () => qForm.value.paramSchemaJson,
  (json) => {
    if (!qFormOpen.value || qQueryMode.value === 'API') return
    const { params, examples } = safeParseParamSchema(String(json))
    const key = params.join('\0')
    if (key !== lastPositionalParamsKey.value) {
      lastPositionalParamsKey.value = key
      positionalExampleDrafts.value = params.map((_, i) => examples[i] ?? '')
    } else if (params.length === positionalExampleDrafts.value.length) {
      positionalExampleDrafts.value = params.map(
        (_, i) => examples[i] ?? positionalExampleDrafts.value[i] ?? '',
      )
    }
  },
)

watch(
  () => qForm.value.datasourceId,
  async (datasourceId, previousId) => {
    if (!qFormOpen.value || !datasourceId || previousId == null || datasourceId === previousId) return
    const datasourceType = currentDatasourceType(datasourceId)
    if (datasourceType === 'API') {
      qQueryMode.value = 'API'
      qForm.value.sqlTemplate = ' '
      qForm.value.apiConfigJson = ''
      await nextTick()
      resetApiQueryEditor()
      return
    }
    if (qQueryMode.value === 'API') {
      qQueryMode.value = 'VISUAL'
      qForm.value.apiConfigJson = ''
      await nextTick()
      resetVisualQueryEditor()
    }
  },
)

/** 与 VisualQueryWizard 内 WIZARD_STEP_LAST 一致，用于抽屉底部「下一步」禁用 */
const VISUAL_WIZARD_STEP_LAST = 4

type VisualWizardExposed = InstanceType<typeof VisualQueryWizard> & {
  goVisualWizardPrev?: () => void
  goVisualWizardNext?: () => void
  wizardStep?: import('vue').Ref<number>
}

const visualFooterWizardStep = computed(() => {
  const inst = visualWizardRef.value as VisualWizardExposed | null
  const ws = inst?.wizardStep
  return ws != null ? unref(ws) : 0
})

function onVisualDrawerPrev() {
  ;(visualWizardRef.value as VisualWizardExposed | null)?.goVisualWizardPrev?.()
}

function onVisualDrawerNext() {
  ;(visualWizardRef.value as VisualWizardExposed | null)?.goVisualWizardNext?.()
}
/** 抽屉内「高级 SQL」模式的 SQL 测试参数与结果 */
const qWizardTestArgs = ref('')
const qWizardTestResult = ref('')
const qWizardTestRunning = ref(false)

const testOpen = ref(false)
const testQueryId = ref<number>(0)
/** 当前测试弹窗对应的行（用于向导模式 OR/UNION 重编译） */
const testQueryRow = ref<Qd | null>(null)
/** 列表/弹窗内测试：STORED=库内已保存 SQL；否则临时按策略从向导 JSON 重编译 */
const testSqlComposition = ref<'STORED' | 'LEGACY_OR' | 'UNION_ALL'>('STORED')
const testArgs = ref('')
const testResultText = ref('')
const testRunning = ref(false)

/** 列表「优化」— 索引建议弹窗 */
const listIndexAdviceOpen = ref(false)
const listIndexRow = ref<Qd | null>(null)
const listIndexAdviceLoading = ref(false)
const listIndexAdviceResult = ref<VisualIndexAdviceResult | null>(null)
/** 列表「优化」— OR/UNION 耗时弹窗 */
const listBenchDlgOpen = ref(false)
const listBenchRow = ref<Qd | null>(null)
const listBenchArgs = ref('')
const listBenchLoading = ref(false)
const listBenchResult = ref<VisualBenchmarkResult | null>(null)

function isVisualQueryForOptimize(row: Qd) {
  return row.queryMode === 'VISUAL' && !!(row.visualConfigJson && row.visualConfigJson.trim())
}

function visualSearchOrCount(row: Qd): number {
  if (!row.visualConfigJson?.trim()) return 0
  try {
    const v = JSON.parse(row.visualConfigJson) as { searchOrColumns?: string[] }
    return Array.isArray(v.searchOrColumns) ? v.searchOrColumns.length : 0
  } catch {
    return 0
  }
}

function openTestDialog(row: Qd) {
  testQueryId.value = row.id
  testQueryRow.value = row
  testSqlComposition.value = 'STORED'
  testArgs.value = ''
  testResultText.value = ''
  testOpen.value = true
}

function onListIndexAdviceClosed() {
  listIndexRow.value = null
  listIndexAdviceResult.value = null
}

function onListBenchClosed() {
  listBenchRow.value = null
  listBenchResult.value = null
}

function handleQueryOptimizeCommand(row: Qd, cmd: string) {
  if (!isVisualQueryForOptimize(row)) {
    ElMessage.warning('仅向导模式且已保存向导 JSON 的查询可使用「优化」')
    return
  }
  if (cmd === 'index') {
    listIndexRow.value = row
    listIndexAdviceResult.value = null
    listIndexAdviceOpen.value = true
  } else if (cmd === 'bench') {
    if (visualSearchOrCount(row) < 2) {
      ElMessage.warning('对比耗时需至少两个 OR 检索列（与抽屉内规则一致）')
      return
    }
    listBenchRow.value = row
    listBenchArgs.value = ''
    listBenchResult.value = null
    listBenchDlgOpen.value = true
  }
}

async function copyTestResult() {
  if (!testResultText.value) return
  try {
    await navigator.clipboard.writeText(testResultText.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

const fieldMainOpen = ref(false)
const fieldQueryId = ref(0)
const fieldRows = ref<FieldMap[]>([])
const fieldTableRef = ref<InstanceType<typeof ElTable> | null>(null)
const fieldSortSaving = ref(false)
let fieldSortable: Sortable | null = null

function destroyFieldSortable() {
  fieldSortable?.destroy()
  fieldSortable = null
}

function fieldTableBodyEl(): HTMLElement | null {
  const root = fieldTableRef.value?.$el as HTMLElement | undefined
  return root?.querySelector?.('.el-table__body-wrapper tbody') ?? null
}

async function initFieldSortable() {
  destroyFieldSortable()
  await nextTick()
  const tbody = fieldTableBodyEl()
  if (!tbody || fieldRows.value.length === 0) {
    return
  }
  fieldSortable = Sortable.create(tbody, {
    handle: '.field-drag-handle',
    animation: 160,
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) {
        return
      }
      const next = [...fieldRows.value]
      const [moved] = next.splice(oldIndex, 1)
      next.splice(newIndex, 0, moved)
      void persistFieldSortOrders(next)
    },
  })
}

async function persistFieldSortOrders(ordered: FieldMap[]) {
  fieldSortSaving.value = true
  const qid = fieldQueryId.value
  try {
    await Promise.all(
      ordered.map((row, i) =>
        api.put(`/admin/queries/${qid}/fields/${row.id}`, {
          columnName: row.columnName,
          label: row.label,
          sortOrder: i,
          maskType: row.maskType || 'NONE',
          formatType: row.formatType ?? null,
          displayPipelineJson: row.displayPipelineJson ?? null,
        }),
      ),
    )
    fieldRows.value = ordered.map((r, i) => ({ ...r, sortOrder: i }))
    ElMessage.success('展示顺序已保存')
  } catch {
    ElMessage.error('保存顺序失败，请刷新后重试')
    await loadFields()
  } finally {
    fieldSortSaving.value = false
    destroyFieldSortable()
    await nextTick()
    void initFieldSortable()
  }
}

const fieldFormOpen = ref(false)
const fieldEditId = ref<number | null>(null)
const fieldForm = ref({
  columnName: '',
  label: '',
  sortOrder: 0,
  maskType: 'NONE',
  formatType: '',
  pipelineSteps: [] as DisplayPipelineStep[],
})

const fieldPipelineJsonPreview = computed(() => {
  try {
    const j = buildDisplayPipelineJson(fieldForm.value.pipelineSteps)
    return j ?? ''
  } catch {
    return '(超过 24000 字符上限)'
  }
})

function addFieldPipelineStep() {
  fieldForm.value.pipelineSteps.push({ op: 'trim' })
}

function removeFieldPipelineStep(index: number) {
  fieldForm.value.pipelineSteps.splice(index, 1)
}

function applyFieldPayLinkPipelinePreset() {
  fieldForm.value.pipelineSteps = payLinkPipelinePreset()
}

async function loadBots() {
  const { data } = await api.get<Bot[]>('/admin/bots')
  bots.value = data
  if (!queryBotId.value && data.length) queryBotId.value = data[0].id
  if (!allowBotId.value && data.length) allowBotId.value = data[0].id
}

async function loadDs() {
  const { data } = await api.get<Ds[]>('/admin/datasources')
  dsList.value = data
}

async function loadQueries() {
  if (!queryBotId.value) {
    queries.value = []
    return
  }
  const { data } = await api.get<Qd[]>(`/admin/bots/${queryBotId.value}/queries`)
  queries.value = data
}

async function loadAudits() {
  const { data } = await api.get<{
    records: Audit[]
    total: number
    page: number
    size: number
  }>('/admin/audit-logs', { params: { page: auditPage.value, size: auditSize.value } })
  audits.value = data.records
  auditTotal.value = data.total
}

/** 仅接受整数数字串，非法则忽略该筛选项 */
function parseOptionalLongId(raw: string): number | undefined {
  const t = raw.trim()
  if (!t) return undefined
  const n = Number(t)
  if (!Number.isFinite(n) || !Number.isInteger(n)) return undefined
  return n
}

function csvEscapeCell(val: unknown): string {
  const s = val == null ? '' : String(val)
  if (/[",\n\r]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`
  }
  return s
}

function exportTgLogsCsv() {
  const rows = tgLogs.value
  if (!rows.length) {
    ElMessage.warning('当前页无数据，请先刷新或调整筛选条件')
    return
  }
  const header = [
    'created_at',
    'bot_id',
    'command',
    'telegram_user_id',
    'chat_id',
    'query_definition_id',
    'success',
    'error_kind',
    'duration_ms',
    'detail',
  ]
  const lines = [
    header.join(','),
    ...rows.map((r) =>
      [
        r.createdAt,
        r.botId,
        r.command,
        r.telegramUserId,
        r.chatId,
        r.queryDefinitionId ?? '',
        r.success ? '1' : '0',
        r.errorKind,
        r.durationMs ?? '',
        r.detail ?? '',
      ]
        .map(csvEscapeCell)
        .join(','),
    ),
  ]
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `telegram-query-log-page-${tgLogPage.value}.csv`
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success('已导出当前页 CSV')
}

async function loadTgLogs() {
  const uidText = tgLogTelegramUserId.value.trim()
  if (uidText && parseOptionalLongId(uidText) === undefined) {
    ElMessage.warning('「用户 ID」须为整数数字（可含负号）')
    return
  }
  const chatText = tgLogChatId.value.trim()
  if (chatText && parseOptionalLongId(chatText) === undefined) {
    ElMessage.warning('「会话 chat_id」须为整数数字（超级群常为负数）')
    return
  }
  const range = tgLogTimeRange.value
  const from = range?.[0]?.trim() || undefined
  const to = range?.[1]?.trim() || undefined
  const { data } = await api.get<{
    records: TgLog[]
    total: number
    page: number
    size: number
  }>('/admin/command-logs', {
    params: {
      page: tgLogPage.value,
      size: tgLogSize.value,
      botId: tgLogBotId.value ?? undefined,
      platform: tgLogPlatform.value || undefined,
      command: tgLogCommand.value.trim() || undefined,
      from,
      to,
      errorKind: tgLogErrorKind.value.trim() || undefined,
      success:
        tgLogSuccess.value === 'all'
          ? undefined
          : tgLogSuccess.value === 'yes'
            ? true
            : false,
      externalUserId: tgLogTelegramUserId.value?.trim() || undefined,
      externalChatId: tgLogChatId.value?.trim() || undefined,
    },
  })
  tgLogs.value = data.records
  tgLogTotal.value = data.total
}

onMounted(async () => {
  pageLoading.value = true
  try {
    await loadBots()
    await loadDs()
    await loadQueries()
    await loadAudits()
    await loadTgLogs()
    await loadAllChannels()
  } catch {
    /* 错误提示由 api client 拦截器统一处理 */
  } finally {
    pageLoading.value = false
  }
})

watch([channelFilterPlatform, channelFilterBotId], () => {
  loadAllChannels()
})

function openNewBot() {
  botChannels.value = []
  botEditId.value = null
  botForm.value = {
    name: '',
    enabled: true,
  }
  botDlgOpen.value = true
}

async function openEditBot(row: Bot) {
  const { data } = await api.get<Bot>(`/admin/bots/${row.id}`)
  botEditId.value = data.id
  botForm.value = {
    name: data.name,
    enabled: data.enabled,
  }
  botDlgOpen.value = true
  await loadBotChannels(data.id)
}

async function saveBot() {
  if (!botForm.value.name.trim()) {
    ElMessage.warning('请填写机器人名称')
    return
  }
  if (botEditId.value != null) {
    await api.put(`/admin/bots/${botEditId.value}`, {
      name: botForm.value.name,
      enabled: botForm.value.enabled,
    })
    ElMessage.success('机器人已更新')
    await loadBotChannels(botEditId.value)
  } else {
    await api.post('/admin/bots', {
      name: botForm.value.name.trim(),
      enabled: botForm.value.enabled,
    })
    ElMessage.success('机器人已创建')
  }
  botDlgOpen.value = false
  await loadBots()
  await loadQueries()
}

async function deleteBot(row: Bot) {
  try {
    await ElMessageBox.confirm('确定删除该机器人？其下查询定义等请先确认已处理。', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  const deletedId = row.id
  await api.delete(`/admin/bots/${deletedId}`)
  await loadBots()
  if (queryBotId.value === deletedId) {
    queryBotId.value = bots.value.length ? bots.value[0].id : null
  }
  if (allowBotId.value === deletedId) {
    allowBotId.value = bots.value.length ? bots.value[0].id : null
  }
  await loadQueries()
  ElMessage.success('已删除')
}

// 机器人详情对话框
const botDetailDlgOpen = ref(false)
const botDetail = ref<any>(null)
const botDetailLoading = ref(false)

async function openBotDetail(row: Bot) {
  botDetailDlgOpen.value = true
  botDetailLoading.value = true
  botDetail.value = null
  try {
    const { data } = await api.get(`/admin/bots/${row.id}/detail`)
    botDetail.value = data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载详情失败')
    botDetailDlgOpen.value = false
  } finally {
    botDetailLoading.value = false
  }
}

function openNewDs() {
  dsEditId.value = null
  dsSourceType.value = 'DATABASE'
   dsApiAdvancedSections.value = []
  dsNameAutoManaged.value = true
  dsNameUpdatingByPreset.value = false
  dsForm.value = emptyDatasourceForm()
  dsDlgOpen.value = true
}

async function openEditDs(row: Ds) {
  const { data } = await api.get<Ds>(`/admin/datasources/${row.id}`)
  dsEditId.value = data.id
  dsSourceType.value = data.sourceType === 'API' ? 'API' : 'DATABASE'
  dsApiAdvancedSections.value = []
  dsNameAutoManaged.value = false
  dsNameUpdatingByPreset.value = false
  const jdbcUrl = data.jdbcUrl ?? ''
  const username = data.username ?? ''
  const parsed = parseMysqlJdbcUrl(jdbcUrl)
  dsForm.value = {
    name: data.name,
    jdbcMode: parsed ? 'simple' : 'advanced',
    mysqlHost: parsed?.host ?? '127.0.0.1',
    mysqlPort: parsed ? Number(parsed.port) || 3306 : 3306,
    mysqlDatabase: parsed?.database ?? '',
    jdbcUrl,
    username,
    passwordPlain: '',
    poolMax: data.poolMax,
    apiBaseUrl: data.apiBaseUrl ?? '',
    apiPresetKey: data.apiPresetKey ?? '',
    authType: data.authType ?? 'NONE',
    authConfigMode: 'simple',
    apiAuthKeyName: '',
    authConfigJson: prettyJsonText(data.authConfigJson),
    defaultHeadersJson: prettyJsonText(data.defaultHeadersJson),
    defaultQueryParamsJson: prettyJsonText(data.defaultQueryParamsJson),
    requestTimeoutMs: data.requestTimeoutMs ?? 5000,
    configJson: prettyJsonText(data.configJson),
  }
  syncApiAuthFieldsFromJson(data.authConfigJson)
  dsDlgOpen.value = true
}

function resolveDsJdbcUrl(): string | null {
  if (dsForm.value.jdbcMode === 'simple') {
    const h = dsForm.value.mysqlHost.trim()
    const d = dsForm.value.mysqlDatabase.trim()
    if (!h || !d) {
      ElMessage.warning('请填写数据库地址与库名')
      return null
    }
    return buildMysqlJdbcUrl(dsForm.value.mysqlHost, dsForm.value.mysqlPort, dsForm.value.mysqlDatabase)
  }
  const u = dsForm.value.jdbcUrl.trim()
  if (!u) {
    ElMessage.warning('请填写 JDBC URL')
    return null
  }
  return u
}

async function testDsConnection() {
  if (dsSourceType.value === 'API') {
    if (!dsForm.value.name.trim()) {
      ElMessage.warning('请填写数据源名称')
      return
    }
    if (!dsForm.value.apiBaseUrl.trim()) {
      ElMessage.warning('请填写 API 基础地址')
      return
    }
    const authConfigJson = buildApiAuthConfigJson()
    const defaultHeadersJson = normalizeJsonInput(dsForm.value.defaultHeadersJson, '默认请求头')
    const defaultQueryParamsJson = normalizeJsonInput(dsForm.value.defaultQueryParamsJson, '默认请求参数')
    const configJson = normalizeJsonInput(dsForm.value.configJson, '高级配置')
    if ([authConfigJson, defaultHeadersJson, defaultQueryParamsJson, configJson].includes('__INVALID__')) return
    const body: Record<string, unknown> = {
      sourceType: 'API',
      name: dsForm.value.name.trim(),
      apiBaseUrl: dsForm.value.apiBaseUrl.trim(),
      apiPresetKey: dsForm.value.apiPresetKey.trim() || null,
      authType: dsForm.value.authType,
      username: dsForm.value.authType === 'BASIC' ? dsForm.value.username.trim() : null,
      passwordPlain: dsForm.value.passwordPlain.trim() || null,
      authConfigJson,
      defaultHeadersJson,
      defaultQueryParamsJson,
      requestTimeoutMs: dsForm.value.requestTimeoutMs,
      configJson,
    }
    if (dsEditId.value != null) body.id = dsEditId.value
    dsTestLoading.value = true
    try {
      await api.post('/admin/datasource-connection/test', body)
      ElMessage.success('API 连通成功')
    } catch {
      /* 错误由 api 拦截器提示 */
    } finally {
      dsTestLoading.value = false
    }
    return
  }
  const jdbcUrl = resolveDsJdbcUrl()
  if (jdbcUrl == null) return
  if (!dsForm.value.username.trim()) {
    ElMessage.warning('请填写数据库用户')
    return
  }
  if (dsEditId.value == null && !dsForm.value.passwordPlain.trim()) {
    ElMessage.warning('新建数据源测试时请填写密码')
    return
  }
  const body: Record<string, unknown> = {
    jdbcUrl,
    username: dsForm.value.username.trim(),
  }
  if (dsForm.value.passwordPlain.trim()) {
    body.passwordPlain = dsForm.value.passwordPlain
  }
  if (dsEditId.value != null) {
    body.id = dsEditId.value
  }
  dsTestLoading.value = true
  try {
    await api.post('/admin/datasource-connection/test', body)
    ElMessage.success('连接成功')
  } catch {
    /* 错误由 api 拦截器提示 */
  } finally {
    dsTestLoading.value = false
  }
}

async function saveDs() {
  if (!dsForm.value.name.trim()) {
    ElMessage.warning('请填写数据源名称')
    return
  }
  if (dsSourceType.value === 'API') {
    if (!dsForm.value.apiBaseUrl.trim()) {
      ElMessage.warning('请填写 API 基础地址')
      return
    }
    const authConfigJson = buildApiAuthConfigJson()
    const defaultHeadersJson = normalizeJsonInput(dsForm.value.defaultHeadersJson, '默认请求头')
    const defaultQueryParamsJson = normalizeJsonInput(dsForm.value.defaultQueryParamsJson, '默认请求参数')
    const configJson = normalizeJsonInput(dsForm.value.configJson, '高级配置')
    if ([authConfigJson, defaultHeadersJson, defaultQueryParamsJson, configJson].includes('__INVALID__')) return

    const body = {
      name: dsForm.value.name.trim(),
      sourceType: 'API',
      apiBaseUrl: dsForm.value.apiBaseUrl.trim(),
      apiPresetKey: dsForm.value.apiPresetKey.trim() || null,
      authType: dsForm.value.authType,
      username: dsForm.value.authType === 'BASIC' ? dsForm.value.username.trim() : null,
      passwordPlain: dsForm.value.passwordPlain.trim() || null,
      authConfigJson,
      defaultHeadersJson,
      defaultQueryParamsJson,
      requestTimeoutMs: dsForm.value.requestTimeoutMs,
      configJson,
    }
    if (dsEditId.value != null) {
      await api.put(`/admin/datasources/${dsEditId.value}`, body)
      ElMessage.success('API 数据源已更新')
    } else {
      await api.post('/admin/datasources', body)
      ElMessage.success('API 数据源已创建')
    }
    dsDlgOpen.value = false
    await loadDs()
    return
  }
  const jdbcUrl = resolveDsJdbcUrl()
  if (jdbcUrl == null) return
  if (dsEditId.value == null && !dsForm.value.passwordPlain.trim()) {
    ElMessage.warning('新建数据源请填写数据库密码')
    return
  }

  if (dsEditId.value != null) {
    const body: Record<string, unknown> = {
      name: dsForm.value.name,
      jdbcUrl,
      username: dsForm.value.username,
      poolMax: dsForm.value.poolMax,
    }
    if (dsForm.value.passwordPlain.trim()) {
      body.passwordPlain = dsForm.value.passwordPlain
    }
    await api.put(`/admin/datasources/${dsEditId.value}`, body)
    ElMessage.success('数据源已更新')
  } else {
    await api.post('/admin/datasources', {
      name: dsForm.value.name,
      jdbcUrl,
      username: dsForm.value.username,
      passwordPlain: dsForm.value.passwordPlain,
      poolMax: dsForm.value.poolMax,
    })
    ElMessage.success('数据源已创建')
  }
  dsDlgOpen.value = false
  await loadDs()
}

async function deleteDs(row: Ds) {
  try {
    await ElMessageBox.confirm(
      '确定删除该数据源？若仍被查询定义引用，数据库可能拒绝删除。',
      '确认删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await api.delete(`/admin/datasources/${row.id}`)
  } catch {
    ElMessage.error('删除失败（可能被查询定义引用）')
    return
  }
  await loadDs()
  ElMessage.success('已删除')
}

async function postVisualBenchmark(
  datasourceId: number,
  visualConfigJson: string,
  maxRows: number,
  timeoutMs: number,
  argsLine: string,
) {
  const parts = argsLine.trim() ? argsLine.trim().split(/\s+/) : []
  return api.post<VisualBenchmarkResult>(`/admin/datasources/${datasourceId}/visual-query/benchmark`, {
    visualConfigJson,
    maxRows: Math.min(200, Math.max(1, maxRows)),
    timeoutMs: Math.min(60_000, Math.max(500, timeoutMs)),
    args: parts,
  })
}

async function postIndexAdvice(datasourceId: number, visualConfigJson: string) {
  return api.post<VisualIndexAdviceResult>(`/admin/datasources/${datasourceId}/visual-query/index-advice`, {
    visualConfigJson,
  })
}

async function fetchListIndexAdvice() {
  const row = listIndexRow.value
  if (!row?.visualConfigJson?.trim()) return
  listIndexAdviceLoading.value = true
  try {
    const { data } = await postIndexAdvice(row.datasourceId, row.visualConfigJson)
    listIndexAdviceResult.value = data
  } finally {
    listIndexAdviceLoading.value = false
  }
}

async function runListBench() {
  const row = listBenchRow.value
  if (!row?.visualConfigJson?.trim()) return
  listBenchLoading.value = true
  try {
    const { data } = await postVisualBenchmark(
      row.datasourceId,
      row.visualConfigJson,
      row.maxRows,
      row.timeoutMs,
      listBenchArgs.value,
    )
    listBenchResult.value = data
  } finally {
    listBenchLoading.value = false
  }
}

async function copyListIndexDdl() {
  const lines = listIndexAdviceResult.value?.ddlStatements ?? []
  const text = lines.join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制 DDL 到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function runWizardSqlTest() {
  if (!queryBotId.value || qEditId.value == null) {
    ElMessage.warning('请先保存查询，再运行测试')
    return
  }
  const parts = qWizardTestArgs.value.trim() ? qWizardTestArgs.value.trim().split(/\s+/) : []
  const body = { args: parts }
  qWizardTestRunning.value = true
  qWizardTestResult.value = ''
  try {
    const { data } = await api.post(`/admin/queries/${qEditId.value}/test`, body)
    qWizardTestResult.value = JSON.stringify(data, null, 2)
    ElMessage.success('已执行，结果见下方')
  } catch {
    /* 拦截器已提示 */
  } finally {
    qWizardTestRunning.value = false
  }
}

async function copyWizardTestResult() {
  if (!qWizardTestResult.value) return
  try {
    await navigator.clipboard.writeText(qWizardTestResult.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}

function openNewQuery() {
  if (!dsList.value.length) {
    ElMessage.warning('请先创建数据源')
    return
  }
  const firstDatasource = dsList.value[0]
  qEditId.value = null
  qQueryMode.value = firstDatasource.sourceType === 'API' ? 'API' : 'VISUAL'
  qForm.value = {
    datasourceId: firstDatasource.id,
    command: '',
    name: '',
    telegramMenuDescription: '',
    sqlTemplate: ' ',
    paramSchemaJson: '{"params":[]}',
    timeoutMs: 5000,
    maxRows: 1,
    enabled: true,
    telegramReplyStyle: 'LIST',
    telegramJoinDelimiter: ' ',
    apiConfigJson: '',
    channelScopeJson: null,
  }
  qWizardTestArgs.value = ''
  qWizardTestResult.value = ''
  qFormOpen.value = true
  void nextTick(() => {
    resetVisualQueryEditor()
    resetApiQueryEditor()
  })
}

function random4Digits(): string {
  return Math.floor(1000 + Math.random() * 9000).toString()
}

function normalizeCommandHint(raw: string): string {
  return raw.trim().replace(/^\/+/, '').replace(/\s+/g, '_').replace(/[^\w]/g, '_')
}

function parseTelegramReplyStyle(raw?: string | null): { style: TelegramReplyStyle; delimiter: string } {
  const text = (raw ?? '').trim()
  if (!text) return { style: 'LIST', delimiter: ' ' }
  if (text.toUpperCase().startsWith('VALUES_JOIN_CUSTOM:')) {
    return { style: 'VALUES_JOIN_CUSTOM', delimiter: text.slice('VALUES_JOIN_CUSTOM:'.length) || ' ' }
  }
  return { style: (text as TelegramReplyStyle) ?? 'LIST', delimiter: ' ' }
}

function buildTelegramReplyStylePayload(style: TelegramReplyStyle, delimiter: string): string {
  if (style !== 'VALUES_JOIN_CUSTOM') return style
  const d = (delimiter ?? '').trim()
  return `VALUES_JOIN_CUSTOM:${d || ' '}`
}

function suggestUniqueCommand(baseHint: string): string {
  const base = normalizeCommandHint(baseHint)
  if (!base) return ''
  const used = new Set(
    queries.value
      .filter((q) => qEditId.value == null || q.id !== qEditId.value)
      .map((q) => q.command.trim().toLowerCase())
      .filter((x) => x.length > 0),
  )
  if (!used.has(base.toLowerCase())) return base
  // Append random 4-digit suffix on duplicate command names.
  for (let i = 0; i < 20; i++) {
    const candidate = `${base}_${random4Digits()}`
    if (!used.has(candidate.toLowerCase())) return candidate
  }
  return `${base}_${Date.now().toString().slice(-4)}`
}

function onApiPresetSelected(payload: ApiQueryPresetAppliedPayload | string) {
  const hint = typeof payload === 'string' ? payload : payload.commandHint
  const displayName = typeof payload === 'string' ? '' : payload.queryDisplayName
  const candidate = suggestUniqueCommand(hint)
  if (!candidate) return
  qForm.value.command = candidate
  if (displayName) {
    qForm.value.name = displayName
  }
}

async function openEditQuery(row: Qd) {
  if (!queryBotId.value) return
  const { data } = await api.get<Qd>(`/admin/bots/${queryBotId.value}/queries/${row.id}`)
  qEditId.value = data.id
  qQueryMode.value = data.queryMode === 'VISUAL' ? 'VISUAL' : data.queryMode === 'API' ? 'API' : 'SQL'
  const parsedReplyStyle = parseTelegramReplyStyle(data.telegramReplyStyle)
  qForm.value = {
    datasourceId: data.datasourceId,
    command: data.command,
    name: data.name?.trim() ? String(data.name) : '',
    telegramMenuDescription: data.telegramMenuDescription?.trim() ? String(data.telegramMenuDescription) : '',
    sqlTemplate: data.sqlTemplate,
    paramSchemaJson: data.paramSchemaJson ?? '{"params":[]}',
    timeoutMs: data.timeoutMs,
    maxRows: data.maxRows,
    enabled: data.enabled,
    telegramReplyStyle: parsedReplyStyle.style,
    telegramJoinDelimiter: parsedReplyStyle.delimiter,
    apiConfigJson: data.apiConfigJson ?? '',
    channelScopeJson: data.channelScopeJson ?? null,
  }
  qWizardTestArgs.value = ''
  qWizardTestResult.value = ''
  pendingEditQuery.value = data
  qFormOpen.value = true
  await hydrateEditQueryState()
}

async function hydrateEditQueryState() {
  const data = pendingEditQuery.value
  if (!data) return
  // Drawer uses destroy-on-close + lazy render; first open may need extra ticks.
  for (let i = 0; i < 3; i++) {
    await nextTick()
  }
  if (qQueryMode.value === 'VISUAL' && data.visualConfigJson?.trim()) {
    try {
      await visualWizardRef.value?.hydrateFromSavedJson(data.visualConfigJson)
    } catch {
      ElMessage.error('向导配置 JSON 无法解析，请改用「高级 SQL」或重新配置')
      qQueryMode.value = 'SQL'
    }
  } else if (qQueryMode.value === 'VISUAL') {
    visualWizardRef.value?.resetVisualWizard()
  } else if (qQueryMode.value === 'API' && data.apiConfigJson?.trim()) {
    apiBuilderRef.value?.hydrateFromSavedJson(data.apiConfigJson)
    apiBuilderRef.value?.syncPreviewArgsFromParamSchema(data.paramSchemaJson ?? '')
  } else if (qQueryMode.value === 'API') {
    resetApiQueryEditor()
  }
  visualWizardRef.value?.resetWizardTestState()
  visualWizardRef.value?.setWizardStep(0)
  pendingEditQuery.value = null
}

function onQueryDrawerOpened() {
  if (pendingEditQuery.value) {
    void hydrateEditQueryState()
  }
}

async function saveQuery() {
  const telegramReplyStylePayload = buildTelegramReplyStylePayload(
    qForm.value.telegramReplyStyle,
    qForm.value.telegramJoinDelimiter,
  )
  if (!queryBotId.value) return
  if (qQueryMode.value !== 'API') {
    flushPositionalExamplesIntoParamSchemaJson()
  }
  let command = qForm.value.command.trim()
  if (!command) {
    ElMessage.warning('请填写命令')
    return
  }
  if (command.startsWith('/')) {
    command = command.slice(1)
    ElMessage.info('命令不需要加 / 前缀，已自动去掉')
  }
  if (!qForm.value.datasourceId || !dsList.value.some((d) => d.id === qForm.value.datasourceId)) {
    ElMessage.warning('请选择有效的数据源')
    return
  }

  const datasourceType = currentDatasourceType(qForm.value.datasourceId)
  if (datasourceType === 'API' && qQueryMode.value !== 'API') {
    qQueryMode.value = 'API'
  }
  if (datasourceType === 'DATABASE' && qQueryMode.value === 'API') {
    ElMessage.warning('数据库数据源不能保存为 API 查询，请切换数据源或改用 SQL/向导模式')
    return
  }

  let body: Record<string, unknown>
  if (qQueryMode.value === 'VISUAL') {
    const wiz = visualWizardRef.value
    if (!wiz?.validateVisualSavePreconditions()) return
    let visualJson: string
    try {
      visualJson = wiz.buildVisualConfigJson()
    } catch (e) {
      if (e instanceof Error && e.message === 'ENUM_JSON') {
        ElMessage.warning('枚举映射 JSON 格式不正确')
      } else {
        ElMessage.warning('向导配置无效')
      }
      return
    }
    body = {
      datasourceId: qForm.value.datasourceId,
      command,
      name: qForm.value.name?.trim() || null,
      telegramMenuDescription: qForm.value.telegramMenuDescription?.trim() || null,
      queryMode: 'VISUAL',
      visualConfigJson: visualJson,
      sqlTemplate: ' ',
      paramSchemaJson: qForm.value.paramSchemaJson,
      timeoutMs: qForm.value.timeoutMs,
      maxRows: qForm.value.maxRows,
      enabled: qForm.value.enabled,
      telegramReplyStyle: telegramReplyStylePayload,
      channelScopeJson: qForm.value.channelScopeJson,
    }
  } else if (qQueryMode.value === 'API') {
    const builder = apiBuilderRef.value
    if (!builder?.validateApiSavePreconditions()) return
    body = {
      datasourceId: qForm.value.datasourceId,
      command,
      name: qForm.value.name?.trim() || null,
      telegramMenuDescription: qForm.value.telegramMenuDescription?.trim() || null,
      queryMode: 'API',
      sqlTemplate: ' ',
      paramSchemaJson: builder.buildParamSchemaJson(),
      apiConfigJson: builder.buildApiConfigJson(),
      timeoutMs: qForm.value.timeoutMs,
      maxRows: qForm.value.maxRows,
      enabled: qForm.value.enabled,
      telegramReplyStyle: telegramReplyStylePayload,
      channelScopeJson: qForm.value.channelScopeJson,
    }
  } else {
    if (!qForm.value.sqlTemplate.trim()) {
      ElMessage.warning('请填写 SQL 模板')
      return
    }
    try {
      JSON.parse(qForm.value.paramSchemaJson)
    } catch {
      ElMessage.warning('参数 JSON 格式不正确')
      return
    }
    body = {
      ...qForm.value,
      command,
      queryMode: 'SQL',
      apiConfigJson: null,
      telegramReplyStyle: telegramReplyStylePayload,
      channelScopeJson: qForm.value.channelScopeJson,
    }
  }

  if (qEditId.value != null) {
    await api.put(`/admin/bots/${queryBotId.value}/queries/${qEditId.value}`, body)
    ElMessage.success('查询定义已更新')
  } else {
    await api.post(`/admin/bots/${queryBotId.value}/queries`, body)
    ElMessage.success('查询定义已创建')
  }
  qFormOpen.value = false
  await loadQueries()
}

async function deleteQuery(row: Qd) {
  if (!queryBotId.value) return
  try {
    await ElMessageBox.confirm('确定删除该查询定义？关联的字段映射将一并删除。', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await api.delete(`/admin/bots/${queryBotId.value}/queries/${row.id}`)
  await loadQueries()
  ElMessage.success('已删除')
}

async function runTest() {
  testResultText.value = ''
  const args = testArgs.value.trim() ? testArgs.value.trim().split(/\s+/) : []
  const body: { args: string[]; orCompositionStrategy?: string; visualConfigJsonOverride?: string } = { args }
  const row = testQueryRow.value
  if (row?.queryMode === 'VISUAL' && row.visualConfigJson?.trim() && testSqlComposition.value !== 'STORED') {
    body.orCompositionStrategy = testSqlComposition.value
  }
  testRunning.value = true
  try {
    const { data } = await api.post(`/admin/queries/${testQueryId.value}/test`, body)
    testResultText.value = JSON.stringify(data, null, 2)
    ElMessage.success('查询已执行，结果见下方')
  } catch {
    /* 错误提示由 api 拦截器统一处理 */
  } finally {
    testRunning.value = false
  }
}

const allowRows = ref<any[]>([])
const allowBatchIds = ref('')
const allowBatchChannelId = ref<number | null>(null)

async function loadAllow() {
  if (!allowBotId.value) return
  const { data } = await api.get(`/admin/bots/${allowBotId.value}/allowlist`)
  allowRows.value = data
}

async function addAllow(tuid: string) {
  if (!allowBotId.value) return
  const s = tuid.trim()
  if (!s) {
    ElMessage.warning('请输入用户 ID')
    return
  }
  const body: any = { externalUserId: s, enabled: true }
  if (allowBatchChannelId.value) body.channelId = allowBatchChannelId.value
  await api.post(`/admin/bots/${allowBotId.value}/allowlist`, body)
  ElMessage.success('已添加到白名单')
  await loadAllow()
}

async function batchAddAllow() {
  if (!allowBotId.value) return
  if (!allowBatchIds.value.trim()) {
    ElMessage.warning('请输入用户 ID（多个用逗号或换行分隔）')
    return
  }
  try {
    const body: any = { externalUserIds: allowBatchIds.value, enabled: true }
    if (allowBatchChannelId.value) body.channelId = allowBatchChannelId.value
    const { data } = await api.post(`/admin/bots/${allowBotId.value}/allowlist/batch`, body)
    ElMessage.success(`批量添加 ${data.created} 条`)
    allowBatchIds.value = ''
    await loadAllow()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量添加失败')
  }
}

async function deleteAllow(id: number) {
  if (!allowBotId.value) return
  await api.delete(`/admin/bots/${allowBotId.value}/allowlist/${id}`)
  await loadAllow()
}

async function openFieldDialog(row: Qd) {
  fieldQueryId.value = row.id
  fieldMainOpen.value = true
  await loadFields()
}

async function loadFields() {
  const { data } = await api.get<FieldMap[]>(`/admin/queries/${fieldQueryId.value}/fields`)
  fieldRows.value = [...data].sort((a, b) => a.sortOrder - b.sortOrder)
}

function openNewField() {
  fieldEditId.value = null
  fieldForm.value = {
    columnName: '',
    label: '',
    sortOrder: fieldRows.value.length,
    maskType: 'NONE',
    formatType: '',
    pipelineSteps: [],
  }
  fieldFormOpen.value = true
}

function openEditField(row: FieldMap) {
  fieldEditId.value = row.id
  fieldForm.value = {
    columnName: row.columnName,
    label: row.label,
    sortOrder: row.sortOrder,
    maskType: row.maskType || 'NONE',
    formatType: row.formatType ?? '',
    pipelineSteps: parseDisplayPipelineJson(row.displayPipelineJson),
  }
  fieldFormOpen.value = true
}

async function saveField() {
  const effectiveSteps = fieldForm.value.pipelineSteps.filter((s) => s.op?.trim())
  if (effectiveSteps.length > 20) {
    ElMessage.error('展现流水线最多 20 步')
    return
  }
  let displayPipelineJson: string | null = null
  try {
    displayPipelineJson = buildDisplayPipelineJson(fieldForm.value.pipelineSteps)
  } catch {
    ElMessage.error('展现流水线 JSON 超过 24000 字符，请精简步骤')
    return
  }
  const body = {
    columnName: fieldForm.value.columnName,
    label: fieldForm.value.label,
    sortOrder: fieldForm.value.sortOrder,
    maskType: fieldForm.value.maskType,
    formatType: fieldForm.value.formatType || null,
    displayPipelineJson,
  }
  if (fieldEditId.value != null) {
    await api.put(`/admin/queries/${fieldQueryId.value}/fields/${fieldEditId.value}`, body)
    ElMessage.success('字段映射已更新')
  } else {
    await api.post(`/admin/queries/${fieldQueryId.value}/fields`, body)
    ElMessage.success('字段映射已创建')
  }
  fieldFormOpen.value = false
  await loadFields()
}

async function deleteField(row: FieldMap) {
  try {
    await ElMessageBox.confirm('确定删除该字段映射？', '确认删除', { type: 'warning' })
  } catch {
    return
  }
  await api.delete(`/admin/queries/${fieldQueryId.value}/fields/${row.id}`)
  await loadFields()
  ElMessage.success('已删除')
}

const active = ref('bots')
const tuid = ref('')
/** 顶部「使用指引」折叠面板；默认折叠 */
const guideExpand = ref<string[]>([])
/** 首屏加载数据 */
const pageLoading = ref(true)

watch(
  () => [fieldMainOpen.value, fieldRows.value.map((r) => r.id).join(',')] as const,
  async ([open]) => {
    destroyFieldSortable()
    if (!open || fieldRows.value.length === 0) {
      return
    }
    await nextTick()
    void initFieldSortable()
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  destroyFieldSortable()
})
</script>

<template>
  <el-container class="admin-shell">
    <el-header class="admin-header">
      <div class="admin-header-title">
        <span class="admin-badge" aria-hidden="true">IM</span>
        <div>
          <div class="admin-product">IM Bot Hub</div>
          <div class="admin-tagline">数据源（库 / API）、查询（SQL·向导·API）、多平台渠道与日志</div>
        </div>
      </div>
      <div class="admin-header-actions">
        <el-button text type="primary" @click="router.push('/guide')">使用说明</el-button>
        <el-link href="/swagger-ui/index.html" target="_blank" type="primary">API 文档</el-link>
        <el-button plain @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main v-loading="pageLoading" class="admin-main">
      <el-collapse v-model="guideExpand" class="admin-guide">
        <el-collapse-item name="guide">
          <template #title>
            <span class="admin-guide-title">第一次使用？展开查看快速入门</span>
          </template>
          <p class="admin-hint" style="margin: 0 0 12px 0">
            更完整说明见右上角 <el-button text type="primary" @click.stop="router.push('/guide')">使用说明</el-button> 页面。
          </p>
          <ol class="admin-guide-steps">
            <li>
              <strong>创建机器人</strong>：点击「机器人」Tab 的<strong>新建机器人</strong>，填写名称即可。机器人是逻辑分组，不含平台配置。
            </li>
            <li>
              <strong>添加渠道</strong>：点击「渠道管理」Tab 的<strong>新建渠道</strong>，选择机器人和平台（Telegram/飞书/钉钉/企业微信/Slack/Discord），填写平台凭证。
            </li>
            <li>
              <strong>配置数据源</strong>：在「数据源」Tab 添加<strong>只读 MySQL</strong>或<strong>API 数据源</strong>（Base URL、鉴权、测试连接）。
            </li>
            <li>
              <strong>创建查询</strong>：在「查询定义」Tab 选择机器人，新建查询。数据库源可选<strong>「向导」</strong>或<strong>「高级 SQL」</strong>；API 源可选<strong>「API 可视化」</strong>。保存后可用<strong>「测试」</strong>验证。
            </li>
            <li>
              <strong>Webhook 配置</strong>：Telegram 渠道系统自动注册 Webhook；其他平台需将 Webhook URL 配置到对应平台后台。
            </li>
            <li>
              <strong>发送命令</strong>：在 IM 平台向机器人发命令（与查询里填的<strong>命令名</strong>、参数一致），例如 <code>/cx 参数值</code>。
            </li>
          </ol>
        </el-collapse-item>
      </el-collapse>

      <el-tabs v-model="active" type="border-card" class="admin-tabs">
        <el-tab-pane label="概览" name="overview">
          <OverviewTab ref="overviewTabRef" />
        </el-tab-pane>
        <el-tab-pane label="机器人" name="bots">
          <BotTab ref="botTabRef" />
        </el-tab-pane>

        <el-tab-pane label="渠道管理" name="channels">
          <ChannelTab ref="channelTabRef" :bots="bots" />
        </el-tab-pane>

        <el-tab-pane label="数据源" name="ds">
          <el-button type="primary" @click="openNewDs">新建数据源</el-button>
          <el-table :data="dsList" style="width: 100%; margin-top: 12px">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="名称" />
            <el-table-column label="类型" width="100">
              <template #default="scope">
                {{ scope.row.sourceType === 'API' ? 'API' : '数据库' }}
              </template>
            </el-table-column>
            <el-table-column label="连接信息" min-width="260" show-overflow-tooltip>
              <template #default="scope">
                {{ scope.row.sourceType === 'API' ? scope.row.apiBaseUrl || '未配置' : scope.row.jdbcUrl || '未配置' }}
              </template>
            </el-table-column>
            <el-table-column label="账号 / 鉴权" min-width="150" show-overflow-tooltip>
              <template #default="scope">
                {{ scope.row.sourceType === 'API' ? scope.row.authType || 'NONE' : scope.row.username || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="容量 / 超时" width="120">
              <template #default="scope">
                {{ scope.row.sourceType === 'API' ? `${scope.row.requestTimeoutMs ?? 5000} ms` : scope.row.poolMax }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="openEditDs(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteDs(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="查询定义" name="q">
          <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
            <span>机器人</span>
            <el-select v-model="queryBotId" placeholder="选择机器人" style="width: 240px" @change="loadQueries">
              <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
            </el-select>
            <el-button @click="loadQueries">刷新</el-button>
            <el-button type="primary" :disabled="!queryBotId" @click="openNewQuery">新建查询</el-button>
            <el-button @click="exportQueries">导出</el-button>
            <el-button @click="triggerImportQueries">导入</el-button>
          </div>
          <el-table :data="queries" style="width: 100%; margin-top: 12px">
            <el-table-column prop="id" label="查询 ID" width="80" />
            <el-table-column label="名称" min-width="120" show-overflow-tooltip>
              <template #default="scope">
                {{ scope.row.name?.trim() || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="command" label="命令" width="100" />
            <el-table-column label="模式" width="88">
              <template #default="scope">
                {{ scope.row.queryMode === 'VISUAL' ? '向导' : scope.row.queryMode === 'API' ? 'API' : 'SQL' }}
              </template>
            </el-table-column>
            <el-table-column label="数据源" min-width="180" show-overflow-tooltip>
              <template #default="scope">
                {{ dsList.find((d) => d.id === scope.row.datasourceId)?.name || `#${scope.row.datasourceId}` }}
              </template>
            </el-table-column>
            <el-table-column label="TG 展现" width="96" show-overflow-tooltip>
              <template #default="scope">
                {{ scope.row.telegramReplyStyle ?? 'LIST' }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="启用" width="70" />
            <el-table-column label="操作" width="360" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="openEditQuery(scope.row)">编辑</el-button>
                <el-button size="small" @click="openFieldDialog(scope.row)">字段映射</el-button>
                <el-button size="small" @click="openTestDialog(scope.row)">测试</el-button>
                <el-dropdown trigger="click" @command="(c: string) => handleQueryOptimizeCommand(scope.row, c)">
                  <el-button size="small" type="success" plain>优化</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="index" :disabled="!isVisualQueryForOptimize(scope.row)">
                        索引建议与 DDL
                      </el-dropdown-item>
                      <el-dropdown-item
                        command="bench"
                        :disabled="!isVisualQueryForOptimize(scope.row) || visualSearchOrCount(scope.row) < 2"
                      >
                        OR / UNION 耗时对比
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button size="small" type="danger" @click="deleteQuery(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="admin-hint" style="margin-top: 12px">
            向导查询：列表「测试」小窗可选<strong>按已保存 SQL</strong>或<strong>临时 OR / UNION 重编译</strong>（与抽屉内测试一致）；「优化」可不打抽屉直接看索引摘要与耗时对比。索引建议与 OR/UNION
            写法<strong>无本质区别</strong>（都是同一批检索列上的索引），执行计划耗时可能不同，仍以 EXPLAIN 与对比为准。
          </p>
        </el-tab-pane>

        <el-tab-pane label="白名单" name="a">
          <AllowlistTab :bots="bots" />
        </el-tab-pane>

        <el-tab-pane label="命令日志" name="tglog">
          <CommandLogTab :bots="bots" />
        </el-tab-pane>

        <el-tab-pane label="审计日志" name="audit">
          <AuditLogTab />
        </el-tab-pane>

        <el-tab-pane label="设置" name="settings">
          <SettingsTab />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>


  <el-dialog v-model="dsDlgOpen" :title="dsEditId != null ? '编辑数据源' : '新建数据源（数据库 / API）'" width="720px">
    <el-form label-width="120px">
      <el-form-item label="名称"><el-input v-model="dsForm.name" placeholder="例如：订单只读库" /></el-form-item>
      <el-form-item label="平台" required>
        <el-select v-model="addChannelForm.platform" placeholder="选择平台" style="width: 100%" @change="onChannelPlatformChange">
          <el-option label="Telegram" value="TELEGRAM" />
          <el-option label="飞书" value="LARK" />
          <el-option label="钉钉" value="DINGTALK" />
          <el-option label="企业微信" value="WEWORK" />
          <el-option label="Slack" value="SLACK" />
          <el-option label="Discord" value="DISCORD" />
        </el-select>
      </el-form-item>

      <!-- Telegram 表单 -->
      <template v-if="addChannelForm.platform === 'TELEGRAM'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="addChannelForm.botToken" type="password" show-password placeholder="123456:ABC-DEF..." />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="addChannelForm.telegramBotUsername" placeholder="选填：xxx_bot" />
        </el-form-item>
        <el-form-item label="Webhook 密钥">
          <el-input v-model="addChannelForm.webhookSecretToken" type="password" show-password placeholder="选填" />
        </el-form-item>
      </template>

      <!-- 飞书表单 -->
      <template v-if="addChannelForm.platform === 'LARK'">
        <el-form-item label="App ID" required>
          <el-input v-model="addChannelForm.appId" placeholder="cli_xxx" />
        </el-form-item>
        <el-form-item label="App Secret" required>
          <el-input v-model="addChannelForm.appSecret" type="password" show-password />
        </el-form-item>
      </template>

      <!-- 钉钉表单 -->
      <template v-if="addChannelForm.platform === 'DINGTALK'">
        <el-form-item label="App Secret" required>
          <el-input v-model="addChannelForm.appSecret" type="password" show-password placeholder="钉钉机器人 AppSecret" />
        </el-form-item>
      </template>

      <!-- 企业微信表单 -->
      <template v-if="addChannelForm.platform === 'WEWORK'">
        <el-form-item label="CorpID" required>
          <el-input v-model="addChannelForm.corpId" placeholder="ww1234567890" />
        </el-form-item>
        <el-form-item label="AgentId" required>
          <el-input-number v-model="addChannelForm.agentId" :min="1" />
        </el-form-item>
        <el-form-item label="Token" required>
          <el-input v-model="addChannelForm.callbackToken" />
        </el-form-item>
        <el-form-item label="EncodingAESKey" required>
          <el-input v-model="addChannelForm.encodingAesKey" placeholder="43 位" />
        </el-form-item>
      </template>

      <!-- Slack 表单 -->
      <template v-if="addChannelForm.platform === 'SLACK'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="addChannelForm.botToken" type="password" show-password placeholder="xoxb-..." />
        </el-form-item>
        <el-form-item label="Signing Secret">
          <el-input v-model="addChannelForm.signingSecret" type="password" show-password placeholder="选填" />
        </el-form-item>
      </template>

      <!-- Discord 表单 -->
      <template v-if="addChannelForm.platform === 'DISCORD'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="addChannelForm.botToken" type="password" show-password placeholder="MTIzNDU2Nzg5..." />
        </el-form-item>
        <el-form-item label="Public Key">
          <el-input v-model="addChannelForm.publicKey" placeholder="选填：用于验证 Interactions 签名" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="addChannelDlgOpen = false">取消</el-button>
      <el-button type="primary" @click="saveAddChannel">创建</el-button>
    </template>
  </el-dialog>

  <!-- 机器人详情对话框 -->
  <el-dialog v-model="botDetailDlgOpen" title="机器人详情" width="720px" destroy-on-close>
    <el-skeleton :loading="botDetailLoading" animated>
      <template v-if="botDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ botDetail.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ botDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="botDetail.enabled ? 'success' : 'info'">{{ botDetail.enabled ? '启用' : '禁用' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 关联渠道 -->
        <el-divider content-position="left">关联渠道（{{ botDetail.channels?.length || 0 }}）</el-divider>
        <el-table v-if="botDetail.channels?.length" :data="botDetail.channels" size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="platform" label="平台" width="120">
            <template #default="{ row }">
              <el-tag :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无渠道" :image-size="48" />

        <!-- 关联查询定义 -->
        <el-divider content-position="left">查询定义（{{ botDetail.queries?.length || 0 }}）</el-divider>
        <el-table v-if="botDetail.queries?.length" :data="botDetail.queries" size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="command" label="命令" width="120">
            <template #default="{ row }"><code>/{{ row.command }}</code></template>
          </el-table-column>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="queryMode" label="模式" width="100" />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无查询定义" :image-size="48" />

        <!-- 最近命令日志 -->
        <el-divider content-position="left">最近命令（{{ botDetail.recentLogs?.length || 0 }}）</el-divider>
        <el-table v-if="botDetail.recentLogs?.length" :data="botDetail.recentLogs" size="small" style="width: 100%">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="platform" label="平台" width="100">
            <template #default="{ row }">
              <el-tag :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="command" label="命令" width="120" />
          <el-table-column prop="success" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时" width="80">
            <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + 'ms' : '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无命令日志" :image-size="48" />
      </template>
    </el-skeleton>
    <template #footer>
      <el-button @click="botDetailDlgOpen = false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 渠道详情对话框 -->
  <el-dialog v-model="channelDetailDlgOpen" title="渠道详情" width="640px" destroy-on-close>
    <template v-if="channelDetail">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="渠道 ID">{{ channelDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="机器人 ID">{{ channelDetail.botId }}</el-descriptions-item>
        <el-descriptions-item label="平台">
          <el-tag :type="platformTagType(channelDetail.platform)">{{ platformLabel(channelDetail.platform) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="channelDetail.enabled ? 'success' : 'info'">{{ channelDetail.enabled ? '启用' : '禁用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="凭证摘要">{{ channelDetail.credentialsSummary }}</el-descriptions-item>
        <el-descriptions-item label="Webhook URL" :span="2">
          <span style="word-break: break-all; font-size: 12px">{{ channelDetail.webhookUrl }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- Telegram Webhook 管理 -->
      <template v-if="channelDetail.platform === 'TELEGRAM'">
        <el-divider content-position="left">Webhook 管理</el-divider>
        <el-form label-width="100px" size="small">
          <el-form-item label="公网基址">
            <el-input v-model="channelWebhookPublicBase" placeholder="可留空：使用配置文件默认值" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="channelWebhookRegLoading" @click="registerChannelWebhook">注册 Webhook</el-button>
            <el-button :loading="channelWebhookLoading" @click="loadChannelWebhookStatus">查看状态</el-button>
          </el-form-item>
        </el-form>
        <template v-if="channelWebhookStatus">
          <el-alert v-if="channelWebhookStatus.error" :title="channelWebhookStatus.error" type="error" show-icon :closable="false" />
          <template v-else>
            <el-descriptions :column="2" border size="small" style="margin-top: 12px">
              <el-descriptions-item label="Telegram 状态">{{ channelWebhookStatus.telegramOk ? '正常' : '异常' }}</el-descriptions-item>
              <el-descriptions-item label="当前 URL">{{ channelWebhookStatus.url || '（空）' }}</el-descriptions-item>
              <el-descriptions-item label="待处理数">{{ channelWebhookStatus.pendingUpdateCount ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="出口 IP">{{ channelWebhookStatus.ipAddress ?? '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="channelWebhookStatus.lastErrorMessage" label="最后错误" :span="2">
                <span style="color: var(--el-color-danger)">{{ channelWebhookStatus.lastErrorMessage }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
      </template>

      <!-- 非 Telegram 平台提示 -->
      <template v-else>
        <el-divider content-position="left">Webhook 配置</el-divider>
        <el-alert title="请将上方 Webhook URL 配置到对应平台的后台" type="info" show-icon :closable="false" />
      </template>

      <!-- 连通性测试 -->
      <el-divider content-position="left">连通性测试</el-divider>
      <el-form label-width="100px" size="small">
        <el-form-item label="目标 ID">
          <el-input v-model="channelTestTargetId"
            :placeholder="channelDetail?.platform === 'TELEGRAM' ? 'chat_id（可留空仅验证 Token）' :
                          channelDetail?.platform === 'LARK' ? 'open_id 或 oc_ 开头的 chat_id' :
                          channelDetail?.platform === 'SLACK' ? 'C 开头的 channel_id（可留空仅验证 Token）' :
                          channelDetail?.platform === 'DISCORD' ? 'channel_id（可留空仅验证 Token）' :
                          '钉钉/企微仅验证凭证格式'" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="channelTestLoading" @click="testChannel">发送测试</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="channelTestResult"
        :title="channelTestResult.message"
        :type="channelTestResult.success ? 'success' : 'error'"
        show-icon :closable="true" @close="channelTestResult = null"
        style="margin-top: 8px" />
    </template>
    <template #footer>
      <el-button @click="channelDetailDlgOpen = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dsDlgOpen" :title="dsEditId != null ? '编辑数据源' : '新建数据源（数据库 / API）'" width="720px">
    <el-form label-width="120px">
      <el-form-item label="名称"><el-input v-model="dsForm.name" placeholder="例如：订单只读库" /></el-form-item>
        <el-form-item label="数据源类型">
          <el-radio-group v-model="dsSourceType">
            <el-radio-button value="DATABASE">数据库</el-radio-button>
            <el-radio-button value="API">API</el-radio-button>
          </el-radio-group>
          <span class="form-hint">数据库适合内部只读查询；API 适合天气、币价、物流、外部平台状态等场景。</span>
        </el-form-item>
      <template v-if="dsSourceType === 'DATABASE'">
        <el-form-item label="连接方式">
          <el-radio-group v-model="dsForm.jdbcMode">
            <el-radio-button label="simple">简易（推荐）</el-radio-button>
            <el-radio-button label="advanced">自定义 JDBC</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="dsForm.jdbcMode === 'simple'">
          <el-form-item label="地址">
            <el-input v-model="dsForm.mysqlHost" placeholder="IP 或域名，如 127.0.0.1" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="dsForm.mysqlPort" :min="1" :max="65535" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库名">
            <el-input v-model="dsForm.mysqlDatabase" placeholder="数据库名，如 shop_db" />
          </el-form-item>
          <el-form-item label="生成 URL">
            <el-input type="textarea" :rows="3" :model-value="dsJdbcPreview" readonly class="ds-jdbc-preview" />
          </el-form-item>
        </template>
        <el-form-item v-else label="JDBC URL">
          <el-input v-model="dsForm.jdbcUrl" type="textarea" :rows="3" placeholder="jdbc:mysql://..." />
          <span class="form-hint">非标准地址或需特殊参数时使用；保存前请自行检查格式。</span>
        </el-form-item>
        <el-form-item label="数据库用户"><el-input v-model="dsForm.username" /></el-form-item>
        <el-form-item label="密码">
          <el-input v-model="dsForm.passwordPlain" type="password" show-password autocomplete="off" />
          <span v-if="dsEditId != null" style="color: #999; font-size: 12px; display: block; margin-top: 4px">
            留空则不修改密码
          </span>
        </el-form-item>
        <el-form-item label="连接池上限"><el-input-number v-model="dsForm.poolMax" :min="1" /></el-form-item>
      </template>
      <ApiDatasourceFormSection
        v-else
        v-model="dsForm"
        v-model:advanced-sections="dsApiAdvancedSections"
        :presets="API_DATASOURCE_PRESETS"
        :auth-type-hint="dsAuthTypeHint"
        :auth-config-placeholder="dsAuthConfigPlaceholder"
        :auth-config-hint="dsAuthConfigHint"
        :show-simple-api-auth-username="showSimpleApiAuthUsername"
        :show-simple-api-auth-password="showSimpleApiAuthPassword"
        :show-simple-api-auth-secret="showSimpleApiAuthSecret"
        :auth-secret-label="dsAuthSecretLabel"
        :auth-secret-hint="dsAuthSecretHint"
        :auth-username-hint="dsAuthUsernameHint"
        :auth-password-hint="dsAuthPasswordHint"
        @apply-preset="applyDatasourcePreset"
      />
    </el-form>
    <template #footer>
      <el-button @click="dsDlgOpen = false">取消</el-button>
      <el-button :loading="dsTestLoading" @click="testDsConnection">测试连接</el-button>
      <el-button type="primary" @click="saveDs">{{ dsEditId != null ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>

  <el-drawer
    v-model="qFormOpen"
    :title="qEditId != null ? '编辑查询定义' : '新建查询定义'"
    direction="rtl"
    :size="1220"
    destroy-on-close
    class="query-def-drawer"
    :close-on-click-modal="false"
    @opened="onQueryDrawerOpened"
  >
    <div
      class="query-def-drawer-inner"
      :class="{ 'query-def-drawer-inner--visual': qQueryMode === 'VISUAL' && qFormOpen }"
    >
    <el-form label-width="140px" class="query-def-form">
      <el-form-item label="配置方式">
        <el-radio-group v-model="qQueryMode">
          <el-radio-button value="VISUAL" :disabled="currentDatasourceType(qForm.datasourceId) === 'API'">向导（选表 + 多列 OR）</el-radio-button>
          <el-radio-button value="SQL" :disabled="currentDatasourceType(qForm.datasourceId) === 'API'">高级 SQL</el-radio-button>
          <el-radio-button value="API" :disabled="currentDatasourceType(qForm.datasourceId) !== 'API'">API 可视化</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="数据源">
        <el-select
          v-model="qForm.datasourceId"
          placeholder="选择数据源"
          style="width: 100%"
          :disabled="!dsList.length"
        >
          <el-option v-for="d in dsList" :key="d.id" :label="`${d.id} — ${d.name}`" :value="d.id" />
        </el-select>
        <span v-if="!dsList.length" class="form-hint">请先在「数据源」页创建数据源。</span>
        <span v-else class="form-hint">
          当前数据源类型：{{ currentDatasourceType(qForm.datasourceId) === 'API' ? 'API 接口' : '数据库' }}。API 数据源会使用可视化 API 配置，不再填写 SQL。
        </span>
      </el-form-item>
      <el-form-item label="查询名称">
        <el-input
          v-model="qForm.name"
          maxlength="128"
          show-word-limit
          clearable
          placeholder="列表展示与命令菜单副标题优先；留空则按 API/向导/SQL 推断"
        />
      </el-form-item>
      <el-form-item label="命令菜单描述">
        <el-input
          v-model="qForm.telegramMenuDescription"
          type="textarea"
          :rows="2"
          maxlength="255"
          show-word-limit
          clearable
          placeholder="留空则自动生成「模式 + 副标题 + 参数示例」；填写则整段覆盖（不再自动拼接）"
        />
      </el-form-item>
      <el-form-item label="命令"><el-input v-model="qForm.command" placeholder="例如 cx（不要带 /）" /></el-form-item>
      <el-form-item
        v-if="qQueryMode === 'VISUAL' && positionalParamNames.length"
        label="参数示例（菜单）"
      >
        <div v-for="(pn, idx) in positionalParamNames" :key="`${pn}-${idx}`" class="query-param-example-row">
          <span class="query-param-example-name">{{ pn }}</span>
          <el-input v-model="positionalExampleDrafts[idx]" placeholder="与 params 顺序一致，写入菜单「示例:」" clearable />
        </div>
        <p class="form-hint">向导保存后才有参数名；与下方服务端合并进 param_schema。</p>
      </el-form-item>
      <el-alert
        v-if="qQueryMode === 'API'"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 18px"
      >
        API 查询推荐流程：选模板 → 预览接口返回 → 点选字段 → 拖拽排序 → 保存。样例参数框会写入菜单用的「示例」；上方<strong>查询名称</strong>会优先作为菜单副标题（模板会预填，可改）。用户只会看到整理后的回复，不会看到底层 JSON。
      </el-alert>

      <template v-if="qQueryMode === 'VISUAL'">
        <div class="visual-wizard-grow">
          <VisualQueryWizard
            ref="visualWizardRef"
            :key="'viz-' + String(qEditId ?? 'new')"
            :active="qFormOpen"
            :datasource-id="qForm.datasourceId"
            :query-edit-id="qEditId"
            :bot-id="queryBotId"
            v-model:telegram-reply-style="qForm.telegramReplyStyle"
            v-model:telegram-join-delimiter="qForm.telegramJoinDelimiter"
            v-model:timeout-ms="qForm.timeoutMs"
            v-model:max-rows="qForm.maxRows"
            v-model:enabled="qForm.enabled"
          />
        </div>
      </template>

      <template v-else-if="qQueryMode === 'API'">
        <ApiQueryBuilder
          ref="apiBuilderRef"
          :key="`api-${qEditId ?? 'new'}-${qForm.datasourceId}`"
          :active="qFormOpen"
          :query-edit-id="qEditId"
          :datasource-id="qForm.datasourceId"
          :datasource-preset-key="dsList.find((d) => d.id === qForm.datasourceId)?.apiPresetKey ?? null"
          :timeout-ms="qForm.timeoutMs"
          :max-rows="qForm.maxRows"
          :enabled="qForm.enabled"
          :telegram-reply-style="qForm.telegramReplyStyle"
          :telegram-join-delimiter="qForm.telegramJoinDelimiter"
          @update:timeout-ms="qForm.timeoutMs = $event"
          @update:max-rows="qForm.maxRows = $event"
          @update:enabled="qForm.enabled = $event"
          @update:telegram-reply-style="qForm.telegramReplyStyle = $event as TelegramReplyStyle"
          @update:telegram-join-delimiter="qForm.telegramJoinDelimiter = $event"
          @update:param-schema-json="qForm.paramSchemaJson = $event"
          @preset-selected="onApiPresetSelected"
        />
      </template>

      <template v-else>
        <el-form-item label="SQL 模板">
          <el-input v-model="qForm.sqlTemplate" type="textarea" :rows="6" placeholder="SELECT ... WHERE x = #{orderNo}" />
        </el-form-item>
        <el-form-item label="参数 JSON">
          <el-input v-model="qForm.paramSchemaJson" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="positionalParamNames.length" label="参数示例（菜单）">
          <div v-for="(pn, idx) in positionalParamNames" :key="`${pn}-sql-${idx}`" class="query-param-example-row">
            <span class="query-param-example-name">{{ pn }}</span>
            <el-input v-model="positionalExampleDrafts[idx]" placeholder="与 params 顺序一致，可写入 JSON 的 examples" clearable />
          </div>
        </el-form-item>

        <el-divider content-position="left">管理端 SQL 测试</el-divider>
        <el-form-item label="测试参数">
          <div class="wizard-test-row">
            <el-input
              v-model="qWizardTestArgs"
              placeholder="空格分隔，顺序与参数 JSON 一致（与列表里「测试」相同）"
              class="wizard-test-input"
            />
            <el-button
              type="primary"
              :disabled="qEditId == null"
              :loading="qWizardTestRunning"
              @click="runWizardSqlTest"
              >运行测试</el-button
            >
          </div>
          <el-alert
            v-if="qEditId == null"
            title="新建查询请先点底部「创建」保存；保存后可在此直接跑当前数据源上的 SQL。"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 10px"
          />
        </el-form-item>
        <el-form-item v-if="qWizardTestResult" label="测试结果">
          <pre class="test-result-pre">{{ qWizardTestResult }}</pre>
          <el-button size="small" style="margin-top: 8px" @click="copyWizardTestResult">复制 JSON</el-button>
        </el-form-item>

        <el-form-item label="超时（毫秒）"><el-input-number v-model="qForm.timeoutMs" :min="500" /></el-form-item>
        <el-form-item label="最大行数"><el-input-number v-model="qForm.maxRows" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="qForm.enabled" /></el-form-item>
        <el-form-item label="适用渠道">
          <el-select
            v-model="qFormChannelScope"
            multiple
            clearable
            placeholder="留空 = 所有渠道"
            style="width: 100%"
          >
            <el-option
              v-for="ch in queryBotChannels"
              :key="ch.id"
              :label="`${platformLabel(ch.platform)} #${ch.id}`"
              :value="ch.id"
            />
          </el-select>
          <span class="form-hint">留空表示该命令适用于机器人下的所有渠道；选择后仅在指定渠道生效。</span>
        </el-form-item>
        <el-form-item label="回复展现样式">
          <el-select v-model="qForm.telegramReplyStyle" style="width: 100%">
            <el-option label="一行一项（默认）" value="LIST" />
            <el-option label="一行一项 · 中间点分隔（窄屏扫读）" value="LIST_DOT" />
            <el-option label="一行一项 · 数值代码块（手机长按易复制单段）" value="LIST_CODE" />
            <el-option label="每字段引用块（层次更强）" value="LIST_BLOCKQUOTE" />
            <el-option label="分块（标签与值分行，更疏朗）" value="SECTION" />
            <el-option label="整块等宽 label: value（多行复制）" value="MONO_PRE" />
            <el-option label="整块等宽 label=value（日志风）" value="CODE_BLOCK" />
            <el-option label="单行键值分号连接（短结果）" value="KV_SINGLE_LINE" />
            <el-option label="每行仅值（空格拼接，无 key）" value="VALUES_JOIN_SPACE" />
            <el-option label="每行仅值（| 拼接，无 key）" value="VALUES_JOIN_PIPE" />
            <el-option label="每行仅值（自定义连接符）" value="VALUES_JOIN_CUSTOM" />
            <el-option label="表格（key 作表头，整块输出）" value="TABLE_PRE" />
          </el-select>
          <span class="form-hint"
            >与下方「字段映射」中的展示名、格式、脱敏一起生效；客户端以 Telegram HTML 展示。</span
          >
        </el-form-item>
        <el-form-item v-if="qForm.telegramReplyStyle === 'VALUES_JOIN_CUSTOM'" label="自定义连接符">
          <el-input v-model="qForm.telegramJoinDelimiter" placeholder="例如：两个空格、/、·、 | " />
          <span class="form-hint">每条记录会把所有 value 按该连接符拼成一行。</span>
        </el-form-item>
      </template>
    </el-form>
    </div>
    <template #footer>
      <div
        class="query-def-drawer-footer"
        :class="{ 'query-def-drawer-footer--visual': qQueryMode === 'VISUAL' }"
      >
        <template v-if="qQueryMode === 'VISUAL'">
          <el-button native-type="button" @click="qFormOpen = false">取消</el-button>
          <span class="query-def-drawer-footer-spacer" aria-hidden="true" />
          <div class="query-def-drawer-footer-wizard-actions">
            <el-button native-type="button" :disabled="visualFooterWizardStep <= 0" @click="onVisualDrawerPrev"
              >上一步</el-button
            >
            <el-button
              native-type="button"
              type="primary"
              plain
              :disabled="visualFooterWizardStep >= VISUAL_WIZARD_STEP_LAST"
              @click="onVisualDrawerNext"
              >下一步</el-button
            >
            <el-tooltip
              :disabled="visualFooterWizardStep >= VISUAL_WIZARD_STEP_LAST"
              content="请完成向导最后一步「调优与验证」后再保存，以免漏配条件或参数。"
              placement="top"
            >
              <span class="query-def-drawer-save-wrap">
                <el-button
                  native-type="button"
                  type="primary"
                  :disabled="visualFooterWizardStep < VISUAL_WIZARD_STEP_LAST"
                  @click="saveQuery"
                  >{{ qEditId != null ? '保存' : '创建' }}</el-button
                >
              </span>
            </el-tooltip>
          </div>
        </template>
        <template v-else>
          <el-button @click="qFormOpen = false">取消</el-button>
          <el-button type="primary" @click="saveQuery">{{ qEditId != null ? '保存' : '创建' }}</el-button>
        </template>
      </div>
    </template>
  </el-drawer>


  <el-dialog v-model="fieldMainOpen" title="字段映射（展示顺序与脱敏）" width="920px">
    <p style="color: #666; margin-bottom: 12px">
      查询 ID：{{ fieldQueryId }}。未配置时 Webhook 将按结果集列名原样输出。若该查询使用<strong>向导</strong>保存，字段映射会随向导一并覆盖；同名列上的<strong>展现流水线</strong>会尽量保留。
    </p>
    <el-button type="primary" @click="openNewField">新建字段</el-button>
    <el-button @click="loadFields">刷新</el-button>
    <p class="form-hint" style="margin-top: 10px; margin-bottom: 0">
      拖动左侧「⠿」手柄可调整展示顺序（自动保存）。格式选「日期时间」时支持 Unix 秒/毫秒时间戳与 JDBC 时间类型。
    </p>
    <el-table
      ref="fieldTableRef"
      v-loading="fieldSortSaving"
      :data="fieldRows"
      row-key="id"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column label="" width="44" align="center" class-name="field-drag-col">
        <template #default>
          <span class="field-drag-handle" title="拖动排序">⠿</span>
        </template>
      </el-table-column>
      <el-table-column prop="columnName" label="列名" width="140" />
      <el-table-column prop="label" label="展示标签" width="140" />
      <el-table-column prop="sortOrder" label="顺序" width="80" />
      <el-table-column prop="maskType" label="脱敏" width="110" />
      <el-table-column prop="formatType" label="格式" width="100" />
      <el-table-column label="流水线" width="88">
        <template #default="scope">
          {{ scope.row.displayPipelineJson ? '已配置' : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openEditField(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteField(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="fieldMainOpen = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="fieldFormOpen" :title="fieldEditId != null ? '编辑字段映射' : '新建字段映射'" width="860px" top="6vh">
    <el-form label-width="120px">
      <el-form-item label="列名"><el-input v-model="fieldForm.columnName" placeholder="与 SELECT 列名一致" /></el-form-item>
      <el-form-item label="展示标签"><el-input v-model="fieldForm.label" /></el-form-item>
      <el-form-item label="顺序"><el-input-number v-model="fieldForm.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="脱敏">
        <el-select v-model="fieldForm.maskType" style="width: 100%">
          <el-option label="无（NONE）" value="NONE" />
          <el-option label="手机号后四位（PHONE_LAST4）" value="PHONE_LAST4" />
        </el-select>
      </el-form-item>
      <el-form-item label="格式">
        <el-select v-model="fieldForm.formatType" clearable placeholder="可选" style="width: 100%">
          <el-option label="无" value="" />
          <el-option
            label="日期时间（DATE_TIME，默认 yyyy-MM-dd HH:mm:ss，支持秒/毫秒时间戳）"
            value="DATE_TIME"
          />
          <el-option label="日期时间 · 仅日期（DATE_TIME:yyyy-MM-dd）" value="DATE_TIME:yyyy-MM-dd" />
          <el-option label="日期时间 · 仅时间（DATE_TIME:HH:mm:ss）" value="DATE_TIME:HH:mm:ss" />
          <el-option label="金额两位（MONEY_2）" value="MONEY_2" />
        </el-select>
        <span class="form-hint">自定义模式可选手填：DATE_TIME:你的Java模式，例如 DATE_TIME:MM/dd HH:mm</span>
      </el-form-item>
      <el-divider content-position="left">展现流水线（可选）</el-divider>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        在脱敏与「格式」之后、Telegram HTML 转义之前执行；最多 20 步，整段 JSON 不超过 24000 字符。非法 JSON 或非 URL 的 URL 类步骤会跳过。
      </el-alert>
      <div class="field-pipeline-toolbar">
        <el-button type="primary" plain size="small" @click="addFieldPipelineStep">添加步骤</el-button>
        <el-button size="small" @click="applyFieldPayLinkPipelinePreset">模板：支付链接 → 注册域（https 主域）</el-button>
      </div>
      <div
        v-for="(step, si) in fieldForm.pipelineSteps"
        :key="'pl-' + si"
        class="field-pipeline-step"
      >
        <div class="field-pipeline-step-head">
          <span class="field-pipeline-step-idx">#{{ si + 1 }}</span>
          <el-select v-model="step.op" filterable placeholder="选择 op" style="width: 320px">
            <el-option v-for="po in PIPELINE_OP_OPTIONS" :key="po.value" :label="po.label" :value="po.value" />
          </el-select>
          <el-button type="danger" link size="small" @click="removeFieldPipelineStep(si)">移除</el-button>
        </div>
        <div v-if="step.op === 'prefix' || step.op === 'suffix' || step.op === 'default_if_empty'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">value</span>
          <el-input v-model="step.value" placeholder="字符串" style="max-width: 420px" />
        </div>
        <div v-else-if="step.op === 'truncate'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">max</span>
          <el-input-number v-model="step.max" :min="1" :max="100000" />
          <span class="field-pipeline-param-label">ellipsis</span>
          <el-input v-model="step.ellipsis" placeholder="默认 …" style="width: 80px" />
        </div>
        <div v-else-if="step.op === 'substring'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">start</span>
          <el-input-number v-model="step.start" :min="0" />
          <span class="field-pipeline-param-label">end</span>
          <el-input-number v-model="step.end" :min="0" />
          <span class="field-pipeline-param-label">或 len</span>
          <el-input-number v-model="step.len" :min="0" />
        </div>
        <div v-else-if="step.op === 'split_take'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">delimiter</span>
          <el-input v-model="step.delimiter" placeholder="," style="width: 100px" />
          <span class="field-pipeline-param-label">index</span>
          <el-input-number v-model="step.index" :min="0" />
          <el-checkbox v-model="step.fromEnd">从末尾计数</el-checkbox>
        </div>
        <div v-else-if="step.op === 'replace_literal'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">from</span>
          <el-input v-model="step.from" style="max-width: 200px" />
          <span class="field-pipeline-param-label">to</span>
          <el-input v-model="step.to" style="max-width: 200px" />
          <span class="field-pipeline-param-label">maxReplacements</span>
          <el-input-number v-model="step.maxReplacements" :min="-1" />
        </div>
        <div v-else-if="step.op === 'extract_between'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">left</span>
          <el-input v-model="step.left" style="max-width: 200px" />
          <span class="field-pipeline-param-label">right</span>
          <el-input v-model="step.right" style="max-width: 200px" />
        </div>
        <div v-else-if="step.op === 'url_to_origin' || step.op === 'url_to_host'" class="field-pipeline-params">
          <el-checkbox v-model="step.lowercaseHost">lowercaseHost</el-checkbox>
        </div>
        <div v-else-if="step.op === 'url_to_path'" class="field-pipeline-params">
          <el-checkbox v-model="step.includeQuery">includeQuery</el-checkbox>
        </div>
        <div v-else-if="step.op === 'url_host_labels'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">count</span>
          <el-input-number v-model="step.count" :min="1" :max="32" />
          <el-checkbox v-model="step.fromRight">fromRight</el-checkbox>
          <el-checkbox v-model="step.withScheme">withScheme</el-checkbox>
        </div>
        <div v-else-if="step.op === 'url_path_segments'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">maxSegments</span>
          <el-input-number v-model="step.maxSegments" :min="1" :max="64" />
          <el-checkbox v-model="step.leadingSlash">leadingSlash</el-checkbox>
        </div>
        <div v-else-if="step.op === 'regex_extract'" class="field-pipeline-params">
          <span class="field-pipeline-param-label">pattern</span>
          <el-input v-model="step.pattern" placeholder="Java 正则" style="max-width: 360px" />
          <span class="field-pipeline-param-label">group</span>
          <el-input-number v-model="step.group" :min="0" :max="32" />
        </div>
      </div>
      <el-form-item label="JSON 预览">
        <el-input type="textarea" :rows="2" readonly :model-value="fieldPipelineJsonPreview || '(空)'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="fieldFormOpen = false">取消</el-button>
      <el-button type="primary" @click="saveField">{{ fieldEditId != null ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="testOpen" title="测试查询（管理端）" width="640px" destroy-on-close>
    <p class="form-hint">参数用空格分隔，对应查询定义中的参数顺序。</p>
    <el-form-item
      v-if="testQueryRow?.queryMode === 'VISUAL' && testQueryRow.visualConfigJson?.trim()"
      label="SQL 形态"
      style="margin-bottom: 8px"
    >
      <el-radio-group v-model="testSqlComposition" size="small">
        <el-radio-button value="STORED">已保存（与线上一致）</el-radio-button>
        <el-radio-button value="LEGACY_OR">临时 OR 重编译</el-radio-button>
        <el-radio-button value="UNION_ALL">临时 UNION 重编译</el-radio-button>
      </el-radio-group>
      <p class="form-hint" style="margin: 6px 0 0 0">
        后两者仅替换 WHERE 组合方式，不修改库内已保存的 sql_template；需当前行已存向导 JSON。
      </p>
    </el-form-item>
    <el-input v-model="testArgs" placeholder="例如 ORD123" />
    <div v-if="testResultText" style="margin-top: 14px">
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
        <span style="font-size: 13px; color: var(--el-text-color-regular)">执行结果（JSON）</span>
        <el-button size="small" @click="copyTestResult">复制结果</el-button>
      </div>
      <pre class="test-result-pre">{{ testResultText }}</pre>
    </div>
    <template #footer>
      <el-button @click="testOpen = false">关闭</el-button>
      <el-button type="primary" :loading="testRunning" @click="runTest">运行</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="listIndexAdviceOpen"
    :title="`索引建议 — ${listIndexRow?.command ?? ''} (#${listIndexRow?.id ?? ''})`"
    width="720px"
    destroy-on-close
    @closed="onListIndexAdviceClosed"
  >
    <template v-if="listIndexRow">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        依据<strong>已保存</strong>的向导 JSON 生成 DDL 建议；与抽屉内「生成索引建议」接口相同。OR 与 UNION 两种写法对「该建哪些列上的索引」结论<strong>一致</strong>，仅执行形态不同。
      </el-alert>
      <el-button type="success" plain :loading="listIndexAdviceLoading" @click="fetchListIndexAdvice"
        >生成索引建议与 DDL</el-button
      >
      <template v-if="listIndexAdviceResult">
        <el-alert type="info" :closable="false" show-icon style="margin-top: 14px">{{ listIndexAdviceResult.summary }}</el-alert>
        <ul v-if="listIndexAdviceResult.warnings?.length" class="index-advice-warn-list">
          <li v-for="(w, wi) in listIndexAdviceResult.warnings" :key="'liw-' + wi">{{ w }}</li>
        </ul>
        <template v-if="listIndexAdviceResult.existingIndexSummaries?.length">
          <p class="index-advice-rec-title">当前表已有索引摘要</p>
          <ul class="index-advice-rec-list">
            <li v-for="(s, si) in listIndexAdviceResult.existingIndexSummaries" :key="'lix-' + si">{{ s }}</li>
          </ul>
        </template>
        <template v-if="listIndexAdviceResult.coverageSkips?.length">
          <p class="index-advice-rec-title">已有索引覆盖（未重复推荐）</p>
          <ul class="index-advice-skip-list">
            <li v-for="(sk, ski) in listIndexAdviceResult.coverageSkips" :key="'lsk-' + ski">{{ sk }}</li>
          </ul>
        </template>
        <p v-if="listIndexAdviceResult.recommendations?.length" class="index-advice-rec-title">建议摘要</p>
        <ul v-if="listIndexAdviceResult.recommendations?.length" class="index-advice-rec-list">
          <li v-for="(rec, ri) in listIndexAdviceResult.recommendations" :key="'lrec-' + ri">
            <span>{{ rec.rationale }}</span>
            <span v-if="rec.columns?.length" class="index-advice-rec-cols">（列：{{ rec.columns.join(', ') }}）</span>
          </li>
        </ul>
        <p class="index-advice-ddl-title">推荐建索引语句（复制到主库执行）</p>
        <pre v-if="listIndexAdviceResult.ddlStatements?.length" class="index-advice-ddl-pre">{{
          listIndexAdviceResult.ddlStatements.join('\n')
        }}</pre>
        <el-button
          v-if="listIndexAdviceResult.ddlStatements?.length"
          type="primary"
          plain
          style="margin-top: 10px"
          @click="copyListIndexDdl"
          >复制全部 DDL</el-button
        >
      </template>
    </template>
    <template #footer>
      <el-button @click="listIndexAdviceOpen = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="listBenchDlgOpen"
    :title="`OR / UNION 耗时 — ${listBenchRow?.command ?? ''} (#${listBenchRow?.id ?? ''})`"
    width="720px"
    destroy-on-close
    @closed="onListBenchClosed"
  >
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
      使用<strong>已保存</strong>向导 JSON；与抽屉内对比接口相同。在只读库、低峰使用；不会写入查询定义。
    </el-alert>
    <el-form label-width="120px">
      <el-form-item label="样例参数">
        <el-input
          v-model="listBenchArgs"
          placeholder="空格分隔，顺序同参数 JSON（如单关键词：一个值）"
        />
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="listBenchLoading" @click="runListBench">开始对比</el-button>
    <template v-if="listBenchResult">
      <el-divider />
      <p style="font-size: 13px; color: #909399">{{ listBenchResult.note }}</p>
      <el-descriptions :column="1" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="OR（LEGACY）">
          <span v-if="listBenchResult.legacyOr?.ok"
            >平均 {{ listBenchResult.legacyOr.durationMsAvg }} ms · 末次行数 {{ listBenchResult.legacyOr.rowCountLast }}</span
          >
          <span v-else style="color: #f56c6c">{{ listBenchResult.legacyOr?.error || '失败' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="UNION（去重）">
          <span v-if="listBenchResult.unionAll?.ok"
            >平均 {{ listBenchResult.unionAll.durationMsAvg }} ms · 末次行数
            {{ listBenchResult.unionAll.rowCountLast }}</span
          >
          <span v-else style="color: #f56c6c">{{ listBenchResult.unionAll?.error || '失败' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-collapse accordion style="margin-top: 12px">
        <el-collapse-item title="查看生成的 OR SQL" name="lo">
          <pre style="white-space: pre-wrap; font-size: 12px">{{ listBenchResult.legacyOr?.sqlTemplate }}</pre>
        </el-collapse-item>
        <el-collapse-item title="查看生成的 UNION SQL" name="lu">
          <pre style="white-space: pre-wrap; font-size: 12px">{{ listBenchResult.unionAll?.sqlTemplate }}</pre>
        </el-collapse-item>
      </el-collapse>
    </template>
    <template #footer>
      <el-button @click="listBenchDlgOpen = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  background: var(--admin-bg, #0b0d11);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: auto !important;
  padding: 16px 24px;
  border-bottom: 1px solid var(--admin-header-border, rgba(255, 255, 255, 0.06));
  background: linear-gradient(125deg, #151823 0%, #12151c 40%, #141a24 100%);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
}

.admin-header-title {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.admin-badge {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #e8e6f3;
  background: linear-gradient(145deg, rgba(124, 58, 237, 0.45), rgba(59, 130, 246, 0.25));
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.admin-product {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.admin-tagline {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.admin-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.admin-main {
  padding: 20px 24px 32px;
  background: var(--admin-bg, #0b0d11);
}

.admin-guide {
  margin-bottom: 18px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  --el-collapse-border-color: transparent;
  --el-collapse-header-bg-color: var(--admin-elevated, #181c26);
}

.admin-guide :deep(.el-collapse-item__header) {
  padding: 12px 16px;
  font-size: 14px;
}

.admin-guide :deep(.el-collapse-item__wrap) {
  border-top: 1px solid var(--el-border-color-lighter);
}

.admin-guide :deep(.el-collapse-item__content) {
  padding: 12px 16px 16px;
  background: var(--admin-surface, #12151c);
}

.admin-guide-title {
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.admin-guide-steps {
  margin: 0;
  padding-left: 1.25em;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
}

.admin-guide-steps li {
  margin-bottom: 8px;
}

.admin-guide-steps code {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-dark);
}

.admin-tabs {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
}

.admin-tabs :deep(.el-tabs__header) {
  margin: 0;
  background: var(--admin-elevated, #181c26);
}

.admin-tabs :deep(.el-tabs__content) {
  padding: 20px;
  background: var(--admin-surface, #12151c);
}

.bot-table-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
}

.bot-table-actions :deep(.el-button),
.bot-table-actions :deep(.el-dropdown) {
  vertical-align: middle;
}

.admin-hint {
  margin-top: 16px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-secondary);
}

.admin-hint code {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-dark);
}

.form-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

.ds-jdbc-preview :deep(textarea) {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}

.test-result-pre {
  margin: 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  font-family: ui-monospace, Consolas, monospace;
  background: var(--el-fill-color-dark);
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  white-space: pre-wrap;
  word-break: break-all;
}

.query-def-drawer {
  /*
   * 与表单主列 + Transfer 列表可视右缘对齐：body(20) + inner(4) + Transfer 内 scrollbar-gutter 等占位
   * （物理右侧；与 direction=rtl 无关，padding-right 始终指几何右边）
   */
  --query-def-drawer-footer-inset-end: calc(var(--el-drawer-padding-primary, 20px) + 4px + 16px);
}

.query-def-drawer :deep(.el-drawer__body) {
  padding: 0 20px 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.query-def-drawer :deep(.el-drawer__footer) {
  padding-right: var(--query-def-drawer-footer-inset-end) !important;
}

.query-def-drawer-inner {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.query-def-drawer-inner--visual {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.query-def-drawer-inner--visual .query-def-form {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.visual-wizard-grow {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.query-def-form {
  max-width: 100%;
}

.query-def-form :deep(.el-form-item__content) {
  min-width: 0;
}

.query-param-example-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.query-param-example-name {
  flex: 0 0 120px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  word-break: break-all;
}

.query-def-drawer :deep(.el-drawer) {
  max-width: calc(100vw - 16px);
}

.query-def-drawer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  /* 水平方向由 el-drawer__footer 内边距承担，避免与正文 20+4 错位 */
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.query-def-drawer-footer--visual {
  flex-wrap: wrap;
  width: 100%;
  justify-content: flex-start;
}

.query-def-drawer-footer-spacer {
  flex: 1;
  min-width: 12px;
}

.query-def-drawer-footer-wizard-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

/* 禁用态时让 el-tooltip 仍能接收悬停（包住按钮） */
.query-def-drawer-save-wrap {
  display: inline-block;
  vertical-align: middle;
}

.index-advice-warn-list {
  margin: 10px 0 0 18px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.55;
}

.index-advice-rec-title,
.index-advice-ddl-title {
  margin: 14px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.index-advice-rec-list {
  margin: 0 0 0 18px;
  padding: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.index-advice-skip-list {
  margin: 8px 0 0 18px;
  padding: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-color-success);
  list-style: disc;
}

.index-advice-rec-cols {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.index-advice-ddl-pre {
  margin: 0;
  padding: 12px 14px;
  max-height: 280px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: ui-monospace, Consolas, monospace;
  background: var(--el-fill-color-dark);
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  white-space: pre-wrap;
  word-break: break-all;
}

.wizard-test-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.wizard-test-input {
  flex: 1;
  min-width: 240px;
}

.field-pipeline-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.field-pipeline-step {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank);
}

.field-pipeline-step-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.field-pipeline-step-idx {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 2.2em;
}

.field-pipeline-params {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.field-pipeline-param-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-drag-handle {
  cursor: grab;
  user-select: none;
  color: var(--el-text-color-secondary);
  font-size: 16px;
  line-height: 1;
  letter-spacing: -2px;
}

.field-drag-handle:active {
  cursor: grabbing;
}
</style>

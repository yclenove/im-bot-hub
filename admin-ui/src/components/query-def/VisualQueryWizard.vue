<script setup lang="ts">
import { Delete, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { parseCommentToEnumJson } from '../../utils/parseCommentEnum'

type VisualSelectItem = { column: string; label?: string; enum?: Record<string, string> }
type VisualColumnMeta = { name: string; comment?: string | null }
type VisualFixedPayload = {
  column: string
  valueType: string
  operator?: string
  intValue?: number
  boolValue?: boolean
}
type VisualConfigPayload = {
  table: string
  select: VisualSelectItem[]
  searchOrColumns: string[]
  searchParamName: string
  paramOrder: string[]
  fixedPredicates?: VisualFixedPayload[]
  orCompositionStrategy?: string
  tableRowsEstimate?: number | null
}

type TableStats = {
  tableRowsEstimate: number | null
  engine?: string | null
  exactCount?: number | null
  exactCountError?: string | null
}

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

const TABLE_ROWS_UNION_HINT = 50_000

type VisualFixedRow = {
  column: string
  valueType: 'INT' | 'BOOL'
  operator: 'EQ' | 'NE'
  intValue: number
  boolValue: boolean
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

const props = defineProps<{
  /** 抽屉打开且本组件可见时为 true */
  active: boolean
  datasourceId: number
  queryEditId: number | null
  botId: number | null
}>()

const telegramReplyStyle = defineModel<TelegramReplyStyle>('telegramReplyStyle', { required: true })
const telegramJoinDelimiter = defineModel<string>('telegramJoinDelimiter', { required: true })
const timeoutMs = defineModel<number>('timeoutMs', { required: true })
const maxRows = defineModel<number>('maxRows', { required: true })
const enabled = defineModel<boolean>('enabled', { required: true })

const qMetaTablesLoading = ref(false)
const qMetaColsLoading = ref(false)
const qVisualTableQ = ref('')
const qVisualTables = ref<string[]>([])
const qVisualColumns = ref<VisualColumnMeta[]>([])
const qVisualColumnFilter = ref('')
const qVisualWizardStep = ref(0)
const WIZARD_STEP_LAST = 4
const VISUAL_WIZARD_STEP_TITLES = [
  '选表与结果列',
  '列展示与顺序',
  '条件与检索',
  '参数与展现',
  '调优与验证',
] as const

const visualWizardSelectedCol = ref<string | null>(null)
const colSortListRef = ref<HTMLElement | null>(null)
let colSortable: ReturnType<typeof Sortable.create> | null = null
const qVisualStatsCollapse = ref<string[]>([])
const qVisualIndexAdviceOpen = ref<string[]>([])
const qVisualTable = ref('')
const qVisualDisplayCols = ref<string[]>([])
const qVisualColLabel = ref<Record<string, string>>({})
const qVisualColEnumJson = ref<Record<string, string>>({})
const qVisualSearchOr = ref<string[]>([])
const qVisualSearchParam = ref('kw')
const qVisualParamOrder = ref('kw')
const qVisualOrComposition = ref<'LEGACY_OR' | 'UNION_ALL'>('LEGACY_OR')
const qTableStats = ref<TableStats | null>(null)
const qTableStatsLoading = ref(false)
const qExactCountLoading = ref(false)
const qVisualTableRowsEstimate = ref<number | null>(null)
const qVisualFixedRows = ref<VisualFixedRow[]>([])
const pendingVisualEditRestore = ref<{ cols: string[]; table: string } | null>(null)

const qBenchmarkDlgOpen = ref(false)
const qBenchmarkArgs = ref('')
const qBenchmarkLoading = ref(false)
const qBenchmarkResult = ref<VisualBenchmarkResult | null>(null)
const qIndexAdviceLoading = ref(false)
const qIndexAdviceResult = ref<VisualIndexAdviceResult | null>(null)
const qWizardTestArgs = ref('')
const qWizardTestResult = ref('')
const qWizardTestRunning = ref(false)
const qWizardTestComposition = ref<'STORED' | 'LEGACY_OR' | 'UNION_ALL'>('STORED')

const qVisualColumnsFiltered = computed(() => {
  const q = qVisualColumnFilter.value.trim().toLowerCase()
  if (!q) return qVisualColumns.value
  return qVisualColumns.value.filter((c) => {
    if (c.name.toLowerCase().includes(q)) return true
    const cm = c.comment?.trim().toLowerCase() ?? ''
    return cm.includes(q)
  })
})

const qVisualUnionHint = computed(() => {
  const n = qTableStats.value?.exactCount ?? qTableStats.value?.tableRowsEstimate
  if (n == null || n < TABLE_ROWS_UNION_HINT) return ''
  return `当前表估算/精确行数 ≥ ${TABLE_ROWS_UNION_HINT.toLocaleString()}，大表多列 OR 时可优先考虑 UNION（仍请用「对比耗时」验证）。`
})

const transferColumnData = computed(() =>
  qVisualColumns.value.map((c) => ({
    key: c.name,
    label: formatColCheckbox(c),
    disabled: false,
  })),
)

function formatColCheckbox(c: VisualColumnMeta) {
  const cm = c.comment?.trim()
  if (cm) return `${cm}（${c.name}）`
  return c.name
}

function removeDisplayColFromTransfer(colKey: string) {
  if (!qVisualDisplayCols.value.includes(colKey)) return
  qVisualDisplayCols.value = qVisualDisplayCols.value.filter((k) => k !== colKey)
}

function defaultLabelForColumnName(name: string) {
  const m = qVisualColumns.value.find((x) => x.name === name)
  const cm = m?.comment?.trim()
  return cm || name
}

function visualColCollapseTitle(colName: string) {
  const custom = (qVisualColLabel.value[colName] ?? '').trim()
  const display = custom || defaultLabelForColumnName(colName)
  return `${display}（${colName}）`
}

function effectiveVisualDisplayLabel(colName: string) {
  const custom = (qVisualColLabel.value[colName] ?? '').trim()
  return custom || defaultLabelForColumnName(colName)
}

function fillEnumFromColumnComment(col: string) {
  const meta = qVisualColumns.value.find((x) => x.name === col)
  let json = parseCommentToEnumJson(meta?.comment ?? undefined)
  if (!json) {
    json = parseCommentToEnumJson(qVisualColLabel.value[col])
  }
  if (!json) {
    ElMessage.warning('未识别到「数字-文案」片段（例：2-已支付 3-未支付），请检查字段注释或展示名')
    return
  }
  qVisualColEnumJson.value[col] = json
  ElMessage.success('已生成枚举 JSON')
}

function resetVisualWizard() {
  qVisualTableQ.value = ''
  qVisualTables.value = []
  qVisualColumns.value = []
  qVisualColumnFilter.value = ''
  qVisualTable.value = ''
  qVisualDisplayCols.value = []
  qVisualColLabel.value = {}
  qVisualColEnumJson.value = {}
  qVisualSearchOr.value = []
  qVisualSearchParam.value = 'kw'
  qVisualParamOrder.value = 'kw'
  qVisualOrComposition.value = 'LEGACY_OR'
  qTableStats.value = null
  qVisualTableRowsEstimate.value = null
  qVisualFixedRows.value = []
  qVisualWizardStep.value = 0
  visualWizardSelectedCol.value = null
  qVisualStatsCollapse.value = []
  qVisualIndexAdviceOpen.value = []
  qBenchmarkResult.value = null
  qIndexAdviceResult.value = null
  qWizardTestArgs.value = ''
  qWizardTestResult.value = ''
}

function resetWizardTestState() {
  qWizardTestArgs.value = ''
  qWizardTestResult.value = ''
  qWizardTestComposition.value = 'STORED'
}

function addVisualFixedRow() {
  qVisualFixedRows.value.push({ column: '', valueType: 'INT', operator: 'EQ', intValue: 0, boolValue: false })
}

function removeVisualFixedRow(idx: number) {
  qVisualFixedRows.value.splice(idx, 1)
}

async function loadMetaTables() {
  if (!props.datasourceId) return
  qMetaTablesLoading.value = true
  try {
    const { data } = await api.get<string[]>(`/admin/datasources/${props.datasourceId}/metadata/tables`, {
      params: { q: qVisualTableQ.value || undefined },
    })
    qVisualTables.value = data
  } finally {
    qMetaTablesLoading.value = false
  }
}

async function loadMetaColumns() {
  if (!props.datasourceId || !qVisualTable.value) return
  qMetaColsLoading.value = true
  try {
    const { data } = await api.get<VisualColumnMeta[]>(
      `/admin/datasources/${props.datasourceId}/metadata/tables/${encodeURIComponent(qVisualTable.value)}/columns`,
    )
    qVisualColumns.value = data
  } finally {
    qMetaColsLoading.value = false
  }
  await loadTableStats()
}

async function loadTableStats() {
  if (!props.datasourceId || !qVisualTable.value) {
    qTableStats.value = null
    return
  }
  qTableStatsLoading.value = true
  try {
    const { data } = await api.get<TableStats>(
      `/admin/datasources/${props.datasourceId}/metadata/tables/${encodeURIComponent(qVisualTable.value)}/stats`,
    )
    qTableStats.value = data
    if (data.tableRowsEstimate != null && Number.isFinite(data.tableRowsEstimate)) {
      qVisualTableRowsEstimate.value = data.tableRowsEstimate
    }
  } catch {
    qTableStats.value = null
  } finally {
    qTableStatsLoading.value = false
  }
}

async function fetchExactTableCount() {
  if (!props.datasourceId || !qVisualTable.value) return
  qExactCountLoading.value = true
  try {
    const { data } = await api.get<TableStats>(
      `/admin/datasources/${props.datasourceId}/metadata/tables/${encodeURIComponent(qVisualTable.value)}/stats`,
      { params: { exactCount: true, exactCountTimeoutSeconds: 10 } },
    )
    qTableStats.value = data
    if (data.exactCount != null) {
      qVisualTableRowsEstimate.value = data.exactCount
    }
    if (data.exactCountError) {
      ElMessage.warning(`精确计数未完成：${data.exactCountError}`)
    } else if (data.exactCount != null) {
      ElMessage.success(`精确行数：${data.exactCount.toLocaleString()}`)
    }
  } finally {
    qExactCountLoading.value = false
  }
}

function openBenchmarkDialog() {
  if (qVisualSearchOr.value.length < 2) {
    ElMessage.warning('至少勾选两个 OR 检索列才能对比')
    return
  }
  qBenchmarkArgs.value = ''
  qBenchmarkResult.value = null
  qBenchmarkDlgOpen.value = true
}

async function postVisualBenchmark(
  datasourceId: number,
  visualConfigJson: string,
  maxRowsVal: number,
  timeoutMsVal: number,
  argsLine: string,
) {
  const parts = argsLine.trim() ? argsLine.trim().split(/\s+/) : []
  return api.post<VisualBenchmarkResult>(`/admin/datasources/${datasourceId}/visual-query/benchmark`, {
    visualConfigJson,
    maxRows: Math.min(200, Math.max(1, maxRowsVal)),
    timeoutMs: Math.min(60_000, Math.max(500, timeoutMsVal)),
    args: parts,
  })
}

async function runVisualBenchmark() {
  if (!props.datasourceId) return
  let visualJson: string
  try {
    visualJson = buildVisualConfigJson()
  } catch {
    ElMessage.warning('向导配置无效')
    return
  }
  qBenchmarkLoading.value = true
  try {
    const { data } = await postVisualBenchmark(
      props.datasourceId,
      visualJson,
      maxRows.value,
      timeoutMs.value,
      qBenchmarkArgs.value,
    )
    qBenchmarkResult.value = data
  } finally {
    qBenchmarkLoading.value = false
  }
}

async function postIndexAdvice(datasourceId: number, visualConfigJson: string) {
  return api.post<VisualIndexAdviceResult>(`/admin/datasources/${datasourceId}/visual-query/index-advice`, {
    visualConfigJson,
  })
}

async function fetchIndexAdvice() {
  if (!props.datasourceId) return
  let visualJson: string
  try {
    visualJson = buildVisualConfigJson()
  } catch {
    ElMessage.warning('向导配置无效')
    return
  }
  qIndexAdviceLoading.value = true
  try {
    const { data } = await postIndexAdvice(props.datasourceId, visualJson)
    qIndexAdviceResult.value = data
    qVisualIndexAdviceOpen.value = []
  } finally {
    qIndexAdviceLoading.value = false
  }
}

async function copyIndexDdl() {
  const lines = qIndexAdviceResult.value?.ddlStatements ?? []
  const text = lines.join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制 DDL 到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function runWizardSqlTest() {
  if (!props.botId || props.queryEditId == null) {
    ElMessage.warning('请先保存查询，再运行测试')
    return
  }
  const parts = qWizardTestArgs.value.trim() ? qWizardTestArgs.value.trim().split(/\s+/) : []
  const body: { args: string[]; orCompositionStrategy?: string; visualConfigJsonOverride?: string } = {
    args: parts,
  }
  if (qWizardTestComposition.value !== 'STORED') {
    body.orCompositionStrategy = qWizardTestComposition.value
    let visualJson: string
    try {
      visualJson = buildVisualConfigJson()
    } catch {
      ElMessage.warning('向导配置无效，无法按 OR/UNION 重编译')
      return
    }
    body.visualConfigJsonOverride = visualJson
  }
  qWizardTestRunning.value = true
  qWizardTestResult.value = ''
  try {
    const { data } = await api.post(`/admin/queries/${props.queryEditId}/test`, body)
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

function applyVisualConfigFromJson(json: string | null | undefined) {
  resetVisualWizard()
  if (!json) return
  const v = JSON.parse(json) as VisualConfigPayload
  qVisualTable.value = v.table || ''
  qVisualSearchParam.value = v.searchParamName || 'kw'
  qVisualParamOrder.value = (v.paramOrder && v.paramOrder.length ? v.paramOrder : [qVisualSearchParam.value]).join(', ')
  qVisualSearchOr.value = v.searchOrColumns ? [...v.searchOrColumns] : []
  const labels: Record<string, string> = {}
  const enums: Record<string, string> = {}
  const cols: string[] = []
  for (const s of v.select || []) {
    if (!s.column) continue
    cols.push(s.column)
    labels[s.column] = s.label || s.column
    enums[s.column] = s.enum && Object.keys(s.enum).length ? JSON.stringify(s.enum) : ''
  }
  qVisualDisplayCols.value = cols
  qVisualColLabel.value = labels
  qVisualColEnumJson.value = enums
  qVisualFixedRows.value = []
  if (Array.isArray(v.fixedPredicates)) {
    for (const fp of v.fixedPredicates) {
      if (!fp?.column) continue
      const vt = (fp.valueType || 'INT').toUpperCase() === 'BOOL' ? 'BOOL' : 'INT'
      const op = (fp.operator || 'EQ').toUpperCase() === 'NE' ? 'NE' : 'EQ'
      qVisualFixedRows.value.push({
        column: fp.column,
        valueType: vt,
        operator: op,
        intValue: typeof fp.intValue === 'number' ? fp.intValue : Number(fp.intValue) || 0,
        boolValue: !!fp.boolValue,
      })
    }
  }
  qVisualOrComposition.value = v.orCompositionStrategy === 'UNION_ALL' ? 'UNION_ALL' : 'LEGACY_OR'
  qVisualTableRowsEstimate.value =
    typeof v.tableRowsEstimate === 'number' && Number.isFinite(v.tableRowsEstimate) ? v.tableRowsEstimate : null
}

function buildVisualConfigJson(): string {
  const select: VisualSelectItem[] = qVisualDisplayCols.value.map((col) => {
    const label = (qVisualColLabel.value[col] || col).trim() || col
    const row: VisualSelectItem = { column: col, label }
    const en = (qVisualColEnumJson.value[col] || '').trim()
    if (en) {
      try {
        row.enum = JSON.parse(en) as Record<string, string>
      } catch {
        throw new Error('ENUM_JSON')
      }
    }
    return row
  })
  const parts = qVisualParamOrder.value
    .split(/[\s,]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  const paramOrder = parts.length ? parts : [qVisualSearchParam.value.trim() || 'kw']
  const payload: VisualConfigPayload = {
    table: qVisualTable.value.trim(),
    select,
    searchOrColumns: [...qVisualSearchOr.value],
    searchParamName: qVisualSearchParam.value.trim() || 'kw',
    paramOrder,
  }
  const fixed = qVisualFixedRows.value
    .filter((r) => r.column.trim())
    .map((r) => {
      if (r.valueType === 'BOOL') {
        const o: VisualFixedPayload = { column: r.column.trim(), valueType: 'BOOL', boolValue: !!r.boolValue }
        if (r.operator === 'NE') o.operator = 'NE'
        return o
      }
      const o: VisualFixedPayload = {
        column: r.column.trim(),
        valueType: 'INT',
        intValue: Math.trunc(Number(r.intValue)) || 0,
      }
      if (r.operator === 'NE') o.operator = 'NE'
      return o
    })
  if (fixed.length) payload.fixedPredicates = fixed
  payload.orCompositionStrategy = qVisualOrComposition.value
  if (qVisualTableRowsEstimate.value != null && Number.isFinite(qVisualTableRowsEstimate.value)) {
    payload.tableRowsEstimate = qVisualTableRowsEstimate.value
  }
  return JSON.stringify(payload)
}

function validateStep(stepIndex: number): boolean {
  if (stepIndex === 0) {
    if (!props.datasourceId) {
      ElMessage.warning('请选择数据源')
      return false
    }
    if (!qVisualTable.value?.trim()) {
      ElMessage.warning('请选择数据表')
      return false
    }
    if (!qVisualDisplayCols.value.length) {
      ElMessage.warning('请至少选择一列到「查询结果列」')
      return false
    }
    return true
  }
  if (stepIndex === 1) {
    for (const col of qVisualDisplayCols.value) {
      const en = (qVisualColEnumJson.value[col] ?? '').trim()
      if (!en) continue
      try {
        JSON.parse(en)
      } catch {
        ElMessage.warning(`列「${col}」的枚举 JSON 格式不正确`)
        return false
      }
    }
    return true
  }
  if (stepIndex === 2) {
    if (!qVisualTable.value?.trim()) {
      ElMessage.warning('请先在第一步选择数据表')
      return false
    }
    const fixedWithCol = qVisualFixedRows.value.filter((r) => r.column?.trim())
    if (fixedWithCol.length && (qMetaColsLoading.value || !qVisualColumns.value.length)) {
      ElMessage.warning('列元数据仍在加载，请稍候再点「下一步」')
      return false
    }
    if (!qVisualColumns.value.length) {
      return true
    }
    const colNames = new Set(qVisualColumns.value.map((c) => c.name))
    for (let i = 0; i < qVisualFixedRows.value.length; i++) {
      const r = qVisualFixedRows.value[i]
      const col = r.column?.trim()
      if (!col) continue
      if (!colNames.has(col)) {
        ElMessage.warning(`固定条件第 ${i + 1} 行：列「${col}」不在当前表字段中`)
        return false
      }
    }
    return true
  }
  if (stepIndex === 3) {
    const spRaw = qVisualSearchParam.value.trim() || 'kw'
    const sp = spRaw.toLowerCase()
    const parts = qVisualParamOrder.value
      .split(/[\s,]+/)
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => s.toLowerCase())
    const order = parts.length ? parts : [sp]
    if (!order.includes(sp)) {
      ElMessage.warning(`参数顺序中须包含关键词参数名「${spRaw}」`)
      return false
    }
    return true
  }
  return true
}

function syncVisualWizardSelectionAfterStepChange() {
  if (qVisualWizardStep.value !== 1 || !qVisualDisplayCols.value.length) return
  const cur = visualWizardSelectedCol.value
  if (!cur || !qVisualDisplayCols.value.includes(cur)) {
    visualWizardSelectedCol.value = qVisualDisplayCols.value[0] ?? null
  }
}

function goVisualWizardNext() {
  if (qVisualWizardStep.value >= WIZARD_STEP_LAST) return
  if (!validateStep(qVisualWizardStep.value)) return
  qVisualWizardStep.value++
  syncVisualWizardSelectionAfterStepChange()
}

function goVisualWizardPrev() {
  if (qVisualWizardStep.value <= 0) return
  qVisualWizardStep.value--
  syncVisualWizardSelectionAfterStepChange()
}

function onWizardStepTitleClick(step: number) {
  if (step === qVisualWizardStep.value) return
  if (step > qVisualWizardStep.value) return
  qVisualWizardStep.value = step
  syncVisualWizardSelectionAfterStepChange()
}

function destroyColSortable() {
  if (colSortable) {
    colSortable.destroy()
    colSortable = null
  }
}

function setupColSortable() {
  destroyColSortable()
  if (qVisualWizardStep.value !== 1 || !props.active) return
  void nextTick(() => {
    const el = colSortListRef.value
    if (!el || !qVisualDisplayCols.value.length) return
    colSortable = Sortable.create(el, {
      animation: 160,
      handle: '.col-sort-handle',
      ghostClass: 'col-sort-ghost',
      chosenClass: 'col-sort-chosen',
      onEnd(evt) {
        const oi = evt.oldIndex
        const ni = evt.newIndex
        if (oi == null || ni == null || oi === ni) return
        const arr = [...qVisualDisplayCols.value]
        const moved = arr.splice(oi, 1)[0]
        if (moved === undefined) return
        arr.splice(ni, 0, moved)
        qVisualDisplayCols.value = arr
        void nextTick(() => {
          destroyColSortable()
          setupColSortable()
        })
      },
    })
  })
}

watch(
  () => [qVisualWizardStep.value, props.active, qVisualDisplayCols.value.length] as const,
  () => {
    destroyColSortable()
    setupColSortable()
  },
)

onBeforeUnmount(() => {
  destroyColSortable()
})

watch(
  () => [...qVisualDisplayCols.value],
  (cols) => {
    const nextL = { ...qVisualColLabel.value }
    const nextE = { ...qVisualColEnumJson.value }
    for (const c of cols) {
      if (nextL[c] === undefined) nextL[c] = defaultLabelForColumnName(c)
      if (nextE[c] === undefined) nextE[c] = ''
    }
    for (const k of Object.keys(nextL)) {
      if (!cols.includes(k)) delete nextL[k]
    }
    for (const k of Object.keys(nextE)) {
      if (!cols.includes(k)) delete nextE[k]
    }
    qVisualColLabel.value = nextL
    qVisualColEnumJson.value = nextE
    const sel = visualWizardSelectedCol.value
    if (sel && !cols.includes(sel)) {
      visualWizardSelectedCol.value = cols.length ? cols[0]! : null
    } else if (!sel && cols.length && qVisualWizardStep.value === 1) {
      visualWizardSelectedCol.value = cols[0]!
    }
  },
)

watch(
  () => props.active,
  (open) => {
    if (!open) pendingVisualEditRestore.value = null
  },
)

watch(
  () => [props.active, props.datasourceId] as const,
  async ([open, ds]) => {
    if (!open) return
    if (!ds) {
      pendingVisualEditRestore.value = null
      return
    }
    await loadMetaTables()
    if (qVisualTable.value) await loadMetaColumns()
    await nextTick()
    await nextTick()
    const p = pendingVisualEditRestore.value
    if (p?.cols.length && !qVisualDisplayCols.value.length) {
      qVisualDisplayCols.value = [...p.cols]
    }
    if (p?.table && !qVisualTable.value?.trim()) {
      qVisualTable.value = p.table
    }
    pendingVisualEditRestore.value = null
  },
)

watch(
  () => [props.active, qVisualTable.value, props.datasourceId] as const,
  async ([open, table, ds]) => {
    if (!open || !ds || !table) return
    await loadMetaColumns()
  },
)

watch(
  () => qVisualTable.value,
  (nv, ov) => {
    if (!props.active) return
    if (ov && nv !== ov) {
      qVisualFixedRows.value = []
    }
  },
)

/** 编辑回填：由父组件在打开抽屉并 nextTick 后调用 */
async function hydrateFromSavedJson(json: string) {
  applyVisualConfigFromJson(json)
  const cols = [...qVisualDisplayCols.value]
  const table = (qVisualTable.value || '').trim()
  pendingVisualEditRestore.value = cols.length ? { cols, table } : null
  await loadMetaTables()
  if (qVisualTable.value) await loadMetaColumns()
  await nextTick()
  await nextTick()
  const p = pendingVisualEditRestore.value
  if (p?.cols.length && !qVisualDisplayCols.value.length) {
    qVisualDisplayCols.value = [...p.cols]
  }
  if (p?.table && !qVisualTable.value?.trim()) {
    qVisualTable.value = p.table
  }
  pendingVisualEditRestore.value = null
}

/** 保存前由父组件调用：表与结果列校验（与原先 saveQuery 一致） */
function validateVisualSavePreconditions(): boolean {
  if (!qVisualTable.value.trim()) {
    ElMessage.warning('请选择数据表')
    return false
  }
  if (!qVisualDisplayCols.value.length) {
    ElMessage.warning('请至少选择一列作为查询结果')
    return false
  }
  return true
}

defineExpose({
  buildVisualConfigJson,
  resetVisualWizard,
  hydrateFromSavedJson,
  resetWizardTestState,
  validateVisualSavePreconditions,
  /** 新建/打开编辑时重置向导步 */
  setWizardStep(step: number) {
    qVisualWizardStep.value = step
  },
  goVisualWizardPrev,
  goVisualWizardNext,
  /** 供抽屉底部栏绑定禁用态 */
  wizardStep: qVisualWizardStep,
  wizardStepLast: WIZARD_STEP_LAST,
})
</script>

<template>
  <div class="visual-wizard-shell">
    <el-steps :active="qVisualWizardStep" finish-status="success" simple align-center class="visual-wizard-steps">
      <el-step v-for="(title, idx) in VISUAL_WIZARD_STEP_TITLES" :key="'wiz-step-' + idx">
        <template #title>
          <span
            class="visual-wizard-step-title"
            :class="{ 'visual-wizard-step-title--navigable': qVisualWizardStep > idx }"
            @click="onWizardStepTitleClick(idx)"
            >{{ title }}</span
          >
        </template>
      </el-step>
    </el-steps>

    <div class="visual-wizard-step-body">
      <div v-show="qVisualWizardStep === 0" class="visual-wizard-step-pane">
        <el-form-item label="表名筛选">
          <div style="display: flex; gap: 8px; width: 100%; flex-wrap: wrap">
            <el-input v-model="qVisualTableQ" placeholder="可选：前缀过滤" clearable style="max-width: 200px" />
            <el-button :loading="qMetaTablesLoading" @click="loadMetaTables">刷新表列表</el-button>
          </div>
        </el-form-item>
        <el-form-item label="数据表">
          <el-select
            v-model="qVisualTable"
            placeholder="选择表"
            filterable
            style="width: 100%"
            :loading="qMetaTablesLoading"
          >
            <el-option v-for="t in qVisualTables" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果列">
          <div v-loading="qMetaColsLoading" class="visual-transfer-wrap">
            <el-transfer
              v-if="qVisualColumns.length"
              :key="`vtransfer-${queryEditId ?? 'new'}-${qVisualTable}-${qVisualColumns.length}`"
              v-model="qVisualDisplayCols"
              filterable
              validate-event="false"
              :titles="['可选字段', '查询结果列']"
              filter-placeholder="筛选列名或注释"
              :data="transferColumnData"
            >
              <template #default="{ option }">
                <span class="transfer-option-row">
                  <span class="transfer-option-text" :title="String(option.label)">{{ option.label }}</span>
                  <el-button
                    v-if="qVisualDisplayCols.includes(String(option.key))"
                    type="danger"
                    link
                    size="small"
                    class="transfer-option-remove"
                    title="移出结果列"
                    @click.stop="removeDisplayColFromTransfer(String(option.key))"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </span>
              </template>
            </el-transfer>
            <span v-else class="form-hint">请先选择数据表并等待列加载</span>
          </div>
        </el-form-item>
      </div>

      <div v-show="qVisualWizardStep === 1" class="visual-wizard-step-pane visual-step1-outer">
        <p class="form-hint visual-step1-top-hint">
          拖动左侧手柄调整 SELECT 与 Telegram 结果列顺序；点击行后在右侧编辑展示名与枚举。
        </p>
        <div class="visual-step1-split">
        <div class="visual-step1-left">
          <div class="visual-step1-left-scroll">
            <div ref="colSortListRef" class="visual-col-sort-list">
              <div
                v-for="(col, idx) in qVisualDisplayCols"
                :key="'sort-' + col"
                class="visual-col-sort-row"
                :class="{ active: visualWizardSelectedCol === col }"
                @click="visualWizardSelectedCol = col"
              >
                <span class="col-sort-handle" @click.stop>
                  <el-icon><Rank /></el-icon>
                </span>
                <span class="col-sort-idx">{{ idx + 1 }}</span>
                <div class="col-sort-meta">
                  <div class="col-sort-title">{{ visualColCollapseTitle(col) }}</div>
                  <div class="col-sort-sub">{{ col }}</div>
                </div>
              </div>
            </div>
          </div>
          <span v-if="!qVisualDisplayCols.length" class="form-hint">暂无结果列，请返回上一步选择列。</span>
        </div>
        <div class="visual-step1-right">
          <div class="visual-step1-right-scroll">
            <template v-if="visualWizardSelectedCol">
              <div class="visual-step1-detail-panel">
                <header class="visual-step1-detail-top">
                  <p class="visual-step1-detail-kicker">Telegram 发送预览</p>
                  <div class="visual-step1-detail-title-row">
                    <strong class="visual-step1-detail-display">{{
                      effectiveVisualDisplayLabel(visualWizardSelectedCol)
                    }}</strong>
                    <code class="visual-step1-detail-colchip">{{ visualWizardSelectedCol }}</code>
                  </div>
                  <p class="visual-step1-detail-muted">
                    下方「展示名」会实时影响上一行预览；留空则沿用 COMMENT 或列名。
                  </p>
                </header>

                <section class="visual-step1-section">
                  <div class="visual-step1-field-label">展示名</div>
                  <el-input
                    v-model="qVisualColLabel[visualWizardSelectedCol]"
                    placeholder="默认取 COMMENT，可改为更短的发送文案"
                    clearable
                  />
                </section>

                <section class="visual-step1-section visual-step1-section--enum">
                  <div class="visual-step1-field-label-row">
                    <span class="visual-step1-field-label">枚举映射（可选）</span>
                    <el-button type="primary" plain size="small" @click="fillEnumFromColumnComment(visualWizardSelectedCol)">
                      从注释解析
                    </el-button>
                  </div>
                  <el-input
                    v-model="qVisualColEnumJson[visualWizardSelectedCol]"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 14 }"
                    placeholder='JSON，如 {"2":"已支付","3":"未支付"}；注释含「数字-文案」可点右上角解析'
                    class="visual-step1-enum-textarea"
                  />
                  <p class="visual-step1-detail-hint">
                    含「数字-文案」的注释可一键解析；末尾 <code>(列名)</code> 会自动忽略。
                  </p>
                </section>
              </div>
            </template>
            <div v-else class="visual-step1-empty-hint">请先在左侧选择一列，或返回上一步添加结果列。</div>
          </div>
        </div>
        </div>
      </div>

      <div v-show="qVisualWizardStep === 2" class="visual-wizard-step-pane visual-step2-pane">
        <div class="visual-step2-scroll">
          <el-form-item v-if="qVisualTable" label="表规模 / SQL 组合">
            <div class="visual-step2-stats-card">
              <div v-loading="qTableStatsLoading" class="visual-step2-stats-inner">
                <template v-if="qTableStats">
                  <p style="margin: 0 0 8px 0; font-size: 13px; color: var(--el-text-color-regular)">
                    估算行数（TABLE_ROWS）：
                    <strong>{{
                      qTableStats.tableRowsEstimate != null ? qTableStats.tableRowsEstimate.toLocaleString() : '—'
                    }}</strong>
                    <span v-if="qTableStats.engine"> · 引擎 {{ qTableStats.engine }}</span>
                  </p>
                </template>
                <p v-else class="form-hint">正在读取或暂时无法获取表统计</p>
                <div style="margin-top: 10px">
                  <span style="margin-right: 8px; font-size: 13px; color: var(--el-text-color-regular)">多列关键词 SQL：</span>
                  <el-radio-group v-model="qVisualOrComposition">
                    <el-radio-button value="LEGACY_OR">OR（兼容）</el-radio-button>
                    <el-radio-button value="UNION_ALL">UNION（去重）</el-radio-button>
                  </el-radio-group>
                </div>
                <el-collapse v-model="qVisualStatsCollapse" class="visual-stats-collapse">
                  <el-collapse-item title="更多：精确计数与风险提示" name="stats">
                    <template v-if="qTableStats">
                      <p v-if="qTableStats.exactCount != null" style="margin: 0 0 8px 0; font-size: 13px">
                        精确行数：<strong>{{ qTableStats.exactCount.toLocaleString() }}</strong>
                      </p>
                      <p v-else-if="qTableStats.exactCountError" style="margin: 0 0 8px 0; color: #e6a23c; font-size: 13px">
                        精确计数：{{ qTableStats.exactCountError }}
                      </p>
                      <el-button size="small" :loading="qExactCountLoading" @click="fetchExactTableCount"
                        >精确计数（短超时，大表可能慢）</el-button
                      >
                    </template>
                    <el-alert
                      v-if="qVisualUnionHint"
                      type="warning"
                      :closable="false"
                      show-icon
                      style="margin-top: 12px"
                      :title="qVisualUnionHint"
                    />
                    <p class="form-hint" style="margin-top: 10px">
                      多支查询用 SQL <code>UNION</code> 合并（默认去重，与多列 OR 结果更接近）；比 <code>UNION ALL</code> 可能更耗
                      CPU；仍受「最大行数」与超时限制。
                    </p>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </el-form-item>
          <el-form-item v-if="!qVisualTable" label="固定条件">
            <span class="form-hint">请先在第一步选择数据表。</span>
          </el-form-item>
          <template v-else>
            <el-form-item label="固定条件">
              <div class="visual-step2-table-wrap">
                <el-table
                  :data="qVisualFixedRows"
                  border
                  class="fixed-predicates-table"
                  :header-cell-style="{ whiteSpace: 'nowrap' }"
                >
                  <el-table-column label="列" min-width="200">
                    <template #default="scope">
                      <el-select v-model="scope.row.column" placeholder="选择列" filterable style="width: 100%">
                        <el-option v-for="c in qVisualColumns" :key="'fx-' + c.name" :label="c.name" :value="c.name" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="条件" width="108" align="center">
                    <template #default="scope">
                      <el-select v-model="scope.row.operator" style="width: 100%">
                        <el-option label="等于 =" value="EQ" />
                        <el-option label="不等于 ≠" value="NE" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型" width="124">
                    <template #default="scope">
                      <el-select v-model="scope.row.valueType" style="width: 100%">
                        <el-option label="整数 INT" value="INT" />
                        <el-option label="布尔 BOOL" value="BOOL" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="比较值" min-width="160">
                    <template #default="scope">
                      <el-input-number
                        v-if="scope.row.valueType === 'INT'"
                        v-model="scope.row.intValue"
                        controls-position="right"
                        style="width: 100%"
                      />
                      <el-switch v-else v-model="scope.row.boolValue" />
                    </template>
                  </el-table-column>
                  <el-table-column label="" width="72" align="center">
                    <template #default="scope">
                      <el-button size="small" type="danger" link @click="removeVisualFixedRow(scope.$index)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="fixed-predicates-toolbar">
                <el-button type="primary" plain size="small" @click="addVisualFixedRow">添加条件</el-button>
              </div>
              <el-alert type="info" :closable="false" show-icon class="visual-hint-alert">
                写入 SQL 的固定 AND，Telegram 用户不可改；仅支持 INT/BOOL 字面量（例如 <code>status &lt;&gt; 9</code>）。切换数据表会清空本表。
              </el-alert>
            </el-form-item>
            <el-form-item label="OR 检索列" class="visual-or-cols-form-item">
              <p class="form-hint" style="margin: 0 0 8px 0">
                与「列筛选」共用关键字；生成 <code>(col1=#{kw} OR col2=#{kw})</code>。
              </p>
              <div v-if="qVisualColumns.length" class="visual-step3-or-wrap">
                <el-checkbox-group v-model="qVisualSearchOr" class="visual-or-checkbox-group">
                  <el-checkbox v-for="c in qVisualColumnsFiltered" :key="'or-' + c.name" :label="c.name">
                    <span :title="c.name">{{ formatColCheckbox(c) }}</span>
                  </el-checkbox>
                </el-checkbox-group>
              </div>
              <span v-else class="form-hint">加载列后可勾选</span>
            </el-form-item>
          </template>
        </div>
      </div>

      <div v-show="qVisualWizardStep === 3" class="visual-wizard-step-pane">
        <el-form-item label="关键词参数名">
          <el-input v-model="qVisualSearchParam" placeholder="默认 kw" style="max-width: 200px" />
          <span class="form-hint">与 Telegram 参数 schema 一致；单关键词时填一个名字即可。</span>
        </el-form-item>
        <el-form-item label="参数顺序">
          <el-input v-model="qVisualParamOrder" placeholder="例如 kw 或 from,to" style="max-width: 320px" />
          <span class="form-hint">空格或逗号分隔，须包含关键词参数名（多列 OR 时常为 kw）。</span>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
          保存时由服务端生成 SQL 并同步<strong>字段映射</strong>（展示名与枚举）；若曾手工改映射，会被向导覆盖；同名列上的<strong>展现流水线</strong>会尽量保留。
        </el-alert>
        <el-form-item label="Telegram 展现">
          <el-select v-model="telegramReplyStyle" style="width: 100%">
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
          <span class="form-hint">与「字段映射」中的展示名、格式、脱敏一起生效；客户端以 Telegram HTML 展示。</span>
        </el-form-item>
        <el-form-item v-if="telegramReplyStyle === 'VALUES_JOIN_CUSTOM'" label="自定义连接符">
          <el-input v-model="telegramJoinDelimiter" placeholder="例如：两个空格、/、·、 | " />
          <span class="form-hint">每条记录会把所有 value 按该连接符拼成一行。</span>
        </el-form-item>
      </div>

      <div v-show="qVisualWizardStep === 4" class="visual-wizard-step-pane">
        <el-card shadow="never" class="visual-tool-card">
          <template #header>
            <div class="visual-tool-card-header">
              <span class="visual-tool-card-title">调优与索引</span>
              <span class="visual-tool-card-sub">对比 OR/UNION 耗时；生成可复制 <code>CREATE INDEX</code>（不在此执行）</span>
            </div>
          </template>
          <el-space wrap size="default">
            <el-button type="primary" :disabled="qVisualSearchOr.length < 2" @click="openBenchmarkDialog"
              >对比 OR / UNION 耗时</el-button
            >
            <el-button type="success" plain :loading="qIndexAdviceLoading" @click="fetchIndexAdvice"
              >生成索引建议与 DDL</el-button
            >
          </el-space>
          <p v-if="qVisualSearchOr.length < 2" class="form-hint" style="margin-top: 10px">
            「对比耗时」需至少勾选两个 OR 检索列；「索引建议」无此限制，可随时点击。
          </p>
          <el-collapse v-if="qIndexAdviceResult" v-model="qVisualIndexAdviceOpen" class="visual-index-advice-collapse">
            <el-collapse-item title="查看索引建议详情与 DDL" name="detail">
              <el-alert type="info" :closable="false" show-icon style="margin-top: 0">{{ qIndexAdviceResult.summary }}</el-alert>
              <ul v-if="qIndexAdviceResult.warnings?.length" class="index-advice-warn-list">
                <li v-for="(w, wi) in qIndexAdviceResult.warnings" :key="'iw-' + wi">{{ w }}</li>
              </ul>
              <template v-if="qIndexAdviceResult.existingIndexSummaries?.length">
                <p class="index-advice-rec-title">当前表已有索引摘要</p>
                <ul class="index-advice-rec-list">
                  <li v-for="(s, si) in qIndexAdviceResult.existingIndexSummaries" :key="'ex-' + si">{{ s }}</li>
                </ul>
              </template>
              <template v-if="qIndexAdviceResult.coverageSkips?.length">
                <p class="index-advice-rec-title">已有索引覆盖（未重复推荐）</p>
                <ul class="index-advice-skip-list">
                  <li v-for="(sk, ski) in qIndexAdviceResult.coverageSkips" :key="'sk-' + ski">{{ sk }}</li>
                </ul>
              </template>
              <p v-if="qIndexAdviceResult.recommendations?.length" class="index-advice-rec-title">建议摘要</p>
              <ul v-if="qIndexAdviceResult.recommendations?.length" class="index-advice-rec-list">
                <li v-for="(rec, ri) in qIndexAdviceResult.recommendations" :key="'rec-' + ri">
                  <span>{{ rec.rationale }}</span>
                  <span v-if="rec.columns?.length" class="index-advice-rec-cols">（列：{{ rec.columns.join(', ') }}）</span>
                </li>
              </ul>
              <p class="index-advice-ddl-title">推荐建索引语句（复制到主库执行）</p>
              <pre v-if="qIndexAdviceResult.ddlStatements?.length" class="index-advice-ddl-pre">{{
                qIndexAdviceResult.ddlStatements.join('\n')
              }}</pre>
              <el-button
                v-if="qIndexAdviceResult.ddlStatements?.length"
                type="primary"
                plain
                style="margin-top: 10px"
                @click="copyIndexDdl"
                >复制全部 DDL</el-button
              >
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <el-divider content-position="left">管理端 SQL 测试</el-divider>
        <el-form-item label="SQL 形态">
          <el-radio-group v-model="qWizardTestComposition" size="small">
            <el-radio-button value="STORED">已保存（与线上一致）</el-radio-button>
            <el-radio-button value="LEGACY_OR">临时 OR 重编译</el-radio-button>
            <el-radio-button value="UNION_ALL">临时 UNION 重编译</el-radio-button>
          </el-radio-group>
          <p class="form-hint" style="margin: 6px 0 0 0">
            OR/UNION 使用当前表单向导 JSON（可不保存先试）；「已保存」与 Telegram 一致。
          </p>
        </el-form-item>
        <el-form-item label="测试参数">
          <div class="wizard-test-row">
            <el-input
              v-model="qWizardTestArgs"
              placeholder="空格分隔，顺序与参数 JSON 一致（与列表里「测试」相同）"
              class="wizard-test-input"
            />
            <el-button
              type="primary"
              :disabled="queryEditId == null"
              :loading="qWizardTestRunning"
              @click="runWizardSqlTest"
              >运行测试</el-button
            >
          </div>
          <el-alert
            v-if="queryEditId == null"
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
        <el-form-item label="超时（毫秒）"><el-input-number v-model="timeoutMs" :min="500" /></el-form-item>
        <el-form-item label="最大行数"><el-input-number v-model="maxRows" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="enabled" /></el-form-item>
      </div>
    </div>
  </div>

  <el-dialog v-model="qBenchmarkDlgOpen" title="对比 OR 与 UNION 耗时" width="720px" destroy-on-close>
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
      在只读库、低峰使用；各策略各跑 2 次取平均，仍受缓冲池影响，仅供参考。不会写入已保存的查询定义。
    </el-alert>
    <el-form label-width="120px">
      <el-form-item label="样例参数">
        <el-input
          v-model="qBenchmarkArgs"
          placeholder="与 Telegram 一致，空格分隔，顺序同参数 JSON（如单关键词：一个值）"
        />
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="qBenchmarkLoading" @click="runVisualBenchmark">开始对比</el-button>
    <template v-if="qBenchmarkResult">
      <el-divider />
      <p style="font-size: 13px; color: #909399">{{ qBenchmarkResult.note }}</p>
      <el-descriptions :column="1" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="OR（LEGACY）">
          <span v-if="qBenchmarkResult.legacyOr?.ok"
            >平均 {{ qBenchmarkResult.legacyOr.durationMsAvg }} ms · 末次行数
            {{ qBenchmarkResult.legacyOr.rowCountLast }}</span
          >
          <span v-else style="color: #f56c6c">{{ qBenchmarkResult.legacyOr?.error || '失败' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="UNION（去重）">
          <span v-if="qBenchmarkResult.unionAll?.ok"
            >平均 {{ qBenchmarkResult.unionAll.durationMsAvg }} ms · 末次行数
            {{ qBenchmarkResult.unionAll.rowCountLast }}</span
          >
          <span v-else style="color: #f56c6c">{{ qBenchmarkResult.unionAll?.error || '失败' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-collapse accordion style="margin-top: 12px">
        <el-collapse-item title="查看生成的 OR SQL" name="o">
          <pre style="white-space: pre-wrap; font-size: 12px">{{ qBenchmarkResult.legacyOr?.sqlTemplate }}</pre>
        </el-collapse-item>
        <el-collapse-item title="查看生成的 UNION SQL" name="u">
          <pre style="white-space: pre-wrap; font-size: 12px">{{ qBenchmarkResult.unionAll?.sqlTemplate }}</pre>
        </el-collapse-item>
      </el-collapse>
    </template>
    <template #footer>
      <el-button @click="qBenchmarkDlgOpen = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.visual-wizard-shell {
  padding: 4px 0 8px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.visual-wizard-steps {
  width: 100%;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.visual-wizard-steps :deep(.el-step__title) {
  font-size: 12px;
  line-height: 1.3;
}

.visual-wizard-step-title {
  display: inline-block;
  max-width: 100%;
}

.visual-wizard-step-title--navigable {
  cursor: pointer;
  text-decoration: underline dotted transparent;
  text-underline-offset: 2px;
}

.visual-wizard-step-title--navigable:hover {
  color: var(--el-color-primary);
  text-decoration-color: color-mix(in srgb, var(--el-color-primary) 55%, transparent);
}

.visual-wizard-step-body {
  padding: 14px 4px 8px 0;
  flex: 1;
  min-height: 0;
  overflow-x: clip;
  overflow-y: hidden;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.visual-wizard-step-pane {
  position: relative;
  z-index: 0;
  min-width: 0;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-x: clip;
  overflow-y: auto;
}

.visual-step2-pane {
  overflow: hidden;
}

.visual-step2-pane .visual-step2-scroll {
  flex: 1;
  min-height: 0;
}

.visual-step1-top-hint {
  margin: 0 0 10px 0;
  flex-shrink: 0;
}

.visual-step1-outer {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.visual-step1-split {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 16px;
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.visual-step1-left {
  flex: 0 0 42%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.visual-step1-left-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.visual-step1-right {
  flex: 1 1 58%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.visual-step1-right-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.visual-step1-detail-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 14px 16px 12px;
  background: var(--admin-elevated, #181c26);
}

.visual-step1-detail-top {
  margin: 0 0 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.visual-step1-detail-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.visual-step1-detail-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.visual-step1-detail-display {
  font-size: 17px;
  font-weight: 600;
  color: var(--el-color-primary);
  line-height: 1.35;
  word-break: break-word;
}

.visual-step1-detail-colchip {
  flex-shrink: 0;
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  padding: 3px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-dark);
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
}

.visual-step1-detail-muted {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}

.visual-step1-section {
  padding-top: 14px;
}

.visual-step1-section--enum {
  padding-bottom: 2px;
}

.visual-step1-field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.visual-step1-field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.visual-step1-field-label-row .visual-step1-field-label {
  margin-bottom: 0;
}

.visual-step1-enum-textarea :deep(textarea) {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}

.visual-step1-detail-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-placeholder);
}

.visual-step2-scroll {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
}

.visual-step2-stats-card {
  width: 100%;
  position: relative;
}

.visual-step2-stats-inner {
  position: relative;
  min-height: 48px;
}

.visual-step2-table-wrap {
  max-height: min(280px, 36vh);
  overflow: auto;
}

.visual-or-cols-form-item {
  align-items: flex-start;
}

.visual-or-cols-form-item :deep(.el-form-item__content) {
  display: block;
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.visual-step3-or-wrap {
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  max-height: min(240px, 32vh);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-dark);
}

.visual-or-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  width: 100%;
  justify-content: flex-start;
  align-content: flex-start;
}

.visual-step1-empty-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 20px 12px;
  text-align: center;
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
}

@media (max-width: 720px) {
  .visual-step1-split {
    flex-direction: column;
    max-height: none;
  }

  .visual-step1-left,
  .visual-step1-right {
    flex: 1 1 auto;
    max-height: min(42vh, 420px);
  }

  .visual-step1-left-scroll,
  .visual-step1-right-scroll {
    max-height: min(40vh, 400px);
  }

  .visual-step1-detail-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .visual-step1-field-label-row {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .visual-step1-field-label-row .el-button {
    align-self: flex-end;
  }
}

.visual-col-sort-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-sizing: border-box;
  width: 100%;
  padding: 8px 6px 6px;
  border-radius: 10px;
  background: var(--el-fill-color-dark);
}

.visual-col-sort-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  background: var(--admin-elevated, #181c26);
}

.visual-col-sort-row.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--el-color-primary) 35%, transparent);
}

.col-sort-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  cursor: grab;
  color: var(--el-text-color-secondary);
  touch-action: none;
}

.col-sort-handle:active {
  cursor: grabbing;
}

.col-sort-idx {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 1.25rem;
}

.col-sort-meta {
  flex: 1;
  min-width: 0;
}

.col-sort-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-sort-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-sort-ghost {
  opacity: 0.55;
}

.col-sort-chosen {
  opacity: 0.95;
}

.visual-transfer-wrap {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  max-height: min(52vh, 640px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.visual-stats-collapse {
  margin-top: 12px;
}

.visual-stats-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
}

.visual-index-advice-collapse {
  margin-top: 14px;
}

.visual-index-advice-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 8px;
}

.visual-tool-card {
  margin: 0 0 12px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color);
  background: var(--admin-elevated, #181c26);
}

.visual-tool-card :deep(.el-card__header) {
  padding: 14px 18px;
}

.visual-tool-card :deep(.el-card__body) {
  padding: 16px 18px 18px;
}

.visual-tool-card-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.visual-tool-card-title {
  font-weight: 600;
  font-size: 15px;
}

.visual-tool-card-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.visual-hint-alert {
  margin-top: 12px;
}

.fixed-predicates-toolbar {
  margin-top: 10px;
  margin-bottom: 4px;
}

.fixed-predicates-table :deep(.el-table__cell) {
  vertical-align: middle;
  padding: 10px 12px;
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

.visual-transfer-wrap :deep(.el-transfer) {
  display: flex;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.visual-transfer-wrap :deep(.el-transfer-panel) {
  min-width: 0;
  flex: 1 1 260px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.visual-transfer-wrap :deep(.el-transfer-panel__body) {
  height: auto !important;
  max-height: min(420px, 52vh) !important;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.visual-transfer-wrap :deep(.el-transfer-panel__list) {
  height: auto !important;
  max-height: none !important;
}

.visual-transfer-wrap :deep(.el-transfer-panel .el-scrollbar) {
  flex: 1;
  min-height: 0;
  height: auto !important;
  max-height: none !important;
}

.visual-transfer-wrap :deep(.el-transfer-panel .el-scrollbar__wrap) {
  max-height: none !important;
  flex: 1;
  min-height: 0;
  height: auto !important;
  overflow-x: hidden !important;
  overflow-y: auto !important;
  scrollbar-gutter: stable;
}

.visual-transfer-wrap :deep(.el-transfer-panel .el-scrollbar__view) {
  display: block;
}

.visual-transfer-wrap :deep(.el-transfer-panel__item) {
  height: auto !important;
  min-height: 26px;
  margin-right: 0;
}

/* 与表头全选同一视觉：表头为普通流勾选框，列表项 EP 默认 absolute 易导致与 15px gutter 不齐，改为行内 flex */
.visual-transfer-wrap :deep(.el-transfer-panel__item.el-checkbox) {
  display: inline-flex !important;
  align-items: flex-start;
  width: 100%;
  box-sizing: border-box;
  padding-left: 15px;
  padding-right: 10px;
  margin-right: 0 !important;
}

.visual-transfer-wrap :deep(.el-transfer-panel__item.el-checkbox .el-checkbox__input) {
  position: relative !important;
  top: auto !important;
  left: auto !important;
  flex-shrink: 0;
  margin-top: 2px;
}

.visual-transfer-wrap :deep(.el-transfer-panel__item.el-checkbox .el-checkbox__label) {
  padding-left: 8px !important;
  line-height: 1.4 !important;
  width: auto !important;
  flex: 1;
  min-width: 0;
}

.visual-transfer-wrap :deep(.el-transfer-panel:first-of-type .el-transfer-panel__item .el-checkbox__label) {
  white-space: normal;
  word-break: break-word;
  line-height: 1.4;
}

.visual-transfer-wrap :deep(.el-transfer-panel:last-of-type .el-transfer-panel__item .el-checkbox__label) {
  width: 100%;
  max-width: 100%;
  white-space: normal;
  word-break: break-word;
  line-height: 1.4;
}

.transfer-option-row {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  max-width: 100%;
  vertical-align: top;
}

.transfer-option-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
  line-height: 1.4;
}

.transfer-option-remove {
  flex-shrink: 0;
  margin-top: 1px;
  padding: 0 2px !important;
  min-height: auto !important;
}

.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
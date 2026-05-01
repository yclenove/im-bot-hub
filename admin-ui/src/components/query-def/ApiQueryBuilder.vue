<script setup lang="ts">
import Sortable from 'sortablejs'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { api } from '../../api/client'
import {
  listQueryPresetsForDatasource,
  type ApiPreset,
  type ApiQueryConfig,
  type ApiQueryPresetAppliedPayload,
} from '../../utils/apiPresets'

type PreviewField = { key: string; label: string; jsonPointer: string; sampleValue: string }
type PresetParamItem = {
  name: string
  source: 'PATH' | 'QUERY' | 'HEADER' | 'BODY' | 'RESULT'
  samples: string[]
}

const props = defineProps<{
  active: boolean
  queryEditId?: number | null
  datasourceId: number
  datasourcePresetKey?: string | null
  timeoutMs: number
  maxRows: number
  enabled: boolean
  telegramReplyStyle: string
  telegramJoinDelimiter: string
}>()

const emit = defineEmits<{
  (e: 'update:timeoutMs', value: number): void
  (e: 'update:maxRows', value: number): void
  (e: 'update:enabled', value: boolean): void
  (e: 'update:telegramReplyStyle', value: string): void
  (e: 'update:telegramJoinDelimiter', value: string): void
  (e: 'update:paramSchemaJson', value: string): void
  (e: 'preset-selected', payload: ApiQueryPresetAppliedPayload): void
}>()

const previewLoading = ref(false)
const previewRows = ref<Record<string, unknown>[]>([])
const previewFields = ref<PreviewField[]>([])
const previewArgsText = ref('')
const selectedPresetKey = ref('')
const outputListEl = ref<HTMLElement | null>(null)
const advancedSections = ref<string[]>([])
const resultSections = ref<string[]>([])

const model = ref<ApiQueryConfig>({
  method: 'GET',
  path: '',
  responseRootPointer: '',
  bodyTemplate: '',
  queryParams: [],
  headers: [],
  outputs: [],
})

let outputSortable: Sortable | null = null

const availablePresets = computed(() => listQueryPresetsForDatasource(props.datasourcePresetKey))
const hasPresetOptions = computed(() => availablePresets.value.length > 0)
const showStarterGuide = computed(() => !selectedPresetKey.value && !model.value.path.trim())
const hasAdvancedQueryContent = computed(
  () =>
    !!model.value.responseRootPointer?.trim() ||
    model.value.queryParams.length > 0 ||
    model.value.headers.length > 0 ||
    !!model.value.bodyTemplate?.trim(),
)
const showAdvancedSummary = computed(() => hasAdvancedQueryContent.value && !advancedSections.value.includes('advanced'))
const advancedSummaryText = computed(() => {
  const parts: string[] = []
  if (model.value.responseRootPointer?.trim()) parts.push('已指定结果位置')
  if (model.value.queryParams.length) parts.push(`请求参数 ${model.value.queryParams.length} 项`)
  if (model.value.headers.length) parts.push(`请求头 ${model.value.headers.length} 项`)
  if (model.value.bodyTemplate?.trim()) parts.push('已配置请求体模板')
  return parts.join('，')
})
const hasResultDetailContent = computed(
  () => previewRows.value.length > 0 || props.timeoutMs !== 5000 || props.maxRows !== 1 || !props.enabled,
)
const showResultDetailSummary = computed(() => hasResultDetailContent.value && !resultSections.value.includes('details'))
const resultDetailSummaryText = computed(() => {
  const parts: string[] = []
  if (previewRows.value.length) parts.push('已获取样例 JSON')
  if (props.timeoutMs !== 5000) parts.push(`超时 ${props.timeoutMs} ms`)
  if (props.maxRows !== 1) parts.push(`最大条数 ${props.maxRows}`)
  if (!props.enabled) parts.push('当前已停用')
  return parts.join('，')
})
const presetEmptyText = computed(() => {
  if (props.datasourcePresetKey) {
    return '当前数据源模板还没有内置查询模板。你可以直接按下方提示从零开始，或继续手工填写接口路径。'
  }
  return '这里暂时没有可直接套用的模板。你可以直接填写接口路径，再点一次预览开始配置。'
})

function collectTemplateParams(
  source: PresetParamItem['source'],
  text: string | undefined,
  output: Map<string, PresetParamItem>,
) {
  if (!text) return
  const regex = /\{\{(\w+)}}/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(text)) != null) {
    const name = match[1]
    if (!name || output.has(name)) continue
    output.set(name, { name, source, samples: [] })
  }
}

function listPresetParams(preset: ApiPreset): PresetParamItem[] {
  const out = new Map<string, PresetParamItem>()
  const pushSample = (item: PresetParamItem, sample: string | undefined) => {
    const text = sample?.trim()
    if (!text || item.samples.includes(text)) return
    item.samples.push(text)
  }
  const fallbackSamplesByName = (name: string): string[] => {
    const key = name.toLowerCase()
    if (key.includes('coin') || key.includes('id')) return ['bitcoin', 'ethereum', 'solana']
    if (key.includes('symbol')) return ['BTCUSDT', 'ETHUSDT', 'SOLUSDT']
    if (key.includes('city')) return ['Beijing', 'Shanghai', 'Tianjin']
    if (key.includes('ip')) return ['8.8.8.8', '1.1.1.1']
    if (key.includes('timezone')) return ['Asia/Shanghai', 'America/New_York']
    if (key.includes('year')) return ['2026', '2025']
    if (key.includes('country')) return ['CN', 'US']
    return []
  }
  const ensureItem = (name: string, source: PresetParamItem['source']) => {
    const existing = out.get(name)
    if (existing) return existing
    const created: PresetParamItem = { name, source, samples: [] }
    out.set(name, created)
    return created
  }
  collectTemplateParams('PATH', preset.config.path, out)
  collectTemplateParams('BODY', preset.config.bodyTemplate, out)
  // normalize items created by collectTemplateParams
  for (const [key, value] of out.entries()) {
    out.set(key, { ...value, samples: value.samples ?? [] })
  }
  for (const item of preset.config.queryParams) {
    if (item.valueSource === 'PARAM' && item.paramName?.trim()) {
      const name = item.paramName.trim()
      const presetItem = ensureItem(name, 'QUERY')
      pushSample(presetItem, item.sampleValue)
    } else {
      collectTemplateParams('QUERY', item.value, out)
      for (const [key, value] of out.entries()) out.set(key, { ...value, samples: value.samples ?? [] })
    }
  }
  for (const item of preset.config.headers) {
    if (item.valueSource === 'PARAM' && item.paramName?.trim()) {
      const name = item.paramName.trim()
      const presetItem = ensureItem(name, 'HEADER')
      pushSample(presetItem, item.sampleValue)
    } else {
      collectTemplateParams('HEADER', item.value, out)
      for (const [key, value] of out.entries()) out.set(key, { ...value, samples: value.samples ?? [] })
    }
  }
  if (preset.config.localResultLimitParamName?.trim()) {
    const name = preset.config.localResultLimitParamName.trim()
    const presetItem = ensureItem(name, 'RESULT')
    pushSample(presetItem, preset.sampleArgs[0])
    pushSample(presetItem, '10')
  }
  const params = Array.from(out.values())
  if (!params.length) return []
  const flattenedSampleArgs = preset.sampleArgs
    .flatMap((item) => item.split(/\s+/))
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
  // Fill by command sample order, then fallback by semantic names.
  params.forEach((item, index) => {
    pushSample(item, flattenedSampleArgs[index])
    for (const fallback of fallbackSamplesByName(item.name)) {
      pushSample(item, fallback)
    }
    item.samples = item.samples.slice(0, 3)
  })
  return params
}

function sourceLabel(source: PresetParamItem['source']): string {
  if (source === 'PATH') return '路径'
  if (source === 'QUERY') return '请求参数'
  if (source === 'HEADER') return '请求头'
  if (source === 'RESULT') return '返回条数'
  return '请求体'
}

function destroyOutputSortable() {
  outputSortable?.destroy()
  outputSortable = null
}

async function initOutputSortable() {
  destroyOutputSortable()
  await nextTick()
  if (!outputListEl.value || model.value.outputs.length < 2) return
  outputSortable = Sortable.create(outputListEl.value, {
    handle: '.api-output-drag',
    animation: 160,
    onEnd: (evt) => {
      const oldIndex = evt.oldIndex
      const newIndex = evt.newIndex
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const next = [...model.value.outputs]
      const [moved] = next.splice(oldIndex, 1)
      next.splice(newIndex, 0, moved)
      model.value.outputs = next.map((item, index) => ({ ...item, sortOrder: index }))
    },
  })
}

watch(
  () => model.value.outputs.length,
  async () => {
    await initOutputSortable()
  },
)

watch(
  () => props.datasourcePresetKey,
  (presetKey) => {
    if (props.queryEditId != null) return
    if (!presetKey || model.value.path.trim()) return
    const match = availablePresets.value[0]
    if (match) {
      selectedPresetKey.value = match.key
      applyPresetInternal(match, false)
    }
  },
  { immediate: true },
)

watch(
  model,
  () => {
    emit('update:paramSchemaJson', buildParamSchemaJson())
  },
  { deep: true, immediate: true },
)

onBeforeUnmount(() => {
  destroyOutputSortable()
})

function resetApiQueryBuilder() {
  advancedSections.value = []
  resultSections.value = []
  model.value = {
    method: 'GET',
    path: '',
    responseRootPointer: '',
    bodyTemplate: '',
    queryParams: [],
    headers: [],
    outputs: [],
  }
  previewRows.value = []
  previewFields.value = []
  previewArgsText.value = ''
  selectedPresetKey.value = ''
}

function hydrateFromSavedJson(json: string) {
  const parsed = JSON.parse(json) as ApiQueryConfig
  advancedSections.value = []
  resultSections.value = []
  model.value = {
    method: parsed.method || 'GET',
    path: parsed.path || '',
    responseRootPointer: parsed.responseRootPointer || '',
    bodyTemplate: parsed.bodyTemplate || '',
    queryParams: Array.isArray(parsed.queryParams) ? parsed.queryParams : [],
    headers: Array.isArray(parsed.headers) ? parsed.headers : [],
    outputs: Array.isArray(parsed.outputs)
      ? parsed.outputs.map((item, index) => ({ ...item, sortOrder: item.sortOrder ?? index }))
      : [],
    presetKey: parsed.presetKey,
    name: parsed.name,
  }
  selectedPresetKey.value = parsed.presetKey || ''
}

/** 从已保存的 paramSchemaJson 回填预览参数，便于再次保存时保留自定义示例。 */
function syncPreviewArgsFromParamSchema(raw: string) {
  previewArgsText.value = ''
  if (!raw?.trim()) return
  try {
    const o = JSON.parse(raw) as { examples?: unknown }
    if (!Array.isArray(o.examples) || o.examples.length === 0) return
    const joined = o.examples
      .map((e) => String(e ?? '').trim())
      .filter(Boolean)
      .join(' ')
      .trim()
    if (joined) previewArgsText.value = joined
  } catch {
    /* ignore */
  }
}

function validateApiSavePreconditions(): boolean {
  if (!props.datasourceId) {
    ElMessage.warning('请先选择 API 数据源')
    return false
  }
  if (!model.value.path.trim()) {
    ElMessage.warning('请填写接口路径')
    return false
  }
  if (!model.value.outputs.length) {
    ElMessage.warning('请至少选择一个返回字段')
    return false
  }
  return true
}

function buildApiConfigJson(): string {
  const normalizedOutputs = model.value.outputs.map((item, index) => ({
    ...item,
    key: item.key.trim(),
    label: item.label.trim(),
    jsonPointer: item.jsonPointer.trim(),
    sortOrder: index,
  }))
  return JSON.stringify({
    ...model.value,
    presetKey: selectedPresetKey.value || model.value.presetKey || undefined,
    outputs: normalizedOutputs,
  })
}

function defaultExampleForParam(name: string): string {
  const k = name.trim().toLowerCase()
  const m: Record<string, string> = {
    coinid: 'bitcoin',
    ids: 'bitcoin',
    vs_currency: 'usd',
    orderno: '10001',
    order_no: '10001',
    symbol: 'BTCUSDT',
    limit: '10',
    days: '7',
    city: 'Beijing',
    q: 'Beijing',
    query: 'Beijing',
    keyword: 'demo',
    kw: 'demo',
  }
  return m[k] || (k.length <= 12 ? k : '…')
}

function buildParamSchemaJson(): string {
  const params: string[] = []
  const pushParam = (name: string | undefined) => {
    const trimmed = name?.trim()
    if (!trimmed || params.includes(trimmed)) return
    params.push(trimmed)
  }
  const scanTemplate = (text: string | undefined) => {
    if (!text) return
    const regex = /\{\{(\w+)}}/g
    let match: RegExpExecArray | null
    while ((match = regex.exec(text)) != null) {
      pushParam(match[1])
    }
  }
  scanTemplate(model.value.path)
  scanTemplate(model.value.bodyTemplate)
  if (model.value.localResultLimitParamName?.trim()) {
    pushParam(model.value.localResultLimitParamName)
  }
  for (const item of [...model.value.queryParams, ...model.value.headers]) {
    if (item.valueSource === 'PARAM') pushParam(item.paramName)
    scanTemplate(item.value)
  }
  if (params.length === 0) {
    return JSON.stringify({ params })
  }
  const argv = previewArgsText.value.trim() ? previewArgsText.value.trim().split(/\s+/) : []
  const examples = params.map((p, i) => {
    const fromPreview = argv[i]?.trim()
    return fromPreview || defaultExampleForParam(p)
  })
  return JSON.stringify({ params, examples })
}

function applyPreset(preset: ApiPreset) {
  applyPresetInternal(preset, true)
}

function applyPresetInternal(preset: ApiPreset, emitPresetSelected: boolean) {
  selectedPresetKey.value = preset.key
  model.value = JSON.parse(JSON.stringify(preset.config)) as ApiQueryConfig
  previewArgsText.value = preset.sampleArgs.join(' ')
  if (emitPresetSelected) {
    const queryDisplayName = (preset.config.name?.trim() || preset.title).trim()
    emit('preset-selected', { commandHint: preset.commandHint, queryDisplayName })
  }
  emit('update:maxRows', 1)
  emit('update:timeoutMs', 5000)
  emit('update:enabled', true)
}

function addQueryParam() {
  model.value.queryParams.push({ key: '', valueSource: 'LITERAL', value: '' })
}

function removeQueryParam(index: number) {
  model.value.queryParams.splice(index, 1)
}

function addHeader() {
  model.value.headers.push({ key: '', valueSource: 'LITERAL', value: '' })
}

function removeHeader(index: number) {
  model.value.headers.splice(index, 1)
}

function addOutputField(field: PreviewField) {
  if (model.value.outputs.some((item) => item.jsonPointer === field.jsonPointer)) {
    ElMessage.info('这个字段已经加入返回内容了')
    return
  }
  model.value.outputs.push({
    key: field.key,
    label: field.label,
    jsonPointer: field.jsonPointer,
    sortOrder: model.value.outputs.length,
    maskType: 'NONE',
    formatType: '',
  })
}

function addAllPreviewFields() {
  previewFields.value.forEach(addOutputField)
}

function removeOutput(index: number) {
  model.value.outputs.splice(index, 1)
  model.value.outputs = model.value.outputs.map((item, idx) => ({ ...item, sortOrder: idx }))
}

async function previewApiResponse() {
  if (!props.datasourceId) {
    ElMessage.warning('请先选择数据源')
    return
  }
  if (!model.value.path.trim()) {
    ElMessage.warning('请先填写接口路径')
    return
  }
  previewLoading.value = true
  try {
    const args = previewArgsText.value.trim() ? previewArgsText.value.trim().split(/\s+/) : []
    const { data } = await api.post(`/admin/datasources/${props.datasourceId}/api-query/preview`, {
      apiConfigJson: buildApiConfigJson(),
      args,
    })
    previewFields.value = Array.isArray(data.fields) ? data.fields : []
    previewRows.value = Array.isArray(data.sampleRows) ? data.sampleRows : []
    if (!previewFields.value.length) {
      ElMessage.warning('预览成功，但没有发现可映射字段')
    } else {
      ElMessage.success('已获取接口返回样例，请点选需要返回给机器人的字段')
    }
  } finally {
    previewLoading.value = false
  }
}

defineExpose({
  resetApiQueryBuilder,
  hydrateFromSavedJson,
  syncPreviewArgsFromParamSchema,
  validateApiSavePreconditions,
  buildApiConfigJson,
  buildParamSchemaJson,
})
</script>

<template>
  <div class="api-builder">
    <el-alert type="info" :closable="false" show-icon>
      <div>
        API 模式适合接第三方服务，如天气、币价、物流状态等。你只需要选模板、填少量参数、预览结果，再点选要返回给机器人的字段。
      </div>
    </el-alert>

    <div class="api-step-grid">
      <section class="api-step-card">
        <div class="api-step-card__index">1</div>
        <div>
          <div class="api-step-card__title">先选模板</div>
          <div class="api-step-card__desc">优先从天气、币价等预制模板开始，少填字段，减少出错。</div>
        </div>
      </section>
      <section class="api-step-card">
        <div class="api-step-card__index">2</div>
        <div>
          <div class="api-step-card__title">再预览返回</div>
          <div class="api-step-card__desc">输入一个样例参数，看看接口真实返回什么，再决定机器人怎么说。</div>
        </div>
      </section>
      <section class="api-step-card">
        <div class="api-step-card__index">3</div>
        <div>
          <div class="api-step-card__title">最后点选字段</div>
          <div class="api-step-card__desc">点击左侧字段加入结果区，再拖拽排序，机器人就会按这个顺序回复。</div>
        </div>
      </section>
    </div>

    <el-alert v-if="showStarterGuide" type="success" :closable="false" show-icon>
      <div>
        如果你是第一次配置 API 查询，最省事的方式是：先点一个模板，再直接点「预览返回」，最后从左侧点字段生成机器人回复。
      </div>
    </el-alert>

    <div v-if="hasPresetOptions" class="api-preset-grid">
      <button
        v-for="preset in availablePresets"
        :key="preset.key"
        type="button"
        class="api-preset-card"
        :class="{ 'api-preset-card--active': selectedPresetKey === preset.key }"
        @click="applyPreset(preset)"
      >
        <div class="api-preset-card__title-row">
          <div class="api-preset-card__title">{{ preset.title }}</div>
          <el-tooltip
            v-if="listPresetParams(preset).length"
            placement="top"
            effect="dark"
          >
            <template #content>
              <div class="preset-param-tooltip">
                <div class="preset-param-tooltip__title">所需参数</div>
                <div v-for="param in listPresetParams(preset)" :key="`${preset.key}-${param.name}`" class="preset-param-tooltip__row">
                  <span class="preset-param-tooltip__name">{{ param.name }}</span>
                  <span class="preset-param-tooltip__meta">{{ sourceLabel(param.source) }}</span>
                  <span class="preset-param-tooltip__sample">{{ param.samples.length ? param.samples.join(' / ') : '-' }}</span>
                </div>
              </div>
            </template>
            <el-icon class="api-preset-card__param-icon" @click.stop>
              <InfoFilled />
            </el-icon>
          </el-tooltip>
        </div>
        <div class="api-preset-card__summary">{{ preset.summary }}</div>
        <div class="api-preset-card__hint">推荐命令：/{{ preset.commandHint }}</div>
      </button>
    </div>
    <el-alert v-else type="warning" :closable="false" show-icon>
      <div>{{ presetEmptyText }}</div>
    </el-alert>

    <el-form label-width="120px" class="api-builder-form">
      <el-form-item label="配置建议">
        <div class="api-guide-banner">
          <strong>推荐顺序：</strong> 选模板 -> 点一次「预览返回」-> 从左侧点字段 -> 右侧拖拽排序 -> 保存查询。
        </div>
      </el-form-item>
      <el-form-item v-if="showStarterGuide" label="从零开始">
        <div class="api-guide-banner">
          没有现成模板也没关系：先填「接口路径」，如果接口需要用户输入，就在“请求参数”里把某项切到“用户输入”，然后点「预览返回」继续。
        </div>
      </el-form-item>
      <el-form-item label="请求方式">
        <el-select v-model="model.method" style="width: 160px">
          <el-option label="GET（推荐）" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
        </el-select>
        <span class="form-hint">大多数公开接口用 GET 就够了，只有接口文档明确要求时再改成 POST / PUT。</span>
      </el-form-item>
      <el-form-item label="接口路径">
        <el-input v-model="model.path" placeholder="例如 /api/v3/ticker/price 或 /{{city}}" />
        <span class="form-hint">这里只填路径，不用重复写整个域名。可用 <code v-pre>{{city}}</code> 这类占位。</span>
      </el-form-item>
      <el-collapse v-model="advancedSections" class="api-advanced-collapse">
        <el-collapse-item name="advanced">
          <template #title>
            <div class="api-advanced-title-wrap">
              <span>高级请求选项（默认可不填）</span>
              <span v-if="showAdvancedSummary" class="api-advanced-title-summary">{{ advancedSummaryText }}</span>
            </div>
          </template>
          <div class="api-advanced-intro">
            只有当接口文档明确提到结果位置、额外参数、固定请求头或请求体时，才需要展开并填写下面这些内容。
          </div>

          <el-form-item label="结果位置">
            <el-input v-model="model.responseRootPointer" placeholder="例如 /current_condition/0；留空表示整个 JSON" />
            <span class="form-hint">如果接口返回很大，只需要指定真正有用的那一段即可。</span>
          </el-form-item>

          <el-divider content-position="left">请求参数</el-divider>
          <div class="api-builder-block-toolbar">
            <el-button size="small" type="primary" plain @click="addQueryParam">添加参数</el-button>
          </div>
          <div v-if="!model.queryParams.length" class="api-empty-hint">当前没有额外参数。大多数预制模板已自动填好。</div>
          <div v-else class="api-empty-hint" style="margin-bottom: 10px">如果某个值来自用户输入，就把中间一列切到「用户输入」，再填参数名。</div>
          <div v-for="(item, index) in model.queryParams" :key="`qp-${index}`" class="api-line-row">
            <el-input v-model="item.key" placeholder="参数名，如 symbol" class="api-line-row__key" />
            <el-select v-model="item.valueSource" class="api-line-row__type">
              <el-option label="固定值" value="LITERAL" />
              <el-option label="用户输入" value="PARAM" />
            </el-select>
            <el-input
              v-if="item.valueSource === 'LITERAL'"
              v-model="item.value"
              placeholder="固定值"
              class="api-line-row__value"
            />
            <el-input
              v-else
              v-model="item.paramName"
              placeholder="参数名，如 symbol"
              class="api-line-row__value"
            />
            <el-button type="danger" link @click="removeQueryParam(index)">删除</el-button>
          </div>

          <el-divider content-position="left">请求头（可选）</el-divider>
          <div class="api-builder-block-toolbar">
            <el-button size="small" plain @click="addHeader">添加请求头</el-button>
          </div>
          <div v-if="!model.headers.length" class="api-empty-hint">通常不需要额外配置，除非目标 API 要求固定 Header。</div>
          <div v-else class="api-empty-hint" style="margin-bottom: 10px">只有目标平台明确要求 Header 时才需要改这里，普通场景可以保持默认。</div>
          <div v-for="(item, index) in model.headers" :key="`hd-${index}`" class="api-line-row">
            <el-input v-model="item.key" placeholder="Header 名" class="api-line-row__key" />
            <el-select v-model="item.valueSource" class="api-line-row__type">
              <el-option label="固定值" value="LITERAL" />
              <el-option label="用户输入" value="PARAM" />
            </el-select>
            <el-input
              v-if="item.valueSource === 'LITERAL'"
              v-model="item.value"
              placeholder="Header 值"
              class="api-line-row__value"
            />
            <el-input
              v-else
              v-model="item.paramName"
              placeholder="参数名"
              class="api-line-row__value"
            />
            <el-button type="danger" link @click="removeHeader(index)">删除</el-button>
          </div>

          <el-form-item v-if="model.method !== 'GET'" label="请求体模板">
            <el-input v-model="model.bodyTemplate" type="textarea" :rows="4" placeholder='例如 {"city":"{{city}}"}' />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>

      <el-divider content-position="left">预览与字段映射</el-divider>
      <el-form-item label="预览参数">
        <div class="api-preview-toolbar">
          <el-input v-model="previewArgsText" placeholder="按顺序填样例参数，用空格分隔，例如 Beijing 或 BTCUSDT" />
          <el-button type="primary" :loading="previewLoading" @click="previewApiResponse">预览返回</el-button>
        </div>
        <span class="form-hint">这里填的是测试样例，不会保存成真实业务数据。</span>
      </el-form-item>

      <div class="api-preview-panels">
        <div class="api-preview-panel">
          <div class="api-preview-panel__head">
            <span>发现的字段</span>
            <el-button v-if="previewFields.length" size="small" plain @click="addAllPreviewFields">全部加入</el-button>
          </div>
          <div v-if="!previewFields.length" class="api-empty-hint">先点击「预览返回」，系统会自动识别字段，供你点选。</div>
          <div v-else class="api-field-list">
            <button
              v-for="field in previewFields"
              :key="field.jsonPointer"
              type="button"
              class="api-field-pill"
              @click="addOutputField(field)"
            >
              <strong>{{ field.label }}</strong>
              <span>{{ field.sampleValue || '（空）' }}</span>
            </button>
          </div>
        </div>

        <div class="api-preview-panel">
          <div class="api-preview-panel__head">
            <span>机器人返回内容</span>
            <span class="api-preview-panel__sub">拖拽排序，决定最终展示顺序</span>
          </div>
          <div v-if="!model.outputs.length" class="api-empty-hint">从左侧点选字段后，这里会生成返回内容模板。</div>
          <template v-else>
            <div class="api-empty-hint" style="margin-bottom: 10px">建议把最关键的信息放前面，比如价格、城市、状态、时间。</div>
            <div ref="outputListEl" class="api-output-list">
              <div v-for="(item, index) in model.outputs" :key="`${item.jsonPointer}-${index}`" class="api-output-row">
                <span class="api-output-drag" title="拖动排序">⠿</span>
                <el-input v-model="item.label" placeholder="展示名称" class="api-output-row__label" />
                <el-input v-model="item.key" placeholder="字段 key" class="api-output-row__key" />
                <el-input v-model="item.jsonPointer" placeholder="JSON 路径" class="api-output-row__pointer" />
                <el-select v-model="item.maskType" class="api-output-row__mask">
                  <el-option label="无脱敏" value="NONE" />
                  <el-option label="手机号后四位" value="PHONE_LAST4" />
                </el-select>
                <el-select v-model="item.formatType" clearable class="api-output-row__format">
                  <el-option label="无格式" value="" />
                  <el-option label="日期时间" value="DATE_TIME" />
                  <el-option label="金额两位" value="MONEY_2" />
                </el-select>
                <el-button type="danger" link @click="removeOutput(index)">移除</el-button>
              </div>
            </div>
          </template>
        </div>
      </div>

      <el-collapse v-model="resultSections" class="api-result-collapse">
        <el-collapse-item name="details">
          <template #title>
            <div class="api-advanced-title-wrap">
              <span>结果预览与运行细项</span>
              <span v-if="showResultDetailSummary" class="api-advanced-title-summary">{{ resultDetailSummaryText }}</span>
            </div>
          </template>
          <div class="api-advanced-intro">
            这里主要用于查看原始样例 JSON，或微调超时、返回条数和启用状态。首次配置时通常保持默认即可。
          </div>

          <div v-if="previewRows.length" class="api-preview-json-wrap">
            <div class="api-preview-panel__head">
              <span>样例返回 JSON</span>
            </div>
            <pre class="test-result-pre">{{ JSON.stringify(previewRows, null, 2) }}</pre>
          </div>
          <div v-else class="api-empty-hint" style="margin-bottom: 12px">点一次「预览返回」后，这里会显示接口原始样例，方便你核对字段结构。</div>

          <el-divider content-position="left">机器人返回样式</el-divider>
          <el-form-item label="Telegram 展现">
            <el-select :model-value="telegramReplyStyle" style="width: 100%" @update:model-value="emit('update:telegramReplyStyle', $event)">
              <el-option label="一行一项（默认）" value="LIST" />
              <el-option label="一行一项 · 中间点分隔" value="LIST_DOT" />
              <el-option label="一行一项 · 数值代码块" value="LIST_CODE" />
              <el-option label="每字段引用块" value="LIST_BLOCKQUOTE" />
              <el-option label="分块" value="SECTION" />
              <el-option label="整块等宽 label: value" value="MONO_PRE" />
              <el-option label="整块等宽 label=value" value="CODE_BLOCK" />
              <el-option label="单行键值分号连接" value="KV_SINGLE_LINE" />
              <el-option label="每行仅值（空格拼接，无 key）" value="VALUES_JOIN_SPACE" />
              <el-option label="每行仅值（| 拼接，无 key）" value="VALUES_JOIN_PIPE" />
              <el-option label="每行仅值（自定义连接符）" value="VALUES_JOIN_CUSTOM" />
              <el-option label="表格（key 作表头，整块输出）" value="TABLE_PRE" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="telegramReplyStyle === 'VALUES_JOIN_CUSTOM'" label="自定义连接符">
            <el-input
              :model-value="telegramJoinDelimiter"
              placeholder="例如：两个空格、/、·、 | "
              @update:model-value="emit('update:telegramJoinDelimiter', String($event ?? ''))"
            />
          </el-form-item>
          <el-form-item label="超时（毫秒)">
            <el-input-number :model-value="timeoutMs" :min="500" :max="30000" @update:model-value="emit('update:timeoutMs', Number($event))" />
          </el-form-item>
          <el-form-item label="最大条数">
            <el-input-number :model-value="maxRows" :min="1" :max="20" @update:model-value="emit('update:maxRows', Number($event))" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch :model-value="enabled" @update:model-value="emit('update:enabled', Boolean($event))" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
  </div>
</template>

<style scoped>
.api-builder {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.api-preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.api-step-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.api-step-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(124, 58, 237, 0.18);
  background: linear-gradient(180deg, rgba(124, 58, 237, 0.12), rgba(255, 255, 255, 0.03));
}

.api-step-card__index {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: white;
  background: linear-gradient(135deg, #7c3aed, #4f46e5);
  flex: 0 0 auto;
}

.api-step-card__title {
  font-weight: 700;
  margin-bottom: 4px;
}

.api-step-card__desc,
.api-guide-banner {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.api-guide-banner {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px dashed rgba(255, 255, 255, 0.12);
}

.api-result-collapse,
.api-advanced-collapse {
  margin-bottom: 4px;
}

.api-preset-card {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 14px;
  padding: 14px;
  text-align: left;
  color: inherit;
  cursor: pointer;
}

.api-preset-card--active {
  border-color: rgba(124, 58, 237, 0.8);
  box-shadow: 0 0 0 1px rgba(124, 58, 237, 0.45) inset;
  background: rgba(124, 58, 237, 0.12);
}

.api-preset-card__title {
  font-weight: 700;
  margin-bottom: 6px;
}

.api-preset-card__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.api-preset-card__param-icon {
  color: var(--el-color-primary);
  font-size: 14px;
}

.preset-param-tooltip {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preset-param-tooltip__title {
  font-weight: 700;
  margin-bottom: 2px;
}

.preset-param-tooltip__row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
  align-items: center;
}

.preset-param-tooltip__name {
  font-weight: 600;
}

.preset-param-tooltip__meta,
.preset-param-tooltip__sample {
  opacity: 0.9;
}

.api-preset-card__summary,
.api-preset-card__hint,
.api-empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.api-builder-form {
  margin-top: 8px;
}

.api-builder-block-toolbar {
  margin-bottom: 10px;
}

.api-line-row {
  display: grid;
  grid-template-columns: 1.1fr 140px 1.2fr auto;
  gap: 10px;
  margin-bottom: 10px;
}

.api-preview-toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  width: 100%;
}

.api-preview-panels {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 16px;
}

.api-preview-panel {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.03);
}

.api-preview-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.api-preview-panel__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.api-field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.api-field-pill {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  padding: 10px 12px;
  text-align: left;
  min-width: 170px;
  color: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.api-field-pill span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}

.api-output-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.api-output-row {
  display: grid;
  grid-template-columns: 24px 1fr 0.9fr 1.2fr 120px 120px auto;
  gap: 8px;
  align-items: center;
}

.api-output-drag {
  cursor: grab;
  color: var(--el-text-color-secondary);
}

.api-preview-json-wrap {
  margin-top: 8px;
}

@media (max-width: 980px) {
  .api-step-grid,
  .api-preview-panels,
  .api-line-row,
  .api-output-row,
  .api-preview-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>

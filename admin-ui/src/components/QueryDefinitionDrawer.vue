<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import type { Bot, Ds, Qd, TelegramReplyStyle } from '../types/dashboard'
import {
  safeParseParamSchema,
  buildTelegramReplyStylePayload,
  parseTelegramReplyStyle,
} from '../utils/dashboard'

const props = defineProps<{
  visible: boolean
  botId: number | null
  editId: number | null
  bots: Bot[]
  dsList: Ds[]
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

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
  channelScopeJson: null as string | null,
})

const positionalExampleDrafts = ref<string[]>([])
const lastPositionalParamsKey = ref('')

const qFormChannelScope = computed({
  get: () => {
    try {
      const arr = JSON.parse(qForm.value.channelScopeJson || '[]')
      return Array.isArray(arr) ? arr : []
    } catch { return [] }
  },
  set: (val: number[]) => {
    qForm.value.channelScopeJson = val.length > 0 ? JSON.stringify(val) : null
  },
})

const queryBotChannels = computed<{ id: number }[]>(() => {
  if (!props.botId) return []
  return [] // 需要从父组件传入
})

const positionalParamNames = computed(() => {
  if (qQueryMode.value === 'API') return []
  return safeParseParamSchema(qForm.value.paramSchemaJson).params
})

watch(() => props.visible, async (val) => {
  if (val) {
    if (props.editId != null && props.botId != null) {
      await loadQuery(props.editId)
    } else {
      resetForm()
    }
  }
})

async function loadQuery(id: number) {
  if (!props.botId) return
  const { data } = await api.get<Qd>(`/admin/bots/${props.botId}/queries/${id}`)
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
}

function resetForm() {
  qQueryMode.value = 'SQL'
  qForm.value = {
    datasourceId: props.dsList[0]?.id ?? 0,
    command: '',
    name: '',
    telegramMenuDescription: '',
    sqlTemplate: '',
    paramSchemaJson: '{"params":["orderNo"]}',
    timeoutMs: 5000,
    maxRows: 1,
    enabled: true,
    telegramReplyStyle: 'LIST',
    telegramJoinDelimiter: ' ',
    apiConfigJson: '',
    channelScopeJson: null,
  }
}

async function save() {
  if (!props.botId) return
  let command = qForm.value.command.trim()
  if (!command) {
    ElMessage.warning('请填写命令')
    return
  }
  if (command.startsWith('/')) {
    command = command.slice(1)
    ElMessage.info('命令不需要加 / 前缀，已自动去掉')
  }
  if (!qForm.value.datasourceId || !props.dsList.some((d) => d.id === qForm.value.datasourceId)) {
    ElMessage.warning('请选择有效的数据源')
    return
  }

  const telegramReplyStylePayload = buildTelegramReplyStylePayload(
    qForm.value.telegramReplyStyle,
    qForm.value.telegramJoinDelimiter,
  )

  const body: Record<string, unknown> = {
    datasourceId: qForm.value.datasourceId,
    command,
    name: qForm.value.name?.trim() || null,
    telegramMenuDescription: qForm.value.telegramMenuDescription?.trim() || null,
    queryMode: qQueryMode.value,
    sqlTemplate: qQueryMode.value === 'SQL' ? qForm.value.sqlTemplate : ' ',
    paramSchemaJson: qForm.value.paramSchemaJson,
    timeoutMs: qForm.value.timeoutMs,
    maxRows: qForm.value.maxRows,
    enabled: qForm.value.enabled,
    telegramReplyStyle: telegramReplyStylePayload,
    channelScopeJson: qForm.value.channelScopeJson,
  }

  if (qQueryMode.value === 'API') {
    body.apiConfigJson = qForm.value.apiConfigJson
  }

  if (props.editId != null) {
    await api.put(`/admin/bots/${props.botId}/queries/${props.editId}`, body)
    ElMessage.success('查询定义已更新')
  } else {
    await api.post(`/admin/bots/${props.botId}/queries`, body)
    ElMessage.success('查询定义已创建')
  }
  emit('update:visible', false)
  emit('saved')
}

function flushPositionalExamplesIntoParamSchemaJson() {
  if (qQueryMode.value === 'API') return
  const { params } = safeParseParamSchema(qForm.value.paramSchemaJson)
  if (params.length === 0) return
  const drafts = positionalExampleDrafts.value
  const ex = params.map((_, i) => (drafts[i] ?? '').trim())
  const o: Record<string, unknown> = { params: [...params] }
  if (ex.some((s) => s !== '')) o.examples = ex
  qForm.value.paramSchemaJson = JSON.stringify(o)
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="editId != null ? '编辑查询定义' : '新建查询定义'"
    size="600px"
    destroy-on-close
  >
    <el-form label-width="120px">
      <el-form-item label="命令" required>
        <el-input v-model="qForm.command" placeholder="例如：order、tq" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="qForm.name" placeholder="命令显示名称" />
      </el-form-item>
      <el-form-item label="数据源" required>
        <el-select v-model="qForm.datasourceId" style="width: 100%">
          <el-option v-for="ds in dsList" :key="ds.id" :label="`${ds.name} (${ds.sourceType || 'DATABASE'})`" :value="ds.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="查询模式">
        <el-radio-group v-model="qQueryMode">
          <el-radio value="SQL">SQL</el-radio>
          <el-radio value="API">API</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- SQL 模式 -->
      <template v-if="qQueryMode === 'SQL'">
        <el-form-item label="SQL 模板">
          <el-input v-model="qForm.sqlTemplate" type="textarea" :rows="6" placeholder="SELECT * FROM t WHERE id = :orderNo" />
        </el-form-item>
      </template>

      <!-- API 模式 -->
      <template v-if="qQueryMode === 'API'">
        <el-form-item label="API 配置">
          <el-input v-model="qForm.apiConfigJson" type="textarea" :rows="4" placeholder="API 配置 JSON" />
        </el-form-item>
      </template>

      <el-form-item label="参数 Schema">
        <el-input v-model="qForm.paramSchemaJson" type="textarea" :rows="2" placeholder='{"params":["orderNo"],"examples":["ORD001"]}' />
      </el-form-item>

      <el-form-item label="超时（毫秒）">
        <el-input-number v-model="qForm.timeoutMs" :min="500" />
      </el-form-item>
      <el-form-item label="最大行数">
        <el-input-number v-model="qForm.maxRows" :min="1" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="qForm.enabled" />
      </el-form-item>
      <el-form-item label="回复展现样式">
        <el-select v-model="qForm.telegramReplyStyle" style="width: 100%">
          <el-option label="一行一项（默认）" value="LIST" />
          <el-option label="一行一项 · 中间点分隔" value="LIST_DOT" />
          <el-option label="一行一项 · 数值代码块" value="LIST_CODE" />
          <el-option label="每字段引用块" value="LIST_BLOCKQUOTE" />
          <el-option label="分块" value="SECTION" />
          <el-option label="整块等宽 label: value" value="MONO_PRE" />
          <el-option label="整块等宽 label=value" value="CODE_BLOCK" />
          <el-option label="单行键值分号连接" value="KV_SINGLE_LINE" />
          <el-option label="每行仅值（空格拼接）" value="VALUES_JOIN_SPACE" />
          <el-option label="每行仅值（| 拼接）" value="VALUES_JOIN_PIPE" />
          <el-option label="每行仅值（自定义连接符）" value="VALUES_JOIN_CUSTOM" />
          <el-option label="表格" value="TABLE_PRE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="qForm.telegramReplyStyle === 'VALUES_JOIN_CUSTOM'" label="自定义连接符">
        <el-input v-model="qForm.telegramJoinDelimiter" placeholder="例如：两个空格、/、·、 | " />
      </el-form-item>
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
            :label="`渠道 #${ch.id}`"
            :value="ch.id"
          />
        </el-select>
        <span class="form-hint">留空表示该命令适用于机器人下的所有渠道。</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

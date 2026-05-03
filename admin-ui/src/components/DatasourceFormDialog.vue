<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import type { Ds } from '../types/dashboard'
import { normalizeJsonInput, prettyJsonText, parseStringMapText } from '../utils/dashboard'
import { buildMysqlJdbcUrl, parseMysqlJdbcUrl } from '../utils/mysqlJdbc'
import { findDatasourcePreset } from '../utils/apiPresets'

const props = defineProps<{
  visible: boolean
  editId: number | null
  dsList: Ds[]
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const dsSourceType = ref<'DATABASE' | 'API'>('DATABASE')
const dsNameAutoManaged = ref(true)
const dsNameUpdatingByPreset = ref(false)
const dsTestLoading = ref(false)

const dsForm = ref(emptyDatasourceForm())

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

watch(() => props.visible, async (val) => {
  if (val) {
    if (props.editId != null) {
      await loadDatasource(props.editId)
    } else {
      dsForm.value = emptyDatasourceForm()
      dsSourceType.value = 'DATABASE'
      dsNameAutoManaged.value = true
    }
  }
})

async function loadDatasource(id: number) {
  const { data } = await api.get<Ds>(`/admin/datasources/${id}`)
  dsSourceType.value = data.sourceType === 'API' ? 'API' : 'DATABASE'
  dsNameAutoManaged.value = false
  const parsed = parseMysqlJdbcUrl(data.jdbcUrl ?? '')
  dsForm.value = {
    name: data.name,
    jdbcMode: parsed ? 'simple' : 'advanced',
    mysqlHost: parsed?.host ?? '127.0.0.1',
    mysqlPort: parsed ? Number(parsed.port) || 3306 : 3306,
    mysqlDatabase: parsed?.database ?? '',
    jdbcUrl: data.jdbcUrl ?? '',
    username: data.username ?? '',
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

async function testConnection() {
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
    const body = {
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
    if (props.editId != null) (body as Record<string, unknown>).id = props.editId
    dsTestLoading.value = true
    try {
      await api.post('/admin/datasource-connection/test', body)
      ElMessage.success('API 连通成功')
    } catch { /* 由 api 拦截器提示 */ } finally {
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
  if (props.editId == null && !dsForm.value.passwordPlain.trim()) {
    ElMessage.warning('新建数据源测试时请填写密码')
    return
  }
  const body: Record<string, unknown> = { jdbcUrl, username: dsForm.value.username.trim() }
  if (dsForm.value.passwordPlain.trim()) body.passwordPlain = dsForm.value.passwordPlain
  if (props.editId != null) body.id = props.editId
  dsTestLoading.value = true
  try {
    await api.post('/admin/datasource-connection/test', body)
    ElMessage.success('连接成功')
  } catch { /* 由 api 拦截器提示 */ } finally {
    dsTestLoading.value = false
  }
}

async function save() {
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
    if (props.editId != null) {
      await api.put(`/admin/datasources/${props.editId}`, body)
      ElMessage.success('API 数据源已更新')
    } else {
      await api.post('/admin/datasources', body)
      ElMessage.success('API 数据源已创建')
    }
    emit('update:visible', false)
    emit('saved')
    return
  }
  const jdbcUrl = resolveDsJdbcUrl()
  if (jdbcUrl == null) return
  if (props.editId == null && !dsForm.value.passwordPlain.trim()) {
    ElMessage.warning('新建数据源请填写数据库密码')
    return
  }
  if (props.editId != null) {
    const body: Record<string, unknown> = {
      name: dsForm.value.name,
      jdbcUrl,
      username: dsForm.value.username,
      poolMax: dsForm.value.poolMax,
    }
    if (dsForm.value.passwordPlain.trim()) body.passwordPlain = dsForm.value.passwordPlain
    await api.put(`/admin/datasources/${props.editId}`, body)
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
  emit('update:visible', false)
  emit('saved')
}

const dsJdbcPreview = computed(() => {
  if (dsForm.value.jdbcMode !== 'simple') return ''
  try {
    return buildMysqlJdbcUrl(dsForm.value.mysqlHost, dsForm.value.mysqlPort, dsForm.value.mysqlDatabase)
  } catch { return '' }
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="editId != null ? '编辑数据源' : '新建数据源（数据库 / API）'"
    width="720px"
    destroy-on-close
  >
    <el-form label-width="120px">
      <el-form-item label="名称">
        <el-input v-model="dsForm.name" placeholder="例如：订单只读库" />
      </el-form-item>

      <el-form-item label="类型">
        <el-radio-group v-model="dsSourceType">
          <el-radio value="DATABASE">数据库</el-radio>
          <el-radio value="API">API</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 数据库表单 -->
      <template v-if="dsSourceType === 'DATABASE'">
        <el-form-item label="连接方式">
          <el-radio-group v-model="dsForm.jdbcMode">
            <el-radio value="simple">简易表单</el-radio>
            <el-radio value="advanced">自定义 JDBC</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="dsForm.jdbcMode === 'simple'">
          <el-form-item label="主机">
            <el-input v-model="dsForm.mysqlHost" placeholder="127.0.0.1" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="dsForm.mysqlPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="库名">
            <el-input v-model="dsForm.mysqlDatabase" placeholder="mydb" />
          </el-form-item>
          <el-form-item v-if="dsJdbcPreview" label="JDBC URL">
            <code style="font-size: 12px; color: #909399">{{ dsJdbcPreview }}</code>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="JDBC URL">
            <el-input v-model="dsForm.jdbcUrl" placeholder="jdbc:mysql://host:3306/db?..." />
          </el-form-item>
        </template>
        <el-form-item label="用户名">
          <el-input v-model="dsForm.username" placeholder="建议只读账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="dsForm.passwordPlain" type="password" show-password :placeholder="editId != null ? '留空则沿用旧密码' : '数据库密码'" />
        </el-form-item>
        <el-form-item label="连接池上限">
          <el-input-number v-model="dsForm.poolMax" :min="1" :max="100" />
        </el-form-item>
      </template>

      <!-- API 表单 -->
      <template v-else>
        <el-form-item label="API 基础地址" required>
          <el-input v-model="dsForm.apiBaseUrl" placeholder="https://api.example.com" />
        </el-form-item>
        <el-form-item label="鉴权方式">
          <el-select v-model="dsForm.authType" style="width: 100%">
            <el-option label="无鉴权" value="NONE" />
            <el-option label="Bearer Token" value="BEARER_TOKEN" />
            <el-option label="Basic" value="BASIC" />
            <el-option label="API Key (Header)" value="API_KEY_HEADER" />
            <el-option label="API Key (Query)" value="API_KEY_QUERY" />
          </el-select>
        </el-form-item>
        <template v-if="dsForm.authType === 'BASIC'">
          <el-form-item label="用户名">
            <el-input v-model="dsForm.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="dsForm.passwordPlain" type="password" show-password />
          </el-form-item>
        </template>
        <template v-if="dsForm.authType === 'BEARER_TOKEN' || dsForm.authType === 'API_KEY_HEADER' || dsForm.authType === 'API_KEY_QUERY'">
          <el-form-item label="Token / Key">
            <el-input v-model="dsForm.passwordPlain" type="password" show-password :placeholder="editId != null ? '留空则沿用旧值' : ''" />
          </el-form-item>
        </template>
        <el-form-item label="超时（毫秒）">
          <el-input-number v-model="dsForm.requestTimeoutMs" :min="1000" :max="60000" />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button :loading="dsTestLoading" @click="testConnection">测试连接</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

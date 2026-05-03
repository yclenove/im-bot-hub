<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import type { Bot, Qd } from '../types/dashboard'
import { platformLabel, platformTagType } from '../utils/platform'

const props = defineProps<{
  bots: Bot[]
  botId: number | null
}>()

const emit = defineEmits<{
  'update:botId': [val: number | null]
  'open-new': []
  'open-edit': [row: Qd]
  'open-test': [row: Qd]
  'open-optimize': [row: Qd, cmd: string]
  refresh: []
}>()

const queries = ref<Qd[]>([])
const loading = ref(false)

async function loadQueries() {
  if (!props.botId) {
    queries.value = []
    return
  }
  loading.value = true
  try {
    const { data } = await api.get<Qd[]>(`/admin/bots/${props.botId}/queries`)
    queries.value = data
  } catch {
    queries.value = []
  } finally {
    loading.value = false
  }
}

async function deleteQuery(row: Qd) {
  try {
    await ElMessageBox.confirm(
      `确定删除查询「${row.command}」？`,
      '删除确认',
      { type: 'warning' }
    )
    await api.delete(`/admin/bots/${props.botId}/queries/${row.id}`)
    ElMessage.success('查询已删除')
    await loadQueries()
    emit('refresh')
  } catch (e: unknown) {
    if (e !== 'cancel') {
      const err = e as { response?: { data?: { message?: string } } }
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}

async function exportQueries() {
  try {
    const { data } = await api.get('/admin/export/queries')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'queries-export.json'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('查询定义已导出')
  } catch {
    ElMessage.error('导出失败')
  }
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
      if (!Array.isArray(items)) {
        ElMessage.error('JSON 格式错误：应为数组')
        return
      }
      const { data } = await api.post('/admin/export/import/queries', items)
      ElMessage.success(`导入完成：新增 ${data.created} 条，跳过 ${data.skipped} 条`)
      await loadQueries()
      emit('refresh')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      ElMessage.error(e?.response?.data?.message || '导入失败')
    }
  }
  input.click()
}

function openNew() {
  emit('open-new')
}

function openEdit(row: Qd) {
  emit('open-edit', row)
}

function openTest(row: Qd) {
  emit('open-test', row)
}

function isVisualQueryForOptimize(row: Qd) {
  return row.queryMode === 'VISUAL' && !!(row.visualConfigJson && row.visualConfigJson.trim())
}

function handleOptimizeCommand(row: Qd, cmd: string) {
  emit('open-optimize', row, cmd)
}

watch(() => props.botId, () => {
  loadQueries()
})

defineExpose({ loadQueries, queries })
</script>

<template>
  <div class="query-tab">
    <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
      <span>机器人</span>
      <el-select
        :model-value="botId"
        placeholder="选择机器人"
        style="width: 240px"
        @update:model-value="emit('update:botId', $event)"
      >
        <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
      </el-select>
      <el-button @click="loadQueries">刷新</el-button>
      <el-button type="primary" :disabled="!botId" @click="openNew">新建查询</el-button>
      <el-button @click="exportQueries">导出</el-button>
      <el-button @click="triggerImportQueries">导入</el-button>
    </div>

    <el-table :data="queries" v-loading="loading" style="width: 100%; margin-top: 12px">
      <el-table-column prop="id" label="查询 ID" width="80" />
      <el-table-column label="名称" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.name?.trim() || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="command" label="命令" width="120">
        <template #default="{ row }">
          <code>/{{ row.command }}</code>
        </template>
      </el-table-column>
      <el-table-column label="模式" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.queryMode || 'SQL' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="telegramReplyStyle" label="展现" width="120" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openTest(row)">测试</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="isVisualQueryForOptimize(row)"
            size="small"
            @click="handleOptimizeCommand(row, 'index')"
          >
            优化
          </el-button>
          <el-button size="small" type="danger" @click="deleteQuery(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.query-tab {
  padding: 0;
}
</style>

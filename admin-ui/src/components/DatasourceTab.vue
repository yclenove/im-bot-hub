<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import type { Ds } from '../types/dashboard'

const emit = defineEmits<{
  'open-new': []
  'open-edit': [row: Ds]
}>()

const dsList = ref<Ds[]>([])
const loading = ref(false)

async function loadDs() {
  loading.value = true
  try {
    const { data } = await api.get<Ds[]>('/admin/datasources')
    dsList.value = data
  } catch {
    dsList.value = []
  } finally {
    loading.value = false
  }
}

async function deleteDs(row: Ds) {
  try {
    await ElMessageBox.confirm(
      '确定删除该数据源？若仍被查询定义引用，数据库可能拒绝删除。',
      '删除确认',
      { type: 'warning' }
    )
    await api.delete(`/admin/datasources/${row.id}`)
    ElMessage.success('数据源已删除')
    await loadDs()
  } catch (e: unknown) {
    if (e !== 'cancel') {
      const err = e as { response?: { data?: { message?: string } } }
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}

function openNew() {
  emit('open-new')
}

function openEdit(row: Ds) {
  emit('open-edit', row)
}

onMounted(loadDs)

defineExpose({ loadDs, dsList })
</script>

<template>
  <div class="datasource-tab">
    <el-button type="primary" @click="openNew">新建数据源</el-button>
    <el-table :data="dsList" v-loading="loading" style="width: 100%; margin-top: 12px">
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
          <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteDs(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.datasource-tab {
  padding: 0;
}
</style>

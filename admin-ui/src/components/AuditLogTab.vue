<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'

const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

async function load() {
  try {
    const { data } = await api.get('/admin/audit-logs', { params: { page: page.value, size: size.value } })
    rows.value = data.records ?? data
    total.value = data.total ?? rows.value.length
  } catch { rows.value = [] }
}

onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div>
    <el-button @click="load" size="small" style="margin-bottom: 12px">刷新</el-button>
    <el-pagination
      v-model:current-page="page" v-model:page-size="size"
      :total="total" :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @size-change="load" @current-change="load"
    />
    <el-table :data="rows" style="width: 100%; margin-top: 12px">
      <el-table-column prop="createdAt" label="时间" width="200" />
      <el-table-column prop="actor" label="操作者" width="120" />
      <el-table-column prop="action" label="动作" width="140" />
      <el-table-column prop="resourceType" label="资源类型" width="140" />
      <el-table-column prop="resourceId" label="资源 ID" width="120" />
      <el-table-column prop="detail" label="详情" />
    </el-table>
    <el-empty v-if="!rows.length" description="暂无审计日志" :image-size="48" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'

const props = defineProps<{ bots: Array<{ id: number; name: string }> }>()

const botId = ref<number | null>(null)
const rows = ref<any[]>([])
const tuid = ref('')
const batchIds = ref('')

async function load() {
  if (!botId.value) return
  try {
    const { data } = await api.get(`/admin/bots/${botId.value}/allowlist`)
    rows.value = data
  } catch { rows.value = [] }
}

async function add(tuidVal: string) {
  if (!botId.value) return
  const s = tuidVal.trim()
  if (!s) { ElMessage.warning('请输入用户 ID'); return }
  await api.post(`/admin/bots/${botId.value}/allowlist`, { externalUserId: s, enabled: true })
  ElMessage.success('已添加到白名单')
  tuid.value = ''
  await load()
}

async function batchAdd() {
  if (!botId.value) return
  if (!batchIds.value.trim()) { ElMessage.warning('请输入用户 ID'); return }
  try {
    const { data } = await api.post(`/admin/bots/${botId.value}/allowlist/batch`, {
      externalUserIds: batchIds.value, enabled: true
    })
    ElMessage.success(`批量添加 ${data.created} 条`)
    batchIds.value = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量添加失败')
  }
}

async function remove(id: number) {
  if (!botId.value) return
  await api.delete(`/admin/bots/${botId.value}/allowlist/${id}`)
  await load()
}

watch(botId, load)
</script>

<template>
  <div>
    <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
      <span>机器人</span>
      <el-select v-model="botId" placeholder="选择机器人" style="width: 240px">
        <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
      </el-select>
      <el-button @click="load">加载</el-button>
    </div>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header><span style="font-weight: 600">添加白名单</span></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input placeholder="用户 ID（单个）" style="max-width: 200px" v-model="tuid" />
        <el-button type="primary" @click="add(tuid)">添加</el-button>
        <el-divider direction="vertical" />
        <el-input v-model="batchIds" type="textarea" :rows="2" placeholder="批量：多个 ID 用逗号或换行分隔" style="width: 280px" />
        <el-button type="success" @click="batchAdd">批量添加</el-button>
      </div>
    </el-card>

    <el-table :data="rows" style="width: 100%; margin-top: 12px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="用户 ID" min-width="140">
        <template #default="{ row }">{{ row.externalUserId || row.telegramUserId || '-' }}</template>
      </el-table-column>
      <el-table-column prop="platform" label="平台" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.platform" :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
          <span v-else style="color: #999">全部</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length && botId" description="暂无白名单" :image-size="48" />
    <p style="color: #666; margin-top: 8px">若白名单为空，则所有用户都可使用查询命令。</p>
  </div>
</template>

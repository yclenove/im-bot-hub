<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'
import AddChannelDialog from './AddChannelDialog.vue'
import ChannelDetailDialog from './ChannelDetailDialog.vue'

interface ChannelRow {
  id: number; botId: number; platform: string; enabled: boolean
  webhookUrl: string; credentialsSummary: string
}

const props = defineProps<{ bots: Array<{ id: number; name: string }> }>()

const rows = ref<ChannelRow[]>([])
const filterPlatform = ref('')
const filterBotId = ref<number | null>(null)
const addOpen = ref(false)
const detailOpen = ref(false)
const detailChannel = ref<ChannelRow | null>(null)

async function load() {
  try {
    const params: Record<string, any> = {}
    if (filterPlatform.value) params.platform = filterPlatform.value
    if (filterBotId.value) params.botId = filterBotId.value
    const { data } = await api.get('/admin/channels', { params })
    rows.value = data
  } catch { rows.value = [] }
}

async function toggle(row: ChannelRow) {
  await api.put(`/admin/channels/${row.id}/toggle`)
  ElMessage.success(row.enabled ? '已禁用' : '已启用')
  await load()
}

async function remove(row: ChannelRow) {
  try {
    await ElMessageBox.confirm('确定删除该渠道？', '确认删除', { type: 'warning' })
  } catch { return }
  await api.delete(`/admin/channels/${row.id}`)
  await load()
  ElMessage.success('已删除')
}

function openDetail(row: ChannelRow) {
  detailChannel.value = row
  detailOpen.value = true
}

watch([filterPlatform, filterBotId], load)
onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div>
    <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 12px">
      <el-select v-model="filterPlatform" placeholder="全部平台" clearable style="width: 180px">
        <el-option label="Telegram" value="TELEGRAM" />
        <el-option label="飞书" value="LARK" />
        <el-option label="钉钉" value="DINGTALK" />
        <el-option label="企业微信" value="WEWORK" />
        <el-option label="Slack" value="SLACK" />
        <el-option label="Discord" value="DISCORD" />
      </el-select>
      <el-select v-model="filterBotId" placeholder="全部机器人" clearable style="width: 220px">
        <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" @click="addOpen = true">新建渠道</el-button>
    </div>

    <el-table v-if="rows.length" :data="rows" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="botId" label="机器人 ID" width="100" />
      <el-table-column prop="platform" label="平台" width="120">
        <template #default="{ row }">
          <el-tag :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="credentialsSummary" label="凭证摘要" width="140" show-overflow-tooltip />
      <el-table-column prop="webhookUrl" label="Webhook URL" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span style="font-size: 12px; color: #666">{{ row.webhookUrl }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button>
          <el-button size="small" @click="toggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="" :image-size="64">
      <template #description>
        <p style="font-size: 15px; color: #606266; margin-bottom: 8px">还没有渠道</p>
        <p style="font-size: 13px; color: #909399">渠道是平台接入单元，点击上方「新建渠道」开始</p>
      </template>
    </el-empty>

    <AddChannelDialog v-model:visible="addOpen" :bots="bots" @created="load" />
    <ChannelDetailDialog v-model:visible="detailOpen" :channel="detailChannel" />
  </div>
</template>

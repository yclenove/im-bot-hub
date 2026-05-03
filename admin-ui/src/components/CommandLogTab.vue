<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'

const props = defineProps<{ bots: Array<{ id: number; name: string }> }>()

const botId = ref<number | null>(null)
const platform = ref('')
const command = ref('')
const timeRange = ref<[string, string] | null>(null)
const errorKind = ref('')
const success = ref<'all' | 'yes' | 'no'>('all')
const userId = ref('')
const chatId = ref('')
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const KIND_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'PARAM_MISSING', value: 'PARAM_MISSING' },
  { label: 'NO_RESULT', value: 'NO_RESULT' },
  { label: 'QUERY_ERROR', value: 'QUERY_ERROR' },
  { label: 'PERMISSION_DENIED', value: 'PERMISSION_DENIED' },
  { label: 'UNKNOWN_COMMAND', value: 'UNKNOWN_COMMAND' },
]

async function load() {
  const from = timeRange.value?.[0]?.trim() || undefined
  const to = timeRange.value?.[1]?.trim() || undefined
  try {
    const { data } = await api.get('/admin/command-logs', {
      params: {
        page: page.value, size: size.value,
        botId: botId.value ?? undefined,
        platform: platform.value || undefined,
        command: command.value.trim() || undefined,
        from, to,
        errorKind: errorKind.value || undefined,
        success: success.value === 'all' ? undefined : success.value === 'yes',
        externalUserId: userId.value?.trim() || undefined,
        externalChatId: chatId.value?.trim() || undefined,
      },
    })
    rows.value = data.records
    total.value = data.total
  } catch { rows.value = []; total.value = 0 }
}

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('无数据可导出'); return }
  const header = '时间,机器人,平台,命令,用户ID,会话ID,查询ID,成功,类型,耗时ms,详情'
  const csvRows = rows.value.map(r =>
    [r.createdAt, r.botId, r.platform, r.command, r.externalUserId, r.chatId, r.queryDefinitionId, r.success ? '是' : '否', r.errorKind, r.durationMs, r.detail]
      .map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')
  )
  const blob = new Blob(['﻿' + header + '\n' + csvRows.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = 'command-logs.csv'; a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div>
    <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 12px">
      <el-select v-model="botId" placeholder="全部机器人" clearable style="width: 220px">
        <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
      </el-select>
      <el-select v-model="platform" placeholder="全部平台" clearable style="width: 140px">
        <el-option label="Telegram" value="TELEGRAM" />
        <el-option label="飞书" value="LARK" />
        <el-option label="钉钉" value="DINGTALK" />
        <el-option label="企业微信" value="WEWORK" />
        <el-option label="Slack" value="SLACK" />
        <el-option label="Discord" value="DISCORD" />
      </el-select>
      <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
        start-placeholder="开始时间" end-placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss" style="width: min(100%, 380px)" clearable />
      <el-input v-model="command" placeholder="命令关键字" clearable style="max-width: 160px" />
      <el-select v-model="errorKind" clearable placeholder="结果类型" style="width: 200px">
        <el-option v-for="o in KIND_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="success" placeholder="是否成功" style="width: 120px">
        <el-option label="不限" value="all" /><el-option label="成功" value="yes" /><el-option label="失败" value="no" />
      </el-select>
      <el-input v-model="userId" placeholder="用户 ID" clearable style="max-width: 150px" />
      <el-input v-model="chatId" placeholder="会话 chat_id" clearable style="max-width: 170px" />
      <el-button @click="load">刷新</el-button>
      <el-button @click="exportCsv">导出当前页 CSV</el-button>
    </div>

    <el-pagination v-model:current-page="page" v-model:page-size="size"
      :total="total" :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @size-change="load" @current-change="load" />

    <el-table :data="rows" style="width: 100%; margin-top: 12px">
      <el-table-column prop="createdAt" label="时间" width="178" />
      <el-table-column prop="botId" label="机器人" width="88" />
      <el-table-column prop="platform" label="平台" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.platform" :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="command" label="命令" width="100" />
      <el-table-column prop="externalUserId" label="用户 ID" width="120" />
      <el-table-column prop="externalChatId" label="会话 ID" width="120" />
      <el-table-column prop="queryDefinitionId" label="查询 ID" width="96" />
      <el-table-column label="成功" width="72">
        <template #default="{ row }">{{ row.success ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="errorKind" label="类型" width="120" />
      <el-table-column prop="durationMs" label="耗时 ms" width="96" />
      <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />
    </el-table>
    <el-empty v-if="!rows.length" description="暂无命令日志" :image-size="48" />
  </div>
</template>

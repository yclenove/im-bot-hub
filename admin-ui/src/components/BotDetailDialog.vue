<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'

const props = defineProps<{ visible: boolean; botId: number | null }>()
const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

const loading = ref(false)
const detail = ref<any>(null)

watch(() => props.visible, async (val) => {
  if (val && props.botId) {
    loading.value = true
    detail.value = null
    try {
      const { data } = await api.get(`/admin/bots/${props.botId}/detail`)
      detail.value = data
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '加载详情失败')
      emit('update:visible', false)
    } finally {
      loading.value = false
    }
  }
})

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog :model-value="visible" @update:model-value="emit('update:visible', $event)" title="机器人详情" width="720px" destroy-on-close>
    <el-skeleton :loading="loading" animated>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.enabled ? 'success' : 'info'">{{ detail.enabled ? '启用' : '禁用' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">关联渠道（{{ detail.channels?.length || 0 }}）</el-divider>
        <el-table v-if="detail.channels?.length" :data="detail.channels" size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="platform" label="平台" width="120">
            <template #default="{ row }">
              <el-tag :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无渠道" :image-size="48" />

        <el-divider content-position="left">查询定义（{{ detail.queries?.length || 0 }}）</el-divider>
        <el-table v-if="detail.queries?.length" :data="detail.queries" size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="command" label="命令" width="120">
            <template #default="{ row }"><code>/{{ row.command }}</code></template>
          </el-table-column>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="queryMode" label="模式" width="100" />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无查询定义" :image-size="48" />

        <el-divider content-position="left">最近命令（{{ detail.recentLogs?.length || 0 }}）</el-divider>
        <el-table v-if="detail.recentLogs?.length" :data="detail.recentLogs" size="small" style="width: 100%">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="platform" label="平台" width="100">
            <template #default="{ row }">
              <el-tag :type="platformTagType(row.platform)" size="small">{{ platformLabel(row.platform) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="command" label="命令" width="120" />
          <el-table-column prop="success" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时" width="80">
            <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + 'ms' : '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无命令日志" :image-size="48" />
      </template>
    </el-skeleton>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface Approval {
  id: number
  rule_name: string
  resource_type: string
  resource_id: number
  action_type: string
  requester_id: number
  status: string
  comment: string
  created_at: string
}

const approvals = ref<Approval[]>([])
const history = ref<Approval[]>([])
const loading = ref(false)
const activeTab = ref('pending')

async function loadApprovals() {
  loading.value = true
  try {
    const [pendingRes, historyRes] = await Promise.all([
      api.get<Approval[]>('/admin/v4/approvals/pending', { params: { approverId: 1 } }),
      api.get<Approval[]>('/admin/v4/approvals/history', { params: { limit: 50 } }),
    ])
    approvals.value = pendingRes.data
    history.value = historyRes.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载审批列表失败')
  } finally {
    loading.value = false
  }
}

async function approve(id: number) {
  try {
    await api.post(`/admin/v4/approvals/${id}/approve`, null, { params: { approverId: 1, comment: '同意' } })
    ElMessage.success('已审批通过')
    await loadApprovals()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '审批失败')
  }
}

async function reject(id: number) {
  try {
    await api.post(`/admin/v4/approvals/${id}/reject`, null, { params: { approverId: 1, comment: '拒绝' } })
    ElMessage.success('已拒绝')
    await loadApprovals()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '拒绝失败')
  }
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
  }
  return map[status] || status
}

onMounted(loadApprovals)
</script>

<template>
  <div class="approval-manager">
    <h4>审批管理</h4>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="approvals" v-loading="loading" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="rule_name" label="规则" width="150" />
          <el-table-column prop="resource_type" label="资源类型" width="100" />
          <el-table-column prop="action_type" label="操作" width="100" />
          <el-table-column prop="created_at" label="申请时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.created_at).toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="approve(row.id)">通过</el-button>
              <el-button size="small" type="danger" @click="reject(row.id)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="审批历史" name="history">
        <el-table :data="history" v-loading="loading" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="rule_name" label="规则" width="150" />
          <el-table-column prop="resource_type" label="资源类型" width="100" />
          <el-table-column prop="action_type" label="操作" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="审批意见" min-width="150" show-overflow-tooltip />
          <el-table-column prop="created_at" label="时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.created_at).toLocaleString() }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.approval-manager {
  padding: 16px;
}

.approval-manager h4 {
  margin: 0 0 16px;
}
</style>

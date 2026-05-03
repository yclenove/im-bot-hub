<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface ClusterNode {
  node_id: string
  status: string
  last_heartbeat: string
}

interface ClusterStatus {
  totalNodes: number
  activeNodes: number
  failedNodes: number
  nodes: ClusterNode[]
}

const status = ref<ClusterStatus | null>(null)
const loading = ref(false)

async function loadStatus() {
  loading.value = true
  try {
    const { data } = await api.get<ClusterStatus>('/admin/v4/cluster/status')
    status.value = data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载集群状态失败')
  } finally {
    loading.value = false
  }
}

function getNodeStatusType(nodeStatus: string): string {
  return nodeStatus === 'ACTIVE' ? 'success' : 'danger'
}

function getNodeStatusLabel(nodeStatus: string): string {
  return nodeStatus === 'ACTIVE' ? '在线' : '离线'
}

onMounted(loadStatus)
</script>

<template>
  <div class="cluster-status">
    <div class="status-header">
      <h4>集群状态</h4>
      <el-button @click="loadStatus" :loading="loading">刷新</el-button>
    </div>

    <template v-if="status">
      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ status.totalNodes }}</div>
            <div class="stat-label">总节点数</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card success">
            <div class="stat-value">{{ status.activeNodes }}</div>
            <div class="stat-label">在线节点</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card danger">
            <div class="stat-value">{{ status.failedNodes }}</div>
            <div class="stat-label">离线节点</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 节点列表 -->
      <el-table :data="status.nodes" size="small">
        <el-table-column prop="node_id" label="节点 ID" min-width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getNodeStatusType(row.status)" size="small">
              {{ getNodeStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_heartbeat" label="最后心跳" width="200">
          <template #default="{ row }">
            {{ new Date(row.last_heartbeat).toLocaleString() }}
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<style scoped>
.cluster-status {
  padding: 16px;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.status-header h4 {
  margin: 0;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
}

.stat-card.success .stat-value {
  color: #67c23a;
}

.stat-card.danger .stat-value {
  color: #f56c6c;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface Anomaly {
  id: number
  detection_id: number
  metric_name: string
  anomaly_type: string
  detected_value: number
  baseline_value: number
  zscore: number
  severity: string
  root_cause: string
  resolved: boolean
  created_at: string
}

const anomalies = ref<Anomaly[]>([])
const loading = ref(false)
const detecting = ref(false)

async function loadAnomalies() {
  loading.value = true
  try {
    const { data } = await api.get<Anomaly[]>('/admin/ai/anomalies', { params: { limit: 50 } })
    anomalies.value = data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载异常记录失败')
  } finally {
    loading.value = false
  }
}

async function triggerDetection() {
  detecting.value = true
  try {
    await api.post('/admin/ai/anomalies/detect')
    ElMessage.success('异常检测已触发')
    await loadAnomalies()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '触发异常检测失败')
  } finally {
    detecting.value = false
  }
}

function getSeverityType(severity: string): string {
  const map: Record<string, string> = {
    CRITICAL: 'danger',
    HIGH: 'warning',
    MEDIUM: 'info',
    LOW: 'success',
  }
  return map[severity] || 'info'
}

function getSeverityLabel(severity: string): string {
  const map: Record<string, string> = {
    CRITICAL: '严重',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
  }
  return map[severity] || severity
}

function getAnomalyTypeLabel(type: string): string {
  const map: Record<string, string> = {
    SPIKE: '突增',
    DROP: '骤降',
    SHIFT: '偏移',
    CYCLE: '周期异常',
  }
  return map[type] || type
}

function getMetricLabel(metric: string): string {
  const map: Record<string, string> = {
    success_rate: '成功率',
    avg_response_time: '平均响应时间',
    request_count: '请求量',
  }
  return map[metric] || metric
}

onMounted(loadAnomalies)
</script>

<template>
  <div class="anomaly-dashboard">
    <div class="dashboard-header">
      <h4>异常检测</h4>
      <el-button type="primary" :loading="detecting" @click="triggerDetection">手动检测</el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ anomalies.length }}</div>
          <div class="stat-label">异常总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card critical">
          <div class="stat-value">{{ anomalies.filter((a) => a.severity === 'CRITICAL').length }}</div>
          <div class="stat-label">严重异常</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card warning">
          <div class="stat-value">{{ anomalies.filter((a) => a.severity === 'HIGH').length }}</div>
          <div class="stat-label">高危异常</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card success">
          <div class="stat-value">{{ anomalies.filter((a) => a.resolved).length }}</div>
          <div class="stat-label">已解决</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 异常列表 -->
    <el-table :data="anomalies" v-loading="loading" size="small" stripe>
      <el-table-column prop="created_at" label="时间" width="180">
        <template #default="{ row }">
          {{ new Date(row.created_at).toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column prop="metric_name" label="指标" width="150">
        <template #default="{ row }">
          {{ getMetricLabel(row.metric_name) }}
        </template>
      </el-table-column>
      <el-table-column prop="anomaly_type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ getAnomalyTypeLabel(row.anomaly_type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detected_value" label="检测值" width="120" align="right">
        <template #default="{ row }">
          {{ row.detected_value.toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column prop="baseline_value" label="基线值" width="120" align="right">
        <template #default="{ row }">
          {{ row.baseline_value.toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column prop="zscore" label="Z-Score" width="100" align="right">
        <template #default="{ row }">
          <span :class="Math.abs(row.zscore) > 3 ? 'text-danger' : ''">
            {{ row.zscore.toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="严重程度" width="100">
        <template #default="{ row }">
          <el-tag :type="getSeverityType(row.severity)" size="small">
            {{ getSeverityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="root_cause" label="根因" min-width="200" show-overflow-tooltip />
      <el-table-column prop="resolved" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.resolved ? 'success' : 'info'" size="small">
            {{ row.resolved ? '已解决' : '未解决' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.anomaly-dashboard {
  padding: 16px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.dashboard-header h4 {
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
  color: #303133;
}

.stat-card.critical .stat-value {
  color: #f56c6c;
}

.stat-card.warning .stat-value {
  color: #e6a23c;
}

.stat-card.success .stat-value {
  color: #67c23a;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}
</style>

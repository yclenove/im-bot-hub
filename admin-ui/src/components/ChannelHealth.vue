<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface HealthStatus {
  status: string
  message: string
  responseTimeMs: number
}

const healthMap = ref<Record<number, HealthStatus>>({})
const loading = ref(false)
const checking = ref(false)

async function loadHealth() {
  loading.value = true
  try {
    const { data } = await api.get<Record<number, HealthStatus>>('/admin/channel-health')
    healthMap.value = data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载健康状态失败')
  } finally {
    loading.value = false
  }
}

async function triggerCheck() {
  checking.value = true
  try {
    await api.post('/admin/channel-health/check')
    ElMessage.success('健康检查已触发')
    await loadHealth()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '检查失败')
  } finally {
    checking.value = false
  }
}

function getStatusType(status: string): string {
  switch (status) {
    case 'HEALTHY': return 'success'
    case 'DEGRADED': return 'warning'
    case 'UNHEALTHY': return 'danger'
    default: return 'info'
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'HEALTHY': return '健康'
    case 'DEGRADED': return '降级'
    case 'UNHEALTHY': return '异常'
    default: return '未知'
  }
}

function getStatusIcon(status: string): string {
  switch (status) {
    case 'HEALTHY': return 'CircleCheck'
    case 'DEGRADED': return 'Warning'
    case 'UNHEALTHY': return 'CircleClose'
    default: return 'QuestionFilled'
  }
}

onMounted(loadHealth)
</script>

<template>
  <div class="channel-health">
    <div class="health-header">
      <h3>渠道健康状态</h3>
      <el-button type="primary" :loading="checking" @click="triggerCheck">手动检查</el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="(health, channelId) in healthMap" :key="channelId" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="health-card" shadow="hover">
          <div class="health-status">
            <el-icon :size="32" :color="getStatusType(health.status) === 'success' ? '#67c23a' : '#f56c6c'">
              <component :is="getStatusIcon(health.status)" />
            </el-icon>
            <div class="health-info">
              <div class="channel-id">渠道 #{{ channelId }}</div>
              <el-tag :type="getStatusType(health.status)" size="small">
                {{ getStatusLabel(health.status) }}
              </el-tag>
            </div>
          </div>
          <div class="health-detail">
            <div class="detail-item">
              <span class="label">响应时间:</span>
              <span class="value">{{ health.responseTimeMs }}ms</span>
            </div>
            <div class="detail-item" v-if="health.message">
              <span class="label">消息:</span>
              <span class="value">{{ health.message }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && Object.keys(healthMap).length === 0" description="暂无健康数据" />
  </div>
</template>

<style scoped>
.channel-health {
  padding: 16px;
}

.health-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.health-header h3 {
  margin: 0;
}

.health-card {
  margin-bottom: 16px;
}

.health-status {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.health-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.channel-id {
  font-weight: 600;
}

.health-detail {
  font-size: 13px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.detail-item .label {
  color: #999;
}

.detail-item .value {
  font-weight: 500;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface CacheStats {
  l1Size: number
  l2Size: number
}

interface OptimizationHint {
  type: string
  severity: string
  title: string
  description: string
  suggestion: string
}

interface ConnectionPool {
  active: number
  idle: number
  total: number
  waiting: number
}

const cacheStats = ref<CacheStats | null>(null)
const hints = ref<OptimizationHint[]>([])
const poolStats = ref<ConnectionPool | null>(null)
const loading = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const [cacheRes, hintsRes, poolRes] = await Promise.all([
      api.get<CacheStats>('/admin/v4/performance/cache'),
      api.get<OptimizationHint[]>('/admin/v4/performance/hints'),
      api.get<ConnectionPool>('/admin/v4/performance/connection-pool'),
    ])
    cacheStats.value = cacheRes.data
    hints.value = hintsRes.data
    poolStats.value = poolRes.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载性能数据失败')
  } finally {
    loading.value = false
  }
}

function getSeverityType(severity: string): string {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info',
  }
  return map[severity] || 'info'
}

function getTypeLabel(type: string): string {
  const map: Record<string, string> = {
    SLOW_QUERY: '慢查询',
    HIGH_FREQUENCY: '高频查询',
    INDEX: '索引建议',
  }
  return map[type] || type
}

onMounted(loadStats)
</script>

<template>
  <div class="performance-monitor">
    <div class="monitor-header">
      <h4>性能监控</h4>
      <el-button @click="loadStats" :loading="loading">刷新</el-button>
    </div>

    <!-- 缓存统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>L1 缓存（本地）</template>
          <div class="stat-value">{{ cacheStats?.l1Size || 0 }}</div>
          <div class="stat-label">条目数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>L2 缓存（查询）</template>
          <div class="stat-value">{{ cacheStats?.l2Size || 0 }}</div>
          <div class="stat-label">条目数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>连接池</template>
          <div class="pool-stats">
            <div>活跃: {{ poolStats?.active || 0 }}</div>
            <div>空闲: {{ poolStats?.idle || 0 }}</div>
            <div>等待: {{ poolStats?.waiting || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 优化建议 -->
    <el-card shadow="hover" class="hints-card">
      <template #header>
        <span>优化建议</span>
      </template>
      <el-empty v-if="hints.length === 0" description="暂无优化建议" />
      <div v-for="(hint, idx) in hints" :key="idx" class="hint-item">
        <div class="hint-header">
          <el-tag :type="getSeverityType(hint.severity)" size="small">
            {{ hint.severity }}
          </el-tag>
          <el-tag size="small" type="info">{{ getTypeLabel(hint.type) }}</el-tag>
          <span class="hint-title">{{ hint.title }}</span>
        </div>
        <div class="hint-description">{{ hint.description }}</div>
        <div class="hint-suggestion">建议: {{ hint.suggestion }}</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.performance-monitor {
  padding: 16px;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.monitor-header h4 {
  margin: 0;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
  text-align: center;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  text-align: center;
  margin-top: 8px;
}

.pool-stats {
  font-size: 14px;
  line-height: 2;
}

.hints-card {
  margin-top: 16px;
}

.hint-item {
  padding: 16px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.hint-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.hint-title {
  font-weight: 600;
  flex: 1;
}

.hint-description {
  color: #606266;
  margin-bottom: 8px;
}

.hint-suggestion {
  color: #909399;
  font-size: 13px;
}
</style>

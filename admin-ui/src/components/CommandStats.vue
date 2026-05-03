<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface CommandStat {
  command: string
  total_count: number
  success_count: number
  fail_count: number
  avg_duration_ms: number
  unique_users: number
}

interface PlatformStat {
  platform: string
  total_count: number
  success_count: number
  fail_count: number
  avg_duration_ms: number
}

const props = defineProps<{ botId: number }>()

const topCommands = ref<CommandStat[]>([])
const platformStats = ref<PlatformStat[]>([])
const loading = ref(false)

async function loadStats() {
  if (!props.botId) return
  loading.value = true
  try {
    const [topRes, platRes] = await Promise.all([
      api.get<CommandStat[]>('/admin/stats/top-commands', { params: { botId: props.botId, limit: 10 } }),
      api.get<PlatformStat[]>('/admin/stats/platforms', { params: { botId: props.botId } })
    ])
    topCommands.value = topRes.data
    platformStats.value = platRes.data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载统计失败')
  } finally {
    loading.value = false
  }
}

function getPlatformLabel(platform: string): string {
  const map: Record<string, string> = {
    TELEGRAM: 'Telegram',
    LARK: '飞书',
    SLACK: 'Slack',
    DISCORD: 'Discord',
    DINGTALK: '钉钉',
    WEWORK: '企业微信'
  }
  return map[platform] || platform
}

function getPlatformType(platform: string): string {
  const map: Record<string, string> = {
    TELEGRAM: 'primary',
    LARK: 'success',
    SLACK: 'warning',
    DISCORD: 'danger',
    DINGTALK: 'info',
    WEWORK: 'info'
  }
  return map[platform] || 'info'
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatNumber(num: number): string {
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`
  return num.toString()
}

onMounted(loadStats)
</script>

<template>
  <div class="command-stats">
    <el-row :gutter="16">
      <!-- 热门命令排行 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span>热门命令排行（近30天）</span>
          </template>
          <el-table :data="topCommands" size="small" v-loading="loading">
            <el-table-column prop="command" label="命令" width="120">
              <template #default="{ row }">
                <code>/{{ row.command }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="total_count" label="总调用" width="80" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.total_count) }}
              </template>
            </el-table-column>
            <el-table-column label="成功率" width="80" align="right">
              <template #default="{ row }">
                <span :class="row.success_count / row.total_count > 0.9 ? 'text-success' : 'text-danger'">
                  {{ ((row.success_count / row.total_count) * 100).toFixed(1) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="avg_duration_ms" label="平均耗时" width="80" align="right">
              <template #default="{ row }">
                {{ formatDuration(row.avg_duration_ms) }}
              </template>
            </el-table-column>
            <el-table-column prop="unique_users" label="用户数" width="70" align="right" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 平台分布 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <span>平台分布（近30天）</span>
          </template>
          <div class="platform-stats">
            <div v-for="stat in platformStats" :key="stat.platform" class="platform-item">
              <div class="platform-info">
                <el-tag :type="getPlatformType(stat.platform)" size="small">
                  {{ getPlatformLabel(stat.platform) }}
                </el-tag>
                <span class="platform-count">{{ formatNumber(stat.total_count) }} 次</span>
              </div>
              <el-progress
                :percentage="Math.round((stat.success_count / stat.total_count) * 100)"
                :color="stat.success_count / stat.total_count > 0.9 ? '#67c23a' : '#f56c6c'"
                :stroke-width="8"
              />
              <div class="platform-meta">
                <span>成功率: {{ ((stat.success_count / stat.total_count) * 100).toFixed(1) }}%</span>
                <span>平均耗时: {{ formatDuration(stat.avg_duration_ms) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.command-stats {
  padding: 16px;
}

.platform-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.platform-item {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.platform-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.platform-count {
  font-size: 14px;
  font-weight: 600;
}

.platform-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.text-success {
  color: #67c23a;
}

.text-danger {
  color: #f56c6c;
}
</style>

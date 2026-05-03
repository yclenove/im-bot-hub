<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'

const stats = ref<Record<string, any>>({})
const trend = ref<Array<{ date: string; total: number; success: number; failed: number }>>([])
const recentLogs = ref<Array<any>>([])

const trendMax = computed(() => Math.max(...trend.value.map(d => d.total), 1))

const quickStartActive = computed(() => {
  if (!stats.value.botTotal) return 0
  if (!stats.value.channelTotal) return 1
  if (!stats.value.queryTotal) return 2
  return 4
})

async function loadAll() {
  try {
    const [s, t, l] = await Promise.all([
      api.get('/admin/dashboard/stats'),
      api.get('/admin/dashboard/trend'),
      api.get('/admin/dashboard/recent-logs'),
    ])
    stats.value = s.data
    trend.value = t.data
    recentLogs.value = l.data
  } catch { /* ignore */ }
}

onMounted(loadAll)

defineExpose({ refresh: loadAll })
</script>

<template>
  <el-skeleton :loading="!stats.botTotal && stats.botTotal !== 0" animated :rows="8">
    <template #template>
      <div style="display: flex; gap: 16px; margin-bottom: 20px">
        <el-skeleton-item v-for="i in 4" :key="i" variant="rect" style="width: 200px; height: 100px; border-radius: 8px" />
      </div>
      <el-skeleton-item variant="rect" style="width: 100%; height: 200px; border-radius: 8px" />
    </template>
    <template #default>
  <div class="overview-tab">
    <!-- 统计卡片 -->
    <div class="overview-stats">
      <el-card shadow="hover" class="overview-stat-card">
        <div class="stat-value" style="color: var(--el-color-primary)">{{ stats.botTotal ?? '-' }}</div>
        <div class="stat-label">机器人总数</div>
        <div class="stat-sub">启用: {{ stats.botEnabled ?? '-' }}</div>
      </el-card>
      <el-card shadow="hover" class="overview-stat-card">
        <div class="stat-value" style="color: var(--el-color-success)">{{ stats.channelTotal ?? '-' }}</div>
        <div class="stat-label">渠道总数</div>
      </el-card>
      <el-card shadow="hover" class="overview-stat-card">
        <div class="stat-value" style="color: var(--el-color-warning)">{{ stats.queryTotal ?? '-' }}</div>
        <div class="stat-label">查询定义</div>
        <div class="stat-sub">启用: {{ stats.queryEnabled ?? '-' }}</div>
      </el-card>
      <el-card shadow="hover" class="overview-stat-card">
        <div class="stat-value" style="color: var(--el-color-info)">{{ stats.todayCommandTotal ?? '-' }}</div>
        <div class="stat-label">今日命令</div>
        <div class="stat-sub success">成功: {{ stats.todayCommandSuccess ?? '-' }} / 失败: {{ stats.todayCommandFailed ?? '-' }}</div>
      </el-card>
    </div>

    <!-- 快速开始引导 -->
    <el-card v-if="!stats.queryTotal" shadow="never" class="overview-section quickstart-card">
      <template #header><span class="section-title">🚀 快速开始</span></template>
      <el-steps direction="vertical" :active="quickStartActive" :space="60">
        <el-step title="创建机器人" description="点击「机器人」Tab → 新建机器人，填写名称即可" />
        <el-step title="添加渠道" description="点击「渠道管理」Tab → 新建渠道，选择平台（Telegram/飞书/钉钉/企微/Slack/Discord）并填写凭证" />
        <el-step title="配置数据源" description="点击「数据源」Tab → 新建数据源，填写数据库连接或 API 地址" />
        <el-step title="创建查询" description="点击「查询定义」Tab → 新建查询，配置命令名和查询逻辑" />
        <el-step title="测试使用" description="在 IM 平台向机器人发送命令，如 /cx 参数值" />
      </el-steps>
    </el-card>

    <!-- 7 天趋势 -->
    <el-card shadow="never" class="overview-section">
      <template #header><span class="section-title">最近 7 天命令趋势</span></template>
      <div class="trend-chart">
        <div v-for="d in trend" :key="d.date" class="trend-bar-group">
          <div class="trend-total">{{ d.total || '' }}</div>
          <div class="trend-bars">
            <div class="trend-bar success" :style="{ height: (d.success / trendMax * 100) + 'px' }" />
            <div class="trend-bar failed" :style="{ height: (d.failed / trendMax * 100) + 'px' }" />
          </div>
          <div class="trend-date">{{ d.date }}</div>
        </div>
      </div>
      <div class="trend-legend">
        <span><span class="legend-dot success"></span>成功</span>
        <span><span class="legend-dot failed"></span>失败</span>
      </div>
    </el-card>

    <!-- 渠道分布 -->
    <el-card v-if="stats.channelByPlatform" shadow="never" class="overview-section">
      <template #header><span class="section-title">渠道分布</span></template>
      <div class="channel-dist">
        <el-tag v-for="(count, platform) in stats.channelByPlatform" :key="platform"
          :type="platformTagType(String(platform))" size="large">
          {{ platformLabel(String(platform)) }}: {{ count }}
        </el-tag>
      </div>
    </el-card>

    <!-- 最近命令 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span class="section-title">最近命令</span>
          <el-button @click="loadAll" size="small">刷新</el-button>
        </div>
      </template>
      <el-table v-if="recentLogs.length" :data="recentLogs" size="small" style="width: 100%">
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
      <el-empty v-else description="暂无命令记录" :image-size="48" />
    </el-card>
  </div>
    </template>
  </el-skeleton>
</template>

<style scoped>
.overview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}
.overview-stat-card { width: 200px; }
.stat-value { font-size: 28px; font-weight: bold; }
.stat-label { color: #999; font-size: 13px; }
.stat-sub { font-size: 12px; color: #666; }
.stat-sub.success { color: #67c23a; }
.overview-section { margin-bottom: 16px; }
.section-title { font-weight: 600; }
.trend-chart { display: flex; align-items: flex-end; gap: 8px; height: 160px; padding: 0 8px; }
.trend-bar-group { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.trend-total { font-size: 11px; color: #999; }
.trend-bars { width: 100%; display: flex; flex-direction: column; gap: 2px; justify-content: flex-end; height: 120px; }
.trend-bar { border-radius: 3px; min-height: 0; }
.trend-bar.success { background: var(--el-color-success); border-radius: 3px 3px 0 0; }
.trend-bar.failed { background: var(--el-color-danger); border-radius: 0 0 3px 3px; }
.trend-date { font-size: 11px; color: #666; }
.trend-legend { display: flex; gap: 16px; justify-content: center; margin-top: 8px; font-size: 12px; color: #999; }
.legend-dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 4px; }
.legend-dot.success { background: var(--el-color-success); }
.legend-dot.failed { background: var(--el-color-danger); }
.channel-dist { display: flex; flex-wrap: wrap; gap: 12px; }
.quickstart-card { border-left: 4px solid var(--el-color-primary); }
</style>

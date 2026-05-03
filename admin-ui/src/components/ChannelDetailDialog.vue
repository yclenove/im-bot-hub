<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { platformLabel, platformTagType } from '../utils/platform'

interface ChannelRow {
  id: number; botId: number; platform: string; enabled: boolean
  webhookUrl: string; credentialsSummary: string
}

const props = defineProps<{ visible: boolean; channel: ChannelRow | null }>()
const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

// Webhook 管理
const webhookPublicBase = ref('')
const webhookStatus = ref<any>(null)
const webhookLoading = ref(false)
const webhookRegLoading = ref(false)

// 连通性测试
const testTargetId = ref('')
const testLoading = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

watch(() => props.visible, (val) => {
  if (val) {
    webhookPublicBase.value = ''
    webhookStatus.value = null
    testTargetId.value = ''
    testResult.value = null
  }
})

async function loadWebhookStatus() {
  if (!props.channel) return
  webhookLoading.value = true
  try {
    const { data } = await api.get(`/admin/channels/${props.channel.id}/webhook-status`)
    webhookStatus.value = data
  } catch (e: any) {
    webhookStatus.value = { error: e?.response?.data?.message || '查询失败' }
  } finally {
    webhookLoading.value = false
  }
}

async function registerWebhook() {
  if (!props.channel) return
  webhookRegLoading.value = true
  try {
    const body: Record<string, string> = {}
    if (webhookPublicBase.value.trim()) body.publicBaseUrl = webhookPublicBase.value.trim()
    const { data } = await api.post(`/admin/channels/${props.channel.id}/register-webhook`, body)
    if (data.telegramOk) {
      ElMessage.success(data.description || 'Webhook 已注册')
    } else {
      ElMessage.error(data.description || '注册失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '注册失败')
  } finally {
    webhookRegLoading.value = false
  }
}

async function testChannel() {
  if (!props.channel) return
  testLoading.value = true
  testResult.value = null
  try {
    const body: Record<string, string> = {}
    if (testTargetId.value.trim()) body.targetId = testTargetId.value.trim()
    const { data } = await api.post(`/admin/channels/${props.channel.id}/test`, body)
    testResult.value = data
    if (data.success) ElMessage.success(data.message)
    else ElMessage.error(data.message)
  } catch (e: any) {
    testResult.value = { success: false, message: e?.response?.data?.message || '测试失败' }
    ElMessage.error('测试失败')
  } finally {
    testLoading.value = false
  }
}

// 飞书菜单同步
const larkMenuSyncLoading = ref(false)
const larkMenuSyncResult = ref<{ success: boolean; message: string } | null>(null)

async function syncLarkMenu() {
  if (!props.channel) return
  larkMenuSyncLoading.value = true
  larkMenuSyncResult.value = null
  try {
    const { data } = await api.post(`/admin/channels/${props.channel.id}/sync-lark-menu`)
    larkMenuSyncResult.value = data
    if (data.success) ElMessage.success(data.message)
    else ElMessage.error(data.message)
  } catch (e: any) {
    larkMenuSyncResult.value = { success: false, message: e?.response?.data?.message || '同步失败' }
    ElMessage.error('同步失败')
  } finally {
    larkMenuSyncLoading.value = false
  }
}

const testPlaceholder: Record<string, string> = {
  TELEGRAM: 'chat_id（可留空仅验证 Token）',
  LARK: 'open_id 或 oc_ 开头的 chat_id',
  SLACK: 'C 开头的 channel_id（可留空仅验证 Token）',
  DISCORD: 'channel_id（可留空仅验证 Token）',
}
</script>

<template>
  <el-dialog :model-value="visible" @update:model-value="emit('update:visible', $event)" title="渠道详情" width="640px" destroy-on-close>
    <template v-if="channel">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="渠道 ID">{{ channel.id }}</el-descriptions-item>
        <el-descriptions-item label="机器人 ID">{{ channel.botId }}</el-descriptions-item>
        <el-descriptions-item label="平台">
          <el-tag :type="platformTagType(channel.platform)">{{ platformLabel(channel.platform) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="channel.enabled ? 'success' : 'info'">{{ channel.enabled ? '启用' : '禁用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="凭证摘要">{{ channel.credentialsSummary }}</el-descriptions-item>
        <el-descriptions-item label="Webhook URL" :span="2">
          <span style="word-break: break-all; font-size: 12px">{{ channel.webhookUrl }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- Telegram Webhook 管理 -->
      <template v-if="channel.platform === 'TELEGRAM'">
        <el-divider content-position="left">Webhook 管理</el-divider>
        <el-form label-width="100px" size="small">
          <el-form-item label="公网基址">
            <el-input v-model="webhookPublicBase" placeholder="可留空：使用配置文件默认值" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="webhookRegLoading" @click="registerWebhook">注册 Webhook</el-button>
            <el-button :loading="webhookLoading" @click="loadWebhookStatus">查看状态</el-button>
          </el-form-item>
        </el-form>
        <template v-if="webhookStatus">
          <el-alert v-if="webhookStatus.error" :title="webhookStatus.error" type="error" show-icon :closable="false" />
          <template v-else>
            <el-descriptions :column="2" border size="small" style="margin-top: 12px">
              <el-descriptions-item label="Telegram 状态">{{ webhookStatus.telegramOk ? '正常' : '异常' }}</el-descriptions-item>
              <el-descriptions-item label="当前 URL">{{ webhookStatus.url || '（空）' }}</el-descriptions-item>
              <el-descriptions-item label="待处理数">{{ webhookStatus.pendingUpdateCount ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="出口 IP">{{ webhookStatus.ipAddress ?? '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="webhookStatus.lastErrorMessage" label="最后错误" :span="2">
                <span style="color: var(--el-color-danger)">{{ webhookStatus.lastErrorMessage }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
      </template>

      <!-- 非 Telegram 平台提示 -->
      <template v-else>
        <el-divider content-position="left">Webhook 配置</el-divider>
        <el-alert title="请将上方 Webhook URL 配置到对应平台的后台" type="info" show-icon :closable="false" />
      </template>

      <!-- 飞书菜单同步 -->
      <template v-if="channel.platform === 'LARK'">
        <el-divider content-position="left">飞书机器人菜单</el-divider>
        <p style="font-size: 13px; color: #909399; margin-bottom: 12px">
          从当前机器人的查询定义自动生成飞书机器人菜单（底部快捷命令）。
        </p>
        <el-button type="primary" :loading="larkMenuSyncLoading" @click="syncLarkMenu">同步菜单到飞书</el-button>
        <el-alert v-if="larkMenuSyncResult"
          :title="larkMenuSyncResult.message"
          :type="larkMenuSyncResult.success ? 'success' : 'error'"
          show-icon :closable="true" @close="larkMenuSyncResult = null"
          style="margin-top: 8px" />
      </template>

      <!-- 连通性测试 -->
      <el-divider content-position="left">连通性测试</el-divider>
      <el-form label-width="100px" size="small">
        <el-form-item label="目标 ID">
          <el-input v-model="testTargetId" :placeholder="testPlaceholder[channel.platform] || '钉钉/企微仅验证凭证格式'" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="testLoading" @click="testChannel">发送测试</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="testResult"
        :title="testResult.message"
        :type="testResult.success ? 'success' : 'error'"
        show-icon :closable="true" @close="testResult = null"
        style="margin-top: 8px" />
    </template>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

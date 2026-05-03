<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

const props = defineProps<{
  visible: boolean
  bots: Array<{ id: number; name: string }>
}>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  'created': []
}>()

const form = ref({
  botId: null as number | null,
  platform: '',
  botToken: '', telegramBotUsername: '', webhookSecretToken: '',
  appId: '', appSecret: '',
  corpId: '', agentId: null as number | null, callbackToken: '', encodingAesKey: '',
  signingSecret: '',
  publicKey: '',
})

function resetForm() {
  form.value = {
    botId: null, platform: '',
    botToken: '', telegramBotUsername: '', webhookSecretToken: '',
    appId: '', appSecret: '',
    corpId: '', agentId: null, callbackToken: '', encodingAesKey: '',
    signingSecret: '', publicKey: '',
  }
}

watch(() => props.visible, (val) => { if (val) resetForm() })

function onPlatformChange() {
  // 清空平台专属字段
  form.value.botToken = ''
  form.value.telegramBotUsername = ''
  form.value.webhookSecretToken = ''
  form.value.appId = ''
  form.value.appSecret = ''
  form.value.corpId = ''
  form.value.agentId = null
  form.value.callbackToken = ''
  form.value.encodingAesKey = ''
  form.value.signingSecret = ''
  form.value.publicKey = ''
}

async function save() {
  const f = form.value
  if (!f.botId) { ElMessage.warning('请选择机器人'); return }
  if (!f.platform) { ElMessage.warning('请选择平台'); return }

  const body: Record<string, any> = { platform: f.platform }

  if (f.platform === 'TELEGRAM') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.telegramBotUsername.trim()) body.telegramBotUsername = f.telegramBotUsername.trim()
    if (f.webhookSecretToken.trim()) body.webhookSecretToken = f.webhookSecretToken.trim()
  } else if (f.platform === 'LARK') {
    if (!f.appId.trim() || !f.appSecret.trim()) { ElMessage.warning('请填写 App ID 和 App Secret'); return }
    body.appId = f.appId.trim()
    body.appSecret = f.appSecret.trim()
  } else if (f.platform === 'DINGTALK') {
    if (!f.appSecret.trim()) { ElMessage.warning('请填写 App Secret'); return }
    body.appSecret = f.appSecret.trim()
  } else if (f.platform === 'WEWORK') {
    if (!f.corpId.trim() || !f.agentId || !f.callbackToken.trim() || !f.encodingAesKey.trim()) {
      ElMessage.warning('请填写所有必填字段'); return
    }
    body.corpId = f.corpId.trim()
    body.agentId = f.agentId
    body.callbackToken = f.callbackToken.trim()
    body.encodingAesKey = f.encodingAesKey.trim()
  } else if (f.platform === 'SLACK') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.signingSecret.trim()) body.signingSecret = f.signingSecret.trim()
  } else if (f.platform === 'DISCORD') {
    if (!f.botToken.trim()) { ElMessage.warning('请填写 Bot Token'); return }
    body.botToken = f.botToken.trim()
    if (f.publicKey.trim()) body.publicKey = f.publicKey.trim()
  }

  try {
    await api.post(`/admin/bots/${f.botId}/channels`, body)
    ElMessage.success('渠道已创建，接下来可配置 Webhook 或测试连通性')
    emit('update:visible', false)
    emit('created')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  }
}
</script>

<template>
  <el-dialog :model-value="visible" @update:model-value="emit('update:visible', $event)" title="新建渠道" width="600px" destroy-on-close>
    <el-form label-width="120px">
      <el-form-item label="机器人" required>
        <el-select v-model="form.botId" placeholder="选择机器人" style="width: 100%">
          <el-option v-for="b in bots" :key="b.id" :label="`${b.id} - ${b.name}`" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="平台" required>
        <el-select v-model="form.platform" placeholder="选择平台" style="width: 100%" @change="onPlatformChange">
          <el-option label="Telegram" value="TELEGRAM" />
          <el-option label="飞书" value="LARK" />
          <el-option label="钉钉" value="DINGTALK" />
          <el-option label="企业微信" value="WEWORK" />
          <el-option label="Slack" value="SLACK" />
          <el-option label="Discord" value="DISCORD" />
        </el-select>
      </el-form-item>

      <template v-if="form.platform === 'TELEGRAM'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="form.botToken" type="password" show-password placeholder="123456:ABC-DEF..." />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.telegramBotUsername" placeholder="选填：xxx_bot" />
        </el-form-item>
        <el-form-item label="Webhook 密钥">
          <el-input v-model="form.webhookSecretToken" type="password" show-password placeholder="选填" />
        </el-form-item>
      </template>

      <template v-if="form.platform === 'LARK'">
        <el-form-item label="App ID" required>
          <el-input v-model="form.appId" placeholder="cli_xxx" />
        </el-form-item>
        <el-form-item label="App Secret" required>
          <el-input v-model="form.appSecret" type="password" show-password />
        </el-form-item>
      </template>

      <template v-if="form.platform === 'DINGTALK'">
        <el-form-item label="App Secret" required>
          <el-input v-model="form.appSecret" type="password" show-password placeholder="钉钉机器人 AppSecret" />
        </el-form-item>
      </template>

      <template v-if="form.platform === 'WEWORK'">
        <el-form-item label="CorpID" required>
          <el-input v-model="form.corpId" placeholder="ww1234567890" />
        </el-form-item>
        <el-form-item label="AgentId" required>
          <el-input-number v-model="form.agentId" :min="1" />
        </el-form-item>
        <el-form-item label="Token" required>
          <el-input v-model="form.callbackToken" />
        </el-form-item>
        <el-form-item label="EncodingAESKey" required>
          <el-input v-model="form.encodingAesKey" placeholder="43 位" />
        </el-form-item>
      </template>

      <template v-if="form.platform === 'SLACK'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="form.botToken" type="password" show-password placeholder="xoxb-..." />
        </el-form-item>
        <el-form-item label="Signing Secret">
          <el-input v-model="form.signingSecret" type="password" show-password placeholder="选填" />
        </el-form-item>
      </template>

      <template v-if="form.platform === 'DISCORD'">
        <el-form-item label="Bot Token" required>
          <el-input v-model="form.botToken" type="password" show-password placeholder="MTIzNDU2Nzg5..." />
        </el-form-item>
        <el-form-item label="Public Key">
          <el-input v-model="form.publicKey" placeholder="选填：用于验证 Interactions 签名" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="save">创建</el-button>
    </template>
  </el-dialog>
</template>

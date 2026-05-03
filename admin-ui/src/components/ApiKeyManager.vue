<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'

interface ApiKey {
  id: number
  key_name: string
  api_key: string
  description: string
  permissions: string
  rate_limit: number
  enabled: boolean
  expires_at: string
  last_used_at: string
  created_at: string
}

const keys = ref<ApiKey[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({
  keyName: '',
  description: '',
  permissions: 'READ',
  rateLimit: 100,
  expiresAt: '',
})
const createdKey = ref<{ apiKey: string; secretKey: string } | null>(null)

async function loadKeys() {
  loading.value = true
  try {
    const { data } = await api.get<ApiKey[]>('/admin/v4/gateway/keys')
    keys.value = data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载 API Key 失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = {
    keyName: '',
    description: '',
    permissions: 'READ',
    rateLimit: 100,
    expiresAt: '',
  }
  createdKey.value = null
  dialogVisible.value = true
}

async function createKey() {
  if (!form.value.keyName.trim()) {
    ElMessage.warning('请填写 Key 名称')
    return
  }

  try {
    const { data } = await api.post('/admin/v4/gateway/keys', {
      keyName: form.value.keyName,
      description: form.value.description,
      permissions: form.value.permissions,
      rateLimit: form.value.rateLimit,
      expiresAt: form.value.expiresAt || null,
    })
    createdKey.value = data
    ElMessage.success('API Key 创建成功')
    await loadKeys()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '创建 API Key 失败')
  }
}

async function toggleKey(key: ApiKey) {
  try {
    if (key.enabled) {
      await api.post(`/admin/v4/gateway/keys/${key.id}/disable`)
      ElMessage.success('已禁用')
    } else {
      await api.post(`/admin/v4/gateway/keys/${key.id}/enable`)
      ElMessage.success('已启用')
    }
    await loadKeys()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '操作失败')
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制到剪贴板')
}

onMounted(loadKeys)
</script>

<template>
  <div class="api-key-manager">
    <div class="manager-header">
      <h4>API Key 管理</h4>
      <el-button type="primary" @click="openCreate">创建 API Key</el-button>
    </div>

    <el-table :data="keys" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="key_name" label="名称" width="150" />
      <el-table-column prop="api_key" label="API Key" width="250">
        <template #default="{ row }">
          <code class="api-key-code">{{ row.api_key.substring(0, 20) }}...</code>
          <el-button size="small" text @click="copyToClipboard(row.api_key)">复制</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="permissions" label="权限" width="100" />
      <el-table-column prop="rate_limit" label="限流" width="100">
        <template #default="{ row }">
          {{ row.rate_limit }}/分钟
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="last_used_at" label="最后使用" width="180">
        <template #default="{ row }">
          {{ row.last_used_at ? new Date(row.last_used_at).toLocaleString() : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" :type="row.enabled ? 'danger' : 'success'" @click="toggleKey(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建对话框 -->
    <el-dialog v-model="dialogVisible" title="创建 API Key" width="500px">
      <template v-if="!createdKey">
        <el-form :model="form" label-width="100px">
          <el-form-item label="名称" required>
            <el-input v-model="form.keyName" placeholder="Key 名称" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" placeholder="用途描述" />
          </el-form-item>
          <el-form-item label="权限">
            <el-select v-model="form.permissions">
              <el-option label="只读" value="READ" />
              <el-option label="读写" value="READ,WRITE" />
              <el-option label="管理员" value="READ,WRITE,ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item label="限流">
            <el-input-number v-model="form.rateLimit" :min="1" :max="10000" />
            <span style="margin-left: 8px">次/分钟</span>
          </el-form-item>
          <el-form-item label="过期时间">
            <el-date-picker v-model="form.expiresAt" type="datetime" placeholder="留空永不过期" />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="createKey">创建</el-button>
        </template>
      </template>

      <template v-else>
        <el-alert title="API Key 创建成功" type="success" show-icon :closable="false" />
        <div class="created-key-info">
          <p><strong>API Key:</strong></p>
          <div class="key-display">
            <code>{{ createdKey.apiKey }}</code>
            <el-button size="small" @click="copyToClipboard(createdKey.apiKey)">复制</el-button>
          </div>
          <p><strong>Secret Key:</strong></p>
          <div class="key-display">
            <code>{{ createdKey.secretKey }}</code>
            <el-button size="small" @click="copyToClipboard(createdKey.secretKey)">复制</el-button>
          </div>
          <el-alert title="请妥善保存 Secret Key，关闭后将无法再次查看" type="warning" show-icon style="margin-top: 12px" />
        </div>
        <template #footer>
          <el-button type="primary" @click="dialogVisible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.api-key-manager {
  padding: 16px;
}

.manager-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.manager-header h4 {
  margin: 0;
}

.api-key-code {
  font-family: monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}

.created-key-info {
  margin-top: 16px;
}

.key-display {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.key-display code {
  flex: 1;
  font-family: monospace;
  word-break: break-all;
}
</style>

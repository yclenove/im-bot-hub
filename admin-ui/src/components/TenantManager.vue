<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface Tenant {
  id: number
  name: string
  code: string
  domain: string
  plan: string
  status: string
}

interface TenantQuota {
  quota_type: string
  quota_limit: number
  current_usage: number
}

const tenants = ref<Tenant[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const quotaDialogVisible = ref(false)
const selectedTenant = ref<Tenant | null>(null)
const quotas = ref<TenantQuota[]>([])
const form = ref({
  name: '',
  code: '',
  plan: 'FREE',
})

async function loadTenants() {
  loading.value = true
  try {
    const { data } = await api.get<Tenant[]>('/admin/v4/tenants')
    tenants.value = data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载租户列表失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = { name: '', code: '', plan: 'FREE' }
  dialogVisible.value = true
}

async function createTenant() {
  if (!form.value.name.trim() || !form.value.code.trim()) {
    ElMessage.warning('请填写完整信息')
    return
  }

  try {
    await api.post('/admin/v4/tenants', form.value)
    ElMessage.success('租户创建成功')
    dialogVisible.value = false
    await loadTenants()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '创建租户失败')
  }
}

async function viewQuotas(tenant: Tenant) {
  selectedTenant.value = tenant
  try {
    const { data } = await api.get<TenantQuota[]>(`/admin/v4/tenants/${tenant.id}/quotas`)
    quotas.value = data
    quotaDialogVisible.value = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载配额失败')
  }
}

function getPlanType(plan: string): string {
  const map: Record<string, string> = {
    FREE: 'info',
    PRO: 'warning',
    ENTERPRISE: 'success',
  }
  return map[plan] || 'info'
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    SUSPENDED: 'danger',
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

function getQuotaLabel(type: string): string {
  const map: Record<string, string> = {
    BOT_COUNT: '机器人数量',
    CHANNEL_COUNT: '渠道数量',
    QUERY_COUNT: '查询数量',
    API_CALLS: 'API 调用次数',
  }
  return map[type] || type
}

onMounted(loadTenants)
</script>

<template>
  <div class="tenant-manager">
    <div class="manager-header">
      <h4>租户管理</h4>
      <el-button type="primary" @click="openCreate">创建租户</el-button>
    </div>

    <el-table :data="tenants" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="domain" label="域名" width="200" show-overflow-tooltip />
      <el-table-column prop="plan" label="套餐" width="100">
        <template #default="{ row }">
          <el-tag :type="getPlanType(row.plan)" size="small">{{ row.plan }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="viewQuotas(row)">配额</el-button>
          <el-button size="small">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建对话框 -->
    <el-dialog v-model="dialogVisible" title="创建租户" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="租户名称" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="form.code" placeholder="唯一编码" />
        </el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.plan">
            <el-option label="免费版" value="FREE" />
            <el-option label="专业版" value="PRO" />
            <el-option label="企业版" value="ENTERPRISE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createTenant">创建</el-button>
      </template>
    </el-dialog>

    <!-- 配额对话框 -->
    <el-dialog v-model="quotaDialogVisible" :title="`${selectedTenant?.name} - 配额管理`" width="600px">
      <el-table :data="quotas" size="small">
        <el-table-column prop="quota_type" label="配额类型" width="150">
          <template #default="{ row }">
            {{ getQuotaLabel(row.quota_type) }}
          </template>
        </el-table-column>
        <el-table-column prop="quota_limit" label="上限" width="120" align="right" />
        <el-table-column prop="current_usage" label="已使用" width="120" align="right" />
        <el-table-column label="使用率" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="row.quota_limit > 0 ? Math.round((row.current_usage / row.quota_limit) * 100) : 0"
              :color="row.current_usage / row.quota_limit > 0.8 ? '#f56c6c' : '#409eff'"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.tenant-manager {
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
</style>

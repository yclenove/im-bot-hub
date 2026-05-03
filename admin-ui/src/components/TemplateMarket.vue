<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface QueryTemplate {
  id: number
  name: string
  category: string
  description: string
  configJson: string
  version: number
  author: string
  downloads: number
  enabled: boolean
}

const templates = ref<QueryTemplate[]>([])
const loading = ref(false)
const categoryFilter = ref('')
const importDialogVisible = ref(false)
const selectedTemplate = ref<QueryTemplate | null>(null)
const importForm = ref({ botId: 0, datasourceId: 0 })

const categories = [
  { label: '全部', value: '' },
  { label: '电商', value: 'ecommerce' },
  { label: 'SaaS', value: 'saas' },
  { label: '运维', value: 'ops' },
  { label: '自定义', value: 'custom' }
]

async function loadTemplates() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (categoryFilter.value) params.category = categoryFilter.value
    const { data } = await api.get<QueryTemplate[]>('/admin/templates', { params })
    templates.value = data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载模板失败')
  } finally {
    loading.value = false
  }
}

function openImport(template: QueryTemplate) {
  selectedTemplate.value = template
  importDialogVisible.value = true
}

async function handleImport() {
  if (!selectedTemplate.value || !importForm.value.botId || !importForm.value.datasourceId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await api.post(`/admin/templates/${selectedTemplate.value.id}/import`, importForm.value)
    ElMessage.success('模板导入成功')
    importDialogVisible.value = false
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '导入失败')
  }
}

function getCategoryLabel(category: string): string {
  const map: Record<string, string> = {
    ecommerce: '电商',
    saas: 'SaaS',
    ops: '运维',
    custom: '自定义'
  }
  return map[category] || category
}

function getCategoryType(category: string): string {
  const map: Record<string, string> = {
    ecommerce: 'danger',
    saas: 'primary',
    ops: 'warning',
    custom: 'info'
  }
  return map[category] || 'info'
}

onMounted(loadTemplates)
</script>

<template>
  <div class="template-market">
    <div class="template-header">
      <h3>查询模板市场</h3>
      <el-select v-model="categoryFilter" placeholder="选择分类" @change="loadTemplates">
        <el-option v-for="cat in categories" :key="cat.value" :label="cat.label" :value="cat.value" />
      </el-select>
    </div>

    <el-row :gutter="16">
      <el-col v-for="template in templates" :key="template.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="template-card" shadow="hover">
          <template #header>
            <div class="template-card-header">
              <span class="template-name">{{ template.name }}</span>
              <el-tag :type="getCategoryType(template.category)" size="small">
                {{ getCategoryLabel(template.category) }}
              </el-tag>
            </div>
          </template>
          <div class="template-desc">{{ template.description }}</div>
          <div class="template-meta">
            <span>作者: {{ template.author }}</span>
            <span>下载: {{ template.downloads }}</span>
          </div>
          <div class="template-actions">
            <el-button type="primary" size="small" @click="openImport(template)">一键导入</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && templates.length === 0" description="暂无模板" />

    <!-- 导入对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入模板" width="500px">
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="模板名称">
          <span>{{ selectedTemplate?.name }}</span>
        </el-form-item>
        <el-form-item label="目标机器人" required>
          <el-input-number v-model="importForm.botId" :min="1" placeholder="机器人 ID" />
        </el-form-item>
        <el-form-item label="目标数据源" required>
          <el-input-number v-model="importForm.datasourceId" :min="1" placeholder="数据源 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.template-market {
  padding: 16px;
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.template-header h3 {
  margin: 0;
}

.template-card {
  margin-bottom: 16px;
}

.template-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.template-name {
  font-weight: 600;
}

.template-desc {
  color: #666;
  font-size: 13px;
  margin-bottom: 12px;
  min-height: 40px;
}

.template-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}

.template-actions {
  text-align: right;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface Workflow {
  id: number
  name: string
  description: string
  trigger_type: string
  enabled: boolean
  created_at: string
}

interface WorkflowStep {
  id: string
  name: string
  type: string
  config: Record<string, unknown>
}

const workflows = ref<Workflow[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({
  name: '',
  description: '',
  triggerType: 'MANUAL',
  steps: [] as WorkflowStep[],
})

async function loadWorkflows() {
  loading.value = true
  try {
    const { data } = await api.get<Workflow[]>('/admin/workflows')
    workflows.value = data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载工作流失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = {
    name: '',
    description: '',
    triggerType: 'MANUAL',
    steps: [],
  }
  dialogVisible.value = true
}

function addStep(type: string) {
  const step: WorkflowStep = {
    id: `step_${Date.now()}`,
    name: `步骤 ${form.value.steps.length + 1}`,
    type,
    config: {},
  }
  form.value.steps.push(step)
}

function removeStep(idx: number) {
  form.value.steps.splice(idx, 1)
}

async function saveWorkflow() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写工作流名称')
    return
  }

  try {
    await api.post('/admin/workflows', {
      name: form.value.name,
      description: form.value.description,
      triggerType: form.value.triggerType,
      stepsJson: JSON.stringify(form.value.steps),
      variablesJson: '{}',
      triggerConfig: '{}',
      createdBy: 1,
    })
    ElMessage.success('工作流创建成功')
    dialogVisible.value = false
    await loadWorkflows()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '创建工作流失败')
  }
}

async function executeWorkflow(id: number) {
  try {
    await api.post(`/admin/workflows/${id}/execute`, {
      triggeredBy: 1,
    })
    ElMessage.success('工作流已触发执行')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '执行工作流失败')
  }
}

function getStepTypeLabel(type: string): string {
  const map: Record<string, string> = {
    QUERY: '查询',
    CONDITION: '条件',
    DELAY: '延迟',
    NOTIFICATION: '通知',
    APPROVAL: '审批',
  }
  return map[type] || type
}

function getStepTypeIcon(type: string): string {
  const map: Record<string, string> = {
    QUERY: 'Search',
    CONDITION: 'Switch',
    DELAY: 'Timer',
    NOTIFICATION: 'Bell',
    APPROVAL: 'Check',
  }
  return map[type] || 'Document'
}

onMounted(loadWorkflows)
</script>

<template>
  <div class="workflow-designer">
    <div class="designer-header">
      <h4>工作流管理</h4>
      <el-button type="primary" @click="openCreate">新建工作流</el-button>
    </div>

    <!-- 工作流列表 -->
    <el-table :data="workflows" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="trigger_type" label="触发方式" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.trigger_type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="executeWorkflow(row.id)">执行</el-button>
          <el-button size="small">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建对话框 -->
    <el-dialog v-model="dialogVisible" title="新建工作流" width="700px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="工作流名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="工作流描述" />
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="form.triggerType">
            <el-option label="手动触发" value="MANUAL" />
            <el-option label="定时触发" value="CRON" />
            <el-option label="事件触发" value="EVENT" />
          </el-select>
        </el-form-item>

        <el-form-item label="步骤">
          <div class="steps-list">
            <div v-for="(step, idx) in form.steps" :key="step.id" class="step-item">
              <div class="step-header">
                <el-tag :type="step.type === 'QUERY' ? 'primary' : step.type === 'CONDITION' ? 'warning' : 'info'" size="small">
                  {{ getStepTypeLabel(step.type) }}
                </el-tag>
                <span class="step-name">{{ step.name }}</span>
                <el-button size="small" type="danger" text @click="removeStep(idx)">删除</el-button>
              </div>
            </div>

            <div class="step-actions">
              <el-button size="small" @click="addStep('QUERY')">+ 查询步骤</el-button>
              <el-button size="small" @click="addStep('CONDITION')">+ 条件步骤</el-button>
              <el-button size="small" @click="addStep('DELAY')">+ 延迟步骤</el-button>
              <el-button size="small" @click="addStep('NOTIFICATION')">+ 通知步骤</el-button>
              <el-button size="small" @click="addStep('APPROVAL')">+ 审批步骤</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWorkflow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.workflow-designer {
  padding: 16px;
}

.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.designer-header h4 {
  margin: 0;
}

.steps-list {
  width: 100%;
}

.step-item {
  padding: 12px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-name {
  flex: 1;
  font-weight: 500;
}

.step-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

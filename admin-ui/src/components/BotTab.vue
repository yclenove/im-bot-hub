<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'
import BotDetailDialog from './BotDetailDialog.vue'

type Bot = { id: number; name: string; primaryChannelId?: number | null; enabled: boolean }

const bots = ref<Bot[]>([])
const dlgOpen = ref(false)
const editId = ref<number | null>(null)
const form = ref({ name: '', enabled: true })
const detailOpen = ref(false)
const detailBotId = ref<number | null>(null)

async function load() {
  try {
    const { data } = await api.get('/admin/bots')
    bots.value = data
  } catch { bots.value = [] }
}

function openNew() {
  editId.value = null
  form.value = { name: '', enabled: true }
  dlgOpen.value = true
}

async function openEdit(row: Bot) {
  editId.value = row.id
  form.value = { name: row.name, enabled: row.enabled }
  dlgOpen.value = true
}

async function save() {
  if (!form.value.name.trim()) { ElMessage.warning('请填写机器人名称'); return }
  if (editId.value != null) {
    await api.put(`/admin/bots/${editId.value}`, { name: form.value.name.trim(), enabled: form.value.enabled })
    ElMessage.success('机器人已更新')
  } else {
    await api.post('/admin/bots', { name: form.value.name.trim(), enabled: form.value.enabled })
    ElMessage({ message: '机器人已创建！接下来可到「渠道管理」Tab 添加渠道', type: 'success', duration: 5000 })
  }
  dlgOpen.value = false
  await load()
}

async function remove(row: Bot) {
  try {
    await ElMessageBox.confirm('确定删除该机器人？关联的渠道和查询定义将同时删除。', '确认删除', { type: 'warning' })
  } catch { return }
  await api.delete(`/admin/bots/${row.id}`)
  await load()
  ElMessage.success('已删除')
}

function openDetail(row: Bot) {
  detailBotId.value = row.id
  detailOpen.value = true
}

async function exportConfig() {
  try {
    const { data } = await api.get('/admin/export/bots')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'bots-export.json'; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('机器人配置已导出')
  } catch { ElMessage.error('导出失败') }
}

onMounted(load)
defineExpose({ refresh: load, bots })
</script>

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <el-button type="primary" @click="openNew">新建机器人</el-button>
      <el-button @click="exportConfig">导出配置</el-button>
    </div>

    <el-table v-if="bots.length" :data="bots" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="primaryChannelId" label="主渠道 ID" width="100" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right" align="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="" :image-size="64">
      <template #description>
        <p style="font-size: 15px; color: #606266; margin-bottom: 8px">还没有机器人</p>
        <p style="font-size: 13px; color: #909399">机器人是逻辑分组单元，点击上方「新建机器人」开始</p>
      </template>
    </el-empty>

    <p class="admin-hint" style="margin-top: 8px">
      机器人是逻辑分组单元。点击<strong>详情</strong>可查看关联渠道、查询定义和最近命令。
    </p>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dlgOpen" :title="editId != null ? '编辑机器人' : '新建机器人'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="机器人名称" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgOpen = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <BotDetailDialog v-model:visible="detailOpen" :bot-id="detailBotId" />
  </div>
</template>

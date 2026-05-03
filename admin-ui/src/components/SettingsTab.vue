<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

const settings = ref<Array<{ key: string; value: string; description: string }>>([])
const loading = ref(false)

async function load() {
  try {
    const { data } = await api.get('/admin/settings')
    settings.value = data
  } catch {
    settings.value = []
  }
}

async function save() {
  loading.value = true
  try {
    const body: Record<string, string> = {}
    for (const s of settings.value) {
      if (s.key !== 'encryption-status') body[s.key] = s.value
    }
    const { data } = await api.put('/admin/settings', body)
    settings.value = data
    ElMessage.success('设置已保存')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span style="font-weight: 600">系统设置</span>
        <el-button type="primary" :loading="loading" @click="save">保存</el-button>
      </div>
    </template>
    <el-form label-width="180px" size="default">
      <el-form-item v-for="s in settings" :key="s.key" :label="s.description || s.key">
        <el-input v-if="s.key !== 'encryption-status'" v-model="s.value" :placeholder="s.description" />
        <el-tag v-else :type="s.value === '已启用' ? 'success' : 'info'">{{ s.value }}</el-tag>
      </el-form-item>
    </el-form>
    <el-button @click="load" size="small" style="margin-top: 12px">刷新</el-button>
  </el-card>
</template>

<script setup lang="ts">
import axios from 'axios'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'
import { clearCredentials, setCredentials } from '../auth/session'

const router = useRouter()
const username = ref(import.meta.env.VITE_ADMIN_USER || '')
const password = ref(import.meta.env.VITE_ADMIN_PASS || '')
const loading = ref(false)

async function submit() {
  const u = username.value.trim()
  const p = password.value
  if (!u || !p) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    clearCredentials()
    setCredentials(u, p)
    await api.get('/admin/bots')
    ElMessage.success('登录成功')
    await router.replace('/')
  } catch (err: unknown) {
    clearCredentials()
    if (axios.isAxiosError(err) && err.response?.status === 401) {
      ElMessage.error('用户名或密码错误')
    }
    /* 非 401：已由 api 拦截器统一提示，避免与「登录失败」重复弹窗 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page login-wrap">
    <div class="login-bg" aria-hidden="true" />
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <span class="login-logo" aria-hidden="true">⚡</span>
        <div>
          <h1 class="login-title">IM Bot Hub</h1>
          <p class="login-sub">管理后台 · 使用服务端 Basic 账号登录</p>
        </div>
      </div>
      <el-form class="login-form" @submit.prevent="submit" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" clearable size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            size="large"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" native-type="submit">
          进入控制台
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 15% 20%, rgba(124, 58, 237, 0.28), transparent 55%),
    radial-gradient(ellipse 70% 50% at 85% 80%, rgba(59, 130, 246, 0.2), transparent 50%),
    linear-gradient(165deg, #090a0f 0%, #0f1218 45%, #0b0e14 100%);
  z-index: 0;
}

.login-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse 90% 70% at 50% 50%, black 25%, transparent 100%);
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  text-align: left;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(22, 25, 34, 0.72) !important;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.login-brand {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.login-logo {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.35), rgba(59, 130, 246, 0.2));
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.login-title {
  margin: 0 0 4px;
  font-size: 22px;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.login-sub {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

.login-form {
  margin-top: 8px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  font-weight: 500;
  border-radius: 10px;
}
</style>

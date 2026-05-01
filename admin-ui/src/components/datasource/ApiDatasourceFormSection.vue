<script setup lang="ts">
import { computed } from 'vue'

type ApiDatasourcePreset = {
  key: string
  title: string
  summary: string
}

type ApiDatasourceFormModel = {
  apiPresetKey: string
  apiBaseUrl: string
  authType: string
  username: string
  passwordPlain: string
  apiAuthKeyName: string
  authConfigMode: 'simple' | 'advanced'
  authConfigJson: string
  requestTimeoutMs: number
  defaultHeadersJson: string
  defaultQueryParamsJson: string
  configJson: string
}

const props = defineProps<{
  modelValue: ApiDatasourceFormModel
  advancedSections: string[]
  presets: ApiDatasourcePreset[]
  authTypeHint: string
  authConfigPlaceholder: string
  authConfigHint: string
  showSimpleApiAuthUsername: boolean
  showSimpleApiAuthPassword: boolean
  showSimpleApiAuthSecret: boolean
  authSecretLabel: string
  authSecretHint: string
  authUsernameHint: string
  authPasswordHint: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ApiDatasourceFormModel): void
  (e: 'update:advancedSections', value: string[]): void
  (e: 'apply-preset', presetKey: string): void
}>()

const form = computed({
  get: () => props.modelValue,
  set: (value: ApiDatasourceFormModel) => emit('update:modelValue', value),
})

const advancedSectionsModel = computed({
  get: () => props.advancedSections,
  set: (value: string[]) => emit('update:advancedSections', value),
})

function patchForm(patch: Partial<ApiDatasourceFormModel>) {
  form.value = { ...form.value, ...patch }
}
</script>

<template>
  <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
    API 数据源适合接天气、币价、物流等第三方服务。先选模板，再补真实密钥或特殊参数，通常不需要理解底层 HTTP 细节。
  </el-alert>
  <el-form-item label="常用模板">
    <div class="api-ds-preset-grid">
      <button
        v-for="preset in presets"
        :key="preset.key"
        type="button"
        class="api-ds-preset-card"
        :class="{ 'api-ds-preset-card--active': form.apiPresetKey === preset.key }"
        @click="emit('apply-preset', preset.key)"
      >
        <div class="api-ds-preset-card__title">{{ preset.title }}</div>
        <div class="api-ds-preset-card__summary">{{ preset.summary }}</div>
      </button>
    </div>
    <span class="form-hint">先选一个接近的模板，再把真实地址、密钥或特殊参数补进去，通常最快。</span>
  </el-form-item>
  <el-form-item label="API 基础地址">
    <el-input :model-value="form.apiBaseUrl" placeholder="例如 https://api.binance.com" @update:model-value="patchForm({ apiBaseUrl: String($event) })" />
    <span class="form-hint">这里只填主地址，不需要把具体接口路径一起写进来。</span>
  </el-form-item>
  <el-form-item label="鉴权方式">
    <el-select :model-value="form.authType" style="width: 100%" @update:model-value="patchForm({ authType: String($event) })">
      <el-option label="无鉴权" value="NONE" />
      <el-option label="Bearer Token" value="BEARER_TOKEN" />
      <el-option label="API Key（Header）" value="API_KEY_HEADER" />
      <el-option label="API Key（Query 参数）" value="API_KEY_QUERY" />
      <el-option label="Basic Auth" value="BASIC" />
    </el-select>
    <span class="form-hint">{{ authTypeHint }}</span>
  </el-form-item>
  <el-form-item v-if="showSimpleApiAuthUsername" label="账号 / 用户名">
    <el-input :model-value="form.username" placeholder="例如 demo_user" autocomplete="off" @update:model-value="patchForm({ username: String($event) })" />
    <span class="form-hint">{{ authUsernameHint }}</span>
  </el-form-item>
  <el-form-item v-if="showSimpleApiAuthPassword" label="密码 / Secret">
    <el-input
      :model-value="form.passwordPlain"
      type="password"
      show-password
      autocomplete="off"
      placeholder="填写账号对应密码"
      @update:model-value="patchForm({ passwordPlain: String($event) })"
    />
    <span class="form-hint">{{ authPasswordHint }}</span>
  </el-form-item>
  <el-form-item v-if="showSimpleApiAuthSecret" :label="authSecretLabel">
    <el-input
      :model-value="form.passwordPlain"
      type="password"
      show-password
      autocomplete="off"
      :placeholder="form.authType === 'BEARER_TOKEN' ? '填写 Bearer Token' : '填写 API Key 密钥'"
      @update:model-value="patchForm({ passwordPlain: String($event) })"
    />
    <span class="form-hint">{{ authSecretHint }}</span>
  </el-form-item>
  <el-form-item v-if="form.authType === 'API_KEY_HEADER' || form.authType === 'API_KEY_QUERY'" label="Key 名称">
    <el-input
      :model-value="form.apiAuthKeyName"
      :placeholder="form.authType === 'API_KEY_HEADER' ? '例如 X-API-Key' : '例如 api_key'"
      autocomplete="off"
      @update:model-value="patchForm({ apiAuthKeyName: String($event) })"
    />
    <span class="form-hint">告诉系统密钥应该放在 Header 里还是 URL 参数里，用什么名字发送。</span>
  </el-form-item>
  <el-form-item label="高级鉴权配置">
    <el-radio-group :model-value="form.authConfigMode" @update:model-value="patchForm({ authConfigMode: $event as 'simple' | 'advanced' })">
      <el-radio-button value="simple">推荐：表单模式</el-radio-button>
      <el-radio-button value="advanced">高级：JSON 模式</el-radio-button>
    </el-radio-group>
    <span class="form-hint">绝大多数场景保持“表单模式”即可，只有目标平台要求特殊字段时再切到 JSON。</span>
  </el-form-item>
  <el-form-item v-if="form.authConfigMode === 'advanced'" label="鉴权配置 JSON">
    <el-input
      :model-value="form.authConfigJson"
      type="textarea"
      :rows="4"
      :placeholder="authConfigPlaceholder"
      @update:model-value="patchForm({ authConfigJson: String($event) })"
    />
    <span class="form-hint">{{ authConfigHint }}</span>
  </el-form-item>
  <el-form-item label="超时（毫秒)">
    <el-input-number
      :model-value="form.requestTimeoutMs"
      :min="500"
      :max="30000"
      controls-position="right"
      style="width: 100%"
      @update:model-value="patchForm({ requestTimeoutMs: Number($event) })"
    />
    <span class="form-hint">外部 API 波动通常比数据库更大，建议从 3000 到 5000 毫秒开始。</span>
  </el-form-item>
  <el-collapse v-model="advancedSectionsModel" class="api-ds-advanced-collapse">
    <el-collapse-item name="advanced">
      <template #title>
        <span>高级选项（默认可不填）</span>
      </template>
      <div class="api-ds-advanced-intro">
        只有接口文档明确要求固定 Header、默认参数或特殊健康检查地址时，才需要展开这里修改。
      </div>
      <el-form-item label="默认请求头 JSON">
        <el-input
          :model-value="form.defaultHeadersJson"
          type="textarea"
          :rows="3"
          placeholder='例如 {"Accept":"application/json"}'
          @update:model-value="patchForm({ defaultHeadersJson: String($event) })"
        />
        <span class="form-hint">只有接口文档明确要求固定 Header 时再填；否则可先留空。</span>
      </el-form-item>
      <el-form-item label="默认参数 JSON">
        <el-input
          :model-value="form.defaultQueryParamsJson"
          type="textarea"
          :rows="3"
          placeholder='例如 {"lang":"zh"}'
          @update:model-value="patchForm({ defaultQueryParamsJson: String($event) })"
        />
        <span class="form-hint">适合放语言、地区、版本号等每次都相同的参数。</span>
      </el-form-item>
      <el-form-item label="高级配置 JSON">
        <el-input
          :model-value="form.configJson"
          type="textarea"
          :rows="4"
          placeholder='例如 {"healthcheckPath":"/api/ping"}'
          @update:model-value="patchForm({ configJson: String($event) })"
        />
        <span class="form-hint">可选。当前可配置连通性测试地址 <code>healthcheckPath</code>。</span>
      </el-form-item>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped>
.api-ds-preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  width: 100%;
}

.api-ds-preset-card {
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-blank);
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.api-ds-preset-card:hover,
.api-ds-preset-card--active {
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 24px rgb(64 158 255 / 0.12);
  transform: translateY(-1px);
}

.api-ds-preset-card__title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.api-ds-preset-card__summary {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.api-ds-advanced-collapse {
  margin-top: 4px;
}

.api-ds-advanced-intro {
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .api-ds-preset-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'

const view = ref<{ protocol: string; baseUrl: string; model: string; imageModel: string; embeddingModel: string; apiKeyMasked: string; configured: boolean } | null>(null)
const form = ref({ protocol: 'openai', baseUrl: '', model: '', imageModel: '', embeddingModel: '', apiKey: '' })
const testing = ref(false)

onMounted(async () => {
  view.value = await $api('GET', '/admin/ai-config')
  form.value.protocol = view.value?.protocol || 'openai'
  form.value.baseUrl = view.value?.baseUrl || ''
  form.value.model = view.value?.model || ''
  form.value.imageModel = view.value?.imageModel || ''
  form.value.embeddingModel = view.value?.embeddingModel || ''
})

async function save() {
  await $api('PUT', '/admin/ai-config', {
    ...form.value,
    // 空串表示清除 Key；null 表示不改
    apiKey: form.value.apiKey === '' ? '' : form.value.apiKey || null,
  })
  ElMessage.success('AI 配置已保存')
  view.value = await $api('GET', '/admin/ai-config')
  form.value.apiKey = ''
}

async function test() {
  testing.value = true
  try {
    const r = await $api<{ ok: boolean }>('POST', '/admin/ai-config/test')
    r.ok ? ElMessage.success('连接成功') : ElMessage.error('连接失败（检查 BaseURL/Key/协议）')
  } catch (e) { ElMessage.error((e as Error).message) } finally { testing.value = false }
}
</script>

<template>
  <div style="max-width:640px">
    <h2>AI 配置</h2>
    <div class="card" v-if="view">
      <p class="muted">当前协议：{{ view.protocol }} · 当前密钥：{{ view.apiKeyMasked || '（未配置）' }} · 状态：{{ view.configured ? '已配置' : '未配置' }}</p>
      <div class="form-row">
        <label>接口协议</label>
        <el-select v-model="form.protocol" style="width:100%">
          <el-option value="openai" label="OpenAI 兼容（/chat/completions + Bearer，支持图像/向量）" />
          <el-option value="anthropic" label="Anthropic（/v1/messages + x-api-key，不支持图像/向量）" />
        </el-select>
      </div>
      <div class="form-row">
        <label>Base URL（{{ form.protocol === 'anthropic' ? '如 https://api.anthropic.com，自动拼 /v1/messages' : 'OpenAI 兼容，含 /v1 或 /v4' }}）</label>
        <input v-model="form.baseUrl" :placeholder="form.protocol === 'anthropic' ? 'https://api.anthropic.com' : 'https://open.bigmodel.cn/api/paas/v4'">
      </div>
      <div class="form-row"><label>对话模型</label><input v-model="form.model" :placeholder="form.protocol === 'anthropic' ? 'claude-sonnet-4-5' : 'glm-4-flash'"></div>
      <div class="form-row"><label>图像模型（仅 OpenAI 兼容协议生效）</label><input v-model="form.imageModel" placeholder="cogview-3-flash" :disabled="form.protocol === 'anthropic'"></div>
      <div class="form-row"><label>向量模型（仅 OpenAI 兼容协议生效）</label><input v-model="form.embeddingModel" placeholder="embedding-3" :disabled="form.protocol === 'anthropic'"></div>
      <div class="form-row"><label>API Key（留空不修改；输入空格后清空）</label>
        <input v-model="form.apiKey" type="password" :placeholder="view.apiKeyMasked || '粘贴新 Key'"></div>
      <div style="display:flex;gap:10px">
        <el-button type="primary" @click="save">保存</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
      </div>
    </div>
  </div>
</template>

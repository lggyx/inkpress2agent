<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'

interface CapConfig {
  protocol: string
  baseUrl: string
  model: string
  apiKeyMasked: string
  configured: boolean
}
interface Capability {
  key: string
  label: string
  note: string
  modelPlaceholder: string
  basePlaceholder: string
}

const CAPS: Capability[] = [
  { key: 'text', label: '文本对话', note: '润色/纠错/续写/起标题/摘要标签/评审全走这里', modelPlaceholder: 'step-5-preview / glm-4-flash / claude-sonnet-4-5', basePlaceholder: 'https://api.stepfun.com/step_plan' },
  { key: 'vision', label: '视觉理解', note: '多模态模型（图片/视频输入理解）；无视觉需求可不填', modelPlaceholder: 'step-5-preview / glm-4v', basePlaceholder: 'https://api.stepfun.com/step_plan' },
  { key: 'image', label: '图像生成', note: 'AI 配图走这里（需 OpenAI 兼容图像服务；Anthropic 协议无此接口）', modelPlaceholder: 'cogview-3-flash / step-1x-medium', basePlaceholder: 'https://open.bigmodel.cn/api/paas/v4' },
  { key: 'embedding', label: '向量 Embedding', note: '混合检索的向量臂（Anthropic 协议无此接口，未配置自动降级 BM25）', modelPlaceholder: 'embedding-3 / text-embedding-3-small', basePlaceholder: 'https://open.bigmodel.cn/api/paas/v4' },
]

const caps = ref<Record<string, CapConfig>>({})
const forms = ref<Record<string, { protocol: string; baseUrl: string; model: string; apiKey: string }>>({})
// 静态能力表同步初始化表单（v-model 需要稳定的赋值目标）
for (const c of CAPS) {
  forms.value[c.key] = { protocol: 'openai', baseUrl: '', model: '', apiKey: '' }
}
const testing = ref<Record<string, boolean>>({})
const saving = ref(false)

onMounted(async () => {
  const view = await $api<{ capabilities: Record<string, CapConfig> }>('GET', '/admin/ai-config')
  caps.value = view.capabilities
  for (const c of CAPS) {
    const cfg = view.capabilities[c.key]
    if (cfg) {
      forms.value[c.key] = {
        protocol: cfg.protocol || 'openai',
        baseUrl: cfg.baseUrl || '',
        model: cfg.model || '',
        apiKey: '',
      }
    }
  }
})

async function save() {
  saving.value = true
  try {
    const updates = CAPS.map((c) => ({
      capability: c.key,
      ...forms.value[c.key],
      // 空串表示清除 Key；null 表示不改
      apiKey: forms.value[c.key].apiKey === '' ? '' : forms.value[c.key].apiKey || null,
    }))
    await $api('PUT', '/admin/ai-config', { updates })
    ElMessage.success('AI 配置已保存')
    const view = await $api<{ capabilities: Record<string, CapConfig> }>('GET', '/admin/ai-config')
    caps.value = view.capabilities
    for (const c of CAPS) forms.value[c.key].apiKey = ''
  } catch (e) { ElMessage.error((e as Error).message) } finally { saving.value = false }
}

async function test(cap: string) {
  testing.value[cap] = true
  try {
    const r = await $api<{ ok: boolean }>('POST', '/admin/ai-config/test', { capability: cap })
    r.ok ? ElMessage.success(`「${CAPS.find((c) => c.key === cap)?.label}」连接成功`)
      : ElMessage.error(`「${CAPS.find((c) => c.key === cap)?.label}」连接失败（检查 URL/Key/协议，或该网关未开放此能力）`)
  } catch (e) { ElMessage.error((e as Error).message) } finally { testing.value[cap] = false }
}
</script>

<template>
  <div>
    <div class="head">
      <h2>AI 配置</h2>
      <el-button type="primary" :loading="saving" @click="save">保存全部</el-button>
    </div>
    <p class="muted" style="margin-top:0">
      四个能力槽独立配置（协议 / Base URL / Key / 模型），互不影响；未填写的槽回退使用文本对话的配置。
    </p>

    <div class="cap-grid">
      <div v-for="c in CAPS" :key="c.key" class="card">
        <div class="cap-head">
          <div>
            <div class="cap-title">{{ c.label }}</div>
            <div class="muted cap-note">{{ c.note }}</div>
          </div>
          <div style="display:flex;flex-direction:column;gap:4px;align-items:flex-end">
            <el-tag size="small" :type="caps[c.key]?.configured ? 'success' : 'info'">
              {{ caps[c.key]?.configured ? '已配置' : '未配置' }}
            </el-tag>
            <el-tag v-if="(c.key === 'image' || c.key === 'embedding') && forms[c.key].protocol === 'anthropic'"
                    size="small" type="warning">
              ⚠ Anthropic 协议无此能力
            </el-tag>
          </div>
        </div>

        <div class="form-row">
          <label>接口协议</label>
          <el-select v-model="forms[c.key].protocol" style="width:100%">
            <el-option value="openai" label="OpenAI 兼容（/chat/completions + Bearer）" />
            <el-option value="anthropic" label="Anthropic（/v1/messages + x-api-key/Bearer）" />
          </el-select>
        </div>
        <div class="form-row">
          <label>Base URL</label>
          <input v-model="forms[c.key].baseUrl" :placeholder="c.basePlaceholder">
        </div>
        <div class="form-row">
          <label>模型</label>
          <input v-model="forms[c.key].model" :placeholder="c.modelPlaceholder">
        </div>
        <div class="form-row">
          <label>API Key（留空不改；输入空格清除）</label>
          <input v-model="forms[c.key].apiKey" type="password" :placeholder="caps[c.key]?.apiKeyMasked || '粘贴 Key'">
        </div>

        <el-button size="small" :loading="testing[c.key]" @click="test(c.key)">测试连接</el-button>
        <span class="muted" style="margin-left:8px">密钥：{{ caps[c.key]?.apiKeyMasked || '（未配置）' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.head h2 { margin: 0; }
.cap-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 14px; }
@media (max-width: 1100px) { .cap-grid { grid-template-columns: 1fr; } }
.cap-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.cap-title { font-weight: 700; font-size: 1.05em; }
.cap-note { max-width: 300px; line-height: 1.5; }
</style>

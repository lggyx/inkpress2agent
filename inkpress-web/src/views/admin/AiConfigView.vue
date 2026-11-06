<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, RefreshLeft } from '@element-plus/icons-vue'
import { getConfig, saveConfig, resetConfig, testConnection, VENDOR_PRESETS } from '@/api/ai-config'

const loading = ref(false)
const testing = ref(false)
const saving = ref(false)

// 表单（完整替换语义：除 apiKey 外均必填，apiKey 留空表示不配置密钥）
const form = reactive({
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: 0.7,
  timeoutSeconds: 60,
  embeddingProvider: 'auto',
  embeddingModel: 'embedding-3',
  localDim: 512
})

// 当前生效状态（来自 GET 回显）
const current = ref(null)
// 勾选后清空已保存的密钥（不勾选且输入框为空 = 保持原密钥）
const clearKey = ref(false)

const vendor = ref('')

function applyPreset(label) {
  const preset = VENDOR_PRESETS.find(p => p.label === label)
  if (preset) {
    form.baseUrl = preset.baseUrl
    form.model = preset.model
  } else {
    // 自定义：清空由用户填写
    form.baseUrl = ''
    form.model = ''
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await getConfig()
    current.value = data
    form.baseUrl = data.baseUrl || ''
    form.model = data.model || ''
    form.temperature = data.temperature ?? 0.7
    form.timeoutSeconds = data.timeoutSeconds ?? 60
    form.embeddingProvider = data.embeddingProvider || 'auto'
    form.embeddingModel = data.embeddingModel || 'embedding-3'
    form.localDim = data.localDim ?? 512
    // apiKey 不回显：占位符展示脱敏值，空输入表示保持当前密钥不变
    form.apiKey = ''
    clearKey.value = false
    const matched = VENDOR_PRESETS.find(p => p.baseUrl === form.baseUrl && p.model === form.model)
    vendor.value = matched ? matched.label : ''
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.baseUrl.trim() || !form.model.trim()) {
    ElMessage.warning('base-url 与模型名不能为空')
    return
  }
  saving.value = true
  try {
    // 密钥语义：输入了新值=替换；未输入且未勾选清除=null 保持；勾选清除=空串
    const payload = { ...form }
    payload.apiKey = clearKey.value ? '' : (form.apiKey.trim() || null)
    await saveConfig(payload)
    ElMessage.success('配置已保存，立即生效（无需重启）')
    clearKey.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function doTest() {
  testing.value = true
  try {
    const { data } = await testConnection()
    if (data.success) {
      ElMessage.success(`连接成功（${data.latencyMs}ms）：${data.reply}`)
    } else {
      ElMessage.error(`连接失败（${data.latencyMs}ms）：${data.message}`)
    }
  } finally {
    testing.value = false
  }
}

async function doReset() {
  await ElMessageBox.confirm(
    '恢复默认将删除数据库中的配置，回落至环境变量（application.yml / AI_* 环境变量）。确定吗？',
    '恢复默认', { type: 'warning' })
  await resetConfig()
  ElMessage.success('已恢复默认（回落环境变量）')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="manage-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>AI 服务配置</span>
          <span class="muted">OpenAI 兼容协议 · 保存后即时生效，无需重启</span>
        </div>
      </template>

      <!-- 当前生效状态 -->
      <el-descriptions v-if="current" :column="3" border size="small" class="status-block">
        <el-descriptions-item label="配置来源">
          <el-tag :type="current.source === 'db' ? 'success' : 'info'" size="small">
            {{ current.source === 'db' ? '数据库配置' : '环境变量配置' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="API 密钥">
          <span v-if="current.hasApiKey" class="mono">{{ current.apiKeyMasked }}</span>
          <el-tag v-else type="warning" size="small">未配置</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="向量化实现">
          {{ current.embeddingImpl }}
        </el-descriptions-item>
        <el-descriptions-item label="最近修改" v-if="current.updatedBy">
          {{ current.updatedBy }} · {{ current.updatedAt?.replace('T', ' ') }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="current && !current.hasApiKey" type="warning" :closable="false" show-icon
        title="未配置 API Key，AI 写作辅助与站内问答不可用（其余功能不受影响），检索使用本地特征哈希向量。" />

      <el-form :model="form" label-width="120px" class="config-form">
        <el-form-item label="服务商预设">
          <el-select v-model="vendor" placeholder="选择预设自动填充，或选自定义" @change="applyPreset" clearable>
            <el-option v-for="p in VENDOR_PRESETS" :key="p.label" :label="p.label" :value="p.label" />
            <el-option label="自定义" value="" />
          </el-select>
        </el-form-item>

        <el-form-item label="base-url" required>
          <el-input v-model="form.baseUrl" placeholder="https://open.bigmodel.cn/api/paas/v4" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password
            :placeholder="current && current.hasApiKey ? `当前：${current.apiKeyMasked}（不输入则保持不变）` : '填写 API Key（不输入则不配置）'" />
          <el-checkbox v-model="clearKey" style="margin-top: 6px;">清除已保存的密钥</el-checkbox>
        </el-form-item>

        <el-form-item label="模型名" required>
          <el-input v-model="form.model" placeholder="glm-4-flash" />
        </el-form-item>

        <el-form-item label="采样温度">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>

        <el-form-item label="超时时间（秒）">
          <el-input-number v-model="form.timeoutSeconds" :min="5" :max="300" />
        </el-form-item>

        <el-form-item label="向量化方式">
          <el-radio-group v-model="form.embeddingProvider">
            <el-radio value="auto">auto（有 Key 用远程，否则本地）</el-radio>
            <el-radio value="local">local（本地特征哈希）</el-radio>
            <el-radio value="remote">remote（远程 Embedding）</el-radio>
          </el-radio-group>
          <div class="field-tip">
            切换向量化方式会自动重建全站向量索引；本地为 {{ form.localDim }} 维特征哈希，远程需接口支持 /embeddings。
          </div>
        </el-form-item>

        <el-form-item label="远程向量模型" v-if="form.embeddingProvider !== 'local'">
          <el-input v-model="form.embeddingModel" placeholder="embedding-3" />
        </el-form-item>

        <el-form-item label="本地向量维度" v-if="form.embeddingProvider !== 'remote'">
          <el-input-number v-model="form.localDim" :min="64" :max="4096" :step="64" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">保存配置</el-button>
          <el-button :icon="Connection" :loading="testing" @click="doTest">测试连接</el-button>
          <el-button :icon="RefreshLeft" @click="doReset">恢复默认</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon
        title="配置优先级：数据库（本页保存）> 环境变量（application.yml / AI_*）> 代码默认值。密钥仅在服务端流转，接口只返回脱敏值。" />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.status-block {
  margin-bottom: 16px;
}
.config-form {
  max-width: 640px;
  margin-top: 16px;
}
.field-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.mono {
  font-family: var(--font-mono, monospace);
}
</style>

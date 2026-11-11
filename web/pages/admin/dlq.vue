<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api } from '~/composables/useApi'

interface DlqMessage { body: string; routingKey: string; redelivered: boolean; death: Record<string, string> }

const count = ref(0)
const messages = ref<DlqMessage[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await $api<{ count: number; messages: DlqMessage[] }>('GET', '/admin/mail/dlq?limit=50')
    count.value = data.count
    messages.value = data.messages
  } catch (e) { ElMessage.error((e as Error).message) } finally { loading.value = false }
}
onMounted(load)

async function republish() {
  await ElMessageBox.confirm('将 DLQ 消息按原路由键重投回业务队列？', '重投确认')
  try {
    const r = await $api<{ republished: number }>('POST', '/admin/mail/dlq/republish', { max: 100 })
    ElMessage.success(`已重投 ${r.republished} 条`)
    await load()
  } catch (e) { ElMessage.error((e as Error).message) }
}
</script>

<template>
  <div>
    <div class="head">
      <h2>邮件死信队列</h2>
      <div style="display:flex;gap:10px;align-items:center">
        <el-tag :type="count ? 'danger' : 'success'" size="large">积压 {{ count }}</el-tag>
        <el-button :disabled="!count" type="warning" @click="republish">全部重投</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>
    <el-table :data="messages" v-loading="loading">
      <el-table-column label="死因" width="120">
        <template #default="{ row }"><el-tag size="small">{{ row.death?.reason || '—' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="来源队列" width="180">
        <template #default="{ row }"><span class="muted">{{ row.death?.queue || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="死信时间" width="180">
        <template #default="{ row }"><span class="muted">{{ (row.death?.time || '').slice(0, 19).replace('T', ' ') }}</span></template>
      </el-table-column>
      <el-table-column label="消息体" min-width="320">
        <template #default="{ row }"><code class="body">{{ row.body.slice(0, 160) }}</code></template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !messages.length" description="队列干净，无死信" />
    <p class="muted">重投后仍失败的消息会被框架兜底丢弃（防循环，服务端日志留痕）。</p>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head h2 { margin: 0; }
.body { font-size: .82em; color: #c4b5fd; word-break: break-all; }
</style>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'

interface LogRow { ts: string; level: string; logger: string; message: string; traceId: string }

const traceId = ref('')
const date = ref('')
const rows = ref<LogRow[]>([])
const loading = ref(false)

async function search() {
  if (!traceId.value.trim()) { ElMessage.warning('输入 traceId'); return }
  loading.value = true
  try {
    const q = `/admin/logs?traceId=${encodeURIComponent(traceId.value.trim())}${date.value ? '&date=' + date.value : ''}`
    rows.value = await $api<LogRow[]>('GET', q)
    if (!rows.value.length) ElMessage.info('该 traceId 无日志')
  } catch (e) { ElMessage.error((e as Error).message) } finally { loading.value = false }
}

function levelTag(level: string) {
  return level.includes('ERROR') ? 'danger' : level.includes('WARN') ? 'warning' : 'info'
}
</script>

<template>
  <div>
    <h2>日志查询（traceId 全链路）</h2>
    <div style="display:flex;gap:8px;margin-bottom:14px">
      <input v-model="traceId" placeholder="粘贴响应头 X-Trace-Id 或日志中的 traceId" style="flex:1" @keyup.enter="search">
      <input v-model="date" type="date" style="width:170px" title="留空查今天">
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
    </div>
    <div class="card" v-if="rows.length">
      <div v-for="(r, i) in rows" :key="i" class="log-line">
        <span class="muted">{{ r.ts }}</span>
        <el-tag size="small" :type="levelTag(r.level)">{{ r.level }}</el-tag>
        <code>{{ r.logger }}</code>
        <div class="msg">{{ r.message }}</div>
      </div>
    </div>
    <p class="muted">traceId 贯穿一次请求的全部日志；Nginx 响应头 X-Trace-Id 与服务端日志行一致。</p>
  </div>
</template>

<style scoped>
.log-line { padding: 8px 0; border-bottom: 1px dashed var(--line); display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
.log-line code { color: var(--ink-faint); font-size: .82em; }
.msg { width: 100%; color: #d6d9de; font-size: .9em; white-space: pre-wrap; }
</style>

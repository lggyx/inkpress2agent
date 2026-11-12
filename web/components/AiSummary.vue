<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api, accessToken } from '~/composables/useApi'

// 文章 AI 总结卡：按需生成（/ai/summary），localStorage 按 articleId 缓存避免重复花费
const props = defineProps<{ articleId: number; title: string; contentHtml: string }>()

const summary = ref('')
const tags = ref<string[]>([])
const busy = ref(false)
const asked = ref(false)

const CACHE_KEY = `inkpress.ai-summary.${props.articleId}`

function stripHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim()
}

onMounted(() => {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as { summary: string; tags: string[] }
      summary.value = saved.summary || ''
      tags.value = saved.tags || []
      asked.value = !!summary.value
    }
  } catch { /* 缓存坏了就当没有 */ }
})

async function generate() {
  if (!accessToken()) { navigateTo('/login'); return }
  const content = stripHtml(props.contentHtml).slice(0, 4000)
  if (!content) { ElMessage.warning('正文为空'); return }
  busy.value = true
  try {
    const r = await $api<{ seoDescription: string; geoSummary: string; tags: string[] }>(
      'POST', '/ai/summary', { content: `文章《${props.title}》：\n\n${content}` })
    summary.value = r.geoSummary || r.seoDescription || ''
    tags.value = r.tags || []
    asked.value = true
    localStorage.setItem(CACHE_KEY, JSON.stringify({ summary: summary.value, tags: tags.value }))
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally { busy.value = false }
}
</script>

<template>
  <section class="card ai-sum">
    <div class="sum-head">
      <span class="sum-badge">AI 总结</span>
      <button class="gen" :disabled="busy" @click="generate">
        {{ busy ? '生成中…' : summary ? '重新生成' : '生成总结' }}
      </button>
    </div>
    <p v-if="summary" class="sum-body">{{ summary }}</p>
    <div v-if="tags.length" class="sum-tags">
      <span v-for="t in tags" :key="t" class="tag-chip">{{ t }}</span>
    </div>
    <p v-else-if="!summary" class="muted sum-hint">
      {{ busy ? 'AI 正在通读全文并提炼要点…' : '一键让 AI 通读全文，生成内容概要与要点标签。' }}
    </p>
    <p v-if="!accessToken()" class="muted" style="margin:6px 0 0;font-size:.85em">
      <NuxtLink to="/login">登录</NuxtLink> 后可用 AI 总结。
    </p>
  </section>
</template>

<style scoped>
.ai-sum { border-color: var(--primary); }
.sum-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.sum-badge {
  background: var(--primary-soft);
  color: var(--primary);
  border-radius: 4px;
  padding: 1px 8px;
  font-size: .8em;
  font-weight: 600;
}
.gen {
  font-size: .82em;
  padding: 4px 12px;
  border-radius: 999px;
}
.sum-body { color: var(--ink); margin: 12px 0 0; line-height: 1.8; white-space: pre-wrap; }
.sum-tags { margin-top: 10px; }
.sum-hint { margin: 10px 0 0; font-size: .88em; }
</style>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $pub, accessToken } from '~/composables/useApi'
import { streamAi } from '~/composables/useAiStream'

interface SearchHit { articleId: number; title: string; highlight: string }
interface SearchData { total: number; mode: string; degraded: boolean; records: SearchHit[] }

const keyword = ref('')
const searched = ref(false)
const searching = ref(false)
const hits = ref<SearchHit[]>([])
const mode = ref('')
const degraded = ref(false)

const aiAnswer = ref('')
const aiStreaming = ref(false)
const aiAsked = ref(false)

useSeoMeta({ title: 'AI 检索 - InkPress' })

async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  searching.value = true
  searched.value = true
  aiAnswer.value = ''
  aiAsked.value = false
  try {
    const d = await $pub<SearchData>(`/search?keyword=${encodeURIComponent(kw)}`)
    hits.value = (d.records || []).map((r) => ({
      articleId: r.articleId,
      title: r.title,
      highlight: r.highlight || '',
    }))
    mode.value = d.mode || 'hybrid'
    degraded.value = !!d.degraded
    await askAi(kw)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

/** RAG：检索片段拼入 AI 搜索模板，流式回答（需登录，走 StepFun 文本模型） */
async function askAi(kw: string) {
  if (!accessToken()) return
  if (!hits.value.length) return
  const context = hits.value
    .map((h, i) => `${i + 1}. 《${h.title}》 ${h.highlight}`)
    .join('\n')
  const userContent = `用户问题：${kw}\n\n${context}`
  aiStreaming.value = true
  aiAsked.value = true
  try {
    await streamAi('search', userContent, (t) => { aiAnswer.value += t })
  } catch (e) {
    ElMessage.error('AI 回答失败：' + (e as Error).message)
  } finally {
    aiStreaming.value = false
  }
}

function fmtHl(h: string): string {
  // 服务端高亮带 <em> 标签，这里直接按 HTML 渲染片段（内容来自站内文章，已过敏感词审核）
  return h
}
</script>

<template>
  <div class="search-page">
    <div class="search-head">
      <h1 class="grad-title">AI 检索</h1>
      <p class="muted">关键词 + 语义混合检索；登录后可由 AI 基于站内内容直接回答。</p>
    </div>

    <div class="search-bar">
      <input
        v-model="keyword"
        placeholder="输入关键词，回车检索…"
        @keyup.enter="doSearch"
      >
      <button class="primary" :disabled="searching" @click="doSearch">检索</button>
    </div>

    <div v-if="aiAsked" class="card ai-card">
      <div class="ai-head">
        <span class="ai-badge">AI 回答</span>
        <span v-if="aiStreaming" class="muted">正在生成…</span>
      </div>
      <div class="ai-body">{{ aiAnswer || '…' }}</div>
      <p v-if="!accessToken() " class="muted" style="margin:8px 0 0">
        <NuxtLink to="/login">登录</NuxtLink> 后可获得 AI 生成的回答。
      </p>
    </div>

    <div v-if="searched" class="result-head">
      <span class="muted">检索方式：{{ degraded ? '关键词（降级）' : mode }} · 命中 {{ hits.length }} 篇</span>
    </div>

    <NuxtLink v-for="h in hits" :key="h.articleId" :to="`/article/${h.articleId}`" class="post-row">
      <span class="p-title">
        {{ h.title }}
        <span v-if="h.highlight" class="muted hl" v-html="fmtHl(h.highlight)" />
      </span>
      <span class="p-arrow">›</span>
    </NuxtLink>

    <div v-if="searched && !searching && !hits.length" class="muted" style="text-align:center;padding:30px 0">
      站内暂无相关内容。
    </div>
  </div>
</template>

<style scoped>
.search-page { max-width: 760px; margin: 0 auto; }
.search-head h1 { margin: 10px 0 4px; }
.search-bar { display: flex; gap: 10px; margin: 16px 0 22px; }
.search-bar input { font-size: 1.02em; padding: 11px 16px; }
.ai-card { margin-bottom: 20px; border-color: var(--primary); }
.ai-head { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.ai-badge {
  background: var(--primary-soft);
  color: var(--primary);
  border-radius: 4px;
  padding: 1px 8px;
  font-size: .8em;
}
.ai-body { white-space: pre-wrap; color: var(--ink); min-height: 1.4em; }
.result-head { margin: 18px 0 10px; }
.hl :deep(em) { color: var(--primary); font-style: normal; font-weight: 600; }
.hl { color: var(--ink-soft); font-size: .88em; margin-left: 8px; }
</style>

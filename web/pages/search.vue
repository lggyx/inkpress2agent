<script setup lang="ts">
import { $pub } from '~/composables/useApi'
import DOMPurify from 'dompurify'

interface Hit { id?: number; articleId?: number; title: string; highlight?: string }

const route = useRoute()
const keyword = ref((route.query.keyword as string) || '')
const hits = ref<Hit[]>([])
const searching = ref(false)
const searched = ref(false)

async function run() {
  const kw = keyword.value.trim()
  if (!kw) { hits.value = []; searched.value = false; return }
  searching.value = true
  try {
    const d = await $pub<{ total: number; records: Hit[] }>(`/search?keyword=${encodeURIComponent(kw)}`)
    hits.value = d.records || []
    searched.value = true
  } catch {
    hits.value = []
    searched.value = true
  } finally {
    searching.value = false
  }
}

// URL 为唯一事实源：/search?keyword=xxx 直达与 NavSearch 跳转共用
watch(() => route.query.keyword, (v) => {
  const kw = (v as string) || ''
  if (kw !== keyword.value) keyword.value = kw
  if (kw) run()
}, { immediate: true })

/** ES highlight 片段含 <em> 标记且源自正文——v-html 前经 DOMPurify 白名单净化 */
function safeHighlight(h?: string): string {
  if (!h) return ''
  return DOMPurify.sanitize(h, { ALLOWED_TAGS: ['em', 'mark'], ALLOWED_ATTR: [] })
}

useSeoMeta({ title: () => (keyword.value.trim() ? `${keyword.value.trim()} - 搜索 - InkPress` : '搜索 - InkPress') })
</script>

<template>
  <div class="page">
    <section class="section-row">
      <h2 class="section-title">检索</h2>
      <div>
        <div class="s-bar">
          <input
            v-model="keyword"
            class="s-input"
            placeholder="输入关键词，回车检索（BM25 + 向量混合排序）"
            @keyup.enter="run"
          />
          <button class="s-go" :disabled="searching" @click="run">{{ searching ? '检索中…' : '检索' }}</button>
        </div>

        <div v-if="searching" class="muted s-hint">检索中…</div>
        <div v-else-if="keyword.trim() && searched && !hits.length" class="muted s-hint">无匹配内容。</div>

        <div v-for="h in hits" :key="h.id ?? h.articleId" class="post-row s-hit">
          <NuxtLink :to="`/article/${h.id ?? h.articleId}`" class="s-title-link">
            <span class="p-title">{{ h.title }}</span>
            <span class="p-arrow">›</span>
          </NuxtLink>
          <div class="s-hl muted" v-html="safeHighlight(h.highlight)" />
        </div>

        <div v-if="!keyword.trim()" class="muted s-hint">
          输入关键词回车，或使用导航栏的即时搜索下拉。检索按 BM25 关键词与语义向量混合排序（RRF）。
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.s-bar { display: flex; gap: 8px; margin-bottom: 18px; }
.s-input {
  flex: 1;
  background: var(--card, transparent);
  border: 1px solid var(--line, #333);
  border-radius: 10px;
  padding: 10px 14px;
  color: var(--ink);
  font-size: 14px;
  outline: none;
}
.s-input:focus { border-color: var(--primary, #9ef01a); }
.s-go {
  border: 1px solid var(--line, #333);
  background: transparent;
  color: var(--ink-soft, #aaa);
  border-radius: 10px;
  padding: 0 18px;
  cursor: pointer;
}
.s-go:hover { color: var(--primary, #9ef01a); border-color: var(--primary, #9ef01a); }
.s-hint { margin: 14px 0; }
.s-hit { display: block; }
.s-title-link { display: flex; align-items: center; gap: 8px; }
.s-hl { margin: 4px 0 14px; font-size: 13px; line-height: 1.7; max-width: 72ch; }
.s-hl :deep(em) { color: var(--primary, #9ef01a); font-style: normal; font-weight: 600; }
</style>

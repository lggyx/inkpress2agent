<script setup lang="ts">
import { $pub } from '~/composables/useApi'

// 导航即时搜索：玻璃浮层 + 防抖即时结果；替代独立检索页
interface SearchHit { articleId: number; title: string; highlight: string }

const open = ref(false)
const keyword = ref('')
const hits = ref<SearchHit[]>([])
const searching = ref(false)
const inputEl = ref<HTMLInputElement | null>(null)
let timer: ReturnType<typeof setTimeout> | null = null

function toggle() {
  open.value = !open.value
  if (open.value) nextTick(() => inputEl.value?.focus())
  else reset()
}

function reset() {
  keyword.value = ''
  hits.value = []
  searching.value = false
  if (timer) { clearTimeout(timer); timer = null }
}

function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) { hits.value = []; return }
  searching.value = true
  $pub<{ total: number; records: { id?: number; articleId?: number; title: string; highlight?: string }[] }>(
    `/search?keyword=${encodeURIComponent(kw)}`,
  ).then((d) => {
    // 后端字段为 id（历史版本曾按 articleId 映射导致 /article/undefined）
    hits.value = (d.records || []).map((r) => ({
      articleId: Number(r.id ?? r.articleId),
      title: r.title,
      highlight: r.highlight || '',
    }))
  }).catch(() => { hits.value = [] }).finally(() => { searching.value = false })
}

watch(keyword, (v) => {
  if (timer) clearTimeout(timer)
  const kw = v.trim()
  if (!kw) { hits.value = []; searching.value = false; return }
  timer = setTimeout(doSearch, 300)
})

function goFirst() {
  if (hits.value.length) {
    const id = hits.value[0].articleId
    open.value = false
    reset()
    navigateTo(`/article/${id}`)
  }
}

function onDocClick(e: MouseEvent) {
  if (!open.value) return
  if (!(e.target as HTMLElement).closest('.nav-search')) { open.value = false; reset() }
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="nav-search">
    <button class="ns-trigger" :class="{ on: open }" @click="toggle">检索</button>
    <Transition name="ns-pop">
      <div v-if="open" class="ns-panel" @keydown.esc="open = false; reset()">
        <input
          ref="inputEl"
          v-model="keyword"
          class="ns-input"
          placeholder="搜文章，即时出结果…"
          @keydown.enter="goFirst"
        >
        <div class="ns-results">
          <NuxtLink
            v-for="h in hits"
            :key="h.articleId"
            :to="`/article/${h.articleId}`"
            class="ns-row"
            @click="open = false; reset()"
          >
            <span class="ns-title">{{ h.title }}</span>
            <!-- 服务端高亮为站内内容的 <em> 片段，直接渲染 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <span class="ns-hl" v-html="h.highlight" />
          </NuxtLink>
          <div v-if="searching" class="ns-empty">检索中…</div>
          <div v-else-if="keyword.trim() && !hits.length" class="ns-empty">无匹配内容</div>
          <div v-else-if="!keyword.trim()" class="ns-empty">输入关键词即时检索；回车进第一篇</div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.nav-search { position: relative; display: flex; align-items: center; }
.ns-trigger {
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  padding: 4px 2px;
  transition: color .18s ease;
}
.ns-trigger:hover, .ns-trigger.on { color: var(--primary); }

.ns-panel {
  position: absolute;
  top: calc(100% + 14px);
  left: 50%;
  transform: translateX(-50%);
  width: 460px;
  max-width: 78vw;
  z-index: 60;
  background: var(--spop, rgba(255, 255, 255, .9));
  border: 1px solid var(--sline, var(--line));
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .35);
  backdrop-filter: blur(30px) saturate(150%);
  -webkit-backdrop-filter: blur(30px) saturate(150%);
  padding: 10px;
}
.ns-pop-enter-active, .ns-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.ns-pop-enter-from, .ns-pop-leave-to { opacity: 0; transform: translateX(-50%) translateY(-6px); }

.ns-input { margin-bottom: 8px; }
.ns-results { max-height: 46vh; overflow-y: auto; }
.ns-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 9px 12px;
  border-radius: 10px;
  transition: background .15s ease;
}
.ns-row:hover { background: var(--primary-soft); text-decoration: none; }
.ns-title { color: var(--ink); font-weight: 600; font-size: .9em; }
.ns-hl { color: var(--ink-faint); font-size: .8em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ns-hl :deep(em) { color: var(--primary); font-style: normal; font-weight: 600; }
.ns-empty { padding: 14px 12px; color: var(--ink-faint); font-size: .84em; }

@media (max-width: 720px) {
  .ns-panel {
    position: fixed;
    left: 12px;
    right: 12px;
    top: 64px;
    width: auto;
    max-width: none;
    transform: none;
  }
  .ns-pop-enter-from, .ns-pop-leave-to { transform: translateY(-6px); }
}
</style>

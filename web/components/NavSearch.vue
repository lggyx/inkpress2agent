<script setup lang="ts">
import { $pub } from '~/composables/useApi'

// 页头即时搜索框：桌面端内联输入（聚焦展宽+下方结果浮层），移动端放大镜展开全宽搜索条
interface SearchHit { articleId: number; title: string; highlight: string }

const open = ref(false)
const expanded = ref(false) // 移动端展开态
const keyword = ref('')
const hits = ref<SearchHit[]>([])
const searching = ref(false)
const inputEl = ref<HTMLInputElement | null>(null)
const mInputEl = ref<HTMLInputElement | null>(null)
let timer: ReturnType<typeof setTimeout> | null = null

function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) { hits.value = []; searching.value = false; return }
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

function reset() {
  keyword.value = ''
  hits.value = []
  searching.value = false
  if (timer) { clearTimeout(timer); timer = null }
}

function close() {
  open.value = false
  expanded.value = false
  reset()
}

function pick() {
  close()
}

function goFirst() {
  if (hits.value.length) {
    const id = hits.value[0].articleId
    close()
    navigateTo(`/article/${id}`)
  }
}

function onDocClick(e: MouseEvent) {
  if (!(e.target as HTMLElement).closest('.nav-search')) close()
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  if (timer) clearTimeout(timer)
})

function escHandler(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}
onMounted(() => document.addEventListener('keydown', escHandler))
onBeforeUnmount(() => document.removeEventListener('keydown', escHandler))
</script>

<template>
  <div class="nav-search">
    <!-- 桌面端：内联搜索框 -->
    <div class="ns-box" :class="{ open }" @click.stop>
      <svg class="ns-mag" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
        <circle cx="11" cy="11" r="6.5" />
        <path d="M16 16l4.5 4.5" />
      </svg>
      <input
        ref="inputEl"
        v-model="keyword"
        class="ns-input"
        placeholder="搜索文章…"
        @focus="open = true"
        @keydown.enter="goFirst"
      >
      <button v-if="keyword" class="ns-clear" @click="reset()">×</button>
      <Transition name="ns-pop">
        <div v-if="open && (keyword.trim() || searching)" class="ns-panel">
          <NuxtLink
            v-for="h in hits"
            :key="h.articleId"
            :to="`/article/${h.articleId}`"
            class="ns-row"
            @click="pick()"
          >
            <span class="ns-title">{{ h.title }}</span>
            <!-- 服务端高亮为站内内容的 <em> 片段，直接渲染 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <span class="ns-hl" v-html="h.highlight" />
          </NuxtLink>
          <div v-if="searching" class="ns-empty">检索中…</div>
          <div v-else-if="keyword.trim() && !hits.length" class="ns-empty">无匹配内容</div>
        </div>
      </Transition>
    </div>

    <!-- 移动端：放大镜展开全宽搜索条 -->
    <button class="ns-m-btn" aria-label="搜索" @click.stop="expanded = true; nextTick(() => mInputEl?.focus())">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
        <circle cx="11" cy="11" r="6.5" />
        <path d="M16 16l4.5 4.5" />
      </svg>
    </button>
    <Transition name="ns-slide">
      <div v-if="expanded" class="ns-mobile" @click.stop>
        <div class="ns-m-bar">
          <svg class="ns-mag" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <circle cx="11" cy="11" r="6.5" />
            <path d="M16 16l4.5 4.5" />
          </svg>
          <input
            ref="mInputEl"
            v-model="keyword"
            placeholder="搜索文章…"
            @keydown.enter="goFirst"
          >
          <button class="ns-m-close" @click="close()">×</button>
        </div>
        <div class="ns-results">
          <NuxtLink
            v-for="h in hits"
            :key="h.articleId"
            :to="`/article/${h.articleId}`"
            class="ns-row"
            @click="pick()"
          >
            <span class="ns-title">{{ h.title }}</span>
            <!-- eslint-disable-next-line vue/no-v-html -->
            <span class="ns-hl" v-html="h.highlight" />
          </NuxtLink>
          <div v-if="searching" class="ns-empty">检索中…</div>
          <div v-else-if="keyword.trim() && !hits.length" class="ns-empty">无匹配内容</div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.nav-search { position: relative; display: flex; align-items: center; }

/* ===== 桌面端搜索框 ===== */
.ns-box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card-soft);
  padding: 4px 10px;
  width: 158px;
  transition: width .25s cubic-bezier(.2, .7, .3, 1), border-color .2s ease, box-shadow .2s ease;
}
.ns-box.open {
  width: 250px;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-soft);
}
.ns-mag { width: 14px; height: 14px; color: var(--ink-faint); flex-shrink: 0; }
.ns-input {
  border: 0 !important;
  background: transparent !important;
  padding: 2px 0 !important;
  font-size: .86em;
  box-shadow: none !important;
  outline: none !important;
  width: 100%;
}
.ns-input:focus { outline: none !important; box-shadow: none !important; border: 0 !important; }
.ns-clear {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  font-size: 1em;
  padding: 0 2px;
  line-height: 1;
}
.ns-clear:hover { color: var(--ink); }

/* 结果浮层 */
.ns-panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 340px;
  max-width: 78vw;
  z-index: 60;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .35);
  backdrop-filter: blur(30px) saturate(150%);
  -webkit-backdrop-filter: blur(30px) saturate(150%);
  padding: 8px;
}
.ns-pop-enter-active, .ns-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.ns-pop-enter-from, .ns-pop-leave-to { opacity: 0; transform: translateY(-6px); }

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

/* ===== 移动端 ===== */
.ns-m-btn {
  display: none;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  padding: 2px;
  transition: color .18s ease;
}
.ns-m-btn:hover { color: var(--primary); }
.ns-m-btn svg { width: 19px; height: 19px; }
.ns-mobile { display: none; }

@media (max-width: 720px) {
  .ns-box { display: none; }
  .ns-m-btn { display: grid; place-items: center; }
  .ns-mobile {
    display: flex;
    flex-direction: column;
    position: fixed;
    left: 10px;
    right: 10px;
    top: 58px;
    z-index: 70;
    background: var(--card);
    border: 1px solid var(--line);
    border-radius: 14px;
    box-shadow: 0 24px 70px rgba(0, 0, 0, .35);
    backdrop-filter: blur(30px) saturate(150%);
    -webkit-backdrop-filter: blur(30px) saturate(150%);
    padding: 10px;
  }
  .ns-slide-enter-active, .ns-slide-leave-active { transition: opacity .18s ease, transform .18s ease; }
  .ns-slide-enter-from, .ns-slide-leave-to { opacity: 0; transform: translateY(-8px); }
  .ns-m-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    border: 1px solid var(--line);
    border-radius: 999px;
    padding: 8px 14px;
    margin-bottom: 8px;
  }
  .ns-m-bar .ns-mag { width: 16px; height: 16px; }
  .ns-m-bar input {
    flex: 1;
    border: 0 !important;
    background: transparent !important;
    padding: 0 !important;
    font-size: .95em;
    box-shadow: none !important;
  }
  .ns-m-close {
    border: 0;
    background: transparent;
    color: var(--ink-faint);
    font-size: 1.2em;
    line-height: 1;
    padding: 0 2px;
  }
  .ns-results { max-height: 56vh; overflow-y: auto; }
}
</style>

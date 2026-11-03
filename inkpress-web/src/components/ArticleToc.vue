<script setup>
/**
 * 文章目录大纲（TOC）
 * 从渲染后的文章 DOM 提取 h1~h3，生成锚点目录；
 * 宽屏（>=1280px）右侧悬浮，窄屏顶部折叠；滚动时高亮当前章节。
 */
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  /** 文章正文容器 ref（渲染 v-html 的节点） */
  contentEl: {
    type: Object,
    default: null
  }
})

const MOBILE_BREAKPOINT = 1280

const headings = ref([])
const activeId = ref('')
const expanded = ref(false)
const isMobile = ref(false)
/** 点击跳转后短暂锁定高亮，避免平滑滚动途中 scrollspy 抢焦点 */
const lockUntil = ref(0)

const hasToc = computed(() => headings.value.length >= 2)

let scrollRaf = null

function checkViewport() {
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
}

/** 从渲染后的 DOM 提取标题并生成锚点（文章切换时由父组件调用） */
function refresh() {
  if (!props.contentEl) {
    headings.value = []
    return
  }
  const nodes = props.contentEl.querySelectorAll('h1, h2, h3')
  const items = []
  nodes.forEach((node, index) => {
    const id = `toc-${index}`
    node.id = id
    items.push({
      id,
      text: (node.textContent || '').trim(),
      level: Number(node.tagName.substring(1))
    })
  })
  headings.value = items
  updateActive()
}

function scrollTo(id) {
  const target = document.getElementById(id)
  if (!target) {
    return
  }
  const top = target.getBoundingClientRect().top + window.scrollY - 76
  window.scrollTo({ top, behavior: 'smooth' })
  activeId.value = id
  // 锁定 600ms，滚动经过的中间章节不抢高亮
  lockUntil.value = Date.now() + 600
  expanded.value = false
}

function updateActive() {
  if (!headings.value.length) {
    return
  }
  if (Date.now() < lockUntil.value) {
    return
  }
  let current = headings.value[0].id
  for (const item of headings.value) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= 90) {
      current = item.id
    }
  }
  activeId.value = current
}

function onScroll() {
  if (scrollRaf) {
    return
  }
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = null
    updateActive()
  })
}

watch(
  () => props.contentEl,
  async el => {
    if (el) {
      await nextTick()
      refresh()
    }
  },
  { immediate: true }
)

onMounted(() => {
  checkViewport()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', checkViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', checkViewport)
  if (scrollRaf) {
    cancelAnimationFrame(scrollRaf)
  }
})

defineExpose({ refresh })
</script>

<template>
  <nav v-if="hasToc" class="toc" :class="{ 'is-mobile': isMobile, 'is-expanded': expanded }">
    <div class="toc-header" @click="isMobile && (expanded = !expanded)">
      <span class="toc-title mono">// 目录大纲</span>
      <span v-if="isMobile" class="toc-toggle mono">{{ expanded ? '[-]' : '[+]' }}</span>
    </div>
    <ol class="toc-list" v-show="!isMobile || expanded">
      <li
        v-for="item in headings"
        :key="item.id"
        class="toc-item"
        :class="['level-' + item.level, { active: item.id === activeId }]"
        @click="scrollTo(item.id)"
      >
        <span class="toc-text">{{ item.text }}</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.toc {
  font-size: 13px;
}

.toc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
  user-select: none;
}

.toc-title {
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--text-dim);
  font-weight: 700;
}

.toc-toggle {
  font-size: 11px;
  color: var(--acid);
}

.toc-list {
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
}

.toc-item {
  padding: 5px 0 5px 12px;
  color: var(--text-dim);
  cursor: pointer;
  line-height: 1.5;
  border-left: 2px solid var(--line);
  transition: color 0.15s, border-color 0.15s;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toc-item:hover {
  color: var(--text);
}

.toc-item.active {
  color: var(--acid);
  border-left-color: var(--acid);
  font-weight: 600;
}

.toc-item.level-2 {
  padding-left: 24px;
}

.toc-item.level-3 {
  padding-left: 36px;
  font-size: 12px;
}

/* 宽屏：右侧悬浮，与 detail-layout 断点保持一致 */
@media (min-width: 1280px) {
  .toc {
    position: sticky;
    top: 88px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }
}

/* 窄屏：顶部折叠卡片 */
@media (max-width: 1279px) {
  .toc {
    border: 1px solid var(--line);
    background: var(--surface);
    padding: 12px 16px;
  }
  .toc:not(.is-expanded) .toc-list {
    display: none;
  }
}
</style>

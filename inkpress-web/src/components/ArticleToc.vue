<script setup>
/**
 * 文章目录大纲（TOC）
 * 从渲染后的文章 DOM 提取 h1~h3，生成锚点目录；
 * 宽屏右侧悬浮，窄屏顶部折叠；滚动时高亮当前章节。
 */
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  /** 文章正文容器 ref（渲染 v-html 的节点） */
  contentEl: {
    type: Object,
    default: null
  }
})

const headings = ref([])
const activeId = ref('')
const expanded = ref(false)
const isMobile = ref(window.innerWidth < 1280)

const hasToc = computed(() => headings.value.length >= 2)

function onResize() {
  isMobile.value = window.innerWidth < 1280
}

/** 从渲染后的 DOM 提取标题并生成锚点（文章切换时由父组件调用） */
function refresh() {
  if (!props.contentEl) {
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
}

function scrollTo(id) {
  const target = document.getElementById(id)
  if (!target) {
    return
  }
  const top = target.getBoundingClientRect().top + window.scrollY - 76
  window.scrollTo({ top, behavior: 'smooth' })
  activeId.value = id
  expanded.value = false
}

function onScroll() {
  if (!headings.value.length) {
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
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
})

defineExpose({ refresh })
</script>

<template>
  <nav v-if="hasToc" class="toc" :class="{ 'is-expanded': expanded }">
    <div class="toc-header" @click="expanded = !expanded">
      <span class="toc-title mono">// 目录大纲</span>
      <span class="toc-toggle mono">{{ expanded ? '[-]' : '[+]' }}</span>
    </div>
    <ol class="toc-list" v-show="expanded || !isMobile">
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

@media (min-width: 1280px) {
  .toc {
    position: sticky;
    top: 88px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }
  .toc-toggle {
    display: none;
  }
}

@media (max-width: 1279px) {
  .toc {
    margin-bottom: 24px;
    border: 1px solid var(--line);
    background: var(--surface);
    padding: 12px 16px;
  }
  .toc:not(.is-expanded) .toc-list {
    display: none;
  }
}
</style>

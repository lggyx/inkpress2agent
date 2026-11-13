<script setup lang="ts">
import { $pub } from '~/composables/useApi'

interface Tag { id: number; name: string; createdAt?: string }
interface ArticleRow { id: number; title: string; tagId?: number; publishedAt: string }

const route = useRoute()
const tags = ref<Tag[]>([])
const selectedId = ref<number | null>(null)
const articles = ref<ArticleRow[]>([])
const loading = ref(false)

onMounted(async () => {
  try {
    tags.value = await $pub<Tag[]>('/tags')
  } catch { tags.value = [] }
  // 支持 /tags?tag=名字 直达（文章页标签 chip 跳转入口）
  const name = (route.query.tag as string) || ''
  if (name) {
    const hit = tags.value.find(t => t.name === name)
    if (hit) {
      selectedId.value = hit.id
      await loadArticles()
    }
  }
})

async function loadArticles() {
  if (selectedId.value == null) { articles.value = []; return }
  loading.value = true
  try {
    const d = await $pub<{ records: ArticleRow[] }>(`/articles?page=1&size=50&tagId=${selectedId.value}`)
    articles.value = d.records || []
  } catch {
    articles.value = []
  } finally {
    loading.value = false
  }
}

async function pick(id: number) {
  selectedId.value = selectedId.value === id ? null : id
  if (selectedId.value != null) {
    navigateTo({ path: '/tags', query: { tag: tags.value.find(t => t.id === id)?.name } })
  }
  await loadArticles()
}

function fmtDate(s: string): string {
  return (s || '').replace('T', ' ').slice(0, 10)
}

useSeoMeta({ title: '标签 - InkPress' })
</script>

<template>
  <div class="page">
    <section class="section-row">
      <h2 class="section-title">标签</h2>
      <div>
        <div v-if="!tags.length" class="muted">还没有标签。</div>
        <div class="tag-cloud">
          <button
            v-for="t in tags"
            :key="t.id"
            class="tag-pill"
            :class="{ on: selectedId === t.id }"
            @click="pick(t.id)"
          >
            {{ t.name }}
          </button>
        </div>

        <div v-if="selectedId != null" class="tag-articles">
          <div v-if="loading" class="muted">加载中…</div>
          <div v-else-if="!articles.length" class="muted">该标签下还没有已发布文章。</div>
          <NuxtLink
            v-for="a in articles"
            :key="a.id"
            :to="`/article/${a.id}`"
            class="post-row"
          >
            <span class="p-date">{{ fmtDate(a.publishedAt) }}</span>
            <span class="p-title">{{ a.title }}</span>
            <span class="p-arrow">›</span>
          </NuxtLink>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tag-cloud { display: flex; flex-wrap: wrap; gap: 10px; }
.tag-pill {
  background: transparent;
  border: 1px solid var(--line, #333);
  border-radius: 999px;
  padding: 6px 16px;
  color: var(--ink-soft, #aaa);
  cursor: pointer;
  font-size: 13.5px;
}
.tag-pill:hover { border-color: var(--primary, #9ef01a); color: var(--primary, #9ef01a); }
.tag-pill.on { border-color: var(--primary, #9ef01a); color: var(--primary, #9ef01a); }
.tag-articles { margin-top: 20px; }
</style>

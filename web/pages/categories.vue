<script setup lang="ts">
import { $pub } from '~/composables/useApi'

interface Category { id: number; name: string; description?: string }
interface ArticleRow { id: number; title: string; categoryId?: number; categoryName?: string; publishedAt: string }

const route = useRoute()
const categories = ref<Category[]>([])
const selected = ref<number | null>(Number(route.query.selected) || null)
const articles = ref<ArticleRow[]>([])
const loading = ref(false)

onMounted(async () => {
  try {
    categories.value = await $pub<Category[]>('/categories')
  } catch { categories.value = [] }
  if (selected.value != null) await loadArticles()
})

async function loadArticles() {
  if (selected.value == null) { articles.value = []; return }
  loading.value = true
  try {
    const d = await $pub<{ records: ArticleRow[] }>(`/articles?page=1&size=50&categoryId=${selected.value}`)
    articles.value = d.records || []
  } catch {
    articles.value = []
  } finally {
    loading.value = false
  }
}

function pick(id: number) {
  selected.value = selected.value === id ? null : id
  if (selected.value != null) {
    navigateTo({ path: '/categories', query: { selected: String(selected.value) } })
  }
  loadArticles()
}

function fmtDate(s: string): string {
  return (s || '').replace('T', ' ').slice(0, 10)
}

useSeoMeta({ title: '分类 - InkPress' })
</script>

<template>
  <div class="page">
    <section class="section-row">
      <h2 class="section-title">分类</h2>
      <div>
        <div v-if="!categories.length" class="muted">还没有分类。</div>
        <div class="cat-grid">
          <button
            v-for="c in categories"
            :key="c.id"
            class="cat-card"
            :class="{ on: selected === c.id }"
            @click="pick(c.id)"
          >
            <span class="cat-name">{{ c.name }}</span>
            <span v-if="c.description" class="muted cat-desc">{{ c.description }}</span>
          </button>
        </div>

        <div v-if="selected != null" class="cat-articles">
          <div v-if="loading" class="muted">加载中…</div>
          <div v-else-if="!articles.length" class="muted">该分类下还没有已发布文章。</div>
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
.cat-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.cat-card {
  display: flex; flex-direction: column; align-items: flex-start; gap: 4px;
  background: transparent;
  border: 1px solid var(--line, #333);
  border-radius: 12px;
  padding: 12px 16px;
  cursor: pointer;
  color: var(--ink, #eee);
  min-width: 160px;
  text-align: left;
}
.cat-card:hover { border-color: var(--primary, #9ef01a); }
.cat-card.on { border-color: var(--primary, #9ef01a); color: var(--primary, #9ef01a); }
.cat-name { font-weight: 600; }
.cat-desc { font-size: 12px; }
.cat-articles { margin-top: 20px; }
</style>

<script setup lang="ts">
import { $pub } from '~/composables/useApi'

interface ArticleBrief {
  id: number
  title: string
  seoDescription: string
  geoSummary: string
  coverUrl: string
  categoryName: string
  tags: string[]
  viewCount: number
  likeCount: number
  commentCount: number
  pinned: boolean
  publishedAt: string
}

interface ArticlePage { records: ArticleBrief[]; total: number }
interface HotItem { id: number; title: string; viewCount: number }

const route = useRoute()
const page = computed(() => Math.max(1, Number(route.query.page) || 1))

const { data: articles, error } = await useAsyncData(
  `home-${page.value}`,
  () => $pub<ArticlePage>(`/articles?page=${page.value}&size=8`),
)
const { data: hot } = await useAsyncData('home-hot', () => $pub<HotItem[]>('/stats/hot?days=7'))

const total = computed(() => articles.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 8)))

function fmtDate(s: string): string {
  return (s || '').replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="home">
    <div class="main-col">
      <div v-if="error" class="card error-text">文章加载失败：{{ error.message }}</div>
      <div v-else-if="!articles?.records?.length" class="card muted">还没有已发布的文章。</div>
      <template v-else>
        <NuxtLink
          v-for="a in articles.records"
          :key="a.id"
          :to="`/article/${a.id}`"
          class="card article-card"
        >
          <div class="ac-title">
            <span v-if="a.pinned" class="pin">置顶</span>
            {{ a.title }}
          </div>
          <p class="ac-desc">{{ a.seoDescription || a.geoSummary || '（暂无摘要）' }}</p>
          <div class="ac-meta">
            <span class="tag-chip" v-for="t in (a.tags || []).slice(0, 3)" :key="t">{{ t }}</span>
            <span class="muted">{{ fmtDate(a.publishedAt) }}</span>
            <span class="muted">· {{ a.viewCount }} 阅读</span>
            <span class="muted">· {{ a.likeCount }} 赞</span>
            <span class="muted">· {{ a.commentCount }} 评论</span>
          </div>
        </NuxtLink>
        <div class="pager" v-if="totalPages > 1">
          <NuxtLink v-if="page > 1" :to="page === 2 ? '/' : `/?page=${page - 1}`">上一页</NuxtLink>
          <span class="muted">{{ page }} / {{ totalPages }}</span>
          <NuxtLink v-if="page < totalPages" :to="`/?page=${page + 1}`">下一页</NuxtLink>
        </div>
      </template>
    </div>

    <aside class="side-col">
      <div class="card">
        <h3 class="side-title">本周热门</h3>
        <ol v-if="hot?.length" class="hot-list">
          <li v-for="(h, i) in hot" :key="h.id">
            <NuxtLink :to="`/article/${h.id}`"><span class="rank">{{ i + 1 }}</span>{{ h.title }}</NuxtLink>
            <div class="muted">{{ h.viewCount }} 阅读</div>
          </li>
        </ol>
        <p v-else class="muted">暂无数据</p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.home { display: grid; grid-template-columns: 1fr 300px; gap: 24px; }
@media (max-width: 860px) { .home { grid-template-columns: 1fr; } }
.article-card { display: block; margin-bottom: 16px; color: var(--ink); }
.article-card:hover { text-decoration: none; border-color: var(--primary); }
.ac-title { font-size: 1.15em; font-weight: 600; }
.pin { background: var(--danger); color: #fff; border-radius: 4px; font-size: .75em; padding: 1px 6px; margin-right: 6px; vertical-align: 2px; }
.ac-desc { color: var(--ink-soft); font-size: .95em; margin: 8px 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.ac-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.pager { display: flex; gap: 18px; align-items: center; justify-content: center; margin-top: 20px; }
.side-title { margin: 0 0 12px; font-size: 1em; }
.hot-list { margin: 0; padding-left: 0; list-style: none; }
.hot-list li { margin-bottom: 12px; }
.hot-list a { color: var(--ink); display: block; }
.rank { color: var(--primary); font-weight: 700; margin-right: 8px; }
</style>

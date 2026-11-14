<script setup lang="ts">
import { $pub } from '~/composables/useApi'

interface ArticleBrief {
  id: number
  title: string
  seoDescription: string
  pinned: boolean
  publishedAt: string
}

interface ArticlePage { records: ArticleBrief[]; total: number }
interface HotItem { id: number; title: string; viewCount: number }

const route = useRoute()
const page = computed(() => Math.max(1, Number(route.query.page) || 1))

const { data: articles } = await useAsyncData(
  `home-${page.value}`,
  () => $pub<ArticlePage>(`/articles?page=${page.value}&size=10`),
)
const { data: hot } = await useAsyncData('home-hot', () => $pub<HotItem[]>('/stats/hot?days=7'))

const total = computed(() => articles.value?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 10)))

function fmtDate(s: string): string {
  const d = (s || '').replace('T', ' ').slice(0, 10)
  return d
}
</script>

<template>
  <div>
    <!-- Hero：站名 + 签名 + 胶囊 CTA（Pure 式居中） -->
    <section class="hero">
      <div class="hero-mark grad-title beam">Ink</div>
      <h1 class="hero-name">InkPress</h1>
      <p class="hero-sig muted">从每次写作与反馈中学习，越用越懂你的 AI 博客</p>
      <NuxtLink to="/search" class="hero-cta">
        <span class="dot" /> AI 搜索
      </NuxtLink>
    </section>

    <!-- About：左标题右内容 -->
    <section class="section-row">
      <h2 class="section-title">关于</h2>
      <div>
        <p class="muted" style="margin:0 0 6px">博客 / AI 创作台 / 进化面板</p>
        <p style="margin:0;color:var(--ink-soft)">
          InkPress 是一个会自我进化的 AI 博客：AI 帮你写作、润色与配图，你对结果的每次反馈都会沉淀成写作记忆，
          让下一次输出更贴合你；文章发布后自动可被混合检索、订阅，并针对搜索引擎与 AI 双重优化（GEO/SEO）。
        </p>
        <div style="text-align:right;margin-top:14px">
          <NuxtLink to="/search" class="about-more">
            试试 AI 搜索 <span class="p-arrow">›</span>
          </NuxtLink>
        </div>
      </div>
    </section>

    <!-- Posts：极简描边卡片行 -->
    <section class="section-row">
      <h2 class="section-title">文章</h2>
      <div>
        <div v-if="!articles?.records?.length" class="muted">还没有已发布的文章。</div>
        <NuxtLink
          v-for="a in articles.records"
          :key="a.id"
          :to="`/article/${a.id}`"
          class="post-row"
        >
          <span class="p-date">{{ fmtDate(a.publishedAt) }}</span>
          <span v-if="a.pinned" class="p-pin">Pin</span>
          <span class="p-title">{{ a.title }}</span>
          <span class="p-arrow">›</span>
        </NuxtLink>

        <div class="pager" v-if="totalPages > 1">
          <NuxtLink v-if="page > 1" :to="page === 2 ? '/' : `/?page=${page - 1}`">上一页</NuxtLink>
          <span class="muted mono">{{ page }} / {{ totalPages }}</span>
          <NuxtLink v-if="page < totalPages" :to="`/?page=${page + 1}`">下一页</NuxtLink>
        </div>
        <div class="more-link" v-if="total > 10 && page >= totalPages">
          <span class="muted">共 {{ total }} 篇 · 往上翻或用检索找到它们</span>
        </div>
      </div>
    </section>

    <!-- 本周热门（侧注样式并入 Posts 区块下方，Pure 无侧栏，改为一段轻量列表） -->
    <section class="section-row" v-if="hot?.length">
      <h2 class="section-title">本周热门</h2>
      <div>
        <div v-for="(h, i) in hot" :key="h.id" class="hot-row">
          <NuxtLink :to="`/article/${h.id}`" class="hot-link">
            <span class="p-date mono">{{ String(i + 1).padStart(2, '0') }}</span>
            {{ h.title }}
          </NuxtLink>
          <span class="muted">{{ h.viewCount }} 阅读</span>
        </div>
      </div>
    </section>

    <!-- 能力区块（妙喵式叙事：AI 检索 / AI 写作辅助 / 多端分发） -->
    <section class="section-row">
      <h2 class="section-title">能力</h2>
      <div class="feat-grid">
        <div class="card feat cc cc-cyan">
          <div class="feat-t">AI 检索</div>
          <p class="muted">关键词与语义混合检索，并可由 AI 基于站内内容直接给出回答。</p>
        </div>
        <div class="card feat cc cc-violet">
          <div class="feat-t">AI 写作辅助</div>
          <p class="muted">润色、纠错、续写与起标题；发布前 AI 评审为质量把关。</p>
        </div>
        <div class="card feat cc cc-lime">
          <div class="feat-t">自我进化</div>
          <p class="muted">你的每次反馈都会沉淀成写作记忆，下一次输出更贴合你。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero { text-align: center; padding: 46px 0 30px; }
.hero-mark {
  width: 84px; height: 84px;
  margin: 0 auto 18px;
  border-radius: 50%;
  border: 2px solid transparent;
  background:
    linear-gradient(160deg, var(--card-strong), var(--card-soft)) padding-box,
    linear-gradient(140deg, var(--grad-a), var(--grad-b)) border-box;
  display: flex; align-items: center; justify-content: center;
  font-size: 2em; font-weight: 800;
  color: #fff;
  text-shadow: 0 2px 18px rgba(124, 58, 237, .55);
  box-shadow: 0 0 34px color-mix(in srgb, var(--primary) 30%, transparent), var(--glass-hi);
  backdrop-filter: blur(14px);
}
.hero-name { font-size: 2.1em; font-weight: 800; margin: 0 0 6px; background: var(--grad-text); -webkit-background-clip: text; background-clip: text; color: transparent; letter-spacing: 1px; }
.hero-sig { margin: 0 0 20px; }
.hero-cta {
  display: inline-flex; align-items: center; gap: 8px;
  border: 1px solid color-mix(in srgb, var(--primary) 40%, var(--line));
  border-radius: var(--radius-pill);
  padding: 7px 22px;
  color: var(--ink);
  background: linear-gradient(165deg, var(--card-strong), var(--card-soft));
  backdrop-filter: blur(12px);
  transition: all .2s ease;
  box-shadow: var(--glass-hi);
}
.hero-cta:hover {
  border-color: color-mix(in srgb, var(--primary) 70%, transparent);
  color: var(--primary);
  text-decoration: none;
  box-shadow: 0 0 26px color-mix(in srgb, var(--primary) 28%, transparent);
  transform: translateY(-1px);
}
.hero-cta .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 8px var(--ok); }
.about-more { color: var(--ink-soft); }
.about-more:hover { color: var(--primary); }
.pager { display: flex; gap: 18px; align-items: center; justify-content: center; margin-top: 18px; }
.pager a { color: var(--primary); }
.more-link { text-align: center; margin-top: 12px; }
.hot-row { display: flex; justify-content: space-between; align-items: baseline; padding: 6px 0; border-top: 1px dashed var(--line); }
.hot-link { color: var(--ink); flex: 1; }
.feat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
@media (max-width: 860px) { .feat-grid { grid-template-columns: 1fr; } }
.feat-t { font-weight: 650; margin-bottom: 6px; color: var(--cc, var(--ink)); }
.feat p { margin: 0; }
</style>

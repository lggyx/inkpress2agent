<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { frontPage, hot } from '@/api/article'
import { list as listCategories } from '@/api/category'
import { list as listTags } from '@/api/tag'
import DecryptedText from '@/components/DecryptedText.vue'
import { formatDate } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const articles = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const hotList = ref([])
const categories = ref([])
const tags = ref([])
/** 图片加载失败的文章 id（外链图失效时回退编号占位） */
const failedMedia = reactive({})

const activeCategoryId = computed(() => (route.query.categoryId ? Number(route.query.categoryId) : null))
const activeTagId = computed(() => (route.query.tagId ? Number(route.query.tagId) : null))
const keyword = computed(() => route.query.keyword || '')

const activeCategoryName = computed(() =>
  categories.value.find(c => c.id === activeCategoryId.value)?.name
)
const activeTagName = computed(() => tags.value.find(t => t.id === activeTagId.value)?.name)

const hotTotalViews = computed(() =>
  hotList.value.reduce((sum, item) => sum + (item.views || 0), 0)
)

async function loadArticles() {
  loading.value = true
  try {
    const { data } = await frontPage({
      pageNum: pageNum.value,
      pageSize: 6,
      keyword: keyword.value || undefined,
      categoryId: activeCategoryId.value || undefined,
      tagId: activeTagId.value || undefined
    })
    articles.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadSidebar() {
  const [hotRes, catRes, tagRes] = await Promise.all([hot(5), listCategories(), listTags()])
  hotList.value = hotRes.data
  categories.value = catRes.data
  tags.value = tagRes.data
}

function setQuery(patch) {
  const query = { ...route.query, ...patch }
  Object.keys(query).forEach(key => {
    if (query[key] === undefined || query[key] === null || query[key] === '') {
      delete query[key]
    }
  })
  router.push({ name: 'home', query })
}

function changePage(page) {
  pageNum.value = page
  setQuery({ page: page > 1 ? page : undefined })
}

/** 文章卡片上的标签只有名称，按名称换算成 tagId 再筛选 */
function goTagByName(name) {
  const tag = tags.value.find(item => item.name === name)
  if (tag) {
    setQuery({ tagId: tag.id })
  }
}

function scrollToList() {
  document.querySelector('.post-list')?.scrollIntoView({ behavior: 'smooth' })
}

watch(
  () => [route.query.keyword, route.query.categoryId, route.query.tagId],
  () => {
    pageNum.value = Number(route.query.page || 1)
    loadArticles()
  },
  { immediate: true }
)

loadSidebar()
</script>

<template>
  <div class="home">
    <!-- ============ HERO ============ -->
    <section class="hero">
      <p class="hero-kicker mono">
        <DecryptedText text="// PERSONAL BLOG & ARTICLE SYSTEM — EST. 2026" />
      </p>
      <h1 class="hero-title">
        写作是<span class="acid">思考</span>的<br />显影液<span class="cursor">_</span>
      </h1>
      <p class="hero-desc">
        基于 Spring Boot + Vue 3 的个人博客与文章管理系统，内置 AI 写作辅助。
        数据自有，样式自定，Markdown 工作流，评论与点赞互动。
      </p>
      <div class="hero-actions">
        <span class="hero-btn" @click="scrollToList">[ READ POSTS ]</span>
        <span class="hero-btn ghost" @click="$router.push('/archive')">[ ARCHIVE ]</span>
      </div>
      <div class="hero-stats mono">
        <span><b>{{ total }}</b> POSTS</span>
        <span class="sep">/</span>
        <span><b>{{ categories.length }}</b> CATEGORIES</span>
        <span class="sep">/</span>
        <span><b>{{ tags.length }}</b> TAGS</span>
        <span class="sep">/</span>
        <span><b>{{ hotTotalViews }}</b> VIEWS</span>
      </div>
    </section>

    <!-- ============ 筛选状态 ============ -->
    <div v-if="keyword || activeCategoryId || activeTagId" class="filter-bar">
      <span class="filter-label mono">FILTER //</span>
      <span v-if="keyword" class="tag-chip" @click="setQuery({ keyword: undefined })">KEY: {{ keyword }} ✕</span>
      <span v-if="activeCategoryId" class="tag-chip" @click="setQuery({ categoryId: undefined })">CAT: {{ activeCategoryName }} ✕</span>
      <span v-if="activeTagId" class="tag-chip" @click="setQuery({ tagId: undefined })">TAG: {{ activeTagName }} ✕</span>
      <span class="bracket" @click="setQuery({ keyword: undefined, categoryId: undefined, tagId: undefined })">clear all</span>
    </div>

    <div class="home-body">
      <!-- ============ 文章列表 ============ -->
      <div class="home-main">
        <h2 class="block-title">
          <span class="idx mono">02</span> LATEST POSTS
        </h2>

        <div v-loading="loading" class="post-list">
          <el-empty v-if="!loading && articles.length === 0" description="NO POSTS FOUND" />

          <article
            v-for="(item, index) in articles"
            :key="item.id"
            class="post-card"
            v-tilt="7"
            @click="$router.push(`/article/${item.id}`)"
          >
            <div class="card-media" v-if="(item.cover || item.firstImage) && !failedMedia[item.id]">
              <img
                :src="item.cover || item.firstImage"
                :alt="item.title"
                loading="lazy"
                @error="failedMedia[item.id] = true"
              />
            </div>
            <div class="card-media placeholder" v-else>
              <span class="mono">{{ String(index + 1 + (pageNum - 1) * 6).padStart(2, '0') }}</span>
            </div>

            <div class="card-body">
              <div class="card-top">
                <span class="idx mono">{{ String(index + 1 + (pageNum - 1) * 6).padStart(2, '0') }}</span>
                <span v-if="item.isTop" class="pin mono">[ PIN ]</span>
              </div>
              <h3 class="card-title">
                <a @click.stop="$router.push(`/article/${item.id}`)">{{ item.title }}</a>
              </h3>
              <p class="card-summary">{{ item.summary || 'NO SUMMARY.' }}</p>
              <div class="card-tags">
                <span class="tag-chip" @click.stop="setQuery({ categoryId: item.categoryId })" v-if="item.categoryName">
                  {{ item.categoryName }}
                </span>
                <span
                  v-for="tag in item.tags"
                  :key="tag"
                  class="tag-chip"
                  @click.stop="goTagByName(tag)"
                ># {{ tag }}</span>
              </div>
              <div class="card-meta mono">
                <span>{{ formatDate(item.publishedAt || item.createdAt) }}</span>
                <span class="dot">·</span>
                <span>{{ item.views }} VIEWS</span>
                <span class="dot">·</span>
                <span>{{ item.likes }} LIKES</span>
              </div>
            </div>
          </article>
        </div>

        <div class="pagination-wrap" v-if="total > 6">
          <el-pagination
            background
            layout="prev, pager, next"
            :total="total"
            :page-size="6"
            :current-page="pageNum"
            @current-change="changePage"
          />
        </div>
      </div>

      <!-- ============ 侧栏 ============ -->
      <aside class="home-aside">
        <section class="side-card">
          <h3 class="side-title"><span class="idx mono">01</span> HOT POSTS</h3>
          <ol class="hot-list">
            <li v-for="(item, index) in hotList" :key="item.id">
              <span class="hot-idx mono">{{ String(index + 1).padStart(2, '0') }}</span>
              <router-link :to="`/article/${item.id}`" class="hot-title">{{ item.title }}</router-link>
              <span class="hot-views mono">{{ item.views }}</span>
            </li>
            <li v-if="hotList.length === 0" class="muted mono">— EMPTY —</li>
          </ol>
        </section>

        <section class="side-card">
          <h3 class="side-title"><span class="idx mono">02</span> CATEGORIES</h3>
          <div class="chip-group">
            <span
              class="tag-chip"
              :class="{ 'is-active': !activeCategoryId }"
              @click="setQuery({ categoryId: undefined })"
            >ALL</span>
            <span
              v-for="cat in categories"
              :key="cat.id"
              class="tag-chip"
              :class="{ 'is-active': cat.id === activeCategoryId }"
              @click="setQuery({ categoryId: cat.id })"
            >{{ cat.name }}</span>
          </div>
        </section>

        <section class="side-card">
          <h3 class="side-title"><span class="idx mono">03</span> TAGS</h3>
          <div class="chip-group">
            <span
              v-for="tag in tags"
              :key="tag.id"
              class="tag-chip"
              :class="{ 'is-active': tag.id === activeTagId }"
              @click="setQuery({ tagId: tag.id })"
            ># {{ tag.name }}</span>
            <span v-if="tags.length === 0" class="muted mono">— EMPTY —</span>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* ---------- HERO ---------- */
.hero {
  border: 2px solid var(--text);
  background: var(--surface);
  padding: 52px 48px 40px;
  margin-bottom: 48px;
  position: relative;
  overflow: hidden;
}
.hero::after {
  content: "+";
  position: absolute;
  top: 14px;
  right: 18px;
  font-family: var(--font-mono);
  font-size: 22px;
  color: var(--acid);
}

.hero-kicker {
  margin: 0 0 18px;
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--acid);
}

.hero-title {
  margin: 0 0 20px;
  font-size: clamp(34px, 5vw, 54px);
  font-weight: 900;
  line-height: 1.14;
  letter-spacing: 0.01em;
  color: var(--text);
}
.hero-title .acid {
  color: var(--acid);
}
.hero-title .cursor {
  color: var(--acid);
  animation: blink 1.1s steps(2) infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}

.hero-desc {
  max-width: 560px;
  margin: 0 0 28px;
  color: var(--text-dim);
  line-height: 1.8;
  font-size: 15px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  margin-bottom: 30px;
}
.hero-btn {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  padding: 11px 22px;
  background: var(--acid);
  color: #000;
  cursor: pointer;
  border: 2px solid var(--acid);
  transition: all 0.15s;
}
.hero-btn:hover {
  background: transparent;
  color: var(--acid);
}
.hero-btn.ghost {
  background: transparent;
  color: var(--text);
  border-color: var(--text);
}
.hero-btn.ghost:hover {
  background: var(--text);
  color: #000;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  border-top: 1px solid var(--line);
  padding-top: 18px;
}
.hero-stats b {
  color: var(--acid);
  font-size: 15px;
  margin-right: 2px;
}
.hero-stats .sep {
  color: var(--line-bright);
}

/* ---------- 筛选条 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 26px;
}
.filter-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--text-faint);
}

/* ---------- 主体两栏 ---------- */
.home-body {
  display: flex;
  gap: 36px;
  align-items: flex-start;
}
.home-main {
  flex: 1;
  min-width: 0;
}

/* ---------- 文章卡片 ---------- */
.post-list {
  display: flex;
  flex-direction: column;
}
.post-card {
  display: flex;
  gap: 22px;
  border: 1px solid var(--line);
  background: var(--surface);
  padding: 22px 24px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: border-color 0.15s;
  position: relative;
  will-change: transform;
}
.post-card:hover {
  border-color: var(--acid);
}
/* 3D 视差光泽层：跟随鼠标（--tilt-x / --tilt-y 由 v-tilt 指令实时更新） */
.post-card::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    520px circle at var(--tilt-x, 50%) var(--tilt-y, 50%),
    rgba(204, 255, 0, 0.07),
    transparent 45%
  );
  opacity: 0;
  transition: opacity 0.2s;
}
.post-card:hover::after {
  opacity: 1;
}

.card-media {
  width: 190px;
  height: 128px;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid var(--line);
}
.card-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s;
}
.post-card:hover .card-media img {
  transform: scale(1.06);
}
.card-media.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2);
}
.card-media.placeholder span {
  font-size: 34px;
  font-weight: 800;
  color: var(--line-bright);
}
.post-card:hover .card-media.placeholder span {
  color: var(--acid-dim);
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.pin {
  font-size: 11px;
  color: #000;
  background: var(--pink);
  padding: 1px 7px;
  font-weight: 700;
}

.card-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.4;
}
.card-title a {
  color: var(--text);
  transition: color 0.15s;
}
.post-card:hover .card-title a {
  color: var(--acid);
}

.card-summary {
  margin: 0 0 14px;
  color: var(--text-dim);
  font-size: 13.5px;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.card-meta {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}
.card-meta .dot {
  color: var(--line-bright);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

/* ---------- 侧栏 ---------- */
.home-aside {
  width: 300px;
  flex-shrink: 0;
}
.side-card {
  border: 1px solid var(--line);
  background: var(--surface);
  padding: 20px 22px;
  margin-bottom: 18px;
}
.side-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--text);
  border-bottom: 1px solid var(--line);
  padding-bottom: 12px;
}

.hot-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.hot-list li {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 7px 0;
}
.hot-idx {
  font-size: 11px;
  color: var(--acid);
  flex-shrink: 0;
}
.hot-title {
  flex: 1;
  font-size: 13px;
  color: var(--text-dim);
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}
.hot-title:hover {
  color: var(--acid);
}
.hot-views {
  font-size: 10px;
  color: var(--text-faint);
  flex-shrink: 0;
}

.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.muted {
  color: var(--text-faint);
  font-size: 12px;
}

@media (max-width: 960px) {
  .home-body {
    flex-direction: column;
  }
  .home-aside {
    width: 100%;
  }
  .hero {
    padding: 36px 26px 28px;
  }
  .post-card {
    flex-direction: column;
  }
  .card-media {
    width: 100%;
    height: 170px;
  }
}
</style>

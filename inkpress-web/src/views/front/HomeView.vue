<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { frontPage, hot } from '@/api/article'
import { list as listCategories } from '@/api/category'
import { list as listTags } from '@/api/tag'
import { View, Star, CollectionTag } from '@element-plus/icons-vue'
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

const activeCategoryId = computed(() => (route.query.categoryId ? Number(route.query.categoryId) : null))
const activeTagId = computed(() => (route.query.tagId ? Number(route.query.tagId) : null))
const keyword = computed(() => route.query.keyword || '')

const activeCategoryName = computed(() =>
  categories.value.find(c => c.id === activeCategoryId.value)?.name
)
const activeTagName = computed(() => tags.value.find(t => t.id === activeTagId.value)?.name)

async function loadArticles() {
  loading.value = true
  try {
    const { data } = await frontPage({
      pageNum: pageNum.value,
      pageSize: 8,
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
    <div class="home-main">
      <!-- 筛选状态提示 -->
      <div v-if="keyword || activeCategoryId || activeTagId" class="filter-bar">
        <el-tag closable type="info" @close="setQuery({ keyword: undefined })" v-if="keyword">
          搜索：{{ keyword }}
        </el-tag>
        <el-tag closable type="info" @close="setQuery({ categoryId: undefined })" v-if="activeCategoryId">
          分类：{{ activeCategoryName }}
        </el-tag>
        <el-tag closable type="info" @close="setQuery({ tagId: undefined })" v-if="activeTagId">
          标签：{{ activeTagName }}
        </el-tag>
      </div>

      <div v-loading="loading" class="article-list">
        <el-empty v-if="!loading && articles.length === 0" description="暂无文章" />
        <article v-for="item in articles" :key="item.id" class="article-card">
          <div class="card-body">
            <h2 class="card-title">
              <el-tag v-if="item.isTop" type="danger" size="small" effect="dark" class="top-tag">置顶</el-tag>
              <router-link :to="`/article/${item.id}`">{{ item.title }}</router-link>
            </h2>
            <p class="card-summary">{{ item.summary || '（暂无摘要）' }}</p>
            <div class="card-meta">
              <el-tag size="small" effect="plain">{{ item.categoryName || '未分类' }}</el-tag>
              <span class="meta-item">{{ formatDate(item.publishedAt || item.createdAt) }}</span>
              <span class="meta-item"><el-icon><View /></el-icon>{{ item.views }}</span>
              <span class="meta-item"><el-icon><Star /></el-icon>{{ item.likes }}</span>
              <el-tag
                v-for="tag in item.tags"
                :key="tag"
                size="small"
                type="warning"
                effect="plain"
                class="card-tag"
                @click="goTagByName(tag)"
              >
                # {{ tag }}
              </el-tag>
            </div>
          </div>
          <img v-if="item.cover" :src="item.cover" class="card-cover" alt="封面" />
        </article>
      </div>

      <div class="pagination-wrap" v-if="total > 8">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="8"
          :current-page="pageNum"
          @current-change="changePage"
        />
      </div>
    </div>

    <aside class="home-aside">
      <section class="side-card">
        <h3 class="side-title"><el-icon><CollectionTag /></el-icon> 热门文章</h3>
        <ol class="hot-list">
          <li v-for="item in hotList" :key="item.id">
            <router-link :to="`/article/${item.id}`">{{ item.title }}</router-link>
            <span class="hot-views">{{ item.views }} 次浏览</span>
          </li>
          <li v-if="hotList.length === 0" class="muted">暂无数据</li>
        </ol>
      </section>

      <section class="side-card">
        <h3 class="side-title"><el-icon><CollectionTag /></el-icon> 分类</h3>
        <div class="chip-group">
          <span
            class="chip"
            :class="{ active: !activeCategoryId }"
            @click="setQuery({ categoryId: undefined })"
          >
            全部
          </span>
          <span
            v-for="cat in categories"
            :key="cat.id"
            class="chip"
            :class="{ active: cat.id === activeCategoryId }"
            @click="setQuery({ categoryId: cat.id })"
          >
            {{ cat.name }}
          </span>
        </div>
      </section>

      <section class="side-card">
        <h3 class="side-title"><el-icon><CollectionTag /></el-icon> 标签</h3>
        <div class="chip-group">
          <span
            v-for="tag in tags"
            :key="tag.id"
            class="chip"
            :class="{ active: tag.id === activeTagId }"
            @click="setQuery({ tagId: tag.id })"
          >
            # {{ tag.name }}
          </span>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.home-main {
  flex: 1;
  min-width: 0;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.article-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px 24px;
  margin-bottom: 16px;
  display: flex;
  gap: 16px;
  transition: box-shadow 0.2s;
}

.article-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-title {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 600;
}

.card-title a:hover {
  color: #409eff;
}

.top-tag {
  margin-right: 8px;
  vertical-align: 2px;
}

.card-summary {
  color: #606266;
  font-size: 14px;
  line-height: 1.7;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 13px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.card-tag {
  cursor: pointer;
}

.card-cover {
  width: 180px;
  height: 110px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.home-aside {
  width: 300px;
  flex-shrink: 0;
}

.side-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.side-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 12px;
  font-size: 15px;
}

.hot-list {
  margin: 0;
  padding-left: 20px;
}

.hot-list li {
  margin-bottom: 8px;
  font-size: 13px;
}

.hot-list a:hover {
  color: #409eff;
}

.hot-views {
  color: #c0c4cc;
  margin-left: 6px;
  font-size: 12px;
}

.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 3px 10px;
  border-radius: 12px;
  background: #f4f4f5;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
}

.chip.active {
  background: #409eff;
  color: #fff;
}

.muted {
  color: #c0c4cc;
}

@media (max-width: 900px) {
  .home {
    flex-direction: column;
  }
  .home-aside {
    width: 100%;
  }
}
</style>

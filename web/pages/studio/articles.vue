<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api } from '~/composables/useApi'

interface ArticleRow {
  id: number
  title: string
  status: string
  source: string
  pinned: boolean
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
  tags: string[]
}

interface ReviewReport {
  totalScore: number
  readability: number
  structure: number
  keywordDensity: number
  seo: number
  suggestion: string
  confidence: string
  model: string
}

interface DistRecord {
  id: number
  platform: string
  status: string
  draftUrl: string
  publishedUrl: string
  attemptCount: number
  detail: string
}

const records = ref<ArticleRow[]>([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const loading = ref(false)

// 状态中文映射（与后端状态机一致）
const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待复审', PUBLISHED: '已发布',
  REJECTED: '未通过', OFFLINE: '已下架',
}
const PLATFORMS = ['juejin', 'csdn', 'zhihu', 'wechat']

async function load() {
  loading.value = true
  try {
    const p = await $api<{ records: ArticleRow[]; total: number }>(
      'GET', `/studio/articles?page=${page.value}&size=10${status.value ? '&status=' + status.value : ''}`)
    records.value = p.records
    total.value = p.total
  } catch (e) { ElMessage.error((e as Error).message) } finally { loading.value = false }
}

onMounted(load)
watch([page, status], load)

async function togglePin(row: ArticleRow) {
  await $api('POST', `/studio/articles/${row.id}/pin`, { pinned: !row.pinned })
  row.pinned = !row.pinned
}

async function offline(row: ArticleRow) {
  await ElMessageBox.confirm('下架后前台不可见，确定？', '下架确认')
  await $api('POST', `/studio/articles/${row.id}/offline`)
  row.status = 'OFFLINE'
}

async function remove(row: ArticleRow) {
  await ElMessageBox.confirm('删除后前台不可见且不可恢复，确定？', '删除确认')
  await $api('DELETE', `/studio/articles/${row.id}`)
  await load()
}

// ---- 分发 ----
const distVisible = ref(false)
const distArticle = ref<ArticleRow | null>(null)
const distRecords = ref<DistRecord[]>([])
const distPlatform = ref('juejin')

async function openDist(row: ArticleRow) {
  distArticle.value = row
  distVisible.value = true
  await refreshDist()
}
async function refreshDist() {
  distRecords.value = await $api<DistRecord[]>('GET', `/studio/distribution/${distArticle.value!.id}`)
}
async function createDist() {
  const r = await $api<{ id: number }>('POST', '/studio/distribution',
    { articleId: distArticle.value!.id, platform: distPlatform.value })
  ElMessage.success(`分发任务 #${r.id} 已创建，可在本机运行 inkpress run ${r.id}`)
  await refreshDist()
}

// ---- AI 评审 ----
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const review = ref<ReviewReport | null>(null)

async function runReview(row: ArticleRow) {
  reviewVisible.value = true
  reviewLoading.value = true
  review.value = null
  try {
    review.value = await $api<ReviewReport>('POST', `/studio/articles/${row.id}/ai-review`)
  } catch (e) { ElMessage.error((e as Error).message) } finally { reviewLoading.value = false }
}
async function showLatest(row: ArticleRow) {
  reviewVisible.value = true
  reviewLoading.value = true
  review.value = null
  try { review.value = await $api<ReviewReport>('GET', `/studio/articles/${row.id}/ai-review/latest`) }
  catch { review.value = null } finally { reviewLoading.value = false }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>文章管理</h2>
        <span class="sub">写作、评审与分发都在这条流水线上</span>
      </div>
      <div class="head-actions">
        <el-select v-model="status" placeholder="全部状态" clearable style="width:140px">
          <el-option v-for="(t, k) in STATUS_TEXT" :key="k" :label="t" :value="k" />
        </el-select>
        <NuxtLink to="/studio/write/0" class="btn primary">写文章</NuxtLink>
      </div>
    </div>

    <div v-loading="loading">
      <div v-for="row in records" :key="row.id" class="row" :class="'spine-' + row.status">
        <div class="row-main">
          <NuxtLink :to="`/studio/write/${row.id}`" class="row-title">
            <span v-if="row.pinned" class="pin">置顶</span>{{ row.title }}
          </NuxtLink>
          <div class="row-sub">
            <span class="mono">{{ (row.publishedAt || '').slice(0, 10) || '未发布' }}</span>
            <span v-if="(row.tags || []).length"> · {{ (row.tags || []).join(' / ') }}</span>
            <span v-if="row.source === 'AGENT'" class="ai-tag">AI 来稿</span>
          </div>
        </div>
        <span class="st" :class="'st-' + row.status">{{ STATUS_TEXT[row.status] || row.status }}</span>
        <div class="row-stats">
          <span>{{ row.viewCount }} 浏览</span>
          <span>{{ row.likeCount }} 赞</span>
          <span>{{ row.commentCount }} 评</span>
        </div>
        <div class="row-actions">
          <button class="abtn" @click="togglePin(row)">{{ row.pinned ? '取消置顶' : '置顶' }}</button>
          <button v-if="row.status === 'PUBLISHED'" class="abtn" @click="offline(row)">下架</button>
          <button class="abtn" @click="runReview(row)">AI 评审</button>
          <button v-if="row.status === 'PUBLISHED'" class="abtn" @click="openDist(row)">分发</button>
          <button class="abtn danger" @click="remove(row)">删除</button>
        </div>
      </div>
      <el-empty v-if="!loading && !records.length" description="还没有文章，点右上角「写文章」开始" />
    </div>

    <el-pagination
      v-model:current-page="page"
      :page-size="10" :total="total" layout="prev, pager, next, total"
      style="margin-top:18px;justify-content:flex-end"
    />

    <!-- 分发对话框 -->
    <el-dialog v-model="distVisible" :title="`分发「${distArticle?.title}」`" width="720">
      <div style="display:flex;gap:10px;margin-bottom:14px">
        <el-select v-model="distPlatform" style="width:160px">
          <el-option v-for="p in PLATFORMS" :key="p" :label="p" :value="p" />
        </el-select>
        <el-button type="primary" @click="createDist">创建分发任务</el-button>
        <span class="muted" style="align-self:center">创建后在本机执行 inkpress run &lt;id&gt;，agent 自动填充平台草稿。</span>
      </div>
      <el-table :data="distRecords" size="small">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="platform" label="平台" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="外链" min-width="200">
          <template #default="{ row }">
            <a v-if="row.publishedUrl" :href="row.publishedUrl" target="_blank" rel="noopener" class="ellip">{{ row.publishedUrl }}</a>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="attemptCount" label="次数" width="60" />
      </el-table>
    </el-dialog>

    <!-- AI 评审报告对话框 -->
    <el-dialog v-model="reviewVisible" title="AI 评审报告" width="620">
      <div v-if="reviewLoading" v-loading="true" style="height:120px" />
      <template v-else-if="review">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="总分">
            <b :style="{ color: review.totalScore >= 60 ? 'var(--ok)' : 'var(--danger)', fontSize: '1.3em' }">{{ review.totalScore }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="置信度">{{ review.confidence }}</el-descriptions-item>
          <el-descriptions-item label="可读性">{{ review.readability }}</el-descriptions-item>
          <el-descriptions-item label="结构">{{ review.structure }}</el-descriptions-item>
          <el-descriptions-item label="关键词密度">{{ review.keywordDensity }}</el-descriptions-item>
          <el-descriptions-item label="SEO">{{ review.seo }}</el-descriptions-item>
        </el-descriptions>
        <p class="muted" style="margin-top:12px;white-space:pre-wrap">{{ review.suggestion }}</p>
        <p class="muted">模型：{{ review.model }}</p>
      </template>
      <el-empty v-else description="暂无评审报告" />
    </el-dialog>
  </div>
</template>

<style scoped>
.ellip { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
</style>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api, $pub } from '~/composables/useApi'
import { streamAi } from '~/composables/useAiStream'

const route = useRoute()
const articleId = Number(route.params.id)

interface ArticleDetail {
  id: number
  title: string
  contentMd: string
  contentHtml: string
  categoryId: number | null
  categoryName: string
  coverUrl: string
  seoDescription: string
  geoSummary: string
  status: string
  source: string
  tags: string[]
}
interface Category { id: number; name: string }
interface LinkItem { articleId: number; title: string }
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

const title = ref('')
const categoryId = ref<number | null>(null)
const tagsText = ref('')
const coverUrl = ref('')
const seoDescription = ref('')
const geoSummary = ref('')
const status = ref('')
const isAgent = ref(false)
const categories = ref<Category[]>([])
const links = ref<LinkItem[]>([])
const newLinkTitle = ref('')
const linkTargetId = ref<number | null>(null)

const editorBox = ref<HTMLElement | null>(null)
let vditor: { getValue(): string; setValue(v: string): void } | null = null
const editorReady = ref(false)

const saving = ref(false)
const aiBusy = ref(false)
const aiOutput = ref('')
const aiPanel = ref(false)
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const review = ref<ReviewReport | null>(null)
const summaryBusy = ref(false)

onMounted(async () => {
  categories.value = await $pub<Category[]>('/categories').catch(() => [])
  let contentMd = ''
  if (articleId > 0) {
    const a = await $api<ArticleDetail>('GET', `/studio/articles/${articleId}`)
    title.value = a.title
    categoryId.value = a.categoryId
    tagsText.value = (a.tags || []).join(', ')
    coverUrl.value = a.coverUrl || ''
    seoDescription.value = a.seoDescription || ''
    geoSummary.value = a.geoSummary || ''
    status.value = a.status
    isAgent.value = a.source === 'AGENT'
    contentMd = a.contentMd || ''
    links.value = await $api<LinkItem[]>('GET', `/studio/articles/${articleId}/links`).catch(() => [])
  }
  await initEditor(contentMd)
})

async function initEditor(pendingContent: string) {
  const [{ default: Vditor }] = await Promise.all([
    import('vditor'),
    import('vditor/dist/index.css'),
  ])
  vditor = new Vditor(editorBox.value!, {
    cdn: '/vditor',
    height: 480,
    mode: 'wysiwyg',
    placeholder: '开始写作…（Markdown，所见即所得）',
    // vditor 3.11 必填：按文章维度的本地草稿缓存（未保存内容不丢失）
    cache: { id: 'inkpress-write-' + articleId, enable: true },
    after: () => {
      editorReady.value = true
      // setValue 必须在编辑器就绪回调之后（提前调用会被吞掉）
      if (pendingContent) vditor?.setValue(pendingContent)
    },
  })
}

function payload(action?: string, confirmLowScore?: boolean) {
  return {
    id: articleId > 0 ? articleId : null,
    title: title.value.trim(),
    contentMd: vditor?.getValue() ?? '',
    categoryId: categoryId.value,
    coverUrl: coverUrl.value || null,
    seoDescription: seoDescription.value || null,
    geoSummary: geoSummary.value || null,
    tags: tagsText.value.split(/[,，]/).map((t) => t.trim()).filter(Boolean),
    action: action ?? 'DRAFT',
    confirmLowScore,
  }
}

async function save(action?: string, confirmLowScore?: boolean): Promise<boolean> {
  if (!title.value.trim()) { ElMessage.warning('标题必填'); return false }
  saving.value = true
  try {
    const saved = await $api<{ id: number; status: string }>(
      'POST', '/studio/articles', payload(action, confirmLowScore))
    status.value = saved.status
    if (articleId === 0 && saved.id) {
      navigateTo(`/studio/write/${saved.id}`, { replace: true })
    }
    ElMessage.success(action === 'PUBLISH' ? '已发布' : '草稿已保存')
    return true
  } catch (e) {
    const msg = (e as Error).message
    if (msg.includes('2005') || msg.includes('评审分数偏低')) {
      // M9 低分闸口：展示评审报告 → 用户显式确认后带 confirmLowScore 重发
      await showLatestReview()
      try {
        await ElMessageBox.confirm(
          `${msg}\n确认仍然发布？（可在评审报告中查看具体建议）`, '低分发布确认', { type: 'warning' })
        return await save(action, true)
      } catch { /* 用户取消 */ }
    } else {
      ElMessage.error(msg)
    }
    return false
  } finally { saving.value = false }
}

async function showLatestReview() {
  reviewLoading.value = true
  reviewVisible.value = true
  try { review.value = await $api<ReviewReport>('GET', `/studio/articles/${articleId}/ai-review/latest`) }
  catch { review.value = null } finally { reviewLoading.value = false }
}

async function runReview() {
  reviewLoading.value = true
  reviewVisible.value = true
  review.value = null
  try { review.value = await $api<ReviewReport>('POST', `/studio/articles/${articleId}/ai-review`) }
  catch (e) { ElMessage.error((e as Error).message) } finally { reviewLoading.value = false }
}

// ---- AI 辅助 ----
async function aiRun(category: string, mode: 'replace' | 'append' | 'title') {
  const md = vditor?.getValue() ?? ''
  if (!md.trim()) { ElMessage.warning('正文为空'); return }
  aiBusy.value = true
  aiPanel.value = true
  aiOutput.value = ''
  try {
    const full = await streamAi(category, md, (t) => { aiOutput.value += t })
    if (mode === 'replace') vditor?.setValue(full)
    else if (mode === 'append') vditor?.setValue(md + '\n\n' + full)
    else if (mode === 'title') {
      const first = full.split('\n')[0].replace(/^[#\s\d.、-]*/, '').trim().slice(0, 60)
      if (first) title.value = first
    }
    ElMessage.success('AI 完成，已按模式应用')
  } catch (e) { ElMessage.error((e as Error).message) } finally { aiBusy.value = false }
}

async function aiSummary() {
  const md = vditor?.getValue() ?? ''
  if (!md.trim()) { ElMessage.warning('正文为空'); return }
  summaryBusy.value = true
  try {
    const r = await $api<{ seoDescription: string; geoSummary: string; tags: string[] }>(
      'POST', '/ai/summary', { content: md })
    seoDescription.value = r.seoDescription
    geoSummary.value = r.geoSummary
    if (r.tags?.length) tagsText.value = r.tags.join(', ')
    ElMessage.success('SEO/GEO 摘要与标签已生成')
  } catch (e) { ElMessage.error((e as Error).message) } finally { summaryBusy.value = false }
}

// ---- 关联管理 ----
async function addLink() {
  if (!linkTargetId.value) return
  try {
    await $api('POST', `/studio/articles/${articleId}/links`, { targetId: linkTargetId.value })
    linkTargetId.value = null
    links.value = await $api<LinkItem[]>('GET', `/studio/articles/${articleId}/links`)
  } catch (e) { ElMessage.error((e as Error).message) }
}
async function removeLink(id: number) {
  await $api('DELETE', `/studio/articles/${articleId}/links/${id}`)
  links.value = await $api<LinkItem[]>('GET', `/studio/articles/${articleId}/links`)
}
</script>

<template>
  <div class="write">
    <div class="write-main">
      <input v-model="title" class="title-input" placeholder="标题（可让 AI 起标题）">
      <div ref="editorBox" class="vditor-box" />
      <div class="meta-grid">
        <div class="form-row">
          <label>分类</label>
          <select v-model="categoryId">
            <option :value="null">（无分类）</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-row">
          <label>标签（逗号分隔）</label>
          <input v-model="tagsText" placeholder="spring-boot, 教程">
        </div>
        <div class="form-row">
          <label>封面 URL</label>
          <input v-model="coverUrl" placeholder="https://… 或 /api/media/1">
        </div>
        <div class="form-row">
          <label>SEO 描述（meta description）</label>
          <textarea v-model="seoDescription" rows="2" />
        </div>
        <div class="form-row">
          <label>GEO 摘要（AI 检索友好）</label>
          <textarea v-model="geoSummary" rows="2" />
        </div>
      </div>
    </div>

    <aside class="write-side">
      <div class="card">
        <div class="side-sec">操作</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <el-button :loading="saving" @click="save('DRAFT')">存草稿</el-button>
          <el-button type="primary" :loading="saving" @click="save('PUBLISH')">
            {{ status === 'PUBLISHED' ? '更新发布' : '发布' }}
          </el-button>
          <el-button @click="runReview">AI 评审</el-button>
        </div>
        <div v-if="status" class="muted" style="margin-top:8px">
          当前状态：{{ status }}<span v-if="isAgent">（AI 来稿，发布走人工复审）</span>
        </div>
      </div>

      <div class="card">
        <div class="side-sec">AI 辅助 <span class="muted">（需管理员配置 AI Key）</span></div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <el-button size="small" :disabled="aiBusy" @click="aiRun('polish', 'replace')">润色全文</el-button>
          <el-button size="small" :disabled="aiBusy" @click="aiRun('correct', 'replace')">纠错全文</el-button>
          <el-button size="small" :disabled="aiBusy" @click="aiRun('continue', 'append')">续写</el-button>
          <el-button size="small" :disabled="aiBusy" @click="aiRun('title', 'title')">起标题</el-button>
          <el-button size="small" :disabled="summaryBusy" @click="aiSummary">摘要+标签</el-button>
        </div>
        <div v-if="aiPanel" class="ai-out">
          <div class="muted" style="margin-bottom:6px">流式输出：</div>
          <pre class="ai-pre">{{ aiOutput || '…' }}</pre>
        </div>
      </div>

      <div class="card">
        <div class="side-sec">手动关联（图谱边）</div>
        <div style="display:flex;gap:8px">
          <el-input-number v-model="linkTargetId" :min="1" placeholder="目标文章 ID" controls-position="right" style="flex:1" />
          <el-button @click="addLink">添加</el-button>
        </div>
        <div v-for="l in links" :key="l.articleId" class="link-row">
          <NuxtLink :to="`/studio/write/${l.articleId}`">#{{ l.articleId }} {{ l.title }}</NuxtLink>
          <button class="linkish" @click="removeLink(l.articleId)">移除</button>
        </div>
        <div v-if="!links.length" class="muted">暂无关联（双向合并展示）</div>
      </div>
    </aside>

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
      <el-empty v-else description="暂无评审报告（发布前会自动触发）" />
    </el-dialog>
  </div>
</template>

<style scoped>
.write { display: grid; grid-template-columns: 1fr 300px; gap: 22px; align-items: start; }
@media (max-width: 1100px) { .write { grid-template-columns: 1fr; } }
.title-input {
  font-size: 1.4em;
  font-weight: 700;
  border: none;
  background: transparent;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  padding: 6px 2px 12px;
  margin-bottom: 14px;
}
.vditor-box { min-height: 480px; }
.meta-grid { margin-top: 16px; }
.side-sec { font-weight: 600; margin-bottom: 10px; }
.write-side { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 20px; }
.ai-out { margin-top: 10px; }
.ai-pre {
  background: #10131a;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: .85em;
  color: #c4b5fd;
}
.link-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-top: 1px dashed var(--line); }
.linkish { border: none; background: none; color: var(--ink-faint); padding: 0; }
.linkish:hover { color: var(--danger); }
</style>

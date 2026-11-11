<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api, accessToken } from '~/composables/useApi'
import { streamChat } from '~/composables/useAiStream'

interface ArticleRow {
  id: number
  title: string
  status: string
  source: string
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
  tags: string[]
}
interface ArticleDetail {
  id: number
  title: string
  contentMd: string
  tags: string[]
  status: string
  source: string
}
interface ChatMsg { role: 'user' | 'assistant'; content: string }
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

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿', PENDING_REVIEW: '待复审', PUBLISHED: '已发布',
  REJECTED: '未通过', OFFLINE: '已下架',
}

const rows = ref<ArticleRow[]>([])
const current = ref<ArticleRow | null>(null)
const loadingList = ref(true)

const title = ref('')
const tagsText = ref('')
const coverUrl = ref('')
const status = ref('')
const isAgent = ref(false)
const seoDescription = ref('')
const geoSummary = ref('')
const saving = ref(false)
const editorBox = ref<HTMLElement | null>(null)
const editorReady = ref(false)
const coverInput = ref<HTMLInputElement | null>(null)
let vditor: {
  getValue(): string
  setValue(v: string): void
  insertValue(v: string): void
} | null = null
let pendingContent = ''

// 对话流状态
const chat = ref<ChatMsg[]>([])
const chatInput = ref('')
const aiBusy = ref(false)
const carryArticle = ref(true)
const chatBody = ref<HTMLElement | null>(null)

const reviewVisible = ref(false)
const reviewLoading = ref(false)
const review = ref<ReviewReport | null>(null)

async function loadList() {
  loadingList.value = true
  try {
    const p = await $api<{ records: ArticleRow[] }>('GET', '/studio/articles?page=1&size=30')
    rows.value = p.records
    if (!current.value && p.records.length) await openArticle(p.records[0])
  } catch (e) { ElMessage.error((e as Error).message) } finally { loadingList.value = false }
}

async function openArticle(row: ArticleRow) {
  current.value = row
  const a = await $api<ArticleDetail>('GET', `/studio/articles/${row.id}`)
  title.value = a.title
  tagsText.value = (a.tags || []).join(', ')
  coverUrl.value = a.coverUrl || ''
  status.value = a.status
  isAgent.value = a.source === 'AGENT'
  const md = a.contentMd || ''
  if (editorReady.value) vditor?.setValue(md)
  else pendingContent = md
}

async function initEditor() {
  const [{ default: Vditor }] = await Promise.all([
    import('vditor'),
    import('vditor/dist/index.css'),
  ])
  vditor = new Vditor(editorBox.value!, {
    cdn: '/vditor',
    height: '100%',
    mode: 'wysiwyg',
    cache: { id: 'inkpress-workbench', enable: true },
    after: () => {
      editorReady.value = true
      if (pendingContent) vditor?.setValue(pendingContent)
    },
    // Vditor 3.11：handler 返回值不会自动插入编辑器，须显式 insertValue
    upload: {
      handler: async (files: File[]): Promise<string[]> => {
        for (const f of files) {
          try {
            const url = await uploadMediaOnly(f)
            const isVideo = (f.type || '').startsWith('video/')
            vditor?.insertValue(isVideo ? `[🎬 视频](${url})` : `![](${url})`)
          } catch (e) {
            ElMessage.error(`${f.name} 上传失败：${(e as Error).message}`)
          }
        }
        return []
      },
    },
  })
}

async function uploadMediaOnly(f: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', f)
  const resp = await fetch('/api/media/upload', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + (accessToken() || '') },
    body: fd,
  })
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || `HTTP ${resp.status}`)
  return json.data.url as string
}

async function onCoverUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    coverUrl.value = await uploadMediaOnly(file)
    ElMessage.success('封面上传成功')
  } catch (e) { ElMessage.error((e as Error).message) } finally { input.value = '' }
}

async function save(action?: string, confirmLowScore?: boolean) {
  if (!current.value || !title.value.trim()) { ElMessage.warning('标题必填'); return }
  saving.value = true
  try {
    const saved = await $api<{ id: number; status: string }>('POST', '/studio/articles', {
      id: current.value.id,
      title: title.value.trim(),
      contentMd: vditor?.getValue() ?? '',
      tags: tagsText.value.split(/[,，]/).map((t) => t.trim()).filter(Boolean),
      action: action ?? 'DRAFT',
      confirmLowScore,
    })
    status.value = saved.status
    current.value.status = saved.status
    const row = rows.value.find((r) => r.id === current.value!.id)
    if (row) row.status = saved.status
    ElMessage.success(action === 'PUBLISH' ? '已发布' : '草稿已保存')
    return true
  } catch (e) {
    const msg = (e as Error).message
    if (msg.includes('2005') || msg.includes('评审分数偏低')) {
      try {
        const report = await $api<ReviewReport>('GET', `/studio/articles/${current.value.id}/ai-review/latest`).catch(() => null)
        await ElMessageBox.confirm(
          `${msg}${report ? `（评审 ${report.totalScore} 分）` : ''}\n确认仍然发布？`, '低分发布确认', { type: 'warning' })
        return await save(action, true)
      } catch { /* 取消 */ }
    } else {
      ElMessage.error(msg)
    }
    return false
  } finally { saving.value = false }
}

async function runReview() {
  if (!current.value) return
  reviewLoading.value = true
  reviewVisible.value = true
  review.value = null
  try { review.value = await $api<ReviewReport>('POST', `/studio/articles/${current.value.id}/ai-review`) }
  catch (e) { ElMessage.error((e as Error).message) } finally { reviewLoading.value = false }
}

async function aiSummary() {
  const md = vditor?.getValue() ?? ''
  if (!md.trim()) { ElMessage.warning('正文为空'); return }
  quickBusy.value = true
  try {
    const r = await $api<{ seoDescription: string; geoSummary: string; tags: string[] }>(
      'POST', '/ai/summary', { content: md })
    seoDescription.value = r.seoDescription
    geoSummary.value = r.geoSummary
    if (r.tags?.length) tagsText.value = r.tags.join(', ')
    ElMessage.success('SEO/GEO 摘要与标签已生成')
  } catch (e) { ElMessage.error((e as Error).message) } finally { quickBusy.value = false }
}


function scrollChat() {
  requestAnimationFrame(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

function currentArticleContext(): string {
  if (!carryArticle.value || !current.value) return ''
  const md = vditor?.getValue() ?? ''
  if (!md.trim()) return ''
  return `\n\n【当前文章草稿（标题：${title.value}）】\n${md.slice(0, 3000)}`
}

async function send(text?: string, intoEditor: 'none' | 'replace' | 'append' = 'none') {
  const msg = (text ?? chatInput.value).trim()
  if (!msg || aiBusy.value) return
  if (text === undefined) chatInput.value = ''
  chat.value.push({ role: 'user', content: msg })
  chat.value.push({ role: 'assistant', content: '' })
  const idx = chat.value.length - 1
  aiBusy.value = true
  scrollChat()
  try {
    const history = chat.value.slice(0, idx).map((m) => ({ role: m.role, content: m.content }))
    const ctx = carryArticle.value ? currentArticleContext() : ''
    let reply = ''
    await streamChat(
      [...history, { role: 'user', content: msg + ctx }],
      (t) => {
        reply += t
        chat.value[idx].content = reply
        scrollChat()
      },
    )
    if (intoEditor === 'replace' && vditor) vditor.setValue(reply)
  } catch (e) {
    chat.value[idx].content += `\n⚠ ${(e as Error).message}`
  } finally {
    aiBusy.value = false
    scrollChat()
  }
}

const quickBusy = ref(false)
async function quick(command: string, apply: 'replace' | 'append' | 'title' | 'none') {
  const md = vditor?.getValue() ?? ''
  if (!md.trim() && apply !== 'title') { ElMessage.warning('正文为空'); return }
  aiBusy.value = true
  chat.value.push({ role: 'user', content: command })
  chat.value.push({ role: 'assistant', content: '' })
  const idx = chat.value.length - 1
  scrollChat()
  try {
    const history = chat.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
    let reply = ''
    await streamChat('assistant', `请${command}${md ? `\n\n【当前文章草稿】\n${md.slice(0, 3000)}` : ''}`, (t) => {
      reply += t
      chat.value[idx].content = reply
      scrollChat()
    })
    if (apply === 'replace' && vditor) vditor.setValue(reply)
    else if (apply === 'append' && vditor) vditor.setValue((vditor.getValue() || '') + '\n\n' + reply)
    else if (apply === 'title') {
      const first = reply.split('\n')[0].replace(/^[#\s\d.、-]*/, '').trim().slice(0, 60)
      if (first) title.value = first
    }
  } catch (e) {
    chat.value[idx].content += `\n⚠ ${(e as Error).message}`
  } finally { aiBusy.value = false; scrollChat() }
}

async function applyToEditor(mode: 'replace' | 'append') {
  const last = [...chat.value].reverse().find((m) => m.role === 'assistant')
  if (!last || !vditor) { ElMessage.warning('暂无 AI 输出'); return }
  if (mode === 'replace') vditor.setValue(last.content)
  else vditor.setValue((vditor.getValue() || '') + '\n\n' + last.content)
  ElMessage.success(mode === 'replace' ? '已替换正文' : '已追加到文末')
}



onMounted(async () => {
  await initEditor()
  await loadList()
})
</script>

<template>
  <div class="wb">
    <!-- 左栏：文章列表 -->
    <aside class="wb-left">
      <button class="new-btn" @click="initEditor(); current = null; title = ''; status = ''; pendingContent = ''">
        + 新文章
      </button>
      <div v-loading="loadingList" class="art-list">
        <div
          v-for="row in rows"
          :key="row.id"
          class="art-item"
          :class="{ active: current?.id === row.id }"
          @click="openArticle(row)"
        >
          <div class="a-title">{{ row.title }}</div>
          <div class="a-meta">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ STATUS_TEXT[row.status] || row.status }}
            </el-tag>
            <span class="muted">{{ (row.publishedAt || '').slice(0, 10) }}</span>
          </div>
        </div>
        <div v-if="!rows.length && !loadingList" class="muted" style="padding:10px">暂无文章</div>
      </div>
    </aside>

    <!-- 中栏：编辑器 -->
    <section class="wb-mid">
      <div class="editor-head">
        <input v-model="title" class="title-input" placeholder="标题…">
        <div class="editor-actions">
          <el-button size="small" @click="save('DRAFT')">存草稿</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="save('PUBLISH')">
            {{ status === 'PUBLISHED' ? '更新发布' : '发布' }}
          </el-button>
          <el-button size="small" @click="runReview">AI 评审</el-button>
        </div>
      </div>
      <div ref="editorBox" class="editor-box" />
      <div class="meta-row">
        <span class="muted">标签：</span>
        <input v-model="tagsText" style="flex:1" placeholder="逗号分隔">
        <el-button size="small" @click="aiSummary">AI 摘要+标签</el-button>
      </div>
      <div class="meta-row muted">
        SEO 描述：<input v-model="seoDescription" style="flex:1" placeholder="AI 生成的 meta description">
      </div>
    </section>

    <!-- 右栏：AI 对话流 -->
    <aside class="wb-right">
      <div class="chat-head">
        <span class="chat-title">AI 助手</span>
        <el-tooltip content="对话时自动携带当前文章草稿作为上下文">
          <el-checkbox v-model="carryArticle" size="small">携带文章</el-checkbox>
        </el-tooltip>
      </div>
      <div ref="chatBody" class="chat-body">
        <div v-for="(m, i) in chat" :key="i" class="bubble" :class="m.role">
          <div class="b-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
          <div class="b-content">{{ m.content || '…' }}</div>
        </div>
        <div v-if="!chat.length" class="muted" style="padding:8px">
          与 AI 讨论当前文章：润色建议、标题方案、写作问题都可以直接问。
        </div>
      </div>
      <div class="chat-input-row">
        <input
          v-model="chatInput"
          placeholder="给 AI 下指令，如：润色这段开头…"
          :disabled="aiBusy"
          @keyup.enter="send()"
        >
        <el-button type="primary" :disabled="aiBusy || !chatInput.trim()" @click="send()">发送</el-button>
      </div>
      <div class="apply-row">
        <el-button size="small" @click="applyToEditor('replace')">AI 输出替换正文</el-button>
        <el-button size="small" @click="applyToEditor('append')">追加到文末</el-button>
      </div>
    </aside>

    <!-- AI 评审报告 -->
    <el-dialog v-model="reviewVisible" title="AI 评审报告" width="620">
      <div v-if="reviewLoading" v-loading="true" style="height:120px" />
      <template v-else-if="review">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="总分">
            <b :style="{ color: review.totalScore >= 60 ? 'var(--ok)' : 'var(--danger)', fontSize: '1.3em' }">
              {{ review.totalScore }}
            </b>
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
.wb {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 300px;
  gap: 14px;
  height: calc(100vh - 130px);
}
@media (max-width: 1100px) { .wb { grid-template-columns: 1fr; height: auto; } }

.wb-left {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px;
  overflow-y: auto;
}
.new-btn { width: 100%; margin-bottom: 10px; }
.art-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
}
.art-item:hover { background: var(--card-soft); }
.art-item.active { background: var(--primary-soft); border-color: var(--primary); }
.a-title { font-size: .92em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink); }
.a-meta { display: flex; gap: 8px; align-items: center; margin-top: 2px; }

.wb-mid { display: flex; flex-direction: column; min-width: 0; gap: 10px; }
.editor-head { display: flex; gap: 10px; align-items: center; }
.title-input {
  flex: 1;
  font-size: 1.25em;
  font-weight: 700;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--card);
  padding: 8px 14px;
}
.editor-actions { display: flex; gap: 8px; }
.editor-box { flex: 1; min-height: 420px; border-radius: 10px; overflow: hidden; }
.meta-row { display: flex; gap: 8px; align-items: center; margin-top: 8px; }

.wb-right {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 400px;
}
.chat-head { display: flex; justify-content: space-between; align-items: center; }
.chat-title { font-weight: 650; }
.chat-body { flex: 1; overflow-y: auto; min-height: 160px; display: flex; flex-direction: column; gap: 10px; }
.bubble { padding: 8px 10px; border-radius: 10px; font-size: .9em; }
.bubble.user { background: var(--primary-soft); align-self: flex-end; max-width: 90%; }
.bubble.assistant { background: var(--card-soft); max-width: 95%; }
.b-role { font-size: .75em; color: var(--ink-faint); margin-bottom: 2px; }
.b-content { white-space: pre-wrap; word-break: break-word; }
.chat-input-row { display: flex; gap: 8px; }
.chat-input-row input { flex: 1; }
.apply-row { display: flex; gap: 8px; }
</style>

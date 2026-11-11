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
  coverUrl?: string
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
const previewBox = ref<HTMLElement | null>(null)
const editorReady = ref(false)
const coverInput = ref<HTMLInputElement | null>(null)
let VditorCtor: any = null
let vditor: {
  getValue(): string
  setValue(v: string): void
  insertValue(v: string): void
} | null = null
let pendingContent = ''

// 右侧文档面板状态
const docOpen = ref(true)
const docTab = ref<'preview' | 'edit'>('preview')
const seoOpen = ref(false)

// 对话流状态
const chat = ref<ChatMsg[]>([])
const chatInput = ref('')
const aiBusy = ref(false)
const carryArticle = ref(true)
const chatBody = ref<HTMLElement | null>(null)

// 文章切换下拉
const swOpen = ref(false)
const swFilter = ref('')

// AI 配图状态
const imgBusy = ref(false)
const imgVisible = ref(false)
const imgGenUrl = ref('')
const imgGenPrompt = ref('')

const reviewVisible = ref(false)
const reviewLoading = ref(false)
const review = ref<ReviewReport | null>(null)

const filteredRows = computed(() => {
  const k = swFilter.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter((r) => r.title.toLowerCase().includes(k))
})

async function loadList() {
  loadingList.value = true
  try {
    const p = await $api<{ records: ArticleRow[] }>('GET', '/studio/articles?page=1&size=30')
    rows.value = p.records
    if (!current.value && p.records.length) await openArticle(p.records[0])
  } catch (e) { ElMessage.error((e as Error).message) } finally { loadingList.value = false }
}

function currentMd(): string {
  if (editorReady.value && vditor) return vditor.getValue() ?? ''
  return pendingContent || ''
}

async function refreshPreview() {
  if (docTab.value !== 'preview' || !previewBox.value || !VditorCtor) return
  try {
    await VditorCtor.preview(previewBox.value, currentMd(), {
      cdn: '/vditor',
      theme: { current: 'dark', path: '/vditor/dist/css/content-theme' },
    })
  } catch { /* 预览失败不打断 */ }
}

watch(docTab, (t) => { if (t === 'preview') nextTick(refreshPreview) })
watch(docOpen, (v) => { if (v) nextTick(refreshPreview) })

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
  nextTick(refreshPreview)
}

function newArticle() {
  current.value = null
  title.value = ''
  status.value = ''
  tagsText.value = ''; coverUrl.value = ''
  seoDescription.value = ''; geoSummary.value = ''
  pendingContent = ''
  if (editorReady.value) vditor?.setValue('')
  nextTick(refreshPreview)
}

function pickArticle(row: ArticleRow) {
  swOpen.value = false
  if (current.value?.id !== row.id) openArticle(row)
}

async function initEditor() {
  const [{ default: Vditor }] = await Promise.all([
    import('vditor'),
    import('vditor/dist/index.css'),
  ])
  VditorCtor = Vditor
  vditor = new Vditor(editorBox.value!, {
    cdn: '/vditor',
    height: '100%',
    mode: 'ir',
    theme: 'dark',
    cache: { enable: false },
    placeholder: '微调正文…',
    preview: {
      theme: { current: 'dark', path: '/vditor/dist/css/content-theme' },
      hljs: { style: 'github-dark', lineNumber: false },
    },
    toolbar: [
      'undo', 'redo', '|', 'headings', 'bold', 'italic', 'strike', '|',
      'list', 'ordered-list', 'check', 'quote', '|',
      'code-block', 'inline-code', 'link', 'table', 'upload', '|', 'edit-mode',
    ],
    after: () => {
      editorReady.value = true
      if (pendingContent) vditor?.setValue(pendingContent)
      nextTick(refreshPreview)
    },
    // Vditor 3.11：handler 返回值不会自动插入编辑器，须显式 insertValue
    upload: {
      handler: async (files: File[]): Promise<string[]> => {
        for (const f of files) {
          try {
            const url = await uploadMediaOnly(f)
            const isVideo = (f.type || '').startsWith('video/')
            vditor?.insertValue(isVideo ? `[🎬 视频](${url})` : `![](${url})`)
            refreshPreview()
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
      coverUrl: coverUrl.value || null,
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
  if (!current.value) { ElMessage.warning('先选择或保存一篇文章'); return }
  reviewLoading.value = true
  reviewVisible.value = true
  review.value = null
  try { review.value = await $api<ReviewReport>('POST', `/studio/articles/${current.value.id}/ai-review`) }
  catch (e) { ElMessage.error((e as Error).message) } finally { reviewLoading.value = false }
}

async function aiSummary() {
  const md = currentMd()
  if (!md.trim()) { ElMessage.warning('正文为空'); return }
  try {
    const r = await $api<{ seoDescription: string; geoSummary: string; tags: string[] }>(
      'POST', '/ai/summary', { content: md })
    seoDescription.value = r.seoDescription
    geoSummary.value = r.geoSummary
    if (r.tags?.length) tagsText.value = r.tags.join(', ')
    seoOpen.value = true
    ElMessage.success('SEO/GEO 摘要与标签已生成')
  } catch (e) { ElMessage.error((e as Error).message) }
}

/** AI 配图：生成 → 预览弹窗 → 设为封面 / 插入正文 */
async function aiImage() {
  let desc = ''
  try {
    const r = await ElMessageBox.prompt('描述想要的画面，中英文都可以', 'AI 配图', {
      inputValue: title.value ? `为文章《${title.value}》生成一张封面插画` : '',
      confirmButtonText: '生成',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '描述不能为空',
    })
    desc = r.value
  } catch { return }
  imgVisible.value = true
  imgBusy.value = true
  imgGenUrl.value = ''
  imgGenPrompt.value = desc
  try {
    const r = await $api<{ mediaId: number; url: string }>('POST', '/ai/images', {
      prompt: desc,
      size: '1024x1024',
    })
    imgGenUrl.value = r.url
  } catch (e) {
    imgVisible.value = false
    ElMessage.error((e as Error).message)
  } finally { imgBusy.value = false }
}

function useAsCover() {
  coverUrl.value = imgGenUrl.value
  imgVisible.value = false
  ElMessage.success('已设为封面，保存或发布后生效')
}

function insertImageToBody() {
  if (!vditor) return
  vditor.insertValue(`![${imgGenPrompt.value}](${imgGenUrl.value})`)
  imgVisible.value = false
  refreshPreview()
  ElMessage.success('已插入正文')
}

function scrollChat() {
  requestAnimationFrame(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

function currentArticleContext(): string {
  if (!carryArticle.value || !current.value) return ''
  const md = currentMd()
  if (!md.trim()) return ''
  return `\n\n【当前文章草稿（标题：${title.value}）】\n${md.slice(0, 3000)}`
}

async function send(text?: string) {
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
    await streamChat(
      [...history, { role: 'user', content: msg + ctx }],
      (t) => {
        chat.value[idx].content += t
        scrollChat()
      },
    )
  } catch (e) {
    chat.value[idx].content += `\n⚠ ${(e as Error).message}`
  } finally {
    aiBusy.value = false
    scrollChat()
  }
}

async function quick(command: string, apply: 'replace' | 'append' | 'title' | 'none') {
  const md = currentMd()
  if (!md.trim() && apply !== 'title') { ElMessage.warning('正文为空'); return }
  aiBusy.value = true
  chat.value.push({ role: 'user', content: command })
  chat.value.push({ role: 'assistant', content: '' })
  const idx = chat.value.length - 1
  scrollChat()
  try {
    const history = chat.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
    await streamChat('assistant', `请${command}${md ? `\n\n【当前文章草稿】\n${md.slice(0, 3000)}` : ''}`, (t) => {
      chat.value[idx].content += t
      scrollChat()
    })
    const reply = chat.value[idx].content
    if (apply === 'replace' && vditor) vditor.setValue(reply)
    else if (apply === 'append' && vditor) vditor.setValue((vditor.getValue() || '') + '\n\n' + reply)
    else if (apply === 'title') {
      const first = reply.split('\n')[0].replace(/^[#\s\d.、-]*/, '').trim().slice(0, 60)
      if (first) title.value = first
    }
    if (apply === 'replace' || apply === 'append') refreshPreview()
  } catch (e) {
    chat.value[idx].content += `\n⚠ ${(e as Error).message}`
  } finally { aiBusy.value = false; scrollChat() }
}

async function applyToEditor(mode: 'replace' | 'append') {
  const last = [...chat.value].reverse().find((m) => m.role === 'assistant')
  if (!last || !vditor) { ElMessage.warning('暂无 AI 输出'); return }
  if (mode === 'replace') vditor.setValue(last.content)
  else vditor.setValue((vditor.getValue() || '') + '\n\n' + last.content)
  refreshPreview()
  ElMessage.success(mode === 'replace' ? '已替换正文' : '已追加到文末')
}

function autoGrow(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}

function onDocClick(e: MouseEvent) {
  if (!swOpen.value) return
  if (!(e.target as HTMLElement).closest('.switcher')) swOpen.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

onMounted(async () => {
  await initEditor()
  await loadList()
})
</script>

<template>
  <div class="wb">
    <!-- 顶栏：文章切换 + 操作 -->
    <header class="wb-top">
      <div class="switcher">
        <button class="sw-btn" @click.stop="swOpen = !swOpen">
          <svg class="sw-doc" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <rect x="4.5" y="3" width="15" height="18" rx="2.5" />
            <path d="M8.5 8h7M8.5 12h7M8.5 16h4.5" />
          </svg>
          <span class="sw-title">{{ current?.title || '未命名草稿' }}</span>
          <span v-if="current" class="st" :class="'st-' + current.status">{{ STATUS_TEXT[current.status] }}</span>
          <svg class="sw-chev" :class="{ rot: swOpen }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 9l6 6 6-6" /></svg>
        </button>
        <Transition name="pop">
          <div v-if="swOpen" class="sw-panel" @click.stop>
            <input v-model="swFilter" placeholder="搜索文章…" class="sw-search">
            <button class="sw-item new" @click="newArticle(); swOpen = false">
              <span class="sw-plus">＋</span> 新文章
            </button>
            <div class="sw-list">
              <button
                v-for="r in filteredRows"
                :key="r.id"
                class="sw-item"
                :class="{ active: current?.id === r.id }"
                @click="pickArticle(r)"
              >
                <span class="sw-item-title">{{ r.title }}</span>
                <span class="sw-item-meta">{{ STATUS_TEXT[r.status] || r.status }} · {{ (r.publishedAt || '').slice(0, 10) }}</span>
              </button>
              <div v-if="!filteredRows.length" class="muted" style="padding:10px 12px">无匹配文章</div>
            </div>
          </div>
        </Transition>
      </div>
      <div class="wb-actions">
        <button class="btn ghost" @click="runReview">AI 评审</button>
        <button class="btn ghost" @click="save('DRAFT')">存草稿</button>
        <button class="btn primary" :disabled="saving" @click="save('PUBLISH')">
          {{ status === 'PUBLISHED' ? '更新发布' : '发布' }}
        </button>
        <button class="btn icon" :class="{ on: docOpen }" title="预览面板" @click="docOpen = !docOpen">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
            <path d="M14.5 4.5v15" />
          </svg>
        </button>
      </div>
    </header>

    <div class="wb-body">
      <!-- 主舞台：AI 创作流 -->
      <section class="pane chat">
        <div class="chat-head">
          <span class="pane-label">AI 创作流</span>
          <button class="chip" :class="{ on: carryArticle }" title="对话时自动携带当前文章草稿作为上下文" @click="carryArticle = !carryArticle">
            <span class="chip-dot" /> 携带文章
          </button>
        </div>
        <div ref="chatBody" class="chat-body">
          <div v-if="!chat.length" class="hero">
            <div class="hero-badge">INKPRESS STUDIO</div>
            <h1 class="hero-title grad-title">让 AI 和你一起写作</h1>
            <p class="hero-sub">顶栏切换文章即自动带入上下文；AI 可润色、起标题、写摘要、配封面。</p>
            <div class="hero-chips">
              <button @click="send('帮我润色这篇文章的开头，使其更有吸引力')">润色开头</button>
              <button @click="send('基于当前文章，帮我想 5 个更有吸引力的标题')">想标题</button>
              <button @click="aiImage()">生成封面插画</button>
              <button @click="aiSummary()">摘要 + 标签</button>
            </div>
          </div>
          <template v-else>
            <div v-for="(m, i) in chat" :key="i" class="bubble" :class="m.role">
              <div class="b-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
              <div class="b-content">
                <template v-if="m.content">{{ m.content }}</template>
                <span v-else-if="i === chat.length - 1 && aiBusy" class="dots"><i /><i /><i /></span>
                <template v-else>…</template>
              </div>
              <div
                v-if="m.role === 'assistant' && m.content && i === chat.length - 1 && !aiBusy"
                class="b-actions"
              >
                <button @click="applyToEditor('replace')">替换正文</button>
                <button @click="applyToEditor('append')">追加到文末</button>
              </div>
            </div>
          </template>
        </div>
        <div class="quick-row">
          <button class="chip" :disabled="aiBusy" @click="quick('润色全文并保持原有结构','replace')">润色全文</button>
          <button class="chip" :disabled="aiBusy" @click="quick('基于全文起一个更吸引人的标题','title')">起标题</button>
          <button class="chip" :disabled="aiBusy" @click="aiSummary()">摘要+标签</button>
          <button class="chip" :disabled="aiBusy" @click="aiImage()">AI 配图</button>
        </div>
        <div class="composer">
          <textarea
            v-model="chatInput"
            rows="1"
            placeholder="向 AI 下指令，Enter 发送，Shift+Enter 换行…"
            :disabled="aiBusy"
            @input="autoGrow"
            @keydown.enter.exact.prevent="send()"
          />
          <button class="send" :disabled="aiBusy || !chatInput.trim()" @click="send()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 19V5M5 12l7-7 7 7" />
            </svg>
          </button>
        </div>
      </section>

      <!-- 文档面板：预览 / 微调 -->
      <aside class="pane doc" :class="{ closed: !docOpen }">
        <div class="doc-inner">
          <div class="doc-head">
            <div class="tabs">
              <button :class="{ on: docTab === 'preview' }" @click="docTab = 'preview'">预览</button>
              <button :class="{ on: docTab === 'edit' }" @click="docTab = 'edit'">微调</button>
            </div>
            <span class="doc-hint">Vditor · 预览与微调</span>
          </div>
          <div class="doc-body">
            <div v-show="docTab === 'preview'" class="doc-preview">
              <div ref="previewBox" />
              <div v-if="!currentMd() && editorReady" class="muted" style="padding:24px;text-align:center">暂无正文，去对话里让 AI 写一段吧</div>
            </div>
            <div v-show="docTab === 'edit'" ref="editorBox" class="doc-editor" />
          </div>
          <div class="doc-meta">
            <div class="meta-line">
              <img v-if="coverUrl" :src="coverUrl" class="cover-thumb" alt="cover">
              <span v-else class="meta-none">无封面</span>
              <button class="chip" @click="coverInput?.click()">上传封面</button>
              <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverUpload">
              <button class="chip" :disabled="imgBusy" @click="aiImage()">AI 配图</button>
            </div>
            <div class="meta-line">
              <input v-model="tagsText" class="tags-input" placeholder="标签，逗号分隔">
              <button class="chip" @click="seoOpen = !seoOpen">SEO</button>
            </div>
            <div v-if="seoOpen" class="meta-line">
              <input v-model="seoDescription" placeholder="SEO meta description">
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- AI 配图结果 -->
    <el-dialog v-model="imgVisible" title="AI 配图" width="480">
      <div v-loading="imgBusy" style="min-height:200px">
        <img v-if="imgGenUrl" :src="imgGenUrl" style="width:100%;border-radius:10px" alt="AI 生成图">
        <div v-else-if="!imgBusy" class="muted" style="padding:40px;text-align:center">生成失败</div>
      </div>
      <template #footer>
        <el-button @click="imgVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!imgGenUrl" @click="useAsCover">设为封面</el-button>
        <el-button :disabled="!imgGenUrl || !editorReady" @click="insertImageToBody">插入正文</el-button>
      </template>
    </el-dialog>

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
/* ===== 工作台：AI 为主舞台，Vditor 为预览/微调面板 ===== */
.wb {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100vh;
  padding: 16px 18px;
  min-width: 0;
}
.wb-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 14px;
}

/* ===== 顶栏 ===== */
.wb-top { display: flex; gap: 12px; align-items: center; justify-content: space-between; }
.switcher { position: relative; min-width: 0; }
.sw-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 560px;
  padding: 9px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--scard);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--ink);
  transition: border-color .18s ease, box-shadow .18s ease;
}
.sw-btn:hover { border-color: rgba(138, 180, 248, .45); box-shadow: 0 0 0 3px rgba(138, 180, 248, .1); }
.sw-doc { width: 17px; height: 17px; color: var(--primary); flex-shrink: 0; }
.sw-title { font-weight: 650; font-size: .95em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sw-chev { width: 15px; height: 15px; color: var(--ink-faint); transition: transform .2s ease; flex-shrink: 0; }
.sw-chev.rot { transform: rotate(180deg); }
.st {
  flex-shrink: 0;
  font-size: .72em;
  padding: 2px 9px;
  border-radius: 999px;
  letter-spacing: .5px;
}
.st-DRAFT { background: rgba(154, 163, 181, .16); color: #aab2c4; }
.st-PUBLISHED { background: rgba(129, 201, 149, .15); color: #81c995; }
.st-PENDING_REVIEW { background: rgba(248, 187, 102, .15); color: #f8bb66; }
.st-REJECTED { background: rgba(242, 139, 130, .15); color: #f28b82; }
.st-OFFLINE { background: rgba(154, 163, 181, .16); color: #aab2c4; }

.sw-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 380px;
  max-width: 76vw;
  z-index: 50;
  background: #141826f2;
  border: 1px solid var(--sline);
  border-radius: 14px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, .5);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  padding: 10px;
}
.pop-enter-active, .pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(-6px); }
.sw-search { margin-bottom: 8px; }
.sw-list { max-height: 320px; overflow-y: auto; }
.sw-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  text-align: left;
  padding: 9px 12px;
  border: 0;
  background: transparent;
  border-radius: 10px;
  color: var(--ink-soft);
  transition: background .15s ease, color .15s ease;
}
.sw-item:hover { background: rgba(255, 255, 255, .05); color: var(--ink); }
.sw-item.active { background: var(--primary-soft); color: var(--primary); }
.sw-item.new { color: var(--primary); font-weight: 600; margin-bottom: 4px; }
.sw-item.new:hover { background: var(--primary-soft); }
.sw-plus { font-weight: 700; }
.sw-item-title { font-weight: 550; font-size: .92em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sw-item-meta { font-size: .75em; color: var(--ink-faint); font-family: ui-monospace, "Cascadia Code", Consolas, monospace; }

.wb-actions { display: flex; gap: 8px; align-items: center; flex-shrink: 0; }
.btn {
  padding: 8px 16px;
  border-radius: 11px;
  font-size: .88em;
  font-weight: 600;
  border: 1px solid var(--line);
  background: var(--scard);
  color: var(--ink-soft);
  transition: all .18s ease;
}
.btn:hover { color: var(--ink); border-color: rgba(138, 180, 248, .45); transform: translateY(-1px); }
.btn.primary {
  background: linear-gradient(135deg, var(--sprimary-deep), #9a7bf5);
  border: 0;
  color: #fff;
  box-shadow: 0 4px 16px rgba(91, 141, 239, .32);
}
.btn.primary:hover { box-shadow: 0 6px 22px rgba(138, 180, 248, .45); filter: brightness(1.08); }
.btn.primary:disabled { opacity: .5; filter: none; transform: none; }
.btn.icon { width: 38px; height: 38px; padding: 0; display: grid; place-items: center; }
.btn.icon svg { width: 18px; height: 18px; }
.btn.icon.on { color: var(--primary); border-color: rgba(138, 180, 248, .45); background: var(--primary-soft); }

/* ===== 面板通用 ===== */
.pane {
  background: var(--scard);
  border: 1px solid var(--sline);
  border-radius: 18px;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: 0 12px 44px rgba(0, 0, 0, .35);
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.pane-label {
  font-size: .78em;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: var(--ink-faint);
}

/* ===== 聊天主舞台 ===== */
.chat { flex: 1.15; padding: 16px 18px; }
.chat-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  font-size: .8em;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-faint);
  transition: all .18s ease;
  white-space: nowrap;
}
.chip:hover:not(:disabled) { color: var(--ink); border-color: rgba(138, 180, 248, .4); }
.chip.on { color: var(--primary); border-color: rgba(138, 180, 248, .5); background: var(--primary-soft); }
.chip:disabled { opacity: .45; }
.chip-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }

.chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 6px 2px;
}
.hero {
  margin: auto;
  text-align: center;
  padding: 30px 20px;
}
.hero-badge {
  display: inline-block;
  font-size: .68em;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--primary);
  border: 1px solid rgba(138, 180, 248, .35);
  border-radius: 999px;
  padding: 4px 14px;
  margin-bottom: 18px;
  animation: rise .4s ease both;
}
.hero-title { font-size: 2em; font-weight: 800; margin: 0 0 10px; animation: rise .45s ease .05s both; }
.hero-sub { color: var(--ink-faint); font-size: .92em; margin: 0 0 22px; animation: rise .5s ease .1s both; }
.hero-chips { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; animation: rise .55s ease .15s both; }
.hero-chips button {
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .03);
  color: var(--ink-soft);
  font-size: .86em;
  transition: all .18s ease;
}
.hero-chips button:hover {
  color: var(--primary);
  border-color: rgba(138, 180, 248, .55);
  background: var(--primary-soft);
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(91, 141, 239, .18);
}
@keyframes rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

.bubble {
  padding: 11px 14px;
  border-radius: 14px;
  font-size: .92em;
  line-height: 1.7;
  animation: rise .28s cubic-bezier(.2, .7, .3, 1) both;
  max-width: 92%;
}
.bubble.user {
  align-self: flex-end;
  background: linear-gradient(135deg, rgba(91, 141, 239, .28), rgba(154, 123, 245, .22));
  border: 1px solid rgba(138, 180, 248, .3);
  border-bottom-right-radius: 5px;
}
.bubble.assistant {
  align-self: flex-start;
  background: rgba(255, 255, 255, .04);
  border: 1px solid var(--sline);
  border-bottom-left-radius: 5px;
}
.b-role { font-size: .72em; color: var(--ink-faint); margin-bottom: 3px; letter-spacing: 1px; }
.b-content { white-space: pre-wrap; word-break: break-word; }
.b-actions { display: flex; gap: 8px; margin-top: 9px; }
.b-actions button {
  font-size: .76em;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(138, 180, 248, .4);
  background: transparent;
  color: var(--primary);
  transition: all .15s ease;
}
.b-actions button:hover { background: var(--primary-soft); transform: translateY(-1px); }

.dots { display: inline-flex; gap: 5px; padding: 4px 0; }
.dots i {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--primary);
  opacity: .4;
  animation: blink 1.2s ease-in-out infinite;
}
.dots i:nth-child(2) { animation-delay: .18s; }
.dots i:nth-child(3) { animation-delay: .36s; }
@keyframes blink { 50% { opacity: 1; transform: translateY(-2px); } }

.quick-row { display: flex; gap: 8px; padding: 10px 0 8px; flex-wrap: wrap; }
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(0, 0, 0, .25);
  transition: border-color .2s ease, box-shadow .2s ease;
}
.composer:focus-within {
  border-color: rgba(138, 180, 248, .55);
  box-shadow: 0 0 0 3px rgba(138, 180, 248, .12);
}
.composer textarea {
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--ink);
  resize: none;
  padding: 6px 4px;
  max-height: 150px;
  line-height: 1.6;
  outline: none;
  font-size: .92em;
}
.composer textarea:focus { outline: none; box-shadow: none; border: 0; }
.send {
  width: 38px; height: 38px;
  flex-shrink: 0;
  display: grid; place-items: center;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--sprimary-deep), #9a7bf5);
  box-shadow: 0 4px 14px rgba(91, 141, 239, .35);
  transition: all .18s ease;
}
.send svg { width: 17px; height: 17px; }
.send:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(138, 180, 248, .5); }
.send:disabled { opacity: .4; }

/* ===== 文档面板 ===== */
.doc {
  flex: 0 0 clamp(380px, 44%, 600px);
  overflow: hidden;
  transition: flex-basis .35s cubic-bezier(.2, .7, .3, 1), opacity .3s ease;
}
.doc.closed { flex-basis: 0%; opacity: 0; border-width: 0; pointer-events: none; }
.doc-inner {
  width: clamp(380px, 100%, 600px);
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 10px;
}
.doc-head { display: flex; justify-content: space-between; align-items: center; }
.tabs { display: flex; gap: 4px; background: rgba(0, 0, 0, .28); border-radius: 11px; padding: 3px; }
.tabs button {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  font-size: .84em;
  font-weight: 600;
  padding: 6px 18px;
  border-radius: 9px;
  transition: all .18s ease;
}
.tabs button:hover { color: var(--ink); }
.tabs button.on {
  color: var(--ink);
  background: rgba(138, 180, 248, .16);
  box-shadow: inset 0 0 0 1px rgba(138, 180, 248, .3);
}
.doc-hint { font-size: .74em; color: var(--ink-faint); font-family: ui-monospace, "Cascadia Code", Consolas, monospace; }

.doc-body { flex: 1; min-height: 0; display: flex; }
.doc-preview {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  border: 1px solid var(--sline);
  border-radius: 12px;
  background: rgba(0, 0, 0, .2);
  padding: 4px;
}
.doc-preview > div { padding: 12px 16px; }
.doc-editor { flex: 1; min-width: 0; min-height: 0; }

/* 预览内容暗色适配 */
:deep(.vditor-reset) { color: var(--ink); font-size: 14.5px; line-height: 1.8; background: transparent; }
:deep(.vditor-reset pre), :deep(.vditor-reset code) { background: var(--code-bg); }
:deep(.vditor-reset a) { color: var(--primary); }
:deep(.vditor-reset blockquote) { border-left-color: var(--primary); }
:deep(.vditor-reset img) { max-width: 100%; border-radius: 8px; }
:deep(.doc-editor .vditor) { border: 1px solid var(--sline); border-radius: 12px; background: rgba(0, 0, 0, .2); }
:deep(.doc-editor .vditor-toolbar) { background: rgba(20, 24, 38, .9); border-bottom: 1px solid var(--sline); }
:deep(.doc-editor .vditor-content) { background: transparent; }

/* ===== 文档元信息 ===== */
.doc-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid var(--sline);
  padding-top: 10px;
}
.meta-line { display: flex; gap: 8px; align-items: center; }
.meta-line input { flex: 1; min-width: 0; }
.meta-none { font-size: .78em; color: var(--ink-faint); }
.cover-thumb { height: 32px; border-radius: 7px; border: 1px solid var(--sline); }

/* ===== 窄屏 ===== */
@media (max-width: 1100px) {
  .wb { height: auto; min-height: 100vh; }
  .wb-body { flex-direction: column; }
  .chat { min-height: 480px; }
  .doc { flex: 0 0 auto; }
  .doc.closed { display: none; }
  .doc-inner { width: 100%; }
  .doc-body { min-height: 360px; }
}
</style>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api, accessToken } from '~/composables/useApi'
import { streamChat } from '~/composables/useAiStream'
import { useTheme } from '~/composables/useTheme'
import { renderAiMd } from '~/utils/aiMd'

const route = useRoute()

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

// 主题（与博客全局主题联动）：驱动 Vditor 编辑器/预览配色
const { theme: themeRef } = useTheme()

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

// 中央文档面板状态（预览/微调）与 VS Code 式「生成代码」内联对话框
const docOpen = ref(true)
const olOpen = ref(true)
const docTab = ref<'preview' | 'edit'>('preview')
const seoOpen = ref(false)
const genOpen = ref(false)
const genInput = ref('')
const genBusy = ref(false)
const genResult = ref('')
const genModel = ref('')
let genAbort = false

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

// 文章大纲：解析正文标题（跳过代码块），点击跳预览对应位置
interface OutlineItem { level: number; text: string; occ: number }
const outline = ref<OutlineItem[]>([])

function updateOutline() {
  const md = currentMd()
  const items: OutlineItem[] = []
  const occ: Record<string, number> = {}
  let inCode = false
  for (const line of md.split('\n')) {
    const t = line.trim()
    if (t.startsWith('```')) { inCode = !inCode; continue }
    if (inCode) continue
    const m = /^(#{1,6})\s+(.+)$/.exec(t)
    if (m) {
      const text = m[2].replace(/[#\s]+$/, '').trim()
      if (!text) continue
      occ[text] = (occ[text] || 0) + 1
      items.push({ level: m[1].length, text, occ: occ[text] })
    }
  }
  outline.value = items
}

function jumpHeading(o: OutlineItem) {
  docTab.value = 'preview'
  nextTick(() => {
    const box = previewBox.value
    if (!box) return
    const hs = Array.from(box.querySelectorAll('h1,h2,h3,h4,h5,h6'))
      .filter((h) => (h.textContent || '').trim() === o.text)
    const el = (hs[o.occ - 1] || hs[0]) as HTMLElement | undefined
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      el.classList.add('ol-flash')
      setTimeout(() => el.classList.remove('ol-flash'), 1300)
    }
  })
}

async function refreshPreview() {
  updateOutline()
  if (docTab.value !== 'preview' || !previewBox.value || !VditorCtor) return
  try {
    await VditorCtor.preview(previewBox.value, currentMd(), {
      cdn: '/vditor',
      theme: { current: themeRef.value, path: '/vditor/dist/css/content-theme' },
    })
  } catch { /* 预览失败不打断 */ }
}

watch(docTab, () => nextTick(refreshPreview))
watch(docOpen, (v) => { if (v) nextTick(refreshPreview) })

// 主题切换：编辑器与预览同步换肤
watch(themeRef, (t) => {
  try {
    (vditor as any)?.setTheme?.(t, undefined, t, t === 'dark' ? 'github-dark' : 'github')
  } catch { /* 旧版本无此方法则忽略 */ }
  nextTick(refreshPreview)
})

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
  const vt = themeRef.value
  vditor = new Vditor(editorBox.value!, {
    cdn: '/vditor',
    height: '100%',
    mode: 'ir',
    theme: vt,
    cache: { enable: false },
    placeholder: '微调正文…',
    preview: {
      theme: { current: vt, path: '/vditor/dist/css/content-theme' },
      hljs: { style: vt === 'dark' ? 'github-dark' : 'github', lineNumber: false },
    },
    toolbar: [
      'undo', 'redo', '|', 'headings', 'bold', 'italic', 'strike', '|',
      'list', 'ordered-list', 'check', 'quote', '|',
      'code-block', 'inline-code', 'link', 'table', 'math', 'upload', '|', 'edit-mode',
    ],
    math: {
      engine: 'KaTeX',
      src: '/vditor/dist/js/katex/katex.min.js',
    },
    after: () => {
      editorReady.value = true
      if (pendingContent) vditor?.setValue(pendingContent)
      nextTick(refreshPreview)
    },
    input: () => { updateOutline() },
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

async function save(action?: string, confirmLowScore?: boolean, silent = false) {
  if (!title.value.trim()) { if (!silent) ElMessage.warning('标题必填'); return false }
  saving.value = true
  try {
    const saved = await $api<{ id: number; status: string }>('POST', '/studio/articles', {
      id: current.value?.id ?? null,
      title: title.value.trim(),
      contentMd: vditor?.getValue() ?? '',
      tags: tagsText.value.split(/[,，]/).map((t) => t.trim()).filter(Boolean),
      coverUrl: coverUrl.value || null,
      action: action ?? 'DRAFT',
      confirmLowScore,
    })
    // 新建文章后绑定当前行，后续自动保存走更新路径
    if (!current.value && saved.id) {
      const row = rows.value.find((r) => r.id === saved.id)
      if (row) current.value = row
    }
    if (current.value) {
      status.value = saved.status
      current.value.status = saved.status
      const row = rows.value.find((r) => r.id === current.value!.id)
      if (row) row.status = saved.status
    }
    if (!silent) ElMessage.success(action === 'PUBLISH' ? '已发布' : '草稿已保存')
    lastSavedSnapshot = autoSnapshot()
    return true
  } catch (e) {
    const msg = (e as Error).message
    if (msg.includes('2005') || msg.includes('评审分数偏低')) {
      try {
        const report = await $api<ReviewReport>('GET', `/studio/articles/${current.value?.id}/ai-review/latest`).catch(() => null)
        await ElMessageBox.confirm(
          `${msg}${report ? `（评审 ${report.totalScore} 分）` : ''}\n确认仍然发布？`, '低分发布确认', { type: 'warning' })
        return await save(action, true, silent)
      } catch { /* 取消 */ }
    } else if (!silent) {
      ElMessage.error(msg)
    }
    return false
  } finally { saving.value = false }
}

// ===== 草稿自动保存（开题模块二）：30s 检查脏快照，有变化且标题非空才静默落库 =====
let lastSavedSnapshot = ''
let autoSaveTimer: ReturnType<typeof setInterval> | null = null
const lastAutoSavedAt = ref('')

function autoSnapshot(): string {
  return JSON.stringify([title.value, current.value?.id ?? 0, tagsText.value, currentMd(), coverUrl.value])
}

function startAutoSave() {
  if (autoSaveTimer) return
  lastSavedSnapshot = autoSnapshot()
  autoSaveTimer = setInterval(() => {
    if (saving.value || aiBusy.value) return
    if (!current.value || !title.value.trim()) return
    const snap = autoSnapshot()
    if (snap === lastSavedSnapshot) return
    save('DRAFT', undefined, true).then((ok) => {
      if (ok) {
        lastAutoSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
        // 首次自动保存前可能尚未建文，保存成功即有 id
      }
    })
  }, 30_000)
}

onMounted(startAutoSave)
onBeforeUnmount(() => { if (autoSaveTimer) clearInterval(autoSaveTimer) })

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
    const prompt = `请${command}${md ? `\n\n【当前文章草稿】\n${md.slice(0, 3000)}` : ''}`
    await streamChat([...history, { role: 'user', content: prompt }], (t) => {
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

// ===== VS Code 式「生成代码」内联对话框 =====
function openGen() {
  genOpen.value = true
  genInput.value = ''
  genResult.value = ''
  genAbort = false
  nextTick(() => (document.querySelector('.gen-input') as HTMLInputElement | null)?.focus())
  // 模型徽章：管理员可读文本槽模型名，失败降级为「AI」
  $api<{ capabilities: Record<string, { model?: string }> }>('GET', '/admin/ai-config')
    .then((c) => { genModel.value = c.capabilities?.text?.model || 'AI' })
    .catch(() => { genModel.value = 'AI' })
}

function closeGen() {
  genOpen.value = false
  genAbort = true
  genInput.value = ''
  genResult.value = ''
}

/** 生成：按指令流式产出 Markdown（默认代码块语境），完成后由用户决定插入光标处或丢弃 */
async function runGen() {
  const cmd = genInput.value.trim()
  if (!cmd || genBusy.value) return
  genBusy.value = true
  genResult.value = ''
  genAbort = false
  const md = currentMd()
  const instruction = `请为以下场景生成代码/内容（输出 Markdown，代码用围栏代码块包裹，不要解释性废话）：\n${cmd}` +
    (md ? `\n\n【供参考的上下文】\n${md.slice(0, 2400)}` : '')
  try {
    const history = [{ role: 'user', content: instruction }]
    await streamChat(history, (t) => {
      if (genAbort) return
      genResult.value += t
    }, 'assistant')
  } catch (e) {
    genResult.value += `\n⚠ ${(e as Error).message}`
  } finally { genBusy.value = false }
}

function insertGen() {
  if (!vditor || !genResult.value.trim()) return
  vditor.insertValue(genResult.value.trim() + '\n')
  refreshPreview()
  ElMessage.success('已插入光标处')
  closeGen()
}

/** 按提纲续写（开题模块三）：把正文标题树作为提纲交给 AI，续写补全未完成部分并追加到文末 */
async function continueByOutline() {
  updateOutline()
  if (!outline.value.length) { ElMessage.warning('正文还没有标题提纲（# 开头的行）'); return }
  const ol = outline.value.map((o) => '  '.repeat(o.level - 1) + '- ' + o.text).join('\n')
  const md = currentMd()
  await quick(`按以下提纲续写这篇文章：判断每个小节当前是否已有内容，只为空缺或过薄的小节补写 2-4 段内容（Markdown，沿用既有标题层级，不要重复已有文字），输出仅包含新增部分。\n\n【提纲】\n${ol}${md ? `\n\n【当前文章草稿】\n${md.slice(0, 3000)}` : ''}`, 'append')
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
  // 支持外部直达：/studio/workbench?article=<id>（文章页「编辑此文」入口）
  const qid = Number(route.query.article)
  if (qid && (!current.value || current.value.id !== qid)) {
    const row = rows.value.find((r) => r.id === qid)
    if (row) await openArticle(row)
  }
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
        <button class="btn icon" :class="{ on: olOpen }" title="大纲面板" @click="olOpen = !olOpen">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4.5 6.5h9M4.5 12h15M4.5 17.5h6" />
            <circle cx="18.5" cy="6.5" r="1.6" />
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
            <h1 class="hero-title">让 AI 和你一起写作</h1>
            <p class="hero-sub">顶栏切换文章即自动带入上下文；选一张卡片开始。</p>
            <div class="cmd-grid">
              <button class="cmd lavender" @click="send('帮我润色这篇文章的开头，使其更有吸引力')">
                <span class="cmd-title">润色开头</span>
                <span class="cmd-sub">从第一段入手，提升吸引力与节奏</span>
                <span class="cmd-foot"><span class="pill">开始</span><span class="go">›</span></span>
                <i class="blob b1" /><i class="blob b2" />
              </button>
              <button class="cmd orange" @click="send('基于当前文章，帮我想 5 个更有吸引力的标题')">
                <span class="cmd-title">想标题</span>
                <span class="cmd-sub">基于全文一口气出 5 个候选</span>
                <span class="cmd-foot"><span class="pill">开始</span><span class="go">›</span></span>
                <i class="blob b1" /><i class="blob b2" />
              </button>
              <button class="cmd lime" @click="aiImage()">
                <span class="cmd-title">生成封面插画</span>
                <span class="cmd-sub">一句话描述画面，即出 1024px 成图</span>
                <span class="cmd-foot"><span class="pill">开始</span><span class="go">›</span></span>
                <i class="blob b1" /><i class="blob b2" />
              </button>
              <button class="cmd yellow" @click="aiSummary()">
                <span class="cmd-title">摘要 + 标签</span>
                <span class="cmd-sub">SEO 描述、GEO 摘要与标签一次生成</span>
                <span class="cmd-foot"><span class="pill">开始</span><span class="go">›</span></span>
                <i class="blob b1" /><i class="blob b2" />
              </button>
            </div>
          </div>
          <template v-else>
            <div v-for="(m, i) in chat" :key="i" class="bubble" :class="m.role">
              <div class="b-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
              <div
                v-if="m.content && !(i === chat.length - 1 && aiBusy)"
                class="b-content md"
                v-html="renderAiMd(m.content)"
              />
              <div v-else-if="m.content" class="b-content">{{ m.content }}</div>
              <div v-else-if="i === chat.length - 1 && aiBusy" class="dots"><i /><i /><i /></div>
              <div v-else class="b-content">…</div>
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
          <button class="chip v-lavender" :disabled="aiBusy" @click="quick('润色全文并保持原有结构','replace')">润色全文</button>
          <button class="chip v-orange" :disabled="aiBusy" @click="quick('基于全文起一个更吸引人的标题','title')">起标题</button>
          <button class="chip" :disabled="aiBusy" @click="continueByOutline()">按提纲续写</button>
          <button class="chip v-yellow" :disabled="aiBusy" @click="aiSummary()">摘要+标签</button>
          <button class="chip v-lime" :disabled="aiBusy" @click="aiImage()">AI 配图</button>
          <span v-if="lastAutoSavedAt" class="autosaved mono">已自动保存 {{ lastAutoSavedAt }}</span>
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

      <!-- 中央：文档编辑器（预览 / 微调，VS Code 式主编辑区） -->
      <section class="pane doc" :class="{ closed: !docOpen }">
        <div class="doc-inner">
          <div class="doc-head">
            <div class="tabs">
              <button :class="{ on: docTab === 'preview' }" @click="docTab = 'preview'">预览</button>
              <button :class="{ on: docTab === 'edit' }" @click="docTab = 'edit'">微调</button>
            </div>
            <div class="doc-head-r">
              <button class="gen-trigger" title="AI 生成代码/内容（插入光标处）" @click="openGen">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 3l1.9 4.7L18.5 9l-4.6 1.9L12 15.5l-1.9-4.6L5.5 9l4.6-1.3L12 3z" />
                  <path d="M18.5 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" />
                </svg>
                生成代码
              </button>
              <span class="doc-hint mono">{{ genModel || 'AI' }}</span>
            </div>
          </div>
          <div class="doc-body">
            <div v-show="docTab === 'preview'" class="doc-preview">
              <div ref="previewBox" />
              <div v-if="!currentMd() && editorReady" class="muted" style="padding:24px;text-align:center">暂无正文，去对话里让 AI 写一段吧</div>
            </div>
            <div v-show="docTab === 'edit'" ref="editorBox" class="doc-editor" />

            <!-- VS Code 式「生成代码」临时对话框 -->
            <Transition name="gen-pop">
              <div v-if="genOpen" class="gen-dlg" @keydown.esc="closeGen">
                <div class="gen-bar">
                  <input
                    v-model="genInput"
                    class="gen-input"
                    :placeholder="genBusy ? '生成中，Esc 停止…' : '描述要生成的代码/内容，回车执行…'"
                    :disabled="genBusy"
                    @keydown.enter="runGen"
                  >
                  <button v-if="genBusy" class="gen-x stop" title="停止" @click="genAbort = true">■</button>
                  <button v-else class="gen-x" title="关闭（Esc）" @click="closeGen">×</button>
                </div>
                <div v-if="genResult || genBusy" class="gen-result">
                  <pre class="mono">{{ genResult || '…' }}</pre>
                  <div v-if="!genBusy && genResult.trim()" class="gen-ops">
                    <button class="gen-op primary" @click="insertGen">插入光标处</button>
                    <button class="gen-op" @click="genInput = ''; genResult = ''">换个指令</button>
                    <button class="gen-op" @click="closeGen">丢弃</button>
                  </div>
                </div>
              </div>
            </Transition>
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
      </section>

      <!-- 右栏：大纲 -->
      <aside class="pane ol" :class="{ closed: !olOpen }">
        <div class="ol-inner">
          <div class="ol-head">
            <span class="pane-label">大纲</span>
            <span class="ol-count mono">{{ outline.length }} 节</span>
          </div>
          <div class="ol-body">
            <button
              v-for="(o, i) in outline"
              :key="i"
              class="ol-item"
              :style="{ paddingLeft: 10 + (o.level - 1) * 14 + 'px' }"
              :title="'跳到：' + o.text"
              @click="jumpHeading(o)"
            >
              <span class="ol-dot" :class="'lv' + o.level" />{{ o.text }}
            </button>
            <div v-if="!outline.length" class="ol-empty">
              正文还没有标题——用 # 开头写一行（或让 AI 加小标题），大纲就有了
            </div>
          </div>
          <div class="ol-foot">点击条目跳到预览对应位置</div>
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
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr) 236px;
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
  background: var(--sglass);
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  color: var(--ink);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .06);
  transition: border-color .18s ease, box-shadow .18s ease, transform .25s cubic-bezier(.34, 1.56, .64, 1);
}
.sw-btn:hover {
  border-color: rgba(217, 242, 79, .4);
  box-shadow: 0 0 0 3px rgba(217, 242, 79, .07), inset 0 1px 0 rgba(255, 255, 255, .06);
  transform: scale(1.01);
}
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
  background: var(--spop);
  border: 1px solid rgba(255, 255, 255, .1);
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .55), inset 0 1px 0 rgba(255, 255, 255, .08);
  backdrop-filter: blur(32px) saturate(160%);
  -webkit-backdrop-filter: blur(32px) saturate(160%);
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
.sw-item:hover { background: var(--sbubble); color: var(--ink); }
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
  background: var(--sglass);
  color: var(--ink-soft);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .05);
  transition: all .2s cubic-bezier(.34, 1.56, .64, 1);
}
.btn:hover {
  color: var(--ink);
  border-color: rgba(217, 242, 79, .45);
  background: rgba(255, 255, 255, .06);
  transform: translateY(-1px) scale(1.04);
  box-shadow: 0 6px 18px rgba(0, 0, 0, .3), inset 0 1px 0 rgba(255, 255, 255, .08);
}
.btn:active { transform: scale(.96); }
.btn.primary {
  background: linear-gradient(135deg, var(--sprimary-deep), #b8d42e);
  border: 0;
  color: #fff;
  box-shadow: 0 4px 16px rgba(217, 242, 79, .32);
}
.btn.primary:hover { box-shadow: 0 6px 22px rgba(217, 242, 79, .45); filter: brightness(1.08); }
.btn.primary:disabled { opacity: .5; filter: none; transform: none; }
.btn.icon { width: 38px; height: 38px; padding: 0; display: grid; place-items: center; }
.btn.icon svg { width: 18px; height: 18px; }
.btn.icon.on { color: var(--primary); border-color: rgba(217, 242, 79, .45); background: var(--primary-soft); }

/* ===== 面板通用：苹果式玻璃（中性化） ===== */
.pane {
  position: relative;
  background: var(--spane);
  border: 1px solid rgba(255, 255, 255, .08);
  border-radius: 14px;
  backdrop-filter: blur(24px) saturate(130%);
  -webkit-backdrop-filter: blur(24px) saturate(130%);
  box-shadow: var(--shadow-pane);
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
.chat { flex: 1.15; padding: 16px 18px; min-width: 0; }
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
  transition: all .2s cubic-bezier(.34, 1.56, .64, 1);
  white-space: nowrap;
}
.chip:hover:not(:disabled) { color: var(--ink); border-color: rgba(217, 242, 79, .4); transform: scale(1.05); }
.chip:active:not(:disabled) { transform: scale(.95); }
.chip.on { color: var(--primary); border-color: rgba(217, 242, 79, .5); background: var(--primary-soft); }
.chip:disabled { opacity: .45; }
.chip-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }

/* 快捷指令 chip 色变体（与色卡同源） */
.chip.v-lavender:hover:not(:disabled) { color: #c5b7f8; border-color: rgba(185, 168, 245, .55); background: rgba(185, 168, 245, .12); }
.chip.v-orange:hover:not(:disabled) { color: #f79a77; border-color: rgba(242, 130, 90, .55); background: rgba(242, 130, 90, .12); }
.chip.v-lime:hover:not(:disabled) { color: #e6fa7e; border-color: rgba(217, 242, 79, .5); background: rgba(217, 242, 79, .1); }
.chip.v-yellow:hover:not(:disabled) { color: #faec9e; border-color: rgba(246, 225, 124, .5); background: rgba(246, 225, 124, .1); }

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
  padding: 24px 18px;
  width: 100%;
}
.hero-badge {
  display: inline-block;
  font-size: .68em;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--primary);
  border: 1px solid rgba(217, 242, 79, .35);
  border-radius: 999px;
  padding: 4px 14px;
  margin-bottom: 16px;
  animation: rise .4s ease both;
}
.hero-title {
  font-size: 1.7em;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: .5px;
  background: linear-gradient(100deg, var(--heroa) 55%, var(--herob));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: rise .45s ease .05s both;
}
.hero-sub { color: var(--ink-faint); font-size: .9em; margin: 0; animation: rise .5s ease .1s both; }

/* ===== 颜色卡快捷指令（暗底高饱和卡 + 黑胶囊，参考 KNOCK 风格） ===== */
.cmd-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 470px;
  margin: 24px auto 0;
}
.cmd {
  position: relative;
  overflow: hidden;
  text-align: left;
  border: 0;
  border-radius: 18px;
  padding: 15px 15px 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .3);
  transition: transform .25s cubic-bezier(.34, 1.56, .64, 1), box-shadow .25s ease, filter .25s ease;
  animation: rise .5s cubic-bezier(.2, .7, .3, 1) both;
}
.cmd:nth-child(1) { animation-delay: .14s; }
.cmd:nth-child(2) { animation-delay: .19s; }
.cmd:nth-child(3) { animation-delay: .24s; }
.cmd:nth-child(4) { animation-delay: .29s; }
.cmd:hover { transform: translateY(-3px) scale(1.015); box-shadow: 0 18px 40px rgba(0, 0, 0, .5), inset 0 1px 0 rgba(255, 255, 255, .35); filter: brightness(1.05); }
.cmd:active { transform: translateY(-1px) scale(.985); }
.cmd .blob {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  transition: transform .5s cubic-bezier(.2, .7, .3, 1);
}
.cmd:hover .b1 { transform: translate(-10px, 6px) scale(1.14); }
.cmd:hover .b2 { transform: translate(5px, 7px) scale(1.18); }
.cmd .b1 { width: 130px; height: 130px; right: -40px; bottom: -54px; }
.cmd .b2 { width: 54px; height: 54px; right: 30px; top: -26px; opacity: .8; }
.cmd-title { font-weight: 800; font-size: 1em; position: relative; letter-spacing: .3px; }
.cmd-sub { font-size: .76em; opacity: .68; line-height: 1.55; position: relative; }
.cmd-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; position: relative; }
.cmd .pill {
  background: #15171e;
  color: #fff;
  font-size: .72em;
  padding: 4px 14px;
  border-radius: 999px;
  font-weight: 650;
  letter-spacing: 1px;
}
.cmd .go {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: #15171e;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.05em;
  line-height: 1;
}
.lavender { background: #b9a8f5; color: #17122b; }
.lavender .b1 { background: #a48ef0; }
.lavender .b2 { background: #c5b7f8; }
.orange { background: #f2825a; color: #2a120a; }
.orange .b1 { background: #e86a3e; }
.orange .b2 { background: #f79a77; }
.lime { background: #d9f24f; color: #1c2005; }
.lime .b1 { background: #c7e336; }
.lime .b2 { background: #e6fa7e; }
.yellow { background: #f6e17c; color: #2a2205; }
.yellow .b1 { background: #eed463; }
.yellow .b2 { background: #faec9e; }
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
  background: linear-gradient(135deg, rgba(217, 242, 79, .28), rgba(184, 212, 46, .22));
  border: 1px solid rgba(217, 242, 79, .3);
  border-bottom-right-radius: 5px;
}
.bubble.assistant {
  align-self: flex-start;
  background: var(--sbubble);
  border: 1px solid var(--sline);
  border-bottom-left-radius: 5px;
}
.b-role { font-size: .72em; color: var(--ink-faint); margin-bottom: 3px; letter-spacing: 1px; }
.b-content { white-space: pre-wrap; word-break: break-word; }
/* Markdown 渲染内容：white-space 交回文档流，元素按 md 排 */
.b-content.md { white-space: normal; }
.md :deep(p) { margin: .35em 0; }
.md :deep(pre) {
  background: var(--sinset);
  border: 1px solid var(--sline);
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: .5em 0;
}
.md :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: .82em;
  color: var(--ink);
  white-space: pre;
}
.md :deep(code) {
  background: var(--sinset);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: .84em;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
  color: var(--sprimary);
}
.md :deep(ul), .md :deep(ol) { margin: .4em 0; padding-left: 1.3em; }
.md :deep(li) { margin: .18em 0; }
.md :deep(h1), .md :deep(h2), .md :deep(h3), .md :deep(h4) { margin: .6em 0 .3em; font-weight: 700; }
.md :deep(h1) { font-size: 1.12em; }
.md :deep(h2) { font-size: 1.05em; }
.md :deep(h3), .md :deep(h4) { font-size: .98em; }
.md :deep(blockquote) {
  margin: .5em 0;
  padding: 4px 12px;
  border-left: 3px solid var(--sprimary);
  color: var(--ink-soft);
}
.md :deep(a) { color: var(--sprimary); text-decoration: underline; text-underline-offset: 2px; }
.md :deep(table) { border-collapse: collapse; margin: .5em 0; font-size: .84em; }
.md :deep(th), .md :deep(td) { border: 1px solid var(--sline); padding: 4px 9px; }
.md :deep(hr) { border: 0; border-top: 1px dashed var(--sline); margin: .6em 0; }
.b-actions { display: flex; gap: 8px; margin-top: 9px; }
.b-actions button {
  font-size: .76em;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(217, 242, 79, .4);
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

.quick-row { display: flex; gap: 8px; padding: 10px 0 8px; flex-wrap: wrap; align-items: center; }
.autosaved { margin-left: auto; font-size: .74em; color: var(--ink-faint); }
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--sinset);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .04);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.composer:focus-within {
  border-color: rgba(217, 242, 79, .55);
  box-shadow: 0 0 0 3px rgba(217, 242, 79, .12), inset 0 1px 0 rgba(255, 255, 255, .06);
  transform: translateY(-1px);
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
  background: linear-gradient(135deg, var(--sprimary-deep), #b8d42e);
  box-shadow: 0 4px 14px rgba(217, 242, 79, .35), inset 0 1px 0 rgba(255, 255, 255, .25);
  transition: all .22s cubic-bezier(.34, 1.56, .64, 1);
}
.send svg { width: 17px; height: 17px; }
.send:hover:not(:disabled) { transform: translateY(-1px) scale(1.08); box-shadow: 0 8px 24px rgba(217, 242, 79, .55), inset 0 1px 0 rgba(255, 255, 255, .3); }
.send:active:not(:disabled) { transform: scale(.94); }
.send:disabled { opacity: .4; }

/* ===== 文档面板（中央编辑区） ===== */
.doc { overflow: hidden; }
.doc.closed { display: none; }
.doc-inner {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 14px 16px;
  gap: 10px;
  min-width: 0;
}
.doc-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.doc-head-r { display: flex; gap: 10px; align-items: center; }
.gen-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: .8em;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid rgba(217, 242, 79, .45);
  background: transparent;
  color: var(--sprimary);
  transition: all .16s ease;
}
.gen-trigger svg { width: 14px; height: 14px; }
.gen-trigger:hover { background: rgba(217, 242, 79, .12); transform: translateY(-1px); }
.tabs { display: flex; gap: 4px; background: var(--sinset); border-radius: 11px; padding: 3px; }
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
  background: rgba(217, 242, 79, .16);
  box-shadow: inset 0 0 0 1px rgba(217, 242, 79, .3);
}
.doc-hint { font-size: .74em; color: var(--ink-faint); font-family: ui-monospace, "Cascadia Code", Consolas, monospace; }

.doc-body { flex: 1; min-height: 0; display: flex; position: relative; }

/* ===== VS Code 式「生成代码」内联对话框 ===== */
.gen-dlg {
  position: absolute;
  top: 10px;
  left: 14px;
  right: 14px;
  z-index: 30;
  background: var(--spop-solid, #161a28);
  border: 1px solid rgba(138, 180, 248, .35);
  border-radius: 12px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, .5);
  overflow: hidden;
}
:root[data-theme='light'] .gen-dlg { box-shadow: 0 18px 50px rgba(0, 0, 0, .15); }
.gen-pop-enter-active, .gen-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.gen-pop-enter-from, .gen-pop-leave-to { opacity: 0; transform: translateY(-6px); }
.gen-bar { display: flex; align-items: center; gap: 8px; padding: 9px 12px; border-bottom: 1px solid var(--sline); }
.gen-input {
  flex: 1;
  border: 0 !important;
  background: transparent !important;
  padding: 2px 0 !important;
  font-size: .9em;
  color: var(--ink);
  box-shadow: none !important;
  outline: none !important;
}
.gen-input:focus { outline: none !important; box-shadow: none !important; border: 0 !important; }
.gen-x {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  font-size: 1em;
  line-height: 1;
  padding: 2px 4px;
}
.gen-x:hover { color: var(--ink); }
.gen-x.stop { color: #f28b82; font-size: .8em; }
.gen-result {
  max-height: 42vh;
  overflow-y: auto;
  padding: 10px 14px 12px;
  border-left: 2px solid var(--sprimary);
  background: var(--sinset);
}
.gen-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--ink);
  font-size: .84em;
  line-height: 1.7;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
}
.gen-ops { display: flex; gap: 8px; margin-top: 10px; }
.gen-op {
  font-size: .8em;
  padding: 4px 13px;
  border-radius: 999px;
  border: 1px solid var(--sline);
  background: transparent;
  color: var(--ink-soft);
  transition: all .15s ease;
}
.gen-op:hover { color: var(--ink); border-color: rgba(217, 242, 79, .45); }
.gen-op.primary {
  color: #101012;
  background: var(--sprimary);
  border-color: var(--sprimary);
  font-weight: 650;
}
.gen-op.primary:hover { filter: brightness(1.06); }

/* ===== 右栏大纲 ===== */
.ol { min-width: 0; }
.ol.closed { display: none; }
.ol-inner { width: 100%; height: 100%; display: flex; flex-direction: column; padding: 14px; gap: 8px; min-width: 0; }
.ol-head { display: flex; justify-content: space-between; align-items: center; }
.ol-count { font-size: .72em; color: var(--ink-faint); }
.ol-body { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
.ol-empty { color: var(--ink-faint); font-size: .8em; padding: 16px 8px; text-align: center; line-height: 1.7; }
.ol-foot { font-size: .72em; color: var(--ink-faint); border-top: 1px solid var(--sline); padding-top: 8px; }
.doc-preview {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  border: 1px solid var(--sline);
  border-radius: 12px;
  background: var(--sinset);
  padding: 4px;
}
.doc-preview > div { padding: 12px 16px; }

/* 大纲条目（右栏复用） */
.ol-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  font-size: .84em;
  padding: 7px 10px;
  border-radius: 8px;
  transition: all .15s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ol-item:hover { color: var(--ink); background: rgba(128, 128, 128, .12); transform: translateX(2px); }
.ol-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: var(--ink-faint); }
.ol-dot.lv1 { background: var(--sprimary); }
.ol-dot.lv2 { background: var(--ink-soft); }
:deep(.ol-flash) { animation: olflash 1.3s ease; }
@keyframes olflash {
  0%, 100% { background: transparent; }
  30% { background: rgba(217, 242, 79, .2); }
}
.doc-editor { flex: 1; min-width: 0; min-height: 0; }

/* 预览内容暗色适配 */
:deep(.vditor-reset) { color: var(--ink); font-size: 14.5px; line-height: 1.8; background: transparent; }
:deep(.vditor-reset pre), :deep(.vditor-reset code) { background: var(--code-bg); }
:deep(.vditor-reset a) { color: var(--primary); }
:deep(.vditor-reset blockquote) { border-left-color: var(--primary); }
:deep(.vditor-reset img) { max-width: 100%; border-radius: 8px; }
:deep(.doc-editor .vditor) { border: 1px solid var(--sline); border-radius: 12px; background: var(--sinset); }
:deep(.doc-editor .vditor-toolbar) { background: var(--spop-solid); border-bottom: 1px solid var(--sline); }
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
  .wb-body { grid-template-columns: 1fr; }
  .chat { min-height: 480px; }
  .doc { min-height: 520px; }
  .ol { display: none; }
  .doc-body { min-height: 360px; }
}
/* ===== 手机 ===== */
@media (max-width: 720px) {
  .wb { padding: 12px 12px 18px; gap: 10px; }
  .wb-top { flex-wrap: wrap; row-gap: 8px; }
  .switcher { width: 100%; }
  .sw-btn { width: 100%; max-width: none; }
  .sw-panel { max-width: calc(100vw - 24px); }
  .wb-actions { width: 100%; justify-content: flex-end; flex-wrap: wrap; }
  .btn { padding: 7px 12px; font-size: .84em; }
  .chat { min-height: 62vh; padding: 12px 12px 10px; }
  .doc-inner { padding: 12px; }
  .doc-head .doc-hint { display: none; }
  .cmd-grid { gap: 10px; max-width: none; }
  .cmd { padding: 13px 12px 11px; border-radius: 14px; }
  .cmd-sub { font-size: .72em; }
  .composer { padding: 8px 10px; }
  .bubble { max-width: 96%; }
}
</style>

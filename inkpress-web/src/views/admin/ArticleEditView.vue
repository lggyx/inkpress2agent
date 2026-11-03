<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Sunny } from '@element-plus/icons-vue'
import { EditPen } from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { adminDetail, save } from '@/api/article'
import { list as listCategories } from '@/api/category'
import { list as listTags } from '@/api/tag'
import { summary as aiSummary, suggestTags, suggestTitles } from '@/api/ai'
import { streamSse } from '@/utils/sse'

const route = useRoute()
const router = useRouter()

const form = reactive({
  id: null,
  title: '',
  cover: '',
  categoryId: null,
  tags: [],
  summary: '',
  status: 'DRAFT',
  isTop: false
})

const categories = ref([])
const tagOptions = ref([])
const saving = ref(false)
const editorRef = ref(null)
const contentWrapRef = ref(null)

let vditor = null

const polishing = ref(false)
const polishInstruction = ref('')
let polishAbort = null

const continuing = ref(false)
const titlesLoading = ref(false)
const titleDialogVisible = ref(false)
const titleCandidates = ref([])
const selectedTitle = ref('')

/** AI 续写：流式输出，优先插入光标处，失败则追加到末尾 */
async function startContinue() {
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请先输入一些正文内容')
    return
  }
  continuing.value = true
  polishAbort = new AbortController()
  let acc = ''
  let applied = 0
  let lastApply = 0
  const applyText = text => {
    // Vditor 提供 insertValue 插入光标处；不可用时降级为 setValue 全量替换
    if (typeof vditor?.insertValue === 'function') {
      vditor.insertValue(text)
    } else {
      vditor.setValue(content + text)
    }
  }
  await streamSse('/api/admin/ai/continue', {
    content,
    instruction: polishInstruction.value || undefined
  }, {
    signal: polishAbort.signal,
    onMessage: delta => {
      acc += delta
      const now = Date.now()
      if (now - lastApply > 150) {
        lastApply = now
        applyText(acc.slice(applied))
        applied = acc.length
      }
    },
    onError: err => ElMessage.error(err.message)
  })
  // 应用被节流跳过的剩余增量
  if (acc.length > applied) {
    applyText(acc.slice(applied))
  }
  continuing.value = false
  polishAbort = null
}

function stopContinue() {
  polishAbort?.abort()
}

/** AI 标题：生成 5 个候选，弹窗选择后填入标题框 */
async function generateTitles() {
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请先输入一些正文内容')
    return
  }
  titlesLoading.value = true
  try {
    const { data } = await suggestTitles(content)
    if (!data.length) {
      ElMessage.warning('AI 暂未给出标题候选')
      return
    }
    titleCandidates.value = data
    selectedTitle.value = data[0]
    titleDialogVisible.value = true
  } finally {
    titlesLoading.value = false
  }
}

function applyTitle() {
  if (selectedTitle.value) {
    form.title = selectedTitle.value
  }
  titleDialogVisible.value = false
}

const summaryLoading = ref(false)
const tagsLoading = ref(false)

function initEditor() {
  vditor = new Vditor(editorRef.value, {
    height: 560,
    mode: 'ir',
    theme: 'dark',
    placeholder: '使用 Markdown 书写正文…（支持所见即所得）',
    cache: { enable: false },
    counter: { enable: true },
    toolbar: [
      'headings', 'bold', 'italic', 'strike', '|',
      'list', 'ordered-list', 'check', 'outdent', 'indent', '|',
      'quote', 'code', 'inline-code', 'link', 'table', '|',
      'undo', 'redo', '|', 'fullscreen', 'edit-mode', 'preview', 'help'
    ]
  })
}

/** AI 润色：SSE 流式接收，节流写入编辑器形成打字机效果 */
async function startPolish() {
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请先输入正文内容')
    return
  }
  polishing.value = true
  polishAbort = new AbortController()
  let acc = ''
  let lastApply = 0
  await streamSse('/api/admin/ai/polish', {
    content,
    instruction: polishInstruction.value || undefined
  }, {
    signal: polishAbort.signal,
    onMessage: delta => {
      acc += delta
      const now = Date.now()
      if (now - lastApply > 150) {
        lastApply = now
        vditor.setValue(acc)
      }
    },
    onError: err => ElMessage.error(err.message)
  })
  if (acc) {
    vditor.setValue(acc)
  }
  polishing.value = false
  polishAbort = null
}

function stopPolish() {
  polishAbort?.abort()
}

async function generateSummary() {
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请先输入正文内容')
    return
  }
  summaryLoading.value = true
  try {
    const { data } = await aiSummary(content)
    form.summary = data
    ElMessage.success('摘要已生成，可编辑后使用')
  } finally {
    summaryLoading.value = false
  }
}

async function generateTags() {
  const content = vditor?.getValue() || ''
  if (!content.trim()) {
    ElMessage.warning('请先输入正文内容')
    return
  }
  tagsLoading.value = true
  try {
    const { data } = await suggestTags(content)
    if (!data.length) {
      ElMessage.warning('AI 暂未给出推荐标签')
      return
    }
    data.forEach(tag => {
      if (!tagOptions.value.includes(tag)) {
        tagOptions.value.push(tag)
      }
      if (!form.tags.includes(tag)) {
        form.tags.push(tag)
      }
    })
    ElMessage.success(`已推荐 ${data.length} 个标签`)
  } finally {
    tagsLoading.value = false
  }
}

async function doSave(status) {
  const content = vditor?.getValue() || ''
  if (!form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!content.trim()) {
    ElMessage.warning('请输入正文内容')
    return
  }
  saving.value = true
  try {
    await save({
      id: form.id || undefined,
      title: form.title,
      summary: form.summary || undefined,
      content,
      cover: form.cover || undefined,
      categoryId: form.categoryId || undefined,
      tags: form.tags,
      status,
      isTop: form.isTop
    })
    ElMessage.success(status === 'PUBLISHED' ? '发布成功' : '已保存为草稿')
    router.push('/admin/articles')
  } finally {
    saving.value = false
  }
}

async function loadArticle(id) {
  const { data } = await adminDetail(id)
  form.id = data.id
  form.title = data.title
  form.cover = data.cover || ''
  form.categoryId = data.categoryId
  form.tags = data.tags || []
  form.summary = data.summary || ''
  form.status = data.status
  form.isTop = !!data.isTop
  await nextTick()
  vditor?.setValue(data.content || '')
}

onBeforeUnmount(() => {
  polishAbort?.abort()
  vditor?.destroy()
})

onMounted(async () => {
  const [catRes, tagRes] = await Promise.all([listCategories(), listTags()])
  categories.value = catRes.data
  tagOptions.value = tagRes.data.map(tag => tag.name)
  initEditor()
  if (route.params.id) {
    await loadArticle(route.params.id)
  }
})
</script>

<template>
  <div class="edit-page">
    <el-card shadow="never">
      <el-input
        v-model="form.title"
        placeholder="输入文章标题…"
        size="large"
        class="title-input"
        maxlength="200"
      />

      <div class="meta-row">
        <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 160px">
          <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
        </el-select>

        <el-select
          v-model="form.tags"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入标签"
          style="width: 320px"
        >
          <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
        </el-select>

        <el-button :icon="MagicStick" :loading="tagsLoading" @click="generateTags">AI 推荐标签</el-button>

        <el-checkbox v-model="form.isTop">置顶</el-checkbox>
      </div>

      <div class="meta-row">
        <el-input v-model="form.cover" placeholder="封面图 URL（选填）" style="width: 320px" />
        <el-input
          v-model="polishInstruction"
          placeholder="AI 要求（选填，如：更口语化 / 续写论点）"
          style="width: 300px"
          :disabled="polishing || continuing"
        />
        <el-button :icon="MagicStick" :loading="titlesLoading" @click="generateTitles">AI 标题</el-button>
      </div>

      <div class="meta-row ai-row">
        <el-button :icon="Sunny" :loading="summaryLoading" @click="generateSummary">AI 生成摘要</el-button>
        <el-button :icon="MagicStick" :loading="tagsLoading" @click="generateTags">AI 推荐标签</el-button>
        <el-button
          type="primary"
          plain
          :icon="EditPen"
          :loading="continuing"
          @click="startContinue"
        >AI 续写</el-button>
      </div>

      <el-input
        v-model="form.summary"
        type="textarea"
        :rows="2"
        maxlength="500"
        placeholder="文章摘要（选填，可由 AI 生成）"
      />

      <div ref="editorRef" class="editor-wrap"></div>

      <div class="footer-actions">
        <el-button @click="router.push('/admin/articles')">返回</el-button>
        <div class="right">
          <el-button :loading="saving" :disabled="polishing" @click="doSave('DRAFT')">保存草稿</el-button>
          <el-button type="primary" :loading="saving" :disabled="polishing" @click="doSave('PUBLISHED')">
            发布文章
          </el-button>
        </div>
      </div>

      <!-- AI 续写悬浮控制条 -->
      <div v-if="polishing || continuing" class="polish-bar">
        <el-icon class="spin"><MagicStick /></el-icon>
        {{ polishing ? 'AI 正在润色…' : 'AI 正在续写…' }}
        <el-button size="small" type="danger" plain @click="polishing ? stopPolish() : stopContinue()">停止</el-button>
      </div>

      <!-- AI 标题候选弹窗 -->
      <el-dialog v-model="titleDialogVisible" title="AI 标题候选" width="480px">
        <el-radio-group v-model="selectedTitle" class="title-radio-group">
          <el-radio
            v-for="(title, index) in titleCandidates"
            :key="index"
            :value="title"
            class="title-radio"
          >{{ title }}</el-radio>
        </el-radio-group>
        <template #footer>
          <el-button @click="titleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="applyTitle">使用所选标题</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<style scoped>
.title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
}

.meta-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 14px 0;
  flex-wrap: wrap;
}

.summary-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.editor-wrap {
  border: 1px solid #2c2c33;
  border-radius: 4px;
  overflow: hidden;
}

.footer-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}

.right {
  display: flex;
  gap: 10px;
}

.ai-row {
  gap: 10px;
}

.title-radio-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-radio {
  margin-right: 0;
  padding: 8px 10px;
  border-radius: 4px;
}

.title-radio:hover {
  background: rgba(255, 255, 255, 0.04);
}

.polish-bar {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  background: #26262c;
  color: #e8e8ea;
  padding: 10px 18px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #3a3a44;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
  z-index: 2000;
}

.polish-bar .spin {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>

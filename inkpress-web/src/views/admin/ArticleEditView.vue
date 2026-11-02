<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Sunny } from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { adminDetail, save } from '@/api/article'
import { list as listCategories } from '@/api/category'
import { list as listTags } from '@/api/tag'
import { summary as aiSummary, suggestTags } from '@/api/ai'
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

const summaryLoading = ref(false)
const tagsLoading = ref(false)

function initEditor() {
  vditor = new Vditor(editorRef.value, {
    height: 560,
    mode: 'ir',
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
          placeholder="润色要求（选填，如：更口语化一些）"
          style="width: 300px"
          :disabled="polishing"
        />
      </div>

      <el-input
        v-model="form.summary"
        type="textarea"
        :rows="2"
        maxlength="500"
        placeholder="文章摘要（选填，可由 AI 生成）"
      />

      <div class="summary-actions">
        <el-button :icon="Sunny" :loading="summaryLoading" @click="generateSummary">AI 生成摘要</el-button>
      </div>

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

      <!-- AI 润色悬浮控制条 -->
      <div v-if="polishing" class="polish-bar">
        <el-icon class="spin"><MagicStick /></el-icon>
        AI 正在润色…
        <el-button size="small" type="danger" plain @click="stopPolish">停止</el-button>
      </div>
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
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  z-index: 1;
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

.polish-bar {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  background: #303133;
  color: #fff;
  padding: 10px 18px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
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

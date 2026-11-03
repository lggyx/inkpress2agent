<script setup>
/**
 * 站内知识问答（RAG）：右下角悬浮入口 → 抽屉对话。
 * 后端先推送一条 {"type":"sources","items":[...]} 引用事件，
 * 之后推送流式回答文本；引用可点击跳转对应文章。
 */
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { streamSse } from '@/utils/sse'

const router = useRouter()

const visible = ref(false)
const question = ref('')
const asking = ref(false)
const messages = ref([])
const listRef = ref(null)

function open() {
  visible.value = true
}

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

async function ask() {
  const q = question.value.trim()
  if (!q || asking.value) {
    return
  }
  asking.value = true
  question.value = ''
  messages.value.push({ role: 'user', content: q })
  scrollToBottom()

  const answer = { role: 'ai', content: '', sources: [] }
  messages.value.push(answer)
  let sourcesDone = false

  await streamSse('/api/ai/ask', { content: q }, {
    onMessage: data => {
      // 首条事件可能是引用来源 JSON
      if (!sourcesDone && data.startsWith('{')) {
        try {
          const parsed = JSON.parse(data)
          if (parsed.type === 'sources') {
            answer.sources = parsed.items || []
            sourcesDone = true
            scrollToBottom()
            return
          }
        } catch {
          /* 非 JSON，按普通文本处理 */
        }
      }
      answer.content += data
      scrollToBottom()
    },
    onError: err => {
      if (!answer.content) {
        answer.content = err.message
      }
    }
  })
  if (!answer.content && !answer.sources.length) {
    answer.content = '（未收到回答，请稍后再试）'
  }
  asking.value = false
  scrollToBottom()
}

function gotoArticle(id) {
  visible.value = false
  router.push(`/article/${id}`)
}
</script>

<template>
  <!-- 悬浮入口 -->
  <button class="ask-fab mono" @click="open">
    [ ASK AI ]
  </button>

  <!-- 问答抽屉 -->
  <el-drawer v-model="visible" direction="rtl" :with-header="false">
    <div class="ask-panel">
      <header class="ask-head">
        <span class="ask-title mono">// 站内知识问答</span>
        <span class="ask-sub mono">基于全站文章 · 检索增强生成（RAG）</span>
      </header>

      <div class="ask-list" ref="listRef">
        <el-empty
          v-if="messages.length === 0"
          description="试试问：系统用了哪些技术？/ Redis 在项目里怎么用的？"
        />
        <div v-for="(msg, index) in messages" :key="index" class="ask-msg" :class="msg.role">
          <span class="msg-tag mono">{{ msg.role === 'user' ? 'YOU' : 'AI' }}</span>
          <div class="msg-body">
            <p class="msg-text">{{ msg.content }}</p>
            <div v-if="msg.sources && msg.sources.length" class="msg-sources">
              <span class="sources-label mono">引用来源：</span>
              <span
                v-for="source in msg.sources"
                :key="source.id"
                class="tag-chip"
                @click="gotoArticle(source.id)"
              >[{{ source.index }}] {{ source.title }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="ask-input">
        <input
          v-model="question"
          class="mono"
          placeholder="输入你的问题…"
          maxlength="200"
          :disabled="asking"
          @keyup.enter="ask"
        />
        <button class="ask-send mono" :disabled="asking || !question.trim()" @click="ask">
          {{ asking ? '…' : 'SEND' }}
        </button>
      </div>
      <p class="ask-foot mono">RAG // 检索增强生成 · 回答基于站内文章</p>
    </div>
  </el-drawer>
</template>

<style scoped>
.ask-fab {
  position: fixed;
  right: 26px;
  bottom: 26px;
  z-index: 500;
  background: var(--acid);
  color: #000;
  border: none;
  font-weight: 800;
  font-size: 13px;
  letter-spacing: 0.1em;
  padding: 12px 18px;
  cursor: pointer;
  box-shadow: 0 6px 24px rgba(204, 255, 0, 0.25);
  transition: transform 0.15s;
}
.ask-fab:hover {
  transform: translateY(-3px);
}

.ask-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.ask-head {
  border-bottom: 2px solid var(--text);
  padding-bottom: 14px;
  margin-bottom: 16px;
}
.ask-title {
  display: block;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.1em;
  color: var(--text);
}
.ask-sub {
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
}

.ask-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.ask-msg {
  margin-bottom: 18px;
}
.msg-tag {
  display: inline-block;
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--acid);
  border: 1px solid var(--acid);
  padding: 1px 6px;
  margin-bottom: 6px;
}
.ask-msg.user .msg-tag {
  color: var(--text-dim);
  border-color: var(--line-bright);
}
.msg-body {
  background: var(--surface);
  border: 1px solid var(--line);
  padding: 12px 14px;
}
.msg-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}
.sources-label {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
}

.ask-input {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.ask-input input {
  flex: 1;
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--text);
  padding: 11px 13px;
  font-size: 13px;
  outline: none;
}
.ask-input input:focus {
  border-color: var(--acid);
}
.ask-send {
  background: var(--acid);
  border: none;
  color: #000;
  font-weight: 800;
  padding: 0 18px;
  cursor: pointer;
}
.ask-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ask-foot {
  margin: 8px 0 0;
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  text-align: center;
}
</style>

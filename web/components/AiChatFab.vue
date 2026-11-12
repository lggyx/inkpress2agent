<script setup lang="ts">
import { $api, accessToken } from '~/composables/useApi'
import { streamChat } from '~/composables/useAiStream'
import { renderAiMd } from '~/utils/aiMd'

// 文章页 AI 问答悬浮窗：右下角悬浮球 → 对话模态框（携带全文上下文，多轮流式）
const props = defineProps<{ articleId: number; title: string; contentHtml: string }>()

interface ChatMsg { role: 'user' | 'assistant'; content: string }

const open = ref(false)
const chat = ref<ChatMsg[]>([])
const input = ref('')
const busy = ref(false)
const chatBody = ref<HTMLElement | null>(null)

function stripHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim()
}

function articleContext(): string {
  const text = stripHtml(props.contentHtml).slice(0, 3000)
  return `\n\n【你在帮助读者理解文章《${props.title}》，以下是全文内容，回答基于它：】\n${text}`
}

function scrollBottom() {
  requestAnimationFrame(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

function openChat() {
  open.value = true
  if (!chat.value.length) {
    chat.value.push({ role: 'assistant', content: `你好！我读过这篇《${props.title}》，关于内容的任何问题都可以问我。` })
  }
  scrollBottom()
}

function close() { open.value = false }

async function send(text?: string) {
  const msg = (text ?? input.value).trim()
  if (!msg || busy.value) return
  if (!accessToken()) { navigateTo('/login'); return }
  if (text === undefined) input.value = ''
  const context = chat.value.length <= 2 ? articleContext() : ''
  chat.value.push({ role: 'user', content: msg })
  chat.value.push({ role: 'assistant', content: '' })
  const idx = chat.value.length - 1
  busy.value = true
  scrollBottom()
  try {
    const history = chat.value.slice(0, idx).map((m) => ({ role: m.role, content: m.content }))
    // persona=reader：文章问答助手人设（区别于工作台写作助手）
    await streamChat([...history, { role: 'user', content: msg + context }], (t) => {
      chat.value[idx].content += t
      scrollBottom()
    }, 'reader')
  } catch (e) {
    chat.value[idx].content += `\n⚠ ${(e as Error).message}`
  } finally {
    busy.value = false
    scrollBottom()
  }
}

const suggestions = ['这篇文章讲了什么？', '用要点总结核心内容', '有哪些值得深入的问题？']

onBeforeUnmount(() => { open.value = false })
</script>

<template>
  <div>
    <!-- 悬浮球 -->
    <button class="fab" title="问 AI" @click="openChat">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 3l1.9 4.7L18.5 9l-4.6 1.9L12 15.5l-1.9-4.6L5.5 9l4.6-1.3L12 3z" />
        <path d="M18.5 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" />
      </svg>
      <span>问 AI</span>
    </button>

    <!-- 对话模态框 -->
    <Teleport to="body">
      <Transition name="ai-fade">
        <div v-if="open" class="ai-overlay" @click.self="close">
          <div class="ai-modal">
            <header class="ai-head">
              <div class="ai-t">
                <span class="ai-badge">AI 问答</span>
                <span class="ai-art">《{{ title }}》</span>
              </div>
              <button class="ai-close" @click="close">×</button>
            </header>
            <div ref="chatBody" class="ai-body">
            <div v-for="(m, i) in chat" :key="i" class="bubble" :class="m.role">
              <div
                class="b-content md"
                v-html="m.content ? renderAiMd(m.content) : '…'"
              />
            </div>
              <div v-if="chat.length <= 1" class="sug-row">
                <button v-for="s in suggestions" :key="s" class="sug" @click="send(s)">{{ s }}</button>
              </div>
            </div>
            <footer class="ai-foot">
              <input
                v-model="input"
                placeholder="针对这篇文章提问…"
                :disabled="busy"
                @keyup.enter="send()"
              >
              <button class="send" :disabled="busy || !input.trim()" @click="send()">
                {{ busy ? '…' : '发送' }}
              </button>
            </footer>
            <p v-if="!accessToken()" class="muted login-hint">
              <NuxtLink to="/login">登录</NuxtLink> 后可与 AI 对话。
            </p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fab {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 999px;
  border: 0;
  font-weight: 650;
  color: #101012;
  background: var(--primary);
  box-shadow: 0 10px 30px rgba(0, 0, 0, .3);
  transition: transform .2s cubic-bezier(.34, 1.56, .64, 1), box-shadow .2s ease;
}
.fab svg { width: 17px; height: 17px; }
.fab:hover { transform: translateY(-2px) scale(1.05); box-shadow: 0 14px 36px rgba(0, 0, 0, .35); }

.ai-overlay {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(0, 0, 0, .45);
  backdrop-filter: blur(18px) saturate(140%);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
  display: grid;
  place-items: center;
  padding: 20px;
}
.ai-modal {
  width: 620px;
  max-width: 100%;
  height: min(72vh, 640px);
  display: flex;
  flex-direction: column;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: 0 32px 90px rgba(0, 0, 0, .45);
  overflow: hidden;
}
.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
}
.ai-t { display: flex; align-items: center; gap: 10px; min-width: 0; }
.ai-badge {
  background: var(--primary-soft);
  color: var(--primary);
  border-radius: 4px;
  padding: 1px 8px;
  font-size: .8em;
  font-weight: 650;
  flex-shrink: 0;
}
.ai-art { color: var(--ink-soft); font-size: .88em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-close {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  font-size: 1.4em;
  line-height: 1;
  padding: 0 4px;
}
.ai-close:hover { color: var(--ink); }
.ai-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bubble { padding: 10px 13px; border-radius: 12px; font-size: .92em; line-height: 1.7; max-width: 92%; }
.bubble.user {
  align-self: flex-end;
  background: var(--primary-soft);
  border: 1px solid var(--line);
}
.bubble.assistant {
  align-self: flex-start;
  background: var(--card-soft);
}
.b-content { word-break: break-word; color: var(--ink); }
/* Markdown 渲染样式（与工作台气泡一致） */
.md { line-height: 1.7; }
.md :deep(p) { margin: .35em 0; }
.md :deep(pre) {
  background: rgba(0, 0, 0, .2);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: .5em 0;
}
.md :deep(pre code) { background: transparent; padding: 0; font-size: .82em; color: var(--ink); }
.md :deep(code) {
  background: rgba(0, 0, 0, .2);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: .84em;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
  color: var(--primary);
}
.md :deep(ul), .md :deep(ol) { margin: .4em 0; padding-left: 1.3em; }
.md :deep(h1), .md :deep(h2), .md :deep(h3), .md :deep(h4) { margin: .6em 0 .3em; font-weight: 700; }
.md :deep(blockquote) { margin: .5em 0; padding: 4px 12px; border-left: 3px solid var(--primary); color: var(--ink-soft); }
.md :deep(a) { color: var(--primary); }
.md :deep(table) { border-collapse: collapse; margin: .5em 0; font-size: .84em; }
.md :deep(th), .md :deep(td) { border: 1px solid var(--line); padding: 4px 9px; }
.sug-row { display: flex; flex-wrap: wrap; gap: 8px; padding: 4px 0; }
.sug {
  font-size: .82em;
  padding: 5px 13px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-soft);
  transition: all .16s ease;
}
.sug:hover { color: var(--primary); border-color: var(--primary); }
.ai-foot {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}
.ai-foot input { flex: 1; }
.send {
  padding: 8px 18px;
  border-radius: 10px;
  background: var(--primary);
  border-color: var(--primary);
  color: #101012;
  font-weight: 650;
}
.login-hint { margin: 0; padding: 0 16px 12px; }

.ai-fade-enter-active, .ai-fade-leave-active { transition: opacity .2s ease; }
.ai-fade-enter-from, .ai-fade-leave-to { opacity: 0; }

@media (max-width: 640px) {
  .fab { right: 14px; bottom: 14px; padding: 9px 14px; }
  .ai-overlay { padding: 10px; align-items: flex-end; }
  .ai-modal { width: 100%; height: 80vh; border-radius: 16px; }
}
</style>

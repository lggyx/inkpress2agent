<script setup lang="ts">
import { $pub, $api, visitorId, accessToken, cachedUser, type UserInfo } from '~/composables/useApi'

interface ArticleDetail {
  id: number
  title: string
  contentHtml: string
  coverUrl: string
  seoDescription: string
  categoryName: string
  tags: string[]
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
}

interface RelatedItem { id: number; title: string; manual: boolean; sharedTags: number }
interface CommentItem { id: number; userId: number; nickname: string; parentId: number; content: string; createdAt: string }

const route = useRoute()
const id = route.params.id as string

const { data: article, error } = await useAsyncData(`article-${id}`, () => $pub<ArticleDetail>(`/articles/${id}`))

useSeoMeta({
  title: () => article.value ? `${article.value.title} - InkPress` : 'InkPress',
  description: () => article.value?.seoDescription || '',
})

const liked = ref(false)
const likeCount = ref(0)
const user = ref<UserInfo | null>(null)
const comments = ref<CommentItem[]>([])
const commentText = ref('')
const commentBusy = ref(false)
const commentMsg = ref('')
const related = ref<RelatedItem[]>([])
// 双层嵌套评论：回复目标（null=发主评论）
const replyTo = ref<CommentItem | null>(null)

/** 双层嵌套树：根评论 + 直接子回复（后端 root_id 已归拢，展示两层足够） */
const commentTree = computed(() => {
  const roots: CommentItem[] = []
  const children = new Map<number, CommentItem[]>()
  for (const c of comments.value) {
    const pid = c.parentId ?? 0
    if (!pid) roots.push(c)
    else {
      const arr = children.get(pid) || []
      arr.push(c)
      children.set(pid, arr)
    }
  }
  return roots.map((r) => ({ row: r, replies: children.get(r.id) || [] }))
})

onMounted(async () => {
  user.value = cachedUser()
  likeCount.value = article.value?.likeCount ?? 0
  if (article.value) {
    // M6 浏览上报（匿名访客），失败不打扰阅读
    fetch('/api/track/visit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ visitorId: visitorId(), path: route.fullPath, articleId: article.value.id }),
      keepalive: true,
    }).catch(() => { /* 统计上报失败不打扰阅读 */ })
    // 相关阅读（携带访客 ID 个性化只重排）
    try {
      related.value = await $pub<RelatedItem[]>(`/articles/${id}/related?visitorId=${encodeURIComponent(visitorId())}&limit=6`)
    } catch { related.value = [] }
  }
  try {
    comments.value = await $pub<CommentItem[]>(`/articles/${id}/comments`)
  } catch { comments.value = [] }
})

async function toggleLike() {
  if (!accessToken()) { navigateTo('/login'); return }
  try {
    const r = await $api<{ liked: boolean; likeCount: number }>('POST', `/articles/${id}/like`)
    liked.value = r.liked
    likeCount.value = r.likeCount
  } catch (e) { commentMsg.value = (e as Error).message }
}

async function submitComment() {
  if (!accessToken()) { navigateTo('/login'); return }
  if (!commentText.value.trim()) return
  commentBusy.value = true
  commentMsg.value = ''
  try {
    const payload: Record<string, unknown> = { content: commentText.value.trim() }
    if (replyTo.value) payload.parentId = replyTo.value.id
    const r = await $api<{ status: string }>('POST', `/articles/${id}/comments`, payload)
    if (r.status === 'PENDING_AUDIT') {
      commentMsg.value = '评论含敏感词，已转入人工审核，通过后展示。'
    } else {
      commentMsg.value = replyTo.value ? '回复已发表。' : '评论已发表。'
    }
    commentText.value = ''
    replyTo.value = null
    comments.value = await $pub<CommentItem[]>(`/articles/${id}/comments`)
  } catch (e) {
    commentMsg.value = (e as Error).message
  } finally {
    commentBusy.value = false
  }
}

function fmtDate(s: string): string { return (s || '').replace('T', ' ').slice(0, 16) }
</script>

<template>
  <div class="article-page">
    <div v-if="error" class="card error-text">文章不存在或未发布。</div>
    <template v-else-if="article">
      <article class="card">
        <h1 class="a-title">{{ article.title }}</h1>
        <div class="a-meta muted">
          <span v-if="article.categoryName">{{ article.categoryName }}</span>
          <span>{{ fmtDate(article.publishedAt) }}</span>
          <span>{{ article.viewCount }} 阅读</span>
        </div>
        <div class="a-tags" v-if="article.tags?.length">
          <span class="tag-chip" v-for="t in article.tags" :key="t">{{ t }}</span>
        </div>
        <!-- 服务端渲染的深度排版正文（M4/M3 contentHtml） -->
        <div class="article-content" v-html="article.contentHtml" />
        <AiSummary :article-id="article.id" :title="article.title" :content-html="article.contentHtml || ''" />
        <div class="a-actions">
          <button class="primary" :class="{ liked }" @click="toggleLike">
            {{ liked ? '已赞' : '点赞' }} · {{ likeCount }}
          </button>
          <span class="muted">{{ article.commentCount }} 条评论</span>
        </div>
      </article>

      <!-- AI 问答悬浮窗（全页级） -->
      <AiChatFab :article-id="article.id" :title="article.title" :content-html="article.contentHtml || ''" />

      <section class="card comments">
        <h3>评论</h3>
        <div v-if="user" class="form-row">
          <div v-if="replyTo" class="reply-bar">
            回复 @{{ replyTo.nickname }}
            <button class="linkish" @click="replyTo = null">取消</button>
          </div>
          <textarea v-model="commentText" rows="3" :placeholder="replyTo ? `回复 @${replyTo.nickname}…` : '写下你的评论…'" />
          <button class="primary" :disabled="commentBusy" @click="submitComment">
            {{ replyTo ? '发表回复' : '发表评论' }}
          </button>
          <div v-if="commentMsg" class="ok-text">{{ commentMsg }}</div>
        </div>
        <p v-else class="muted"><NuxtLink to="/login">登录</NuxtLink> 后参与评论。</p>
        <div v-if="!comments.length" class="muted">还没有评论。</div>
        <div v-for="node in commentTree" :key="node.row.id" class="comment-item">
          <div class="c-head"><b>{{ node.row.nickname }}</b><span class="muted">{{ fmtDate(node.row.createdAt) }}</span></div>
          <div class="c-body">{{ node.row.content }}</div>
          <div v-if="user" class="c-ops"><button class="linkish" @click="replyTo = node.row; commentText = ''">回复</button></div>
          <div v-for="c in node.replies" :key="c.id" class="comment-item reply">
            <div class="c-head"><b>{{ c.nickname }}</b><span class="muted">{{ fmtDate(c.createdAt) }}</span></div>
            <div class="c-body">{{ c.content }}</div>
            <div v-if="user" class="c-ops"><button class="linkish" @click="replyTo = c; commentText = ''">回复</button></div>
          </div>
        </div>
      </section>

      <section v-if="related.length" class="card related">
        <h3>相关阅读</h3>
        <NuxtLink v-for="r in related" :key="r.id" :to="`/article/${r.id}`" class="rel-item">
          {{ r.title }}
          <span class="muted">{{ r.manual ? '· 作者推荐' : '' }}</span>
        </NuxtLink>
      </section>
    </template>
  </div>
</template>

<style scoped>
.article-page { max-width: 760px; margin: 0 auto; }
.card { margin-bottom: 20px; }
.a-title { margin: 0 0 8px; }
.a-meta { display: flex; gap: 14px; }
.a-tags { margin: 10px 0 0; }
.article-content { margin-top: 18px; }
.a-actions { display: flex; align-items: center; gap: 16px; margin-top: 26px; border-top: 1px solid var(--line); padding-top: 16px; }
.comments h3, .related h3 { margin: 0 0 12px; }
.comment-item { border-top: 1px dashed var(--line); padding: 10px 0; }
.comment-item.reply { margin-left: 34px; border-top: 1px dashed var(--line); }
.c-ops { margin-top: 4px; }
.c-ops .linkish { font-size: .8em; }
.reply-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--primary-soft);
  border-radius: 8px;
  padding: 6px 10px;
  margin-bottom: 8px;
  font-size: .88em;
}
.c-head { display: flex; gap: 10px; align-items: baseline; }
.c-body { color: var(--ink-soft); margin-top: 2px; }
.rel-item { display: block; padding: 6px 0; color: var(--ink); border-top: 1px dashed var(--line); }
.rel-item:first-of-type { border-top: none; }
button.liked { background: var(--danger); border-color: var(--danger); }
</style>

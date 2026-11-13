<script setup lang="ts">
import { $pub, $api, visitorId, accessToken, base, cachedUser, type UserInfo } from '~/composables/useApi'
import { useAuthUser } from '~/composables/useApi'
import { renderMath } from '~/utils/mathRender'

interface ArticleDetail {
  id: number
  title: string
  contentHtml: string
  coverUrl: string
  seoDescription: string
  geoSummary: string
  categoryName: string
  tags: string[]
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
  updatedAt: string
}

interface RelatedItem { id: number; title: string; manual: boolean; sharedTags: number }
interface CommentItem { id: number; userId: number; nickname: string; parentId: number; content: string; createdAt: string }

const route = useRoute()
const id = route.params.id as string

const { data: article, error } = await useAsyncData(`article-${id}`, () => $pub<ArticleDetail>(`/articles/${id}`))

// 数学公式渲染：服务端 escapeHtml 后的正文（$...$ / $$...$$ 为纯文本）挂载后由 KaTeX 客户端渲染。
// onMounted 覆盖 SSR/ISR 直达（数据已就绪、watch 不触发），watch 覆盖客户端导航与刷新。
const contentBox = ref<HTMLElement>()
onMounted(() => renderMath(contentBox.value))
watch(() => article.value?.contentHtml, () => nextTick(() => renderMath(contentBox.value)))

// 站点源：SSR 内同步求值（useSeoMeta/useHead 的回调在 Nuxt 实例外触发，不能在里面调 useRequestURL）
const siteOrigin = import.meta.server ? useRequestURL().origin : window.location.origin

// 正文首图兜底 og:image（封面缺省时）
const firstImage = computed(() => {
  const m = /<img[^>]+src="([^"]+)"/.exec(article.value?.contentHtml || '')
  return m ? m[1] : ''
})
const ogImage = computed(() => {
  const raw = article.value?.coverUrl || firstImage.value
  if (!raw) return undefined
  return raw.startsWith('http') ? raw : siteOrigin.value + raw
})

useSeoMeta({
  title: () => article.value ? `${article.value.title} - InkPress` : 'InkPress',
  description: () => article.value?.seoDescription || '',
  ogTitle: () => article.value ? `${article.value.title} - InkPress` : 'InkPress',
  ogDescription: () => article.value?.seoDescription || '',
  ogType: 'article',
  ogImage: () => ogImage.value,
  ogUrl: () => siteOrigin.value ? `${siteOrigin.value}/article/${id}` : undefined,
})

// canonical 防多入口重复内容 + JSON-LD Article 结构化数据（生成式引擎提取）
useHead({
  link: () => article.value ? [{ rel: 'canonical', href: `${siteOrigin.value}/article/${article.value.id}` }] : [],
  script: () => {
    const a = article.value
    if (!a) return []
    const ld = {
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: a.title,
      description: a.seoDescription || '',
      datePublished: a.publishedAt || undefined,
      dateModified: a.updatedAt || a.publishedAt || undefined,
      author: { '@type': 'Organization', name: 'InkPress' },
      keywords: (a.tags || []).join(', '),
      image: ogImage.value || undefined,
      mainEntityOfPage: `${siteOrigin.value}/article/${a.id}`,
    }
    return [{ type: 'application/ld+json', innerHTML: JSON.stringify(ld) }]
  },
})

const liked = ref(false)
const likeCount = ref(0)
const user = ref<UserInfo | null>(null)
const authUser = useAuthUser()
const isBlogger = computed(() => !!authUser.value?.roles?.includes('BLOGGER'))
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
    fetch(base() + '/track/visit', {
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
        <div v-if="isBlogger" class="edit-entry">
          <NuxtLink :to="`/studio/workbench?article=${article.id}`" class="edit-link">✎ 编辑此文（博主端工作台）</NuxtLink>
        </div>
        <!-- GEO：结构化「核心结论」摘要区块，供生成式引擎直接提取 -->
        <div v-if="article.geoSummary" class="geo-box">
          <div class="geo-badge">核心结论 · GEO</div>
          <p class="geo-text">{{ article.geoSummary }}</p>
        </div>
    <!-- 服务端渲染的深度排版正文（M4/M3 contentHtml；公式由客户端 KaTeX 渲染） -->
    <div ref="contentBox" class="article-content" v-html="article.contentHtml" />
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
.edit-entry { margin-top: 10px; }
.edit-link {
  color: var(--primary);
  font-size: .85em;
  border: 1px dashed var(--line);
  border-radius: 999px;
  padding: 3px 12px;
  transition: all .16s ease;
}
.edit-link:hover { border-color: var(--primary); text-decoration: none; }
.article-content { margin-top: 18px; }
.geo-box {
  margin: 18px 0 0;
  padding: 14px 18px;
  border: 1px solid var(--primary);
  border-radius: var(--radius);
  background: var(--primary-soft);
}
.geo-badge {
  display: inline-block;
  background: var(--primary);
  color: #fff;
  border-radius: 4px;
  padding: 1px 8px;
  font-size: .8em;
  font-weight: 650;
  margin-bottom: 8px;
}
.geo-text { margin: 0; color: var(--ink); line-height: 1.8; }
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

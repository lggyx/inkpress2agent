<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { detail, like as likeApi } from '@/api/article'
import { listByArticle, create } from '@/api/comment'
import { renderMarkdown, highlightIn } from '@/utils/markdown'
import ArticleToc from '@/components/ArticleToc.vue'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/format'

const route = useRoute()
const userStore = useUserStore()

const article = ref(null)
const notFound = ref(false)
const likeCount = ref(0)
const liked = ref(false)
const contentRef = ref(null)

const comments = ref([])
const submitting = ref(false)
const commentForm = ref({ nickname: '', email: '', content: '', parentId: null })
const replyTo = ref(null)
const tocRef = ref(null)

const html = computed(() => renderMarkdown(article.value?.content))

const rootComments = computed(() => comments.value.filter(c => !c.parentId))
const repliesOf = id => comments.value.filter(c => c.parentId === id)

function likedKey() {
  return `inkpress_liked_${route.params.id}`
}

async function load() {
  notFound.value = false
  article.value = null
  try {
    const { data } = await detail(route.params.id)
    article.value = data
    likeCount.value = data.likes
    liked.value = localStorage.getItem(likedKey()) === '1'
    await nextTick()
    if (contentRef.value) {
      highlightIn(contentRef.value)
      // 正文渲染后刷新目录锚点
      tocRef.value?.refresh()
    }
    await loadComments()
  } catch (e) {
    // 草稿、已删除或不存在的文章：给出明确空状态而非白屏
    notFound.value = true
  }
}

async function loadComments() {
  const { data } = await listByArticle(route.params.id)
  comments.value = data
}

async function doLike() {
  if (liked.value) {
    ElMessage.info('已经点过赞啦')
    return
  }
  const { data } = await likeApi(route.params.id)
  likeCount.value = data
  liked.value = true
  localStorage.setItem(likedKey(), '1')
  ElMessage.success('感谢点赞！')
}

function reply(comment) {
  replyTo.value = comment
  commentForm.value.parentId = comment.id
}

function cancelReply() {
  replyTo.value = null
  commentForm.value.parentId = null
}

async function submitComment() {
  if (!commentForm.value.content.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  if (!userStore.isLoggedIn && !commentForm.value.nickname.trim()) {
    ElMessage.warning('游客评论请填写昵称')
    return
  }
  submitting.value = true
  try {
    const { data: status } = await create({
      articleId: Number(route.params.id),
      nickname: commentForm.value.nickname || undefined,
      email: commentForm.value.email || undefined,
      content: commentForm.value.content,
      parentId: commentForm.value.parentId
    })
    ElMessage.success(status === 'PENDING' ? '评论提交成功，审核通过后将展示' : '评论已发布')
    commentForm.value.content = ''
    cancelReply()
    await loadComments()
  } finally {
    submitting.value = false
  }
}

watch(() => route.params.id, () => {
  if (route.name === 'article-detail') {
    load()
  }
}, { immediate: true })
</script>

<template>
  <div class="article-detail">
    <!-- 404 空状态 -->
    <el-empty v-if="notFound" description="404 // ARTICLE NOT FOUND OR UNPUBLISHED">
      <span class="bracket" @click="$router.push('/')">back home</span>
    </el-empty>

    <template v-else-if="article">
      <!-- 面包屑 -->
      <div class="crumb mono">
        <span class="bracket" @click="$router.push('/')">home</span>
        <span class="crumb-sep">//</span>
        <span class="crumb-current">{{ article.categoryName || 'UNCATEGORIZED' }}</span>
      </div>

      <div class="detail-layout">
        <div class="detail-main">
          <article class="detail-card">
        <h1 class="detail-title">{{ article.title }}</h1>

        <div class="detail-meta mono">
          <span class="meta-author">{{ article.authorName || 'ANONYMOUS' }}</span>
          <span class="dot">·</span>
          <span>{{ formatDate(article.publishedAt || article.createdAt) }}</span>
          <span class="dot">·</span>
          <span>{{ article.views }} VIEWS</span>
          <span class="dot">·</span>
          <span>{{ article.likes }} LIKES</span>
          <span class="dot">·</span>
          <span class="meta-status" v-if="article.isTop">[ PIN ]</span>
        </div>

        <div class="detail-tags">
          <span class="tag-chip">{{ article.categoryName || '未分类' }}</span>
          <span v-for="tag in article.tags" :key="tag" class="tag-chip"># {{ tag }}</span>
        </div>

        <img v-if="article.cover" :src="article.cover" class="detail-cover" alt="封面" />

        <!-- Markdown 渲染（已做 XSS 净化） -->
        <div ref="contentRef" class="article-content" v-html="html"></div>

        <div class="like-bar">
          <button class="like-btn" :class="{ liked }" @click="doLike">
            <span class="like-icon">{{ liked ? '♥' : '♡' }}</span>
            <span class="mono">{{ liked ? 'LIKED' : 'LIKE' }}</span>
            <span class="like-count mono">{{ likeCount }}</span>
          </button>
        </div>
      </article>

      <!-- 评论区 -->
      <section class="comment-section">
        <h2 class="block-title">
          <span class="idx mono">03</span> COMMENTS [ {{ comments.length }} ]
        </h2>

        <div class="comment-form">
          <div class="reply-tip mono" v-if="replyTo">
            <span>REPLY TO @{{ replyTo.nickname }}</span>
            <span class="bracket" @click="cancelReply">cancel</span>
          </div>
          <div class="guest-fields" v-if="!userStore.isLoggedIn">
            <input v-model="commentForm.nickname" class="mono" placeholder="NICKNAME *" maxlength="20" />
            <input v-model="commentForm.email" class="mono" placeholder="EMAIL (OPTIONAL)" maxlength="50" />
          </div>
          <textarea
            v-model="commentForm.content"
            class="comment-textarea mono"
            rows="4"
            maxlength="1000"
            placeholder="WRITE YOUR COMMENT…"
          ></textarea>
          <div class="form-actions">
            <span class="char-count mono">{{ commentForm.content.length }} / 1000</span>
            <button class="submit-btn mono" :disabled="submitting" @click="submitComment">
              {{ submitting ? 'SUBMITTING…' : 'SUBMIT' }}
            </button>
          </div>
        </div>

        <el-empty v-if="comments.length === 0" description="NO COMMENTS YET — BE THE FIRST" />

        <div v-for="comment in rootComments" :key="comment.id" class="comment-item">
          <span class="comment-avatar mono">{{ comment.nickname.slice(0, 1) }}</span>
          <div class="comment-body">
            <div class="comment-head">
              <span class="comment-nick">{{ comment.nickname }}</span>
              <span class="comment-time mono">{{ formatDate(comment.createdAt) }}</span>
              <span class="bracket reply-link" @click="reply(comment)">reply</span>
            </div>
            <p class="comment-content">{{ comment.content }}</p>

            <div v-for="child in repliesOf(comment.id)" :key="child.id" class="comment-item reply">
              <span class="comment-avatar small mono">{{ child.nickname.slice(0, 1) }}</span>
              <div class="comment-body">
                <div class="comment-head">
                  <span class="comment-nick">{{ child.nickname }}</span>
                  <span class="comment-time mono">{{ formatDate(child.createdAt) }}</span>
                </div>
                <p class="comment-content">{{ child.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>
        </div>

        <!-- 目录大纲（宽屏右侧悬浮 / 窄屏顶部折叠） -->
        <aside class="toc-aside">
          <ArticleToc ref="tocRef" :contentEl="contentRef" />
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.article-detail {
  max-width: 1120px;
  margin: 0 auto;
}

.detail-layout {
  display: flex;
  gap: 44px;
  align-items: flex-start;
}

.detail-main {
  flex: 1;
  min-width: 0;
}

.toc-aside {
  width: 230px;
  flex-shrink: 0;
}

@media (max-width: 1279px) {
  .detail-layout {
    flex-direction: column;
  }
  .toc-aside {
    width: 100%;
    order: -1;
  }
}

/* ---------- 面包屑 ---------- */
.crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  letter-spacing: 0.14em;
  margin-bottom: 22px;
}
.crumb-sep {
  color: var(--acid);
}
.crumb-current {
  color: var(--text-faint);
}

/* ---------- 文章主体 ---------- */
.detail-card {
  border: 2px solid var(--text);
  background: var(--surface);
  padding: 44px 48px 36px;
  margin-bottom: 40px;
}

.detail-title {
  margin: 0 0 16px;
  font-size: clamp(26px, 3.4vw, 36px);
  font-weight: 900;
  line-height: 1.32;
  color: var(--text);
}

.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--text-faint);
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}
.meta-author {
  color: var(--acid);
  font-weight: 700;
}
.meta-status {
  color: #000;
  background: var(--pink);
  padding: 1px 6px;
  font-weight: 700;
}
.dot {
  color: var(--line-bright);
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 16px 0 8px;
}

.detail-cover {
  width: 100%;
  max-height: 380px;
  object-fit: cover;
  border: 1px solid var(--line);
  margin: 14px 0 8px;
}

/* ---------- 点赞 ---------- */
.like-bar {
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 2px solid var(--text);
}
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  border: 2px solid var(--text);
  color: var(--text);
  padding: 12px 30px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: all 0.15s;
}
.like-btn:hover {
  border-color: var(--acid);
  color: var(--acid);
}
.like-btn.liked {
  background: var(--acid);
  border-color: var(--acid);
  color: #000;
}
.like-icon {
  font-size: 17px;
}
.like-count {
  border-left: 1px solid currentColor;
  padding-left: 12px;
  opacity: 0.75;
}

/* ---------- 评论区 ---------- */
.comment-section {
  margin-bottom: 40px;
}

.comment-form {
  border: 1px solid var(--line);
  background: var(--surface);
  padding: 22px 24px;
  margin-bottom: 26px;
}
.reply-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--acid);
  margin-bottom: 12px;
}
.guest-fields {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.guest-fields input {
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--text);
  padding: 9px 12px;
  font-size: 12px;
  letter-spacing: 0.06em;
  outline: none;
  width: 220px;
}
.guest-fields input:focus {
  border-color: var(--acid);
}

.comment-textarea {
  width: 100%;
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--text);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  resize: vertical;
  outline: none;
}
.comment-textarea:focus {
  border-color: var(--acid);
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.char-count {
  font-size: 10px;
  color: var(--text-faint);
  letter-spacing: 0.1em;
}
.submit-btn {
  background: var(--acid);
  border: none;
  color: #000;
  font-weight: 800;
  letter-spacing: 0.12em;
  padding: 10px 26px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.submit-btn:hover {
  opacity: 0.85;
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---------- 评论列表 ---------- */
.comment-item {
  display: flex;
  gap: 14px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}
.comment-item.reply {
  border-bottom: none;
  border-left: 2px solid var(--acid);
  padding: 12px 0 12px 16px;
  margin-top: 10px;
}
.comment-avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2);
  border: 1px solid var(--line-bright);
  color: var(--acid);
  font-weight: 800;
  font-size: 15px;
}
.comment-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 12px;
}
.comment-body {
  flex: 1;
  min-width: 0;
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.comment-nick {
  font-weight: 700;
  font-size: 14px;
  color: var(--text);
}
.comment-time {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
}
.reply-link {
  margin-left: auto;
  font-size: 11px;
}
.comment-content {
  margin: 0;
  line-height: 1.75;
  color: var(--text-dim);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}

@media (max-width: 720px) {
  .detail-card {
    padding: 28px 22px 24px;
    border-width: 1px;
  }
}
</style>

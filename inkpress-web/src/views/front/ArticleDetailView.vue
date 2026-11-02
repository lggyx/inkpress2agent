<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View, Star, ChatDotRound, User } from '@element-plus/icons-vue'
import { detail, like as likeApi } from '@/api/article'
import { listByArticle, create } from '@/api/comment'
import { renderMarkdown, highlightIn } from '@/utils/markdown'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/format'

const route = useRoute()
const userStore = useUserStore()

const article = ref(null)
const likeCount = ref(0)
const liked = ref(false)
const contentRef = ref(null)

const comments = ref([])
const submitting = ref(false)
const commentForm = ref({ nickname: '', email: '', content: '', parentId: null })
const replyTo = ref(null)

const html = computed(() => renderMarkdown(article.value?.content))

const rootComments = computed(() => comments.value.filter(c => !c.parentId))
const repliesOf = id => comments.value.filter(c => c.parentId === id)

function likedKey() {
  return `inkpress_liked_${route.params.id}`
}

async function load() {
  const { data } = await detail(route.params.id)
  article.value = data
  likeCount.value = data.likes
  liked.value = localStorage.getItem(likedKey()) === '1'
  await nextTick()
  if (contentRef.value) {
    highlightIn(contentRef.value)
  }
  await loadComments()
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
    // 按落库状态给出准确提示：管理员或未开启审核时直接可见
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
  <div class="article-detail" v-if="article">
    <div class="detail-main">
      <article class="detail-card">
        <h1 class="detail-title">{{ article.title }}</h1>
        <div class="detail-meta">
          <span><el-icon><User /></el-icon> {{ article.authorName || '佚名' }}</span>
          <el-tag size="small" effect="plain">{{ article.categoryName || '未分类' }}</el-tag>
          <span>{{ formatDate(article.publishedAt || article.createdAt) }}</span>
          <span><el-icon><View /></el-icon> {{ article.views }} 次浏览</span>
        </div>
        <img v-if="article.cover" :src="article.cover" class="detail-cover" alt="封面" />

        <!-- Markdown 渲染（已做 XSS 净化） -->
        <div ref="contentRef" class="article-content" v-html="html"></div>

        <div class="like-bar">
          <el-button :type="liked ? 'info' : 'danger'" round @click="doLike">
            <el-icon style="margin-right: 6px"><Star /></el-icon>
            {{ liked ? '已点赞' : '点赞' }} · {{ likeCount }}
          </el-button>
        </div>
      </article>

      <!-- 评论区 -->
      <section class="detail-card comment-section">
        <h3 class="section-title"><el-icon><ChatDotRound /></el-icon> 评论（{{ comments.length }}）</h3>

        <div class="comment-form">
          <div class="reply-tip" v-if="replyTo">
            回复 @{{ replyTo.nickname }}：
            <el-button link type="primary" size="small" @click="cancelReply">取消回复</el-button>
          </div>
          <div class="guest-fields" v-if="!userStore.isLoggedIn">
            <el-input v-model="commentForm.nickname" placeholder="昵称（必填）" maxlength="20" style="width: 160px" />
            <el-input v-model="commentForm.email" placeholder="邮箱（选填）" maxlength="50" style="width: 220px" />
          </div>
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="写下你的评论…"
          />
          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="submitComment">发表评论</el-button>
          </div>
        </div>

        <el-empty v-if="comments.length === 0" description="还没有评论，来抢沙发" :image-size="80" />
        <div v-for="comment in rootComments" :key="comment.id" class="comment-item">
          <el-avatar :size="36">{{ comment.nickname.slice(0, 1) }}</el-avatar>
          <div class="comment-body">
            <div class="comment-head">
              <span class="comment-nick">{{ comment.nickname }}</span>
              <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
              <el-button link type="primary" size="small" @click="reply(comment)">回复</el-button>
            </div>
            <p class="comment-content">{{ comment.content }}</p>

            <div v-for="child in repliesOf(comment.id)" :key="child.id" class="comment-item reply">
              <el-avatar :size="28">{{ child.nickname.slice(0, 1) }}</el-avatar>
              <div class="comment-body">
                <div class="comment-head">
                  <span class="comment-nick">{{ child.nickname }}</span>
                  <span class="comment-time">{{ formatDate(child.createdAt) }}</span>
                </div>
                <p class="comment-content">{{ child.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.detail-main {
  max-width: 820px;
  margin: 0 auto;
}

.detail-card {
  background: #fff;
  border-radius: 10px;
  padding: 32px 36px;
  margin-bottom: 20px;
}

.detail-title {
  margin: 0 0 12px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.4;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 13px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.detail-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.detail-cover {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  border-radius: 8px;
  margin: 16px 0;
}

.like-bar {
  display: flex;
  justify-content: center;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #f0f2f5;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 16px;
}

.comment-form .guest-fields {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.reply-tip {
  margin-bottom: 8px;
  font-size: 13px;
  color: #909399;
}

.form-actions {
  margin-top: 8px;
  text-align: right;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f5f7fa;
}

.comment-item.reply {
  background: #fafbfc;
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 8px;
  border-bottom: none;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.comment-nick {
  font-weight: 600;
}

.comment-time {
  color: #c0c4cc;
}

.comment-content {
  margin: 6px 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

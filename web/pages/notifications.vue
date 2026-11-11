<script setup lang="ts">
import { useAuthUser, hydrateAuthUser } from '~/composables/useApi'
import { useNotifications } from '~/composables/useNotifications'

const user = useAuthUser()
const { unread, items, refreshList, markRead } = useNotifications()

onMounted(async () => {
  hydrateAuthUser()
  if (!localStorage.getItem('inkpress.access')) { navigateTo('/login'); return }
  await refreshList()
})

async function readAll() {
  await markRead()
}

function typeText(t: string) {
  return { COMMENT: '评论', REPLY: '回复', REVIEW_RESULT: '复审', SYSTEM: '系统' }[t] ?? t
}
</script>

<template>
  <div class="notif-page">
    <div class="head">
      <h2>通知中心 <el-tag v-if="unread" type="danger" size="small">未读 {{ unread }}</el-tag></h2>
      <el-button :disabled="!unread" @click="readAll">全部已读</el-button>
    </div>
    <div v-if="!items.length" class="card muted">暂无通知。评论、回复与复审结果会实时推送到这里。</div>
    <div v-for="n in items" :key="n.id" class="card n-item" :class="{ unread: !n.readAt }">
      <div class="n-head">
        <el-tag size="small">{{ typeText(n.type) }}</el-tag>
        <b>{{ n.title }}</b>
        <span class="muted">{{ n.createdAt.replace('T', ' ').slice(0, 16) }}</span>
        <span v-if="!n.readAt" class="dot" />
      </div>
      <div v-if="n.content" class="muted n-body">{{ n.content }}</div>
      <NuxtLink v-if="n.refUrl" :to="n.refUrl" class="n-link">查看 →</NuxtLink>
    </div>
  </div>
</template>

<style scoped>
.notif-page { max-width: 720px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head h2 { margin: 0; }
.n-item { margin-bottom: 12px; padding: 14px 18px; }
.n-item.unread { border-color: var(--primary-deep); }
.n-head { display: flex; gap: 10px; align-items: center; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); }
.n-body { margin-top: 4px; }
.n-link { display: inline-block; margin-top: 4px; font-size: .9em; }
</style>

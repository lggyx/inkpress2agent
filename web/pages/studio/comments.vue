<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api } from '~/composables/useApi'

interface PendingComment {
  id: number
  articleId: number
  userId: number
  content: string
  createdAt: string
}

const rows = ref<PendingComment[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try { rows.value = await $api<PendingComment[]>('GET', '/studio/comments/pending') }
  catch (e) { ElMessage.error((e as Error).message) } finally { loading.value = false }
}
onMounted(load)

async function approve(row: PendingComment) {
  await $api('POST', `/studio/comments/${row.id}/approve`)
  ElMessage.success('已通过')
  await load()
}

async function remove(row: PendingComment) {
  await ElMessageBox.confirm('拒绝并删除该评论？', '删除确认')
  await $api('DELETE', `/studio/comments/${row.id}`)
  await load()
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>评论审核</h2>
        <span class="sub">命中敏感词的评论在此人工审核</span>
      </div>
    </div>

    <div v-loading="loading">
      <div v-for="row in rows" :key="row.id" class="row">
        <div class="row-main">
          <div class="c-content">{{ row.content }}</div>
          <div class="row-sub">
            <NuxtLink :to="`/article/${row.articleId}`" target="_blank">文章 #{{ row.articleId }} ↗</NuxtLink>
            <span> · {{ (row.createdAt || '').slice(0, 16).replace('T', ' ') }}</span>
          </div>
        </div>
        <div class="row-actions">
          <button class="abtn ok" @click="approve(row)">通过</button>
          <button class="abtn danger" @click="remove(row)">拒绝</button>
        </div>
      </div>
      <el-empty v-if="!loading && !rows.length" description="没有待审核评论" />
    </div>
  </div>
</template>

<style scoped>
.c-content { color: var(--ink); font-size: .95em; line-height: 1.6; }
</style>

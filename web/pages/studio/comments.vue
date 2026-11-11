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
  <div>
    <div class="page-head"><h2>评论审核</h2><span class="muted">命中敏感词的评论在此人工审核</span></div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="#" width="70" />
      <el-table-column label="文章" width="90">
        <template #default="{ row }">
          <NuxtLink :to="`/article/${row.articleId}`" target="_blank">#{{ row.articleId }}</NuxtLink>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="380" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row)">通过</el-button>
          <el-button size="small" type="danger" @click="remove(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="没有待审核评论" />
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: baseline; gap: 14px; margin-bottom: 16px; }
.page-head h2 { margin: 0; }
</style>

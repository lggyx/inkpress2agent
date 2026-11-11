<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api } from '~/composables/useApi'

interface Word { id: number; word: string }

const rows = ref<Word[]>([])
const word = ref('')

async function load() {
  rows.value = await $api<Word[]>('GET', '/admin/sensitive-words')
}
onMounted(load)

async function add() {
  if (!word.value.trim()) return
  try {
    await $api('POST', '/admin/sensitive-words', { word: word.value.trim() })
    word.value = ''
    await load()
  } catch (e) { ElMessage.error((e as Error).message) }
}

async function remove(row: Word) {
  await ElMessageBox.confirm(`删除敏感词「${row.word}」？`, '删除确认')
  await $api('DELETE', `/admin/sensitive-words/${row.id}`)
  await load()
}

async function reload() {
  await $api('POST', '/admin/sensitive-words/reload')
  ElMessage.success('词库已即时刷新（平时每 60s 自动刷新）')
}
</script>

<template>
  <div style="max-width:640px">
    <h2>敏感词库</h2>
    <div style="display:flex;gap:8px;margin-bottom:14px">
      <input v-model="word" placeholder="添加敏感词（命中评论转人工审核）" @keyup.enter="add">
      <el-button type="primary" @click="add">添加</el-button>
      <el-button @click="reload">立即刷新</el-button>
    </div>
    <div class="card">
      <span v-for="w in rows" :key="w.id" class="word-chip">
        {{ w.word }}
        <button class="x" @click="remove(w)">×</button>
      </span>
      <span v-if="!rows.length" class="muted">词库为空</span>
    </div>
  </div>
</template>

<style scoped>
.word-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--card-soft);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 3px 12px;
  margin: 0 8px 8px 0;
}
.x { border: none; background: none; color: var(--ink-faint); padding: 0; font-size: 1em; }
.x:hover { color: var(--danger); }
</style>

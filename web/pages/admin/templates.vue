<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'

interface Template { id: number; category: string; name: string; content: string; enabled: number }

const rows = ref<Template[]>([])
const editing = ref<Template | null>(null)

async function load() {
  rows.value = await $api<Template[]>('GET', '/admin/prompt-templates')
}
onMounted(load)

function edit(row: Template) {
  editing.value = { ...row }
}

async function save() {
  if (!editing.value) return
  await $api('PUT', '/admin/prompt-templates', {
    category: editing.value.category,
    name: editing.value.name,
    content: editing.value.content,
    enabled: editing.value.enabled === 1,
  })
  ElMessage.success('模板已保存（热生效，留痕）')
  editing.value = null
  await load()
}
</script>

<template>
  <div class="page">
    <h2>提示词模板</h2>
    <el-table :data="rows">
      <el-table-column prop="category" label="类别" width="110" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column label="内容" min-width="380">
        <template #default="{ row }">
          <span class="muted">{{ row.content.slice(0, 90) }}…</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }"><el-button size="small" @click="edit(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-if="editing" :model-value="true" :title="`编辑模板：${editing.category}`" width="680" @close="editing = null">
      <div class="form-row"><label>名称</label><input v-model="editing.name"></div>
      <div class="form-row">
        <label>内容（content 为渲染占位符）</label>
        <textarea v-model="editing.content" rows="10" style="font-family:ui-monospace,monospace;font-size:.88em"></textarea>
      </div>
      <el-checkbox v-model="editing.enabled" :true-value="1" :false-value="0" label="启用" />
      <div style="display:flex;gap:10px;margin-top:10px">
        <el-button type="primary" @click="save">保存</el-button>
        <el-button @click="editing = null">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

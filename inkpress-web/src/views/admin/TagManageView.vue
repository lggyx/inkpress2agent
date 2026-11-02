<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { list, save, update, remove } from '@/api/tag'

const records = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '' })

async function load() {
  loading.value = true
  try {
    const { data } = await list()
    records.value = data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.name = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  if (form.id) {
    await update({ ...form })
  } else {
    await save({ ...form })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function doRemove(row) {
  await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="manage-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增标签</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="标签名称" min-width="140">
          <template #default="{ row }">
            <el-tag effect="plain"># {{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑标签' : '新增标签'" width="380px">
      <el-input v-model="form.name" maxlength="50" placeholder="标签名称" @keyup.enter="submit" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

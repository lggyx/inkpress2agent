<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminPage, audit, remove } from '@/api/comment'
import { formatDate } from '@/utils/format'

const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await adminPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  await audit(row.id, 'APPROVED')
  ElMessage.success('已通过')
  load()
}

async function reject(row) {
  await audit(row.id, 'REJECTED')
  ElMessage.success('已拒绝')
  load()
}

async function doRemove(row) {
  await ElMessageBox.confirm('确定删除该评论吗？', '删除确认', { type: 'warning' })
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
        <el-select v-model="query.status" placeholder="审核状态" clearable style="width: 140px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="query.pageNum = 1; load()">查询</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="articleTitle" label="文章" min-width="180" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" width="110" />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'danger'"
            >
              {{ row.status === 'APPROVED' ? '已通过' : row.status === 'PENDING' ? '待审核' : '已拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'APPROVED'" link type="success" @click="approve(row)">通过</el-button>
            <el-button v-if="row.status !== 'REJECTED'" link type="warning" @click="reject(row)">拒绝</el-button>
            <el-button link type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="page => { query.pageNum = page; load() }"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

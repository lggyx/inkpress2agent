<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { adminPage, updateStatus, updateTop, remove } from '@/api/article'
import { list as listCategories } from '@/api/category'
import { formatDate } from '@/utils/format'

const router = useRouter()

const query = reactive({ keyword: '', status: '', categoryId: null, pageNum: 1, pageSize: 10 })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const categories = ref([])

async function load() {
  loading.value = true
  try {
    const { data } = await adminPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      categoryId: query.categoryId || undefined
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.status = ''
  query.categoryId = null
  search()
}

async function togglePublish(row) {
  const next = row.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
  await updateStatus(row.id, next)
  ElMessage.success(next === 'PUBLISHED' ? '已发布' : '已转为草稿')
  load()
}

async function toggleTop(row) {
  await updateTop(row.id, !row.isTop)
  ElMessage.success(row.isTop ? '已取消置顶' : '已置顶')
  load()
}

async function doRemove(row) {
  await ElMessageBox.confirm(`确定删除文章《${row.title}》吗？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  listCategories().then(({ data }) => (categories.value = data))
})
</script>

<template>
  <div class="manage-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="按标题搜索"
          :prefix-icon="Search"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
        <el-select v-model="query.categoryId" placeholder="分类" clearable style="width: 140px">
          <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" :icon="Plus" class="new-btn" @click="router.push('/admin/articles/edit')">
          写文章
        </el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.isTop" type="danger" size="small" effect="dark" style="margin-right: 6px">顶</el-tag>
            {{ row.title }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column label="标签" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="views" label="浏览" width="70" />
        <el-table-column prop="likes" label="点赞" width="70" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/articles/edit/${row.id}`)">编辑</el-button>
            <el-button link :type="row.status === 'PUBLISHED' ? 'warning' : 'success'" @click="togglePublish(row)">
              {{ row.status === 'PUBLISHED' ? '下架' : '发布' }}
            </el-button>
            <el-button link :type="row.isTop ? 'info' : 'danger'" @click="toggleTop(row)">
              {{ row.isTop ? '取消置顶' : '置顶' }}
            </el-button>
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

.new-btn {
  margin-left: auto;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

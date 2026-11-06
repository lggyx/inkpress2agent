<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis, Document, Collection, PriceTag, ChatDotRound,
  User, Tickets, Reading, MagicStick
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuItems = [
  { index: '/admin/dashboard', icon: DataAnalysis, label: '数据看板' },
  { index: '/admin/articles', icon: Document, label: '文章管理' },
  { index: '/admin/categories', icon: Collection, label: '分类管理' },
  { index: '/admin/tags', icon: PriceTag, label: '标签管理' },
  { index: '/admin/comments', icon: ChatDotRound, label: '评论管理' },
  { index: '/admin/users', icon: User, label: '用户管理' },
  { index: '/admin/ai-config', icon: MagicStick, label: 'AI 服务配置' },
  { index: '/admin/logs', icon: Tickets, label: '操作日志' }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin/articles')) {
    return '/admin/articles'
  }
  return route.path
})

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="admin-brand" @click="router.push('/')">
        <span class="logo-mark">墨</span>
        <span>InkPress 管理后台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-icon :size="18"><Reading /></el-icon>
          <span class="muted">个人博客与文章管理系统</span>
        </div>
        <el-dropdown>
          <span class="user-chip">
            <el-avatar :size="30">{{ (userStore.user?.nickname || 'A').slice(0, 1) }}</el-avatar>
            <span class="nickname">{{ userStore.user?.nickname || '管理员' }}</span>
            <el-icon><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/')">前往前台</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.el-container {
  min-height: 100vh;
  background: var(--bg);
}

.admin-aside {
  background: var(--bg-soft);
  border-right: 2px solid var(--text);
  display: flex;
  flex-direction: column;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  font-weight: 900;
  letter-spacing: 0.04em;
  cursor: pointer;
  border-bottom: 1px solid var(--line);
  color: var(--text);
}

.logo-mark {
  width: 30px;
  height: 30px;
  background: var(--acid);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.admin-menu {
  border-right: none;
  flex: 1;
  background: transparent;
}

.admin-menu :deep(.el-menu-item) {
  color: var(--text-dim);
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.06em;
  border-left: 3px solid transparent;
}
.admin-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.04);
  color: var(--accent-hover);
}
.admin-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.045);
  color: var(--accent);
  border-left-color: var(--accent);
  font-weight: 600;
}

.admin-header {
  background: rgba(11, 11, 12, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 2px solid var(--text);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-dim);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.nickname {
  font-size: 14px;
  color: var(--text);
}

.admin-main {
  background: var(--bg);
}

.muted {
  color: var(--text-faint);
  font-size: 12px;
  font-family: var(--font-mono);
  letter-spacing: 0.1em;
}
</style>

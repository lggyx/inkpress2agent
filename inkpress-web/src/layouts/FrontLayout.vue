<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()
const keyword = ref('')

function onSearch() {
  router.push({ name: 'home', query: { keyword: keyword.value || undefined } })
}
</script>

<template>
  <div class="front-layout">
    <header class="site-header">
      <div class="container header-inner">
        <router-link to="/" class="site-logo">
          <span class="logo-mark">墨</span>
          <span class="logo-text">InkPress<em>墨记</em></span>
        </router-link>
        <nav class="site-nav">
          <router-link to="/">首页</router-link>
          <router-link to="/archive">归档</router-link>
        </nav>
        <div class="site-search">
          <el-input
            v-model="keyword"
            placeholder="搜索文章…"
            :prefix-icon="Search"
            clearable
            @keyup.enter="onSearch"
          />
        </div>
      </div>
    </header>

    <main class="site-main">
      <div class="container">
        <router-view />
      </div>
    </main>

    <footer class="site-footer">
      <div class="container">
        <p>InkPress · 墨记 — 基于 Spring Boot + Vue 3 的个人博客与文章管理系统</p>
        <p class="muted">毕业设计作品 · {{ new Date().getFullYear() }}</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.container {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 16px;
}

.site-header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 60px;
}

.site-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #303133;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.logo-text em {
  font-style: normal;
  font-size: 13px;
  color: #909399;
  margin-left: 6px;
}

.site-nav {
  display: flex;
  gap: 24px;
}

.site-nav a {
  color: #606266;
  font-size: 15px;
}

.site-nav a.router-link-active {
  color: #409eff;
  font-weight: 600;
}

.site-search {
  margin-left: auto;
  width: 220px;
}

.site-main {
  flex: 1;
  padding: 24px 0 48px;
}

.site-footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: #606266;
}

.site-footer .muted {
  color: #c0c4cc;
  margin: 4px 0 0;
}
</style>

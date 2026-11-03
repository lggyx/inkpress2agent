<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import KnowledgeAsk from '@/components/KnowledgeAsk.vue'

const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')

// 预加载层：作品集式入场仪式（500ms 进度动画）
const loading = ref(true)
const progress = ref(0)
let timer = null

onMounted(() => {
  const started = Date.now()
  timer = setInterval(() => {
    const elapsed = Date.now() - started
    progress.value = Math.min(100, Math.round((elapsed / 480) * 100))
    if (progress.value >= 100) {
      clearInterval(timer)
      setTimeout(() => {
        loading.value = false
      }, 120)
    }
  }, 30)
})

function onSearch() {
  router.push({ name: 'home', query: { keyword: keyword.value || undefined } })
}
</script>

<template>
  <!-- 预加载层 -->
  <transition name="fade">
    <div v-if="loading" class="preloader">
      <div class="preloader-inner">
        <div class="preloader-logo">INK<span>//</span>PRESS</div>
        <div class="preloader-bar">
          <div class="preloader-fill" :style="{ width: progress + '%' }"></div>
        </div>
        <div class="preloader-num mono">{{ String(progress).padStart(3, '0') }} / 100</div>
      </div>
    </div>
  </transition>

  <div class="front-layout">
    <header class="site-header">
      <div class="container header-inner">
        <nav class="nav-left">
          <router-link to="/" class="bracket" :class="{ active: $route.name === 'home' }">home</router-link>
          <router-link to="/archive" class="bracket" :class="{ active: $route.name === 'archive' }">archive</router-link>
        </nav>

        <router-link to="/" class="site-logo">
          <span class="logo-mark">墨</span>
          <span class="logo-text">INK<em>//</em>PRESS</span>
          <span class="logo-sub mono">墨记 · PERSONAL BLOG</span>
        </router-link>

        <div class="nav-right">
          <span class="bracket" @click="router.push(userStore.isLoggedIn && userStore.isAdmin ? '/admin/dashboard' : '/login')">
            {{ userStore.isLoggedIn && userStore.isAdmin ? 'admin' : 'login' }}
          </span>
          <el-input
            v-model="keyword"
            class="site-search"
            placeholder="SEARCH…"
            :prefix-icon="Search"
            clearable
            @keyup.enter="onSearch"
          />
        </div>
      </div>
    </header>

    <main class="site-main">
      <div class="container">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <footer class="site-footer">
      <div class="container footer-inner">
        <div class="footer-brand">
          <span class="logo-mark small">墨</span>
          <span>INK<em>//</em>PRESS</span>
        </div>
        <p class="footer-note mono">
          SPRING BOOT × VUE 3 · AI-ASSISTED WRITING · GRADUATION PROJECT
        </p>
        <p class="footer-copy mono">© {{ new Date().getFullYear() }} INKPRESS. ALL RIGHTS RESERVED.</p>
      </div>
    </footer>

    <!-- 站内知识问答（RAG） -->
    <KnowledgeAsk />
  </div>
</template>

<style scoped>
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.container {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
}

/* ---------- 预加载层 ---------- */
.preloader {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: var(--bg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.preloader-inner {
  width: 280px;
  text-align: center;
}
.preloader-logo {
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0.06em;
  color: var(--text);
  margin-bottom: 20px;
}
.preloader-logo span {
  color: var(--acid);
}
.preloader-bar {
  height: 4px;
  background: var(--line);
}
.preloader-fill {
  height: 100%;
  background: var(--acid);
  transition: width 0.05s linear;
}
.preloader-num {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-faint);
  text-align: right;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------- 页头 ---------- */
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(11, 11, 12, 0.86);
  backdrop-filter: blur(12px);
  border-bottom: 2px solid var(--text);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  height: 64px;
}

.nav-left,
.nav-right {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 1;
}
.nav-right {
  justify-content: flex-end;
}

.site-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 32px;
  height: 32px;
  background: var(--acid);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 800;
}
.logo-mark.small {
  width: 22px;
  height: 22px;
  font-size: 12px;
}

.logo-text {
  font-size: 19px;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: var(--text);
}
.logo-text em {
  font-style: normal;
  color: var(--acid);
}
.logo-sub {
  font-size: 10px;
  color: var(--text-faint);
  letter-spacing: 0.14em;
  margin-left: 2px;
}

.site-search {
  width: 170px;
}
.site-search :deep(.el-input__wrapper) {
  border-radius: 0;
}

/* ---------- 主体 / 页脚 ---------- */
.site-main {
  flex: 1;
  padding: 40px 0 72px;
}

.site-footer {
  border-top: 2px solid var(--text);
  padding: 28px 0 36px;
  background: var(--bg-soft);
}
.footer-inner {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 900;
  letter-spacing: 0.04em;
}
.footer-brand em {
  font-style: normal;
  color: var(--acid);
}
.footer-note {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--text-dim);
}
.footer-copy {
  margin: 0;
  font-size: 11px;
  color: var(--text-faint);
}

/* 页面切换动效 */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s, transform 0.22s;
}
.page-enter-from,
.page-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 860px) {
  .logo-sub {
    display: none;
  }
  .site-search {
    width: 120px;
  }
  .nav-left,
  .nav-right {
    gap: 10px;
  }
}
</style>

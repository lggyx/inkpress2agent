<script setup lang="ts">
import { useAuthUser, hydrateAuthUser } from '~/composables/useApi'

// 脱离博客默认布局：studio 使用独立 AI 工作台外壳
definePageMeta({ layout: 'studio' })

const user = useAuthUser()
onMounted(() => { hydrateAuthUser() })

// SPA 端登录门禁：未登录跳登录页
watchEffect(() => {
  if (process.client && user.value === null && !localStorage.getItem('inkpress.access')) {
    navigateTo('/login')
  }
})
</script>

<template>
  <div class="studio-shell">
    <!-- 氛围光晕 -->
    <div class="orb orb-a" aria-hidden="true" />
    <div class="orb orb-b" aria-hidden="true" />

    <!-- 图标侧栏 -->
    <aside class="rail">
      <NuxtLink to="/" class="rail-logo" title="返回博客">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2.5l2.1 5.4 5.4 2.1-5.4 2.1L12 17.5l-2.1-5.4-5.4-2.1 5.4-2.1L12 2.5z" />
          <path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15z" />
        </svg>
      </NuxtLink>
      <div class="rail-sep" />
      <nav class="rail-nav">
        <NuxtLink to="/studio/workbench" class="rail-btn" title="AI 工作台">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l1.9 4.7L18.5 9l-4.6 1.9L12 15.5l-1.9-4.6L5.5 9l4.6-1.3L12 3z" />
            <path d="M18.5 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" />
            <path d="M5 17.5l.6 1.5 1.5.6-1.5.6L5 21.7l-.6-1.5-1.5-.6 1.5-.6.6-1.5z" />
          </svg>
          <span class="rail-tip">AI 工作台</span>
        </NuxtLink>
        <NuxtLink to="/studio/articles" class="rail-btn" title="文章管理">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4.5" y="3" width="15" height="18" rx="2.5" />
            <path d="M8.5 8h7M8.5 12h7M8.5 16h4.5" />
          </svg>
          <span class="rail-tip">文章管理</span>
        </NuxtLink>
        <NuxtLink to="/studio/comments" class="rail-btn" title="评论审核">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5c-1.2 0-2.4-.3-3.4-.8L4 19.5l1.3-4.1A7.5 7.5 0 1 1 20 11.5z" />
          </svg>
          <span class="rail-tip">评论审核</span>
        </NuxtLink>
      </nav>
      <div class="rail-bottom">
        <NuxtLink to="/notifications" class="rail-btn" title="通知中心">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 9a6 6 0 1 0-12 0c0 6-2.5 7-2.5 7h17S18 15 18 9z" />
            <path d="M10 19.5a2.2 2.2 0 0 0 4 0" />
          </svg>
          <span class="rail-tip">通知中心</span>
        </NuxtLink>
        <NuxtLink to="/me" class="rail-avatar" :title="(user?.nickname || '个人中心')">
          {{ (user?.nickname || '·').slice(0, 1) }}
        </NuxtLink>
      </div>
    </aside>

    <main class="studio-main">
      <NuxtPage />
    </main>
  </div>
</template>

<style scoped>
/* ===== Studio 外壳：AI 工作台形态，与博客风格完全解耦（配色同源） ===== */
.studio-shell {
  --sbg: #0a0c12;
  --scard: rgba(20, 24, 36, .62);
  --scard-solid: #12151f;
  --sline: rgba(255, 255, 255, .07);
  --sprimary: #8ab4f8;
  --sprimary-deep: #5b8def;
  --sviolet: #b7a4f8;
  /* 重定义博客变量，让子页面自动进入暗色工作台语境 */
  --bg: var(--sbg);
  --card: var(--scard-solid);
  --card-soft: #1a1f2d;
  --line: var(--sline);
  --ink: #e8eaf2;
  --ink-soft: #9aa3b5;
  --ink-faint: #69718a;
  --primary: var(--sprimary);
  --primary-deep: var(--sprimary-deep);
  --primary-soft: rgba(138, 180, 248, .13);
  --code-bg: #0d1017;
  --radius: 14px;

  position: relative;
  display: flex;
  min-height: 100vh;
  background: var(--sbg);
  color: var(--ink);
  overflow: hidden;
}

/* 背景光晕：流动渐变（Web3 氛围） */
.orb {
  position: fixed;
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
}
.orb-a {
  width: 560px; height: 560px; top: -180px; left: 120px;
  background: radial-gradient(closest-side, rgba(138, 180, 248, .16), transparent 70%);
  animation: drift 26s ease-in-out infinite alternate;
}
.orb-b {
  width: 640px; height: 640px; bottom: -240px; right: -120px;
  background: radial-gradient(closest-side, rgba(183, 164, 248, .14), transparent 70%);
  animation: drift 32s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(60px, 40px) scale(1.15); }
}

/* ===== 图标侧栏 ===== */
.rail {
  position: relative;
  z-index: 2;
  width: 64px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0 16px;
  gap: 10px;
  border-right: 1px solid var(--sline);
  background: rgba(12, 14, 22, .72);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}
.rail-logo {
  width: 40px; height: 40px;
  display: grid; place-items: center;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, var(--sprimary-deep), var(--sviolet));
  box-shadow: 0 4px 18px rgba(91, 141, 239, .35);
  transition: transform .2s ease, box-shadow .2s ease;
}
.rail-logo:hover { transform: translateY(-1px) scale(1.04); box-shadow: 0 6px 22px rgba(138, 180, 248, .45); }
.rail-logo svg { width: 21px; height: 21px; }
.rail-sep { width: 26px; height: 1px; background: var(--sline); margin: 2px 0; }
.rail-nav, .rail-bottom { display: flex; flex-direction: column; gap: 6px; }
.rail-bottom { margin-top: auto; }
.rail-btn {
  position: relative;
  width: 42px; height: 42px;
  display: grid; place-items: center;
  border-radius: 12px;
  color: var(--ink-faint);
  transition: color .18s ease, background .18s ease, transform .18s ease;
}
.rail-btn svg { width: 20px; height: 20px; }
.rail-btn:hover { color: var(--ink); background: rgba(255, 255, 255, .06); transform: translateY(-1px); }
.rail-btn.router-link-active { color: var(--sprimary); background: rgba(138, 180, 248, .12); }
.rail-btn.router-link-active::before {
  content: '';
  position: absolute; left: -11px; top: 11px;
  width: 3px; height: 20px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--sprimary), var(--sviolet));
}
/* 悬浮提示 */
.rail-tip {
  position: absolute; left: 52px; top: 50%;
  transform: translateY(-50%) translateX(-4px);
  white-space: nowrap;
  font-size: .78em;
  color: var(--ink);
  background: #1c2130;
  border: 1px solid var(--sline);
  padding: 4px 10px;
  border-radius: 8px;
  opacity: 0;
  pointer-events: none;
  transition: opacity .18s ease, transform .18s ease;
  box-shadow: 0 6px 20px rgba(0, 0, 0, .4);
}
.rail-btn:hover .rail-tip { opacity: 1; transform: translateY(-50%) translateX(0); }
.rail-avatar {
  width: 36px; height: 36px;
  display: grid; place-items: center;
  border-radius: 50%;
  font-weight: 700; font-size: .95em;
  color: #fff;
  background: linear-gradient(135deg, var(--sprimary-deep), var(--sviolet));
  transition: transform .18s ease, box-shadow .18s ease;
}
.rail-avatar:hover { transform: scale(1.08); box-shadow: 0 0 0 3px rgba(138, 180, 248, .25); }

.studio-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.studio-main > :deep(*) { flex: 1; }
</style>

<style>
/* 全局前缀样式：只作用于 studio 外壳内（el-dialog 默认就地渲染） */
.studio-shell .el-dialog {
  background: #141826;
  border: 1px solid rgba(255, 255, 255, .08);
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .5);
}
.studio-shell .el-dialog__title { color: #e8eaf2; font-weight: 650; }
.studio-shell .el-dialog__body { color: #c6cbd9; }
.studio-shell .el-message-box {
  background: #141826;
  border: 1px solid rgba(255, 255, 255, .08);
  border-radius: 14px;
}
.studio-shell .el-message-box__title { color: #e8eaf2; }
.studio-shell .el-message-box__message { color: #c6cbd9; }
.studio-shell .el-loading-mask { background: rgba(10, 12, 18, .6); }
</style>

<script setup lang="ts">
import { useAuthUser, hydrateAuthUser } from '~/composables/useApi'
import { useTheme } from '~/composables/useTheme'

// 工作台共享外壳（博主端 + 管理端）：图标侧栏 + 氛围光晕 + 主题切换
const props = defineProps<{ area: 'studio' | 'admin' }>()

const user = useAuthUser()
const { theme, init: initTheme, toggle: toggleTheme } = useTheme()
onMounted(() => {
  hydrateAuthUser()
  initTheme()
})

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
        <!-- 博主端 -->
        <template v-if="area === 'studio'">
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
          <NuxtLink to="/studio/evolution" class="rail-btn" title="进化面板">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 19c4.5 0 4.5-7 8-7s3.5 4 8 4" />
              <path d="M16.5 12.5L20 16l-3.5 3.5" />
              <path d="M4 8c2.5 0 3.5-3 6-3" />
              <circle cx="12" cy="5" r="1.4" />
            </svg>
            <span class="rail-tip">进化面板</span>
          </NuxtLink>
          <NuxtLink to="/studio/comments" class="rail-btn" title="评论审核">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5c-1.2 0-2.4-.3-3.4-.8L4 19.5l1.3-4.1A7.5 7.5 0 1 1 20 11.5z" />
            </svg>
            <span class="rail-tip">评论审核</span>
          </NuxtLink>
        </template>
        <!-- 管理端 -->
        <template v-else>
          <NuxtLink to="/admin" class="rail-btn" exact-active-class="router-link-active" title="仪表盘">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3.5" y="3.5" width="7.4" height="7.4" rx="2" />
              <rect x="13.1" y="3.5" width="7.4" height="7.4" rx="2" />
              <rect x="3.5" y="13.1" width="7.4" height="7.4" rx="2" />
              <rect x="13.1" y="13.1" width="7.4" height="7.4" rx="2" />
            </svg>
            <span class="rail-tip">仪表盘</span>
          </NuxtLink>
          <NuxtLink to="/admin/stats" class="rail-btn" title="画像总览">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />
            </svg>
            <span class="rail-tip">画像总览</span>
          </NuxtLink>
          <NuxtLink to="/admin/ai" class="rail-btn" title="AI 配置">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 8h9M17 8h3M4 16h3M11 16h9" />
              <circle cx="15" cy="8" r="2.2" />
              <circle cx="9" cy="16" r="2.2" />
            </svg>
            <span class="rail-tip">AI 配置</span>
          </NuxtLink>
          <NuxtLink to="/admin/templates" class="rail-btn" title="提示词模板">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <rect x="4.5" y="3" width="15" height="18" rx="2.5" />
              <path d="M8.5 8h7M8.5 12h7M8.5 16h4.5" />
            </svg>
            <span class="rail-tip">提示词模板</span>
          </NuxtLink>
          <NuxtLink to="/admin/words" class="rail-btn" title="敏感词库">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l7.5 3v5.5c0 4.6-3.2 7.9-7.5 9.5-4.3-1.6-7.5-4.9-7.5-9.5V6l7.5-3z" />
              <path d="M9 11.5l2.2 2.2L15.5 9.4" />
            </svg>
            <span class="rail-tip">敏感词库</span>
          </NuxtLink>
          <NuxtLink to="/admin/dlq" class="rail-btn" title="死信队列">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3.5 13.5L6 6.5A2 2 0 0 1 7.9 5h8.2a2 2 0 0 1 1.9 1.5l2.5 7v4a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5v-4z" />
              <path d="M3.5 13.5h5a3.5 3.5 0 0 0 7 0h5" />
            </svg>
            <span class="rail-tip">死信队列</span>
          </NuxtLink>
          <NuxtLink to="/admin/logs" class="rail-btn" title="日志查询">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
              <path d="M7.5 9.5l3 2.5-3 2.5M12.5 15h4.5" />
            </svg>
            <span class="rail-tip">日志查询</span>
          </NuxtLink>
          <NuxtLink to="/admin/oss" class="rail-btn" title="对象存储">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M7 18.5a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 8.6a4 4 0 0 1-.6 7.9H7z" />
            </svg>
            <span class="rail-tip">对象存储</span>
          </NuxtLink>
        </template>
      </nav>
      <div class="rail-bottom">
        <!-- 跨端入口：博主端↔管理端按角色显示 -->
        <NuxtLink
          v-if="area === 'studio' && user?.roles?.includes('ADMIN')"
          to="/admin" class="rail-btn" title="管理端"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l7.5 3v5.5c0 4.6-3.2 7.9-7.5 9.5-4.3-1.6-7.5-4.9-7.5-9.5V6l7.5-3z" />
            <path d="M9 12l2.2 2.2L15.5 10" />
          </svg>
          <span class="rail-tip">管理端</span>
        </NuxtLink>
        <NuxtLink
          v-if="area === 'admin' && user?.roles?.includes('BLOGGER')"
          to="/studio/workbench" class="rail-btn" title="博主端"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l1.9 4.7L18.5 9l-4.6 1.9L12 15.5l-1.9-4.6L5.5 9l4.6-1.3L12 3z" />
            <path d="M18.5 14l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" />
          </svg>
          <span class="rail-tip">博主端</span>
        </NuxtLink>
        <button class="rail-btn" :title="theme === 'dark' ? '切换浅色' : '切换深色'" @click="toggleTheme()">
          <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <circle cx="12" cy="12" r="4.2" />
            <path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M18.5 5.5l-1.7 1.7M7.2 16.8l-1.7 1.7" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 13.2A8.2 8.2 0 0 1 10.8 4a7.6 7.6 0 1 0 9.2 9.2z" />
          </svg>
          <span class="rail-tip">{{ theme === 'dark' ? '切换浅色' : '切换深色' }}</span>
        </button>
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
      <slot />
    </main>
  </div>
</template>

<style scoped>
/* ===== 外壳布局 ===== */
.studio-shell {
  position: relative;
  display: flex;
  min-height: 100vh;
  background: var(--sbg);
  color: var(--ink);
}

/* 背景光晕：极光流动（紫/青双团，柔和模糊） */
/* 极光球已移除（野兽派：无辉光无漂移斑块），类保留作兼容 */
.orb { display: none; }
.orb-a, .orb-b { display: none; }
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
  border-right: 2px solid var(--sline);
  background: var(--srail);
}
.rail-logo {
  width: 40px; height: 40px;
  display: grid; place-items: center;
  border-radius: 0;
  color: var(--accent-ink);
  background: var(--c-lime);
  border: 2px solid var(--ink);
  box-shadow: 3px 3px 0 var(--ink);
  transition: transform .12s ease, box-shadow .12s ease;
}
.rail-logo:hover { transform: translate(-2px, -2px); box-shadow: 5px 5px 0 var(--ink); }
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
  border: 0;
  background: transparent;
  transition: color .18s ease, background .18s ease, transform .25s cubic-bezier(.34, 1.56, .64, 1);
}
.rail-btn svg { width: 20px; height: 20px; }
.rail-btn:hover { color: var(--ink); background: rgba(128, 128, 128, .12); transform: scale(1.1); }
.rail-btn:active { transform: scale(.96); }
.rail-btn.router-link-active { color: var(--sprimary); background: rgba(167, 139, 250, .09); }
.rail-btn.router-link-active::before {
  content: '';
  position: absolute; left: -11px; top: 11px;
  width: 3px; height: 20px;
  border-radius: 2px;
  background: var(--sprimary);
}
/* 悬浮提示 */
.rail-tip {
  position: absolute; left: 52px; top: 50%;
  transform: translateY(-50%) translateX(-4px);
  white-space: nowrap;
  font-size: .78em;
  color: var(--ink);
  background: var(--spop-solid);
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
  color: #101012;
  background: var(--sprimary);
  transition: transform .18s ease, box-shadow .18s ease;
}
.rail-avatar:hover { transform: scale(1.08); box-shadow: 0 0 0 3px rgba(167, 139, 250, .25); }

.studio-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.studio-main > :deep(*) { flex: 1; }

/* ===== 移动端：侧栏转底部导航条 ===== */
@media (max-width: 720px) {
  .rail {
    position: fixed;
    left: 0; right: 0; bottom: 0; top: auto;
    width: 100%;
    height: 58px;
    flex-direction: row;
    align-items: center;
    padding: 4px 8px calc(4px + env(safe-area-inset-bottom));
    border-right: 0;
    border-top: 1px solid var(--sline);
    box-shadow: 0 -10px 30px rgba(0, 0, 0, .35);
    gap: 0;
  }
  .rail-logo, .rail-sep { display: none; }
  .rail-nav {
    flex-direction: row;
    flex: 1;
    min-width: 0;
    overflow-x: auto;
    justify-content: flex-start;
    scrollbar-width: none;
    gap: 2px;
  }
  .rail-nav::-webkit-scrollbar { display: none; }
  .rail-bottom { flex-direction: row; margin: 0 0 0 auto; gap: 2px; }
  .rail-btn { width: 42px; height: 40px; flex-shrink: 0; }
  .rail-btn.router-link-active::before {
    left: 50%; top: auto; bottom: -5px;
    transform: translateX(-50%);
    width: 16px; height: 3px;
  }
  .rail-tip { display: none; }
  .rail-avatar { width: 32px; height: 32px; }
  .studio-main { padding-bottom: calc(66px + env(safe-area-inset-bottom)); }
}
</style>

<style>
/* ===== 主题变量：野兽派白纸体系（与站点 main.css 同语言），暗色为暖墨变体 ===== */
.studio-shell {
  --sbg: #14120e;
  --scard: #211e18;
  --scard-solid: #211e18;
  --sline: rgba(245, 243, 238, .18);
  --sprimary: #8f9bff;
  --sprimary-deep: #c8f04b;
  --sprimary-2: #c8f04b;
  --srail: rgba(20, 18, 14, .92);
  --sglass: #211e18;
  --spop: #211e18;
  --spop-solid: #211e18;
  --sinset: #0c0b08;
  --sbubble: rgba(245, 243, 238, .07);
  --spane: #211e18;
  --sorba: rgba(143, 155, 255, .12);
  --sorbb: rgba(200, 240, 75, .08);
  --shadow-pane: 6px 6px 0 #000;
  --heroa: #c8f04b;
  --herob: #c8f04b;
  --grad-btn: #c8f04b;
  --grad-text: #c8f04b;
  --glass-hi: none;
  --shadow-card: 4px 4px 0 #000;
  --accent-ink: #14120e;
  --c-violet: #8f9bff;
  --c-cyan: #5eead4;
  --c-lime: #c8f04b;
  --c-orange: #ff8a5c;
  --c-pink: #ff7aa8;
  --c-blue: #8f9bff;
  /* 重定义博客变量，让子页面自动进入工作台语境 */
  --bg: var(--sbg);
  --card: var(--scard);
  --card-soft: #1a1813;
  --line: var(--sline);
  --ink: #f5f3ee;
  --ink-soft: #b8b3a6;
  --ink-faint: #837e72;
  --primary: var(--sprimary);
  --primary-deep: var(--sprimary-deep);
  --primary-soft: rgba(143, 155, 255, .14);
  --code-bg: #0c0b08;
  --radius: 0;
}
:root[data-theme='light'] .studio-shell {
  --sbg: #f5f3ee;
  --scard: #ffffff;
  --scard-solid: #ffffff;
  --sline: rgba(20, 18, 14, .16);
  --srail: rgba(245, 243, 238, .94);
  --sglass: #ffffff;
  --spop: #ffffff;
  --spop-solid: #ffffff;
  --sinset: #edeae2;
  --sbubble: rgba(20, 18, 14, .06);
  --spane: #ffffff;
  --sorba: rgba(59, 76, 224, .08);
  --sorbb: rgba(200, 240, 75, .14);
  --shadow-pane: 6px 6px 0 #14120e;
  --heroa: #3b4ce0;
  --herob: #3b4ce0;
  --grad-btn: #14120e;
  --grad-text: #14120e;
  --glass-hi: none;
  --shadow-card: 4px 4px 0 #14120e;
  --accent-ink: #f5f3ee;
  --c-violet: #3b4ce0;
  --c-cyan: #0f766e;
  --c-lime: #c8f04b;
  --c-orange: #ff6b2c;
  --c-pink: #ff4d8d;
  --c-blue: #3b4ce0;
  --card: #ffffff;
  --card-soft: #edeae2;
  --ink: #14120e;
  --ink-soft: #4a463c;
  --ink-faint: #8a8578;
  --primary: #3b4ce0;
  --primary-deep: #2f3cb8;
  --primary-soft: rgba(59, 76, 224, .1);
  --code-bg: #14120e;
}

/* ===== studio 管理页共享：纸墨行列表（articles/comments/admin 等） ===== */
.studio-shell .page {
  padding: 24px 30px;
  max-width: 1240px;
  margin: 0 auto;
  width: 100%;
}
.studio-shell .page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  gap: 14px;
}
.studio-shell .page-head h2 { margin: 0; font-size: 1.35em; font-weight: 900; letter-spacing: .5px; }
.studio-shell .page-head .sub { font-size: .84em; color: var(--ink-faint); margin-left: 12px; }
.studio-shell .head-actions { display: flex; gap: 10px; align-items: center; }

.studio-shell .row {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 15px 20px;
  margin-bottom: 10px;
  border: 2px solid var(--sline);
  border-radius: 0;
  background: var(--scard);
  transition: border-color .12s ease, transform .12s ease, box-shadow .12s ease;
  animation: s-row-in .3s cubic-bezier(.2, .7, .3, 1) both;
}
.studio-shell .row:hover {
  border-color: var(--ink);
  transform: translate(-2px, -2px);
  box-shadow: 5px 5px 0 var(--ink);
}
.studio-shell .row:hover::before { opacity: 1; }
@keyframes s-row-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}
.studio-shell .row-main { flex: 1; min-width: 0; }
.studio-shell .row-title {
  font-weight: 650;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.studio-shell .row-title:hover { color: var(--sprimary); text-decoration: none; }
.studio-shell .row-sub {
  color: var(--ink-faint);
  font-size: .8em;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.studio-shell .row-sub a { color: var(--primary); }
.studio-shell .pin {
  color: var(--accent-ink);
  font-size: .74em;
  font-weight: 700;
  border: 1.5px solid var(--c-lime);
  background: var(--c-lime);
  border-radius: 2px;
  padding: 0 6px;
  margin-right: 8px;
  vertical-align: 1px;
}
.studio-shell .ai-tag {
  color: var(--primary);
  font-size: .9em;
  border: 1px solid var(--sline);
  border-radius: 5px;
  padding: 0 6px;
  margin-left: 8px;
}
.studio-shell .row-stats {
  display: flex;
  gap: 14px;
  color: var(--ink-faint);
  font-size: .82em;
  white-space: nowrap;
  font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
}
.studio-shell .row-actions { display: flex; gap: 8px; flex-shrink: 0; }
.studio-shell .abtn {
  font-size: .8em;
  padding: 5px 13px;
  border-radius: 999px;
  border: 1px solid var(--sline);
  background: transparent;
  color: var(--ink-soft);
  transition: all .16s ease;
  white-space: nowrap;
}
.studio-shell .abtn:hover { color: var(--ink); border-color: rgba(167, 139, 250, .45); background: rgba(128, 128, 128, .08); transform: translateY(-1px) scale(1.05); }
.studio-shell .abtn:active { transform: scale(.96); }
.studio-shell .abtn.danger { color: #f28b82; border-color: rgba(242, 139, 130, .35); }
.studio-shell .abtn.danger:hover { background: rgba(242, 139, 130, .1); border-color: rgba(242, 139, 130, .6); }
.studio-shell .abtn.ok { color: #81c995; border-color: rgba(129, 201, 149, .35); }
.studio-shell .abtn.ok:hover { background: rgba(129, 201, 149, .1); border-color: rgba(129, 201, 149, .6); }
.studio-shell .btn.primary {
  display: inline-block;
  padding: 8px 18px;
  border-radius: 10px;
  font-size: .88em;
  font-weight: 700;
  background: var(--grad-btn);
  color: #fff;
  border: 0;
  box-shadow: 0 4px 18px rgba(124, 58, 237, .35);
  transition: all .2s cubic-bezier(.34, 1.56, .64, 1);
}
.studio-shell .btn.primary:hover {
  filter: brightness(1.08);
  transform: translateY(-1px) scale(1.04);
  color: #fff;
  box-shadow: 0 8px 28px rgba(124, 58, 237, .5), 0 0 0 1px rgba(167, 139, 250, .45);
}
.studio-shell .btn.ghost {
  display: inline-block;
  padding: 8px 16px;
  border-radius: 10px;
  font-size: .88em;
  font-weight: 600;
  border: 1px solid var(--sline);
  background: var(--sglass);
  color: var(--ink-soft);
  transition: all .2s cubic-bezier(.34, 1.56, .64, 1);
}
.studio-shell .btn.ghost:hover { color: var(--ink); border-color: rgba(167, 139, 250, .4); transform: translateY(-1px) scale(1.03); }

/* 状态 pill（与工作台一致） */
.studio-shell .st {
  flex-shrink: 0;
  font-size: .72em;
  padding: 2px 10px;
  border-radius: 999px;
  letter-spacing: .5px;
  white-space: nowrap;
}
.studio-shell .st-DRAFT { background: rgba(154, 163, 181, .16); color: #aab2c4; }
.studio-shell .st-PUBLISHED { background: rgba(129, 201, 149, .15); color: #81c995; }
.studio-shell .st-PENDING_REVIEW { background: rgba(248, 187, 102, .15); color: #f8bb66; }
.studio-shell .st-REJECTED { background: rgba(242, 139, 130, .15); color: #f28b82; }
.studio-shell .st-OFFLINE { background: rgba(154, 163, 181, .16); color: #aab2c4; }
:root[data-theme='light'] .studio-shell .st-DRAFT { background: rgba(0, 0, 0, .06); color: #5f6773; }
:root[data-theme='light'] .studio-shell .st-PUBLISHED { background: rgba(24, 128, 56, .12); color: #188038; }
:root[data-theme='light'] .studio-shell .st-PENDING_REVIEW { background: rgba(194, 129, 10, .12); color: #a86f08; }
:root[data-theme='light'] .studio-shell .st-REJECTED { background: rgba(217, 48, 37, .1); color: #d93025; }
:root[data-theme='light'] .studio-shell .st-OFFLINE { background: rgba(0, 0, 0, .06); color: #5f6773; }

/* Element 分页/空态融入主题 */
.studio-shell .el-pagination { --el-pagination-bg-color: transparent; --el-pagination-text-color: var(--ink-soft); --el-pagination-button-disabled-bg-color: transparent; }
.studio-shell .el-pagination .el-pager li { background: transparent; border-radius: 8px; }
.studio-shell .el-pagination .el-pager li.is-active { color: var(--sprimary); background: var(--primary-soft); }
.studio-shell .el-empty__description p { color: var(--ink-faint); }
@media (max-width: 900px) {
  .studio-shell .row { flex-wrap: wrap; }
  .studio-shell .row-stats { display: none; }
}
/* ===== 移动端共享：留白收紧 + 弹窗全宽 ===== */
@media (max-width: 720px) {
  .studio-shell .page { padding: 16px 14px 20px; }
  .studio-shell .page-head { flex-wrap: wrap; row-gap: 10px; }
  .studio-shell .page-head .sub { display: block; margin-left: 0; width: 100%; }
}
@media (max-width: 640px) {
  .studio-shell .el-dialog {
    width: calc(100vw - 18px) !important;
    max-width: calc(100vw - 18px) !important;
    margin-top: 7vh !important;
  }
  .studio-shell .el-message-box { width: calc(100vw - 30px) !important; }
}

/* ===== 苹果式模态：背景化霜 + 玻璃面板（按主题分色） ===== */
.studio-shell .el-overlay {
  backdrop-filter: blur(26px) saturate(140%);
  -webkit-backdrop-filter: blur(26px) saturate(140%);
}
:root[data-theme='dark'] .studio-shell .el-overlay { background: rgba(8, 10, 16, .42); }
:root[data-theme='light'] .studio-shell .el-overlay { background: rgba(0, 0, 0, .16); }
.studio-shell .el-dialog { border-radius: 18px; }
:root[data-theme='dark'] .studio-shell .el-dialog {
  background: rgba(22, 26, 40, .72);
  backdrop-filter: blur(40px) saturate(160%);
  -webkit-backdrop-filter: blur(40px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, .1);
  box-shadow: 0 32px 90px rgba(0, 0, 0, .6), inset 0 1px 0 rgba(255, 255, 255, .09);
}
:root[data-theme='light'] .studio-shell .el-dialog {
  background: rgba(255, 255, 255, .86);
  backdrop-filter: blur(40px) saturate(140%);
  -webkit-backdrop-filter: blur(40px) saturate(140%);
  border: 1px solid rgba(0, 0, 0, .08);
  box-shadow: 0 32px 90px rgba(0, 0, 0, .18), inset 0 1px 0 rgba(255, 255, 255, .8);
}
.studio-shell .el-dialog__title { color: var(--ink); font-weight: 650; }
.studio-shell .el-dialog__body { color: var(--ink-soft); }
.studio-shell .el-message-box { border-radius: 16px; }
:root[data-theme='dark'] .studio-shell .el-message-box {
  background: rgba(22, 26, 40, .78);
  backdrop-filter: blur(40px) saturate(160%);
  -webkit-backdrop-filter: blur(40px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, .1);
  box-shadow: 0 32px 90px rgba(0, 0, 0, .6), inset 0 1px 0 rgba(255, 255, 255, .09);
}
:root[data-theme='light'] .studio-shell .el-message-box {
  background: rgba(255, 255, 255, .88);
  backdrop-filter: blur(40px) saturate(140%);
  -webkit-backdrop-filter: blur(40px) saturate(140%);
  border: 1px solid rgba(0, 0, 0, .08);
  box-shadow: 0 32px 90px rgba(0, 0, 0, .18), inset 0 1px 0 rgba(255, 255, 255, .8);
}
.studio-shell .el-message-box__title { color: var(--ink); }
.studio-shell .el-message-box__message { color: var(--ink-soft); }
:root[data-theme='dark'] .studio-shell .el-loading-mask { background: rgba(10, 12, 18, .55); }
:root[data-theme='light'] .studio-shell .el-loading-mask { background: rgba(255, 255, 255, .6); }
.studio-shell .el-loading-mask { backdrop-filter: blur(4px); }
</style>

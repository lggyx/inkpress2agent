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
  background: rgba(12, 14, 22, .55);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, .03);
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
  transition: color .18s ease, background .18s ease, transform .25s cubic-bezier(.34, 1.56, .64, 1);
}
.rail-btn svg { width: 20px; height: 20px; }
.rail-btn:hover { color: var(--ink); background: rgba(255, 255, 255, .08); transform: scale(1.1); }
.rail-btn:active { transform: scale(.96); }
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
/* 苹果式模态：背景化霜 + 玻璃面板 + 顶面高光 */
.studio-shell .el-overlay {
  backdrop-filter: blur(26px) saturate(140%);
  -webkit-backdrop-filter: blur(26px) saturate(140%);
  background: rgba(8, 10, 16, .42);
}
.studio-shell .el-dialog {
  background: rgba(22, 26, 40, .72);
  backdrop-filter: blur(40px) saturate(160%);
  -webkit-backdrop-filter: blur(40px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, .1);
  border-radius: 18px;
  box-shadow: 0 32px 90px rgba(0, 0, 0, .6), inset 0 1px 0 rgba(255, 255, 255, .09);
}
.studio-shell .el-dialog__title { color: #e8eaf2; font-weight: 650; }
.studio-shell .el-dialog__body { color: #c6cbd9; }
.studio-shell .el-message-box {
  background: rgba(22, 26, 40, .78);
  backdrop-filter: blur(40px) saturate(160%);
  -webkit-backdrop-filter: blur(40px) saturate(160%);
  border: 1px solid rgba(255, 255, 255, .1);
  border-radius: 16px;
  box-shadow: 0 32px 90px rgba(0, 0, 0, .6), inset 0 1px 0 rgba(255, 255, 255, .09);
}
.studio-shell .el-message-box__title { color: #e8eaf2; }
.studio-shell .el-message-box__message { color: #c6cbd9; }
.studio-shell .el-loading-mask { background: rgba(10, 12, 18, .55); backdrop-filter: blur(4px); }

/* ===== studio 管理页共享：玻璃行列表（articles/comments 等） ===== */
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
.studio-shell .page-head h2 { margin: 0; font-size: 1.35em; font-weight: 750; letter-spacing: .5px; }
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
  border: 1px solid var(--sline);
  border-radius: 14px;
  background: var(--scard);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: border-color .18s ease, transform .18s ease, box-shadow .18s ease;
  animation: s-row-in .3s cubic-bezier(.2, .7, .3, 1) both;
}
/* 状态色脊条（spine-* 类由页面按行状态绑定） */
.studio-shell .row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: var(--spine, transparent);
  opacity: .8;
}
.studio-shell .spine-PUBLISHED { --spine: #81c995; }
.studio-shell .spine-DRAFT { --spine: #9aa3b5; }
.studio-shell .spine-PENDING_REVIEW { --spine: #f8bb66; }
.studio-shell .spine-PENDING { --spine: #f8bb66; }
.studio-shell .spine-PENDING_AUDIT { --spine: #f8bb66; }
.studio-shell .spine-REJECTED { --spine: #f28b82; }
.studio-shell .spine-OFFLINE { --spine: #6b7280; }
.studio-shell .row:hover {
  border-color: rgba(138, 180, 248, .45);
  transform: translateY(-2px) scale(1.003);
  box-shadow: 0 14px 40px rgba(0, 0, 0, .45), inset 0 1px 0 rgba(255, 255, 255, .09);
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
.studio-shell .row-sub a { color: var(--sprimary); }
.studio-shell .pin {
  color: #f8bb66;
  font-size: .74em;
  border: 1px solid rgba(248, 187, 102, .5);
  border-radius: 5px;
  padding: 0 6px;
  margin-right: 8px;
  vertical-align: 1px;
}
.studio-shell .ai-tag {
  color: var(--sviolet);
  font-size: .9em;
  border: 1px solid rgba(183, 164, 248, .4);
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
.studio-shell .abtn:hover { color: var(--ink); border-color: rgba(138, 180, 248, .45); background: rgba(255, 255, 255, .05); transform: translateY(-1px) scale(1.05); }
.studio-shell .abtn:active { transform: scale(.96); }
.studio-shell .abtn.danger { color: #f28b82; border-color: rgba(242, 139, 130, .35); }
.studio-shell .abtn.danger:hover { background: rgba(242, 139, 130, .1); border-color: rgba(242, 139, 130, .6); }
.studio-shell .abtn.ok { color: #81c995; border-color: rgba(129, 201, 149, .35); }
.studio-shell .abtn.ok:hover { background: rgba(129, 201, 149, .1); border-color: rgba(129, 201, 149, .6); }
.studio-shell .btn.primary {
  display: inline-block;
  padding: 8px 18px;
  border-radius: 11px;
  font-size: .88em;
  font-weight: 600;
  background: linear-gradient(135deg, var(--sprimary-deep), #9a7bf5);
  color: #fff;
  border: 0;
  box-shadow: 0 4px 16px rgba(91, 141, 239, .32);
  transition: all .2s cubic-bezier(.34, 1.56, .64, 1);
}
.studio-shell .btn.primary:hover {
  filter: brightness(1.08);
  transform: translateY(-1px) scale(1.04);
  color: #fff;
  box-shadow: 0 8px 26px rgba(138, 180, 248, .45);
}
.studio-shell .btn.primary:active { transform: scale(.97); }

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

/* Element 分页/空态融入暗色 */
.studio-shell .el-pagination { --el-pagination-bg-color: transparent; --el-pagination-text-color: #9aa3b5; --el-pagination-button-disabled-bg-color: transparent; }
.studio-shell .el-pagination .el-pager li { background: transparent; border-radius: 8px; }
.studio-shell .el-pagination .el-pager li.is-active { color: var(--sprimary); background: var(--primary-soft); }
.studio-shell .el-empty__description p { color: var(--ink-faint); }
@media (max-width: 900px) {
  .studio-shell .row { flex-wrap: wrap; }
  .studio-shell .row-stats { display: none; }
}
</style>

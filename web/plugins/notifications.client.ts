import { accessToken } from '~/composables/useApi'

/** 登录态下启动通知 SSE 长连接（应用加载后一次，断线由 composable 内自愈重连） */
export default defineNuxtPlugin(() => {
  const { startStream, refreshList } = useNotifications()
  setTimeout(() => {
    if (accessToken()) {
      startStream()
      refreshList().catch(() => { /* 静默 */ })
    }
  }, 800)
})

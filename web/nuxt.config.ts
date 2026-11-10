export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api'
    }
  },
  routeRules: {
    // 前台内容页走 SSR/ISR，利于 SEO 与首屏
    '/': { isr: 60 },
    '/article/**': { isr: 60 },
    '/archive': { isr: 300 },
    // 博主端 / 管理员端为纯 SPA
    '/studio/**': { ssr: false },
    '/admin/**': { ssr: false }
  }
})

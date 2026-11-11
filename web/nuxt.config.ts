export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  devtools: { enabled: true },
  runtimeConfig: {
    // SSR 侧（容器内直连 server，绕开 Nginx）；浏览器端用 public.apiBase 经 Nginx 相对路径
    apiBase: process.env.NUXT_API_BASE || 'http://localhost:8080/api',
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api'
    }
  },
  app: {
    head: {
      title: 'InkPress · 个人博客与文章管理',
      htmlAttrs: { lang: 'zh-CN', class: 'dark' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'InkPress：写作、发布、分发一体的个人博客与文章管理系统' }
      ]
    }
  },
  css: ['~/assets/css/main.css'],
  routeRules: {
    // 前台内容页走 SSR/ISR，利于 SEO 与首屏
    '/': { isr: 60 },
    '/article/**': { isr: 60 },
    '/archive': { isr: 300 },
    '/graph': { ssr: false },
    // 博主端 / 管理员端 / 个人中心为纯 SPA
    '/studio/**': { ssr: false },
    '/admin/**': { ssr: false },
    '/me': { ssr: false }
  }
})

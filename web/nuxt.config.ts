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
      ],
      // 首屏前设置主题，避免闪烁
      script: [
        {
          innerHTML: "(function(){try{var t=localStorage.getItem('inkpress.theme')||'dark';document.documentElement.setAttribute('data-theme',t);document.documentElement.classList.toggle('dark',t==='dark');}catch(e){document.documentElement.setAttribute('data-theme','dark');}})();"
        }
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
    // 门户根路径直接落到默认功能页（裸布局页无内容，避免空白）
    '/studio': { redirect: { to: '/studio/articles', statusCode: 302 } },
    '/admin': { redirect: { to: '/admin/ai', statusCode: 302 } },
    // 博主端 / 管理员端 / 个人中心为纯 SPA
    '/studio/**': { ssr: false },
    '/admin/**': { ssr: false },
    '/me': { ssr: false }
  }
})

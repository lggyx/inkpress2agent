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
      title: 'InkPress · 会自我进化的 AI 博客',
      htmlAttrs: { lang: 'zh-CN', class: 'dark' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'InkPress：会自我进化的 AI 博客——从每次写作与访问反馈中学习，越用越懂你。' }
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
  build: {
    vite: {
      build: {
        rollupOptions: {
          output: {
            // 首屏主 chunk 1.1MB → 大依赖各自成 chunk：并行加载 + 长缓存（各页引用才拉取）
            manualChunks(id) {
              if (id.includes('node_modules')) {
                if (id.includes('echarts')) return 'vendor-echarts'
                if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element'
                if (id.includes('vditor')) return 'vendor-vditor'
                if (id.includes('katex')) return 'vendor-katex'
                if (id.includes('marked') || id.includes('dompurify')) return 'vendor-markdown'
              }
              return undefined
            },
          },
        },
      },
    },
  },
  routeRules: {
    // 前台内容页走 SSR/ISR，利于 SEO 与首屏
    '/': { isr: 60 },
    '/article/**': { isr: 60 },
    '/categories': { isr: 300 },
    '/tags': { isr: 300 },
    '/graph': { ssr: false },
    // 搜索页依赖 query 交互，客户端渲染
    '/search': { ssr: false },
    // 门户根路径直接落到默认功能页（/admin 已有仪表盘、/studio 已有工作台，不再重定向）
    '/studio': { redirect: { to: '/studio/workbench', statusCode: 302 } },
    // 博主端 / 管理员端 / 个人中心为纯 SPA
    '/studio/**': { ssr: false },
    '/admin/**': { ssr: false },
    '/me': { ssr: false }
  }
})

import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/FrontLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/front/HomeView.vue') },
      { path: 'article/:id', name: 'article-detail', component: () => import('@/views/front/ArticleDetailView.vue') },
      { path: 'archive', name: 'archive', component: () => import('@/views/front/ArchiveView.vue') }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/admin/LoginView.vue')
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/admin/DashboardView.vue'), meta: { requiresAdmin: true } },
      { path: 'articles', name: 'article-manage', component: () => import('@/views/admin/ArticleManageView.vue'), meta: { requiresAdmin: true } },
      { path: 'articles/edit', name: 'article-create', component: () => import('@/views/admin/ArticleEditView.vue'), meta: { requiresAdmin: true } },
      { path: 'articles/edit/:id', name: 'article-edit', component: () => import('@/views/admin/ArticleEditView.vue'), meta: { requiresAdmin: true } },
      { path: 'categories', name: 'category-manage', component: () => import('@/views/admin/CategoryManageView.vue'), meta: { requiresAdmin: true } },
      { path: 'tags', name: 'tag-manage', component: () => import('@/views/admin/TagManageView.vue'), meta: { requiresAdmin: true } },
      { path: 'comments', name: 'comment-manage', component: () => import('@/views/admin/CommentManageView.vue'), meta: { requiresAdmin: true } },
      { path: 'users', name: 'user-manage', component: () => import('@/views/admin/UserManageView.vue'), meta: { requiresAdmin: true } },
      { path: 'logs', name: 'log-manage', component: () => import('@/views/admin/LogManageView.vue'), meta: { requiresAdmin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(to => {
  const store = useUserStore()
  if (to.meta.requiresAdmin && (!store.token || !store.isAdmin)) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router

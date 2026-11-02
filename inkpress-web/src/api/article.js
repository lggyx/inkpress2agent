import request from '@/utils/request'

// ===== 前台 =====
export const frontPage = params => request.get('/articles/page', { params })

export const detail = id => request.get(`/articles/${id}`)

export const hot = (limit = 5) => request.get('/articles/hot', { params: { limit } })

export const archives = () => request.get('/articles/archives')

export const like = id => request.post(`/articles/${id}/like`)

// ===== 管理端 =====
export const adminPage = params => request.get('/admin/articles/page', { params })

export const adminDetail = id => request.get(`/admin/articles/${id}`)

export const save = data => request.post('/admin/articles', data)

export const updateStatus = (id, status) => request.put(`/admin/articles/${id}/status`, { status })

export const updateTop = (id, isTop) => request.put(`/admin/articles/${id}/top`, null, { params: { isTop } })

export const remove = id => request.delete(`/admin/articles/${id}`)

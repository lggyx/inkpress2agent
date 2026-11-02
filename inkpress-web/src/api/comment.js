import request from '@/utils/request'

export const listByArticle = articleId => request.get(`/articles/${articleId}/comments`)

export const create = data => request.post('/comments', data)

export const adminPage = params => request.get('/admin/comments', { params })

export const audit = (id, status) => request.put(`/admin/comments/${id}/status`, { status })

export const remove = id => request.delete(`/admin/comments/${id}`)

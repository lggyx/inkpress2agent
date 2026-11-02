import request from '@/utils/request'

export const page = params => request.get('/admin/users', { params })

export const remove = id => request.delete(`/admin/users/${id}`)

export const resetPassword = (id, password) => request.put(`/admin/users/${id}/password`, { password })

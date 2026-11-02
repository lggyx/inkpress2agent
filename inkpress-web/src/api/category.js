import request from '@/utils/request'

export const list = () => request.get('/categories')

export const save = data => request.post('/admin/categories', data)

export const update = data => request.put('/admin/categories', data)

export const remove = id => request.delete(`/admin/categories/${id}`)

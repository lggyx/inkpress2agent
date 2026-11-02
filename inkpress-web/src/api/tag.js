import request from '@/utils/request'

export const list = () => request.get('/tags')

export const save = data => request.post('/admin/tags', data)

export const update = data => request.put('/admin/tags', data)

export const remove = id => request.delete(`/admin/tags/${id}`)

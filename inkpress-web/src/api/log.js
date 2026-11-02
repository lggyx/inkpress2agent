import request from '@/utils/request'

export const page = params => request.get('/admin/logs', { params })

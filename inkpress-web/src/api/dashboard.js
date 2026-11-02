import request from '@/utils/request'

export const stats = () => request.get('/admin/dashboard/stats')

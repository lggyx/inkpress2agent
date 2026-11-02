import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'inkpress_token'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('inkpress_user')
      if (window.location.pathname.startsWith('/admin')) {
        window.location.href = '/login'
      }
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  error => {
    ElMessage.error(
      error.message === 'Network Error' ? '网络异常，请检查后端服务是否启动' : error.message || '请求失败'
    )
    return Promise.reject(error)
  }
)

export default request

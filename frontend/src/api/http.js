import axios from 'axios'
import { clearAuth, getToken } from '../utils/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response?.data
    if (payload && typeof payload.code !== 'undefined' && Number(payload.code) === 401) {
      clearAuth()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      const error = new Error(payload.message || '登录已过期，请重新登录')
      error.response = response
      return Promise.reject(error)
    }
    return payload
  },
  (error) => {
    const status = error?.response?.status
    const backendMessage = error?.response?.data?.message

    if (status === 401) {
      clearAuth()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    if (backendMessage) {
      error.message = backendMessage
    } else if (status === 500) {
      const url = String(error?.config?.url || '')
      if (url.includes('/api/stats')) {
        error.message = '后端统计服务异常或未启动，请检查 stats-service / gateway 是否运行'
      } else if (url.includes('/api/market')) {
        error.message = '后端二手市场服务异常或未启动，请检查 market-service / gateway 是否运行'
      } else {
        error.message = '后端服务异常或未启动，请检查 user-service / gateway 是否运行'
      }
    } else if (status === 502 || status === 503 || status === 504) {
      error.message = '后端网关不可用，请检查服务注册和网关状态'
    } else if (error?.code === 'ECONNABORTED') {
      error.message = '请求超时，请检查后端服务状态'
    } else if (!error?.response) {
      error.message = '无法连接后端服务，请确认网关和服务已启动'
    }
    return Promise.reject(error)
  }
)

export default http

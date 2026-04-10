import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkStatsStartupHealth } from '../api/stats'
import { isLoggedIn } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/profile', component: () => import('../views/ProfileView.vue') },
  { path: '/lost-found', component: () => import('../views/LostFoundView.vue') },
  { path: '/activities', component: () => import('../views/ActivityView.vue') },
  { path: '/market', component: () => import('../views/MarketView.vue') },
  { path: '/system', component: () => import('../views/SystemView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const DASHBOARD_HEALTH_CACHE_MS = 20000
let dashboardHealthCache = {
  timestamp: 0,
  detail: null
}

function buildHealthFailMessage(detail) {
  if (!detail) {
    return '后端服务异常或未启动，请检查 gateway / user-service / stats-service'
  }
  const segments = []
  const services = [
    ['gateway', detail.gateway],
    ['user', detail.user],
    ['stats', detail.stats]
  ]
  for (const [name, status] of services) {
    const up = status?.up === true ? 'UP' : 'DOWN'
    const message = status?.message ? `(${status.message})` : ''
    segments.push(`${name}:${up}${message}`)
  }
  return `启动检查未通过：${segments.join('，')}`
}

function buildHealthRequestErrorMessage(error) {
  const status = Number(error?.response?.status || 0)
  const requestUrl = String(error?.config?.url || '')
  if (status === 404 && requestUrl.includes('/api/stats/health/startup')) {
    return '未找到启动健康检查接口，已降级为兼容模式，请尽快重启 api-gateway / stats-service'
  }
  if (status === 500 && requestUrl.includes('/api/stats/health/startup')) {
    return '启动检查失败，请检查 gateway / user-service / stats-service 是否运行'
  }
  return error?.message || '后端服务异常或未启动，请检查 gateway / user-service / stats-service'
}

function isStartupHealth404(error) {
  const status = Number(error?.response?.status || 0)
  const requestUrl = String(error?.config?.url || '')
  return status === 404 && requestUrl.includes('/api/stats/health/startup')
}

async function ensureDashboardHealth() {
  const now = Date.now()
  if (dashboardHealthCache.detail && now - dashboardHealthCache.timestamp < DASHBOARD_HEALTH_CACHE_MS) {
    return dashboardHealthCache.detail
  }
  const payload = await checkStatsStartupHealth()
  if (!payload || Number(payload.code) !== 0) {
    throw new Error(payload?.message || '启动健康检查失败')
  }
  const detail = payload.data || {}
  dashboardHealthCache = {
    timestamp: now,
    detail
  }
  return detail
}

router.beforeEach(async (to, from, next) => {
  if (to.meta.public) {
    if (to.path === '/login' && isLoggedIn()) {
      next('/dashboard')
      return
    }
    next()
    return
  }
  if (!isLoggedIn()) {
    next('/login')
    return
  }

  if (to.path === '/dashboard') {
    try {
      const detail = await ensureDashboardHealth()
      if (detail.overallUp !== true) {
        ElMessage.error(buildHealthFailMessage(detail))
        next('/system')
        return
      }
    } catch (error) {
      if (isStartupHealth404(error)) {
        dashboardHealthCache = {
          timestamp: Date.now(),
          detail: { overallUp: true, compatibilityMode: true }
        }
        ElMessage.warning(buildHealthRequestErrorMessage(error))
        next()
        return
      }
      ElMessage.error(buildHealthRequestErrorMessage(error))
      next('/system')
      return
    }
  }

  next()
})

export default router

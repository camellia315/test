import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkStatsStartupHealth } from '../api/stats'
import { fetchUserRoles } from '../api/user'
import { getRoles, isLoggedIn, setRoles } from '../utils/auth'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
  { path: '/messages', component: () => import('../views/MessagesView.vue') },
  { path: '/help', component: () => import('../views/HelpView.vue') },
  { path: '/personal', component: () => import('../views/PersonalCenterView.vue') },
  { path: '/profile', component: () => import('../views/ProfileView.vue') },
  { path: '/space/:userId', component: () => import('../views/UserSpaceView.vue') },
  { path: '/lost-found', component: () => import('../views/LostFoundView.vue') },
  { path: '/lost-found/private-chat', component: () => import('../views/LostFoundPrivateChatView.vue') },
  { path: '/activities', component: () => import('../views/ActivityView.vue') },
  { path: '/market', component: () => import('../views/MarketView.vue') },
  { path: '/chat-center', component: () => import('../views/ChatCenterView.vue') },
  { path: '/system', component: () => import('../views/SystemView.vue'), meta: { requiresAdmin: true } },
  { path: '/admin/users', component: () => import('../views/SystemView.vue'), meta: { requiresAdmin: true } },
  { path: '/admin/content-audit', component: () => import('../views/admin/ContentAuditView.vue'), meta: { requiresAdmin: true } },
  { path: '/admin/analytics', component: () => import('../views/admin/AdminAnalyticsView.vue'), meta: { requiresAdmin: true } },
  { path: '/admin/settings', component: () => import('../views/admin/AdminSettingsView.vue'), meta: { requiresAdmin: true } }
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

async function ensureRolesLoaded() {
  const cached = getRoles()
  if (Array.isArray(cached) && cached.length > 0) {
    return cached
  }
  try {
    const payload = await fetchUserRoles()
    if (payload && Number(payload.code) === 0 && Array.isArray(payload.data?.roles)) {
      setRoles(payload.data.roles)
      return payload.data.roles
    }
  } catch {
  }
  setRoles([])
  return []
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
    setRoles([])
    next('/login')
    return
  }

  const roles = await ensureRolesLoaded()
  const isAdmin = roles.includes('ADMIN')
  if (to.meta.requiresAdmin && !isAdmin) {
    ElMessage.warning('该页面仅管理员可见')
    next('/dashboard')
    return
  }

  if (to.path === '/dashboard') {
    try {
      const detail = await ensureDashboardHealth()
      if (detail.overallUp !== true) {
        ElMessage.error(buildHealthFailMessage(detail))
        next('/help')
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
      next('/help')
      return
    }
  }

  next()
})

export default router

